package openbilling.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import openbilling.model.Berechnung;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;
import openbilling.ods.Kunden;
import openbilling.ods.RechnungOds.Entwurf;

/**
 * Eingabeformular einer Rechnung. Liest und liefert einen {@link Entwurf}; Felder, die das Formular
 * nicht zeigt (Währung, Rechnungsart, XRechnung-Version …), bleiben unverändert erhalten.
 */
final class Formular extends JPanel implements Scrollable {

    /** Anzeige ↔ UN/ECE-Rec-20-Code (BT-130). Unbekanntes wird unverändert als Code übernommen. */
    private static final String[][] EINHEITEN = {
        {"Stück", "C62"}, {"Stunde", "HUR"}, {"Tag", "DAY"}, {"pauschal", "LS"}, {"Monat", "MON"},
        {"km", "KMT"}, {"m", "MTR"}, {"m²", "MTK"}, {"kg", "KGM"}, {"Liter", "LTR"},
    };

    /** Anzeige ↔ Steuerkategorie (BT-151) für alles ohne Steuersatz. */
    private static final String[][] STEUERN = {
        {"steuerfrei", "E"}, {"Reverse Charge", "AE"}, {"innergem. Lieferung", "K"}, {"Ausfuhr", "G"},
        {"nicht steuerbar", "O"},
    };

    private static final Pattern STEUERSATZ = Pattern.compile("(?:([A-Za-z]{1,2})\\s+)?([\\d.,]+)\\s*%?");
    private static final DecimalFormat BETRAG =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    // Spalten des Tabellenmodells; BASIS (BT-149) wird nicht angezeigt, aber mitgeführt
    private static final int POS = 0, TEXT = 1, MENGE = 2, EINHEIT = 3, PREIS = 4, STEUER = 5, BETRAG_SPALTE = 6,
            BASIS = 7, BESCHREIBUNG = 8;

    /**
     * Felder, ohne die keine gültige XRechnung entsteht: Pflicht im Rechnungsmodell (RechnungOds) oder für den
     * Verkäuferkontakt nach BR-DE-5/6/7. Die E-Mail des Empfängers vertritt seine E-Rechnungs-Adresse (BT-49).
     */
    private static final Set<String> PFLICHT = Set.of("BT-1", "BT-2", "BT-10", "BT-27", "BT-37", "BT-38",
            "BT-40", "BT-41", "BT-42", "BT-43", "BT-44", "BT-52", "BT-53", "BT-55", "BT-58", "BT-84");

    private final Map<String, JTextComponent> felder = new LinkedHashMap<>();
    private final Map<String, String> namen = new HashMap<>();
    private final DefaultTableModel positionen = new DefaultTableModel(
            new String[] {"Pos.", "Bezeichnung", "Menge", "Einheit", "Einzelpreis", "USt", "Betrag", "", ""}, 0) {
        @Override
        public boolean isCellEditable(int zeile, int spalte) {
            return spalte != BETRAG_SPALTE;
        }
    };
    private final JTable tabelle = new JTable(positionen);
    private final JLabel netto = new JLabel();
    private final JLabel steuer = new JLabel();
    private final JLabel gesamt = new JLabel();
    private final JTextArea beschreibung = new JTextArea(4, 4);
    private boolean waehlt;
    private Map<String, String> rest = new HashMap<>();
    private boolean laedt;

    /** Wird nach jeder Eingabe aufgerufen. */
    Runnable beiAenderung = () -> {};

    Formular(Runnable ausAdressbuch, Runnable insAdressbuch) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 32, 32, 32));

        JLabel hinweis = gedaempft(new JLabel("* Pflichtfeld"));
        hinweis.putClientProperty("FlatLaf.styleClass", "small");
        JPanel kopf = abschnitt("Rechnung", hinweis);
        kopf.add(zeile(feld("BT-1", "Rechnungsnummer", "Rechnung"), 3, feld("BT-2", "Rechnungsdatum", "Rechnung"), 2,
                feld("BT-9", "Fällig am", "Rechnung"), 2, feld("BT-10", "Käuferreferenz / Leitweg-ID", "Rechnung"), 2));
        kopf.add(zeile(feld("BT-72", "Leistungsdatum", "Rechnung"), 3,
                feld("BT-73", "oder Leistungszeitraum von", "Rechnung"), 2, feld("BT-74", "bis", "Rechnung"), 2,
                new JPanel(), 2));
        kopf.add(zeile(feld("BT-20", "Zahlungsbedingungen", "Rechnung"), 1));
        kopf.add(zeile(feld("BT-22", "Bemerkung, z. B. Einleitung oder Bauvorhaben", "Rechnung"), 1));
        kopf.add(zeile(feld("OB-1", "Schlussformel, z. B. Dank und Grüße", "Rechnung"), 1));
        tipp("OB-1", "Steht unten im PDF, nicht in der XRechnung. In einer Vorlage gespeichert, gilt sie für jede"
                + " neue Rechnung.");
        tipp("BT-72", "Tag der Lieferung oder Leistung. Bei einem Zeitraum stattdessen von und bis ausfüllen.");
        tipp("BT-10", "Pflichtangabe. Bei Behörden die Leitweg-ID, sonst z. B. Bestell- oder Kundennummer.");

        JPanel absender = abschnitt("Absender", null);
        absender.add(zeile(feld("OB-2", "Firmenname im PDF-Kopf", "Absender"), 1));
        felder.get("OB-2").putClientProperty("JTextField.placeholderText", "wie Name");
        tipp("OB-2", "Steht groß oben rechts im PDF, nicht in der XRechnung. Bleibt das Feld leer, steht dort"
                + " der Name.");
        partei(absender, "Absender", "BT-27", "BT-35", "BT-38", "BT-37", "BT-40", "BT-41", "BT-42", "BT-43", "BT-34");
        absender.add(zeile(feld("BT-31", "USt-IdNr.", "Absender"), 1, feld("BT-32", "Steuernummer", "Absender"), 1,
                feld("BT-29", "Kennung", "Absender"), 1));
        tipp("BT-29", "Nur ohne USt-IdNr. nötig: XRechnung verlangt dann eine eigene Kennung des Absenders,"
                + " z. B. eine Lieferanten- oder Geschäftsnummer (Regel BR-CO-26).");
        felder.get("BT-29").putClientProperty("JTextField.placeholderText", "ohne USt-IdNr.");

        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        knoepfe.add(knopf("Aus Adressbuch…", ausAdressbuch));
        knoepfe.add(knopf("Im Adressbuch speichern", insAdressbuch));
        JPanel empfaenger = abschnitt("Empfänger", knoepfe);
        partei(empfaenger, "Empfänger", "BT-44", "BT-50", "BT-53", "BT-52", "BT-55", "BT-56", "BT-57", "BT-58",
                "BT-49");
        empfaenger.add(zeile(feld("BT-48", "USt-IdNr.", "Empfänger"), 1,
                feld("BT-46", "Kundennummer", "Empfänger"), 1));

        JPanel parteien = new JPanel(new GridLayout(1, 2, 40, 0));
        parteien.add(absender);
        parteien.add(empfaenger);

        JPanel zahlung = abschnitt("Zahlung", null);
        zahlung.add(zeile(feld("BT-84", "IBAN", "Zahlung"), 3, feld("BT-86", "BIC", "Zahlung"), 2));
        zahlung.add(zeile(feld("BT-85", "Kontoinhaber", "Zahlung"), 1));
        zahlung.add(zeile(feld("BT-83", "Verwendungszweck", "Zahlung"), 1));
        zahlung.add(zeile(feld("BT-120", "Grund der Steuerbefreiung", "Zahlung"), 1));
        tipp("BT-120", "Nur nötig, wenn eine Position steuerfrei ist, z. B. Kleinunternehmer nach § 19 UStG.");

        JPanel unten = new JPanel(new GridLayout(1, 2, 40, 0));
        unten.add(zahlung);
        unten.add(summen());

        JPanel seite = new JPanel();
        seite.setLayout(new BoxLayout(seite, BoxLayout.Y_AXIS));
        for (JComponent teil : new JComponent[] {kopf, parteien, positionsteil(), unten}) {
            teil.setAlignmentX(LEFT_ALIGNMENT);
            seite.add(teil);
            seite.add(Box.createVerticalStrut(28));
        }
        // Inhalt höchstens 1080 breit und mittig, damit Zeilen auf großen Bildschirmen lesbar bleiben
        JPanel rahmen = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(Math.min(1080, Math.max(680, Formular.this.getWidth() - 64)),
                        super.getPreferredSize().height);
            }
        };
        rahmen.add(seite);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTH;
        c.weighty = 1;
        add(rahmen, c);
    }

    // ------------------------------------------------------------ Aufbau

    private void partei(JPanel p, String gruppe, String name, String strasse, String plz, String ort, String land,
            String kontakt, String telefon, String email, String adresse) {
        p.add(zeile(feld(name, "Name", gruppe), 1));
        p.add(zeile(feld(strasse, "Straße und Hausnummer", gruppe), 1));
        p.add(zeile(feld(plz, "PLZ", gruppe), 2, feld(ort, "Ort", gruppe), 5, feld(land, "Land", gruppe), 1));
        p.add(zeile(feld(kontakt, "Kontaktperson", gruppe), 1));
        p.add(zeile(feld(telefon, "Telefon", gruppe), 1, feld(email, "E-Mail", gruppe), 1));
        p.add(zeile(feld(adresse, "E-Rechnungs-Adresse", gruppe), 1));
        felder.get(adresse).putClientProperty("JTextField.placeholderText", "wie E-Mail");
        tipp(adresse, "Elektronische Adresse für den Rechnungsempfang: E-Mail-Adresse oder Leitweg-ID."
                + " Bleibt das Feld leer, gilt die E-Mail-Adresse darüber.");
        tipp(land, "Ländercode mit zwei Buchstaben, z. B. DE");
    }

    private static JPanel abschnitt(String titel, JComponent rechts) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = new JLabel(titel);
        l.putClientProperty("FlatLaf.styleClass", "h3");
        JPanel kopf = new JPanel(new BorderLayout());
        kopf.add(l, BorderLayout.WEST);
        if (rechts != null) {
            kopf.add(rechts, BorderLayout.EAST);
        }
        kopf.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        kopf.setAlignmentX(LEFT_ALIGNMENT);
        kopf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        kopf.setPreferredSize(new Dimension(10, 30));
        p.add(kopf);
        return p;
    }

    /** Beschriftetes Eingabefeld, unter seiner BT-Kennung registriert. */
    private JComponent feld(String bt, String beschriftung, String gruppe) {
        JTextComponent f = Set.of("BT-22", "OB-1").contains(bt) ? mehrzeilig(new JTextArea(3, 4))
                : Set.of("BT-2", "BT-9", "BT-72", "BT-73", "BT-74").contains(bt) ? new DatumFeld()
                : new JTextField(4);
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                geaendert();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                geaendert();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        felder.put(bt, f);
        namen.put(bt, gruppe + " – " + beschriftung);
        JLabel l = new JLabel(PFLICHT.contains(bt) ? beschriftung + " *" : beschriftung);
        l.putClientProperty("FlatLaf.styleClass", "small");
        gedaempft(l);
        l.setLabelFor(f);
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.add(l, BorderLayout.NORTH);
        p.add(f instanceof JTextArea ? new JScrollPane(f) : f);
        return p;
    }

    private static JTextArea mehrzeilig(JTextArea a) {
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        return a;
    }

    /** Zurückhaltende Textfarbe, die beim Wechsel des Designs mitgeht. */
    static JLabel gedaempft(JLabel l) {
        l.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        return l;
    }

    private void tipp(String bt, String text) {
        felder.get(bt).setToolTipText(text);
    }

    /** Felder nebeneinander: abwechselnd Komponente und relative Breite. */
    private static JPanel zeile(Object... teile) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < teile.length; i += 2) {
            c.weightx = (Integer) teile[i + 1];
            c.insets = new Insets(0, i == 0 ? 0 : 12, 10, 0);
            p.add((JComponent) teile[i], c);
        }
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JButton knopf(String text, Runnable aktion) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "borderless");
        b.putClientProperty("FlatLaf.styleClass", "small");
        b.setFocusable(false);
        b.addActionListener(e -> aktion.run());
        return b;
    }

    private JComponent positionsteil() {
        tabelle.setRowHeight(30);
        tabelle.setShowHorizontalLines(true);
        tabelle.setFillsViewportHeight(true);
        tabelle.putClientProperty("terminateEditOnFocusLost", true);
        tabelle.getTableHeader().setReorderingAllowed(false);
        tabelle.removeColumn(tabelle.getColumnModel().getColumn(BASIS));
        tabelle.removeColumn(tabelle.getColumnModel().getColumn(BASIS)); // danach steht dort BESCHREIBUNG
        tabelle.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        int[] breiten = {44, 420, 70, 100, 110, 150, 110};
        for (int i = 0; i < breiten.length; i++) {
            tabelle.getColumnModel().getColumn(i).setPreferredWidth(breiten[i]);
        }
        DefaultTableCellRenderer rechts = new DefaultTableCellRenderer();
        rechts.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int spalte : new int[] {MENGE, PREIS, BETRAG_SPALTE}) {
            tabelle.getColumnModel().getColumn(spalte).setCellRenderer(rechts);
        }
        tabelle.getColumnModel().getColumn(EINHEIT).setCellEditor(auswahl(EINHEITEN, List.of()));
        tabelle.getColumnModel().getColumn(STEUER).setCellEditor(auswahl(STEUERN, List.of("19 %", "7 %", "0 %")));
        positionen.addTableModelListener(e -> {
            if (!laedt) {
                rechne();
                beiAenderung.run();
            }
        });

        JPanel rahmen = new JPanel(new BorderLayout());
        rahmen.setBorder(new javax.swing.border.AbstractBorder() { // Farbe erst beim Zeichnen: folgt dem Design
            @Override
            public void paintBorder(java.awt.Component k, java.awt.Graphics g, int x, int y, int b, int h) {
                g.setColor(UIManager.getColor("Component.borderColor"));
                g.drawRect(x, y, b - 1, h - 1);
            }

            @Override
            public Insets getBorderInsets(java.awt.Component k) {
                return new Insets(1, 1, 1, 1);
            }
        });
        rahmen.add(tabelle.getTableHeader(), BorderLayout.NORTH);
        rahmen.add(tabelle);

        JButton neu = new JButton("Position hinzufügen");
        neu.addActionListener(e -> {
            positionen.addRow(new Object[] {String.valueOf(positionen.getRowCount() + 1), "", "1", "Stück", "",
                "19 %", "", null, null});
            int zeile = positionen.getRowCount() - 1;
            tabelle.changeSelection(zeile, TEXT, false, false);
            tabelle.editCellAt(zeile, TEXT);
            tabelle.requestFocusInWindow();
        });
        JButton weg = new JButton("Entfernen");
        weg.addActionListener(e -> {
            beendeEingabe();
            int zeile = tabelle.getSelectedRow();
            if (zeile >= 0) {
                positionen.removeRow(zeile);
            }
        });
        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        knoepfe.add(neu);
        knoepfe.add(Box.createHorizontalStrut(8));
        knoepfe.add(weg);
        knoepfe.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // ausführlicher Text (BT-154) zur gerade gewählten Position
        mehrzeilig(beschreibung).setEnabled(false);
        tabelle.getSelectionModel().addListSelectionListener(e -> {
            int zeile = tabelle.getSelectedRow();
            waehlt = true;
            beschreibung.setText(zeile < 0 || wert(zeile, BESCHREIBUNG) == null ? "" : wert(zeile, BESCHREIBUNG));
            beschreibung.setEnabled(zeile >= 0);
            waehlt = false;
        });
        beschreibung.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                uebernimm();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                uebernimm();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}

            private void uebernimm() {
                if (!waehlt && tabelle.getSelectedRow() >= 0) {
                    positionen.setValueAt(beschreibung.getText(), tabelle.getSelectedRow(), BESCHREIBUNG);
                }
            }
        });
        JLabel titel = gedaempft(new JLabel("Beschreibung der gewählten Position (optional, mehrzeilig)"));
        titel.putClientProperty("FlatLaf.styleClass", "small");
        JPanel detail = new JPanel(new BorderLayout(0, 3));
        detail.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        detail.add(titel, BorderLayout.NORTH);
        detail.add(new JScrollPane(beschreibung));

        JPanel p = abschnitt("Positionen", null);
        for (JComponent teil : new JComponent[] {rahmen, knoepfe, detail}) {
            teil.setAlignmentX(LEFT_ALIGNMENT);
            p.add(teil);
        }
        return p;
    }

    private static DefaultCellEditor auswahl(String[][] benannte, List<String> davor) {
        JComboBox<String> box = new JComboBox<>();
        davor.forEach(box::addItem);
        for (String[] eintrag : benannte) {
            box.addItem(eintrag[0]);
        }
        box.setEditable(true); // eigene Codes und Sätze (z. B. 16 %) lassen sich eintippen
        return new DefaultCellEditor(box);
    }

    private JComponent summen() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 24, 4, 0);
        c.anchor = GridBagConstraints.EAST;
        gesamt.setFont(gesamt.getFont().deriveFont(Font.BOLD, gesamt.getFont().getSize2D() + 5));
        String[] texte = {"Netto", "Umsatzsteuer", "Rechnungsbetrag"};
        JLabel[] werte = {netto, steuer, gesamt};
        for (int i = 0; i < 3; i++) {
            c.gridy = i;
            c.gridx = 0;
            JLabel l = gedaempft(new JLabel(texte[i]));
            p.add(l, c);
            c.gridx = 1;
            p.add(werte[i], c);
        }
        JPanel aussen = new JPanel(new BorderLayout());
        JPanel oben = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        oben.add(p);
        aussen.add(oben, BorderLayout.NORTH);
        return aussen;
    }

    // ------------------------------------------------------------ Daten

    void zeige(Entwurf e) {
        laedt = true;
        beendeEingabe();
        rest = new HashMap<>(e.daten());
        felder.forEach((bt, f) -> {
            f.setText(e.daten().getOrDefault(bt, ""));
            f.putClientProperty("JComponent.outline", null);
        });
        for (String[] paar : new String[][] {{"BT-34", "BT-43"}, {"BT-49", "BT-58"}}) {
            if (felder.get(paar[0]).getText().equals(felder.get(paar[1]).getText())) {
                felder.get(paar[0]).setText(""); // gleich der E-Mail: Platzhalter „wie E-Mail“ genügt
            }
        }
        positionen.setRowCount(0);
        for (String[] z : e.positionen()) {
            BigDecimal preis = zahl(z[4]);
            positionen.addRow(new Object[] {z[0], z[1], z[2], einheit(z[3], 1, 0),
                preis != null ? BETRAG.format(preis) : z[4], steuerText(z[6], z[7]), "", z[5], z[8]});
        }
        laedt = false;
        rechne();
    }

    Entwurf entwurf() {
        beendeEingabe();
        Map<String, String> d = new LinkedHashMap<>(rest);
        for (String bt : List.of("BT-3", "BT-5", "BT-23", "BT-24", "BT-81")) {
            // Pflichtangaben ohne eigenes Feld: fehlen sie in einer alten Vorlage, gilt die Vorgabe
            d.putIfAbsent(bt, openbilling.ods.RechnungOds.VORGABEN.get(bt));
        }
        felder.forEach((bt, f) -> {
            if (f.getText().isBlank()) {
                d.remove(bt);
            } else {
                d.put(bt, f.getText().strip());
            }
        });
        // ohne eigene E-Rechnungs-Adresse gilt die E-Mail; das Schema folgt aus der Form der Adresse
        for (String[] paar : new String[][] {{"BT-34", "BT-43"}, {"BT-49", "BT-58"}}) {
            if (!d.containsKey(paar[0]) && d.containsKey(paar[1])) {
                d.put(paar[0], d.get(paar[1]));
            }
            if (d.containsKey(paar[0])) {
                boolean mail = d.get(paar[0]).contains("@");
                d.put(paar[0] + "-1", mail ? "EM" : paar[0].equals("BT-49") ? "0204" : d.getOrDefault("BT-34-1", "EM"));
            }
        }
        List<String[]> zeilen = new ArrayList<>();
        for (int i = 0; i < positionen.getRowCount(); i++) {
            String[] st = steuer(wert(i, STEUER));
            zeilen.add(new String[] {wert(i, POS), wert(i, TEXT), normal(wert(i, MENGE)),
                einheit(wert(i, EINHEIT), 0, 1), normal(wert(i, PREIS)), wert(i, BASIS), st[0], st[1],
                wert(i, BESCHREIBUNG)});
        }
        return new Entwurf(d, zeilen);
    }

    /** Käuferfelder des Formulars als Adressbuch-Eintrag. */
    Map<String, String> kunde() {
        Map<String, String> alle = entwurf().daten();
        Map<String, String> k = new LinkedHashMap<>();
        for (String bt : Kunden.FELDER) {
            if (alle.containsKey(bt)) {
                k.put(bt, alle.get(bt));
            }
        }
        return k;
    }

    void setzeKunde(Map<String, String> kunde) {
        for (String bt : Kunden.FELDER) {
            rest.remove(bt);
            if (felder.containsKey(bt)) {
                felder.get(bt).setText(kunde.getOrDefault(bt, ""));
            }
        }
        if (kunde.getOrDefault("BT-49", "").equals(kunde.get("BT-58"))) {
            felder.get("BT-49").setText("");
        }
    }

    void setze(String bt, String wert) {
        felder.get(bt).setText(wert);
    }

    /** Lesbarer Name eines Feldes, z. B. „Empfänger – Name“, sonst die Kennung selbst. */
    String name(String bt) {
        return namen.getOrDefault(bt, bt);
    }

    /** Markiert die genannten Felder rot und springt zum ersten. */
    void markiere(Set<String> bts) {
        boolean erstes = true;
        for (var e : felder.entrySet()) {
            boolean fehler = bts.contains(e.getKey());
            e.getValue().putClientProperty("JComponent.outline", fehler ? "error" : null);
            if (fehler && erstes) {
                e.getValue().requestFocusInWindow();
                erstes = false;
            }
        }
    }

    private void geaendert() {
        if (!laedt) {
            rechne();
            beiAenderung.run();
        }
    }

    private void beendeEingabe() {
        if (tabelle.isEditing()) {
            tabelle.getCellEditor().stopCellEditing();
        }
    }

    private String wert(int zeile, int spalte) {
        Object v = positionen.getValueAt(zeile, spalte);
        return v == null || v.toString().isBlank() ? null : v.toString().strip();
    }

    /** Zeilenbeträge und Summen aus allen Positionen, die schon vollständig sind. */
    private void rechne() {
        List<Position> ps = new ArrayList<>();
        List<Integer> zeilen = new ArrayList<>();
        for (int i = 0; i < positionen.getRowCount(); i++) {
            BigDecimal menge = zahl(wert(i, MENGE));
            BigDecimal preis = zahl(wert(i, PREIS));
            String[] st = steuer(wert(i, STEUER));
            try {
                if (menge != null && preis != null) {
                    ps.add(new Position("", "", menge, "", preis, zahl(wert(i, BASIS)),
                            Steuerkategorie.valueOf(st[0]), zahl(st[1])));
                    zeilen.add(i);
                }
            } catch (RuntimeException unvollstaendig) {
                // Zeile zählt noch nicht mit
            }
        }
        boolean vorher = laedt;
        laedt = true; // eigene Änderungen am Modell lösen keine neue Rechnung aus
        for (int i = 0; i < positionen.getRowCount(); i++) {
            positionen.setValueAt("", i, BETRAG_SPALTE);
        }
        String waehrung = " " + rest.getOrDefault("BT-5", "EUR").replace("EUR", "€");
        if (ps.isEmpty()) {
            netto.setText("–");
            steuer.setText("–");
            gesamt.setText("–");
        } else {
            var erg = Berechnung.berechne(new Rechnung(null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, ps));
            for (int i = 0; i < zeilen.size(); i++) {
                positionen.setValueAt(BETRAG.format(erg.zeilenbetraege().get(i)), zeilen.get(i), BETRAG_SPALTE);
            }
            netto.setText(BETRAG.format(erg.netto()) + waehrung);
            steuer.setText(BETRAG.format(erg.steuer()) + waehrung);
            gesamt.setText(BETRAG.format(erg.brutto()) + waehrung);
        }
        laedt = vorher;
    }

    // ------------------------------------------------------------ Umrechnung Anzeige ↔ Datei

    /** Deutsche Zahl: {@code 3.000,50}, {@code 3000,5}, {@code 3.000}; ein einzelner Punkt gilt als Komma. */
    static BigDecimal zahl(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        s = s.replace(" ", "").replace("€", "");
        if (s.contains(",") || s.matches("\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Zahl ohne Tausenderpunkte mit Komma, so wie die ODS sie eindeutig liest. Unlesbares bleibt stehen. */
    private static String normal(String s) {
        BigDecimal z = zahl(s);
        return z == null ? s : z.toPlainString().replace('.', ',');
    }

    private static String einheit(String s, int von, int nach) {
        for (String[] e : EINHEITEN) {
            if (e[von].equalsIgnoreCase(s)) {
                return e[nach];
            }
        }
        return s;
    }

    private static String steuerText(String kategorie, String satz) {
        if (kategorie == null) {
            return satz == null ? "" : satz + " %";
        }
        for (String[] s : STEUERN) {
            if (s[1].equalsIgnoreCase(kategorie)) {
                return s[0];
            }
        }
        String prozent = (satz == null ? "0" : satz) + " %";
        return Set.of("S", "Z").contains(kategorie.toUpperCase()) ? prozent : kategorie.toUpperCase() + " " + prozent;
    }

    /** Anzeige der Steuerspalte → {Kategorie BT-151, Satz BT-152}. */
    private static String[] steuer(String text) {
        if (text == null) {
            return new String[] {null, null};
        }
        for (String[] s : STEUERN) {
            if (s[0].equalsIgnoreCase(text)) {
                return new String[] {s[1], s[1].equals("O") ? null : "0"};
            }
        }
        Matcher m = STEUERSATZ.matcher(text);
        BigDecimal satz = m.matches() ? zahl(m.group(2)) : null;
        if (satz == null) {
            return new String[] {text, null}; // unbekannt: die Prüfung meldet es
        }
        String kategorie = m.group(1) != null ? m.group(1).toUpperCase() : satz.signum() == 0 ? "Z" : "S";
        return new String[] {kategorie, satz.toPlainString().replace('.', ',')};
    }

    // ------------------------------------------------------------ Scrollable: Breite folgt dem Fenster

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle sichtbar, int richtung, int vorzeichen) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle sichtbar, int richtung, int vorzeichen) {
        return sichtbar.height - 48;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
