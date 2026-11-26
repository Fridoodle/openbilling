package openbilling.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    /** Größter Platz im Kopf; höher würde die Empfängeranschrift aus dem Umschlagfenster schieben. */
    static final double MAX_BREITE = 90;
    static final double MAX_HOEHE = 24;

    /** Längste Seite des gespeicherten Bildes in Pixeln, reicht für 300 dpi bei voller Breite. */
    static final int MAX_PIXEL = 1200;

    /**
     * Liest das gespeicherte Logo, {@code null} wenn es keins gibt.
     *
     * @param prozent Größe in Prozent des größten Platzes im Kopf (10 bis 100)
     */
    public static Logo lies(Path datei, int prozent) throws IOException {
        if (!Files.exists(datei)) {
            return null;
        }
        byte[] png = Files.readAllBytes(datei);
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(png));
        if (b == null) {
            throw new IOException("Das Logo ist kein lesbares Bild: " + datei);
        }
        double f = Math.clamp(prozent, 10, 100) / 100.0
                * Math.min(MAX_BREITE / b.getWidth(), MAX_HOEHE / b.getHeight());
        return new Logo(png, b.getWidth() * f, b.getHeight() * f);
    }

    /**
     * Speichert ein Bild (PNG, JPEG, GIF, BMP) als Logo: verkleinert auf {@link #MAX_PIXEL} und auf Weiß gelegt,
     * denn die Seite ist weiß, und ohne Alphakanal enthält das PDF/A keine Transparenz. Ein vorhandenes Logo
     * bleibt erhalten, wenn sich das Bild nicht lesen lässt.
     */
    public static void speichere(Path quelle, Path ziel) throws IOException {
        BufferedImage b = ImageIO.read(quelle.toFile());
        if (b == null) {
            throw new IOException("Kein unterstütztes Bild (PNG, JPEG, GIF, BMP): " + quelle.getFileName());
        }
        double f = Math.min(1, (double) MAX_PIXEL / Math.max(b.getWidth(), b.getHeight()));
        int w = Math.max(1, (int) Math.round(b.getWidth() * f));
        int h = Math.max(1, (int) Math.round(b.getHeight() * f));
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(b, 0, 0, w, h, null);
        g.dispose();
        Files.createDirectories(ziel.toAbsolutePath().getParent());
        ImageIO.write(rgb, "png", ziel.toFile());
    }
}
