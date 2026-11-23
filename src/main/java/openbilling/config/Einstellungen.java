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

    /** Vorgabe {@code tools/kosit/config} (relativ zum Arbeitsverzeichnis, per setup-validator.ps1). */
    public Path kositKonfiguration() {
        return Path.of(werte.getProperty(KOSIT, "tools/kosit/config"));
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
