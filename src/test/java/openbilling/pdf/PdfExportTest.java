package openbilling.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import openbilling.Testdaten;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.xml.CiiSchreiber;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
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
