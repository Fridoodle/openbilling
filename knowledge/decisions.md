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
- **Umsetzung (2026-11-23):** `openbilling.ods.RechnungOds` enthält Feldliste, Schreiber (`schreibe`, `vorlage`) und Leser (`lese`) an einer Stelle. Das Schema einer elektronischen Adresse steht unter der Kennung `BT-34-1` bzw. `BT-49-1` (Schreibweise der EN 16931 für Attribute). Zeilen ohne BT-Kennung gelten als Überschrift oder Notiz. Die berechneten Felder BT-106/109/110/112/115/131 schreibt die App zur Anzeige, liest sie aber nie. Zahlen dürfen als Zahl oder deutscher Text (`1.234,50`) stehen, Daten als Datum oder `TT.MM.JJJJ`. Der Leser sammelt alle Probleme mit Blatt, Zeile und BT und bricht nicht beim ersten ab. `tests/fixtures/minimal.ods` ist die eingecheckte Referenzdatei. Wenn `RechnungOdsTest.fixtureOdsErgibtGueltigeXRechnung` bricht, ist der Vertrag nicht mehr rückwärtskompatibel. Bibliothek: **SODS 1.10.1** (`com.github.miachm.sods`), keine Laufzeitabhängigkeiten, gepflegt (Release Juni 2026). Ob LibreOffice die von SODS geschriebenen Dateien samt Text-Format korrekt öffnet, ist noch (unverifiziert), weil auf dem Entwicklungsrechner kein LibreOffice installiert war.
- **Konsequenzen:** Der Datenvertrag besteht aus Blattnamen und BT-Kennungen, nicht aus Positionen. Rechnungen liegen in einem vom Nutzer gewählten Ordner (z. B. `Dokumente\openbilling\2026\`), dort neben der `.ods` auch das exportierte `.pdf` und `.xml`. Damit eine versendete Rechnung nicht unbemerkt neu erzeugt wird (GoBD-Aufbewahrung), verweigert `export`, wenn `.pdf` oder `.xml` schon existieren; nur mit `--ueberschreiben` geht es trotzdem. Ein Statusfeld in der ODS war geplant, ist aber unnötig, weil die vorhandenen Exportdateien denselben Zweck erfüllen.

## 2026-11-23 — Einstellungen, Stammdaten und Vorlagen im Benutzerprofil

- **Kontext:** Firmendaten, Kunden und Vorlagen gelten für alle Rechnungen und sollen nicht in jede Datei kopiert werden müssen.
- **Entscheidung:** Ablage unter `%APPDATA%\openbilling\` (später `$XDG_CONFIG_HOME/openbilling` bzw. `~/Library/Application Support/openbilling`): `einstellungen.properties` für Technisches (Rechnungsordner, XRechnung-Version für BT-24, Nummernschema, Zähler), `stammdaten.ods` (eigene Firma), `kunden.ods` (Kundenliste) und `vorlagen\` (Rechnungsvorlage `standard.ods`, PDF-Layout `layout.html`). Beim ersten Start legt die App diese Dateien aus mitgelieferten Vorgaben an.
- **Konsequenzen:** Nutzerdaten sind ODS und damit mit demselben Parser lesbar und in LibreOffice editierbar. Für `.properties` reicht die Java-Standardbibliothek. Die nächste Rechnungsnummer ist das Maximum aus Zähler und höchster vorhandener Nummer im Rechnungsordner plus eins, damit nach einer Neuinstallation keine Nummer doppelt vergeben wird.

## 2026-11-23 — PDF entsteht in der App, nicht über LibreOffice

- **Kontext:** LibreOffice headless wäre eine schwere Laufzeitabhängigkeit, seine PDF/A-Optionen sind unverifiziert, und das Ergebnis sähe aus wie eine Tabelle.
- **Entscheidung:** Rechnungsmodell → HTML-Vorlage → PDF/A-3b (openhtmltopdf, Schriften eingebettet) → Mustang bettet `xrechnung.xml` ein. Die XML wird zusätzlich separat ausgegeben. Falls schneller, dient in einer ersten Phase Mustangs eingebaute Visualisierung als Übergang (ob sie direkt PDF/A-3 liefert und anpassbar ist, ist unverifiziert).
- **Konsequenzen:** PDF und XML entstehen aus demselben Modell und können nicht voneinander abweichen. LibreOffice wird nur noch zum Bearbeiten der ODS gebraucht.
- **Nachtrag 2026-11-23 (Spike):** Der Übergangsweg funktioniert. Mustangs Visualisierung (XML → PDF über FOP) und die anschließende Einbettung ergeben ein laut veraPDF PDF/A-3u-konformes Hybrid-PDF. Phase 1 nutzt deshalb diesen Weg. Ein eigenes HTML-Layout mit openhtmltopdf kommt erst, wenn Logo oder Gestaltung angepasst werden sollen. Wie weit sich Mustangs Layout anpassen lässt, ist noch (unverifiziert).
- **Nachtrag 2026-11-23 (eigenes Layout):** Die Sichtkopie entsteht jetzt aus dem Rechnungsmodell als XSL-FO (`openbilling.pdf.Layout`) und wird mit Apache FOP gerendert (`PdfExport.sicht(Rechnung)`). Mustangs Visualisierung wird nicht mehr benutzt, Mustang bettet nur noch die XML ein. Grund: Betriebe brauchen die Rechnung auf **einer Seite**, Mustangs Ansicht war mehrseitig und zeigte technische Felder. FOP statt openhtmltopdf, weil FOP über Mustang schon vorhanden ist (keine neue Abhängigkeit); Schrift (SourceSansPro), Farbprofil und `fop-config.xconf` kommen aus dem Mustang-JAR. Regeln des Layouts:
  - Das PDF ist für Menschen, die XML für Systeme. Nicht im PDF stehen deshalb BT-23, BT-24, BT-10 (Leitweg-ID), elektronische Adressen (BT-34/BT-49) und Codes (BT-3, BT-81, Steuerkategorie). Das PDF darf weglassen, aber nichts anderes behaupten als die XML: alle Beträge kommen aus `Berechnung`.
  - Eine Seite ist Pflicht, solange die Positionen es zulassen: `PdfExport` rendert mit 10, 9 und 8 pt und nimmt die erste Größe, die eine Seite ergibt (bei 8 pt etwa 32 einzeilige Positionen). Passt keine, wird die Rechnung in 10 pt mehrseitig; der Tabellenkopf wiederholt sich, der Summenblock bleibt zusammen.
  - Aufbau: Absendername, Anschriften (Empfänger links in Höhe des Umschlagfensters), Rechnungsdaten links und Steuer-/Bankdaten rechts, Bemerkung (BT-22), Positionen, Summen, Befreiungsgrund und Zahlungsbedingungen. Eine Position mit Einheit `LS` und Menge 1 erscheint als „pauschal“ ohne Einzelpreis.
  - Mit veraPDF weiterhin PDF/A-3u-konform (`PdfExportTest`). Ein Logo und ein vom Nutzer anpassbares Layout gibt es noch nicht.

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

## 2026-11-23 — CLI und Auslieferung als JAR mit lib-Ordner

- **Kontext:** Die erste Bedienoberfläche soll klein sein; bearbeitet wird in LibreOffice.
- **Entscheidung:** `openbilling.Main` mit den Befehlen `neu`, `pruefen` und `export`, ohne CLI-Bibliothek. Exit-Codes: 0 ok, 1 Rechnung ungültig, 2 Bedienfehler. `neu` legt beim ersten Aufruf `%APPDATA%\openbilling\vorlagen\standard.ods` an und kopiert diese Vorlage. Wer dort einmal Verkäufer- und Bankdaten einträgt, hat sie in jeder neuen Rechnung (Stammdaten über die Vorlage, ohne eigene `stammdaten.ods`). `export` schreibt nur, wenn die XML exportbereit ist (ACCEPTABLE ohne Warnungen). `mvn package` erzeugt `target/openbilling.jar` mit Manifest-Classpath auf `target/lib/` (maven-jar-plugin und maven-dependency-plugin, nur Build-Plugins). Ein jpackage-Installer kommt später.
- **Konsequenzen:** `org.glassfish.jaxb:jaxb-runtime` 4.0.8 musste als Laufzeitabhängigkeit dazu, weil der KoSIT-Validator es nur optional deklariert (siehe `lessons-learned.md`). Stammdaten liegen damit vorerst in der Vorlage statt in einer eigenen Datei. Das ist einfacher, aber Änderungen an der Vorlage wirken nur auf neue Rechnungen.

## 2026-11-23 — Swing-Fenster mit FlatLaf, Entwürfe, Vorlagen und Adressbuch

- **Kontext:** Rechnungen sollen ohne LibreOffice und ohne Kommandozeile entstehen: Vorlage wählen, ausfüllen, speichern, exportieren. Gewünscht sind ein schlichtes Aussehen und ein helles sowie ein dunkles Design.
- **Entscheidung:** `openbilling.gui` mit drei Klassen: `Fenster` (Menü Datei / Adressbuch / Ansicht, Startseite mit Vorlagenliste, Prüfen und Export im Hintergrund), `Formular` (Eingabefelder und Positionstabelle) und `DatumFeld` (Datum tippen oder im Kalender anklicken, selbst gebaut, weil Swing keine Datumsauswahl hat und eine Bibliothek dafür nicht lohnt). `java -jar openbilling.jar` ohne Argumente öffnet das Fenster, mit Argumenten bleibt es die CLI.
- **Neue Abhängigkeit `com.formdev:flatlaf` 3.8:** Die Look-and-Feels des JDK haben kein dunkles Design und wirken veraltet. FlatLaf ist ein einzelnes JAR ohne eigene Abhängigkeiten und liefert hell/dunkel, Platzhaltertexte, Fehlerumrandung und die Menüleiste in der Titelzeile.
- **Entwurf statt Rechnung:** Das Formular arbeitet mit `RechnungOds.Entwurf` (BT-Kennung → Text, Positionen als Textzeilen). Ein Entwurf darf unvollständig sein, deshalb lassen sich angefangene Rechnungen und Vorlagen speichern. `RechnungOds.rechnung(Entwurf)` prüft dieselben Pflichtfelder wie `lese`. Felder, die das Formular nicht zeigt (BT-3, BT-5, BT-23, BT-24, BT-81, BT-113, BT-29, BT-149), bleiben beim Speichern unverändert erhalten. Der ODS-Datenvertrag ist unverändert.
- **Vorlagen:** Jede `*.ods` unter `%APPDATA%\openbillingorlagen\` ist eine Vorlage und erscheint auf der Startseite. „Als Vorlage speichern“ übernimmt alles außer BT-1, BT-2, BT-9 und BT-72. Eine neue Rechnung bekommt das heutige Datum; die Rechnungsnummer trägt der Nutzer von Hand ein.
- **Adressbuch:** `%APPDATA%\openbilling\kunden.ods`, Blatt `Kunden`, Kopfzeile mit BT-Kennungen (Käuferfelder plus BT-10), eine Zeile je Kunde (`openbilling.ods.Kunden`). Der Name (BT-44) ist der Schlüssel: Speichern unter gleichem Namen ersetzt den Eintrag.
- **Vereinfachungen im Formular:** Bleibt die E-Rechnungs-Adresse (BT-34/BT-49) leer, gilt die E-Mail (BT-43/BT-58). Das Schema (BT-34-1/BT-49-1) wird abgeleitet: mit `@` ist es `EM`, sonst beim Käufer `0204` (Leitweg-ID). Einheit und Steuer wählt man als Klartext (Stück = C62, Stunde = HUR, pauschal = LS; „19 %“ = S, „0 %“ = Z, „steuerfrei“ = E, „Reverse Charge“ = AE …); eigene Codes und Sätze lassen sich eintippen.
- **Exporte:** „Als PDF mit XRechnung“ prüft mit dem KoSIT-Validator und schreibt nur ein exportbereites Hybrid-PDF. „Als PDF“ schreibt nur die Sichtkopie (`PdfExport.sicht`) ohne Validator; das ist keine E-Rechnung. Anders als die CLI schreibt das Fenster keine separate `.xml`.
- **Konsequenzen:** Das Fenster findet die Validator-Konfiguration wie die CLI nur über `tools/kosit/config` im Arbeitsverzeichnis oder die Einstellung `kosit.konfiguration` (siehe „Offen“). Design und zuletzt benutzter Ordner stehen in `einstellungen.properties` (`ansicht.dunkel`, `rechnungsordner`).

## 2026-11-23 — Leistungszeitraum, Bemerkung und Positionsbeschreibung

- **Kontext:** Eine typische Rechnung eines Nutzers (Architekturbüro) nennt einen Leistungszeitraum statt eines Leistungstags, einen einleitenden Text mit Bauvorhaben und Angebot sowie mehrzeilige Beschreibungen je Position. Die Sichtkopie entsteht aus der XML, und in einem Hybrid-PDF dürfen PDF und XML inhaltlich nicht voneinander abweichen. Diese Angaben müssen deshalb in die XML und können nicht nur im PDF stehen.
- **Entscheidung:** Das Modell bekommt `Rechnung.bemerkung` (BT-22), `leistungVon`/`leistungBis` (BG-14, BT-73/74) und `Position.beschreibung` (BT-154). Die bisherigen Konstruktoren bleiben als Kurzform ohne diese Felder erhalten. CII: `ExchangedDocument/ram:IncludedNote/ram:Content` nach `IssueDateTime`, `ram:BillingSpecifiedPeriod` in `ApplicableHeaderTradeSettlement` zwischen `ApplicableTradeTax` und `SpecifiedTradePaymentTerms`, `ram:Description` nach `ram:Name` in `SpecifiedTradeProduct`. Mit dem KoSIT-Validator geprüft (`CiiSchreiberTest.leistungszeitraumBemerkungUndPositionsbeschreibung`).
- **Datenvertrag:** Blatt `Daten` hat neu die optionalen Zeilen `BT-73`, `BT-74` und `BT-22`, Blatt `Positionen` die optionale Spalte `BT-154`. Ältere Dateien ohne diese Felder bleiben lesbar (`tests/fixtures/minimal.ods` ist unverändert). Mehrzeiliger Text in einer Zelle übersteht Schreiben und Lesen mit SODS.
- **Vorgaben:** `RechnungOds.VORGABEN` ist die eine Stelle für Vorgabewerte neuer Rechnungen. Neu dabei ist BT-23 (`urn:fdc:peppol.eu:2017:poacc:billing:01:1.0`), ohne das der Validator ablehnt. Das Formular ergänzt fehlende Vorgaben für BT-3, BT-5, BT-23, BT-24 und BT-81, weil es dafür keine Eingabefelder hat. Die Verkäuferkennung (BT-29) hat jetzt ein Feld „Kennung“ beim Absender.
- **Konsequenzen:** Die Sichtkopie zeigt alle drei Angaben.

## Offen

- Validator-Konfiguration mit der App ausliefern (als Ressource im JAR oder beim ersten Start nach `%APPDATA%` entpacken).

- Automatische Rechnungsnummer (Nummernschema und Zähler). Bisher trägt man die Nummer von Hand ein.
- Bedienung des Fensters (Menüs, Dialoge, Kalender, Export) ist bisher nur offscreen gerendert und über die Tests der Datenschicht geprüft, nicht von Hand durchgeklickt (unverifiziert).
- Paketgröße: `target/lib` umfasst 63 JARs mit 43 MB, vor allem Mustang (FOP, Batik, ph-cii/ph-ubl) und Saxon. Ob sich Teile ausschließen lassen (z. B. ph-ubl, das nur für die CII→UBL-Umwandlung in Mustang nötig ist), ist noch zu prüfen (unverifiziert).
- Syntax der erzeugten XML: Empfehlung CII D16B als Hauptformat (nötig für die Factur-X-Einbettung), UBL optional. Noch zu bestätigen.
