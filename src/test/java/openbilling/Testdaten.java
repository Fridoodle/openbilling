package openbilling;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Adresse;
import openbilling.model.Rechnung.ElektronischeAdresse;
import openbilling.model.Rechnung.Kontakt;
import openbilling.model.Rechnung.Partei;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.model.Rechnung.Zahlung;

/** Testrechnungen. Erfundene Daten; IBAN und Leitweg-ID sind prüfziffernkorrekt. */
public final class Testdaten {

    public static final String XRECHNUNG_30 =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    private Testdaten() {}

    public static final Partei VERKAEUFER = new Partei("Beispiel GmbH", null,
            new Adresse("Musterstraße 1", "10115", "Berlin", "DE"),
            new Kontakt("Max Muster", "+49 30 1234567", "max.muster@beispiel-gmbh.de"),
            new ElektronischeAdresse("EM", "rechnung@beispiel-gmbh.de"),
            "DE123456789", null);

    public static final Partei KAEUFER = new Partei("Freie Hansestadt Bremen, Senator für Finanzen", null,
            new Adresse("Amtsweg 5", "28195", "Bremen", "DE"), null,
            new ElektronischeAdresse("0204", "04011000-12345-03"), null, null);

    /** Rechnung wie tests/fixtures/minimal-cii.xml, aber mit beliebigen Positionen. */
    public static Rechnung rechnung(List<Position> positionen) {
        return new Rechnung(XRECHNUNG_30, "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
                "RE-2026-0001", LocalDate.of(2026, 11, 23), "380", "EUR",
                LocalDate.of(2026, 12, 7), "04011000-12345-03",
                "Zahlbar innerhalb von 14 Tagen ohne Abzug.", LocalDate.of(2026, 11, 20),
                VERKAEUFER, KAEUFER,
                new Zahlung("58", "RE-2026-0001", "DE02120300000000202051", "Beispiel GmbH", null),
                null, null, positionen);
    }

    /** Genau die Rechnung aus tests/fixtures/minimal-cii.xml. */
    public static Rechnung minimal() {
        return rechnung(List.of(new Position("1", "Beratungsleistung", b("10"), "HUR", b("85.00"), null,
                Steuerkategorie.S, b("19"))));
    }

    /** {@link #minimal()} mit Schlussformel (nur Sichtkopie). */
    public static Rechnung mitSchlussformel(String schlussformel) {
        Rechnung r = minimal();
        return new Rechnung(r.spezifikation(), r.prozess(), r.nummer(), r.datum(), r.typ(), r.waehrung(),
                r.faelligAm(), r.kaeuferReferenz(), r.zahlungsbedingungen(), r.lieferdatum(), r.verkaeufer(),
                r.kaeufer(), r.zahlung(), r.befreiungsgrund(), r.vorausbezahlt(), r.positionen(), r.bemerkung(),
                r.leistungVon(), r.leistungBis(), schlussformel);
    }

    public static Position pos(String id, String menge, String preis, Steuerkategorie k, String satz) {
        return new Position(id, "Leistung " + id, b(menge), "C62", b(preis), null,
                k, satz == null ? null : b(satz));
    }

    public static BigDecimal b(String s) {
        return new BigDecimal(s);
    }
}
