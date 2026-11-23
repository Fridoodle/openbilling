package openbilling.ods;

import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import openbilling.model.Berechnung;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Adresse;
import openbilling.model.Rechnung.ElektronischeAdresse;
import openbilling.model.Rechnung.Kontakt;
import openbilling.model.Rechnung.Partei;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.model.Rechnung.Zahlung;

/**
 * Datenvertrag zwischen Rechnungs-ODS und {@link Rechnung} (siehe knowledge/decisions.md).
 *
 * <ul>
 *   <li>Blatt {@code Daten}: Spalte A Feld, Spalte B Wert. Ein Feld beginnt mit seiner BT-Kennung
 *       ({@code BT-1 Rechnungsnummer}); nur die Kennung zählt, Reihenfolge und Beschriftung sind frei.
 *       Zeilen ohne BT-Kennung (Überschriften) und berechnete Felder werden beim Lesen ignoriert.
 *       Felder, die es in der EN 16931 nicht gibt (nur für die Sichtkopie), haben eine eigene Kennung
 *       {@code OB-n} (openbilling) und werden wie BT-Felder behandelt.
 *   <li>Blatt {@code Positionen}: Zeile 1 enthält Spaltenköpfe mit BT-Kennung, darunter eine Zeile je Position.
 *       Leere Zeilen werden übersprungen.
 *   <li>Weitere Blätter (z. B. {@code Ansicht}) werden nicht gelesen.
 * </ul>
 *
 * Zahlen dürfen als Zahl oder Text ({@code 85,00}, {@code 1.234,50}) stehen, Datumswerte als Datum
 * oder Text ({@code 23.11.2026}, {@code 2026-11-23}).
 */
public final class RechnungOds {

    public static final String BLATT_DATEN = "Daten";
    public static final String BLATT_POSITIONEN = "Positionen";

    /** Vorgabe für BT-24 in neuen Vorlagen. Gilt mindestens bis 31.07.2027 (knowledge/xrechnung-format.md). */
    public static final String XRECHNUNG_30 =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /**
     * Vorgaben einer neuen Rechnung. BT-23 ist in XRechnung Pflicht (PEPPOL-EN16931-R001); der Wert ist
     * der übliche Standardprozess.
     */
    public static final Map<String, String> VORGABEN = Map.of(
            "BT-3", "380", "BT-5", "EUR", "BT-24", XRECHNUNG_30,
            "BT-23", "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            "BT-40", "DE", "BT-55", "DE", "BT-34-1", "EM", "BT-49-1", "EM", "BT-81", "58");

    /** Zeilen des Blatts Daten in Anzeigereihenfolge. Eintrag ohne "BT-" ist eine Überschrift. */
    private static final String[] DATEN = {
        "Rechnung",
        "BT-1 Rechnungsnummer",
        "BT-2 Rechnungsdatum",
        "BT-3 Rechnungsart (380 Rechnung, 381 Gutschrift, 384 Korrektur)",
        "BT-5 Währung",
        "BT-72 Liefer-/Leistungsdatum",
        "BT-73 Leistungszeitraum von (optional)",
        "BT-74 Leistungszeitraum bis (optional)",
        "BT-9 Fällig am",
        "BT-20 Zahlungsbedingungen",
        "BT-10 Leitweg-ID / Käuferreferenz (Pflicht)",
        "BT-120 Grund der Steuerbefreiung (bei Kategorie ungleich S)",
        "BT-113 Bereits gezahlt",
        "BT-22 Bemerkung (optional)",
        "OB-1 Schlussformel (optional, nur im PDF, nicht in der XRechnung)",
        "BT-24 Spezifikation (XRechnung-Version)",
        "BT-23 Geschäftsprozess (optional)",
        "Verkäufer",
        "BT-27 Name",
        "BT-29 Verkäuferkennung (nötig ohne USt-IdNr.)",
        "BT-35 Straße",
        "BT-38 PLZ",
        "BT-37 Ort",
        "BT-40 Land",
        "BT-41 Kontaktperson",
        "BT-42 Telefon",
        "BT-43 E-Mail",
        "BT-34 Elektronische Adresse",
        "BT-34-1 Schema der elektronischen Adresse (EM = E-Mail)",
        "BT-31 USt-IdNr.",
        "BT-32 Steuernummer",
        "Käufer",
        "BT-44 Name",
        "BT-46 Käuferkennung (optional)",
        "BT-50 Straße",
        "BT-53 PLZ",
        "BT-52 Ort",
        "BT-55 Land",
        "BT-56 Kontaktperson (optional)",
        "BT-57 Telefon (optional)",
        "BT-58 E-Mail (optional)",
        "BT-49 Elektronische Adresse",
        "BT-49-1 Schema der elektronischen Adresse (EM = E-Mail, 0204 = Leitweg-ID)",
        "BT-48 USt-IdNr. (optional)",
        "Zahlung",
        "BT-81 Zahlungsart (58 SEPA-Überweisung)",
        "BT-83 Verwendungszweck",
        "BT-84 IBAN",
        "BT-85 Kontoinhaber",
        "BT-86 BIC (optional)",
        "Berechnet von openbilling (wird nicht gelesen)",
        "BT-106 Summe Positionen",
        "BT-109 Netto",
        "BT-110 Umsatzsteuer",
        "BT-112 Brutto",
        "BT-115 Zahlbetrag",
    };

    private static final String[] POSITIONEN = {
        "BT-126 Pos.",
        "BT-153 Bezeichnung",
        "BT-129 Menge",
        "BT-130 Einheit (HUR, C62, …)",
        "BT-146 Nettopreis",
        "BT-149 Preis je Menge (optional)",
        "BT-151 Steuerkategorie (S, E, AE, …)",
        "BT-152 Steuersatz %",
        "BT-154 Beschreibung (optional)",
        "BT-131 Zeilenbetrag (berechnet)",
    };

    private static final java.util.Set<String> BERECHNET =
            java.util.Set.of("BT-106", "BT-109", "BT-110", "BT-112", "BT-115", "BT-131");

    /** Eingabespalten des Blatts Positionen, in dieser Reihenfolge stehen sie in {@link Entwurf#positionen}. */
    public static final List<String> POSITIONSFELDER =
            List.of("BT-126", "BT-153", "BT-129", "BT-130", "BT-146", "BT-149", "BT-151", "BT-152", "BT-154");

    private static final java.util.Set<String> DATUMSFELDER = java.util.Set.of("BT-2", "BT-9", "BT-72", "BT-73", "BT-74");
    private static final java.util.Set<String> ZAHLENFELDER =
            java.util.Set.of("BT-113", "BT-129", "BT-146", "BT-149", "BT-152");

    /**
     * Rohe Eingaben als Text, auch unvollständig (Bedienoberfläche, Vorlagen).
     *
     * @param daten      BT-Kennung → Wert des Blatts Daten, leere Felder fehlen
     * @param positionen je Position ein Feld pro Eintrag von {@link #POSITIONSFELDER}, leer = {@code null}
     */
    public record Entwurf(Map<String, String> daten, List<String[]> positionen) {}

    private static final Pattern KENNUNG = Pattern.compile("^\\s*((?:BT|OB)-\\d+(?:-\\d+)?)\\b");
    private static final DateTimeFormatter DATUM_DE = DateTimeFormatter.ofPattern("d.M.uuuu");

    private RechnungOds() {}

    /** Eingaben unvollständig oder nicht lesbar. Enthält alle gefundenen Probleme, nicht nur das erste. */
    public static final class OdsFehler extends RuntimeException {
        private final List<String> probleme;

        OdsFehler(List<String> probleme) {
            super(String.join("\n", probleme));
            this.probleme = List.copyOf(probleme);
        }

        public List<String> probleme() {
            return probleme;
        }
    }

    // ---------------------------------------------------------------- Schreiben

    /** Leere Vorlage mit Vorgaben (Währung, Land, XRechnung 3.0 …). */
    public static void vorlage(Path ziel) throws IOException {
        vorlage(ziel, XRECHNUNG_30);
    }

    /** Leere Vorlage mit Vorgaben; {@code spezifikation} ist BT-24 (aus den Einstellungen). */
    public static void vorlage(Path ziel, String spezifikation) throws IOException {
        Map<String, Object> w = new HashMap<>(VORGABEN);
        w.put("BT-24", spezifikation);
        speichere(w, List.of(), ziel);
    }

    /** Schreibt die Rechnung samt berechneter Summen (nur zur Anzeige). */
    public static void schreibe(Rechnung r, Path ziel) throws IOException {
        Map<String, Object> w = new HashMap<>();
        w.put("BT-1", r.nummer());
        w.put("BT-2", r.datum());
        w.put("BT-3", r.typ());
        w.put("BT-5", r.waehrung());
        w.put("BT-72", r.lieferdatum());
        w.put("BT-73", r.leistungVon());
        w.put("BT-74", r.leistungBis());
        w.put("BT-22", r.bemerkung());
        w.put("OB-1", r.schlussformel());
        w.put("BT-9", r.faelligAm());
        w.put("BT-20", r.zahlungsbedingungen());
        w.put("BT-10", r.kaeuferReferenz());
        w.put("BT-120", r.befreiungsgrund());
        w.put("BT-113", r.vorausbezahlt());
        w.put("BT-24", r.spezifikation());
        w.put("BT-23", r.prozess());
        partei(w, r.verkaeufer(), "BT-27", "BT-29", "BT-35", "BT-38", "BT-37", "BT-40",
                "BT-41", "BT-42", "BT-43", "BT-34", "BT-31");
        w.put("BT-32", r.verkaeufer().steuernummer());
        partei(w, r.kaeufer(), "BT-44", "BT-46", "BT-50", "BT-53", "BT-52", "BT-55",
                "BT-56", "BT-57", "BT-58", "BT-49", "BT-48");
        w.put("BT-81", r.zahlung().art());
        w.put("BT-83", r.zahlung().verwendungszweck());
        w.put("BT-84", r.zahlung().iban());
        w.put("BT-85", r.zahlung().kontoinhaber());
        w.put("BT-86", r.zahlung().bic());

        var e = Berechnung.berechne(r);
        w.put("BT-106", e.summeZeilen());
        w.put("BT-109", e.netto());
        w.put("BT-110", e.steuer());
        w.put("BT-112", e.brutto());
        w.put("BT-115", e.zahlbar());

        List<Object[]> zeilen = new ArrayList<>();
        for (int i = 0; i < r.positionen().size(); i++) {
            Position p = r.positionen().get(i);
            zeilen.add(new Object[] {p.id(), p.bezeichnung(), p.menge(), p.einheit(), p.nettopreis(),
                p.basismenge(), p.kategorie().name(), p.steuersatz(), p.beschreibung(), e.zeilenbetraege().get(i)});
        }
        speichere(w, zeilen, ziel);
    }

    /**
     * Schreibt einen Entwurf, auch einen unvollständigen. Datums- und Zahlenwerte werden als solche
     * gespeichert, wenn sie lesbar sind. Die berechneten Summen kommen nur dazu, wenn der Entwurf vollständig ist.
     */
    public static void schreibe(Entwurf e, Path ziel) throws IOException {
        Map<String, Object> w = new HashMap<>();
        e.daten().forEach((k, v) -> w.put(k, typisiert(k, v)));
        List<BigDecimal> betraege = List.of();
        try {
            var erg = Berechnung.berechne(rechnung(e));
            w.put("BT-106", erg.summeZeilen());
            w.put("BT-109", erg.netto());
            w.put("BT-110", erg.steuer());
            w.put("BT-112", erg.brutto());
            w.put("BT-115", erg.zahlbar());
            betraege = erg.zeilenbetraege();
        } catch (OdsFehler | IllegalArgumentException unvollstaendig) {
            // dann ohne Summen
        }
        List<Object[]> zeilen = new ArrayList<>();
        for (int i = 0; i < e.positionen().size(); i++) {
            Object[] z = new Object[POSITIONEN.length];
            for (int c = 0; c < POSITIONSFELDER.size(); c++) {
                z[c] = typisiert(POSITIONSFELDER.get(c), e.positionen().get(i)[c]);
            }
            z[POSITIONSFELDER.size()] = betraege.size() == e.positionen().size() ? betraege.get(i) : null;
            zeilen.add(z);
        }
        speichere(w, zeilen, ziel);
    }

    private static Object typisiert(String k, String s) {
        Felder f = new Felder(s == null ? Map.of() : Map.of(k, s), new ArrayList<>(), "");
        Object v = DATUMSFELDER.contains(k) ? f.datum(k, false) : ZAHLENFELDER.contains(k) ? f.zahl(k, false) : null;
        return v != null ? v : s;
    }

    private static void partei(Map<String, Object> w, Partei p, String name, String kennung, String strasse,
            String plz, String ort, String land, String kontakt, String telefon, String email, String adresse,
            String ustId) {
        w.put(name, p.name());
        w.put(kennung, p.kennung());
        w.put(strasse, p.adresse().strasse());
        w.put(plz, p.adresse().plz());
        w.put(ort, p.adresse().ort());
        w.put(land, p.adresse().land());
        if (p.kontakt() != null) {
            w.put(kontakt, p.kontakt().name());
            w.put(telefon, p.kontakt().telefon());
            w.put(email, p.kontakt().email());
        }
        w.put(adresse, p.elektronischeAdresse().wert());
        w.put(adresse + "-1", p.elektronischeAdresse().schema());
        w.put(ustId, p.ustId());
    }

    private static void speichere(Map<String, Object> werte, List<Object[]> positionen, Path ziel)
            throws IOException {
        Style text = new Style();
        text.setDataStyle("@"); // getippte Werte bleiben Text: PLZ 01067, IBAN, Steuernummer
        Style ueberschrift = new Style();
        ueberschrift.setBold(true);

        Sheet daten = new Sheet(BLATT_DATEN, DATEN.length + 1, 2);
        daten.setDefaultColumnCellStyle(1, text);
        daten.setColumnWidth(0, 110.0);
        daten.setColumnWidth(1, 90.0);
        daten.getRange(0, 0, 1, 2).setValues("Feld", "Wert");
        daten.getRange(0, 0, 1, 2).setStyle(ueberschrift);
        for (int i = 0; i < DATEN.length; i++) {
            Range zeile = daten.getRange(i + 1, 0);
            zeile.setValue(DATEN[i]);
            String k = kennung(DATEN[i]);
            if (k == null) {
                zeile.setStyle(ueberschrift);
            } else if (werte.get(k) != null) {
                daten.getRange(i + 1, 1).setValue(werte.get(k));
            }
        }

        int leer = 20; // freie Zeilen zum Ausfüllen
        Sheet pos = new Sheet(BLATT_POSITIONEN, 1 + Math.max(positionen.size(), leer), POSITIONEN.length);
        for (int c : new int[] {0, 1, 3, 6, 8}) {
            pos.setDefaultColumnCellStyle(c, text);
        }
        pos.setColumnWidth(1, 80.0);
        pos.getRange(0, 0, 1, POSITIONEN.length).setValues((Object[]) POSITIONEN);
        pos.getRange(0, 0, 1, POSITIONEN.length).setStyle(ueberschrift);
        for (int i = 0; i < positionen.size(); i++) {
            pos.getRange(i + 1, 0, 1, POSITIONEN.length).setValues(positionen.get(i));
        }

        SpreadSheet ss = new SpreadSheet();
        ss.appendSheet(daten);
        ss.appendSheet(pos);
        ss.save(ziel.toFile());
    }

    // ---------------------------------------------------------------- Lesen

    public static Rechnung lese(Path quelle) throws IOException {
        Sheet[] b = blaetter(quelle);
        List<String> probleme = new ArrayList<>();
        return rechnung(datenWerte(b[0], probleme), b[1].getDataRange().getValues(), probleme);
    }

    /** Liest auch unvollständige Dateien (Vorlagen, angefangene Rechnungen), ohne Pflichtfelder zu prüfen. */
    public static Entwurf leseEntwurf(Path quelle) throws IOException {
        Sheet[] b = blaetter(quelle);
        Map<String, String> daten = new LinkedHashMap<>();
        datenWerte(b[0], new ArrayList<>()).forEach((k, v) -> {
            if (anzeige(v) != null) {
                daten.put(k, anzeige(v));
            }
        });
        Object[][] z = b[1].getDataRange().getValues();
        List<String[]> positionen = new ArrayList<>();
        for (int i = 1; i < z.length; i++) {
            String[] zeile = new String[POSITIONSFELDER.size()];
            boolean leer = true;
            for (int c = 0; c < z[0].length && c < z[i].length; c++) {
                String k = z[0][c] instanceof String kopf ? kennung(kopf) : null;
                int feld = k == null ? -1 : POSITIONSFELDER.indexOf(k);
                if (feld >= 0) {
                    zeile[feld] = anzeige(z[i][c]);
                    leer &= zeile[feld] == null;
                }
            }
            if (!leer) {
                positionen.add(zeile);
            }
        }
        return new Entwurf(daten, positionen);
    }

    /** Prüft Pflichtfelder und Formate eines Entwurfs wie {@link #lese}. */
    public static Rechnung rechnung(Entwurf e) {
        Object[][] zellen = new Object[e.positionen().size() + 1][];
        zellen[0] = POSITIONSFELDER.toArray();
        for (int i = 0; i < e.positionen().size(); i++) {
            zellen[i + 1] = e.positionen().get(i);
        }
        return rechnung(new HashMap<>(e.daten()), zellen, new ArrayList<>());
    }

    private static Sheet[] blaetter(Path quelle) throws IOException {
        SpreadSheet ss = new SpreadSheet(quelle.toFile());
        Sheet daten = ss.getSheet(BLATT_DATEN);
        Sheet pos = ss.getSheet(BLATT_POSITIONEN);
        if (daten == null || pos == null) {
            throw new OdsFehler(List.of("Blätter '" + BLATT_DATEN + "' und '" + BLATT_POSITIONEN
                    + "' werden benötigt, gefunden: " + ss.getSheets().stream().map(Sheet::getName).toList()));
        }
        return new Sheet[] {daten, pos};
    }

    /** Zellwert als Text, wie ihn ein Mensch eintippen würde: {@code 20.11.2026}, {@code 1234,5}. */
    static String anzeige(Object v) {
        if (v instanceof LocalDateTime dt) {
            v = dt.toLocalDate();
        }
        if (v instanceof LocalDate d) {
            return d.format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
        }
        String s = text(v);
        return v instanceof Double && s != null ? s.replace('.', ',') : s;
    }

    private static Rechnung rechnung(Map<String, Object> werte, Object[][] posZellen, List<String> probleme) {
        Felder f = new Felder(werte, probleme);

        var verkaeufer = new Partei(f.pflicht("BT-27"), f.text("BT-29"),
                new Adresse(f.text("BT-35"), f.pflicht("BT-38"), f.pflicht("BT-37"), f.pflicht("BT-40")),
                kontakt(f, "BT-41", "BT-42", "BT-43"),
                new ElektronischeAdresse(f.pflicht("BT-34-1"), f.pflicht("BT-34")),
                f.text("BT-31"), f.text("BT-32"));
        var kaeufer = new Partei(f.pflicht("BT-44"), f.text("BT-46"),
                new Adresse(f.text("BT-50"), f.pflicht("BT-53"), f.pflicht("BT-52"), f.pflicht("BT-55")),
                kontakt(f, "BT-56", "BT-57", "BT-58"),
                new ElektronischeAdresse(f.pflicht("BT-49-1"), f.pflicht("BT-49")),
                f.text("BT-48"), null);
        var zahlung = new Zahlung(f.pflicht("BT-81"), f.text("BT-83"), f.pflicht("BT-84"),
                f.text("BT-85"), f.text("BT-86"));
        String nummer = f.pflicht("BT-1");
        LocalDate datum = f.datum("BT-2", true);
        String typ = f.pflicht("BT-3");
        String waehrung = f.pflicht("BT-5");
        LocalDate faellig = f.datum("BT-9", false);
        String referenz = f.pflicht("BT-10");
        String bedingungen = f.text("BT-20");
        LocalDate lieferdatum = f.datum("BT-72", false);
        String befreiung = f.text("BT-120");
        BigDecimal voraus = f.zahl("BT-113", false);
        String spezifikation = f.pflicht("BT-24");
        String prozess = f.text("BT-23");
        String bemerkung = f.text("BT-22");
        LocalDate von = f.datum("BT-73", false);
        LocalDate bis = f.datum("BT-74", false);
        if (von != null && bis != null && bis.isBefore(von)) {
            probleme.add(BLATT_DATEN + ", BT-74: Leistungszeitraum endet vor seinem Beginn (BR-29)");
        }

        List<Position> positionen = positionen(posZellen, probleme);
        if (positionen.isEmpty() && probleme.isEmpty()) {
            probleme.add(BLATT_POSITIONEN + ": keine Position eingetragen (BG-25)");
        }
        if (!probleme.isEmpty()) {
            throw new OdsFehler(probleme);
        }
        return new Rechnung(spezifikation, prozess, nummer, datum, typ, waehrung, faellig, referenz,
                bedingungen, lieferdatum, verkaeufer, kaeufer, zahlung, befreiung, voraus, positionen,
                bemerkung, von, bis, f.text("OB-1"));
    }

    private static Kontakt kontakt(Felder f, String name, String telefon, String email) {
        String n = f.text(name);
        String t = f.text(telefon);
        String e = f.text(email);
        return n == null && t == null && e == null ? null : new Kontakt(n, t, e);
    }

    private static Map<String, Object> datenWerte(Sheet daten, List<String> probleme) {
        Map<String, Object> werte = new LinkedHashMap<>();
        Object[][] zellen = daten.getDataRange().getValues();
        for (int i = 0; i < zellen.length; i++) {
            if (zellen[i].length < 2 || !(zellen[i][0] instanceof String feld)) {
                continue;
            }
            String k = kennung(feld);
            if (k == null || BERECHNET.contains(k)) {
                continue;
            }
            if (werte.containsKey(k)) {
                probleme.add(BLATT_DATEN + " Zeile " + (i + 1) + ": " + k + " kommt doppelt vor");
            }
            werte.put(k, zellen[i][1]);
        }
        return werte;
    }

    private static List<Position> positionen(Object[][] zellen, List<String> probleme) {
        if (zellen.length == 0) {
            return List.of();
        }
        Map<String, Integer> spalte = new HashMap<>();
        for (int c = 0; c < zellen[0].length; c++) {
            if (zellen[0][c] instanceof String kopf && kennung(kopf) != null) {
                spalte.put(kennung(kopf), c);
            }
        }
        for (String pflicht : List.of("BT-153", "BT-129", "BT-130", "BT-146", "BT-151", "BT-152")) {
            if (!spalte.containsKey(pflicht)) {
                probleme.add(BLATT_POSITIONEN + ": Spalte " + pflicht + " fehlt in Zeile 1");
            }
        }
        if (!probleme.isEmpty()) {
            return List.of();
        }
        List<Position> liste = new ArrayList<>();
        for (int i = 1; i < zellen.length; i++) {
            Object[] z = zellen[i];
            Map<String, Object> w = new HashMap<>();
            boolean leer = true;
            for (var e : spalte.entrySet()) {
                Object v = e.getValue() < z.length ? z[e.getValue()] : null;
                if (BERECHNET.contains(e.getKey())) {
                    continue;
                }
                w.put(e.getKey(), v);
                leer &= text(v) == null;
            }
            if (leer) {
                continue;
            }
            String ort = BLATT_POSITIONEN + " Zeile " + (i + 1) + ", ";
            Felder f = new Felder(w, probleme, ort);
            String id = f.text("BT-126");
            String kat = f.pflicht("BT-151");
            Steuerkategorie kategorie = null;
            try {
                kategorie = kat == null ? null : Steuerkategorie.valueOf(kat.toUpperCase());
            } catch (IllegalArgumentException ex) {
                probleme.add(ort + "BT-151: unbekannte Steuerkategorie '" + kat + "'");
            }
            String bezeichnung = f.pflicht("BT-153");
            BigDecimal menge = f.zahl("BT-129", true);
            String einheit = f.pflicht("BT-130");
            BigDecimal preis = f.zahl("BT-146", true);
            BigDecimal basis = f.zahl("BT-149", false);
            BigDecimal satz = f.zahl("BT-152", kategorie != Steuerkategorie.O);
            if (preis != null && preis.signum() < 0) {
                probleme.add(ort + "BT-146: Nettopreis darf nicht negativ sein (BR-27)");
            }
            if (probleme.isEmpty()) {
                liste.add(new Position(id != null ? id : String.valueOf(liste.size() + 1), bezeichnung, menge,
                        einheit, preis, basis, kategorie, satz, f.text("BT-154")));
            }
        }
        return liste;
    }

    /** BT- oder OB-Kennung am Anfang einer Beschriftung, sonst {@code null}. */
    static String kennung(String beschriftung) {
        Matcher m = KENNUNG.matcher(beschriftung);
        return m.find() ? m.group(1) : null;
    }

    private static String text(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Double d) {
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        String s = v.toString().strip();
        return s.isEmpty() ? null : s;
    }

    /** Liest Werte zu BT-Kennungen und sammelt Probleme statt beim ersten abzubrechen. */
    private record Felder(Map<String, Object> werte, List<String> probleme, String ort) {

        Felder(Map<String, Object> werte, List<String> probleme) {
            this(werte, probleme, BLATT_DATEN + ", ");
        }

        String text(String k) {
            return RechnungOds.text(werte.get(k));
        }

        String pflicht(String k) {
            String s = text(k);
            if (s == null) {
                probleme.add(ort + k + ": Pflichtfeld ist leer" + (werte.containsKey(k) ? "" : " oder fehlt"));
            }
            return s;
        }

        BigDecimal zahl(String k, boolean pflicht) {
            Object v = werte.get(k);
            if (v instanceof Double d) {
                return BigDecimal.valueOf(d); // getippte Zahl: kürzeste exakte Darstellung, kein Float-Rest
            }
            String s = pflicht ? pflicht(k) : text(k);
            if (s == null) {
                return null;
            }
            String norm = s.replace(" ", "").replace("€", "");
            if (norm.contains(",")) { // deutsche Schreibweise: 1.234,50
                norm = norm.replace(".", "").replace(',', '.');
            }
            try {
                return new BigDecimal(norm);
            } catch (NumberFormatException ex) {
                probleme.add(ort + k + ": keine Zahl: '" + s + "'");
                return null;
            }
        }

        LocalDate datum(String k, boolean pflicht) {
            Object v = werte.get(k);
            if (v instanceof LocalDate d) {
                return d;
            }
            if (v instanceof LocalDateTime dt) {
                return dt.toLocalDate();
            }
            String s = pflicht ? pflicht(k) : text(k);
            if (s == null) {
                return null;
            }
            try {
                return s.contains(".") ? LocalDate.parse(s, DATUM_DE) : LocalDate.parse(s);
            } catch (DateTimeParseException ex) {
                probleme.add(ort + k + ": kein Datum (TT.MM.JJJJ): '" + s + "'");
                return null;
            }
        }
    }
}
