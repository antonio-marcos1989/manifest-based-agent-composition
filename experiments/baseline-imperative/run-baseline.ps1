# Imperative baseline: three HTTP calls without manifest catalog (integration glue only).
param(
    [string]$MockUrl = "http://localhost:9090"
)

$ErrorActionPreference = "Stop"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

Invoke-RestMethod -Method POST -Uri "$MockUrl/mock/component/generative" -ContentType "application/json" -Body '{"prompt":"baseline"}' | Out-Null
Invoke-RestMethod -Method POST -Uri "$MockUrl/mock/component/classification" -ContentType "application/json" -Body '{"feature":"baseline"}' | Out-Null
Invoke-RestMethod -Method POST -Uri "$MockUrl/mock/component/regression" -ContentType "application/json" -Body '{"value":1.0}' | Out-Null

$sw.Stop()
Write-Host ("Imperative baseline completed in {0} ms (3 direct HTTP calls, no catalog/ALA/telemetry)." -f $sw.ElapsedMilliseconds)
