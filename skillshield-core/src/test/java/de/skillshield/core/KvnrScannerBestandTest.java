package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.KvnrKollisionsNachrechnung.Ergebnis;
import de.skillshield.core.KvnrKollisionsNachrechnung.Zuschnitt;
import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz auf Scannerebene nach der Einbindung des KVNR-Erkenners (Feature kvnr-bsnr-erkennung, T-6; AK-19,
 * AK-18, Nachtrag 3 zu T-1, Punkt 3). Die eingefrorenen Pruefungen ({@code KvStandTrennerDifferenzTest}) bleiben
 * unveraendert; diese Klasse ergaenzt sie:
 * <ul>
 * <li>Auf allen acht Bestandsmengen (Fuzz 1, 1b, 1c, 1d, 1e, Korpus, Korpus Teil 2, Generatoren; dieselben Mengen
 * wie dort) gelten Verstoesse 0, K-1 bis K-4 je 0 und S-3 ohne Abweichung; die unabhaengige Nachrechnung ueber
 * {@link KvnrKollisionsNachrechnung} findet keinen Kollisionsfall und keine ungleiche Liste.</li>
 * <li>Eine feste Menge gezielter Kollisionstexte ({@link KvnrKollisionsTexte}) hat Verstoesse strikt 0 und
 * mindestens die geforderte Zahl K-1-, K-2- und K-4-Faelle (K-4 nur zwischen KV-Arten); jeder K-4-Fall wird ueber
 * {@code ConflictResolver.resolve} nachgerechnet und stimmt in Bereich, Typ und Konfidenz mit dem Produktfund
 * ueberein.</li>
 * <li>Vorrang des Bestands (Nachtrag 2 und 3 zu T-6): In den Texten mit E-Mail-, IP-, URL- und Pfad-Funden neben,
 * in oder hinter KVNR-Kontextwort und -Form steht jeder alte Nicht-KV-Fund unveraendert (alle Felder) in der
 * neuen Liste (ueberschneidende KVNR-Kandidaten werden zugeschnitten, Restfunde stehen zusaetzlich); Typwechsel
 * bei Nicht-KV-Alttypen sind unabhaengig von K-4 strikt 0.</li>
 * <li>Selbsttests: Ein kuenstlich falscher Produktfund wird von der Nachrechnung gemeldet; ein Scanner ohne
 * Filterstufe verletzt den Vorrang in einer Mindestzahl von Texten.</li>
 * </ul>
 * Meldungen nennen nur Zahlen und Namen der Mengen, nie Text oder Ziffern.
 */
class KvnrScannerBestandTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String[] NAMEN = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
        "Korpus Teil 2", "Generatoren"};

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

    /** Dieselben acht Bestandsmengen wie in {@code KvStandTrennerDifferenzTest}. */
    private static List<List<String>> mengen() {
        return List.of(
                texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE), RoundtripFuzzTest.Fall::text),
                texte(RoundtripFuzzTest.erzeugeKv(RoundtripFuzzTest.SEED_TEXTE_KV), RoundtripFuzzTest.Fall::text),
                texte(RoundtripFuzzTest.erzeugeKvC(RoundtripFuzzTest.SEED_TEXTE_KV_C),
                        RoundtripFuzzTest.FallC::text),
                texte(RoundtripFuzzTest.erzeugeKvD(RoundtripFuzzTest.SEED_TEXTE_KV_D),
                        RoundtripFuzzTest.FallD::text),
                texte(RoundtripFuzzTest.erzeugeKvE(RoundtripFuzzTest.SEED_TEXTE_KV_E),
                        RoundtripFuzzTest.FallE::text),
                KvDifferenzTest.korpus(),
                texte(RoundtripKorpusTest.kvTrennerFaelle(), RoundtripKorpusTest.KvFall::text),
                KvKontextLueckenEigenschaftTest.differenzTexte());
    }

    /** Messwerte einer Textmenge. */
    private record Messung(int texte, int alteFunde, int neueFunde, Abweichungen abweichungen, int s3Geprueft,
            int s3Verstoesse, Ergebnis nachrechnung) {
    }

    private static Messung messe(List<String> texte) {
        int alteFunde = 0;
        int neueFunde = 0;
        int s3Geprueft = 0;
        int s3Verstoesse = 0;
        Abweichungen summe = KvnrBsnrHilfen.KEINE;
        Ergebnis nach = Ergebnis.NULL;
        for (String text : texte) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            alteFunde += alt.size();
            neueFunde += neu.size();
            summe = summe.plus(KvnrBsnrHilfen.bestandsabweichungen(text, alt, neu));
            S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, alt, neu);
            s3Geprueft += s3.geprueft();
            s3Verstoesse += s3.verstoesse();
            nach = nach.plus(KvnrKollisionsNachrechnung.rechne(text, alt, neu));
        }
        return new Messung(texte.size(), alteFunde, neueFunde, summe, s3Geprueft, s3Verstoesse, nach);
    }

    private static Messung[] bestandsMessungen;

    private static synchronized Messung[] bestandsMessungen() {
        if (bestandsMessungen == null) {
            List<List<String>> mengen = mengen();
            Messung[] m = new Messung[mengen.size()];
            for (int i = 0; i < m.length; i++) {
                m[i] = messe(mengen.get(i));
            }
            bestandsMessungen = m;
        }
        return bestandsMessungen;
    }

    // ------------------------------------------------------------------ AK-19: Bestandsmengen

    /**
     * AK-19 auf Scannerebene: Auf den acht Bestandsmengen gibt es keinen Verstoss gegen S-1/S-2, keinen der
     * Kollisionsfaelle K-1 bis K-4 und keine S-3-Abweichung (kein Fund des alten Scanners geht verloren oder
     * aendert Typ, Bereich oder Konfidenz; es entsteht kein zusaetzlicher Fund in der Naehe ohne KVNR-/BSNR-
     * Kontextwort und ohne enge KVNR-Form).
     */
    @Test
    void ak19_bestandsmengen_keinVerstossKeinKollisionsfallKeineS3Abweichung() {
        Messung[] m = bestandsMessungen();
        for (int i = 0; i < m.length; i++) {
            Abweichungen a = m[i].abweichungen();
            assertEquals(0, a.verstoesse(), NAMEN[i] + ": Verstoesse; " + a.kurz());
            assertEquals(0, a.k1(), NAMEN[i] + ": K-1; " + a.kurz());
            assertEquals(0, a.k2(), NAMEN[i] + ": K-2; " + a.kurz());
            assertEquals(0, a.k3(), NAMEN[i] + ": K-3; " + a.kurz());
            assertEquals(0, a.k4(), NAMEN[i] + ": K-4; " + a.kurz());
            assertEquals(0, m[i].s3Verstoesse(), NAMEN[i] + ": S-3-Abweichungen bei " + m[i].s3Geprueft()
                    + " geprueften Funden");
        }
    }

    /** Die unabhaengige Nachrechnung findet auf den Bestandsmengen keinen Kollisionsfall und keine ungleiche Liste. */
    @Test
    void ak19_bestandsmengen_nachrechnungUeberConflictResolverFindetKeinenKollisionsfall() {
        Messung[] m = bestandsMessungen();
        for (int i = 0; i < m.length; i++) {
            Ergebnis e = m[i].nachrechnung();
            assertEquals(0, e.kollisionen(), NAMEN[i] + ": Kollisionsfaelle; " + e.kurz());
            assertEquals(0, e.k4Faelle(), NAMEN[i] + ": K-4-Faelle; " + e.kurz());
            assertEquals(0, e.k4Abweichungen(), NAMEN[i] + ": K-4-Abweichungen; " + e.kurz());
            assertEquals(0, e.typwechselNichtKv(), NAMEN[i] + ": Typwechsel Nicht-KV; " + e.kurz());
            assertEquals(0, e.listenUngleich(), NAMEN[i] + ": Texte mit ungleicher Liste; " + e.kurz());
        }
    }

    /** Die Pruefung laeuft nicht ins Leere: Funde und S-3-Pruefungen je Menge. */
    @Test
    void ak19_bestandsmengen_mindestzahlenDerPruefungen() {
        Messung[] m = bestandsMessungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].alteFunde() >= MIN_ALTE_FUNDE[i], NAMEN[i] + ": zu wenig alte Funde " + m[i].alteFunde());
            assertTrue(m[i].neueFunde() >= m[i].alteFunde(), NAMEN[i] + ": weniger neue als alte Funde");
            assertTrue(m[i].s3Geprueft() >= MIN_S3_GEPRUEFT[i], NAMEN[i] + ": zu wenig S-3-Pruefungen "
                    + m[i].s3Geprueft());
        }
    }

    /**
     * Messwerte der Bestandsmengen (feste Seeds, Kopie und Generatoren eingefroren), Untergrenzen jeweils 70 %
     * darunter (abgerundet). Alte Funde: 4 630 / 5 425 / 6 239 / 6 914 / 5 144 / 305 / 58 / 7 984. Funde, die S-3
     * pruefen: 9 260 / 10 850 / 12 424 / 13 812 / 10 246 / 610 / 116 / 15 962.
     */
    private static final int[] MIN_ALTE_FUNDE = {3_241, 3_797, 4_367, 4_839, 3_600, 213, 40, 5_588};
    private static final int[] MIN_S3_GEPRUEFT = {6_482, 7_595, 8_696, 9_668, 7_172, 427, 81, 11_173};

    // ------------------------------------------------------------------ gezielte Kollisionstexte

    private static Messung kollisionsMessung;

    private static synchronized Messung kollisionsMessung() {
        if (kollisionsMessung == null) {
            kollisionsMessung = messe(KvnrKollisionsTexte.alle());
        }
        return kollisionsMessung;
    }

    /**
     * Jeder gezielte Kollisionstext hat ein KVNR-Kontextwort (nach der Referenz), besteht den Laufzeit-Waechter fuer
     * synthetische KVNR und hat eine deterministische, sortierte, ueberlappungsfreie Fundliste.
     */
    @Test
    void kollisionstexte_habenKontextwortWaechterUndGueltigeFundlisten() {
        for (String text : KvnrKollisionsTexte.alle()) {
            assertTrue(KvnrBsnrHilfen.kvnrKontextReferenz(text), "Text ohne KVNR-Kontextwort in der Menge");
            KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
            List<Finding> funde = NEU.scan(text);
            assertTrue(funde.equals(NEU.scan(text)), "Fundliste bei Wiederholung nicht gleich");
            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
                assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
                vorigesEnde = f.end();
            }
        }
    }

    /**
     * Verstoesse strikt 0 auf den gezielten Kollisionstexten; Mindestzahlen fuer K-1, K-2 und K-4 mit Untergrenzen
     * 70 % unter dem Istwert. Istwerte (feste Texte): K-1 29, K-2 5, K-3 0, K-4 9 (nur zwischen KV-Arten).
     */
    @Test
    void kollisionstexte_verstoesseStriktNull_mindestzahlenFuerK1UndK4() {
        Abweichungen a = kollisionsMessung().abweichungen();
        assertEquals(0, a.verstoesse(), "Verstoesse auf den Kollisionstexten; " + a.kurz());
        assertEquals(0, a.k3(), "K-3 kommt nicht vor (der Kandidat ohne Buchstaben hat hoechstens 0,40); "
                + a.kurz());
        assertTrue(a.k1() >= MIN_K1, "zu wenig K-1-Faelle " + a.k1());
        assertTrue(a.k2() >= MIN_K2, "zu wenig K-2-Faelle " + a.k2());
        assertTrue(a.k4() >= MIN_K4, "zu wenig K-4-Faelle " + a.k4());
    }

    private static final int MIN_K1 = 20;
    private static final int MIN_K2 = 3;
    private static final int MIN_K4 = 6;
    private static final int MIN_NUR_VERSTREUT = 2;
    private static final int MIN_KOLLISIONEN = 30;

    /**
     * Die Nachrechnung ueber {@code ConflictResolver.resolve} (Nachtrag 3 zu T-1, Punkt 3) auf den Kollisionstexten,
     * exakte Werte: 43 Kollisionsfaelle, davon 9 K-4-Faelle (nur zwischen KV-Arten) ohne jede Abweichung in
     * Bereich, Typ und Konfidenz; 3 Faelle gelten nur ueber verstreute Buchstaben im Fenster als nahe (Sperrschrift
     * {@code K V N R} mit ungueltiger KVNR, ohne enge Form im Fenster); 0 Typwechsel bei Nicht-KV-Alttypen
     * (unabhaengig von K-4); die ganze nachgerechnete Liste (mit unabhaengig nachgebildeter Filterstufe) ist in
     * allen Texten gleich der Produktliste. Die K-4-Zahl der Pruefregel und die der
     * Nachrechnung stimmen ueberein.
     */
    @Test
    void kollisionstexte_nachrechnungUeberConflictResolver_exakteWerte() {
        Messung m = kollisionsMessung();
        Ergebnis e = m.nachrechnung();
        assertEquals(43, e.kollisionen(), "Kollisionsfaelle; " + e.kurz());
        assertEquals(9, e.k4Faelle(), "K-4-Faelle; " + e.kurz());
        assertEquals(0, e.k4Abweichungen(), "K-4-Abweichungen; " + e.kurz());
        assertEquals(3, e.nurVerstreut(), "nur ueber verstreute Buchstaben nahe; " + e.kurz());
        assertEquals(0, e.typwechselNichtKv(), "Typwechsel Nicht-KV-Alttyp; " + e.kurz());
        assertEquals(0, e.listenUngleich(), "Texte mit ungleicher nachgerechneter Liste; " + e.kurz());
        assertEquals(m.abweichungen().k4(), e.k4Faelle(), "K-4-Zahl der Pruefregel gegen Nachrechnung");
        assertEquals(m.abweichungen().kollisionen(), e.kollisionen(), "Kollisionen der Pruefregel gegen Nachrechnung");
        assertTrue(e.nurVerstreut() >= MIN_NUR_VERSTREUT, "zu wenig Faelle nur ueber verstreute Buchstaben");
        assertTrue(e.kollisionen() >= MIN_KOLLISIONEN, "zu wenig Kollisionsfaelle");
    }

    // ------------------------------------------------------------------ Vorrang des Bestands (Nachtrag 2 zu T-6)

    private static List<Finding> nichtKv(List<Finding> funde) {
        List<Finding> r = new ArrayList<>();
        for (Finding f : funde) {
            if (!KvnrBsnrHilfen.istKv(f) && f.type() != FindingType.KVNR) {
                r.add(f);
            }
        }
        return r;
    }

    /** Ein Scanner ohne Filterstufe: dieselben Erkenner wie {@code standard()}, direkt ueber den Konfliktaufloeser. */
    private static List<Finding> ohneFilter(String text) {
        List<Finding> kandidaten = new ArrayList<>();
        for (Recognizer r : NEU.recognizers()) {
            kandidaten.addAll(r.detect(text));
        }
        return ConflictResolver.resolve(text, kandidaten);
    }

    /**
     * Jeder alte Fund von E-Mail, IP, URL und Pfad steht in allen Feldern unveraendert in der neuen Liste (auch die
     * Nicht-KV-Teilliste ist gleich); Verstoesse, Kollisionsfaelle und Typwechsel Nicht-KV sind 0. Die Menge
     * enthaelt die Texte aus {@code zuschnitt()} (Nachtrag 3 zu T-6); jeder Restfund steht in der neuen Liste.
     * Istwerte (368 Texte): 208 mit mindestens einem zugeschnittenen KVNR-Kandidaten, 80 mit Restfund (80
     * Restfunde, 24 entfallene Reststuecke unter der Schwelle), 338 mit mindestens einem alten Nicht-KV-Fund; ohne
     * Filterstufe weichen 102 Texte in der Nicht-KV-Teilliste ab.
     */
    @Test
    void bestandVorrang_alterNichtKvFundStehtUnveraendertInDerNeuenListe() {
        List<String> texte = KvnrKollisionsTexte.bestandVorrang();
        Messung m = messe(texte);
        assertEquals(0, m.abweichungen().verstoesse(), "Verstoesse; " + m.abweichungen().kurz());
        assertEquals(0, m.abweichungen().kollisionen(), "Kollisionsfaelle; " + m.abweichungen().kurz());
        assertEquals(0, m.nachrechnung().typwechselNichtKv(), "Typwechsel; " + m.nachrechnung().kurz());
        assertEquals(0, m.nachrechnung().listenUngleich(), "ungleiche Listen; " + m.nachrechnung().kurz());
        int mitZugeschnittenem = 0;
        int mitRestfund = 0;
        int restfundeGesamt = 0;
        int unterSchwelleGesamt = 0;
        int mitNichtKvAltFund = 0;
        int ohneFilterAbweichend = 0;
        for (String text : texte) {
            KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            for (Finding f : nichtKv(alt)) {
                assertTrue(neu.contains(f), "alter Nicht-KV-Fund fehlt oder ist veraendert");
            }
            assertTrue(nichtKv(neu).equals(nichtKv(alt)), "Nicht-KV-Teilliste ungleich");
            mitNichtKvAltFund += nichtKv(alt).isEmpty() ? 0 : 1;
            Zuschnitt zuschnitt = KvnrKollisionsNachrechnung.kvnrZuschnitt(text);
            mitZugeschnittenem += zuschnitt.zugeschnitten() > 0 ? 1 : 0;
            mitRestfund += zuschnitt.restfunde() > 0 ? 1 : 0;
            restfundeGesamt += zuschnitt.restfunde();
            unterSchwelleGesamt += zuschnitt.unterSchwelle();
            // jeder Restfund steht (allein oder mit LANR/IK zusammengelegt) in der neuen Liste
            for (Finding r : zuschnitt.kandidaten()) {
                if (r.source().equals(KvnrRecognizer.SOURCE_REST)) {
                    assertTrue(neu.stream().anyMatch(f -> f.start() <= r.start() && f.end() >= r.end()),
                            "Restfund nicht abgedeckt");
                }
            }
            ohneFilterAbweichend += nichtKv(ohneFilter(text)).equals(nichtKv(alt)) ? 0 : 1;
        }
        assertTrue(mitZugeschnittenem >= MIN_MIT_ZUGESCHNITTENEM, "zu wenig Texte mit zugeschnittenem Kandidaten "
                + mitZugeschnittenem);
        assertTrue(mitRestfund >= MIN_MIT_RESTFUND, "zu wenig Texte mit Restfund " + mitRestfund);
        assertTrue(restfundeGesamt >= MIN_RESTFUNDE, "zu wenig Restfunde " + restfundeGesamt);
        assertTrue(unterSchwelleGesamt >= MIN_UNTER_SCHWELLE, "zu wenig entfallene Reststuecke unter der Schwelle "
                + unterSchwelleGesamt);
        assertTrue(mitNichtKvAltFund >= MIN_MIT_NICHT_KV_ALTFUND, "zu wenig Texte mit altem Nicht-KV-Fund "
                + mitNichtKvAltFund);
        // Selbsttest: ohne Filterstufe wird der Vorrang in einer Mindestzahl von Texten verletzt
        assertTrue(ohneFilterAbweichend >= MIN_OHNE_FILTER_ABWEICHEND, "Scanner ohne Filter zu selten gemeldet "
                + ohneFilterAbweichend);
    }

    private static final int MIN_MIT_ZUGESCHNITTENEM = 145;
    private static final int MIN_MIT_RESTFUND = 56;
    private static final int MIN_RESTFUNDE = 56;
    private static final int MIN_UNTER_SCHWELLE = 16;
    private static final int MIN_MIT_NICHT_KV_ALTFUND = 236;
    private static final int MIN_OHNE_FILTER_ABWEICHEND = 71;

    // ------------------------------------------------------------------ IPv4-Gestalt (Nachtrag zu T-6)

    /**
     * Kontextwort, Buchstabe und IPv4-Gestalt (direkt, mit Trenner, mit unsichtbarem Zeichen, gesperrtes Kontextwort,
     * laengere Ketten): Verstoesse 0, kein Kollisionsfall, die Nachrechnung findet keinen; jeder Fund des alten
     * Scanners (der IP-Fund) bleibt in Bereich, Typ, Konfidenz und Quelle unveraendert in der neuen Liste. Der
     * KVNR-Erkenner allein liefert keinen Fund in Buchstabenform. Istwert: 26 Texte, davon 26 mit altem IP-Fund.
     */
    @Test
    void ipGestalt_keinVerstossKeinKollisionsfall_alterIpFundBleibtUnveraendert() {
        List<String> texte = KvnrKollisionsTexte.ipGestalt();
        Messung m = messe(texte);
        Abweichungen a = m.abweichungen();
        assertEquals(0, a.verstoesse(), "Verstoesse; " + a.kurz());
        assertEquals(0, a.kollisionen(), "Kollisionsfaelle; " + a.kurz());
        assertEquals(0, m.nachrechnung().kollisionen(), "Kollisionsfaelle der Nachrechnung; "
                + m.nachrechnung().kurz());
        assertEquals(0, m.nachrechnung().listenUngleich(), "ungleiche Listen; " + m.nachrechnung().kurz());
        int mitIpFund = 0;
        for (String text : texte) {
            List<Finding> neu = NEU.scan(text);
            boolean ip = false;
            for (Finding alt : ALT.scan(text)) {
                assertTrue(neu.contains(alt), "alter Fund fehlt oder ist veraendert");
                ip |= alt.type() == FindingType.IP;
            }
            mitIpFund += ip ? 1 : 0;
            for (Finding f : new KvnrRecognizer().detect(text)) {
                assertTrue(Character.isDigit(f.originalValue().charAt(0)), "KVNR-Fund in Buchstabenform");
                assertTrue(f.confidence() <= 0.40, "KVNR-Fund ueber 0,40");
            }
        }
        assertTrue(mitIpFund >= MIN_MIT_IP_FUND, "zu wenig Texte mit altem IP-Fund " + mitIpFund);
    }

    private static final int MIN_MIT_IP_FUND = 18;

    // ------------------------------------------------------------------ Selbsttests der Nachrechnung

    /** Der K-4-Text mit genau einem K-4-Fall zwischen KV-Arten, dessen Produktfund verfaelscht wird. */
    private static String k4Text() {
        return KvnrKollisionsTexte.k4().get(4);
    }

    private static Finding zusammengelegt(List<Finding> funde) {
        for (Finding f : funde) {
            if (f.source().equals(Finding.SOURCE_MERGED)) {
                return f;
            }
        }
        throw new IllegalStateException("kein zusammengelegter Fund");
    }

    private static List<Finding> ersetze(List<Finding> funde, Finding alt, Finding neu) {
        List<Finding> liste = new ArrayList<>(funde);
        liste.set(liste.indexOf(alt), neu);
        return liste;
    }

    @Test
    void selbsttest_nachrechnung_derUnveraenderteProduktfundWirdNichtGemeldet() {
        String text = k4Text();
        Ergebnis e = KvnrKollisionsNachrechnung.rechne(text, ALT.scan(text), NEU.scan(text));
        assertEquals(1, e.k4Faelle(), "K-4-Faelle; " + e.kurz());
        assertEquals(0, e.k4Abweichungen(), "K-4-Abweichungen; " + e.kurz());
        assertEquals(0, e.listenUngleich(), "ungleiche Listen; " + e.kurz());
    }

    @Test
    void selbsttest_nachrechnung_einVerfaelschterProduktfundWirdGemeldet() {
        String text = k4Text();
        List<Finding> alt = ALT.scan(text);
        List<Finding> produkt = NEU.scan(text);
        Finding g = zusammengelegt(produkt);
        Finding andereKonfidenz = new Finding(g.type(), g.originalValue(), g.start(), g.end(),
                g.confidence() - 0.01, g.source());
        FindingType anderer = g.type() == FindingType.KVNR ? FindingType.IK : FindingType.KVNR;
        Finding andererTyp = new Finding(anderer, g.originalValue(), g.start(), g.end(), g.confidence(),
                g.source());
        Finding andererBereich = new Finding(g.type(), g.originalValue().substring(0, g.originalValue().length() - 1),
                g.start(), g.end() - 1, g.confidence(), g.source());
        for (Finding verfaelscht : List.of(andereKonfidenz, andererTyp, andererBereich)) {
            Ergebnis e = KvnrKollisionsNachrechnung.rechne(text, alt, ersetze(produkt, g, verfaelscht));
            assertEquals(1, e.k4Abweichungen(), "verfaelschter Produktfund nicht gemeldet; " + e.kurz());
            assertEquals(1, e.listenUngleich(), "ungleiche Liste nicht gemeldet; " + e.kurz());
        }
    }

    @Test
    void selbsttest_nachrechnung_typwechselBeiNichtKvAlttypWirdUnabhaengigVonK4Gezaehlt() {
        String text = "abcdefghijkl";
        Finding alt = new Finding(FindingType.IP, text.substring(2, 10), 2, 10, 0.85, "ipv4");
        Finding anderer = new Finding(FindingType.LANR, text, 0, 12, 0.50, Finding.SOURCE_MERGED);
        Ergebnis e = KvnrKollisionsNachrechnung.rechne(text, List.of(alt), List.of(anderer));
        assertEquals(1, e.k4Faelle(), "K-4-Faelle; " + e.kurz());
        assertEquals(1, e.typwechselNichtKv(), "Typwechsel Nicht-KV; " + e.kurz());
        // auch ohne K-4 (teilweise Ueberdeckung durch einen Fund anderen Typs, kein zusammengelegter Fund)
        Finding teilweise = new Finding(FindingType.LANR, text.substring(5), 5, 12, 0.50, "lanr");
        Ergebnis ohneK4 = KvnrKollisionsNachrechnung.rechne(text, List.of(alt), List.of(teilweise));
        assertEquals(0, ohneK4.k4Faelle(), "K-4-Faelle; " + ohneK4.kurz());
        assertEquals(1, ohneK4.typwechselNichtKv(), "Typwechsel ohne K-4; " + ohneK4.kurz());
        // Gegenproben: bei gleichem Typ und bei einem KV-Alttyp zaehlt der Wechsel nicht
        Finding gleicherTyp = new Finding(FindingType.IP, text, 0, 12, 0.50, Finding.SOURCE_MERGED);
        assertEquals(0, KvnrKollisionsNachrechnung.rechne(text, List.of(alt), List.of(gleicherTyp))
                .typwechselNichtKv(), "gleicher Typ gezaehlt");
        Finding altLanr = new Finding(FindingType.LANR, text.substring(2, 10), 2, 10, 0.85, "lanr");
        Finding kvnr = new Finding(FindingType.KVNR, text, 0, 12, 0.50, Finding.SOURCE_MERGED);
        assertEquals(0, KvnrKollisionsNachrechnung.rechne(text, List.of(altLanr), List.of(kvnr))
                .typwechselNichtKv(), "KV-Alttyp gezaehlt");
    }

    @Test
    void selbsttest_nachrechnung_nurVerstreuteBuchstabenWerdenGezaehltZusammenhaengendeKontextwoerterNicht() {
        String verstreut = KvnrKollisionsTexte.verstreut().get(0);
        String zusammen = "KVNR LANR " + SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        assertEquals(1, KvnrKollisionsNachrechnung.rechne(verstreut, ALT.scan(verstreut), NEU.scan(verstreut))
                .nurVerstreut(), "Sperrschrift nicht gezaehlt");
        assertEquals(0, KvnrKollisionsNachrechnung.rechne(zusammen, ALT.scan(zusammen), NEU.scan(zusammen))
                .nurVerstreut(), "zusammenhaengendes Kontextwort gezaehlt");
    }

    @Test
    void selbsttest_zusammenhaengendesKontextwort_erkenntWoerterUndGrenztAb() {
        char ae = (char) 0xE4;
        for (String ja : List.of("KVNR", "Versichertennummer", "KV-Nr.", "KV Nr", "Versicherten-Nr.", "eGK-Nummer",
                "Patienten-KVNR", "Betriebsstaetten-Nr.", "Betriebsst" + ae + "tten-Nr.", "BSNR")) {
            assertTrue(KvnrKollisionsNachrechnung.zusammenhaengendesKontextwort("Wort " + ja + " Ende"),
                    "Kontextwort nicht erkannt");
        }
        for (String nein : List.of("K V N R", "K. V. N. R.", "Die KV zahlt", "VKNR", "Versicherungsnummer",
                "Betrieb", "B S N R")) {
            assertFalse(KvnrKollisionsNachrechnung.zusammenhaengendesKontextwort("Wort " + nein + " Ende"),
                    "Kein Kontextwort, aber erkannt");
        }
    }
}
