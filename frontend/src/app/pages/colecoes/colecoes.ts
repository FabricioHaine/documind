import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DocumindApi } from '../../core/documind-api.service';
import { extrairApiError } from '../../core/http.interceptor';
import {
  Colecao, ColecaoDetalhe, Documento, PageResponse, Severidade, formatarBytes, formatarData,
} from '../../core/models';

@Component({
  imports: [
    FormsModule, ButtonModule, DialogModule, InputTextModule, TextareaModule,
    SelectModule, TagModule, ToastModule, TooltipModule, ConfirmDialogModule,
  ],
  providers: [MessageService, ConfirmationService],
  selector: 'app-colecoes',
  styleUrl: './colecoes.scss',
  templateUrl: './colecoes.html',
})
export class ColecoesPage implements OnInit {
  private readonly api = inject(DocumindApi);
  private readonly toasts = inject(MessageService);
  private readonly confirmacoes = inject(ConfirmationService);

  protected colecoes = signal<Colecao[]>([]);
  protected carregando = signal(false);

  // ---- criar/editar
  protected dialogEdicao = signal(false);
  protected editandoId: number | null = null;
  protected nome = '';
  protected descricao = '';
  protected salvando = signal(false);

  // ---- detalhe documentos
  protected dialogDetalhe = signal(false);
  protected detalhe = signal<ColecaoDetalhe | null>(null);
  protected dialogVincular = signal(false);
  protected candidatos = signal<Documento[]>([]);
  protected candidatoEscolhido: Documento | null = null;

  protected readonly exibirBytes = formatarBytes;
  protected readonly exibirData = formatarData;

  protected severidade(estado: string): Severidade {
    return ({
      CONCLUIDO: 'success', PROCESSANDO: 'warn', FALHOU: 'danger',
      AGUARDANDO_PROCESSAMENTO: 'info', ENVIADO: 'secondary',
    } as Record<string, Severidade>)[estado] ?? 'info';
  }

  ngOnInit(): void {
    void this.carregar();
  }

  private async carregar(): Promise<void> {
    this.carregando.set(true);
    try {
      this.colecoes.set(await this.api.listarColecoes());
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Coleções', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.carregando.set(false);
    }
  }

  // ---------------------------------------------------------------- CRUD
  protected nova(): void {
    this.editandoId = null;
    this.nome = '';
    this.descricao = '';
    this.dialogEdicao.set(true);
  }

  protected editar(colecao: Colecao): void {
    this.editandoId = colecao.id;
    this.nome = colecao.nome;
    this.descricao = colecao.descricao ?? '';
    this.dialogEdicao.set(true);
  }

  protected async salvar(): Promise<void> {
    if (!this.nome.trim() || this.salvando()) {
      return;
    }
    this.salvando.set(true);
    try {
      if (this.editandoId) {
        await this.api.atualizarColecao(this.editandoId, this.nome.trim(), this.descricao);
      } else {
        await this.api.criarColecao(this.nome.trim(), this.descricao);
      }
      this.toasts.add({ severity: 'success', summary: 'Coleção', detail: 'Salva com sucesso.', life: 3000 });
      this.dialogEdicao.set(false);
      void this.carregar();
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Coleção', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.salvando.set(false);
    }
  }

  protected confirmarExclusao(colecao: Colecao): void {
    this.confirmacoes.confirm({
      header: 'Excluir coleção',
      message: `"${colecao.nome}" será excluída. Os DOCUMENTOS permanecem no seu acervo (só o escopo some). Continuar?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Excluir',
      rejectLabel: 'Cancelar',
      acceptButtonProps: { severity: 'danger' },
      accept: () => {
        void this.excluir(colecao.id);
      },
    });
  }

  private async excluir(id: number): Promise<void> {
    try {
      await this.api.excluirColecao(id);
      this.toasts.add({ severity: 'success', summary: 'Excluída', detail: 'Documentos preservados.', life: 3500 });
      void this.carregar();
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Falha', detail: extrairApiError(falha).message, life: 5000 });
    }
  }

  // ------------------------------------------------- documentos da coleção
  protected async abrirDetalhe(colecao: Colecao): Promise<void> {
    try {
      this.detalhe.set(await this.api.detalheColecao(colecao.id));
      this.dialogDetalhe.set(true);
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Coleção', detail: extrairApiError(falha).message, life: 5000 });
    }
  }

  protected async abrirVincular(): Promise<void> {
    const detalhe = this.detalhe();
    if (!detalhe) {
      return;
    }
    try {
      const vinculados = new Set(detalhe.documentos.map(d => d.id));
      const lista = await this.api.listarDocumentos({ page: 0, size: 100 });
      this.candidatos.set(lista.conteudo.filter(d => !vinculados.has(d.id)));
      this.candidatoEscolhido = null;
      this.dialogVincular.set(true);
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Documentos', detail: extrairApiError(falha).message, life: 5000 });
    }
  }

  protected async vincular(): Promise<void> {
    const detalhe = this.detalhe();
    if (!detalhe || !this.candidatoEscolhido) {
      return;
    }
    try {
      this.detalhe.set(await this.api.adicionarDocColecao(detalhe.id, this.candidatoEscolhido.id));
      this.dialogVincular.set(false);
      void this.carregar();
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Vincular', detail: extrairApiError(falha).message, life: 5000 });
    }
  }

  protected async desvincular(documentoId: number): Promise<void> {
    const detalhe = this.detalhe();
    if (!detalhe) {
      return;
    }
    try {
      await this.api.removerDocColecao(detalhe.id, documentoId);
      this.detalhe.set(await this.api.detalheColecao(detalhe.id));
      void this.carregar();
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Remover', detail: extrairApiError(falha).message, life: 5000 });
    }
  }
}
