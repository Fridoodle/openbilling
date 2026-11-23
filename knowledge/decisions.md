# Entscheidungen

Kurz-ADRs. Format: `## YYYY-MM-DD — Titel` · Kontext · Entscheidung · Konsequenzen.

## 2026-11-23 — Speicherformat ODS, Export PDF mit eingebetteter XRechnung

- **Kontext:** Rechnungen sollen mit freier Office-Software bearbeitbar bleiben und als rechtssichere E-Rechnung verschickt werden.
- **Entscheidung:** Quelle der Wahrheit ist eine OpenDocument-Tabelle (`.ods`). Export erzeugt ein PDF (Ziel: PDF/A-3), in das die XRechnung-XML als Anhang eingebettet ist.
- **Konsequenzen:** ODS-Layout (Zellen/Blätter) ist ein Datenvertrag zwischen Vorlage und Parser → Änderungen an der Vorlage brauchen Anpassung im Parser. Bei B2G ist die XML rechtlich maßgeblich, nicht die PDF-Ansicht.

## 2026-11-23 — ~~Rust, leichtgewichtig und minimalistisch~~ (ersetzt, siehe „Java 21 statt Rust“)

- **Kontext:** Das Tool soll klein, schnell und leicht weiterzugeben sein.
- **Entscheidung:** Implementierung in Rust. Jede neue Abhängigkeit braucht eine Begründung und wird hier eingetragen.
- **Status:** Am selben Tag durch die folgende Entscheidung ersetzt, bevor Code entstand.

## 2026-11-23 — Java 21 statt Rust

- **Kontext:** Der schwierige Teil von openbilling ist nicht das Programm selbst, sondern eine gültige XRechnung-XML, ein PDF/A-3 mit korrekter Factur-X-Einbettung (XMP-Extension-Schema, `/AF`, AFRelationship) und deren Prüfung. Die fertigen Werkzeuge dafür gibt es fast nur in Java: Mustangproject (CII, ZUGFeRD 2.5, PDF/A-3, Einbettung, Validierung) und der offizielle KoSIT-Validator. In Rust müsste die Factur-X-Einbettung selbst gebaut werden, und für die Validierung bräuchte das Programm zur Laufzeit trotzdem Java.
- **Entscheidung:** Java 21 (LTS), Build mit Maven, Auslieferung später per `jpackage` als Installer mit eigener JRE. Minimalismus gilt weiter: kein Framework (kein Spring), Swing statt JavaFX, jede Bibliothek wird hier begründet.
- **Konsequenzen:** Das Installationspaket wird größer (geschätzt 50–70 MB inklusive JRE, unverifiziert). Dafür kann die App jede XML **vor dem Export selbst mit dem KoSIT-Validator prüfen** und dem Nutzer Regel-IDs anzeigen. Geplante Bibliotheken: `org.mustangproject:library`, `org.kosit:validator`, SODS (ODS), openhtmltopdf (HTML → PDF/A-3), JUnit 5 für Tests. Jede wird beim Einbinden einzeln bestätigt.

## 2026-11-23 — ODS mit Schlüssel/Wert-Blättern statt Zellkoordinaten

- **Kontext:** Die `.ods` bleibt Quelle der Wahrheit und von Hand bearbeitbar. Ein Parser, der feste Zellen liest (z. B. `B7`), bricht, sobald jemand eine Zeile einfügt.
- **Entscheidung:** Eine Rechnungs-ODS hat das Blatt `Daten` mit zwei Spalten `Feld | Wert`, wobei das Feld mit der BT-Kennung beginnt (z. B. `BT-1 Rechnungsnummer`), und das Blatt `Positionen` mit einer Kopfzeile aus BT-Kennungen (`BT-153`, `BT-129`, `BT-130`, `BT-146`, `BT-151`, `BT-152`) und einer Zeile je Position. Ein optionales Blatt `Ansicht` ist nur für Menschen da und wird nicht gelesen. Summen und Steuern berechnet die App mit `BigDecimal` (HALF_UP, BR-CO-Regeln), nicht LibreOffice. Die App darf die berechneten Werte zur Kontrolle zurückschreiben.
- **Konsequenzen:** Der Datenvertrag besteht aus Blattnamen und BT-Kennungen, nicht aus Positionen. Rechnungen liegen in einem vom Nutzer gewählten Ordner (z. B. `Dokumente\openbilling\2026\`), dort neben der `.ods` auch das exportierte `.pdf` und `.xml`. Eine exportierte Rechnung wird im Blatt `Daten` als exportiert markiert, damit sie nicht unbemerkt geändert wird (GoBD-Aufbewahrung).

## 2026-11-23 — Einstellungen, Stammdaten und Vorlagen im Benutzerprofil

- **Kontext:** Firmendaten, Kunden und Vorlagen gelten für alle Rechnungen und sollen nicht in jede Datei kopiert werden müssen.
- **Entscheidung:** Ablage unter `%APPDATA%\openbilling\` (später `$XDG_CONFIG_HOME/openbilling` bzw. `~/Library/Application Support/openbilling`): `einstellungen.properties` für Technisches (Rechnungsordner, XRechnung-Version für BT-24, Nummernschema, Zähler), `stammdaten.ods` (eigene Firma), `kunden.ods` (Kundenliste) und `vorlagen\` (Rechnungsvorlage `standard.ods`, PDF-Layout `layout.html`). Beim ersten Start legt die App diese Dateien aus mitgelieferten Vorgaben an.
- **Konsequenzen:** Nutzerdaten sind ODS und damit mit demselben Parser lesbar und in LibreOffice editierbar. Für `.properties` reicht die Java-Standardbibliothek. Die nächste Rechnungsnummer ist das Maximum aus Zähler und höchster vorhandener Nummer im Rechnungsordner plus eins, damit nach einer Neuinstallation keine Nummer doppelt vergeben wird.

## 2026-11-23 — PDF entsteht in der App, nicht über LibreOffice

- **Kontext:** LibreOffice headless wäre eine schwere Laufzeitabhängigkeit, seine PDF/A-Optionen sind unverifiziert, und das Ergebnis sähe aus wie eine Tabelle.
- **Entscheidung:** Rechnungsmodell → HTML-Vorlage → PDF/A-3b (openhtmltopdf, Schriften eingebettet) → Mustang bettet `xrechnung.xml` ein. Die XML wird zusätzlich separat ausgegeben. Falls schneller, dient in einer ersten Phase Mustangs eingebaute Visualisierung als Übergang (ob sie direkt PDF/A-3 liefert und anpassbar ist, ist unverifiziert).
- **Konsequenzen:** PDF und XML entstehen aus demselben Modell und können nicht voneinander abweichen. LibreOffice wird nur noch zum Bearbeiten der ODS gebraucht.
- **Nachtrag 2026-11-23 (Spike):** Der Übergangsweg funktioniert. Mustangs Visualisierung (XML → PDF über FOP) und die anschließende Einbettung ergeben ein laut veraPDF PDF/A-3u-konformes Hybrid-PDF. Phase 1 nutzt deshalb diesen Weg. Ein eigenes HTML-Layout mit openhtmltopdf kommt erst, wenn Logo oder Gestaltung angepasst werden sollen. Wie weit sich Mustangs Layout anpassen lässt, ist noch (unverifiziert).

## 2026-11-23 — Abhängigkeiten des Maven-Grundgerüsts

- **`org.mustangproject:library` 2.26.0** (compile): erzeugt das PDF und bettet die XRechnung ein (siehe oben). Die Bibliothek ist schwer, denn sie zieht unter anderem Apache FOP, Batik, Saxon-HE, PDFBox und die ph-cii/ph-ubl-Bibliotheken mit. Das ist der Preis dafür, Factur-X nicht selbst zu bauen. PDFBox ist damit schon vorhanden und kann direkt genutzt werden.
- **`org.mustangproject:validator` 2.26.0** (nur test): prüft im Test PDF/A-3 mit veraPDF und die eingebettete XML.
- **JUnit 6.1.3** (nur test, über `junit-bom`).
- **Maven 3.9.16** wird nicht systemweit installiert, sondern per `scripts/setup-maven.ps1` nach `tools/maven/` geladen (SHA-512 geprüft), weil winget kein offizielles Maven-Paket hat.

## 2026-11-23 — Formatprüfung mit dem offiziellen KoSIT-Validator

- **Kontext:** Erzeugte XRechnungen müssen nachweislich gültig sein.
- **Entscheidung:** Geprüft wird mit dem offiziellen KoSIT-Validator plus der XRechnung-Konfiguration, mit festgelegten Versionen in `scripts/setup-validator.ps1`. Er läuft nur als Entwicklungs- und Testwerkzeug, nicht im ausgelieferten Rust-Binary. Eine eigene Validierungslogik wird nicht nachgebaut.
- **Konsequenzen:** Wer entwickelt, braucht Java 11+. Rust-Tests können den Validator später als Integrationstest aufrufen (`java -jar …`, stdin auf eine leere Datei umgeleitet, siehe `lessons-learned.md`).

## 2026-11-23 — CII-XML mit eigenem Schreiber, Prüfung mit KoSIT in-process

- **Kontext:** Mustang hat ein eigenes Rechnungsmodell, aus dem es CII erzeugt. Wir haben aber schon ein Modell nach BT/BG. Ein Umweg über Mustangs Modell wäre eine zweite Abbildung mit eigenen Vorgaben, die man nicht sieht (z. B. welche BT es selbst setzt).
- **Entscheidung:** `openbilling.xml.CiiSchreiber` schreibt CII D16B direkt mit `javax.xml.stream.XMLStreamWriter` (JDK, keine Bibliothek). Die Elementreihenfolge folgt der XSD-Sequenz, Vorbild ist die geprüfte Fixture. Mustang wird nur noch für Sichtkopie und Einbettung genutzt. Die Prüfung übernimmt `openbilling.pruefung.Pruefung` mit `org.kosit:validator` 1.6.3 als normale Abhängigkeit, damit die App vor dem Export selbst prüfen kann. „Exportbereit“ heißt: ACCEPTABLE und weder fatale Fehler noch Warnungen.
- **Konsequenzen:** Volle Kontrolle über jedes Element, und jede Zeile ist einem BT zugeordnet. Neue BT müssen an der richtigen Stelle der XSD-Sequenz ergänzt werden; die Tests in `CiiSchreiberTest` prüfen jede Variante mit dem KoSIT-Validator. Die Validator-Konfiguration liegt vorerst unter `tools/kosit/config`. Wie sie mit der App ausgeliefert wird, ist noch offen. Unterstützt sind bisher nur Überweisungen (BT-81 = 30/58) und keine Rabatte oder Zuschläge auf Belegebene.

## Offen

- Validator-Konfiguration mit der App ausliefern (als Ressource im JAR oder beim ersten Start nach `%APPDATA%` entpacken).

- Bedienoberfläche: geplant ist zuerst eine CLI (`neu`, `pruefen`, `export`; bearbeitet wird in LibreOffice), danach ein kleines Swing-Fenster. Noch zu bestätigen.
- Syntax der erzeugten XML: Empfehlung CII D16B als Hauptformat (nötig für die Factur-X-Einbettung), UBL optional. Noch zu bestätigen.
