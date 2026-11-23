package openbilling.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Programmsymbol: bernsteinfarbener Kassenbon mit €, der aus einem Schlitz auf einer Petrol-Kachel kommt.
 * Einzige Quelle des Symbols. Das Fenster nutzt {@link #bilder()}, {@code scripts/package.ps1} schreibt über
 * {@link #main} daraus die .ico für openbilling.exe, Verknüpfungen und Installer.
 */
final class Symbol {

    private static final int[] GROESSEN = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
    private static final Color KACHEL_HELL = new Color(0x13707F);
    private static final Color KACHEL_DUNKEL = new Color(0x0A3D4F);
    private static final Color SCHLITZ = new Color(0x052530);
    private static final Color BON_OBEN = new Color(0xFFD45C);
    private static final Color BON_UNTEN = new Color(0xFFAE1F);
    private static final Color EURO = new Color(0x0B3A4A);

    private Symbol() {}

    static List<Image> bilder() {
        List<Image> bilder = new ArrayList<>();
        for (int g : GROESSEN) {
            bilder.add(bild(g));
        }
        return bilder;
    }

    private static BufferedImage bild(int g) {
        BufferedImage b = new BufferedImage(g, g, BufferedImage.TYPE_INT_ARGB);
        Graphics2D z = b.createGraphics();
        z.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        z.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        z.scale(g / 32.0, g / 32.0); // gezeichnet wird auf einem 32er-Raster
        boolean klein = g < 24; // unter 24 px ohne Schlitz und mit kräftigeren Formen, sonst verschwimmt es

        z.setPaint(new GradientPaint(0, 0, KACHEL_HELL, 32, 32, KACHEL_DUNKEL));
        z.fill(new RoundRectangle2D.Double(0, 0, 32, 32, 10, 10));
        if (!klein) {
            z.setColor(SCHLITZ);
            z.fill(new RoundRectangle2D.Double(4, 4.5, 24, 3.5, 3.5, 3.5));
        }

        Path2D bon = klein ? bon(4, 29, 3, 3) : bon(6.2, 28.5, 4, 2.5);
        z.setColor(new Color(0, 0, 0, 90));
        z.fill(AffineTransform.getTranslateInstance(0, 0.7).createTransformedShape(bon));
        z.setPaint(new GradientPaint(0, 4, BON_OBEN, 0, 28, BON_UNTEN));
        z.fill(bon);

        // € aus Bogen und zwei Querstrichen, ohne Schrift, damit es überall gleich aussieht
        double mitteY = klein ? 15.6 : 17.3;
        double r = klein ? 5.8 : 5.4;
        float strich = klein ? 3.1f : 2.5f;
        z.setColor(EURO);
        z.setStroke(new BasicStroke(strich, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        z.draw(new Arc2D.Double(16.8 - r, mitteY - r, 2 * r, 2 * r, 48, 264, Arc2D.OPEN));
        z.setStroke(new BasicStroke(strich * 0.85f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        double abstand = klein ? 2.0 : 1.9;
        z.draw(new Line2D.Double(klein ? 8.8 : 9.2, mitteY - abstand, 17.2, mitteY - abstand));
        z.draw(new Line2D.Double(klein ? 8.8 : 9.2, mitteY + abstand, 17.2, mitteY + abstand));
        z.dispose();
        return b;
    }

    /** Bon von x = 8 bis 24 (auf ganzen Pixeln bei 16, 24, 32 und 48 px) mit gezackter Abrisskante unten. */
    private static Path2D bon(double oben, double unten, int zaehne, double tiefe) {
        Path2D p = new Path2D.Double();
        p.moveTo(8, oben);
        p.lineTo(24, oben);
        p.lineTo(24, unten);
        double breite = 16.0 / zaehne;
        for (int i = 0; i < zaehne; i++) {
            double rechts = 24 - i * breite;
            p.lineTo(rechts - breite / 2, unten - tiefe);
            p.lineTo(rechts - breite, unten);
        }
        p.closePath();
        return p;
    }

    /**
     * Build-Helfer für {@code scripts/package.ps1}: schreibt in den Zielordner {@code openbilling.ico} (Starter,
     * Verknüpfungen, Installer) und {@code assistent-<px>.png} (Bild oben rechts im Installer, je DPI-Stufe).
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Aufruf: java -cp openbilling.jar openbilling.gui.Symbol <zielordner>");
            System.exit(2);
        }
        Path ordner = Files.createDirectories(Path.of(args[0]));
        Files.write(ordner.resolve("openbilling.ico"), ico());
        for (int g : new int[] {58, 87, 116}) { // Inno Setup: 58 px bei 100 %, wählt je DPI das passende Bild
            ImageIO.write(bild(g), "png", ordner.resolve("assistent-" + g + ".png").toFile());
        }
    }

    /** Windows-.ico in allen Größen: bis 128 px als 32-Bit-Bitmap (versteht jede Windows-Komponente), 256 px als PNG. */
    private static byte[] ico() throws IOException {
        List<byte[]> daten = new ArrayList<>();
        for (int g : GROESSEN) {
            daten.add(g >= 256 ? png(bild(g)) : dib(bild(g)));
        }
        ByteBuffer kopf = ByteBuffer.allocate(6 + 16 * GROESSEN.length).order(ByteOrder.LITTLE_ENDIAN);
        kopf.putShort((short) 0).putShort((short) 1).putShort((short) GROESSEN.length);
        int versatz = kopf.capacity();
        for (int i = 0; i < GROESSEN.length; i++) {
            int g = GROESSEN[i];
            kopf.put((byte) (g >= 256 ? 0 : g)).put((byte) (g >= 256 ? 0 : g)).put((byte) 0).put((byte) 0);
            kopf.putShort((short) 1).putShort((short) 32).putInt(daten.get(i).length).putInt(versatz);
            versatz += daten.get(i).length;
        }
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        aus.write(kopf.array());
        for (byte[] d : daten) {
            aus.write(d);
        }
        return aus.toByteArray();
    }

    private static byte[] png(BufferedImage b) throws IOException {
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        ImageIO.write(b, "png", aus);
        return aus.toByteArray();
    }

    /** Icon-Bitmap: BITMAPINFOHEADER mit doppelter Höhe, BGRA-Zeilen von unten nach oben, dann die 1-Bit-Maske. */
    private static byte[] dib(BufferedImage b) {
        int g = b.getWidth();
        int maskenZeile = (g + 31) / 32 * 4;
        ByteBuffer d = ByteBuffer.allocate(40 + g * g * 4 + maskenZeile * g).order(ByteOrder.LITTLE_ENDIAN);
        d.putInt(40).putInt(g).putInt(2 * g).putShort((short) 1).putShort((short) 32).putInt(0)
                .putInt(g * g * 4 + maskenZeile * g).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = g - 1; y >= 0; y--) {
            for (int x = 0; x < g; x++) {
                d.putInt(b.getRGB(x, y)); // ARGB als Little-Endian-int ergibt die Bytefolge B, G, R, A
            }
        }
        for (int y = g - 1; y >= 0; y--) {
            byte[] zeile = new byte[maskenZeile];
            for (int x = 0; x < g; x++) {
                if (b.getRGB(x, y) >>> 24 == 0) {
                    zeile[x / 8] |= (byte) (0x80 >> (x % 8)); // gesetztes Bit = durchsichtig
                }
            }
            d.put(zeile);
        }
        return d.array();
    }
}
