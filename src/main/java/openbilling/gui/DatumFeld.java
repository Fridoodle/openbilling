package openbilling.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/** Datum zum Tippen ({@code 20.11.2026}, {@code 20.11.26}, {@code 20.11.}) mit Kalender zum Anklicken. */
final class DatumFeld extends JTextField {

    static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final Pattern EINGABE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.?(\\d{2}|\\d{4})?");

    DatumFeld() {
        super(4);
        putClientProperty("JTextField.placeholderText", "TT.MM.JJJJ");
        JButton knopf = new JButton(new KalenderIcon());
        knopf.setToolTipText("Kalender öffnen");
        knopf.setFocusable(false);
        knopf.addActionListener(e -> zeigeKalender());
        putClientProperty("JTextField.trailingComponent", knopf);
        addActionListener(e -> normalisiere());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                normalisiere();
            }
        });
    }

    /** Gelesenes Datum oder {@code null}, wenn das Feld leer oder nicht lesbar ist. */
    static LocalDate lese(String text) {
        Matcher m = EINGABE.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        int jahr = m.group(3) == null ? LocalDate.now().getYear() : Integer.parseInt(m.group(3));
        try {
            return LocalDate.of(jahr < 100 ? 2000 + jahr : jahr, Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private void normalisiere() {
        LocalDate d = lese(getText());
        if (d != null) {
            setText(d.format(FORMAT));
        }
        putClientProperty("JComponent.outline", d == null && !getText().isBlank() ? "error" : null);
    }

    private void zeigeKalender() {
        JPopupMenu popup = new JPopupMenu();
        JPanel inhalt = new JPanel(new BorderLayout(0, 6));
        inhalt.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        popup.add(inhalt);
        LocalDate gewaehlt = lese(getText());
        fuelle(inhalt, popup, YearMonth.from(gewaehlt != null ? gewaehlt : LocalDate.now()), gewaehlt);
        popup.show(this, 0, getHeight() + 2);
    }

    private void fuelle(JPanel inhalt, JPopupMenu popup, YearMonth monat, LocalDate gewaehlt) {
        inhalt.removeAll();
        JLabel titel = new JLabel(monat.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)
                + " " + monat.getYear(), SwingConstants.CENTER);
        titel.setFont(titel.getFont().deriveFont(Font.BOLD));
        JPanel kopf = new JPanel(new BorderLayout());
        kopf.add(flach("‹", () -> fuelle(inhalt, popup, monat.minusMonths(1), gewaehlt)), BorderLayout.WEST);
        kopf.add(titel);
        kopf.add(flach("›", () -> fuelle(inhalt, popup, monat.plusMonths(1), gewaehlt)), BorderLayout.EAST);

        JPanel tage = new JPanel(new GridLayout(0, 7, 2, 2));
        for (String tag : new String[] {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"}) {
            JLabel l = new JLabel(tag, SwingConstants.CENTER);
            l.setForeground(UIManager.getColor("Label.disabledForeground"));
            tage.add(l);
        }
        for (int i = 1; i < monat.atDay(1).getDayOfWeek().getValue(); i++) {
            tage.add(new JLabel());
        }
        LocalDate heute = LocalDate.now();
        for (int t = 1; t <= monat.lengthOfMonth(); t++) {
            LocalDate tag = monat.atDay(t);
            JButton b = flach(String.valueOf(t), () -> {
                setText(tag.format(FORMAT));
                normalisiere();
                popup.setVisible(false);
            });
            if (tag.equals(gewaehlt)) {
                b.putClientProperty("JButton.buttonType", null); // als normaler Knopf sichtbar hervorgehoben
            }
            if (tag.equals(heute)) {
                b.setFont(b.getFont().deriveFont(Font.BOLD));
            }
            tage.add(b);
        }
        inhalt.add(kopf, BorderLayout.NORTH);
        inhalt.add(tage);
        inhalt.add(flach("Heute", () -> {
            setText(heute.format(FORMAT));
            normalisiere();
            popup.setVisible(false);
        }), BorderLayout.SOUTH);
        popup.pack();
        inhalt.revalidate();
        inhalt.repaint();
    }

    private static JButton flach(String text, Runnable aktion) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "borderless");
        b.setMargin(new java.awt.Insets(4, 6, 4, 6));
        b.setFocusable(false);
        b.addActionListener(e -> aktion.run());
        return b;
    }

    /** Kleines Kalenderblatt in der Textfarbe des aktuellen Designs. */
    private static final class KalenderIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color farbe = UIManager.getColor("Label.disabledForeground");
            g2.setColor(farbe != null ? farbe : Color.GRAY);
            g2.drawRoundRect(x + 1, y + 2, 13, 12, 3, 3);
            g2.drawLine(x + 1, y + 6, x + 14, y + 6);
            g2.drawLine(x + 5, y, x + 5, y + 3);
            g2.drawLine(x + 10, y, x + 10, y + 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
