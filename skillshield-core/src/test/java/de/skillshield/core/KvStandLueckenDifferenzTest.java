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
 * <p>Siegel: Ein Hash ueber alle Funde der Kopie auf Fuzz-Teil 1, 1b und 1c haelt sie fest; aendert sich die Kopie
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

    /** Einmal erzeugt und von allen Tests wiederverwendet (die Listen werden nur gelesen). */
    private static final List<String> TEIL_1C = teil1c();
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
     * T-2 (Teil 1 wirkt nur auf CR UZ+ LF): Auf jedem Text ohne diese Folge sind die Fundlisten des Produkts und der
     * eingefrorenen Kopie gleich (zusaetzliche Funde gibt es nur mit der Folge).
     */
    @Test
    void t2_zusaetzlicheFundeNurInTextenMitCrUnsichtbarLf() {
        assertEquals(0, ungleicheTexte(teil1())[0], "Teil 1: Abweichung ohne die Folge");
        assertEquals(0, ungleicheTexte(teil1b())[0], "Teil 1b: Abweichung ohne die Folge");
        assertEquals(0, ungleicheTexte(TEIL_1C)[0], "Teil 1c: Abweichung ohne die Folge");
        assertEquals(0, ungleicheTexte(KvDifferenzTest.korpus())[0], "Korpus: Abweichung ohne die Folge");
        assertEquals(0, ungleicheTexte(GENERATOREN)[0], "Generatoren: Abweichung ohne die Folge");
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
    void differenz_erkennerebene_generatorenAusDenEigenschaftstests() {
        pruefeMitWaechter(GENERATOREN);
    }

    @Test
    void differenz_standLueckenLiefertFunde_pruefungLaeuftNichtInsLeere() {
        for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
            assertTrue(zaehleAlteFunde(a, teil1b()) > 0, a.name + ": Kopie ohne Funde auf Teil 1b");
            assertTrue(zaehleAlteFunde(a, TEIL_1C) > 0, a.name + ": Kopie ohne Funde auf Teil 1c");
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
        assertEquals(0, verloreneTexte(NEU, GENERATOREN), "Generatoren: Texte mit verlorener Position");
    }

    @Test
    void differenz_scannerebene_kvPositionWirdNieNurVonNichtKvTypAbgedeckt() {
        assertEquals(0, typVerlusteTexte(NEU, teil1()), "Teil 1: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, teil1b()), "Teil 1b: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, KvDifferenzTest.korpus()), "Korpus: Texte mit Typverlust");
        assertEquals(0, typVerlusteTexte(NEU, TEIL_1C), "Teil 1c: Texte mit Typverlust");
        assertEquals(0, typVerlusteTexte(NEU, GENERATOREN), "Generatoren: Texte mit Typverlust");
    }

    @Test
    void differenz_scannerebene_alterScannerLiefertKvFunde_pruefungLaeuftNichtInsLeere() {
        for (List<String> texte : List.of(teil1b(), TEIL_1C, KvDifferenzTest.korpus(), GENERATOREN)) {
            assertTrue(alteKvScannerFunde(texte) > 0, "Scanner mit der Kopie ohne KV-Funde (Pruefung liefe ins Leere)");
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
}
