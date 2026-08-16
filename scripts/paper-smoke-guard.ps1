function Test-IsSmokePaperCommandLine {
    [CmdletBinding()]
    param(
        [AllowNull()]
        [AllowEmptyString()]
        [string]$CommandLine,
        [string]$PaperJarName = "paper-1.20.4-499.jar"
    )
    if ([string]::IsNullOrWhiteSpace($PaperJarName)) {
        throw "PaperJarName must not be empty."
    }
    return (
        $null -ne $CommandLine -and
        $CommandLine.Length -gt 0 -and
        $CommandLine.IndexOf($PaperJarName, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    )
}

function Test-SmokePortListening {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port,
        [int]$ConnectTimeoutMilliseconds = 2000
    )
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connectTask = $client.ConnectAsync([System.Net.IPAddress]::Loopback, $Port)
        if ($connectTask.Wait($ConnectTimeoutMilliseconds)) {
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-SmokePaperProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$PaperJarName,
        [int]$QueryTimeoutSeconds = 20
    )
    if ([string]::IsNullOrWhiteSpace($PaperJarName)) {
        throw "PaperJarName must not be empty."
    }
    $wql = "(Name = 'java.exe' OR Name = 'javaw.exe')"
    $queryJob = Start-Job -ScriptBlock {
        param($Filter, $Needle, $GuardPath)
        . $GuardPath
        $rows = Get-CimInstance Win32_Process -Filter $Filter -ErrorAction Stop
        foreach ($row in $rows) {
            if (Test-IsSmokePaperCommandLine -CommandLine $row.CommandLine -PaperJarName $Needle) {
                $row
            }
        }
    } -ArgumentList $wql, $PaperJarName, (Join-Path $PSScriptRoot "paper-smoke-guard.ps1")
    try {
        if (-not (Wait-Job -Job $queryJob -Timeout $QueryTimeoutSeconds)) {
            Stop-Job -Job $queryJob -Force
            throw "Timed out after $QueryTimeoutSeconds seconds while checking for an existing Paper smoke process. Refusing to start a second instance."
        }
        return @(Receive-Job -Job $queryJob -ErrorAction Stop)
    } finally {
        Remove-Job -Job $queryJob -Force -ErrorAction SilentlyContinue
    }
}

function Assert-NoRunningPaperSmoke {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port,
        [string]$PaperJarName = "paper-1.20.4-499.jar"
    )
    if (Test-SmokePortListening -Port $Port) {
        throw "Port $Port already accepts connections. Refusing to start a second server on the smoke port."
    }
    $existing = @(Get-SmokePaperProcess -PaperJarName $PaperJarName)
    if ($existing.Count -ne 0) {
        $pids = @($existing | ForEach-Object { $_.ProcessId }) -join ", "
        throw "A Paper 1.20.4 smoke process is already running (PID: $pids). Refusing to start a second instance."
    }
}
