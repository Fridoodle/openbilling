# Lädt den offiziellen KoSIT-Validator + XRechnung-Konfiguration nach tools/kosit/ (gitignored).
# Voraussetzung: Java 11+ (z. B. winget install EclipseAdoptium.Temurin.21.JRE)
$ErrorActionPreference = 'Stop'
$ValidatorVersion = '1.6.3'
$ConfigTag        = '2026-08-31'
$ConfigZip        = "xrechnung-3.0.2-validator-configuration-$ConfigTag.zip"

$dir = Join-Path (Split-Path $PSScriptRoot -Parent) 'tools\kosit'
New-Item -ItemType Directory -Force $dir | Out-Null

$jar = Join-Path $dir "validator-$ValidatorVersion-standalone.jar"
if (-not (Test-Path $jar)) {
    Invoke-WebRequest "https://github.com/itplr-kosit/validator/releases/download/v$ValidatorVersion/validator-$ValidatorVersion-standalone.jar" -OutFile $jar
}

$cfg = Join-Path $dir 'config'
if (-not (Test-Path (Join-Path $cfg 'scenarios.xml'))) {
    $zip = Join-Path $dir $ConfigZip
    Invoke-WebRequest "https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v$ConfigTag/$ConfigZip" -OutFile $zip
    Expand-Archive $zip -DestinationPath $cfg -Force
    Remove-Item $zip
}
Write-Host "KoSIT-Validator bereit in $dir"
