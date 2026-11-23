package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.List;

/**
 * Teil 1b "KVNR ohne Kontextwort" (Feature kvnr-bsnr-erkennung, E-3, D-4, D-5, AK-11 bis AK-15). Die Klasse ergaenzt
 * die Funde des {@link KvNumberScanner} (Teil 1a) um geschlossene KVNR-Formen ohne Kontextwort; alles, was nur 1b
 * dient, steht hier und im einen Aufruf in {@link KvnrRecognizer} (Ruecknahme ohne Rest, Plan Regel 14).
 *
 * <p>Ein Kandidat an Position {@code i} entsteht nur, wenn alle fuenf Bedingungen gelten:
 * <ol>
 * <li>Zeichen {@code i} ist ein ASCII-Grossbuchstabe {@code A-Z}, die neun Zeichen danach sind ASCII-Ziffern (genau
 * zehn Zeichen, keine Trenner, kein Kleinbuchstabe).</li>
 * <li>Wortgrenze vorn: {@code i} ist der Textanfang, oder das Zeichen davor ist weder Buchstabe oder
 * Kombinationszeichen noch Ziffer noch eines von {@code _ - / \ @}.</li>
 * <li>Wortgrenze hinten: Textende, oder das Zeichen dahinter ist keines dieser Zeichen, und es ist nicht {@code .}
 * oder {@code ,} mit einer ASCII-Ziffer danach (Dezimalzahl, Aufzaehlung von Zahlen).</li>
 * <li>Die Pruefziffer ist nach {@link KvnrPruefziffer} gueltig.</li>
 * <li>Negativkontext (D-4): Rueckwaerts ab {@code i - 1} werden Leerraum ({@link Character#isWhitespace(int)},
 * {@link Character#isSpaceChar(int)}), {@code :} und {@code =} uebersprungen; endet der Text dort (mit Faltung
 * {@code A-Z}) auf ein Wort aus {@link #NEGATIVKONTEXT}, und steht davor kein Buchstabe und keine Ziffer, entsteht
 * kein Kandidat. Fuellwoerter werden nie uebersprungen.</li>
 * </ol>
 * Der Kandidat ist ein Fund KVNR ueber die zehn Zeichen mit Konfidenz 0,60 (fraglich) und Quelle {@value #QUELLE}.
 * Ein Kandidat, der einen Fund mit Kontextwort (Teil 1a) ueberlappt, entfaellt: dann bleibt der Fund aus 1a allein.
 *
 * <p>Nicht-ASCII-Ziffern: Anforderung A-3 nennt sie keine KVNR-Zeichen (sie bilden nie die neun Ziffern, Schritt 1),
 * der Plan sagt zur Wortgrenze nur "Ziffer". Gewaehlte Lesart: sperrend. Jede Ziffer im Sinne von
 * {@link Character#isDigit(int)} (auch arabisch-indische oder vollbreite Ziffern) direkt vor oder hinter den zehn
 * Zeichen verhindert den Fund wie ein Buchstabe. Das ist die vorsichtige Lesart gegen Fehlalarme (D-5); die
 * Gegenrichtung, eine echte KVNR direkt an einer solchen Ziffer, bleibt ohne Fund und ist eine bekannte Grenze von 1b.
 *
 * <p>Linearitaet: Je Startposition werden hoechstens elf Zeichen vorwaerts gelesen. Zwei Kandidaten liegen mindestens
 * elf Zeichen auseinander (neun Ziffern und die Grenze hinten); der Rueckwaertslauf ueber Leerraum, {@code :} und
 * {@code =} endet spaetestens an der letzten Ziffer des vorigen Kandidaten, die Rueckwaertslaeufe sind also disjunkt,
 * und der Wortvergleich liest hoechstens 15 Zeichen. Das Zusammenfuehren mit den Funden aus 1a ist ein Durchlauf ueber
 * zwei sortierte Listen. Insgesamt O(n), ohne regulaeren Ausdruck.
 *
 * <p>Die Klasse ist zustandslos, schreibt nichts, greift weder auf Netz noch auf Dateien zu, und Exceptions tragen nie
 * Text oder Ziffern aus der Eingabe.
 */
final class KvnrOhneKontext {

    /** Quelle der Funde ohne Kontextwort. */
    static final String QUELLE = "kvnr-ohne-kontext";

    /** Konfidenz (fraglich). */
    static final double KONFIDENZ = 0.60;

    /** Negativkontext nach D-4 (ASCII, klein; Vergleich mit Faltung {@code A-Z}), pflegbare Konstante. */
    static final List<String> NEGATIVKONTEXT = List.of("artikelnummer", "art.-nr.", "rechnungsnummer",
            "rechnungs-nr.", "vorgangsnummer", "aktenzeichen", "az.", "kennzeichen", "bestellnummer",
            "auftragsnummer", "kundennummer", "seriennummer", "sendungsnummer", "ticket");

    private static final int ZIFFERN = 9;
    private static final int LAENGE = ZIFFERN + 1;

    private KvnrOhneKontext() {
    }

    /**
     * Ergaenzt die Funde mit Kontextwort um die Funde ohne Kontextwort.
     *
     * @param text       der Text, nicht {@code null}
     * @param mitKontext die Funde aus Teil 1a, nach Position sortiert und ueberlappungsfrei
     * @return die Funde aus {@code mitKontext} und die Funde ohne Kontextwort, nach Position sortiert; ohne neuen
     *         Fund dieselbe Liste
     */
    static List<Finding> ergaenze(String text, List<Finding> mitKontext) {
        int n = text.length();
        List<Finding> neu = null;
        for (int i = 0; i + LAENGE <= n; i++) {
            if (istKandidat(text, i)) {
                if (neu == null) {
                    neu = new ArrayList<>();
                }
                neu.add(new Finding(FindingType.KVNR, text.substring(i, i + LAENGE), i, i + LAENGE, KONFIDENZ,
                        QUELLE));
                i += LAENGE - 1;
            }
        }
        if (neu == null) {
            return mitKontext;
        }
        return zusammenfuehren(mitKontext, neu);
    }

    /** Mischt beide sortierten Listen; ein Kandidat, der einen Fund aus {@code alt} ueberlappt, entfaellt. */
    private static List<Finding> zusammenfuehren(List<Finding> alt, List<Finding> kandidaten) {
        List<Finding> ergebnis = new ArrayList<>(alt.size() + kandidaten.size());
        int a = 0;
        for (Finding k : kandidaten) {
            while (a < alt.size() && alt.get(a).end() <= k.start()) {
                ergebnis.add(alt.get(a));
                a++;
            }
            if (a < alt.size() && alt.get(a).start() < k.end()) {
                continue;
            }
            ergebnis.add(k);
        }
        while (a < alt.size()) {
            ergebnis.add(alt.get(a));
            a++;
        }
        return ergebnis;
    }

    private static boolean istKandidat(String text, int i) {
        char anfang = text.charAt(i);
        if (anfang < 'A' || anfang > 'Z' || !neunZiffern(text, i + 1)) {
            return false;
        }
        return wortgrenzeVorn(text, i) && wortgrenzeHinten(text, i + LAENGE) && pruefzifferGueltig(text, i)
                && !negativkontextDavor(text, i);
    }

    private static boolean neunZiffern(String text, int von) {
        for (int k = 0; k < ZIFFERN; k++) {
            char c = text.charAt(von + k);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean pruefzifferGueltig(String text, int i) {
        int[] ziffern = new int[ZIFFERN];
        for (int k = 0; k < ZIFFERN; k++) {
            ziffern[k] = text.charAt(i + 1 + k) - '0';
        }
        return KvnrPruefziffer.istGueltig(text.charAt(i), ziffern);
    }

    /** Buchstabe, Kombinationszeichen, Ziffer (auch Nicht-ASCII, siehe Klassen-Javadoc) oder {@code _ - / \ @}. */
    private static boolean sperrzeichen(int cp) {
        int art = Character.getType(cp);
        boolean kombination = art == Character.NON_SPACING_MARK || art == Character.COMBINING_SPACING_MARK
                || art == Character.ENCLOSING_MARK;
        return Character.isLetter(cp) || kombination || Character.isDigit(cp) || cp == '_' || cp == '-'
                || cp == '/' || cp == '\\' || cp == '@';
    }

    private static boolean wortgrenzeVorn(String text, int i) {
        return i == 0 || !sperrzeichen(Character.codePointBefore(text, i));
    }

    private static boolean wortgrenzeHinten(String text, int ende) {
        if (ende >= text.length()) {
            return true;
        }
        if (sperrzeichen(Character.codePointAt(text, ende))) {
            return false;
        }
        char c = text.charAt(ende);
        if (c != '.' && c != ',') {
            return true;
        }
        if (ende + 1 >= text.length()) {
            return true;
        }
        char danach = text.charAt(ende + 1);
        return danach < '0' || danach > '9';
    }

    private static boolean negativkontextDavor(String text, int i) {
        int ende = i;
        while (ende > 0 && istZwischenzeichen(text.charAt(ende - 1))) {
            ende--;
        }
        for (String wort : NEGATIVKONTEXT) {
            int start = ende - wort.length();
            if (start >= 0 && gleichGefaltet(text, start, wort)
                    && (start == 0 || !Character.isLetterOrDigit(Character.codePointBefore(text, start)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean istZwischenzeichen(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == ':' || c == '=';
    }

    /** Vergleich ab {@code start} mit Faltung von {@code A-Z} auf klein, sonst zeichengleich. */
    private static boolean gleichGefaltet(String text, int start, String wort) {
        for (int k = 0; k < wort.length(); k++) {
            char c = text.charAt(start + k);
            char gefaltet = c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
            if (gefaltet != wort.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}
