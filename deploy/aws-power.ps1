# Liga/desliga a EC2 do portfolio (a unica parte da AWS com custo relevante por hora).
# Uso: .\aws-power.ps1 off | on | status
#
# Parar a instancia (nao terminar) preserva disco, Elastic IP e DNS, entao nada
# precisa ser reconfigurado ao ligar. O Elastic IP e o EBS continuam cobrados
# parados (~US$ 4-5/mes); so a hora de computacao deixa de ser cobrada.
param(
    [Parameter(Mandatory = $true)][ValidateSet('on', 'off', 'status')][string]$Action
)

$InstanceId = 'i-0208b4902902e7dd6'
$Region = 'us-east-1'

# Opcional: silencia o alerta de backup do Grafana enquanto a instancia esta parada
# (sem a EC2 ligada o backup diario nao roda e o dead man's switch dispara, ver
# ADR 0012). Sem as variaveis abaixo, o silencio e ignorado.
#   GRAFANA_URL              ex: https://SEU-STACK.grafana.net
#   GRAFANA_TOKEN            service account token com permissao de editar silences
#   GRAFANA_BACKUP_ALERTNAME nome da regra de backup (padrao: estado-backup)
$SilenceMarker = 'aws-power.ps1: instancia desligada'
$SilenceDays = 30

function Get-State {
    aws ec2 describe-instances --region $Region --instance-ids $InstanceId `
        --query 'Reservations[0].Instances[0].State.Name' --output text
}

function Write-Step($message) {
    Write-Host "[$(Get-Date -Format HH:mm:ss)] $message"
}

# `aws ec2 wait` fica mudo ate terminar; consultar o estado em loop mostra o andamento.
function Wait-State($target) {
    $started = Get-Date
    do {
        $state = Get-State
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        Write-Step "estado: $state (${elapsed}s)"
        if ($state -eq $target) { return }
        Start-Sleep -Seconds 5
    } while ($true)
}

# /actuator/health e bloqueado no Caddy de proposito; /actuator/info e o publico.
# Postgres/Caddy sobem sozinhos (restart: unless-stopped), a app pode demorar mais.
function Wait-Api {
    $url = 'https://api.ronybrand.click/actuator/info'
    $started = Get-Date
    while (((Get-Date) - $started).TotalSeconds -lt 300) {
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        try {
            $code = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10).StatusCode
            Write-Step "API respondeu $code (${elapsed}s). Tudo no ar."
            return
        }
        catch { Write-Step "API ainda nao respondeu (${elapsed}s)..." }
        Start-Sleep -Seconds 10
    }
    Write-Step "Aviso: a API nao respondeu em 5 min. Confira os containers via SSM."
}

function Invoke-Grafana($method, $path, $body = $null) {
    $params = @{
        Method  = $method
        Uri     = "$($env:GRAFANA_URL.TrimEnd('/'))/api/alertmanager/grafana/api/v2/$path"
        Headers = @{ Authorization = "Bearer $env:GRAFANA_TOKEN" }
    }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 5); $params.ContentType = 'application/json' }
    Invoke-RestMethod @params
}

function Set-BackupAlertSilence($silence) {
    if (-not $env:GRAFANA_URL -or -not $env:GRAFANA_TOKEN) {
        Write-Step "GRAFANA_URL/GRAFANA_TOKEN nao definidos: alerta de backup do Grafana nao foi alterado."
        return
    }
    try {
        $active = Invoke-Grafana Get 'silences' | Where-Object { $_.comment -eq $SilenceMarker -and $_.status.state -ne 'expired' }
        if (-not $silence) {
            foreach ($s in $active) { Invoke-Grafana Delete "silence/$($s.id)" | Out-Null }
            Write-Step "Silencio do alerta de backup removido ($(@($active).Count))."
            return
        }
        if ($active) { Write-Step 'Silencio do alerta de backup ja existe.'; return }
        $alertName = if ($env:GRAFANA_BACKUP_ALERTNAME) { $env:GRAFANA_BACKUP_ALERTNAME } else { 'estado-backup' }
        Invoke-Grafana Post 'silences' @{
            matchers  = @(@{ name = 'alertname'; value = $alertName; isRegex = $false; isEqual = $true })
            startsAt  = (Get-Date).ToUniversalTime().ToString('o')
            endsAt    = (Get-Date).AddDays($SilenceDays).ToUniversalTime().ToString('o')
            createdBy = 'aws-power.ps1'
            comment   = $SilenceMarker
        } | Out-Null
        Write-Step "Alerta '$alertName' silenciado por $SilenceDays dias (removido no 'on')."
    }
    catch {
        Write-Step "Aviso: nao consegui alterar o silencio no Grafana: $($_.Exception.Message)"
    }
}

switch ($Action) {
    'off' {
        Write-Step "Estado atual: $(Get-State). Enviando comando de parada..."
        aws ec2 stop-instances --region $Region --instance-ids $InstanceId | Out-Null
        Wait-State 'stopped'
        Write-Step "Desligada."
        Set-BackupAlertSilence $true
    }
    'on' {
        Set-BackupAlertSilence $false
        Write-Step "Estado atual: $(Get-State). Enviando comando de inicio..."
        aws ec2 start-instances --region $Region --instance-ids $InstanceId | Out-Null
        Wait-State 'running'
        Write-Step "Instancia ligada. Aguardando a API responder..."
        Wait-Api
    }
    'status' { Write-Host (Get-State) }
}
