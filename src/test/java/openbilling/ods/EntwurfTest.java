package openbilling.ods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import openbilling.model.Berechnung;
import openbilling.ods.RechnungOds.Entwurf;
import openbilling.ods.RechnungOds.OdsFehler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Entwürfe (Bedienoberfläche, Vorlagen) und Adressbuch. */
class EntwurfTest {

    private static final Path FIXTURE = Path.of("tests/fixtures/minimal.ods");

    @Test
    void entwurfErgibtDieselbeRechnungWieDieDatei(@TempDir Path tmp) throws Exception {
        var erwartet = Berechnung.berechne(RechnungOds.lese(FIXTURE));
        Entwurf e = RechnungOds.leseEntwurf(FIXTURE);
        assertEquals(erwartet, Berechnung.berechne(RechnungOds.rechnung(e)));

        Path kopie = tmp.resolve("kopie.ods");
        RechnungOds.schreibe(e, kopie);
        assertEquals(erwartet, Berechnung.berechne(RechnungOds.lese(kopie)));
        assertEquals(e.daten(), RechnungOds.leseEntwurf(kopie).daten());
    }

    /** BT-10 ist nur in der XRechnung Pflicht (BR-DE-15), nicht bei ZUGFeRD EN 16931. */
    @Test
    void leitwegIdNurBeiXRechnungPflicht() throws Exception {
        Entwurf fixture = RechnungOds.leseEntwurf(FIXTURE);
        var ohne = new java.util.HashMap<>(fixture.daten());
        ohne.remove("BT-10");
        OdsFehler f = assertThrows(OdsFehler.class,
                () -> RechnungOds.rechnung(new Entwurf(ohne, fixture.positionen())));
        assertTrue(f.probleme().stream().anyMatch(p -> p.contains("BT-10")), f.getMessage());

        ohne.put("BT-24", RechnungOds.EN16931);
        var r = RechnungOds.rechnung(new Entwurf(ohne, fixture.positionen()));
        assertEquals(null, r.kaeuferReferenz());
        assertTrue(!r.xrechnung());
    }

    @Test
    void unvollstaendigerEntwurfLaesstSichSpeichernAberNichtPruefen(@TempDir Path tmp) throws Exception {
        Entwurf e = new Entwurf(Map.of("BT-27", "Muster GmbH", "BT-38", "01067", "BT-2", "23.11.2026"),
                List.<String[]>of(new String[] {"1", "Beratung", "1,5", "HUR", "85", null, "S", "19",
                    "erste Zeile\nzweite Zeile"}));
        Path datei = tmp.resolve("vorlage.ods");
        RechnungOds.schreibe(e, datei);
        Entwurf gelesen = RechnungOds.leseEntwurf(datei);
        assertEquals(e.daten(), gelesen.daten());
        assertEquals("1,5", gelesen.positionen().get(0)[2]);
        assertEquals("erste Zeile\nzweite Zeile", gelesen.positionen().get(0)[8]);
        OdsFehler f = assertThrows(OdsFehler.class, () -> RechnungOds.rechnung(gelesen));
        assertTrue(f.probleme().stream().anyMatch(p -> p.contains("BT-1:")), f.getMessage());
    }

    @Test
    void adressbuchRundreise(@TempDir Path tmp) throws Exception {
        Path datei = tmp.resolve("kunden.ods");
        assertTrue(Kunden.lese(datei).isEmpty());
        var kunden = List.of(Map.of("BT-44", "Stadt Beispiel", "BT-53", "01067", "BT-10", "04011000-12345-06"),
                Map.of("BT-44", "Muster AG", "BT-49", "rechnung@example.com", "BT-49-1", "EM"));
        Kunden.schreibe(kunden, datei);
        assertEquals(kunden, Kunden.lese(datei));
    }
}
