package openbilling.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Firmenlogo oben rechts in der Sichtkopie, anstelle des Firmennamens. Nur im PDF, nicht in der XML.
 *
 * @param png    Bild als PNG
 * @param breite Breite im PDF in mm
 * @param hoehe  Höhe im PDF in mm
 */
public record Logo(byte[] png, double breite, double hoehe) {

    /** Feste Größe im PDF-Kopf in mm; höher würde die Empfängeranschrift aus dem Umschlagfenster schieben. */
    public static final double BREITE = 72;
    public static final double HOEHE = 24;

    /** Größe des zugeschnittenen Bildes in Pixeln: Seitenverhältnis wie im PDF, gut 300 dpi. */
    public static final int PIXEL_BREITE = 900;
    public static final int PIXEL_HOEHE = 300;

    /** Liest das gespeicherte Logo, {@code null} wenn es keins gibt. Es füllt {@link #BREITE} × {@link #HOEHE}. */
    public static Logo lies(Path datei) throws IOException {
        if (!Files.exists(datei)) {
            return null;
        }
        byte[] png = Files.readAllBytes(datei);
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(png));
        if (b == null) {
            throw new IOException("Das Logo ist kein lesbares Bild: " + datei);
        }
        // eine von Hand ersetzte Datei mit anderem Seitenverhältnis wird unverzerrt eingepasst
        double f = Math.min(BREITE / b.getWidth(), HOEHE / b.getHeight());
        return new Logo(png, b.getWidth() * f, b.getHeight() * f);
    }

    /** Speichert das zugeschnittene Bild als Logo (PNG). */
    public static void speichere(BufferedImage bild, Path ziel) throws IOException {
        Files.createDirectories(ziel.toAbsolutePath().getParent());
        ImageIO.write(bild, "png", ziel.toFile());
    }
}
