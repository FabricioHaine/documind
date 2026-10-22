-- =====================================================================
-- DocuMind - Schema inicial
-- Vetores sao armazenados no tipo `vector` (pgvector) com dimensao FIXA
-- (vector(768), padrao do nomic-embed-text). O pgvector EXIGE dimensao
-- explicita no coluna para criar indice HNSW; a aplicacao valida que o
-- modelo configurado emite exatamente essa dimensao. Para usar um modelo
-- de outra dimensao, escreva uma migracao nova (ALTER COLUMN ... TYPE)
-- e ajuste DOCUMIND_AI_EMBEDDING_DIMENSIONS.
-- =====================================================================

CREATE TABLE usuario (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome             VARCHAR(120) NOT NULL,
    email            VARCHAR(180) NOT NULL,
    senha_hash       VARCHAR(100) NOT NULL,
    estado           VARCHAR(12)  NOT NULL DEFAULT 'ATIVO',
    data_criacao     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- E-mail único (case-insensitive)
CREATE UNIQUE INDEX ux_usuario_email ON usuario (LOWER(email));

CREATE TABLE documento (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL REFERENCES usuario (id),
    nome_original       VARCHAR(260) NOT NULL,
    nome_armazenamento  VARCHAR(64)  NOT NULL UNIQUE,
    tipo_arquivo        VARCHAR(10)  NOT NULL,
    tipo_mime           VARCHAR(120) NOT NULL,
    tamanho_bytes       BIGINT       NOT NULL,
    total_paginas       INT          NULL,
    estado              VARCHAR(32)  NOT NULL DEFAULT 'AGUARDANDO_PROCESSAMENTO',
    mensagem_erro       VARCHAR(500) NULL,
    modelo_embedding    VARCHAR(80)  NULL,
    data_upload         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data_processamento  TIMESTAMPTZ  NULL,

    CONSTRAINT ck_documento_estado CHECK (
        estado IN ('ENVIADO', 'AGUARDANDO_PROCESSAMENTO', 'PROCESSANDO', 'CONCLUIDO', 'FALHOU')
    )
);

CREATE INDEX ix_documento_usuario_estado ON documento (usuario_id, estado);
CREATE INDEX ix_documento_usuario_upload ON documento (usuario_id, data_upload DESC);

CREATE TABLE colecao (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL REFERENCES usuario (id),
    nome             VARCHAR(120) NOT NULL,
    descricao        VARCHAR(500) NULL,
    data_criacao     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_colecao_usuario_nome ON colecao (usuario_id, LOWER(nome));

CREATE TABLE documento_colecao (
    colecao_id    BIGINT      NOT NULL REFERENCES colecao (id)   ON DELETE CASCADE,
    documento_id  BIGINT      NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    data_inclusao TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (colecao_id, documento_id)
);

-- Um documento pode pertencer a varias colecoes (associação muitos-para-muitos).
CREATE INDEX ix_documento_colecao_doc ON documento_colecao (documento_id);

CREATE TABLE trecho_documento (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento_id     BIGINT       NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    indice_trecho    INT          NOT NULL,
    conteudo         TEXT         NOT NULL,
    pagina_inicio    INT          NULL,
    pagina_fim       INT          NULL,
    metadados        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    modelo_embedding VARCHAR(80)  NOT NULL,
    embedding        vector(768),

    UNIQUE (documento_id, indice_trecho)
);

CREATE INDEX ix_trecho_documento ON trecho_documento (documento_id);
CREATE INDEX ix_trecho_documento_modelo ON trecho_documento (documento_id, modelo_embedding);

-- Indice HNSW para busca vetorial por similaridade de cosseno.
CREATE INDEX ix_trecho_embedding_hnsw ON trecho_documento USING hnsw (embedding vector_cosine_ops);

CREATE TABLE conversa (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT        NOT NULL REFERENCES usuario (id),
    colecao_id       BIGINT        NULL REFERENCES colecao (id) ON DELETE SET NULL,
    titulo           VARCHAR(160)  NOT NULL DEFAULT 'Nova conversa',
    data_criacao     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_conversa_usuario ON conversa (usuario_id, data_atualizacao DESC);

CREATE TABLE mensagem (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversa_id  BIGINT       NOT NULL REFERENCES conversa (id) ON DELETE CASCADE,
    autor        VARCHAR(12)  NOT NULL,
    conteudo     TEXT         NOT NULL,
    estado       VARCHAR(20)  NOT NULL,
    metadados    TEXT         NOT NULL DEFAULT '{}',
    data_criacao TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_mensagem_autor CHECK (autor IN ('USUARIO', 'ASSISTENTE', 'SISTEMA')),
    CONSTRAINT ck_mensagem_estado CHECK (estado IN ('OK', 'SEM_EVIDENCIA', 'ERRO'))
);

CREATE INDEX ix_mensagem_conversa ON mensagem (conversa_id, data_criacao);

CREATE TABLE citacao (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mensagem_id         BIGINT      NOT NULL REFERENCES mensagem (id) ON DELETE CASCADE,
    documento_id        BIGINT      NULL REFERENCES documento (id) ON DELETE CASCADE,
    trecho_documento_id BIGINT      NULL REFERENCES trecho_documento (id) ON DELETE SET NULL,
    referencia          INT         NOT NULL,
    indice_trecho       INT         NOT NULL,
    pagina_inicio       INT         NULL,
    pagina_fim          INT         NULL,
    conteudo_trecho     TEXT        NOT NULL
);

CREATE UNIQUE INDEX ux_citacao_msg_trecho ON citacao (mensagem_id, trecho_documento_id);
CREATE INDEX ix_citacao_documento ON citacao (documento_id);

CREATE TABLE tarefa_processamento (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento_id    BIGINT       NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',
    tentativas      INT          NOT NULL DEFAULT 0,
    ultimo_erro     VARCHAR(500) NULL,
    data_criacao    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data_inicio     TIMESTAMPTZ  NULL,
    data_conclusao  TIMESTAMPTZ  NULL,
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_tarefa_estado CHECK (estado IN ('PENDENTE', 'EM_EXECUCAO', 'CONCLUIDA', 'FALHA'))
);

CREATE INDEX ix_tarefa_estado ON tarefa_processamento (estado);
