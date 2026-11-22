package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_DREI;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static de.skillshield.core.SynthetischeKvNummern.bildeIk;
import static de.skillshield.core.SynthetischeKvNummern.bildeLanr;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ARTEN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.DUENN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.EN_SPACE;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.GESCH_BINDESTRICH;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.HALBGEVIERT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.IK_ART;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.KW_IK;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.KW_LANR;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LANR_ART;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LS;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LUECKE_LEERRAUM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LUECKE_PUNKT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LUECKE_STRENG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LUECKE_TAB;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NEL;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.PS;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SCHMAL_NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SHY;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.UZ_AK1;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.assertKein;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.assertKeinGueltigesNeunerfensterOhne00;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.enthaeltCrUnsichtbarLf;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.fehlalarmZaehlung;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.fehlalarmZahl;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.gruppiert;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.hatErweitertesWeitKontextwort;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.istZiffer;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.neunerfenster;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.scan;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.verletztBestandsschutzOhneAusnahme;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.weitFolgenReferenz;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.weitKontextwoerterReferenz;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.zusaetzlicheFunde;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import de.skillshield.core.recognizer.kv.KvTestHilfen.WeitFolge;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Fehlalarm-Pruefstand fuer Teil 2 (Grenze 14c) des Features kv-trenner-und-umbruch (T-5, D-8, AK-15, AK-16). Er steht
 * vor jeder Produktaenderung von Teil 2 und wird in T-6 bis T-10 nicht mehr geaendert (Regel 8.10): Zeigt er dort
 * einen zusaetzlichen Fund, wird Teil 2 nicht durch Anpassen des Pruefstands, sondern nach D-8 entschieden.
 *
 * <p>Vergleichsbasis ist der eingefrorene Stand {@code KvStandLuecken} (Ende kv-kontext-luecken). In Texten ohne
 * Weit-Kontextwort der Art mit erweiterter Luecke gilt volle Gleichheit der Fundliste mit der Kopie (Start, Ende, Typ,
 * Konfidenz, Quelle, gleiche Anzahl; {@link KvTestHilfen#abweichendeFunde}). Ausgenommen sind Texte mit
 * {@code CR UZ+ LF} (dort wirkt Teil 1, Kap. 13 Hinweis 1 des Plans; die Zufallstexte setzen nie ein unsichtbares
 * Zeichen hinter ein CR). In Texten, in denen die unabhaengige Referenz ein Weit-Kontextwort der Art ab vier
 * Buchstaben mit erweiterter Luecke findet ({@link KvTestHilfen#weitFolgenReferenz},
 * {@link KvTestHilfen#hatErweitertesWeitKontextwort}), ist ein zusaetzlicher Fund nur zulaessig, wenn die Kopie ihn
 * auf der Wort-Vergleichsform liefert ({@link KvTestHilfen#wortVergleichsform}, {@link KvTestHilfen#fehlalarmZaehlung};
 * Plan 9a). Dieselbe Pruefung steckt in {@link KvTestHilfen#scan} und auf Scannerebene in
 * {@code KvStandLueckenDifferenzTest}.
 *
 * <p>Trennschaerfe: Die Selbsttests belegen mit einem kuenstlichen "Neu"-Erkenner
 * ({@link KvTestHilfen#kuenstlicherNeuErkenner}, ein Fund hinter {@code I. K. } bzw. hinter Initialen), dass AK-15,
 * AK-16 und die Pruefung in {@code scan} einen Fehlalarm wirklich melden, und dass die Ausnahmen nur dort greifen, wo
 * der Plan sie vorsieht. Alle Meldungen nennen nur Seed, Iteration und Zaehler, nie Text oder Ziffern.
 */
class KvTrennerFehlalarmTest {

    /** Seed des Zufallsvergleichs AK-16 (siebenstellig, nur dieser Test). */
    static final long SEED_AK16 = 4160721L;
    static final int ANZAHL_AK16 = 100_000;
    // Mindestzaehlungen AK-16 (Texte mit dem Merkmal): je hoechstens 70 % des gemessenen Istwerts, Messwerte am
    // Test ak16_zufallsvergleich_keinZusaetzlicherFund.
    static final int MIN_NEU_GEZOGEN = 1_900;
    static final int MIN_ALT_FUND = 32_000;
    static final int MIN_NORMALES_KW = 38_000;
    static final int MIN_INITIALEN = 35_000;
    static final int MIN_LANG_ERWEITERT = 43_000;
    static final int MIN_LANG_LUECKE = 26_000;
    static final int MIN_KURZ_ERWEITERT = 52_000;
    static final int MIN_BEINAHE = 10_000;
    static final int MIN_BEINAHE_KURZ = 2_500;
    static final int MIN_LISTE = 21_000;
    static final int MIN_SPALTE = 20_000;
    static final int MIN_FREMDE_SCHRIFT = 16_000;
    static final int MIN_IK_VOR_NUMMER = 14_000;
    /** Platzhalter fuer {@code <Zahl>} in den Vorlagen von AK-15. */
    private static final String PLATZ = "#";

    // ------------------------------------------------------------------ AK-15: fester Satz von Nicht-Faellen

    private static final String[] AK15_INITIALEN = {"I. K. #", "I. K.: #", "i. k. #", "I. K #", "I  K #", "I   K #",
        "I\tK #", "I.  K. #", "Dr. I. K. #", "Prof. I. K. #", "I. K.-Nr. #", "I. K. Mustermann #",
        "Prof. I. K. Mustermann, Rechnung #", "# (I. K.)", "#, I  K", "Dr. M. A. Beispiel #",
        "Dr. M. A. Beispiel, Rechnung #", "J. R. R. #", "J. R. R. Beispiel #"};
    private static final String[] AK15_AUFZAEHLUNGEN = {"a) b) c) #", "a. b. c. #", "A, B, C #", "Teil I K L #",
        "Anlage A  B  C #", "Punkte a) b) c) d) e) #", "A. B. C. D. #", "x y z #"};
    private static final String[] AK15_ABKUERZUNGEN = {"z. B. #", "u. a. #", "i. d. R. #", "d. h. #", "Dr. med. #",
        "e. V. #", "u. v. m. #", "i. V. m. #", "m. E. #", "z. T. #", "z. B. die Rechnung #", "i. d. R. Nr. #"};
    private static final String[] AK15_TABELLEN = {"A\tB\tC\tD #", "X  Y  Z #", "Ja  Nein  k. A. #",
        "Spalte L\tSpalte A #", "L\tA\tN\t#", "Name\tI\tK\t#", "A  B  C  D  E  F #"};
    private static final String[] AK15_MEHRFACH = {"Rechnung  Nummer  #", "Der  Arzt  hat  #  gesehen",
        "Der   Arzt   hat   #   gesehen", "Praxis  Nr  #", "Arzt  Nummer  #", "Rechnung Nr.  #"};
    private static final String[] AK15_FOLGEN = {"L A N #", "I K E A #", "L. A. N. #", "I K N #", "N. R. L. A. #",
        "R. N. A. L. #", "L  A  N #", "L\tA\tN #", "L. A. N #", "I. K. E. A. #", "I  K  E  A #", "I\tK\tN #",
        "N  R  L  A #", "A. L. A. N. R. #", "L. A. N. R. X #", "L. A. N. X. R. #", "L. A. N. R. L. A. N. R. #",
        "LA. NR. #", "L. ANR #", "Herr A. L. A. N. R. #", "I. K. N. #", "A. I. K. N. R. X #",
        "L    A    N    R #", "L\t\tA\t\tN\t\tR #", "L\t A\t N\t R #", "L, A, N, R #", "L; A; N; R #", "L/A/N/R #",
        "L_A_N_R #", "L: A: N: R #", "L .A .N .R #", "L.. A.. N.. R.. #", "L - A - N - R #", "I    K    N    R #"};
    /** Die nicht zulaessigen Trenner aus AK-17 (D-2): vier Leerzeichen, vier Leerraumzeichen gemischt, Umbruch usw. */
    private static final String[] AK15_NICHT_ZULAESSIG = {"L.    A.    N.    R. #", "L\nA\nN\nR #", "L.\tA.\tN.\tR #",
        "L" + NBSP + DUENN + SCHMAL_NBSP + " A" + NBSP + DUENN + SCHMAL_NBSP + " N" + NBSP + DUENN + SCHMAL_NBSP
            + " R #",
        "L. A.\nN. R. #", "L\r\nA N R #", "L " + HALBGEVIERT + " A " + HALBGEVIERT + " N " + HALBGEVIERT + " R #",
        "L" + EN_SPACE + "A" + EN_SPACE + "N" + EN_SPACE + "R #", "L\n\nA\n\nN\n\nR #", "L" + LS + "A" + LS + "N" + LS
            + "R #",
        "I.    K.    N.    R. #", "I\nK\nN\nR #", "I.\tK.\tN.\tR #", "L.     A.     N.     R. #"};
    /** Beinahe-Kontextwoerter (ein Buchstabe zu wenig, vertauscht oder zu viel) mit erweiterten Trennern. */
    private static final String[] AK15_BEINAHE = {"A. R. Z. T. N. #", "L. A. R. N. #", "I. K. N. U. M. M. E. #",
        "A. R. Z. T. N. U. M. M. E. #", "L.  A.  N. #", "L  A  R  N #", "I\tK\tN\tU\tM #", "L. A. N. R. N. #",
        "A. R. Z. T. N. N. R. #", "I. K. N. R. N. #"};
    /** Kontrollen: Bestandsfaelle, in denen die Kopie Funde liefert (die Pruefung laeuft nicht ins Leere). */
    private static final String[] AK15_KONTROLLEN = {"LANR #", "LANR: #", "IK: #", "I K #", "L A N R #", "I.K. #",
        "Arztnummer #", "LANR  #", "IK:  #", "LANR   #", "IK-Nr.  #", "Arztnummer:  #"};
    private static final String[][] AK15_GRUPPEN = {AK15_INITIALEN, AK15_AUFZAEHLUNGEN, AK15_ABKUERZUNGEN,
        AK15_TABELLEN, AK15_MEHRFACH, AK15_FOLGEN, AK15_NICHT_ZULAESSIG, AK15_BEINAHE, AK15_KONTROLLEN};

    /** Die vier Zahlvarianten je Vorlage: gueltige LANR, gueltige IK, Nullfolge, ohne Zahl. */
    private static List<String> zahlen() {
        return List.of(LANR_GUELTIG, IK_GUELTIG, "0".repeat(9), "");
    }

    /**
     * Alle Texte des AK-15-Satzes (Vorlage mal Zahlvariante), je Art zu pruefen. Vorlagen, in denen die Referenz ein
     * Weit-Kontextwort findet (zum Beispiel {@code L. A. N. R.} ohne Zahl), gehoeren nicht in diesen Satz: Sie sind
     * Faelle von AK-7 bis AK-12 und laufen in T-6.
     */
    static List<String> ak15Texte() {
        List<String> texte = new ArrayList<>();
        for (String[] gruppe : AK15_GRUPPEN) {
            for (String vorlage : gruppe) {
                for (String zahl : zahlen()) {
                    texte.add(vorlage.replace(PLATZ, zahl));
                }
            }
        }
        return texte;
    }

    /** Ergebnis eines AK-15-Laufs (Paare aus Text und Art). */
    record Ak15(int paare, int mitAltFund, int zusaetzlich, int paareMitZusatz) {
    }

    /**
     * Zaehlt auf jedem Text des Satzes und je Art die zusaetzlichen Funde von {@code neuErkenner} gegenueber der
     * Kopie. Vorbedingung je Text: Waechter, und die Referenz findet kein Weit-Kontextwort der Art mit erweiterter
     * Luecke (sonst wuerde der Satz nichts pruefen).
     */
    static Ak15 ak15Lauf(Function<Art, Recognizer> neuErkenner) {
        int paare = 0;
        int mitAlt = 0;
        int zusaetzlich = 0;
        int mitZusatz = 0;
        List<String> texte = ak15Texte();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            assertKeinGueltigesNeunerfensterOhne00(text, "AK-15 Text " + i);
            List<WeitFolge> folgen = weitFolgenReferenz(text);
            for (Art a : ARTEN) {
                assertFalse(hatErweitertesWeitKontextwort(folgen, a),
                        "AK-15 Text " + i + ": Weit-Kontextwort " + a.name);
                List<Finding> alt = a.standLuecken.detect(text);
                int extra = fehlalarmZahl(a, text, neuErkenner.apply(a).detect(text), alt);
                paare++;
                mitAlt += alt.isEmpty() ? 0 : 1;
                zusaetzlich += extra;
                mitZusatz += extra > 0 ? 1 : 0;
            }
        }
        return new Ak15(paare, mitAlt, zusaetzlich, mitZusatz);
    }

    /**
     * Gemessen (fester Satz): 976 Paare (122 Vorlagen mal 4 Zahlvarianten mal 2 Arten), davon 36 mit mindestens einem
     * alten Fund (Bestandsfaelle wie {@code I K <IK>}, {@code L A N R <LANR>}, {@code LANR <LANR>}). Die
     * Untergrenzen (680 und 25) halten mehr als 30 % Abstand.
     */
    @Test
    void ak15_festerSatzVonNichtFaellen_keinZusaetzlicherFund() {
        Ak15 e = ak15Lauf(a -> a.erkenner);
        assertEquals(0, e.zusaetzlich(), "AK-15: zusaetzliche Funde in " + e.paareMitZusatz() + " Paaren");
        assertTrue(e.paare() >= 680, "AK-15: Paare " + e.paare());
        assertTrue(e.mitAltFund() >= 25, "AK-15: Paare mit altem Fund " + e.mitAltFund());
    }

    /** Derselbe Satz durch {@code scan} (Waechter, beide Differenzen, Fehlalarm-Pruefung, Invarianten). */
    @Test
    void ak15_festerSatzVonNichtFaellen_durchScan() {
        for (String text : ak15Texte()) {
            for (Art a : ARTEN) {
                scan(a, text);
            }
        }
    }

    // ------------------------------------------------------------------ AK-16: Zufallsvergleich

    private static final String[] RAUSCH = {"der", "die", "und", "Nr", "Nummer", "Arzt", "Praxis", "Rechnung",
        "Termin", "Beispiel", "Mustermann", "vom", "Bericht", "hat", "gesehen", "am", "Teil", "Anlage", "Seite",
        "Herr", "Frau", "Bescheid", "Muster"};
    private static final String[] INITIALEN = {"I. K.", "Dr. M. A. Beispiel", "J. R. R.", "Prof. I. K. Mustermann",
        "A. B.", "H. J.", "Dr. L. A. Beispiel", "Prof. N. R. Mustermann", "z. B.", "u. a.", "i. d. R.", "Dr. med.",
        "I. K. Mustermann"};
    private static final String[] WOERTER_ALLE = {"lanr", "lanrnr", "arztnummer", "arztnr", "iknr", "iknummer",
        "institutionskennzeichen"};
    private static final String BUCHSTABEN_KW = "LANRIKZTUME";
    private static final char[] WS4 = {' ', NBSP, DUENN, SCHMAL_NBSP};
    private static final char[] STRENG = {' ', ' ', '.', '.', '-', NBSP, DUENN, SCHMAL_NBSP, GESCH_BINDESTRICH,
        HALBGEVIERT};
    /** Ideographisches Leerzeichen U+3000 (kein Leerraum der Sperrschrift). */
    private static final char IDEO_SPACE = (char) 0x3000;
    /** Vertikaltabulator (kein Leerraum der Sperrschrift). */
    private static final char VT = (char) 0x0B;
    /** Nicht-Luecken nach D-2: Folgen, die eine Einzelbuchstabenfolge beenden. */
    private static final String[] KEINE_LUECKE = {"    ", "     ", "\t\t", "\t ", " \t", " - ", "/", "_", ",", ": ",
        ", ", " .", "..", "; ", ".    ", "\n", "\r\n", ".\n", ".\t", String.valueOf(EN_SPACE),
        String.valueOf(IDEO_SPACE), String.valueOf(LS), String.valueOf(VT)};
    /** Seltene Trenner zwischen Bausteinen: Zeilen- und Absatztrenner, andere Leerraeume, Leerzeile, 4 Leerzeichen. */
    private static final String[] TRENNER_SELTEN = {String.valueOf(LS), String.valueOf(PS), String.valueOf(EN_SPACE),
        String.valueOf(IDEO_SPACE), String.valueOf(VT), "    ", "\n\n"};
    /** Lateinisch aussehende Buchstaben anderer Schriften: kyrillisch (A, E, K, M, H, O, P, C, T) und Vollbreite. */
    private static final char[] KYRILLISCH = {(char) 0x410, (char) 0x415, (char) 0x41A, (char) 0x41C, (char) 0x41D,
        (char) 0x41E, (char) 0x420, (char) 0x421, (char) 0x422};
    private static final String[] GRUPPENTRENNER = {" ", "-", ".", "/", String.valueOf(NBSP)};

    private static String leerraum(Random r, int anzahl) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < anzahl; i++) {
            sb.append(r.nextInt(3) == 0 ? WS4[r.nextInt(WS4.length)] : ' ');
        }
        return sb.toString();
    }

    /** Eine erweiterte Luecke nach D-2 (b), (c) oder (d). */
    private static String lueckeErweitert(Random r) {
        int art = r.nextInt(10);
        if (art < 4) {
            return leerraum(r, 2 + r.nextInt(2));
        }
        if (art < 8) {
            return "." + leerraum(r, 1 + r.nextInt(3));
        }
        return "\t";
    }

    /** Eine Luecke aus (a) bis (d) oder eine Nicht-Luecke. */
    private static String luecke(Random r) {
        int w = r.nextInt(100);
        if (w < 40) {
            return String.valueOf(STRENG[r.nextInt(STRENG.length)]);
        }
        if (w < 82) {
            return lueckeErweitert(r);
        }
        return KEINE_LUECKE[r.nextInt(KEINE_LUECKE.length)];
    }

    private static String uz(Random r) {
        return String.valueOf(UZ_AK1[r.nextInt(UZ_AK1.length)]);
    }

    /**
     * Mit Wahrscheinlichkeit 1/12 ein unsichtbares Zeichen an zufaelliger Stelle der Zeichenkette; enthaelt sie ein
     * CR, bleibt sie unveraendert (nie ein unsichtbares Zeichen direkt hinter einem CR, Filter {@code CR UZ+ LF}).
     */
    private static String mitUz(Random r, String s) {
        if (r.nextInt(12) != 0 || s.indexOf('\r') >= 0) {
            return s;
        }
        int pos = r.nextInt(s.length() + 1);
        return s.substring(0, pos) + uz(r) + s.substring(pos);
    }

    private static String buchstabe(Random r) {
        char c;
        int w = r.nextInt(100);
        if (w < 50) {
            c = BUCHSTABEN_KW.charAt(r.nextInt(BUCHSTABEN_KW.length()));
        } else if (w < 92) {
            c = (char) ('A' + r.nextInt(26));
        } else if (w < 94) {
            c = KYRILLISCH[r.nextInt(KYRILLISCH.length)];
        } else if (w < 96) {
            c = (char) (0xFF21 + r.nextInt(26));
        } else {
            c = r.nextBoolean() ? (char) 0xC4 : (char) 0xD6;
        }
        return String.valueOf(r.nextBoolean() ? Character.toLowerCase(c) : c);
    }

    /** Ein Buchstabe der Schriften Kyrillisch oder Vollbreite (lateinisch aussehend, aber nicht {@code A-Z})? */
    private static boolean fremdeSchrift(char c) {
        return Character.isLetter(c) && (c >= 0x400 && c <= 0x4FF || c >= 0xFF21 && c <= 0xFF5A);
    }

    /** Zwei bis sieben Einzelbuchstaben mit Luecken, unsichtbaren Zeichen und optionalem Schlusspunkt. */
    private static String folgeBaustein(Random r) {
        int n = 2 + r.nextInt(6);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            if (k > 0) {
                sb.append(mitUz(r, luecke(r)));
            }
            sb.append(buchstabe(r));
            if (r.nextInt(20) == 0) {
                sb.append(uz(r));
            }
        }
        if (r.nextInt(3) == 0) {
            sb.append('.');
        }
        return sb.toString();
    }

    /**
     * Beinahe-Kontextwort: ein Wort der Art ab vier Buchstaben, dem ein Buchstabe fehlt, einer zu viel ist, zwei
     * benachbarte vertauscht sind oder vor oder hinter dem ein Einzelbuchstabe steht; mit Luecken, davon die Haelfte
     * erweitert. In einem Zehntel der Faelle bleibt das Wort richtig (Koeder: solche Texte werden neu gezogen).
     */
    private static String beinaheBaustein(Random r) {
        StringBuilder w = new StringBuilder(WOERTER_ALLE[r.nextInt(WOERTER_ALLE.length)]);
        int art = r.nextInt(10);
        if (art < 3) {
            w.deleteCharAt(r.nextInt(w.length()));
        } else if (art < 5) {
            w.insert(r.nextInt(w.length() + 1), (char) ('a' + r.nextInt(26)));
        } else if (art < 7) {
            int pos = r.nextInt(w.length() - 1);
            char c = w.charAt(pos);
            w.setCharAt(pos, w.charAt(pos + 1));
            w.setCharAt(pos + 1, c);
        } else if (art == 8) {
            w.insert(0, (char) ('a' + r.nextInt(26)));
        } else if (art == 9) {
            w.append((char) ('a' + r.nextInt(26)));
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < w.length(); k++) {
            if (k > 0) {
                sb.append(mitUz(r, r.nextBoolean() ? lueckeErweitert(r) : luecke(r)));
            }
            sb.append(r.nextBoolean() ? Character.toUpperCase(w.charAt(k)) : w.charAt(k));
        }
        if (r.nextInt(3) == 0) {
            sb.append('.');
        }
        return sb.toString();
    }

    private static String nummer(Random r) {
        String[] laengen = {LAENGE_5, LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, LAENGE_12, LAENGE_13};
        String t = GRUPPENTRENNER[r.nextInt(GRUPPENTRENNER.length)];
        switch (r.nextInt(16)) {
            case 0:
                return LANR_GUELTIG;
            case 1:
                return LANR_GUELTIG_ZWEI;
            case 2:
                return LANR_GUELTIG_FACHGRUPPE;
            case 3:
                return IK_GUELTIG;
            case 4:
                return IK_GUELTIG_ZWEI;
            case 5:
                return IK_GUELTIG_DREI;
            case 6:
                return bildeLanr(r);
            case 7:
                return bildeIk(r);
            case 8:
                return LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
            case 9:
                return IK_UNGUELTIG_KOMPLEMENT;
            case 10:
                return gruppiert(LANR_GUELTIG, t);
            case 11:
                return gruppiert(IK_GUELTIG, t);
            case 12:
                return TELEFON_PROBE;
            case 13:
                return "00" + (1 + r.nextInt(99_999));
            default:
                return laengen[r.nextInt(laengen.length)];
        }
    }

    /** Eine Form des Zwei-Buchstaben-Worts mit erweiterten Trennern, davon die Haelfte mit Punkt, plus IK-Nummer. */
    private static String ikVorNummerBaustein(Random r) {
        String[] formen = {"I. K.", "I  K", "I\tK", "I.  K.", "i. k.", "I   K", "I" + NBSP + NBSP + "K", "I. K"};
        String[] seps = {" ", " ", ": ", "\n", "  "};
        String[] iks = {IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, bildeIk(r)};
        return formen[r.nextInt(formen.length)] + seps[r.nextInt(seps.length)] + iks[r.nextInt(iks.length)];
    }

    private static String normalesKwBaustein(Random r) {
        String[] kws = r.nextBoolean() ? KW_LANR : KW_IK;
        StringBuilder sb = new StringBuilder(kws[r.nextInt(kws.length)]);
        if (r.nextInt(3) == 0) {
            sb.append(':');
        }
        if (r.nextInt(100) < 75) {
            sb.append(r.nextInt(8) == 0 ? "\n" : " ").append(nummer(r));
        }
        return sb.toString();
    }

    private static String trenner(Random r) {
        int w = r.nextInt(100);
        if (w < 53) {
            return " ";
        }
        if (w < 61) {
            return "  ";
        }
        if (w < 67) {
            return "\t";
        }
        if (w < 75) {
            return "\n";
        }
        if (w < 79) {
            return "\r\n";
        }
        if (w < 82) {
            return "\r";
        }
        if (w < 84) {
            return String.valueOf(NEL);
        }
        if (w < 90) {
            return TRENNER_SELTEN[r.nextInt(TRENNER_SELTEN.length)];
        }
        if (w < 96) {
            return "";
        }
        return ", ";
    }

    /**
     * Listen: {@code a) b) c)} (zwei bis fuenf Aufzaehlungsbuchstaben, durch Leerzeichen oder Umbruch getrennt) oder
     * {@code 1. A. Muster} (Nummer, Einzelbuchstabe mit Punkt, Wort).
     */
    private static String listeBaustein(Random r) {
        StringBuilder sb = new StringBuilder();
        if (r.nextBoolean()) {
            int n = 2 + r.nextInt(4);
            for (int k = 0; k < n; k++) {
                sb.append(k > 0 ? (r.nextInt(4) == 0 ? "\n" : " ") : "").append(buchstabe(r)).append(')');
            }
        } else {
            sb.append(1 + r.nextInt(9)).append(". ").append(buchstabe(r)).append(". ")
                    .append(RAUSCH[r.nextInt(RAUSCH.length)]);
        }
        return sb.toString();
    }

    /** Eine Spalte aus Einzelbuchstaben ueber drei bis sechs Zeilen (LF oder CR LF, nie ein CR allein). */
    private static String spalteBaustein(Random r) {
        StringBuilder sb = new StringBuilder();
        int n = 3 + r.nextInt(4);
        String umbruch = r.nextInt(4) == 0 ? "\r\n" : "\n";
        for (int k = 0; k < n; k++) {
            sb.append(k > 0 ? umbruch : "").append(buchstabe(r));
        }
        return sb.toString();
    }

    private static final int FLAG_INITIALEN = 0;
    private static final int FLAG_NORMALES_KW = 1;
    private static final int FLAG_LISTE = 2;
    private static final int FLAG_SPALTE = 3;
    private static final int ANZAHL_FLAGS = 4;

    /** Haengt einen Baustein an; {@code flag} nach den Konstanten {@code FLAG_*} (Initialen, Kontextwort, ...). */
    private static void baustein(Random r, StringBuilder sb, boolean[] flag) {
        int w = r.nextInt(100);
        if (w < 18) {
            sb.append(RAUSCH[r.nextInt(RAUSCH.length)]);
        } else if (w < 27) {
            sb.append(normalesKwBaustein(r));
            flag[FLAG_NORMALES_KW] = true;
        } else if (w < 39) {
            sb.append(nummer(r));
        } else if (w < 47) {
            sb.append(INITIALEN[r.nextInt(INITIALEN.length)]);
            flag[FLAG_INITIALEN] = true;
        } else if (w < 59) {
            sb.append(folgeBaustein(r));
        } else if (w < 65) {
            sb.append(beinaheBaustein(r));
        } else if (w < 69) {
            sb.append(ikVorNummerBaustein(r));
        } else if (w < 75) {
            String[] satzzeichen = {".", ",", ";", ":", "(", ")", "!", "?", "-"};
            sb.append(satzzeichen[r.nextInt(satzzeichen.length)]);
        } else if (w < 81 && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\r')) {
            sb.append(uz(r));
        } else if (w < 85) {
            sb.append(listeBaustein(r));
            flag[FLAG_LISTE] = true;
        } else if (w < 89) {
            sb.append(spalteBaustein(r));
            flag[FLAG_SPALTE] = true;
        } else {
            sb.append(RAUSCH[r.nextInt(RAUSCH.length)]).append(' ').append(r.nextInt(10_000));
        }
    }

    /** Ein Zufallstext aus vier bis vierzehn Bausteinen, hoechstens etwa 300 Zeichen. */
    static String ziehe(Random r, boolean[] flag) {
        StringBuilder sb = new StringBuilder();
        int bloecke = 4 + r.nextInt(11);
        for (int b = 0; b < bloecke && sb.length() < 240; b++) {
            baustein(r, sb, flag);
            sb.append(trenner(r));
        }
        return sb.toString();
    }

    /** Seed des Selbsttest-Zufallslaufs mit Weit-Kontextwoertern (siebenstellig, nur dieser Test). */
    static final long SEED_WEIT = 5160722L;
    static final int ANZAHL_WEIT = 6_000;
    // Mindestzaehlungen des Selbsttest-Zufallslaufs (Istwerte siehe Javadoc des Tests).
    static final int MIN_WEIT_PAARE = 4_100;
    static final int MIN_WEIT_ERKLAERT = 5_600;

    /**
     * Ein Zufallstext mit einem Weit-Kontextwort (je Wort der Liste ab vier Buchstaben, mindestens eine erweiterte
     * Luecke, sonst strenge Luecken, optional unsichtbare Zeichen und Schlusspunkt), danach null bis drei Fuellwoerter
     * und eine Nummer (oder zwei), in einem Drittel der Faelle gefolgt von einem Zufallstext aus {@link #ziehe}.
     */
    static String weitText(Random r) {
        StringBuilder sb = new StringBuilder();
        if (r.nextInt(3) == 0) {
            sb.append(RAUSCH[r.nextInt(RAUSCH.length)]).append(' ');
        }
        String wort = WOERTER_ALLE[r.nextInt(WOERTER_ALLE.length)];
        int erweitert = 1 + r.nextInt(wort.length() - 1);
        for (int k = 0; k < wort.length(); k++) {
            if (k > 0) {
                String luecke = k == erweitert ? lueckeErweitert(r) : String.valueOf(STRENG[r.nextInt(STRENG.length)]);
                sb.append(mitUz(r, luecke));
            }
            sb.append(r.nextBoolean() ? Character.toUpperCase(wort.charAt(k)) : wort.charAt(k));
        }
        sb.append(r.nextInt(3) == 0 ? "." : "").append(r.nextInt(5) == 0 ? ":" : "");
        int fueller = r.nextInt(4);
        for (int k = 0; k < fueller; k++) {
            sb.append(' ').append(RAUSCH[r.nextInt(RAUSCH.length)]);
        }
        sb.append(r.nextInt(10) == 0 ? "\n" : r.nextInt(8) == 0 ? "\t" : " ").append(nummer(r));
        if (r.nextInt(3) == 0) {
            sb.append(", ").append(nummer(r));
        }
        if (r.nextInt(3) == 0) {
            sb.append(' ').append(ziehe(r, new boolean[ANZAHL_FLAGS]));
        }
        return sb.toString();
    }

    /** Zaehler eines AK-16-Laufs. */
    static final class Ak16 {
        int texte;
        int neuGezogen;
        int neuGezogenWaechter;
        int ersteIteration = -1;
        int zusaetzlich;
        int bestandVerletzt;
        int crUzLf;
        int mitAltFund;
        int mitInitialen;
        int mitNormalemKw;
        int langErweitert;
        int langLeerraum;
        int langPunkt;
        int langTab;
        int kurzErweitert;
        int beinahe;
        int beinaheKurz;
        int mitListe;
        int mitSpalte;
        int mitFremderSchrift;
        int ikVorNummer;

        /** Alle Zaehler fuer die Meldungen der Mindestzaehlungen (nur Zahlen). */
        String zaehler() {
            return "[texte " + texte + ", neuGezogen " + neuGezogen + ", neuGezogenWaechter " + neuGezogenWaechter
                    + ", mitAltFund " + mitAltFund + ", mitInitialen " + mitInitialen + ", mitNormalemKw "
                    + mitNormalemKw + ", langErweitert " + langErweitert + ", langLeerraum " + langLeerraum
                    + ", langPunkt " + langPunkt + ", langTab " + langTab + ", kurzErweitert " + kurzErweitert
                    + ", beinahe " + beinahe + ", beinaheKurz " + beinaheKurz + ", ikVorNummer " + ikVorNummer
                    + ", mitListe " + mitListe + ", mitSpalte " + mitSpalte + ", mitFremderSchrift "
                    + mitFremderSchrift + "]";
        }

        String zusammenfassung() {
            return "Seed " + SEED_AK16 + ": " + texte + " Texte, " + neuGezogen + " neu gezogen, " + zusaetzlich
                    + " zusaetzliche Funde (erste Iteration " + ersteIteration + ")";
        }
    }

    /** Loescht aus {@code lang} ein Zeichen und erhaelt {@code kurz}. */
    private static boolean loeschtZuGleich(String lang, String kurz) {
        if (lang.length() != kurz.length() + 1) {
            return false;
        }
        for (int i = 0; i < lang.length(); i++) {
            if ((lang.substring(0, i) + lang.substring(i + 1)).equals(kurz)) {
                return true;
            }
        }
        return false;
    }

    /** {@code s} ist ein Beinahe-Kontextwort: ein Zeichen fehlt, ist zu viel oder zwei Nachbarn sind vertauscht. */
    static boolean beinaheKontextwort(String s) {
        for (String w : WOERTER_ALLE) {
            if (s.equals(w)) {
                continue;
            }
            boolean vertauscht = false;
            if (s.length() == w.length()) {
                for (int i = 0; i + 1 < s.length() && !vertauscht; i++) {
                    String t = s.substring(0, i) + s.charAt(i + 1) + s.charAt(i) + s.substring(i + 2);
                    vertauscht = t.equals(w);
                }
            }
            if (vertauscht || loeschtZuGleich(w, s) || loeschtZuGleich(s, w)) {
                return true;
            }
        }
        return false;
    }

    /** Steht hinter dem Ende (nach Trennern, Doppelpunkt, Punkt, unsichtbaren Zeichen) eine Ziffer? */
    private static boolean nummerFolgt(String text, int ende) {
        int k = ende;
        while (k < text.length()) {
            char c = text.charAt(k);
            boolean ueberspringen = c == ' ' || c == '\t' || c == '.' || c == ':' || c == '-' || c == NBSP
                    || c == DUENN || c == SCHMAL_NBSP || KvTestHilfen.unsichtbar(c);
            if (!ueberspringen) {
                break;
            }
            k++;
        }
        return k < text.length() && istZiffer(text.charAt(k));
    }

    private static void zaehleMerkmale(Ak16 z, String text, List<WeitFolge> folgen) {
        boolean lang = false;
        boolean leer = false;
        boolean punkt = false;
        boolean tab = false;
        boolean kurz = false;
        boolean beinahe = false;
        boolean beinaheKurz = false;
        boolean ik = false;
        for (WeitFolge f : folgen) {
            if (!f.erweitert()) {
                continue;
            }
            if (f.buchstaben() >= 4) {
                lang = true;
                leer |= (f.luecken() & LUECKE_LEERRAUM) != 0;
                punkt |= (f.luecken() & LUECKE_PUNKT) != 0;
                tab |= (f.luecken() & LUECKE_TAB) != 0;
            } else {
                kurz = true;
            }
            boolean beinaheFolge = beinaheKontextwort(f.gefaltet());
            beinahe |= beinaheFolge && f.buchstaben() >= 4;
            beinaheKurz |= beinaheFolge && f.buchstaben() < 4;
            ik |= f.buchstaben() == 2 && f.gefaltet().equals("ik") && nummerFolgt(text, f.ende());
        }
        z.langErweitert += lang ? 1 : 0;
        z.langLeerraum += leer ? 1 : 0;
        z.langPunkt += punkt ? 1 : 0;
        z.langTab += tab ? 1 : 0;
        z.kurzErweitert += kurz ? 1 : 0;
        z.beinahe += beinahe ? 1 : 0;
        z.beinaheKurz += beinaheKurz ? 1 : 0;
        z.ikVorNummer += ik ? 1 : 0;
    }

    /** Enthaelt der Text einen Buchstaben kyrillischer oder vollbreiter Schrift? */
    private static boolean hatFremdeSchrift(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (fremdeSchrift(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zieht {@code anzahl} Texte ohne Weit-Kontextwort mit erweiterter Luecke (fuer keine der beiden Arten) und ohne
     * gueltiges Neunerfenster ohne Praefix 00 (Waechter als Ziehbedingung; andere Texte werden neu gezogen) und zaehlt
     * je Art die zusaetzlichen Funde von {@code neuErkenner} gegenueber der Kopie sowie die Verletzungen des strengen
     * Bestandsschutzes.
     */
    static Ak16 ak16Lauf(long seed, int anzahl, Function<Art, Recognizer> neuErkenner) {
        Random r = new Random(seed);
        Ak16 z = new Ak16();
        Recognizer[] neu = {neuErkenner.apply(LANR_ART), neuErkenner.apply(IK_ART)};
        int iteration = 0;
        while (z.texte < anzahl) {
            iteration++;
            boolean[] flag = new boolean[ANZAHL_FLAGS];
            String text = ziehe(r, flag);
            List<WeitFolge> folgen = weitFolgenReferenz(text);
            if (hatErweitertesWeitKontextwort(folgen, LANR_ART) || hatErweitertesWeitKontextwort(folgen, IK_ART)) {
                z.neuGezogen++;
                continue;
            }
            if (neunerfenster(text, "00")[1] != 0) {
                z.neuGezogenWaechter++;
                continue;
            }
            z.texte++;
            z.crUzLf += enthaeltCrUnsichtbarLf(text) ? 1 : 0;
            z.mitInitialen += flag[FLAG_INITIALEN] ? 1 : 0;
            z.mitListe += flag[FLAG_LISTE] ? 1 : 0;
            z.mitSpalte += flag[FLAG_SPALTE] ? 1 : 0;
            z.mitFremderSchrift += hatFremdeSchrift(text) ? 1 : 0;
            z.mitNormalemKw += flag[FLAG_NORMALES_KW] ? 1 : 0;
            zaehleMerkmale(z, text, folgen);
            boolean altFund = false;
            for (int k = 0; k < ARTEN.size(); k++) {
                List<Finding> alt = ARTEN.get(k).standLuecken.detect(text);
                List<Finding> neuFunde = neu[k].detect(text);
                altFund |= !alt.isEmpty();
                int extra = fehlalarmZahl(ARTEN.get(k), text, neuFunde, alt);
                if (extra > 0 && z.ersteIteration < 0) {
                    z.ersteIteration = iteration;
                }
                z.zusaetzlich += extra;
                z.bestandVerletzt += verletztBestandsschutzOhneAusnahme(alt, neuFunde) ? 1 : 0;
            }
            z.mitAltFund += altFund ? 1 : 0;
        }
        return z;
    }

    /**
     * Zufallsvergleich AK-16: 100 000 Texte, fester Seed, genau 0 zusaetzliche Funde gegenueber der Kopie. Gemessen
     * (siehe {@code Ak16}), Texte mit: altem Fund 46 818, Initialen 50 891, normalem Kontextwort mit Nummer 54 938,
     * Einzelbuchstabenfolge ab vier Buchstaben mit erweiterter Luecke 62 665 (davon mit Leerraumluecke 50 682, Punkt
     * 53 744, Tab 37 988), kurzer erweiterter Folge (zwei oder drei Buchstaben) 74 487, Beinahe-Kontextwort ab vier
     * Buchstaben mit erweiterter Luecke 14 457, kurzer Beinahe-Folge (drei Buchstaben) 3 683, {@code I. K.} mit
     * erweiterter Luecke vor einer Nummer 21 324, Listen-Baustein 30 988, Spalte aus Einzelbuchstaben 30 130,
     * Buchstaben kyrillischer oder vollbreiter Schrift 23 539; neu gezogen 2 834 (Weit-Kontextwort) und 38 (Waechter).
     * Die Untergrenzen halten mehr als 30 % Abstand. Die Pruefung ist die volle Gleichheit der Fundlisten
     * ({@link KvTestHilfen#fehlalarmZahl}).
     */
    @Test
    void ak16_zufallsvergleich_keinZusaetzlicherFund() {
        Ak16 z = ak16Lauf(SEED_AK16, ANZAHL_AK16, a -> a.erkenner);
        assertEquals(0, z.zusaetzlich, "AK-16 " + z.zusammenfassung());
        assertEquals(0, z.bestandVerletzt, "AK-16 Bestandsschutz verletzt, " + z.zusammenfassung());
        assertEquals(0, z.crUzLf, "AK-16: Text mit CR UZ+ LF gezogen, " + z.zusammenfassung());
        assertTrue(z.texte >= 100_000, "AK-16 Texte " + z.texte);
        String zaehler = z.zaehler();
        assertTrue(z.neuGezogen >= MIN_NEU_GEZOGEN, "AK-16 neu gezogen (Naehe zum Kontextwort) " + zaehler);
        assertTrue(z.mitAltFund >= MIN_ALT_FUND, "AK-16 Texte mit altem Fund " + zaehler);
        assertTrue(z.mitNormalemKw >= MIN_NORMALES_KW, "AK-16 Texte mit normalem Kontextwort " + zaehler);
        assertTrue(z.mitInitialen >= MIN_INITIALEN, "AK-16 Texte mit Initialen " + zaehler);
        assertTrue(z.langErweitert >= MIN_LANG_ERWEITERT, "AK-16 Folgen ab vier Buchstaben " + zaehler);
        assertTrue(z.langLeerraum >= MIN_LANG_LUECKE, "AK-16 Leerraumluecke " + zaehler);
        assertTrue(z.langPunkt >= MIN_LANG_LUECKE, "AK-16 Punktluecke " + zaehler);
        assertTrue(z.langTab >= MIN_LANG_LUECKE, "AK-16 Tabluecke " + zaehler);
        assertTrue(z.kurzErweitert >= MIN_KURZ_ERWEITERT, "AK-16 kurze erweiterte Folgen " + zaehler);
        assertTrue(z.beinahe >= MIN_BEINAHE, "AK-16 Beinahe-Kontextwoerter " + zaehler);
        assertTrue(z.beinaheKurz >= MIN_BEINAHE_KURZ, "AK-16 kurze Beinahe-Folgen " + zaehler);
        assertTrue(z.mitListe >= MIN_LISTE, "AK-16 Listen " + zaehler);
        assertTrue(z.mitSpalte >= MIN_SPALTE, "AK-16 Spalten aus Einzelbuchstaben " + zaehler);
        assertTrue(z.mitFremderSchrift >= MIN_FREMDE_SCHRIFT, "AK-16 andere Schriften " + zaehler);
        assertTrue(z.ikVorNummer >= MIN_IK_VOR_NUMMER, "AK-16 I. K. vor einer Nummer " + zaehler);
    }

    // ------------------------------------------------------------------ Selbsttests: Zufallstexte

    @Test
    void selbsttest_generator_istDeterministischUndSetztNieEinUzHinterEinCr() {
        Random r1 = new Random(SEED_AK16);
        Random r2 = new Random(SEED_AK16);
        for (int i = 0; i < 3_000; i++) {
            String t1 = ziehe(r1, new boolean[ANZAHL_FLAGS]);
            String t2 = ziehe(r2, new boolean[ANZAHL_FLAGS]);
            assertTrue(t1.equals(t2), "Generator nicht deterministisch, Iteration " + i);
            assertFalse(enthaeltCrUnsichtbarLf(t1), "CR UZ+ LF im Zufallstext, Iteration " + i);
            for (int k = t1.indexOf('\r'); k >= 0; k = t1.indexOf('\r', k + 1)) {
                assertTrue(k + 1 >= t1.length() || !KvTestHilfen.unsichtbar(t1.charAt(k + 1)),
                        "unsichtbares Zeichen hinter CR, Iteration " + i);
            }
        }
    }

    @Test
    void selbsttest_beinaheKontextwort_fehltEinsZuVielVertauscht() {
        assertTrue(beinaheKontextwort("lan"));
        assertTrue(beinaheKontextwort("lanrx"));
        assertTrue(beinaheKontextwort("xlanr"));
        assertTrue(beinaheKontextwort("lnar"));
        assertTrue(beinaheKontextwort("arztnumer"));
        assertFalse(beinaheKontextwort("lanr"), "das Wort selbst ist kein Beinahe-Wort");
        assertFalse(beinaheKontextwort("abc"));
        assertFalse(beinaheKontextwort("lxxr"), "zwei Abweichungen");
    }

    // ------------------------------------------------------------------ Selbsttests: Trennschaerfe

    private static Function<Art, Recognizer> fehlalarm(int modus) {
        return a -> KvTestHilfen.kuenstlicherNeuErkenner(a.erkenner, a.typ, a.quelle, modus);
    }

    /** AK-15 schlaegt an: ein Fund hinter {@code I. K. } (gemessen 18) und hinter Initialen (237) wird gezaehlt. */
    @Test
    void selbsttest_ak15_kuenstlicherFehlalarmHinterInitialen_wirdGemeldet() {
        Ak15 ik = ak15Lauf(fehlalarm(KvTestHilfen.FEHLALARM_IK_INITIALEN));
        assertTrue(ik.zusaetzlich() >= 12, "AK-15 IK-Initialen: zusaetzliche Funde " + ik.zusaetzlich());
        Ak15 initiale = ak15Lauf(fehlalarm(KvTestHilfen.FEHLALARM_INITIALE));
        assertTrue(initiale.zusaetzlich() >= 160, "AK-15 Initialen: zusaetzliche Funde " + initiale.zusaetzlich());
        assertEquals(0, ak15Lauf(a -> a.standLuecken).zusaetzlich(), "Gegenprobe: die Kopie selbst hat keinen");
    }

    /** AK-16 schlaegt an, mit beiden Mustern (gemessen in 3 000 Texten: 116 bzw. 553 zusaetzliche Funde). */
    @Test
    void selbsttest_ak16_kuenstlicherFehlalarmHinterInitialen_wirdGemeldet() {
        Ak16 ik = ak16Lauf(SEED_AK16, 3_000, fehlalarm(KvTestHilfen.FEHLALARM_IK_INITIALEN));
        assertTrue(ik.zusaetzlich >= 80 && ik.ersteIteration > 0, "AK-16 IK-Initialen " + ik.zusammenfassung());
        Ak16 initiale = ak16Lauf(SEED_AK16, 3_000, fehlalarm(KvTestHilfen.FEHLALARM_INITIALE));
        assertTrue(initiale.zusaetzlich >= 380, "AK-16 Initialen " + initiale.zusammenfassung());
        assertEquals(0, ak16Lauf(SEED_AK16, 3_000, a -> a.standLuecken).zusaetzlich, "Gegenprobe Kopie");
    }

    /** Die Pruefung in {@code scan} schlaegt an (die Meldung nennt den Fehlalarm, nicht eine andere Invariante). */
    @Test
    void selbsttest_scan_kuenstlicherFehlalarmHinterInitialen_wirdGemeldet() {
        Art falsch = new Art(IK_ART.name, false, KvTestHilfen.kuenstlicherNeuErkenner(IK_ART.erkenner, IK_ART.typ,
                IK_ART.quelle, KvTestHilfen.FEHLALARM_IK_INITIALEN), IK_ART.vorgaenger, IK_ART.standLuecken, IK_ART.typ,
                IK_ART.kw, IK_ART.kwLang, IK_ART.gueltig, IK_ART.gueltig2, IK_ART.gueltig3, IK_ART.ungueltig,
                IK_ART.sicher, IK_ART.quelle, IK_ART.kws, IK_ART.fremdeKws);
        String text = "Prof. I. K. " + IK_GUELTIG;
        AssertionError fehler = assertThrows(AssertionError.class, () -> scan(falsch, text));
        assertTrue(fehler.getMessage().contains("Fehlalarm Teil 2"), "andere Pruefung hat angeschlagen");
        assertKein(IK_ART, text);
    }

    @Test
    void selbsttest_fehlalarmPruefung_ausnahmenGreifenNurWoDerPlanSieVorsieht() {
        Recognizer fehlalarmLanr = KvTestHilfen.kuenstlicherNeuErkenner(LANR_ART.erkenner, LANR_ART.typ,
                LANR_ART.quelle, KvTestHilfen.FEHLALARM_INITIALE);
        String ohne = "Dr. M. A. " + LANR_GUELTIG;
        assertEquals(1, fehlalarmZahl(LANR_ART, ohne, fehlalarmLanr.detect(ohne)), "Muster ohne Ausnahme");
        String weit = "L. A. N. R. " + LANR_GUELTIG;
        assertTrue(hatErweitertesWeitKontextwort(weitFolgenReferenz(weit), LANR_ART));
        assertEquals(0, fehlalarmZahl(LANR_ART, weit, fehlalarmLanr.detect(weit)),
                "Weit-Kontextwort der Art: der Fund ist durch die Vergleichsform erklaert");
        // Das Weit-Kontextwort der anderen Art (iknr) steht getrennt von der Fehlalarm-Form (x.): kein Verdecken.
        String andereArt = "I. K. N. R. und x. " + LANR_GUELTIG;
        assertTrue(hatErweitertesWeitKontextwort(weitFolgenReferenz(andereArt), IK_ART));
        assertFalse(hatErweitertesWeitKontextwort(weitFolgenReferenz(andereArt), LANR_ART));
        assertEquals(1, fehlalarmZahl(LANR_ART, andereArt, fehlalarmLanr.detect(andereArt)),
                "Weit-Kontextwort der anderen Art nimmt LANR nicht aus: strenge Pruefung, ein zusaetzlicher Fund");
        // Gegenprobe fuer IK: Der Text ist fuer IK ausgenommen; nach der neuen Regel zaehlt nur ein unerklaerter Fund.
        // Die Kopie liefert auf der Vergleichsform (I K N R und x. ...) den Fund hinter dem Kontextwort (IK-Kontext
        // aktiv, zwei Fuellwoerter), also ist der kuenstliche Fund dort erklaert (0 unerklaert, 1 erklaert).
        Recognizer fehlalarmIk = KvTestHilfen.kuenstlicherNeuErkenner(IK_ART.erkenner, IK_ART.typ, IK_ART.quelle,
                KvTestHilfen.FEHLALARM_INITIALE);
        int[] ik = fehlalarmZaehlung(IK_ART, andereArt, fehlalarmIk.detect(andereArt),
                IK_ART.standLuecken.detect(andereArt));
        assertEquals(0, ik[0], "IK: unerklaerte Funde");
        assertEquals(1, ik[1], "IK: erklaerte Funde");
        String strenge = "L A N R der Muster Herr Frau x. " + LANR_GUELTIG;
        assertEquals(1, fehlalarmZahl(LANR_ART, strenge, fehlalarmLanr.detect(strenge)),
                "Sperrschrift ohne erweiterte Luecke ist keine Ausnahme");
        String umbruch = "\r" + ZWSP + "\nDr. M. A. " + LANR_GUELTIG;
        assertEquals(0, fehlalarmZahl(LANR_ART, umbruch, fehlalarmLanr.detect(umbruch)), "CR UZ LF: Ausnahme");
        String nurCrLf = "\r\nDr. M. A. " + LANR_GUELTIG;
        assertEquals(1, fehlalarmZahl(LANR_ART, nurCrLf, fehlalarmLanr.detect(nurCrLf)), "CR LF ist keine Ausnahme");
    }

    /** Verschaerfung (1) aus Plan 9a: Im ausgenommenen Text ist nur ein erklaerter Zusatzfund zulaessig. */
    @Test
    void selbsttest_wortVergleichsform_unerklaerterFundImAusgenommenenTextWirdGemeldet() {
        // (i) Fund hinter einer Leerzeile, zwei Absaetze nach L. A. N. R.: die Vergleichsform liefert ihn nicht.
        Recognizer leerzeile = KvTestHilfen.kuenstlicherNeuErkenner(LANR_ART.erkenner, LANR_ART.typ, LANR_ART.quelle,
                KvTestHilfen.FEHLALARM_LEERZEILE);
        String absaetze = "L. A. N. R.\n\nZwischentext hier\n\n" + LANR_GUELTIG;
        assertTrue(hatErweitertesWeitKontextwort(weitFolgenReferenz(absaetze), LANR_ART));
        int[] a1 = fehlalarmZaehlung(LANR_ART, absaetze, leerzeile.detect(absaetze),
                LANR_ART.standLuecken.detect(absaetze));
        assertEquals(1, a1[0], "Leerzeile: unerklaerte Funde");
        assertEquals(0, a1[1], "Leerzeile: erklaerte Funde");
        // (ii) kuenstlicher I. K.-Initialen-Fund in einem Text, der zusaetzlich I. K. N. R. enthaelt
        Recognizer ikInitialen = KvTestHilfen.kuenstlicherNeuErkenner(IK_ART.erkenner, IK_ART.typ, IK_ART.quelle,
                KvTestHilfen.FEHLALARM_IK_INITIALEN);
        String initialen = "Prof. I. K. " + IK_GUELTIG + " Rechnung I. K. N. R.";
        assertTrue(hatErweitertesWeitKontextwort(weitFolgenReferenz(initialen), IK_ART));
        int[] a2 = fehlalarmZaehlung(IK_ART, initialen, ikInitialen.detect(initialen),
                IK_ART.standLuecken.detect(initialen));
        assertEquals(1, a2[0], "I. K. neben I. K. N. R.: unerklaerte Funde");
        // (iv) Volle Ersetzung aller Luecken waere falsch: I. K. <IK> neben dem Weit-Kontextwort bleibt gemeldet,
        // obwohl die Kopie auf "I K <IK>" einen Fund liefern wuerde.
        assertFalse(IK_ART.standLuecken.detect("I K " + IK_GUELTIG).isEmpty(), "Voraussetzung der Gegenprobe");
        String neben = "I. K. N. R.\n\nDr. I. K. " + IK_GUELTIG;
        int[] a4 = fehlalarmZaehlung(IK_ART, neben, ikInitialen.detect(neben), IK_ART.standLuecken.detect(neben));
        assertEquals(1, a4[0], "I. K. <IK> neben I. K. N. R.: unerklaerte Funde");
        // Scannerebene-Entsprechung der Gegenprobe: das Weit-Kontextwort der anderen Art verdeckt nichts (siehe
        // t5_selbsttest_scannerebene_wortVergleichsform in KvStandLueckenDifferenzTest).
        Recognizer ikBeiLanr = KvTestHilfen.kuenstlicherNeuErkenner(LANR_ART.erkenner, LANR_ART.typ, LANR_ART.quelle,
                KvTestHilfen.FEHLALARM_IK_INITIALEN);
        String lanrNeben = "L. A. N. R.\n\nDr. I. K. " + IK_GUELTIG;
        int[] a5 = fehlalarmZaehlung(LANR_ART, lanrNeben, ikBeiLanr.detect(lanrNeben),
                LANR_ART.standLuecken.detect(lanrNeben));
        assertEquals(1, a5[0], "LANR-Wort, Fund hinter I. K.: unerklaerte Funde");
    }

    /** Ein korrektes Teil 2 (Kopie auf der Wort-Vergleichsform, Positionen zurueckgebildet) wird nie gemeldet. */
    @Test
    void selbsttest_wortVergleichsform_richtigerNeuErkenner_festeFaelle() {
        Recognizer richtig = KvTestHilfen.kuenstlicherRichtigerNeuErkenner(LANR_ART.standLuecken, LANR_ART);
        String[] texte = {"L. A. N. R. " + LANR_GUELTIG, "L.  A.  N.  R.\tdes Arztes " + LANR_GUELTIG,
            "L\tA\tN\tR " + LANR_GUELTIG, "L. A N  R: " + LANR_GUELTIG};
        for (int i = 0; i < texte.length; i++) {
            String text = texte[i];
            List<Finding> neu = richtig.detect(text);
            assertEquals(1, neu.size(), "Fall " + i + ": Fund des korrekten Teil 2");
            assertEquals(text.indexOf(LANR_GUELTIG), neu.get(0).start(), "Fall " + i + ": Start");
            assertEquals(text.indexOf(LANR_GUELTIG) + LANR_GUELTIG.length(), neu.get(0).end(), "Fall " + i + ": Ende");
            List<Finding> alt = LANR_ART.standLuecken.detect(text);
            assertTrue(alt.isEmpty(), "Fall " + i + ": die Kopie findet im Originaltext nichts");
            int[] z = fehlalarmZaehlung(LANR_ART, text, neu, alt);
            assertEquals(0, z[0], "Fall " + i + ": unerklaerte Funde");
            assertEquals(1, z[1], "Fall " + i + ": erklaerte Funde");
        }
    }

    /** Die genaue Definition der Wort-Vergleichsform und die Positionsabbildung. */
    @Test
    void selbsttest_wortVergleichsform_definitionUndAbbildung() {
        String text = "L.  A.  N.  R.\tdes";
        KvTestHilfen.Vergleichsform vf = KvTestHilfen.wortVergleichsform(text, LANR_ART);
        assertTrue(vf.text().equals("L A N R.\tdes"), "nur Luecken im Wort, Schlusspunkt bleibt");
        assertTrue(KvTestHilfen.wortVergleichsform(text, IK_ART).text().equals(text), "Wort der anderen Art");
        String neben = "I. K. " + IK_GUELTIG + " L. A. N. R.";
        assertTrue(KvTestHilfen.wortVergleichsform(neben, LANR_ART).text().equals("I. K. " + IK_GUELTIG + " L A N R."),
                "Luecken ausserhalb des Wortes bleiben");
        String mitUz = "L. " + ZWSP + " A. N. R.";
        assertTrue(KvTestHilfen.wortVergleichsform(mitUz, LANR_ART).text().equals("L A N R."), "UZ in der Luecke");
        String streng = "L-A. N. R";
        assertTrue(KvTestHilfen.wortVergleichsform(streng, LANR_ART).text().equals("L-A N R"), "strenge Luecke bleibt");
        String ohneErweitert = "L A N R";
        assertTrue(KvTestHilfen.wortVergleichsform(ohneErweitert, LANR_ART).text().equals(ohneErweitert));
        String beide = "L. A. N. R. und I. K. N. R.";
        assertTrue(KvTestHilfen.wortVergleichsform(beide, LANR_ART, IK_ART).text().equals("L A N R. und I K N R."));
        assertTrue(KvTestHilfen.wortVergleichsform(beide, IK_ART).text().equals("L. A. N. R. und I K N R."));
        // Abbildung: Start und Ende eines Fundes auf der Vergleichsform entsprechen dem Originalbereich.
        String abbildung = "L.  A.  N.  R. " + LANR_GUELTIG;
        KvTestHilfen.Vergleichsform vf2 = KvTestHilfen.wortVergleichsform(abbildung, LANR_ART);
        int start = vf2.text().indexOf(LANR_GUELTIG);
        Finding f = new Finding(LANR_ART.typ, LANR_GUELTIG, start, start + LANR_GUELTIG.length(), 0.8, "lanr");
        Finding zurueck = vf2.aufOriginal(List.of(f)).get(0);
        assertEquals(abbildung.indexOf(LANR_GUELTIG), zurueck.start(), "abgebildeter Start");
        assertEquals(abbildung.length(), zurueck.end(), "abgebildetes Ende");
    }

    /** Verschaerfung (2) aus Plan 9a: In nicht ausgenommenen Texten gilt volle Gleichheit mit der Kopie. */
    @Test
    void selbsttest_volleGleichheitInNichtAusgenommenenTexten() {
        String text = "LANR " + LANR_GUELTIG;
        Recognizer hoeher = KvTestHilfen.kuenstlicherAbweichenderNeuErkenner(LANR_ART.erkenner, 0.02, null);
        Recognizer andereQuelle = KvTestHilfen.kuenstlicherAbweichenderNeuErkenner(LANR_ART.erkenner, 0.0,
                "lanr-laenge-abweichend");
        Recognizer gleich = KvTestHilfen.kuenstlicherAbweichenderNeuErkenner(LANR_ART.erkenner, 0.0, null);
        assertEquals(1, LANR_ART.erkenner.detect(text).size(), "Voraussetzung: ein Fund");
        assertEquals(0, fehlalarmZahl(LANR_ART, text, gleich.detect(text)), "Gegenprobe: gleiche Liste");
        assertEquals(1, fehlalarmZahl(LANR_ART, text, hoeher.detect(text)), "angehobene Konfidenz");
        assertEquals(1, fehlalarmZahl(LANR_ART, text, andereQuelle.detect(text)), "geaenderte Quelle");
        assertEquals(1, fehlalarmZahl(LANR_ART, text, List.of()), "fehlender Fund (gleiche Anzahl)");
        Art falsch = new Art(LANR_ART.name, true, hoeher, LANR_ART.vorgaenger, LANR_ART.standLuecken, LANR_ART.typ,
                LANR_ART.kw, LANR_ART.kwLang, LANR_ART.gueltig, LANR_ART.gueltig2, LANR_ART.gueltig3,
                LANR_ART.ungueltig, LANR_ART.sicher, LANR_ART.quelle, LANR_ART.kws, LANR_ART.fremdeKws);
        AssertionError fehler = assertThrows(AssertionError.class, () -> scan(falsch, text));
        assertTrue(fehler.getMessage().contains("Fehlalarm Teil 2"), "andere Pruefung hat angeschlagen");
        // Im ausgenommenen Text (Weit-Kontextwort) wird die Konfidenz nicht verglichen.
        String weit = "L. A. N. R. LANR " + LANR_GUELTIG;
        Recognizer hoeherKopie = KvTestHilfen.kuenstlicherAbweichenderNeuErkenner(LANR_ART.standLuecken, 0.02, null);
        assertEquals(1, LANR_ART.standLuecken.detect(weit).size(), "Voraussetzung: ein alter Fund");
        assertEquals(0, fehlalarmZahl(LANR_ART, weit, hoeherKopie.detect(weit)), "ausgenommen: Konfidenz egal");
        // CR UZ+ LF bleibt ganz ausgenommen
        String crUz = "LANR:\r" + ZWSP + "\n" + LANR_GUELTIG;
        assertEquals(0, fehlalarmZahl(LANR_ART, crUz, hoeher.detect(crUz)), "CR UZ LF: nicht geprueft");
    }

    /**
     * Eigener Zufallslauf ({@link #weitText}): Texte mit Weit-Kontextwort; das korrekte Teil 2 (Kopie auf der
     * Vergleichsform) und das Produkt werden nie gemeldet. Gemessen: 6 026 Paare (Text, Art) mit Weit-Kontextwort,
     * 8 264 erklaerte Zusatzfunde; Untergrenzen 4 100 und 5 600.
     */
    @Test
    void selbsttest_wortVergleichsform_zufallslaufMitWeitKontextwoertern() {
        Random r = new Random(SEED_WEIT);
        Recognizer[] richtig = {KvTestHilfen.kuenstlicherRichtigerNeuErkenner(LANR_ART.standLuecken, LANR_ART),
            KvTestHilfen.kuenstlicherRichtigerNeuErkenner(IK_ART.standLuecken, IK_ART)};
        int paare = 0;
        int unerklaert = 0;
        int erklaert = 0;
        int produkt = 0;
        for (int i = 0; i < ANZAHL_WEIT; i++) {
            String text = weitText(r);
            if (neunerfenster(text, "00")[1] != 0) {
                continue;
            }
            List<WeitFolge> folgen = weitFolgenReferenz(text);
            for (int k = 0; k < ARTEN.size(); k++) {
                Art a = ARTEN.get(k);
                if (!hatErweitertesWeitKontextwort(folgen, a) || enthaeltCrUnsichtbarLf(text)) {
                    continue;
                }
                paare++;
                List<Finding> alt = a.standLuecken.detect(text);
                int[] z = fehlalarmZaehlung(a, text, richtig[k].detect(text), alt);
                unerklaert += z[0];
                erklaert += z[1];
                produkt += fehlalarmZahl(a, text, a.erkenner.detect(text), alt);
            }
        }
        String zaehler = " [Paare " + paare + ", unerklaert " + unerklaert + ", erklaert " + erklaert
                + ", Produkt " + produkt + "]";
        assertEquals(0, unerklaert, "korrektes Teil 2 gemeldet" + zaehler);
        assertEquals(0, produkt, "Produkt gemeldet" + zaehler);
        assertTrue(paare >= MIN_WEIT_PAARE, "zu wenige Paare mit Weit-Kontextwort" + zaehler);
        assertTrue(erklaert >= MIN_WEIT_ERKLAERT, "zu wenige erklaerte Funde" + zaehler);
    }

    @Test
    void selbsttest_zusaetzlicheFunde_zaehltNurFundeOhneGleichenStartEndeUndTyp() {
        List<Finding> lanr = LANR_ART.standLuecken.detect("LANR " + LANR_GUELTIG);
        List<Finding> ik = IK_ART.standLuecken.detect("IK " + IK_GUELTIG);
        assertEquals(1, lanr.size());
        assertEquals(1, ik.size());
        assertEquals(0, zusaetzlicheFunde(List.of(), List.of()));
        assertEquals(0, zusaetzlicheFunde(lanr, lanr), "gleiche Liste");
        assertEquals(1, zusaetzlicheFunde(List.of(), lanr), "Fund ohne Gegenstueck");
        assertEquals(0, zusaetzlicheFunde(lanr, List.of()), "fehlender Fund ist nicht zusaetzlich");
        Finding f = lanr.get(0);
        Finding hoeher = new Finding(f.type(), f.originalValue(), f.start(), f.end(), 1.0, f.source());
        assertEquals(0, zusaetzlicheFunde(lanr, List.of(hoeher)), "andere Konfidenz ist nicht zusaetzlich");
        Finding verschoben = new Finding(f.type(), f.originalValue().substring(1) + "x", f.start() + 1, f.end() + 1,
                f.confidence(), f.source());
        assertEquals(1, zusaetzlicheFunde(lanr, List.of(verschoben)), "anderer Bereich");
        Finding g = ik.get(0);
        Finding andererTyp = new Finding(g.type(), f.originalValue(), f.start(), f.end(), f.confidence(), g.source());
        assertEquals(1, zusaetzlicheFunde(lanr, List.of(andererTyp)), "anderer Typ");
        List<Finding> beide = new ArrayList<>(lanr);
        beide.add(g);
        assertEquals(1, zusaetzlicheFunde(lanr, beide), "ein Fund mehr");
    }

    @Test
    void selbsttest_enthaeltCrUnsichtbarLf() {
        assertTrue(enthaeltCrUnsichtbarLf("a\r" + ZWSP + "\nb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r\nb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + ZWSP + "\rb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + ZWSP + "A\nb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + SHY + " \nb"));
    }

    // ------------------------------------------------------------------ Selbsttests: Referenz (D-2, D-3, D-4)

    private static int treffer(Art a, String text) {
        return weitKontextwoerterReferenz(text, a).size();
    }

    @Test
    void selbsttest_referenz_erkenntErweiterteFormenOhneSchluss() {
        String[] lanr = {"L. A. N. R.", "L  A  N  R", "L\tA\tN\tR", "L. A N  R", "l. a. n. r.", "L.  A.   N. R",
            "L" + NBSP + " A" + DUENN + SCHMAL_NBSP + "N" + " " + " R", "L. A. N. R. N. R.", "A r z t n r"};
        for (int i = 0; i < lanr.length; i++) {
            String text = lanr[i];
            List<int[]> t = weitKontextwoerterReferenz(text, LANR_ART);
            boolean erwartetErweitert = !text.equals("A r z t n r");
            assertEquals(1, t.size(), "Treffer fuer Form " + i);
            assertEquals(erwartetErweitert ? 1 : 0, t.get(0)[2], "erweitert");
            assertEquals(0, t.get(0)[0], "Start");
            assertEquals(text.length() - (text.endsWith(".") ? 1 : 0), t.get(0)[1], "Ende");
        }
        assertEquals(1, treffer(IK_ART, "I. K. N. R."));
        assertEquals(1, treffer(IK_ART, "I  K  N  U  M  M  E  R"));
        assertEquals(1, treffer(IK_ART, "I\tK\tN\tR"));
        assertEquals(1, treffer(IK_ART, "I. N. S. T. I. T. U. T. I. O. N. S. K. E. N. N. Z. E. I. C. H. E. N."));
        assertEquals(0, treffer(LANR_ART, "I. K. N. R."), "Wort der anderen Art");
        assertEquals(0, treffer(IK_ART, "L. A. N. R."), "Wort der anderen Art");
    }

    @Test
    void selbsttest_referenz_sperrschriftOhneErweiterteLueckeIstTrefferOhneErweitertFlag() {
        List<int[]> t = weitKontextwoerterReferenz("L A N R", LANR_ART);
        assertEquals(1, t.size());
        assertEquals(0, t.get(0)[2], "nicht erweitert");
        assertFalse(hatErweitertesWeitKontextwort(weitFolgenReferenz("L A N R"), LANR_ART));
        assertFalse(hatErweitertesWeitKontextwort(weitFolgenReferenz("L-A-N-R L" + GESCH_BINDESTRICH + "A"), LANR_ART));
        assertTrue(hatErweitertesWeitKontextwort(weitFolgenReferenz("L A N  R"), LANR_ART));
    }

    @Test
    void selbsttest_referenz_zweiBuchstabenWortIstNieEinTreffer() {
        for (String text : new String[] {"I. K.", "I  K", "I\tK", "I.  K.", "I K", "I.K.", "I-K", "Prof. I. K. M"}) {
            assertEquals(0, treffer(IK_ART, text), "Zwei Buchstaben (D-4)");
            assertFalse(hatErweitertesWeitKontextwort(weitFolgenReferenz(text), IK_ART));
        }
        List<WeitFolge> folgen = weitFolgenReferenz("I. K.");
        assertEquals(1, folgen.size());
        assertEquals(2, folgen.get(0).buchstaben());
        assertTrue(folgen.get(0).erweitert());
    }

    @Test
    void selbsttest_referenz_keineLueckeNachD2_keinTreffer() {
        String[] texte = {"L    A    N    R", "L" + NBSP + NBSP + NBSP + NBSP + "A" + NBSP + NBSP + NBSP + NBSP + "N",
            "L\t\tA\t\tN\t\tR", "L\t A\t N\t R", "L .A .N .R", "L - A - N - R", "L.. A.. N.. R..", "L.\tA.\tN.\tR",
            "L, A, N, R", "L; A; N; R", "L/A/N/R", "L_A_N_R", "L: A: N: R", "L\nA\nN\nR", "L\r\nA\r\nN\r\nR",
            "L. A.\nN. R.", "L " + HALBGEVIERT + " A " + HALBGEVIERT + " N " + HALBGEVIERT + " R",
            "L" + EN_SPACE + "A" + EN_SPACE + "N" + EN_SPACE + "R", "L. A. N. X. R."};
        for (int i = 0; i < texte.length; i++) {
            assertEquals(0, treffer(LANR_ART, texte[i]), "Keine Luecke, Fall " + i);
        }
    }

    @Test
    void selbsttest_referenz_maximaleFolgeVerhindertTeilTreffer() {
        String[] texte = {"A. L. A. N. R.", "L. A. N. R. X", "L. A. N.", "L. A. N. R. L. A. N. R.",
            "Herr A. L. A. N. R.", "LA. NR.", "L. ANR", "L" + ZWSP + "A. N. R.", "L. A. N. N. R."};
        for (int i = 0; i < texte.length; i++) {
            assertEquals(0, treffer(LANR_ART, texte[i]), "Maximale Folge, Fall " + i);
        }
    }

    @Test
    void selbsttest_referenz_unsichtbareZeichenSindDurchsichtigUndStartEndeSindOriginalIndizes() {
        String durchsichtig = "L. " + ZWSP + " A. N. R.";
        List<int[]> t = weitKontextwoerterReferenz(durchsichtig, LANR_ART);
        assertEquals(1, t.size(), "UZ zwischen Punkt-Leerzeichen und Leerzeichen");
        assertEquals(1, t.get(0)[2]);
        assertEquals(durchsichtig.length() - 1, t.get(0)[1]);
        String mitUz = "Herr " + ZWSP + "L" + SHY + ". A. N. R" + KvTestHilfen.BOM + ". Muster";
        List<int[]> u = weitKontextwoerterReferenz(mitUz, LANR_ART);
        assertEquals(1, u.size());
        assertEquals(mitUz.indexOf('L'), u.get(0)[0], "Start im Originaltext");
        assertEquals(mitUz.indexOf('R') + 1, u.get(0)[1], "Ende im Originaltext, ohne das folgende UZ");
        String ausserhalbBmp = KvTestHilfen.zeichen(0xE0020);
        String bmp = "L." + ausserhalbBmp + " A. N. R.";
        assertEquals(1, treffer(LANR_ART, bmp), "UZ ausserhalb der BMP");
        assertEquals(0, treffer(LANR_ART, "L" + ZWSP + "A N R"), "UZ zwischen Buchstaben macht eine Gruppe");
        assertEquals(0, treffer(LANR_ART, "LA. N. R."), "Teilgruppe");
    }

    @Test
    void selbsttest_referenz_lueckentabelleD2() {
        char[] streng = {' ', NBSP, DUENN, SCHMAL_NBSP, '.', '-', GESCH_BINDESTRICH, HALBGEVIERT};
        for (char c : streng) {
            assertLuecke(String.valueOf(c), LUECKE_STRENG);
        }
        String[] leerraum = {"  ", "   ", " " + NBSP, NBSP + " " + DUENN, "" + SCHMAL_NBSP + SCHMAL_NBSP};
        for (String s : leerraum) {
            assertLuecke(s, LUECKE_LEERRAUM);
        }
        String[] punkt = {". ", ".  ", ".   ", "." + NBSP, ". " + SCHMAL_NBSP + DUENN};
        for (String s : punkt) {
            assertLuecke(s, LUECKE_PUNKT);
        }
        assertLuecke("\t", LUECKE_TAB);
        assertLuecke("." + ZWSP + " ", LUECKE_PUNKT);
        String[] keine = {"    ", "" + NBSP + NBSP + NBSP + NBSP, "\t\t", "\t ", " \t", ".\t", " .", "..", " - ",
            " " + HALBGEVIERT + " ", "-  ", "\n", "\r\n", ",", ";", ":", "/", "_", "" + EN_SPACE, ", ", ". .", "!",
            ".    ", "." + NBSP + DUENN + SCHMAL_NBSP + " "};
        for (String s : keine) {
            assertLuecke(s, 0);
        }
    }

    private static void assertLuecke(String luecke, int erwartet) {
        List<WeitFolge> folgen = weitFolgenReferenz("L" + luecke + "A");
        if (erwartet == 0) {
            assertEquals(2, folgen.size(), "keine Luecke: zwei Folgen, Luecke mit " + luecke.length() + " Zeichen");
        } else {
            assertEquals(1, folgen.size(), "Luecke mit " + luecke.length() + " Zeichen");
            assertEquals(2, folgen.get(0).buchstaben());
            assertEquals(erwartet, folgen.get(0).luecken(), "Lueckenart, " + luecke.length() + " Zeichen");
        }
    }
}
