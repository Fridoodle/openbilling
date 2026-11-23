# Knowledge Base

Gemeinsames Gedächtnis für alle KI-Agenten (Claude Code, Codex, Gemini, …) und Menschen in diesem Repo.
Alles, was man beim Arbeiten lernt und was **nicht** direkt aus Code oder Git-History ablesbar ist, gehört hierher.

## Regeln

- Eine Datei pro Thema, kebab-case, Markdown. Bestehende Datei erweitern statt Duplikat anlegen.
- Kopf jeder Datei: Zweck in 1–2 Sätzen + `Stand: YYYY-MM-DD`.
- Fakten mit Quelle (URL, Spec-Abschnitt, Regel-ID wie `BR-DE-15`). Ungeprüftes als `(unverifiziert)` markieren.
- Veraltetes korrigieren oder löschen, nicht nur ergänzen.
- Neue Datei → Zeile im Index unten ergänzen.
- Änderungen an `knowledge/` mit committen (eigener Commit ok: `docs(knowledge): …`).

Was hier rein gehört: Spec-Wissen (XRechnung, EN 16931, ZUGFeRD), Validator-Fehler und ihre Lösung,
Stolperfallen mit Libraries/LibreOffice, getroffene Designentscheidungen samt Begründung, Kontakte/Links.

## Index

- [xrechnung-format.md](xrechnung-format.md) — XRechnung-Standard: Version, Syntaxen (UBL/CII), Pflichtfelder (BT/BG), Codelisten, Rechenregeln, Validierung, PDF/A-3-Einbettung
- [decisions.md](decisions.md) — Architektur-/Designentscheidungen (ADR-Kurzform)
- [lessons-learned.md](lessons-learned.md) — Stolperfallen und Lösungen aus der Entwicklung
