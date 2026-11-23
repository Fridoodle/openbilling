package openbilling.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import openbilling.model.Rechnung;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.mustangproject.ClasspathResolverURIAdapter;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;

/**
 * Hybrid-PDF: Sichtkopie im eigenen Layout ({@link Layout}, XSL-FO über FOP) als PDF/A-3 mit eingebetteter
 * {@code xrechnung.xml} (Factur-X-Profil XRECHNUNG, AFRelationship Alternative).
 * Geprüft mit veraPDF: PDF/A-3u (knowledge/xrechnung-format.md, Abschnitt 8).
 * Die XML muss vorher den KoSIT-Validator bestanden haben.
 */
public final class PdfExport {

    /** Schriftgrößen in Punkt, die der Reihe nach probiert werden, bis die Rechnung auf eine Seite passt. */
    private static final int[] SCHRIFTGROESSEN = {10, 9, 8};

    private static FopFactory fop;

    private PdfExport() {}

    /** Hybrid-PDF: Sichtkopie mit eingebetteter XRechnung. {@code ciiXml} muss aus derselben Rechnung stammen. */
    public static byte[] hybrid(Rechnung r, String ciiXml) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var exporter = new ZUGFeRDExporterFromPDFA()) {
            exporter.load(sicht(r))
                    .setProducer("openbilling")
                    .setCreator("openbilling")
                    .setProfile(Profiles.getByName("XRECHNUNG"))
                    .setXML(ciiXml.getBytes(StandardCharsets.UTF_8));
            exporter.export(out);
        }
        return out.toByteArray();
    }

    /**
     * Nur die Sichtkopie (PDF/A-3b), ohne eingebettete XML. Das ist keine E-Rechnung.
     * Passt die Rechnung in keiner Schriftgröße auf eine Seite, wird sie in der größten mehrseitig.
     */
    public static byte[] sicht(Rechnung r) throws IOException {
        byte[] mehrseitig = null;
        for (int pt : SCHRIFTGROESSEN) {
            var out = new ByteArrayOutputStream();
            if (rendere(Layout.fo(r, pt), r.nummer(), out) == 1) {
                return out.toByteArray();
            }
            if (mehrseitig == null) {
                mehrseitig = out.toByteArray();
            }
        }
        return mehrseitig;
    }

    /** Schreibt das PDF und liefert die Seitenzahl. */
    private static int rendere(String fo, String titel, ByteArrayOutputStream out) throws IOException {
        try {
            FOUserAgent agent = fabrik().newFOUserAgent();
            agent.getRendererOptions().put("pdf-a-mode", "PDF/A-3b");
            agent.setTitle(titel);
            agent.setProducer("openbilling");
            agent.setCreator("openbilling");
            Fop lauf = fabrik().newFop("application/pdf", agent, out);
            TransformerFactory.newInstance().newTransformer()
                    .transform(new StreamSource(new StringReader(fo)), new SAXResult(lauf.getDefaultHandler()));
            return lauf.getResults().getPageCount();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("PDF-Erzeugung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** FOP mit Mustangs Konfiguration: eingebettete Schrift SourceSansPro und Farbprofil aus dessen JAR. */
    private static synchronized FopFactory fabrik() throws Exception {
        if (fop == null) {
            try (InputStream in = PdfExport.class.getClassLoader().getResourceAsStream("fop-config.xconf")) {
                URI basis = new File(".").toURI();
                var resolver = new ClasspathResolverURIAdapter();
                FopFactory f = new FopFactoryBuilder(basis, resolver)
                        .setConfiguration(new DefaultConfigurationBuilder().build(in)).build();
                f.getFontManager().setResourceResolver(
                        ResourceResolverFactory.createInternalResourceResolver(basis, resolver));
                fop = f;
            }
        }
        return fop;
    }
}
