import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DocumindApi } from '../../core/documind-api.service';
import { extrairApiError } from '../../core/http.interceptor';
import {
  Colecao, Documento, ESTADOS_DOCUMENTO, PageResponse, TrechoHit, formatarData, formatarBytes,
} from '../../core/models';

/**
 * Pesquisa global:
 * - SEMÂNTICO: trechos por similaridade (exigindo embeddings; 503 honesto senão);
 * - METADADOS: mesmo motor de filtros da listagem de documentos.
 */
@Component({
  imports: [
    FormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule,
    TagModule, ToastModule,
  ],
  providers: [MessageService],
  selector: 'app-busca',
  styleUrl: './busca.scss',
  templateUrl: './busca.html',
})
export class BuscaPage implements OnInit {
  private readonly api = inject(DocumindApi);
  private readonly router = inject(Router);
  private readonly toasts = inject(MessageService);

  protected aba = signal<'semantico' | 'metadados'>('semantico');

  // ---- semântico
  protected pergunta = '';
  protected colecoes = signal<Colecao[]>([]);
  protected escopo: Colecao | null = null;
  protected resultados = signal<TrechoHit[] | null>(null);
  protected pesquisando = signal(false);
  protected ultimaPergunta = '';

  // ---- metadados
  protected nome = '';
  protected filtroEstado = '';
  protected documentos = signal<PageResponse<Documento> | null>(null);
  protected listaCarregando = signal(false);

  protected dialogCitacao = signal(false);
  protected citacaoAberta = signal<TrechoHit | null>(null);

  protected readonly estadosBusca = ['', ...ESTADOS_DOCUMENTO];
  protected readonly exibirData = formatarData;
  protected readonly exibirBytes = formatarBytes;

  ngOnInit(): void {
    void this.carregarColecoes();
  }

  private async carregarColecoes(): Promise<void> {
    try {
      this.colecoes.set(await this.api.listarColecoes());
    } catch {
      /* opcional */
    }
  }

  protected async pesquisarSemantico(): Promise<void> {
    const texto = this.pergunta.trim();
    if (!texto || this.pesquisando()) {
      return;
    }
    this.pesquisando.set(true);
    try {
      const resposta = await this.api.buscarSemantico(texto, this.escopo?.id);
      this.resultados.set(resposta.resultados);
      this.ultimaPergunta = resposta.pergunta;
    } catch (falha) {
      const erro = extrairApiError(falha);
      this.toasts.add({
        severity: erro.status === 503 ? 'warn' : 'error',
        summary: erro.code,
        detail: erro.message,
        life: 8000,
      });
      this.resultados.set([]);
    } finally {
      this.pesquisando.set(false);
    }
  }

  protected abrir(hit: TrechoHit): void {
    this.citacaoAberta.set(hit);
    this.dialogCitacao.set(true);
  }

  protected abrirDocumento(documentoId: number): void {
    this.dialogCitacao.set(false);
    void this.router.navigate(['/documentos'], { queryParams: { doc: documentoId } });
  }

  protected similaridade(hit: TrechoHit): string {
    return `${Math.round(hit.similaridade * 100)}%`;
  }

  protected classificacao(hit: TrechoHit): string {
    const valor = hit.similaridade;
    if (valor >= 0.8) {
      return 'alta';
    }
    if (valor >= 0.5) {
      return 'media';
    }
    return 'baixa';
  }

  protected async listarMetadados(): Promise<void> {
    this.listaCarregando.set(true);
    try {
      this.documentos.set(await this.api.listarDocumentos({
        page: 0,
        size: 50,
        nome: this.nome.trim() || undefined,
        estado: this.filtroEstado || undefined,
      }));
    } catch (falha) {
      this.toasts.add({ severity: 'error', summary: 'Busca', detail: extrairApiError(falha).message, life: 5000 });
    } finally {
      this.listaCarregando.set(false);
    }
  }
}
