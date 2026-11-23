package de.skillshield.core;

import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kuenstlicherErkenner;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kvnrOhneKontextReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.KvnrFensterfernPruefung.Ergebnis;
import de.skillshield.core.KvnrKollisionsNachrechnung.Zuschnitt;
import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextAusnahme;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextAusnahme.Zaehlung;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.Optionen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz auf Scannerebene fuer den Fuzz-Teil 1g (KVNR ohne Kontextwort, Feature kvnr-bsnr-erkennung, Task
 * T-11; AK-16, AK-17, AK-19; Nachtrag zu T-11, Punkt 1: S-1 bis S-3 und K-1 stehen in dieser Klasse, weil
 * {@code KvStandTrennerDifferenzTest} eingefroren ist; Muster wie {@code KvnrFuzzBestandTest}). Die Klasse gehoert zum
 * 1b-Inventar (Plan Regel 14). Je Text von Teil 1g gilt mit der eingefrorenen Pruefregel
 * {@code KvnrBsnrHilfen.bestandsabweichungen} ({@code ALT_TRENNER} gegen {@code TextScanner.standard()}) und der
 * Nachpruefung {@link KvnrFensterfernPruefung}:
 * <ul>
 * <li>S-1: Jede Position, die der eingefrorene Stand abdeckt, deckt auch der neue Scanner ab;</li>
 * <li>S-2: Kein echter Verstoss (Typ, Konfidenz, Bereich jedes alten Fundes bleiben oder gehoeren zu einem der Faelle
 * K-1 bis K-4); K-1 (LANR- oder IK-Kontextwort direkt vor der KVNR-Form: der KVNR-Fund mit 0,60 umschliesst den alten
 * Fund auf den Ziffern) ist ein exakter Messwert mit Mindestzahl, K-2 bis K-4 sind exakte Messwerte;</li>
 * <li>S-3: Jeder Fund, in dessen Naehe weder Kontextwort noch die enge Form steht, hat in der anderen Liste einen
 * gleichen Fund;</li>
 * <li>Bestand hat Vorrang: Die Teilliste der Funde von E-Mail, IP, URL und Pfad ist in jedem Text gleich der des alten
 * Scanners; der Typwechsel eines Nicht-KV-Altfundes ist strikt 0 (unabhaengige Nachrechnung ueber
 * {@code ConflictResolver.resolve});</li>
 * <li>Funde der Regel 1b (Quelle {@code kvnr-ohne-kontext}) liegen im Scan nur auf Treffern der unabhaengigen
 * Referenz und der engen Form, mit 0,60 und zehn Zeichen.</li>
 * </ul>
 * Selbsttests: Ohne Filterstufe weicht die Teilliste des Bestands ab; ein Scanner, der einen alten Fund verliert,
 * laesst S-1 anschlagen; ein zusaetzlicher Erkenner fuer die weite Form laesst S-3 anschlagen. Meldungen nennen nur
 * Zahlen und Namen, nie Text oder Ziffern.
 */
class KvnrOhneKontextFuzzBestandTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String QUELLE_1B = "kvnr-ohne-kontext";
    private static final double KONFIDENZ_1B = 0.60;
    private static final int LAENGE_1B = 10;

    /** Messwerte der Textmenge von Teil 1g. */
    private record Messung(int texte, int alteFunde, int verloren, Ergebnis ergebnis, int s3Geprueft, int s3Verstoesse,
            int s3VerstoesseOhneFensterfern, KvnrKollisionsNachrechnung.Ergebnis nachrechnung, int teillisteUngleich,
            int altFundUngleichZumText, int zugeschnitten, int restfunde, int ohneFilterTeillisteUngleich,
            int funde1b, int funde1bAusserhalbReferenz, int funde1bAnderesFormat, Zaehlung ausnahme) {
    }

    private static List<Finding> bestand(List<Finding> funde) {
        List<Finding> r = new ArrayList<>();
        for (Finding f : funde) {
            if (f.type() == FindingType.EMAIL || f.type() == FindingType.IP || f.type() == FindingType.URL
                    || f.type() == FindingType.PFAD) {
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

    private static boolean abdeckungVerloren(int laenge, List<Finding> alt, List<Finding> neu) {
        boolean[] imNeuen = new boolean[laenge];
        for (Finding g : neu) {
            Arrays.fill(imNeuen, g.start(), g.end(), true);
        }
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                if (!imNeuen[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean liegtIn(Finding f, List<Bereich> bereiche) {
        for (Bereich b : bereiche) {
            if (f.start() == b.start() && f.end() == b.ende()) {
                return true;
            }
        }
        return false;
    }

    private static boolean ganzeFormAusAscii(Finding f) {
        String w = f.originalValue();
        if (w.length() != LAENGE_1B || w.charAt(0) < 'A' || w.charAt(0) > 'Z') {
            return false;
        }
        for (int i = 1; i < w.length(); i++) {
            if (w.charAt(i) < '0' || w.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static Messung messe(List<String> texte) {
        int alteFunde = 0;
        int verloren = 0;
        int s3Geprueft = 0;
        int s3Verstoesse = 0;
        int s3OhneFensterfern = 0;
        int teillisteUngleich = 0;
        int altFundUngleich = 0;
        int zugeschnitten = 0;
        int restfunde = 0;
        int ohneFilterUngleich = 0;
        int funde1b = 0;
        int ausserhalbReferenz = 0;
        int anderesFormat = 0;
        Zaehlung ausnahme = Zaehlung.NULL;
        Ergebnis summe = Ergebnis.NULL;
        KvnrKollisionsNachrechnung.Ergebnis nach = KvnrKollisionsNachrechnung.Ergebnis.NULL;
        for (String text : texte) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            alteFunde += alt.size();
            verloren += abdeckungVerloren(text.length(), alt, neu) ? 1 : 0;
            for (Finding f : alt) {
                altFundUngleich += text.substring(f.start(), f.end()).equals(f.originalValue()) ? 0 : 1;
            }
            Ergebnis e = KvnrFensterfernPruefung.pruefe(text, alt, neu);
            summe = summe.plus(e);
            S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, alt, neu);
            s3Geprueft += s3.geprueft();
            s3Verstoesse += s3.verstoesse();
            s3OhneFensterfern += e.fensterfern() > 0 ? 0 : s3.verstoesse();
            teillisteUngleich += bestand(alt).equals(bestand(neu)) ? 0 : 1;
            ohneFilterUngleich += bestand(alt).equals(bestand(ohneFilter(text))) ? 0 : 1;
            nach = nach.plus(KvnrKollisionsNachrechnung.rechne(text, alt, neu));
            Zuschnitt z = KvnrKollisionsNachrechnung.kvnrZuschnitt(text);
            zugeschnitten += z.zugeschnitten() > 0 ? 1 : 0;
            restfunde += z.restfunde();
            ausnahme = ausnahme.plus(KvnrOhneKontextAusnahme.zaehle(text, alt, neu));
            List<Bereich> referenz = kvnrOhneKontextReferenz(text);
            List<Bereich> eng = KvnrBsnrHilfen.kvnrFormEngTreffer(text);
            for (Finding f : neu) {
                if (f.type() == FindingType.KVNR && QUELLE_1B.equals(f.source())) {
                    funde1b++;
                    ausserhalbReferenz += liegtIn(f, referenz) && liegtIn(f, eng) ? 0 : 1;
                    anderesFormat += ganzeFormAusAscii(f) && Math.abs(f.confidence() - KONFIDENZ_1B) < 1e-9 ? 0 : 1;
                }
            }
        }
        return new Messung(texte.size(), alteFunde, verloren, summe, s3Geprueft, s3Verstoesse, s3OhneFensterfern, nach,
                teillisteUngleich, altFundUngleich, zugeschnitten, restfunde, ohneFilterUngleich, funde1b,
                ausserhalbReferenz, anderesFormat, ausnahme);
    }

    private static List<String> texte1g() {
        List<String> texte = new ArrayList<>();
        for (RoundtripFuzzTest.FallG f : RoundtripFuzzTest.erzeugeKvG(RoundtripFuzzTest.SEED_TEXTE_KV_G)) {
            texte.add(f.text());
        }
        return texte;
    }

    private static Messung messung;

    private static synchronized Messung messung() {
        if (messung == null) {
            messung = messe(texte1g());
        }
        return messung;
    }

    // ------------------------------------------------------------------ S-1

    /** S-1: Jede Position des eingefrorenen Standes bleibt abgedeckt (Zahl der Texte mit Verlust: strikt 0). */
    @Test
    void s1_teil1g_jedePositionDesAltenStandsBleibtAbgedeckt() {
        assertEquals(0, messung().verloren(), "Teil 1g: Texte mit verlorener Abdeckung");
    }

    // ------------------------------------------------------------------ S-2, K-1 bis K-4

    /**
     * S-2: kein echter Verstoss (die Pruefregel meldet Verstoesse nur fuer fensterfern zulaessige Faelle; in Teil 1g
     * gibt es vier fensterferne K-2-Faelle aus langen Aufzaehlungen der Bausteine von Teil 1f, K-1 und K-3 exakt 0;
     * Nachtrag 5 zu T-6, Punkt 1). Der Typ, die Konfidenz und der Bereich jedes
     * alten Fundes bleiben oder gehoeren zu einem der Faelle K-1 bis K-4.
     */
    @Test
    void s2_teil1g_keinEchterVerstoss_keineFensterfernenFaelle() {
        Ergebnis e = messung().ergebnis();
        assertEquals(0, e.echteVerstoesse(), "Teil 1g: echte Verstoesse; " + e.kurz());
        assertEquals(0, e.fensterfernK1(), "Teil 1g: fensterfern K-1; " + e.kurz());
        assertEquals(4, e.fensterfernK2(), "Teil 1g: fensterfern K-2; " + e.kurz());
        assertEquals(0, e.fensterfernK3(), "Teil 1g: fensterfern K-3; " + e.kurz());
    }

    /**
     * K-1 (LANR- oder IK-Kontextwort direkt vor der KVNR-Form; Risiko R-2): exakter Messwert 738 (alle Faelle der
     * Pruefregel, auch die aus den Bausteinen von Teil 1f mit KVNR-Kontextwort) mit Mindestzahl (70 Prozent), und der
     * Anteil der Regel 1b daran: 452 neue Funde ohne Kontextwort ersetzen einen alten LANR- oder IK-Fund auf denselben
     * neun Ziffern ({@code KvnrOhneKontextAusnahme}), 2 538 liegen auf einer Stelle ohne alten Fund, zusammen alle
     * 2 990 Funde der Regel 1b. K-2 55 (nur Bausteine aus 1f) und K-3 sowie K-4 exakt 0: K-4 gibt es nur bei
     * Teilueberlappung ohne Trenner, die der Generator nicht erzeugt.
     */
    @Test
    void k1_teil1g_exakterMesswertUndMindestzahl_k2BisK4Exakt() {
        Ergebnis e = messung().ergebnis();
        assertEquals(738, e.k1(), "Teil 1g: K-1; " + e.kurz());
        assertEquals(55, e.k2(), "Teil 1g: K-2; " + e.kurz());
        assertEquals(0, e.k3(), "Teil 1g: K-3; " + e.kurz());
        assertEquals(0, e.k4(), "Teil 1g: K-4; " + e.kurz());
        assertTrue(e.k1() >= MIN_K1, "zu wenig K-1-Faelle in Teil 1g: " + e.k1());
        Zaehlung a = messung().ausnahme();
        assertEquals(452, a.ersetzt(), "Teil 1g: K-1 durch Regel 1b; " + a.kurz());
        assertTrue(a.ersetzt() >= MIN_K1_1B, "zu wenig K-1-Faelle durch Regel 1b: " + a.ersetzt());
        assertEquals(2538, a.frei(), "Teil 1g: Funde der Regel 1b ohne alten Fund; " + a.kurz());
        assertEquals(2990, a.gesamt(), "Teil 1g: Funde der Regel 1b; " + a.kurz());
    }

    private static final int MIN_K1 = 516;
    private static final int MIN_K1_1B = 316;

    // ------------------------------------------------------------------ S-3

    /**
     * S-3: In Texten ohne fensterfernen Fall gibt es keine Abweichung (Zahl der Abweichungen insgesamt exakt, keine
     * davon ausserhalb fensterferner Faelle). Mindestzahl der geprueften Funde: Istwert 10, 70 Prozent. Die Zahl ist
     * klein, weil in den Texten von Teil 1g fast jeder Fund ein Kontextwort oder eine enge Form in der Naehe hat (die
     * Pruefung S-3 nimmt solche Funde aus); die Absicherung dort leisten S-2, die Nachrechnung und die Bloecke des
     * Fuzz-Laufs.
     */
    @Test
    void s3_teil1g_keineAbweichung_mindestzahlGeprueft() {
        Messung m = messung();
        assertEquals(0, m.s3VerstoesseOhneFensterfern(), "Teil 1g: S-3-Abweichungen ohne fensterfernen Fall");
        assertEquals(0, m.s3Verstoesse(), "Teil 1g: S-3-Abweichungen");
        assertTrue(m.s3Geprueft() >= MIN_S3_GEPRUEFT, "Teil 1g: zu wenig S-3-Pruefungen " + m.s3Geprueft());
    }

    private static final int MIN_S3_GEPRUEFT = 7;

    // ------------------------------------------------------------------ Bestand hat Vorrang

    /**
     * Bestand hat Vorrang: Die Teilliste der Funde von E-Mail, IP, URL und Pfad ist in jedem Text gleich der des alten
     * Scanners; der Typwechsel eines Nicht-KV-Altfundes ist strikt 0; die unabhaengige Nachrechnung findet keine
     * Abweichung (K-4-Faelle, ungleiche Listen), und ihre Kollisionszahl ist die Summe der zulaessigen Faelle; der
     * Originalwert aller alten Funde ist der Textausschnitt.
     */
    @Test
    void bestandHatVorrang_teil1g_nachrechnungOhneAbweichung() {
        Messung m = messung();
        KvnrKollisionsNachrechnung.Ergebnis n = m.nachrechnung();
        Ergebnis e = m.ergebnis();
        assertEquals(0, m.teillisteUngleich(), "Teil 1g: Texte mit ungleicher Bestands-Teilliste");
        assertEquals(0, n.typwechselNichtKv(), "Teil 1g: Typwechsel Nicht-KV; " + n.kurz());
        assertEquals(0, n.k4Abweichungen(), "Teil 1g: K-4-Abweichungen; " + n.kurz());
        assertEquals(0, n.listenUngleich(), "Teil 1g: Texte mit ungleicher nachgerechneter Liste; " + n.kurz());
        assertEquals(e.k4(), n.k4Faelle(), "Teil 1g: K-4 der Pruefregel gegen Nachrechnung");
        assertEquals(e.k1() + e.k2() + e.k3() + e.k4() + e.fensterfern(), n.kollisionen(),
                "Teil 1g: Kollisionen der Pruefregel gegen Nachrechnung; " + n.kurz());
        assertEquals(0, m.altFundUngleichZumText(), "Teil 1g: alte Funde mit Originalwert ungleich Text");
    }

    // ------------------------------------------------------------------ Funde der Regel 1b

    /**
     * Funde der Regel 1b im Scan: Quelle {@code kvnr-ohne-kontext}, genau zehn ASCII-Zeichen (Grossbuchstabe und neun
     * Ziffern), 0,60, und jeder liegt auf einem Treffer der unabhaengigen Referenz und der engen Form (strikt 0
     * Ausnahmen). Exakte Zahl der Funde im Scan: 2 990; Mindestzahl
     * 70 Prozent.
     */
    @Test
    void fundeDerRegel1b_liegenAufReferenzUndEngerForm_mitNullKommaSechzig() {
        Messung m = messung();
        assertEquals(0, m.funde1bAusserhalbReferenz(), "Teil 1g: Funde der Regel 1b ausserhalb der Referenz");
        assertEquals(0, m.funde1bAnderesFormat(), "Teil 1g: Funde der Regel 1b mit anderem Format");
        assertEquals(2990, m.funde1b(), "Teil 1g: Funde der Regel 1b im Scan");
        assertTrue(m.funde1b() >= MIN_FUNDE_1B, "Teil 1g: zu wenig Funde der Regel 1b " + m.funde1b());
    }

    private static final int MIN_FUNDE_1B = 2093;

    // ------------------------------------------------------------------ Mindestzahlen und Selbsttests

    /**
     * Die Pruefung laeuft nicht ins Leere (Untergrenzen 70 Prozent der Istwerte): alte Funde (Istwert 3 145), Texte
     * mit zugeschnittenem KVNR-Kandidaten (329), Restfunde (82). Selbsttest des Vergleichs: Ohne Filterstufe weicht die
     * Bestands-Teilliste in einer Mindestzahl von Texten ab (Istwert 155).
     */
    @Test
    void mindestzahlenDerPruefungenUndSelbsttestOhneFilter() {
        Messung m = messung();
        assertEquals(1000, m.texte(), "Zahl der Texte");
        assertTrue(m.alteFunde() >= MIN_ALTE_FUNDE, "Teil 1g: zu wenig alte Funde " + m.alteFunde());
        assertTrue(m.zugeschnitten() >= MIN_ZUGESCHNITTEN, "Teil 1g: zu wenig Texte mit zugeschnittenem Kandidaten "
                + m.zugeschnitten());
        assertTrue(m.restfunde() >= MIN_RESTFUNDE, "Teil 1g: zu wenig Restfunde " + m.restfunde());
        assertTrue(m.ohneFilterTeillisteUngleich() >= MIN_OHNE_FILTER, "Teil 1g: Scanner ohne Filter zu selten "
                + "gemeldet " + m.ohneFilterTeillisteUngleich());
    }

    private static final int MIN_ALTE_FUNDE = 2201;
    private static final int MIN_ZUGESCHNITTEN = 230;
    private static final int MIN_RESTFUNDE = 57;
    private static final int MIN_OHNE_FILTER = 108;

    /**
     * Selbsttest S-1: Ein Scanner, der den ersten alten Fund verliert, laesst die Pruefung anschlagen (Istwert 927
     * Texte, Untergrenze 70 Prozent).
     */
    @Test
    void selbsttestS1_einVerlorenerAlterFund_wirdGemeldet() {
        int verloren = 0;
        for (String text : texte1g()) {
            List<Finding> alt = ALT.scan(text);
            if (!alt.isEmpty()) {
                verloren += abdeckungVerloren(text.length(), alt, alt.subList(1, alt.size())) ? 1 : 0;
            }
        }
        assertTrue(verloren >= MIN_S1_SELBSTTEST, "Texte mit eingebautem Verlust: " + verloren);
    }

    private static final int MIN_S1_SELBSTTEST = 648;

    /**
     * Selbsttest S-3: Ein zusaetzlicher Erkenner fuer die weite Form (ohne Pruefziffer, Negativkontext, Grenzen) laesst
     * die Pruefung anschlagen (Istwert 4 Verstoesse; wenige, weil S-3 Funde nahe einem Kontextwort oder einer engen
     * Form ausnimmt, Untergrenze 70 Prozent).
     */
    @Test
    void selbsttestS3_weiteForm_wirdGemeldet() {
        Optionen weit = Optionen.REFERENZ.ohnePruefziffer().ohneNegativkontext().ohneWortgrenzen();
        List<Recognizer> erkenner = new ArrayList<>(NEU.recognizers());
        erkenner.add(kuenstlicherErkenner(weit));
        TextScanner mitWeit = new TextScanner(erkenner);
        int verstoesse = 0;
        for (String text : texte1g()) {
            verstoesse += KvnrBsnrHilfen.s3Pruefung(text, ALT.scan(text), mitWeit.scan(text)).verstoesse();
        }
        assertTrue(verstoesse >= MIN_S3_SELBSTTEST, "Verstoesse im Selbsttest: " + verstoesse);
    }

    private static final int MIN_S3_SELBSTTEST = 2;
}
