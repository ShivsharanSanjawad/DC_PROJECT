param(
    [string]$Module = "all"
)

# ============================================================
# Dynamic Path Resolution
# ============================================================
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Definition
$BackendDir = Join-Path $ScriptDir "..\backend"
$BackendDir = (Resolve-Path $BackendDir).Path

Write-Host "`nStarting cluster modules: $Module"
Write-Host "Backend Path: $BackendDir"


# ============================================================
# Function: Start-Node
# ============================================================
function Start-Node($name, $port, $workDir, $extraEnv = @{}) {
    Write-Host "`n================================================"
    Write-Host "Launching: $name"
    Write-Host "Port: $port"
    Write-Host "WorkDir: $workDir"
    
    # Build environment variable assignments
    $envCommands = @("`$env:SERVER_PORT='$port'")
    foreach ($key in $extraEnv.Keys) {
        $value = $extraEnv[$key]
        $envCommands += "`$env:$key='$value'"
        Write-Host "  $key = $value"
    }

    Write-Host "================================================`n"

    # Join all environment commands
    $envString = $envCommands -join "; "
    
    # Build the complete command
    $fullCmd = "$envString; cd '$workDir'; mvn spring-boot:run"
    Write-Host "Command: $fullCmd`n"

    # Launch in new PowerShell window (not cmd)
    Start-Process "powershell.exe" -ArgumentList "-NoExit", "-Command", $fullCmd -WindowStyle Normal

    # Small delay between launches
    Start-Sleep -Seconds 2
}


# ============================================================
# DataNodes
# ============================================================
if ($Module -eq "all" -or $Module -eq "datanode") {
    Write-Host "`nStarting DataNodes..."
    $dnDir = Join-Path $BackendDir "datanode"

    Start-Node "DataNode-1" 9101 $dnDir @{
        "NODE_ID"             = "dn1"
        "DATANODE_BLOCK_PORT" = "10001"
        "DATANODE_DATA_DIR"   = "./data/dn1"
    }

    Start-Node "DataNode-2" 9102 $dnDir @{
        "NODE_ID"             = "dn2"
        "DATANODE_BLOCK_PORT" = "10002"
        "DATANODE_DATA_DIR"   = "./data/dn2"
    }

    Start-Node "DataNode-3" 9103 $dnDir @{
        "NODE_ID"             = "dn3"
        "DATANODE_BLOCK_PORT" = "10003"
        "DATANODE_DATA_DIR"   = "./data/dn3"
    }

    Write-Host "`nWaiting for DataNodes to start (15 seconds)..."
    Start-Sleep -Seconds 15
}


# ============================================================
# NameNodes
# ============================================================
if ($Module -eq "all" -or $Module -eq "namenode") {
    Write-Host "`nStarting NameNodes..."
    $nnDir = Join-Path $BackendDir "namenode"
    $nnList = "http://localhost:9001,http://localhost:9002,http://localhost:9003"

    Start-Node "NameNode-1" 9001 $nnDir @{
        "NODE_ID"       = "nn1"
        "ALL_NAMENODES" = $nnList
    }

    Start-Node "NameNode-2" 9002 $nnDir @{
        "NODE_ID"       = "nn2"
        "ALL_NAMENODES" = $nnList
    }

    Start-Node "NameNode-3" 9003 $nnDir @{
        "NODE_ID"       = "nn3"
        "ALL_NAMENODES" = $nnList
    }

    Write-Host "`nWaiting for NameNodes to start (15 seconds)..."
    Start-Sleep -Seconds 15
}


# ============================================================
# Gateway
# ============================================================
# if ($Module -eq "all" -or $Module -eq "gateway") {
#     Write-Host "`nStarting Gateway..."
#     $gwDir = Join-Path $BackendDir "gateway"
#     Start-Node "Gateway" 8001 $gwDir @{}
# }


# ============================================================
# Summary
# ============================================================
Write-Host "`nAll requested modules have been launched."
Write-Host "Check individual PowerShell windows for logs."
Write-Host "`nIMPORTANT:"
Write-Host "  Look for 'DATANODE BLOCK SERVER READY'"
Write-Host "  And 'Listening on: 0.0.0.0:10001 (or 10002, 10003)'"
Write-Host "`nIf you don't see those messages, the BlockServer didn't start!"
