package openbilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.junit.jupiter.api.Test;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer;

/**
 * Spike: Kann Mustang aus der geprüften Fixture-XML ein PDF/A-3 mit eingebetteter XRechnung bauen?
 * Gibt aus, was wirklich im PDF steht (Anhangname, AFRelationship, XMP), um die
 * (unverifiziert)-Punkte in knowledge/xrechnung-format.md Abschnitt 8 zu klären.
 */
class FacturXSpikeTest {

    private static final Path FIXTURE = Path.of("tests/fixtures/minimal-cii.xml");
    private static final Path OUT = Path.of("out/spike");

    @Test
    void visualisierenUndEinbetten() throws Exception {
        Files.createDirectories(OUT);
        Path sicht = OUT.resolve("sicht.pdf");
        Path hybrid = OUT.resolve("hybrid.pdf");

        new ZUGFeRDVisualizer()
                .toPDF(FIXTURE.toString(), sicht.toString(), ZUGFeRDVisualizer.Language.DE);
        System.out.println("=== Visualisierung ===");
        bericht(sicht);

        try (var exporter = new ZUGFeRDExporterFromPDFA()) {
            exporter.load(sicht.toString())
                    .setProducer("openbilling")
                    .setCreator("openbilling")
                    .setProfile(Profiles.getByName("XRECHNUNG"))
                    .setXML(Files.readAllBytes(FIXTURE));
            exporter.export(hybrid.toString());
        }
        System.out.println("=== Hybrid ===");
        String xmp = bericht(hybrid);
        assertNotNull(xmp);

        var validator = new org.mustangproject.validator.ZUGFeRDValidator();
        String report = validator.validate(hybrid.toString());
        Files.writeString(OUT.resolve("mustang-report.xml"), report);
        System.out.println("=== Mustang-Validator ===\n" + report);
        org.junit.jupiter.api.Assertions.assertTrue(validator.wasCompletelyValid(),
                "Mustang/veraPDF meldet Fehler, siehe out/spike/mustang-report.xml");
    }

    /** Druckt XMP und eingebettete Dateien; liefert das XMP. */
    private static String bericht(Path pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String xmp = doc.getDocumentCatalog().getMetadata() == null
                    ? null
                    : new String(doc.getDocumentCatalog().getMetadata().toByteArray(),
                            java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("PDF-Version: " + doc.getVersion());
            System.out.println("XMP:\n" + xmp);
            PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
            if (names != null && names.getEmbeddedFiles() != null) {
                for (var e : names.getEmbeddedFiles().getNames().entrySet()) {
                    PDComplexFileSpecification fs = e.getValue();
                    System.out.println("Anhang: " + e.getKey()
                            + " | AFRelationship=" + fs.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship"))
                            + " | Subtype=" + fs.getEmbeddedFile().getSubtype());
                }
            }
            System.out.println("/AF im Catalog: "
                    + doc.getDocumentCatalog().getCOSObject().containsKey(COSName.getPDFName("AF")));
            return xmp;
        }
    }

    @Test
    void fixtureVorhanden() {
        assertEquals(true, Files.exists(FIXTURE));
    }
}
