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
  imports: [FormsModule, ButtonModule, InputTextModule, PasswordModule, MessageModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.scss',
  templateUrl: './login.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected senha = '';
  protected enviando = signal(false);
  protected erro = signal<string | null>(null);

  async entrar(): Promise<void> {
    if (!this.email.trim() || !this.senha || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    try {
      await this.auth.login(this.email.trim(), this.senha);
      await this.router.navigate(['/documentos']);
    } catch (falha) {
      this.erro.set(extrairApiError(falha).message);
    } finally {
      this.enviando.set(false);
    }
  }
}
