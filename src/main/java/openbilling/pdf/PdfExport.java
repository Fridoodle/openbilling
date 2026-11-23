package openbilling.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer;

/**
 * Hybrid-PDF: Sichtkopie (Mustang-Visualisierung über FOP) als PDF/A-3 mit eingebetteter
 * {@code xrechnung.xml} (Factur-X-Profil XRECHNUNG, AFRelationship Alternative).
 * Geprüft mit veraPDF: PDF/A-3u (knowledge/xrechnung-format.md, Abschnitt 8).
 * Die XML muss vorher den KoSIT-Validator bestanden haben.
 */
public final class PdfExport {

    private PdfExport() {}

    /** Hybrid-PDF: Sichtkopie mit eingebetteter XRechnung. */
    public static byte[] hybrid(String ciiXml) throws IOException {
        byte[] xml = ciiXml.getBytes(StandardCharsets.UTF_8);
        var out = new ByteArrayOutputStream();
        try (var exporter = new ZUGFeRDExporterFromPDFA()) {
            exporter.load(sicht(ciiXml))
                    .setProducer("openbilling")
                    .setCreator("openbilling")
                    .setProfile(Profiles.getByName("XRECHNUNG"))
                    .setXML(xml);
            exporter.export(out);
        }
        return out.toByteArray();
    }

    /** Nur die Sichtkopie, ohne eingebettete XML. Das ist keine E-Rechnung. */
    public static byte[] sicht(String ciiXml) throws IOException {
        byte[] xml = ciiXml.getBytes(StandardCharsets.UTF_8);
        // Visualizer liest nur aus Dateien. Die byte[]-Variante toPDF(xml, sprache) ist in Mustang 2.26.0
        // defekt (NullPointerException), deshalb über eine temporäre PDF-Datei (lessons-learned.md).
        Path tmp = Files.createTempFile("openbilling-", ".xml");
        Path tmpPdf = Files.createTempFile("openbilling-", ".pdf");
        try {
            Files.write(tmp, xml);
            new ZUGFeRDVisualizer().toPDF(tmp.toString(), tmpPdf.toString(), ZUGFeRDVisualizer.Language.DE);
            byte[] sicht = Files.readAllBytes(tmpPdf);
            if (sicht.length == 0) {
                throw new IOException("Visualisierung hat kein PDF erzeugt");
            }
            return sicht;
        } finally {
            Files.deleteIfExists(tmp);
            Files.deleteIfExists(tmpPdf);
        }
    }
}
