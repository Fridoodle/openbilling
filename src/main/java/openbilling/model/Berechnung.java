package openbilling.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;

/**
 * Summen und Steuern nach EN 16931 (BR-CO-*, BR-S-08/09 und Analoga).
 * Beträge auf 2 Nachkommastellen, kaufmännisch gerundet (HALF_UP).
 * Die Steuer wird je (Kategorie, Satz) berechnet, nicht je Zeile.
 * Beleg-Abschläge/-Zuschläge (BG-20/21) gibt es noch nicht; BT-107/108 sind daher 0.
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
     */
    public record Ergebnis(
            List<BigDecimal> zeilenbetraege,
            BigDecimal summeZeilen,
            BigDecimal netto,
            List<Steuergruppe> steuergruppen,
            BigDecimal steuer,
            BigDecimal brutto,
            BigDecimal vorausbezahlt,
            BigDecimal zahlbar) {}

    public static Ergebnis berechne(Rechnung r) {
        List<BigDecimal> zeilen = new ArrayList<>();
        Map<Schluessel, BigDecimal> basisJeGruppe = new LinkedHashMap<>();
        for (Position p : r.positionen()) {
            BigDecimal betrag = zeilenbetrag(p);
            zeilen.add(betrag);
            basisJeGruppe.merge(new Schluessel(p.kategorie(), satz(p)), betrag, BigDecimal::add);
        }

        BigDecimal summeZeilen = summe(zeilen); // BR-CO-10
        BigDecimal netto = summeZeilen; // BR-CO-13 (ohne BT-107/108)

        List<Steuergruppe> gruppen = new ArrayList<>();
        for (var e : basisJeGruppe.entrySet()) {
            Steuerkategorie k = e.getKey().kategorie();
            BigDecimal satz = e.getKey().satz();
            BigDecimal basis = e.getValue();
            BigDecimal steuer = k == Steuerkategorie.S || k == Steuerkategorie.L || k == Steuerkategorie.M
                    ? runde(basis.multiply(satz).divide(HUNDERT)) // BR-S-09
                    : runde(BigDecimal.ZERO); // BR-Z/E/AE/K/G/O: Steuer 0
            gruppen.add(new Steuergruppe(k, satz, basis, steuer));
        }

        BigDecimal steuer = summe(gruppen.stream().map(Steuergruppe::steuer).toList()); // BR-CO-14
        BigDecimal brutto = netto.add(steuer); // BR-CO-15
        BigDecimal voraus = runde(r.vorausbezahlt() == null ? BigDecimal.ZERO : r.vorausbezahlt());
        BigDecimal zahlbar = brutto.subtract(voraus); // BR-CO-16 (ohne BT-114)

        return new Ergebnis(List.copyOf(zeilen), summeZeilen, netto, List.copyOf(gruppen), steuer, brutto, voraus, zahlbar);
    }

    /** BT-131 = round(BT-129 × BT-146 / BT-149, 2). */
    static BigDecimal zeilenbetrag(Position p) {
        BigDecimal brutto = p.menge().multiply(p.nettopreis());
        if (p.basismenge() != null) {
            // Zwischenergebnis mit Reserve-Stellen, erst danach auf 2 Stellen runden
            brutto = brutto.divide(p.basismenge(), 10, RoundingMode.HALF_UP);
        }
        return runde(brutto);
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
