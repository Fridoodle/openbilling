package openbilling.gui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
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
            BASIS = 7, BESCHREIBUNG = 8, RABATT = 9;

    /** Zahlenfelder im Kopf, die wie die Positionen ohne Tausenderpunkte gespeichert werden. */
    private static final Set<String> ZAHLEN = Set.of("BT-92", "BT-94", "BT-99", "BT-101", "OB-3", "OB-4");

    /**
     * Felder, ohne die keine gültige XRechnung entsteht: Pflicht im Rechnungsmodell (RechnungOds) oder für den
     * Verkäuferkontakt nach BR-DE-5/6/7. Die E-Mail des Empfängers vertritt seine E-Rechnungs-Adresse (BT-49).
     */
    private static final Set<String> PFLICHT = Set.of("BT-1", "BT-2", "BT-10", "BT-27", "BT-37", "BT-38",
            "BT-40", "BT-41", "BT-42", "BT-43", "BT-44", "BT-52", "BT-53", "BT-55", "BT-58", "BT-84", "BT-87",
            "BT-89", "BT-90", "BT-91");

    /** Anzeige ↔ Format (BT-24); bestimmt das ZUGFeRD-Profil im PDF. */
    private static final String[][] FORMATE = {
        {"XRechnung (Behörden)", openbilling.ods.RechnungOds.XRECHNUNG_30},
        {"ZUGFeRD EN 16931 (Unternehmen)", openbilling.ods.RechnungOds.EN16931},
    };

    /** Anzeige ↔ Zahlungsart (BT-81, UNTDID 4461). */
    private static final String[][] ZAHLUNGSARTEN = {
        {"SEPA-Überweisung", "58"}, {"SEPA-Lastschrift", "59"}, {"Kreditkarte", "54"}, {"Debitkarte", "55"},
        {"Barzahlung", "10"}, {"PayPal oder anderer Online-Dienst", "68"},
    };

    private final Map<String, JTextComponent> felder = new LinkedHashMap<>();
    private final Map<String, String> namen = new HashMap<>();
    private final DefaultTableModel positionen = new DefaultTableModel(
            new String[] {"Pos.", "Bezeichnung", "Menge", "Einheit", "Einzelpreis", "USt", "Betrag", "", "",
                "Rabatt %"}, 0) {
        @Override
        public boolean isCellEditable(int zeile, int spalte) {
            return spalte != BETRAG_SPALTE;
        }
    };
    /** Schmalste Breite des Inhalts bei normaler Größe; bei Zoom hochgerechnet. */
    private static final int SCHMAL = 720;
    private final JTable tabelle = new JTable(positionen) {
        @Override
        public void updateUI() { // Zeilenhöhe und Spaltenbreiten wachsen mit der Schriftgröße
            super.updateUI();
            setRowHeight(UIScale.scale(30));
            if (spaltenBreiten != null) { // null, solange der Konstruktor von JTable läuft
                spaltenBreiten.forEach((spalte, breite) -> spalte.setPreferredWidth(UIScale.scale(breite)));
            }
        }
    };
    /** Spaltenbreiten der Positionstabelle bei normaler Größe. */
    private final Map<javax.swing.table.TableColumn, Integer> spaltenBreiten = new HashMap<>();
    private final JLabel netto = new JLabel();
    private final JLabel steuer = new JLabel();
    private final JLabel gesamt = new JLabel();
    private final JTextArea beschreibung = new JTextArea(4, 4);
    private boolean waehlt;
    private final JComboBox<String> art = new JComboBox<>();
    private final JComboBox<String> format = new JComboBox<>();
    private final Map<String, JLabel> beschriftungen = new HashMap<>();
    /** Felder je Zahlungsart; nur die passende Gruppe ist sichtbar. */
    private Map<String, JPanel> artGruppen;
    private Map<String, String> rest = new HashMap<>();
    private boolean laedt;

    /** Wird nach jeder Eingabe aufgerufen. */
    Runnable beiAenderung = () -> {};

    Formular(Runnable ausAdressbuch, Runnable insAdressbuch) {
        super(new GridBagLayout());
        setBorder(rand(24, 32, 32, 32));

        JLabel hinweis = gedaempft(new JLabel("* Pflichtfeld"));
        hinweis.putClientProperty("FlatLaf.styleClass", "small");
        JPanel kopf = abschnitt("Rechnung", hinweis);
        kopf.add(zeile(feld("BT-1", "Rechnungsnummer", "Rechnung"), 3, feld("BT-2", "Rechnungsdatum", "Rechnung"), 2,
                feld("BT-9", "Fällig am", "Rechnung"), 2, feld("BT-10", "Käuferreferenz / Leitweg-ID", "Rechnung"), 2));
        kopf.add(zeile(feld("BT-72", "Leistungsdatum", "Rechnung"), 3,
                feld("BT-73", "oder Leistungszeitraum von", "Rechnung"), 2, feld("BT-74", "bis", "Rechnung"), 2,
                formatAuswahl(), 2));
        kopf.add(zeile(feld("BT-20", "Zahlungsbedingungen", "Rechnung"), 1));
        kopf.add(zeile(feld("BT-22", "Bemerkung, z. B. Einleitung oder Bauvorhaben", "Rechnung"), 1));
        kopf.add(zeile(feld("OB-1", "Schlussformel, z. B. Dank und Grüße", "Rechnung"), 1));
        tipp("OB-1", "Steht unten im PDF, nicht in der XRechnung. In einer Vorlage gespeichert, gilt sie für jede"
                + " neue Rechnung.");
        tipp("BT-72", "Tag der Lieferung oder Leistung. Bei einem Zeitraum stattdessen von und bis ausfüllen.");
        zeigeFormat();

        JPanel absender = abschnitt("Absender", null);
        absender.add(zeile(feld("OB-2", "Firmenname im PDF-Kopf", "Absender"), 1));
        felder.get("OB-2").putClientProperty("JTextField.placeholderText", "wie Name");
        tipp("OB-2", "Steht groß oben rechts im PDF, nicht in der XRechnung. Bleibt das Feld leer, steht dort"
                + " der Name. Ein Logo (Ansicht › PDF-Vorschau und Logo) ersetzt ihn.");
        partei(absender, "Absender", "BT-27", "BT-35", "BT-38", "BT-37", "BT-40", "BT-41", "BT-42", "BT-43", "BT-34");
        absender.add(zeile(feld("BT-31", "USt-IdNr.", "Absender"), 1, feld("BT-32", "Steuernummer", "Absender"), 1,
                feld("BT-29", "Kennung", "Absender"), 1));
        tipp("BT-29", "Nur ohne USt-IdNr. nötig: XRechnung verlangt dann eine eigene Kennung des Absenders,"
                + " z. B. eine Lieferanten- oder Geschäftsnummer (Regel BR-CO-26).");
        felder.get("BT-29").putClientProperty("JTextField.placeholderText", "ohne USt-IdNr.");

        // Steht neben „Firmenname im PDF-Kopf“, damit die Adressfelder beider Seiten auf einer Höhe liegen
        JPanel empfaenger = abschnitt("Empfänger", null);
        empfaenger.add(zeile(adressbuch(ausAdressbuch, insAdressbuch), 1));
        partei(empfaenger, "Empfänger", "BT-44", "BT-50", "BT-53", "BT-52", "BT-55", "BT-56", "BT-57", "BT-58",
                "BT-49");
        empfaenger.add(zeile(feld("BT-48", "USt-IdNr.", "Empfänger"), 1,
                feld("BT-46", "Kundennummer", "Empfänger"), 1));

        JPanel parteien = new JPanel(new GridLayout(1, 2, 40, 0));
        parteien.add(absender);
        parteien.add(empfaenger);

        JPanel zahlung = abschnitt("Zahlung", null);
        zahlung.add(zeile(zahlungsart(), 1));
        JPanel ueberweisung = gruppe(
                zeile(feld("BT-84", "IBAN", "Zahlung"), 3, feld("BT-86", "BIC", "Zahlung"), 2),
                zeile(feld("BT-85", "Kontoinhaber", "Zahlung"), 1));
        JPanel lastschrift = gruppe(
                zeile(feld("BT-90", "Gläubiger-ID", "Lastschrift"), 1, feld("BT-89", "Mandatsreferenz", "Lastschrift"), 1),
                zeile(feld("BT-91", "IBAN des Kunden", "Lastschrift"), 1));
        tipp("BT-90", "Ihre Gläubiger-Identifikationsnummer von der Bundesbank, z. B. DE98ZZZ09999999999.");
        tipp("BT-89", "Referenz des SEPA-Mandats, das der Kunde unterschrieben hat. Wird mit dem Kunden im"
                + " Adressbuch gespeichert.");
        tipp("BT-91", "Konto, von dem der Betrag eingezogen wird. Im PDF steht es verkürzt.");
        JPanel karte = gruppe(zeile(feld("BT-87", "Letzte Ziffern der Karte", "Karte"), 1,
                feld("BT-88", "Karteninhaber", "Karte"), 2));
        tipp("BT-87", "Nur die letzten 4 bis 6 Ziffern, nie die ganze Kartennummer.");
        JPanel online = gruppe(zeile(feld("BT-82", "Zahlungsdienst", "Zahlung"), 1));
        felder.get("BT-82").putClientProperty("JTextField.placeholderText", "z. B. PayPal");
        for (JPanel g : new JPanel[] {ueberweisung, lastschrift, karte, online}) {
            zahlung.add(g);
        }
        artGruppen = Map.of("ueberweisung", ueberweisung, "lastschrift", lastschrift, "karte", karte,
                "online", online);
        zeigeZahlungsart();
        zahlung.add(zeile(feld("BT-83", "Verwendungszweck", "Zahlung"), 1));
        zahlung.add(zeile(feld("BT-120", "Grund der Steuerbefreiung", "Zahlung"), 1));
        tipp("BT-120", "Nur nötig, wenn eine Position steuerfrei ist, z. B. Kleinunternehmer nach § 19 UStG.");

        zahlung.add(Box.createVerticalStrut(18));
        JPanel nachlass = abschnitt("Rabatt, Zuschlag und Skonto", null);
        nachlass.add(zeile(feld("BT-94", "Rabatt %", "Rabatt"), 1, feld("BT-92", "oder Rabatt €", "Rabatt"), 1,
                feld("BT-97", "Grund", "Rabatt"), 2));
        nachlass.add(zeile(feld("BT-101", "Zuschlag %", "Zuschlag"), 1, feld("BT-99", "oder Zuschlag €", "Zuschlag"),
                1, feld("BT-104", "Grund", "Zuschlag"), 2));
        nachlass.add(zeile(feld("OB-3", "Skonto Tage", "Skonto"), 1,
                feld("OB-4", "Skonto %", "Skonto"), 1, new JPanel(), 2));
        felder.get("BT-97").putClientProperty("JTextField.placeholderText", "Rabatt");
        felder.get("BT-104").putClientProperty("JTextField.placeholderText", "z. B. Versand");
        tipp("BT-94", "Rabatt auf die ganze Rechnung in Prozent der Positionssumme. Bei mehreren Steuersätzen wird er"
                + " auf jeden Satz angewendet.");
        tipp("BT-92", "Rabatt als fester Nettobetrag statt Prozent. Bei mehreren Steuersätzen wird er anteilig"
                + " verteilt.");
        tipp("BT-101", "Zuschlag auf die ganze Rechnung in Prozent der Positionssumme, z. B. Eilzuschlag.");
        tipp("BT-99", "Zuschlag als fester Nettobetrag, z. B. Versand oder Verpackung. Bei mehreren Steuersätzen"
                + " wird er anteilig verteilt.");
        tipp("OB-3", "Skonto: Zahlt der Kunde innerhalb dieser Tage ab Rechnungsdatum, darf er den Prozentsatz"
                + " abziehen. Steht im PDF und maschinenlesbar in der XRechnung.");
        for (JComponent teil : new JComponent[] {zahlung, nachlass}) {
            teil.setAlignmentX(LEFT_ALIGNMENT);
        }
        JPanel links = new JPanel();
        links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        links.add(zahlung);
        links.add(nachlass);

        JPanel unten = new JPanel(new GridLayout(1, 2, 40, 0));
        unten.add(links);
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
                // Breite des Sichtbereichs, aber mindestens SCHMAL (sonst rollt die Seite waagrecht, siehe unten)
                return new Dimension(Math.min(UIScale.scale(1080), Math.max(UIScale.scale(SCHMAL), platz())),
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

    /** Auswahl der Zahlungsart (BT-81), beschriftet wie ein Eingabefeld. */
    private JComponent zahlungsart() {
        for (String[] a : ZAHLUNGSARTEN) {
            art.addItem(a[0]);
        }
        art.addActionListener(e -> {
            zeigeZahlungsart();
            geaendert();
        });
        art.setToolTipText("Lastschrift und Karte brauchen eigene Angaben, bar und online keine.");
        JLabel l = gedaempft(new JLabel("Zahlungsart"));
        l.putClientProperty("FlatLaf.styleClass", "small");
        l.setLabelFor(art);
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.add(l, BorderLayout.NORTH);
        p.add(art);
        return p;
    }

    /** Auswahl des Formats (BT-24): XRechnung für Behörden oder ZUGFeRD EN 16931 für Unternehmen. */
    private JComponent formatAuswahl() {
        for (String[] f : FORMATE) {
            format.addItem(f[0]);
        }
        format.addActionListener(e -> {
            zeigeFormat();
            geaendert();
        });
        format.setToolTipText("<html>Beides ist ein ZUGFeRD-PDF mit eingebetteter XML.<br>XRechnung verlangt die"
                + " Leitweg-ID und den Kontakt des Absenders,<br>EN 16931 genügt für Geschäftskunden.</html>");
        JLabel l = gedaempft(new JLabel("Format"));
        l.putClientProperty("FlatLaf.styleClass", "small");
        l.setLabelFor(format);
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.add(l, BorderLayout.NORTH);
        p.add(format);
        return p;
    }

    /** BT-24 der Auswahl. Ein unbekannter Wert aus einer ODS steht unverändert als eigener Eintrag darin. */
    private String formatCode() {
        String gewaehlt = (String) format.getSelectedItem();
        for (String[] f : FORMATE) {
            if (f[0].equals(gewaehlt)) {
                return f[1];
            }
        }
        return gewaehlt == null ? FORMATE[0][1] : gewaehlt;
    }

    /** Kurzname des gewählten Formats für Menü und Meldungen. */
    String formatName() {
        return Rechnung.istXRechnung(formatCode()) ? "XRechnung" : "ZUGFeRD EN 16931";
    }

    private void waehleFormat(String code) {
        for (String[] f : FORMATE) {
            if (f[1].equals(code)) {
                format.setSelectedItem(f[0]);
                return;
            }
        }
        if (((javax.swing.DefaultComboBoxModel<String>) format.getModel()).getIndexOf(code) < 0) {
            format.addItem(code);
        }
        format.setSelectedItem(code);
    }

    /** Die Käuferreferenz ist nur in der XRechnung Pflicht (BR-DE-15). */
    private void zeigeFormat() {
        boolean xr = Rechnung.istXRechnung(formatCode());
        JLabel l = beschriftungen.get("BT-10");
        if (l != null) {
            l.setText(xr ? "Käuferreferenz / Leitweg-ID *" : "Käuferreferenz");
        }
        tipp("BT-10", xr ? "Pflichtangabe. Bei Behörden die Leitweg-ID, sonst z. B. Bestell- oder Kundennummer."
                : "Optional, z. B. Bestell- oder Kundennummer des Empfängers.");
    }

    /** Zeilen, die nur bei bestimmten Zahlungsarten sichtbar sind. */
    private static JPanel gruppe(JComponent... zeilen) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(LEFT_ALIGNMENT);
        for (JComponent z : zeilen) {
            p.add(z);
        }
        return p;
    }

    /** BT-81 der Auswahl. Ein Code, den die Liste nicht kennt (aus einer ODS), steht als „Code 30“ darin. */
    private String artCode() {
        String gewaehlt = (String) art.getSelectedItem();
        for (String[] a : ZAHLUNGSARTEN) {
            if (a[0].equals(gewaehlt)) {
                return a[1];
            }
        }
        return gewaehlt == null ? "58" : gewaehlt.replace("Code ", "");
    }

    private void waehleArt(String code) {
        for (String[] a : ZAHLUNGSARTEN) {
            if (a[1].equals(code)) {
                art.setSelectedItem(a[0]);
                return;
            }
        }
        if (((javax.swing.DefaultComboBoxModel<String>) art.getModel()).getIndexOf("Code " + code) < 0) {
            art.addItem("Code " + code);
        }
        art.setSelectedItem("Code " + code);
    }

    private void zeigeZahlungsart() {
        if (artGruppen == null) {
            return;
        }
        var z = new Rechnung.Zahlung(artCode(), null, null, null, null);
        artGruppen.get("ueberweisung").setVisible(z.istUeberweisung());
        artGruppen.get("lastschrift").setVisible(z.istLastschrift());
        artGruppen.get("karte").setVisible(z.istKarte());
        artGruppen.get("online").setVisible(!z.istUeberweisung() && !z.istLastschrift() && !z.istKarte());
        revalidate();
    }

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
        JPanel kopf = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, UIScale.scale(30));
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, UIScale.scale(30));
            }
        };
        kopf.add(l, BorderLayout.WEST);
        if (rechts != null) {
            kopf.add(rechts, BorderLayout.EAST);
        }
        kopf.setBorder(rand(0, 0, 6, 0));
        kopf.setAlignmentX(LEFT_ALIGNMENT);
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
        beschriftungen.put(bt, l);
        l.putClientProperty("FlatLaf.styleClass", "small");
        gedaempft(l);
        l.setLabelFor(f);
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.add(l, BorderLayout.NORTH);
        p.add(f instanceof JTextArea ? rollbar(f) : f);
        return p;
    }

    private static JTextArea mehrzeilig(JTextArea a) {
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        return a;
    }

    /**
     * Scrollbereich für ein mehrzeiliges Feld. Das Mausrad rollt das Feld nur, solange dort noch etwas zu rollen
     * ist, sonst die ganze Seite. Ein normaler {@link JScrollPane} schluckt das Mausrad immer.
     */
    private static JScrollPane rollbar(JComponent inhalt) {
        JScrollPane s = new JScrollPane(inhalt);
        s.setWheelScrollingEnabled(false);
        s.addMouseWheelListener(e -> {
            JScrollBar b = s.getVerticalScrollBar();
            boolean nachOben = e.getWheelRotation() < 0;
            boolean kann = b.isVisible() && (nachOben ? b.getValue() > b.getMinimum()
                    : b.getValue() + b.getVisibleAmount() < b.getMaximum());
            if (kann) {
                b.setValue(b.getValue() + e.getUnitsToScroll() * b.getUnitIncrement(nachOben ? -1 : 1));
            } else {
                s.getParent().dispatchEvent(SwingUtilities.convertMouseEvent(s, e, s.getParent()));
            }
        });
        return s;
    }

    /**
     * Leerer Rand, der mit der Schriftgröße (Zoom) wächst. Nicht {@code FlatEmptyBorder}: das ist eine UIResource,
     * und {@code updateUI()} beim Wechsel von Design oder Größe würde ihn entfernen.
     */
    static Border rand(int oben, int links, int unten, int rechts) {
        return new EmptyBorder(oben, links, unten, rechts) {
            @Override
            public Insets getBorderInsets(java.awt.Component c, Insets i) {
                i.set(UIScale.scale(top), UIScale.scale(left), UIScale.scale(bottom), UIScale.scale(right));
                return i;
            }

            @Override
            public Insets getBorderInsets() {
                return getBorderInsets(null, new Insets(0, 0, 0, 0));
            }
        };
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
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() { // nur in der Breite dehnen, Höhe folgt der Schriftgröße
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < teile.length; i += 2) {
            c.weightx = (Integer) teile[i + 1];
            c.insets = new Insets(0, i == 0 ? 0 : 12, 10, 0);
            p.add((JComponent) teile[i], c);
        }
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    /** Adressbuch-Knöpfe, beschriftet und so hoch wie ein Eingabefeld. */
    private static JComponent adressbuch(Runnable aus, Runnable ins) {
        JPanel knoepfe = new JPanel(new GridLayout(1, 2, 12, 0));
        // kurze Beschriftungen, „Adressbuch“ steht schon darüber; so passen sie auch bei großer Schrift ganz hinein
        String[][] knopf = {{"Auswählen…", "Empfänger aus dem Adressbuch übernehmen"},
                {"Speichern", "Diesen Empfänger im Adressbuch speichern"}};
        for (String[] k : knopf) {
            JButton b = new JButton(k[0]) {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(10, new JTextField().getPreferredSize().height);
                }

                @Override
                public Dimension getMinimumSize() { // Breite gibt das Raster vor, nicht der Text
                    return getPreferredSize();
                }
            };
            b.setToolTipText(k[1]);
            b.setFocusable(false);
            knoepfe.add(b);
        }
        ((JButton) knoepfe.getComponent(0)).addActionListener(e -> aus.run());
        ((JButton) knoepfe.getComponent(1)).addActionListener(e -> ins.run());
        JLabel l = gedaempft(new JLabel("Adressbuch"));
        l.putClientProperty("FlatLaf.styleClass", "small");
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.add(l, BorderLayout.NORTH);
        p.add(knoepfe);
        return p;
    }

    private JComponent positionsteil() {
        tabelle.setShowHorizontalLines(true);
        tabelle.setFillsViewportHeight(true);
        tabelle.putClientProperty("terminateEditOnFocusLost", true);
        tabelle.getTableHeader().setReorderingAllowed(false);
        tabelle.removeColumn(tabelle.getColumnModel().getColumn(BASIS));
        tabelle.removeColumn(tabelle.getColumnModel().getColumn(BASIS)); // danach steht dort BESCHREIBUNG
        tabelle.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        int[] breiten = {44, 420, 70, 100, 110, 150, 110};
        for (int i = 0; i < breiten.length; i++) {
            spaltenBreiten.put(tabelle.getColumnModel().getColumn(i), breiten[i]);
        }
        DefaultTableCellRenderer rechts = new DefaultTableCellRenderer();
        rechts.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int spalte : new int[] {MENGE, PREIS, BETRAG_SPALTE}) {
            tabelle.getColumnModel().getColumn(spalte).setCellRenderer(rechts);
        }
        // Rabatt (BT-138) steht im Modell hinten, in der Ansicht vor dem Betrag
        var rabatt = tabelle.getColumnModel().getColumn(tabelle.convertColumnIndexToView(RABATT));
        spaltenBreiten.put(rabatt, 80);
        spaltenBreiten.forEach((spalte, breite) -> spalte.setPreferredWidth(UIScale.scale(breite)));
        rabatt.setCellRenderer(rechts);
        tabelle.moveColumn(tabelle.convertColumnIndexToView(RABATT), tabelle.convertColumnIndexToView(BETRAG_SPALTE));
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
                "19 %", "", null, null, ""});
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
        knoepfe.setBorder(rand(10, 0, 0, 0));

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
        detail.setBorder(rand(12, 0, 0, 0));
        detail.add(titel, BorderLayout.NORTH);
        detail.add(rollbar(beschreibung));

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
        gesamt.putClientProperty("FlatLaf.style", "font: bold +5"); // als Stil, damit es dem Zoom folgt
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
        waehleArt(e.daten().getOrDefault("BT-81", openbilling.ods.RechnungOds.VORGABEN.get("BT-81")));
        waehleFormat(e.daten().getOrDefault("BT-24", openbilling.ods.RechnungOds.VORGABEN.get("BT-24")));
        positionen.setRowCount(0);
        for (String[] z : e.positionen()) {
            BigDecimal preis = zahl(z[4]);
            positionen.addRow(new Object[] {z[0], z[1], z[2], einheit(z[3], 1, 0),
                preis != null ? BETRAG.format(preis) : z[4], steuerText(z[6], z[7]), "", z[5], z[8],
                z.length > 9 ? z[9] : null});
        }
        laedt = false;
        rechne();
    }

    Entwurf entwurf() {
        beendeEingabe();
        Map<String, String> d = new LinkedHashMap<>(rest);
        for (String bt : List.of("BT-3", "BT-5", "BT-23", "BT-24")) {
            // Pflichtangaben ohne eigenes Feld: fehlen sie in einer alten Vorlage, gilt die Vorgabe
            d.putIfAbsent(bt, openbilling.ods.RechnungOds.VORGABEN.get(bt));
        }
        d.put("BT-81", artCode());
        d.put("BT-24", formatCode());
        felder.forEach((bt, f) -> {
            if (f.getText().isBlank()) {
                d.remove(bt);
            } else {
                d.put(bt, ZAHLEN.contains(bt) ? normal(f.getText().strip()) : f.getText().strip());
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
                wert(i, BESCHREIBUNG), normal(wert(i, RABATT))});
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
                            Steuerkategorie.valueOf(st[0]), zahl(st[1]), null, zahl(wert(i, RABATT))));
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
                    null, null, null, null, null, ps).mit(abschlaegeZuschlaege(), null));
            for (int i = 0; i < zeilen.size(); i++) {
                positionen.setValueAt(BETRAG.format(erg.zeilenbetraege().get(i)), zeilen.get(i), BETRAG_SPALTE);
            }
            netto.setText(BETRAG.format(erg.netto()) + waehrung);
            steuer.setText(BETRAG.format(erg.steuer()) + waehrung);
            gesamt.setText(BETRAG.format(erg.brutto()) + waehrung);
        }
        laedt = vorher;
    }

    /** Rabatt und Zuschlag auf die ganze Rechnung, soweit schon gültig eingegeben; für die Summen im Formular. */
    private List<Rechnung.AbschlagZuschlag> abschlaegeZuschlaege() {
        List<Rechnung.AbschlagZuschlag> liste = new ArrayList<>();
        for (String[] k : new String[][] {{"BT-94", "BT-92", "BT-97", ""}, {"BT-101", "BT-99", "BT-104", "z"}}) {
            try {
                BigDecimal p = zahl(felder.get(k[0]).getText());
                BigDecimal b = zahl(felder.get(k[1]).getText());
                if (p != null || b != null) {
                    liste.add(new Rechnung.AbschlagZuschlag(!k[3].isEmpty(), p, b, null));
                }
            } catch (IllegalArgumentException unvollstaendig) {
                // zählt erst, wenn die Eingabe stimmt; die Prüfung meldet den Fehler
            }
        }
        return liste;
    }

    // ------------------------------------------------------------ Umrechnung Anzeige ↔ Datei

    /** Deutsche Zahl: {@code 3.000,50}, {@code 3000,5}, {@code 3.000}; ein einzelner Punkt gilt als Komma. */
    static BigDecimal zahl(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        s = s.replace(" ", "").replace("€", "").replace("%", "");
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
        return UIScale.scale(24);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle sichtbar, int richtung, int vorzeichen) {
        return sichtbar.height - 48;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Bei großer Schrift in kleinem Fenster lieber waagrecht rollen, als Felder zu stauchen und Text zu kürzen
        return platz() >= UIScale.scale(SCHMAL);
    }

    /** Breite für den Inhalt: Sichtbereich (Viewport) abzüglich Rand, nicht die eigene, evtl. breitere Größe. */
    private int platz() {
        Insets rand = getInsets();
        int breite = getParent() != null ? getParent().getWidth() : getWidth();
        return breite - rand.left - rand.right;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
