// ---------------------------------------------------------------------------
// DocuMind — contratos da API (espelhos fiis dos DTOs do backend Spring).
// ---------------------------------------------------------------------------

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  dataCriacao: string;
}

export interface TokenResponse {
  token: string;
  tipo: string;
  expiraEm: string;
  usuario: Usuario;
}

// ---------- Documentos ----------

export interface ColecaoResumo {
  id: number;
  nome: string;
}

export interface Documento {
  id: number;
  nomeOriginal: string;
  tipoArquivo: string;
  tipoMime: string;
  tamanhoBytes: number;
  totalPaginas: number | null;
  estado: string; // ENVIADO | AGUARDANDO_PROCESSAMENTO | PROCESSANDO | CONCLUIDO | FALHOU
  mensagemErro: string | null;
  dataUpload: string;
  dataProcessamento: string | null;
  colecoes: ColecaoResumo[];
}

export interface DocumentoDetalhe extends Documento {
  modeloEmbedding: string | null;
  quantTrechos: number;
  tarefaEstado: string | null;
  tarefaTentativas: number | null;
  tarefaUltimoErro: string | null;
}

export interface DocumentoStatus {
  id: number;
  estado: string;
  mensagemErro: string | null;
  quantTrechos: number;
  dataProcessamento: string | null;
}

export interface PageResponse<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

// ---------- Coleções ----------

export interface Colecao {
  id: number;
  nome: string;
  descricao: string | null;
  dataCriacao: string;
  dataAtualizacao: string;
  quantDocumentos: number;
}

export interface DocumentoDaColecao {
  id: number;
  nomeOriginal: string;
  tipoArquivo: string;
  tamanhoBytes: number;
  totalPaginas: number | null;
  estado: string;
  dataUpload: string;
}

export interface ColecaoDetalhe {
  id: number;
  nome: string;
  descricao: string | null;
  dataCriacao: string;
  dataAtualizacao: string;
  quantDocumentos: number;
  documentos: DocumentoDaColecao[];
}

// ---------- Conversas / RAG ----------

export interface Conversa {
  id: number;
  titulo: string;
  colecaoId: number | null;
  colecaoNome: string | null;
  dataCriacao: string;
  dataAtualizacao: string;
}

export interface MetadadosResposta {
  modelo?: string;
  tokensEntrada?: number;
  tokensSaida?: number;
  latenciaMs?: number;
  trechosContexto?: number;
}

export interface Citacao {
  id: number;
  documentoId: number;
  documentoNome: string | null;
  trechoId: number | null;
  referencia: number;
  indiceTrecho: number;
  paginaInicio: number | null;
  paginaFim: number | null;
  conteudoTrecho: string;
}

export interface Mensagem {
  id: number;
  autor: 'USUARIO' | 'ASSISTENTE' | 'SISTEMA';
  estado: 'OK' | 'SEM_EVIDENCIA' | 'ERRO';
  conteudo: string;
  dataCriacao: string;
  metadados: MetadadosResposta;
  citacoes: Citacao[];
}

// ---------- Busca ----------

export interface TrechoHit {
  trechoId: number;
  documentoId: number;
  documentoNome: string;
  documentoTipo: string;
  indiceTrecho: number;
  paginaInicio: number | null;
  paginaFim: number | null;
  conteudo: string;
  similaridade: number;
  referencia: number;
}

// ---------- Erro da API ----------

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
}

export interface HealthResponse {
  banco: { estado: 'UP' | 'DOWN'; detalhe?: string };
  ia: Record<string, unknown>;
  timestamp: string;
}

// Estados de documento na ordem do ciclo de vida.
export const ESTADOS_DOCUMENTO = [
  'ENVIADO',
  'AGUARDANDO_PROCESSAMENTO',
  'PROCESSANDO',
  'CONCLUIDO',
  'FALHOU',
] as const;

export const ESTADO_SEVERITY: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary'> = {
  ENVIADO: 'secondary',
  AGUARDANDO_PROCESSAMENTO: 'info',
  PROCESSANDO: 'warn',
  CONCLUIDO: 'success',
  FALHOU: 'danger',
};

/** Severidades aceitáveis por p-tag/p-button (union estável da API PrimeNG). */
export type Severidade = 'success' | 'info' | 'warn' | 'danger' | 'secondary';

export function formatarBytes(bytes: number): string {  if (!bytes && bytes !== 0) {
    return '-';
  }
  const unidades = ['B', 'KB', 'MB', 'GB'];
  let valor = bytes;
  let unidade = 0;
  while (valor >= 1024 && unidade < unidades.length - 1) {
    valor /= 1024;
    unidade++;
  }
  return `${valor.toFixed(valor < 10 && unidade > 0 ? 1 : 0)} ${unidades[unidade]}`;
}

export function formatarData(iso: string | null | undefined): string {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}
