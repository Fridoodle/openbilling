# Lessons Learned

Stolperfallen und ihre Lösung. Format: `## Problem` · Symptom · Ursache · Lösung · Quelle/Commit.

## Maven unter PowerShell: `-D`-Argumente mit Punkt quoten

- **Symptom:** `Unknown lifecycle phase ".txt"` bei `mvn dependency:tree -DoutputFile=out\deps.txt`.
- **Ursache:** PowerShell zerlegt ein ungequotetes Argument der Form `-Dname=wert.endung` am Punkt.
- **Lösung:** `-D`-Argumente in einfache Anführungszeichen setzen: `mvn test '-Dtest=FacturXSpikeTest'`.
- **Quelle:** Windows PowerShell 5.1, 2026-11-23.

## Nur JRE installiert: Maven kann nicht kompilieren

- **Symptom:** `mvn -version` läuft, zeigt aber `runtime: …\jre-21…`; `javac` fehlt.
- **Ursache:** `EclipseAdoptium.Temurin.21.JRE` enthält keinen Compiler.
- **Lösung:** `winget install EclipseAdoptium.Temurin.21.JDK`. Der Installer setzt `JAVA_HOME` systemweit; in einer schon offenen Shell `$env:JAVA_HOME` selbst setzen oder die Shell neu starten.
- **Quelle:** 2026-11-23.

## Mustang: Hinweis „No SLF4J providers were found“

- **Symptom:** Beim Test erscheinen SLF4J-Warnungen auf stderr.
- **Ursache:** Mustang nutzt die SLF4J-API, ohne eine Implementierung mitzubringen.
- **Lösung:** Harmlos, Logging ist dann aus. Nur wenn Mustang-Logs gebraucht werden, `org.slf4j:slf4j-simple` als Abhängigkeit ergänzen.
- **Quelle:** Mustang 2.26.0, 2026-11-23.

## KoSIT-Validator: `..` im Repository-Pfad

- **Symptom:** `IllegalStateException: Can not resolve resources/ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd in repository …/scripts/../tools/kosit/config/`, obwohl die Datei existiert.
- **Ursache:** Der Validator vergleicht den aufgelösten Artefaktpfad mit dem Repository-Pfad als Zeichenkette. Enthält `-r` ein `..`, liegt das Artefakt scheinbar „außerhalb des Repositorys“.
- **Lösung:** `-r` und `-s` immer als absolute, normalisierte Pfade übergeben (in PowerShell `Split-Path $PSScriptRoot -Parent` statt `$PSScriptRoot\..`).
- **Quelle:** Validator 1.6.3, `scripts/validate.ps1`, 2026-11-23.

## KoSIT-Validator: „Incorrect function“ bei nicht-interaktivem Aufruf (Windows)

- **Symptom:** `java.io.IOException: Incorrect function` in `Validator.isPiped` (`FileInputStream.available`), danach die Usage-Hilfe. Das passiert nur, wenn ein Agent, eine CI oder ein Skript den Validator ohne Konsole startet.
- **Ursache:** Der Validator prüft mit `System.in.available()`, ob Eingabe gepipt wird. Ist stdin das Windows-NUL-Gerät, wirft diese Methode eine Exception. Eine PowerShell-Pipe (`@() | java …`) hilft nicht: PS 5.1 schickt dann Bytes, und der Validator liest stdin als Rechnung („Error on line 1 column 1 of stdin“).
- **Lösung:** stdin auf eine **leere Datei** umleiten: `Start-Process … -RedirectStandardInput <leere Datei>`. Dann liefert `available()` 0 und die Dateiargumente werden normal verarbeitet. In Bash geht entsprechend `java … < leere-datei`.
- **Quelle:** Validator 1.6.3, `scripts/validate.ps1`, 2026-11-23.
