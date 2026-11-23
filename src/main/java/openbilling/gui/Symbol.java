package openbilling.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Programmsymbol für Fenster und Taskleiste: Rechnungsblatt auf blauem Grund, in mehreren Größen gezeichnet. */
final class Symbol {

    private Symbol() {}

    static List<Image> bilder() {
        return List.of(bild(16), bild(20), bild(24), bild(32), bild(40), bild(48), bild(64), bild(128), bild(256));
    }

    private static Image bild(int g) {
        BufferedImage b = new BufferedImage(g, g, BufferedImage.TYPE_INT_ARGB);
        Graphics2D z = b.createGraphics();
        z.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        z.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        z.scale(g / 32.0, g / 32.0); // gezeichnet wird auf einem 32er-Raster

        z.setColor(new Color(0x2F6FDE));
        z.fill(new RoundRectangle2D.Double(0, 0, 32, 32, 9, 9));

        // Blatt mit umgeknickter Ecke
        Path2D blatt = new Path2D.Double();
        blatt.moveTo(8, 5);
        blatt.lineTo(19, 5);
        blatt.lineTo(25, 11);
        blatt.lineTo(25, 27);
        blatt.lineTo(8, 27);
        blatt.closePath();
        z.setColor(Color.WHITE);
        z.fill(blatt);
        Path2D ecke = new Path2D.Double();
        ecke.moveTo(19, 5);
        ecke.lineTo(19, 11);
        ecke.lineTo(25, 11);
        ecke.closePath();
        z.setColor(new Color(0xB9CDF3));
        z.fill(ecke);

        // Textzeilen und Summenstrich
        z.setColor(new Color(0x2F6FDE));
        z.setStroke(new BasicStroke(g < 24 ? 2.4f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        z.drawLine(11, 10, 16, 10);
        z.drawLine(11, 15, 22, 15);
        z.drawLine(11, 19, 22, 19);
        z.drawLine(17, 23, 22, 23);
        z.dispose();
        return b;
    }
}
