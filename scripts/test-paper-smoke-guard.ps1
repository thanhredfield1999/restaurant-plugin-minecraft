[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$guardPath = Join-Path $PSScriptRoot "paper-smoke-guard.ps1"
if (!(Test-Path -LiteralPath $guardPath)) {
    throw "Guard file not found: $guardPath"
}
. $guardPath

$failures = [System.Collections.Generic.List[string]]::new()

function New-EphemeralListener {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    return $listener, $port
}

function Invoke-Test {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Body
    )
    try {
        & $Body
        Write-Output "PASS: $Name"
    } catch {
        $script:failures.Add("$Name : $($_.Exception.Message)")
        Write-Output "FAIL: $Name : $($_.Exception.Message)"
    }
}

Invoke-Test "Test-IsSmokePaperCommandLine matches the pinned smoke jar on java.exe" {
    $result = Test-IsSmokePaperCommandLine -CommandLine "java -Xms512M -Xmx1G -jar `"C:\smoke\run\paper-smoke\paper-1.20.4-499.jar`" --nogui"
    if (-not $result) { throw "Expected a match for the pinned smoke jar." }
}

Invoke-Test "Test-IsSmokePaperCommandLine matches a javaw.exe smoke launch" {
    $result = Test-IsSmokePaperCommandLine -CommandLine "javaw.exe -jar E:\run\paper-smoke\paper-1.20.4-499.jar --nogui"
    if (-not $result) { throw "Expected a match for a javaw.exe smoke launch." }
}

Invoke-Test "Test-IsSmokePaperCommandLine does not match a Java Minecraft client" {
    $result = Test-IsSmokePaperCommandLine -CommandLine "javaw.exe -Dminecraft.jars.dir=C:\Users\me\AppData\Roaming\.minecraft -cp minecraft-client.jar net.minecraft.client.main.Main --username dev"
    if ($result) { throw "A Minecraft client command line must not match the smoke jar check." }
}

Invoke-Test "Test-IsSmokePaperCommandLine does not match a production Paper server" {
    $result = Test-IsSmokePaperCommandLine -CommandLine "java -Xmx2G -jar C:\prod\paper-1.20.4-437.jar --nogui"
    if ($result) { throw "A production Paper jar must not match the pinned smoke jar." }
}

Invoke-Test "Test-IsSmokePaperCommandLine rejects null and empty command lines" {
    if (Test-IsSmokePaperCommandLine -CommandLine $null) { throw "null command line must not match." }
    if (Test-IsSmokePaperCommandLine -CommandLine "") { throw "empty command line must not match." }
}

Invoke-Test "Test-SmokePortListening detects an active listener" {
    $listener, $port = New-EphemeralListener
    try {
        $listener.Start()
        if (-not (Test-SmokePortListening -Port $port)) {
            throw "Expected the active listener on port $port to be detected."
        }
    } finally {
        $listener.Stop()
    }
}

Invoke-Test "Test-SmokePortListening returns false for a closed port" {
    $listener, $port = New-EphemeralListener
    $listener.Stop()
    if (Test-SmokePortListening -Port $port) {
        throw "Expected the closed port $port not to be detected."
    }
}

Invoke-Test "Test-SmokePortListening rejects out-of-range ports" {
    foreach ($badPort in @(0, 65536, -1)) {
        $threw = $false
        try {
            Test-SmokePortListening -Port $badPort | Out-Null
        } catch {
            $threw = $true
        }
        if (-not $threw) { throw "Expected port $badPort to be rejected." }
    }
}

Invoke-Test "Get-SmokePaperProcess completes within the bound and returns a collection" {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $result = @(Get-SmokePaperProcess -PaperJarName "paper-1.20.4-499.jar" -QueryTimeoutSeconds 15)
    $stopwatch.Stop()
    if ($null -eq $result) { throw "Expected a collection result." }
    if ($stopwatch.Elapsed.TotalSeconds -ge 60) {
        throw "Process query took $($stopwatch.Elapsed.TotalSeconds)s; the query is not bounded."
    }
}

Invoke-Test "Assert-NoRunningPaperSmoke throws when the smoke port is occupied" {
    $listener, $port = New-EphemeralListener
    try {
        $listener.Start()
        $threw = $false
        try {
            Assert-NoRunningPaperSmoke -Port $port
        } catch {
            $threw = $true
        }
        if (-not $threw) { throw "Expected Assert-NoRunningPaperSmoke to reject an occupied port." }
    } finally {
        $listener.Stop()
    }
}

Invoke-Test "Assert-NoRunningPaperSmoke passes on a free port with no smoke process" {
    $listener, $port = New-EphemeralListener
    $listener.Stop()
    Assert-NoRunningPaperSmoke -Port $port
}

if ($failures.Count -gt 0) {
    Write-Output "Smoke-guard regression tests FAILED ($($failures.Count))."
    exit 1
}
Write-Output "All smoke-guard regression tests passed."
exit 0
