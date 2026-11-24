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
 * @param kaeuferReferenz BT-10 Leitweg-ID bzw. Buyer reference, in XRechnung Pflicht (BR-DE-15), sonst optional
 * @param zahlungsbedingungen BT-20, optional
 * @param lieferdatum     BT-72, optional
 * @param verkaeufer      BG-4
 * @param kaeufer         BG-7
 * @param zahlung         BG-16
 * @param befreiungsgrund BT-120 für Steuerkategorien ungleich S, optional
 * @param vorausbezahlt   BT-113, optional
 * @param positionen      BG-25, mindestens eine
 * @param bemerkung       BT-22 Freitext zur Rechnung (z. B. Einleitung, Bauvorhaben), optional
 * @param leistungVon     BT-73 Beginn des Leistungszeitraums (BG-14), optional
 * @param leistungBis     BT-74 Ende des Leistungszeitraums (BG-14), optional
 * @param schlussformel   kein BT: Grußformel am Ende der Sichtkopie (z. B. Dank und Unterschrift), nur im PDF,
 *                        nicht in der XRechnung, optional, darf mehrzeilig sein
 * @param firmenname      kein BT: Name oben rechts im Kopf der Sichtkopie, nur im PDF, optional;
 *                        leer gilt der Verkäufername (BT-27)
 * @param abschlaegeZuschlaege BG-20 Abschläge (Rabatte) und BG-21 Zuschläge auf die ganze Rechnung, ggf. leer
 * @param skonto          Skonto, wird nach BR-DE-18 in BT-20 kodiert, optional
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
        List<Position> positionen,
        String bemerkung,
        LocalDate leistungVon,
        LocalDate leistungBis,
        String schlussformel,
        String firmenname,
        List<AbschlagZuschlag> abschlaegeZuschlaege,
        Skonto skonto) {

    /** Ohne Abschläge, Zuschläge und Skonto. */
    public Rechnung(String spezifikation, String prozess, String nummer, LocalDate datum, String typ,
            String waehrung, LocalDate faelligAm, String kaeuferReferenz, String zahlungsbedingungen,
            LocalDate lieferdatum, Partei verkaeufer, Partei kaeufer, Zahlung zahlung, String befreiungsgrund,
            BigDecimal vorausbezahlt, List<Position> positionen, String bemerkung, LocalDate leistungVon,
            LocalDate leistungBis, String schlussformel, String firmenname) {
        this(spezifikation, prozess, nummer, datum, typ, waehrung, faelligAm, kaeuferReferenz,
                zahlungsbedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiungsgrund, vorausbezahlt,
                positionen, bemerkung, leistungVon, leistungBis, schlussformel, firmenname, List.of(), null);
    }

    /** Dieselbe Rechnung mit anderen Abschlägen, Zuschlägen und Skonto. */
    public Rechnung mit(List<AbschlagZuschlag> abschlaegeZuschlaege, Skonto skonto) {
        return new Rechnung(spezifikation, prozess, nummer, datum, typ, waehrung, faelligAm, kaeuferReferenz,
                zahlungsbedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiungsgrund, vorausbezahlt,
                positionen, bemerkung, leistungVon, leistungBis, schlussformel, firmenname, abschlaegeZuschlaege,
                skonto);
    }

    /** Ohne Firmenname. */
    public Rechnung(String spezifikation, String prozess, String nummer, LocalDate datum, String typ,
            String waehrung, LocalDate faelligAm, String kaeuferReferenz, String zahlungsbedingungen,
            LocalDate lieferdatum, Partei verkaeufer, Partei kaeufer, Zahlung zahlung, String befreiungsgrund,
            BigDecimal vorausbezahlt, List<Position> positionen, String bemerkung, LocalDate leistungVon,
            LocalDate leistungBis, String schlussformel) {
        this(spezifikation, prozess, nummer, datum, typ, waehrung, faelligAm, kaeuferReferenz,
                zahlungsbedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiungsgrund, vorausbezahlt,
                positionen, bemerkung, leistungVon, leistungBis, schlussformel, null);
    }

    /** Name oben rechts im PDF: der Firmenname, sonst der Verkäufername (BT-27). */
    public String kopfname() {
        return firmenname == null || firmenname.isBlank() ? verkaeufer.name() : firmenname.strip();
    }

    /** Ohne Schlussformel. */
    public Rechnung(String spezifikation, String prozess, String nummer, LocalDate datum, String typ,
            String waehrung, LocalDate faelligAm, String kaeuferReferenz, String zahlungsbedingungen,
            LocalDate lieferdatum, Partei verkaeufer, Partei kaeufer, Zahlung zahlung, String befreiungsgrund,
            BigDecimal vorausbezahlt, List<Position> positionen, String bemerkung, LocalDate leistungVon,
            LocalDate leistungBis) {
        this(spezifikation, prozess, nummer, datum, typ, waehrung, faelligAm, kaeuferReferenz,
                zahlungsbedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiungsgrund, vorausbezahlt,
                positionen, bemerkung, leistungVon, leistungBis, null);
    }

    /** Ohne Bemerkung und Leistungszeitraum. */
    public Rechnung(String spezifikation, String prozess, String nummer, LocalDate datum, String typ,
            String waehrung, LocalDate faelligAm, String kaeuferReferenz, String zahlungsbedingungen,
            LocalDate lieferdatum, Partei verkaeufer, Partei kaeufer, Zahlung zahlung, String befreiungsgrund,
            BigDecimal vorausbezahlt, List<Position> positionen) {
        this(spezifikation, prozess, nummer, datum, typ, waehrung, faelligAm, kaeuferReferenz,
                zahlungsbedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiungsgrund, vorausbezahlt,
                positionen, null, null, null, null);
    }

    /**
     * XRechnung (BT-24 mit der KoSIT-CIUS, BR-DE-Regeln, ZUGFeRD-Profil XRECHNUNG) oder reine EN 16931
     * (ZUGFeRD-Profil EN 16931/Comfort, z. B. für Geschäftskunden ohne Leitweg-ID).
     */
    public static boolean istXRechnung(String spezifikation) {
        return spezifikation != null && spezifikation.contains("urn:xeinkauf.de:kosit:xrechnung");
    }

    public boolean xrechnung() {
        return istXRechnung(spezifikation);
    }

    /** Name des Formats für Meldungen. */
    public String format() {
        return xrechnung() ? "XRechnung" : "ZUGFeRD-Rechnung nach EN 16931";
    }

    public Rechnung {
        positionen = List.copyOf(positionen);
        abschlaegeZuschlaege = abschlaegeZuschlaege == null ? List.of() : List.copyOf(abschlaegeZuschlaege);
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
     * BG-16 Zahlungsanweisung. Welche Teile gefüllt sind, hängt von der Zahlungsart ab (BR-DE-23/24/25):
     * Überweisung (30, 58) mit IBAN des Verkäufers, Lastschrift (59) mit {@link Lastschrift},
     * Karte (48, 54, 55) mit {@link Karte}, sonst (z. B. 10 bar, 68 Online-Zahlungsdienst) nichts davon.
     *
     * @param art              BT-81 (UNTDID 4461)
     * @param verwendungszweck BT-83, optional
     * @param iban             BT-84, nur bei Überweisung
     * @param kontoinhaber     BT-85, optional
     * @param bic              BT-86, optional
     * @param text             BT-82 Zahlungsart als Text (z. B. „PayPal“), optional; die ODS liest ihn nur bei
     *                         Arten ohne BG-17/18/19
     * @param lastschrift      BG-19, nur bei Lastschrift
     * @param karte            BG-18, nur bei Kartenzahlung
     */
    public record Zahlung(String art, String verwendungszweck, String iban, String kontoinhaber, String bic,
            String text, Lastschrift lastschrift, Karte karte) {

        /** Überweisung. */
        public Zahlung(String art, String verwendungszweck, String iban, String kontoinhaber, String bic) {
            this(art, verwendungszweck, iban, kontoinhaber, bic, null, null, null);
        }

        public boolean istUeberweisung() {
            return "30".equals(art) || "58".equals(art);
        }

        public boolean istLastschrift() {
            return "59".equals(art);
        }

        public boolean istKarte() {
            return "48".equals(art) || "54".equals(art) || "55".equals(art);
        }
    }

    /**
     * BG-19 SEPA-Lastschrift.
     *
     * @param mandat       BT-89 Mandatsreferenz
     * @param glaeubigerId BT-90 Gläubiger-Identifikationsnummer des Verkäufers (BR-DE-30)
     * @param iban         BT-91 IBAN des Kontos, das belastet wird (BR-DE-31)
     */
    public record Lastschrift(String mandat, String glaeubigerId, String iban) {}

    /**
     * BG-18 Kartenzahlung.
     *
     * @param nummer  BT-87 nur die letzten 4 bis 6 Ziffern der Kartennummer (BR-51), nie die ganze
     * @param inhaber BT-88, optional
     */
    public record Karte(String nummer, String inhaber) {}

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
     * @param beschreibung BT-154 ausführlicher Text zur Position, optional, darf mehrzeilig sein
     * @param rabatt      BT-138 Rabatt auf die Position in Prozent (BG-27 Zeilenabschlag), optional
     */
    public record Position(
            String id,
            String bezeichnung,
            BigDecimal menge,
            String einheit,
            BigDecimal nettopreis,
            BigDecimal basismenge,
            Steuerkategorie kategorie,
            BigDecimal steuersatz,
            String beschreibung,
            BigDecimal rabatt) {

        /** Ohne Rabatt. */
        public Position(String id, String bezeichnung, BigDecimal menge, String einheit, BigDecimal nettopreis,
                BigDecimal basismenge, Steuerkategorie kategorie, BigDecimal steuersatz, String beschreibung) {
            this(id, bezeichnung, menge, einheit, nettopreis, basismenge, kategorie, steuersatz, beschreibung, null);
        }

        /** Ohne Beschreibung. */
        public Position(String id, String bezeichnung, BigDecimal menge, String einheit, BigDecimal nettopreis,
                BigDecimal basismenge, Steuerkategorie kategorie, BigDecimal steuersatz) {
            this(id, bezeichnung, menge, einheit, nettopreis, basismenge, kategorie, steuersatz, null);
        }

        public Position {
            if (nettopreis.signum() < 0) {
                throw new IllegalArgumentException("Nettopreis darf nicht negativ sein (BR-27): " + id);
            }
            if (rabatt != null && rabatt.signum() == 0) {
                rabatt = null;
            }
            if (rabatt != null && (rabatt.signum() < 0 || rabatt.compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalArgumentException("Rabatt muss zwischen 0 und 100 % liegen (BT-138): " + id);
            }
        }
    }

    /**
     * BG-20 Abschlag (Rabatt) oder BG-21 Zuschlag auf die ganze Rechnung, entweder in Prozent der Positionssumme
     * oder als fester Nettobetrag. Die Steuerkategorie gibt man nicht an: {@link Berechnung} verteilt den Betrag
     * anteilig auf die Steuergruppen der Positionen, so wie eine Nebenleistung der Hauptleistung folgt.
     *
     * @param zuschlag {@code true} Zuschlag (BG-21), {@code false} Abschlag (BG-20)
     * @param prozent  BT-94 / BT-101, nur ohne Betrag
     * @param betrag   BT-92 / BT-99 als Nettobetrag, nur ohne Prozent
     * @param grund    BT-97 / BT-104, optional (Vorgabe „Rabatt“ bzw. „Zuschlag“)
     */
    public record AbschlagZuschlag(boolean zuschlag, BigDecimal prozent, BigDecimal betrag, String grund) {

        public AbschlagZuschlag {
            if ((prozent == null) == (betrag == null)) {
                throw new IllegalArgumentException(
                        (zuschlag ? "Zuschlag" : "Rabatt") + ": Prozent oder Betrag angeben, nicht beides");
            }
            if ((prozent != null && prozent.signum() <= 0) || (betrag != null && betrag.signum() <= 0)) {
                throw new IllegalArgumentException((zuschlag ? "Zuschlag" : "Rabatt") + " muss größer als 0 sein");
            }
            if (grund != null && grund.isBlank()) {
                grund = null;
            }
        }

        /** Text für BT-97 / BT-104 und das PDF. */
        public String text() {
            return grund != null ? grund.strip() : zuschlag ? "Zuschlag" : "Rabatt";
        }
    }

    /**
     * Skonto: bei Zahlung innerhalb von {@code tage} Tagen darf der Käufer {@code prozent} vom Zahlbetrag abziehen.
     * Kodiert nach BR-DE-18 als {@code #SKONTO#TAGE=14#PROZENT=2.00#} in BT-20.
     */
    public record Skonto(int tage, BigDecimal prozent) {

        public Skonto {
            if (tage <= 0 || prozent.signum() <= 0 || prozent.compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new IllegalArgumentException("Skonto braucht Tage über 0 und einen Satz zwischen 0 und 100 %");
            }
        }
    }

    /** UNCL 5305, BT-118/BT-151. */
    public enum Steuerkategorie { S, Z, E, AE, K, G, O, L, M }
}
