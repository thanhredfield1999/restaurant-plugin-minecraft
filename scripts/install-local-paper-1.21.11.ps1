param(
    [string]$ServerDirectory = (Join-Path $PSScriptRoot "..\run\paper-1.21.11-local")
)

$ErrorActionPreference = "Stop"

$paperVersion = "1.21.11"
$paperBuild = "132"
$paperJarName = "paper-1.21.11-132.jar"
$paperSha256 = "5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"
$paperUrl = "https://fill-data.papermc.io/v1/objects/$paperSha256/$paperJarName"

New-Item -ItemType Directory -Force -Path $ServerDirectory | Out-Null
$paperJar = Join-Path $ServerDirectory $paperJarName
Invoke-WebRequest -Uri $paperUrl -OutFile $paperJar

$actualSha256 = (Get-FileHash -Algorithm SHA256 -Path $paperJar).Hash.ToLowerInvariant()
if ($actualSha256 -ne $paperSha256) {
    Remove-Item -Force $paperJar
    throw "Paper checksum mismatch. Expected $paperSha256, got $actualSha256."
}

$pluginsDirectory = Join-Path $ServerDirectory "plugins"
New-Item -ItemType Directory -Force -Path $pluginsDirectory | Out-Null

Write-Host "Paper $paperVersion build $paperBuild installed: $paperJar"
Write-Host "Copy ViaVersion and ViaBackwards release JARs into: $pluginsDirectory"
Write-Host "Do not copy plugins while Paper is running. Restart local Paper after both JARs exist."
