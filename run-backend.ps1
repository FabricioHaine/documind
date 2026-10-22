# DocuMind — sobe o backend com as variaveis de backend/.env carregadas.
# Uso:  .\run-backend.ps1      (na raiz do projeto)

$ErrorActionPreference = "Stop"

$backendDir = Join-Path $PSScriptRoot "backend"
$envFile = Join-Path $backendDir ".env"

if (Test-Path $envFile) {
    Write-Host "Carregando variaveis de $envFile"
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $chave = $Matches[1]
            $valor = $Matches[2].Trim()
            Set-Item -Path "Env:$chave" -Value $valor
        }
    }
} else {
    Write-Host "[AVISO] backend/.env nao encontrado. Rode scripts/setup-database.ps1 primeiro." -ForegroundColor Yellow
    Write-Host "Sem IA configurada, a API sobe porem os endpoints de RAG respondem 503." -ForegroundColor Yellow
}

Write-Host "> Iniciando DocuMind backend (http://localhost:8080)..."
Write-Host "> Swagger: http://localhost:8080/swagger-ui.html"
Set-Location $backendDir
cmd /c "mvnw.cmd spring-boot:run"
