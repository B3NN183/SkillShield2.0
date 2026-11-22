package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest zur Umbruchzaehlung (Feature kv-trenner-und-umbruch, Task T-3, AK-24 (b), D-1): Texte aus
 * Kontextwort, Trenner, einer zufaellig gezogenen Folge aus CR, LF, unsichtbaren Zeichen (die sechs aus AK-1 und
 * U+FE00), Leerzeichen und U+0085, optional einer nummerierten Liste {@code 1.} und einer Nummer. Das Orakel ist
 * unabhaengig vom Produkt: Die Zahl der Umbrueche der Folge liefert allein {@link KvTestHilfen#umbruchZahlReferenz}
 * (ein Umbruch fuer CR, unsichtbare Zeichen, LF, sonst je Zeichen einer); daraus folgt der erwartete Fund nach den
 * Regeln der Anforderung. Es gibt keinen Aufruf von Scanner, Erkenner oder eingefrorener Kopie bei der Bildung der
 * Erwartung. Jeder Text geht durch {@link KvTestHilfen#scan} (Waechter, Invarianten, strenge Differenzpruefung).
 *
 * <p>Drei Kontexte, je 1 000 Texte mit eigenem festem Seed, die Art wechselt je Iteration (bei {@code I K} nur IK):
 * <b>normales Kontextwort</b> (0 Umbrueche: direkt, Deckel der Art; 1: 0,80; ab 2: kein Fund), <b>gesperrtes
 * Kontextwort ab drei Buchstaben</b> ({@code L A N R}, {@code I K N R}: 0 und 1 Umbruch 0,80, ab 2 kein Fund) und
 * <b>{@code I K}</b> (0 Umbrueche 0,50, sonst kein Fund). Die Nummer ist gueltig (Deckel), ungueltig (0,50) oder
 * hat eine abweichende Laenge (0,40). Die nummerierte Liste steht nur hinter einer Folge mit mindestens einem
 * Umbruch (dann ist sie das erste Zeichen ihrer Zeile) und aendert die Erwartung nicht. Die Zaehler belegen, dass
 * jede Klasse und {@code CR UZ+ LF} haeufig vorkommen. Meldungen nennen nur Seed, Iteration und Zaehler.
 *
 * <p>Testdatenregel: Nummern kommen aus {@code bilde...} (Praefix {@code 00}) oder den Konstanten von
 * {@code SynthetischeKvNummern}; der Waechter sichert jeden Text.
 */
class KvUmbruchEigenschaftTest {

    private static final long SEED_NORMAL = 20261301L;
    private static final long SEED_SPERR = 20261302L;
    private static final long SEED_IK_ZWEI = 20261303L;
    private static final int TEXTE = 1_000;
    private static final double ZWEI = 0.50;

    /** Die sechs unsichtbaren Zeichen aus AK-1 und der Variantenwaehler U+FE00 (gemeinsam in KvTestHilfen). */
    private static final char[] UZ = UZ_AK1;
    private static final String[] TRENNER = {":", ": ", " ", " : ", "="};
    private static final String[] VORFELD = {"", "", "Rechnung ", "Praxis "};
    private static final String[] NACHFELD = {"", "", ".", " Ende"};
    private static final String[] LISTE = {"1. ", "2. ", "12. "};

    private enum Kontext {
        NORMAL, SPERR, IK_ZWEI
    }

    /** Ein erzeugter Text samt Erwartung (Start und Wert der Nummer, Konfidenz; {@code konfidenz < 0}: kein Fund). */
    private record Probe(String text, int start, String nummer, double konfidenz, int umbrueche) {
    }

    private static final class Zaehler {
        final Map<String, Integer> z = new TreeMap<>();

        void zaehle(String schluessel) {
            z.merge(schluessel, 1, Integer::sum);
        }

        int get(String schluessel) {
            return z.getOrDefault(schluessel, 0);
        }

        void mindestens(String schluessel, int min) {
            assertTrue(get(schluessel) >= min, "Zaehler " + schluessel + " zu klein: " + get(schluessel));
        }
    }

    // ------------------------------------------------------------------ Generator

    private static <T> T wahl(Random r, T[] werte) {
        return werte[r.nextInt(werte.length)];
    }

    /** Die Folge aus zufaellig gezogenen Teilen: CR, LF, unsichtbares Zeichen, Leerzeichen, U+0085, CR UZ+ LF. */
    private static String folge(Random r) {
        StringBuilder f = new StringBuilder();
        int teile = r.nextInt(6);
        for (int i = 0; i < teile; i++) {
            int w = r.nextInt(100);
            if (w < 8) {
                f.append('\r');
            } else if (w < 16) {
                f.append('\n');
            } else if (w < 50) {
                f.append(UZ[r.nextInt(UZ.length)]);
            } else if (w < 72) {
                f.append(' ');
            } else if (w < 75) {
                f.append(NEL);
            } else {
                f.append('\r');
                int uz = 1 + r.nextInt(3);
                for (int k = 0; k < uz; k++) {
                    f.append(UZ[r.nextInt(UZ.length)]);
                }
                f.append('\n');
            }
        }
        return f.toString();
    }

    private static String kontextwort(Random r, Art art, Kontext k) {
        return switch (k) {
            case NORMAL -> wahl(r, art.kws);
            case SPERR -> art.lanr ? "L A N R" : "I K N R";
            case IK_ZWEI -> "I K";
        };
    }

    /** Deckel der Konfidenz nach Zahl der Umbrueche; negativ: kein Fund (Orakel aus den Regeln der Anforderung). */
    private static double deckel(Kontext k, Art art, int umbrueche) {
        return switch (k) {
            case NORMAL -> umbrueche == 0 ? art.sicher : umbrueche == 1 ? WAHRSCHEINLICH : -1;
            case SPERR -> umbrueche <= 1 ? WAHRSCHEINLICH : -1;
            case IK_ZWEI -> umbrueche == 0 ? ZWEI : -1;
        };
    }

    private static Probe baue(Random r, Art art, Kontext k, Zaehler z) {
        StringBuilder sb = new StringBuilder(wahl(r, VORFELD));
        sb.append(kontextwort(r, art, k));
        String trenner = wahl(r, TRENNER);
        String folge = folge(r);
        sb.append(trenner).append(folge);
        int umbrueche = umbruchZahlReferenz(folge);
        boolean liste = umbrueche >= 1 && r.nextInt(3) == 0;
        if (liste) {
            sb.append(wahl(r, LISTE));
        }
        int w = r.nextInt(100);
        String nummer;
        double nummernKonf;
        if (w < 70) {
            nummer = art.bilde(r);
            nummernKonf = 1.0;
        } else if (w < 85) {
            nummer = art.ungueltig;
            nummernKonf = PZ_UNGUELTIG;
        } else {
            nummer = LAENGE_7;
            nummernKonf = LAENGE;
        }
        int start = sb.length();
        sb.append(nummer).append(wahl(r, NACHFELD));
        double obergrenze = deckel(k, art, umbrueche);
        double konfidenz = obergrenze < 0 ? -1 : Math.min(obergrenze, nummernKonf);
        z.zaehle("umbruch." + Math.min(umbrueche, 2));
        if (enthaeltCrUnsichtbarLf(folge)) {
            z.zaehle("crUzLf");
            z.zaehle("crUzLf.umbruch." + Math.min(umbrueche, 2));
        }
        if (liste) {
            z.zaehle("liste");
        }
        z.zaehle(konfidenz < 0 ? "keinFund" : "fund");
        z.zaehle("nummer." + (nummernKonf == 1.0 ? "gueltig" : nummernKonf == PZ_UNGUELTIG ? "ungueltig" : "laenge"));
        return new Probe(sb.toString(), start, nummer, konfidenz, umbrueche);
    }

    // ------------------------------------------------------------------ Pruefung

    private static String ort(long seed, int iteration) {
        return "Seed " + seed + " Iteration " + iteration;
    }

    private static void pruefe(Art art, Probe p, String ort) {
        List<Finding> ist = scan(art, p.text());
        if (p.konfidenz() < 0) {
            assertEquals(0, ist.size(), ort + " Anzahl Funde (" + p.umbrueche() + " Umbrueche)");
        } else {
            assertEquals(1, ist.size(), ort + " Anzahl Funde (" + p.umbrueche() + " Umbrueche)");
            Finding f = ist.get(0);
            assertEquals(p.start(), f.start(), ort + " Start");
            assertEquals(p.start() + p.nummer().length(), f.end(), ort + " Ende");
            assertTrue(p.nummer().equals(f.originalValue()), ort + " Wert");
            assertEquals(p.konfidenz(), f.confidence(), ort + " Konfidenz (" + p.umbrueche() + " Umbrueche)");
            assertEquals(ConfidenceLevel.of(p.konfidenz()), f.level(), ort + " Stufe");
            assertEquals(art.quelleNachNummer(p.nummer()), f.source(), ort + " Quelle");
        }
        assertEquals(ist, art.erkenner.detect(p.text()), ort + " Determinismus");
    }

    private static Zaehler eigenschaft(long seed, Kontext k) {
        Random r = new Random(seed);
        Zaehler z = new Zaehler();
        for (int it = 0; it < TEXTE; it++) {
            Art art = k == Kontext.IK_ZWEI || it % 2 != 0 ? IK_ART : LANR_ART;
            Art andere = art == LANR_ART ? IK_ART : LANR_ART;
            Probe p = baue(r, art, k, z);
            String ort = ort(seed, it);
            pruefe(art, p, ort);
            assertEquals(List.of(), scan(andere, p.text()), ort + " andere Art darf nichts finden");
        }
        return z;
    }

    /**
     * Mindestzahlen je Kontext (reine Vorkommens-Untergrenzen, mehr als 30 % unter den Messwerten des festen Seeds).
     * Messwerte der drei Kontexte (normal / gesperrt / I K): 0 Umbrueche 354 / 348 / 356, genau 1 Umbruch 333 / 326 /
     * 311, ab 2 Umbruechen 313 / 326 / 333; Texte mit CR UZ+ LF 459 / 454 / 464, davon mit genau 1 Umbruch 191 / 183 /
     * 166; mit nummerierter Liste 220 / 210 / 218; erwarteter Fund 687 / 674 / 356, kein Fund 313 / 326 / 644;
     * Nummern gueltig mindestens 693, ungueltig mindestens 141, abweichende Laenge mindestens 127.
     */
    private static void mindestzahlen(Zaehler z, int minFund) {
        z.mindestens("umbruch.0", 200);
        z.mindestens("umbruch.1", 200);
        z.mindestens("umbruch.2", 200);
        z.mindestens("crUzLf", 300);
        z.mindestens("crUzLf.umbruch.1", 100);
        z.mindestens("liste", 140);
        z.mindestens("fund", minFund);
        z.mindestens("keinFund", 200);
        z.mindestens("nummer.gueltig", 450);
        z.mindestens("nummer.ungueltig", 90);
        z.mindestens("nummer.laenge", 80);
    }

    // ------------------------------------------------------------------ AK-24 (b)

    @Test
    void ak24b_normalesKontextwort_befundFolgtDerUmbruchzaehlung() {
        mindestzahlen(eigenschaft(SEED_NORMAL, Kontext.NORMAL), 450);
    }

    @Test
    void ak24b_gesperrtesKontextwortAbDreiBuchstaben_befundFolgtDerUmbruchzaehlung() {
        mindestzahlen(eigenschaft(SEED_SPERR, Kontext.SPERR), 450);
    }

    @Test
    void ak24b_gesperrtesIkAusZweiBuchstaben_befundFolgtDerUmbruchzaehlung() {
        mindestzahlen(eigenschaft(SEED_IK_ZWEI, Kontext.IK_ZWEI), 240);
    }

    // ------------------------------------------------------------------ Selbsttests des Orakels

    @Test
    void referenz_zaehltDieFolgenAusDemPlan() {
        String uz = String.valueOf(ZWSP);
        String uzMehr = "" + ZWSP + BOM + SHY + LRM;
        assertEquals(0, umbruchZahlReferenz(""));
        assertEquals(0, umbruchZahlReferenz(uz + " " + NBSP + uzMehr));
        assertEquals(1, umbruchZahlReferenz("\r\n"));
        assertEquals(1, umbruchZahlReferenz("\r" + uz + "\n"));
        assertEquals(1, umbruchZahlReferenz("\r" + uzMehr + "\n"));
        assertEquals(1, umbruchZahlReferenz("\r" + zeichen(0xE0001) + "\n"));
        assertEquals(1, umbruchZahlReferenz("\r" + uz.repeat(1_000)));
        assertEquals(1, umbruchZahlReferenz("\n"));
        assertEquals(1, umbruchZahlReferenz("\r"));
        assertEquals(1, umbruchZahlReferenz(String.valueOf(NEL)));
        assertEquals(1, umbruchZahlReferenz(String.valueOf(LS)));
        assertEquals(1, umbruchZahlReferenz(String.valueOf(PS)));
        assertEquals(1, umbruchZahlReferenz(" \n" + uz + " "));
        assertEquals(2, umbruchZahlReferenz("\r" + uz + "\r"));
        assertEquals(2, umbruchZahlReferenz("\r" + uz + NEL));
        assertEquals(2, umbruchZahlReferenz("\n" + uz + "\n"));
        assertEquals(2, umbruchZahlReferenz("\n" + uz + "\r"));
        assertEquals(2, umbruchZahlReferenz("\r\n" + uz + "\n"));
        assertEquals(2, umbruchZahlReferenz("\r" + uz + "\n" + uz + "\n"));
        assertEquals(2, umbruchZahlReferenz("\r\r\n"));
        assertEquals(2, umbruchZahlReferenz("\r \n"));
        assertEquals(2, umbruchZahlReferenz("\r" + NBSP + "\n"));
        assertEquals(2, umbruchZahlReferenz("\r" + uz + "\n\r" + uz + "\n"));
        assertEquals(2, umbruchZahlReferenz("\r" + uz + "A\n"));
        assertEquals(2, umbruchZahlReferenz("\r" + AKUT + "\n"));
    }

    @Test
    void referenz_unterscheidetSichVonEinfachenZaehlweisen() {
        // CR UZ LF zaehlt eins (zaehlt man jedes CR und jedes LF, waeren es zwei), CR Leerzeichen LF zwei
        String crUzLf = "\r" + ZWSP + "\n";
        String crLeerLf = "\r \n";
        assertEquals(1, umbruchZahlReferenz(crUzLf));
        assertEquals(2, umbruchZahlReferenz(crLeerLf));
        assertTrue(enthaeltCrUnsichtbarLf(crUzLf));
        assertFalse(enthaeltCrUnsichtbarLf(crLeerLf));
    }

    /**
     * Die Tabelle {@link #deckel} (Orakel der Eigenschaftstests) stimmt an je einem festen Text pro Kontext, Art und
     * Umbruchzahl mit dem Befund des Produkts ueberein. Der Text wird ohne Zufall gebaut; die Umbruchzahl 0, 1, 2
     * entsteht aus keinem Umbruch, {@code CR ZWSP LF} und zweimal {@code CR ZWSP LF}.
     */
    @Test
    void erwartung_tabelleDerDeckelJeKontext_stimmtMitDemProduktAnFestenTextenUeberein() {
        String uz = String.valueOf(ZWSP);
        String[] folgen = {"", "\r" + uz + "\n", "\r" + uz + "\n\r" + uz + "\n"};
        for (Kontext k : Kontext.values()) {
            for (Art art : ARTEN) {
                if (k == Kontext.IK_ZWEI && art != IK_ART) {
                    continue;
                }
                for (int umbrueche = 0; umbrueche < folgen.length; umbrueche++) {
                    assertEquals(umbrueche, umbruchZahlReferenz(folgen[umbrueche]), "Folge " + umbrueche);
                    double erwartet = deckel(k, art, umbrueche);
                    String wort = switch (k) {
                        case NORMAL -> art.kw;
                        case SPERR -> art.lanr ? "L A N R" : "I K N R";
                        case IK_ZWEI -> "I K";
                    };
                    String vor = wort + ":" + folgen[umbrueche];
                    if (erwartet < 0) {
                        assertKein(art, vor + art.gueltig);
                    } else {
                        assertEin(art, vor, art.gueltig, "", erwartet, art.quelle);
                    }
                }
            }
        }
    }
}
