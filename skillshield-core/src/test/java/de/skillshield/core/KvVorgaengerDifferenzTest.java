package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
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
import de.skillshield.core.recognizer.kv.KvTestHilfen;
import de.skillshield.core.recognizer.kv.KvVorgaenger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vergleich des heutigen Standes gegen den eingefrorenen Vorgaenger ({@code KvNumberScannerVorgaenger}, Stand vor
 * kv-kontext-luecken; T-1, AK-22, AK-25, AK-26).
 *
 * <p>Siegel: Ein Hash ueber alle Funde des Vorgaengers auf Fuzz-Teil 1 und Teil 1b haelt die Kopie fest; aendert sich
 * die Kopie oder einer der Generatoren, schlaegt er an. Differenzpruefung: Erkennerebene je Art
 * ({@link KvTestHilfen#pruefeGegenVorgaenger}) und Scannerebene ({@code TextScanner} mit den Erkennern des
 * Vorgaengers gegen {@code TextScanner.standard()}: jede alt abgedeckte Position ist neu abgedeckt, und eine alt von
 * einem KV-Fund (LANR, IK) abgedeckte Position ist neu nicht nur von einem Nicht-KV-Fund abgedeckt; ein Typwechsel
 * zwischen IK und LANR und die Stufenaenderung durch Zusammenlegung bleiben erlaubt). Selbsttests
 * belegen, dass die Pruefregel nicht ins Leere laeuft. Meldungen nennen nur Zahlen, nie Text oder Ziffern.
 *
 * <p>Teil 1c (T-6: Sperrschrift, Komma vor dem Kontextwort), der erweiterte Korpus ({@code KvDifferenzTest.korpus()}
 * enthaelt die Faelle des Roundtrip-Korpus) und die Texte der Generatoren (a) bis (d) aus
 * {@code KvKontextLueckenEigenschaftTest} laufen durch dieselben Pruefungen. Teil 1c ist zusaetzlich mit einem
 * eigenen Siegel festgehalten und muss neuen Schutz bringen (Texte, in denen der neue Scanner Positionen abdeckt,
 * die der alte nicht abdeckt).
 *
 * <p>Teil 1 ist die unveraenderte Altfolge und wird (wie im {@code KvDifferenzTest}) nicht mit dem Waechter geprueft;
 * Teil 1b und der Korpus gehen vor dem Vergleich durch den Waechter.
 */
class KvVorgaengerDifferenzTest {

    /** Siegel ueber die Funde des Vorgaengers auf Fuzz-Teil 1 (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TEIL_1 = 0xE79FC611;
    /** Siegel ueber die Funde des Vorgaengers auf Fuzz-Teil 1b (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TEIL_1B = 0x62D65251;

    /** Siegel ueber die Funde des Vorgaengers auf Fuzz-Teil 1c (hexadezimal, in T-6 gemessen). */
    static final int SIEGEL_TEIL_1C = 0x9099FBAD;

    /** Mindestzahlen (Messwerte bei festen Seeds: Teil 1c 980 von 1 000, Generatoren 2 014 von 4 000 Texten). */
    static final int MIN_TEXTE_MIT_MEHR_ABDECKUNG_1C = 650;
    static final int MIN_TEXTE_MIT_MEHR_ABDECKUNG_GENERATOREN = 1_300;

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
            new UrlRecognizer(), new PathRecognizer(), KvVorgaenger.lanr(), KvVorgaenger.ik()));

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
        return texte1c(RoundtripFuzzTest.erzeugeKvC(RoundtripFuzzTest.SEED_TEXTE_KV_C));
    }

    private static List<String> texte1c(List<RoundtripFuzzTest.FallC> faelle) {
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
                List<Finding> funde = a.vorgaenger.detect(text);
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
    void vorgaenger_fundbildIstEingefroren() {
        int teil1 = siegel(teil1());
        int teil1b = siegel(teil1b());
        assertEquals(SIEGEL_TEIL_1, teil1, "Siegel Teil 1, gemessen 0x" + Integer.toHexString(teil1));
        assertEquals(SIEGEL_TEIL_1B, teil1b, "Siegel Teil 1b, gemessen 0x" + Integer.toHexString(teil1b));
    }

    @Test
    void vorgaenger_fundbildAufTeil1cIstEingefroren() {
        int teil1c = siegel(TEIL_1C);
        assertEquals(SIEGEL_TEIL_1C, teil1c, "Siegel Teil 1c, gemessen 0x" + Integer.toHexString(teil1c));
    }

    // ------------------------------------------------------------------ Differenz Erkennerebene

    /** Zaehlt Verletzungen des Bestandsschutzes ohne Waechter (Teil 1 ist die unveraenderte Altfolge). */
    private static int zaehleVerletzungen(List<String> texte) {
        int verletzungen = 0;
        for (String text : texte) {
            for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
                if (KvTestHilfen.verletztBestandsschutz(a.vorgaenger.detect(text), a.erkenner.detect(text))) {
                    verletzungen++;
                }
            }
        }
        return verletzungen;
    }

    /** Prueft Waechter, Invarianten und Differenz je Art ueber {@code scan}; ein Verstoss laesst den Test scheitern. */
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
            summe += a.vorgaenger.detect(text).size();
        }
        return summe;
    }

    @Test
    void differenz_erkennerebene_teil1() {
        assertEquals(0, zaehleVerletzungen(teil1()), "Teil 1: Verletzungen des Bestandsschutzes");
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
    void differenz_vorgaengerLiefertFundeAufTeil1cUndGeneratoren() {
        for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
            assertTrue(zaehleAlteFunde(a, TEIL_1C) > 0, a.name + ": Vorgaenger ohne Funde auf Teil 1c");
            assertTrue(zaehleAlteFunde(a, GENERATOREN) > 0,
                    a.name + ": Vorgaenger ohne Funde auf den Generatoren");
        }
    }

    @Test
    void differenz_vorgaengerLiefertFundeAufTeil1bUndKorpus() {
        for (KvTestHilfen.Art a : KvTestHilfen.ARTEN) {
            assertTrue(zaehleAlteFunde(a, teil1b()) > 0, a.name + ": Vorgaenger ohne Funde auf Teil 1b");
            assertTrue(zaehleAlteFunde(a, KvDifferenzTest.korpus()) > 0, a.name + ": Vorgaenger ohne Funde im Korpus");
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

    private static int verloreneTexte(List<String> texte) {
        return verloreneTexte(NEU, texte);
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

    @Test
    void differenz_scannerebene_keineAbgedeckePositionGehtVerloren() {
        assertEquals(0, verloreneTexte(teil1()), "Teil 1: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(teil1b()), "Teil 1b: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(KvDifferenzTest.korpus()), "Korpus: Texte mit verlorener Position");
    }

    @Test
    void differenz_scannerebene_teil1cUndGeneratoren_keineAbgedeckePositionGehtVerloren() {
        List<String> generatoren = GENERATOREN;
        pruefeMitWaechter(TEIL_1C);
        pruefeMitWaechter(generatoren);
        assertEquals(0, verloreneTexte(TEIL_1C), "Teil 1c: Texte mit verlorener Position");
        assertEquals(0, verloreneTexte(generatoren), "Generatoren (a) bis (d): Texte mit verlorener Position");
    }

    /** Zaehlt die Texte, in denen der neue Scanner eine Position abdeckt, die der alte nicht abdeckt. */
    private static int mehrAbdeckungTexte(List<String> texte) {
        int mehr = 0;
        for (String text : texte) {
            boolean[] imAlten = abdeckung(text.length(), ALT.scan(text));
            boolean[] imNeuen = abdeckung(text.length(), NEU.scan(text));
            boolean gewonnen = false;
            for (int i = 0; i < text.length(); i++) {
                gewonnen |= imNeuen[i] && !imAlten[i];
            }
            if (gewonnen) {
                mehr++;
            }
        }
        return mehr;
    }

    @Test
    void differenz_scannerebene_teil1cBringtNeuenSchutz_pruefungLaeuftNichtInsLeere() {
        int mehr = mehrAbdeckungTexte(TEIL_1C);
        assertTrue(mehr >= MIN_TEXTE_MIT_MEHR_ABDECKUNG_1C, "Teil 1c: Texte mit zusaetzlich abgedeckter Position: "
                + mehr + ", erwartet mindestens " + MIN_TEXTE_MIT_MEHR_ABDECKUNG_1C);
        int generatoren = mehrAbdeckungTexte(GENERATOREN);
        assertTrue(generatoren >= MIN_TEXTE_MIT_MEHR_ABDECKUNG_GENERATOREN, "Generatoren: Texte mit zusaetzlich "
                + "abgedeckter Position: " + generatoren + ", erwartet mindestens "
                + MIN_TEXTE_MIT_MEHR_ABDECKUNG_GENERATOREN);
        // Gegenprobe: ein Text ohne neue Form bringt keinen Zugewinn
        assertEquals(0, mehrAbdeckungTexte(List.of("LANR " + LANR_GUELTIG)), "Bestandsform: kein Zugewinn");
        assertEquals(1, mehrAbdeckungTexte(List.of(LANR_GUELTIG + ", LANR")), "Komma-Form: Zugewinn");
    }

    private static boolean istKv(Finding f) {
        return f.type() == FindingType.LANR || f.type() == FindingType.IK;
    }

    /**
     * Zaehlt die Texte, in denen eine alt von einem KV-Fund abgedeckte Position neu zwar abgedeckt ist, aber nur von
     * einem Fund eines Nicht-KV-Typs (die Abdeckung allein wuerde das nicht melden).
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

    @Test
    void differenz_scannerebene_teil1cUndGeneratoren_kvPositionWirdNieNurVonNichtKvTypAbgedeckt() {
        assertEquals(0, typVerlusteTexte(NEU, TEIL_1C), "Teil 1c: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, GENERATOREN),
                "Generatoren: Texte mit KV-Position nur noch als Nicht-KV");
    }

    @Test
    void differenz_scannerebene_kvPositionWirdNieNurVonNichtKvTypAbgedeckt() {
        assertEquals(0, typVerlusteTexte(NEU, teil1()), "Teil 1: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, teil1b()), "Teil 1b: Texte mit KV-Position nur noch als Nicht-KV");
        assertEquals(0, typVerlusteTexte(NEU, KvDifferenzTest.korpus()), "Korpus: Texte mit Typverlust");
    }

    /**
     * Zaehlt die Texte, in denen eine alt nur von einem Nicht-KV-Fund abgedeckte Position neu von einem KV-Fund
     * abgedeckt wird (zusaetzlicher Schutz); nur vom Selbsttest unten benutzt, kein Sollwert auf dem Korpus.
     */
    private static int nichtKvZuKvTexte(TextScanner neu, List<String> texte) {
        int gewonnen = 0;
        for (String text : texte) {
            boolean[] kvNeu = abdeckung(text.length(), neu.scan(text).stream().filter(f -> istKv(f)).toList());
            List<Finding> altFunde = ALT.scan(text);
            boolean[] kvAlt = abdeckung(text.length(), altFunde.stream().filter(f -> istKv(f)).toList());
            boolean neuKv = false;
            for (Finding f : altFunde) {
                for (int i = f.start(); !istKv(f) && i < f.end(); i++) {
                    neuKv |= !kvAlt[i] && kvNeu[i];
                }
            }
            if (neuKv) {
                gewonnen++;
            }
        }
        return gewonnen;
    }

    @Test
    void selbsttest_nichtKvPositionNeuVonKvFundAbgedeckt_wirdGezaehlt() {
        // Synthetische Dokumentations-IP: alt nur der IP-Fund. Ein Stand, der dieselbe Position mit einem staerkeren
        // KV-Fund abdeckt (Selbsttest-Erkenner, Konfidenz 0,99 gegen 0,85 der IP), wird gezaehlt; der Standardscanner
        // (kein Kontextwort) und der alte Scanner nicht.
        String text = "192.0.2.1 " + LAENGE_7;
        assertEquals(0, KvTestHilfen.neunerfenster(text, "00")[1], "Waechter Selbsttest");
        List<Finding> alt = ALT.scan(text);
        assertEquals(1, alt.size(), "alt: genau ein Fund");
        assertEquals(FindingType.IP, alt.get(0).type(), "alt: Nicht-KV-Fund (IP)");
        Recognizer staerkererKv = t -> List.of(
                new Finding(FindingType.LANR, t.substring(0, 9), 0, 9, 0.99, "selbsttest"));
        TextScanner neuMitKv = new TextScanner(List.of(new Ipv4Recognizer(), staerkererKv));
        assertEquals(1, nichtKvZuKvTexte(neuMitKv, List.of(text)), "IP-Position neu von KV-Fund abgedeckt: gezaehlt");
        assertEquals(0, nichtKvZuKvTexte(ALT, List.of(text)), "alter gegen alten Scanner: nichts gewonnen");
        assertEquals(0, nichtKvZuKvTexte(NEU, List.of(text)), "Standardscanner ohne Kontextwort: nichts gewonnen");
    }

    @Test
    void selbsttest_typVerlust_wirdGemeldet() {
        Recognizer alsEmail = text -> {
            List<Finding> umgetypt = new ArrayList<>();
            for (Finding f : KvTestHilfen.LANR_ART.vorgaenger.detect(text)) {
                umgetypt.add(new Finding(FindingType.EMAIL, f.originalValue(), f.start(), f.end(), f.confidence(),
                        f.source()));
            }
            return umgetypt;
        };
        TextScanner nurNichtKv = new TextScanner(List.of(alsEmail));
        List<String> texte = List.of("LANR " + LANR_GUELTIG);
        assertEquals(0, verloreneTexte(nurNichtKv, texte), "Position bleibt abgedeckt");
        assertEquals(1, typVerlusteTexte(nurNichtKv, texte), "Abdeckung nur durch Nicht-KV-Typ wird gemeldet");
        assertEquals(0, typVerlusteTexte(NEU, texte), "Standardscanner: KV bleibt KV");
        assertEquals(0, typVerlusteTexte(ALT, texte), "Alter gegen alten Scanner");
    }

    @Test
    void differenz_scannerebene_alterScannerLiefertFundeAufTeil1bUndKorpus() {
        for (List<String> texte : List.of(teil1b(), TEIL_1C, KvDifferenzTest.korpus(),
                GENERATOREN)) {
            int funde = 0;
            for (String text : texte) {
                funde += ALT.scan(text).size();
            }
            assertTrue(funde > 0, "Alter Scanner ohne Funde (Pruefung liefe ins Leere)");
        }
    }

    // ------------------------------------------------------------------ Selbsttests der Pruefregel

    @Test
    void selbsttest_scannerebene_verkuerzterNeuStandWirdGemeldet() {
        TextScanner ohneKv = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
                new UrlRecognizer(), new PathRecognizer()));
        List<String> texte = List.of("LANR " + LANR_GUELTIG, "IK " + IK_GUELTIG);
        assertEquals(0, verloreneTexte(ALT, texte), "Alter gegen alten Scanner: nichts verloren");
        assertEquals(2, verloreneTexte(ohneKv, texte), "Neuer Stand ohne KV-Erkenner: beide Texte verlieren");
    }

    private static Finding einFund() {
        List<Finding> funde = KvTestHilfen.LANR_ART.vorgaenger.detect("LANR " + LANR_GUELTIG);
        assertEquals(1, funde.size(), "Ausgangsfund");
        return funde.get(0);
    }

    @Test
    void selbsttest_gleicheListe_wirdNichtGemeldet() {
        Finding f = einFund();
        assertFalse(KvTestHilfen.verletztBestandsschutz(List.of(f), List.of(f)));
        assertFalse(KvTestHilfen.verletztBestandsschutz(List.of(), List.of(f)), "zusaetzlicher Fund ist erlaubt");
    }

    @Test
    void selbsttest_fehlenderFund_wirdErkannt() {
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(einFund()), List.of()));
    }

    @Test
    void selbsttest_gesenkteKonfidenz_wirdErkannt() {
        Finding f = einFund();
        Finding gesenkt = new Finding(f.type(), f.originalValue(), f.start(), f.end(), f.confidence() - 0.1,
                f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(f), List.of(gesenkt)));
    }

    @Test
    void selbsttest_verschobenerBereich_wirdErkannt() {
        Finding f = einFund();
        Finding verschoben = new Finding(f.type(), f.originalValue().substring(1) + "x", f.start() + 1, f.end() + 1,
                f.confidence(), f.source());
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(f), List.of(verschoben)));
    }

    @Test
    void selbsttest_andererTyp_wirdErkannt() {
        Finding f = einFund();
        Finding ik = KvTestHilfen.IK_ART.vorgaenger.detect("IK " + IK_GUELTIG).get(0);
        Finding andererTyp = new Finding(ik.type(), f.originalValue(), f.start(), f.end(), f.confidence(), "ik");
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(f), List.of(andererTyp)));
    }

    private static Finding ikFund(double konfidenz, String quelle) {
        Finding basis = KvTestHilfen.IK_ART.vorgaenger.detect("IK " + IK_GUELTIG).get(0);
        return new Finding(basis.type(), basis.originalValue(), basis.start(), basis.end(), konfidenz, quelle);
    }

    @Test
    void selbsttest_erlaubteAusnahmeIkVon080Auf050_wirdNichtGemeldet() {
        assertFalse(KvTestHilfen.verletztBestandsschutz(List.of(ikFund(0.80, "ik")), List.of(ikFund(0.50, "ik"))));
    }

    @Test
    void selbsttest_senkungVon092Auf050_wirdAuchBeiQuelleIkGemeldet() {
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(ikFund(0.92, "ik")), List.of(ikFund(0.50, "ik"))),
                "bisher sicherer Fund darf nicht auf 0,50 sinken");
    }

    @Test
    void selbsttest_ausnahmeNurBeiQuelleIkBeidseitig_sonstGemeldet() {
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(ikFund(0.80, "ik")),
                List.of(ikFund(0.50, "ik-pruefziffer-ungueltig"))), "neue Quelle ungleich ik");
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(ikFund(0.80, "ik-laenge-abweichend")),
                List.of(ikFund(0.50, "ik"))), "alte Quelle ungleich ik");
        assertTrue(KvTestHilfen.verletztBestandsschutz(List.of(ikFund(0.80, "ik")), List.of(ikFund(0.40, "ik"))),
                "neue Konfidenz ungleich 0,50");
    }

    @Test
    void selbsttest_quelleNachNummer_folgtNurDerNummer() {
        assertEquals("lanr", KvTestHilfen.LANR_ART.quelleNachNummer(LANR_GUELTIG));
        assertEquals("ik", KvTestHilfen.IK_ART.quelleNachNummer(IK_GUELTIG));
        assertEquals("ik-pruefziffer-ungueltig", KvTestHilfen.IK_ART.quelleNachNummer(KvTestHilfen.IK_ART.ungueltig));
        assertEquals("lanr-laenge-abweichend",
                KvTestHilfen.LANR_ART.quelleNachNummer(LANR_GUELTIG.substring(0, 7)));
    }

    @Test
    void selbsttest_zeichenkonstanten() {
        assertEquals(0x2011, KvTestHilfen.GESCH_BINDESTRICH);
        assertEquals(0x2013, KvTestHilfen.HALBGEVIERT);
    }
}
