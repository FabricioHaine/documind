# DocuMind — Preparacao do banco de dados (PostgreSQL 18 + pgvector)
# Cria usuario/database `documind`, habilita a extensao `vector` e gera o
# backend/.env local (NUNCA versionado). Segredos ficam fora do repositorio.
#
# Uso (DE UM TERMINAL, nao por duplo-clique):
#   powershell -ExecutionPolicy Bypass -File scripts/setup-database.ps1
# Pre-requisitos: PostgreSQL 18 rodando, pgvector instalado
# (scripts/install-pgvector.ps1) e Ollama com os modelos baixados.

$ErrorActionPreference = "Stop"

$PgRoot = "C:\Program Files\PostgreSQL\18"
$Psql   = Join-Path $PgRoot "bin\psql.exe"
$PgUser = "postgres"
$DbUser = "documind"
$DbName = "documind"

function Pausar {
    Write-Host ""
    Read-Host "Pressione ENTER para fechar"
}

if (-not (Test-Path $Psql)) {
    Write-Host ""
    Write-Host "[ERRO] psql nao encontrado em: $Psql" -ForegroundColor Red
    Write-Host "Ajuste a variavel `$PgRoot no topo deste script para a raiz do PostgreSQL."
    Pausar
    exit 1
}

# ---------------------------------------------------------------------
# Servico do PostgreSQL precisa estar rodando.
# ---------------------------------------------------------------------
$servico = Get-Service -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like 'postgresql*' -and $_.Name -like '*18' } |
    Select-Object -First 1

if ($servico -and $servico.Status -ne 'Running') {
    Write-Host "> Servico '$($servico.Name)' parado. Tentando iniciar..." -ForegroundColor Yellow
    try {
        Start-Service $servico.Name -ErrorAction Stop
        Write-Host "> Servico iniciado."
    } catch {
        Write-Host "[ERRO] Nao consegui iniciar o servico '$($servico.Name)'. Inicie-o manualmente (services.msc)." -ForegroundColor Red
        Pausar
        exit 1
    }
}

# Escapa aspas simples para uso seguro dentro do SQL.
function Esc-Sql([string]$Valor) {
    return $Valor.Replace("'", "''")
}

function Invoke-Psql([string]$Db, [string]$Sql, [pscredential]$Credential) {
    $env:PGPASSWORD = $Credential.GetNetworkCredential().Password
    try {
        & $Psql -U $PgUser -h localhost -p 5432 -d $Db -v ON_ERROR_STOP=1 -c $Sql
        if ($LASTEXITCODE -ne 0) {
            throw "psql saiu com codigo $LASTEXITCODE"
        }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Test-DbExists([pscredential]$Credential) {
    $env:PGPASSWORD = $Credential.GetNetworkCredential().Password
    try {
        $existe = & $Psql -U $PgUser -h localhost -p 5432 -d postgres -t -A `
            -c "SELECT (SELECT 1) WHERE EXISTS (SELECT 1 FROM pg_database WHERE datname = '$DbName');" 2>$null
        return ($existe -match '1')
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "=== DocuMind: preparacao de banco de dados ===" -ForegroundColor Cyan
Write-Host "Vou pedir 2 senhas: (1) superusuario postgres, (2) novo usuario app '$DbUser'."

try {
    $senhaPostgres = Read-Host "1/2 - Senha do '$PgUser' do PostgreSQL" -AsSecureString
    $credAdmin = New-Object System.Management.Automation.PSCredential($PgUser, $senhaPostgres)

    Write-Host "2/2 - Senha do NOVO usuario '$DbUser' (guarde: ela vira variavel no .env)" -ForegroundColor Yellow
    $senhaApp = Read-Host "    Senha para '$DbUser'" -AsSecureString

    # Decode seguro de SecureString para uso pontual.
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($senhaApp)
    $senhaAppTexto = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

    if ($senhaAppTexto.Length -lt 8) {
        throw "A senha do usuario '$DbUser' deve ter pelo menos 8 caracteres."
    }

    $senhaAppSql = Esc-Sql $senhaAppTexto

    Write-Host "> Testando conexao com o PostgreSQL (postgres@localhost:5432)..."
    $jaExiste = Test-DbExists $credAdmin
    Write-Host "> Conexao OK. Database '$DbName' $(if ($jaExiste) { 'ja existe' } else { 'nao existe ainda' })."

    $doTemplate = @'
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{USER}') THEN
        CREATE ROLE {USER} LOGIN PASSWORD '{PASS}';
    ELSE
        ALTER ROLE {USER} WITH LOGIN PASSWORD '{PASS}';
    END IF;
END
$$;
'@
    $doSql = $doTemplate.Replace('{USER}', $DbUser).Replace('{PASS}', $senhaAppSql)

    Write-Host "> Criando/atualizando role '$DbUser'..."
    Invoke-Psql postgres $doSql $credAdmin

    if (-not $jaExiste) {
        Write-Host "> Criando database '$DbName' (owner: $DbUser)..."
        Invoke-Psql postgres "CREATE DATABASE $DbName OWNER $DbUser;" $credAdmin
    } else {
        Write-Host "> Database '$DbName' ja existe (owner garantido)."
        Invoke-Psql postgres "ALTER DATABASE $DbName OWNER TO $DbUser;" $credAdmin
    }

    Write-Host "> Garantindo extensao pgvector..."
    Invoke-Psql $DbName "CREATE EXTENSION IF NOT EXISTS vector;" $credAdmin

    Write-Host "> Garantindo permissoes no schema public..."
    Invoke-Psql $DbName "GRANT ALL ON SCHEMA public TO $DbUser;" $credAdmin

    # -----------------------------------------------------------------
    # Segredo JWT forte e aleatorio (48 bytes -> base64, ~64 chars)
    # -----------------------------------------------------------------
    $bytesSegredo = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytesSegredo)
    } finally {
        $rng.Dispose()
    }
    $segredoJwt = [Convert]::ToBase64String($bytesSegredo)

    # -----------------------------------------------------------------
    # backend/.env local (ignorado pelo git) — UTF-8 SEM BOM
    # -----------------------------------------------------------------
    $envPath = Join-Path $PSScriptRoot "..\backend\.env"
    $conteudo = @(
        "# DocuMind - config local (NAO versionar)",
        "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/$DbName",
        "SPRING_DATASOURCE_USERNAME=$DbUser",
        "SPRING_DATASOURCE_PASSWORD=$senhaAppTexto",
        "",
        "DOCUMIND_AI_BASE_URL=http://localhost:11434/v1",
        "DOCUMIND_AI_CHAT_MODEL=llama3.2:3b",
        "DOCUMIND_AI_EMBEDDING_MODEL=nomic-embed-text",
        "DOCUMIND_AI_EMBEDDING_DIMENSIONS=768",
        "DOCUMIND_AI_REQUEST_TIMEOUT_SECONDS=300",
        "DOCUMIND_AI_API_KEY=",
        "",
        "DOCUMIND_JWT_SECRET=$segredoJwt",
        ""
    )
    $utf8SemBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($envPath, $conteudo, $utf8SemBom)

    # Verificacao: o arquivo PRECISA existir ao final.
    if (-not (Test-Path $envPath)) {
        throw "backend/.env nao foi gravado (caminho: $envPath)"
    }

    Write-Host ""
    Write-Host "[OK] Tudo pronto!" -ForegroundColor Green
    Write-Host "     Database : '$DbName' (role '$DbUser', extensao vector ativa)"
    Write-Host "     .env     : $envPath"
    Write-Host "Proximo passo: .\run-backend.ps1   (a partir da RAIZ do projeto)"
    Pausar
} catch {
    Write-Host ""
    Write-Host "[FALHA] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Causas mais comuns:" -ForegroundColor Yellow
    Write-Host "  1) Senha do postgres errada          -> rode de novo com cuidado;"
    Write-Host "  2) Servico PostgreSQL parado        -> services.msc -> iniciar 'postgresql-x64-18';"
    Write-Host "  3) pgvector nao instalado           -> rode scripts\install-pgvector.ps1 como ADMIN;"
    Write-Host "  4) PostgreSQL em outra porta/pasta  -> ajuste `$PgRoot/-p 5432 no topo do script."
    Pausar
    exit 1
}
