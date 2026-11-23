# openbilling

E-Rechnungen nach **XRechnung** erstellen, ohne Abo und ohne Cloud. openbilling speichert jede Rechnung als OpenDocument-Tabelle (`.ods`) und exportiert sie als PDF mit eingebetteter XRechnung, so wie ZUGFeRD/Factur-X. Jede XML wird vor dem Export mit dem offiziellen KoSIT-Validator geprüft.

- **Rechnungen als `.ods`:** Die Tabelle ist die Quelle der Wahrheit und lässt sich auch in LibreOffice oder OpenOffice bearbeiten.
- **Export als Hybrid-PDF:** Eine lesbare Rechnung auf einer Seite (PDF/A-3) mit der XRechnung-XML (UN/CEFACT CII, XRechnung 3.0) als Anhang.
- **Geprüft vor dem Versand:** Exportiert wird nur, was der KoSIT-Validator akzeptiert. Fehler zeigt das Programm mit Regel-ID (z. B. `BR-DE-15`).
- **Vorlagen und Adressbuch:** Eigene Daten einmal in eine Vorlage eintragen, Kunden im Adressbuch speichern.
- **Fenster und Kommandozeile:** Hell- und Dunkelmodus, dazu eine CLI für Skripte.

> **Stand:** Version 0.1, frühe Phase. Unterstützt sind Rechnungen mit Überweisung, Rabatten auf Positionen und auf die ganze Rechnung, Zuschlägen (z. B. Versand) und Skonto. Lastschrift fehlt noch. Prüfe die ersten Rechnungen gegen, bevor du dich darauf verlässt.

## Installation (Windows)

Unter [Releases](https://github.com/Fridoodle/openbilling/releases) gibt es zwei Varianten. Java muss in keinem Fall installiert sein, beide bringen ihre eigene Laufzeit mit. Deine Einstellungen, Vorlagen und das Adressbuch liegen unter `%APPDATA%\openbilling\` und bleiben bei Updates und beim Deinstallieren erhalten.

**Installer (empfohlen):** `openbilling-<version>-windows-x64-setup.exe` herunterladen und starten.

- Installiert ohne Administratorrechte nach `%LOCALAPPDATA%\Programs\openbilling` (auf Wunsch für alle Benutzer) und legt einen Eintrag im Startmenü an, wahlweise auch auf dem Desktop.
- Bietet openbilling für `.ods`-Dateien unter **Öffnen mit** an. Die Standardzuordnung, meist LibreOffice, bleibt unverändert. Wer `.ods` per Doppelklick in openbilling öffnen will, wählt dort einmal **Immer diese App verwenden**.
- **Update:** Den Installer der neuen Version starten. Er ersetzt die alte Version im selben Ordner, ein laufendes openbilling wird vorher geschlossen.
- **Deinstallieren:** Windows-Einstellungen → Apps → Installierte Apps → openbilling → Deinstallieren.

**Portabel:** `openbilling-<version>-windows-x64.zip` herunterladen, entpacken (zum Beispiel auf einen USB-Stick) und `openbilling.exe` starten. Diese Variante schreibt nichts in die Registry. Deinstallieren heißt: Ordner löschen, Update heißt: neue Version entpacken und den alten Ordner löschen.

### Windows-Warnung beim ersten Start

Die Programmdateien sind noch nicht digital signiert. Windows SmartScreen zeigt deshalb beim ersten Start „Der Computer wurde durch Windows geschützt“. Klicke auf **Weitere Informationen → Trotzdem ausführen**. Das ist bei neuen Open-Source-Programmen ohne Signatur üblich.

Wer sichergehen will, vergleicht die Prüfsumme der heruntergeladenen Datei mit der zugehörigen `.sha256` im Release:

```powershell
Get-FileHash openbilling-0.1.3-windows-x64-setup.exe -Algorithm SHA256
```

Meldet ein Virenscanner das Programm, ist das sehr wahrscheinlich ein Fehlalarm. Bitte melde es als [Issue](https://github.com/Fridoodle/openbilling/issues) mit dem Namen des Scanners.

## Bedienung

### Fenster

`openbilling.exe` öffnet die Startseite mit deinen Vorlagen.

1. Beim ersten Mal eine Rechnung aus der Standardvorlage anlegen, deine Firmen- und Bankdaten eintragen und über **Datei → Als Vorlage speichern** sichern. Ab dann hat jede neue Rechnung diese Daten.
2. Neue Rechnung aus der Vorlage erstellen, Kunde und Positionen eintragen. Pflichtfelder sind mit `*` markiert. Die Rechnungsnummer schlägt openbilling vor: die zuletzt exportierte plus eins, im neuen Jahr wieder ab 1.
   Rabatt auf eine Position gibst du in der Spalte **Rabatt %** ein. Rabatt und Zuschlag auf die ganze Rechnung (in Prozent oder als Betrag, z. B. Versand) sowie Skonto stehen unter **Rabatt, Zuschlag und Skonto**. Bei mehreren Steuersätzen verteilt openbilling Rabatt und Zuschlag anteilig.
3. **Datei → Speichern** legt die `.ods` ab, **Datei → Als PDF mit XRechnung exportieren** (Strg+E) prüft und schreibt das Hybrid-PDF.

Auch mitten in einer Rechnung geht **Datei → Neu aus Vorlage** (neue Rechnung aus einer Vorlage) und **Datei → Vorlage bearbeiten** (die Vorlage selbst öffnen; Speichern ändert dann die Vorlage). Ungespeicherte Änderungen fragt openbilling vorher ab.

**Rechnung per Doppelklick öffnen:** Rechtsklick auf eine `.ods` → **Öffnen mit** → **Andere App auswählen** → **Nach einer App auf dem PC suchen** → `openbilling.exe` wählen. Mit „Immer“ öffnen alle `.ods` in openbilling, sonst bleibt LibreOffice der Standard und openbilling steht im Menü „Öffnen mit“. `openbilling.exe rechnung.ods` geht genauso.

### Kommandozeile

`openbilling-cli.exe` liegt im selben Ordner:

```powershell
.\openbilling-cli.exe neu rechnung.ods       # neue Rechnung aus der eigenen Vorlage
.\openbilling-cli.exe pruefen rechnung.ods   # XRechnung erzeugen und prüfen
.\openbilling-cli.exe export rechnung.ods    # rechnung.xml und rechnung.pdf daneben schreiben
.\openbilling-cli.exe --hilfe
```

Exit-Codes: `0` in Ordnung, `1` Rechnung ungültig, `2` Bedienfehler oder ODS unvollständig. Ein bestehendes PDF oder XML überschreibt `export` nur mit `--ueberschreiben`, damit eine versendete Rechnung nicht unbemerkt neu entsteht.

### Wo liegt was?

| Was | Wo |
| --- | --- |
| Rechnungen (`.ods`, `.pdf`, `.xml`) | in einem Ordner deiner Wahl |
| Vorlagen | `%APPDATA%\openbilling\vorlagen\*.ods` |
| Adressbuch | `%APPDATA%\openbilling\kunden.ods` |
| Einstellungen | `%APPDATA%\openbilling\einstellungen.properties` |

## Selbst bauen

Voraussetzung ist ein JDK 21, zum Beispiel `winget install EclipseAdoptium.Temurin.21.JDK`.

```powershell
.\scripts\setup-maven.ps1        # einmalig: Maven nach tools\maven\
.\scripts\setup-validator.ps1    # einmalig: KoSIT-Validator und XRechnung-Konfiguration nach tools\kosit\
.\tools\maven\bin\mvn.cmd test   # Tests
.\tools\maven\bin\mvn.cmd package
java -jar target\openbilling.jar
```

Das portable Paket baut `.\scripts\package.ps1` nach `dist\` (mit `jpackage` aus dem JDK).

Wie das Projekt aufgebaut ist und welche Entscheidungen dahinterstehen, steht in [`CLAUDE.md`](CLAUDE.md) und unter [`knowledge/`](knowledge/README.md), vor allem in [`knowledge/xrechnung-format.md`](knowledge/xrechnung-format.md) und [`knowledge/decisions.md`](knowledge/decisions.md).

## Verwendete Software

- [Mustangproject](https://www.mustangproject.org/): PDF/A-3 und Einbettung der XML (Apache-2.0)
- [KoSIT-Validator](https://github.com/itplr-kosit/validator) mit der [XRechnung-Konfiguration](https://github.com/itplr-kosit/validator-configuration-xrechnung) (Apache-2.0)
- [SODS](https://github.com/miachm/SODS): ODS lesen und schreiben (Unlicense)
- [FlatLaf](https://www.formdev.com/flatlaf/): Aussehen der Oberfläche (Apache-2.0)

Das Windows-Paket enthält außerdem eine Java-Laufzeit von [Eclipse Temurin](https://adoptium.net/) (GPL-2.0 mit Classpath Exception). Alle Bibliotheken samt Lizenz stehen im Paket in `DRITTANBIETER.txt`.

## Lizenz

openbilling ist freie Software unter der [GNU General Public License v3.0](LICENSE). Du darfst das Programm nutzen, auch gewerblich, es verändern und weitergeben. Wer eine veränderte Fassung weitergibt, muss sie ebenfalls unter der GPL-3.0 mit Quelltext weitergeben.

Die Rechnungen, die du mit openbilling erstellst, gehören dir. Die Lizenz gilt für das Programm, nicht für deine Daten.
