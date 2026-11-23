package openbilling.pruefung;

import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prüft XRechnung-XML mit dem offiziellen KoSIT-Validator (in-process).
 * Braucht die XRechnung-Validator-Konfiguration (Ordner mit scenarios.xml), z. B. tools/kosit/config.
 * Die Konfiguration einmal laden und die Instanz wiederverwenden: Das Laden dauert Sekunden.
 */
public final class Pruefung {

    /** Stufe aus dem Schematron-Flag: fatal führt zur Ablehnung, warning und information nicht. */
    public enum Stufe { FATAL, WARNUNG, INFO }

    /**
     * @param regel Regel-ID, z. B. {@code BR-DE-15}, bei XSD-Fehlern {@code XSD}
     * @param ort   XPath oder Zeile/Spalte
     */
    public record Meldung(String regel, Stufe stufe, String text, String ort) {}

    /** {@code akzeptiert} entspricht der KoSIT-Empfehlung ACCEPTABLE. */
    public record Ergebnis(boolean akzeptiert, List<Meldung> meldungen) {
        public long anzahl(Stufe s) {
            return meldungen.stream().filter(m -> m.stufe() == s).count();
        }

        /** Exportbereit: akzeptiert und keine Warnungen (siehe knowledge/xrechnung-format.md, Abschnitt 7). */
        public boolean exportbereit() {
            return akzeptiert && anzahl(Stufe.FATAL) == 0 && anzahl(Stufe.WARNUNG) == 0;
        }
    }

    private final DefaultCheck check;

    public Pruefung(Path konfiguration) {
        Path dir = konfiguration.toAbsolutePath().normalize(); // kein ".." im Pfad, siehe lessons-learned.md
        Path szenarien = dir.resolve("scenarios.xml");
        if (!Files.exists(szenarien)) {
            throw new IllegalArgumentException("Keine Validator-Konfiguration in " + dir
                    + " (einmalig .\\scripts\\setup-validator.ps1 ausführen)");
        }
        var processor = ProcessorProvider.getProcessor();
        Configuration cfg = Configuration.load(szenarien.toUri(), dir.toUri()).build(processor);
        this.check = new DefaultCheck(processor, cfg);
    }

    public Ergebnis pruefe(String xml, String name) {
        Result res = check.checkInput(InputFactory.read(xml.getBytes(StandardCharsets.UTF_8), name));
        List<Meldung> meldungen = new ArrayList<>();
        for (String fehler : res.getProcessingErrors()) {
            meldungen.add(new Meldung("VERARBEITUNG", Stufe.FATAL, fehler, null));
        }
        for (var x : res.getSchemaViolations()) {
            meldungen.add(new Meldung("XSD", Stufe.FATAL, x.getMessage(),
                    "Zeile " + x.getRowNumber() + ", Spalte " + x.getColumnNumber()));
        }
        for (var f : res.getFailedAsserts()) {
            meldungen.add(new Meldung(f.getId(), stufe(f.getFlag()),
                    f.getText() == null ? "" : String.join("", f.getText().getContent().stream()
                            .map(String::valueOf).toList()).trim(),
                    f.getLocation()));
        }
        return new Ergebnis(res.isAcceptable(), List.copyOf(meldungen));
    }

    private static Stufe stufe(String flag) {
        if (flag == null || flag.equals("fatal") || flag.equals("error")) {
            return Stufe.FATAL;
        }
        return flag.equals("warning") ? Stufe.WARNUNG : Stufe.INFO;
    }
}
