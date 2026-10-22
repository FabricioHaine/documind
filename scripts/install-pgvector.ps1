# DocuMind - Instalador do pgvector v0.8.7 para PostgreSQL 18 (Windows)
# Executar como administrador. Copia os binaries/lib/headers da extensao para o diretorio do PostgreSQL.
# Origem do build: https://github.com/andreiramani/pgvector_pgsql_windows (pgvector v0.8.7, PG18, compilado nativamente em Windows)

param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDir,                                  # pasta extraida do zip (contem lib/share/include)
    [string]$PgRoot = "C:\Program Files\PostgreSQL\18",  # raiz da instalacao do PostgreSQL
    [string]$LogPath = "$env:TEMP\documind-pgvector-install.log"
)

$ErrorActionPreference = 'Stop'
Start-Transcript -Path $LogPath -Force | Out-Null

try {
    Write-Host "Instalando pgvector de '$SourceDir' para '$PgRoot'..."

    $steps = @(
        @{ Src = "$SourceDir\lib";        Dst = "$PgRoot\lib" },
        @{ Src = "$SourceDir\share\extension"; Dst = "$PgRoot\share\extension" },
        @{ Src = "$SourceDir\include\server\extension\vector"; Dst = "$PgRoot\include\server\extension\vector" }
    )

    foreach ($step in $steps) {
        if (-not (Test-Path $step.Src)) { throw "Origem nao encontrada: $($step.Src)" }
        New-Item -ItemType Directory -Force -Path $step.Dst | Out-Null
        Copy-Item -Path "$($step.Src)\*" -Destination $step.Dst -Recurse -Force
        Write-Host "  Copiados: $($step.Src) -> $($step.Dst)"
    }

    # Verificacoes
    if (-not (Test-Path "$PgRoot\lib\vector.dll")) { throw "vector.dll nao foi copiado" }
    if (-not (Test-Path "$PgRoot\share\extension\vector.control")) { throw "vector.control nao foi copiado" }
    if (-not (Test-Path "$PgRoot\share\extension\vector--0.8.7.sql")) { throw "vector--0.8.7.sql nao foi copiado" }

    Write-Host "pgvector instalado com sucesso em $PgRoot"
    Write-Host "LOG CONCLUIDO OK"
}
catch {
    Write-Host "ERRO: $($_.Exception.Message)"
    Write-Host "LOG CONCLUIDO FALHA"
}
finally {
    Stop-Transcript | Out-Null
}
