package de.skillshield.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Menge 1i des Features datum-und-kontextvorrang (T-3, Architektur Kap. 7: {@code erzeugeKvI}): deterministisch
 * erzeugte Texte mit Datumssegmenten. Bausteine: Kontextwoerter aller vier Arten (auch nachgestellt, in
 * Sperrschrift und mit Fuellwoertern), echte synthetische Nummern ({@link SynthetischeKvNummern}, auch mit
 * Trennern), Datumsangaben aller Formen (gueltig, ungueltig, Nicht-Datumsformen), Zeitraeume, Uhrzeiten,
 * Aufzaehlungen, Trenner, Zeilenumbrueche und Bestandstypen (IP, Pfad, URL, E-Mail) in der Naehe, damit auch die
 * Konstellationen entstehen, in denen ein Datum bisher mit einem Nachbarfund zusammengelegt war. Es entsteht
 * keine Ziffernfolge ab sechs Ziffern ausser aus {@link SynthetischeKvNummern} und {@link DatumFormen}.
 *
 * <p>Die Menge steht in einer eigenen Klasse, weil die Datei {@code RoundtripFuzzTest} (Seeds, Siegel der Teile 1 bis
 * 1h) unveraendert bleibt. Der Seed ist fest und von allen Seeds dort verschieden.
 */
final class DatumFuzzMengen {

    /** Startwert der Menge 1i; verschieden von allen Startwerten in {@code RoundtripFuzzTest}. */
    static final long SEED_TEXTE_KV_I = 20261201L;

    /** Zahl der Texte der Menge 1i. */
    static final int ANZAHL_TEXTE = 60_000;

    /** Zahl der strukturierten Texte am Anfang der Menge; der Rest sind dichte Texte. */
    static final int ANZAHL_STRUKTURIERT = 20_000;

    private static final List<String> KONTEXT = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR", "Versichertennummer",
            "Arztnummer", "Betriebsstaettennummer", "Institutionskennzeichen", "L A N R", "lanr", "Bsnr");

    private static final List<String> NACHGESTELLT = List.of("(BSNR)", "(LANR)", "(IK)", "(KVNR)", "BSNR", "LANR");

    private static final List<String> TRENNER_KONTEXT = List.of(" ", " ", " ", ": ", ":", " - ", " (", "\n", " = ",
            "  ");

    private static final List<String> VERBINDER = List.of(" ", " ", ", ", "; ", "/", "-", "\n", " und ", ": ", "",
            ".", "_", " / ", " - ", ",");

    private static final List<String> FUELLWOERTER = List.of("Datum", "vom", "am", "Leistung", "Rechnung", "geb.",
            "gueltig ab", "bis", "von", "Praxis", "Beispielfall");

    private static final List<String> SATZ_TRENNER = List.of("\n", " ", "\n\n", ". ", "\n", ", ");

    private static final List<String> UHRZEIT_ANHANG = List.of(" 08:30", " 8.30", " 1234", " 1", " 12", ", 08:30",
            " um 08:30", " 08.30");

    private static final String[] NUMMERN_FEST = {SynthetischeKvNummern.LANR_GUELTIG,
        SynthetischeKvNummern.LANR_GUELTIG_ZWEI, SynthetischeKvNummern.IK_GUELTIG,
        SynthetischeKvNummern.IK_GUELTIG_ZWEI, SynthetischeKvNummern.KVNR_GUELTIG,
        SynthetischeKvNummern.KVNR_GUELTIG_ZWEI, SynthetischeKvNummern.BSNR_BEKANNT,
        SynthetischeKvNummern.BSNR_BEKANNT_ZWEI, SynthetischeKvNummern.BSNR_LANR_GUELTIG,
        SynthetischeKvNummern.LANR_TRENNER_LEERZEICHEN, SynthetischeKvNummern.IK_TRENNER_BINDESTRICH,
        SynthetischeKvNummern.IK_TRENNER_PUNKT, SynthetischeKvNummern.LANR_TRENNER_SCHRAEGSTRICH,
        SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, SynthetischeKvNummern.BSNR_UNBEKANNT,
        SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN};

    private DatumFuzzMengen() {
    }

    private static <T> T wahl(Random r, List<T> liste) {
        return liste.get(r.nextInt(liste.size()));
    }

    private static String nummer(Random r) {
        return switch (r.nextInt(8)) {
            case 0 -> SynthetischeKvNummern.bildeLanr(r);
            case 1 -> SynthetischeKvNummern.bildeIk(r);
            case 2 -> SynthetischeKvNummern.bildeBsnr(r);
            case 3 -> SynthetischeKvNummern.bildeBsnrUnbekannt(r);
            case 4 -> SynthetischeKvNummern.fremdKvnr(r, r.nextBoolean());
            default -> NUMMERN_FEST[r.nextInt(NUMMERN_FEST.length)];
        };
    }

    private static String datum(Random r) {
        int art = r.nextInt(10);
        int form = r.nextInt(DatumFormen.ANZAHL);
        if (art < 6) {
            return DatumFormen.gueltig(r, form);
        }
        if (art < 8) {
            return DatumFormen.ungueltig(r, form);
        }
        if (art < 9) {
            return DatumFormen.keinDatum(r);
        }
        return DatumFormen.gueltig(r, DatumFormen.PUNKT) + wahl(r, UHRZEIT_ANHANG);
    }

    private static String ip(Random r) {
        return r.nextBoolean() ? "192.0.2." + (1 + r.nextInt(250)) : "198.51.100." + (1 + r.nextInt(250));
    }

    private static String bestand(Random r, String datum) {
        return switch (r.nextInt(6)) {
            case 0 -> ip(r);
            case 1 -> "C:\\Berichte\\" + datum + "\\a.txt";
            case 2 -> "/opt/beispiel/" + datum + "/a.txt";
            case 3 -> "https://intranet.invalid/" + datum + "?x=" + r.nextInt(100);
            case 4 -> "erika.beispiel@example.invalid";
            default -> "~erika/" + datum + "/a.txt";
        };
    }

    private static String kontext(Random r) {
        return wahl(r, KONTEXT) + wahl(r, TRENNER_KONTEXT);
    }

    /** Ein Teil des Textes; die Auswahl ist gewichtet auf Datum neben Nummern und Nachbarfunden. */
    private static String teil(Random r) {
        String d1 = datum(r);
        String d2 = datum(r);
        return switch (r.nextInt(16)) {
            case 0 -> kontext(r) + d1;
            case 1 -> kontext(r) + nummer(r);
            case 2 -> kontext(r) + d1 + wahl(r, VERBINDER) + nummer(r);
            case 3 -> kontext(r) + nummer(r) + wahl(r, VERBINDER) + d1;
            case 4 -> kontext(r) + d1 + wahl(r, VERBINDER) + d2 + wahl(r, VERBINDER) + nummer(r);
            case 5 -> kontext(r) + nummer(r) + wahl(r, VERBINDER) + d1 + wahl(r, VERBINDER) + nummer(r);
            case 6 -> d1 + " " + wahl(r, NACHGESTELLT);
            case 7 -> nummer(r) + wahl(r, VERBINDER) + d1 + " " + wahl(r, NACHGESTELLT);
            case 8 -> kontext(r) + DatumFormen.gueltig(r, DatumFormen.ZEITRAUM) + wahl(r, VERBINDER) + nummer(r);
            case 9 -> kontext(r) + d1 + wahl(r, VERBINDER) + bestand(r, d2);
            case 10 -> kontext(r) + nummer(r) + wahl(r, VERBINDER) + bestand(r, d1);
            case 11 -> wahl(r, FUELLWOERTER) + " " + d1 + wahl(r, VERBINDER) + wahl(r, FUELLWOERTER);
            case 12 -> bestand(r, d1) + wahl(r, VERBINDER) + kontext(r) + d2;
            case 13 -> kontext(r) + wahl(r, FUELLWOERTER) + " " + d1 + wahl(r, VERBINDER) + nummer(r);
            case 14 -> kontext(r) + d1 + wahl(r, VERBINDER) + d2 + wahl(r, VERBINDER) + d1;
            default -> kontext(r) + nummer(r) + wahl(r, VERBINDER) + nummer(r) + wahl(r, VERBINDER) + d1;
        };
    }

    private static String text(Random r) {
        StringBuilder sb = new StringBuilder();
        int teile = 1 + r.nextInt(6);
        for (int i = 0; i < teile; i++) {
            sb.append(teil(r));
            if (i + 1 < teile) {
                sb.append(wahl(r, SATZ_TRENNER));
            }
        }
        return sb.toString();
    }

    private static final List<String> DICHT_VERBINDER = List.of(" ", " ", "", "/", "-", ".", ", ", ":", "\n", "_",
            " (", ") ");

    private static final List<String> DICHT_WOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer");

    private static final String[] DICHT_FEST = {SynthetischeKvNummern.LANR_GUELTIG, SynthetischeKvNummern.IK_GUELTIG,
        SynthetischeKvNummern.KVNR_GUELTIG, SynthetischeKvNummern.BSNR_BEKANNT, "A", "B", "Z", "C:/x/",
        "https://a.invalid/", "/opt/a/", "~e/", "e@example.invalid", "1", "12", "1234", "08:30"};

    /**
     * Ein dichter Text ohne Satzbau: zwei bis sechs Bausteine (Kontextwort, Datum, Nummer, Buchstabe, Pfad-, URL-,
     * E-Mail- oder IP-Anfang, kurze Ziffernbloecke) mit Trennern oder ohne Trenner dazwischen. Er erzeugt die
     * Konstellationen, in denen ein Datum mit einem Nachbarfund zusammengelegt oder von ihm angeschnitten ist.
     */
    private static String dicht(Random r) {
        StringBuilder sb = new StringBuilder();
        int teile = 2 + r.nextInt(5);
        for (int t = 0; t < teile; t++) {
            int art = r.nextInt(4);
            if (art == 0) {
                sb.append(wahl(r, DICHT_WOERTER));
            } else if (art == 1) {
                sb.append(DatumFormen.gueltig(r, r.nextInt(DatumFormen.ANZAHL)));
            } else if (r.nextInt(8) == 0) {
                sb.append(ip(r));
            } else {
                sb.append(DICHT_FEST[r.nextInt(DICHT_FEST.length)]);
            }
            sb.append(wahl(r, DICHT_VERBINDER));
        }
        return sb.toString();
    }

    /**
     * Die Texte der Menge 1i; gleicher Startwert ergibt dieselbe Folge. Die ersten {@link #ANZAHL_STRUKTURIERT} Texte
     * sind strukturiert (Saetze mit Kontext, Nummern und Daten), die uebrigen dicht ({@link #dicht}).
     */
    static List<String> erzeugeKvI(long seed) {
        Random r = new Random(seed);
        List<String> texte = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            texte.add(i < ANZAHL_STRUKTURIERT ? text(r) : dicht(r));
        }
        return List.copyOf(texte);
    }
}
