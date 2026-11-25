package de.skillshield.core;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Messwerkzeug fuer Teil A des Features datum-und-kontextvorrang: vergleicht das Produkt mit der eingefrorenen Kopie
 * ({@link KvStandDatum}) und mit der Simulation des kuenftigen Verhaltens (Funde, deren Bereich nach
 * {@link DatumReferenz} ein Datumssegment ist, fallen weg), je Textmenge aus {@link DatumMengen}. Gemeinsam benutzt
 * von {@code DatumKontextvorrangDifferenzTest} und {@code DatumKontextvorrangPrognoseTest}. Meldungen nennen nur
 * Zahlen, nie Text oder Ziffern.
 */
final class DatumMessung {

    /** Namen der vier Arten in der Reihenfolge der Felder: LANR, IK, KVNR, BSNR. */
    static final List<String> ARTEN = List.of("LANR", "IK", "KVNR", "BSNR");

    private static final List<FindingType> KV_TYPEN = List.of(FindingType.LANR, FindingType.IK, FindingType.KVNR,
            FindingType.BSNR);

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner SIMULIERT = KvStandDatum.simuliert(
            (text, f) -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));

    private DatumMessung() {
    }

    /**
     * Ergebnis des Vergleichs zweier Fundlisten desselben Textes (alt gegen neu).
     *
     * @param datumEntfallen alte Funde der vier KV-Typen ohne gleichen neuen Fund, deren Bereich ein Datumssegment ist
     * @param sonstigeVerluste alte Funde ohne gleichen neuen Fund, die kein entfallenes Datumssegment sind
     * @param neueFunde neue Funde ohne gleichen alten Fund (Folgeaenderungen, Klasse Z)
     * @param abdeckung Positionen, deren Schutz anders ist als "alt ohne die entfallenen Datumsfunde"
     */
    record Vergleich(int datumEntfallen, int sonstigeVerluste, int neueFunde, int abdeckung) {

        static final Vergleich KEIN = new Vergleich(0, 0, 0, 0);

        Vergleich plus(Vergleich o) {
            return new Vergleich(datumEntfallen + o.datumEntfallen, sonstigeVerluste + o.sonstigeVerluste,
                    neueFunde + o.neueFunde, abdeckung + o.abdeckung);
        }

        /** Ob die Abweichungen zulaessig sind: nur entfallene Datumsfunde, und nur bei {@code teilA}. */
        boolean zulaessig(boolean teilA) {
            return sonstigeVerluste == 0 && neueFunde == 0 && abdeckung == 0 && (teilA || datumEntfallen == 0);
        }

        /** Kurzform fuer Meldungen. */
        String kurz() {
            return "A=" + datumEntfallen + ", Verluste=" + sonstigeVerluste + ", neu=" + neueFunde + ", Abdeckung="
                    + abdeckung;
        }
    }

    /** Ob der Fund einer der vier KV-Typen ist. */
    static boolean istKv(Finding f) {
        return KV_TYPEN.contains(f.type());
    }

    private static boolean[] abdeckung(int laenge, List<Finding> funde) {
        boolean[] a = new boolean[laenge];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    /** Vergleicht eine alte und eine neue Fundliste desselben Textes. */
    static Vergleich vergleiche(String text, List<Finding> alt, List<Finding> neu) {
        Set<Finding> neuMenge = new HashSet<>(neu);
        Set<Finding> altMenge = new HashSet<>(alt);
        int datum = 0;
        int verluste = 0;
        List<Finding> behalten = new ArrayList<>(alt.size());
        for (Finding f : alt) {
            if (neuMenge.contains(f)) {
                behalten.add(f);
            } else if (istKv(f) && DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                datum++;
            } else {
                verluste++;
                behalten.add(f);
            }
        }
        int neue = 0;
        for (Finding f : neu) {
            neue += altMenge.contains(f) ? 0 : 1;
        }
        boolean[] erwartet = abdeckung(text.length(), behalten);
        boolean[] ist = abdeckung(text.length(), neu);
        int unterschied = 0;
        for (int i = 0; i < erwartet.length; i++) {
            unterschied += erwartet[i] == ist[i] ? 0 : 1;
        }
        return new Vergleich(datum, verluste, neue, unterschied);
    }

    /** Alle Messwerte einer Textmenge. */
    static final class Messung {
        /** Zahl der Texte der Menge. */
        int texte;
        /** Funde der Kopie auf Erkennerebene je Art (LANR, IK, KVNR, BSNR). */
        final int[] kopieFunde = new int[4];
        /** Davon Funde, deren Bereich nach der Referenz ein Datumssegment ist (kuenftig entfallend). */
        final int[] datumFunde = new int[4];
        /** Davon Funde ueber einen Zeitraum (Segment ist kein einzelnes Datum). */
        final int[] zeitraumFunde = new int[4];
        /** Texte, auf denen das Produkt von der Kopie abweicht, je Art. */
        final int[] ungleichKopie = new int[4];
        /** Texte, auf denen das Produkt von "Kopie ohne Datumssegmente" abweicht, je Art. */
        final int[] ungleichKopieOhneDatum = new int[4];
        /** Vergleich {@code ALT_DATUM} gegen {@code standard()} (Produkt). */
        Vergleich produkt = Vergleich.KEIN;
        /** Vergleich {@code ALT_DATUM} gegen die Simulation (kuenftiges Verhalten von Teil A). */
        Vergleich simulation = Vergleich.KEIN;
        /** Texte, auf denen die Simulation von {@code ALT_DATUM} abweicht. */
        int texteMitAenderung;
        /** Texte, deren Produktliste weder sortiert noch ueberlappungsfrei noch deterministisch ist. */
        int unsortiert;
    }

    private static List<Messung> messungen;

    /** Die Messungen aller Mengen in der Reihenfolge von {@link DatumMengen#NAMEN}; einmal berechnet. */
    static synchronized List<Messung> messungen() {
        if (messungen == null) {
            List<List<String>> mengen = DatumMengen.mengen();
            List<Messung> m = new ArrayList<>(mengen.size());
            for (List<String> texte : mengen) {
                m.add(messe(texte));
            }
            messungen = List.copyOf(m);
        }
        return messungen;
    }

    private static boolean sortiertUndUeberlappungsfrei(String text, List<Finding> funde) {
        int voriges = 0;
        for (Finding f : funde) {
            boolean passt = f.start() >= voriges && f.start() <= f.end() && f.end() <= text.length()
                    && text.substring(f.start(), f.end()).equals(f.originalValue());
            if (!passt) {
                return false;
            }
            voriges = f.end();
        }
        return true;
    }

    private static List<Finding> ohneDatum(String text, List<Finding> funde) {
        List<Finding> r = new ArrayList<>(funde.size());
        for (Finding f : funde) {
            if (!DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                r.add(f);
            }
        }
        return r;
    }

    private static Messung messe(List<String> texte) {
        List<Recognizer> kopien = KvStandDatum.alle();
        List<Recognizer> produkt = List.of(new LanrRecognizer(), new IkRecognizer(), new KvnrRecognizer(),
                new BsnrRecognizer());
        Messung m = new Messung();
        m.texte = texte.size();
        for (String text : texte) {
            for (int a = 0; a < 4; a++) {
                List<Finding> kopie = kopien.get(a).detect(text);
                List<Finding> prod = produkt.get(a).detect(text);
                m.kopieFunde[a] += kopie.size();
                for (Finding f : kopie) {
                    if (DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                        m.datumFunde[a]++;
                        boolean einzel = DatumReferenz.istDatum(text.substring(f.start(), f.end()));
                        m.zeitraumFunde[a] += einzel ? 0 : 1;
                    }
                }
                m.ungleichKopie[a] += prod.equals(kopie) ? 0 : 1;
                m.ungleichKopieOhneDatum[a] += prod.equals(ohneDatum(text, kopie)) ? 0 : 1;
            }
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            List<Finding> sim = SIMULIERT.scan(text);
            m.produkt = m.produkt.plus(vergleiche(text, alt, neu));
            Vergleich v = vergleiche(text, alt, sim);
            m.simulation = m.simulation.plus(v);
            m.texteMitAenderung += alt.equals(sim) ? 0 : 1;
            m.unsortiert += sortiertUndUeberlappungsfrei(text, neu) && neu.equals(NEU.scan(text)) ? 0 : 1;
        }
        return m;
    }
}
