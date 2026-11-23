package openbilling.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static openbilling.Testdaten.b;
import static openbilling.Testdaten.pos;

import java.util.List;
import openbilling.Testdaten;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;
import org.junit.jupiter.api.Test;

class BerechnungTest {

    private static Rechnung fixture(List<Position> positionen) {
        return Testdaten.rechnung(positionen);
    }

    @Test
    void fixtureWerte() {
        var e = Berechnung.berechne(Testdaten.minimal());

        assertEquals(List.of(b("850.00")), e.zeilenbetraege());
        assertEquals(b("850.00"), e.summeZeilen());
        assertEquals(b("850.00"), e.netto());
        assertEquals(1, e.steuergruppen().size());
        assertEquals(b("850.00"), e.steuergruppen().get(0).basis());
        assertEquals(b("161.50"), e.steuer());
        assertEquals(b("1011.50"), e.brutto());
        assertEquals(b("1011.50"), e.zahlbar());
    }

    @Test
    void zeilenbetragRundetKaufmaennisch() {
        // 3 × 0,335 = 1,005 → HALF_UP 1,01 (HALF_EVEN ergäbe 1,00)
        assertEquals(b("1.01"), Berechnung.zeilenbetrag(pos("1", "3", "0.335", Steuerkategorie.S, "19")));
    }

    @Test
    void basismengeTeiltVorDemRunden() {
        // 250 Stück, Preis 12,34 je 100 Stück → 30,85
        var p = new Position("1", "Schrauben", b("250"), "C62", b("12.34"), b("100"), Steuerkategorie.S, b("19"));
        assertEquals(b("30.85"), Berechnung.zeilenbetrag(p));
    }

    @Test
    void steuerJeGruppeNichtJeZeile() {
        // je Zeile: 0,10 × 7 % = 0,007 → 0,01, zusammen 0,02. Richtig (BR-S-09): 0,20 × 7 % = 0,014 → 0,01
        var e = Berechnung.berechne(fixture(List.of(
                pos("1", "1", "0.10", Steuerkategorie.S, "7"),
                pos("2", "1", "0.10", Steuerkategorie.S, "7"))));
        assertEquals(b("0.01"), e.steuer());
        assertEquals(b("0.21"), e.brutto());
    }

    @Test
    void gemischteSaetzeErgebenEigeneGruppen() {
        var e = Berechnung.berechne(fixture(List.of(
                pos("1", "2", "50.00", Steuerkategorie.S, "19"),
                pos("2", "1", "20.00", Steuerkategorie.S, "7"),
                pos("3", "1", "30.00", Steuerkategorie.S, "19.00"))));
        assertEquals(2, e.steuergruppen().size(), "19 und 19.00 sind dieselbe Gruppe");
        var g19 = e.steuergruppen().get(0);
        assertEquals(b("130.00"), g19.basis());
        assertEquals(b("24.70"), g19.steuer());
        var g7 = e.steuergruppen().get(1);
        assertEquals(b("20.00"), g7.basis());
        assertEquals(b("1.40"), g7.steuer());
        assertEquals(b("26.10"), e.steuer());
        assertEquals(b("176.10"), e.brutto());
    }

    @Test
    void steuerfreieKategorienHabenSteuerNull() {
        var e = Berechnung.berechne(fixture(List.of(
                pos("1", "1", "100.00", Steuerkategorie.E, "0"),
                pos("2", "1", "50.00", Steuerkategorie.AE, "0"))));
        assertEquals(b("0.00"), e.steuer());
        assertEquals(b("150.00"), e.brutto());
    }

    @Test
    void vorauszahlungMindertZahlbetrag() {
        var r = fixture(List.of(pos("1", "1", "100.00", Steuerkategorie.S, "19")));
        r = new Rechnung(r.spezifikation(), r.prozess(), r.nummer(), r.datum(), r.typ(), r.waehrung(),
                r.faelligAm(), r.kaeuferReferenz(), r.zahlungsbedingungen(), r.lieferdatum(), r.verkaeufer(),
                r.kaeufer(), r.zahlung(), r.befreiungsgrund(), b("50"), r.positionen());
        var e = Berechnung.berechne(r);
        assertEquals(b("119.00"), e.brutto());
        assertEquals(b("50.00"), e.vorausbezahlt());
        assertEquals(b("69.00"), e.zahlbar());
    }

    @Test
    void ohnePositionenUngueltig() {
        assertThrows(IllegalArgumentException.class, () -> fixture(List.of()));
    }

    @Test
    void negativerPreisUngueltig() {
        assertThrows(IllegalArgumentException.class, () -> pos("1", "1", "-1", Steuerkategorie.S, "19"));
    }
}
