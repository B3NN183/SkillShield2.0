package de.skillshield.core.recognizer;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
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
 * Scan: ruft alle Erkenner auf und macht die Kandidaten mit dem {@link ConflictResolver} überlappungsfrei.
 *
 * <p>Das Ergebnis ist unveränderlich, überlappungsfrei und aufsteigend nach {@code start} sortiert; es ist
 * unabhängig von der Reihenfolge der Erkenner. Der Scan verändert den Text nicht (er ist ein {@code String}) und
 * ersetzt nichts. Funde jeder Konfidenzstufe bleiben erhalten.
 *
 * <p>Grundsatz "Bestand hat Vorrang" (Plan kvnr-bsnr-erkennung, Nachtrag 2 und 3 zu T-6): Funde der Typen E-Mail,
 * IP, URL und Pfad bleiben durch die neueren Arten in allen Feldern unveraendert. Vor der Konfliktaufloesung
 * schneidet der Scanner deshalb jeden Kandidaten eines nachrangigen Erkenners ({@link Recognizer#nachrangig()},
 * derzeit der {@code KvnrRecognizer} und der {@code BsnrRecognizer}), der sich mit den Bestandsbereichen in
 * mindestens einer Position
 * ueberschneidet, auf die Teile ausserhalb zu (Zuschneiden statt Verwerfen). Bestandsbereiche sind die Bereiche der
 * Funde dieser vier Typen im Scan ohne die nachrangigen Erkenner, also einschliesslich der dort schon mit LANR- oder
 * IK-Kandidaten zusammengelegten Funde (sonst koennte ein Restfund ueber einen LANR-/IK-Kandidaten einen
 * zusammengelegten Bestandsfund verlaengern oder abschwaechen). Jedes Reststueck wird
 * an beiden Enden auf ASCII-Buchstaben und -Ziffern gekuerzt und bleibt als Fund mit der Quelle
 * {@link Recognizer#restQuelle()} (KVNR: {@code kvnr-rest}, BSNR: {@code bsnr-rest}) und hoechstens 0,80
 * Konfidenz, wenn es mindestens drei
 * Ziffern enthaelt; liegt der Kandidat ganz im Bestand, entfaellt er. Restfunde grenzen an Bestandsfunde nur an.
 * Kandidaten der uebrigen Erkenner (auch LANR und IK) werden nie veraendert; der {@link ConflictResolver} bleibt
 * unveraendert. Aufwand: zwei Aufloesungen und je Kandidat eine binaere Suche, also O(n log n) in der Zahl der
 * Kandidaten.
 *
 * <p>Lueckenregel fuer Geheimnisse (Plan geheimnisse-erkennung, Kapitel 1.1; D-13, OF-7, AK-31): Kandidaten der
 * Typen PASSWORT und TOKEN ({@code GeheimnisTypen#istGeheimnistyp}) werden beim Einsammeln beiseite gelegt, gleich
 * von welchem Erkenner sie kommen; die Zugehoerigkeit entscheidet allein der Typ, nicht die Stelle in der Liste.
 * Ueber alle uebrigen Kandidaten laeuft der bisherige Ablauf unveraendert (Zuschneiden nachrangiger Erkenner,
 * Aufloesung); sein Ergebnis heisst {@code alt}. Gibt es keinen Geheimnis-Kandidaten, ist {@code alt} das Ergebnis:
 * der Ablauf ist dann Schritt fuer Schritt der alte (dieselben Aufrufe in derselben Reihenfolge, dieselbe Liste).
 * Sonst wird jeder Geheimnis-Kandidat auf die Teile ausserhalb aller Funde von {@code alt} zugeschnitten (alle Typen,
 * auch KV-Funde und zusammengelegte Funde); liegt er ganz in {@code alt}, entfaellt er. Jedes Reststueck bleibt als
 * Fund mit Typ, Konfidenz und Quelle des Kandidaten und dem Textausschnitt als Originalwert; es wird nicht gekuerzt
 * (auch kein Leerraum), damit jede Kandidatenposition abgedeckt bleibt. Zuletzt loest
 * {@link ConflictResolver#resolve} die Funde von {@code alt} und die Reststuecke gemeinsam auf.
 *
 * <p>Eigenschaften der Lueckenregel und ihr Beweis. Sei {@code A} die Menge der Positionen aller Funde von
 * {@code alt}, {@code G} die Vereinigung aller Geheimnis-Kandidaten und {@code R} die Menge der Reststuecke.
 * <ul>
 * <li>(a) {@code alt} liegt feldgleich im Ergebnis. Jedes Reststueck liegt ausserhalb von {@code A} (es entsteht
 * nur aus Positionen, die kein Bereich von {@code alt} belegt), kein Reststueck teilt also eine Position mit einem
 * Fund von {@code alt}; die Funde von {@code alt} sind untereinander ueberlappungsfrei. Die Gruppen des
 * {@link ConflictResolver} entstehen nur aus ueberlappenden Kandidaten (angrenzende bilden keine Gruppe); jeder
 * Fund von {@code alt} ist daher allein in seiner Gruppe und wird unveraendert uebernommen (Gruppe mit einem
 * Mitglied: Typ, Bereich, Konfidenz und Quelle bleiben).</li>
 * <li>(b) Jeder weitere Fund hat den Typ PASSWORT oder TOKEN. Weitere Funde entstehen nur aus Gruppen, die aus
 * Reststuecken bestehen (nach (a) enthaelt keine Gruppe Funde von {@code alt}). Ein Reststueck traegt den Typ seines
 * Kandidaten; legt der Aufloeser Reststuecke zusammen, ist der Typ der des staerksten Mitglieds, also wieder ein
 * Geheimnistyp.</li>
 * <li>(c) Die Abdeckung des Ergebnisses ist die Vereinigung aus {@code A} und {@code G}. Die Reststuecke eines
 * Kandidaten sind genau seine Positionen ausserhalb von {@code A} und werden nicht gekuerzt, also ist die Abdeckung
 * von {@code R} gleich {@code G} ohne {@code A}. Der Aufloeser nimmt keine abgedeckte Position aus dem Ergebnis
 * (Schutzgarantie). Damit bleibt keine Position eines Kandidaten ohne Abdeckung: jede liegt in einem Fund von
 * {@code alt} oder in einem Reststueck.</li>
 * <li>(d) Das Ergebnis ist unabhaengig von der Reihenfolge der Erkenner. {@code alt} ist es (Klassenbeschreibung),
 * die Menge der Reststuecke haengt nur von der Menge der Kandidaten und von {@code alt} ab, und der Aufloeser
 * sortiert seine Eingabe. Die Einordnung folgt allein dem Typ.</li>
 * </ul>
 * Aufwand: {@code alt} wie bisher; dazu je Geheimnis-Kandidat eine binaere Suche ueber die sortierten, disjunkten
 * Bereiche von {@code alt} und ein einmaliger Durchlauf ueber genau die Bereiche, die der Kandidat schneidet, ohne
 * Rueckwaertslesen und ohne Rekursion; dann eine weitere Aufloesung. Die Kosten sind O((k + m + s) log (k + m + s))
 * in der Zahl k der Funde von {@code alt}, der Kandidaten m und der Schnittpaare s zwischen Kandidaten und Bereichen
 * von {@code alt}; s ist zugleich die Zahl der erzeugten Reststuecke. Die Erkenner liefern je Art
 * ueberlappungsfreie Kandidaten; s ist dann hoechstens ein kleines Vielfaches von k (kein quadratisches Verhalten
 * bei vielen Bestandsfunden in einem langen Kandidaten und bei vielen Kandidaten). Liefern Erkenner absichtlich
 * viele einander ueberlappende Kandidaten, waechst s mit der Ausgabe (jedes Reststueck ist ein Fund der Eingabe der
 * zweiten Aufloesung). Es gibt keinen regulaeren Ausdruck.
 *
 * <p>Bekannte Grenze G-K14 (Test {@code bekannteGrenze...} in {@code KvnrGrenzenTest} und {@code TextScannerKvnrTest}):
 * Der Teil einer Nummer, der im Bestandsfund liegt, traegt dessen Typ; ein einzelner Buchstabe oder ein Rest mit
 * weniger als drei Ziffern ausserhalb bleibt stehen. Das gilt auch fuer den hinteren Teil einer gruppierten Nummer, den
 * der lokale Teil einer E-Mail-Adresse verschluckt: Der Anfang bleibt stehen, wenn er weniger als drei Ziffern hat.
 * Liegt die Nummer ganz im Bestandsfund (zum Beispiel in einem Dateinamen), schuetzt dieser sie mit seinem Typ.
 *
 * <p>Bekannte Grenze G-K16: Der Scan liefert nur Fundstellen, und {@code Pseudonymizer} ersetzt nur gefundene
 * Stellen. Steht derselbe Wert an einer zweiten, nicht erkannten Stelle, bleibt er dort stehen; ein Abgleich aller
 * Vorkommen (Leak-Check) ist nicht Teil dieses Features. Die weiteren Grenzen der KVNR (G-K1 bis G-K17, G-D2) stehen
 * im Javadoc von {@code KvNumberScanner} und {@code KvnrRecognizer}.
 *
 * <p>Quellen der KVNR-Funde im Scan: {@code kvnr}, {@code kvnr-pruefziffer-ungueltig}, {@code kvnr-form-abweichend}
 * (Konfidenzen 0,95 bis 0,40 nach den Regeln im Javadoc von {@code KvnrRecognizer}) und {@code kvnr-rest} (Reststueck
 * neben einem Bestandsfund, hoechstens 0,80).
 *
 * <p>Quellen der BSNR-Funde im Scan: {@code bsnr}, {@code bsnr-regionalcode-unbekannt},
 * {@code bsnr-laenge-abweichend} (Konfidenzen 0,90 bis 0,40 nach den Regeln im Javadoc von
 * {@code BsnrRecognizer}) und {@code bsnr-rest}. Mehrere Kontextwoerter und Zahlen, die zu mehreren Arten passen
 * (Formularfeld {@code BSNR/LANR <Nummer>/<Nummer>}, dieselbe Zahl unter LANR und BSNR): jede Art erzeugt ihre
 * Kandidaten getrennt; ueber einer Position bleibt genau ein Fund, der Typ folgt der Konfliktregel (hoehere
 * Konfidenz, bei Gleichstand der Typname). Ein Kontextwort einer anderen Art aendert keinen Fund der Typen E-Mail,
 * IP, URL und Pfad.
 *
 * <p>Bekannte Grenzen der BSNR im Scan (Teil 2; Einzelheiten im Javadoc von {@code KvNumberScanner}, je Grenze ein
 * Test {@code bekannteGrenze...} in {@code BsnrGrenzenTest}): G-K14 gilt auch fuer {@code bsnr-rest} (ein Rest von
 * einer oder zwei Ziffern neben einem Bestandsfund bleibt stehen); bei {@code <Kontextwort> <IPv4>/<gruppierte
 * Nummer>} (bestand schon bei der LANR) liefert der Scan, wenn IP-Ziffern und Nummer zusammen eine Kette von mehr als
 * zwoelf Ziffern bilden, den IP-Fund und einen Restfund ueber die erste Gruppe, die letzte Gruppe bleibt im
 * Klartext (eine geschlossene Nummer ist abgedeckt); bei Gleichstand der
 * Konfidenz bei gleichem Bereich gewinnt BSNR vor IK, KVNR und LANR, KVNR vor LANR, IP vor allen KV-Arten (G-D1: der
 * Typ kann "falsch" sein, die Position ist geschuetzt). Ein zusammengelegter Fund kann durch einen teilweise
 * ueberlappenden BSNR-Kandidaten von "sicher" auf "wahrscheinlich" fallen (K-4); ein spaeterer Filter nach Stufe
 * muss das beruecksichtigen.
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
     * Scanner mit den Standard-Erkennern in dieser Reihenfolge: E-Mail, IPv4, URL, Pfad, LANR, IK, KVNR, BSNR. Die
     * Liste ist ueber {@link #recognizers()} lesbar. KVNR und BSNR stehen am Ende; bei Konflikten entscheidet allein
     * der {@link ConflictResolver} (nicht die Reihenfolge). Typen mit gleichem Bereich: hoehere Konfidenz gewinnt,
     * bei Gleichstand der Typname (BSNR vor IK vor KVNR vor LANR); Plan kvnr-bsnr-erkennung, Kapitel 2.
     */
    public static TextScanner standard() {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), new LanrRecognizer(), new IkRecognizer(),
                new KvnrRecognizer(), new BsnrRecognizer()));
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
        List<Finding> geheimnisse = new ArrayList<>();
        Map<Integer, List<Finding>> nachrangig = new TreeMap<>();
        for (int i = 0; i < recognizers.size(); i++) {
            List<Finding> found = Objects.requireNonNull(detect(recognizers.get(i), i, text),
                    "Erkenner lieferte null");
            for (Finding f : found) {
                Objects.requireNonNull(f, "Erkenner lieferte null-Element");
                if (!matchesText(f, text)) {
                    throw new IllegalStateException("Erkenner lieferte einen Fund, der nicht zum Text passt");
                }
                if (GeheimnisTypen.istGeheimnistyp(f.type())) {
                    geheimnisse.add(f);
                } else if (recognizers.get(i).nachrangig()) {
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
        List<Finding> alt = ConflictResolver.resolve(text, candidates);
        if (geheimnisse.isEmpty()) {
            return alt;
        }
        return luecken(text, geheimnisse, alt);
    }

    /**
     * Lueckenregel (Javadoc der Klasse): schneidet die Geheimnis-Kandidaten auf die Teile ausserhalb aller Funde von
     * {@code alt} zu und loest {@code alt} und die Reststuecke gemeinsam auf. Kein Fund von {@code alt} aendert sich.
     *
     * @param text      der gescannte Text
     * @param geheimnis die Kandidaten der Geheimnistypen
     * @param alt       das aufgeloeste Ergebnis ohne die Geheimnis-Kandidaten (ueberlappungsfrei)
     * @return unveraenderliche, ueberlappungsfreie, aufsteigend sortierte Fundliste
     */
    static List<Finding> luecken(String text, List<Finding> geheimnis, List<Finding> alt) {
        if (geheimnis.isEmpty()) {
            return alt;
        }
        List<Finding> alle = new ArrayList<>(alt);
        alle.addAll(Bestand.von(alt, false).luecken(geheimnis));
        return ConflictResolver.resolve(text, alle);
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
            return von(funde, true);
        }

        /** Mit {@code nurBestandstypen == false} zaehlen die Funde aller Typen (Lueckenregel). */
        static Bestand von(List<Finding> funde, boolean nurBestandstypen) {
            List<Finding> bestand = new ArrayList<>();
            for (Finding f : funde) {
                if (!nurBestandstypen || istBestandstyp(f.type())) {
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

        /**
         * Die Teile der Kandidaten ausserhalb der Bereiche, ungekuerzt, mit Typ, Konfidenz und Quelle des
         * Kandidaten. Ein Kandidat ohne gemeinsame Position mit den Bereichen bleibt unveraendert; liegt er ganz
         * darin, entfaellt er.
         */
        List<Finding> luecken(List<Finding> kandidaten) {
            List<Finding> ergebnis = new ArrayList<>(kandidaten.size());
            for (Finding f : kandidaten) {
                int cursor = f.start();
                for (int i = ersterBereichHinter(f.start()); i < zahl && starts[i] < f.end(); i++) {
                    stueck(f, cursor, starts[i], ergebnis);
                    cursor = Math.max(cursor, ends[i]);
                }
                stueck(f, cursor, f.end(), ergebnis);
            }
            return ergebnis;
        }

        private static void stueck(Finding f, int von, int bis, List<Finding> ziel) {
            if (von >= bis) {
                return;
            }
            if (von == f.start() && bis == f.end()) {
                ziel.add(f);
                return;
            }
            ziel.add(new Finding(f.type(), f.originalValue().substring(von - f.start(), bis - f.start()), von, bis,
                    f.confidence(), f.source()));
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
