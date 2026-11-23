package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.BOM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SHY;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.enthaeltCrUnsichtbarLf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.RoundtripFuzzTest.Fall;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvStandLuecken;
import de.skillshield.core.recognizer.kv.KvTestHilfen;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vergleich des heutigen Standes gegen die zweite eingefrorene Kopie ({@code KvNumberScannerStandLuecken}, Stand Ende
 * kv-kontext-luecken; T-1, AK-22, AK-27, AK-28). Die Kopie wird nie geaendert.
 *
 * <p>Siegel: Ein Hash ueber alle Funde der Kopie auf Fuzz-Teil 1, 1b, 1c und 1d haelt sie fest; aendert sich die Kopie
 * oder einer der Generatoren, schlaegt er an. Differenzpruefung: Erkennerebene je Art
 * ({@link KvTestHilfen#pruefeGegenStandLuecken}, {@link KvTestHilfen#verletztBestandsschutzOhneAusnahme}: kein Fund
 * fehlt, keine Konfidenz sinkt, gleiche Quelle, keine Ausnahme) und Scannerebene ({@code TextScanner} mit den
 * Erkennern der Kopie gegen {@code TextScanner.standard()}: jede alt abgedeckte Position ist neu abgedeckt, und eine
 * alt von einem KV-Fund abgedeckte Position ist neu nicht nur von einem Nicht-KV-Fund abgedeckt). Selbsttests belegen,
 * dass die Pruefregel nicht ins Leere laeuft. Meldungen nennen nur Zahlen, nie Text oder Ziffern.
 *
 * <p>Teil 1 enthaelt keine KV-Funde der Kopie (per Test {@code teil1_enthaeltKeineKvFundeDerKopie} geprueft). Sein
 * Siegel und die Erkenner-Differenz sind dort nur Textzahl bzw. Leerlauf; eine Mindestzahl an Funden gibt es fuer
 * Teil 1 bewusst nicht, weil sie nie erfuellbar waere. Der Schutz der Kopie liegt auf Teil 1b, 1c, Korpus und
 * Generatoren.
 *
 * <p>Die Hilfen {@code siegel}, {@code texte}, {@code teil1c} und {@code abdeckung} stehen bewusst noch einmal hier
 * und nicht gemeinsam mit {@code KvVorgaengerDifferenzTest}: Der Vorgaengertest bleibt unveraendert.
 *
 * <p>Seit T-2 (CR UZ+ LF als ein Umbruch) weicht das Produkt auf Texten mit dieser Folge ab; der fruehere
 * Uebergangstest {@code t1Uebergang_produktGleichStandLuecken} ist entfernt. An seine Stelle tritt
 * {@code t2_zusaetzlicheFundeNurInTextenMitCrUnsichtbarLf}: ohne die Folge sind die Fundlisten gleich.
 *
 * <p>T-5 (Fehlalarm-Pruefstand fuer Teil 2, Plan Kap. 4.3): Auf Scannerebene deckt der neue Stand auf Texten ohne
 * {@code CR UZ+ LF} und ohne Weit-Kontextwort keine Position ab, die die Kopie nicht abdeckt
 * ({@code t5_differenz_scannerebene_...}; in Texten mit Weit-Kontextwort sind nur Positionen zulaessig, die die Kopie
 * auf der Wort-Vergleichsform abdeckt, Plan 9a); die Selbsttests belegen mit einem kuenstlichen Fehlalarm-Scanner,
 * dass die Pruefung anschlaegt. Der Block steht vor Teil 2 und wird in T-6 bis T-10 nicht geaendert (Regel 8.10).
 */
class KvStandLueckenDifferenzTest {

    /**
     * Siegel ueber die Funde der Kopie auf Fuzz-Teil 1 (hexadezimal, in T-1 gemessen). Gleich dem Teil-1-Siegel des
     * Vorgaengers: Teil 1 enthaelt keine KV-Funde, der Hash besteht nur aus den Fundzahlen 0 (Textzahl). Er
     * schuetzt die Kopie dort nicht; das tun 1b, 1c, Korpus und Generatoren.
     */
    static final int SIEGEL_LUECKEN_TEIL_1 = 0xE79FC611;
    /** Siegel ueber die Funde der Kopie auf Fuzz-Teil 1b (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_LUECKEN_TEIL_1B = 0x04D9C620;
    /** Siegel ueber die Funde der Kopie auf Fuzz-Teil 1c (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_LUECKEN_TEIL_1C = 0x6CA21DC5;
    /** Siegel ueber die Funde der Kopie auf Fuzz-Teil 1d (hexadezimal, in T-4 gemessen). */
    static final int SIEGEL_LUECKEN_TEIL_1D = 0x8AE75156;

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
            new UrlRecognizer(), new PathRecognizer(), KvStandLuecken.lanr(), KvStandLuecken.ik()));

    private static List<String> texte(List<Fall> faelle) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (Fall f : faelle) {
            texte.add(f.text());
        }
        return texte;
    }

    private static List<String> teil1() {
        return texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE));
    }

    private static List<String> teil1b() {
        return texte(RoundtripFuzzTest.erzeugeKv(RoundtripFuzzTest.SEED_TEXTE_KV));
    }

    private static List<String> teil1c() {
        List<RoundtripFuzzTest.FallC> faelle = RoundtripFuzzTest.erzeugeKvC(RoundtripFuzzTest.SEED_TEXTE_KV_C);
        List<String> texte = new ArrayList<>(faelle.size());
        for (RoundtripFuzzTest.FallC f : faelle) {
            texte.add(f.text());
        }
        return texte;
    }

    private static List<String> teil1d() {
        List<RoundtripFuzzTest.FallD> faelle = RoundtripFuzzTest.erzeugeKvD(RoundtripFuzzTest.SEED_TEXTE_KV_D);
        List<String> texte = new ArrayList<>(faelle.size());
        for (RoundtripFuzzTest.FallD f : faelle) {
            texte.add(f.text());
        }
        return texte;
    }

    /** Einmal erzeugt und von allen Tests wiederverwendet (die Listen werden nur gelesen). */
    private static final List<String> TEIL_1C = teil1c();
    private static final List<String> TEIL_1D = teil1d();
    private static final List<String> GENERATOREN = KvKontextLueckenEigenschaftTest.differenzTexte();

    // ------------------------------------------------------------------ Siegel

    /** Hash aus den Feldern der Funde selbst (nicht aus {@code hashCode()} von Objekten), stabil ueber Laeufe. */
    private static int siegel(List<String> texte) {
        int h = 17;
        for (String text : texte) {
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                List<Finding> funde = a.standLuecken.detect(text);
                h = 31 * h + funde.size();
                for (Finding f : funde) {
                    long bits = Double.doubleToLongBits(f.confidence());
                    h = 31 * h + f.start();
                    h = 31 * h + f.end();
                    h = 31 * h + f.type().name().hashCode();
                    h = 31 * h + (int) (bits ^ (bits >>> 32));
                    h = 31 * h + f.source().hashCode();
                }
            }
        }
        return h;
    }

    @Test
    void standLuecken_fundbildIstEingefroren() {
        int teil1 = siegel(teil1());
        int teil1b = siegel(teil1b());
        int teil1c = siegel(TEIL_1C);
        assertEquals(SIEGEL_LUECKEN_TEIL_1, teil1, "Siegel Teil 1, gemessen 0x" + Integer.toHexString(teil1));
        assertEquals(SIEGEL_LUECKEN_TEIL_1B, teil1b, "Siegel Teil 1b, gemessen 0x" + Integer.toHexString(teil1b));
        assertEquals(SIEGEL_LUECKEN_TEIL_1C, teil1c, "Siegel Teil 1c, gemessen 0x" + Integer.toHexString(teil1c));
    }

    /** Eigenes Siegel fuer Teil 1d (T-4); die Siegel von Teil 1, 1b und 1c bleiben im Test davor unveraendert. */
    @Test
    void standLuecken_fundbildAufTeil1dIstEingefroren() {
        int teil1d = siegel(TEIL_1D);
        assertEquals(SIEGEL_LUECKEN_TEIL_1D, teil1d, "Siegel Teil 1d, gemessen 0x" + Integer.toHexString(teil1d));
    }

    // ------------------------------------------------------------------ Uebergang

    /** Texte, deren Fundliste (je Art) von der der Kopie abweicht, getrennt nach mit und ohne CR UZ+ LF. */
    private static int[] ungleicheTexte(List<String> texte) {
        int[] zaehler = new int[2];
        for (String text : texte) {
            boolean mitFolge = enthaeltCrUnsichtbarLf(text);
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                if (!a.erkenner.detect(text).equals(a.standLuecken.detect(text))) {
                    zaehler[mitFolge ? 1 : 0]++;
                }
            }
        }
        return zaehler;
    }

    /**
     * Paare (Text, Art) ohne {@code CR UZ+ LF}: {Paare ohne erweitertes Weit-Kontextwort mit abweichender Fundliste,
     * Paare mit erweitertem Weit-Kontextwort der Art, davon mit verletztem strengem Bestandsschutz}. Fuer die Paare
     * mit Weit-Kontextwort gilt zusaetzlich "kein unerklaerter Fund" ({@link KvTestHilfen#scan} prueft ihn mit dem
     * Fehlalarm-Pruefstand und bricht sonst ab).
     */
    private static int[] ungleichOhneErklaerung(List<String> texte) {
        int[] zaehler = new int[3];
        for (String text : texte) {
            if (enthaeltCrUnsichtbarLf(text)) {
                continue;
            }
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                List<Finding> neu = a.erkenner.detect(text);
                List<Finding> alt = a.standLuecken.detect(text);
                if (KvTestHilfen.hatErweitertesWeitKontextwort(KvTestHilfen.weitFolgenReferenz(text), a)) {
                    zaehler[1]++;
                    KvTestHilfen.scan(a, text);
                    zaehler[2] += KvTestHilfen.verletztBestandsschutzOhneAusnahme(alt, neu) ? 1 : 0;
                } else if (!neu.equals(alt)) {
                    zaehler[0]++;
                }
            }
        }
        return zaehler;
    }

    /**
     * T-2 (Teil 1 wirkt nur auf CR UZ+ LF) mit Teil 2 (Plan 9b): Auf jedem Text ohne diese Folge und ohne erweitertes
     * Weit-Kontextwort der Art sind die Fundlisten des Produkts und der eingefrorenen Kopie gleich. Paare (Text, Art)
     * mit erweitertem Weit-Kontextwort (Teil 2, D-2/D-3) sind davon ausgenommen und werden stattdessen auf "kein
     * unerklaerter Fund" und strengen Bestandsschutz geprueft; ihre Zahl je Textmenge ist ein exakter Messwert (fester
     * Seed, Kopie und Generatoren sind eingefroren) und haelt die Ausnahme eng.
     */
    @Test
    void t2_zusaetzlicheFundeNurInTextenMitCrUnsichtbarLfOderMitErweitertemWeitKontextwort() {
        List<List<String>> mengen = List.of(teil1(), teil1b(), TEIL_1C, TEIL_1D, KvDifferenzTest.korpus(), GENERATOREN);
        String[] namen = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Korpus", "Generatoren"};
        // Messwerte je Textmenge (gleiche Reihenfolge wie "namen"): Zahl der Paare (Text, Art) ohne CR UZ+ LF mit
        // erweitertem Weit-Kontextwort der Art (Zaehler z[i][1] aus ungleichOhneErklaerung), fester Seed.
        int[] weitKontextwort = {0, 0, 72, 0, 3, 294};
        int[][] z = new int[namen.length][];
        StringBuilder alle = new StringBuilder();
        for (int i = 0; i < namen.length; i++) {
            z[i] = ungleichOhneErklaerung(mengen.get(i));
            alle.append(namen[i]).append(" ").append(z[i][1]).append("; ");
        }
        for (int i = 0; i < namen.length; i++) {
            assertEquals(0, z[i][0], namen[i] + ": Abweichung ohne die Folge und ohne Weit-Kontextwort");
            assertEquals(0, z[i][2], namen[i] + ": Bestandsschutz verletzt");
            assertEquals(weitKontextwort[i], z[i][1], namen[i] + ": Paare mit Weit-Kontextwort; " + alle);
        }
    }

    /**
     * Teil 1d enthaelt die Folge haeufig, und dort weicht die Fundliste des Produkts von der der Kopie ab (die
     * zusaetzlichen Funde sind der Zweck von Teil 1); die Pruefung "ohne Folge gleich" laeuft also nicht ins Leere.
     * Mindestzahlen mit mehr als 30 % Abstand zu den Messwerten (fester Seed): 993 Texte mit der Folge (Untergrenze
     * 650), 1 727 Paare (Text, Art) mit der Folge und abweichender Fundliste (1 100). Die Kopie liefert auf Teil 1d
     * 1 695 LANR- und 2 019 IK-Funde (Pruefung {@code differenz_standLueckenLiefertFunde_...}).
     */
    @Test
    void t4_teil1d_folgeKommtVorUndZusaetzlicheFundeEntstehenNurMitIhr() {
        int mitFolge = 0;
        for (String text : TEIL_1D) {
            if (enthaeltCrUnsichtbarLf(text)) {
                mitFolge++;
            }
        }
        int[] ungleich = ungleicheTexte(TEIL_1D);
        assertEquals(0, ungleich[0], "Teil 1d: Abweichung ohne die Folge");
        assertTrue(mitFolge >= 650, "Teil 1d: Texte mit der Folge " + mitFolge);
        assertTrue(ungleich[1] >= 1_100, "Teil 1d: Paare mit der Folge und anderer Fundliste " + ungleich[1]);
    }

    @Test
    void t2_selbsttest_folgeWirdErkanntUndWirktAufDieFundliste() {
        assertTrue(enthaeltCrUnsichtbarLf("a\r" + ZWSP + "\nb"));
        assertTrue(enthaeltCrUnsichtbarLf("\r" + SHY + BOM + "\n"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r\nb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + ZWSP + " \nb"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + ZWSP));
        String ausserhalbBmp = new String(Character.toChars(0xE0020));
        String ausserhalbBmp2 = new String(Character.toChars(0x1D173));
        assertTrue(enthaeltCrUnsichtbarLf("a\r" + ausserhalbBmp + "\nb"));
        assertTrue(enthaeltCrUnsichtbarLf("\r" + ZWSP + ausserhalbBmp + ausserhalbBmp2 + "\n"));
        assertFalse(enthaeltCrUnsichtbarLf("a\r" + ausserhalbBmp + " \nb"));
        int[] ausserhalb = ungleicheTexte(List.of("LANR:\r" + ausserhalbBmp + "\n" + LANR_GUELTIG));
        assertEquals(1, ausserhalb[1], "Produkt gleicht der Kopie trotz CR UZ LF ausserhalb der BMP");
        int[] zaehler = ungleicheTexte(List.of("LANR:\r" + ZWSP + "\n" + LANR_GUELTIG));
        assertEquals(0, zaehler[0], "Folge ohne Folge gezaehlt");
        assertEquals(1, zaehler[1], "Produkt gleicht der Kopie trotz CR UZ LF (Pruefung liefe ins Leere)");
    }

    // ------------------------------------------------------------------ Differenz Erkennerebene

    /** Zaehlt Verletzungen ohne Waechter (Teil 1 ist die unveraenderte Altfolge). */
    private static int zaehleVerletzungen(List<String> texte) {
        int verletzungen = 0;
        for (String text : texte) {
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                if (KvTestHilfen.verletztBestandsschutzOhneAusnahme(a.standLuecken.detect(text),
                        a.erkenner.detect(text))) {
                    verletzungen++;
                }
            }
        }
        return verletzungen;
    }

    /** Prueft Waechter, Invarianten und beide Differenzen je Art ueber {@code scan}. */
    private static void pruefeMitWaechter(List<String> texte) {
        for (String text : texte) {
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                KvTestHilfen.scan(a, text);
            }
        }
    }

    private static int zaehleAlteFunde(KvTestHilfen.Art a, List<String> texte) {
        int summe = 0;
        for (String text : texte) {
            summe += a.standLuecken.detect(text).size();
        }
        return summe;
    }

    @Test
    void teil1_enthaeltKeineKvFundeDerKopie() {
        for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
            assertEquals(0, zaehleAlteFunde(a, teil1()), a.name + ": Kopie findet auf Teil 1 etwas (Generator?)");
        }
    }

    /** Leerlauf, solange Teil 1 keine KV-Funde enthaelt (siehe oben); faengt eine Aenderung des Generators auf. */
    @Test
    void differenz_erkennerebene_teil1() {
        assertEquals(0, zaehleVerletzungen(teil1()), "Teil 1: Verletzungen des strengen Bestandsschutzes");
    }

    @Test
    void differenz_erkennerebene_teil1bUndKorpus() {
        pruefeMitWaechter(teil1b());
        pruefeMitWaechter(KvDifferenzTest.korpus());
    }

    @Test
    void differenz_erkennerebene_teil1c() {
        pruefeMitWaechter(TEIL_1C);
    }

    @Test
    void differenz_erkennerebene_teil1d() {
        pruefeMitWaechter(TEIL_1D);
    }

    @Test
    void differenz_erkennerebene_generatorenAusDenEigenschaftstests() {
        pruefeMitWaechter(GENERATOREN);
    }

    @Test
    void differenz_standLueckenLiefertFunde_pruefungLaeuftNichtInsLeere() {
        for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
            assertTrue(zaehleAlteFunde(a, teil1b()) > 0, a.name + ": Kopie ohne Funde auf Teil 1b");
            assertTrue(zaehleAlteFunde(a, TEIL_1C) > 0, a.name + ": Kopie ohne Funde auf Teil 1c");
            assertTrue(zaehleAlteFunde(a, TEIL_1D) > 0, a.name + ": Kopie ohne Funde auf Teil 1d");
            assertTrue(zaehleAlteFunde(a, KvDifferenzTest.korpus()) > 0, a.name + ": Kopie ohne Funde im Korpus");
            assertTrue(zaehleAlteFunde(a, GENERATOREN) > 0, a.name + ": Kopie ohne Funde auf den Generatoren");
        }
    }

    // ------------------------------------------------------------------ Differenz Scannerebene

    private static boolean[] abdeckung(int laenge, List<Finding> funde) {
        boolean[] a = new boolean[laenge];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    private static int verloreneTexte(TextScanner neu, List<String> texte) {
        int verloren = 0;
        for (String text : texte) {
            boolean[] imNeuen = abdeckung(text.length(), neu.scan(text));
            boolean fehlt = false;
            for (Finding f : ALT.scan(text)) {
                for (int i = f.start(); i < f.end(); i++) {
                    fehlt |= !imNeuen[i];
                }
            }
            if (fehlt) {
                verloren++;
            }
        }
        return verloren;
    }

    private static boolean istKv(Finding f) {
        return f.type() == FindingType.LANR || f.type() == FindingType.IK;
    }

    /**
     * Zaehlt die Texte, in denen eine alt von einem KV-Fund abgedeckte Position neu zwar abgedeckt ist, aber nur von
     * einem Fund eines Nicht-KV-Typs.
     */
    private static int typVerlusteTexte(TextScanner neu, List<String> texte) {
        int verloren = 0;
        for (String text : texte) {
            List<Finding> neuFunde = neu.scan(text);
            boolean[] kvNeu = abdeckung(text.length(), neuFunde.stream().filter(f -> istKv(f)).toList());
            boolean[] alleNeu = abdeckung(text.length(), neuFunde);
            boolean fehlt = false;
            for (Finding f : ALT.scan(text)) {
                if (!istKv(f)) {
                    continue;
                }
                for (int i = f.start(); i < f.end(); i++) {
                    fehlt |= !kvNeu[i] && alleNeu[i];
                }
            }
            if (fehlt) {
                verloren++;
            }
        }
        return verloren;
    }

    private static int alteKvScannerFunde(List<String> texte) {
        int funde = 0;
        for (String text : texte) {
            for (Finding f : ALT.scan(text)) {
                if (istKv(f)) {
                    funde++;
                }
            }
        }
        return funde;
    }

    @Test
    void differenz_scannerebene_keineAbgedeckePositionGehtVerloren() {
        assertEquals(0, verloreneTexte(NEU, teil1()), "Teil 1: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(NEU, teil1b()), "Teil 1b: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(NEU, KvDifferenzTest.korpus()), "Korpus: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(NEU, TEIL_1C), "Teil 1c: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(NEU, TEIL_1D), "Teil 1d: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(NEU, GENERATOREN), "Generatoren: Texte mit verlorener Position");
    }

    @Test
    void differenz_scannerebene_kvPositionWirdNieNurVonNichtKvTypAbgedeckt() {
        assertEquals(0, typVerlusteTexte(NEU, teil1()), "Teil 1: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, teil1b()), "Teil 1b: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, KvDifferenzTest.korpus()), "Korpus: Texte mit Typverlust");
        assertEquals(0, typVerlusteTexte(NEU, TEIL_1C), "Teil 1c: Texte mit Typverlust");
        assertEquals(0, typVerlusteTexte(NEU, TEIL_1D), "Teil 1d: Texte mit Typverlust");
        assertEquals(0, typVerlusteTexte(NEU, GENERATOREN), "Generatoren: Texte mit Typverlust");
    }

    @Test
    void differenz_scannerebene_alterScannerLiefertKvFunde_pruefungLaeuftNichtInsLeere() {
        for (List<String> texte : List.of(teil1b(), TEIL_1C, TEIL_1D, KvDifferenzTest.korpus(), GENERATOREN)) {
            assertTrue(alteKvScannerFunde(texte) > 0, "Scanner mit der Kopie ohne KV-Funde (Pruefung liefe ins Leere)");
        }
    }

    // ------------------------------------------------------------------ Fehlalarm Scannerebene (T-5, D-8)

    /** Zahl der Positionen, die {@code neu} abdeckt, die die Kopie ({@link #ALT}) aber nicht abdeckt. */
    private static int neuAbgedeckt(TextScanner neu, String text) {
        boolean[] alt = abdeckung(text.length(), ALT.scan(text));
        boolean[] jetzt = abdeckung(text.length(), neu.scan(text));
        int zahl = 0;
        for (int i = 0; i < text.length(); i++) {
            zahl += jetzt[i] && !alt[i] ? 1 : 0;
        }
        return zahl;
    }

    /**
     * Zahl der Positionen, die {@code neu} abdeckt, die die Kopie ({@link #ALT}) weder auf dem Originaltext noch auf
     * der Wort-Vergleichsform beider Arten ({@link KvTestHilfen#wortVergleichsform}, auf den Originaltext
     * zurueckgebildet) abdeckt. In einem Text ohne Weit-Kontextwort ist die Vergleichsform der Text selbst.
     */
    private static int neuUnerklaert(TextScanner neu, String text) {
        boolean[] alt = abdeckung(text.length(), ALT.scan(text));
        boolean[] jetzt = abdeckung(text.length(), neu.scan(text));
        KvTestHilfen.Vergleichsform vf = KvTestHilfen.wortVergleichsform(text, KvTestHilfen.LANR_ART,
                KvTestHilfen.IK_ART);
        boolean[] erklaert = abdeckung(text.length(), vf.aufOriginal(ALT.scan(vf.text())));
        int zahl = 0;
        for (int i = 0; i < text.length(); i++) {
            zahl += jetzt[i] && !alt[i] && !erklaert[i] ? 1 : 0;
        }
        return zahl;
    }

    /**
     * Fehlalarm-Pruefung auf Scannerebene (Plan Kap. 4.3, 9a): Auf jedem Text ohne {@code CR UZ+ LF} deckt {@code neu}
     * keine Position ab, die die Kopie nicht abdeckt. In Texten mit Weit-Kontextwort mit erweiterter Luecke (fuer
     * eine der beiden Arten) gelten dabei die Positionen als abgedeckt, die die Kopie auf der Wort-Vergleichsform
     * beider Arten abdeckt ({@link #neuUnerklaert}).
     *
     * @return {geprueft ohne Weit-Kontextwort, ausgenommen (CR UZ+ LF oder Weit-Kontextwort), Texte mit neu
     *         abgedeckter unerklaerter Position, alte KV-Funde in den Texten ohne Ausnahme}
     */
    private static int[] fehlalarmScannerebene(TextScanner neu, List<String> texte) {
        int[] z = new int[4];
        for (String text : texte) {
            boolean ausgenommen = KvTestHilfen.vonFehlalarmPruefungBeiderArtenAusgenommen(text);
            z[ausgenommen ? 1 : 0]++;
            if (!enthaeltCrUnsichtbarLf(text)) {
                z[2] += neuUnerklaert(neu, text) > 0 ? 1 : 0;
            }
            z[3] += ausgenommen ? 0 : alteKvScannerFunde(List.of(text));
        }
        return z;
    }

    private static TextScanner scannerRichtig() {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(),
                KvTestHilfen.kuenstlicherRichtigerNeuErkenner(KvStandLuecken.lanr(), KvTestHilfen.LANR_ART,
                        KvTestHilfen.IK_ART),
                KvTestHilfen.kuenstlicherRichtigerNeuErkenner(KvStandLuecken.ik(), KvTestHilfen.LANR_ART,
                        KvTestHilfen.IK_ART)));
    }

    private static TextScanner scannerMitFehlalarm(int modus) {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), KvTestHilfen.kuenstlicherNeuErkenner(KvStandLuecken.lanr(), FindingType.LANR,
                        "lanr", modus),
                KvTestHilfen.kuenstlicherNeuErkenner(KvStandLuecken.ik(), FindingType.IK, "ik", modus)));
    }

    /**
     * Keine neu abgedeckte Position auf Texten ohne Weit-Kontextwort und ohne {@code CR UZ+ LF}, ueber alle Textmengen
     * des Features (Fuzz 1, 1b, 1c, 1d, Korpus, Generatoren der Eigenschaftstests; Fuzz 1e entsteht erst in T-9).
     * Mindestzahlen mit mehr als 30 % Abstand zu den Messwerten (je Textmenge: geprueft / ausgenommen / alte KV-Funde
     * in den gepruefen Texten; Teil 1 enthaelt keine KV-Funde der Kopie): Teil 1 1 000 / 0 / 0, Teil 1b 1 000 / 0 /
     * 679, Teil 1c 928 / 72 / 5 415, Teil 1d 7 / 993 / 19 (fast alle Texte haben {@code CR UZ+ LF}), Korpus 288 / 39 /
     * 208, Generatoren 3 716 / 284 / 7 813. Die Untergrenzen stehen in {@link #MIN_GEPRUEFT} und
     * {@link #MIN_ALTE_KV_FUNDE} (gleiche Reihenfolge).
     */
    @Test
    void t5_differenz_scannerebene_keineNeuAbgedecktePositionOhneWeitKontextwort() {
        String[] namen = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Korpus", "Generatoren"};
        List<List<String>> mengen = List.of(teil1(), teil1b(), TEIL_1C, TEIL_1D, KvDifferenzTest.korpus(), GENERATOREN);
        int[][] ergebnis = new int[namen.length][];
        StringBuilder alle = new StringBuilder();
        for (int i = 0; i < namen.length; i++) {
            ergebnis[i] = fehlalarmScannerebene(NEU, mengen.get(i));
            alle.append(namen[i]).append(": geprueft ").append(ergebnis[i][0]).append(", ausgenommen ")
                    .append(ergebnis[i][1]).append(", alte KV-Funde ").append(ergebnis[i][3]).append("; ");
        }
        for (int i = 0; i < namen.length; i++) {
            assertEquals(0, ergebnis[i][2], namen[i] + ": Texte mit neu abgedeckter Position; " + alle);
        }
        for (int i = 0; i < namen.length; i++) {
            assertTrue(ergebnis[i][0] >= MIN_GEPRUEFT[i], namen[i] + ": zu wenig geprueft; " + alle);
            assertTrue(ergebnis[i][3] >= MIN_ALTE_KV_FUNDE[i], namen[i] + ": zu wenig alte Funde; " + alle);
        }
    }

    private static final int[] MIN_GEPRUEFT = {700, 700, 650, 4, 200, 2_600};
    private static final int[] MIN_ALTE_KV_FUNDE = {0, 470, 3_700, 13, 145, 5_400};

    /**
     * Trennschaerfe: Ein kuenstlicher Neu-Scanner mit einem Fund hinter {@code I. K. } bzw. hinter Initialen wird auf
     * Scannerebene gemeldet; Texte mit {@code CR UZ+ LF} sind ausgenommen, {@code CR LF} nicht; in Texten mit
     * Weit-Kontextwort ist der Fund hinter dem Wort durch die Wort-Vergleichsform erklaert.
     */
    @Test
    void t5_selbsttest_scannerebene_kuenstlicherFehlalarm_wirdGemeldet() {
        String text = "Prof. I. K. " + IK_GUELTIG;
        assertEquals(0, neuAbgedeckt(NEU, text), "Produkt hat keinen Fehlalarm");
        TextScanner ikInitialen = scannerMitFehlalarm(KvTestHilfen.FEHLALARM_IK_INITIALEN);
        assertTrue(neuAbgedeckt(ikInitialen, text) > 0, "kuenstlicher Fund hinter I. K. wird nicht abgedeckt");
        int[] gemischt = fehlalarmScannerebene(ikInitialen,
                List.of(text, "x. " + LANR_GUELTIG, "Dr. I. K. " + IK_GUELTIG));
        assertEquals(2, gemischt[2], "geprueft mit neu abgedeckter Position");
        TextScanner initiale = scannerMitFehlalarm(KvTestHilfen.FEHLALARM_INITIALE);
        int[] geprueft = fehlalarmScannerebene(initiale, List.of("x. " + LANR_GUELTIG, "\r\nx. " + IK_GUELTIG));
        assertEquals(2, geprueft[0], "beide Texte werden geprueft");
        assertEquals(2, geprueft[2], "beide melden den kuenstlichen Fehlalarm");
        int[] ausnahmen = fehlalarmScannerebene(initiale, List.of("L. A. N. R. " + LANR_GUELTIG,
                "\r" + ZWSP + "\nx. " + LANR_GUELTIG, "I. K. N. R. " + IK_GUELTIG));
        assertEquals(3, ausnahmen[1], "alle drei Texte sind ausgenommen");
        assertEquals(0, ausnahmen[2], "ausgenommene Texte melden nichts");
    }

    /**
     * Verschaerfung (1) aus Plan 9a auf Scannerebene: Im Text mit Weit-Kontextwort ist eine neu abgedeckte Position
     * nur zulaessig, wenn die Kopie sie auf der Wort-Vergleichsform beider Arten abdeckt. Ein kuenstlicher Fehlalarm
     * wird dort gemeldet, ein korrektes Teil 2 (Kopie auf der Vergleichsform) nie. Ein Weit-Wort der anderen Art
     * verdeckt keinen Fehlalarm der einen Art: {@code I. K. <IK>} neben {@code L. A. N. R.} bleibt gemeldet.
     */
    @Test
    void t5_selbsttest_scannerebene_wortVergleichsform() {
        TextScanner leerzeile = scannerMitFehlalarm(KvTestHilfen.FEHLALARM_LEERZEILE);
        TextScanner ikInitialen = scannerMitFehlalarm(KvTestHilfen.FEHLALARM_IK_INITIALEN);
        List<String> fehlalarme = List.of("L. A. N. R.\n\nZwischentext hier\n\n" + LANR_GUELTIG,
                "I. K. N. R.\n\nZwischentext hier\n\n" + IK_GUELTIG);
        int[] erste = fehlalarmScannerebene(leerzeile, fehlalarme);
        assertEquals(2, erste[1], "beide Texte haben ein Weit-Kontextwort");
        assertEquals(2, erste[2], "Fund hinter der Leerzeile bleibt unerklaert");
        List<String> neben = List.of("Prof. I. K. " + IK_GUELTIG + " Rechnung I. K. N. R.",
                "L. A. N. R.\n\nDr. I. K. " + IK_GUELTIG, "I. K. N. R.\n\nDr. I. K. " + IK_GUELTIG);
        int[] zweite = fehlalarmScannerebene(ikInitialen, neben);
        assertEquals(3, zweite[1], "alle Texte haben ein Weit-Kontextwort");
        assertEquals(3, zweite[2], "I. K. <IK> bleibt gemeldet, auch neben dem Weit-Wort der anderen Art");
        assertFalse(KvStandLuecken.ik().detect("I K " + IK_GUELTIG).isEmpty(),
                "Voraussetzung: volle Ersetzung aller Luecken haette den Fund erklaert");
        TextScanner richtig = scannerRichtig();
        List<String> korrekt = List.of("L. A. N. R. " + LANR_GUELTIG, "L.  A.  N.  R.\tdes Arztes " + LANR_GUELTIG,
                "I. K. N. R. " + IK_GUELTIG, "I\tK\tN\tR: " + IK_GUELTIG);
        int[] dritte = fehlalarmScannerebene(richtig, korrekt);
        assertEquals(4, dritte[1], "alle Texte haben ein Weit-Kontextwort");
        assertEquals(0, dritte[2], "ein korrektes Teil 2 wird nie gemeldet");
        for (String text : korrekt) {
            assertTrue(neuAbgedeckt(richtig, text) > 0, "das korrekte Teil 2 deckt eine zusaetzliche Position ab");
        }
    }

    // ------------------------------------------------------------------ Selbsttests der Pruefregel

    @Test
    void selbsttest_scannerebene_verkuerzterNeuStandWirdGemeldet() {
        TextScanner ohneKv = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
                new UrlRecognizer(), new PathRecognizer()));
        List<String> texte = List.of("LANR " + LANR_GUELTIG, "IK " + IK_GUELTIG);
        assertEquals(0, verloreneTexte(ALT, texte), "Kopie gegen Kopie: nichts verloren");
        assertEquals(2, verloreneTexte(ohneKv, texte), "Neuer Stand ohne KV-Erkenner: beide Texte verlieren");
    }

    @Test
    void selbsttest_scannerebene_nurNichtKvAbdeckung_wirdAlsTypverlustGemeldet() {
        TextScanner nurText = new TextScanner(List.of(text -> List.of(
                new Finding(FindingType.TEXT, text, 0, text.length(), 1.0, "text"))));
        List<String> texte = List.of("LANR " + LANR_GUELTIG, "IK " + IK_GUELTIG);
        assertEquals(2, typVerlusteTexte(nurText, texte), "KV-Position nur von Nicht-KV-Typ abgedeckt: beide zaehlen");
        assertEquals(0, typVerlusteTexte(ALT, texte), "Gegenprobe Kopie gegen Kopie");
        assertEquals(0, verloreneTexte(nurText, texte), "Position bleibt abgedeckt: kein verlorener Text");
    }

    private static Finding einFund() {
        List<Finding> funde = KvTestHilfen.LANR_ART.standLuecken.detect("LANR " + LANR_GUELTIG);
        assertEquals(1, funde.size(), "Ausgangsfund");
        return funde.get(0);
    }

    private static Finding ikFund(double konfidenz, String quelle) {
        Finding basis = KvTestHilfen.IK_ART.standLuecken.detect("IK " + IK_GUELTIG).get(0);
        return new Finding(basis.type(), basis.originalValue(), basis.start(), basis.end(), konfidenz, quelle);
    }

    @Test
    void selbsttest_gleicheListeUndZusaetzlicherFund_werdenNichtGemeldet() {
        Finding f = einFund();
        assertFalse(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(f)));
        assertFalse(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(), List.of(f)),
                "zusaetzlicher Fund ist erlaubt");
        Finding hoeher = new Finding(f.type(), f.originalValue(), f.start(), f.end(), 1.0, f.source());
        assertFalse(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(hoeher)),
                "hoehere Konfidenz ist erlaubt");
    }

    @Test
    void selbsttest_fehlenderFund_wirdGemeldet() {
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(einFund()), List.of()));
    }

    @Test
    void selbsttest_gesenkteKonfidenz_wirdGemeldet() {
        Finding f = einFund();
        Finding gesenkt = new Finding(f.type(), f.originalValue(), f.start(), f.end(), f.confidence() - 0.1,
                f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(gesenkt)));
    }

    @Test
    void selbsttest_geschrumpfterBereich_wirdGemeldet() {
        Finding f = einFund();
        Finding kuerzer = new Finding(f.type(), f.originalValue().substring(0, f.originalValue().length() - 1),
                f.start(), f.end() - 1, f.confidence(), f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(kuerzer)));
    }

    @Test
    void selbsttest_verschobenerBereich_wirdGemeldet() {
        Finding f = einFund();
        Finding verschoben = new Finding(f.type(), f.originalValue().substring(1) + "x", f.start() + 1, f.end() + 1,
                f.confidence(), f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(verschoben)));
    }

    @Test
    void selbsttest_andererTyp_wirdGemeldet() {
        Finding f = einFund();
        Finding ik = ikFund(f.confidence(), "ik");
        Finding andererTyp = new Finding(ik.type(), f.originalValue(), f.start(), f.end(), f.confidence(), f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(andererTyp)));
    }

    @Test
    void selbsttest_andereQuelle_wirdGemeldet() {
        Finding f = einFund();
        Finding andereQuelle = new Finding(f.type(), f.originalValue(), f.start(), f.end(), f.confidence(),
                "lanr-laenge-abweichend");
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(List.of(f), List.of(andereQuelle)));
    }

    @Test
    void selbsttest_ik14gSenkungVon080Auf050_wirdGemeldetObwohlDieAltePruefregelSieErlaubt() {
        List<Finding> alt = List.of(ikFund(0.80, "ik"));
        List<Finding> neu = List.of(ikFund(0.50, "ik"));
        assertFalse(KvTestHilfen.verletztBestandsschutz(alt, neu), "alte Regel: Ausnahme 14g");
        assertTrue(KvTestHilfen.verletztBestandsschutzOhneAusnahme(alt, neu), "neue Regel: keine Ausnahme");
    }

    @Test
    void selbsttest_zeichenkonstanten() {
        assertEquals(0x200E, KvTestHilfen.LRM);
        assertEquals(0x2002, KvTestHilfen.EN_SPACE);
    }

    // ------------------------------------------------------------------ T-6: Abdeckung je KV-Typ (Plan 9a, Luecke b)

    private static boolean[] abdeckungTyp(int laenge, List<Finding> funde, FindingType typ) {
        return abdeckung(laenge, funde.stream().filter(f -> f.type() == typ).toList());
    }

    /**
     * Zahl der Positionen, die {@code neu} von einem Fund des Typs {@code typ} abdeckt, die die Kopie weder auf dem
     * Originaltext noch auf der Wort-Vergleichsform beider Arten mit demselben Typ abdeckt: Eine neu von Typ X
     * abgedeckte Position muss die Kopie auf der Vergleichsform mit Typ X abdecken (LANR und IK getrennt).
     */
    private static int neuUnerklaertJeTyp(TextScanner neu, String text, FindingType typ) {
        boolean[] jetzt = abdeckungTyp(text.length(), neu.scan(text), typ);
        boolean[] alt = abdeckungTyp(text.length(), ALT.scan(text), typ);
        KvTestHilfen.Vergleichsform vf = KvTestHilfen.wortVergleichsform(text, KvTestHilfen.LANR_ART,
                KvTestHilfen.IK_ART);
        boolean[] erklaert = abdeckungTyp(text.length(), vf.aufOriginal(ALT.scan(vf.text())), typ);
        int zahl = 0;
        for (int i = 0; i < text.length(); i++) {
            zahl += jetzt[i] && !alt[i] && !erklaert[i] ? 1 : 0;
        }
        return zahl;
    }

    /** Zahl der Positionen, die {@code neu} von Typ {@code typ} abdeckt und die Kopie nicht (ohne Erklaerung). */
    private static int neuAbgedecktJeTyp(TextScanner neu, String text, FindingType typ) {
        boolean[] jetzt = abdeckungTyp(text.length(), neu.scan(text), typ);
        boolean[] alt = abdeckungTyp(text.length(), ALT.scan(text), typ);
        int zahl = 0;
        for (int i = 0; i < text.length(); i++) {
            zahl += jetzt[i] && !alt[i] ? 1 : 0;
        }
        return zahl;
    }

    /**
     * Je Textmenge, Texte ohne {@code CR UZ+ LF}: {geprueft, Texte mit unerklaert neu abgedeckter LANR-Position,
     * dasselbe fuer IK, Texte mit neu abgedeckter LANR-Position, dasselbe fuer IK}.
     */
    private static int[] jeKvTyp(TextScanner neu, List<String> texte) {
        int[] z = new int[5];
        FindingType[] typen = {FindingType.LANR, FindingType.IK};
        for (String text : texte) {
            if (enthaeltCrUnsichtbarLf(text)) {
                continue;
            }
            z[0]++;
            for (int k = 0; k < typen.length; k++) {
                z[1 + k] += neuUnerklaertJeTyp(neu, text, typen[k]) > 0 ? 1 : 0;
                z[3 + k] += neuAbgedecktJeTyp(neu, text, typen[k]) > 0 ? 1 : 0;
            }
        }
        return z;
    }

    private static List<String> t6Texte(String[] formen, String[] nummern) {
        List<String> texte = new ArrayList<>();
        for (String form : formen) {
            for (String nummer : nummern) {
                texte.add(form + nummer);
            }
        }
        return texte;
    }

    private static final String[] T6_LANR_FORMEN = {"L. A. N. R. ", "L. A. N. R.: ", "l. a. n. r. ", "L. A. N. R ",
        "L.  A.  N.  R. ", "L  A  N  R ", "L   A   N   R ", "L\tA\tN\tR\t", "L. A N  R ", "A. R. Z. T. N. R. ",
        "A  R  Z  T  N  U  M  M  E  R ", "L. A. N. R. N. R. "};
    private static final String[] T6_IK_FORMEN = {"I. K. N. R. ", "I  K  N  R ", "I\tK\tN\tR\t",
        "I. K. N. U. M. M. E. R. ", "I  K  N  U  M  M  E  R ", "i. k. n. r. ", "I.  K.  N.  R. ", "I. K N  R "};

    /**
     * Abdeckung je KV-Typ (Plan 9a, Restluecke (b)): Der Scanner deckt nur Positionen neu ab, die die Kopie auf der
     * Wort-Vergleichsform mit demselben Typ abdeckt; LANR und IK werden getrennt geprueft, auch mit der Nummer der
     * jeweils anderen Art hinter dem Kontextwort (der Typ folgt dem Kontextwort, nicht der Nummer). Mindestzahlen: auf
     * den festen Texten hat jeder Text einen Zugewinn des richtigen Typs; auf den Textmengen mit mehr als 30 % Abstand
     * zu den Messwerten (Texte mit LANR- bzw. IK-Zugewinn: Teil 1c 53 / 0, Korpus 3 / 0, Generatoren 102 / 49, sonst 0;
     * Untergrenzen in {@link #MIN_ZUGEWINN_LANR} und {@link #MIN_ZUGEWINN_IK}, gleiche Reihenfolge wie die Mengen).
     */
    @Test
    void t6_differenz_scannerebene_jeKvTyp_neuAbgedeckteHatDenTypDerWortVergleichsform() {
        String[] namen = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Korpus", "Generatoren"};
        List<List<String>> mengen = List.of(teil1(), teil1b(), TEIL_1C, TEIL_1D, KvDifferenzTest.korpus(), GENERATOREN);
        int[][] ergebnis = new int[namen.length][];
        StringBuilder alle = new StringBuilder();
        for (int i = 0; i < namen.length; i++) {
            ergebnis[i] = jeKvTyp(NEU, mengen.get(i));
            alle.append(namen[i]).append(": geprueft ").append(ergebnis[i][0]).append(", Zugewinn LANR ")
                    .append(ergebnis[i][3]).append(", IK ").append(ergebnis[i][4]).append("; ");
        }
        for (int i = 0; i < namen.length; i++) {
            assertEquals(0, ergebnis[i][1], namen[i] + ": unerklaert neu abgedeckte LANR-Position; " + alle);
            assertEquals(0, ergebnis[i][2], namen[i] + ": unerklaert neu abgedeckte IK-Position; " + alle);
            assertTrue(ergebnis[i][3] >= MIN_ZUGEWINN_LANR[i], namen[i] + ": zu wenig LANR-Zugewinn; " + alle);
            assertTrue(ergebnis[i][4] >= MIN_ZUGEWINN_IK[i], namen[i] + ": zu wenig IK-Zugewinn; " + alle);
        }
        String[] beide = {LANR_GUELTIG, IK_GUELTIG};
        List<String> lanr = t6Texte(T6_LANR_FORMEN, beide);
        List<String> ik = t6Texte(T6_IK_FORMEN, beide);
        int[] zl = jeKvTyp(NEU, lanr);
        assertEquals(lanr.size(), zl[0], "feste LANR-Texte geprueft");
        assertEquals(0, zl[1] + zl[2], "feste LANR-Texte: unerklaerte Position");
        assertEquals(lanr.size(), zl[3], "feste LANR-Texte: jeder hat einen LANR-Zugewinn");
        assertEquals(0, zl[4], "feste LANR-Texte: kein IK-Zugewinn");
        int[] zi = jeKvTyp(NEU, ik);
        assertEquals(ik.size(), zi[0], "feste IK-Texte geprueft");
        assertEquals(0, zi[1] + zi[2], "feste IK-Texte: unerklaerte Position");
        assertEquals(ik.size(), zi[4], "feste IK-Texte: jeder hat einen IK-Zugewinn");
        assertEquals(0, zi[3], "feste IK-Texte: kein LANR-Zugewinn");
    }

    private static final int[] MIN_ZUGEWINN_LANR = {0, 0, 35, 0, 2, 70};
    private static final int[] MIN_ZUGEWINN_IK = {0, 0, 0, 0, 0, 34};

    /** Ein Scanner, dessen Erkenner einer Art die Funde der anderen Art (Vergleichsform) mit falschem Typ liefert. */
    private static TextScanner scannerMitVertauschtemTyp(boolean ikAlsLanr) {
        KvTestHilfen.Art quelle = ikAlsLanr ? KvTestHilfen.IK_ART : KvTestHilfen.LANR_ART;
        FindingType typ = ikAlsLanr ? FindingType.LANR : FindingType.IK;
        Recognizer vertauscht = text -> {
            KvTestHilfen.Vergleichsform vf = KvTestHilfen.wortVergleichsform(text, KvTestHilfen.LANR_ART,
                    KvTestHilfen.IK_ART);
            List<Finding> funde = new ArrayList<>();
            for (Finding f : vf.aufOriginal(quelle.standLuecken.detect(vf.text()))) {
                funde.add(new Finding(typ, f.originalValue(), f.start(), f.end(), f.confidence(), f.source()));
            }
            return funde;
        };
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), ikAlsLanr ? vertauscht : KvStandLuecken.lanr(),
                ikAlsLanr ? KvStandLuecken.ik() : vertauscht));
    }

    /**
     * Selbsttest zu {@link #neuUnerklaertJeTyp}: Ein LANR-Fund hinter {@code I. K. N. R. <IK>} (der Typ folgt dem
     * Kontextwort, hier IK) und ein IK-Fund hinter {@code L. A. N. R. <LANR>} werden gemeldet; ein korrektes Teil 2 und
     * das Produkt nie. Die Abdeckung insgesamt waere gleich (nur der Typ ist falsch).
     */
    @Test
    void t6_selbsttest_scannerebene_jeKvTyp_falscherTypWirdGemeldet() {
        String ikText = "I. K. N. R. " + IK_GUELTIG;
        String lanrText = "L. A. N. R. " + LANR_GUELTIG;
        TextScanner ikAlsLanr = scannerMitVertauschtemTyp(true);
        TextScanner lanrAlsIk = scannerMitVertauschtemTyp(false);
        assertTrue(neuUnerklaertJeTyp(ikAlsLanr, ikText, FindingType.LANR) > 0, "LANR-Fund hinter I. K. N. R. ");
        assertEquals(0, neuUnerklaertJeTyp(ikAlsLanr, ikText, FindingType.IK), "kein IK-Fund im Fehlerscanner");
        assertTrue(neuUnerklaertJeTyp(lanrAlsIk, lanrText, FindingType.IK) > 0, "IK-Fund hinter L. A. N. R. ");
        assertEquals(0, neuUnerklaertJeTyp(lanrAlsIk, lanrText, FindingType.LANR), "kein LANR-Fund im Fehlerscanner");
        assertEquals(0, verloreneTexte(ikAlsLanr, List.of(ikText)), "die Abdeckung insgesamt geht nicht verloren");
        for (String text : List.of(ikText, lanrText)) {
            for (FindingType typ : new FindingType[] {FindingType.LANR, FindingType.IK}) {
                assertEquals(0, neuUnerklaertJeTyp(NEU, text, typ), "Produkt: unerklaerte Position " + typ);
                assertEquals(0, neuUnerklaertJeTyp(scannerRichtig(), text, typ), "korrektes Teil 2 " + typ);
            }
        }
        assertTrue(neuAbgedecktJeTyp(NEU, ikText, FindingType.IK) > 0, "Produkt deckt die IK-Nummer neu ab");
        assertTrue(neuAbgedecktJeTyp(NEU, lanrText, FindingType.LANR) > 0, "Produkt deckt die LANR-Nummer neu ab");
        assertEquals(0, neuAbgedecktJeTyp(NEU, ikText, FindingType.LANR), "Produkt: kein LANR-Zugewinn bei IK");
    }

    // ------------------------------------------------------------------ T-9: Teil 1e und Korpus Teil 2

    /** Siegel ueber die Funde der Kopie auf Fuzz-Teil 1e (hexadezimal, in T-9 gemessen). */
    static final int SIEGEL_LUECKEN_TEIL_1E = 0xDF08B330;

    private static List<String> teil1e() {
        List<RoundtripFuzzTest.FallE> faelle = RoundtripFuzzTest.erzeugeKvE(RoundtripFuzzTest.SEED_TEXTE_KV_E);
        List<String> texte = new ArrayList<>(faelle.size());
        for (RoundtripFuzzTest.FallE f : faelle) {
            texte.add(f.text());
        }
        return texte;
    }

    private static List<String> korpusTrenner() {
        List<String> texte = new ArrayList<>();
        for (RoundtripKorpusTest.KvFall k : RoundtripKorpusTest.kvTrennerFaelle()) {
            texte.add(k.text());
        }
        return texte;
    }

    private static final List<String> TEIL_1E = teil1e();
    private static final List<String> KORPUS_TRENNER = korpusTrenner();

    private static int verlorenGegenVergleichsform(TextScanner neu, String text) {
        boolean[] jetzt = abdeckung(text.length(), neu.scan(text));
        KvTestHilfen.Vergleichsform vf = KvTestHilfen.wortVergleichsform(text, KvTestHilfen.LANR_ART,
                KvTestHilfen.IK_ART);
        boolean[] erwartet = abdeckung(text.length(), vf.aufOriginal(ALT.scan(vf.text())));
        int zahl = 0;
        for (int i = 0; i < text.length(); i++) {
            zahl += erwartet[i] && !jetzt[i] ? 1 : 0;
        }
        return zahl;
    }

    private static final String[] T9_NAMEN = {"Teil 1e", "Korpus Teil 2"};

    private static List<List<String>> t9Mengen() {
        return List.of(TEIL_1E, KORPUS_TRENNER);
    }

    /** Eigenes Siegel fuer Teil 1e (T-9); die Siegel von Teil 1, 1b, 1c und 1d bleiben in den Tests davor. */
    @Test
    void t9_standLuecken_fundbildAufTeil1eIstEingefroren() {
        int teil1e = siegel(TEIL_1E);
        assertEquals(SIEGEL_LUECKEN_TEIL_1E, teil1e, "Siegel Teil 1e, gemessen 0x" + Integer.toHexString(teil1e));
    }

    /**
     * Differenz auf Erkennerebene ueber Teil 1e und den Korpus von Teil 2: Je Text und Art laufen die Waechter, die
     * Invarianten, der strenge Bestandsschutz gegen beide Kopien und der Fehlalarm-Pruefstand
     * ({@link KvTestHilfen#scan}); ein zusaetzlicher Fund ohne Weit-Kontextwort bricht ab (Regel 10, D-8). Die Kopie
     * liefert auf beiden Mengen Funde, die Pruefung laeuft also nicht ins Leere. Messwerte der Funde der Kopie:
     * Teil 1e LANR 732, IK 994 (Untergrenzen 500, 690), Korpus Teil 2 je 3 (je 2).
     */
    @Test
    void t9_differenz_erkennerebene_teil1eUndKorpusTrenner() {
        int[] minLanr = {500, 2};
        int[] minIk = {690, 2};
        for (int i = 0; i < T9_NAMEN.length; i++) {
            pruefeMitWaechter(t9Mengen().get(i));
            int lanr = zaehleAlteFunde(KvTestHilfen.LANR_ART, t9Mengen().get(i));
            int ik = zaehleAlteFunde(KvTestHilfen.IK_ART, t9Mengen().get(i));
            assertTrue(lanr >= minLanr[i], T9_NAMEN[i] + ": zu wenig alte LANR-Funde " + lanr);
            assertTrue(ik >= minIk[i], T9_NAMEN[i] + ": zu wenig alte IK-Funde " + ik);
        }
    }

    /**
     * Differenz auf Scannerebene ueber Teil 1e und den Korpus von Teil 2: keine alt abgedeckte Position geht
     * verloren, keine alt von einem KV-Fund abgedeckte Position wird nur noch von einem Nicht-KV-Fund abgedeckt, und
     * die Abdeckung der Wort-Vergleichsform (die Kopie auf dem Text mit je einem Leerzeichen je erweiterter Luecke)
     * bleibt vollstaendig erhalten (AK-21, AK-22). Der Scanner mit der Kopie liefert auf beiden Mengen KV-Funde.
     */
    @Test
    void t9_differenz_scannerebene_teil1eUndKorpusTrenner() {
        for (int i = 0; i < T9_NAMEN.length; i++) {
            List<String> texte = t9Mengen().get(i);
            assertEquals(0, verloreneTexte(NEU, texte), T9_NAMEN[i] + ": Texte mit verlorener Position");
            assertEquals(0, typVerlusteTexte(NEU, texte), T9_NAMEN[i] + ": Texte mit Typverlust");
            int gegenVergleichsform = 0;
            for (String text : texte) {
                gegenVergleichsform += verlorenGegenVergleichsform(NEU, text) > 0 ? 1 : 0;
            }
            assertEquals(0, gegenVergleichsform, T9_NAMEN[i] + ": Texte mit Verlust gegen die Vergleichsform");
            assertTrue(alteKvScannerFunde(texte) > 0, T9_NAMEN[i] + ": Kopie ohne KV-Funde auf Scannerebene");
        }
    }

    /**
     * Fehlalarm-Pruefstand ueber Teil 1e und den Korpus von Teil 2 (Plan 9a, Regel 10): Auf Scannerebene deckt der
     * Scanner keine Position unerklaert neu ab ({@link #fehlalarmScannerebene}), und je Art gibt es weder eine
     * Abweichung ohne Weit-Kontextwort und ohne {@code CR UZ+ LF} noch eine Verletzung des strengen Bestandsschutzes
     * ({@link #ungleichOhneErklaerung}). Mindestzahlen mit mehr als 30 % Abstand zu den Messwerten (Teil 1e / Korpus):
     * geprueft ohne Ausnahme 26 / 47 (18, 32), ausgenommen 974 / 54 (680, 37), alte KV-Funde in den geprueften
     * Texten 39 / 4 (27, 2), Paare (Text, Art) mit Weit-Kontextwort ohne {@code CR UZ+ LF} 1 247 / 52 (870, 36).
     */
    @Test
    void t9_fehlalarm_teil1eUndKorpusTrenner_keineUnerklaertNeuAbgedecktePositionUndKeineAbweichung() {
        int[] minGeprueft = {18, 32};
        int[] minAusgenommen = {680, 37};
        int[] minAlteFunde = {27, 2};
        int[] minPaare = {870, 36};
        for (int i = 0; i < T9_NAMEN.length; i++) {
            List<String> texte = t9Mengen().get(i);
            int[] z = fehlalarmScannerebene(NEU, texte);
            assertEquals(0, z[2], T9_NAMEN[i] + ": Texte mit unerklaert neu abgedeckter Position");
            assertTrue(z[0] >= minGeprueft[i], T9_NAMEN[i] + ": zu wenig geprueft " + z[0]);
            assertTrue(z[1] >= minAusgenommen[i], T9_NAMEN[i] + ": zu wenig ausgenommen " + z[1]);
            assertTrue(z[3] >= minAlteFunde[i], T9_NAMEN[i] + ": zu wenig alte KV-Funde " + z[3]);
            int[] paare = ungleichOhneErklaerung(texte);
            assertEquals(0, paare[0], T9_NAMEN[i] + ": Abweichung ohne die Folge und ohne Weit-Kontextwort");
            assertEquals(0, paare[2], T9_NAMEN[i] + ": Bestandsschutz verletzt");
            assertTrue(paare[1] >= minPaare[i], T9_NAMEN[i] + ": zu wenig Paare mit Weit-Kontextwort " + paare[1]);
        }
    }

    /**
     * Zugewinn-Beleg (Plan T-9, AK-25): Auf Teil 1e und dem Korpus von Teil 2 deckt das Produkt in vielen Texten ohne
     * {@code CR UZ+ LF} Positionen ab, die die Kopie nicht abdeckt (Teil 2 wirkt), und jeder Zugewinn hat den Typ der
     * Wort-Vergleichsform ({@link #jeKvTyp}, unerklaerte Positionen 0). Messwerte (Teil 1e / Korpus): Texte mit neu
     * abgedeckter Position 723 / 46 (Untergrenzen 500, 32), davon mit LANR-Zugewinn 608 / 38 (420, 26) und
     * IK-Zugewinn 595 / 9 (410, 6), Texte ohne {@code CR UZ+ LF} 769 / 98 (530, 68), Texte mit der Folge 231 / 3
     * (160, 2). Mit der Folge weicht die Fundliste von der der Kopie auf 398 / 2 Paaren (Text, Art) ab (275, 1).
     */
    @Test
    void t9_zugewinn_teil1eUndKorpusTrenner_neuAbgedeckteTextePositionenUndTyp() {
        int[] minZugewinn = {500, 32};
        int[] minLanr = {420, 26};
        int[] minIk = {410, 6};
        int[] minOhneFolge = {530, 68};
        int[] minMitFolge = {160, 2};
        int[] minAbweichend = {275, 1};
        for (int i = 0; i < T9_NAMEN.length; i++) {
            List<String> texte = t9Mengen().get(i);
            int zugewinn = 0;
            int mitFolge = 0;
            for (String text : texte) {
                mitFolge += enthaeltCrUnsichtbarLf(text) ? 1 : 0;
                zugewinn += !enthaeltCrUnsichtbarLf(text) && neuAbgedeckt(NEU, text) > 0 ? 1 : 0;
            }
            int[] typ = jeKvTyp(NEU, texte);
            assertEquals(0, typ[1], T9_NAMEN[i] + ": unerklaert neu abgedeckte LANR-Position");
            assertEquals(0, typ[2], T9_NAMEN[i] + ": unerklaert neu abgedeckte IK-Position");
            assertTrue(zugewinn >= minZugewinn[i], T9_NAMEN[i] + ": zu wenig Texte mit Zugewinn " + zugewinn);
            assertTrue(typ[3] >= minLanr[i], T9_NAMEN[i] + ": zu wenig LANR-Zugewinn " + typ[3]);
            assertTrue(typ[4] >= minIk[i], T9_NAMEN[i] + ": zu wenig IK-Zugewinn " + typ[4]);
            assertTrue(typ[0] >= minOhneFolge[i], T9_NAMEN[i] + ": zu wenig Texte ohne die Folge " + typ[0]);
            assertTrue(mitFolge >= minMitFolge[i], T9_NAMEN[i] + ": zu wenig Texte mit der Folge " + mitFolge);
            assertEquals(texte.size(), typ[0] + mitFolge, T9_NAMEN[i] + ": jeder Text ist einmal eingeordnet");
            int abweichend = ungleicheTexte(texte)[1];
            assertTrue(abweichend >= minAbweichend[i], T9_NAMEN[i] + ": zu wenig Abweichungen mit Folge " + abweichend);
        }
    }

    /**
     * Selbsttest zu {@link #verlorenGegenVergleichsform}: Ein Scanner ohne KV-Erkenner verliert die Position, die die
     * Kopie auf der Wort-Vergleichsform abdeckt ({@code L. A. N. R. <LANR>} wird zu {@code L A N R <LANR>}); das
     * Produkt und die Kopie auf einem Text ohne Weit-Kontextwort verlieren nichts.
     */
    @Test
    void t9_selbsttest_verlustGegenDieVergleichsformWirdGemeldet() {
        TextScanner ohneKv = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
                new UrlRecognizer(), new PathRecognizer()));
        for (String text : List.of("L. A. N. R. " + LANR_GUELTIG, "L  A  N  R " + LANR_GUELTIG,
                "I\tK\tN\tR\t" + IK_GUELTIG, LANR_GUELTIG + " (L. A. N. R.)")) {
            assertTrue(verlorenGegenVergleichsform(ohneKv, text) > 0, "Scanner ohne KV verliert Positionen");
            assertEquals(0, verlorenGegenVergleichsform(NEU, text), "Produkt verliert nichts");
        }
        assertEquals(0, verlorenGegenVergleichsform(ALT, "LANR " + LANR_GUELTIG), "Text ohne Weit-Kontextwort");
        assertTrue(verlorenGegenVergleichsform(ohneKv, "LANR " + LANR_GUELTIG) > 0, "Text ohne Weit-Kontextwort");
    }
}
