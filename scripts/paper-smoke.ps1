param(
    [int]$Port = 25566,
    [int]$TimeoutSeconds = 240,
    [switch]$Projection,
    [switch]$RuntimeFixture,
    [switch]$UseExistingConfig,
    [switch]$CrashAfterCommit,
    [switch]$RecoverCrash
)

$ErrorActionPreference = "Stop"

$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME")
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = "C:\Program Files\Java\jdk-21"
}
$javaExecutable = Join-Path $javaHome "bin\java.exe"
if (!(Test-Path -LiteralPath $javaExecutable)) {
    throw "Java 21 executable not found: $javaExecutable. Set JAVA_HOME to a Java 21 installation."
}
$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path

. (Join-Path $PSScriptRoot "paper-smoke-guard.ps1")

$paperUrl = "https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar"
$paperSha256 = "5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"
$paperSize = 0
$userAgent = "restaurant-tycoon-smoke/0.1.0 (local development harness)"
$smokeWorldName = "rt-flat-test"

function Require-EnvironmentVariable([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name must be set in this PowerShell process."
    }
    return $value
}

function Quote-Yaml([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

if (!$UseExistingConfig -and $env:RT_ACCEPT_MINECRAFT_EULA -ne "true") {
    throw "Read https://www.minecraft.net/eula and set RT_ACCEPT_MINECRAFT_EULA=true only if you accept it."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot "run\paper-smoke"
$pluginsDirectory = Join-Path $runDirectory "plugins"
$pluginDirectory = Join-Path $pluginsDirectory "RestaurantTycoon"
$paperJar = Join-Path $runDirectory "paper-1.21.11-132.jar"
$pluginJar = Join-Path $projectRoot "build\libs\restaurant-tycoon-0.1.0-SNAPSHOT.jar"
$stdoutLog = Join-Path $runDirectory "smoke-stdout.log"
$stderrLog = Join-Path $runDirectory "smoke-stderr.log"

if (!$UseExistingConfig) {
    $postgresUrl = Require-EnvironmentVariable "RT_TEST_POSTGRES_URL"
    $postgresUser = Require-EnvironmentVariable "RT_TEST_POSTGRES_USER"
    $postgresPassword = Require-EnvironmentVariable "RT_TEST_POSTGRES_PASSWORD"
}

if (!(Test-Path -LiteralPath $runDirectory)) {
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
}
if (!(Test-Path -LiteralPath $pluginsDirectory)) {
    New-Item -ItemType Directory -Path $pluginsDirectory | Out-Null
}
if (!(Test-Path -LiteralPath $pluginDirectory)) {
    New-Item -ItemType Directory -Path $pluginDirectory | Out-Null
}

if (!(Test-Path -LiteralPath $paperJar)) {
    Invoke-WebRequest -Uri $paperUrl -Headers @{ "User-Agent" = $userAgent } -OutFile $paperJar -TimeoutSec 120
}
$paperFile = Get-Item -LiteralPath $paperJar
$actualHash = (Get-FileHash -LiteralPath $paperJar -Algorithm SHA256).Hash.ToLowerInvariant()
if (($paperSize -gt 0 -and $paperFile.Length -ne $paperSize) -or $actualHash -ne $paperSha256) {
    throw "Paper artifact checksum or size mismatch. Delete $paperJar before retrying."
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot "gradlew.bat") build --no-daemon --console=plain
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($gradleExitCode -ne 0) {
    throw "Plugin build failed with exit code $gradleExitCode."
}
Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $pluginsDirectory "RestaurantTycoon.jar") -Force

if ($UseExistingConfig) {
    $eulaFile = Join-Path $runDirectory "eula.txt"
    $eulaExists = Test-Path -LiteralPath $eulaFile
    $eulaAccepted = $eulaExists -and (Get-Content -LiteralPath $eulaFile -Raw).Trim() -eq "eula=true"
    if (!$eulaAccepted) {
        throw "Existing EULA acceptance was not found."
    }
} else {
    Set-Content -LiteralPath (Join-Path $runDirectory "eula.txt") -Encoding ASCII -Value "eula=true"
}
Set-Content -LiteralPath (Join-Path $runDirectory "server.properties") -Encoding ASCII -Value @(
    "server-port=$Port"
    "online-mode=false"
    "white-list=true"
    "spawn-protection=0"
    "view-distance=4"
    "simulation-distance=4"
    "motd=RestaurantTycoon local smoke test"
    "level-name=$smokeWorldName"
)

if ($UseExistingConfig) {
    if (!(Test-Path -LiteralPath (Join-Path $pluginDirectory "config.yml"))) {
        throw "Existing smoke config was requested but does not exist."
    }
} else {
    $jdbcUri = [Uri]$postgresUrl.Substring(5)
    $databaseName = $jdbcUri.AbsolutePath.TrimStart('/')
    $config = @"
schema-version: 2
database:
  enabled: true
  host: $(Quote-Yaml $jdbcUri.Host)
  port: $($jdbcUri.Port)
  name: $(Quote-Yaml $databaseName)
  username: $(Quote-Yaml $postgresUser)
  password: $(Quote-Yaml $postgresPassword)
  maximum-pool-size: 4
  connection-timeout-ms: 5000
gameplay:
  max-active-plots: 1
  max-physical-customers-per-plot: 1
world-operations:
  instance-id: paper-smoke
  poll-ticks: 10
  lease-seconds: 30
  blocks-per-tick: 100
plots:
  plot_1:
    world: world
    origin:
      x: 0
      y: 64
      z: 0
    trigger:
      min:
        x: -2
        y: 64
        z: -2
      max:
        x: 2
        y: 67
        z: 2
supply-catalog:
  version: 1
  ingredients:
    tomato:
      display-name: "Cà chua"
      unit: PIECE
      unit-price: 25
      max-quantity: 64
    rice:
      display-name: "Gạo"
      unit: GRAM
      unit-price: 1
      max-quantity: 1000
"@
    Set-Content -LiteralPath (Join-Path $pluginDirectory "config.yml") -Encoding UTF8 -Value $config
}

Remove-Item -LiteralPath $stdoutLog, $stderrLog -Force -ErrorAction SilentlyContinue
Assert-NoRunningPaperSmoke -Port $Port -PaperJarName (Split-Path -Leaf $paperJar)
Remove-Item -LiteralPath (Join-Path $runDirectory "logs\latest.log") `
    -Force -ErrorAction SilentlyContinue
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = "java"
$fixtureEnabled = $Projection -or $RuntimeFixture -or $CrashAfterCommit -or $RecoverCrash
$projectionFixtureEnabled = $Projection -or $CrashAfterCommit -or $RecoverCrash
$fixtureProperty = if ($fixtureEnabled) { "-Drestauranttycoon.testFixtures=true " } else { "" }
if ($CrashAfterCommit) {
    $fixtureProperty += "-Drestauranttycoon.testCrashAfterCommit=true "
}
$startInfo.Arguments = "-Xms512M -Xmx1G $fixtureProperty-jar `"$paperJar`" --nogui"
$startInfo.WorkingDirectory = $runDirectory
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
$process.Start() | Out-Null
$stdoutTask = $process.StandardOutput.ReadToEndAsync()
$stderrTask = $process.StandardError.ReadToEndAsync()
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$success = $false

try {
    while ([DateTime]::UtcNow -lt $deadline -and !$process.HasExited) {
        Start-Sleep -Milliseconds 500
        $latestLog = Join-Path $runDirectory "logs\latest.log"
        if (Test-Path -LiteralPath $latestLog) {
            try {
                $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                if (![string]::IsNullOrEmpty($log)) {
                    $success = $log.Contains("RestaurantTycoon enabled with config schema 2") `
                        -and $log.Contains("Database connected and migrations completed") `
                        -and $log.Contains("World-operation worker started as paper-smoke") `
                        -and $log.Contains("Done (")
                    if ($success) {
                        break
                    }
                }
            } catch [System.IO.IOException] {
                # Paper may have the file locked while rotating or flushing it.
            }
        }
    }
    if ($success -and $RuntimeFixture) {
        $fixtureDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $fixtureId = [Guid]::NewGuid().ToString()
        $restaurantId = [Guid]::NewGuid().ToString()
        $playerId = [Guid]::NewGuid().ToString()
        $runtimeLogOffset = 0
        if (Test-Path -LiteralPath $latestLog) {
            $runtimeLogOffset = (Get-Content -LiteralPath $latestLog -Raw).Length
        }
        $process.StandardInput.WriteLine("restaurant dev runtime-fixture seed $fixtureId $restaurantId $playerId")
        $process.StandardInput.Flush()
        $seeded = $false
        $seededShipmentId = $null
        $dispatched = $false
        while ([DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
            Start-Sleep -Milliseconds 500
            try {
                $fullLog = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                $log = if ($fullLog.Length -gt $runtimeLogOffset) { $fullLog.Substring($runtimeLogOffset) } else { "" }
                $seedMatch = [regex]::Match(
                    [string]$log,
                    "SUPPLY_RUNTIME_FIXTURE_SEEDED fixture=$fixtureId shipment=([0-9a-f-]+)")
                if ($seedMatch.Success) {
                    $seeded = $true
                    $seededShipmentId = $seedMatch.Groups[1].Value
                }
                $dispatched = $seeded -and [string]$log -like "*SUPPLY_RUNTIME_PROJECTION_DISPATCHED shipment=$seededShipmentId stage=DELIVERY_ENTRY index=0 movement=disabled*"
                if ($seeded -and $dispatched) { break }
                if ([string]$log -like "*SUPPLY_RUNTIME_FIXTURE_SEED_FAILED fixture=$fixtureId*") {
                    throw "Runtime fixture seed failed."
                }
            } catch [System.IO.IOException] {
            }
        }
        if (!$seeded) { throw "Runtime fixture did not seed before timeout." }
        if (!$dispatched) { throw "Runtime projection callback was not observed before timeout." }
        $process.StandardInput.WriteLine("restaurant dev runtime-fixture cleanup $fixtureId")
        $process.StandardInput.Flush()
        $cleaned = $false
        while ([DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
            Start-Sleep -Milliseconds 500
            try {
                $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                $cleaned = [string]$log -like "*SUPPLY_RUNTIME_FIXTURE_CLEANED fixture=$fixtureId*deleted=True*"
                if ($cleaned) { break }
                if ([string]$log -like "*SUPPLY_RUNTIME_FIXTURE_CLEANUP_FAILED fixture=$fixtureId*") {
                    throw "Runtime fixture cleanup failed."
                }
            } catch [System.IO.IOException] {
            }
        }
        if (!$cleaned) { throw "Runtime fixture cleanup did not finish before timeout." }
    }
    if ($success -and $projectionFixtureEnabled) {
        $fixtureDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $stateFile = Join-Path $runDirectory "restauranttycoon-fixture-state.json"
        if ($RecoverCrash) {
            if (!(Test-Path -LiteralPath $stateFile)) {
                throw "Crash-recovery fixture state does not exist."
            }
            $fixtureState = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
            $accountId = [string]$fixtureState.accountId
            $worldOperationId = [string]$fixtureState.worldOperationId
        } else {
            $accountId = [Guid]::NewGuid().ToString()
            $grantOperationId = [Guid]::NewGuid().ToString()
            $purchaseOperationId = [Guid]::NewGuid().ToString()
            $process.StandardInput.WriteLine(
                "restaurant dev fixture seed $accountId $grantOperationId $purchaseOperationId")
            $process.StandardInput.Flush()

            $worldOperationId = $null
            $purchaseId = $null
            while ([DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
                Start-Sleep -Milliseconds 500
                $latestLog = Join-Path $runDirectory "logs\latest.log"
                if (!(Test-Path -LiteralPath $latestLog)) {
                    continue
                }
                try {
                    $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                    $seedMatch = [regex]::Match(
                        [string]$log,
                        "PROJECTION_FIXTURE_SEEDED account=$accountId purchase=([0-9a-f-]+) worldOperation=([0-9a-f-]+).* duplicate=false")
                    if ($seedMatch.Success) {
                        $purchaseId = $seedMatch.Groups[1].Value
                        $worldOperationId = $seedMatch.Groups[2].Value
                        break
                    }
                    if ([string]$log -like "*PROJECTION_FIXTURE_SEED_FAILED account=$accountId*") {
                        throw "Projection fixture seed failed."
                    }
                } catch [System.IO.IOException] {
                }
            }
            if ([string]::IsNullOrEmpty($worldOperationId)) {
                throw "Projection fixture did not commit before timeout."
            }
            if ($CrashAfterCommit) {
                @{
                    accountId = $accountId
                    grantOperationId = $grantOperationId
                    purchaseOperationId = $purchaseOperationId
                    purchaseId = $purchaseId
                    worldOperationId = $worldOperationId
                } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding ASCII
                if (!$process.WaitForExit(30000)) {
                    throw "Paper did not execute the injected crash."
                }
                if ($process.ExitCode -ne 86) {
                    throw "Injected crash exited with code $($process.ExitCode), expected 86."
                }
                $crashPassed = $true
            }
        }

        $verified = $CrashAfterCommit
        $nextVerify = [DateTime]::MinValue
        while (!$CrashAfterCommit -and [DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
            if ([DateTime]::UtcNow -ge $nextVerify) {
                $process.StandardInput.WriteLine(
                    "restaurant dev fixture verify $worldOperationId")
                $process.StandardInput.Flush()
                $nextVerify = [DateTime]::UtcNow.AddSeconds(1)
            }
            Start-Sleep -Milliseconds 250
            try {
                $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                $verified = [string]$log -like "*PROJECTION_FIXTURE_VERIFIED worldOperation=$worldOperationId*"
                if ($verified) {
                    break
                }
            } catch [System.IO.IOException] {
            }
        }
        if (!$CrashAfterCommit -and !$verified) {
            throw "Projection fixture was not applied and verified before timeout."
        }

        if (!$CrashAfterCommit -and !$RecoverCrash) {
            $process.StandardInput.WriteLine(
                "restaurant dev fixture seed $accountId $grantOperationId $purchaseOperationId")
            $process.StandardInput.Flush()
        }
        $replayed = $CrashAfterCommit -or $RecoverCrash
        while (!$CrashAfterCommit -and !$RecoverCrash -and [DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
            Start-Sleep -Milliseconds 500
            try {
                $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                $replayPattern = "PROJECTION_FIXTURE_SEEDED account=$accountId purchase=$purchaseId worldOperation=$worldOperationId.* duplicate=true"
                $replayed = [regex]::IsMatch([string]$log, $replayPattern)
                if ($replayed) {
                    break
                }
            } catch [System.IO.IOException] {
            }
        }
        if (!$CrashAfterCommit -and !$RecoverCrash -and !$replayed) {
            throw "Projection fixture replay was not idempotent before timeout."
        }

        if (!$CrashAfterCommit) {
            $process.StandardInput.WriteLine("restaurant dev fixture cleanup $accountId")
            $process.StandardInput.Flush()
        }
        $cleaned = $CrashAfterCommit
        while (!$CrashAfterCommit -and [DateTime]::UtcNow -lt $fixtureDeadline -and !$process.HasExited) {
            Start-Sleep -Milliseconds 500
            try {
                $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction Stop
                $cleaned = [string]$log -like "*PROJECTION_FIXTURE_CLEANED account=$accountId*"
                if ($cleaned) {
                    break
                }
                if ([string]$log -like "*PROJECTION_FIXTURE_CLEANUP_FAILED account=$accountId*") {
                    throw "Projection fixture cleanup failed."
                }
            } catch [System.IO.IOException] {
            }
        }
        if (!$CrashAfterCommit -and !$cleaned) {
            throw "Projection fixture cleanup did not finish before timeout."
        }
        if ($RecoverCrash -and $cleaned) {
            Remove-Item -LiteralPath $stateFile -Force
        }
    }
} finally {
    try {
        if (!$process.HasExited) {
            try {
                $process.StandardInput.WriteLine("stop")
                $process.StandardInput.Flush()
            } catch {
                # Process may exit between HasExited and stdin write.
            }
            if (!$process.WaitForExit(30000)) {
                try { $process.Kill() } catch { }
                try { $process.WaitForExit() } catch { }
            }
        }
    } finally {
        Set-Content -LiteralPath $stdoutLog -Encoding UTF8 -Value $stdoutTask.GetAwaiter().GetResult()
        Set-Content -LiteralPath $stderrLog -Encoding UTF8 -Value $stderrTask.GetAwaiter().GetResult()
    }
}

if (!$success) {
    throw "Paper smoke markers were not reached. Inspect $runDirectory\logs\latest.log and smoke logs."
}
if ($CrashAfterCommit -and !$crashPassed) {
    throw "Injected crash did not complete."
}
if (!$CrashAfterCommit -and $process.ExitCode -ne 0) {
    throw "Paper exited with code $($process.ExitCode)."
}

Write-Output "Paper 1.21.11 build 132 smoke test passed and stopped cleanly."
if ($Projection) {
    Write-Output "Playerless purchase-to-world projection, replay, and cleanup passed."
}
if ($CrashAfterCommit) {
    Write-Output "Crash injected after purchase commit and before world apply."
}
if ($RecoverCrash) {
    Write-Output "Restart recovery, projection verification, replay, and cleanup passed."
}
