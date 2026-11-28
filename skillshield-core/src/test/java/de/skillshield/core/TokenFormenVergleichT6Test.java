package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizer;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizerStandT6;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Kein Unterschied zu T-6 ausser den neuen Formen (Plan geheimnisse-erkennung, T-7). Der {@link TokenRecognizer}
 * (Stand T-7) wird gegen {@link TokenRecognizerStandT6} verglichen (woertliche Kopien von {@code KontextRegel},
 * {@code SchwacheVerbinder}, {@code TokenKontext}, {@code ZweiDurchgaenge} und des Erkenners am Tag
 * {@code ge-t6-token}, nur die Klassennamen sind geaendert). Je Text gilt auf Kandidaten-Ebene:
 * <ol>
 * <li>jeder Kandidat von T-6 steht feldgleich im neuen Ergebnis, <b>oder</b> ein Kandidat einer Form
 * ({@code token-jwt}, {@code token-pem}, {@code token-praefix}) deckt genau denselben Bereich mit demselben Typ und
 * mindestens derselben Konfidenz ab (er ersetzt ihn; solche Faelle werden einzeln gezaehlt);</li>
 * <li>jeder weitere neue Kandidat hat die Quelle einer Form;</li>
 * <li>die Abdeckung von T-6 ist eine Teilmenge der neuen, und die neuen Kandidaten sind ueberlappungsfrei.</li>
 * </ol>
 * Der {@code PasswortRecognizer} ist unveraendert (Vergleich in {@code PasswortVergleichT5Test}). Auf der Ebene des
 * Scanners wird {@code standard()} gegen einen Scanner mit den acht bisherigen Erkennern, dem
 * {@code PasswortRecognizer} und der T-6-Kopie verglichen: Keine Position, die der T-6-Scanner schuetzt, wird frei,
 * die Funde der uebrigen Typen sind gleich, und was neu hinzukommt, liegt ganz in Kandidaten einer Form. Gezaehlt
 * werden dieselben Mengen wie in {@code PasswortVergleichT5Test} (die 15 Bestandsmengen, Korpora, Generatoren,
 * Referenz-Ticket) und zwei Zufallslaeufe mit festem Seed. Meldungen nennen nur Menge, Index und Zahlen.
 */
class TokenFormenVergleichT6Test {

    private static final Set<String> QUELLEN_FORMEN = Set.of(TokenRecognizer.QUELLE_JWT, TokenRecognizer.QUELLE_PEM,
            TokenRecognizer.QUELLE_PRAEFIX);

    private static final Recognizer NEU = new TokenRecognizer();
    private static final Recognizer T6 = new TokenRecognizerStandT6();

    private static final TextScanner SCANNER_NEU = TextScanner.standard();
    private static final TextScanner SCANNER_T6 = scannerT6();

    private static TextScanner scannerT6() {
        List<Recognizer> liste = new ArrayList<>(SCANNER_NEU.recognizers().subList(0, 8));
        liste.add(new PasswortRecognizer());
        liste.add(new TokenRecognizerStandT6());
        return new TextScanner(liste);
    }

    /** Ergebnis des Vergleichs einer Menge auf Kandidaten-Ebene. */
    record Zahlen(int texte, int t6Kandidaten, int identisch, int ersetzt, int zusaetzlich, int textMitUnterschied,
            List<String> verstoesse) {

        String kurz() {
            return "texte=" + texte + "|t6=" + t6Kandidaten + "|identisch=" + identisch + "|ersetzt=" + ersetzt
                    + "|zusaetzlich=" + zusaetzlich + "|textMitUnterschied=" + textMitUnterschied;
        }
    }

    private static BitSet abdeckung(List<Finding> funde) {
        BitSet bits = new BitSet();
        for (Finding f : funde) {
            bits.set(f.start(), f.end());
        }
        return bits;
    }

    private static boolean ersetztDurch(Finding alt, Finding neu) {
        return neu.start() == alt.start() && neu.end() == alt.end() && neu.type().equals(alt.type())
                && neu.confidence() >= alt.confidence() && QUELLEN_FORMEN.contains(neu.source());
    }

    static Zahlen vergleiche(String menge, List<String> texte, Recognizer neuErkenner, Recognizer t6Erkenner) {
        int t6Summe = 0;
        int identisch = 0;
        int ersetzt = 0;
        int zusaetzlich = 0;
        int mitUnterschied = 0;
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> alt = t6Erkenner.detect(text);
            List<Finding> neu = neuErkenner.detect(text);
            t6Summe += alt.size();
            for (Finding c : alt) {
                if (neu.contains(c)) {
                    identisch++;
                } else if (neu.stream().anyMatch(f -> ersetztDurch(c, f))) {
                    ersetzt++;
                } else {
                    verstoesse.add(menge + "#" + i + ": Kandidat von T-6 fehlt oder ist veraendert");
                }
            }
            for (Finding f : neu) {
                boolean imAlten = alt.contains(f) || alt.stream().anyMatch(c -> ersetztDurch(c, f));
                if (!imAlten) {
                    zusaetzlich++;
                    if (!QUELLEN_FORMEN.contains(f.source())) {
                        verstoesse.add(menge + "#" + i + ": zusaetzlicher Kandidat ohne Quelle einer Form");
                    }
                }
            }
            BitSet verloren = abdeckung(alt);
            verloren.andNot(abdeckung(neu));
            if (!verloren.isEmpty()) {
                verstoesse.add(menge + "#" + i + ": " + verloren.cardinality()
                        + " Positionen von T-6 nicht mehr gedeckt");
            }
            for (int k = 1; k < neu.size(); k++) {
                if (neu.get(k - 1).end() > neu.get(k).start()) {
                    verstoesse.add(menge + "#" + i + ": Kandidaten ueberlappen bei " + k);
                }
            }
            if (!alt.equals(neu)) {
                mitUnterschied++;
            }
        }
        return new Zahlen(texte.size(), t6Summe, identisch, ersetzt, zusaetzlich, mitUnterschied, verstoesse);
    }

    private static Zahlen vergleiche(String menge, List<String> texte) {
        return vergleiche(menge, texte, NEU, T6);
    }

    // ------------------------------------------------------------------ Mengen, Kandidaten-Ebene

    @Test
    void bestandsmengen_alleFuenfzehn_keinUnterschiedZuT6() {
        int gezaehlt = 0;
        int texte = 0;
        int unterschied = 0;
        int kandidaten = 0;
        for (Map.Entry<String, List<String>> e : PasswortVergleichT5Test.mengen().entrySet()) {
            if (gezaehlt >= 15) {
                break;
            }
            gezaehlt++;
            Zahlen z = vergleiche(e.getKey(), e.getValue());
            assertEquals(List.of(), z.verstoesse(), e.getKey());
            texte += z.texte();
            unterschied += z.textMitUnterschied();
            kandidaten += z.t6Kandidaten() + z.zusaetzlich();
        }
        assertEquals(15, gezaehlt, "Zahl der Bestandsmengen");
        assertTrue(texte > 70_000, "zu wenige Texte: " + texte);
        assertEquals(0, unterschied, "Texte mit Unterschied in den Bestandsmengen");
        assertEquals(0, kandidaten, "Token-Kandidaten in den Bestandsmengen");
    }

    @Test
    void korporaGeneratorenUndTicket_zahlenJeMenge() {
        Map<String, List<String>> mengen = PasswortVergleichT5Test.mengen();
        Map<String, String> ist = new LinkedHashMap<>();
        for (String name : List.of("Negativkorpus", "Positivkorpus", "Negativ-Generator", "Verletzend-Generator",
                "Positiv-Generator", "Referenz-Ticket")) {
            Zahlen z = vergleiche(name, mengen.get(name));
            assertEquals(List.of(), z.verstoesse(), name);
            ist.put(name, z.kurz());
        }
        assertEquals(ERWARTET_MENGEN, ist);
    }

    /**
     * Gemessen am 27.11.2026. Positivkorpus: alle 26 Kandidaten von T-6 stehen feldgleich im neuen Ergebnis, dazu 38
     * zusaetzliche Kandidaten der Formen (die 38 Faelle von JWT, PEM und PRAEFIX); kein Kandidat wird ersetzt, weil
     * das JWT der zentralen Klasse keine Ziffer hat und die Kontextregel dafuer keinen Kandidaten liefert. Der
     * Positiv-Generator hat 33 337 Kandidaten (genau die TOKEN-Texte), alle identisch.
     */
    private static final Map<String, String> ERWARTET_MENGEN = Map.of(
            "Negativkorpus", "texte=372|t6=0|identisch=0|ersetzt=0|zusaetzlich=0|textMitUnterschied=0",
            "Positivkorpus", "texte=199|t6=26|identisch=26|ersetzt=0|zusaetzlich=38|textMitUnterschied=38",
            "Negativ-Generator", "texte=100000|t6=0|identisch=0|ersetzt=0|zusaetzlich=0|textMitUnterschied=0",
            "Verletzend-Generator", "texte=100000|t6=0|identisch=0|ersetzt=0|zusaetzlich=0|textMitUnterschied=0",
            "Positiv-Generator",
            "texte=100000|t6=33337|identisch=33337|ersetzt=0|zusaetzlich=0|textMitUnterschied=0",
            "Referenz-Ticket", "texte=1|t6=0|identisch=0|ersetzt=0|zusaetzlich=0|textMitUnterschied=0");

    // ------------------------------------------------------------------ Zufallslaeufe

    private static final long SEED_FORMEN = 20261211L;
    private static final int FORMEN_TEXTE = 120_000;

    private static final List<String> KONTEXT = List.of("Token", "Secret", "access_token", "API Key", "Bearer",
            "Authorization", "Passwort", "pw", "<token>", "<password>", "| Token |", "Token ist", "x");
    private static final List<String> VERBINDER = List.of(": ", ":", " = ", "=", " ", "\n", "\r\n", " | ", "\t", "");
    private static final List<String> WERTE = List.of(T, P1, P2, "x", "Basic ", "Bearer ", "\"", "'", "**", "`");

    private static String zufallstext(Random r, Map<String, Integer> zaehler) {
        List<String> formen = formenBausteine();
        StringBuilder sb = new StringBuilder();
        int n = 1 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            int w = r.nextInt(10);
            if (w < 4) {
                sb.append(KONTEXT.get(r.nextInt(KONTEXT.size()))).append(VERBINDER.get(r.nextInt(VERBINDER.size())));
                zaehler.merge("kontext", 1, Integer::sum);
            } else if (w < 8) {
                sb.append(formen.get(r.nextInt(formen.size())));
                zaehler.merge("form", 1, Integer::sum);
            } else {
                sb.append(WERTE.get(r.nextInt(WERTE.size())));
            }
            sb.append(r.nextInt(4) == 0 ? "\n" : " ");
        }
        return sb.toString();
    }

    private static List<String> formenBausteine() {
        List<String> liste = new ArrayList<>();
        liste.add(SynthetischeGeheimnisse.jwt());
        liste.add(SynthetischeGeheimnisse.jwt() + ".");
        liste.add("<" + SynthetischeGeheimnisse.jwt() + ">");
        liste.add("\"" + SynthetischeGeheimnisse.jwt() + "\"");
        liste.add("https://example.invalid/x?t=" + SynthetischeGeheimnisse.jwt());
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            liste.add(SynthetischeGeheimnisse.praefixToken(praefix));
        }
        liste.add(SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\n", true));
        liste.add(SynthetischeGeheimnisse.pemBlock("RSA PRIVATE KEY", "\r\n", true));
        liste.add(SynthetischeGeheimnisse.pemBlock("EC PRIVATE KEY", "\n", false));
        liste.add(SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY") + "\n");
        liste.add(SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY") + "\n");
        liste.add(SynthetischeGeheimnisse.pemBlock("CERTIFICATE", "\n", true));
        liste.addAll(SynthetischeGeheimnisse.pemKoerperZeilen());
        liste.add("eyJ");
        liste.add("ey" + "J" + SynthetischeGeheimnisse.MARKE);
        liste.add(".");
        return liste;
    }

    static List<String> formenZufall(Map<String, Integer> zaehler) {
        Random r = new Random(SEED_FORMEN);
        List<String> texte = new ArrayList<>(FORMEN_TEXTE);
        for (int i = 0; i < FORMEN_TEXTE; i++) {
            texte.add(zufallstext(r, zaehler));
        }
        return texte;
    }

    @Test
    void zufallslauf_formenUndKontextGemischt_keinUnterschiedAusserDenFormen() {
        Map<String, Integer> zaehler = new LinkedHashMap<>();
        List<String> texte = formenZufall(zaehler);
        Zahlen z = vergleiche("Zufall Formen", texte);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(FORMEN_TEXTE, z.texte(), "Texte");
        // Ist-Stand (T-7, Abnahme): 11 661 Kandidaten von T-6, davon 4 686 feldgleich im neuen Ergebnis und 6 975 von
        // einem Kandidaten einer Form mit demselben Bereich ersetzt (Praefix-Token, PEM-Koerper, JWT mit Ziffer hinter
        // einem Kontextwort), dazu 130 704 neue Kandidaten der Formen und 80 679 Texte mit Unterschied; kein Kandidat
        // von T-6 geht verloren.
        assertEquals("texte=120000|t6=11661|identisch=4686|ersetzt=6975|zusaetzlich=130704|textMitUnterschied=80679",
                z.kurz());
        assertEquals(z.t6Kandidaten(), z.identisch() + z.ersetzt(), "jeder T-6-Kandidat ist identisch oder ersetzt");
    }

    @Test
    void zufallslauf_ausDemPasswortVergleich_keinUnterschiedAusserDenFormen() {
        PasswortVergleichT5Test.Zaehler z = new PasswortVergleichT5Test.Zaehler();
        List<String> texte = PasswortVergleichT5Test.zufallstexte(z);
        Zahlen zahlen = vergleiche("Zufall T-5/T-6", texte);
        assertEquals(List.of(), zahlen.verstoesse());
        assertEquals(120_000, zahlen.texte(), "Texte");
        assertEquals(0, zahlen.ersetzt(), "ersetzte Kandidaten");
        assertEquals(0, zahlen.zusaetzlich(), "zusaetzliche Kandidaten");
        assertEquals(0, zahlen.textMitUnterschied(), "Texte mit Unterschied");
        assertTrue(zahlen.identisch() > 30_000, "zu wenige Kandidaten " + zahlen.identisch());
    }

    // ------------------------------------------------------------------ Scanner-Ebene

    /** Ergebnis des Vergleichs der Scanner einer Menge. */
    record ScannerZahlen(int texte, int mitFormKandidat, int mitUnterschied, int freiePositionen,
            List<String> verstoesse) {
    }

    private static List<Finding> ohneGeheimnis(List<Finding> funde) {
        return funde.stream().filter(f -> !GeheimnisTypen.istGeheimnistyp(f.type())).toList();
    }

    static ScannerZahlen vergleicheScanner(String menge, List<String> texte, TextScanner neu, TextScanner t6) {
        int mitForm = 0;
        int mitUnterschied = 0;
        int frei = 0;
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> a = t6.scan(text);
            List<Finding> n = neu.scan(text);
            BitSet abdA = abdeckung(a);
            BitSet abdN = abdeckung(n);
            BitSet verloren = (BitSet) abdA.clone();
            verloren.andNot(abdN);
            if (!verloren.isEmpty()) {
                frei += verloren.cardinality();
                verstoesse.add(menge + "#" + i + ": " + verloren.cardinality() + " Positionen frei geworden");
            }
            if (!ohneGeheimnis(a).equals(ohneGeheimnis(n))) {
                verstoesse.add(menge + "#" + i + ": Funde der uebrigen Typen unterscheiden sich");
            }
            List<Finding> formen = NEU.detect(text).stream().filter(f -> QUELLEN_FORMEN.contains(f.source())).toList();
            BitSet neuHinzu = (BitSet) abdN.clone();
            neuHinzu.andNot(abdA);
            neuHinzu.andNot(abdeckung(formen));
            if (!neuHinzu.isEmpty()) {
                verstoesse.add(menge + "#" + i + ": " + neuHinzu.cardinality() + " Positionen ohne Form-Kandidat neu");
            }
            if (formen.isEmpty()) {
                if (!a.equals(n)) {
                    verstoesse.add(menge + "#" + i + ": ohne Form-Kandidat nicht feldgleich");
                }
            } else {
                mitForm++;
            }
            if (!a.equals(n)) {
                mitUnterschied++;
            }
        }
        return new ScannerZahlen(texte.size(), mitForm, mitUnterschied, frei, verstoesse);
    }

    @Test
    void scanner_bestandsmengen_keinePositionWirdFrei_keinUnterschied() {
        int gezaehlt = 0;
        int mitUnterschied = 0;
        for (Map.Entry<String, List<String>> e : PasswortVergleichT5Test.mengen().entrySet()) {
            if (gezaehlt >= 15) {
                break;
            }
            gezaehlt++;
            ScannerZahlen z = vergleicheScanner(e.getKey(), e.getValue(), SCANNER_NEU, SCANNER_T6);
            assertEquals(List.of(), z.verstoesse(), e.getKey());
            assertEquals(0, z.freiePositionen(), e.getKey() + ": freie Positionen");
            assertEquals(0, z.mitFormKandidat(), e.getKey() + ": Texte mit Form-Kandidat");
            mitUnterschied += z.mitUnterschied();
        }
        assertEquals(15, gezaehlt, "Zahl der Bestandsmengen");
        assertEquals(0, mitUnterschied, "Texte mit Unterschied in den Bestandsmengen");
    }

    @Test
    void scanner_korporaGeneratorenUndTicket_keinePositionWirdFrei() {
        Map<String, List<String>> mengen = PasswortVergleichT5Test.mengen();
        Map<String, String> ist = new LinkedHashMap<>();
        for (String name : List.of("Negativkorpus", "Positivkorpus", "Negativ-Generator", "Verletzend-Generator",
                "Positiv-Generator", "Referenz-Ticket")) {
            ScannerZahlen z = vergleicheScanner(name, mengen.get(name), SCANNER_NEU, SCANNER_T6);
            assertEquals(List.of(), z.verstoesse(), name);
            assertEquals(0, z.freiePositionen(), name + ": freie Positionen");
            ist.put(name, "texte=" + z.texte() + "|mitForm=" + z.mitFormKandidat() + "|mitUnterschied="
                    + z.mitUnterschied());
        }
        assertEquals(ERWARTET_SCANNER, ist);
    }

    /** Gemessen am 27.11.2026; Positivkorpus: 38 Texte mit Form-Kandidat, 37 mit Unterschied (JWT in der URL nicht). */
    private static final Map<String, String> ERWARTET_SCANNER = Map.of(
            "Negativkorpus", "texte=372|mitForm=0|mitUnterschied=0",
            "Positivkorpus", "texte=199|mitForm=38|mitUnterschied=37",
            "Negativ-Generator", "texte=100000|mitForm=0|mitUnterschied=0",
            "Verletzend-Generator", "texte=100000|mitForm=0|mitUnterschied=0",
            "Positiv-Generator", "texte=100000|mitForm=0|mitUnterschied=0",
            "Referenz-Ticket", "texte=1|mitForm=0|mitUnterschied=0");

    @Test
    void scanner_zufallslaeufe_keinePositionWirdFrei() {
        List<String> texte = formenZufall(new LinkedHashMap<>());
        ScannerZahlen z = vergleicheScanner("Zufall Formen", texte, SCANNER_NEU, SCANNER_T6);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(0, z.freiePositionen(), "freie Positionen");
        // Ist-Stand (T-7, Abnahme): 80 679 Texte mit Form-Kandidat, 77 635 davon mit anderer Fundliste als T-6.
        assertEquals("120000|80679|77635", z.texte() + "|" + z.mitFormKandidat() + "|" + z.mitUnterschied());
        PasswortVergleichT5Test.Zaehler zz = new PasswortVergleichT5Test.Zaehler();
        ScannerZahlen z2 = vergleicheScanner("Zufall T-5/T-6", PasswortVergleichT5Test.zufallstexte(zz), SCANNER_NEU,
                SCANNER_T6);
        assertEquals(List.of(), z2.verstoesse());
        assertEquals(0, z2.freiePositionen(), "freie Positionen");
        assertEquals(0, z2.mitFormKandidat(), "Texte mit Form-Kandidat");
        assertEquals(0, z2.mitUnterschied(), "Texte mit Unterschied");
    }

    // ------------------------------------------------------------------ Gegenproben

    /** Mit einer Ziffer im JWT: Die Kontextregel von T-6 liefert fuer denselben Bereich einen Kandidaten. */
    private static final String PROBE_JWT = "Authorization: Bearer " + SynthetischeGeheimnisse.jwt() + "7";

    @Test
    void gegenprobe_identischeErkenner_keinVerstoss_keinUnterschied() {
        Zahlen z = vergleiche("Probe", List.of("Token: " + T, PROBE_JWT), T6, T6);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(0, z.textMitUnterschied(), "identisch");
    }

    @Test
    void gegenprobe_jwtHinterBearer_istEinGezaehlterErsatz() {
        Zahlen z = vergleiche("Probe", List.of(PROBE_JWT));
        assertEquals(List.of(), z.verstoesse());
        assertEquals(1, z.t6Kandidaten(), "T-6-Kandidaten");
        assertEquals(0, z.identisch(), "identisch");
        assertEquals(1, z.ersetzt(), "ersetzt");
        assertEquals(0, z.zusaetzlich(), "zusaetzlich");
        assertEquals(1, z.textMitUnterschied(), "Text mit Unterschied");
    }

    @Test
    void gegenprobe_jwtHinterBearerOhneZiffer_istEinZusaetzlicherFormenKandidat() {
        Zahlen z = vergleiche("Probe", List.of("Authorization: Bearer " + SynthetischeGeheimnisse.jwt()));
        assertEquals(List.of(), z.verstoesse());
        assertEquals(0, z.t6Kandidaten(), "T-6-Kandidaten");
        assertEquals(0, z.ersetzt(), "ersetzt");
        assertEquals(1, z.zusaetzlich(), "zusaetzlich");
    }

    @Test
    void gegenprobe_jwtOhneKontext_istEinZusaetzlicherFormenKandidat() {
        Zahlen z = vergleiche("Probe", List.of("Das Token " + SynthetischeGeheimnisse.jwt() + " ist abgelaufen."));
        assertEquals(List.of(), z.verstoesse());
        assertEquals(0, z.t6Kandidaten(), "T-6-Kandidaten");
        assertEquals(1, z.zusaetzlich(), "zusaetzlich");
    }

    @Test
    void gegenprobe_fehlenderVerschobenerOderFremderKandidat_wirdGemeldet() {
        String text = "Token: " + T;
        Finding k = T6.detect(text).get(0);
        Recognizer ohne = t -> List.of();
        Recognizer verschoben = t -> List.of(new Finding(k.type(), t.substring(k.start(), k.end() - 1), k.start(),
                k.end() - 1, k.confidence(), k.source()));
        Recognizer fremd = t -> List.of(k, new Finding(GeheimnisTypen.TOKEN, "x", 0, 1, 0.95, "token-kontext"));
        Recognizer zuletzt = t -> List.of(new Finding(k.type(), t.substring(k.start(), k.end()), k.start(), k.end(),
                0.95, TokenRecognizer.QUELLE_PEM));
        assertTrue(vergleiche("Probe", List.of(text), ohne, T6).verstoesse().size() >= 1, "fehlt");
        assertTrue(vergleiche("Probe", List.of(text), verschoben, T6).verstoesse().size() >= 1, "verschoben");
        assertTrue(vergleiche("Probe", List.of(text), fremd, T6).verstoesse().size() >= 1, "fremde Quelle");
        assertEquals(List.of(), vergleiche("Probe", List.of(text), zuletzt, T6).verstoesse(), "Form ersetzt");
    }

    /**
     * Entscheidung nach T-7, Durchlauf 1, Punkt 3 (d): Bei {@code Passwort ist <JWT mit Ziffer>} ersetzt der TOKEN-Fund
     * 0,95 der Form den PASSWORT-Fund 0,80 gleichen Bereichs aus T-5. Der Scanner zaehlt: ein Ersatz, ein
     * unveraenderter Fund ({@code Passwort:} mit Doppelpunkt bleibt PASSWORT 0,95) und zwei neue Funde (JWT ohne
     * Ziffer, das T-5 nicht als Passwort las).
     */
    @Test
    void gegenprobe_passwortIstJwt_scannerZaehltDenErsatzPasswort080DurchToken095() {
        String jwt = SynthetischeGeheimnisse.jwt();
        List<String> texte = List.of("Passwort ist " + jwt + "7", "Passwort: " + jwt + "7", "Passwort ist " + jwt,
                "Kennwort ist " + jwt);
        int ersetzt = 0;
        int unveraendert = 0;
        int zusaetzlich = 0;
        for (String text : texte) {
            List<Finding> alt = SCANNER_T6.scan(text);
            List<Finding> neu = SCANNER_NEU.scan(text);
            assertEquals(1, neu.size(), "ein neuer Fund");
            if (alt.isEmpty()) {
                zusaetzlich++;
                continue;
            }
            assertEquals(1, alt.size(), "ein alter Fund");
            Finding a = alt.get(0);
            Finding n = neu.get(0);
            if (a.equals(n)) {
                unveraendert++;
            } else if (a.type().equals(GeheimnisTypen.PASSWORT) && a.confidence() == 0.80
                    && n.type().equals(GeheimnisTypen.TOKEN) && n.confidence() == 0.95
                    && a.start() == n.start() && a.end() == n.end()
                    && TokenRecognizer.QUELLE_JWT.equals(n.source())) {
                ersetzt++;
            }
        }
        assertEquals(1, ersetzt, "Ersatz PASSWORT 0,80 durch TOKEN 0,95");
        assertEquals(1, unveraendert, "unveraendert");
        assertEquals(2, zusaetzlich, "neue Funde");
    }

    /**
     * Typwechsel (Entscheidung nach T-7, Durchlauf 2, Punkt 4 (c)): Bei {@code Passwort ist <Praefix-Token>} ersetzt
     * der TOKEN-Fund 0,95 der Form den PASSWORT-Fund 0,80 gleichen Bereichs aus T-5; mit Doppelpunkt bleibt der
     * PASSWORT-Fund 0,95. Gezaehlt: ein Ersatz, ein unveraenderter Fund, kein neuer Fund.
     */
    @Test
    void gegenprobe_passwortIstPraefixToken_scannerZaehltDenErsatzPasswort080DurchToken095() {
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        List<String> texte = List.of("Passwort ist " + token, "Passwort: " + token);
        int ersetzt = 0;
        int unveraendert = 0;
        int zusaetzlich = 0;
        for (String text : texte) {
            List<Finding> alt = SCANNER_T6.scan(text);
            List<Finding> neu = SCANNER_NEU.scan(text);
            assertEquals(1, neu.size(), "ein neuer Fund");
            if (alt.isEmpty()) {
                zusaetzlich++;
                continue;
            }
            assertEquals(1, alt.size(), "ein alter Fund");
            Finding a = alt.get(0);
            Finding n = neu.get(0);
            if (a.equals(n)) {
                unveraendert++;
            } else if (a.type().equals(GeheimnisTypen.PASSWORT) && a.confidence() == 0.80
                    && n.type().equals(GeheimnisTypen.TOKEN) && n.confidence() == 0.95
                    && a.start() == n.start() && a.end() == n.end()
                    && TokenRecognizer.QUELLE_PRAEFIX.equals(n.source())) {
                ersetzt++;
            }
        }
        assertEquals(1, ersetzt, "Ersatz PASSWORT 0,80 durch TOKEN 0,95");
        assertEquals(1, unveraendert, "unveraendert");
        assertEquals(0, zusaetzlich, "neue Funde");
    }

    /**
     * Typwechsel (Punkt 4 (c)): Ein PASSWORT-Fund ganz in einem PEM-Fund zwischen den Markierungen geht in diesem auf.
     * T-6 meldet den Wert hinter {@code Passwort:} im Schluessel; jetzt liegt er im TOKEN-Fund ueber den Inhalt
     * zwischen den Markierungen, kein PASSWORT-Fund bleibt, keine Position wird frei.
     */
    @Test
    void gegenprobe_passwortInPemZwischenMarkierungen_gehtImPemFundAuf() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        List<String> zeilen = SynthetischeGeheimnisse.pemKoerperZeilen();
        String text = "x " + kopf + " " + zeilen.get(0) + " Passwort: " + P1 + " " + zeilen.get(1) + " " + fuss;
        List<Finding> alt = SCANNER_T6.scan(text);
        List<Finding> neu = SCANNER_NEU.scan(text);
        assertEquals(1, alt.size(), "ein alter Fund");
        assertEquals(GeheimnisTypen.PASSWORT, alt.get(0).type(), "alter Typ");
        assertEquals(1, neu.size(), "ein neuer Fund");
        Finding n = neu.get(0);
        assertEquals(GeheimnisTypen.TOKEN, n.type(), "neuer Typ");
        assertEquals(TokenRecognizer.QUELLE_PEM, n.source(), "Quelle");
        assertTrue(n.start() <= alt.get(0).start() && alt.get(0).end() <= n.end(), "PASSWORT-Fund liegt im PEM-Fund");
        ScannerZahlen z = vergleicheScanner("Probe", List.of(text), SCANNER_NEU, SCANNER_T6);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(0, z.freiePositionen(), "freie Positionen");
    }

    @Test
    void gegenprobe_scanner_einFehlenderErkennerGibtPositionenFrei_wirdGemeldet() {
        TextScanner ohneToken = new TextScanner(SCANNER_NEU.recognizers().subList(0, 8));
        ScannerZahlen z = vergleicheScanner("Probe", List.of("Token: " + T), ohneToken, SCANNER_T6);
        assertEquals(T.length(), z.freiePositionen(), "freie Positionen");
        assertTrue(!z.verstoesse().isEmpty(), "gemeldet");
        ScannerZahlen gleich = vergleicheScanner("Probe", List.of("Token: " + T, "x"), SCANNER_NEU, SCANNER_T6);
        assertEquals(List.of(), gleich.verstoesse());
        assertEquals(0, gleich.mitUnterschied(), "gleich");
        ScannerZahlen jwt = vergleicheScanner("Probe", List.of("Das Token " + SynthetischeGeheimnisse.jwt()),
                SCANNER_NEU, SCANNER_T6);
        assertEquals(List.of(), jwt.verstoesse());
        assertEquals(1, jwt.mitUnterschied(), "ein neuer Fund");
    }
}
