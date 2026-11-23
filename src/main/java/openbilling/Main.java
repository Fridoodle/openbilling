package openbilling;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import openbilling.config.Einstellungen;
import openbilling.model.Berechnung;
import openbilling.model.Rechnung;
import openbilling.ods.RechnungOds;
import openbilling.ods.RechnungOds.OdsFehler;
import openbilling.pdf.PdfExport;
import openbilling.pruefung.Pruefung;
import openbilling.xml.CiiSchreiber;

/** Kommandozeile. Exit-Codes: 0 ok, 1 Rechnung ungültig, 2 Bedienfehler oder Datei nicht lesbar. */
public final class Main {

    static final int OK = 0;
    static final int UNGUELTIG = 1;
    static final int FEHLER = 2;

    private static final String HILFE = """
            openbilling – E-Rechnungen nach XRechnung aus OpenDocument-Tabellen

              (ohne Argumente)                    Fenster öffnen
              <rechnung.ods>                      Rechnung im Fenster öffnen
              --hilfe                             diese Hilfe
              neu <rechnung.ods>                  neue Rechnung aus der eigenen Vorlage anlegen
              pruefen <rechnung.ods>              XRechnung erzeugen und mit dem KoSIT-Validator prüfen
              export <rechnung.ods> [--ueberschreiben]
                                                  prüfen, dann <name>.xml und <name>.pdf (PDF/A-3 mit
                                                  eingebetteter XRechnung) neben die ODS schreiben

            Eigene Vorlage mit Stammdaten: %s
            Einstellungen: %s
            """;

    private final Einstellungen einstellungen;
    private final PrintStream out;
    private final PrintStream err;

    Main(Einstellungen einstellungen, PrintStream out, PrintStream err) {
        this.einstellungen = einstellungen;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) throws IOException {
        // ohne Argumente oder mit nur einer Datei (Doppelklick, „Öffnen mit“): Fenster
        boolean nurDatei = args.length == 1 && !args[0].startsWith("-") && Files.isRegularFile(Path.of(args[0]));
        if ((args.length == 0 || nurDatei) && !java.awt.GraphicsEnvironment.isHeadless()) {
            openbilling.gui.Fenster.starte(Einstellungen.ausBenutzerprofil(), nurDatei ? Path.of(args[0]) : null);
            return;
        }
        var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        var err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(new Main(Einstellungen.ausBenutzerprofil(), out, err).ausfuehren(args));
    }

    int ausfuehren(String... args) {
        List<String> a = new ArrayList<>(List.of(args));
        boolean ueberschreiben = a.remove("--ueberschreiben");
        if (a.size() != 2) {
            out.printf(HILFE, einstellungen.vorlage(), einstellungen.ordner().resolve(Einstellungen.DATEI));
            return a.isEmpty() || a.contains("--hilfe") ? OK : FEHLER;
        }
        Path datei = Path.of(a.get(1));
        try {
            return switch (a.get(0)) {
                case "neu" -> neu(datei);
                case "pruefen" -> pruefen(datei) == null ? UNGUELTIG : OK;
                case "export" -> export(datei, ueberschreiben);
                default -> {
                    err.println("Unbekannter Befehl: " + a.get(0) + " (siehe openbilling --hilfe)");
                    yield FEHLER;
                }
            };
        } catch (OdsFehler e) {
            err.println(datei + " ist unvollständig oder nicht lesbar:");
            e.probleme().forEach(p -> err.println("  " + p));
            return FEHLER;
        } catch (IOException | IllegalArgumentException e) {
            err.println("Fehler: " + e.getMessage());
            return FEHLER;
        }
    }

    private int neu(Path ziel) throws IOException {
        if (Files.exists(ziel)) {
            err.println(ziel + " existiert schon, nichts geändert.");
            return FEHLER;
        }
        if (einstellungen.vorlageAnlegen()) {
            out.println("Vorlage angelegt: " + einstellungen.vorlage());
            out.println("  Tipp: Dort einmal Verkäufer- und Bankdaten eintragen, dann sind sie in jeder neuen Rechnung.");
        }
        if (ziel.toAbsolutePath().getParent() != null) {
            Files.createDirectories(ziel.toAbsolutePath().getParent());
        }
        Files.copy(einstellungen.vorlage(), ziel);
        out.println("Neue Rechnung: " + ziel + " (in LibreOffice ausfüllen, dann 'pruefen')");
        return OK;
    }

    /** Liefert die geprüfte XML oder {@code null}, wenn die Rechnung nicht exportbereit ist. */
    private String pruefen(Path datei) throws IOException {
        Rechnung r = RechnungOds.lese(datei);
        var e = Berechnung.berechne(r);
        out.printf("%s: %d Position(en), netto %s, USt %s, zahlbar %s %s%n", r.nummer(), r.positionen().size(),
                e.netto(), e.steuer(), e.zahlbar(), r.waehrung());
        String xml = CiiSchreiber.schreibe(r);
        var ergebnis = new Pruefung(einstellungen.kositKonfiguration()).pruefe(xml, datei.getFileName().toString());
        for (var m : ergebnis.meldungen()) {
            out.printf("  %-7s %s: %s%n", m.stufe(), m.regel(), m.text());
        }
        if (ergebnis.exportbereit()) {
            out.println("Gültige XRechnung (KoSIT-Validator: ACCEPTABLE, keine Warnungen).");
            return xml;
        }
        out.println(ergebnis.akzeptiert()
                ? "Akzeptiert, aber mit Warnungen. Bitte beheben, dann erneut prüfen."
                : "Abgelehnt. Bitte die Meldungen oben beheben.");
        return null;
    }

    private int export(Path datei, boolean ueberschreiben) throws IOException {
        String name = datei.getFileName().toString().replaceFirst("(?i)\\.ods$", "");
        Path xmlDatei = datei.resolveSibling(name + ".xml");
        Path pdfDatei = datei.resolveSibling(name + ".pdf");
        if (!ueberschreiben && (Files.exists(xmlDatei) || Files.exists(pdfDatei))) {
            err.println(pdfDatei + " bzw. " + xmlDatei + " existiert schon. Eine versendete Rechnung darf nicht"
                    + " verändert werden (GoBD). Bewusst neu erzeugen mit --ueberschreiben.");
            return FEHLER;
        }
        String xml = pruefen(datei);
        if (xml == null) {
            return UNGUELTIG;
        }
        Files.writeString(xmlDatei, xml, StandardCharsets.UTF_8);
        Files.write(pdfDatei, PdfExport.hybrid(RechnungOds.lese(datei), xml));
        out.println("Geschrieben: " + xmlDatei);
        out.println("Geschrieben: " + pdfDatei + " (PDF/A-3 mit eingebetteter xrechnung.xml)");
        return OK;
    }
}
