# Lessons Learned

Stolperfallen und ihre Lösung. Format: `## Problem` · Symptom · Ursache · Lösung · Quelle/Commit.

## KoSIT-Validator als Bibliothek: JAXB-Laufzeit fehlt im JAR, Tests sind trotzdem grün

- **Symptom:** `java -jar target\openbilling.jar export …` bricht ab mit `Implementation of Jakarta XML Binding-API has not been found` (`ClassNotFoundException: org.glassfish.jaxb.runtime.v2.ContextFactory`). Alle Tests liefen vorher durch.
- **Ursache:** `org.kosit:validator` 1.6.3 deklariert `org.glassfish.jaxb:jaxb-runtime` als `optional`, deshalb zieht Maven es nicht mit. Im Test kam es zufällig über die Test-Abhängigkeit `org.mustangproject:validator` auf den Classpath.
- **Lösung:** `org.glassfish.jaxb:jaxb-runtime` in der Version des Validator-POMs (`version.jaxb-impl`, 4.0.8) als `runtime`-Abhängigkeit eintragen. Allgemein: Nach Änderungen an Abhängigkeiten das gebaute JAR von Hand starten, denn der Test-Classpath ist größer als der ausgelieferte.
- **Quelle:** `pom.xml`, 2026-11-23.

## Mustang 2.26.0: `ZUGFeRDVisualizer.toPDF(xml, sprache)` liefert NullPointerException

- **Symptom:** `NullPointerException: … because "fopInput" is null` in `ZUGFeRDVisualizer.toPDFfromFOP`, auch mit einer gültigen Fixture.
- **Ursache:** Fehler in der Variante, die das PDF als `byte[]` zurückgibt (in Mustang 2.26.0, Ursache nicht weiter untersucht).
- **Lösung:** Die Variante `toPDF(xmlDatei, pdfDatei, sprache)` benutzen, die in eine Datei schreibt, und die Datei danach lesen (`PdfExport`). Bei einem Mustang-Update prüfen, ob der Fehler behoben ist.
- **Quelle:** `PdfExport`, `PdfExportTest`, 2026-11-23.

## Maven unter PowerShell: `-D`-Argumente mit Punkt quoten

- **Symptom:** `Unknown lifecycle phase ".txt"` bei `mvn dependency:tree -DoutputFile=out\deps.txt`.
- **Ursache:** PowerShell zerlegt ein ungequotetes Argument der Form `-Dname=wert.endung` am Punkt.
- **Lösung:** `-D`-Argumente in einfache Anführungszeichen setzen: `mvn test '-Dtest=PdfExportTest'`.
- **Quelle:** Windows PowerShell 5.1, 2026-11-23.

## SODS: Zahlen kommen als Double, Text-Format per Data-Style "@"

- **Symptom:** Getippte Beträge liest SODS als `Double`. Eine als Zahl getippte PLZ `01067` würde zu `1067`.
- **Ursache:** ODS speichert Zahlen als `office:value-type="float"`, und SODS gibt dafür `Double` zurück. Datumszellen kommen als `LocalDate`, leere Zellen als `null`.
- **Lösung:** Beträge mit `BigDecimal.valueOf(double)` umwandeln (kürzeste Darstellung, für getippte Werte exakt), nie `new BigDecimal(double)`. Spalten für PLZ, IBAN, Steuernummer usw. mit `Style.setDataStyle("@")` als Spaltenvorgabe (`setDefaultColumnCellStyle`) auf Text stellen. SODS schreibt dann `number:text-style`, sodass neu getippte Werte Text bleiben. Formelergebnisse nie als Eingabe lesen, denn sie können Float-Reste enthalten.
- **Quelle:** SODS 1.10.1, `RechnungOds`, `RechnungOdsTest.plzBleibtText`, 2026-11-23. Verhalten in LibreOffice selbst (unverifiziert).

## PowerShell 5.1 schreibt UTF-8 mit BOM, javac scheitert daran

- **Symptom:** `illegal character: '﻿'` beim Kompilieren, nachdem eine `.java`-Datei mit `Set-Content -Encoding utf8` oder `Out-File -Encoding utf8` bearbeitet wurde.
- **Ursache:** In Windows PowerShell 5.1 bedeutet `-Encoding utf8` immer UTF-8 **mit** BOM.
- **Lösung:** Quelltext nicht über PowerShell-Cmdlets schreiben. Wenn nötig: `[IO.File]::WriteAllText($pfad, $text, (New-Object Text.UTF8Encoding $false))`.
- **Quelle:** 2026-11-23.

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

## FlatLaf: selbst gesetzte Farben gehen beim Wechsel hell/dunkel verloren

- **Symptom:** Ein Label mit `setForeground(UIManager.getColor("Label.disabledForeground"))` ist nach dem Umschalten des Designs wieder normal gefärbt; ein `LineBorder` behält die Farbe des alten Designs.
- **Ursache:** `UIManager.getColor` liefert eine `UIResource`. Beim `updateUI()` ersetzt Swing solche Farben durch die Vorgabe der Komponente. Fest übergebene Farben in Rahmen werden dagegen gar nicht aktualisiert.
- **Lösung:** Farben über FlatLaf-Stile setzen, z. B. `putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground")`, und in eigenen Rahmen oder Icons die Farbe erst in `paintBorder`/`paintIcon` aus dem `UIManager` holen. Umschalten mit `FlatDarkLaf.setup()` bzw. `FlatLightLaf.setup()` und danach `FlatLaf.updateUI()`.
- **Quelle:** FlatLaf 3.8, `openbilling.gui.Formular.gedaempft`, 2026-11-23.

## Swing-Fenster ohne Bildschirm prüfen

- **Symptom:** Ein Agent kann das Fenster nicht ansehen, ohne es auf dem Bildschirm des Nutzers zu öffnen.
- **Lösung:** Fenster erzeugen, `addNotify()`, `setSize(…)`, `validate()`, dann `getRootPane().paint(…)` in ein `BufferedImage` und als PNG speichern. Die Menüleiste ist dabei, die Titelzeile des Betriebssystems nicht. Als Einstellungsordner einen temporären Ordner übergeben (`new Einstellungen(tmp)`), damit `%APPDATA%` unberührt bleibt.
- **Quelle:** 2026-11-23.
