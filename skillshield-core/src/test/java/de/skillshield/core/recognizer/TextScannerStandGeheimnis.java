package de.skillshield.core.recognizer;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Eingefrorene Testkopie des {@code TextScanner} vor dem Feature geheimnisse-erkennung (Stand nach dem Feature
 * datum-und-kontextvorrang, Teil A, Tag dk-t3-teil-a; Plan geheimnisse-erkennung, Kapitel 7.2, T-1). Ab Abnahme von T-1
 * ist sie eingefroren (Plan, Kapitel 6.3) und wird nicht mehr geaendert.
 *
 * <p>Der Rumpf ist eine woertliche Kopie des Produktklassen-Rumpfs (Konstruktor, {@code recognizers()}, {@code scan}
 * und alle Hilfsmethoden bis zum Dateiende); veraendert sind nur der Klassenname samt Konstruktorname, dieser
 * Kopf-Kommentar und {@code standard()}: Statt dessen steht {@link #ALT} mit denselben acht Produkt-Erkennern in
 * derselben Reihenfolge (E-Mail, IPv4, URL, Pfad, LANR, IK, KVNR, BSNR). Die acht Erkenner aendern sich in diesem
 * Feature nicht (Nachweis ueber {@code kern-pruefsummen-vorher.txt} und {@code kern-pruefsummen-nachher.txt}); damit
 * ist {@code ALT} genau der Scanner vor dem Feature. Die Kopie dient dem Differenztest
 * ({@code GeheimnisDifferenzTest}) und der Vorabmessung ({@code GeheimnisVorabMessungTest}).
 */
public final class TextScannerStandGeheimnis {

    private final List<Recognizer> recognizers;

    /**
     * @param recognizers die Erkenner; die Liste wird kopiert, {@code null} und {@code null}-Elemente werden
     *                    abgelehnt
     */
    public TextScannerStandGeheimnis(List<? extends Recognizer> recognizers) {
        Objects.requireNonNull(recognizers, "recognizers");
        for (Recognizer r : recognizers) {
            Objects.requireNonNull(r, "recognizers enthält null");
        }
        this.recognizers = List.copyOf(recognizers);
    }

    /**
     * Der Scanner vor dem Feature: dieselben acht Produkt-Erkenner in derselben Reihenfolge wie
     * {@code TextScanner.standard()} im Stand dk-t3-teil-a.
     */
    public static final TextScannerStandGeheimnis ALT = new TextScannerStandGeheimnis(List.of(
            new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
            new PathRecognizer(), new LanrRecognizer(), new IkRecognizer(),
            new KvnrRecognizer(), new BsnrRecognizer()));

    /**
     * Die Erkenner dieses Scanners in der Aufrufreihenfolge. Tests nehmen die Liste von
     * {@code TextScanner.standard().recognizers()}, damit sie nicht von der Standardliste abweichen koennen.
     *
     * @return unveraenderliche Liste
     */
    public List<Recognizer> recognizers() {
        return recognizers;
    }

    /**
     * Durchsucht den Text.
     *
     * @param text der Text, nicht {@code null}
     * @return unveränderliche, überlappungsfreie Fundliste, aufsteigend nach {@code start}
     * @throws NullPointerException  bei {@code null} als Text (Meldung {@code text}) oder wenn ein Erkenner
     *                               {@code null} liefert
     * @throws IllegalStateException wenn ein Erkenner einen Fund liefert, der nicht zum Text passt (die Meldung
     *                               enthält keinen Fundwert), oder wenn ein Erkenner eine
     *                               {@link RuntimeException} wirft (die Meldung nennt nur Index und Klassenname;
     *                               die Ausnahme hat weder Ursache noch unterdrückte Ausnahmen).
     *                               Ein {@link Error} wird bewusst nicht gekapselt (TE-20): er zeigt einen
     *                               Zustand der JVM an, nicht einen Fehler des Erkenners; die Klartextfreiheit
     *                               der Erkenner sichern deren eigene Tests
     */
    public List<Finding> scan(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> candidates = new ArrayList<>();
        Map<Integer, List<Finding>> nachrangig = new TreeMap<>();
        for (int i = 0; i < recognizers.size(); i++) {
            List<Finding> found = Objects.requireNonNull(detect(recognizers.get(i), i, text),
                    "Erkenner lieferte null");
            for (Finding f : found) {
                Objects.requireNonNull(f, "Erkenner lieferte null-Element");
                if (!matchesText(f, text)) {
                    throw new IllegalStateException("Erkenner lieferte einen Fund, der nicht zum Text passt");
                }
                if (recognizers.get(i).nachrangig()) {
                    nachrangig.computeIfAbsent(i, k -> new ArrayList<>()).add(f);
                } else {
                    candidates.add(f);
                }
            }
        }
        if (!nachrangig.isEmpty()) {
            Bestand bestand = Bestand.von(ConflictResolver.resolve(text, candidates));
            for (Map.Entry<Integer, List<Finding>> e : nachrangig.entrySet()) {
                candidates.addAll(bestand.zuschneiden(e.getValue(), recognizers.get(e.getKey()).restQuelle()));
            }
        }
        return ConflictResolver.resolve(text, candidates);
    }

    private static boolean istBestandstyp(FindingType type) {
        return type == FindingType.EMAIL || type == FindingType.IP || type == FindingType.URL
                || type == FindingType.PFAD;
    }

    /** Kleinste Zahl ASCII-Ziffern, die ein zugeschnittenes Reststueck haben muss, damit es ein Fund bleibt. */
    static final int MIN_REST_ZIFFERN = 3;

    /** Hoechste Konfidenz eines Restfundes (die vollstaendige Form ist nicht mehr belegt). */
    static final double MAX_REST_KONFIDENZ = 0.80;

    /**
     * Schneidet die nachrangigen Kandidaten auf die Teile ausserhalb der Bestandsbereiche zu (Plan
     * kvnr-bsnr-erkennung, Nachtrag 3 zu T-6). Bestandsbereiche sind die zu disjunkten Bereichen vereinigten
     * Funde der Typen E-Mail, IP, URL und Pfad aus {@code funde}.
     *
     * <p>Ein Kandidat ohne gemeinsame Position mit dem Bestand (auch ein angrenzender) bleibt unveraendert
     * erhalten. Sonst wird er in die Reststuecke ausserhalb des Bestands zerlegt; jedes Reststueck wird an beiden
     * Enden auf ASCII-Buchstaben und -Ziffern gekuerzt und bleibt als Fund nur, wenn es mindestens
     * {@value #MIN_REST_ZIFFERN} ASCII-Ziffern enthaelt. Der Restfund hat den Typ des Kandidaten, den
     * Textausschnitt als Originalwert, die Quelle {@code restQuelle} und die Konfidenz des Kandidaten, hoechstens
     * {@value #MAX_REST_KONFIDENZ}. Liegt der Kandidat ganz im Bestand, entfaellt er. Aufwand: Sortieren der
     * Bestandsbereiche, je Kandidat eine binaere Suche und ein Durchlauf ueber die geschnittenen Bereiche.
     *
     * @param nachrangig die Kandidaten eines nachrangigen Erkenners
     * @param restQuelle Quelle der Restfunde ({@link Recognizer#restQuelle()})
     * @param funde      die Funde, die der Bestand sein koennen; nur die Bestandstypen zaehlen
     * @return die unveraenderten und die zugeschnittenen Kandidaten
     */
    static List<Finding> zuschneiden(List<Finding> nachrangig, String restQuelle, List<Finding> funde) {
        if (nachrangig.isEmpty()) {
            return nachrangig;
        }
        return Bestand.von(funde).zuschneiden(nachrangig, restQuelle);
    }

    /** Die vereinigten, disjunkten, aufsteigend sortierten Bestandsbereiche. */
    private record Bestand(int[] starts, int[] ends, int zahl) {

        static Bestand von(List<Finding> funde) {
            List<Finding> bestand = new ArrayList<>();
            for (Finding f : funde) {
                if (istBestandstyp(f.type())) {
                    bestand.add(f);
                }
            }
            bestand.sort(Comparator.comparingInt(Finding::start));
            int[] starts = new int[bestand.size()];
            int[] ends = new int[bestand.size()];
            int zahl = 0;
            for (Finding f : bestand) {
                if (zahl > 0 && f.start() < ends[zahl - 1]) {
                    ends[zahl - 1] = Math.max(ends[zahl - 1], f.end());
                } else {
                    starts[zahl] = f.start();
                    ends[zahl] = f.end();
                    zahl++;
                }
            }
            return new Bestand(starts, ends, zahl);
        }

        /** Index des ersten Bereichs, dessen Ende hinter {@code pos} liegt, sonst {@code zahl}. */
        private int ersterBereichHinter(int pos) {
            int lo = 0;
            int hi = zahl;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (ends[mid] > pos) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            return lo;
        }

        List<Finding> zuschneiden(List<Finding> nachrangig, String restQuelle) {
            List<Finding> ergebnis = new ArrayList<>(nachrangig.size());
            for (Finding f : nachrangig) {
                int i = ersterBereichHinter(f.start());
                if (i >= zahl || starts[i] >= f.end()) {
                    ergebnis.add(f);
                    continue;
                }
                int cursor = f.start();
                for (; i < zahl && starts[i] < f.end(); i++) {
                    restfund(f, cursor, starts[i], restQuelle, ergebnis);
                    cursor = Math.max(cursor, ends[i]);
                }
                restfund(f, cursor, f.end(), restQuelle, ergebnis);
            }
            return ergebnis;
        }
    }

    /** Fuegt das Reststueck [von, bis) des Kandidaten als Fund hinzu, wenn es nach dem Kuerzen genug Ziffern hat. */
    private static void restfund(Finding f, int von, int bis, String restQuelle, List<Finding> ziel) {
        String wert = f.originalValue();
        int s = von - f.start();
        int e = bis - f.start();
        while (s < e && !istAsciiBuchstabeOderZiffer(wert.charAt(s))) {
            s++;
        }
        while (e > s && !istAsciiBuchstabeOderZiffer(wert.charAt(e - 1))) {
            e--;
        }
        int ziffern = 0;
        for (int k = s; k < e; k++) {
            if (wert.charAt(k) >= '0' && wert.charAt(k) <= '9') {
                ziffern++;
            }
        }
        if (ziffern >= MIN_REST_ZIFFERN) {
            ziel.add(new Finding(f.type(), wert.substring(s, e), f.start() + s, f.start() + e,
                    Math.min(f.confidence(), MAX_REST_KONFIDENZ), restQuelle));
        }
    }

    private static boolean istAsciiBuchstabeOderZiffer(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** Ruft den Erkenner auf und kapselt dessen Ausnahmen klartextfrei (keine Meldung, keine Ursache). */
    private static List<Finding> detect(Recognizer recognizer, int index, String text) {
        try {
            return recognizer.detect(text);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Erkenner an Index " + index + " fehlgeschlagen: "
                    + e.getClass().getName());
        }
    }

    private static boolean matchesText(Finding f, String text) {
        // regionMatches liefert false, wenn der Bereich über das Textende hinausreicht
        return text.regionMatches(f.start(), f.originalValue(), 0, f.originalValue().length());
    }
}
