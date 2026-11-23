# Prüft XRechnung-Dateien (UBL oder CII) mit dem KoSIT-Validator.
# Aufruf: .\scripts\validate.ps1 rechnung.xml [weitere.xml ...]
# Berichte landen in out/validation/. Exit-Code 0 = alle gültig.
$ErrorActionPreference = 'Stop'
if ($args.Count -eq 0) { Write-Error 'Aufruf: validate.ps1 <datei.xml> [...]' }

$root = Split-Path $PSScriptRoot -Parent   # absoluter Pfad ohne '..' (der Validator lehnt '..' im Repository-Pfad ab)
$dir  = Join-Path $root 'tools\kosit'
$jar = Get-ChildItem $dir -Filter 'validator-*-standalone.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { & (Join-Path $PSScriptRoot 'setup-validator.ps1'); $jar = Get-ChildItem $dir -Filter 'validator-*-standalone.jar' | Select-Object -First 1 }

$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) { $java = Get-ChildItem 'C:\Program Files\Eclipse Adoptium\*\bin\java.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $java) { Write-Error 'Java 11+ nicht gefunden.' }

$out = Join-Path $root 'out\validation'
New-Item -ItemType Directory -Force $out | Out-Null
$cfg = Join-Path $dir 'config'

# stdin = leere Datei: Der Validator prüft stdin auf gepipte Eingabe und bricht bei nicht-interaktiven
# Aufrufen (stdin = NUL-Gerät, z. B. durch Agenten oder CI) mit "Incorrect function" ab.
$empty = Join-Path $out '.stdin'
[IO.File]::WriteAllBytes($empty, @())
$q = { param($s) '"' + $s + '"' }
$argList = @('-jar', (& $q $jar.FullName), '-s', (& $q (Join-Path $cfg 'scenarios.xml')), '-r', (& $q $cfg), '-o', (& $q $out), '-h') +
           ($args | ForEach-Object { & $q (Resolve-Path $_).Path })
$p = Start-Process $java -ArgumentList $argList -NoNewWindow -Wait -PassThru -RedirectStandardInput $empty
exit $p.ExitCode
