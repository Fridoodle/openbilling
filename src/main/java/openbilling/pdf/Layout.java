package openbilling.pdf;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import openbilling.model.Berechnung;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Partei;
import openbilling.model.Rechnung.Position;

/**
 * Sichtkopie als XSL-FO: ein kompakter Geschäftsbrief für Menschen. Technische Angaben
 * (BT-23, BT-24, elektronische Adressen, Codes) stehen nur in der XML.
 * Alle Beträge kommen aus {@link Berechnung}, also aus derselben Rechnung wie die XML.
 */
final class Layout {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);
    private static final String GRAU = "#666666";

    private Layout() {}

    /** @param pt Schriftgröße in Punkt; kleinere Werte bringen mehr Positionen auf eine Seite */
    static String fo(Rechnung r, int pt) {
        var e = Berechnung.berechne(r);
        String w = "EUR".equals(r.waehrung()) ? "€" : r.waehrung();
        String art = "381".equals(r.typ()) ? "Gutschrift" : "Rechnung";
        Partei v = r.verkaeufer();
        Partei k = r.kaeufer();
        var s = new StringBuilder();
        s.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" xml:lang="de" font-family="SourceSansPro"
                    font-size="%dpt" line-height="1.25">
                <fo:layout-master-set><fo:simple-page-master master-name="a4" page-height="297mm" page-width="210mm"
                    margin-top="15mm" margin-bottom="15mm" margin-left="25mm" margin-right="20mm">
                <fo:region-body/></fo:simple-page-master></fo:layout-master-set>
                <fo:page-sequence master-reference="a4"><fo:flow flow-name="xsl-region-body">
                """.formatted(pt));

        // Kopf: Absendername. Der Abstand setzt die Empfängeranschrift ins Fenster eines DIN-Umschlags.
        s.append("<fo:block font-size=\"16pt\" text-align=\"end\" space-after=\"20mm\">").append(x(v.name()))
                .append("</fo:block>\n");

        // Anschriften
        var empf = new StringBuilder(marke("Rechnungsempfänger"));
        zeile(empf, k.name());
        anschrift(empf, k);
        var abs = new StringBuilder(marke("Absender"));
        zeile(abs, v.name());
        if (v.kontakt() != null && !v.name().equals(v.kontakt().name())) {
            zeile(abs, v.kontakt().name());
        }
        anschrift(abs, v);
        if (v.kontakt() != null) {
            zeile(abs, leer(v.kontakt().telefon()) ? null : "T " + v.kontakt().telefon());
            zeile(abs, v.kontakt().email());
        }
        zweispaltig(s, empf, abs);

        // Rechnungsdaten links, Steuer- und Bankdaten rechts
        var links = new StringBuilder();
        paar(links, art + "snummer", r.nummer());
        paar(links, art + "sdatum", datum(r.datum()));
        if (r.leistungVon() != null || r.leistungBis() != null) {
            paar(links, "Leistungszeitraum", datum(r.leistungVon()) + " – " + datum(r.leistungBis()));
        } else {
            paar(links, "Leistungsdatum", datum(r.lieferdatum()));
        }
        paar(links, "Fällig am", datum(r.faelligAm()));
        var rechts = new StringBuilder();
        paar(rechts, "USt-IdNr.", v.ustId());
        paar(rechts, "Steuernummer", v.steuernummer());
        if (r.zahlung() != null) {
            paar(rechts, "IBAN", iban(r.zahlung().iban()));
            paar(rechts, "BIC", r.zahlung().bic());
            paar(rechts, "Inhaber", r.zahlung().kontoinhaber());
            if (!r.nummer().equals(r.zahlung().verwendungszweck())) {
                paar(rechts, "Verwendungszweck", r.zahlung().verwendungszweck());
            }
        }
        s.append("<fo:block space-before=\"8mm\"/>\n");
        zweispaltig(s, paare(links, "32mm"), paare(rechts, "27mm"));

        if (!leer(r.bemerkung())) {
            s.append("<fo:block space-before=\"8mm\" linefeed-treatment=\"preserve\">").append(x(r.bemerkung()))
                    .append("</fo:block>\n");
        }

        // Positionen
        s.append("""
                <fo:table table-layout="fixed" width="100%%" space-before="8mm">
                <fo:table-column column-width="10mm"/><fo:table-column column-width="85mm"/>
                <fo:table-column column-width="24mm"/><fo:table-column column-width="22mm"/>
                <fo:table-column column-width="24mm"/>
                <fo:table-header font-size="85%%" color="%s"><fo:table-row>
                %s%s%s%s%s</fo:table-row></fo:table-header>
                <fo:table-body>
                """.formatted(GRAU, kopf("Pos.", "start"), kopf("Leistung", "start"), kopf("Menge", "end"),
                        kopf("Einzelpreis", "end"), kopf("Betrag", "end")));
        for (int i = 0; i < r.positionen().size(); i++) {
            Position p = r.positionen().get(i);
            boolean pauschal = "LS".equals(p.einheit()) && p.menge().compareTo(BigDecimal.ONE) == 0;
            s.append("<fo:table-row>").append(zelle(x(p.id()), "start"));
            s.append("<fo:table-cell padding-top=\"0.4em\"><fo:block>").append(x(p.bezeichnung())).append("</fo:block>");
            if (!leer(p.beschreibung())) {
                s.append("<fo:block font-size=\"90%\" color=\"").append(GRAU)
                        .append("\" linefeed-treatment=\"preserve\">").append(x(p.beschreibung())).append("</fo:block>");
            }
            s.append("</fo:table-cell>");
            s.append(zelle(pauschal ? "pauschal" : x(zahl(p.menge()) + " " + einheit(p.einheit())), "end"));
            s.append(zelle(pauschal ? "" : x(geld(p.nettopreis(), w)
                    + (p.basismenge() == null || p.basismenge().compareTo(BigDecimal.ONE) == 0
                            ? "" : " / " + zahl(p.basismenge()))), "end"));
            s.append(zelle(x(geld(e.zeilenbetraege().get(i), w)), "end")).append("</fo:table-row>\n");
        }
        s.append("</fo:table-body></fo:table>\n");

        // Summen, zusammen auf einer Seite
        s.append("""
                <fo:table table-layout="fixed" width="100%" space-before="4mm" keep-together.within-page="always">
                <fo:table-column column-width="85mm"/><fo:table-column column-width="50mm"/>
                <fo:table-column column-width="30mm"/><fo:table-body>
                """);
        summe(s, "Betrag netto", geld(e.netto(), w), false);
        for (var g : e.steuergruppen()) {
            summe(s, zahl(g.satz() == null ? BigDecimal.ZERO : g.satz()) + " % USt"
                    + (e.steuergruppen().size() > 1 ? " auf " + geld(g.basis(), w) : ""), geld(g.steuer(), w), false);
        }
        boolean voraus = e.vorausbezahlt() != null && e.vorausbezahlt().signum() != 0;
        summe(s, art + "sbetrag", geld(e.brutto(), w), !voraus);
        if (voraus) {
            summe(s, "Bereits gezahlt", geld(e.vorausbezahlt(), w), false);
            summe(s, "Zahlbetrag", geld(e.zahlbar(), w), true);
        }
        s.append("</fo:table-body></fo:table>\n");

        for (String text : new String[] {r.befreiungsgrund(), r.zahlungsbedingungen()}) {
            if (!leer(text)) {
                s.append("<fo:block space-before=\"6mm\" linefeed-treatment=\"preserve\">").append(x(text))
                        .append("</fo:block>\n");
            }
        }
        if (!leer(r.schlussformel())) {
            s.append("<fo:block space-before=\"10mm\" linefeed-treatment=\"preserve\"")
                    .append(" keep-together.within-page=\"always\">").append(x(r.schlussformel().strip()))
                    .append("</fo:block>\n");
        }
        return s.append("</fo:flow></fo:page-sequence></fo:root>\n").toString();
    }

    private static void zweispaltig(StringBuilder s, CharSequence links, CharSequence rechts) {
        s.append("<fo:table table-layout=\"fixed\" width=\"100%\"><fo:table-column column-width=\"95mm\"/>")
                .append("<fo:table-column column-width=\"70mm\"/><fo:table-body><fo:table-row><fo:table-cell>")
                .append(links).append("</fo:table-cell><fo:table-cell>").append(rechts)
                .append("</fo:table-cell></fo:table-row></fo:table-body></fo:table>\n");
    }

    private static String marke(String text) {
        return "<fo:block font-size=\"85%\" color=\"" + GRAU + "\">" + text + "</fo:block>";
    }

    private static void anschrift(StringBuilder s, Partei p) {
        if (p.adresse() != null) {
            zeile(s, p.adresse().strasse());
            zeile(s, (nichtNull(p.adresse().plz()) + " " + nichtNull(p.adresse().ort())).strip());
            zeile(s, "DE".equals(p.adresse().land()) ? null : p.adresse().land());
        }
    }

    private static void zeile(StringBuilder s, String text) {
        if (!leer(text)) {
            s.append("<fo:block>").append(x(text)).append("</fo:block>");
        }
    }

    private static void paar(StringBuilder s, String name, String wert) {
        if (!leer(wert)) {
            s.append("<fo:table-row><fo:table-cell><fo:block color=\"").append(GRAU).append("\">").append(name)
                    .append("</fo:block></fo:table-cell><fo:table-cell><fo:block>").append(x(wert))
                    .append("</fo:block></fo:table-cell></fo:table-row>");
        }
    }

    /** Tabelle aus {@link #paar}-Zeilen; ohne Zeilen ein leerer Block, weil FO keine leere Tabelle erlaubt. */
    private static String paare(StringBuilder zeilen, String breite) {
        return zeilen.isEmpty() ? "<fo:block/>"
                : "<fo:table table-layout=\"fixed\" width=\"100%\"><fo:table-column column-width=\"" + breite
                        + "\"/><fo:table-column column-width=\"proportional-column-width(1)\"/><fo:table-body>"
                        + zeilen + "</fo:table-body></fo:table>";
    }

    private static String kopf(String text, String ausrichtung) {
        return "<fo:table-cell border-bottom=\"0.5pt solid " + GRAU + "\" padding-bottom=\"1mm\"><fo:block text-align=\""
                + ausrichtung + "\">" + text + "</fo:block></fo:table-cell>";
    }

    private static String zelle(String inhalt, String ausrichtung) {
        return "<fo:table-cell padding-top=\"0.4em\"><fo:block text-align=\"" + ausrichtung + "\">" + inhalt
                + "</fo:block></fo:table-cell>";
    }

    private static void summe(StringBuilder s, String name, String betrag, boolean fett) {
        String stil = fett ? " font-weight=\"700\" border-top=\"0.5pt solid black\" padding-top=\"1mm\"" : "";
        s.append("<fo:table-row><fo:table-cell><fo:block/></fo:table-cell><fo:table-cell").append(stil)
                .append("><fo:block>").append(x(name)).append("</fo:block></fo:table-cell><fo:table-cell").append(stil)
                .append("><fo:block text-align=\"end\">").append(x(betrag))
                .append("</fo:block></fo:table-cell></fo:table-row>\n");
    }

    private static String geld(BigDecimal b, String waehrung) {
        return new DecimalFormat("#,##0.00####", DE).format(b) + " " + waehrung;
    }

    private static String zahl(BigDecimal b) {
        return new DecimalFormat("#,##0.####", DE).format(b);
    }

    private static String datum(LocalDate d) {
        return d == null ? "" : DATUM.format(d);
    }

    private static String iban(String iban) {
        return leer(iban) ? null : iban.replaceAll("\\s", "").replaceAll("(.{4})(?=.)", "$1 ");
    }

    /** Klartext für die häufigsten Codes aus UN/ECE Rec 20 (BT-130), sonst der Code selbst. */
    private static String einheit(String code) {
        return switch (nichtNull(code)) {
            case "C62", "H87" -> "Stk.";
            case "HUR" -> "Std.";
            case "DAY" -> "Tage";
            case "MON" -> "Monate";
            case "LS" -> "pauschal";
            case "MTR" -> "m";
            case "MTK" -> "m²";
            case "KGM" -> "kg";
            case "LTR" -> "l";
            case "KMT" -> "km";
            default -> code;
        };
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }

    private static String nichtNull(String s) {
        return s == null ? "" : s;
    }

    private static String x(String s) {
        return nichtNull(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
