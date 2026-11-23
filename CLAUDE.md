# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projekt

**openbilling** — Open-Source-Tool zum Erstellen von E-Rechnungen nach **XRechnung** (deutsche CIUS von EN 16931).

- **Speichern:** Rechnungen sind OpenDocument-Tabellen (`.ods`, LibreOffice/OpenOffice Calc). Die `.ods` ist die Quelle der Wahrheit und bleibt von Hand bearbeitbar.
- **Exportieren:** fertiges PDF (Ziel PDF/A-3) mit der XRechnung-XML als eingebettetem Anhang (hybrid wie ZUGFeRD/Factur-X).
- Repo: https://github.com/Fridoodle/openbilling · Hauptbranch `main`

Datenfluss (Zielbild):

```
.ods (Vorlage + Eingaben) → Parser → Rechnungsmodell (BT/BG nach EN 16931)
                                         ├→ XRechnung-XML → KoSIT-Validator
                                         └→ PDF-Ansicht → PDF/A-3 + XML-Anhang
```

Das Rechnungsmodell ist die zentrale Schicht: ODS-Parser und XML-/PDF-Generatoren hängen nur daran, nicht direkt aneinander. Das Zellen-/Blattlayout der ODS-Vorlage ist ein **Datenvertrag**: Wer die Vorlage ändert, passt den Parser und die Tests gleich mit an.

## Stack und Leitlinie: Rust, leichtgewichtig, minimalistisch

- Sprache ist **Rust** (stable, Cargo). Ziel ist ein kleines, schnelles Programm ohne Laufzeitabhängigkeiten außer denen, die wirklich nötig sind.
- **Minimalismus ist eine Anforderung, keine Kür:**
  - Jede neue Crate braucht einen Grund. Erst prüfen, ob `std` oder eine schon vorhandene Crate reicht. Bei Crates schlanke Features wählen (`default-features = false`).
  - Kein Framework, keine unnötigen Abstraktionsschichten, keine Traits und Generics auf Vorrat.
  - Eine schlichte Bedienoberfläche geht vor einer aufwendigen.
- Neue Abhängigkeiten und ihren Grund in `knowledge/decisions.md` festhalten.
- Das Projekt hat noch keinen Code. Beim Anlegen `cargo new` verwenden. Weicht der Aufbau von den Befehlen unten ab (zum Beispiel bei einem Workspace), aktualisiere diesen Abschnitt.

```sh
cargo build                      # Build
cargo run -- <args>              # Programm starten
cargo test                       # alle Tests
cargo test <name>                # einzelner Test (Substring des Testnamens)
cargo test <name> -- --nocapture # mit println-Ausgabe
cargo clippy -- -D warnings      # Lint (muss sauber sein)
cargo fmt                        # Formatieren (vor jedem Commit)
cargo build --release            # schlankes Release-Binary
```

## Fachwissen: zuerst `knowledge/` lesen

Bevor du an XML-Erzeugung, Rechenlogik oder PDF-Export arbeitest, lies `knowledge/xrechnung-format.md`. Dort stehen die Pflichtfelder (BT/BG), Codelisten, Rundungsregeln (BR-CO-*), die deutschen Zusatzregeln (BR-DE-*), die Validierung und die PDF-Einbettung. Die Spezifikation ist maßgeblich, nicht die Intuition: Beträge, Steuerkategorien und Pflichtfelder müssen den Schematron-Regeln genügen.

Kernpunkte (Stand 2026-11, Details und Quellen in der Datei):
- Gültig ist **XRechnung 3.0.x**. XRechnung 4.0 gibt es bisher nur als Vorabversion. Die Version (CustomizationID BT-24) deshalb konfigurierbar halten, nicht fest im Code verdrahten.
- Primäre Syntax ist **UN/CEFACT CII**, denn nur CII lässt sich als Factur-X/ZUGFeRD (Profil XRECHNUNG) ins PDF einbetten. UBL bleibt ein optionaler Zusatzexport.
- Punkte mit `(unverifiziert)` erst gegen die Primärquelle prüfen, bevor du darauf aufbaust.

Jede erzeugte XML muss den **KoSIT-Validator** bestehen (Java 11+). Ein Feature gilt erst als fertig, wenn das der Fall ist.

## Knowledge Base pflegen (Pflicht für alle Agenten)

`knowledge/` ist das gemeinsame Gedächtnis für alle KI-Agenten und Menschen. Regeln und Index stehen in `knowledge/README.md`.

- Wenn du etwas Nicht-Offensichtliches lernst (eine Validator-Regel und ihren Fix, eine Library-Stolperfalle, ein LibreOffice-Exportflag, eine Spec-Auslegung), trage es **in derselben Aufgabe** ein: in `lessons-learned.md`, in `decisions.md` oder in eine passende Themendatei.
- Bestehende Einträge aktualisieren statt Duplikate anlegen. Falsches löschen.
- Quellen angeben (URL, Regel-ID). Ungeprüftes als `(unverifiziert)` markieren.

## Sessions

Eine Session gilt für eine zusammenhängende Aufgabe. Wenn der Nutzer mitten in der Arbeit mit etwas fachfremdem oder unabhängigem kommt, **erinnere ihn daran, dafür eine neue Session zu starten**. Das hält den Kontext klein und die Antworten präzise. Kleine Rückfragen und direkte Folgeaufgaben bleiben in der laufenden Session.

## Git

- Arbeite mit git und committe in kleinen, thematischen Commits (Conventional Commits: `feat:`, `fix:`, `docs(knowledge):`, `test:` …).
- Größere Arbeit auf Feature-Branches, nicht direkt auf `main`.
- Echte Kundendaten oder Rechnungen gehören nicht ins Repo. Testrechnungen kommen als Fixtures unter `tests/fixtures/`.

## Skills / Plugins

`.claude/settings.json` aktiviert zwei Plugins für alle, die das Repo öffnen. Claude Code fragt beim ersten Öffnen, ob man den Marketplaces vertraut.

- **ponytail** ([DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)): standardmäßig verwenden. Minimal-Code-Prinzip: erst Vorhandenes, dann die Standardbibliothek, dann bewährte Libraries, eigener Code zuletzt. Gerade hier zählt das: Für Factur-X/ZUGFeRD-Einbettung, ODS-Lesen und Validierung gibt es fertige Werkzeuge (siehe Knowledge Base), die du nicht nachbauen sollst.
- **caveman** ([JuliusBrussee/caveman](https://github.com/JuliusBrussee/caveman)): standardmäßig verwenden für knappe Antworten und Commit-Messages. Ausnahme: Inhalte in `knowledge/` und Nutzer-Dokumentation schreibst du in vollständigen, verständlichen Sätzen, denn sie müssen für andere Agenten eindeutig sein.
