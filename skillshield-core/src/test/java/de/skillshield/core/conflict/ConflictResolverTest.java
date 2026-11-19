package de.skillshield.core.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConflictResolverTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";

    /** Text aus Punkten; der Originalwert eines Fundes ist ein Teilstring davon. */
    private static final String TEXT = ".".repeat(200);

    private static final FindingType PERSON = new FindingType("PERSON");

    private static Finding f(String text, FindingType type, int start, int end, double confidence, String source) {
        return new Finding(type, text.substring(start, end), start, end, confidence, source);
    }

    private static Finding f(FindingType type, int start, int end, double confidence, String source) {
        return f(TEXT, type, start, end, confidence, source);
    }

    private static Finding f(FindingType type, int start, int end, double confidence) {
        return f(type, start, end, confidence, "stub");
    }

    private static List<Finding> resolve(Finding... kandidaten) {
        return ConflictResolver.resolve(TEXT, List.of(kandidaten));
    }

    private static void assertMerged(Finding actual, FindingType type, int start, int end, double confidence) {
        assertEquals(type, actual.type());
        assertEquals(start, actual.start());
        assertEquals(end, actual.end());
        assertEquals(TEXT.substring(start, end), actual.originalValue());
        assertEquals(confidence, actual.confidence());
        assertEquals(Finding.SOURCE_MERGED, actual.source());
    }

    // ---------- Enthaltensein ----------

    @Test
    void e1EnthaltenseinGehtVorKonfidenz() {
        Finding pfad = f(FindingType.PFAD, 0, 20, 0.80, "pfad-laufwerk");
        Finding email = f(FindingType.EMAIL, 5, 10, 1.0, "email");
        assertEquals(List.of(pfad), resolve(email, pfad));
        assertEquals(List.of(pfad), resolve(pfad, email));
        Finding r = resolve(email, pfad).get(0);
        assertEquals(0.80, r.confidence());
        assertEquals("pfad-laufwerk", r.source());
        assertNotEquals(Finding.SOURCE_MERGED, r.source());
    }

    @Test
    void e2StufeDesUmschliessendenBleibtErhalten() {
        Finding aussen = f(FindingType.URL, 0, 30, 0.95, "url");
        Finding innen = f(PERSON, 10, 20, 0.30, "stub");
        List<Finding> r = resolve(innen, aussen);
        assertEquals(List.of(aussen), r);
        assertEquals(ConfidenceLevel.SICHER, r.get(0).level());
    }

    @Test
    void e3RandfaelleDerSortierung() {
        Finding aussen = f(FindingType.URL, 0, 20, 0.3, "url");
        Finding gleicherStart = f(PERSON, 0, 10, 0.9, "stub");
        Finding gleichesEnde = f(PERSON, 10, 20, 0.9, "stub");
        assertEquals(List.of(aussen), resolve(gleicherStart, aussen));
        assertEquals(List.of(aussen), resolve(aussen, gleicherStart));
        assertEquals(List.of(aussen), resolve(gleichesEnde, aussen));
        assertEquals(List.of(aussen), resolve(aussen, gleichesEnde));
        assertEquals(List.of(aussen), resolve(gleichesEnde, gleicherStart, aussen));
    }

    @Test
    void e4DreifachVerschachteltErgibtNurDenAeussersten() {
        Finding aussen = f(FindingType.PFAD, 0, 30, 0.4, "a");
        Finding mitte = f(FindingType.URL, 5, 25, 0.9, "b");
        Finding innen = f(FindingType.EMAIL, 10, 20, 1.0, "c");
        assertEquals(List.of(aussen), resolve(innen, mitte, aussen));
        assertEquals(List.of(aussen), resolve(aussen, mitte, innen));
    }

    // ---------- gleicher Bereich ----------

    @Test
    void g1KonfidenzGehtVorTyprang() {
        Finding pfad = f(FindingType.PFAD, 0, 10, 0.95, "x");
        Finding url = f(FindingType.URL, 0, 10, 0.80, "x");
        assertEquals(List.of(pfad), resolve(url, pfad));
        assertEquals(List.of(pfad), resolve(pfad, url));
    }

    @Test
    void g2TypRangBeiGleicherKonfidenzAuchInUmgekehrterReihenfolge() {
        Finding pfad = f(FindingType.PFAD, 0, 10, 0.95, "x");
        Finding url = f(FindingType.URL, 0, 10, 0.95, "x");
        assertEquals(List.of(url), resolve(pfad, url));
        assertEquals(List.of(url), resolve(url, pfad));
    }

    @Test
    void g2TypRangFolgeUrlPfadEmailIpUebrige() {
        Finding url = f(FindingType.URL, 5, 15, 0.9, "x");
        Finding pfad = f(FindingType.PFAD, 5, 15, 0.9, "x");
        Finding email = f(FindingType.EMAIL, 5, 15, 0.9, "x");
        Finding ip = f(FindingType.IP, 5, 15, 0.9, "x");
        Finding sonst = f(PERSON, 5, 15, 0.9, "x");
        List<Finding> alle = new ArrayList<>(List.of(sonst, ip, email, pfad, url));
        for (Finding erwartet : List.of(url, pfad, email, ip, sonst)) {
            assertEquals(List.of(erwartet), ConflictResolver.resolve(TEXT, alle));
            Collections.reverse(alle);
            assertEquals(List.of(erwartet), ConflictResolver.resolve(TEXT, alle));
            alle.remove(erwartet);
        }
    }

    @Test
    void typRangGehtVorTypnameUndTypnameVorQuelle() {
        // Typrang vor Typname: "AAA" waere alphabetisch zuerst, IP hat aber den besseren Rang.
        Finding ip = f(FindingType.IP, 5, 15, 0.9, "x");
        Finding aaa = f(new FindingType("AAA"), 5, 15, 0.9, "x");
        assertEquals(List.of(ip), resolve(aaa, ip));
        // Typrang vor Quelle: Quelle "aaa" waere alphabetisch zuerst, URL hat aber den besseren Rang.
        Finding url = f(FindingType.URL, 5, 15, 0.9, "zzz");
        Finding pfad = f(FindingType.PFAD, 5, 15, 0.9, "aaa");
        assertEquals(List.of(url), resolve(pfad, url));
        // Typname vor Quelle
        Finding alpha = f(new FindingType("ALPHA"), 5, 15, 0.9, "zzz");
        Finding beta = f(new FindingType("BETA"), 5, 15, 0.9, "aaa");
        assertEquals(List.of(alpha), resolve(beta, alpha));
        assertEquals(List.of(alpha), resolve(alpha, beta));
    }

    @Test
    void quelleEntscheidetBeiGleichemTypUndGleicherKonfidenz() {
        Finding a = f(FindingType.IP, 5, 15, 0.9, "aaa");
        Finding b = f(FindingType.IP, 5, 15, 0.9, "bbb");
        assertEquals(List.of(a), resolve(b, a));
        assertEquals(List.of(a), resolve(a, b));
    }

    @Test
    void gleichwertigeKandidatenSindAustauschbarUndErgebnisIstEqualsGleich() {
        Finding a = f(FindingType.IP, 3, 9, 0.8, "ipv4");
        Finding b = f(FindingType.IP, 3, 9, 0.8, "ipv4");
        Finding rand = f(FindingType.IP, 8, 14, 0.8, "ipv4");
        assertEquals(resolve(a, b, rand), resolve(rand, b, a));
        assertEquals(List.of(a), resolve(b, a));
    }

    @Test
    void g3VollstaendigGleicheDuplikateErgebenEinenFund() {
        Finding a = f(FindingType.IP, 3, 9, 0.8, "ipv4");
        assertEquals(List.of(a), resolve(a, a, a));
        assertEquals(List.of(a), resolve(a, f(FindingType.IP, 3, 9, 0.8, "ipv4")));
    }

    // ---------- Zusammenlegung und Ketten ----------

    @Test
    void z1TeilueberlappungWirdZuEinemFundZusammengelegt() {
        String text = "0123456789abcdefghij";
        List<Finding> r = ConflictResolver.resolve(text, List.of(
                f(text, FindingType.IP, 0, 10, 0.9, "stub"), f(text, FindingType.IP, 5, 15, 0.9, "stub")));
        assertEquals(1, r.size());
        assertEquals("0123456789abcde", r.get(0).originalValue());
        assertEquals(0, r.get(0).start());
        assertEquals(15, r.get(0).end());
        assertEquals("zusammengelegt", r.get(0).source());
    }

    @Test
    void z2KetteAusDreiKandidatenErgibtEinenFundInJederReihenfolge() {
        Finding a = f(PERSON, 0, 10, 0.9);
        Finding b = f(PERSON, 5, 15, 0.9);
        Finding c = f(PERSON, 12, 20, 0.9);
        List<List<Finding>> eingaben = List.of(List.of(a, b, c), List.of(c, b, a), List.of(b, c, a),
                List.of(c, a, b));
        for (List<Finding> eingabe : eingaben) {
            List<Finding> r = ConflictResolver.resolve(TEXT, eingabe);
            assertEquals(1, r.size());
            assertMerged(r.get(0), PERSON, 0, 20, 0.9);
        }
    }

    @Test
    void z3AngrenzendeKandidatenBleibenGetrenntLueckeBleibtUnbedeckt() {
        Finding a = f(FindingType.IP, 0, 5, 0.5);
        Finding b = f(FindingType.URL, 5, 10, 0.9);
        assertEquals(List.of(a, b), resolve(b, a));
        Finding c = f(FindingType.URL, 6, 10, 0.9);
        List<Finding> r = resolve(c, a);
        assertEquals(List.of(a, c), r);
        assertTrue(r.stream().noneMatch(x -> x.start() <= 5 && 5 < x.end()), "Position 5 in keinem Fund");
    }

    @Test
    void z4ZweiGetrennteKettenErgebenZweiZusammengelegteFunde() {
        List<Finding> r = resolve(f(PERSON, 40, 50, 0.9), f(PERSON, 0, 10, 0.9), f(PERSON, 45, 60, 0.9),
                f(PERSON, 8, 20, 0.9));
        assertEquals(2, r.size());
        assertMerged(r.get(0), PERSON, 0, 20, 0.9);
        assertMerged(r.get(1), PERSON, 40, 60, 0.9);
    }

    @Test
    void z5GliedNurInDerVereinigungIstBeteiligt() {
        Finding a = f(PERSON, 0, 10, 0.95);
        Finding b = f(PERSON, 5, 15, 0.95);
        Finding c = f(PERSON, 8, 20, 0.95);
        List<Finding> r = resolve(c, a, b);
        assertEquals(1, r.size());
        assertMerged(r.get(0), PERSON, 0, 20, 0.95);
    }

    @Test
    void gemischteKetteEnthaltenseinUndTeilueberlappung() {
        Finding a = f(FindingType.URL, 0, 20, 0.9);
        Finding b = f(PERSON, 5, 10, 1.0);
        Finding c = f(FindingType.PFAD, 15, 30, 0.8);
        List<Finding> r = resolve(b, c, a);
        assertEquals(1, r.size());
        assertMerged(r.get(0), FindingType.URL, 0, 30, 0.8);
    }

    // ---------- Typ: nur aus maximalen Gliedern ----------

    @Test
    void typKommtVomKonfidenzstaerkstenMaximalenGlied() {
        Finding ip = f(FindingType.IP, 0, 12, 0.80);
        Finding email = f(FindingType.EMAIL, 8, 14, 0.95);
        assertMerged(resolve(ip, email).get(0), FindingType.EMAIL, 0, 14, 0.80);
        assertMerged(resolve(email, ip).get(0), FindingType.EMAIL, 0, 14, 0.80);
    }

    @Test
    void typBeiGleicherKonfidenzKommtVomLaengstenMaximalenGlied() {
        Finding url = f(FindingType.URL, 0, 6, 0.90);
        Finding pfad = f(FindingType.PFAD, 3, 15, 0.90);
        assertMerged(resolve(url, pfad).get(0), FindingType.PFAD, 0, 15, 0.90);
        assertMerged(resolve(pfad, url).get(0), FindingType.PFAD, 0, 15, 0.90);
    }

    @Test
    void typBeiGleicherKonfidenzUndLaengeKommtVomFruehestenStart() {
        Finding ip = f(FindingType.IP, 0, 8, 0.90);
        Finding url = f(FindingType.URL, 4, 12, 0.90);
        assertMerged(resolve(ip, url).get(0), FindingType.IP, 0, 12, 0.90);
        assertMerged(resolve(url, ip).get(0), FindingType.IP, 0, 12, 0.90);
    }

    @Test
    void enthaltenerKandidatLiefertKeinenTypDerVereinigung() {
        Finding url = f(FindingType.URL, 0, 10, 0.90);
        Finding email = f(FindingType.EMAIL, 2, 6, 1.0);
        Finding pfad = f(FindingType.PFAD, 8, 16, 0.80);
        List<Finding> r = resolve(email, pfad, url);
        assertEquals(1, r.size());
        assertMerged(r.get(0), FindingType.URL, 0, 16, 0.80);
    }

    // ---------- Konfidenz und Stufe ----------

    @Test
    void k1SchwaechstesGliedBestimmtKonfidenzNichtTyp() {
        Finding pfad = f(FindingType.PFAD, 0, 10, 0.95);
        Finding ip = f(FindingType.IP, 5, 12, 0.40);
        Finding r = resolve(ip, pfad).get(0);
        assertMerged(r, FindingType.PFAD, 0, 12, 0.40);
        assertEquals(ConfidenceLevel.FRAGLICH, r.level());
    }

    @Test
    void k2EnthaltenerKandidatZaehltFuerDieStufeNicht() {
        Finding url = f(FindingType.URL, 0, 10, 0.95);
        Finding innen = f(PERSON, 2, 4, 0.30);
        Finding pfad = f(FindingType.PFAD, 8, 15, 0.85);
        Finding r = resolve(innen, pfad, url).get(0);
        assertMerged(r, FindingType.URL, 0, 15, 0.85);
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, r.level());
    }

    @Test
    void k3GliedNurInDerVereinigungZaehltFuerDieKonfidenz() {
        Finding a = f(PERSON, 0, 10, 0.95);
        Finding b = f(PERSON, 5, 15, 0.50);
        Finding c = f(PERSON, 8, 20, 0.95);
        assertMerged(resolve(a, b, c).get(0), PERSON, 0, 20, 0.50);
        assertMerged(resolve(c, b, a).get(0), PERSON, 0, 20, 0.50);
    }

    @Test
    void k4AlleGliederSicherErgebenStufeSicher() {
        Finding r = resolve(f(FindingType.URL, 0, 10, 0.95), f(FindingType.PFAD, 5, 15, 1.0)).get(0);
        assertEquals(ConfidenceLevel.SICHER, r.level());
        assertEquals(Finding.SOURCE_MERGED, r.source());
        assertEquals(0.95, r.confidence());
    }

    // ---------- Beispiel AK-7 (g) mit Stubs ----------

    @Test
    void beispielAk7gWirdZuEinemPfadFundZusammengelegt() {
        String text = "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende";
        int urlStart = text.indexOf("https://");
        int urlEnd = text.indexOf("Akte") + "Akte".length();
        int pfadStart = text.indexOf("C:/Akte");
        int pfadEnd = text.indexOf(".pdf") + ".pdf".length();
        int mailStart = text.indexOf("erika.beispiel");
        int mailEnd = text.indexOf("example.invalid") + "example.invalid".length();
        assertEquals(5, urlStart);
        assertEquals(41, urlEnd);
        assertEquals(34, pfadStart);
        assertEquals(83, pfadEnd);
        assertEquals(47, mailStart);
        assertEquals(77, mailEnd);
        List<Finding> kandidaten = List.of(
                f(text, FindingType.URL, urlStart, urlEnd, 0.95, "url"),
                f(text, FindingType.PFAD, pfadStart, pfadEnd, 0.95, "pfad-laufwerk"),
                f(text, FindingType.EMAIL, mailStart, mailEnd, 0.95, "email"));
        List<Finding> r = ConflictResolver.resolve(text, kandidaten);
        assertEquals(1, r.size());
        Finding fund = r.get(0);
        assertEquals(FindingType.PFAD, fund.type());
        assertEquals(5, fund.start());
        assertEquals(83, fund.end());
        assertEquals(text.substring(5, 83), fund.originalValue());
        assertEquals(0.95, fund.confidence());
        assertEquals(Finding.SOURCE_MERGED, fund.source());
        assertEquals('\'', text.charAt(83));
        for (int p = 5; p < 83; p++) {
            assertTrue(fund.start() <= p && p < fund.end(), "Position " + p + " bedeckt");
        }
    }

    // ---------- Eingaben und Fehler ----------

    @Test
    void ergebnisIstUnveraenderlichUndNachStartSortiert() {
        List<Finding> r = resolve(f(PERSON, 50, 60, 0.9), f(PERSON, 0, 10, 0.9), f(PERSON, 20, 30, 0.9));
        assertEquals(List.of(f(PERSON, 0, 10, 0.9), f(PERSON, 20, 30, 0.9), f(PERSON, 50, 60, 0.9)), r);
        assertThrows(UnsupportedOperationException.class, () -> r.add(f(PERSON, 70, 80, 0.5)));
        assertThrows(UnsupportedOperationException.class, () -> r.remove(0));
    }

    @Test
    void eingabeListeBleibtUnveraendertUndZweiAufrufeSindGleich() {
        List<Finding> eingabe = new ArrayList<>(List.of(f(PERSON, 10, 20, 0.5), f(PERSON, 0, 30, 0.5),
                f(PERSON, 5, 8, 0.9), f(PERSON, 25, 40, 0.7)));
        List<Finding> kopie = List.copyOf(eingabe);
        List<Finding> erster = ConflictResolver.resolve(TEXT, eingabe);
        assertEquals(kopie, eingabe);
        assertEquals(erster, ConflictResolver.resolve(TEXT, eingabe));
    }

    @Test
    void alleVierundzwanzigPermutationenEinerKetteMitEnthaltenseinGebenDasGleicheErgebnis() {
        List<Finding> basis = List.of(f(FindingType.URL, 0, 20, 0.9), f(PERSON, 5, 10, 1.0),
                f(FindingType.PFAD, 15, 30, 0.8), f(FindingType.IP, 28, 35, 0.6));
        List<Finding> erwartet = ConflictResolver.resolve(TEXT, basis);
        assertEquals(1, erwartet.size());
        assertMerged(erwartet.get(0), FindingType.URL, 0, 35, 0.6);
        int[] perm = {0, 1, 2, 3};
        int zaehler = permutationen(basis, perm, 0, erwartet);
        assertEquals(24, zaehler);
    }

    private static int permutationen(List<Finding> basis, int[] perm, int k, List<Finding> erwartet) {
        if (k == perm.length) {
            List<Finding> eingabe = new ArrayList<>();
            for (int i : perm) {
                eingabe.add(basis.get(i));
            }
            assertEquals(erwartet, ConflictResolver.resolve(TEXT, eingabe));
            return 1;
        }
        int summe = 0;
        for (int i = k; i < perm.length; i++) {
            int t = perm[k];
            perm[k] = perm[i];
            perm[i] = t;
            summe += permutationen(basis, perm, k + 1, erwartet);
            t = perm[k];
            perm[k] = perm[i];
            perm[i] = t;
        }
        return summe;
    }

    @Test
    void leereListeLiefertLeereListe() {
        assertTrue(ConflictResolver.resolve(TEXT, List.of()).isEmpty());
        assertTrue(ConflictResolver.resolve("", List.of()).isEmpty());
    }

    @Test
    void nullTextListeUndElementWerdenMitParameternamenAbgelehnt() {
        assertEquals("text", assertThrows(NullPointerException.class,
                () -> ConflictResolver.resolve(null, List.of())).getMessage());
        assertEquals("candidates", assertThrows(NullPointerException.class,
                () -> ConflictResolver.resolve(TEXT, null)).getMessage());
        List<Finding> mitNull = new ArrayList<>();
        mitNull.add(f(PERSON, 0, 5, 0.5));
        mitNull.add(null);
        assertEquals("candidates enthält null", assertThrows(NullPointerException.class,
                () -> ConflictResolver.resolve(TEXT, mitNull)).getMessage());
    }

    @Test
    void kandidatMitFalschemWertWirdMitListenindexOhneWertAbgelehnt() {
        Finding falsch = new Finding(PERSON, MARKER, 5, 5 + MARKER.length(), 0.9, "stub");
        List<Finding> liste = List.of(f(PERSON, 0, 3, 0.9), falsch);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConflictResolver.resolve(TEXT, liste));
        assertTrue(e.getMessage().contains("1"));
        assertFalse(e.getMessage().contains(MARKER));
        assertFalse(e.getMessage().contains("mustermann"));
    }

    @Test
    void kandidatUeberDasTextendeHinausWirdMitListenindexAbgelehnt() {
        String kurz = "abcdef";
        Finding zuWeit = new Finding(PERSON, "ef" + MARKER, 4, 4 + 2 + MARKER.length(), 0.9, "stub");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConflictResolver.resolve(kurz, List.of(zuWeit)));
        assertTrue(e.getMessage().contains("0"));
        assertFalse(e.getMessage().contains(MARKER));
        Finding endeNachTextende = new Finding(PERSON, "ef" + "g", 4, 7, 0.9, "stub");
        assertThrows(IllegalArgumentException.class,
                () -> ConflictResolver.resolve(kurz, List.of(endeNachTextende)));
    }

    // ---------- Grenzlast (gemessen wird nur resolve, nicht der Aufbau) ----------

    private static void assertSchnell(Runnable resolveAufruf) {
        assertTimeoutPreemptively(Duration.ofSeconds(2), resolveAufruf::run);
    }

    @Test
    void kette100000KandidatenErgibtEinenFundAuchGemischt() {
        int n = 100_000;
        String text = "x".repeat(2 * n + 3);
        List<Finding> kette = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            kette.add(f(text, PERSON, 2 * i, 2 * i + 3, 0.9, "stub"));
        }
        List<Finding> gemischt = new ArrayList<>(kette);
        Collections.shuffle(gemischt, new Random(4711));
        assertSchnell(() -> {
            List<Finding> r = ConflictResolver.resolve(text, kette);
            assertEquals(1, r.size());
            assertEquals(0, r.get(0).start());
            assertEquals(2 * n + 1, r.get(0).end());
        });
        assertSchnell(() -> {
            List<Finding> r = ConflictResolver.resolve(text, gemischt);
            assertEquals(1, r.size());
            assertEquals(Finding.SOURCE_MERGED, r.get(0).source());
        });
    }

    @Test
    void verschachtelte10000ErgebenGenauDenAeusserstenUndDisjunkte100000BleibenAlle() {
        int n = 10_000;
        String text = "x".repeat(2 * n);
        List<Finding> verschachtelt = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            verschachtelt.add(f(text, PERSON, i, 2 * n - i, 0.9, "stub"));
        }
        Collections.shuffle(verschachtelt, new Random(1));
        assertSchnell(() -> {
            List<Finding> r = ConflictResolver.resolve(text, verschachtelt);
            assertEquals(1, r.size());
            assertEquals(0, r.get(0).start());
            assertEquals(2 * n, r.get(0).end());
            assertEquals("stub", r.get(0).source());
        });
        int m = 100_000;
        String langerText = "x".repeat(2 * m);
        List<Finding> disjunkt = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            disjunkt.add(f(langerText, PERSON, 2 * i, 2 * i + 1, 0.9, "stub"));
        }
        assertSchnell(() -> assertEquals(m, ConflictResolver.resolve(langerText, disjunkt).size()));
    }
}
