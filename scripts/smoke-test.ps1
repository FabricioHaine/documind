# DocuMind — Smoke test E2E (cenarios do README §1 / §16)
# Uso: powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
# Fluxo: health -> registro (2 usuarios) -> upload TXT -> polling ate CONCLUIDO
#        -> detalhe -> colecao + vinculo -> conversa escopo -> pergunta RAG (OK + citacoes)
#        -> pergunta SEM_EVIDENCIA -> busca semantica -> isolamento entre usuarios.

$ErrorActionPreference = 'Stop'
$app = 'http://localhost:8080/api'
$script:pass = 0; $script:fail = 0

if (-not ('System.Net.Http.HttpClient' -as [type])) { Add-Type -AssemblyName System.Net.Http }
$script:http = [System.Net.Http.HttpClient]::new()

function Step {
    param([string]$Nome, [bool]$Ok, [string]$Detalhe)
    if ($Ok) { $script:pass++; Write-Host ("[OK]    {0}  {1}" -f $Nome, $Detalhe) -ForegroundColor Green }
    else     { $script:fail++; Write-Host ("[FALHA] {0}  {1}" -f $Nome, $Detalhe) -ForegroundColor Yellow }
}

function Invoke-Api {
    param(
        [string]$Method, [string]$Path,
        $Body = $null, [string]$Token = '', [int]$TimeoutSec = 90
    )
    $req = [System.Net.Http.HttpRequestMessage]::new($Method, ($app + $Path))
    if ($Token) { $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    if ($null -ne $Body) {
        $req.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 6), [System.Text.Encoding]::UTF8, 'application/json')
    }
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSec))
    try {
        $resp = $script:http.SendAsync($req, $cts.Token).GetAwaiter().GetResult()
        $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $txt = [System.Text.Encoding]::UTF8.GetString($bytes)
        $parsed = $null; try { $parsed = $txt | ConvertFrom-Json } catch {}
        [pscustomobject]@{ Status = [int]$resp.StatusCode; Ok = $resp.IsSuccessStatusCode; Data = $parsed; Raw = $txt }
    } catch {
        [pscustomobject]@{ Status = 0; Ok = $false; Data = $null; Raw = ("ERRO DE CONEXAO: " + $_.Exception.Message) }
    }
}

function Invoke-ApiUpload {
    param([string]$Path, [string]$FilePath, [string]$Token, [int]$TimeoutSec = 90)
    $req = [System.Net.Http.HttpRequestMessage]::new('POST', ($app + $Path))
    $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    $mp = [System.Net.Http.MultipartFormDataContent]::new()
    $bytes = [System.IO.File]::ReadAllBytes($FilePath)
    $bc = [System.Net.Http.ByteArrayContent]::new($bytes)
    $bc.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/plain')
    $mp.Add($bc, 'arquivos', (Split-Path $FilePath -Leaf))
    $req.Content = $mp
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSec))
    $resp = $script:http.SendAsync($req, $cts.Token).GetAwaiter().GetResult()
    $txt = [System.Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    $parsed = $null; try { $parsed = $txt | ConvertFrom-Json } catch {}
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Ok = $resp.IsSuccessStatusCode; Data = $parsed; Raw = $txt }
}

Write-Host "=== DocuMind Smoke Test E2E ===" -ForegroundColor Cyan

# 1) Health
$h = Invoke-Api -Method 'GET' -Path '/health' -TimeoutSec 20
$iaOk = [bool]$h.Data.ia.configurado; $bancoOk = ($h.Data.banco.estado -eq 'UP')
Step 'Health' ($bancoOk -and $iaOk) ("banco={0} ia.configurado={1} chat={2}" -f $h.Data.banco.estado, $h.Data.ia.configurado, $h.Data.ia.modeloChat)

# 2) Registro (2 usuarios)
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$senha = 'SenhaForte#2026'
$r1 = Invoke-Api -Method 'POST' -Path '/auth/register' -Body @{ nome = 'Usuario Smoke 1'; email = "smoke1.$stamp@documind.test"; senha = $senha } -TimeoutSec 30
$tok1 = $r1.Data.token
Step 'Registro usuario 1' ($r1.Ok -and $tok1) ("status=$($r1.Status) email=smoke1.$stamp@documind.test")
$r2 = Invoke-Api -Method 'POST' -Path '/auth/register' -Body @{ nome = 'Usuario Smoke 2'; email = "smoke2.$stamp@documind.test"; senha = $senha } -TimeoutSec 30
$tok2 = $r2.Data.token
Step 'Registro usuario 2' ($r2.Ok -and $tok2) "status=$($r2.Status)"

# 3) Documento de teste (conteudo conhecido)
$dir = Join-Path $env:TEMP 'documind-smoke'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$docPath = Join-Path $dir 'politica-ferias-aurora.txt'
@'
POLITICA DE FERIAS DA EMPRESA AURORA

1. Periodo aquisitivo - O funcionario tem direito a 30 dias de ferias a cada 12 meses trabalhados.

2. Venda de ferias - O funcionario pode vender (abono pecuniario) ate 10 dos 30 dias de ferias remunerados.

3. Programacao - O pedido de ferias deve ser comunicado ao RH com no minimo 30 dias de antecedencia.

4. Trabalho remoto - A empresa permite ate 2 dias de home office por semana.

5. Faltas justificadas - Ate 5 abonos por ano nao reduzem o periodo aquisitivo.

6. Pagamento - O abono de ferias e pago junto com a primeira parcela das ferias.
'@ | Set-Content -Path $docPath -Encoding UTF8

# 4) Upload
$up = Invoke-ApiUpload -Path '/documents' -FilePath $docPath -Token $tok1 -TimeoutSec 90
$docId = $null
try { $docId = @($up.Data)[0].id } catch {}
Step 'Upload TXT' ($up.Ok -and $docId) ("status=$($up.Status) documentoId=$docId")

if ($docId) {
    # 5) Polling ate CONCLUIDO (max ~120s)
    $estado = 'AGUARDANDO_PROCESSAMENTO'; $trechos = 0; $msgErro = $null
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $st = Invoke-Api -Method 'GET' -Path "/documents/$docId/status" -Token $tok1 -TimeoutSec 30
        if ($st.Data) { $estado = $st.Data.estado; $trechos = $st.Data.quantTrechos; $msgErro = $st.Data.mensagemErro }
        if ($estado -in @('CONCLUIDO', 'FALHOU')) { break }
    }
    Step 'Processamento CONCLUIDO' ($estado -eq 'CONCLUIDO' -and $trechos -ge 1) ("estado=$estado trechos=$trechos msgErro=$msgErro")

    # 6) Detalhe (modelo de embedding)
    $det = Invoke-Api -Method 'GET' -Path "/documents/$docId" -Token $tok1 -TimeoutSec 30
    $embOk = ($det.Data.modeloEmbedding -eq $h.Data.ia.modeloEmbeddings)
    Step 'Detalhe / modeloEmbedding' $embOk ("modeloEmbedding=$($det.Data.modeloEmbedding) esperado=$($h.Data.ia.modeloEmbeddings)")

    # 7) Colecao + vinculo
    $col = Invoke-Api -Method 'POST' -Path '/collections' -Body @{ nome = "Politicas Internas Smoke $stamp"; descricao = 'Colecao do smoke test' } -Token $tok1 -TimeoutSec 30
    $colId = $col.Data.id
    $vinc = Invoke-Api -Method 'POST' -Path "/collections/$colId/documents" -Body @{ documentoId = $docId } -Token $tok1 -TimeoutSec 30
    Step 'Colecao + vinculo' ($col.Ok -and $colId -and $vinc.Ok) ("colecaoId=$colId statusVinculo=$($vinc.Status)")

    # 8) Conversa no escopo da colecao
    $conv = Invoke-Api -Method 'POST' -Path '/conversations' -Body @{ titulo = "Smoke $stamp"; colecaoId = $colId } -Token $tok1 -TimeoutSec 30
    $convId = $conv.Data.id
    Step 'Conversa com escopo' ($conv.Ok -and $convId) "status=$($conv.Status) conversaId=$convId"

    # 9) Pergunta RAG com evidencia (pode demorar ~45-90s com llama3.2:3b)
    Write-Host "... aguardando resposta RAG (modelo local, pode levar 1-2 min) ..." -ForegroundColor DarkGray
    $chat = Invoke-Api -Method 'POST' -Path "/conversations/$convId/messages" `
        -Body @{ conteudo = 'Quantos dias de ferias o funcionario tem direito e quantos dias ele pode vender?' } `
        -Token $tok1 -TimeoutSec 300
    $citacoes = @($chat.Data.citacoes).Count
    Step 'Pergunta RAG (OK + citacoes)' ($chat.Ok -and $chat.Data.estado -eq 'OK' -and $citacoes -ge 1) ("estado=$($chat.Data.estado) citacoes=$citacoes metadados.modelo=$($chat.Data.metadados.modelo)")
    if ($chat.Data.conteudo) { Write-Host ("        Resposta: {0}" -f $chat.Data.conteudo) -ForegroundColor White }
    if ($citacoes -ge 1) {
        $c1 = $chat.Data.citacoes[0]
        Write-Host ("        Citacao [1]: doc={0} trecho={1} conteudo={2}..." -f $c1.documentoNome, $c1.indiceTrecho, ([string]$c1.conteudoTrecho).Substring(0, [Math]::Min(60, ([string]$c1.conteudoTrecho).Length))) -ForegroundColor DarkGray
    }

    # 10) Pergunta SEM evidencia
    $neg = Invoke-Api -Method 'POST' -Path "/conversations/$convId/messages" `
        -Body @{ conteudo = 'Qual a politica da empresa sobre financiamento imobiliario?' } `
        -Token $tok1 -TimeoutSec 300
    Step 'Pergunta SEM_EVIDENCIA' ($neg.Data.estado -eq 'SEM_EVIDENCIA') ("estado=$($neg.Data.estado) conteudo=$($neg.Data.conteudo)")

    # 11) Busca semantica
    $sem = Invoke-Api -Method 'POST' -Path '/search/semantic' `
        -Body @{ pergunta = 'venda de dias de ferias e abono pecuniario'; colecaoId = $colId } `
        -Token $tok1 -TimeoutSec 120
    $res = @($sem.Data.resultados).Count
    $sim = if ($res -ge 1) { $sem.Data.resultados[0].similaridade } else { $null }
    Step 'Busca semantica' ($sem.Ok -and $res -ge 1) ("resultados=$res topSimilaridade=$sim")

    # 12) Isolamento entre usuarios (usuario 2 nao ve nada do usuario 1)
    $intr = Invoke-Api -Method 'GET' -Path "/documents/$docId" -Token $tok2 -TimeoutSec 30
    $lista = Invoke-Api -Method 'GET' -Path '/documents' -Token $tok2 -TimeoutSec 30
    $arr = @()
    foreach ($p in $lista.Data.PSObject.Properties) {
        if ($p.Value -is [System.Array]) { $arr = @($p.Value); break }
    }
    Step 'Isolamento (403 + lista vazia)' ($intr.Status -in @(403, 404) -and $arr.Count -eq 0) ("statusIntruso=$($intr.Status) itensListaUsuario2=$($arr.Count)")
} else {
    Step 'Processamento CONCLUIDO' $false 'upload falhou - etapas dependentes puladas'
}

Write-Host ""
Write-Host ("=== RESULTADO: {0} OK / {1} FALHA ===" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Yellow' })
if ($tok1) {
    Write-Host ""
    Write-Host 'Para explorar na UI (http://localhost:4200):' -ForegroundColor Cyan
    Write-Host ("  login: smoke1.$stamp@documind.test   senha: $senha") -ForegroundColor Cyan
}
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
