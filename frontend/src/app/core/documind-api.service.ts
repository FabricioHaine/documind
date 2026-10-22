import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Colecao, ColecaoDetalhe, Conversa, Documento, DocumentoDetalhe, DocumentoStatus,
  HealthResponse, Mensagem, PageResponse, TrechoHit, Usuario,
} from './models';

/** Endpoint único — o back-end sempre determina o usuário pelo JWT. */
const BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class DocumindApi {
  private readonly http = inject(HttpClient);

  private get<T>(caminho: string, params?: HttpParams): Promise<T> {
    return firstValueFrom(this.http.get<T>(`${BASE}${caminho}`, { params }));
  }

  private post<T>(caminho: string, corpo?: unknown): Promise<T> {
    return firstValueFrom(this.http.post<T>(`${BASE}${caminho}`, corpo ?? {}));
  }

  private put<T>(caminho: string, corpo: unknown): Promise<T> {
    return firstValueFrom(this.http.put<T>(`${BASE}${caminho}`, corpo));
  }

  private patch<T>(caminho: string, corpo: unknown): Promise<T> {
    return firstValueFrom(this.http.patch<T>(`${BASE}${caminho}`, corpo));
  }

  private del(caminho: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${BASE}${caminho}`));
  }

  // ----------------------------------------------------------------- saude
  saude(): Promise<HealthResponse> {
    return this.get('/health');
  }

  // ----------------------------------------------------------------- documentos
  enviarDocumentos(arquivos: File[], colecaoId?: number): Promise<Documento[]> {
    const form = new FormData();
    arquivos.forEach(arquivo => form.append('arquivos', arquivo));
    if (colecaoId) {
      form.append('colecaoId', String(colecaoId));
    }
    return firstValueFrom(this.http.post<Documento[]>(`${BASE}/documents`, form));
  }

  listarDocumentos(filtros: DocFiltros): Promise<PageResponse<Documento>> {
    let params = new HttpParams()
      .set('page', filtros.page)
      .set('size', filtros.size);
    if (filtros.nome) params = params.set('nome', filtros.nome);
    if (filtros.tipo) params = params.set('tipo', filtros.tipo);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.colecaoId) params = params.set('colecaoId', String(filtros.colecaoId));
    if (filtros.data) params = params.set('data', filtros.data);
    return this.get('/documents', params);
  }

  detalheDocumento(id: number): Promise<DocumentoDetalhe> {
    return this.get(`/documents/${id}`);
  }

  statusDocumento(id: number): Promise<DocumentoStatus> {
    return this.get(`/documents/${id}/status`);
  }

  reprocessarDocumento(id: number): Promise<DocumentoStatus> {
    return this.post(`/documents/${id}/reprocess`);
  }

  excluirDocumento(id: number): Promise<void> {
    return this.del(`/documents/${id}`);
  }

  urlDownload(id: number): string {
    return `${BASE}/documents/${id}/download`;
  }

  // ----------------------------------------------------------------- coleções
  listarColecoes(): Promise<Colecao[]> {
    return this.get('/collections');
  }

  criarColecao(nome: string, descricao: string): Promise<Colecao> {
    return this.post('/collections', { nome, descricao });
  }

  detalheColecao(id: number): Promise<ColecaoDetalhe> {
    return this.get(`/collections/${id}`);
  }

  atualizarColecao(id: number, nome: string, descricao: string): Promise<Colecao> {
    return this.put(`/collections/${id}`, { nome, descricao });
  }

  adicionarDocColecao(colecaoId: number, documentoId: number): Promise<ColecaoDetalhe> {
    return this.post(`/collections/${colecaoId}/documents`, { documentoId });
  }

  removerDocColecao(colecaoId: number, documentoId: number): Promise<void> {
    return this.del(`/collections/${colecaoId}/documents/${documentoId}`);
  }

  excluirColecao(id: number): Promise<void> {
    return this.del(`/collections/${id}`);
  }

  // ----------------------------------------------------------------- conversas
  listarConversas(): Promise<Conversa[]> {
    return this.get('/conversations');
  }

  criarConversa(titulo?: string, colecaoId?: number): Promise<Conversa> {
    return this.post('/conversations', { titulo, colecaoId });
  }

  detalheConversa(id: number): Promise<Conversa> {
    return this.get(`/conversations/${id}`);
  }

  renomearConversa(id: number, titulo: string): Promise<Conversa> {
    return this.patch(`/conversations/${id}`, { titulo });
  }

  trocarEscopoConversa(id: number, colecaoId: number): Promise<Conversa> {
    return this.patch(`/conversations/${id}`, { colecaoId });
  }

  mensagens(id: number): Promise<Mensagem[]> {
    return this.get(`/conversations/${id}/messages`);
  }

  perguntar(id: number, conteudo: string): Promise<Mensagem> {
    return this.post(`/conversations/${id}/messages`, { conteudo });
  }

  excluirConversa(id: number): Promise<void> {
    return this.del(`/conversations/${id}`);
  }

  // ----------------------------------------------------------------- busca
  buscarSemantico(pergunta: string, colecaoId?: number): Promise<{ pergunta: string; resultados: TrechoHit[] }> {
    return this.post('/search/semantic', { pergunta, colecaoId });
  }

  // ----------------------------------------------------------------- usuário
  meuPerfil(): Promise<Usuario> {
    return this.get('/auth/me');
  }
}

export interface DocFiltros {
  page: number;
  size: number;
  nome?: string;
  tipo?: string;
  estado?: string;
  colecaoId?: number;
  data?: string;
}
