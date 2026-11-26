package openbilling.gui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import openbilling.pdf.Logo;

/**
 * Zuschnitt des Logos wie bei Profilbildern: Der Rahmen hat das feste Seitenverhältnis des Logos im PDF,
 * darunter wird das Bild verschoben (ziehen) und gezoomt (Mausrad, Regler). Was außerhalb des Bildes im
 * Rahmen liegt, wird weiß, so passt auch ein quadratisches Logo hinein.
 */
final class Zuschnitt extends JComponent {

    /** Zoom-Regler: 0 bis 100, bei {@link #EINGEPASST} passt das ganze Bild genau in den Rahmen. */
    private static final int EINGEPASST = 25;

    private final BufferedImage bild;
    /** Ergebnis-Pixel je Bildpixel, wenn das Bild genau in den Rahmen passt. */
    private final double passend;
    private double zoom = 1;
    /** Verschiebung der Bildmitte gegenüber der Rahmenmitte, in Ergebnis-Pixeln. */
    private double dx, dy;
    private Point griff;

    Zuschnitt(BufferedImage bild) {
        this.bild = bild;
        passend = Math.min((double) Logo.PIXEL_BREITE / bild.getWidth(), (double) Logo.PIXEL_HOEHE / bild.getHeight());
        MouseAdapter maus = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                griff = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                double d = rahmen().width / (double) Logo.PIXEL_BREITE;
                dx += (e.getX() - griff.x) / d;
                dy += (e.getY() - griff.y) / d;
                griff = e.getPoint();
                repaint();
            }
        };
        addMouseListener(maus);
        addMouseMotionListener(maus);
    }

    /** Reglerwert 0 bis 100 → Zoom 0,5 bis 8, exponentiell, damit jeder Schritt gleich stark wirkt. */
    void zoom(int regler) {
        zoom = Math.pow(2, (regler - EINGEPASST) / 25.0);
        repaint();
    }

    /** Bild im Ergebnis: Ergebnis-Pixel ← Bildpixel. */
    private AffineTransform lage() {
        var t = AffineTransform.getTranslateInstance(Logo.PIXEL_BREITE / 2.0 + dx, Logo.PIXEL_HOEHE / 2.0 + dy);
        t.scale(passend * zoom, passend * zoom);
        t.translate(-bild.getWidth() / 2.0, -bild.getHeight() / 2.0);
        return t;
    }

    /** Rahmen in der Komponente, mittig mit Rand, Seitenverhältnis wie das Logo. */
    private Rectangle rahmen() {
        int rand = UIScale.scale(24);
        int w = Math.max(1, Math.min(getWidth() - 2 * rand,
                (getHeight() - 2 * rand) * Logo.PIXEL_BREITE / Logo.PIXEL_HOEHE));
        int h = w * Logo.PIXEL_HOEHE / Logo.PIXEL_BREITE;
        return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }

    /** Der Rahmeninhalt als Bild mit {@link Logo#PIXEL_BREITE} × {@link Logo#PIXEL_HOEHE}, Hintergrund weiß. */
    BufferedImage ergebnis() {
        var out = new BufferedImage(Logo.PIXEL_BREITE, Logo.PIXEL_HOEHE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(bild, lage(), null);
        g.dispose();
        return out;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Rectangle r = rahmen();
        g.setColor(Color.WHITE);
        g.fill(r);
        var t = AffineTransform.getTranslateInstance(r.x, r.y);
        t.scale(r.width / (double) Logo.PIXEL_BREITE, r.width / (double) Logo.PIXEL_BREITE);
        t.concatenate(lage());
        g.drawImage(bild, t, null);
        // außerhalb des Rahmens abgedunkelt: das wird abgeschnitten
        Area aussen = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        aussen.subtract(new Area(r));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g.setColor(Color.BLACK);
        g.fill(aussen);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0x2675BF));
        g.draw(new Rectangle(r.x - 1, r.y - 1, r.width + 1, r.height + 1));
        g.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return UIScale.scale(new Dimension(620, 300));
    }

    /** Zeigt den Zuschnitt als Dialog. Liefert das zugeschnittene Bild oder {@code null} bei Abbruch. */
    static BufferedImage zeige(Component eltern, BufferedImage bild) {
        Zuschnitt z = new Zuschnitt(bild);
        JSlider regler = new JSlider(0, 100, EINGEPASST);
        regler.addChangeListener(e -> z.zoom(regler.getValue()));
        z.addMouseWheelListener(e -> regler.setValue(regler.getValue() - 4 * e.getWheelRotation()));
        JPanel unten = new JPanel(new BorderLayout(8, 0));
        unten.add(new JLabel("Zoom"), BorderLayout.WEST);
        unten.add(regler);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(Formular.gedaempft(new JLabel("Bild ziehen zum Verschieben, Mausrad oder Regler zum Zoomen."
                + " Der Rahmen zeigt das Logo im PDF.")), BorderLayout.NORTH);
        p.add(z);
        p.add(unten, BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(eltern, p, "Logo zuschneiden", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION ? z.ergebnis() : null;
    }
}
