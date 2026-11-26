package openbilling.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import openbilling.pdf.Logo;
import org.junit.jupiter.api.Test;

class ZuschnittTest {

    private static final int ROT = Color.RED.getRGB();
    private static final int WEISS = Color.WHITE.getRGB();

    @Test
    void quadratEingepasstMittigMitWeissDaneben() {
        var bild = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        var g = bild.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        var z = new Zuschnitt(bild);

        BufferedImage e = z.ergebnis();
        assertEquals(Logo.PIXEL_BREITE, e.getWidth());
        assertEquals(Logo.PIXEL_HOEHE, e.getHeight());
        assertEquals(ROT, e.getRGB(Logo.PIXEL_BREITE / 2, Logo.PIXEL_HOEHE / 2));
        assertEquals(WEISS, e.getRGB(10, Logo.PIXEL_HOEHE / 2)); // links neben dem Quadrat

        z.zoom(100); // achtfach: Rahmen ganz gefüllt
        assertEquals(ROT, z.ergebnis().getRGB(10, 10));
    }
}
