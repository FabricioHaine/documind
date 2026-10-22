import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth.service';
import { extrairApiError } from '../../core/http.interceptor';

@Component({
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  selector: 'app-register',
  styleUrl: './register.scss',
  templateUrl: './register.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected nome = '';
  protected email = '';
  protected senha = '';
  protected confirmacao = '';
  protected enviando = signal(false);
  protected erro = signal<string | null>(null);

  /** Validações de FORMA no front; a política real é do BACKEND (>=8, letra+número). */
  protected get senhasDiferentes(): boolean {
    return this.confirmacao.length > 0 && this.confirmacao !== this.senha;
  }

  async criarConta(): Promise<void> {
    if (this.enviando() || this.senhasDiferentes) {
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    try {
      await this.auth.registrar({ nome: this.nome.trim(), email: this.email.trim(), senha: this.senha });
      await this.router.navigate(['/documentos']);
    } catch (falha) {
      this.erro.set(extrairApiError(falha).message);
    } finally {
      this.enviando.set(false);
    }
  }
}
