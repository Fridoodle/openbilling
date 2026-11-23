# Baut das portable Windows-Paket: dist\openbilling-<version>-windows-x64.zip
# Inhalt: openbilling.exe (Fenster), openbilling-cli.exe (Kommandozeile), eigene Java-Laufzeit,
# alle Bibliotheken und die KoSIT-Konfiguration. Auf dem Zielrechner muss nichts installiert sein.
# Voraussetzung: JDK 21 mit jpackage, .\scripts\setup-maven.ps1 und .\scripts\setup-validator.ps1.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$version = ([xml](Get-Content pom.xml -Raw)).project.version
if ($version -like '*-SNAPSHOT') { throw "Version $version ist ein SNAPSHOT. Für ein Release in pom.xml setzen." }
$cfg = 'tools\kosit\config'
if (-not (Test-Path "$cfg\scenarios.xml")) { throw "$cfg fehlt. Erst .\scripts\setup-validator.ps1 ausführen." }

& .\tools\maven\bin\mvn.cmd -q clean package
if ($LASTEXITCODE -ne 0) { throw 'Maven-Build fehlgeschlagen' }
# Liste aller mitgelieferten Bibliotheken mit Lizenz (nur Build-Plugin, keine Abhängigkeit)
& .\tools\maven\bin\mvn.cmd -q org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party `
    '-Dlicense.outputDirectory=target\paket' '-Dlicense.thirdPartyFilename=DRITTANBIETER.txt' `
    '-Dlicense.excludedScopes=test,provided,system'
if ($LASTEXITCODE -ne 0) { throw 'Lizenzliste fehlgeschlagen' }

# Eingabe für jpackage: JAR, lib\ und kosit\config landen im Paket unter app\
$eingabe = 'target\paket\eingabe'
New-Item -ItemType Directory -Force $eingabe | Out-Null
Copy-Item target\openbilling.jar $eingabe
Copy-Item target\lib "$eingabe\lib" -Recurse
New-Item -ItemType Directory -Force "$eingabe\kosit" | Out-Null
Copy-Item $cfg "$eingabe\kosit\config" -Recurse

# zweiter Starter mit Konsole für die Kommandozeile
$cli = 'target\paket\cli.properties'
Set-Content $cli "win-console=true`r`nlauncher-as-service=false" -Encoding ascii

# Java-Laufzeit nur mit den nötigen Modulen (spart rund 100 MB). Liste aus
#   jdeps --ignore-missing-deps --print-module-deps --multi-release 21 --class-path <lib> openbilling.jar <lib\*.jar>
# plus Module, die erst zur Laufzeit per Reflection/ServiceLoader geladen werden und die jdeps nicht sieht
# (deutsche Zahlen- und Datumsformate in jdk.localedata, Zeichensätze, Krypto, JNDI, XML-Signatur).
# Nach jeder Änderung an Abhängigkeiten neu prüfen und das Paket von Hand testen.
$module = 'java.base,java.compiler,java.desktop,java.management,java.sql,jdk.httpserver,jdk.xml.dom,' +
          'java.logging,java.naming,java.net.http,java.prefs,java.scripting,java.xml,java.xml.crypto,' +
          'jdk.accessibility,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.zipfs'

$bild = 'target\paket\bild'
& jpackage --type app-image --name openbilling --app-version $version `
    --add-modules $module `
    --jlink-options '--strip-debug --no-header-files --no-man-pages --strip-native-commands' `
    --vendor 'openbilling' --description 'E-Rechnungen nach XRechnung' `
    --input $eingabe --main-jar openbilling.jar --main-class openbilling.Main `
    --add-launcher "openbilling-cli=$cli" `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $bild
if ($LASTEXITCODE -ne 0) { throw 'jpackage fehlgeschlagen' }

Copy-Item README.md "$bild\openbilling\LIESMICH.md"
Copy-Item LICENSE "$bild\openbilling\LICENSE.txt"
Copy-Item target\paket\DRITTANBIETER.txt "$bild\openbilling\DRITTANBIETER.txt"
New-Item -ItemType Directory -Force dist | Out-Null
$zip = "dist\openbilling-$version-windows-x64.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive "$bild\openbilling" $zip
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Set-Content "$zip.sha256" "$hash  $(Split-Path $zip -Leaf)" -Encoding ascii
Write-Host "Fertig: $zip ($([math]::Round((Get-Item $zip).Length / 1MB)) MB), SHA-256 $hash"
