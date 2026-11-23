package openbilling.ods;

import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adressbuch {@code kunden.ods}: Blatt {@code Kunden}, Zeile 1 Spaltenköpfe mit BT-Kennung (Käuferfelder
 * der Rechnung plus BT-10), darunter eine Zeile je Kunde. Wie bei {@link RechnungOds} zählt nur die Kennung.
 */
public final class Kunden {

    public static final String BLATT = "Kunden";

    private static final String[] SPALTEN = {
        "BT-44 Name",
        "BT-46 Kundennummer",
        "BT-50 Straße",
        "BT-53 PLZ",
        "BT-52 Ort",
        "BT-55 Land",
        "BT-56 Kontaktperson",
        "BT-57 Telefon",
        "BT-58 E-Mail",
        "BT-49 Elektronische Adresse",
        "BT-49-1 Schema (EM = E-Mail, 0204 = Leitweg-ID)",
        "BT-48 USt-IdNr.",
        "BT-10 Leitweg-ID / Käuferreferenz",
    };

    /** BT-Kennungen, die ein Kunde trägt. */
    public static final List<String> FELDER =
            java.util.Arrays.stream(SPALTEN).map(RechnungOds::kennung).toList();

    private Kunden() {}

    /** Kunden als BT-Kennung → Wert. Fehlt die Datei, ist das Adressbuch leer. */
    public static List<Map<String, String>> lese(Path datei) throws IOException {
        List<Map<String, String>> kunden = new ArrayList<>();
        Sheet blatt = Files.exists(datei) ? new SpreadSheet(datei.toFile()).getSheet(BLATT) : null;
        if (blatt == null) {
            return kunden;
        }
        Object[][] z = blatt.getDataRange().getValues();
        for (int i = 1; i < z.length; i++) {
            Map<String, String> k = new LinkedHashMap<>();
            for (int c = 0; c < z[0].length && c < z[i].length; c++) {
                String kennung = z[0][c] instanceof String kopf ? RechnungOds.kennung(kopf) : null;
                String wert = RechnungOds.anzeige(z[i][c]);
                if (kennung != null && wert != null) {
                    k.put(kennung, wert);
                }
            }
            if (k.containsKey("BT-44")) {
                kunden.add(k);
            }
        }
        return kunden;
    }

    public static void schreibe(List<Map<String, String>> kunden, Path datei) throws IOException {
        Style text = new Style();
        text.setDataStyle("@"); // PLZ, Telefon und Leitweg-ID bleiben Text
        Style kopf = new Style();
        kopf.setBold(true);
        Sheet blatt = new Sheet(BLATT, kunden.size() + 20, SPALTEN.length);
        for (int c = 0; c < SPALTEN.length; c++) {
            blatt.setDefaultColumnCellStyle(c, text);
            blatt.setColumnWidth(c, 45.0);
        }
        blatt.getRange(0, 0, 1, SPALTEN.length).setValues((Object[]) SPALTEN);
        blatt.getRange(0, 0, 1, SPALTEN.length).setStyle(kopf);
        for (int i = 0; i < kunden.size(); i++) {
            for (int c = 0; c < SPALTEN.length; c++) {
                String wert = kunden.get(i).get(FELDER.get(c));
                if (wert != null) {
                    blatt.getRange(i + 1, c).setValue(wert);
                }
            }
        }
        SpreadSheet ss = new SpreadSheet();
        ss.appendSheet(blatt);
        if (datei.getParent() != null) {
            Files.createDirectories(datei.getParent());
        }
        ss.save(datei.toFile());
    }
}
