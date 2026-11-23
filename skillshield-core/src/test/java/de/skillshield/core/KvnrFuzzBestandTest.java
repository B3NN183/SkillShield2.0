package de.skillshield.core;

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
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz auf Scannerebene fuer die neuen Textmengen Fuzz-Teil 1f und KVNR-Korpus (Feature
 * kvnr-bsnr-erkennung, T-7; AK-19, AK-21, Nachtrag zu T-3 Punkt 1: neue Textmengen in neuen Testklassen mit denselben
 * Pruefregeln). Die eingefrorenen Pruefungen ({@code KvStandTrennerDifferenzTest}, {@code KvnrScannerBestandTest})
 * bleiben unveraendert. Je Menge gilt mit der eingefrorenen Pruefregel {@code KvnrBsnrHilfen.bestandsabweichungen}
 * (ALT_TRENNER gegen {@code TextScanner.standard()}) und der Nachpruefung {@link KvnrFensterfernPruefung}:
 * <ul>
 * <li>Abdeckung nie verloren und echte Verstoesse strikt 0; die Verstoesse der Pruefregel sind genau die
 * fensterfern zulaessigen Faelle (K-1, K-2, K-3 mit Kontextwort weiter als 120 Zeichen davor im selben Absatz, Nachtrag
 * 2 zu T-6 Punkt 6 und Nachtrag 5 zu T-6 Punkt 1), deren Zahl je Art exakt festgehalten ist;</li>
 * <li>die Kollisionsfaelle K-1 bis K-4 sind exakte Messwerte, K-1 und K-2 kommen mit Mindestzahl vor;</li>
 * <li>die Bestands-Teilliste (E-Mail, IP, URL, Pfad) ist gleich der des alten Scanners (Bestand hat Vorrang), der
 * Typwechsel eines Nicht-KV-Altfundes ist strikt 0 (Nachrechnung ueber {@code ConflictResolver.resolve},
 * {@link KvnrKollisionsNachrechnung}, ohne Abweichung);</li>
 * <li>Texte, in denen die Pruefregel keinen Verstoss meldet, haben keine S-3-Abweichung.</li>
 * </ul>
 * Selbsttests belegen, dass die Nachpruefung echte Verstoesse nicht als fensterfern durchlaesst und dass der Vergleich
 * der Bestands-Teilliste einen Scanner ohne Filterstufe meldet. Meldungen nennen nur Zahlen, nie Text oder Ziffern.
 */
class KvnrFuzzBestandTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String[] NAMEN = {"Teil 1f", "KVNR-Korpus"};

    /** Messwerte einer Textmenge. */
    private record Messung(int texte, int alteFunde, Ergebnis ergebnis, int s3Geprueft, int s3Verstoesse,
            int s3VerstoesseOhneFensterfern, KvnrKollisionsNachrechnung.Ergebnis nachrechnung, int teillisteUngleich,
            int altFundUngleichZumText, int texteMitFensterfern, int zugeschnitten, int restfunde,
            int ohneFilterTeillisteUngleich) {
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

    private static Messung messe(List<String> texte) {
        int alteFunde = 0;
        int s3Geprueft = 0;
        int s3Verstoesse = 0;
        int s3OhneFensterfern = 0;
        int teillisteUngleich = 0;
        int altFundUngleich = 0;
        int mitFensterfern = 0;
        int zugeschnitten = 0;
        int restfunde = 0;
        int ohneFilterUngleich = 0;
        Ergebnis summe = Ergebnis.NULL;
        KvnrKollisionsNachrechnung.Ergebnis nach = KvnrKollisionsNachrechnung.Ergebnis.NULL;
        for (String text : texte) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            alteFunde += alt.size();
            for (Finding f : alt) {
                altFundUngleich += text.substring(f.start(), f.end()).equals(f.originalValue()) ? 0 : 1;
            }
            Ergebnis e = KvnrFensterfernPruefung.pruefe(text, alt, neu);
            summe = summe.plus(e);
            mitFensterfern += e.fensterfern() > 0 ? 1 : 0;
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
        }
        return new Messung(texte.size(), alteFunde, summe, s3Geprueft, s3Verstoesse, s3OhneFensterfern, nach,
                teillisteUngleich, altFundUngleich, mitFensterfern, zugeschnitten, restfunde, ohneFilterUngleich);
    }

    private static List<String> texte1f() {
        List<String> texte = new ArrayList<>();
        for (RoundtripFuzzTest.FallF f : RoundtripFuzzTest.erzeugeKvF(RoundtripFuzzTest.SEED_TEXTE_KV_F)) {
            texte.add(f.text());
        }
        return texte;
    }

    private static List<String> texteKorpus() {
        List<String> texte = new ArrayList<>();
        for (RoundtripKorpusTest.KvFall k : RoundtripKorpusTest.kvnrFaelle()) {
            texte.add(k.text());
        }
        return texte;
    }

    private static Messung[] messungen;

    private static synchronized Messung[] messungen() {
        if (messungen == null) {
            messungen = new Messung[] {messe(texte1f()), messe(texteKorpus())};
        }
        return messungen;
    }

    // ------------------------------------------------------------------ Bestandsschutz auf den neuen Mengen

    /**
     * Abdeckung nie verloren und echte Verstoesse strikt 0 (die Pruefregel meldet Verstoesse nur fuer die
     * fensterfern zulaessigen Faelle); kein alter Fund wechselt auf eine andere Weise Typ, Bereich oder Konfidenz.
     */
    @Test
    void ak19_neueMengen_keinEchterVerstoss_abdeckungNieVerloren() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].ergebnis().echteVerstoesse(), NAMEN[i] + ": echte Verstoesse; "
                    + m[i].ergebnis().kurz());
        }
    }

    /**
     * Fensterfern zulaessige Faelle (Kontextwort weiter als 120 Zeichen davor, im selben Absatz): exakte Zahl je Art.
     * Teil 1f: 49 K-2-Faelle in 8 Texten (lange Aufzaehlung hinter KVNR- und LANR-Kontextwort mit Ziffernfolgen: die
     * hinteren Nummern sind alte LANR-Funde und neue KVNR-Funde mit gleicher Konfidenz 0,40 im gleichen Bereich; das
     * Kontextwort wirkt als Listenkontext weiter), K-1 0, K-3 0 (ein K-3-Fall braucht einen Restfund ueber einem
     * alten Fund mit niedrigerer Konfidenz, Nachtrag 5 zu T-6 Punkt 1; bei diesem Seed kommt er nicht vor: exakt 0
     * ohne Mindestwert, Nachtrag zu T-7); alle 49 haben ein zusammenhaengendes Kontextwort davor (nur verstreut: 0).
     * Korpus: 0.
     */
    @Test
    void ak19_neueMengen_fensterfernZulaessigeFaelle_exakteZahlenJeArt() {
        Messung[] m = messungen();
        Ergebnis f1 = m[0].ergebnis();
        assertEquals(0, f1.fensterfernK1(), "Teil 1f: fensterfern K-1; " + f1.kurz());
        assertEquals(49, f1.fensterfernK2(), "Teil 1f: fensterfern K-2; " + f1.kurz());
        assertEquals(0, f1.fensterfernK3(), "Teil 1f: fensterfern K-3; " + f1.kurz());
        assertEquals(0, f1.fensterfernVerstreut(), "Teil 1f: fensterfern nur ueber verstreute Buchstaben; "
                + f1.kurz());
        assertEquals(8, m[0].texteMitFensterfern(), "Teil 1f: Texte mit fensterfernem Fall");
        assertEquals(0, m[1].ergebnis().fensterfern(), "Korpus: fensterferne Faelle; " + m[1].ergebnis().kurz());
        assertTrue(f1.fensterfern() >= MIN_FENSTERFERN, "zu wenig fensterferne Faelle " + f1.fensterfern());
        assertTrue(f1.fensterfern() <= MAX_FENSTERFERN, "zu viele fensterferne Faelle " + f1.fensterfern());
    }

    private static final int MIN_FENSTERFERN = 34;
    private static final int MAX_FENSTERFERN = 70;

    /**
     * Kollisionsfaelle (zulaessig im Fenster der Pruefregel), exakte Messwerte. Teil 1f: K-1 1 532, K-2 336, K-3 0
     * (exakt 0, kein Mindestwert: ein K-3-Fall braucht einen Restfund ueber einem alten Fund, siehe Nachtrag zu T-7),
     * K-4 0 (K-4 gibt es nur zwischen KV-Arten und bei Teilueberlappung ohne Trenner, die der Generator nicht
     * erzeugt); Untergrenzen K-1 und K-2 bei 70 % der Messwerte. Korpus: K-1 und K-2 wie in
     * {@code kn-k1-...} und {@code kn-k2-...}.
     */
    @Test
    void ak19_neueMengen_kollisionsfaelle_exakteMesswerteUndMindestzahlen() {
        Messung[] m = messungen();
        Ergebnis f1 = m[0].ergebnis();
        assertEquals(1532, f1.k1(), "Teil 1f: K-1; " + f1.kurz());
        assertEquals(336, f1.k2(), "Teil 1f: K-2; " + f1.kurz());
        assertEquals(0, f1.k3(), "Teil 1f: K-3; " + f1.kurz());
        assertEquals(0, f1.k4(), "Teil 1f: K-4; " + f1.kurz());
        assertTrue(f1.k1() >= 1072, "zu wenig K-1-Faelle in Teil 1f");
        assertTrue(f1.k2() >= 235, "zu wenig K-2-Faelle in Teil 1f");
        Ergebnis k = m[1].ergebnis();
        assertEquals(KORPUS_K1, k.k1(), "Korpus: K-1; " + k.kurz());
        assertEquals(KORPUS_K2, k.k2(), "Korpus: K-2; " + k.kurz());
        assertEquals(0, k.k3(), "Korpus: K-3; " + k.kurz());
        assertEquals(0, k.k4(), "Korpus: K-4; " + k.kurz());
    }

    private static final int KORPUS_K1 = 3;
    private static final int KORPUS_K2 = 1;

    /**
     * Bestand hat Vorrang: Die Teilliste der Funde von E-Mail, IP, URL und Pfad ist in jedem Text gleich der des alten
     * Scanners (alle Felder, gleiche Reihenfolge); der Typwechsel eines Nicht-KV-Altfundes ist strikt 0; die
     * unabhaengige Nachrechnung ueber {@code ConflictResolver.resolve} findet keine Abweichung (K-4-Faelle, ungleiche
     * Listen), und ihre Kollisionszahl ist die Summe der zulaessigen und der fensterfern zulaessigen Faelle; der
     * Originalwert aller alten Funde ist der Textausschnitt.
     */
    @Test
    void ak19_neueMengen_bestandHatVorrang_nachrechnungOhneAbweichung() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            KvnrKollisionsNachrechnung.Ergebnis n = m[i].nachrechnung();
            Ergebnis e = m[i].ergebnis();
            assertEquals(0, m[i].teillisteUngleich(), NAMEN[i] + ": Texte mit ungleicher Bestands-Teilliste");
            assertEquals(0, n.typwechselNichtKv(), NAMEN[i] + ": Typwechsel Nicht-KV; " + n.kurz());
            assertEquals(0, n.k4Abweichungen(), NAMEN[i] + ": K-4-Abweichungen; " + n.kurz());
            assertEquals(0, n.listenUngleich(), NAMEN[i] + ": Texte mit ungleicher nachgerechneter Liste; " + n.kurz());
            assertEquals(e.k4(), n.k4Faelle(), NAMEN[i] + ": K-4 der Pruefregel gegen Nachrechnung");
            assertEquals(e.k1() + e.k2() + e.k3() + e.k4() + e.fensterfern(), n.kollisionen(),
                    NAMEN[i] + ": Kollisionen der Pruefregel gegen Nachrechnung; " + n.kurz());
            assertEquals(0, m[i].altFundUngleichZumText(), NAMEN[i] + ": alte Funde mit Originalwert ungleich Text");
        }
    }

    /**
     * S-3: In Texten ohne fensterfernen Fall gibt es keine Abweichung. Die S-3-Pruefung zaehlt einen fensterfernen Fall
     * zweimal (der alte Fund fehlt, der neue steht ausserhalb des Fensters); in Teil 1f sind das 2 mal 49 = 98
     * Abweichungen, im Korpus 0.
     */
    @Test
    void ak19_neueMengen_s3_keineAbweichungAusserBeiFensterfernenFaellen() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].s3VerstoesseOhneFensterfern(),
                    NAMEN[i] + ": S-3-Abweichungen ohne fensterfernen Fall");
        }
        assertEquals(2 * m[0].ergebnis().fensterfern(), m[0].s3Verstoesse(),
                "Teil 1f: S-3 nur an fensterfernen Faellen");
        assertEquals(0, m[1].s3Verstoesse(), "Korpus: S-3-Abweichungen");
    }

    /**
     * Die Pruefung laeuft nicht ins Leere (Untergrenzen 70 % der Messwerte): alte Funde (Teil 1f 5 751, Korpus
     * 23), von S-3 geprueft (590), Texte mit zugeschnittenem KVNR-Kandidaten (348), Restfunde (66).
     * Selbsttest des Vergleichs: Ohne Filterstufe weicht die Bestands-Teilliste in einer Mindestzahl von Texten ab
     * (Teil 1f 98, Korpus 5).
     */
    @Test
    void ak19_neueMengen_mindestzahlenDerPruefungenUndSelbsttestOhneFilter() {
        Messung[] m = messungen();
        assertTrue(m[0].alteFunde() >= 4025, "Teil 1f: zu wenig alte Funde " + m[0].alteFunde());
        assertTrue(m[0].s3Geprueft() >= 413, "Teil 1f: zu wenig S-3-Pruefungen " + m[0].s3Geprueft());
        assertTrue(m[0].zugeschnitten() >= 243, "Teil 1f: zu wenig Texte mit zugeschnittenem Kandidaten "
                + m[0].zugeschnitten());
        assertTrue(m[0].restfunde() >= 46, "Teil 1f: zu wenig Restfunde " + m[0].restfunde());
        assertTrue(m[0].ohneFilterTeillisteUngleich() >= 68, "Teil 1f: Scanner ohne Filter zu selten gemeldet "
                + m[0].ohneFilterTeillisteUngleich());
        assertTrue(m[1].alteFunde() >= MIN_KORPUS_FUNDE, "Korpus: zu wenig alte Funde " + m[1].alteFunde());
        assertTrue(m[1].ohneFilterTeillisteUngleich() >= MIN_KORPUS_OHNE_FILTER,
                "Korpus: Scanner ohne Filter zu selten gemeldet " + m[1].ohneFilterTeillisteUngleich());
    }

    private static final int MIN_KORPUS_FUNDE = 16;
    private static final int MIN_KORPUS_OHNE_FILTER = 3;
}
