# Liga/desliga o ambiente de teste do GCP (terraform-gcp/): VM do Postgres + Cloud Run.
# Uso: .\deploy\gcp-power.ps1 off | on | status
#
# A VM e2-micro fica no Always Free do GCP quando em us-central1 (30 GB disco padrao).
# Ao parar a instancia ('off'), o compute cessa de rodar.
# Os servicos do Cloud Run (estado-api e estado-ai-agent) ja escalam para zero (min=0, max=1),
# mas ao desligar ('off') o max-instances e reduzido para 0 de proposito: assim uma requisicao
# externa nao tenta acordar o backend enquanto o banco de dados estiver desligado.
# Ao ligar ('on'), a VM e iniciada, o script de startup sobe o Postgres e o Cloud Run
# volta a permitir max-instances=1.
param(
    [Parameter(Mandatory = $true)][ValidateSet('on', 'off', 'status')][string]$Action
)

$ErrorActionPreference = 'Stop'

# Localiza o gcloud CLI
$Gcloud = 'gcloud'
if (-not (Get-Command $Gcloud -ErrorAction SilentlyContinue)) {
    $GcloudCmd = "C:\Users\Desenvolvimento\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
    if (Test-Path $GcloudCmd) {
        $Gcloud = $GcloudCmd
    }
}

$Project = 'estado-teste-1afmel'
$Zone = 'us-central1-a'
$Region = 'us-central1'
$VmName = 'estado-postgres'
$Apps = @('estado-api', 'estado-ai-agent')
$ApiApp = 'estado-api'

function Write-Step($message) {
    Write-Host "[$(Get-Date -Format HH:mm:ss)] $message"
}

function Get-VmState {
    $state = & $Gcloud compute instances describe $VmName --zone $Zone --project $Project --format="value(status)" 2>&1
    return $state.Trim()
}

function Get-RunMaxScale($app) {
    try {
        $json = & $Gcloud run services describe $app --region $Region --project $Project --format="json" 2>&1 | ConvertFrom-Json
        $max = $json.spec.template.metadata.annotations.'autoscaling.knative.dev/maxScale'
        if ($max -eq '0') { return 'desativado (max=0)' }
        if ($max) { return "ativo (max=$max)" }
        return 'ativo (padrao)'
    } catch {
        return 'desconhecido'
    }
}

function Wait-VmState($target) {
    $started = Get-Date
    do {
        $state = Get-VmState
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        Write-Step "VM ${VmName}: $state (${elapsed}s)"
        if ($state -eq $target) { return }
        Start-Sleep -Seconds 5
    } while ($true)
}

function Wait-Api {
    $url = & $Gcloud run services describe $ApiApp --region $Region --project $Project --format="value(status.url)" 2>&1
    if (-not $url) {
        Write-Step "Aviso: nao foi possivel obter a URL publica de $ApiApp"
        return
    }
    $infoUrl = "$($url.Trim())/actuator/info"
    $started = Get-Date
    while (((Get-Date) - $started).TotalSeconds -lt 300) {
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        try {
            $code = (Invoke-WebRequest -Uri $infoUrl -UseBasicParsing -TimeoutSec 30).StatusCode
            Write-Step "API respondeu $code (${elapsed}s). Tudo no ar."
            return
        }
        catch {
            Write-Step "API ainda subindo ou acordando (${elapsed}s)..."
        }
        Start-Sleep -Seconds 10
    }
    Write-Step "Aviso: a API nao respondeu em 5 min. Confira os logs: gcloud run services logs read $ApiApp --region $Region --project $Project"
}

switch ($Action) {
    'off' {
        Write-Step "VM estado atual: $(Get-VmState). Pausando Cloud Run para evitar acordar sem banco..."
        foreach ($app in $Apps) {
            Write-Step "Limitando $app para max-instances=0..."
            & $Gcloud run services update $app --region $Region --project $Project --max-instances 0 --quiet 2>&1 | Out-Null
            Write-Step "${app}: $(Get-RunMaxScale $app)"
        }

        if ((Get-VmState) -ne 'TERMINATED') {
            Write-Step "Parando VM $VmName..."
            & $Gcloud compute instances stop $VmName --zone $Zone --project $Project --quiet 2>&1 | Out-Null
        }
        Wait-VmState 'TERMINATED'
        Write-Step "Desligado com sucesso no GCP. VM parada e Cloud Run pausado (sem chamadas orfas ao banco)."
    }
    'on' {
        Write-Step "Iniciando VM $VmName (Postgres)..."
        if ((Get-VmState) -ne 'RUNNING') {
            & $Gcloud compute instances start $VmName --zone $Zone --project $Project --quiet 2>&1 | Out-Null
        }
        Wait-VmState 'RUNNING'

        Write-Step "Aguardando inicializacao do Docker e Postgres (30s)..."
        Start-Sleep -Seconds 30

        Write-Step "Reativando Cloud Run (max-instances=1)..."
        # ai-agent primeiro, depois backend
        foreach ($app in @('estado-ai-agent', 'estado-api')) {
            & $Gcloud run services update $app --region $Region --project $Project --max-instances 1 --quiet 2>&1 | Out-Null
            Write-Step "${app}: $(Get-RunMaxScale $app)"
        }

        Write-Step "Aguardando a API responder..."
        Wait-Api
    }
    'status' {
        Write-Host "gcp-project : $Project"
        Write-Host "postgres-vm : $(Get-VmState)"
        foreach ($app in $Apps) {
            Write-Host "${app}  : $(Get-RunMaxScale $app)"
        }
    }
}
