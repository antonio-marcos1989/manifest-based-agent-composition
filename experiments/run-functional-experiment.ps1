param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$MockUrl = "http://localhost:9090",
    [switch]$SkipSlowAlaDemo
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigPath = Join-Path $ScriptDir "paper-agents.json"

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    $uri = "$BaseUrl$Path"
    if ($null -ne $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri
}

function Write-Step([string]$Message) {
    Write-Host "`n== $Message ==" -ForegroundColor Cyan
}

$config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$registered = @{}
$results = @()

Write-Step "Paper experiment: roles/types, contracts, multi-dimensional ALA, portability"

try {
    Invoke-RestMethod -Method GET -Uri "$MockUrl/health" | Out-Null
} catch {
    Write-Error "Mock agent not reachable at $MockUrl. Run: cd experiments && npm run mock-agent"
}

function Register-Agent($agentDef) {
    $manifest = [ordered]@{
        name = $agentDef.name
        description = $agentDef.description
        role = $agentDef.role
        type = $agentDef.type
        endpointUrl = "$MockUrl$($agentDef.path)"
        active = $true
        requestPayloadMode = $agentDef.requestPayloadMode
        capabilities = @($agentDef.capabilities)
    }
    foreach ($optional in @(
        "defaultModel", "systemPrompt", "forceJsonResponse",
        "inputJsonSchema", "outputJsonSchema", "inputContract", "outputContract"
    )) {
        if ($null -ne $agentDef.$optional) { $manifest[$optional] = $agentDef.$optional }
    }
    $ala = [ordered]@{}
    $config.baseAla.PSObject.Properties | ForEach-Object { $ala[$_.Name] = $_.Value }
    if ($agentDef.alaSettings) {
        $agentDef.alaSettings.PSObject.Properties | ForEach-Object { $ala[$_.Name] = $_.Value }
    }
    $manifest.alaSettings = $ala
    $saved = Invoke-Api -Method POST -Path "/api/v1/manifests" -Body $manifest
    $registered[$agentDef.key] = $saved
    Write-Host "Registered $($agentDef.name) -> $($saved.id)"
    return $saved
}

function Execute-Agent($key, [object]$Payload) {
    $agent = $registered[$key]
    $response = Invoke-Api -Method POST -Path "/api/v1/execute/$($agent.id)" -Body $Payload
    $row = [PSCustomObject]@{
        Key = $key
        Status = $response.status
        AlaCompliant = $response.alaCompliant
        LatencyMs = $response.latencyMs
        AlaViolations = ($response.alaViolations -join "; ")
    }
    $script:results += $row
    Write-Host ("  {0}: status={1} ala={2} latency={3}ms violations={4}" -f $key, $response.status, $response.alaCompliant, $response.latencyMs, $row.AlaViolations)
    return $response
}

Write-Step "1. Register manifests"
foreach ($agentDef in $config.agents) {
    if ($SkipSlowAlaDemo -and $agentDef.key -eq "classificationSlow") { continue }
    Register-Agent $agentDef | Out-Null
}

Write-Step "2. Contract validation (invalid input must fail before dispatch)"
$classificationId = $registered["classification"].id
try {
    Invoke-Api -Method POST -Path "/api/v1/execute/$classificationId" -Body @{ wrongField = "x" }
    Write-Warning "Expected contract rejection but dispatch succeeded."
} catch {
    Write-Host "  Contract rejection OK: $($_.Exception.Message)" -ForegroundColor Green
}

Write-Step "3. Baseline COMPONENT dispatch (generative, classification, regression)"
foreach ($key in @("generative", "classification", "regression")) {
    $def = $config.agents | Where-Object { $_.key -eq $key } | Select-Object -First 1
    Execute-Agent $key $def.executePayload | Out-Null
}

if (-not $SkipSlowAlaDemo) {
    Write-Step "4. ALA latency stress"
    $slowDef = $config.agents | Where-Object { $_.key -eq "classificationSlow" } | Select-Object -First 1
    $slowPayload = @{}
    $slowDef.executePayload.PSObject.Properties | ForEach-Object { $slowPayload[$_.Name] = $_.Value }
    $slowResponse = Execute-Agent "classificationSlow" $slowPayload

    Write-Step "5. ALA confidence stress"
    $confDef = $config.agents | Where-Object { $_.key -eq "classificationLowConfidence" } | Select-Object -First 1
    $confPayload = @{}
    $confDef.executePayload.PSObject.Properties | ForEach-Object { $confPayload[$_.Name] = $_.Value }
    $confResponse = Execute-Agent "classificationLowConfidence" $confPayload
} else {
    $slowResponse = $results | Where-Object { $_.Key -eq "classification" } | Select-Object -Last 1
}

Write-Step "6. Endpoint portability (provider B, same manifest schema)"
$providerDef = $config.agents | Where-Object { $_.key -eq "classificationProviderB" } | Select-Object -First 1
Execute-Agent "classificationProviderB" $providerDef.executePayload | Out-Null

Write-Step "7. ALA evaluator diagnosis"
$targetKey = if ($SkipSlowAlaDemo) { "classification" } else { "classificationSlow" }
$targetAgent = $registered[$targetKey]
$targetExecution = $results | Where-Object { $_.Key -eq $targetKey } | Select-Object -Last 1
$logs = Invoke-Api -Method GET -Path "/api/v1/manifests/$($targetAgent.id)/invocations?limit=5"
$lastExec = @(Invoke-Api -Method GET -Path "/api/v1/manifests/$($targetAgent.id)/invocations?limit=1") | Select-Object -First 1

$evalPayload = @{
    execution = @{
        agentId = $targetAgent.id
        agentName = $targetAgent.name
        agentRole = $targetAgent.role
        agentType = $targetAgent.type
        latencyMs = $targetExecution.LatencyMs
        alaCompliant = $targetExecution.AlaCompliant
        status = $targetExecution.Status
        httpStatusCode = 200
        metrics = @{ confidenceScore = $lastExec.confidenceScore; tokensTotal = $lastExec.tokensTotal; estimatedCost = $lastExec.estimatedCost }
    }
    alaSettings = $targetAgent.alaSettings
    recentInvocations = @($logs | ForEach-Object { @{ latencyMs = $_.latencyMs; alaCompliant = $_.alaCompliant; status = $_.status } })
}
$diagnosis = Execute-Agent "alaEvaluator" $evalPayload

Write-Step "8. REFEREE and OBSERVER"
$refereeDef = $config.agents | Where-Object { $_.key -eq "referee" } | Select-Object -First 1
Execute-Agent "referee" $refereeDef.executePayload | Out-Null
Execute-Agent "observer" @{ execution = @{ agentName = $targetAgent.name; latencyMs = $targetExecution.LatencyMs; alaCompliant = $targetExecution.AlaCompliant; status = $targetExecution.Status } } | Out-Null

Write-Step "Summary"
$results | Format-Table Key, Status, AlaCompliant, LatencyMs, AlaViolations -AutoSize
Write-Host "Experiment completed." -ForegroundColor Green
