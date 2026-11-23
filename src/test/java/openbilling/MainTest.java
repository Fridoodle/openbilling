package openbilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import openbilling.config.Einstellungen;
import openbilling.ods.RechnungOds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ganzer Ablauf über die Kommandozeile, mit eigenem Benutzerordner statt %APPDATA%. */
class MainTest {

    @TempDir
    Path tmp;

    private Main main;
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @BeforeEach
    void setUp() throws Exception {
        Path profil = tmp.resolve("appdata");
        Files.createDirectories(profil);
        // Konfiguration absolut angeben, weil der Test im Projektordner läuft
        Files.writeString(profil.resolve(Einstellungen.DATEI), Einstellungen.KOSIT + "="
                + Path.of("tools/kosit/config").toAbsolutePath().toString().replace("\\", "/") + "\n");
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        main = new Main(new Einstellungen(profil),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String ausgabe() {
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void exportSchreibtXmlUndPdfUndSchuetztVorUeberschreiben() throws Exception {
        Path ods = tmp.resolve("RE-2026-0001.ods");
        Files.copy(RechnungOdsTestFixture.PFAD, ods);

        assertEquals(Main.OK, main.ausfuehren("export", ods.toString()), this::ausgabe);
        assertTrue(Files.size(tmp.resolve("RE-2026-0001.pdf")) > 1000);
        assertTrue(Files.readString(tmp.resolve("RE-2026-0001.xml")).contains("RE-2026-0001"));

        assertEquals(Main.FEHLER, main.ausfuehren("export", ods.toString()), "zweiter Export ohne Flag");
        assertTrue(ausgabe().contains("GoBD"), ausgabe());
        assertEquals(Main.OK, main.ausfuehren("export", ods.toString(), "--ueberschreiben"), this::ausgabe);
    }

    @Test
    void pruefenZeigtSummenUndErgebnis() {
        assertEquals(Main.OK, main.ausfuehren("pruefen", RechnungOdsTestFixture.PFAD.toString()), this::ausgabe);
        assertTrue(ausgabe().contains("netto 850.00, USt 161.50, zahlbar 1011.50 EUR"), ausgabe());
        assertTrue(ausgabe().contains("Gültige XRechnung"), ausgabe());
    }

    @Test
    void neuLegtVorlageAnUndNeueRechnungIstNochUnvollstaendig() throws Exception {
        Path ods = tmp.resolve("rechnungen/2026/neu.ods");
        assertEquals(Main.OK, main.ausfuehren("neu", ods.toString()), this::ausgabe);
        assertTrue(Files.exists(ods));
        assertTrue(ausgabe().contains("Vorlage angelegt"), ausgabe());

        assertEquals(Main.FEHLER, main.ausfuehren("neu", ods.toString()), "existiert schon");
        assertEquals(Main.FEHLER, main.ausfuehren("pruefen", ods.toString()));
        assertTrue(ausgabe().contains("BT-1: Pflichtfeld ist leer"), ausgabe());
    }

    @Test
    void neueRechnungUebernimmtStammdatenAusEigenerVorlage() throws Exception {
        // eigene Vorlage = ausgefüllte Rechnung; neue Rechnungen erben deren Inhalt
        Path profilVorlage = tmp.resolve("appdata/vorlagen/standard.ods");
        Files.createDirectories(profilVorlage.getParent());
        RechnungOds.schreibe(Testdaten.minimal(), profilVorlage);
        Path ods = tmp.resolve("zweite.ods");
        assertEquals(Main.OK, main.ausfuehren("neu", ods.toString()), this::ausgabe);
        assertEquals("Beispiel GmbH", RechnungOds.lese(ods).verkaeufer().name());
    }

    @Test
    void ungueltigeRechnungLiefertExitEinsMitRegelId() throws Exception {
        var basis = Testdaten.minimal();
        var v = basis.verkaeufer();
        var ohneKennung = new openbilling.model.Rechnung.Partei(v.name(), null, v.adresse(), v.kontakt(),
                v.elektronischeAdresse(), null, "30/123/45678");
        Path ods = tmp.resolve("ungueltig.ods");
        RechnungOds.schreibe(new openbilling.model.Rechnung(basis.spezifikation(), basis.prozess(), basis.nummer(),
                basis.datum(), basis.typ(), basis.waehrung(), basis.faelligAm(), basis.kaeuferReferenz(),
                basis.zahlungsbedingungen(), basis.lieferdatum(), ohneKennung, basis.kaeufer(), basis.zahlung(),
                null, null, basis.positionen()), ods);

        assertEquals(Main.UNGUELTIG, main.ausfuehren("export", ods.toString()));
        assertTrue(ausgabe().contains("BR-CO-26"), ausgabe());
        assertTrue(!Files.exists(tmp.resolve("ungueltig.pdf")), "kein PDF bei ungültiger Rechnung");
    }

    @Test
    void hilfeUndFalscheAufrufe() {
        assertEquals(Main.OK, main.ausfuehren());
        assertTrue(ausgabe().contains("pruefen <rechnung.ods>"));
        assertEquals(Main.FEHLER, main.ausfuehren("drucken", "x.ods"));
        assertEquals(Main.FEHLER, main.ausfuehren("pruefen"));
    }

    /** Pfad der eingecheckten Fixture, gemeinsam mit RechnungOdsTest. */
    static final class RechnungOdsTestFixture {
        static final Path PFAD = Path.of("tests/fixtures/minimal.ods");
    }
}
