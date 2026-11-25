package de.skillshield.core;

import java.time.LocalDate;
import java.util.Random;

/**
 * Zufallsgenerator fuer erfundene Datumsangaben aller Formen aus D-2 (Feature datum-und-kontextvorrang, T-3). Alle
 * Werte entstehen zur Laufzeit aus dem uebergebenen {@link Random}; es steht keine Zahl als Literal im Code. Ob ein
 * Wert ein Datum ist, entscheidet beim Erzeugen allein {@link DatumReferenz} (Ablehnungsverfahren), nie der
 * Erkenner. Gemeinsam benutzt von {@link DatumFuzzMengen} (Menge 1i) und {@code DatumFuzzTest}.
 */
final class DatumFormen {

    /** Form (a): Tag.Monat.Jahr, Tag und Monat ein- oder zweistellig, vierstelliges Jahr. */
    static final int PUNKT = 0;
    /** Form (b): Tag.Monat.JJ mit zweistelligem Jahr. */
    static final int PUNKT_ZWEI = 1;
    /** Form (c): Tag/Monat/Jahr. */
    static final int SCHRAEG = 2;
    /** Form (c): Tag-Monat-Jahr. */
    static final int STRICH = 3;
    /** Form (d): Jahr-Monat-Tag (ISO). */
    static final int ISO = 4;
    /** Form (e): Zeitraum aus zwei Daten der Form (a), durch ein Minuszeichen verbunden. */
    static final int ZEITRAUM = 5;
    /** Zahl der Formen. */
    static final int ANZAHL = 6;

    /** Namen der Formen in der Reihenfolge der Konstanten. */
    static final String[] NAMEN = {"Punkt vierstellig", "Punkt zweistellig", "Schraegstrich", "Bindestrich", "ISO",
        "Zeitraum"};

    private static final int TAGE_BIS_ZEITRAUMENDE = 400;
    private static final int ANZAHL_MAENGEL = 7;

    private DatumFormen() {
    }

    /** Die Zahl, auf Wunsch mit fuehrender Null auf zwei Stellen aufgefuellt (nur einstellige Zahlen). */
    static String zahl(int wert, boolean mitNull) {
        return mitNull && wert < 10 ? "0" + wert : String.valueOf(wert);
    }

    private static String jahr4(int jahr) {
        String s = String.valueOf(jahr);
        return "0".repeat(Math.max(0, 4 - s.length())) + s;
    }

    private static LocalDate zufallstag(Random r) {
        return LocalDate.of(DatumReferenz.JAHR_MIN, 1, 1).plusDays(r.nextInt(
                SynthetischeDaten.KALENDERTAGE_1900_BIS_2099));
    }

    private static String punkt(Random r, LocalDate d) {
        return zahl(d.getDayOfMonth(), r.nextBoolean()) + "." + zahl(d.getMonthValue(), r.nextBoolean()) + "."
                + d.getYear();
    }

    /** Ein gueltiges Datum der Form {@code form}, nach {@link DatumReferenz} ein Datum (Zeitraum: zwei Daten). */
    static String gueltig(Random r, int form) {
        LocalDate d = zufallstag(r);
        return switch (form) {
            case PUNKT -> punkt(r, d);
            case PUNKT_ZWEI -> gueltigZweistellig(r);
            case SCHRAEG -> zahl(d.getDayOfMonth(), r.nextBoolean()) + "/" + zahl(d.getMonthValue(), r.nextBoolean())
                    + "/" + d.getYear();
            case STRICH -> zahl(d.getDayOfMonth(), r.nextBoolean()) + "-" + zahl(d.getMonthValue(), r.nextBoolean())
                    + "-" + d.getYear();
            case ISO -> d.getYear() + "-" + zahl(d.getMonthValue(), true) + "-" + zahl(d.getDayOfMonth(), true);
            case ZEITRAUM -> {
                LocalDate ende = d.plusDays(r.nextInt(TAGE_BIS_ZEITRAUMENDE));
                yield punkt(r, d) + "-" + punkt(r, ende.getYear() <= DatumReferenz.JAHR_MAX ? ende : d);
            }
            default -> throw new IllegalArgumentException("Form " + form);
        };
    }

    /** Ein gueltiger Zeitraum, in dem Tag und Monat beider Daten immer zweistellig geschrieben sind. */
    static String zeitraumZweistellig(Random r) {
        LocalDate d = zufallstag(r);
        LocalDate ende = d.plusDays(r.nextInt(TAGE_BIS_ZEITRAUMENDE));
        LocalDate zweites = ende.getYear() <= DatumReferenz.JAHR_MAX ? ende : d;
        return zahl(d.getDayOfMonth(), true) + "." + zahl(d.getMonthValue(), true) + "." + d.getYear() + "-"
                + zahl(zweites.getDayOfMonth(), true) + "." + zahl(zweites.getMonthValue(), true) + "."
                + zweites.getYear();
    }

    private static String gueltigZweistellig(Random r) {
        while (true) {
            int tag = 1 + r.nextInt(31);
            int monat = 1 + r.nextInt(12);
            int jj = r.nextInt(100);
            if (DatumReferenz.kalenderGueltigZweistellig(tag, monat, jj)) {
                return zahl(tag, r.nextBoolean()) + "." + zahl(monat, r.nextBoolean()) + "." + zahl(jj, true);
            }
        }
    }

    /** Ein Wert mit genau einem Mangel: Tag oder Monat nicht im Kalender, 30. Februar, 31. im 30-Tage-Monat, Jahr. */
    private static int[] mangelhaft(Random r) {
        int[] w = {1 + r.nextInt(28), 1 + r.nextInt(12), DatumReferenz.JAHR_MIN + r.nextInt(200)};
        switch (r.nextInt(ANZAHL_MAENGEL)) {
            case 0 -> w[0] = 32 + r.nextInt(68);
            case 1 -> w[0] = 0;
            case 2 -> w[1] = 13 + r.nextInt(87);
            case 3 -> w[1] = 0;
            case 4 -> {
                w[0] = 29 + r.nextInt(3);
                w[1] = 2;
                while (w[0] == 29 && w[2] % 4 == 0 && w[2] != 1900) {
                    w[2] = DatumReferenz.JAHR_MIN + r.nextInt(200);
                }
            }
            case 5 -> {
                w[0] = 31;
                int[] kurz = {4, 6, 9, 11};
                w[1] = kurz[r.nextInt(kurz.length)];
            }
            default -> w[2] = r.nextBoolean() ? r.nextInt(DatumReferenz.JAHR_MIN)
                    : DatumReferenz.JAHR_MAX + 1 + r.nextInt(7900);
        }
        return w;
    }

    private static String mangelhaftPunkt(Random r, String trenner) {
        while (true) {
            int[] w = mangelhaft(r);
            if (!DatumReferenz.kalenderGueltig(w[0], w[1], w[2])) {
                return zahl(w[0], r.nextBoolean()) + trenner + zahl(w[1], r.nextBoolean()) + trenner + jahr4(w[2]);
            }
        }
    }

    /** Ein Wert der Form {@code form}, der kein Datum ist (Kalender oder Jahresbereich verletzt). */
    static String ungueltig(Random r, int form) {
        return switch (form) {
            case PUNKT -> mangelhaftPunkt(r, ".");
            case PUNKT_ZWEI -> ungueltigZweistellig(r);
            case SCHRAEG -> mangelhaftPunkt(r, "/");
            case STRICH -> mangelhaftPunkt(r, "-");
            case ISO -> ungueltigIso(r);
            case ZEITRAUM -> r.nextBoolean() ? mangelhaftPunkt(r, ".") + "-" + punkt(r, zufallstag(r))
                    : punkt(r, zufallstag(r)) + "-" + mangelhaftPunkt(r, ".");
            default -> throw new IllegalArgumentException("Form " + form);
        };
    }

    private static String ungueltigZweistellig(Random r) {
        while (true) {
            int tag = r.nextInt(100);
            int monat = r.nextInt(100);
            int jj = r.nextInt(100);
            boolean nahe = r.nextBoolean();
            if (nahe) {
                tag = 28 + r.nextInt(5);
                monat = 1 + r.nextInt(12);
            }
            if (!DatumReferenz.kalenderGueltigZweistellig(tag, monat, jj)) {
                return zahl(tag, true) + "." + zahl(monat, true) + "." + zahl(jj, true);
            }
        }
    }

    private static String ungueltigIso(Random r) {
        while (true) {
            int[] w = mangelhaft(r);
            if (!DatumReferenz.kalenderGueltig(w[0], w[1], w[2])) {
                return jahr4(w[2]) + "-" + zahl(w[1], true) + "-" + zahl(w[0], true);
            }
        }
    }

    private static final int ANZAHL_KEINE_FORM = 8;

    /** Ein Wert, der kein Datum im Sinne des Features ist, obwohl er Datumsform nahekommt (D-2, AK-3). */
    static String keinDatum(Random r) {
        LocalDate d = zufallstag(r);
        String tag = zahl(d.getDayOfMonth(), true);
        String monat = zahl(d.getMonthValue(), true);
        String jj = zahl(d.getYear() % 100, true);
        return switch (r.nextInt(ANZAHL_KEINE_FORM)) {
            case 0 -> tag + monat + d.getYear();
            case 1 -> tag + " " + monat + " " + d.getYear();
            case 2 -> tag + "." + monat + "-" + d.getYear();
            case 3 -> d.getYear() + "." + monat + "." + tag;
            case 4 -> d.getYear() + "/" + monat + "/" + tag;
            case 5 -> tag + "/" + monat + "/" + jj;
            case 6 -> tag + "-" + monat + "-" + jj;
            default -> tag + "." + monat + "." + d.getYear() + r.nextInt(10);
        };
    }

    /** Eine Uhrzeit mit Doppelpunkt oder Punkt (zwei plus zwei Ziffern). */
    static String uhrzeit(Random r, boolean doppelpunkt) {
        return zahl(r.nextInt(24), true) + (doppelpunkt ? ":" : ".") + zahl(r.nextInt(60), true);
    }
}
