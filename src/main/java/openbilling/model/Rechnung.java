package openbilling.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rechnung nach EN 16931 / XRechnung. Feldnamen deutsch, BT-/BG-Nummer jeweils im Kommentar.
 * Enthält nur Eingaben; alle Summen und Steuern berechnet {@link Berechnung}.
 * Optionale Felder sind {@code null}.
 *
 * @param spezifikation   BT-24 CustomizationID (konfigurierbar, z. B. XRechnung 3.0)
 * @param prozess         BT-23 Business process type, optional
 * @param nummer          BT-1
 * @param datum           BT-2
 * @param typ             BT-3 (380 Rechnung, 381 Gutschrift, …)
 * @param waehrung        BT-5 (ISO 4217)
 * @param faelligAm       BT-9, optional
 * @param kaeuferReferenz BT-10 Leitweg-ID bzw. Buyer reference, in XRechnung Pflicht
 * @param zahlungsbedingungen BT-20, optional
 * @param lieferdatum     BT-72, optional
 * @param verkaeufer      BG-4
 * @param kaeufer         BG-7
 * @param zahlung         BG-16
 * @param befreiungsgrund BT-120 für Steuerkategorien ungleich S, optional
 * @param vorausbezahlt   BT-113, optional
 * @param positionen      BG-25, mindestens eine
 */
public record Rechnung(
        String spezifikation,
        String prozess,
        String nummer,
        LocalDate datum,
        String typ,
        String waehrung,
        LocalDate faelligAm,
        String kaeuferReferenz,
        String zahlungsbedingungen,
        LocalDate lieferdatum,
        Partei verkaeufer,
        Partei kaeufer,
        Zahlung zahlung,
        String befreiungsgrund,
        BigDecimal vorausbezahlt,
        List<Position> positionen) {

    public Rechnung {
        positionen = List.copyOf(positionen);
        if (positionen.isEmpty()) {
            throw new IllegalArgumentException("Rechnung braucht mindestens eine Position (BG-25)");
        }
    }

    /**
     * Verkäufer (BG-4) oder Käufer (BG-7).
     *
     * @param name                BT-27 / BT-44
     * @param kennung             BT-29 / BT-46, optional. Beim Verkäufer ohne USt-IdNr. nötig (BR-CO-26)
     * @param adresse             BG-5 / BG-8
     * @param kontakt             BG-6, beim Verkäufer Pflicht (BR-DE-2), beim Käufer optional
     * @param elektronischeAdresse BT-34 / BT-49
     * @param ustId               BT-31 / BT-48, optional
     * @param steuernummer        BT-32, nur Verkäufer, optional
     */
    public record Partei(
            String name,
            String kennung,
            Adresse adresse,
            Kontakt kontakt,
            ElektronischeAdresse elektronischeAdresse,
            String ustId,
            String steuernummer) {}

    /** BG-5 / BG-8. In DE sind Ort und PLZ Pflicht (BR-DE-3/4/8/9). */
    public record Adresse(String strasse, String plz, String ort, String land) {}

    /** BG-6: BT-41 Name, BT-42 Telefon, BT-43 E-Mail. */
    public record Kontakt(String name, String telefon, String email) {}

    /** BT-34 / BT-49 mit Schema (z. B. {@code EM} für E-Mail, {@code 0204} für Leitweg-ID). */
    public record ElektronischeAdresse(String schema, String wert) {}

    /**
     * BG-16 Zahlungsanweisung.
     *
     * @param art              BT-81 (58 SEPA-Überweisung, 59 Lastschrift, …)
     * @param verwendungszweck BT-83, optional
     * @param iban             BT-84
     * @param kontoinhaber     BT-85, optional
     * @param bic              BT-86, optional
     */
    public record Zahlung(String art, String verwendungszweck, String iban, String kontoinhaber, String bic) {}

    /**
     * BG-25 Rechnungsposition.
     *
     * @param id          BT-126
     * @param bezeichnung BT-153
     * @param menge       BT-129
     * @param einheit     BT-130 (UN/ECE Rec 20, z. B. {@code HUR}, {@code C62})
     * @param nettopreis  BT-146
     * @param basismenge  BT-149, optional (Standard 1)
     * @param kategorie   BT-151
     * @param steuersatz  BT-152 in Prozent (bei Kategorie O ohne Bedeutung)
     */
    public record Position(
            String id,
            String bezeichnung,
            BigDecimal menge,
            String einheit,
            BigDecimal nettopreis,
            BigDecimal basismenge,
            Steuerkategorie kategorie,
            BigDecimal steuersatz) {

        public Position {
            if (nettopreis.signum() < 0) {
                throw new IllegalArgumentException("Nettopreis darf nicht negativ sein (BR-27): " + id);
            }
        }
    }

    /** UNCL 5305, BT-118/BT-151. */
    public enum Steuerkategorie { S, Z, E, AE, K, G, O, L, M }
}
