import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { AuthService } from '../../core/auth.service';
import { DocumindApi } from '../../core/documind-api.service';
import { extrairApiError } from '../../core/http.interceptor';
import {
  Colecao, Documento, DocumentoDetalhe, ESTADOS_DOCUMENTO, ESTADO_SEVERITY,
  formatarBytes, formatarData, PageResponse, Severidade,
} from '../../core/models';

const TAMANHO_PAGINA = 8;
const INTERVALO_POLLING_MS = 2500;

@Component({
  imports: [
    FormsModule, ButtonModule, DialogModule, InputTextModule,
    SelectModule, TagModule, ToastModule, TooltipModule, ConfirmDialogModule,
  ],
  providers: [MessageService, ConfirmationService],
  selector: 'app-documentos',
  styleUrl: './documentos.scss',
  templateUrl: './documentos.html',
})
export class DocumentosPage implements OnInit, OnDestroy {
  private readonly api = inject(DocumindApi);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(MessageService);
  private readonly confirmacoes = inject(ConfirmationService);
  private readonly rota = inject(ActivatedRoute);

  // ---- listagem
  protected pagina = signal<PageResponse<Documento> | null>(null);
  protected numeroPagina = signal(0);
  protected filtroNome = '';
  protected filtroTipo: { label: string; value: string | null } | null = null;
  protected filtroEstado: { label: string; value: string | null } | null = null;
  protected carregando = signal(false);

  protected readonly tipos = [
    { label: 'Todos os tipos', value: null },
    { label: 'PDF', value: 'PDF' },
    { label: 'DOCX', value: 'DOCX' },
    { label: 'TXT', value: 'TXT' },
  ];

  protected readonly estados = [
    { label: 'Todos os estados', value: null },
    ...ESTADOS_DOCUMENTO.map(e => ({ label: rotuloEstado(e), value: e })),
  ];

  // ---- upload
  protected dialogUpload = signal(false);
  protected colecoes = signal<Colecao[]>([]);
  protected colecaoEscolhida: Colecao | null = null;
  protected arquivosEscolhidos = signal<File[]>([]);
  protected enviando = signal(false);

  // ---- detalhe
  protected dialogDetalhe = signal(false);
  protected detalhe = signal<DocumentoDetalhe | null>(null);

  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    void this.carregar(0);
    void this.carregarColecoes();
    this.abrirDoQueryParam();
  }

  ngOnDestroy(): void {
    this.pararPolling();
  }

  protected severidade(estado: string): Severidade {
    return ESTADO_SEVERITY[estado] ?? 'info';
  }

  protected rotulo(estado: string): string {
    return rotuloEstado(estado);
  }

  protected readonly exibirBytes = formatarBytes;
  protected readonly exibirData = formatarData;

  protected rotuloTarefa(estado: string): string {
    return ({
      PENDENTE: 'Na fila',
      EM_EXECUCAO: 'Executando',
      CONCLUIDA: 'Concluída',
      FALHA: 'Falhou',
    })[estado] ?? estado;
  }

  private async carregar(pagina: number): Promise<void> {
    this.carregando.set(true);
    try {
      const resposta = await this.api.listarDocumentos({
        page: pagina,
        size: TAMANHO_PAGINA,
        nome: this.filtroNome.trim() || undefined,
        tipo: this.filtroTipo?.value ?? undefined,
        estado: this.filtroEstado?.value ?? undefined,
      });
      this.pagina.set(resposta);
      this.numeroPagina.set(resposta.pagina);
      this.ajustarPolling(resposta.conteudo);
    } catch (falha) {
      this.toasts.add({
        severity: 'error',
        summary: 'Falha ao listar',
        detail: extrairApiError(falha).message,
        life: 5000,
      });
    } finally {
      this.carregando.set(false);
    }
  }

  private async carregarColecoes(): Promise<void> {
    try {
      this.colecoes.set(await this.api.listarColecoes());
    } catch {
      // Upload sem coleção continua possível; coleções são opcionais.
    }
  }

  protected buscar(): void {
    void this.carregar(0);
  }

  protected paginar(evento: { first: number }): void {
    void this.carregar(Math.floor(evento.first / TAMANHO_PAGINA));
  }

  // ---------------------------------------------------------------- upload
  protected abrirUpload(): void {
    this.arquivosEscolhidos.set([]);
    this.colecaoEscolhida = null;
    this.dialogUpload.set(true);
  }

  protected escolherArquivos(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    this.arquivosEscolhidos.set(Array.from(entrada.files ?? []));
  }

  protected async enviar(): Promise<void> {
    const arquivos = this.arquivosEscolhidos();
    if (!arquivos.length || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    try {
      await this.api.enviarDocumentos(arquivos, this.colecaoEscolhida?.id);
      this.toasts.add({
        severity: 'success',
        summary: 'Enviado',
        detail: `${arquivos.length} arquivo(s) em processamento.`,
        life: 4000,
      });
      this.dialogUpload.set(false);
      void this.carregar(0);
    } catch (falha) {
      this.toasts.add({
        severity: 'error',
        summary: 'Falha no envio',
        detail: extrairApiError(falha).message,
        life: 6000,
      });
    } finally {
      this.enviando.set(false);
    }
  }

  // ---------------------------------------------------------------- detalhe
  /** Acesso direto via citacao do chat: /documentos?doc=<id> abre o painel. */
  private abrirDoQueryParam(): void {
    const bruto = this.rota.snapshot.queryParamMap.get('doc');
    if (!bruto) {
      return;
    }
    const id = Number(bruto);
    if (Number.isInteger(id) && id > 0) {
      void this.abrirDetalhe(id);
    }
  }

  protected async abrirDetalhe(id: number): Promise<void> {
    this.dialogDetalhe.set(true);
    try {
      this.detalhe.set(await this.api.detalheDocumento(id));
    } catch (falha) {
      this.toasts.add({
        severity: 'error',
        summary: 'Detalhe',
        detail: extrairApiError(falha).message,
        life: 5000,
      });
    }
  }

  protected async baixar(id: number, nomeOriginal: string): Promise<void> {
    const token = this.auth.tokenValido();
    if (!token) {
      return;
    }
    try {
      const resposta = await fetch(this.api.urlDownload(id), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!resposta.ok) {
        throw new Error(`HTTP ${resposta.status}`);
      }
      const blob = await resposta.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = nomeOriginal;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      this.toasts.add({
        severity: 'error',
        summary: 'Download',
        detail: 'Não foi possível baixar o arquivo.',
        life: 4000,
      });
    }
  }

  protected async reprocessar(id: number): Promise<void> {
    try {
      await this.api.reprocessarDocumento(id);
      this.toasts.add({
        severity: 'info',
        summary: 'Reprocessar',
        detail: 'Documento reenfileirado.',
        life: 3000,
      });
      this.dialogDetalhe.set(false);
      void this.carregar(this.numeroPagina());
    } catch (falha) {
      this.toasts.add({
        severity: 'error',
        summary: 'Falha',
        detail: extrairApiError(falha).message,
        life: 5000,
      });
    }
  }

  protected confirmarExclusao(id: number, nomeOriginal: string): void {
    this.confirmacoes.confirm({
      header: 'Excluir documento',
      message: `"${nomeOriginal}" será excluído permanentemente: trechos, embeddings e citações somem. Continuar?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Excluir',
      rejectLabel: 'Cancelar',
      acceptButtonProps: { severity: 'danger' },
      accept: () => {
        void this.excluir(id);
      },
    });
  }

  private async excluir(id: number): Promise<void> {
    try {
      await this.api.excluirDocumento(id);
      this.toasts.add({
        severity: 'success',
        summary: 'Excluído',
        detail: 'Documento e derivados removidos.',
        life: 3500,
      });
      this.dialogDetalhe.set(false);
      void this.carregar(this.numeroPagina());
    } catch (falha) {
      this.toasts.add({
        severity: 'error',
        summary: 'Falha',
        detail: extrairApiError(falha).message,
        life: 5000,
      });
    }
  }

  // ---------------------------------------------------------------- polling
  /** Polling ligado apenas com documentos em estados transitórios. */
  private ajustarPolling(itens: Documento[]): void {
    const existeAndamento = itens.some(
      d => d.estado === 'PROCESSANDO' || d.estado === 'AGUARDANDO_PROCESSAMENTO',
    );
    if (existeAndamento && !this.timer) {
      this.timer = setInterval(
        () => void this.carregar(this.numeroPagina()),
        INTERVALO_POLLING_MS,
      );
    } else if (!existeAndamento) {
      this.pararPolling();
    }
  }

  private pararPolling(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}

/** Rótulos legíveis dos estados (o código UPPERCASE é estável na API). */
function rotuloEstado(estado: string): string {
  return ({
    ENVIADO: 'Enviado',
    AGUARDANDO_PROCESSAMENTO: 'Aguardando',
    PROCESSANDO: 'Processando',
    CONCLUIDO: 'Concluído',
    FALHOU: 'Falhou',
  })[estado] ?? estado;
}
