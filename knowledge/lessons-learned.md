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
- **Stand:** openbilling ruft den Visualizer seit dem eigenen Layout nicht mehr auf (`decisions.md`, „PDF entsteht in der App“). Der Eintrag bleibt für den Fall, dass er wieder gebraucht wird.
- **Quelle:** frühere Fassung von `PdfExport`, 2026-11-23.

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

## Zwei Ablehnungen, die bei Freiberuflern fast immer auftreten

- **Symptom:** Der Validator lehnt eine sonst vollständige Rechnung ab mit `PEPPOL-EN16931-R001` („Business process MUST be provided“) oder `BR-CO-26`.
- **Ursache:** XRechnung verlangt den Geschäftsprozess BT-23, obwohl er in EN 16931 optional ist. `BR-CO-26` verlangt vom Verkäufer eine USt-IdNr. (BT-31), eine Registernummer (BT-30) oder eine Verkäuferkennung (BT-29). Die Steuernummer (BT-32) allein genügt nicht, und viele Freiberufler haben nur diese.
- **Lösung:** BT-23 mit `urn:fdc:peppol.eu:2017:poacc:billing:01:1.0` vorbelegen (`RechnungOds.VORGABEN`). Ohne USt-IdNr. eine Kennung in BT-29 eintragen (Feld „Kennung“ beim Absender); der Wert ist frei wählbar.
- **Quelle:** KoSIT-Validator 1.6.3 mit XRechnung 3.0, 2026-11-23.

## Swing-Fenster ohne Bildschirm prüfen

- **Symptom:** Ein Agent kann das Fenster nicht ansehen, ohne es auf dem Bildschirm des Nutzers zu öffnen.
- **Lösung:** Fenster erzeugen, `addNotify()`, `setSize(…)`, `validate()`, dann `getRootPane().paint(…)` in ein `BufferedImage` und als PNG speichern. Die Menüleiste ist dabei, die Titelzeile des Betriebssystems nicht. Als Einstellungsordner einen temporären Ordner übergeben (`new Einstellungen(tmp)`), damit `%APPDATA%` unberührt bleibt.
- **Quelle:** 2026-11-23.

## Nativer Dateidialog statt `JFileChooser` (Windows)

- **Symptom:** Der Swing-Dialog `JFileChooser` sieht unter Windows fremd aus und kennt weder Schnellzugriff noch Explorer-Suche. Nutzer wünschen den gewohnten Windows-Dialog.
- **Irrweg:** `java.awt.FileDialog` öffnet unter Windows nur den alten, kleinen Common-Dialog (ohne Navigationsbereich, Adressleiste und Suche), weil AWT ihn mit einem Hook aufruft. Nutzer empfinden ihn als „Mini-Fenster“.
- **Lösung:** `com.formdev.flatlaf.util.SystemFileChooser` (FlatLaf ab 3.6, also schon vorhanden) zeigt den modernen Explorer-Dialog (`IFileDialog`) und unter macOS/Linux den jeweiligen Systemdialog. Die API ähnelt `JFileChooser`. Die nötigen DLLs stecken im FlatLaf-JAR. Mit `putPlatformProperty(WINDOWS_DEFAULT_EXTENSION, "ods")` hängt Windows die Endung selbst an und fragt beim Überschreiben korrekt nach. `Fenster.waehleZiel` prüft trotzdem weiter selbst, falls die Endung fehlt.
- **Quelle:** 2026-11-23, Windows 11, JDK 21.

## FOP direkt benutzen: Konfiguration und Seitenzahl

- **Aufbau:** `new FopFactoryBuilder(basisUri, new ClasspathResolverURIAdapter()).setConfiguration(new DefaultConfigurationBuilder().build(strom))` mit `fop-config.xconf` aus dem Mustang-JAR, danach denselben Resolver am `FontManager` setzen, sonst findet FOP die Schriften unter `classpath:fonts/…` nicht. PDF/A über `agent.getRendererOptions().put("pdf-a-mode", "PDF/A-3b")`. So macht es auch Mustang selbst.
- **Stolperfallen:** Die Konfiguration hat `strict-validation`, also keine leere `fo:table-body` und in jeder `fo:table-cell` ein Block. FO kennt kein „auf eine Seite verkleinern“; die Seitenzahl liefert `fop.getResults().getPageCount()` nach dem Lauf, deshalb rendert `PdfExport` notfalls mehrfach mit kleinerer Schrift. Abstände in Tabellenzeilen in `em` angeben, damit sie mit der Schrift schrumpfen.
- **Quelle:** `PdfExport`, `Layout`, 2026-11-23.

## PowerShell: `$input` ist eine automatische Variable

- **Symptom:** Ein Skript, das `$input = 'target\paket\eingabe'` setzt und danach `Copy-Item … $input` aufruft, verhält sich unberechenbar.
- **Ursache:** `$input` ist in PowerShell der Enumerator für Pipeline-Eingaben und darf nicht als eigene Variable benutzt werden. Ähnlich: `$args`, `$host`, `$error`, `$matches`.
- **Lösung:** Andere Namen wählen (in `scripts/package.ps1`: `$eingabe`).
- **Quelle:** https://learn.microsoft.com/powershell/module/microsoft.powershell.core/about/about_automatic_variables, 2026-11-23.

## PowerShell 5.1 liest `.ps1` ohne BOM als ANSI

- **Symptom:** Umlaute in Meldungen eines Skripts erscheinen als `Ã¼`.
- **Ursache:** Windows PowerShell 5.1 nimmt für Skriptdateien ohne BOM die ANSI-Codepage an. Das ist die Gegenrichtung zum Eintrag „PowerShell 5.1 schreibt UTF-8 mit BOM“: `.java`-Dateien dürfen keinen BOM haben, `.ps1`-Dateien mit Umlauten brauchen einen.
- **Lösung:** `.ps1` mit Umlauten als UTF-8 **mit** BOM speichern (`scripts/package.ps1`) oder auf Umlaute verzichten.
- **Quelle:** 2026-11-23.

## jpackage: `jdeps --print-module-deps` reicht nicht für die gekürzte Laufzeit

- **Problem:** jdeps findet nur statische Verweise. Module, die über Reflection oder ServiceLoader geladen werden, fehlen in seiner Liste und damit in der gekürzten Laufzeit. Kandidaten sind `jdk.localedata` (laut JDK-Doku liegen Gebietsschemata außer Englisch dort, also auch deutsche Zahlen- und Datumsformate), `jdk.charsets`, `jdk.crypto.ec` und `java.naming`. Ein konkreter Fehler ohne diese Module wurde nicht beobachtet, sie sind vorsorglich drin (unverifiziert, ob nötig).
- **Lösung:** jdeps-Liste plus diese Module in `scripts/package.ps1` eintragen und das entpackte Paket aus einem fremden Ordner testen (`openbilling-cli.exe export` auf eine Kopie von `tests/fixtures/minimal.ods`), damit nicht versehentlich `tools/kosit/config` im Arbeitsverzeichnis gefunden wird. jdeps braucht bei Mustangs Abhängigkeiten `--ignore-missing-deps --multi-release 21`.
- **Quelle:** 2026-11-23.

## Inno Setup: `ValueType: none` legt keinen Registry-Wert an

- **Symptom:** Nach der Installation fehlt openbilling unter „Öffnen mit“. `reg query HKCU\Software\Classes\.ods\OpenWithProgids /v openbilling.ods` findet den Wert nicht.
- **Ursache:** Bei `ValueType: none` erzeugt Inno Setup nur den Schlüssel und ignoriert `ValueName`. Windows braucht unter `OpenWithProgids` aber einen Wert mit dem Namen der ProgId.
- **Lösung:** `ValueType: string; ValueName: "openbilling.ods"; ValueData: ""` (leerer REG_SZ) mit `Flags: uninsdeletevalue`, damit beim Deinstallieren nur dieser Wert verschwindet und die Einträge anderer Programme bleiben.
- **Quelle:** https://jrsoftware.org/ishelp/topic_registrysection.htm, `packaging/openbilling.iss`, 2026-11-23.

## jpackage: ohne `--icon` zeigen Desktop und Startmenü das Java-Symbol

- **Symptom:** Das laufende Fenster und sein Taskleisten-Knopf zeigen das openbilling-Symbol, die Desktop-Verknüpfung, das Startmenü, eine angeheftete Taskleiste und „Apps“ aber das Java-Standardsymbol.
- **Ursache:** `setIconImages` setzt nur das Symbol des Fensters zur Laufzeit. Alles, was Windows ohne laufendes Programm anzeigt, liest das Symbol aus den Ressourcen von `openbilling.exe`. Ohne `--icon` setzt jpackage dort sein eigenes Standardsymbol ein.
- **Lösung:** `jpackage --icon <datei.ico>`. Der zusätzliche Starter aus `--add-launcher` (`openbilling-cli.exe`) übernimmt das Symbol des Hauptstarters, wenn seine Properties-Datei kein `icon=` angibt. Prüfen ohne Installation: `[System.Drawing.Icon]::ExtractAssociatedIcon('openbilling.exe').ToBitmap().Save('x.png')` in PowerShell. Wer das Fenster per Skript sucht: Der jpackage-Starter startet sich als Kindprozess gleichen Namens noch einmal, das Fenster gehört dem Kind, nicht dem Prozess aus `Start-Process`. Eine .ico braucht kein Grafikwerkzeug: Kopf (6 Byte), je Bild ein 16-Byte-Verzeichniseintrag, dann die Bilder als BITMAPINFOHEADER mit doppelter Höhe plus BGRA-Zeilen von unten und 1-Bit-Maske (256 px als PNG), siehe `Symbol.ico()`.
- **Quelle:** https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html (`--icon`), https://learn.microsoft.com/en-us/previous-versions/ms997538(v=msdn.10) (ICO-Format), 2026-11-23.

## ODS-Zahlen kommen als Double zurück: Prozentsätze und Beträge vor der XML normieren

- **Symptom:** Nach `RechnungOds.schreibe` und `lese` steht in der XML `10.0` statt `10` (`CalculationPercent`) und `50.0` statt `50.00` (`ChargeAmount`). Der Validator akzeptiert beides, aber ein Byte-Vergleich der XML vor und nach dem ODS-Umweg schlägt fehl.
- **Ursache:** SODS liefert Zahlzellen als `Double`; `BigDecimal.valueOf(10.0)` ergibt `10.0` mit Skala 1. Beträge, die durch `Berechnung` laufen, werden auf zwei Stellen gerundet, Eingabewerte wie Preis, Menge und Prozent nicht.
- **Lösung:** Prozentsätze in `CiiSchreiber` über `satz()` (`stripTrailingZeros`) ausgeben. Tests vergleichen nach dem ODS-Umweg Beträge und Texte, nicht die ganze XML. Für BigDecimal-Vergleiche `compareTo` statt `equals` nutzen.
- **Quelle:** `RechnungOdsTest.rabatteZuschlaegeUndSkontoUeberstehenOds`, 2026-11-23.

## Datei-Bearbeitung unter Windows: kein Python, PowerShell 5.1 schreibt BOM

- **Symptom:** `python3` startet nur den Microsoft-Store-Hinweis. `Out-File -Encoding utf8` bzw. `Set-Content -Encoding utf8` schreiben eine BOM; jshell bricht dann mit `illegal character: '﻿'` ab, und `java.util.Properties` würde die erste Zeile als Schlüssel mit BOM lesen.
- **Lösung:** Für Skripte `-Encoding ascii` oder `[IO.File]::WriteAllText(pfad, text, (New-Object Text.UTF8Encoding $false))`. Für Quelltext-Änderungen die Edit-Werkzeuge oder `sed` aus Git Bash.
- **Quelle:** eigene Erfahrung, 2026-11-23.

## Mausrad über mehrzeiligen Feldern rollt die Seite nicht (Swing)

- **Symptom:** Steht der Mauszeiger über einem mehrzeiligen Feld (`JTextArea` in einem `JScrollPane`, z. B. Schlussformel), rollt das Mausrad die Formularseite nicht mehr.
- **Ursache:** Jeder `JScrollPane` fängt das Mausrad ab, auch wenn er selbst nichts zu rollen hat. Das Ereignis erreicht den äußeren Scrollbereich nicht.
- **Lösung:** `Formular.rollbar`: `setWheelScrollingEnabled(false)` und ein eigener `MouseWheelListener`. Er rollt das Feld, solange es dort noch etwas zu rollen gibt, und reicht das Ereignis sonst mit `SwingUtilities.convertMouseEvent` an den Elternteil weiter. Von dort wandert es bis zum äußeren `JScrollPane`.
- **Quelle:** 2026-11-23, JDK 21, geprüft mit simulierten `MouseWheelEvent`s.
