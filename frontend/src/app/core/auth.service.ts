import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenResponse, Usuario } from './models';

/**
 * Identidade: token no localStorage + expiração verificada no boot e no guard.
 * A identidade NUNCA vem do front para o back — o backend usa o JWT.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private static readonly CHAVE_TOKEN = 'documind.token';

  readonly usuario = signal<Usuario | null>(null);
  readonly autenticado = computed(() => !!this.tokenValido());

  constructor() {
    this.restaurarSessao();
  }

  registrar(dados: { nome: string; email: string; senha: string }): Promise<TokenResponse> {
    return this.enviar('/auth/register', dados);
  }

  login(email: string, senha: string): Promise<TokenResponse> {
    return this.enviar('/auth/login', { email, senha });
  }

  private async enviar(caminho: string, corpo: unknown): Promise<TokenResponse> {
    const resposta = await firstValueFrom(
      this.http.post<TokenResponse>(`${this.base()}${caminho}`, corpo),
    );
    localStorage.setItem(AuthService.CHAVE_TOKEN, resposta.token);
    this.usuario.set(resposta.usuario);
    return resposta;
  }

  logout(): void {
    localStorage.removeItem(AuthService.CHAVE_TOKEN);
    this.usuario.set(null);
  }

  /** Token AINDA válido (não expirado); usado pelo interceptor e pelo guard. */
  tokenValido(): string | null {
    const token = localStorage.getItem(AuthService.CHAVE_TOKEN);
    if (!token) {
      return null;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (payload?.exp && Date.now() / 1000 >= payload.exp) {
        this.logout();
        return null;
      }
      return token;
    } catch {
      this.logout();
      return null;
    }
  }

  /** Revalida o token no backend (/auth/me) chamado no boot da área protegida. */
  async sincronizarSessao(): Promise<void> {
    const token = this.tokenValido();
    if (!token) {
      return;
    }
    try {
      const usuario = await firstValueFrom(
        this.http.get<Usuario>(`${this.base()}/auth/me`),
      );
      this.usuario.set(usuario);
    } catch (erro) {
      const status = (erro as HttpErrorResponse).status;
      if (status === 401 || status === 403) {
        this.logout();
      }
    }
  }

  private base(): string {
    return 'http://localhost:8080/api';
  }

  private restaurarSessao(): void {
    if (this.tokenValido()) {
      const token = localStorage.getItem(AuthService.CHAVE_TOKEN);
      try {
        const payload = JSON.parse(atob((token ?? '').split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        // Nome/email só aparecem após /auth/me; exibição provisória pelo id.
        this.usuario.set({ id: Number(payload.sub), nome: '...', email: '', dataCriacao: '' });
      } catch {
        /* token estranho: fica null até o sincronizarSessao */
      }
    }
  }
}
