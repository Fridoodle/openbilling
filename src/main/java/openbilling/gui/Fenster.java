package openbilling.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import openbilling.config.Einstellungen;
import openbilling.model.Rechnung;
import openbilling.ods.Kunden;
import openbilling.ods.RechnungOds;
import openbilling.ods.RechnungOds.Entwurf;
import openbilling.ods.RechnungOds.OdsFehler;
import openbilling.pdf.PdfExport;
import openbilling.pruefung.Pruefung;
import openbilling.xml.CiiSchreiber;

/** Hauptfenster: Startseite mit Vorlagen, Rechnungsformular, Menü Datei / Adressbuch / Ansicht. */
public final class Fenster extends JFrame {

    private static final String DUNKEL = "ansicht.dunkel";
    private static final String ORDNER = "rechnungsordner";
    private static final Pattern BT = Pattern.compile("BT-\\d+(?:-\\d+)?");

    private final Einstellungen einstellungen;
    private final Formular formular = new Formular(this::ausAdressbuch, this::insAdressbuch);
    private final CardLayout karten = new CardLayout();
    private final JPanel inhalt = new JPanel(karten);
    private final JLabel status = Formular.gedaempft(new JLabel(" "));
    private final DefaultListModel<String> vorlagen = new DefaultListModel<>();
    private final List<JMenuItem> nurMitRechnung = new ArrayList<>();
    private JButton erstellen;
    private Path datei;
    /** Name der Vorlage, aus der die offene Rechnung entstanden ist, sonst {@code null}. */
    private String vorlage;
    private boolean offen;
    private boolean geaendert;
    private Pruefung pruefung;

    public static void starte(Einstellungen einstellungen) {
        SwingUtilities.invokeLater(() -> {
            design(Boolean.parseBoolean(einstellungen.wert(DUNKEL, "false")));
            new Fenster(einstellungen).setVisible(true);
        });
    }

    private static void design(boolean dunkel) {
        if (dunkel) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI();
    }

    Fenster(Einstellungen einstellungen) {
        this.einstellungen = einstellungen;
        setIconImages(Symbol.bilder());
        setJMenuBar(menue());
        JScrollPane rollen = new JScrollPane(formular);
        rollen.setBorder(null);
        inhalt.add(startseite(), "start");
        inhalt.add(rollen, "rechnung");
        status.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        add(inhalt);
        add(status, BorderLayout.SOUTH);
        formular.beiAenderung = () -> {
            geaendert = true;
            titel();
        };
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (verwerfenErlaubt()) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        setSize(1180, 860);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);
        zeigeStart();
    }

    // ------------------------------------------------------------ Menü und Startseite

    private JMenuBar menue() {
        JMenu d = new JMenu("Datei");
        d.add(eintrag("Neu…", KeyEvent.VK_N, false, this::zeigeStart));
        d.add(eintrag("Öffnen…", KeyEvent.VK_O, false, this::oeffne));
        d.addSeparator();
        d.add(eintrag("Speichern", KeyEvent.VK_S, true, () -> speichere(false)));
        d.add(eintrag("Speichern unter…", 0, true, () -> speichere(true)));
        d.add(eintrag("Als Vorlage speichern…", 0, true, this::speichereVorlage));
        d.addSeparator();
        d.add(eintrag("Prüfen", KeyEvent.VK_P, true, this::pruefe));
        d.add(eintrag("Als PDF exportieren…", 0, true, () -> exportiere(false)));
        d.add(eintrag("Als PDF mit XRechnung exportieren…", KeyEvent.VK_E, true, () -> exportiere(true)));
        d.addSeparator();
        d.add(eintrag("Beenden", 0, false,
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu a = new JMenu("Adressbuch");
        a.add(eintrag("Empfänger aus Adressbuch…", 0, true, this::ausAdressbuch));
        a.add(eintrag("Empfänger im Adressbuch speichern", 0, true, this::insAdressbuch));

        JMenu v = new JMenu("Ansicht");
        JCheckBoxMenuItem dunkel = new JCheckBoxMenuItem("Dunkles Design",
                Boolean.parseBoolean(einstellungen.wert(DUNKEL, "false")));
        dunkel.addActionListener(e -> {
            design(dunkel.isSelected());
            versuche(() -> einstellungen.setze(DUNKEL, String.valueOf(dunkel.isSelected())));
        });
        v.add(dunkel);

        JMenuBar leiste = new JMenuBar();
        leiste.add(d);
        leiste.add(a);
        leiste.add(v);
        return leiste;
    }

    private JMenuItem eintrag(String text, int taste, boolean brauchtRechnung, Runnable aktion) {
        JMenuItem m = new JMenuItem(text);
        if (taste != 0) {
            m.setAccelerator(KeyStroke.getKeyStroke(taste, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        m.addActionListener(e -> aktion.run());
        if (brauchtRechnung) {
            nurMitRechnung.add(m);
        }
        return m;
    }

    private JComponent startseite() {
        JLabel titel = new JLabel("Neue Rechnung");
        titel.putClientProperty("FlatLaf.styleClass", "h1");
        JLabel unter = Formular.gedaempft(new JLabel("Vorlage wählen. Sie bringt Absender, Bankverbindung und"
                + " alles Wiederkehrende mit."));

        JList<String> liste = new JList<>(vorlagen);
        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liste.setFixedCellHeight(34);
        JScrollPane rollen = new JScrollPane(liste);
        rollen.setPreferredSize(new Dimension(460, 220));

        erstellen = new JButton("Rechnung erstellen");
        Runnable neu = () -> {
            if (liste.getSelectedValue() != null) {
                neuAusVorlage(liste.getSelectedValue());
            }
        };
        erstellen.addActionListener(e -> neu.run());
        liste.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    neu.run();
                }
            }
        });
        vorlagen.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                liste.setSelectedIndex(0);
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {}

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {}
        });
        JButton oeffnen = new JButton("Vorhandene Rechnung öffnen…");
        oeffnen.addActionListener(e -> oeffne());
        JButton loeschen = new JButton("Vorlage löschen");
        loeschen.putClientProperty("JButton.buttonType", "borderless");
        loeschen.addActionListener(e -> loescheVorlage(liste.getSelectedValue()));

        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        knoepfe.add(erstellen);
        knoepfe.add(Box.createHorizontalStrut(8));
        knoepfe.add(oeffnen);
        knoepfe.add(Box.createHorizontalStrut(8));
        knoepfe.add(loeschen);

        JPanel spalte = new JPanel();
        spalte.setLayout(new BoxLayout(spalte, BoxLayout.Y_AXIS));
        int[] abstand = {6, 20, 16, 0};
        JComponent[] teile = {titel, unter, rollen, knoepfe};
        for (int i = 0; i < teile.length; i++) {
            teile[i].setAlignmentX(LEFT_ALIGNMENT);
            spalte.add(teile[i]);
            spalte.add(Box.createVerticalStrut(abstand[i]));
        }
        JPanel mitte = new JPanel(new GridBagLayout());
        mitte.add(spalte);
        return mitte;
    }

    private void zeigeStart() {
        if (!verwerfenErlaubt()) {
            return;
        }
        versuche(() -> {
            einstellungen.vorlageAnlegen();
            vorlagen.clear();
            try (var dateien = Files.list(einstellungen.vorlagen())) {
                dateien.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase().endsWith(".ods"))
                        .map(n -> n.substring(0, n.length() - 4)).sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(vorlagen::addElement);
            }
        });
        offen = false;
        geaendert = false;
        datei = null;
        vorlage = null;
        karten.show(inhalt, "start");
        getRootPane().setDefaultButton(erstellen);
        melde("Vorlagen liegen in " + einstellungen.vorlagen());
        titel();
    }

    private void zeigeRechnung(Entwurf e, Path quelle) {
        formular.zeige(e);
        datei = quelle;
        offen = true;
        geaendert = false;
        karten.show(inhalt, "rechnung");
        getRootPane().setDefaultButton(null);
        melde(quelle == null ? "Neue Rechnung, noch nicht gespeichert" : quelle.toString());
        titel();
    }

    private void titel() {
        nurMitRechnung.forEach(m -> m.setEnabled(offen));
        String name = !offen ? null : datei == null ? "Neue Rechnung" : datei.getFileName().toString();
        setTitle((geaendert ? "● " : "") + (name == null ? "" : name + " – ") + "openbilling");
    }

    private void melde(String text) {
        status.setText(text);
    }

    // ------------------------------------------------------------ Dateien

    private void neuAusVorlage(String name) {
        versuche(() -> {
            Entwurf e = RechnungOds.leseEntwurf(einstellungen.vorlagen().resolve(name + ".ods"));
            e.daten().putIfAbsent("BT-2", LocalDate.now().format(DatumFeld.FORMAT));
            zeigeRechnung(e, null);
            vorlage = name;
        });
    }

    private void oeffne() {
        if (!verwerfenErlaubt()) {
            return;
        }
        Path p = dateiwahl(FileDialog.LOAD, "ods", null);
        if (p != null) {
            versuche(() -> {
                zeigeRechnung(RechnungOds.leseEntwurf(p), p);
                vorlage = null;
                einstellungen.setze(ORDNER, p.toAbsolutePath().getParent().toString());
            });
        }
    }

    /** Liefert {@code true}, wenn gespeichert wurde. */
    private boolean speichere(boolean unter) {
        Entwurf e = formular.entwurf();
        Path ziel = datei;
        if (unter || ziel == null) {
            ziel = waehleZiel("ods", e);
            if (ziel == null) {
                return false;
            }
        }
        Path p = ziel;
        return versuche(() -> {
            RechnungOds.schreibe(e, p);
            datei = p;
            geaendert = false;
            titel();
            melde("Gespeichert: " + p);
        });
    }

    /** Neue Vorlage anlegen oder eine vorhandene überschreiben; vorgeschlagen ist die Vorlage der Rechnung. */
    private void speichereVorlage() {
        JComboBox<String> wahl = new JComboBox<>();
        for (int i = 0; i < vorlagen.size(); i++) {
            wahl.addItem(vorlagen.get(i));
        }
        wahl.setEditable(true);
        wahl.setSelectedItem(vorlage != null ? vorlage : "");
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel("<html>Neuen Namen eingeben oder eine vorhandene Vorlage zum Überschreiben wählen.<br>"
                + "Rechnungsnummer und Datumsangaben werden nicht übernommen, alles andere schon.</html>"),
                BorderLayout.NORTH);
        p.add(wahl);
        if (JOptionPane.showConfirmDialog(this, p, "Als Vorlage speichern", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String name = String.valueOf(wahl.getEditor().getItem());
        if (name.isBlank()) {
            return;
        }
        Path ziel = einstellungen.vorlagen().resolve(dateiname(name) + ".ods");
        if (Files.exists(ziel) && !bestaetigt("Die Vorlage „" + name.strip() + "“ gibt es schon. Ersetzen?")) {
            return;
        }
        Entwurf e = formular.entwurf();
        Map<String, String> daten = new LinkedHashMap<>(e.daten());
        List.of("BT-1", "BT-2", "BT-9", "BT-72").forEach(daten::remove);
        versuche(() -> {
            Files.createDirectories(ziel.getParent());
            RechnungOds.schreibe(new Entwurf(daten, e.positionen()), ziel);
            String n = dateiname(name);
            if (!vorlagen.contains(n)) {
                vorlagen.addElement(n);
            }
            vorlage = n;
            melde("Vorlage gespeichert: " + ziel);
        });
    }

    private void loescheVorlage(String name) {
        if (name != null && bestaetigt("Vorlage „" + name + "“ löschen?")) {
            versuche(() -> {
                Files.delete(einstellungen.vorlagen().resolve(name + ".ods"));
                vorlagen.removeElement(name);
            });
        }
    }

    /**
     * Nativer Dateidialog des Betriebssystems (unter Windows der Explorer-Dialog) statt des Swing-Dialogs.
     * Liefert {@code null} bei Abbruch.
     */
    private Path dateiwahl(int modus, String endung, String vorschlag) {
        Path ordner = datei != null ? datei.toAbsolutePath().getParent()
                : Path.of(einstellungen.wert(ORDNER, System.getProperty("user.home")));
        FileDialog d = new FileDialog(this, modus == FileDialog.LOAD ? "Öffnen" : "Speichern unter", modus);
        d.setDirectory(ordner.toString());
        d.setFile(vorschlag != null ? vorschlag + "." + endung : "*." + endung); // Windows: Muster wirkt als Filter
        d.setFilenameFilter((o, name) -> name.toLowerCase().endsWith("." + endung)); // andere Systeme
        d.setVisible(true);
        return d.getFile() == null ? null : Path.of(d.getDirectory(), d.getFile());
    }

    /** Fragt nach einer Zieldatei; Vorschlag ist der Dateiname bzw. die Rechnungsnummer. */
    private Path waehleZiel(String endung, Entwurf e) {
        String vorschlag = datei != null ? datei.getFileName().toString().replaceFirst("(?i)\\.ods$", "")
                : dateiname(e.daten().getOrDefault("BT-1", "Rechnung"));
        Path p = dateiwahl(FileDialog.SAVE, endung, vorschlag);
        if (p == null) {
            return null;
        }
        // Überschreiben fragt der native Dialog selbst ab, aber nur für den Namen, so wie er eingegeben wurde
        if (!p.getFileName().toString().toLowerCase().endsWith("." + endung)) {
            p = p.resolveSibling(p.getFileName() + "." + endung);
            if (Files.exists(p) && !bestaetigt(p.getFileName() + " gibt es schon. Ersetzen?"
                    + (endung.equals("pdf") ? "\nEine bereits versendete Rechnung darf nicht verändert werden." : ""))) {
                return null;
            }
        }
        Path ordner = p.toAbsolutePath().getParent();
        versuche(() -> einstellungen.setze(ORDNER, ordner.toString()));
        return p;
    }

    private static String dateiname(String s) {
        return s.strip().replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private boolean verwerfenErlaubt() {
        if (!geaendert) {
            return true;
        }
        int antwort = JOptionPane.showConfirmDialog(this, "Änderungen an der Rechnung speichern?", "openbilling",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return antwort == JOptionPane.NO_OPTION || antwort == JOptionPane.YES_OPTION && speichere(false);
    }

    // ------------------------------------------------------------ Prüfen und Exportieren

    private void pruefe() {
        Rechnung r = vollstaendig();
        if (r != null) {
            imHintergrund("Prüfe die Rechnung …", () -> meldungen(CiiSchreiber.schreibe(r)), m -> {
                if (m.isEmpty()) {
                    melde("Geprüft: gültige XRechnung.");
                    JOptionPane.showMessageDialog(this, "Die Rechnung ist eine gültige XRechnung.", "Prüfen",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    zeigeProbleme("Die Rechnung ist noch nicht gültig:", m);
                }
            });
        }
    }

    private void exportiere(boolean mitXRechnung) {
        Rechnung r = vollstaendig();
        if (r == null) {
            return;
        }
        Entwurf e = formular.entwurf();
        imHintergrund("Erzeuge das PDF …", () -> {
            String xml = CiiSchreiber.schreibe(r);
            List<String> m = mitXRechnung ? meldungen(xml) : List.<String>of();
            return m.isEmpty() ? (Object) (mitXRechnung ? PdfExport.hybrid(r, xml) : PdfExport.sicht(r)) : m;
        }, ergebnis -> {
            if (ergebnis instanceof byte[] pdf) {
                Path ziel = waehleZiel("pdf", e);
                if (ziel != null && versuche(() -> Files.write(ziel, pdf))) {
                    melde("Exportiert: " + ziel + (mitXRechnung ? " (PDF/A-3 mit XRechnung)" : " (ohne XRechnung)"));
                    Object[] wahl = {"PDF öffnen", "Schließen"};
                    if (JOptionPane.showOptionDialog(this, "Gespeichert: " + ziel, "Export",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, wahl, wahl[1]) == 0) {
                        versuche(() -> Desktop.getDesktop().open(ziel.toFile()));
                    }
                }
            } else {
                @SuppressWarnings("unchecked")
                List<String> m = (List<String>) ergebnis;
                zeigeProbleme("Die Rechnung ist noch nicht gültig und wurde nicht exportiert:", m);
            }
        });
    }

    /** Rechnung aus dem Formular oder {@code null}; fehlende Felder werden angezeigt und markiert. */
    private Rechnung vollstaendig() {
        try {
            Rechnung r = RechnungOds.rechnung(formular.entwurf());
            formular.markiere(Set.of());
            return r;
        } catch (OdsFehler f) {
            Set<String> bts = new HashSet<>();
            List<String> texte = new ArrayList<>();
            for (String problem : f.probleme()) {
                Matcher m = BT.matcher(problem);
                String text = problem.replaceFirst("^Daten, ", "").replace(" oder fehlt", "");
                if (m.find()) {
                    bts.add(m.group());
                    text = text.replace(m.group(), formular.name(m.group()));
                }
                texte.add(text);
            }
            formular.markiere(bts);
            zeigeProbleme("Es fehlen noch Angaben:", texte);
            return null;
        }
    }

    /** Meldungen des KoSIT-Validators; leer heißt exportbereit. */
    private List<String> meldungen(String xml) {
        if (pruefung == null) {
            pruefung = new Pruefung(einstellungen.kositKonfiguration()); // Laden dauert Sekunden, daher einmal
        }
        var ergebnis = pruefung.pruefe(xml, "rechnung.xml");
        if (ergebnis.exportbereit()) {
            return List.of();
        }
        List<String> m = new ArrayList<>();
        ergebnis.meldungen().forEach(x -> m.add(x.regel() + ": " + x.text()));
        return m.isEmpty() ? List.of("Der Validator hat die Rechnung abgelehnt.") : m;
    }

    private void zeigeProbleme(String ueberschrift, List<String> probleme) {
        JTextArea text = new JTextArea(String.join("\n", probleme));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JScrollPane rollen = new JScrollPane(text);
        rollen.setPreferredSize(new Dimension(560, Math.min(320, 60 + 22 * probleme.size())));
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel(ueberschrift), BorderLayout.NORTH);
        p.add(rollen);
        melde(ueberschrift);
        JOptionPane.showMessageDialog(this, p, "openbilling", JOptionPane.WARNING_MESSAGE);
    }

    private <T> void imHintergrund(String meldung, Callable<T> arbeit, Consumer<T> danach) {
        melde(meldung);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return arbeit.call();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                melde(" ");
                try {
                    danach.accept(get());
                } catch (ExecutionException e) {
                    fehler(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ Adressbuch

    private void insAdressbuch() {
        Map<String, String> kunde = formular.kunde();
        if (!kunde.containsKey("BT-44")) {
            formular.markiere(Set.of("BT-44"));
            melde("Zum Speichern im Adressbuch braucht der Empfänger einen Namen.");
            return;
        }
        versuche(() -> {
            List<Map<String, String>> kunden = Kunden.lese(einstellungen.kunden());
            boolean ersetzt = kunden.removeIf(k -> k.get("BT-44").equalsIgnoreCase(kunde.get("BT-44")));
            kunden.add(kunde);
            kunden.sort(Comparator.comparing(k -> k.get("BT-44"), String.CASE_INSENSITIVE_ORDER));
            Kunden.schreibe(kunden, einstellungen.kunden());
            melde("Adressbuch: „" + kunde.get("BT-44") + "“ " + (ersetzt ? "aktualisiert." : "gespeichert."));
        });
    }

    private void ausAdressbuch() {
        List<Map<String, String>> kunden = new ArrayList<>();
        if (!versuche(() -> kunden.addAll(Kunden.lese(einstellungen.kunden())))) {
            return;
        }
        if (kunden.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Das Adressbuch ist noch leer.\nEmpfänger ausfüllen und"
                    + " „Im Adressbuch speichern“ wählen.", "Adressbuch", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JDialog dialog = new JDialog(this, "Adressbuch", true);
        DefaultListModel<Map<String, String>> modell = new DefaultListModel<>();
        JList<Map<String, String>> liste = new JList<>(modell);
        liste.setFixedCellHeight(34);
        liste.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object wert, int i, boolean gewaehlt,
                    boolean fokus) {
                Map<?, ?> k = (Map<?, ?>) wert;
                String ort = k.get("BT-52") == null ? "" : "   ·   " + k.get("BT-52");
                return super.getListCellRendererComponent(l, k.get("BT-44") + ort, i, gewaehlt, fokus);
            }
        });
        JTextField suche = new JTextField();
        suche.putClientProperty("JTextField.placeholderText", "Suchen");
        Runnable filtere = () -> {
            modell.clear();
            String s = suche.getText().strip().toLowerCase();
            kunden.stream().filter(k -> String.join(" ", k.values()).toLowerCase().contains(s))
                    .forEach(modell::addElement);
            liste.setSelectedIndex(0);
        };
        suche.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtere.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtere.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        filtere.run();

        JButton nehmen = new JButton("Übernehmen");
        Runnable uebernehmen = () -> {
            if (liste.getSelectedValue() != null) {
                formular.setzeKunde(liste.getSelectedValue());
                dialog.dispose();
            }
        };
        nehmen.addActionListener(e -> uebernehmen.run());
        suche.addActionListener(e -> uebernehmen.run());
        liste.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    uebernehmen.run();
                }
            }
        });
        JButton loeschen = new JButton("Löschen");
        loeschen.putClientProperty("JButton.buttonType", "borderless");
        loeschen.addActionListener(e -> {
            Map<String, String> k = liste.getSelectedValue();
            if (k != null && bestaetigt("„" + k.get("BT-44") + "“ aus dem Adressbuch löschen?")) {
                kunden.remove(k);
                if (versuche(() -> Kunden.schreibe(kunden, einstellungen.kunden()))) {
                    filtere.run();
                }
            }
        });
        JPanel knoepfe = new JPanel(new BorderLayout());
        knoepfe.add(loeschen, BorderLayout.WEST);
        knoepfe.add(nehmen, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(suche, BorderLayout.NORTH);
        p.add(new JScrollPane(liste));
        p.add(knoepfe, BorderLayout.SOUTH);
        dialog.setContentPane(p);
        dialog.getRootPane().setDefaultButton(nehmen);
        dialog.setSize(460, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------ Hilfen

    private interface Schritt {
        void run() throws Exception;
    }

    /** Führt aus und zeigt Fehler an. Liefert {@code true}, wenn es geklappt hat. */
    private boolean versuche(Schritt schritt) {
        try {
            schritt.run();
            return true;
        } catch (Exception e) {
            fehler(e);
            return false;
        }
    }

    private void fehler(Throwable e) {
        String text = e instanceof OdsFehler || e.getMessage() != null ? e.getMessage() : e.toString();
        melde("Fehler: " + text.lines().findFirst().orElse(""));
        JOptionPane.showMessageDialog(this, text, "openbilling", JOptionPane.ERROR_MESSAGE);
    }

    private boolean bestaetigt(String frage) {
        return JOptionPane.showConfirmDialog(this, frage, "openbilling", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
}
