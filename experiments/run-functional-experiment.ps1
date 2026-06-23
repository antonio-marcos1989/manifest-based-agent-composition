param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$MockUrl = "http://localhost:9090"
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    $uri = "$BaseUrl$Path"
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri
}

Write-Host "== Functional experiment: manifest registration + dispatch ==" -ForegroundColor Cyan

try {
    Invoke-RestMethod -Method GET -Uri "$MockUrl/health" | Out-Null
} catch {
    Write-Error "Mock agent not reachable at $MockUrl. Run: cd experiments && npm run mock-agent"
}

$manifest = @{
    name = "mock-classifier"
    description = "Mock classification agent for paper experiment"
    role = "COMPONENT"
    type = "CLASSIFICATION"
    endpointUrl = $MockUrl
    active = $true
    requestPayloadMode = "DIRECT_JSON"
    capabilities = @("experiment", "classification")
    inputJsonSchema = @{
        type = "object"
        required = @("feature")
        properties = @{ feature = @{ type = "string" } }
    }
    outputJsonSchema = @{
        type = "object"
        properties = @{ answer = @{ type = "string" } }
    }
    alaSettings = @{
        maxLatencyMs = 5000
        maxErrorPercentage = 30
        evaluationWindow = 5
        reliabilityThreshold = 0.7
        strictContract = $false
    }
}

Write-Host "Registering manifest..."
$registered = Invoke-Api -Method POST -Path "/api/v1/manifests" -Body $manifest
$agentId = $registered.id
Write-Host "Agent id: $agentId"

Write-Host "Dispatching..."
$execution = Invoke-Api -Method POST -Path "/api/v1/execute/$agentId" -Body @{ feature = "sample-input" }
Write-Host "Dispatch status: $($execution.status) | ALA compliant: $($execution.alaCompliant) | latency: $($execution.latencyMs) ms"

Write-Host "Metrics..."
$metrics = Invoke-Api -Method GET -Path "/api/v1/manifests/$agentId/metrics?window=10"
Write-Host "Invocations: $($metrics.totalInvocations) | success rate: $($metrics.successRate)%"

Write-Host "Recent logs..."
$logs = Invoke-Api -Method GET -Path "/api/v1/manifests/$agentId/invocations?limit=5"
Write-Host "Log entries: $($logs.Count)"

Write-Host "Experiment completed successfully." -ForegroundColor Green
