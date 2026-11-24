package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.KvnrFensterfernPruefung.Ergebnis;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvStandTrenner;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz auf Scannerebene fuer die neue Textmenge Fuzz-Teil 1h (Feature kvnr-bsnr-erkennung, T-15; AK-32,
 * Nachtrag zu Teil 2 Punkt 1: neue Textmengen in neuen Testklassen, weil {@code KvStandTrennerDifferenzTest}
 * eingefroren ist). Die Klasse folgt dem Muster von {@code KvnrFuzzBestandTest} und {@code BsnrScannerBestandTest}.
 * Auf der Menge gilt mit der eingefrorenen Pruefregel {@code KvnrBsnrHilfen.bestandsabweichungen}
 * ({@code ALT_TRENNER} gegen {@code TextScanner.standard()}) und der Nachpruefung {@link KvnrFensterfernPruefung}:
 * <ul>
 * <li>Siegel: Ein Hash ueber alle Funde der eingefrorenen Kopie (LANR und IK, Erkennerebene) und ein zweiter ueber die
 * vollstaendige Ausgabe von {@code ALT_TRENNER} (alle Typen, alle Felder, mit Originalwert) halten Kopie, Generator
 * und die uebrigen Erkenner fest (wie die Siegel in {@code KvStandTrennerDifferenzTest}, die Hash-Verfahren sind
 * dieselben); {@code LanrRecognizer} und {@code IkRecognizer} liefern auf jedem Text dieselbe Fundliste wie die
 * Kopie;</li>
 * <li>S-1: Jede Position, die der eingefrorene Stand abdeckt, deckt auch der neue Scanner ab;</li>
 * <li>S-2: Kein echter Verstoss. K-4 ist strikt 0; K-1 (der KVNR-Fund umschliesst den alten LANR-/IK-Fund), K-2 und
 * K-3 (der BSNR-Fund uebernimmt den Bereich eines alten LANR-/IK-Fundes bei gleicher bzw. hoeherer Konfidenz) sind
 * exakte Messwerte mit Mindestzahl; fensterferne Faelle gibt es nicht;</li>
 * <li>S-3: Jeder Fund, in dessen Naehe weder Kontextwort noch die enge KVNR-Form steht, hat in der anderen Liste einen
 * gleichen Fund;</li>
 * <li>Bestand hat Vorrang: Die Teilliste der Funde von E-Mail, IP, URL und Pfad ist in jedem Text gleich der des alten
 * Scanners, der Typwechsel eines Nicht-KV-Altfundes ist strikt 0 (unabhaengige Nachrechnung ueber
 * {@code ConflictResolver.resolve}, {@link KvnrKollisionsNachrechnung#rechne}).</li>
 * </ul>
 * Selbsttests belegen, dass die Pruefungen nicht ins Leere laufen: Ein Scanner ohne LANR- und IK-Erkenner laesst S-1
 * anschlagen, einer mit zusaetzlichem BSNR-Fund S-3, einer ohne BSNR-Erkenner zeigt, dass K-2 und K-3 allein vom
 * BSNR-Erkenner kommen. Meldungen nennen nur Zahlen und Namen, nie Text oder Ziffern.
 */
class BsnrFuzzBestandTest {

    /**
     * Siegel der Funde der Kopie (LANR und IK, Erkennerebene) auf Fuzz-Teil 1h (hexadezimal), gemessen in T-15. Das
     * Verfahren ist das der Siegel in {@code KvStandTrennerDifferenzTest}.
     */
    static final int SIEGEL_TRENNER_TEIL_1H = 0x56f2329a;
    /** Siegel der Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1h (alle Felder, mit Originalwert), in T-15. */
    static final int SIEGEL_ALT_TEIL_1H = 0xd4e8a5b4;

    private static final int SIEGEL_START = 17;
    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;

    /** Messwerte der Textmenge von Teil 1h. */
    private record Messung(int texte, int siegel, int siegelAlt, int ungleichKopieProdukt, int kopieFundeErkenner,
            int alteFunde, int neueFunde, int bsnrFunde, int bsnrRestfunde, int verloren, Ergebnis ergebnis,
            int s3Geprueft, int s3Verstoesse, int teillisteUngleich, int typwechselNichtKv,
            int altFundUngleichZumText) {
    }

    private static List<String> texte1h() {
        List<String> texte = new ArrayList<>();
        for (RoundtripFuzzTest.FallH f : RoundtripFuzzTest.erzeugeKvH(RoundtripFuzzTest.SEED_TEXTE_KV_H)) {
            texte.add(f.text());
        }
        return texte;
    }

    /** Hash aus den Feldern der Funde selbst (wie in {@code KvStandTrennerDifferenzTest}), stabil ueber Laeufe. */
    private static int hash(int h, List<Finding> funde) {
        int ergebnis = 31 * h + funde.size();
        for (Finding f : funde) {
            long bits = Double.doubleToLongBits(f.confidence());
            ergebnis = 31 * ergebnis + f.start();
            ergebnis = 31 * ergebnis + f.end();
            ergebnis = 31 * ergebnis + f.type().name().hashCode();
            ergebnis = 31 * ergebnis + (int) (bits ^ (bits >>> 32));
            ergebnis = 31 * ergebnis + f.source().hashCode();
        }
        return ergebnis;
    }

    private static int hashMitOriginalwert(int h, List<Finding> funde) {
        int ergebnis = hash(h, funde);
        for (Finding f : funde) {
            ergebnis = 31 * ergebnis + f.originalValue().hashCode();
        }
        return ergebnis;
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

    private static int bsnrFunde(List<Finding> funde, boolean nurRest) {
        int n = 0;
        for (Finding f : funde) {
            n += f.type() == FindingType.BSNR && (!nurRest || BsnrRecognizer.SOURCE_REST.equals(f.source())) ? 1 : 0;
        }
        return n;
    }

    private static Messung messe(List<String> texte) {
        Recognizer[] kopien = {KvStandTrenner.lanr(), KvStandTrenner.ik()};
        Recognizer[] produkt = {new LanrRecognizer(), new IkRecognizer()};
        int siegel = SIEGEL_START;
        int siegelAlt = SIEGEL_START;
        int ungleichKopie = 0;
        int kopieFunde = 0;
        int alteFunde = 0;
        int neueFunde = 0;
        int bsnr = 0;
        int rest = 0;
        int verloren = 0;
        int s3Geprueft = 0;
        int s3Verstoesse = 0;
        int teillisteUngleich = 0;
        int typwechsel = 0;
        int altFundUngleich = 0;
        Ergebnis summe = Ergebnis.NULL;
        for (String text : texte) {
            for (int a = 0; a < kopien.length; a++) {
                List<Finding> kopie = kopien[a].detect(text);
                siegel = hash(siegel, kopie);
                ungleichKopie += kopie.equals(produkt[a].detect(text)) ? 0 : 1;
                kopieFunde += kopie.size();
            }
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            siegelAlt = hashMitOriginalwert(siegelAlt, alt);
            alteFunde += alt.size();
            neueFunde += neu.size();
            bsnr += bsnrFunde(neu, false);
            rest += bsnrFunde(neu, true);
            verloren += abdeckungVerloren(text.length(), alt, neu) ? 1 : 0;
            for (Finding f : alt) {
                altFundUngleich += text.substring(f.start(), f.end()).equals(f.originalValue()) ? 0 : 1;
            }
            summe = summe.plus(KvnrFensterfernPruefung.pruefe(text, alt, neu));
            S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, alt, neu);
            s3Geprueft += s3.geprueft();
            s3Verstoesse += s3.verstoesse();
            teillisteUngleich += bestand(alt).equals(bestand(neu)) ? 0 : 1;
            typwechsel += KvnrKollisionsNachrechnung.rechne(text, alt, neu).typwechselNichtKv();
        }
        return new Messung(texte.size(), siegel, siegelAlt, ungleichKopie, kopieFunde, alteFunde, neueFunde, bsnr,
                rest, verloren, summe, s3Geprueft, s3Verstoesse, teillisteUngleich, typwechsel, altFundUngleich);
    }

    private static Messung messung;
    private static List<String> texte;

    private static synchronized Messung messung() {
        if (messung == null) {
            messung = messe(texte());
        }
        return messung;
    }

    private static synchronized List<String> texte() {
        if (texte == null) {
            texte = texte1h();
        }
        return texte;
    }

    // ------------------------------------------------------------------ Siegel und Erkennerebene

    /** Hash ueber die Funde der Kopie (LANR und IK); ein Fehlschlag nennt den gemessenen Wert. */
    @Test
    void siegel_teil1h_fundbildDerKopieIstEingefroren() {
        assertEquals(SIEGEL_TRENNER_TEIL_1H, messung().siegel(), "Siegel Teil 1h, gemessen 0x"
                + Integer.toHexString(messung().siegel()));
    }

    /**
     * Hash ueber die vollstaendige Ausgabe von {@code ALT_TRENNER} (alle Typen, alle Felder, mit Originalwert): Eine
     * Aenderung an den uebrigen Erkennern, am {@code TextScanner} oder am {@code ConflictResolver} faellt auf.
     */
    @Test
    void siegel_teil1h_ausgabeDesAltScannersIstEingefroren() {
        assertEquals(SIEGEL_ALT_TEIL_1H, messung().siegelAlt(), "Siegel ALT_TRENNER Teil 1h, gemessen 0x"
                + Integer.toHexString(messung().siegelAlt()));
    }

    /**
     * E: {@code LanrRecognizer} und {@code IkRecognizer} liefern auf jedem Text von Teil 1h dieselbe Fundliste wie die
     * Kopie (alle Felder); bei alten Funden ist der Originalwert der Textausschnitt.
     */
    @Test
    void e_teil1h_lanrUndIkAufErkennerebeneGleichDerKopie() {
        assertEquals(0, messung().ungleichKopieProdukt(), "Teil 1h: Texte mit abweichender Fundliste LANR oder IK");
        assertEquals(0, messung().altFundUngleichZumText(), "Teil 1h: alte Funde mit Originalwert ungleich Text");
    }

    // ------------------------------------------------------------------ S-1, S-2, K-Zaehler

    /** S-1: Jede Position des eingefrorenen Standes bleibt abgedeckt (Zahl der Texte mit Verlust: strikt 0). */
    @Test
    void s1_teil1h_jedePositionDesAltenStandsBleibtAbgedeckt() {
        assertEquals(0, messung().verloren(), "Teil 1h: Texte mit verlorener Abdeckung");
    }

    /** S-2: Kein echter Verstoss; es gibt keine fensterfernen Faelle und keinen K-4-Fall. */
    @Test
    void s2_teil1h_keinEchterVerstossKeinFensterfernerFallKeinK4() {
        Ergebnis e = messung().ergebnis();
        assertEquals(0, e.echteVerstoesse(), "Teil 1h: echte Verstoesse; " + e.kurz());
        assertEquals(0, e.fensterfern(), "Teil 1h: fensterferne Faelle; " + e.kurz());
        assertEquals(0, e.k4(), "Teil 1h: K-4; " + e.kurz());
    }

    /**
     * Kollisionsfaelle (zulaessig im Fenster der Pruefregel), exakte Messwerte mit Untergrenze bei 70 %. K-1: 81 (56):
     * der KVNR-Fund (Buchstabe und Ziffern) umschliesst einen alten LANR- oder IK-Fund auf den Ziffern,
     * aufgeschluesselt nach altem und neuem Fund etwa IK 0,50 zu KVNR 0,95 21 Faelle, LANR 0,50 zu KVNR 0,95 12.
     * K-2: 292 (204): Der BSNR-Fund hat bei gleichem Bereich dieselbe Konfidenz wie der alte LANR-/IK-Fund und
     * gewinnt nach der Konfliktregel (Typname BSNR vor IK und LANR); gemessen 0,40 gegen 0,40 (Laengenabweichung)
     * 125 Faelle und 0,50 gegen 0,50 (unbekannter Regionalcode gegen falsche Pruefziffer) 167 Faelle. K-3: 751 (525):
     * Der BSNR-Fund hat die hoehere Konfidenz (0,60 bis 0,90) als der alte LANR-/IK-Fund mit falscher Pruefziffer
     * (0,50) auf demselben Bereich. Auf den elf Bestandsmengen sind K-2 und K-3 je 0
     * ({@code BsnrScannerBestandTest}); hier entstehen sie allein durch die BSNR-Kontextwoerter.
     */
    @Test
    void s2_teil1h_kollisionsfaelle_exakteMesswerteUndMindestzahlen() {
        Ergebnis e = messung().ergebnis();
        assertEquals(81, e.k1(), "Teil 1h: K-1; " + e.kurz());
        assertEquals(292, e.k2(), "Teil 1h: K-2; " + e.kurz());
        assertEquals(751, e.k3(), "Teil 1h: K-3; " + e.kurz());
        assertTrue(e.k1() >= 56, "zu wenig K-1-Faelle in Teil 1h: " + e.k1());
        assertTrue(e.k2() >= 204, "zu wenig K-2-Faelle in Teil 1h: " + e.k2());
        assertTrue(e.k3() >= 525, "zu wenig K-3-Faelle in Teil 1h: " + e.k3());
    }

    // ------------------------------------------------------------------ S-3 und Bestand hat Vorrang

    /** S-3: Kein Fund ohne Kontextwort oder Form in der Naehe hat keinen gleichen Fund in der anderen Liste. */
    @Test
    void s3_teil1h_keineAbweichungOhneKontextwortInDerNaehe() {
        assertEquals(0, messung().s3Verstoesse(), "Teil 1h: S-3-Abweichungen bei " + messung().s3Geprueft()
                + " geprueften Funden");
    }

    /**
     * Bestand hat Vorrang: Die Teilliste der Funde von E-Mail, IP, URL und Pfad ist in jedem Text gleich der des alten
     * Scanners (alle Felder, gleiche Reihenfolge); der Typwechsel eines Nicht-KV-Altfundes ist strikt 0.
     */
    @Test
    void bestandHatVorrang_teil1h_teillisteGleichUndKeinTypwechselNichtKv() {
        assertEquals(0, messung().teillisteUngleich(), "Teil 1h: Texte mit ungleicher Bestands-Teilliste");
        assertEquals(0, messung().typwechselNichtKv(), "Teil 1h: Typwechsel eines Nicht-KV-Altfundes");
    }

    /**
     * Die Pruefungen laufen nicht ins Leere (Untergrenzen 70 % der Messwerte): Texte 1 000; Funde der Kopie auf
     * Erkennerebene 3 308 (2 315); alte Funde 3 138 (2 196); neue Funde 7 624 (5 336); BSNR-Funde im Scan 5 202
     * (3 641); von S-3 geprueft 74 (51). Restfunde ({@code bsnr-rest}) gibt es nicht (exakt 0): Teil 1h hat keinen
     * Bestandsfund, der einen BSNR-Kandidaten schneidet.
     */
    @Test
    void mindestzahlenDerPruefungen_teil1h() {
        Messung m = messung();
        assertEquals(1_000, m.texte(), "Zahl der Texte");
        assertTrue(m.kopieFundeErkenner() >= 2_315, "zu wenig Funde der Kopie " + m.kopieFundeErkenner());
        assertTrue(m.alteFunde() >= 2_196, "zu wenig alte Funde " + m.alteFunde());
        assertTrue(m.neueFunde() >= 5_336, "zu wenig neue Funde " + m.neueFunde());
        assertTrue(m.bsnrFunde() >= 3_641, "zu wenig BSNR-Funde " + m.bsnrFunde());
        assertTrue(m.s3Geprueft() >= 51, "zu wenig S-3-Pruefungen " + m.s3Geprueft());
        assertEquals(0, m.bsnrRestfunde(), "BSNR-Restfunde");
    }

    // ------------------------------------------------------------------ Selbsttests

    private static List<Recognizer> standardOhne(Class<?>... klassen) {
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.removeIf(r -> Arrays.stream(klassen).anyMatch(k -> k.isInstance(r)));
        return liste;
    }

    /**
     * Selbsttest S-1: Ein Scanner ohne LANR- und IK-Erkenner verliert Abdeckung; die Pruefregel meldet einen Verstoss
     * in einer Mindestzahl von Texten (Istwert 811 von 858 Texten mit alten Funden, Untergrenze 567).
     */
    @Test
    void selbsttest_scannerOhneLanrUndIkWirdVonDerPruefregelGemeldet() {
        TextScanner ohne = new TextScanner(standardOhne(LanrRecognizer.class, IkRecognizer.class));
        int betroffen = 0;
        for (String text : texte()) {
            List<Finding> alt = ALT.scan(text);
            betroffen += KvnrBsnrHilfen.bestandsabweichungen(text, alt, ohne.scan(text)).verstoesse() > 0 ? 1 : 0;
        }
        assertTrue(betroffen >= 567, "Scanner ohne LANR und IK zu selten gemeldet: " + betroffen);
    }

    /**
     * Selbsttest S-3: Ein Scanner mit einem zusaetzlichen Erkenner, der in jedem Text einen BSNR-Fund an den ersten
     * drei Zeichen liefert, wird von S-3 in einer Mindestzahl von Texten gemeldet (Istwert 7, Untergrenze 4; der Fund
     * liegt meist in der Naehe eines Kontextworts, wo S-3 nicht prueft), und die Fundliste weicht in einer
     * Mindestzahl von Texten ab (Istwert 895, Untergrenze 626).
     */
    @Test
    void selbsttest_scannerMitZusaetzlichemBsnrFundWirdVonS3UndVomListenvergleichGemeldet() {
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.add(text -> text.length() >= 3
                ? List.of(new Finding(FindingType.BSNR, text.substring(0, 3), 0, 3, 0.90, "mutant"))
                : List.of());
        TextScanner mutant = new TextScanner(liste);
        int s3Texte = 0;
        int ungleich = 0;
        for (String text : texte()) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> funde = mutant.scan(text);
            s3Texte += KvnrBsnrHilfen.s3Pruefung(text, alt, funde).verstoesse() > 0 ? 1 : 0;
            ungleich += funde.equals(NEU.scan(text)) ? 0 : 1;
        }
        assertTrue(s3Texte >= 4, "Mutant von S-3 zu selten gemeldet: " + s3Texte);
        assertTrue(ungleich >= 626, "Mutant im Listenvergleich zu selten gemeldet: " + ungleich);
    }

    /**
     * Selbsttest K-2 und K-3: Ohne den BSNR-Erkenner gibt es keinen BSNR-Fund, K-1 bleibt gleich (81), und K-2 plus K-3
     * schrumpfen auf den einen K-2-Fall aus einem Mischbaustein (Istwert 1): Die K-Zaehler messen den BSNR-Erkenner,
     * nicht den Rest. Die Fundlisten weichen in einer Mindestzahl von Texten ab (Istwert 990, Untergrenze 693).
     */
    @Test
    void selbsttest_scannerOhneBsnrErkennerZeigtDassK2UndK3VomBsnrErkennerKommen() {
        TextScanner ohne = new TextScanner(standardOhne(BsnrRecognizer.class));
        Abweichungen summe = KvnrBsnrHilfen.KEINE;
        int bsnr = 0;
        int ungleich = 0;
        for (String text : texte()) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> funde = ohne.scan(text);
            summe = summe.plus(KvnrBsnrHilfen.bestandsabweichungen(text, alt, funde));
            bsnr += bsnrFunde(funde, false);
            ungleich += funde.equals(NEU.scan(text)) ? 0 : 1;
        }
        assertEquals(0, bsnr, "BSNR-Funde im Scanner ohne BSNR-Erkenner");
        assertEquals(0, summe.verstoesse(), "Verstoesse im Scanner ohne BSNR-Erkenner; " + summe.kurz());
        assertEquals(messung().ergebnis().k1(), summe.k1(), "K-1 unabhaengig vom BSNR-Erkenner; " + summe.kurz());
        assertEquals(1, summe.k2() + summe.k3(), "K-2 plus K-3 ohne BSNR-Erkenner; " + summe.kurz());
        assertTrue(ungleich >= 693, "Scanner ohne BSNR-Erkenner zu selten gemeldet: " + ungleich);
    }
}
