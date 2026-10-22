import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiErrorBody } from './models';

/**
 * Anexa o Bearer token. Em 401 encerra a sessão (o guard redireciona).
 * A identidade NUNCA vem do front — o backend valida o JWT.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (req.url.includes('/health')) {
    return next(req);
  }
  const token = auth.tokenValido();
  const autorizada = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(autorizada).pipe(
    catchError(erro => {
      if ((erro as HttpErrorResponse).status === 401) {
        auth.logout();
      }
      return throwError(() => erro);
    }),
  );
};

/** Extrai o corpo estável da API para exibição honesta (mensagens do backend). */
export function extrairApiError(erro: unknown): ApiErrorBody {
  const excecao = erro as HttpErrorResponse;
  if (excecao && typeof excecao === 'object' && 'error' in excecao) {
    const corpo = excecao.error as Partial<ApiErrorBody>;
    if (corpo && corpo.code && corpo.message) {
      return { status: corpo.status ?? excecao.status, code: corpo.code, message: corpo.message };
    }
  }
  const status = (erro as HttpErrorResponse)?.status ?? 0;
  return {
    status,
    code: status ? 'ERRO_INESPERADO' : 'SEM_CONEXAO',
    message: status
      ? 'Erro inesperado. Verifique os logs do back-end.'
      : 'Sem conexão com o back-end. Ele está rodando em http://localhost:8080?',
  };
}
