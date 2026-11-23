package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ARTEN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.DUENN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.assertKeinGueltigesNeunerfensterOhne00;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.fehlalarmZaehlung;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.fehlalarmZahl;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.hatErweitertesWeitKontextwort;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.uzAk1Texte;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.weitFolgenReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import de.skillshield.core.recognizer.kv.KvTestHilfen.WeitFolge;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Ergaenzung des Fehlalarm-Pruefstands um das Fehlermodell "unsichtbares Zeichen steht direkt zwischen zwei
 * Buchstaben, ohne Luecke" (Grenze 14a; Plan 9a, Restluecke (a), kv-trenner-und-umbruch T-6). Ein unsichtbares
 * Zeichen zwischen zwei Buchstaben trennt keine Einzelbuchstaben, also darf eine Folge wie
 * {@code L ZWSP A. N. R.} kein Weit-Kontextwort ergeben: Das Produkt liefert dort genau die Fundliste der
 * eingefrorenen Kopie ({@code fehlalarmZahl} ist 0, in Texten ohne Weit-Kontextwort gilt volle Gleichheit). Der
 * Pruefstand selbst ({@code KvTrennerFehlalarmTest},
 * {@code KvTestHilfen}) bleibt unveraendert; dieser Test nutzt nur seine Hilfen. Meldungen nennen nur Seed, Iteration
 * und Zaehler.
 */
class KvTrennerUzFehlalarmTest {

    /** Seed des kleinen Zufallslaufs (siebenstellig, nur dieser Test). */
    static final long SEED = 6061307L;
    static final int ANZAHL = 20_000;
    private static final String PLATZ = "#";
    private static final String[] VORLAGEN = {"L" + ZWSP + "A. N. R. #", "L" + ZWSP + "A  N  R #",
        "I" + ZWSP + "K. N. R. #", "I" + ZWSP + "K  N  R #", "L" + ZWSP + "A\tN\tR #", "L. A" + ZWSP + "N. R. #",
        "L. A. N" + ZWSP + "R. #", "I. K" + ZWSP + "N. R. #", "I. K. N" + ZWSP + "R. #",
        "A. R. Z" + ZWSP + "T. N. R. #", "I. K. N. U" + ZWSP + "M. M. E. R. #"};

    /** Die Zahlvarianten wie in AK-15: gueltige LANR, gueltige IK, Nullfolge, ohne Zahl. */
    private static List<String> zahlen() {
        return List.of(LANR_GUELTIG, IK_GUELTIG, "0".repeat(9), "");
    }

    /**
     * Die Fundliste nach der Zaehlung des Pruefstands: Vorbedingung kein Weit-Kontextwort der Art, dann
     * {@code fehlalarmZahl == 0} (volle Gleichheit mit der Kopie).
     */
    private static int pruefe(Art a, String text) {
        assertKeinGueltigesNeunerfensterOhne00(text, "Waechter " + a.name);
        assertFalse(hatErweitertesWeitKontextwort(weitFolgenReferenz(text), a), "Weit-Kontextwort " + a.name);
        List<Finding> neu = a.erkenner.detect(text);
        List<Finding> alt = a.standLuecken.detect(text);
        assertEquals(0, fehlalarmZahl(a, text, neu, alt), a.name + " zusaetzlicher oder abweichender Fund");
        assertEquals(alt, neu, a.name + " Fundliste weicht von der Kopie ab");
        return alt.size();
    }

    @Test
    void uzZwischenZweiBuchstaben_festeFaelle_keinZusaetzlicherOderAbweichenderFund() {
        int paare = 0;
        int mitAltFund = 0;
        for (String vorlage : VORLAGEN) {
            for (String zahl : zahlen()) {
                for (Art a : ARTEN) {
                    paare++;
                    mitAltFund += pruefe(a, vorlage.replace(PLATZ, zahl)) > 0 ? 1 : 0;
                }
            }
        }
        // Gemessen: 88 Paare (11 Vorlagen mal 4 Zahlvarianten mal 2 Arten), 6 davon mit altem Fund (I ZWSP K).
        assertTrue(paare >= 60, "Paare " + paare);
        assertTrue(mitAltFund >= 4, "Paare mit altem Fund " + mitAltFund);
    }

    @Test
    void uzZwischenZweiBuchstaben_jedesUnsichtbareZeichen_keinZusaetzlicherOderAbweichenderFund() {
        int paare = 0;
        for (String uz : uzAk1Texte()) {
            for (String vorlage : VORLAGEN) {
                for (String zahl : zahlen()) {
                    for (Art a : ARTEN) {
                        paare++;
                        pruefe(a, vorlage.replace(String.valueOf(ZWSP), uz).replace(PLATZ, zahl));
                    }
                }
            }
        }
        assertTrue(paare >= 450, "Paare " + paare);
    }

    // ------------------------------------------------------------------ kleiner Zufallslauf

    private static final String[] RAUSCH = {"der", "die", "und", "Nr", "Nummer", "Arzt", "Praxis", "Rechnung", "Termin",
        "Beispiel", "vom", "Bericht", "hat", "Teil", "Anlage", "Herr", "Frau"};
    private static final String[] NORMAL_KW = {"LANR", "LANR:", "IK", "IK:", "Arztnummer", "IK-Nr.", "Lanr"};
    private static final String[] WOERTER = {"LANR", "IKNR", "ARZTNR", "IKNUMMER", "LANRNR", "ARZTNUMMER"};
    private static final String[] LUECKEN = {". ", "  ", "\t", " ", ".  ", "   ", "-", ".", "" + NBSP, ". " + DUENN};
    private static final String[] TRENNER = {" ", ", ", "\n", ": ", ". ", "; ", "\r\n"};
    private static final String POOL = "LANRIKZTUME";

    /** Zaehler eines Laufs. */
    private static final class Zaehler {
        int texte;
        int neuGezogen;
        int nahKontextwort;
        int mitAltFund;
        int mitNormalemKw;
        int mitErweiterterLuecke;
    }

    private static String nummer(Random r) {
        String[] nummern = {LANR_GUELTIG, IK_GUELTIG, LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, IK_UNGUELTIG_KOMPLEMENT,
            LAENGE_7, "0".repeat(9), "0".repeat(7)};
        return nummern[r.nextInt(nummern.length)];
    }

    private static char buchstabe(Random r, char c) {
        return r.nextBoolean() ? Character.toLowerCase(c) : c;
    }

    /** Ein Beinahe-Kontextwort: zufaellige Luecken, an einer Stelle nur ein unsichtbares Zeichen. */
    private static String nah(Random r, String[] uz) {
        String w = WOERTER[r.nextInt(WOERTER.length)];
        int bruch = r.nextInt(w.length() - 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            if (i > 0) {
                b.append(i - 1 == bruch ? uz[r.nextInt(uz.length)] : LUECKEN[r.nextInt(LUECKEN.length)]);
            }
            b.append(buchstabe(r, w.charAt(i)));
        }
        return b.toString();
    }

    private static String einzelFolge(Random r, String[] uz) {
        int laenge = 1 + r.nextInt(6);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < laenge; i++) {
            if (i > 0) {
                b.append(LUECKEN[r.nextInt(LUECKEN.length)]);
            }
            b.append(buchstabe(r, POOL.charAt(r.nextInt(POOL.length()))));
            if (r.nextInt(8) == 0) {
                b.append(uz[r.nextInt(uz.length)]);
            }
        }
        return b.toString();
    }

    private static String text(Random r, String[] uz, Zaehler z) {
        int bloecke = 3 + r.nextInt(7);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bloecke; i++) {
            if (i > 0) {
                b.append(TRENNER[r.nextInt(TRENNER.length)]);
            }
            int w = r.nextInt(100);
            if (w < 30) {
                b.append(nah(r, uz));
                z.nahKontextwort++;
            } else if (w < 50) {
                b.append(einzelFolge(r, uz));
            } else if (w < 65) {
                b.append(RAUSCH[r.nextInt(RAUSCH.length)]);
            } else if (w < 75) {
                b.append(NORMAL_KW[r.nextInt(NORMAL_KW.length)]);
                z.mitNormalemKw++;
            } else {
                b.append(nummer(r));
            }
        }
        return b.toString();
    }

    private static boolean hatWeitKontextwort(String text) {
        List<WeitFolge> folgen = weitFolgenReferenz(text);
        return hatErweitertesWeitKontextwort(folgen, ARTEN.get(0))
                || hatErweitertesWeitKontextwort(folgen, ARTEN.get(1));
    }

    /**
     * Kleiner Zufallslauf (eigener Seed): Beinahe-Kontextwoerter, in denen an einer Stelle nur ein unsichtbares Zeichen
     * statt einer Luecke steht, neben Einzelbuchstabenfolgen, Rauschwoertern, normalen Kontextwoertern und Nummern.
     * Texte, in denen die Referenz ein Weit-Kontextwort mit erweiterter Luecke findet, werden neu gezogen. Je Text und
     * Art: 0 zusaetzliche oder abweichende Funde ({@code fehlalarmZaehlung}), volle Gleichheit mit der Kopie.
     */
    @Test
    void zufallslauf_uzZwischenZweiBuchstaben_keinZusaetzlicherOderAbweichenderFund() {
        Random r = new Random(SEED);
        String[] uz = uzAk1Texte();
        Zaehler z = new Zaehler();
        int unerklaert = 0;
        for (int it = 0; it < ANZAHL; it++) {
            String text;
            do {
                text = text(r, uz, z);
                if (hatWeitKontextwort(text)) {
                    z.neuGezogen++;
                } else {
                    break;
                }
            } while (true);
            z.texte++;
            assertKeinGueltigesNeunerfensterOhne00(text, "Waechter Seed " + SEED + " Iteration " + it);
            for (Art a : ARTEN) {
                List<Finding> neu = a.erkenner.detect(text);
                List<Finding> alt = a.standLuecken.detect(text);
                int[] zaehlung = fehlalarmZaehlung(a, text, neu, alt);
                unerklaert += zaehlung[0];
                assertEquals(0, zaehlung[0], "Seed " + SEED + " Iteration " + it + " " + a.name + ": Fehlalarm");
                assertEquals(alt, neu, "Seed " + SEED + " Iteration " + it + " " + a.name + ": Abweichung");
                z.mitAltFund += alt.isEmpty() ? 0 : 1;
            }
            boolean erweitert = false;
            for (WeitFolge f : weitFolgenReferenz(text)) {
                erweitert |= f.erweitert() && f.buchstaben() >= 3;
            }
            z.mitErweiterterLuecke += erweitert ? 1 : 0;
        }
        assertEquals(0, unerklaert, "Seed " + SEED + ": Fehlalarme");
        String m = "Seed " + SEED + " Zaehler: neuGezogen " + z.neuGezogen + ", nah " + z.nahKontextwort
                + ", mitAltFund " + z.mitAltFund + ", normalesKw " + z.mitNormalemKw + ", erweitert "
                + z.mitErweiterterLuecke;
        assertEquals(ANZAHL, z.texte, m);
        // Gemessen (fester Seed): neuGezogen 1 045, nah 38 061, mitAltFund 4 648, normalesKw 12 511,
        // erweitert 16 924; die Untergrenzen liegen mehr als 30 % darunter.
        assertTrue(z.neuGezogen >= 700, m);
        assertTrue(z.nahKontextwort >= 26_000, m);
        assertTrue(z.mitAltFund >= 3_200, m);
        assertTrue(z.mitNormalemKw >= 8_000, m);
        assertTrue(z.mitErweiterterLuecke >= 11_000, m);
    }

    // ------------------------------------------------------------------ Selbsttests

    private static Recognizer fehlalarm(Art a) {
        return KvTestHilfen.kuenstlicherNeuErkenner(a.standLuecken, a.typ, a.quelle, KvTestHilfen.FEHLALARM_INITIALE);
    }

    @Test
    void selbsttest_kuenstlicherFehlalarmHinterUzZwischenBuchstaben_wirdGemeldet() {
        for (String vorlage : VORLAGEN) {
            for (Art a : ARTEN) {
                String text = vorlage.replace(PLATZ, a.gueltig);
                List<Finding> alt = a.standLuecken.detect(text);
                assertEquals(0, fehlalarmZahl(a, text, a.erkenner.detect(text), alt), a.name + " Produkt");
                int gemeldet = fehlalarmZahl(a, text, fehlalarm(a).detect(text), alt);
                // ein Fund der Kopie (normales Kontextwort I ZWSP K) deckt die Stelle schon ab: dort kein Zusatzfund
                boolean gemeldetErwartet = vorlage.contains(". #") && alt.isEmpty();
                assertEquals(gemeldetErwartet ? 1 : 0, gemeldet, a.name + " kuenstlicher Fehlalarm");
            }
        }
    }

    @Test
    void selbsttest_generator_ziehtBeinaheKontextwoerterMitUnsichtbaremZeichenOhneLuecke() {
        Random r = new Random(SEED);
        String[] uz = uzAk1Texte();
        int ohneLuecke = 0;
        for (int i = 0; i < 200; i++) {
            String n = nah(r, uz);
            boolean enthaeltUz = false;
            for (String u : uz) {
                enthaeltUz |= n.contains(u);
            }
            ohneLuecke += enthaeltUz ? 1 : 0;
        }
        assertEquals(200, ohneLuecke, "jedes Beinahe-Kontextwort enthaelt ein unsichtbares Zeichen");
    }
}
