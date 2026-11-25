package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Oeffentliche Huellen um die vierte eingefrorene Kopie {@link KvNumberScannerStandDatum} (Stand vor dem Feature
 * datum-und-kontextvorrang, alle vier Arten) und der Scanner {@link #ALT_DATUM}. Die Kopie ist bis auf Kopfkommentar
 * und Namen von Klasse und Konstruktor wortgleich zu {@code KvNumberScanner} (Stand nach oberflaeche-mvp). Nur
 * Testcode; wird nie geaendert.
 *
 * <p>Die Huellen sind keine Produktklassen. Sie bilden {@code LanrRecognizer}, {@code IkRecognizer},
 * {@code KvnrRecognizer} (Kopie plus unveraenderter {@code KvnrOhneKontext}) und {@code BsnrRecognizer} nach, mit
 * denselben Werten fuer {@code nachrangig()} und {@code restQuelle()}. Aenderungen am Produkt wirken deshalb nie auf
 * {@link #ALT_DATUM}: er bleibt der Stand vor dem Feature.
 *
 * <p>{@link #simuliert(BiPredicate)} dient nur dem Messen (Prognose und Selbsttests des Differenztests): derselbe
 * Scanner, in dem die Huellen die Funde, die das Praedikat nennt, vor der weiteren Verarbeitung weglassen. Das
 * bildet nach, was das Feature in {@code KvNumberScanner.ausgeben} tun wird (nur die Ausgabe eines Segments
 * entfaellt, der Zustand des Scanners bleibt wie heute).
 */
public final class KvStandDatum {

    /**
     * Der Scanner mit dem Stand vor dem Feature: E-Mail, IPv4, URL, Pfad und die vier Huellen, in der Reihenfolge
     * von {@code TextScanner.standard()}.
     */
    public static final TextScanner ALT_DATUM = scanner(null);

    private KvStandDatum() {
    }

    /** LANR-Erkenner des eingefrorenen Standes. */
    public static Recognizer lanr() {
        return new Huelle(KvArt.LANR, null);
    }

    /** IK-Erkenner des eingefrorenen Standes. */
    public static Recognizer ik() {
        return new Huelle(KvArt.IK, null);
    }

    /** KVNR-Erkenner des eingefrorenen Standes (Kopie plus unveraenderter {@code KvnrOhneKontext}). */
    public static Recognizer kvnr() {
        return new Huelle(KvArt.KVNR, null);
    }

    /** BSNR-Erkenner des eingefrorenen Standes. */
    public static Recognizer bsnr() {
        return new Huelle(KvArt.BSNR, null);
    }

    /** Die vier Huellen in der Reihenfolge von {@code TextScanner.standard()}: LANR, IK, KVNR, BSNR. */
    public static List<Recognizer> alle() {
        return List.of(lanr(), ik(), kvnr(), bsnr());
    }

    /**
     * Wie {@link #ALT_DATUM}, aber jede Huelle laesst die rohen Funde der Kopie weg, fuer die das Praedikat
     * (Text, Fund) {@code true} liefert; bei der KVNR geschieht das vor {@code KvnrOhneKontext}. Nur zum Messen.
     *
     * @param entfaellt Praedikat, nicht {@code null}
     */
    public static TextScanner simuliert(BiPredicate<String, Finding> entfaellt) {
        return scanner(Objects.requireNonNull(entfaellt, "entfaellt"));
    }

    private static TextScanner scanner(BiPredicate<String, Finding> entfaellt) {
        List<Recognizer> liste = new ArrayList<>();
        liste.add(new EmailRecognizer());
        liste.add(new Ipv4Recognizer());
        liste.add(new UrlRecognizer());
        liste.add(new PathRecognizer());
        liste.add(new Huelle(KvArt.LANR, entfaellt));
        liste.add(new Huelle(KvArt.IK, entfaellt));
        liste.add(new Huelle(KvArt.KVNR, entfaellt));
        liste.add(new Huelle(KvArt.BSNR, entfaellt));
        return new TextScanner(liste);
    }

    private record Huelle(KvArt art, BiPredicate<String, Finding> entfaellt) implements Recognizer {
        @Override
        public List<Finding> detect(String text) {
            List<Finding> roh = KvNumberScannerStandDatum.detect(art, text);
            if (entfaellt != null) {
                List<Finding> behalten = new ArrayList<>(roh.size());
                for (Finding f : roh) {
                    if (!entfaellt.test(text, f)) {
                        behalten.add(f);
                    }
                }
                roh = behalten;
            }
            return art == KvArt.KVNR ? KvnrOhneKontext.ergaenze(text, roh) : roh;
        }

        @Override
        public boolean nachrangig() {
            return art == KvArt.KVNR || art == KvArt.BSNR;
        }

        @Override
        public String restQuelle() {
            if (art == KvArt.KVNR) {
                return KvnrRecognizer.SOURCE_REST;
            }
            if (art == KvArt.BSNR) {
                return BsnrRecognizer.SOURCE_REST;
            }
            return Recognizer.super.restQuelle();
        }
    }
}
