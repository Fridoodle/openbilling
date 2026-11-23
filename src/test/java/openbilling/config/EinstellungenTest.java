package openbilling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EinstellungenTest {

    @TempDir
    Path tmp;

    @Test
    void naechsteNummerZaehltWeiterUndBehaeltStellen() {
        assertEquals("2026-001", Einstellungen.naechste(null, 2026));
        assertEquals("RE-2026-0008", Einstellungen.naechste("RE-2026-0007", 2026));
        assertEquals("2026-100", Einstellungen.naechste("2026-099", 2026));
        assertEquals("1000", Einstellungen.naechste("999", 2026));
        assertEquals("R-12/B", Einstellungen.naechste("R-11/B", 2026));
        assertEquals("Rechnung-1", Einstellungen.naechste("Rechnung", 2026));
    }

    @Test
    void neuesJahrBeginntBeiEins() {
        assertEquals("RE-2027-0001", Einstellungen.naechste("RE-2026-0042", 2027));
        assertEquals("2027-001", Einstellungen.naechste("2026-117", 2027));
    }

    @Test
    void merktNurHoehereNummernDesselbenSchemas() throws Exception {
        var e = new Einstellungen(tmp);
        LocalDate heute = LocalDate.of(2026, 11, 23);
        assertEquals("2026-001", e.naechsteNummer(heute));
        e.merkeNummer("RE-2026-0005");
        e.merkeNummer("RE-2026-0003"); // alte Rechnung erneut exportiert
        assertEquals("RE-2026-0006", e.naechsteNummer(heute));
        assertEquals("RE-2026-0006", new Einstellungen(tmp).naechsteNummer(heute)); // gespeichert
        e.merkeNummer("A-1"); // neues Schema
        assertEquals("A-2", e.naechsteNummer(heute));
    }
}
