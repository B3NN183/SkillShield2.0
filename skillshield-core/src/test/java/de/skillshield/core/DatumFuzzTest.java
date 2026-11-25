package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Die 100 000er-Laeufe nach AK-11 c und d fuer Teil A des Features datum-und-kontextvorrang (T-3). Alle Daten
 * entstehen zur Laufzeit aus {@link DatumFormen} mit festem Seed je Form; ob ein Wert ein Datum ist, entscheidet beim
 * Erzeugen {@link DatumReferenz}.
 *
 * <p><b>c) Gueltige Daten</b>: Je Form (Punkt vierstellig, Punkt zweistellig, Schraegstrich, Bindestrich, ISO,
 * Zeitraum) 100 000 Texte "Kontextwort, Trenner, Datum", die Kontextwoerter reihum aus allen vier Arten (auch
 * Langformen), Trenner Leerzeichen und {@code ": "}. Erwartet: kein Fund der vier Erkenner ueber dem Datum. Einzige
 * Ausnahme sind Zeitraeume, deren Praefix genau neun Ziffern erreicht (Neun-Ziffern-Regel, Grenze aus A-2): dort bleibt
 * ein Fund mit genau neun Ziffern; die Zahl steht exakt fest, und jeder Fund wird auf diese Eigenschaft geprueft. Als
 * Gegenprobe zaehlt die eingefrorene Kopie je Form die Texte mit Fund (sie ist nicht leer).
 *
 * <p><b>d) Ungueltige Daten und Nicht-Daten</b>: Je Form 100 000 Texte mit kalendarisch oder im Jahresbereich
 * ungueltigem Wert, dazu 100 000 Texte mit Nicht-Datumsformen. Erwartet: Die Fundliste jedes Erkenners ist gleich der
 * der Kopie, und die Kopie hat dort keinen Fund ueber einem Datumssegment. Je Form laeuft zusaetzlich eine feste
 * Stichprobe von 1 000 Texten ueber {@code TextScanner.standard()} gegen {@code ALT_DATUM}. Meldungen nennen nur
 * Zahlen.
 */
class DatumFuzzTest {

    private static final int TEXTE_JE_FORM = 100_000;
    private static final int STICHPROBE = 1_000;
    private static final long SEED_GUELTIG = 20261202L;
    private static final long SEED_UNGUELTIG = 20261203L;
    private static final long SEED_KEIN_DATUM = 20261204L;

    private static final List<String> KONTEXTWOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer", "Arztnummer", "Betriebsstaettennummer", "Institutionskennzeichen");
    private static final List<String> TRENNER = List.of(" ", ": ");

    private static final List<Recognizer> PRODUKT = List.of(new LanrRecognizer(), new IkRecognizer(),
            new KvnrRecognizer(), new BsnrRecognizer());
    private static final List<Recognizer> KOPIE = KvStandDatum.alle();
    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;

    /**
     * Zeitraeume mit Neun-Ziffern-Praefix je 100 000 Zeitraeumen (Istwert; Grenze aus A-2). Der Anteil ist hoch, weil
     * Tag und Monat hier zufaellig ein- oder zweistellig geschrieben werden; mit durchgehend zweistelligen Werten
     * erreicht kein Praefix neun Ziffern (siehe {@link #ak11c_zeitraumMitZweistelligenWerten_keinFund}).
     */
    private static final int ZEITRAUM_NEUN_ZIFFERN = 47_585;

    /** Texte mit Fund der Kopie je Form (c): Istwerte; Formen mit mindestens sechs Ziffern. */
    private static final int[] KOPIE_MIT_FUND_GUELTIG = {100_000, 53_231, 100_000, 100_000, 100_000, 100_000};

    /** Texte mit Fund der Kopie je Form (d), ungueltige Werte: Istwerte. */
    private static final int[] KOPIE_MIT_FUND_UNGUELTIG = {100_000, 100_000, 100_000, 100_000, 100_000, 100_000};

    /** Texte mit Fund der Kopie bei Nicht-Datumsformen (d): Istwert. */
    private static final int KOPIE_MIT_FUND_KEIN_DATUM = 100_000;

    private static String text(Random r, int i, String datum) {
        return KONTEXTWOERTER.get(i % KONTEXTWOERTER.size()) + TRENNER.get(r.nextInt(TRENNER.size())) + datum;
    }

    private static boolean ueberDatum(String text, List<Finding> funde) {
        for (Finding f : funde) {
            if (DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                return true;
            }
        }
        return false;
    }

    /** Ergebnis eines Laufs ueber eine Form. */
    private record Zaehlung(int texte, int produktMitFund, int kopieMitFund, int produktFundNichtNeunZiffern,
            int ungleichKopie, int kopieFundUeberDatum, int ungleichStichprobe) {
    }

    private static final Map<String, Zaehlung> ZAEHLUNGEN = new HashMap<>();

    /** Einmal je Parameter berechnet; mehrere Tests lesen denselben Lauf. */
    private static synchronized Zaehlung lauf(long seed, int form, boolean gueltig, boolean keinDatum) {
        String schluessel = seed + ":" + form + ":" + gueltig + ":" + keinDatum;
        Zaehlung z = ZAEHLUNGEN.get(schluessel);
        if (z == null) {
            z = rechne(seed, form, gueltig, keinDatum);
            ZAEHLUNGEN.put(schluessel, z);
        }
        return z;
    }

    private static Zaehlung rechne(long seed, int form, boolean gueltig, boolean keinDatum) {
        Random r = new Random(seed + form);
        int produktMitFund = 0;
        int kopieMitFund = 0;
        int nichtNeun = 0;
        int ungleich = 0;
        int kopieDatum = 0;
        int stichprobe = 0;
        for (int i = 0; i < TEXTE_JE_FORM; i++) {
            String datum = keinDatum ? DatumFormen.keinDatum(r)
                    : gueltig ? DatumFormen.gueltig(r, form) : DatumFormen.ungueltig(r, form);
            String text = text(r, i, datum);
            boolean produktFund = false;
            boolean kopieFund = false;
            for (int a = 0; a < PRODUKT.size(); a++) {
                List<Finding> produkt = PRODUKT.get(a).detect(text);
                List<Finding> kopie = KOPIE.get(a).detect(text);
                produktFund |= !produkt.isEmpty();
                kopieFund |= !kopie.isEmpty();
                ungleich += produkt.equals(kopie) ? 0 : 1;
                kopieDatum += ueberDatum(text, kopie) ? 1 : 0;
                for (Finding f : produkt) {
                    nichtNeun += DatumReferenz.ziffern(text, f.start(), f.end()) == 9 ? 0 : 1;
                }
            }
            produktMitFund += produktFund ? 1 : 0;
            kopieMitFund += kopieFund ? 1 : 0;
            if (i < STICHPROBE) {
                List<Finding> erwartet = gueltig ? altOhneDatumssegmente(text) : ALT.scan(text);
                stichprobe += NEU.scan(text).equals(erwartet) ? 0 : 1;
            }
        }
        return new Zaehlung(TEXTE_JE_FORM, produktMitFund, kopieMitFund, nichtNeun, ungleich, kopieDatum, stichprobe);
    }

    /** Die Fundliste von {@code ALT_DATUM} ohne die Funde, deren Bereich ein Datumssegment ist (Laeufe c). */
    private static List<Finding> altOhneDatumssegmente(String text) {
        List<Finding> rest = new ArrayList<>();
        for (Finding f : ALT.scan(text)) {
            if (!DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                rest.add(f);
            }
        }
        return rest;
    }

    // ------------------------------------------------------------------ c) gueltige Daten

    /** AK-11 c: Gueltige Daten je Form hinter Kontextwoertern aller Arten bleiben ohne Fund. */
    @Test
    void ak11c_gueltigeDatenJeForm_keinFundUeberDemDatum() {
        for (int form = 0; form < DatumFormen.ANZAHL; form++) {
            Zaehlung z = lauf(SEED_GUELTIG, form, true, false);
            String name = DatumFormen.NAMEN[form];
            boolean zeitraum = form == DatumFormen.ZEITRAUM;
            assertEquals(TEXTE_JE_FORM, z.texte(), name + ": Zahl der Texte");
            assertEquals(zeitraum ? ZEITRAUM_NEUN_ZIFFERN : 0, z.produktMitFund(), name + ": Texte mit Fund");
            assertEquals(0, z.produktFundNichtNeunZiffern(), name + ": Funde ohne genau neun Ziffern");
            assertEquals(KOPIE_MIT_FUND_GUELTIG[form], z.kopieMitFund(), name + ": Gegenprobe Kopie");
            assertEquals(0, z.ungleichStichprobe(), name + ": Stichprobe ueber standard()");
        }
    }

    /** AK-11 c, Zeitraum in der ueblichen Schreibweise (Tag und Monat immer zweistellig): nie ein Fund. */
    @Test
    void ak11c_zeitraumMitZweistelligenWerten_keinFund() {
        Random r = new Random(SEED_GUELTIG + 100);
        int mitFund = 0;
        for (int i = 0; i < TEXTE_JE_FORM; i++) {
            String text = text(r, i, DatumFormen.zeitraumZweistellig(r));
            for (Recognizer erkenner : PRODUKT) {
                mitFund += erkenner.detect(text).isEmpty() ? 0 : 1;
            }
        }
        assertEquals(0, mitFund, "Texte mit Fund");
    }

    /** Bekannte Grenze (A-2): Ein Zeitraum, dessen Praefix genau neun Ziffern erreicht, bleibt teilweise Fund. */
    @Test
    void bekannteGrenze_zeitraumMitNeunZiffernPraefix_inDenZufallslaeufen() {
        Zaehlung z = lauf(SEED_GUELTIG, DatumFormen.ZEITRAUM, true, false);
        assertTrue(z.produktMitFund() > 0, "Zeitraeume mit Neun-Ziffern-Praefix kommen vor");
        assertEquals(ZEITRAUM_NEUN_ZIFFERN, z.produktMitFund(), "Zahl dieser Zeitraeume");
        assertTrue(z.produktMitFund() < z.texte(), "nicht jeder Zeitraum ist betroffen");
    }

    // ------------------------------------------------------------------ d) ungueltige Daten, Nicht-Daten

    /** AK-11 d: Ungueltige Werte je Form liefern dieselbe Fundliste wie die Kopie (Erkenner und Scanner). */
    @Test
    void ak11d_ungueltigeDatenJeForm_fundlisteGleichKopie() {
        for (int form = 0; form < DatumFormen.ANZAHL; form++) {
            Zaehlung z = lauf(SEED_UNGUELTIG, form, false, false);
            String name = DatumFormen.NAMEN[form];
            assertEquals(TEXTE_JE_FORM, z.texte(), name + ": Zahl der Texte");
            assertEquals(0, z.ungleichKopie(), name + ": Erkennerlisten ungleich der Kopie");
            assertEquals(0, z.kopieFundUeberDatum(), name + ": die Kopie hat einen Fund ueber einem Datum");
            assertEquals(KOPIE_MIT_FUND_UNGUELTIG[form], z.kopieMitFund(), name + ": Texte mit Fund");
            assertEquals(z.kopieMitFund(), z.produktMitFund(), name + ": Texte mit Fund im Produkt");
            assertEquals(0, z.ungleichStichprobe(), name + ": Stichprobe ueber standard() gegen ALT_DATUM");
        }
    }

    /** AK-11 d: Nicht-Datumsformen (Trenner, Reihenfolge, Stellenzahl) liefern dieselbe Fundliste wie die Kopie. */
    @Test
    void ak11d_nichtDatumsformen_fundlisteGleichKopie() {
        Zaehlung z = lauf(SEED_KEIN_DATUM, 0, false, true);
        assertEquals(TEXTE_JE_FORM, z.texte(), "Zahl der Texte");
        assertEquals(0, z.ungleichKopie(), "Erkennerlisten ungleich der Kopie");
        assertEquals(0, z.kopieFundUeberDatum(), "die Kopie hat einen Fund ueber einem Datum");
        assertEquals(KOPIE_MIT_FUND_KEIN_DATUM, z.kopieMitFund(), "Texte mit Fund");
        assertEquals(z.kopieMitFund(), z.produktMitFund(), "Texte mit Fund im Produkt");
        assertEquals(0, z.ungleichStichprobe(), "Stichprobe ueber standard() gegen ALT_DATUM");
    }

    // ------------------------------------------------------------------ Erzeuger

    /** Die Erzeuger sind deterministisch, und gueltige und ungueltige Werte sind nach der Referenz unterschieden. */
    @Test
    void erzeuger_deterministischUndTrennscharf() {
        Random a = new Random(SEED_GUELTIG);
        Random b = new Random(SEED_GUELTIG);
        Random c = new Random(SEED_UNGUELTIG);
        for (int form = 0; form < DatumFormen.ANZAHL; form++) {
            for (int i = 0; i < 2_000; i++) {
                String g = DatumFormen.gueltig(a, form);
                assertTrue(g.equals(DatumFormen.gueltig(b, form)), "gleicher Seed, gleicher Wert");
                String u = DatumFormen.ungueltig(c, form);
                boolean zeitraum = form == DatumFormen.ZEITRAUM;
                if (!zeitraum) {
                    assertTrue(DatumReferenz.istDatum(g), DatumFormen.NAMEN[form] + ": gueltiger Wert ist ein Datum");
                    assertFalse(DatumReferenz.istDatum(u), DatumFormen.NAMEN[form] + ": ungueltiger Wert ist keins");
                } else {
                    assertTrue(DatumReferenz.istZeitraumAb(g, 0, g.indexOf('-') + 2), "gueltiger Zeitraum");
                    assertFalse(DatumReferenz.istDatumssegment(u, 0, u.indexOf('-') + 2), "ungueltiger Zeitraum");
                }
            }
        }
    }
}
