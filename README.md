# DocuMind — Plataforma de Inteligência de Documentos (RAG)

> Pergunte aos seus documentos. Respostas com fontes verificáveis — busca vetorial real (pgvector),
> embeddings reais e citações construídas pelo back-end, nunca pelo modelo.

**DocuMind** é uma aplicação full-stack de **RAG (Retrieval-Augmented Generation)** com autenticação
própria, upload de PDF/DOCX/TXT, processamento assíncrono com embeddings, chat por documentos (ou
por coleções), referências clicáveis com página/trecho de origem, busca semântica entre trechos e
isolamento total de dados por usuário.

O projeto foi construído como **portfólio profissional**, com foco em: pipeline RAG honesto,
arquitetura em camadas com regras de negócio nos serviços, segurança aplicada ao SQL (não ao prom
-zA), operação explícita de falhas e qualidade automatizada.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Arquitetura](#2-arquitetura)
3. [Stack técnica](#3-stack-técnica)
4. [Funcionalidades](#4-funcionalidades)
5. [O pipeline RAG em detalhe](#5-o-pipeline-rag-em-detalhe)
6. [Modelo de dados](#6-modelo-de-dados)
7. [Pré-requisitos](#7-pré-requisitos)
8. [Instalação e execução](#8-instalação-e-execução)
9. [Variáveis de ambiente](#9-variáveis-de-ambiente)
10. [Referência da API](#10-referência-da-api)
11. [Segurança](#11-segurança)
12. [Política de falhas e honestidade da IA](#12-política-de-falhas-e-honestidade-da-ia)
13. [Exclusão, retenção e reprocessamento](#13-exclusão-retenção-e-reprocessamento)
14. [Testes](#14-testes)
15. [Estrutura do repositório](#15-estrutura-do-repositório)
16. [Decisões técnicas](#16-decisões-técnicas)
17. [Limitações conhecidas](#17-limitações-known)
18. [Roadmap](#18-roadmap)
19. [Solução de problemas](#19-solução-de-problemas)
20. [Licença](#20-licença)

---

## 1. Visão geral

Fluxo do produto:

1. O usuário **envia documentos** (PDF/DOCX/TXT, até 25 MB cada, com verificação de *magic bytes*).
2. O texto é **extraído, dividido em trechos** (com overlap) e **gerados embeddings reais** através
   de um provedor compatível com a API OpenAI (por padrão, **Ollama local**).
3. Cada trecho + embedding é persistido no **PostgreSQL 18 com pgvector**.
4. No chat, a pergunta do usuário vira um embedding e a **busca vetorial** (cosine) — filtrando
   **por usuário e pelo escopo (coleção opcional)** — recupera os trechos mais relevantes.
5. O prompt com contexto numerado `[1..n]` é enviado ao modelo de chat, e a **resposta vem com
   citações** cujos identificadores são construídos pelo **back-end** a partir dos trechos
   REALMENTE recuperados. Se a recuperação não traz nada acima do limiar de relevância, o sistema
   responde **explicitamente que não há evidência** — o modelo nunca é deixado adivinhar.
6. Na interface, cada citação é clicável e leva ao trecho de origem (documento, página, índice).

**Importante**: sem IA configurada o sistema é honesto — sobe normalmente, mas endpoints de RAG
respondem `503 IA_NAO_CONFIGURADA` com mensagem clara. Nunca há respostas simuladas, dados de
exemplo "enganadores" nem fontes inventadas.

---

## 2. Arquitetura

```
┌────────────────────────┐     HTTPS/JSON      ┌─────────────────────────────┐
│  Front-end (Angular 22)│  ─────────────────► │  Back-end (Java 21,         │
│  PrimeNG, SCSS, tokens │  ◄───────────────── │  Spring Boot 3.5)           │
│  premium (dark)        │   JWT Bearer        │  Controllers (sem regras)   │
└────────────────────────┘                     │  ├─ Services (regras)       │
                                               │  ├─ Services de IA (RAG)    │
                                               │  ├─ Repositórios (JPA/SQL   │
                                               │  │   nativo p/ vetores)     │
                                               │  └─ Flyway (schema)         │
                                               └────┬───────────────┬────────┘
                                                    │               │
                                          ┌─────────▼──────┐  ┌─────▼──────────────┐
                                          │ PostgreSQL 18  │  │ Ollama (OpenAI-    │
                                          │ + pgvector     │  │ compatible v1)     │
                                          │ HNSW cosine    │  │ chat + embeddings  │
                                          └────────────────┘  └────────────────────┘
```

Camadas do back-end (`com.documind`):

| Pacote | Responsabilidade |
|---|---|
| `config` | Propriedades tipadas (`AppProperties`, `AiProperties`), Security/CORS, Async, OpenAPI |
| `common` | Exceção hierarchy + `ApiError` consistente + `GlobalExceptionHandler` + `PageResponse` |
| `security` | `JwtService`, filtros de autenticação e rate limit, `AuthFacade` (identidade na thread) |
| `usuario` | Registro/login/me (BCrypt, e-mail único) |
| `documento` | Upload/validação/armazenamento, extração (PDFBox/POI), chunking, listagem/detalhe/download/reprocess/exclusão |
| `processamento` | Fila assíncrona (tarefa + estados), pipeline extração→chunks→embeddings, cancelamento cooperativo, retry com agendamento |
| `rag` | `VectorSearchRepository` (SQL nativo pgvector), cliente turn-compatible (embeddings/chat com retry), `RagService` (retrieval+geração), prompts endurecidos |
| `colecao` | CRUD de coleções e vínculos com documentos (escopo de pesquisa do RAG) |
| `conversa` | Conversas, mensagens, ções com snapshot, orquestração pergunta→RAG→resposta+citações |
| `busca` | `GET /api/search` (metadados) e `POST /api/search/semantic` (vetorial) |
| `health` | `GET /api/health` com estado real do banco e da configuração de IA |

**Controllers não contêm regra de negócio** — apenas HTTP, validação de entrada e delegação.
**DTOs em tudo na superfície HTTP** — entidades JPA nunca são serializadas.

---

## 3. Stack técnica

| Camada | Tecnologia | Versão |
|---|---|---|
| Back-end | Java + Spring Boot | 21 / 3.5.16 |
| Build | Maven (wrapper incluído) | 3.9.11 |
| Segurança | Spring Security + JJWT | 0.13.0 |
| Banco | PostgreSQL + pgvector | 18 / 0.8.7 |
| Migrações | Flyway (`V1__documind_schema.sql`) | — |
| Busca vetorial | pgvector HNSW (`vector_cosine_ops`) | — |
| Extração PDF | Apache PDFBox | 3.0.8 |
| Extração DOCX | Apache POI | 5.4.1 |
| API docs | springdoc-openapi | 2.8.17 |
| Testes | JUnit 5 + Mockito + AssertJ | — |
| Front-end | Angular (standalone) | 22.2.3 |
| UI Kit | PrimeNG + PrimeIcons | 22.1.4 / 8.0.2 |
| Tipografia | Inter (@fontsource/inter) | 5.3.0 |
| IA (padrão) | Ollama local | `llama3.2:3b` + `nomic-embed-text` (768 dims) |

O cliente de IA usa o contrato **OpenAI-compatible** (`/v1/embeddings`, `/v1/chat/completions`), então
funciona com Ollama, vLLM, OpenRouter, LM Studio etc. — **nenhum modelo está fixado no código**.

---

## 4. Funcionalidades

### Conta
- Registro com e-mail único e senha forte (BCrypt); login JWT (HS256, expiração configurável);
  `GET /api/auth/me` para o perfil.

### Documentos
- Upload **múltiplo** com vínculo opcional a uma coleção.
- Validação por **assinatura binária** (magic bytes) — o `content-type` do cliente não é confiável;
  mime é reconstruído no servidor; nomes sanitizados (caminhos/controles removidos).
- Estados de ciclo de vida: `ENVIADO → AGUARDANDO_PROCESSAMENTO → PROCESSANDO → CONCLUIDO` (ou
  `FALHOU` com mensagem legível). **Polling** de status por documento.
- Listagem com filtros (nome, tipo, estado, data de envio, coleção), ordenação e paginação.
- Detalhe com metadados completos (trechos totais, tarefa, tentativas e último erro).
- Download do arquivo original; reprocessamento; exclusão consistente (§13).

### Coleções (escopo de RAG)
- CRUD + adicionar/remover documentos. **Excluir coleção NÃO exclui documentos** (decisão
  documentada); conversas com escopo nessa coleção passam a buscar em todo o acervo.

### Conversas (chat com documentos)
- Histórico persistido; renomear; escopo opcional por coleção.
- **Resposta com citações** `[1..n]`: cada fonte referencia documento + trecho + página + conteúdo.
- Mensagens marcadas com estado `OK`, `SEM_EVIDENCIA` ou `ERRO` — o histórico é honesto.

### Pesquisa
- `GET /api/search`: metadados (mesma engine de filtros da listagem).
- `POST /api/search/semantic`: trechos por **similaridade semântica** (só exige embeddings
  configurados), com isolamento por usuário/coleção.

### Operação
- `GET /api/health` público com diagnóstico real de banco e configuração de IA.
- Swagger UI em `/swagger-ui.html`.

---

## 5. O pipeline RAG em detalhe

```
upload ──► extração (PDF: página-a-página; DOCX/TXT: sem páginas)
       ──► chunking (1200 chars, overlap 200, respeita linhas/parágrafos)
       ──► embeddings (em lotes, modelo p.ex. nomic-embed-text, 768 dims)
       ──► upsert pgvector (metadados JSON: caracteres/unidades + modelo_embedding) + índice HNSW

pergunta ──► embedding da pergunta
         ──► busca cosseno HNSW APENAS: usuario_id = X, (colecao opcional), modelo_embedding = modelo configurado
         ──► limiar de relevância (0.35 default) e top-K (6 trechos)
         ──► contexto numerado [1..n] (atribuido pelo BACKEND)
         ──► prompt do sistema anti-injection (texto do documento = DADO, nunca instrução)
         ──► resposta com [n] nas afirmações
         ──► persistência: mensagem + citações (SNAPSHOT de trecho/página)
```

Detalhes de engenharia que sustentam a corretude:

- **Isolamento no SQL, não no prompt**: a query vetorial inclui `usuario_id` e escopo de coleção
  como predicados — o modelo não pode "ver" documentos de outros usuários.
- **Espaço de embeddings por modelo**: busca sempre filtra `modelo_embedding` = modelo configurado;
  não mistura espaços de modelos diferentes.
- **Decisão de sem-evidência é do backend**: recuperação vazia ⇒ resposta fixa honesta e mensagem
  persistida como `SEM_EVIDENCIA`, sem gastar chamada de chat.
- **Prompt injection**: instruções dentro de documentos são tratadas como dados no prompt do sistema.
- **Dimensão fixa no schema**: `embedding vector(768)` — a dimensão é validada a cada lote no cliente
  (rejeita resposta fora da dimensão esperada) e uma migração é exigida para trocar de modelo/novo tamanho.

---

## 6. Modelo de dados

Principal (resumo — ver `V1__documind_schema.sql`):

| Tabela | Papel | Notas |
|---|---|---|
| `usuario` | conta | e-mail único; senha BCrypt |
| `documento` | arquivo do usuário | nome original/armazenado, mime REAL, tamanho, `estado`, erro, modelo de embedding, totais reais |
| `trecho_documento` | chunk + vetor | `embedding vector(768)`, `modelo_embedding`, índice, páginas; FK `ON DELETE CASCADE` p/ documento; índice **HNSW cosine** |
| `tarefa_processamento` | fila/retries | estados da tarefa, tentativas, último erro |
| `colecao` | escopo | nome único por usuário |
| `documento_colecao` | vínculo N:N | FK duplo CASCADE |
| `conversa` | chat | FK coleção **SET NULL** (perde escopo, não o histórico) |
| `mensagem` | USUARIO/ASSISTENTE... | `estado` (OK/SEM_EVIDENCIA/ERRO), `metadados` TEXT (JSON seguro: modelo, tokens, latência, trechos no contexto) |
| `citacao` | fonte da resposta | FK documento **CASCADE**; FK trecho **SET NULL** (snapshot de conteúdo/páginas é preservado) |

---

## 7. Pré-requisitos

1. **PostgreSQL 18 + pgvector 0.8.7** — no Windows, use `scripts/install-pgvector.ps1`
   (executar como administrador; copia os binários da extensão para a instalação do PostgreSQL).
2. **Ollama + modelos** (provedor de IA padrão, executado localmente):

   ```powershell
   winget install Ollama.Ollama
   ollama pull llama3.2:3b        # modelo de chat (bom equilíbrio qualidade/latência local)
   ollama pull nomic-embed-text   # modelo de embeddings (768 dims)
   ```

   > Em máquinas com GPU capaz de rodar o `llama3.1:8b` 100% na VRAM, use-o como
   > `DOCUMIND_AI_CHAT_MODEL` — respostas de melhor qualidade. Em máquinas onde o 8B
   > fica parcialmente em CPU (ver `ollama ps`), o `llama3.2:3b` evita latências de
   > dezenas de segundos por resposta. O modelo nunca é fixo no código.

3. **JDK 21+** (build usa `--release 21`) e **Node.js 20+**.
4. Qualquer provedor **OpenAI-compatible** pode substituir o Ollama (vLLM, LM Studio, OpenRouter) —
   só mudar as variáveis `DOCUMIND_AI_*`.

---

## 8. Instalação e execução

```powershell
# 1. Obtenha o código (já na raiz do projeto)
git clone <repo> documind; cd documind   # ou use a pasta já criada

# 2. Banco + .env local (interativo: pede senha do postgres e do app)
powershell -ExecutionPolicy Bypass -File scripts/setup-database.ps1

# 3. Backend (carrega backend/.env automaticamente, aplica Flyway e sobe na :8080)
.\run-backend.ps1
#    Swagger: http://localhost:8080/swagger-ui.html
#    Health:  http://localhost:8080/api/health

# 4. Front-end
cd frontend
npm install
npm start           # http://localhost:4200
```

Testes unitários: `cd backend; .\mvnw.cmd test`.
Testes contra banco real: `$env:DOCUMIND_IT="true"; .\mvnw.cmd test` (exige PostgreSQL preparado).

---

## 9. Variáveis de ambiente

Todas têm default funcional exceto as marcadas **(obrigatória)**. Veja `.env.example`.

| Variável | Descrição | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC do banco | `jdbc:postgresql://localhost:5432/documind` |
| `SPRING_DATASOURCE_USERNAME` | usuário do banco | `documind` |
| `SPRING_DATASOURCE_PASSWORD` | senha do banco — **(setup define)** | `documind` |
| `DOCUMIND_JWT_SECRET` | segredo HS256 **(obrigatória, ≥ 32 chars)**; ausente/curta aborta o startup | — |
| `DOCUMIND_AI_BASE_URL` | base **OpenAI-compatible** (ex.: `http://localhost:11434/v1`); vazio ⇒ IA desligada (503 claro) | vazio |
| `DOCUMIND_AI_API_KEY` | token se o provedor pedir (Ollama local: vazio) | vazio |
| `DOCUMIND_AI_CHAT_MODEL` | modelo de chat (ex.: `llama3.2:3b`) | vazio |
| `DOCUMIND_AI_EMBEDDING_MODEL` | modelo de embeddings (ex.: `nomic-embed-text`) | vazio |
| `DOCUMIND_AI_EMBEDDING_DIMENSIONS` | dimensão esperada (validada no recebimento) | `768` |
| `DOCUMIND_AI_MAX_CONTEXT_CHUNKS` | K trechos no contexto | `6` |
| `DOCUMIND_AI_MIN_RELEVANCE_THRESHOLD` | limiar cosine mínimo para aceitar trecho | `0.35` |
| `DOCUMIND_AI_REQUEST_TIMEOUT_SECONDS` | timeout por chamada | `120` |
| `DOCUMIND_AI_MAX_RETRIES` | retries de chamada de IA | `2` |
| `DOCUMIND_AI_EMBEDDING_BATCH_SIZE` | trechos por lote de embedding | `16` |
| `DOCUMIND_AI_MAX_OUTPUT_TOKENS` | limite de saída do chat | `1024` |
| `DOCUMIND_RAG_CHUNK_SIZE_CHARS` | tamanho de chunk | `1200` |
| `DOCUMIND_RAG_CHUNK_OVERLAP_CHARS` | overlap | `200` |
| `DOCUMIND_DOCUMENT_MAX_SIZE_MB` | tamanho máximo de cada arquivo | `25` |
| `DOCUMIND_DOCUMENT_UPLOADS_PER_HOUR` | limite por usuário/hora | `30` |
| `DOCUMIND_DOCUMENT_STORAGE_PATH` | onde os arquivos ficam | `./storage` |
| `DOCUMIND_RATE_LIMIT_PER_MINUTE` | reqs/min por IP (rotas de IA: por usuário) | `60` |
| `DOCUMIND_CORS_ORIGINS` | origens permitidas (dev front) | `http://localhost:4200` |
| `DOCUMIND_JWT_EXP_MINUTES` | validade do token | `120` |

**Nenhum segredo é commitado.** `.gitignore` cobre `.env`, `*.env` e `backend/storage/`.

---

## 10. Referência da API

Autenticação via header `Authorization: Bearer <JWT>` (exceto register/login/health/docs).

### `POST /api/auth/register` | `POST /api/auth/login` | `GET /api/auth/me`

### Documentos `/api/documents`
| Método | Caminho | Descrição |
|---|---|---|
| POST | `/` | multipart `arquivos[]` (+`colecaoId` opcional) ⇒ 201 com metadados |
| GET | `/` | filtros `nome,tipo,estado,colecaoId,data&page,size` |
| GET | `/{id}` | detalhe completo |
| GET | `/{id}/status` | polling de processamento |
| GET | `/{id}/download` | stream do original |
| POST | `/{id}/reprocess` | 202 reenfileira |
| DELETE | `/{id}` | exclusão consistente (§13) |

### Coleções `/api/collections`
| POST `/` · GET `/` · GET `/{id}` · PUT `/{id}` · DELETE `/{id}` |
| POST `/{id}/documents` `{ "documentoId": n }` · DELETE `/{id}/documents/{documentoId}` |

### Conversas `/api/conversations`
| POST `/` `{titulo?, colecaoId?}` · GET `/` · GET `/{id}` · PATCH `/{id}` · DELETE `/{id}` |
| GET `/{id}/messages` histórico · POST `/{id}/messages` `{conteudo}` ⇒ resposta RAG + citações |

### Pesquisa e saúde
| GET `/api/search` metadados · POST `/api/search/semantic` `{pergunta, colecaoId?}` |
| GET `/api/health` (público) |

Códigos de erro padronizados:

```json
{ "status": 403, "code": "ACESSO_NEGADO", "message": "Você não tem acesso a este documento." }
```

Formas usuais: `400 REQUISICAO_INVALIDA`, `400 ARQUIVO_INVALIDO`, `401 NAO_AUTORIZADO`,
`403 ACESSO_NEGADO`, `404 NAO_ENCONTRADO`, `409 CONFLITO`, `429 LIMITE_EXCEDIDO`,
`502 PROVEDOR_IA_INDISPONIVEL`, `503 IA_NAO_CONFIGURADA`. Stack traces nunca vazam (server.error.*=never).

---

## 11. Segurança

- **JWT HS256** com segredo obrigatório ≥ 32 chars (fail-fast no startup — nada de segredo default).
- **Credenciais**: BCrypt (strength default do Spring Security); e-mail único no banco + validação.
- **Autorização no back-end em cada consulta** via `AuthFacade`: o intermediário nunca envia
  `usuarioId` — a identidade vem do contexto de segurança e os repositórios filtram por ela
  (Specifications JPA e SQL nativo).
- **Rate limiting**: fixed-window por IP (auth) e por usuário (rotas de IA). Excede ⇒ `429`.
- **Upload seguro**: extensão em lista de permissões (`pdf/docx/txt`), verificação de *magic bytes* por tipo
  (PNG/JPEG/executável mascarado como `.txt` → rejeitado), tamanho máximo antes da leitura completa.
- **Armazenamento local**: nomes UUID dentro do path configurado; download valida o dono ANTES de abrir o stream.
- **CORS** fechado por default (`http://localhost:4200` em dev) e headers de segurança do Spring Security.

---

## 12. Política de falhas e honestidade da IA

| Situação | Comportamento |
|---|---|
| IA não configurada (`DOCUMIND_AI_*` vazios) | app sobe; `503 IA_NAO_CONFIGURADA`; health mostra `ia.configurado=false` |
| Provedor cai/timeout/limite | retry configurável ⇒ se persistir, mensagem do assistente persistida com estado `ERRO` + `502 PROVEDOR_IA_INDISPONIVEL` |
| Recuperação vazia (nada acima do limiar) | resposta fixa honesta (sem chamada de chat) persistida como `SEM_EVIDENCIA` — e o front exibe como tal |
| Erro inesperado no fluxo de resposta | detalhes logados no servidor; mensagem segura persistida como `ERRO` e `502` com texto genérico (sem stack p/ usuário) |
| Embeddings fora da dimensão esperada | lote abortado, tarefa `FALHOU` com a mensagem no documento |

A pergunta do usuário é sempre persistida (mesmo em falha) — o histórico conversacional é fiel;
nada de respostas "concluídas falsas".

---

## 13. Exclusão, retenção e reprocessamento

- **Excluir documento**: cancelamento cooperativo do processamento (flag verificada por lote)
  ⇒ delete com **FK CASCADE** (trechos + embeddings + tarefas + citações) ⇒ remoção do
  arquivo no disco. **Não sobra trecho recuperável pelo RAG** — a cascata é atômica no banco.
- **Excluir coleção**: só o container; documentos permanecem; conversas com escopo perdem a coleção (SET NULL).
- **Excluir conversa**: mensagens + citações removidas em cascata.
- **Reprocessar**: enfileira nova tarefa; citações antigas mantêm snapshot (FK trecho SET NULL),
  então referências antigas continuam legíveis mesmo com trechos reindexados.

---

## 14. Testes

| Teste | Escopo | Como roda |
|---|---|---|
| `ChunkingServiceTest` | limites, overlap, páginas, normalização | `mvnw test` |
| `DocumentoUploadValidatorTest` | magic bytes, extensão, sanitização | `mvnw test` |
| `PromptTemplatesTest` | regras anti-injeção, numeração backend, escopo | `mvnw test` |
| `JwtServiceTest` | fail-fast de segredo, roundtrip, expiração | `mvnw test` |
| `DocumindIt` | banco real: extensão vector, 9 tabelas, dimensão 768 | `DOCUMIND_IT=true mvnw test` |

O conjunto IT fica **gated** por `DOCUMIND_IT` para não pedir infra em toda execução local/pipeline.

---

## 15. Estrutura do repositório

```
documind/
├── backend/
│   ├── mvnw.cmd / mvnw
│   ├── pom.xml
│   ├── .env                  (gerado pelo setup — NÃO versionado)
│   └── src/
│       ├── main/java/com/documind/   (pacotes por domain, §2)
│       ├── main/resources/
│       │   ├── application.yml       (tudo sobrescrito por DOCUMIND_*)
│       │   └── db/migration/V1__documind_schema.sql
│       └── test/java/               (unitários + DocumindIt gated)
├── frontend/                  (Angular 22 + PrimeNG; gerado por ng new)
├── scripts/
│   ├── install-pgvector.ps1   (copia pgvector 0.8.7 p/ PostgreSQL 18 — admin)
│   └── setup-database.ps1     (role+db+extensão + backend/.env local)
├── .env.example
├── .gitignore
├── run-backend.ps1
└── README.md
```

---

## 16. Decisões técnicas

1. **pgvector + SQL nativo via `JdbcTemplate`** para vetores (upsert/busca) com `CAST(? AS vector)`:
   contole total do SQL de isolamento e indexação; JPA não mapeia o vetor (evita converters frágeis).
2. **Dimensão fixa `vector(768)`** no schema: pgvector exige typmod para HNSW; a dimensão escolhida
   acompanha o modelo de embeddings padrão e a verificação de troca é explícita.
3. **Tarefas em banco, não em fila externa**: para portfólio local, isto é simples de operar,
   observável (estados/tentativas/erro) e sobrevive a restart (scheduler reenfileira órfãos).
4. **Providers por interface** (`ChatProvider`, `EmbeddingProvider`) + checks SEPARADOS:
   sem chat mas com embeddings ⇒ busca semântica continua funcionando.
5. **Citações com snapshot**: o texto do trecho é copiado para a citação; reprocessar/excluir trechos
   não destrói o histórico; a FK de trecho é SET NULL de propósito.
6. **Estado de mensagens explícito** (OK/SEM_EVIDENCIA/ERRO): o front nunca precisa "adivinhar".
7. **`--release 21` com JDK qualquer ≥ 21**: build determinístico em command line (Windows/Idea friendly).
8. **Angular standalone + PrimeNG**: templates sem NgModule, tema dark premium com tokens
   SCSS definidos uma única vez (`styles.scss`): fundo `#0B0F14`, superfícies `#11161D`/`#1A212B`,
   borda `#232C38`, texto `#E6EDF3`/`#8B98A9`, acento teal `#2DD4BF`, perigo `#F87171`,
   aviso `#FBBF24`, sucesso `#34D399`; tipografia Inter.

---

## 17. Limitações conhecidas

- DOCX/TXT não têm noção de página (indica "página null") — estrutura do formato, honestamente
  exposto na UI.
- Rate limit fixed-window in-memory (não distribuído).
- Fila de processamento é serial por documento; OCR/escaneados não suportados (PDF com imagem
  sem texto ⇒ falha legível no documento).
- O chat não faz streaming de tokens (requisito simples interno: resposta completa).
- Trocar o **modelo de embeddings** exige migração (dimension fixa) — ver §5 e §9.

---

## 18. Roadmap

- [ ] OCR (Tesseract) para PDFs escaneados
- [ ] Streaming SSE do chat
- [ ] Upload de CSV/planilhas com chunking estruturado
- [ ] Progresso de processamento via WebSocket em tempo real (substituir polling)
- [ ] Métricas Prometheus + tracing distribuído
- [ ] Rate limit/fila com Redis/RabbitMQ para escala horizontal

---

## 19. Solução de problemas

| Sintoma | Causa provável | Ação |
|---|---|---|
| `IA_NAO_CONFIGURADA` no chat | variáveis `DOCUMIND_AI_*` ausentes | preencher `backend/.env` e reiniciar |
| Backend não sobe: "DOCUMIND_JWT_SECRET obrigatoria" | segredo ausente/curto | rodar setup ou definir manualmente |
| `5432` refused | Postgres parado | serviços do Windows / `pg_ctl start` |
| Flyway falha cria índice HNSW | pgvector ausente | rodar `scripts/install-pgvector.ps1` (admin) e `CREATE EXTENSION vector` |
| `IA provider timeout` no processamento | Ollama dormindo/pesado | `ollama run llama3.2:3b` para pré-carregar; subir timeout |
| CORS no front | origem diferente da permitida | ajustar `DOCUMIND_CORS_ORIGINS` |
| 401 em tudo após login | relógio atrasado (exp) / token expirado | relogar; conferir hora da máquina |

---

## 20. Licença

MIT — veja [LICENSE](LICENSE). Projeto de portfólio: use, adapte e mostre seu trabalho.
Créditos: OpenAI-compatible API contract, pgvector community build para Windows.
