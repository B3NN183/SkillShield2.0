package de.skillshield.core;

import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Die Textmengen, auf denen die Messungen des Features datum-und-kontextvorrang laufen: alle Mengen, die die
 * bestehenden Differenz- und Bestandstests benutzen (Fuzz-Teile 1 bis 1h, die Korpora und die Generatoren der
 * Eigenschaftstests). Die Mengen werden von den vorhandenen Generatoren mit deren festen Seeds erzeugt; hier wird
 * nichts veraendert. Je Menge wird einmal erzeugt und dann von allen Tests der Testlaufzeit gelesen.
 */
public final class DatumMengen {

    /** Namen der Mengen in der Reihenfolge von {@link #mengen()}. */
    public static final List<String> NAMEN = List.of("Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e",
            "Teil 1f", "Teil 1g", "Teil 1h", "Korpus", "Korpus Teil 2", "Generatoren", "KVNR-Korpus",
            "KVNR-ohne-Kontext-Korpus", "BSNR-Korpus");

    private static List<List<String>> mengen;

    private DatumMengen() {
    }

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

    /** Die Textmengen in der Reihenfolge von {@link #NAMEN}; unveraenderlich, einmal berechnet. */
    public static synchronized List<List<String>> mengen() {
        if (mengen == null) {
            List<List<String>> m = new ArrayList<>();
            m.add(texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE), RoundtripFuzzTest.Fall::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKv(RoundtripFuzzTest.SEED_TEXTE_KV), RoundtripFuzzTest.Fall::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvC(RoundtripFuzzTest.SEED_TEXTE_KV_C),
                    RoundtripFuzzTest.FallC::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvD(RoundtripFuzzTest.SEED_TEXTE_KV_D),
                    RoundtripFuzzTest.FallD::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvE(RoundtripFuzzTest.SEED_TEXTE_KV_E),
                    RoundtripFuzzTest.FallE::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvF(RoundtripFuzzTest.SEED_TEXTE_KV_F),
                    RoundtripFuzzTest.FallF::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvG(RoundtripFuzzTest.SEED_TEXTE_KV_G),
                    RoundtripFuzzTest.FallG::text));
            m.add(texte(RoundtripFuzzTest.erzeugeKvH(RoundtripFuzzTest.SEED_TEXTE_KV_H),
                    RoundtripFuzzTest.FallH::text));
            m.add(KvDifferenzTest.korpus());
            m.add(texte(RoundtripKorpusTest.kvTrennerFaelle(), RoundtripKorpusTest.KvFall::text));
            m.add(KvKontextLueckenEigenschaftTest.differenzTexte());
            m.add(texte(RoundtripKorpusTest.kvnrFaelle(), RoundtripKorpusTest.KvFall::text));
            m.add(texte(RoundtripKorpusTest.kvnrOhneKontextFaelle(), RoundtripKorpusTest.KvFall::text));
            m.add(texte(RoundtripKorpusTest.bsnrFaelle(), RoundtripKorpusTest.KvFall::text));
            mengen = List.copyOf(m);
        }
        return mengen;
    }
}
