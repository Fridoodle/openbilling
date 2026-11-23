# Lädt Apache Maven von Maven Central nach tools/maven/ (gitignored) und prüft die SHA-512-Prüfsumme.
# Ändert nichts am System (kein PATH-Eintrag). Aufruf danach: .\tools\maven\bin\mvn.cmd <ziel>
# Voraussetzung: JDK 21 (winget install EclipseAdoptium.Temurin.21.JDK)
$ErrorActionPreference = 'Stop'
$MavenVersion = '3.9.16'
# Prüfsumme gegen repo1.maven.org und archive.apache.org abgeglichen (2026-11-23)
$Sha512 = 'ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3'

$tools = Join-Path (Split-Path $PSScriptRoot -Parent) 'tools'
$dir   = Join-Path $tools 'maven'
$mvn   = Join-Path $dir 'bin\mvn.cmd'
$stamp = Join-Path $dir ".version-$MavenVersion"

if (-not (Test-Path $stamp)) {
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools "apache-maven-$MavenVersion-bin.zip"
    Invoke-WebRequest "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip" -OutFile $zip -UseBasicParsing
    $actual = (Get-FileHash $zip -Algorithm SHA512).Hash.ToLower()
    if ($actual -ne $Sha512) {
        Remove-Item $zip
        throw "SHA-512 stimmt nicht: erwartet $Sha512, erhalten $actual"
    }
    $tmp = Join-Path $tools 'maven-tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $zip -DestinationPath $tmp
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    Move-Item (Join-Path $tmp "apache-maven-$MavenVersion") $dir
    Remove-Item $tmp, $zip -Recurse -Force
    New-Item -ItemType File $stamp | Out-Null
}
Write-Host "Maven $MavenVersion bereit: $mvn"
