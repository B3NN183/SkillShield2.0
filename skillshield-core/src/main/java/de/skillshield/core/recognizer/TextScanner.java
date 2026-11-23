package de.skillshield.core.recognizer;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Scan: ruft alle Erkenner auf und macht die Kandidaten mit dem {@link ConflictResolver} überlappungsfrei.
 *
 * <p>Das Ergebnis ist unveränderlich, überlappungsfrei und aufsteigend nach {@code start} sortiert; es ist
 * unabhängig von der Reihenfolge der Erkenner. Der Scan verändert den Text nicht (er ist ein {@code String}) und
 * ersetzt nichts. Funde jeder Konfidenzstufe bleiben erhalten.
 *
 * <p>Grundsatz "Bestand hat Vorrang" (Plan kvnr-bsnr-erkennung, Nachtrag 2 und 3 zu T-6): Funde der Typen E-Mail,
 * IP, URL und Pfad bleiben durch die neueren Arten in allen Feldern unveraendert. Vor der Konfliktaufloesung
 * schneidet der Scanner deshalb jeden Kandidaten eines nachrangigen Erkenners ({@link Recognizer#nachrangig()},
 * derzeit nur der {@code KvnrRecognizer}), der sich mit den Bestandsbereichen in mindestens einer Position
 * ueberschneidet, auf die Teile ausserhalb zu (Zuschneiden statt Verwerfen). Bestandsbereiche sind die Bereiche der
 * Funde dieser vier Typen im Scan ohne die nachrangigen Erkenner, also einschliesslich der dort schon mit LANR- oder
 * IK-Kandidaten zusammengelegten Funde (sonst koennte ein Restfund ueber einen LANR-/IK-Kandidaten einen
 * zusammengelegten Bestandsfund verlaengern oder abschwaechen). Jedes Reststueck wird
 * an beiden Enden auf ASCII-Buchstaben und -Ziffern gekuerzt und bleibt als Fund mit der Quelle
 * {@link Recognizer#restQuelle()} (KVNR: {@code kvnr-rest}) und hoechstens 0,80 Konfidenz, wenn es mindestens drei
 * Ziffern enthaelt; liegt der Kandidat ganz im Bestand, entfaellt er. Restfunde grenzen an Bestandsfunde nur an.
 * Kandidaten der uebrigen Erkenner (auch LANR und IK) werden nie veraendert; der {@link ConflictResolver} bleibt
 * unveraendert. Aufwand: zwei Aufloesungen und je Kandidat eine binaere Suche, also O(n log n) in der Zahl der
 * Kandidaten.
 *
 * <p>Bekannte Grenze: Der Teil einer Nummer, der im Bestandsfund liegt, traegt dessen Typ; ein einzelner
 * Buchstabe oder ein Rest mit weniger als drei Ziffern ausserhalb bleibt stehen.
 */
public final class TextScanner {

    private final List<Recognizer> recognizers;

    /**
     * @param recognizers die Erkenner; die Liste wird kopiert, {@code null} und {@code null}-Elemente werden
     *                    abgelehnt
     */
    public TextScanner(List<? extends Recognizer> recognizers) {
        Objects.requireNonNull(recognizers, "recognizers");
        for (Recognizer r : recognizers) {
            Objects.requireNonNull(r, "recognizers enthält null");
        }
        this.recognizers = List.copyOf(recognizers);
    }

    /**
     * Scanner mit den Standard-Erkennern in dieser Reihenfolge: E-Mail, IPv4, URL, Pfad, LANR, IK, KVNR. Die Liste
     * ist ueber {@link #recognizers()} lesbar. Die KVNR steht am Ende; bei Konflikten entscheidet allein der
     * {@link ConflictResolver} (nicht die Reihenfolge).
     */
    public static TextScanner standard() {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), new LanrRecognizer(), new IkRecognizer(),
                new KvnrRecognizer()));
    }

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
