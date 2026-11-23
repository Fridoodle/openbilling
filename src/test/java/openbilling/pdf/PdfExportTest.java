package openbilling.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import openbilling.Testdaten;
import openbilling.xml.CiiSchreiber;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;
import org.mustangproject.validator.ZUGFeRDValidator;

/** Hybrid-PDF: PDF/A-3 laut veraPDF, eingebettete XML unverändert und mit Factur-X-Metadaten. */
class PdfExportTest {

    @Test
    void hybridIstPdfA3MitEingebetteterXRechnung() throws Exception {
        String xml = CiiSchreiber.schreibe(Testdaten.minimal());
        byte[] pdf = PdfExport.hybrid(xml);
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
}
