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
import openbilling.ods.RechnungOds;
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
 * Hybrid-PDF nach ZUGFeRD 2.x / Factur-X: Sichtkopie im eigenen Layout ({@link Layout}, XSL-FO über FOP) als
 * PDF/A-3 mit eingebetteter CII-XML, AFRelationship Alternative. Profil XRECHNUNG ({@code xrechnung.xml}) oder
 * EN 16931 ({@code factur-x.xml}), je nach BT-24.
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
        return hybrid(r, ciiXml, null);
    }

    /** Wie {@link #hybrid(Rechnung, String)}, mit Logo im Kopf ({@code null}: ohne). */
    public static byte[] hybrid(Rechnung r, String ciiXml, Logo logo) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var exporter = new ZUGFeRDExporterFromPDFA()) {
            exporter.load(sicht(r, logo))
                    .setProducer("openbilling")
                    .setCreator("openbilling")
                    .setProfile(Profiles.getByName(profil(r)))
                    .setXML(ciiXml.getBytes(StandardCharsets.UTF_8));
            exporter.export(out);
        }
        return out.toByteArray();
    }

    /**
     * ZUGFeRD-Profil zu BT-24. Mustang wählt danach Anhangsname ({@code xrechnung.xml} bzw. {@code factur-x.xml})
     * und die XMP-ConformanceLevel ({@code XRECHNUNG} bzw. {@code EN 16931}).
     */
    static String profil(Rechnung r) {
        if (r.xrechnung()) {
            return "XRECHNUNG";
        }
        if (RechnungOds.EN16931.equals(r.spezifikation())) {
            return "EN16931";
        }
        throw new IllegalArgumentException("Kein ZUGFeRD-Profil für BT-24 " + r.spezifikation());
    }

    /** Name der eingebetteten XML. */
    public static String anhang(Rechnung r) {
        return r.xrechnung() ? "xrechnung.xml" : "factur-x.xml";
    }

    /**
     * Nur die Sichtkopie (PDF/A-3b), ohne eingebettete XML. Das ist keine E-Rechnung.
     * Passt die Rechnung in keiner Schriftgröße auf eine Seite, wird sie in der größten mehrseitig.
     */
    public static byte[] sicht(Rechnung r) throws IOException {
        return sicht(r, null);
    }

    /** Wie {@link #sicht(Rechnung)}, mit Logo im Kopf ({@code null}: ohne). */
    public static byte[] sicht(Rechnung r, Logo logo) throws IOException {
        byte[] mehrseitig = null;
        for (int pt : SCHRIFTGROESSEN) {
            var out = new ByteArrayOutputStream();
            if (rendere(Layout.fo(r, pt, logo), r.nummer(), out) == 1) {
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
                // ohne Complex-Script-Features setzt FOP keine Ligaturen („ft“ in „Lastschrift“), die sich
                // aus dem PDF nicht als Text kopieren lassen (lessons-learned.md)
                FopFactory f = new FopFactoryBuilder(basis, resolver)
                        .setConfiguration(new DefaultConfigurationBuilder().build(in))
                        .setComplexScriptFeatures(false).build();
                f.getFontManager().setResourceResolver(
                        ResourceResolverFactory.createInternalResourceResolver(basis, resolver));
                fop = f;
            }
        }
        return fop;
    }
}
