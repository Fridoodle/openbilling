# Entscheidungen

Kurz-ADRs. Format: `## YYYY-MM-DD — Titel` · Kontext · Entscheidung · Konsequenzen.

## 2026-11-23 — Speicherformat ODS, Export PDF mit eingebetteter XRechnung

- **Kontext:** Rechnungen sollen mit freier Office-Software bearbeitbar bleiben und als rechtssichere E-Rechnung verschickt werden.
- **Entscheidung:** Quelle der Wahrheit ist eine OpenDocument-Tabelle (`.ods`). Export erzeugt ein PDF (Ziel: PDF/A-3), in das die XRechnung-XML als Anhang eingebettet ist.
- **Konsequenzen:** ODS-Layout (Zellen/Blätter) ist ein Datenvertrag zwischen Vorlage und Parser → Änderungen an der Vorlage brauchen Anpassung im Parser. Bei B2G ist die XML rechtlich maßgeblich, nicht die PDF-Ansicht.

## 2026-11-23 — Rust, leichtgewichtig und minimalistisch

- **Kontext:** Das Tool soll klein, schnell und leicht weiterzugeben sein.
- **Entscheidung:** Implementierung in Rust. Jede neue Abhängigkeit braucht eine Begründung und wird hier eingetragen.
- **Konsequenzen:** Das Rust-Ökosystem für PDF/A-3 und Factur-X ist dünner als bei Java oder Python. Für die Einbettung ist vermutlich eigene, schlanke Logik auf einer PDF-Crate nötig (unverifiziert, vor Umsetzung prüfen). Den ODS→PDF-Schritt kann LibreOffice im Headless-Modus übernehmen; das ist dann eine externe Laufzeitabhängigkeit.

## Offen

- Libraries für ODS, XML und PDF; Bedienoberfläche (CLI, Desktop oder Web)
- Syntax der erzeugten XML: Empfehlung CII D16B als Hauptformat (nötig für die Factur-X-Einbettung), UBL optional. Noch zu bestätigen.
