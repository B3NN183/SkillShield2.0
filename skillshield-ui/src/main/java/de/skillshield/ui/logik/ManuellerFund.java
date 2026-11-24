package de.skillshield.ui.logik;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Manuelles Hinzufügen einer markierten Stelle als Fund nach D-7 bis D-9 und Architektur-Plan Kap. 3.4 samt
 * "Nachtrag zu T-3", als reine Funktion ohne Zustand: Sie liest nur ihre Argumente und liefert die neue
 * Zeilenliste, ohne etwas zu verändern.
 *
 * <p>Regeln: Ein Surrogatpaar am Rand der Markierung wird ganz einbezogen; eine leere oder nur aus Leerraum
 * bestehende Markierung wird abgelehnt. Schneidet die markierte Stelle bestehende Funde (auch abgewählte), ersetzt
 * ein einziger neuer Fund die Vereinigung aller beteiligten Bereiche. Jedes weitere zeichengleiche Vorkommen
 * (von links nach rechts, nicht überlappend, ohne Wortgrenzen) wird so behandelt: (a) schneidet es keinen Fund,
 * wird es ein neuer Fund; (b) liegt es vollständig in angewählten Funden, ist es schon geschützt und nichts ändert
 * sich; (c) sonst (es schneidet einen abgewählten Fund oder ragt aus einem angewählten heraus) entsteht ein neuer
 * Fund über die Vereinigung aus dem Vorkommen und allen Funden, die es schneidet. Neue Funde der Fälle (c) und der
 * markierten Stelle, die sich berühren oder überlappen, werden zu einem verschmolzen. Danach steht kein Vorkommen
 * der markierten Zeichenfolge mehr ganz oder teilweise im Klartext. Alle neuen Funde haben den Typ {@code TEXT},
 * Konfidenz 1,0 und sind angewählt; die übrigen Zeilen behalten ihren Auswahlzustand. Die Ergebnisliste ist nach
 * Start sortiert und überlappungsfrei, sofern der Bestand es war (so liefert ihn der Kern). Der Aufwand ist linear
 * in Text und Fundzahl.
 *
 * <p>Kein Klartext in Ausnahmen: Geprüft werden nur Zahlen.
 */
final class ManuellerFund {

    private ManuellerFund() {
    }

    /**
     * Ergebnis des Hinzufügens. Die Platzhalter der Zeilen sind noch nicht berechnet ({@code null}); das geschieht
     * beim Festschreiben in der Sitzung aus dem Live-Ergebnis des Kerns.
     *
     * @param zeilen            alle Zeilen nach dem Hinzufügen, nach Start sortiert; bei Ablehnung der unveränderte
     *                          Bestand
     * @param hinzugefuegt      Zahl der Stellen, die neu geschützt wurden (die markierte Stelle und jedes weitere
     *                          Vorkommen der Fälle a und c)
     * @param bereitsGeschuetzt Zahl der weiteren Vorkommen, die schon vollständig in angewählten Funden lagen
     * @param ersetzt           Zahl der bestehenden Funde, die in einem neuen Fund aufgegangen sind
     * @param abgelehnt         {@code true} bei leerer oder nur aus Leerraum bestehender Markierung
     */
    record Ergebnis(List<FundZeile> zeilen, int hinzugefuegt, int bereitsGeschuetzt, int ersetzt,
                    boolean abgelehnt) {

        Ergebnis {
            zeilen = List.copyOf(zeilen);
        }

        /** Nur Zahlen, nie Zeilen oder Text. */
        @Override
        public String toString() {
            return "ManuellerFund.Ergebnis[zeilen=" + zeilen.size() + ", hinzugefuegt=" + hinzugefuegt
                    + ", bereitsGeschuetzt=" + bereitsGeschuetzt + ", ersetzt=" + ersetzt
                    + ", abgelehnt=" + abgelehnt + "]";
        }
    }

    /**
     * Arbeitseintrag der Berechnung: ein bestehender Fund ({@code zeile != null}) oder ein neuer Bereich.
     * Verschmelzbar sind nur neue Bereiche der markierten Stelle und des Falls (c).
     */
    private static final class Eintrag {
        private final int start;
        private final int ende;
        private final FundZeile zeile;
        private final boolean verschmelzbar;

        Eintrag(int start, int ende, FundZeile zeile, boolean verschmelzbar) {
            this.start = start;
            this.ende = ende;
            this.zeile = zeile;
            this.verschmelzbar = verschmelzbar;
        }

        int start() {
            return start;
        }

        int ende() {
            return ende;
        }

        FundZeile zeile() {
            return zeile;
        }

        boolean verschmelzbar() {
            return verschmelzbar;
        }

        boolean angewaehlt() {
            return zeile == null || zeile.angewaehlt();
        }

        /** Nur Zahlen, nie Text. */
        @Override
        public String toString() {
            return "Eintrag[" + start + ", " + ende + "]";
        }
    }

    /**
     * Fügt die Markierung {@code [start, ende)} und alle weiteren Vorkommen ihres Textes als Funde hinzu.
     *
     * @param text    Text, auf den sich die Positionen beziehen
     * @param bestand bestehende Zeilen, nach Start sortiert und überlappungsfrei
     * @param start   Beginn der Markierung (einschließlich)
     * @param ende    Ende der Markierung (ausschließlich)
     * @throws IndexOutOfBoundsException wenn die Markierung nicht im Text liegt (Meldung nur mit Zahlen)
     */
    static Ergebnis hinzufuegen(String text, List<FundZeile> bestand, int start, int ende) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(bestand, "bestand");
        Objects.checkFromToIndex(start, ende, text.length());
        if (start == ende) {
            return abgelehnt(bestand);
        }
        int von = start;
        int bis = ende;
        if (trenntPaarVor(text, von)) {
            von--;
        }
        if (trenntPaarVor(text, bis)) {
            bis++;
        }
        String wert = text.substring(von, bis);
        if (Leerraum.istLeer(wert)) {
            return abgelehnt(bestand);
        }

        // Markierte Stelle: bestehende Funde, die sie schneiden, gehen in einem neuen Fund auf.
        List<Eintrag> stand = new ArrayList<>(bestand.size() + 1);
        int ersetzt = 0;
        int neuVon = von;
        int neuBis = bis;
        for (FundZeile zeile : bestand) {
            Finding fund = zeile.fund();
            if (fund.start() < bis && fund.end() > von) {
                ersetzt++;
                neuVon = Math.min(neuVon, fund.start());
                neuBis = Math.max(neuBis, fund.end());
            } else {
                stand.add(new Eintrag(fund.start(), fund.end(), zeile, false));
            }
        }
        stand.add(new Eintrag(neuVon, neuBis, null, true));
        stand.sort(Comparator.comparingInt(Eintrag::start));

        // Weitere Vorkommen von links nach rechts. "aus" ist der fertige Teil links, "stand" der noch nicht
        // betrachtete Rest; beide sind nach Start sortiert, der Zeiger geht nur vorwärts.
        List<Eintrag> aus = new ArrayList<>(stand.size());
        int zeiger = 0;
        int stellen = 1;
        int geschuetzt = 0;
        int treffer = text.indexOf(wert);
        while (treffer >= 0) {
            int trefferEnde = treffer + wert.length();
            if (treffer != von && !trenntPaar(text, treffer, trefferEnde)) {
                while (zeiger < stand.size() && stand.get(zeiger).ende() <= treffer) {
                    aus.add(stand.get(zeiger++));
                }
                int vornAb = aus.size();
                while (vornAb > 0 && aus.get(vornAb - 1).ende() > treffer) {
                    vornAb--;
                }
                int hintenAb = zeiger;
                while (zeiger < stand.size() && stand.get(zeiger).start() < trefferEnde) {
                    zeiger++;
                }
                List<Eintrag> vorn = aus.subList(vornAb, aus.size());
                List<Eintrag> hinten = stand.subList(hintenAb, zeiger);
                if (vorn.isEmpty() && hinten.isEmpty()) {
                    aus.add(new Eintrag(treffer, trefferEnde, null, false));
                    stellen++;
                } else if (ueberdeckt(treffer, trefferEnde, vorn, hinten)) {
                    geschuetzt++;
                    aus.addAll(hinten);
                } else {
                    int uVon = treffer;
                    int uBis = trefferEnde;
                    for (List<Eintrag> teil : List.of(vorn, hinten)) {
                        for (Eintrag e : teil) {
                            if (e.zeile() != null) {
                                ersetzt++;
                            }
                            uVon = Math.min(uVon, e.start());
                            uBis = Math.max(uBis, e.ende());
                        }
                    }
                    vorn.clear();
                    if (!aus.isEmpty() && aus.get(aus.size() - 1).verschmelzbar()
                            && aus.get(aus.size() - 1).ende() >= uVon) {
                        uVon = Math.min(uVon, aus.remove(aus.size() - 1).start());
                    }
                    if (zeiger < stand.size() && stand.get(zeiger).verschmelzbar()
                            && stand.get(zeiger).start() <= uBis) {
                        uBis = Math.max(uBis, stand.get(zeiger++).ende());
                    }
                    aus.add(new Eintrag(uVon, uBis, null, true));
                    stellen++;
                }
            }
            treffer = text.indexOf(wert, trefferEnde);
        }
        aus.addAll(stand.subList(zeiger, stand.size()));

        List<FundZeile> alle = new ArrayList<>(aus.size());
        for (Eintrag e : aus) {
            if (e.zeile() != null) {
                alle.add(e.zeile());
            } else {
                alle.add(new FundZeile(Finding.manual(FindingType.TEXT, text.substring(e.start(), e.ende()),
                        e.start(), e.ende()), true, null));
            }
        }
        return new Ergebnis(alle, stellen, geschuetzt, ersetzt, false);
    }

    /**
     * {@code true}, wenn jedes Zeichen von {@code [von, bis)} in einem angewählten Eintrag liegt. Beide Listen
     * zusammen sind sortiert und überlappungsfrei.
     */
    private static boolean ueberdeckt(int von, int bis, List<Eintrag> vorn, List<Eintrag> hinten) {
        int position = von;
        for (List<Eintrag> teil : List.of(vorn, hinten)) {
            for (Eintrag e : teil) {
                if (!e.angewaehlt() || e.start() > position) {
                    return false;
                }
                position = Math.max(position, e.ende());
            }
        }
        return position >= bis;
    }

    private static Ergebnis abgelehnt(List<FundZeile> bestand) {
        return new Ergebnis(bestand, 0, 0, 0, true);
    }

    /** {@code true}, wenn die Grenze vor {@code index} ein Surrogatpaar trennt. */
    private static boolean trenntPaarVor(String text, int index) {
        return index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1)) && Character.isLowSurrogate(text.charAt(index));
    }

    private static boolean trenntPaar(String text, int von, int bis) {
        return trenntPaarVor(text, von) || trenntPaarVor(text, bis);
    }
}
