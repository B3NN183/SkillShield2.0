package de.skillshield.core.recognizer;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Lueckenregel des {@link TextScanner} (Feature geheimnisse-erkennung, T-3; D-13, OF-7, AK-29, AK-31, AK-45):
 * Kandidaten der Typen PASSWORT und TOKEN veraendern keinen Fund der uebrigen Erkenner und fuellen nur die Luecken.
 *
 * <p>Die Faelle laufen mit kuenstlichen Erkennern, die feste Bereiche eines Texts ohne Ziffern liefern; die Positionen
 * stehen in den Erwartungen als Zahlen. Der Eigenschaftstest nutzt einen festen Seed. Kein Test nennt einen Wert in
 * einer Meldung.
 */
class TextScannerLueckenTest {

    /** Text ohne Ziffern; die Zeichen sind beliebig, es zaehlen nur die Positionen. */
    private static final String T = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final FindingType HOST = new FindingType("HOST");
    private static final FindingType PASSWORT = GeheimnisTypen.PASSWORT;
    private static final FindingType TOKEN = GeheimnisTypen.TOKEN;

    private static final String Q_BESTAND = "kuenstlich-bestand";
    private static final String Q_PW = "kuenstlich-pw";
    private static final String Q_TOKEN = "kuenstlich-token";
    private static final long SEED = 20261125L;

    // ------------------------------------------------------------------ Hilfen

    private static Finding f(FindingType typ, int von, int bis, double konfidenz, String quelle) {
        return new Finding(typ, T.substring(von, bis), von, bis, konfidenz, quelle);
    }

    private static Finding bestand(int von, int bis) {
        return f(FindingType.EMAIL, von, bis, 0.95, Q_BESTAND);
    }

    private static Finding pw(int von, int bis) {
        return f(PASSWORT, von, bis, 0.95, Q_PW);
    }

    private static Recognizer fest(Finding... funde) {
        List<Finding> liste = List.of(funde);
        return text -> liste;
    }

    private static Recognizer nachrangig(String restQuelle, Finding... funde) {
        List<Finding> liste = List.of(funde);
        return new Recognizer() {
            @Override
            public List<Finding> detect(String text) {
                return liste;
            }

            @Override
            public boolean nachrangig() {
                return true;
            }

            @Override
            public String restQuelle() {
                return restQuelle;
            }
        };
    }

    private static List<Finding> scan(Recognizer... erkenner) {
        return new TextScanner(List.of(erkenner)).scan(T);
    }

    private static List<Finding> mit(Finding bestand, Finding... geheim) {
        return scan(fest(bestand), fest(geheim));
    }

    private static BitSet abdeckung(List<Finding> funde) {
        BitSet bits = new BitSet();
        for (Finding f : funde) {
            bits.set(f.start(), f.end());
        }
        return bits;
    }

    private static boolean ueberlappt(Finding a, Finding b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    // ------------------------------------------------------------------ Typen

    @Test
    void geheimnisTypen_nurPasswortUndToken_nullWirdAbgelehnt() {
        assertTrue(GeheimnisTypen.istGeheimnistyp(PASSWORT));
        assertTrue(GeheimnisTypen.istGeheimnistyp(TOKEN));
        assertTrue(GeheimnisTypen.istGeheimnistyp(new FindingType("PASSWORT")));
        for (FindingType t : List.of(FindingType.EMAIL, FindingType.IP, FindingType.URL, FindingType.PFAD,
                FindingType.IK, FindingType.LANR, FindingType.KVNR, FindingType.BSNR, FindingType.TEXT, HOST)) {
            assertFalse(GeheimnisTypen.istGeheimnistyp(t), t.name());
        }
        NullPointerException e = assertThrows(NullPointerException.class, () -> GeheimnisTypen.istGeheimnistyp(null));
        assertEquals("type", e.getMessage());
    }

    @Test
    void geheimnisTypen_namenSindGueltigeTypnamen() {
        assertEquals("PASSWORT", PASSWORT.name());
        assertEquals("TOKEN", TOKEN.name());
        assertTrue(FindingType.isValidName(PASSWORT.name()));
        assertTrue(FindingType.isValidName(TOKEN.name()));
    }

    // ------------------------------------------------------------------ Einzelfaelle

    @Test
    void ohneBestand_derKandidatBleibtUnveraendert() {
        Finding g = pw(5, 15);
        assertEquals(List.of(g), scan(fest(g)));
    }

    @Test
    void angrenzendLinks_keinBestandsfundVeraendert_kandidatBleibtGanz() {
        Finding b = bestand(10, 20);
        Finding g = pw(5, 10);
        assertEquals(List.of(g, b), mit(b, g));
    }

    @Test
    void angrenzendRechts_keinBestandsfundVeraendert_kandidatBleibtGanz() {
        Finding b = bestand(10, 20);
        Finding g = pw(20, 25);
        assertEquals(List.of(b, g), mit(b, g));
    }

    @Test
    void ganzInnen_derKandidatEntfaellt() {
        Finding b = bestand(10, 20);
        assertEquals(List.of(b), mit(b, pw(12, 18)));
        assertEquals(List.of(b), mit(b, pw(10, 15)));
        assertEquals(List.of(b), mit(b, pw(15, 20)));
    }

    @Test
    void gleicherBereich_derKandidatEntfaellt_derBestandsfundBleibtMitSeinemTyp() {
        Finding b = bestand(10, 20);
        assertEquals(List.of(b), mit(b, pw(10, 20)));
        assertEquals(List.of(b), mit(b, f(TOKEN, 10, 20, 1.0, Q_TOKEN)));
    }

    @Test
    void ganzUmfassend_zweiReststuecke_mitTypQuelleKonfidenzDesKandidaten_ungekuerzt() {
        Finding b = bestand(10, 20);
        Finding g = f(TOKEN, 5, 25, 0.80, Q_TOKEN);
        List<Finding> erwartet = List.of(f(TOKEN, 5, 10, 0.80, Q_TOKEN), b, f(TOKEN, 20, 25, 0.80, Q_TOKEN));
        assertEquals(erwartet, mit(b, g));
    }

    @Test
    void teilueberlappungLinks_reststueckLinksDesBestandsfunds() {
        Finding b = bestand(10, 20);
        assertEquals(List.of(pw(5, 10), b), mit(b, pw(5, 15)));
    }

    @Test
    void teilueberlappungRechts_reststueckRechtsDesBestandsfunds() {
        Finding b = bestand(10, 20);
        assertEquals(List.of(b, pw(20, 25)), mit(b, pw(15, 25)));
    }

    @Test
    void einZeichenReststueck_bleibtEinFund() {
        Finding b = bestand(10, 20);
        assertEquals(List.of(pw(9, 10), b, pw(20, 21)), mit(b, pw(9, 21)));
    }

    @Test
    void mehrereBestandsfundeImKandidaten_alleLueckenWerdenReststuecke() {
        Finding b1 = bestand(10, 14);
        Finding b2 = bestand(16, 20);
        Finding b3 = bestand(22, 24);
        List<Finding> erwartet = List.of(pw(5, 10), b1, pw(14, 16), b2, pw(20, 22), b3, pw(24, 30));
        assertEquals(erwartet, scan(fest(b1, b2, b3), fest(pw(5, 30))));
    }

    @Test
    void aneinandergrenzendeBestandsfunde_keineLueckeDazwischen() {
        Finding b1 = bestand(10, 14);
        Finding b2 = f(FindingType.PFAD, 14, 20, 1.0, Q_BESTAND);
        assertEquals(List.of(pw(5, 10), b1, b2, pw(20, 25)), scan(fest(b1, b2), fest(pw(5, 25))));
    }

    @Test
    void kvFundAlsBestand_bleibtUnveraendert_geheimnisNurInDenLuecken() {
        for (FindingType kv : List.of(FindingType.KVNR, FindingType.LANR, FindingType.IK, FindingType.BSNR)) {
            Finding b = f(kv, 10, 20, 0.90, "kuenstlich-kv");
            assertEquals(List.of(pw(5, 10), b, pw(20, 25)), mit(b, pw(5, 25)), kv.name());
            assertEquals(List.of(b), mit(b, pw(12, 18)), kv.name());
        }
    }

    @Test
    void zusammengelegterFundAlsBestand_bleibtUnveraendert() {
        Finding a = f(FindingType.KVNR, 10, 16, 0.95, Q_BESTAND);
        Finding b = f(FindingType.LANR, 14, 20, 0.60, Q_BESTAND);
        List<Finding> alt = scan(fest(a, b));
        assertEquals(1, alt.size());
        assertEquals(Finding.SOURCE_MERGED, alt.get(0).source());
        List<Finding> neu = scan(fest(a, b), fest(pw(5, 25)));
        assertEquals(List.of(pw(5, 10), alt.get(0), pw(20, 25)), neu);
    }

    @Test
    void nachrangigerErkennerImScan_seinFundBleibt_geheimnisFuelltDieLuecken() {
        Finding b = bestand(10, 20);
        Finding rang = f(FindingType.KVNR, 30, 40, 0.90, "kuenstlich-kvnr");
        List<Finding> alt = scan(fest(b), nachrangig("kuenstlich-rest", rang));
        assertEquals(List.of(b, rang), alt);
        List<Finding> neu = scan(fest(b), nachrangig("kuenstlich-rest", rang), fest(pw(8, 45)));
        assertEquals(List.of(pw(8, 10), b, pw(20, 30), rang, pw(40, 45)), neu);
    }

    @Test
    void geheimnisKandidatEinesNachrangigenErkenners_wirdNachDemTypBehandelt_nichtZugeschnittenMitRestQuelle() {
        Finding b = bestand(10, 20);
        Finding g = pw(5, 25);
        List<Finding> neu = scan(fest(b), nachrangig("kuenstlich-rest", g));
        assertEquals(List.of(pw(5, 10), b, pw(20, 25)), neu);
    }

    @Test
    void kandidatenUntereinanderUeberlappend_ohneBestand_werdenZusammengelegt() {
        Finding g1 = pw(5, 15);
        Finding g2 = f(TOKEN, 10, 20, 0.80, Q_TOKEN);
        List<Finding> neu = scan(fest(g1), fest(g2));
        assertEquals(1, neu.size());
        Finding m = neu.get(0);
        assertEquals(5, m.start());
        assertEquals(20, m.end());
        assertEquals(PASSWORT, m.type());
        assertEquals(0.80, m.confidence(), 0.0);
        assertEquals(Finding.SOURCE_MERGED, m.source());
    }

    @Test
    void kandidatenUntereinanderUeberlappend_mitBestand_bestandUnveraendert_keinePositionFrei() {
        Finding b = bestand(12, 14);
        Finding g1 = pw(5, 15);
        Finding g2 = f(TOKEN, 10, 20, 0.80, Q_TOKEN);
        List<Finding> neu = scan(fest(b), fest(g1), fest(g2));
        assertTrue(neu.contains(b));
        BitSet erwartet = new BitSet();
        erwartet.set(5, 20);
        assertEquals(erwartet, abdeckung(neu));
        for (Finding f : neu) {
            if (!f.equals(b)) {
                assertFalse(ueberlappt(f, b));
                assertTrue(GeheimnisTypen.istGeheimnistyp(f.type()));
            }
        }
    }

    @Test
    void gleicherKandidatVonZweiErkennern_einFund() {
        Finding g = pw(5, 15);
        assertEquals(List.of(g), scan(fest(g), fest(g)));
    }

    @Test
    void leereListe_ergebnisIstAlt_dieselbeListe() {
        List<Finding> alt = scan(fest(bestand(10, 20)));
        assertSame(alt, TextScanner.luecken(T, List.of(), alt));
        assertEquals(alt, scan(fest(bestand(10, 20)), fest()));
        assertEquals(List.of(), scan(fest()));
        assertEquals(List.of(), new TextScanner(List.of()).scan(T));
    }

    @Test
    void ergebnisIstUnveraenderlich() {
        List<Finding> neu = mit(bestand(10, 20), pw(5, 25));
        assertThrows(UnsupportedOperationException.class, () -> neu.add(pw(1, 2)));
    }

    @Test
    void reihenfolgeDerErkenner_aendertDasErgebnisNicht() {
        Recognizer b = fest(bestand(10, 14), bestand(16, 20));
        Recognizer g1 = fest(pw(5, 30), f(TOKEN, 12, 18, 0.80, Q_TOKEN));
        Recognizer g2 = nachrangig("kuenstlich-rest", f(TOKEN, 22, 40, 0.80, Q_TOKEN));
        List<Finding> erste = scan(b, g1, g2);
        assertEquals(erste, scan(g2, g1, b));
        assertEquals(erste, scan(g1, b, g2));
        assertEquals(erste, scan(g2, b, g1));
    }

    @Test
    void fehlerfaelle_unveraendert_ohneKlartextInDerMeldung() {
        TextScanner s = new TextScanner(List.of(fest(pw(5, 15))));
        assertEquals("text", assertThrows(NullPointerException.class, () -> s.scan(null)).getMessage());
        TextScanner falsch = new TextScanner(List.of(text -> List.of(new Finding(PASSWORT, "zzz", 5, 8, 0.9, "x"))));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> falsch.scan(T));
        assertFalse(e.getMessage().contains("zzz"));
        assertFalse(e.getMessage().contains(T.substring(5, 8)));
    }

    // ------------------------------------------------------------------ Laufzeit

    @Test
    void laufzeit_vieleBestandsfundeInEinemLangenKandidaten_linear() {
        int n = 200_000;
        String text = "a".repeat(3 * n + 1);
        List<Finding> bestand = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bestand.add(new Finding(FindingType.EMAIL, "aa", 3 * i + 1, 3 * i + 3, 0.95, Q_BESTAND));
        }
        Finding lang = new Finding(PASSWORT, text, 0, text.length(), 0.95, Q_PW);
        TextScanner scanner = new TextScanner(List.of(text0 -> bestand, text0 -> List.of(lang)));
        long t0 = System.nanoTime();
        List<Finding> neu = scanner.scan(text);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(2 * n + 1, neu.size());
        assertTrue(ms < 20_000, "Laufzeit in ms: " + ms);
        BitSet voll = new BitSet();
        voll.set(0, text.length());
        assertEquals(voll, abdeckung(neu));
    }

    @Test
    void laufzeit_vieleKandidatenUndVieleBestandsfunde_linear() {
        int n = 100_000;
        String text = "a".repeat(4 * n);
        List<Finding> bestand = new ArrayList<>(n);
        List<Finding> geheim = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bestand.add(new Finding(FindingType.PFAD, "aa", 4 * i + 1, 4 * i + 3, 0.95, Q_BESTAND));
            geheim.add(new Finding(TOKEN, "aaa", 4 * i, 4 * i + 3, 0.80, Q_TOKEN));
        }
        TextScanner scanner = new TextScanner(List.of(text0 -> bestand, text0 -> geheim));
        long t0 = System.nanoTime();
        List<Finding> neu = scanner.scan(text);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(n + n, neu.size());
        assertTrue(ms < 20_000, "Laufzeit in ms: " + ms);
    }

    // ------------------------------------------------------------------ Eigenschaftstest (fester Seed)

    private static final List<FindingType> BESTAND_TYPEN = List.of(FindingType.EMAIL, FindingType.IP,
            FindingType.URL, FindingType.PFAD, FindingType.LANR, FindingType.IK, FindingType.BSNR, HOST);
    private static final double[] KONFIDENZEN = {0.40, 0.60, 0.80, 0.90, 0.95, 1.0};

    private static List<Finding> zufall(Random r, String text, int max, List<FindingType> typen, String quelle) {
        List<Finding> liste = new ArrayList<>();
        int zahl = r.nextInt(max + 1);
        for (int i = 0; i < zahl; i++) {
            int von = r.nextInt(text.length() - 1);
            int bis = Math.min(text.length(), von + 1 + r.nextInt(30));
            FindingType typ = typen.get(r.nextInt(typen.size()));
            liste.add(new Finding(typ, text.substring(von, bis), von, bis, KONFIDENZEN[r.nextInt(6)], quelle));
        }
        return liste;
    }

    /** Ob zu dem Fund ein Kandidat gehoert, der ihn mit gleichem Typ, gleicher Quelle und Konfidenz umfasst. */
    private static boolean stammtVonKandidat(Finding f, List<Finding> kandidaten) {
        for (Finding k : kandidaten) {
            if (k.type().equals(f.type()) && k.source().equals(f.source()) && k.confidence() == f.confidence()
                    && k.start() <= f.start() && f.end() <= k.end()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void eigenschaften_festerSeed_altEnthaltenAbdeckungReihenfolgeUndGleichheitMitAlt() {
        Random r = new Random(SEED);
        int mitReststuecken = 0;
        int mitZusammenlegung = 0;
        for (int i = 0; i < 20_000; i++) {
            String text = T.substring(0, 40 + r.nextInt(T.length() - 39));
            List<Finding> bestandListe = zufall(r, text, 8, BESTAND_TYPEN, Q_BESTAND);
            List<Finding> rangListe = zufall(r, text, 2, List.of(FindingType.KVNR), "kuenstlich-kvnr");
            List<Finding> pwListe = zufall(r, text, 4, List.of(PASSWORT), Q_PW);
            List<Finding> tokenListe = zufall(r, text, 4, List.of(TOKEN, PASSWORT), Q_TOKEN);
            Recognizer b = t -> bestandListe;
            Recognizer rang = new Recognizer() {
                @Override
                public List<Finding> detect(String t) {
                    return rangListe;
                }

                @Override
                public boolean nachrangig() {
                    return true;
                }

                @Override
                public String restQuelle() {
                    return "kuenstlich-rest";
                }
            };
            Recognizer g1 = t -> pwListe;
            Recognizer g2 = t -> tokenListe;

            List<Finding> alt = new TextScanner(List.of(b, rang)).scan(text);
            // ALT == Produkt ohne Geheimnis-Kandidaten (Zeichen- und Feldgleichheit zum eingefrorenen Ablauf)
            assertEquals(new TextScannerStandGeheimnis(List.of(b, rang)).scan(text), alt, "ALT-Gleichheit " + i);

            List<Finding> neu = new TextScanner(List.of(b, g1, rang, g2)).scan(text);
            List<Finding> alle = new ArrayList<>(pwListe);
            alle.addAll(tokenListe);

            // (a) alt ist feldgleich enthalten
            for (Finding f : alt) {
                assertTrue(neu.contains(f), "Fund von alt fehlt, Iteration " + i);
            }
            // sortiert und ueberlappungsfrei
            for (int k = 1; k < neu.size(); k++) {
                assertTrue(neu.get(k - 1).end() <= neu.get(k).start(), "Ueberlappung, Iteration " + i);
            }
            // (b) jeder weitere Fund ist ein Geheimnistyp und beruehrt alt nicht; Reststuecke behalten Typ,
            // Quelle und Konfidenz des Kandidaten
            for (Finding f : neu) {
                if (alt.contains(f)) {
                    continue;
                }
                assertTrue(GeheimnisTypen.istGeheimnistyp(f.type()), "Typ, Iteration " + i);
                for (Finding a : alt) {
                    assertFalse(ueberlappt(a, f), "Ueberlappung mit alt, Iteration " + i);
                }
                if (f.source().equals(Finding.SOURCE_MERGED)) {
                    mitZusammenlegung++;
                } else {
                    mitReststuecken++;
                    assertTrue(stammtVonKandidat(f, alle), "Reststueck ohne Kandidat, Iteration " + i);
                }
            }
            // (c) Abdeckung neu = alt vereinigt Kandidaten
            BitSet erwartet = abdeckung(alt);
            erwartet.or(abdeckung(alle));
            assertEquals(erwartet, abdeckung(neu), "Abdeckung, Iteration " + i);
            // (d) Reihenfolge der Erkenner
            List<Recognizer> mischung = new ArrayList<>(List.of(b, g1, rang, g2));
            Collections.shuffle(mischung, r);
            assertEquals(neu, new TextScanner(mischung).scan(text), "Reihenfolge, Iteration " + i);
        }
        assertTrue(mitReststuecken > 1_000, "zu wenige Reststuecke im Lauf: " + mitReststuecken);
        assertTrue(mitZusammenlegung > 100, "zu wenige Zusammenlegungen im Lauf: " + mitZusammenlegung);
    }

    // ------------------------------------------------------------------ standard() gleich ALT

    private static final List<String> BAUSTEINE = List.of("Dienst", "gestartet", "und", "Server", "LANR", "IK",
            "KVNR", "BSNR", "Nummer", "Passwort", "Token", "C:\\Temp\\x.log", "/etc/dienst/x.conf",
            "\\\\srv01\\freigabe\\a.txt", "https://host.example/pfad?x=y", "max@example.invalid", "192.0.2.17",
            LANR_GUELTIG, IK_GUELTIG, KVNR_GUELTIG, "(", ")", ",", ".", ":", "=", "-", "/", "\n", "\t", "  ");

    @Test
    void standard_ohneGeheimnisKandidaten_gleichAlt_ueberEinenGrossenZufallslauf() {
        Random r = new Random(SEED + 1);
        TextScanner neu = TextScanner.standard();
        int gesamt = 0;
        for (int i = 0; i < 60_000; i++) {
            StringBuilder sb = new StringBuilder();
            int teile = 1 + r.nextInt(14);
            for (int k = 0; k < teile; k++) {
                sb.append(BAUSTEINE.get(r.nextInt(BAUSTEINE.size())));
                if (r.nextInt(4) != 0) {
                    sb.append(' ');
                }
            }
            String text = sb.toString();
            List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
            gesamt += alt.size();
            assertEquals(alt, neu.scan(text), "standard() ungleich ALT, Iteration " + i);
        }
        assertTrue(gesamt > 50_000, "zu wenige Funde im Lauf: " + gesamt);
    }
}
