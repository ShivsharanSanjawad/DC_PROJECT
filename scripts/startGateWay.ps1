param(
    [string]$Module = "gateway"
)

# --- Resolve paths dynamically ---
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$BackendDir = Join-Path $ScriptDir "..\backend"
$BackendDir = (Resolve-Path $BackendDir).Path

Write-Host "Starting cluster modules: $Module"
Write-Host "Backend Path: $BackendDir"

function Start-Node($name, $port, $workDir, $extraEnv = @{}) {
    Write-Host "Launching $name on port $port..."

    $envVars = @{
        "SERVER_PORT" = $port
        "JAVA_OPTS"   = "--add-opens=java.base/sun.misc=ALL-UNNAMED"
    }
    foreach ($key in $extraEnv.Keys) { $envVars[$key] = $extraEnv[$key] }

    # ✅ Build environment variable string
    $envString = ($envVars.GetEnumerator() | ForEach-Object { "`$env:$($_.Key)='$($_.Value)'" }) -join "; "

    # ✅ Run Spring Boot app with Maven
    $cmd = "$envString; cd '$workDir'; mvn -q -DskipTests spring-boot:run"

    Start-Process powershell -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", $cmd
}

# --- Gateway Cluster (gw1, gw2, gw3) ---
if ($Module -eq "all" -or $Module -eq "gateway") {
    $gwDir = Join-Path $BackendDir "gateway"

    Start-Node "Gateway-1" 8001 $gwDir @{
        "NODE_ID" = "gw1"
        "ALL_GATEWAYS" = "http://localhost:8001,http://localhost:8002,http://localhost:8003"
    }

    Start-Node "Gateway-2" 8002 $gwDir @{
        "NODE_ID" = "gw2"
        "ALL_GATEWAYS" = "http://localhost:8001,http://localhost:8002,http://localhost:8003"
    }

    Start-Node "Gateway-3" 8003 $gwDir @{
        "NODE_ID" = "gw3"
        "ALL_GATEWAYS" = "http://localhost:8001,http://localhost:8002,http://localhost:8003"
    }
}

Write-Host "`n✅ All requested gateway nodes have been launched."
Write-Host "Check individual PowerShell windows for logs."
