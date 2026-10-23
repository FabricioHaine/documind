import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenResponse, Usuario } from './models';

/**
 * Identidade: token no localStorage + expiração verificada no boot e no guard.
 * A identidade NUNCA vem do front para o back — o backend usa o JWT.
 *
 * O token vive num SIGNAL (fonte reativa) — o localStorage só é a persistência.
 * Assim `autenticado` reage à troca de token: um `computed` sobre localStorage
 * (não reativo) avaliava uma vez e jamais se invalidava, travando o usuário
 * na tela de login mesmo com a sessão válida.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private static readonly CHAVE_TOKEN = 'documind.token';

  /** Fonte de verdade reativa do token em memória (espelha o localStorage). */
  private readonly tokenAtual = signal<string | null>(null);

  readonly usuario = signal<Usuario | null>(null);
  readonly autenticado = computed(() => !!this.tokenAtual());

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
    this.definirToken(resposta.token);
    this.usuario.set(resposta.usuario);
    return resposta;
  }

  logout(): void {
    this.tokenAtual.set(null);
    localStorage.removeItem(AuthService.CHAVE_TOKEN);
    this.usuario.set(null);
  }

  /** Token AINDA válido (não expirado); usado pelo interceptor e pelo guard auxiliar. */
  tokenValido(): string | null {
    const token = this.tokenAtual();
    if (!token) {
      return null;
    }
    if (this.expirado(token)) {
      this.logout();
      return null;
    }
    return token;
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

  /** Restaura a sessão persistida: token no signal + perfil provisório até /auth/me. */
  private restaurarSessao(): void {
    const salvo = localStorage.getItem(AuthService.CHAVE_TOKEN);
    if (!salvo) {
      return;
    }
    try {
      const payload = JSON.parse(atob(salvo.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (payload?.exp && Date.now() / 1000 >= payload.exp) {
        this.logout();
        return;
      }
      this.tokenAtual.set(salvo);
      // Nome/email só aparecem após /auth/me; exibição provisória pelo id.
      this.usuario.set({ id: Number(payload.sub), nome: '...', email: '', dataCriacao: '' });
    } catch {
      // token estranho: descarta e fica null até o sincronizarSessao
      this.logout();
    }
  }

  private definirToken(token: string): void {
    this.tokenAtual.set(token);
    localStorage.setItem(AuthService.CHAVE_TOKEN, token);
  }

  private expirado(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return !!payload?.exp && Date.now() / 1000 >= payload.exp;
    } catch {
      return true;
    }
  }
}
