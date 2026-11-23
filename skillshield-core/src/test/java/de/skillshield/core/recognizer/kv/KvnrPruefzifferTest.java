package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.KVNR_ACHT_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_BUCHSTABE_C;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_LEHRBEISPIEL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_MARKER;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_ZEHN_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.alleKvnrGueltig;
import static de.skillshield.core.SynthetischeKvNummern.alleKvnrUngueltig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * T-3 (kvnr-bsnr-erkennung, AK-6, AK-23, AK-24): Prüfziffer der KVNR ({@link KvnrPruefziffer}), geprüft an den
 * Vektoren der zentralen Klasse {@link SynthetischeKvNummern}, am Lehrbeispiel der Quelle, an Randfällen und in einer
 * Kreuzprobe gegen die unabhängige Rechnung {@link KvnrBsnrHilfen#kvnrPruefziffer}. Die Testnummern stehen nur in der
 * zentralen Klasse; hier entstehen Ziffern ausschließlich aus Feldern, Teilen und dem Zufall.
 *
 * <p><b>Quellenvermerk (AK-24).</b> Quelle: Wikipedia, Artikel "Krankenversichertennummer",
 * https://de.wikipedia.org/wiki/Krankenversichertennummer, abgerufen am 23.11.2026. Status: Sekundärquelle, nicht
 * gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: GKV-Datenaustausch, "Prüfziffernberechnung für
 * die Krankenversichertennummer nach § 290 SGB V" (Anlage 1, 2. Januar 2023), nicht eingesehen.
 */
class KvnrPruefzifferTest {

    private static final int KREUZPROBE_DURCHLAEUFE = 100_000;
    private static final long SEED_KREUZPROBE = 4711L;
    private static final long SEED_EINDEUTIG = 815L;

    /** Die neun Ziffern hinter dem Buchstaben einer Nummer als Werte. */
    private static int[] ziffern(String nummer) {
        int[] werte = new int[9];
        for (int i = 0; i < 9; i++) {
            werte[i] = nummer.charAt(i + 1) - '0';
        }
        return werte;
    }

    private static boolean istGueltig(String nummer) {
        return KvnrPruefziffer.istGueltig(nummer.charAt(0), ziffern(nummer));
    }

    /** Eigene Rechnung mit wählbaren Abweichungen, nur zum Nachweis, dass ein Vektor sie unterscheidet. */
    private static int pruefziffer(char buchstabe, String achtZiffern, int startGewicht, boolean zweistellig,
            boolean quersumme) {
        int wert = buchstabe - 'A' + 1;
        String folge = (zweistellig ? String.format("%02d", wert) : Integer.toString(wert)) + achtZiffern;
        int summe = 0;
        for (int i = 0; i < folge.length(); i++) {
            int gewicht = (i + startGewicht - 1) % 2 == 0 ? 1 : 2;
            int produkt = (folge.charAt(i) - '0') * gewicht;
            summe += quersumme ? produkt / 10 + produkt % 10 : produkt;
        }
        return summe % 10;
    }

    // ------------------------------------------------------------------ Lehrbeispiel und Vektoren

    @Test
    void lehrbeispielDerQuelle_ergibtPruefziffer0_undJedeAndereLetzteZifferIstUngueltig() {
        assertEquals('A', KVNR_LEHRBEISPIEL.charAt(0));
        assertTrue(istGueltig(KVNR_LEHRBEISPIEL), "Lehrbeispiel der Quelle muss gueltig sein");
        assertEquals('0', KVNR_LEHRBEISPIEL.charAt(9));
        int[] werte = ziffern(KVNR_LEHRBEISPIEL);
        for (int ersatz = 1; ersatz <= 9; ersatz++) {
            werte[8] = ersatz;
            assertFalse(KvnrPruefziffer.istGueltig('A', werte), "Pruefziffer " + ersatz + " muss ungueltig sein");
        }
    }

    @Test
    void zentraleVektoren_gueltigeSindGueltig_ungueltigeUngueltig_gleichDerUnabhaengigenRechnung() {
        for (String n : alleKvnrGueltig()) {
            assertTrue(istGueltig(n), "gueltiger Vektor wird als ungueltig bewertet");
            assertEquals(n.charAt(9) - '0', KvnrBsnrHilfen.kvnrPruefziffer(n.charAt(0), n.substring(1, 9)));
        }
        for (String n : alleKvnrUngueltig()) {
            assertFalse(istGueltig(n), "ungueltiger Vektor wird als gueltig bewertet");
            assertTrue(n.charAt(9) - '0' != KvnrBsnrHilfen.kvnrPruefziffer(n.charAt(0), n.substring(1, 9)),
                    "ungueltiger Vektor ist nach der unabhaengigen Rechnung gueltig");
        }
        assertTrue(alleKvnrGueltig().size() >= 2 && alleKvnrUngueltig().size() >= 3);
    }

    @Test
    void ungueltigeVektoren_pruefzifferVerschoben_nachbarzifferVertauscht_zifferGeaendert_sindDreiArten() {
        // Pruefziffer verschoben: nur die letzte Ziffer unterscheidet sich vom gueltigen Vektor
        String verschoben = SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        assertEquals(KVNR_GUELTIG.substring(0, 9), verschoben.substring(0, 9));
        assertTrue(KVNR_GUELTIG.charAt(9) != verschoben.charAt(9));
        // Nachbarziffern vertauscht: gleiche Ziffernmenge, gleiche Pruefziffer, andere Reihenfolge
        String vertauscht = SynthetischeKvNummern.KVNR_UNGUELTIG_VERTAUSCHT;
        assertEquals(KVNR_GUELTIG.charAt(9), vertauscht.charAt(9));
        assertEquals(KVNR_GUELTIG.charAt(8), vertauscht.charAt(7));
        assertEquals(KVNR_GUELTIG.charAt(7), vertauscht.charAt(8));
        // eine Ziffer geaendert: genau eine Stelle der ersten neun Zeichen weicht ab, die Pruefziffer bleibt
        String geaendert = SynthetischeKvNummern.KVNR_UNGUELTIG_ZIFFER_GEAENDERT;
        int abweichungen = 0;
        for (int i = 0; i < 10; i++) {
            abweichungen += KVNR_GUELTIG.charAt(i) == geaendert.charAt(i) ? 0 : 1;
        }
        assertEquals(1, abweichungen);
        assertEquals(KVNR_GUELTIG.charAt(9), geaendert.charAt(9));
    }

    @Test
    void vektorMitBuchstabeC_unterscheidetGewichtsbeginn_buchstabenwertung_undQuersumme() {
        String acht = KVNR_GUELTIG_BUCHSTABE_C.substring(1, 9);
        int soll = KVNR_GUELTIG_BUCHSTABE_C.charAt(9) - '0';
        assertEquals(soll, pruefziffer('C', acht, 1, true, true), "eigene Rechnung weicht von der Referenz ab");
        assertTrue(soll != pruefziffer('C', acht, 2, true, true), "Gewichtsbeginn 2 ergibt dieselbe Pruefziffer");
        assertTrue(soll != pruefziffer('C', acht, 1, false, true), "einstellige Wertung ergibt dieselbe Pruefziffer");
        assertTrue(soll != pruefziffer('C', acht, 1, true, false), "Summe ohne Quersumme ergibt dieselbe Pruefziffer");
        assertTrue(istGueltig(KVNR_GUELTIG_BUCHSTABE_C));
    }

    @Test
    void vektorMitBuchstabeZ_istGueltig_undWertSechsundzwanzigGehtZweistelligEin() {
        assertEquals('Z', KVNR_GUELTIG.charAt(0));
        assertEquals('Z', KVNR_GUELTIG_ZWEI.charAt(0));
        assertTrue(istGueltig(KVNR_GUELTIG));
        assertTrue(istGueltig(KVNR_GUELTIG_ZWEI));
        // Z = 26 geht mit 2 und 6 ein: jede andere Prüfziffer als die berechnete ist ungültig
        int[] werte = ziffern(KVNR_GUELTIG);
        int richtig = werte[8];
        for (int ersatz = 0; ersatz <= 9; ersatz++) {
            werte[8] = ersatz;
            assertEquals(ersatz == richtig, KvnrPruefziffer.istGueltig('Z', werte), "Ersatzziffer " + ersatz);
        }
    }

    @Test
    void beideVektorenMitBuchstabeZ_unterscheidenGewichtsbeginnUndQuersumme() {
        for (String n : List.of(KVNR_GUELTIG, KVNR_GUELTIG_ZWEI)) {
            String acht = n.substring(1, 9);
            int soll = n.charAt(9) - '0';
            assertTrue(soll != pruefziffer('Z', acht, 2, true, true), "Gewichtsbeginn 2 ergibt dieselbe Pruefziffer");
            assertTrue(soll != pruefziffer('Z', acht, 1, true, false), "Summe ohne Quersumme ist gleich");
        }
    }

    @Test
    void jederBuchstabeAZ_inGrossUndKlein_wirdMitSeinerZweistelligenWertungGerechnet() {
        for (char c = 'A'; c <= 'Z'; c++) {
            String acht = "0000" + "5173";
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer(c, acht);
            int[] werte = new int[9];
            for (int i = 0; i < 8; i++) {
                werte[i] = acht.charAt(i) - '0';
            }
            werte[8] = pruef;
            assertTrue(KvnrPruefziffer.istGueltig(c, werte), "Grossbuchstabe " + (int) c);
            assertTrue(KvnrPruefziffer.istGueltig(Character.toLowerCase(c), werte), "Kleinbuchstabe " + (int) c);
            werte[8] = (pruef + 1) % 10;
            assertFalse(KvnrPruefziffer.istGueltig(c, werte), "Grossbuchstabe " + (int) c + " mit falscher Ziffer");
            assertFalse(KvnrPruefziffer.istGueltig(Character.toLowerCase(c), werte), "Kleinbuchstabe falsch");
        }
    }

    @Test
    void gleicheZiffern_verschiedeneBuchstaben_ergebenVerschiedenePruefziffernAmAlphabetsRand() {
        // A = 01 und J = 10 und S = 19: je andere Zehner- und Einerstelle, daher verschiedene Summen
        String acht = "0000" + "0000";
        Set<Integer> werte = new HashSet<>();
        for (char c : new char[] {'A', 'J', 'S', 'Z'}) {
            werte.add(KvnrBsnrHilfen.kvnrPruefziffer(c, acht));
        }
        assertEquals(4, werte.size(), "Buchstabenwerte gehen nicht unterscheidbar ein");
    }

    // ------------------------------------------------------------------ Randfälle der Rechnung

    @Test
    void produktAchtzehn_hatQuersummeNeun_nichtAchtzehn() {
        // A = 01 ergibt 0 + 2 = 2; die Ziffer 9 an der zweiten gewichteten Stelle ergibt 18, Quersumme 9: Summe 11
        int[] mit = {0, 0, 0, 0, 0, 9, 0, 0, 1};
        assertTrue(KvnrPruefziffer.istGueltig('A', mit), "Summe 11 verlangt Pruefziffer 1");
        // ohne Quersumme waere die Summe 20 und die Pruefziffer 0
        int[] ohne = {0, 0, 0, 0, 0, 9, 0, 0, 0};
        assertFalse(KvnrPruefziffer.istGueltig('A', ohne), "Produkt 18 darf nicht als 18 eingehen");
    }

    @Test
    void summeMitUebertrag_undSummeGenauZehn_ergebenDieRestZiffer() {
        // Summe 11 ergibt Rest 1 (Uebertrag in die Zehnerstelle), Summe 10 ergibt Rest 0
        assertTrue(KvnrPruefziffer.istGueltig('A', new int[] {0, 0, 0, 0, 0, 9, 0, 0, 1}));
        assertTrue(istGueltig(KVNR_NULL), "S und nur Nullen: Summe 10, Pruefziffer 0");
        assertFalse(KvnrPruefziffer.istGueltig('S', new int[] {0, 0, 0, 0, 0, 0, 0, 0, 1}));
        // Summe 40 des Lehrbeispiels: Rest 0
        assertTrue(istGueltig(KVNR_LEHRBEISPIEL));
    }

    @Test
    void jedeZifferJederStelle_gehtMitDemRichtigenGewichtEin() {
        // Eine Ziffer der ersten acht um 1 erhoeht (9 wird 0): die Pruefziffer aendert sich um die Differenz der
        // Quersummen von Ziffer mal Gewicht (gerade Stelle ab 0: Gewicht 1, sonst 2), gerechnet modulo 10
        String acht = KVNR_GUELTIG.substring(1, 9);
        int basis = KvnrBsnrHilfen.kvnrPruefziffer('Z', acht);
        for (int stelle = 0; stelle < 8; stelle++) {
            int alt = acht.charAt(stelle) - '0';
            int neu = (alt + 1) % 10;
            int gewicht = stelle % 2 == 0 ? 1 : 2;
            int erwartet = Math.floorMod(quersumme(neu * gewicht) - quersumme(alt * gewicht), 10);
            String geaendert = acht.substring(0, stelle) + neu + acht.substring(stelle + 1);
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer('Z', geaendert);
            assertEquals(erwartet, Math.floorMod(pruef - basis, 10), "Stelle " + stelle + " geht falsch gewichtet ein");
            assertTrue(erwartet != 0, "Stelle " + stelle + " aendert die Summe nicht");
            int[] werte = ziffern(KVNR_GUELTIG);
            werte[stelle] = neu;
            assertFalse(KvnrPruefziffer.istGueltig('Z', werte), "Stelle " + stelle + " geht nicht in die Summe ein");
            werte[8] = pruef;
            assertTrue(KvnrPruefziffer.istGueltig('Z', werte), "Stelle " + stelle + " mit neuer Pruefziffer");
        }
    }

    private static int quersumme(int zahl) {
        return zahl / 10 + zahl % 10;
    }

    @Test
    void formabweichungen_achtUndZehnZiffern_habenNichtNeunZiffernHinterDemBuchstaben() {
        assertEquals(9, KVNR_ACHT_ZIFFERN.length());
        assertEquals(11, KVNR_ZEHN_ZIFFERN.length());
        assertEquals(10, KVNR_GUELTIG.length());
    }

    // ------------------------------------------------------------------ ungültige Eingaben

    @Test
    void ungueltigeEingaben_werfenIllegalArgumentException_mitFesterMeldungOhneWerte() {
        int[] neun = ziffern(KVNR_GUELTIG);
        char umlautGross = (char) 0xC4;
        char umlautKlein = (char) 0xE4;
        char eszett = (char) 0xDF;
        List<Character> falscheBuchstaben = List.of(umlautGross, umlautKlein, eszett, '1', ' ', '@', '[', '`', '{',
                (char) 0, (char) 0xFF21);
        Set<String> meldungen = new HashSet<>();
        for (char c : falscheBuchstaben) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> KvnrPruefziffer.istGueltig(c, neun), "Zeichen " + (int) c);
            meldungen.add(e.getMessage());
        }
        List<int[]> falscheZiffern = List.of(new int[0], new int[8], new int[10],
                new int[] {0, 0, 0, 0, 0, 0, 0, 0, -1}, new int[] {0, 0, 0, 0, 0, 0, 0, 0, 10},
                new int[] {-1, 0, 0, 0, 0, 0, 0, 0, 0}, new int[] {0, 0, 0, 0, 0, 0, 0, 0, Integer.MAX_VALUE},
                new int[] {0, 0, 0, 0, 0, 0, 0, 0, '0'});
        for (int i = 0; i < falscheZiffern.size(); i++) {
            int index = i;
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> KvnrPruefziffer.istGueltig('Z', falscheZiffern.get(index)), "Fall " + index);
            meldungen.add(e.getMessage());
        }
        IllegalArgumentException ohneFeld = assertThrows(IllegalArgumentException.class,
                () -> KvnrPruefziffer.istGueltig('Z', null));
        meldungen.add(ohneFeld.getMessage());
        // eine einzige feste Meldung fuer alle Fehlerarten, ohne Ziffern und ohne Eingabewert
        assertEquals(1, meldungen.size(), "Die Meldungen unterscheiden sich je nach Eingabe");
        String meldung = meldungen.iterator().next();
        assertTrue(meldung != null && !meldung.isEmpty(), "Meldung fehlt");
        assertTrue(meldung.chars().noneMatch(Character::isDigit), "Die Meldung enthaelt Ziffern");
        assertTrue(meldung.chars().allMatch(ch -> ch < 128), "Die Meldung enthaelt Nicht-ASCII-Zeichen");
    }

    @Test
    void ungueltigeEingabe_buchstabeUndZiffernZugleichFalsch_wirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> KvnrPruefziffer.istGueltig((char) 0xE4, null));
        assertThrows(IllegalArgumentException.class, () -> KvnrPruefziffer.istGueltig('1', new int[3]));
    }

    @Test
    void eingabeArrayWirdNichtVeraendert() {
        int[] werte = ziffern(KVNR_MARKER);
        int[] kopie = werte.clone();
        KvnrPruefziffer.istGueltig('M', werte);
        assertTrue(Arrays.equals(kopie, werte), "Das Eingabefeld wurde veraendert");
    }

    // ------------------------------------------------------------------ Kreuzprobe

    @Test
    void kreuzprobe_gegenDieUnabhaengigeRechnung_ueber100000ZufallswerteMitFestemSeed() {
        Random zufall = new Random(SEED_KREUZPROBE);
        int gueltig = 0;
        for (int lauf = 0; lauf < KREUZPROBE_DURCHLAEUFE; lauf++) {
            char buchstabe = (char) ('A' + zufall.nextInt(26));
            StringBuilder acht = new StringBuilder();
            int[] werte = new int[9];
            for (int i = 0; i < 8; i++) {
                werte[i] = zufall.nextInt(10);
                acht.append((char) ('0' + werte[i]));
            }
            werte[8] = zufall.nextInt(10);
            char eingabe = zufall.nextBoolean() ? buchstabe : Character.toLowerCase(buchstabe);
            boolean erwartet = KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, acht.toString()) == werte[8];
            boolean ist = KvnrPruefziffer.istGueltig(eingabe, werte);
            boolean eigene = pruefziffer(buchstabe, acht.toString(), 1, true, true) == werte[8];
            assertTrue(erwartet == ist, "Kreuzprobe weicht ab: Seed " + SEED_KREUZPROBE + ", Durchlauf " + lauf);
            assertTrue(erwartet == eigene, "Eigene Rechnung weicht ab: Seed " + SEED_KREUZPROBE + ", Lauf " + lauf);
            if (ist) {
                gueltig++;
            }
        }
        // Erwartet etwa jede zehnte Nummer (Istwert siehe Meldung); Grenzen weit um 10 000
        assertTrue(gueltig > 9_000 && gueltig < 11_000, "Gueltige in der Kreuzprobe: " + gueltig);
    }

    @Test
    void zuJeAchtZiffernUndBuchstabeIstGenauEineLetzteZifferGueltig() {
        Random zufall = new Random(SEED_EINDEUTIG);
        for (int lauf = 0; lauf < 2_000; lauf++) {
            char buchstabe = (char) ('A' + zufall.nextInt(26));
            int[] werte = new int[9];
            for (int i = 0; i < 8; i++) {
                werte[i] = zufall.nextInt(10);
            }
            int treffer = 0;
            for (int letzte = 0; letzte <= 9; letzte++) {
                werte[8] = letzte;
                if (KvnrPruefziffer.istGueltig(buchstabe, werte)) {
                    treffer++;
                }
            }
            assertEquals(1, treffer, "Durchlauf " + lauf);
        }
    }

    // ------------------------------------------------------------------ Waechter fuer gueltige Nummern

    @Test
    void zentraleKlasse_alleGueltigenVektorenBestehenDenLaufzeitWaechter_mitDemLehrbeispielAlsAusnahme() {
        String text = String.join(" und ", alleKvnrGueltig()) + "; " + String.join(", ", alleKvnrUngueltig());
        KvnrSynthetischWaechter.Befund befund = KvnrSynthetischWaechter.pruefe(text);
        assertEquals(alleKvnrGueltig().size(), befund.gueltigeFormen());
        assertEquals(0, befund.verstoesse());
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        for (String n : alleKvnrGueltig()) {
            if (!n.equals(KVNR_LEHRBEISPIEL)) {
                assertTrue(n.substring(1).startsWith("0000"), "Vektor ohne die Ziffern 0000 neben dem Lehrbeispiel");
            }
        }
    }

    @Test
    void selbsttest_laufzeitWaechter_findetGueltigeNummerOhne0000_auchMitTrennerUndKleinbuchstabe() {
        String acht = "1234" + "5671";
        String neun = KvnrBsnrHilfen.kvnrNeunZiffern('Q', acht);
        List<String> texte = List.of("Q" + neun, "q" + neun, "Q " + neun, "Q-" + neun, "Q/" + neun, "x Q" + neun + ".",
                "Q" + neun.substring(0, 4) + " " + neun.substring(4), "Q " + neun.substring(0, 3) + " "
                        + neun.substring(3, 6) + " " + neun.substring(6));
        for (int i = 0; i < texte.size(); i++) {
            KvnrSynthetischWaechter.Befund b = KvnrSynthetischWaechter.pruefe(texte.get(i));
            assertEquals(1, b.verstoesse(), "Text " + i);
            assertEquals(1, b.gueltigeFormen(), "Text " + i);
            int index = i;
            assertThrows(AssertionError.class, () -> KvnrSynthetischWaechter.assertKvnrSynthetisch(texte.get(index)));
        }
    }

    /** Die Ziffern des Textes als vollbreite Ziffern (U+FF10 bis U+FF19). */
    private static String vollbreit(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) (0xFF10 + (c - '0')) : c);
        }
        return sb.toString();
    }

    @Test
    void selbsttest_laufzeitWaechter_kenntTabUnterstrichUndUnterstrichGruppen_alsTrenner() {
        String neun = KvnrBsnrHilfen.kvnrNeunZiffern('Q', "1234" + "5671");
        String vorn = neun.substring(0, 3);
        String mitte = neun.substring(3, 6);
        String hinten = neun.substring(6);
        List<String> texte = List.of("Q\t" + neun, "Q_" + neun, "Q" + neun.substring(0, 4) + "\t" + neun.substring(4),
                "Q" + neun.substring(0, 4) + "_" + neun.substring(4), "Q_" + vorn + "_" + mitte + "_" + hinten,
                "Q\t" + vorn + "\t" + mitte + "\t" + hinten, "Q " + vorn + "_" + mitte + "\t" + hinten);
        for (int i = 0; i < texte.size(); i++) {
            KvnrSynthetischWaechter.Befund b = KvnrSynthetischWaechter.pruefe(texte.get(i));
            assertEquals(1, b.gueltigeFormen(), "Text " + i);
            assertEquals(1, b.verstoesse(), "Text " + i);
        }
        // zwei Trenner hintereinander trennen die Kette: keine Form aus ganzen Bloecken
        assertEquals(0, KvnrSynthetischWaechter.pruefe("Q_" + vorn + "_ " + mitte + "_" + hinten).gueltigeFormen());
    }

    @Test
    void selbsttest_laufzeitWaechter_zaehltVollbreiteZiffernAlsZiffern() {
        String neun = KvnrBsnrHilfen.kvnrNeunZiffern('Q', "1234" + "5671");
        List<String> texte = List.of("Q" + vollbreit(neun), "Q " + vollbreit(neun),
                "Q" + vollbreit(neun.substring(0, 4)) + " " + neun.substring(4),
                "Q" + neun.substring(0, 5) + "-" + vollbreit(neun.substring(5)));
        for (int i = 0; i < texte.size(); i++) {
            KvnrSynthetischWaechter.Befund b = KvnrSynthetischWaechter.pruefe(texte.get(i));
            assertEquals(1, b.gueltigeFormen(), "Text " + i);
            assertEquals(1, b.verstoesse(), "Text " + i);
        }
        // mit 0000 an Stelle 2 bis 5 bleibt auch die vollbreite Form ohne Verstoss
        String regelkonform = KvnrBsnrHilfen.kvnrNeunZiffern('Q', "0000" + "5671");
        KvnrSynthetischWaechter.Befund ok = KvnrSynthetischWaechter.pruefe("Q" + vollbreit(regelkonform));
        assertEquals(1, ok.gueltigeFormen());
        assertEquals(0, ok.verstoesse());
    }

    @Test
    void selbsttest_laufzeitWaechter_ignoriertUngueltigeUndFormabweichungen_undNurDasLehrbeispielMitAIstAusgenommen() {
        String acht = "1234" + "5671";
        String richtig = KvnrBsnrHilfen.kvnrNeunZiffern('Q', acht);
        String falsch = acht + (char) ('0' + (richtig.charAt(8) - '0' + 1) % 10);
        assertEquals(0, KvnrSynthetischWaechter.pruefe("Q" + falsch).gueltigeFormen());
        assertEquals(0, KvnrSynthetischWaechter.pruefe("Q" + richtig + "1").gueltigeFormen());
        assertEquals(0, KvnrSynthetischWaechter.pruefe("Q" + richtig.substring(0, 8)).gueltigeFormen());
        assertEquals(0, KvnrSynthetischWaechter.pruefe("QQ" + "  " + richtig).gueltigeFormen());
        // Lehrbeispiel mit anderem Buchstaben, gleiche Ziffern: gueltig nur, wenn die Rechnung es hergibt
        String lehrZiffern = KVNR_LEHRBEISPIEL.substring(1);
        String fuerB = lehrZiffern.substring(0, 8) + KvnrBsnrHilfen.kvnrPruefziffer('B', lehrZiffern.substring(0, 8));
        KvnrSynthetischWaechter.Befund b = KvnrSynthetischWaechter.pruefe("B" + fuerB);
        assertEquals(1, b.gueltigeFormen());
        assertEquals(1, b.verstoesse());
        KvnrSynthetischWaechter.Befund a = KvnrSynthetischWaechter.pruefe(KVNR_LEHRBEISPIEL);
        assertEquals(1, a.gueltigeFormen());
        assertEquals(0, a.verstoesse());
    }
}
