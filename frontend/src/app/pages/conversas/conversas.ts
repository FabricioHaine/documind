import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DocumindApi } from '../../core/documind-api.service';
import { extrairApiError } from '../../core/http.interceptor';
import { Colecao, Conversa, formatarData } from '../../core/models';

@Component({
  imports: [
    FormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule,
    ToastModule, TooltipModule, ConfirmDialogModule,
  ],
  providers: [MessageService, ConfirmationService],
  selector: 'app-conversas',
  styleUrl: './conversas.scss',
  templateUrl: './conversas.html',
})
export class ConversasPage implements OnInit {
  private readonly api = inject(DocumindApi);
  private readonly router = inject(Router);
  private readonly toasts = inject(MessageService);
  private readonly confirmacoes = inject(ConfirmationService);

  protected conversas = signal<Conversa[]>([]);
  protected colecoes = signal<Colecao[]>([]);
  protected carregando = signal(false);

  protected dialogNova = signal(false);
  protected titulo = '';
  protected colecaoEscolhida: Colecao | null = null;
  protected criando = signal(false);

  protected readonly exibirData = formatarData;

  ngOnInit(): void {
    void this.carregar();
  }

  private async carregar(): Promise<void> {
    this.carregando.set(true);
    try {
      const [conversas, colecoes] = await Promise.all([
        this.api.listarConversas(),
        this.api.listarColecoes(),
      ]);
      this.conversas.set(conversas);
      this.colecoes.set(colecoes);
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Conversas', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.carregando.set(false);
    }
  }

  protected abrirNova(): void {
    this.titulo = '';
    this.colecaoEscolhida = null;
    this.dialogNova.set(true);
  }

  protected async criar(): Promise<void> {
    if (this.criando()) {
      return;
    }
    this.criando.set(true);
    try {
      const nova = await this.api.criarConversa(
        this.titulo.trim() || undefined,
        this.colecaoEscolhida?.id,
      );
      this.dialogNova.set(false);
      await this.router.navigate(['/conversas', nova.id]);
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Conversa', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.criando.set(false);
    }
  }

  protected entrar(conversa: Conversa): void {
    void this.router.navigate(['/conversas', conversa.id]);
  }

  protected confirmarExclusao(conversa: Conversa): void {
    this.confirmacoes.confirm({
      header: 'Excluir conversa',
      message: `"${conversa.titulo}" e todas as mensagens/citações serão excluídas. Continuar?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Excluir',
      rejectLabel: 'Cancelar',
      acceptButtonProps: { severity: 'danger' },
      accept: () => {
        void this.excluir(conversa.id);
      },
    });
  }

  private async excluir(id: number): Promise<void> {
    try {
      await this.api.excluirConversa(id);
      void this.carregar();
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Falha', detail: extrairApiError(falha).message, life: 5000 });
    }
  }
}
