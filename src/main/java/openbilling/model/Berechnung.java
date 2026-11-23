package openbilling.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openbilling.model.Rechnung.AbschlagZuschlag;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;

/**
 * Summen und Steuern nach EN 16931 (BR-CO-*, BR-S-08/09 und Analoga).
 * Beträge auf 2 Nachkommastellen, kaufmännisch gerundet (HALF_UP).
 * Die Steuer wird je (Kategorie, Satz) berechnet, nicht je Zeile.
 *
 * <p>Abschläge und Zuschläge auf die ganze Rechnung (BG-20/21) hängen im Modell an keiner Steuergruppe. Sie werden
 * hier auf die Steuergruppen der Positionen verteilt: in Prozent je Gruppe auf deren Positionssumme, ein fester
 * Betrag anteilig nach den Positionssummen (Rundungsrest auf die letzte Gruppe). Jede Gruppe ergibt einen eigenen
 * BG-20/21-Eintrag mit Kategorie und Satz (BR-32/BR-37).
 */
public final class Berechnung {

    private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

    private Berechnung() {}

    /**
     * BG-23 Steueraufschlüsselung.
     *
     * @param kategorie BT-118
     * @param satz      BT-119
     * @param basis     BT-116
     * @param steuer    BT-117
     */
    public record Steuergruppe(Steuerkategorie kategorie, BigDecimal satz, BigDecimal basis, BigDecimal steuer) {}

    /**
     * Ein BG-20-Abschlag oder BG-21-Zuschlag, wie er in der XML steht.
     *
     * @param zuschlag  {@code true} BG-21, {@code false} BG-20
     * @param kategorie BT-95 / BT-102
     * @param satz      BT-96 / BT-103
     * @param prozent   BT-94 / BT-101, nur bei prozentualen Einträgen
     * @param basis     BT-93 / BT-100, nur bei prozentualen Einträgen
     * @param betrag    BT-92 / BT-99
     * @param grund     BT-97 / BT-104
     * @param quelle    Index des Eintrags in {@link Rechnung#abschlaegeZuschlaege()}, aus dem er stammt
     */
    public record Belegposten(boolean zuschlag, Steuerkategorie kategorie, BigDecimal satz, BigDecimal prozent,
            BigDecimal basis, BigDecimal betrag, String grund, int quelle) {}

    /**
     * Berechnete Werte.
     *
     * @param zeilenbetraege BT-131 je Position, gleiche Reihenfolge wie die Positionen
     * @param summeZeilen    BT-106
     * @param netto          BT-109
     * @param steuergruppen  BG-23
     * @param steuer         BT-110
     * @param brutto         BT-112
     * @param vorausbezahlt  BT-113
     * @param zahlbar        BT-115
     * @param grundbetraege  BT-137 je Position: Menge × Preis vor dem Positionsrabatt
     * @param zeilenrabatte  BT-136 je Position, 0.00 ohne Rabatt
     * @param belegposten    BG-20/21, je Steuergruppe aufgeteilt
     * @param abschlaege     BT-107
     * @param zuschlaege     BT-108
     */
    public record Ergebnis(
            List<BigDecimal> zeilenbetraege,
            BigDecimal summeZeilen,
            BigDecimal netto,
            List<Steuergruppe> steuergruppen,
            BigDecimal steuer,
            BigDecimal brutto,
            BigDecimal vorausbezahlt,
            BigDecimal zahlbar,
            List<BigDecimal> grundbetraege,
            List<BigDecimal> zeilenrabatte,
            List<Belegposten> belegposten,
            BigDecimal abschlaege,
            BigDecimal zuschlaege) {}

    public static Ergebnis berechne(Rechnung r) {
        List<BigDecimal> zeilen = new ArrayList<>();
        List<BigDecimal> grund = new ArrayList<>();
        List<BigDecimal> rabatte = new ArrayList<>();
        Map<Schluessel, BigDecimal> zeilenJeGruppe = new LinkedHashMap<>();
        for (Position p : r.positionen()) {
            BigDecimal g = grundbetrag(p);
            BigDecimal rabatt = zeilenrabatt(p, g);
            BigDecimal betrag = g.subtract(rabatt);
            grund.add(g);
            rabatte.add(rabatt);
            zeilen.add(betrag);
            zeilenJeGruppe.merge(new Schluessel(p.kategorie(), satz(p)), betrag, BigDecimal::add);
        }
        BigDecimal summeZeilen = summe(zeilen); // BR-CO-10

        List<Belegposten> posten = new ArrayList<>();
        for (int i = 0; i < r.abschlaegeZuschlaege().size(); i++) {
            posten.addAll(verteile(r.abschlaegeZuschlaege().get(i), i, zeilenJeGruppe));
        }
        BigDecimal abschlaege = summe(posten.stream().filter(b -> !b.zuschlag()).map(Belegposten::betrag).toList());
        BigDecimal zuschlaege = summe(posten.stream().filter(Belegposten::zuschlag).map(Belegposten::betrag).toList());
        BigDecimal netto = summeZeilen.subtract(abschlaege).add(zuschlaege); // BR-CO-11/12/13

        List<Steuergruppe> gruppen = new ArrayList<>();
        for (var e : zeilenJeGruppe.entrySet()) {
            Steuerkategorie k = e.getKey().kategorie();
            BigDecimal satz = e.getKey().satz();
            BigDecimal basis = e.getValue(); // BR-S-08 und Analoga: Zeilen − Abschläge + Zuschläge der Gruppe
            for (Belegposten b : posten) {
                if (b.kategorie() == k && b.satz().equals(satz)) {
                    basis = b.zuschlag() ? basis.add(b.betrag()) : basis.subtract(b.betrag());
                }
            }
            BigDecimal steuer = k == Steuerkategorie.S || k == Steuerkategorie.L || k == Steuerkategorie.M
                    ? runde(basis.multiply(satz).divide(HUNDERT)) // BR-S-09
                    : runde(BigDecimal.ZERO); // BR-Z/E/AE/K/G/O: Steuer 0
            gruppen.add(new Steuergruppe(k, satz, basis, steuer));
        }

        BigDecimal steuer = summe(gruppen.stream().map(Steuergruppe::steuer).toList()); // BR-CO-14
        BigDecimal brutto = netto.add(steuer); // BR-CO-15
        BigDecimal voraus = runde(r.vorausbezahlt() == null ? BigDecimal.ZERO : r.vorausbezahlt());
        BigDecimal zahlbar = brutto.subtract(voraus); // BR-CO-16 (ohne BT-114)

        return new Ergebnis(List.copyOf(zeilen), summeZeilen, netto, List.copyOf(gruppen), steuer, brutto, voraus,
                zahlbar, List.copyOf(grund), List.copyOf(rabatte), List.copyOf(posten), abschlaege, zuschlaege);
    }

    /** Skontobetrag: Prozentsatz vom Zahlbetrag (BT-115), auf Cent gerundet. */
    public static BigDecimal skonto(Ergebnis e, BigDecimal prozent) {
        return runde(e.zahlbar().multiply(prozent).divide(HUNDERT));
    }

    /** Teilt einen Abschlag oder Zuschlag auf die Steuergruppen auf; Gruppen mit Betrag 0 entfallen. */
    private static List<Belegposten> verteile(AbschlagZuschlag a, int quelle, Map<Schluessel, BigDecimal> gruppen) {
        List<Belegposten> liste = new ArrayList<>();
        List<Schluessel> schluessel = new ArrayList<>(gruppen.keySet());
        if (a.prozent() != null) {
            for (Schluessel s : schluessel) {
                BigDecimal basis = gruppen.get(s);
                BigDecimal betrag = runde(basis.multiply(a.prozent()).divide(HUNDERT));
                if (betrag.signum() != 0) {
                    liste.add(new Belegposten(a.zuschlag(), s.kategorie(), s.satz(), a.prozent(), basis, betrag,
                            a.text(), quelle));
                }
            }
            return liste;
        }
        BigDecimal gesamt = runde(a.betrag());
        BigDecimal summe = gruppen.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rest = gesamt;
        for (int i = 0; i < schluessel.size(); i++) {
            Schluessel s = schluessel.get(i);
            boolean letzte = i == schluessel.size() - 1;
            BigDecimal betrag = letzte ? rest
                    : summe.signum() == 0 ? runde(BigDecimal.ZERO)
                    : runde(gesamt.multiply(gruppen.get(s)).divide(summe, 10, RoundingMode.HALF_UP));
            rest = rest.subtract(betrag);
            if (betrag.signum() != 0) {
                liste.add(new Belegposten(a.zuschlag(), s.kategorie(), s.satz(), null, null, betrag, a.text(), quelle));
            }
        }
        return liste;
    }

    /** BT-137 = round(BT-129 × BT-146 / BT-149, 2). */
    static BigDecimal grundbetrag(Position p) {
        BigDecimal brutto = p.menge().multiply(p.nettopreis());
        if (p.basismenge() != null) {
            // Zwischenergebnis mit Reserve-Stellen, erst danach auf 2 Stellen runden
            brutto = brutto.divide(p.basismenge(), 10, RoundingMode.HALF_UP);
        }
        return runde(brutto);
    }

    /** BT-136 = round(BT-137 × BT-138 / 100, 2). */
    private static BigDecimal zeilenrabatt(Position p, BigDecimal grundbetrag) {
        return p.rabatt() == null ? runde(BigDecimal.ZERO) : runde(grundbetrag.multiply(p.rabatt()).divide(HUNDERT));
    }

    /** BT-131 = BT-137 − BT-136. */
    static BigDecimal zeilenbetrag(Position p) {
        BigDecimal g = grundbetrag(p);
        return g.subtract(zeilenrabatt(p, g));
    }

    /** Satz normiert, damit 19, 19.0 und 19.00 dieselbe Gruppe ergeben. Kategorie O hat keinen Satz. */
    private static BigDecimal satz(Position p) {
        if (p.kategorie() == Steuerkategorie.O || p.steuersatz() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal s = p.steuersatz().stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private static BigDecimal summe(List<BigDecimal> werte) {
        return werte.stream().reduce(runde(BigDecimal.ZERO), BigDecimal::add);
    }

    private static BigDecimal runde(BigDecimal wert) {
        return wert.setScale(2, RoundingMode.HALF_UP);
    }

    private record Schluessel(Steuerkategorie kategorie, BigDecimal satz) {}
}
