package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.GeheimnisPositivGenerator.Text;
import de.skillshield.core.GeheimnisPositivKorpus.Erwartung;
import de.skillshield.core.GeheimnisPositivKorpus.Fall;
import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Pruefstand fuer den Positivkorpus und den Positiv-Generator (Plan geheimnisse-erkennung, T-2; Anforderung AK-37,
 * AK-38). Er steht vor der Erkennerlogik und ist ab Abnahme von T-2 eingefroren (Plan Kapitel 6.3); erlaubt ist nur
 * das Freischalten von Kategorien in {@link #AKTIVE_KATEGORIEN} und der Generator-Treffer, und das Eintragen von
 * Istwerten.
 *
 * <ul>
 * <li><b>Strukturpruefungen:</b> mindestens 100 Faelle, Mindestzahl je Kategorie, Erwartungen passen zum Text,
 * jedes Kontextwort, jeder Verbinder, jedes Zwischenwort kommt vor, Werte stammen aus der zentralen Klasse, und die
 * Vorbedingung gegen {@code ALT} (Konfliktfaelle nach T-1 Regel 3, Reststuecke nach der Lueckenregel).</li>
 * <li><b>Prueflogik:</b> Fuer jede Kategorie in {@link #AKTIVE_KATEGORIEN} (in T-2 leer) liefert
 * {@code TextScanner.standard()} genau die erwarteten Funde: die Funde von {@code ALT} unveraendert und dazu die
 * Geheimnis-Funde des Falls (Typ, Bereich, Konfidenz, Stufe, Quelle).</li>
 * <li><b>Generator (AK-38):</b> 100 000 gueltige Texte mit genau einem erwarteten Fund und 100 000 Texte, die W1 oder
 * W2 verletzen. Die Treffer von {@code standard()} stehen als Istwert fest ({@link #GENERATOR_PASSWORT_TREFFER_IST},
 * {@link #GENERATOR_TOKEN_TREFFER_IST}); in T-2 sind es 0, weil es noch keine Logik gibt. Die verletzenden Texte
 * liefern 0 PASSWORT- und TOKEN-Funde.</li>
 * <li><b>Gegenproben:</b> Ein kuenstlicher Scanner, der genau die Erwartungen liefert, besteht; einer mit
 * verschobenem Bereich, anderer Quelle, anderer Konfidenz oder ohne Geheimnis-Fund faellt durch; einer ohne die
 * Funde von {@code ALT} faellt bei jedem Fall mit Bestandsfund durch.</li>
 * </ul>
 * Meldungen nennen nur Fall-IDs, Kategorien, Seeds und Zahlen, nie Texte oder Werte (Plan Kapitel 6.1).
 */
class GeheimnisPositivKorpusTest {

    // ------------------------------------------------------------------ Freischaltung und Istwerte (eingefroren)

    /**
     * Kategorien, die {@code standard()} schon erfuellen muss; in T-2 leer, T-4 und folgende schalten frei.
     * Umgestellt durch geheimnisse-erkennung T-5: alt K1, ZWISCHENTEIL, UMBRUCH, ANFUEHRUNG, MEHRFACH; neu dazu K2,
     * K3, K4 (nur die Freischaltung, die Pruefung bleibt unveraendert).
     * Umgestellt durch geheimnisse-erkennung T-6: neu dazu TOKEN_KONTEXT und SCHEMA (MEHRFACH war schon aktiv).
     */
    static final Set<String> AKTIVE_KATEGORIEN = Set.of(GeheimnisPositivKorpus.K1,
            GeheimnisPositivKorpus.ZWISCHENTEIL, GeheimnisPositivKorpus.UMBRUCH,
            GeheimnisPositivKorpus.ANFUEHRUNG, GeheimnisPositivKorpus.MEHRFACH, GeheimnisPositivKorpus.K2,
            GeheimnisPositivKorpus.K3, GeheimnisPositivKorpus.K4, GeheimnisPositivKorpus.TOKEN_KONTEXT,
            GeheimnisPositivKorpus.SCHEMA, GeheimnisPositivKorpus.JWT, GeheimnisPositivKorpus.PEM,
            GeheimnisPositivKorpus.PRAEFIX);

    /**
     * Treffer von {@code standard()} im Positiv-Generator je Typ. Erlaubt sind nur 0 (noch nicht freigeschaltet) und
     * die volle Zahl der Texte des Typs (AK-38: 100 Prozent); die Testmethode prueft das. Sollwert PASSWORT: 66 663
     * ab T-4.
     */
    static final int GENERATOR_PASSWORT_TREFFER_IST = 66_663;
    /** Sollwert TOKEN: 33 337 ab T-6 (sonst 0, siehe {@link #GENERATOR_PASSWORT_TREFFER_IST}). */
    static final int GENERATOR_TOKEN_TREFFER_IST = 33_337;

    /** Zahl der Faelle, der Konfliktfaelle und der Faelle je Kategorie (Istwerte). */
    static final int FAELLE_IST = 199;
    static final int KONFLIKTFAELLE_IST = 26;
    static final String FAELLE_JE_KATEGORIE_IST = "K1=64,ZWISCHENTEIL=13,UMBRUCH=5,ANFUEHRUNG=8,K2=7,K3=5,K4=3,"
            + "TOKEN_KONTEXT=18,SCHEMA=4,JWT=4,PEM=22,PRAEFIX=12,ODBC=7,ORACLE=7,KOMMANDOZEILE=13,MEHRFACH=7";

    /** Kennzahlen des Positiv-Generators (Istwerte). */
    static final String GENERATOR_KENNZAHLEN_IST =
            "verworfenAlt=0,verworfenForm=318,passwort=66663,token=33337,kurz=5620";
    static final String VERLETZEND_KENNZAHLEN_IST = "NUR_BUCHSTABEN=25016,NUR_ZIFFERN=25086,"
            + "HOECHSTENS_FUENF_ZEICHEN=25015,WORT_MIT_AUSRUFEZEICHEN_AM_ENDE=24883";

    static final String SIEGEL_POSITIVKORPUS = "cd49ad36";
    static final String SIEGEL_POSITIV_GENERATOR = "4dbd584c";
    static final String SIEGEL_VERLETZEND_GENERATOR = "0e051149";

    private static final int SIEGEL_TEXTE = 1000;
    private static final int GEGENPROBE_TEXTE = 5000;

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TextScannerStandGeheimnis ALT = TextScannerStandGeheimnis.ALT;
    private static final String UE = String.valueOf((char) 0xFC);

    /** Mindestzahl je Kategorie (Strukturpruefung). */
    private static final Map<String, Integer> MINDESTZAHL = Map.ofEntries(
            Map.entry(GeheimnisPositivKorpus.K1, 30), Map.entry(GeheimnisPositivKorpus.ZWISCHENTEIL, 8),
            Map.entry(GeheimnisPositivKorpus.UMBRUCH, 4), Map.entry(GeheimnisPositivKorpus.ANFUEHRUNG, 6),
            Map.entry(GeheimnisPositivKorpus.K2, 6), Map.entry(GeheimnisPositivKorpus.K3, 3),
            Map.entry(GeheimnisPositivKorpus.K4, 2), Map.entry(GeheimnisPositivKorpus.TOKEN_KONTEXT, 14),
            Map.entry(GeheimnisPositivKorpus.SCHEMA, 4), Map.entry(GeheimnisPositivKorpus.JWT, 4),
            Map.entry(GeheimnisPositivKorpus.PEM, 12), Map.entry(GeheimnisPositivKorpus.PRAEFIX, 9),
            Map.entry(GeheimnisPositivKorpus.ODBC, 6), Map.entry(GeheimnisPositivKorpus.ORACLE, 4),
            Map.entry(GeheimnisPositivKorpus.KOMMANDOZEILE, 10), Map.entry(GeheimnisPositivKorpus.MEHRFACH, 6));

    // ------------------------------------------------------------------ Hilfen

    /** Die erwarteten Funde eines Falls: {@code ALT} unveraendert plus die Geheimnis-Funde, nach Beginn sortiert. */
    static List<Finding> erwartet(Fall fall) {
        List<Finding> alle = new ArrayList<>(ALT.scan(fall.text()));
        alle.addAll(fall.geheimnisFunde());
        alle.sort(Comparator.comparingInt(Finding::start));
        return alle;
    }

    /** Fall-IDs, bei denen {@code scanner} nicht genau die erwarteten Funde liefert. */
    static List<String> abweichungen(Function<String, List<Finding>> scanner, List<Fall> faelle) {
        List<String> ids = new ArrayList<>();
        for (Fall f : faelle) {
            if (!scanner.apply(f.text()).equals(erwartet(f))) {
                ids.add(f.id());
            }
        }
        return ids;
    }

    private static List<Fall> faelle() {
        return GeheimnisPositivKorpus.faelle();
    }

    private static Map<String, Fall> nachText() {
        Map<String, Fall> map = new HashMap<>();
        for (Fall f : faelle()) {
            map.put(f.text(), f);
        }
        return map;
    }

    private static String kennzahlenJeKategorie() {
        Map<String, Integer> zaehler = new LinkedHashMap<>();
        for (String k : GeheimnisPositivKorpus.KATEGORIEN) {
            zaehler.put(k, 0);
        }
        for (Fall f : faelle()) {
            zaehler.merge(f.kategorie(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        zaehler.forEach((k, n) -> sb.append(sb.length() == 0 ? "" : ",").append(k).append('=').append(n));
        return sb.toString();
    }

    /** Die nicht von {@code alt} abgedeckten, zusammenhaengenden Teile des Bereichs {@code [von, bis)}. */
    private static List<int[]> unbedeckteStuecke(int von, int bis, List<Finding> alt) {
        boolean[] bedeckt = new boolean[bis - von];
        for (Finding a : alt) {
            for (int p = Math.max(a.start(), von); p < Math.min(a.end(), bis); p++) {
                bedeckt[p - von] = true;
            }
        }
        List<int[]> stuecke = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= bedeckt.length; i++) {
            boolean frei = i < bedeckt.length && !bedeckt[i];
            if (frei && start < 0) {
                start = i;
            } else if (!frei && start >= 0) {
                stuecke.add(new int[] {von + start, von + i});
                start = -1;
            }
        }
        return stuecke;
    }

    // ------------------------------------------------------------------ Strukturpruefungen

    @Test
    void struktur_mindestzahlen_idsEinmalig_textEinmalig() {
        List<Fall> faelle = faelle();
        assertTrue(faelle.size() >= 100, "weniger als 100 Faelle: " + faelle.size());
        assertEquals(FAELLE_IST, faelle.size(), "Istwert der Fallzahl");
        assertEquals(FAELLE_JE_KATEGORIE_IST, kennzahlenJeKategorie(), "Istwerte je Kategorie");
        assertEquals(KONFLIKTFAELLE_IST, faelle.stream().filter(Fall::konflikt).count(), "Istwert Konfliktfaelle");
        Set<String> ids = new HashSet<>();
        Set<String> texte = new HashSet<>();
        for (Fall f : faelle) {
            assertTrue(ids.add(f.id()), "doppelte Fall-ID " + f.id());
            assertTrue(texte.add(f.text()), "doppelter Text bei " + f.id());
            assertTrue(GeheimnisPositivKorpus.KATEGORIEN.contains(f.kategorie()), "Kategorie bei " + f.id());
        }
        for (String kategorie : GeheimnisPositivKorpus.KATEGORIEN) {
            int n = (int) faelle.stream().filter(f -> f.kategorie().equals(kategorie)).count();
            assertTrue(n >= MINDESTZAHL.get(kategorie), "zu wenige Faelle in " + kategorie + ": " + n);
        }
        assertTrue(GeheimnisPositivKorpus.KATEGORIEN.containsAll(AKTIVE_KATEGORIEN), "unbekannte aktive Kategorie");
    }

    @Test
    void struktur_erwartungenPassenZumText_typQuelleKonfidenzStufe() {
        Set<String> quellen = Set.of(GeheimnisPositivKorpus.QUELLE_PASSWORT, GeheimnisPositivKorpus.QUELLE_VERBINDUNG,
                GeheimnisPositivKorpus.QUELLE_TOKEN, GeheimnisPositivKorpus.QUELLE_JWT,
                GeheimnisPositivKorpus.QUELLE_PEM, GeheimnisPositivKorpus.QUELLE_PRAEFIX);
        for (Fall f : faelle()) {
            int vorher = 0;
            for (Erwartung e : f.funde()) {
                assertTrue(e.start() >= vorher && e.start() < e.end() && e.end() <= f.text().length(),
                        "Bereich bei " + f.id());
                vorher = e.end();
                String typ = e.typ().name();
                assertTrue(typ.equals("PASSWORT") || typ.equals("TOKEN"), "Typ bei " + f.id());
                assertTrue(quellen.contains(e.quelle()), "Quelle bei " + f.id());
                assertTrue(e.konfidenz() == 0.95 || e.konfidenz() == 0.80, "Konfidenz bei " + f.id());
                assertEquals(e.konfidenz() == 0.95 ? ConfidenceLevel.SICHER : ConfidenceLevel.WAHRSCHEINLICH,
                        e.stufe(), "Stufe bei " + f.id());
                boolean passwort = typ.equals("PASSWORT");
                assertEquals(passwort, e.quelle().startsWith("passwort-"), "Typ und Quelle passen nicht: " + f.id());
                boolean inGeschuetzt = false;
                for (int[] g : f.geschuetzt()) {
                    inGeschuetzt |= e.start() >= g[0] && e.end() <= g[1];
                }
                assertTrue(inGeschuetzt, "Fund ausserhalb der geschuetzten Bereiche bei " + f.id());
            }
            for (int[] g : f.geschuetzt()) {
                assertTrue(g[0] >= 0 && g[0] < g[1] && g[1] <= f.text().length(), "Schutzbereich bei " + f.id());
            }
            assertEquals(f.funde().size(), f.geheimnisFunde().size(), "Funde bei " + f.id());
        }
    }

    @Test
    void struktur_vorbedingungGegenAlt_konfliktfaelleUndReststuecke() {
        for (Fall f : faelle()) {
            List<Finding> alt = ALT.scan(f.text());
            boolean altDeckt = false;
            List<int[]> erwarteteStuecke = new ArrayList<>();
            for (int[] g : f.geschuetzt()) {
                List<int[]> stuecke = unbedeckteStuecke(g[0], g[1], alt);
                altDeckt |= stuecke.size() != 1 || stuecke.get(0)[0] != g[0] || stuecke.get(0)[1] != g[1];
                erwarteteStuecke.addAll(stuecke);
            }
            assertEquals(altDeckt, f.konflikt(), "Konfliktfall-Markierung stimmt nicht bei " + f.id());
            List<int[]> ist = new ArrayList<>();
            for (Erwartung e : f.funde()) {
                ist.add(new int[] {e.start(), e.end()});
            }
            assertEquals(erwarteteStuecke.size(), ist.size(), "Zahl der Funde bei " + f.id());
            for (int i = 0; i < ist.size(); i++) {
                assertTrue(ist.get(i)[0] == erwarteteStuecke.get(i)[0] && ist.get(i)[1] == erwarteteStuecke.get(i)[1],
                        "Fund ist nicht das Reststueck des Wertbereichs bei " + f.id());
            }
        }
    }

    @Test
    void struktur_jedesKontextwortUndJederVerbinderUndJedesZwischenwortKommtVor() {
        List<String> texte = new ArrayList<>();
        faelle().forEach(f -> texte.add(f.text()));
        List<String> kontexte = new ArrayList<>(List.of("Passwort", "passwort", "KENNWORT", "Kennwort", "password",
                "Password", "passwd", "Passphrase", "Initialpasswort", "Einmalkennwort", "Adminpasswort",
                "Root-Passwort", "db-passwort", "PW", "pw", "Pwd", "DB_PASSWORD", "dbPassword", "pwd", "Token",
                "access_token", "accessToken", "refresh_token", "idToken", "API-Key", "apikey", "api_key",
                "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret", "Bearer", "Authorization"));
        for (String kontext : kontexte) {
            boolean vorhanden = texte.stream().anyMatch(t -> t.contains(kontext + ": ") || t.contains(kontext + "=")
                    || t.contains(kontext + " "));
            assertTrue(vorhanden, "Kontextwort kommt nicht vor, Index " + kontexte.indexOf(kontext));
        }
        for (String zwischen : List.of("f" + UE + "r", "for", "von", "vom", "des", "der", "zu", "zum", "zur", "of",
                "to")) {
            assertTrue(texte.stream().anyMatch(t -> t.contains(" " + zwischen + " ")),
                    "Zwischenwort kommt nicht vor: " + zwischen);
        }
        Map<String, String> verbinder = new LinkedHashMap<>();
        verbinder.put(": ", GeheimnisPositivKorpus.K1);
        verbinder.put(" : ", GeheimnisPositivKorpus.K1);
        verbinder.put(" = ", GeheimnisPositivKorpus.K1);
        verbinder.put("=", GeheimnisPositivKorpus.K1);
        verbinder.put(":", GeheimnisPositivKorpus.K1);
        verbinder.put(" ist ", GeheimnisPositivKorpus.K2);
        verbinder.put(" lautet ", GeheimnisPositivKorpus.K2);
        verbinder.put(" is ", GeheimnisPositivKorpus.K2);
        verbinder.put("\t", GeheimnisPositivKorpus.K3);
        verbinder.put("| ", GeheimnisPositivKorpus.K3);
        verbinder.put("<password>", GeheimnisPositivKorpus.K4);
        verbinder.put(":\n", GeheimnisPositivKorpus.UMBRUCH);
        verbinder.put(":\r\n", GeheimnisPositivKorpus.UMBRUCH);
        verbinder.put(" =\n", GeheimnisPositivKorpus.UMBRUCH);
        verbinder.put(":\r", GeheimnisPositivKorpus.UMBRUCH);
        verbinder.put("\"", GeheimnisPositivKorpus.ANFUEHRUNG);
        verbinder.put("'", GeheimnisPositivKorpus.ANFUEHRUNG);
        verbinder.put("`", GeheimnisPositivKorpus.ANFUEHRUNG);
        verbinder.put(String.valueOf((char) 0x201E), GeheimnisPositivKorpus.ANFUEHRUNG);
        int i = 0;
        for (Map.Entry<String, String> e : verbinder.entrySet()) {
            boolean vorhanden = faelle().stream().anyMatch(f -> f.kategorie().equals(e.getValue())
                    && f.text().contains(e.getKey()));
            assertTrue(vorhanden, "Verbinder kommt in " + e.getValue() + " nicht vor, Index " + i);
            i++;
        }
        assertTrue(faelle().stream().anyMatch(f -> f.text().contains(":\r") && !f.text().contains("\r\n")),
                "Zeilenumbruch nur mit CR kommt nicht vor");
    }

    @Test
    void struktur_werteStammenAusDerZentralenKlasse() {
        Set<String> passwoerter = Set.of(SynthetischeGeheimnisse.P1, SynthetischeGeheimnisse.P1_OHNE_AUSRUF,
                SynthetischeGeheimnisse.P1 + "!", SynthetischeGeheimnisse.P2, SynthetischeGeheimnisse.P3,
                SynthetischeGeheimnisse.P6, SynthetischeGeheimnisse.P7, SynthetischeGeheimnisse.P_DOLLAR,
                SynthetischeGeheimnisse.P_HUNTER, SynthetischeGeheimnisse.P_GEHEIM,
                SynthetischeGeheimnisse.P_MIT_LEERZEICHEN, SynthetischeGeheimnisse.P_LEERZEICHEN_OHNE,
                SynthetischeGeheimnisse.P_SEMIKOLON, SynthetischeGeheimnisse.wert4096(), "!", "!!");
        for (Fall f : faelle()) {
            for (Finding g : f.geheimnisFunde()) {
                String wert = g.originalValue();
                if (g.type().name().equals("PASSWORT")) {
                    assertTrue(passwoerter.contains(wert), "Passwort-Wert nicht aus der zentralen Klasse: " + f.id());
                } else {
                    boolean ok = wert.contains(SynthetischeGeheimnisse.MARKE)
                            || wert.equals(SynthetischeGeheimnisse.uuid())
                            || g.source().equals(GeheimnisPositivKorpus.QUELLE_PEM);
                    assertTrue(ok, "Token-Wert ohne Marke: " + f.id());
                }
            }
        }
    }

    // ------------------------------------------------------------------ Prueflogik fuer aktive Kategorien

    @Test
    void aktiveKategorien_standardLiefertGenauDieErwartetenFunde() {
        List<Fall> aktiv = new ArrayList<>();
        for (Fall f : faelle()) {
            if (AKTIVE_KATEGORIEN.contains(f.kategorie())) {
                aktiv.add(f);
            }
        }
        assertEquals(List.of(), abweichungen(STANDARD::scan, aktiv), "Abweichungen in aktiven Kategorien");
    }

    // ------------------------------------------------------------------ Gegenproben des Positivkorpus

    /** Derselbe Fund mit einem anderen Bereich: um eins nach rechts, am Textende nach links, sonst um eins gekuerzt. */
    private static Finding verschoben(Finding f, String text) {
        int start = f.start();
        int end = f.end();
        if (end < text.length()) {
            start++;
            end++;
        } else if (start > 0) {
            start--;
            end--;
        } else {
            end--;
        }
        return new Finding(f.type(), text.substring(start, end), start, end, f.confidence(), f.source());
    }

    private static Function<String, List<Finding>> kuenstlich(Function<Finding, Finding> aenderung,
                                                              boolean mitAlt, boolean mitGeheimnis) {
        Map<String, Fall> nachText = nachText();
        return text -> {
            Fall f = nachText.get(text);
            List<Finding> alle = new ArrayList<>();
            if (mitAlt) {
                alle.addAll(ALT.scan(text));
            }
            if (mitGeheimnis) {
                for (Finding g : f.geheimnisFunde()) {
                    alle.add(aenderung.apply(g));
                }
            }
            alle.sort(Comparator.comparingInt(Finding::start));
            return alle;
        };
    }

    private static long faelleMitGeheimnisFund() {
        return faelle().stream().filter(f -> !f.funde().isEmpty()).count();
    }

    @Test
    void gegenprobe_einKuenstlicherScannerMitGenauDenErwartungenBesteht() {
        assertEquals(List.of(), abweichungen(kuenstlich(g -> g, true, true), faelle()));
    }

    @Test
    void gegenprobe_verschobenerBereichFaelltBeiJedemFallMitGeheimnisFundDurch() {
        Map<String, Fall> nachText = nachText();
        Function<String, List<Finding>> scanner = text -> {
            Fall f = nachText.get(text);
            List<Finding> alle = new ArrayList<>(ALT.scan(text));
            f.geheimnisFunde().forEach(g -> alle.add(verschoben(g, text)));
            alle.sort(Comparator.comparingInt(Finding::start));
            return alle;
        };
        assertEquals(faelleMitGeheimnisFund(), abweichungen(scanner, faelle()).size());
    }

    @Test
    void gegenprobe_andereQuelleOderKonfidenzFaelltBeiJedemFallMitGeheimnisFundDurch() {
        Function<String, List<Finding>> andereQuelle = kuenstlich(g -> new Finding(g.type(), g.originalValue(),
                g.start(), g.end(), g.confidence(), "kuenstlich"), true, true);
        Function<String, List<Finding>> andereKonfidenz = kuenstlich(g -> new Finding(g.type(), g.originalValue(),
                g.start(), g.end(), g.confidence() == 0.95 ? 0.80 : 0.95, g.source()), true, true);
        Function<String, List<Finding>> andererTyp = kuenstlich(g -> new Finding(
                new FindingType(g.type().name().equals("TOKEN") ? "PASSWORT" : "TOKEN"),
                g.originalValue(), g.start(), g.end(), g.confidence(), g.source()), true, true);
        assertEquals(faelleMitGeheimnisFund(), abweichungen(andereQuelle, faelle()).size());
        assertEquals(faelleMitGeheimnisFund(), abweichungen(andereKonfidenz, faelle()).size());
        assertEquals(faelleMitGeheimnisFund(), abweichungen(andererTyp, faelle()).size());
    }

    @Test
    void gegenprobe_keinGeheimnisFundOderKeinBestandsfundFaelltDurch() {
        long mitAlt = faelle().stream().filter(f -> !ALT.scan(f.text()).isEmpty()).count();
        assertTrue(mitAlt > 0, "kein Fall hat einen Bestandsfund");
        assertEquals(faelleMitGeheimnisFund(), abweichungen(kuenstlich(g -> g, true, false), faelle()).size());
        assertEquals(mitAlt, abweichungen(kuenstlich(g -> g, false, true), faelle()).size());
    }

    // ------------------------------------------------------------------ Generator: gueltige Werte (AK-38)

    private static GeheimnisPositivGenerator.Lauf lauf;

    private static synchronized GeheimnisPositivGenerator.Lauf lauf() {
        if (lauf == null) {
            lauf = GeheimnisPositivGenerator.lauf();
        }
        return lauf;
    }

    @Test
    void generator_selbsttest_istGueltig_erkenntVerletzungenUndGueltigeWerte() {
        for (String gueltig : List.of(SynthetischeGeheimnisse.P1, SynthetischeGeheimnisse.P2,
                SynthetischeGeheimnisse.P3, SynthetischeGeheimnisse.P6, SynthetischeGeheimnisse.P7,
                SynthetischeGeheimnisse.P_DOLLAR, SynthetischeGeheimnisse.P1_OHNE_AUSRUF)) {
            assertTrue(GeheimnisPositivGenerator.istGueltig(gueltig), "gueltiger Wert abgelehnt: " + gueltig.length());
        }
        List<String> ungueltig = List.of(SynthetischeGeheimnisse.P5, "Sommer!", "Sonnenschein", "123456", "xxxxxxxx",
                "CHG0012345", "INC-0004711", "8-stellig", "90-t" + (char) 0xE4 + "gig", "SHA-256", "AES-256", "TLS1.2",
                "v4.2.1", "4.2.1", "Windows11", "$abc12xyz", "{abc12x}", "[abc12x]", "<abc12x>", "/abc12x", "~abc12x",
                "abc 12x", "abc12x.", "abc12x,", "abc12x?", "abc12x)", SynthetischeGeheimnisse.wert4097(),
                "http://abc12x");
        for (int i = 0; i < ungueltig.size(); i++) {
            assertFalse(GeheimnisPositivGenerator.istGueltig(ungueltig.get(i)), "ungueltiger Wert akzeptiert: " + i);
        }
    }

    @Test
    void generator_kennzahlen_hunderttausendTexte_jedeLaengeVon6Bis40() {
        GeheimnisPositivGenerator.Lauf l = lauf();
        assertEquals(GeheimnisPositivGenerator.ANZAHL, l.texte().size());
        assertEquals(100_000, l.texte().size());
        assertEquals(l.texte().size(), l.passwort() + l.token());
        for (int laenge = GeheimnisPositivGenerator.MIN_LAENGE; laenge <= GeheimnisPositivGenerator.MAX_LAENGE;
                laenge++) {
            assertTrue(l.jeLaenge()[laenge] >= 1000, "Laenge selten: " + laenge + ": " + l.jeLaenge()[laenge]);
        }
        assertTrue(l.kurz() > 0, "keine kurzen Werte");
        String ist = "verworfenAlt=" + l.verworfenAlt() + ",verworfenForm=" + l.verworfenForm() + ",passwort="
                + l.passwort() + ",token=" + l.token() + ",kurz=" + l.kurz();
        assertEquals(GENERATOR_KENNZAHLEN_IST, ist, "Kennzahlen des Positiv-Generators");
    }

    @Test
    void generator_jederText_hatDenWertAmErwartetenOrt_istGueltig_undAltLiefertKeinenFund() {
        GeheimnisPositivGenerator.Lauf l = lauf();
        for (int i = 0; i < l.texte().size(); i++) {
            Text t = l.texte().get(i);
            String wert = t.text().substring(t.start(), t.end());
            assertTrue(GeheimnisPositivGenerator.istGueltig(wert), "ungueltiger Wert, Text " + i);
            assertEquals(wert.length() >= 8 ? 0.95 : 0.80, t.konfidenz(), "Konfidenz, Text " + i);
            assertEquals(t.typ().name().equals("TOKEN") ? "token-kontext" : "passwort-kontext", t.quelle());
            if (i < 3000) {
                assertTrue(ALT.scan(t.text()).isEmpty(), "ALT liefert einen Fund, Text " + i);
            }
        }
    }

    @Test
    void generator_istDeterministisch_ersteTexteSindPraefixDesVollenLaufs() {
        GeheimnisPositivGenerator.Lauf kurz = GeheimnisPositivGenerator.lauf(GeheimnisPositivGenerator.SEED, 2000);
        assertTrue(kurz.texte().equals(lauf().texte().subList(0, 2000)), "Praefix des vollen Laufs, 2000 Texte");
        GeheimnisPositivGenerator.Lauf anders = GeheimnisPositivGenerator.lauf(GeheimnisPositivGenerator.SEED + 1,
                2000);
        assertFalse(kurz.texte().equals(anders.texte()), "anderer Seed muss andere Texte liefern");
        assertTrue(GeheimnisPositivGenerator.verletzend(1500).texte().equals(
                GeheimnisPositivGenerator.verletzend().texte().subList(0, 1500)),
                "Praefix des vollen verletzenden Laufs, 1500 Texte");
    }

    @Test
    void generator_standardLiefertGenauDenErwartetenFund_inDerErfasstenZahlVonFaellen() {
        GeheimnisPositivGenerator.Lauf l = lauf();
        int passwort = 0;
        int token = 0;
        for (Text t : l.texte()) {
            List<Finding> funde = STANDARD.scan(t.text());
            if (funde.equals(List.of(t.erwartet()))) {
                if (t.typ().name().equals("TOKEN")) {
                    token++;
                } else {
                    passwort++;
                }
            }
        }
        // AK-38: 100 Prozent oder noch nichts freigeschaltet (0), nie ein Zwischenwert
        assertTrue(passwort == 0 || passwort == l.passwort(), "PASSWORT-Treffer weder 0 noch alle: " + passwort);
        assertTrue(token == 0 || token == l.token(), "TOKEN-Treffer weder 0 noch alle: " + token);
        assertTrue(GENERATOR_PASSWORT_TREFFER_IST == 0 || GENERATOR_PASSWORT_TREFFER_IST == l.passwort(),
                "Istwert PASSWORT-Treffer weder 0 noch alle");
        assertTrue(GENERATOR_TOKEN_TREFFER_IST == 0 || GENERATOR_TOKEN_TREFFER_IST == l.token(),
                "Istwert TOKEN-Treffer weder 0 noch alle");
        assertEquals(GENERATOR_PASSWORT_TREFFER_IST, passwort, "Treffer PASSWORT im Positiv-Generator");
        assertEquals(GENERATOR_TOKEN_TREFFER_IST, token, "Treffer TOKEN im Positiv-Generator");
    }

    @Test
    void gegenprobe_generator_einKuenstlicherErkennerMitGenauDenErwartungenTrifftHundertProzent() {
        List<Text> texte = lauf().texte().subList(0, GEGENPROBE_TEXTE);
        Map<String, List<Finding>> vorgegeben = new HashMap<>();
        for (Text t : texte) {
            vorgegeben.put(t.text(), List.of(t.erwartet()));
        }
        TextScanner scanner = new TextScanner(List.of(GeheimnisNegativGenerator.liefertVorgegebene(vorgegeben)));
        int treffer = 0;
        for (Text t : texte) {
            if (scanner.scan(t.text()).equals(List.of(t.erwartet()))) {
                treffer++;
            }
        }
        assertEquals(GEGENPROBE_TEXTE, treffer, "Treffer des kuenstlichen Erkenners");
    }

    @Test
    void gegenprobe_generator_einKuenstlicherErkennerMitVerschobenemBereichTrifftNie() {
        List<Text> texte = lauf().texte().subList(0, GEGENPROBE_TEXTE);
        Map<String, List<Finding>> vorgegeben = new HashMap<>();
        for (Text t : texte) {
            vorgegeben.put(t.text(), List.of(verschoben(t.erwartet(), t.text())));
        }
        TextScanner scanner = new TextScanner(List.of(GeheimnisNegativGenerator.liefertVorgegebene(vorgegeben)));
        for (Text t : texte) {
            assertFalse(scanner.scan(t.text()).equals(List.of(t.erwartet())), "verschobener Fund gilt als Treffer");
        }
    }

    // ------------------------------------------------------------------ Generator: verletzende Werte (AK-38)

    @Test
    void generator_verletzend_hunderttausendTexte_keinPasswortUndKeinTokenFund() {
        GeheimnisPositivGenerator.Verletzend v = GeheimnisPositivGenerator.verletzend();
        assertEquals(100_000, v.texte().size());
        StringBuilder ist = new StringBuilder();
        for (int i = 0; i < v.jeArt().length; i++) {
            assertTrue(v.jeArt()[i] >= 20_000, "Art selten, Index " + i + ": " + v.jeArt()[i]);
            ist.append(i == 0 ? "" : ",").append(GeheimnisPositivGenerator.Verletzung.values()[i]).append('=')
                    .append(v.jeArt()[i]);
        }
        assertEquals(VERLETZEND_KENNZAHLEN_IST, ist.toString(), "Kennzahlen der verletzenden Texte");
        List<TextScanner> scanner = GeheimnisFehlalarmTest.pruefScanner();
        int funde = 0;
        int erster = -1;
        for (int i = 0; i < v.texte().size(); i++) {
            for (TextScanner s : scanner) {
                for (Finding f : s.scan(v.texte().get(i))) {
                    if (f.type().name().equals("PASSWORT") || f.type().name().equals("TOKEN")) {
                        funde++;
                        erster = erster < 0 ? i : erster;
                    }
                }
            }
        }
        assertEquals(0, funde, "Funde bei verletzenden Werten, erster Text: " + erster);
    }

    @Test
    void gegenprobe_verletzend_einErkennerOhneWertTestTrifftFastJedenText() {
        List<String> texte = GeheimnisPositivGenerator.verletzend(2000).texte();
        TextScanner scanner = new TextScanner(List.of(GeheimnisNegativGenerator.ersterWortNachK1()));
        int treffer = 0;
        for (String text : texte) {
            if (!scanner.scan(text).isEmpty()) {
                treffer++;
            }
        }
        assertTrue(10 * treffer >= 9 * texte.size(), "Erkenner ohne Wert-Test trifft weniger als 90 Prozent: "
                + treffer);
    }

    // ------------------------------------------------------------------ Siegel

    private static String serialisiere() {
        StringBuilder sb = new StringBuilder();
        for (Fall f : faelle()) {
            sb.append(f.id()).append('|').append(f.kategorie()).append('|').append(f.konflikt()).append('|')
                    .append(f.text());
            for (Erwartung e : f.funde()) {
                sb.append('|').append(e.typ().name()).append(',').append(e.start()).append(',').append(e.end())
                        .append(',').append(e.konfidenz()).append(',').append(e.quelle());
            }
            for (int[] g : f.geschuetzt()) {
                sb.append('|').append(g[0]).append('-').append(g[1]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void siegel_positivkorpusUndBeideGeneratorenSindEingefroren() {
        assertEquals(SIEGEL_POSITIVKORPUS, GeheimnisNegativGenerator.siegel(serialisiere()), "Siegel Positivkorpus");
        List<String> gueltig = new ArrayList<>();
        for (Text t : lauf().texte().subList(0, SIEGEL_TEXTE)) {
            gueltig.add(t.text());
        }
        assertEquals(SIEGEL_POSITIV_GENERATOR, GeheimnisNegativGenerator.siegelUeberTexte(gueltig, SIEGEL_TEXTE),
                "Siegel der ersten 1000 gueltigen Texte");
        assertEquals(SIEGEL_VERLETZEND_GENERATOR, GeheimnisNegativGenerator.siegelUeberTexte(
                GeheimnisPositivGenerator.verletzend(SIEGEL_TEXTE).texte(), SIEGEL_TEXTE),
                "Siegel der ersten 1000 verletzenden Texte");
    }
}
