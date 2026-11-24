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

    /** EN 16931 ohne XRechnung-Pflichtfelder: KoSIT prüft im Szenario „EN16931 (CII)“ ohne BR-DE-Regeln. */
    @Test
    void zugferdEn16931OhneLeitwegId() throws Exception {
        var r = Testdaten.b2b();
        assertFalse(r.xrechnung());
        assertFalse(CiiSchreiber.schreibe(r).contains("BuyerReference"));
        assertExportbereit(pruefe(r, "b2b-en16931"));
    }

    @Test
    void leistungszeitraumBemerkungUndPositionsbeschreibung() throws Exception {
        var basis = Testdaten.rechnung(List.of(new Position("1", "Planung", b("1"), "LS", b("3000.00"), null,
                Steuerkategorie.S, b("19"), "Aufmaß des Bestandes\nund vorbereitende Planung")));
        var r = new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(), basis.typ(),
                basis.waehrung(), basis.faelligAm(), basis.kaeuferReferenz(), basis.zahlungsbedingungen(),
                null, basis.verkaeufer(), basis.kaeufer(), basis.zahlung(), null, null, basis.positionen(),
                "Leistungen gemäß Angebot vom 13.08.2026", java.time.LocalDate.of(2026, 8, 20),
                java.time.LocalDate.of(2026, 11, 20));
        String xml = CiiSchreiber.schreibe(r);
        assertTrue(xml.contains("<ram:StartDateTime>") && xml.contains("<ram:IncludedNote>")
                && xml.contains("<ram:Description>Aufmaß"), xml);
        assertExportbereit(pruefe(r, "zeitraum"));
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
    void rabatteZuschlaegeUndSkonto() throws Exception {
        String xml = CiiSchreiber.schreibe(Testdaten.rabattiert());
        assertTrue(xml.contains("<ram:CalculationPercent>10</ram:CalculationPercent>"), xml);
        assertTrue(xml.contains("<ram:AllowanceTotalAmount>10.50</ram:AllowanceTotalAmount>"), xml);
        assertTrue(xml.contains("<ram:ChargeTotalAmount>4.90</ram:ChargeTotalAmount>"), xml);
        assertTrue(xml.contains("#SKONTO#TAGE=10#PROZENT=2.00#\n"), xml);
        assertExportbereit(pruefe(Testdaten.rabattiert(), "rabatte"));
    }

    @Test
    void skontoOhneFreitext() throws Exception {
        var basis = Testdaten.minimal();
        var r = new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(), basis.typ(),
                basis.waehrung(), null, basis.kaeuferReferenz(), null, basis.lieferdatum(), basis.verkaeufer(),
                basis.kaeufer(), basis.zahlung(), null, null, basis.positionen())
                .mit(List.of(), new Rechnung.Skonto(14, b("3")));
        assertExportbereit(pruefe(r, "skonto"));
    }

    @Test
    void rabattUndZuschlagMitSteuerfreierPosition() throws Exception {
        var v = Testdaten.VERKAEUFER;
        var r = Testdaten.rechnung(List.of(pos("1", "1", "100.00", Steuerkategorie.S, "19"),
                pos("2", "1", "50.00", Steuerkategorie.E, "0")))
                .mit(List.of(new Rechnung.AbschlagZuschlag(false, b("10"), null, null),
                        new Rechnung.AbschlagZuschlag(true, null, b("7.50"), "Verpackung")), null);
        var mitGrund = new Rechnung(r.spezifikation(), r.prozess(), r.nummer(), r.datum(), r.typ(), r.waehrung(),
                r.faelligAm(), r.kaeuferReferenz(), r.zahlungsbedingungen(), r.lieferdatum(), v, r.kaeufer(),
                r.zahlung(), "Steuerfrei nach § 4 Nr. 21 UStG", null, r.positionen()).mit(r.abschlaegeZuschlaege(), null);
        assertExportbereit(pruefe(mitGrund, "rabatt-e"));
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
    void sepaLastschrift() throws Exception {
        var r = Testdaten.mitZahlung(Testdaten.lastschrift());
        String xml = CiiSchreiber.schreibe(r);
        assertTrue(xml.contains("<ram:CreditorReferenceID>DE98ZZZ09999999999</ram:CreditorReferenceID>"), xml);
        assertTrue(xml.contains("<ram:DirectDebitMandateID>MANDAT-0042</ram:DirectDebitMandateID>"), xml);
        assertFalse(xml.contains("PayeePartyCreditorFinancialAccount"), xml); // BR-DE-25-b
        assertExportbereit(pruefe(r, "lastschrift"));
    }

    @Test
    void lastschriftOhneFaelligkeitUndBedingungen() throws Exception {
        var b = Testdaten.mitZahlung(Testdaten.lastschrift());
        var r = new Rechnung(b.spezifikation(), b.prozess(), b.nummer(), b.datum(), b.typ(), b.waehrung(), null,
                b.kaeuferReferenz(), null, b.lieferdatum(), b.verkaeufer(), b.kaeufer(), b.zahlung(), null, null,
                b.positionen());
        assertExportbereit(pruefe(r, "lastschrift-ohne-termine"));
    }

    @Test
    void kreditkarte() throws Exception {
        var z = new Rechnung.Zahlung("54", null, null, null, null, null, null,
                new Rechnung.Karte("4242", "Erika Mustermann"));
        assertExportbereit(pruefe(Testdaten.mitZahlung(z), "kreditkarte"));
    }

    @Test
    void barUndOnlineZahlungsdienst() throws Exception {
        assertExportbereit(pruefe(Testdaten.mitZahlung(new Rechnung.Zahlung("10", null, null, null, null)), "bar"));
        var paypal = new Rechnung.Zahlung("68", "RE-2026-0001", null, null, null, "PayPal", null, null);
        assertExportbereit(pruefe(Testdaten.mitZahlung(paypal), "paypal"));
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
