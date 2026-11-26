package openbilling.config;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import openbilling.ods.RechnungOds;

/**
 * Benutzerordner und {@code einstellungen.properties} (siehe knowledge/decisions.md).
 * Windows: {@code %APPDATA%\openbilling}, sonst {@code $XDG_CONFIG_HOME/openbilling} bzw.
 * {@code ~/.config/openbilling} (macOS später {@code ~/Library/Application Support}).
 */
public final class Einstellungen {

    public static final String DATEI = "einstellungen.properties";

    /** Ordner mit scenarios.xml der XRechnung-Validator-Konfiguration. */
    public static final String KOSIT = "kosit.konfiguration";

    /** BT-24 für neue Vorlagen. */
    public static final String SPEZIFIKATION = "xrechnung.spezifikation";

    /** Größe des Logos in Prozent des größten Platzes im PDF-Kopf, siehe {@link #logo()}. */
    public static final String LOGO_GROESSE = "pdf.logo.groesse";

    /** Zuletzt exportierte Rechnungsnummer, Grundlage für den nächsten Vorschlag. */
    public static final String LETZTE_NUMMER = "rechnungsnummer.letzte";

    /** Rechnungsnummer: alles davor, letzte Ziffernfolge (der Zähler), alles danach ohne Ziffern. */
    private static final Pattern ZAEHLER = Pattern.compile("(.*?)(\\d+)(\\D*)");

    /** Jahreszahl im Teil vor dem Zähler, z. B. RE-2026-. */
    private static final Pattern JAHR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");

    private final Path ordner;
    private final Properties werte = new Properties();

    public Einstellungen(Path ordner) throws IOException {
        this.ordner = ordner;
        Path datei = ordner.resolve(DATEI);
        if (Files.exists(datei)) {
            try (Reader r = Files.newBufferedReader(datei, StandardCharsets.UTF_8)) {
                werte.load(r);
            }
        }
    }

    public static Einstellungen ausBenutzerprofil() throws IOException {
        return new Einstellungen(standardOrdner());
    }

    static Path standardOrdner() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) {
            return Path.of(appdata, "openbilling");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, "openbilling");
        }
        return Path.of(System.getProperty("user.home"), ".config", "openbilling");
    }

    public Path ordner() {
        return ordner;
    }

    /** Eigene Rechnungsvorlage; Stammdaten (Verkäufer, Bank) trägt man hier einmal ein. */
    public Path vorlage() {
        return ordner.resolve("vorlagen").resolve("standard.ods");
    }

    /** Ordner mit allen Rechnungsvorlagen ({@code *.ods}). */
    public Path vorlagen() {
        return ordner.resolve("vorlagen");
    }

    /** Adressbuch, siehe {@link openbilling.ods.Kunden}. */
    public Path kunden() {
        return ordner.resolve("kunden.ods");
    }

    /** Firmenlogo für die Sichtkopie (PDF), siehe {@link openbilling.pdf.Logo}; fehlt die Datei, gibt es keins. */
    public Path logo() {
        return ordner.resolve("logo.png");
    }

    /** {@link #LOGO_GROESSE}, Vorgabe 60 %. */
    public int logoGroesse() {
        try {
            return Integer.parseInt(werte.getProperty(LOGO_GROESSE, "60").strip());
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    public String wert(String schluessel, String vorgabe) {
        return werte.getProperty(schluessel, vorgabe);
    }

    /** Setzt einen Wert und schreibt {@code einstellungen.properties} sofort. */
    public void setze(String schluessel, String wert) throws IOException {
        werte.setProperty(schluessel, wert);
        Files.createDirectories(ordner);
        try (var w = Files.newBufferedWriter(ordner.resolve(DATEI), StandardCharsets.UTF_8)) {
            werte.store(w, "openbilling");
        }
    }

    /**
     * Einstellung {@code kosit.konfiguration}, sonst {@code tools/kosit/config} im Arbeitsverzeichnis
     * (Entwicklung, per setup-validator.ps1), sonst {@code kosit/config} neben {@code openbilling.jar}
     * (ausgeliefertes Paket, siehe scripts/package.ps1).
     */
    public Path kositKonfiguration() {
        String gesetzt = werte.getProperty(KOSIT);
        if (gesetzt != null && !gesetzt.isBlank()) {
            return Path.of(gesetzt);
        }
        Path entwicklung = Path.of("tools/kosit/config");
        if (Files.exists(entwicklung.resolve("scenarios.xml"))) {
            return entwicklung;
        }
        try {
            Path jar = Path.of(Einstellungen.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path mitgeliefert = jar.resolveSibling("kosit").resolve("config");
            if (Files.exists(mitgeliefert.resolve("scenarios.xml"))) {
                return mitgeliefert;
            }
        } catch (Exception e) {
            // kein JAR-Pfad ermittelbar: bei der Vorgabe bleiben
        }
        return entwicklung;
    }

    /**
     * Vorschlag für die nächste Rechnungsnummer (BT-1): die zuletzt exportierte Nummer plus eins, mit gleicher
     * Stellenzahl. Enthält sie ein früheres Jahr, beginnt die Zählung im neuen Jahr bei 1. Ohne bisherigen Export
     * {@code JJJJ-001}.
     */
    public String naechsteNummer(LocalDate heute) {
        return naechste(werte.getProperty(LETZTE_NUMMER), heute.getYear());
    }

    /**
     * Merkt sich eine exportierte Rechnungsnummer als Grundlage für {@link #naechsteNummer}. Eine kleinere Nummer
     * desselben Schemas (z. B. ein erneuter Export einer alten Rechnung) ändert nichts.
     */
    public void merkeNummer(String nummer) throws IOException {
        String letzte = werte.getProperty(LETZTE_NUMMER);
        if (nummer == null || nummer.isBlank() || nummer.equals(letzte)) {
            return;
        }
        Matcher neu = ZAEHLER.matcher(nummer);
        Matcher alt = letzte == null ? null : ZAEHLER.matcher(letzte);
        if (alt != null && neu.matches() && alt.matches() && neu.group(1).equals(alt.group(1))
                && new BigInteger(neu.group(2)).compareTo(new BigInteger(alt.group(2))) <= 0) {
            return;
        }
        setze(LETZTE_NUMMER, nummer.strip());
    }

    static String naechste(String letzte, int jahr) {
        if (letzte == null || letzte.isBlank()) {
            return jahr + "-001";
        }
        Matcher m = ZAEHLER.matcher(letzte.strip());
        if (!m.matches()) {
            return letzte.strip() + "-1";
        }
        String vorn = m.group(1);
        String zahl = m.group(2);
        Matcher j = JAHR.matcher(vorn);
        String fortlaufend;
        if (j.find() && Integer.parseInt(j.group(1)) < jahr) {
            vorn = vorn.substring(0, j.start(1)) + jahr + vorn.substring(j.end(1));
            fortlaufend = "1";
        } else {
            fortlaufend = new BigInteger(zahl).add(BigInteger.ONE).toString();
        }
        String nullen = "0".repeat(Math.max(0, zahl.length() - fortlaufend.length()));
        return vorn + nullen + fortlaufend + m.group(3);
    }

    public String spezifikation() {
        return werte.getProperty(SPEZIFIKATION, RechnungOds.XRECHNUNG_30);
    }

    /** Legt die Vorlage an, falls sie fehlt. Liefert {@code true}, wenn sie neu angelegt wurde. */
    public boolean vorlageAnlegen() throws IOException {
        if (Files.exists(vorlage())) {
            return false;
        }
        Files.createDirectories(vorlage().getParent());
        RechnungOds.vorlage(vorlage(), spezifikation());
        return true;
    }
}
