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
    void rabatteZuschlaegeUndSkonto() {
        var e = Berechnung.berechne(Testdaten.rabattiert());

        // Position 1: 2 × 50,00 = 100,00, davon 10 % Rabatt (BT-136/137/138)
        assertEquals(b("100.00"), e.grundbetraege().get(0));
        assertEquals(b("10.00"), e.zeilenrabatte().get(0));
        assertEquals(List.of(b("90.00"), b("20.00"), b("99.99")), e.zeilenbetraege());
        assertEquals(b("209.99"), e.summeZeilen());

        // 5 % je Steuergruppe: 19 % auf 189,99 → 9,50; 7 % auf 20,00 → 1,00
        // 4,90 Versand anteilig: 4,90 × 189,99 / 209,99 = 4,43; Rest 0,47 auf 7 %
        var p = e.belegposten();
        assertEquals(4, p.size());
        assertEquals(List.of(b("9.50"), b("1.00"), b("4.43"), b("0.47")), p.stream().map(x -> x.betrag()).toList());
        assertEquals(b("189.99"), p.get(0).basis());
        assertEquals(b("10.50"), e.abschlaege());
        assertEquals(b("4.90"), e.zuschlaege());
        assertEquals(b("204.39"), e.netto()); // BR-CO-13

        // BR-S-08: Basis je Satz = Zeilen − Abschläge + Zuschläge
        assertEquals(b("184.92"), e.steuergruppen().get(0).basis());
        assertEquals(b("35.13"), e.steuergruppen().get(0).steuer());
        assertEquals(b("19.47"), e.steuergruppen().get(1).basis());
        assertEquals(b("1.36"), e.steuergruppen().get(1).steuer());
        assertEquals(b("240.88"), e.brutto());
        assertEquals(b("4.82"), Berechnung.skonto(e, b("2")));
    }

    @Test
    void festerRabattBeiEinemSteuersatzOhneAufteilung() {
        var r = Testdaten.minimal().mit(List.of(new Rechnung.AbschlagZuschlag(false, null, b("50"), null)), null);
        var e = Berechnung.berechne(r);
        assertEquals(1, e.belegposten().size());
        assertEquals("Rabatt", e.belegposten().get(0).grund());
        assertEquals(b("800.00"), e.netto());
        assertEquals(b("152.00"), e.steuer());
    }

    @Test
    void rabattUndZuschlagBrauchenProzentOderBetrag() {
        assertThrows(IllegalArgumentException.class, () -> new Rechnung.AbschlagZuschlag(false, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Rechnung.AbschlagZuschlag(true, b("5"), b("5"), null));
        assertThrows(IllegalArgumentException.class, () -> new Position("1", "x", b("1"), "C62", b("1"), null,
                Steuerkategorie.S, b("19"), null, b("120")));
    }

    @Test
    void negativerPreisUngueltig() {
        assertThrows(IllegalArgumentException.class, () -> pos("1", "1", "-1", Steuerkategorie.S, "19"));
    }
}
