import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DocumindApi } from '../../../core/documind-api.service';
import { extrairApiError } from '../../../core/http.interceptor';
import { Citacao, Colecao, Conversa, Mensagem, formatarData } from '../../../core/models';

@Component({
  imports: [
    FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule,
    SelectModule, TagModule, ToastModule,
  ],
  providers: [MessageService],
  selector: 'app-conversa-detalhe',
  styleUrl: './conversa-detalhe.scss',
  templateUrl: './conversa-detalhe.html',
})
export class ConversaDetalhePage implements OnInit {
  private readonly api = inject(DocumindApi);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toasts = inject(MessageService);

  protected conversa = signal<Conversa | null>(null);
  protected mensagens = signal<Mensagem[]>([]);
  protected colecoes = signal<Colecao[]>([]);
  protected carregando = signal(false);
  protected perguntando = signal(false);

  protected novaPergunta = '';
  protected dialogAjustes = signal(false);
  protected tituloAjuste = '';
  protected escopoAjuste: Colecao | null = null;

  protected dialogCitacao = signal(false);
  protected citacaoAberta = signal<(Citacao & { conversaId: number }) | null>(null);

  protected readonly exibirData = formatarData;

  ngOnInit(): void {
    const id = Number(this.rota.snapshot.paramMap.get('id'));
    if (!id) {
      void this.router.navigate(['/conversas']);
      return;
    }
    void this.carregarConversa(id);
    void this.carregarMensagens(id);
    void this.carregarColecoes();
  }

  private idConversa(): number {
    return this.conversa()?.id ?? Number(this.rota.snapshot.paramMap.get('id'));
  }

  private async carregarConversa(id: number): Promise<void> {
    try {
      this.conversa.set(await this.api.detalheConversa(id));
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Conversa', detail: extrairApiError(falha).message, life: 5000 });
    }
  }

  private async carregarMensagens(id: number): Promise<void> {
    this.carregando.set(true);
    try {
      this.mensagens.set(await this.api.mensagens(id));
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Mensagens', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.carregando.set(false);
    }
  }

  private async carregarColecoes(): Promise<void> {
    try {
      this.colecoes.set(await this.api.listarColecoes());
    } catch {
      /* opcional */
    }
  }

  protected async perguntar(): Promise<void> {
    const texto = this.novaPergunta.trim();
    if (!texto || this.perguntando()) {
      return;
    }
    this.perguntando.set(true);
    try {
      await this.api.perguntar(this.idConversa(), texto);
      this.novaPergunta = '';
      await this.carregarMensagens(this.idConversa());
      // título auto muda no backend na 1ª pergunta; recarrega cabeçalho
      void this.carregarConversa(this.idConversa());
    } catch (falha) {
      const erro = extrairApiError(falha);
      this.toasts.add({
        severity: erro.status === 503 ? 'warn' : 'error',
        summary: erro.code,
        detail: erro.message,
        life: 8000,
      });
      await this.carregarMensagens(this.idConversa());
    } finally {
      this.perguntando.set(false);
    }
  }

  protected abrirCitacao(citacao: Citacao): void {
    this.citacaoAberta.set({ ...citacao, conversaId: this.idConversa() });
    this.dialogCitacao.set(true);
  }

  /** Citação clicável → documento focado na listagem (query param doc=id). */
  protected abrirDocumento(documentoId: number): void {
    this.dialogCitacao.set(false);
    void this.router.navigate(['/documentos'], { queryParams: { doc: documentoId } });
  }

  protected abrirAjustes(): void {
    const c = this.conversa();
    if (!c) {
      return;
    }
    this.tituloAjuste = c.titulo;
    this.escopoAjuste = this.colecoes().find(x => x.id === c.colecaoId) ?? null;
    this.dialogAjustes.set(true);
  }

  protected async salvarAjustes(): Promise<void> {
    const c = this.conversa();
    if (!c) {
      return;
    }
    try {
      if (this.tituloAjuste.trim() && this.tituloAjuste.trim() !== c.titulo) {
        this.conversa.set(await this.api.renomearConversa(c.id, this.tituloAjuste.trim()));
      }
      if (this.escopoAjuste && this.escopoAjuste.id !== c.colecaoId) {
        this.conversa.set(await this.api.trocarEscopoConversa(c.id, this.escopoAjuste.id));
      }
      this.dialogAjustes.set(false);
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Ajustes', detail: extrairApiError(falha).message, life: 5000 });
    }
  }
}
