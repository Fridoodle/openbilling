package openbilling.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
