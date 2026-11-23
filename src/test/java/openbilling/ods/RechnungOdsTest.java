package openbilling.ods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import openbilling.Testdaten;
import openbilling.model.Berechnung;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.ods.RechnungOds.OdsFehler;
import openbilling.pruefung.Pruefung;
import openbilling.xml.CiiSchreiber;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RechnungOdsTest {

    /** Eingecheckte Rechnungs-ODS. Bricht dieser Test, ist der Datenvertrag nicht mehr rückwärtskompatibel. */
    static final Path FIXTURE = Path.of("tests/fixtures/minimal.ods");

    private static Pruefung pruefung;

    @TempDir
    Path tmp;

    @BeforeAll
    static void laden() {
        pruefung = new Pruefung(Path.of("tools/kosit/config"));
    }

    private static void assertGleicheSummen(Rechnung erwartet, Rechnung ist) {
        var a = Berechnung.berechne(erwartet);
        var b = Berechnung.berechne(ist);
        assertEquals(a.netto(), b.netto());
        assertEquals(a.steuer(), b.steuer());
        assertEquals(a.zahlbar(), b.zahlbar());
    }

    @Test
    void schreibenUndLesen() throws Exception {
        Path datei = tmp.resolve("rechnung.ods");
        Rechnung original = Testdaten.minimal();
        RechnungOds.schreibe(original, datei);
        Rechnung gelesen = RechnungOds.lese(datei);

        assertEquals(original.nummer(), gelesen.nummer());
        assertEquals(original.datum(), gelesen.datum());
        assertEquals(original.lieferdatum(), gelesen.lieferdatum());
        assertEquals(original.kaeuferReferenz(), gelesen.kaeuferReferenz());
        assertEquals(original.verkaeufer(), gelesen.verkaeufer());
        assertEquals(original.kaeufer(), gelesen.kaeufer());
        assertEquals(original.zahlung(), gelesen.zahlung());
        assertEquals(1, gelesen.positionen().size());
        assertEquals(0, new BigDecimal("85.00").compareTo(gelesen.positionen().get(0).nettopreis()));
        assertGleicheSummen(original, gelesen);
        var e = pruefung.pruefe(CiiSchreiber.schreibe(gelesen), "aus-ods");
        assertTrue(e.exportbereit(), () -> "Meldungen: " + e.meldungen());
    }

    @Test
    void fixtureOdsErgibtGueltigeXRechnung() throws Exception {
        Rechnung r = RechnungOds.lese(FIXTURE);
        assertGleicheSummen(Testdaten.minimal(), r);
        var e = pruefung.pruefe(CiiSchreiber.schreibe(r), "fixture-ods");
        assertTrue(e.exportbereit(), () -> "Meldungen: " + e.meldungen());
    }

    @Test
    void leereVorlageMeldetAllePflichtfelder() throws Exception {
        Path datei = tmp.resolve("vorlage.ods");
        RechnungOds.vorlage(datei);
        var fehler = assertThrows(OdsFehler.class, () -> RechnungOds.lese(datei));
        String alle = String.join("\n", fehler.probleme());
        for (String k : new String[] {"BT-1:", "BT-2:", "BT-10:", "BT-27:", "BT-84:"}) {
            assertTrue(alle.contains(k), () -> k + " fehlt in:\n" + alle);
        }
        // Vorgaben aus der Vorlage zählen nicht als fehlend
        assertTrue(!alle.contains("BT-5:") && !alle.contains("BT-24:"), alle);
    }

    /** So sieht eine von Hand in LibreOffice bearbeitete Datei aus: andere Reihenfolge, Text statt Zahl. */
    @Test
    void handbearbeitetDeutscheSchreibweiseUndAndereReihenfolge() throws Exception {
        Path datei = tmp.resolve("hand.ods");
        RechnungOds.schreibe(Testdaten.minimal(), datei);
        SpreadSheet ss = new SpreadSheet(datei.toFile());
        Sheet daten = ss.getSheet(RechnungOds.BLATT_DATEN);
        daten.insertRowsBefore(0, 2); // Leerzeilen oben eingefügt
        daten.getRange(0, 0).setValue("Meine Notiz ohne BT-Kennung");
        int zeile = zeileVon(daten, "BT-2");
        daten.getRange(zeile, 1).setValue("23.11.2026");
        Sheet pos = ss.getSheet(RechnungOds.BLATT_POSITIONEN);
        pos.getRange(1, 4).setValue("85,00");
        pos.getRange(2, 0, 1, 8).setValues("2", "Reisekosten", "1", "LS", "1.234,50", null, "s", "19");
        ss.save(datei.toFile());

        Rechnung r = RechnungOds.lese(datei);
        assertEquals(LocalDate.of(2026, 11, 23), r.datum());
        assertEquals(2, r.positionen().size());
        assertEquals(0, new BigDecimal("1234.50").compareTo(r.positionen().get(1).nettopreis()));
        assertEquals(Steuerkategorie.S, r.positionen().get(1).kategorie());
        // 850,00 + 1.234,50 = 2.084,50 netto, 19 % = 396,06
        assertEquals(new BigDecimal("396.06"), Berechnung.berechne(r).steuer());
    }

    @Test
    void fehlerMitBlattUndZeile() throws Exception {
        Path datei = tmp.resolve("kaputt.ods");
        RechnungOds.schreibe(Testdaten.minimal(), datei);
        SpreadSheet ss = new SpreadSheet(datei.toFile());
        Sheet pos = ss.getSheet(RechnungOds.BLATT_POSITIONEN);
        pos.getRange(1, 2).setValue("zehn");
        pos.getRange(1, 6).setValue("X");
        ss.save(datei.toFile());

        var fehler = assertThrows(OdsFehler.class, () -> RechnungOds.lese(datei));
        String alle = String.join("\n", fehler.probleme());
        assertTrue(alle.contains("Positionen Zeile 2, BT-129: keine Zahl: 'zehn'"), alle);
        assertTrue(alle.contains("Positionen Zeile 2, BT-151: unbekannte Steuerkategorie 'X'"), alle);
    }

    @Test
    void plzBleibtText() throws Exception {
        Path datei = tmp.resolve("plz.ods");
        var basis = Testdaten.minimal();
        var v = basis.verkaeufer();
        var dresden = new Rechnung.Partei(v.name(), v.kennung(),
                new Rechnung.Adresse("Altmarkt 1", "01067", "Dresden", "DE"), v.kontakt(),
                v.elektronischeAdresse(), v.ustId(), v.steuernummer());
        RechnungOds.schreibe(new Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(), basis.datum(),
                basis.typ(), basis.waehrung(), basis.faelligAm(), basis.kaeuferReferenz(),
                basis.zahlungsbedingungen(), basis.lieferdatum(), dresden, basis.kaeufer(), basis.zahlung(),
                null, null, basis.positionen()), datei);
        assertEquals("01067", RechnungOds.lese(datei).verkaeufer().adresse().plz());
    }

    @Test
    void schlussformelUeberstehtOdsUndFehltInDerXml() throws Exception {
        Path datei = tmp.resolve("schluss.ods");
        String gruss = "Vielen Dank für die gute Zusammenarbeit.\nMit freundlichen Grüßen\nMax Mustermann";
        RechnungOds.schreibe(Testdaten.mitSchlussformel(gruss), datei);
        assertEquals(gruss, RechnungOds.lese(datei).schlussformel());
        assertEquals(gruss, RechnungOds.leseEntwurf(datei).daten().get("OB-1"));
        assertTrue(!CiiSchreiber.schreibe(RechnungOds.lese(datei)).contains("Zusammenarbeit"));
    }

    @Test
    void firmennameUeberstehtOdsUndFehltInDerXml() throws Exception {
        Path datei = tmp.resolve("firma.ods");
        RechnungOds.schreibe(Testdaten.mitFirmenname("Studio Beispiel"), datei);
        assertEquals("Studio Beispiel", RechnungOds.lese(datei).firmenname());
        assertEquals("Studio Beispiel", RechnungOds.leseEntwurf(datei).daten().get("OB-2"));
        assertTrue(!CiiSchreiber.schreibe(RechnungOds.lese(datei)).contains("Studio Beispiel"));
    }

    @Test
    void kennungAusBeschriftung() {
        assertEquals("BT-34-1", RechnungOds.kennung("BT-34-1 Schema"));
        assertEquals("BT-1", RechnungOds.kennung("  BT-1 Rechnungsnummer"));
        assertNull(RechnungOds.kennung("Verkäufer"));
        assertNull(RechnungOds.kennung("BT-12a"));
        assertEquals("OB-1", RechnungOds.kennung("OB-1 Schlussformel"));
    }

    @Test
    void fixtureVorhanden() {
        assertTrue(Files.exists(FIXTURE), "tests/fixtures/minimal.ods fehlt");
    }

    private static int zeileVon(Sheet blatt, String kennung) {
        Object[][] z = blatt.getDataRange().getValues();
        for (int i = 0; i < z.length; i++) {
            if (z[i][0] instanceof String s && kennung.equals(RechnungOds.kennung(s))) {
                return i;
            }
        }
        throw new AssertionError(kennung + " nicht gefunden");
    }
}
