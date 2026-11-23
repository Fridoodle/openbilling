package openbilling.xml;

import static openbilling.Testdaten.b;
import static openbilling.Testdaten.pos;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import openbilling.Testdaten;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Partei;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.pruefung.Pruefung;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Jede erzeugte XML muss den KoSIT-Validator bestehen (CLAUDE.md).
 * Braucht tools/kosit/config (einmalig .\scripts\setup-validator.ps1).
 * Die erzeugten Dateien liegen danach in out/cii/ zur Gegenprobe mit scripts/validate.ps1.
 */
class CiiSchreiberTest {

    private static Pruefung pruefung;
    private static final Path OUT = Path.of("out/cii");

    @BeforeAll
    static void laden() throws Exception {
        pruefung = new Pruefung(Path.of("tools/kosit/config"));
        Files.createDirectories(OUT);
    }

    private static Pruefung.Ergebnis pruefe(Rechnung r, String name) throws Exception {
        String xml = CiiSchreiber.schreibe(r);
        Files.writeString(OUT.resolve(name + ".xml"), xml);
        return pruefung.pruefe(xml, name);
    }

    private static void assertExportbereit(Pruefung.Ergebnis e) {
        assertTrue(e.exportbereit(), () -> "Meldungen: " + e.meldungen());
    }

    @Test
    void minimalrechnungWieFixture() throws Exception {
        assertExportbereit(pruefe(Testdaten.minimal(), "minimal"));
    }

    @Test
    void gemischteSaetzeUndBasismenge() throws Exception {
        var r = Testdaten.rechnung(List.of(
                pos("1", "2", "50.00", Steuerkategorie.S, "19"),
                pos("2", "1", "20.00", Steuerkategorie.S, "7"),
                new Position("3", "Schrauben", b("250"), "C62", b("12.34"), b("100"), Steuerkategorie.S, b("19"))));
        assertExportbereit(pruefe(r, "gemischt"));
    }

    @Test
    void kleinunternehmerNachParagraph19() throws Exception {
        var v = Testdaten.VERKAEUFER;
        // keine USt-IdNr. (BT-31): dann verlangt BR-CO-26 eine Verkäuferkennung (BT-29) oder BT-30
        var kleinunternehmer = new Partei(v.name(), "KU-4711", v.adresse(), v.kontakt(),
                v.elektronischeAdresse(), null, "30/123/45678");
        var basis = Testdaten.rechnung(List.of(pos("1", "3", "40.00", Steuerkategorie.E, "0")));
        var r = new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(), basis.typ(),
                basis.waehrung(), basis.faelligAm(), basis.kaeuferReferenz(), basis.zahlungsbedingungen(),
                basis.lieferdatum(), kleinunternehmer, basis.kaeufer(), basis.zahlung(),
                "Kein Ausweis von Umsatzsteuer, da Kleinunternehmer gemäß § 19 UStG",
                null, basis.positionen());
        assertExportbereit(pruefe(r, "kleinunternehmer"));
    }

    @Test
    void nurSteuernummerOhneKennungVerletztBrCo26() throws Exception {
        var v = Testdaten.VERKAEUFER;
        var ohneKennung = new Partei(v.name(), null, v.adresse(), v.kontakt(), v.elektronischeAdresse(),
                null, "30/123/45678");
        var basis = Testdaten.minimal();
        var r = new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(), basis.typ(),
                basis.waehrung(), basis.faelligAm(), basis.kaeuferReferenz(), basis.zahlungsbedingungen(),
                basis.lieferdatum(), ohneKennung, basis.kaeufer(), basis.zahlung(), null, null, basis.positionen());
        var e = pruefe(r, "ohne-bt29");
        assertFalse(e.akzeptiert());
        assertTrue(e.meldungen().stream().anyMatch(m -> m.regel().equals("BR-CO-26")),
                () -> "BR-CO-26 erwartet, erhalten: " + e.meldungen());
    }

    @Test
    void fehlendeLeitwegIdWirdAbgelehnt() throws Exception {
        var basis = Testdaten.minimal();
        var r = new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(), basis.typ(),
                basis.waehrung(), basis.faelligAm(), "", basis.zahlungsbedingungen(), basis.lieferdatum(),
                basis.verkaeufer(), basis.kaeufer(), basis.zahlung(), null, null, basis.positionen());
        var e = pruefe(r, "ohne-bt10");
        assertFalse(e.akzeptiert());
        assertTrue(e.meldungen().stream().anyMatch(m -> m.regel().equals("BR-DE-15")),
                () -> "BR-DE-15 erwartet, erhalten: " + e.meldungen());
    }
}
