package openbilling.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import openbilling.Testdaten;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.xml.CiiSchreiber;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.mustangproject.validator.ZUGFeRDValidator;

/** Hybrid-PDF: PDF/A-3 laut veraPDF, eingebettete XML unverändert und mit Factur-X-Metadaten. */
class PdfExportTest {

    @Test
    void hybridIstPdfA3MitEingebetteterXRechnung() throws Exception {
        String xml = CiiSchreiber.schreibe(Testdaten.minimal());
        byte[] pdf = PdfExport.hybrid(Testdaten.minimal(), xml);
        Path out = Path.of("out/pdf");
        Files.createDirectories(out);
        Path datei = out.resolve("minimal.pdf");
        Files.write(datei, pdf);

        try (var doc = Loader.loadPDF(pdf)) {
            var catalog = doc.getDocumentCatalog();
            var anhaenge = catalog.getNames().getEmbeddedFiles().getNames();
            assertEquals(1, anhaenge.size());
            var fs = anhaenge.get("xrechnung.xml");
            assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), fs.getEmbeddedFile().toByteArray());
            assertEquals("Alternative", fs.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship")));
            assertTrue(catalog.getCOSObject().containsKey(COSName.getPDFName("AF")), "/AF fehlt im Catalog");
            String xmp = new String(catalog.getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("<fx:ConformanceLevel>XRECHNUNG</fx:ConformanceLevel>"), xmp);
            assertTrue(xmp.contains("<pdfaid:part>3</pdfaid:part>"), xmp);
        }

        var validator = new ZUGFeRDValidator();
        String bericht = validator.validate(datei.toString());
        Files.writeString(out.resolve("minimal-mustang-bericht.xml"), bericht);
        assertTrue(validator.wasCompletelyValid(), bericht);
    }

    @Test
    void zugferdEn16931BettetFacturXXmlEin() throws Exception {
        var r = Testdaten.b2b();
        String xml = CiiSchreiber.schreibe(r);
        byte[] pdf = PdfExport.hybrid(r, xml);
        Path datei = Files.createDirectories(Path.of("out/pdf")).resolve("b2b-en16931.pdf");
        Files.write(datei, pdf);
        assertEquals("factur-x.xml", PdfExport.anhang(r));

        try (var doc = Loader.loadPDF(pdf)) {
            var catalog = doc.getDocumentCatalog();
            var anhaenge = catalog.getNames().getEmbeddedFiles().getNames();
            assertEquals(1, anhaenge.size());
            var fs = anhaenge.get("factur-x.xml");
            assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), fs.getEmbeddedFile().toByteArray());
            assertEquals("Alternative", fs.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship")));
            String xmp = new String(catalog.getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>"), xmp);
            assertTrue(xmp.contains("<fx:DocumentFileName>factur-x.xml</fx:DocumentFileName>"), xmp);
        }

        var validator = new ZUGFeRDValidator();
        String bericht = validator.validate(datei.toString());
        Files.writeString(Path.of("out/pdf/b2b-en16931-mustang-bericht.xml"), bericht);
        assertTrue(validator.wasCompletelyValid(), bericht);
    }

    @Test
    void sichtZeigtRechnungOhneTechnischeAngaben() throws Exception {
        try (var doc = Loader.loadPDF(PdfExport.sicht(Testdaten.minimal()))) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("RE-2026-0001"), text);
            assertTrue(text.contains("1.011,50 €"), text);
            assertTrue(text.contains("DE02 1203 0000 0000 2020 51"), text);
            assertTrue(!text.contains("urn:") && !text.contains("04011000-12345-03"), text);
        }
    }

    @Test
    void rabatteZuschlaegeUndSkontoImPdf() throws Exception {
        var r = Testdaten.rabattiert();
        byte[] pdf = PdfExport.hybrid(r, openbilling.xml.CiiSchreiber.schreibe(r));
        Path datei = Files.createDirectories(Path.of("out/pdf")).resolve("rabatte.pdf");
        Files.write(datei, pdf);
        try (var doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.contains("10 % Rabatt auf 100,00 €: −10,00 €"), text);
            assertTrue(text.contains("Treuerabatt 5 %") && text.contains("−10,50 €"), text);
            assertTrue(text.contains("Versand") && text.contains("+4,90 €"), text);
            assertTrue(text.contains("2 % Skonto (4,82 €)"), text);
            assertTrue(!text.contains("#SKONTO#"), text);
        }
        var validator = new ZUGFeRDValidator();
        String bericht = validator.validate(datei.toString());
        assertTrue(validator.wasCompletelyValid(), bericht);
    }

    @Test
    void lastschriftMitVorabankuendigung() throws Exception {
        var r = Testdaten.mitZahlung(Testdaten.lastschrift());
        byte[] pdf = PdfExport.hybrid(r, CiiSchreiber.schreibe(r));
        Path datei = Files.createDirectories(Path.of("out/pdf")).resolve("lastschrift.pdf");
        Files.write(datei, pdf);
        try (var doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.contains("SEPA-Lastschrift") && text.contains("MANDAT-0042")
                    && text.contains("DE98ZZZ09999999999"), text);
            assertTrue(text.contains("Den Betrag von 1.011,50 € ziehen wir am 07.12.2026 per SEPA-Lastschrift von"
                    + " Ihrem Konto DE89 … 3000 ein."), text);
            assertTrue(!text.contains("DE89370400440532013000") && !text.contains("DE02 1203"), text);
        }
        var validator = new ZUGFeRDValidator();
        assertTrue(validator.validate(datei.toString()) != null && validator.wasCompletelyValid());
    }

    @Test
    void kartenzahlungZeigtNurLetzteZiffern() throws Exception {
        var z = new openbilling.model.Rechnung.Zahlung("54", null, null, null, null, null, null,
                new openbilling.model.Rechnung.Karte("4242", null));
        try (var doc = Loader.loadPDF(PdfExport.sicht(Testdaten.mitZahlung(z)))) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.contains("Kreditkarte") && text.contains("endet auf 4242"), text);
        }
    }

    @Test
    void schlussformelStehtUntenImPdf() throws Exception {
        var r = Testdaten.mitSchlussformel("Vielen Dank für die gute Zusammenarbeit.\nMit freundlichen Grüßen\n"
                + "Max Mustermann");
        try (var doc = Loader.loadPDF(PdfExport.sicht(r))) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.contains("Mit freundlichen Grüßen\nMax Mustermann"), text);
            assertTrue(text.indexOf("Max Mustermann") > text.indexOf("Zahlbar innerhalb"), text);
        }
    }

    @Test
    void firmennameStehtImKopfNameBleibtBeimAbsender() throws Exception {
        var r = Testdaten.mitFirmenname("Studio Beispiel");
        try (var doc = Loader.loadPDF(PdfExport.sicht(r))) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.startsWith("Studio Beispiel\n"), text);
            assertTrue(text.contains(r.verkaeufer().name()), text);
        }
        try (var doc = Loader.loadPDF(PdfExport.sicht(Testdaten.mitFirmenname(" ")))) {
            String text = new PDFTextStripper().getText(doc).replace("\r", "");
            assertTrue(text.startsWith(Testdaten.minimal().verkaeufer().name() + "\n"), text);
        }
    }

    @Test
    void logoErsetztFirmennameUndLaesstAnschriftImFenster() throws Exception {
        Path ordner = Files.createDirectories(Path.of("out/pdf"));
        Path gespeichert = ordner.resolve("logo.png");
        Logo.speichere(new BufferedImage(Logo.PIXEL_BREITE, Logo.PIXEL_HOEHE, BufferedImage.TYPE_INT_RGB), gespeichert);
        Logo logo = Logo.lies(gespeichert);
        assertEquals(Logo.BREITE, logo.breite(), 0.01);
        assertEquals(Logo.HOEHE, logo.hoehe(), 0.01);
        assertNull(Logo.lies(ordner.resolve("fehlt.png")));
        // von Hand ersetzt, quadratisch: unverzerrt eingepasst
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", gespeichert.toFile());
        assertEquals(Logo.HOEHE, Logo.lies(gespeichert).breite(), 0.01);
        logo = new Logo(Files.readAllBytes(gespeichert), Logo.HOEHE, Logo.HOEHE);

        var r = Testdaten.mitFirmenname("Studio Beispiel");
        byte[] mit = PdfExport.sicht(r, logo);
        try (var doc = Loader.loadPDF(mit)) {
            assertFalse(new PDFTextStripper().getText(doc).contains("Studio Beispiel"));
        }
        assertEquals(oben(PdfExport.sicht(r), "Rechnungsempfänger"), oben(mit, "Rechnungsempfänger"), 0.5);

        Path datei = ordner.resolve("logo.pdf");
        Files.write(datei, PdfExport.hybrid(r, CiiSchreiber.schreibe(r), logo));
        var validator = new ZUGFeRDValidator();
        String bericht = validator.validate(datei.toString());
        assertTrue(validator.wasCompletelyValid(), bericht);
    }

    /** Abstand des ersten Vorkommens von {@code text} vom oberen Seitenrand in pt. */
    private static float oben(byte[] pdf, String text) throws Exception {
        float[] y = {-1};
        try (var doc = Loader.loadPDF(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void writeString(String t, List<TextPosition> p) throws java.io.IOException {
                    if (y[0] < 0 && t.contains(text)) {
                        y[0] = p.get(0).getYDirAdj();
                    }
                }
            }.getText(doc);
        }
        assertTrue(y[0] >= 0, text + " nicht gefunden");
        return y[0];
    }

    @Test
    void vielePositionenPassenDurchKleinereSchriftAufEineSeite() throws Exception {
        var r = Testdaten.rechnung(IntStream.rangeClosed(1, 32)
                .mapToObj(i -> Testdaten.pos(String.valueOf(i), "1", "10.00", Steuerkategorie.S, "19")).toList());
        byte[] pdf = PdfExport.sicht(r);
        Files.write(Files.createDirectories(Path.of("out/pdf")).resolve("viele.pdf"), pdf);
        try (var doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
        }
        var zuViele = Testdaten.rechnung(IntStream.rangeClosed(1, 120)
                .mapToObj(i -> Testdaten.pos(String.valueOf(i), "1", "10.00", Steuerkategorie.S, "19")).toList());
        try (var doc = Loader.loadPDF(PdfExport.sicht(zuViele))) {
            assertTrue(doc.getNumberOfPages() > 1);
        }
    }
}
