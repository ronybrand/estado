# Liga/desliga o ambiente de teste do Azure (terraform-azure/): Postgres + Container Apps.
# Uso: .\azure-power.ps1 off | on | status
#
# So o Postgres Flexible Server cobra por hora de computacao; parado, continua
# cobrando apenas o armazenamento. Os Container Apps ja escalam a zero, mas sao
# desativados no 'off' de proposito: uma requisicao perdida acordaria o backend
# com o banco parado e ele ficaria subindo e falhando.
#
# ATENCAO: o Azure religa um Postgres parado sozinho depois de 7 dias (limite da
# plataforma, sem opcao de desligar). Se o ambiente ficar parado por mais que
# isso, rode 'off' de novo.
param(
    [Parameter(Mandatory = $true)][ValidateSet('on', 'off', 'status')][string]$Action
)

$ResourceGroup = 'estado-rg'
$Apps = @('estado-api', 'estado-ai-agent')
$ApiApp = 'estado-api'

# O nome do servidor tem sufixo aleatorio (random_string no Terraform): descobrir
# pelo resource group evita ficar desatualizado se o ambiente for recriado.
$DbName = az postgres flexible-server list --resource-group $ResourceGroup --query '[0].name' --output tsv --only-show-errors
if (-not $DbName) { throw "Nenhum Postgres encontrado em '$ResourceGroup'. O ambiente foi criado (terraform apply)?" }

function Get-DbState {
    az postgres flexible-server show --resource-group $ResourceGroup --name $DbName --query state --output tsv --only-show-errors
}

# Em modo de revision unica, o app so tem a ultima revision ativa.
function Get-LatestRevision($app) {
    az containerapp show --resource-group $ResourceGroup --name $app --query properties.latestRevisionName --output tsv --only-show-errors
}

function Get-AppState($app) {
    $revision = Get-LatestRevision $app
    $active = az containerapp revision show --resource-group $ResourceGroup --name $app --revision $revision --query properties.active --output tsv --only-show-errors
    if ($active -eq 'true') { 'ativo' } else { 'desativado' }
}

function Write-Step($message) {
    Write-Host "[$(Get-Date -Format HH:mm:ss)] $message"
}

function Wait-DbState($target) {
    $started = Get-Date
    do {
        $state = Get-DbState
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        Write-Step "postgres: $state (${elapsed}s)"
        if ($state -eq $target) { return }
        Start-Sleep -Seconds 10
    } while ($true)
}

# /actuator/health fica na porta de gerenciamento (8081), fora do ingress; o
# /actuator/info e o publico. O primeiro acesso acorda o scale a zero.
function Wait-Api {
    $fqdn = az containerapp show --resource-group $ResourceGroup --name $ApiApp --query properties.configuration.ingress.fqdn --output tsv --only-show-errors
    $url = "https://$fqdn/actuator/info"
    $started = Get-Date
    while (((Get-Date) - $started).TotalSeconds -lt 300) {
        $elapsed = [int]((Get-Date) - $started).TotalSeconds
        try {
            $code = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30).StatusCode
            Write-Step "API respondeu $code (${elapsed}s). Tudo no ar."
            return
        }
        catch { Write-Step "API ainda nao respondeu (${elapsed}s)..." }
        Start-Sleep -Seconds 10
    }
    Write-Step "Aviso: a API nao respondeu em 5 min. Confira os logs: az containerapp logs show -g $ResourceGroup -n $ApiApp --type console"
}

switch ($Action) {
    'off' {
        Write-Step "Postgres: $(Get-DbState). Desativando os apps antes de parar o banco..."
        foreach ($app in $Apps) {
            if ((Get-AppState $app) -eq 'ativo') {
                az containerapp revision deactivate --resource-group $ResourceGroup --name $app --revision (Get-LatestRevision $app) --only-show-errors | Out-Null
            }
            Write-Step "${app}: $(Get-AppState $app)"
        }
        if ((Get-DbState) -ne 'Stopped') {
            az postgres flexible-server stop --resource-group $ResourceGroup --name $DbName --only-show-errors | Out-Null
        }
        Wait-DbState 'Stopped'
        Write-Step "Desligado. Armazenamento do banco continua cobrado; religa sozinho em 7 dias."
    }
    'on' {
        Write-Step "Postgres: $(Get-DbState). Enviando comando de inicio..."
        if ((Get-DbState) -ne 'Ready') {
            az postgres flexible-server start --resource-group $ResourceGroup --name $DbName --only-show-errors | Out-Null
        }
        Wait-DbState 'Ready'
        # ai-agent primeiro: o backend o chama, e ele escala a zero sozinho.
        foreach ($app in @($Apps | Sort-Object { $_ -eq $ApiApp })) {
            if ((Get-AppState $app) -ne 'ativo') {
                az containerapp revision activate --resource-group $ResourceGroup --name $app --revision (Get-LatestRevision $app) --only-show-errors | Out-Null
            }
            Write-Step "${app}: $(Get-AppState $app)"
        }
        Write-Step "Aguardando a API responder..."
        Wait-Api
    }
    'status' {
        Write-Host "postgres: $(Get-DbState)"
        foreach ($app in $Apps) { Write-Host "${app}: $(Get-AppState $app)" }
    }
}
