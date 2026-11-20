package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fuzz-Test (T-18; AK-45, AK-1, AK-7 (a) und (e)) mit den echten Standard-Erkennern.
 *
 * <p>Teil 1: {@value #ANZAHL_TEXTE} Texte aus {@code new Random(SEED_TEXTE)}, gemischt aus synthetischen Funden
 * (Pfade aller elf Formen mit Backslashes, Leerzeichen, auch ungeschlossenen Anführungszeichen; E-Mail, IPv4, URLs
 * mit angehängtem Pfad in Anführungszeichen), Sonderzeichen, Unicode (auch außerhalb der BMP), Zeilenenden und
 * platzhalterähnlichen Mustern. Je Text: zufällige Teilmenge der Scan-Funde plus manuelle Funde, Pseudonymisierung,
 * Rückübersetzung. Teil 2: {@value #ANZAHL_EINGABEN} Zufallseingaben an {@code restore}.
 *
 * <p>Der Lauf wird einmal ausgeführt ({@link #LAUF}); jede Eigenschaft ist ein eigener Test. Meldungen nennen nur
 * Startwert, Iteration und Anzahl, nie einen Text. Testdaten sind synthetisch. Nicht-ASCII-Zeichen stehen nur als
 * Escapes im Quelltext, Backslashes über {@link TestTexte#bs(String)}.
 */
class RoundtripFuzzTest {

    static final long SEED_TEXTE = 20261120L;
    static final long SEED_AUSWAHL = 424242L;
    static final long SEED_EINGABEN = 777001L;
    static final int ANZAHL_TEXTE = 1000;
    static final int ANZAHL_EINGABEN = 1000;

    private static final String[] FORMEN = {"LAUFWERK", "UNC", "UNIX", "HOME", "HOST", "RELATIV", "EINZEL_BACKSLASH",
        "BENUTZER_WURZEL", "WURZEL_ZWEI_SEGMENTE", "UMGEBUNG", "FILE_URI"};
    private static final String[] QUELLEN = {"pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home", "pfad-host",
        "pfad-relativ", "pfad-relativ", "pfad-relativ", "pfad-relativ", "pfad-umgebung", "pfad-file-uri"};

    private static final String[] SEGMENTE = {"Tools", "BeispielTool", "Mein Tool", "erika", "mustermann",
        "Akte 2025", "datei.txt", "a b", "[x]", "x_y", "\u00E4u\u00DF", "x\uD83D\uDE00y", "'q", "k\"l"};

    private static final String[] SONDER = {"\r\n", "\n", "\r", "\t", "\u00E4\u00F6\u00FC\u00DF", "\u202E",
        "\u200B", "\u00A0", "\uD83D\uDE00", "\uD834\uDD1E", "\uD840\uDC00", "[", "]", "_", "[[X_1]]", "[[TEXT_1]]",
        "[[PERSON_1", "[[PFAD_1]]", "[[", "]]", "[[IP_2]]", "[[EMAIL_", "Hallo", "Text", ",", ".", ";", "(", ")",
        "\"", "'", "`", "$1", "\\", "\\\\", "%", "$"};

    private static final String[] TRENNER = {" ", " ", " ", "\n", "\r\n", ", ", "", " - ", "\t"};

    private static final String[] DOMAINS = {"example.invalid", "example.org", "example.net"};
    private static final String[] LOKAL = {"erika.beispiel", "max.mustermann", "a_b", "x+y"};

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** Genau die Erkenner von {@code TextScanner.standard()}, in derselben Reihenfolge. */
    private static final List<Recognizer> STANDARD_ERKENNER = List.of(new EmailRecognizer(), new Ipv4Recognizer(),
            new UrlRecognizer(), new PathRecognizer());

    private static Lauf LAUF;

    // ------------------------------------------------------------------ Erzeugung

    /** Ein erzeugter Text mit den Bereichen der eingefügten Pfadbausteine (Form, Start, Ende). */
    record Fall(String text, List<int[]> pfade, boolean ungeschlossen) {
    }

    static List<Fall> erzeuge(long seed) {
        Random r = new Random(seed);
        List<Fall> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeText(r));
        }
        return faelle;
    }

    private static Fall erzeugeText(Random r) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        boolean[] ungeschlossen = {false};
        int n = 2 + r.nextInt(9);
        for (int i = 0; i < n; i++) {
            int art = r.nextInt(100);
            if (art < 45) {
                int form = r.nextInt(FORMEN.length);
                int start = sb.length();
                sb.append(pfad(r, form, ungeschlossen));
                pfade.add(new int[] {form, start, sb.length()});
            } else if (art < 55) {
                sb.append(LOKAL[r.nextInt(LOKAL.length)]).append('@').append(DOMAINS[r.nextInt(DOMAINS.length)]);
            } else if (art < 63) {
                sb.append(ip(r));
            } else if (art < 71) {
                sb.append(url(r));
            } else if (art < 78) {
                int start = sb.length();
                int form = r.nextInt(FORMEN.length);
                sb.append("https://intranet.invalid/?f='").append(pfadOhneAnfuehrung(r, form))
                        .append(" 2025/").append(LOKAL[0]).append("@example.invalid/x.pdf'");
                pfade.add(new int[] {form, start + 29, sb.length()});
            } else {
                sb.append(SONDER[r.nextInt(SONDER.length)]);
            }
            sb.append(TRENNER[r.nextInt(TRENNER.length)]);
        }
        return new Fall(sb.toString(), pfade, ungeschlossen[0]);
    }

    private static String ip(Random r) {
        String[] netze = {"192.0.2.", "198.51.100.", "203.0.113."};
        return netze[r.nextInt(netze.length)] + r.nextInt(256);
    }

    private static String url(Random r) {
        switch (r.nextInt(4)) {
            case 0:
                return "https://intranet.invalid/pfad?x=" + r.nextInt(100);
            case 1:
                return "http://srv-beispiel.example/a/b.html";
            case 2:
                return "https://e.invalid/" + SEGMENTE[r.nextInt(SEGMENTE.length)].replace(' ', '+');
            default:
                return bs("https://e.invalid/'C:¦a b¦c.txt' ende");
        }
    }

    /** Pfad der Form {@code form}, mit zufälligem Anführungszeichen-Mantel (auch ungeschlossen). */
    private static String pfad(Random r, int form, boolean[] ungeschlossen) {
        String p = pfadOhneAnfuehrung(r, form);
        switch (r.nextInt(8)) {
            case 0:
                return "\"" + p + "\"";
            case 1:
                return "'" + p + "'";
            case 2:
                return "`" + p + "`";
            case 3:
                ungeschlossen[0] = true;
                return "\"" + p;
            case 4:
                ungeschlossen[0] = true;
                return "'" + p;
            default:
                return p;
        }
    }

    private static String pfadOhneAnfuehrung(Random r, int form) {
        String kopf;
        char[] trenner;
        switch (form) {
            case 0:
                kopf = r.nextBoolean() ? bs("C:¦") : "c:/";
                trenner = new char[] {'\\', '/'};
                break;
            case 1:
                kopf = bs("¦¦srv-beispiel¦freigabe¦");
                trenner = new char[] {'\\'};
                break;
            case 2:
                kopf = "/opt/beispiel/";
                trenner = new char[] {'/'};
                break;
            case 3:
                kopf = r.nextBoolean() ? "~/" : "~erika/";
                trenner = new char[] {'/'};
                break;
            case 4:
                kopf = "host.example:/export/";
                trenner = new char[] {'/'};
                break;
            case 5:
                String[] relativ = {bs(".¦"), "./", "../", bs("..¦")};
                kopf = relativ[r.nextInt(relativ.length)];
                trenner = new char[] {kopf.charAt(kopf.length() - 1)};
                break;
            case 6:
                kopf = bs("¦srv¦freigabe¦");
                trenner = new char[] {'\\'};
                break;
            case 7:
                kopf = r.nextBoolean() ? bs("Users¦erika.mustermann¦") : bs("Users¦erika¦");
                trenner = new char[] {'\\', '/'};
                break;
            case 8:
                kopf = r.nextBoolean() ? "home/erika/" : "Benutzer/erika/";
                trenner = new char[] {'/'};
                break;
            case 9:
                String[] umgebung = {"$HOME/", "${HOME}/", bs("%APPDATA%¦")};
                kopf = umgebung[r.nextInt(umgebung.length)];
                trenner = new char[] {'\\', '/'};
                break;
            default:
                kopf = r.nextBoolean() ? "file:///C:/" : "file://srv-beispiel/";
                trenner = new char[] {'/'};
                break;
        }
        StringBuilder sb = new StringBuilder(kopf);
        int segmente = 1 + r.nextInt(4);
        for (int i = 0; i < segmente; i++) {
            if (i > 0) {
                sb.append(trenner[r.nextInt(trenner.length)]);
            }
            sb.append(SEGMENTE[r.nextInt(SEGMENTE.length)]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Lauf

    /** Ergebnis des gesamten Laufs: erste Verletzung und Anzahl je Eigenschaft sowie Klassenhäufigkeiten. */
    static final class Lauf {
        final Map<String, int[]> verletzungen = new LinkedHashMap<>();
        final Map<String, Integer> klassen = new TreeMap<>();
        int eingabenOhneAusnahme;

        void verletzt(String eigenschaft, int iteration) {
            int[] v = verletzungen.computeIfAbsent(eigenschaft, k -> new int[] {iteration, 0});
            v[1]++;
        }

        void klasse(String name) {
            klassen.merge(name, 1, Integer::sum);
        }

        int klasse_(String name) {
            return klassen.getOrDefault(name, 0);
        }

        String meldung(String eigenschaft) {
            int[] v = verletzungen.get(eigenschaft);
            if (v == null) {
                return "Startwert " + SEED_TEXTE;
            }
            return "Startwert " + SEED_TEXTE + ", Eigenschaft " + eigenschaft + ", erste Iteration " + v[0]
                    + ", betroffene Iterationen " + v[1];
        }

        int anzahl(String eigenschaft) {
            int[] v = verletzungen.get(eigenschaft);
            return v == null ? 0 : v[1];
        }
    }

    @BeforeAll
    static void lauf() {
        LAUF = fuehreAus();
    }

    private static boolean paarGetrennt(String s, int pos) {
        return pos > 0 && pos < s.length() && Character.isHighSurrogate(s.charAt(pos - 1))
                && Character.isLowSurrogate(s.charAt(pos));
    }

    private static boolean[] abdeckung(int laenge, List<int[]> bereiche) {
        boolean[] a = new boolean[laenge];
        for (int[] b : bereiche) {
            for (int i = b[0]; i < b[1]; i++) {
                a[i] = true;
            }
        }
        return a;
    }

    private static boolean teilueberlappt(Finding a, Finding b) {
        return (a.start() < b.start() && b.start() < a.end() && a.end() < b.end())
                || (b.start() < a.start() && a.start() < b.end() && b.end() < a.end());
    }

    private static Lauf fuehreAus() {
        Lauf lauf = new Lauf();
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL);
        List<Fall> faelle = erzeuge(SEED_TEXTE);
        for (int it = 0; it < faelle.size(); it++) {
            Fall fall = faelle.get(it);
            String text = fall.text();
            try {
                pruefeText(lauf, scanner, auswahl, it, fall, text);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        // Teil 2
        Random r = new Random(SEED_EINGABEN);
        PlaceholderMapping zuordnung = PlaceholderMapping.of(Map.of("[[PERSON_1]]", "erika", "[[PFAD_2]]",
                bs("C:¦x"), "[[TEXT_3]]", "[[X_1]]", "[[EMAIL_10]]", "a@example.invalid"));
        String[] teile = {"[[", "]]", "[", "]", "_", "PERSON", "PFAD", "TEXT", "EMAIL", "1", "2", "3", "10",
            "99999999999999999999", "[[PERSON_1]]", "[[PFAD_2]]", "[[PERSON_1", "[PERSON_1]", "[[person_1]]",
            "[[PERSON_01]]", "[[PERSON_]]", "[[TEXT_3]]", "[[X_1]]", "[[EMAIL_10]]", " ", "\n", "\r\n", "\\", "$1",
            "\uD83D\uDE00", "\uD83D", "\uDE00", "\u0000", "\u00E4"};
        for (int it = 0; it < ANZAHL_EINGABEN; it++) {
            StringBuilder sb = new StringBuilder();
            int n = r.nextInt(40);
            for (int i = 0; i < n; i++) {
                if (r.nextInt(4) == 0) {
                    sb.append((char) r.nextInt(0x10000));
                } else {
                    sb.append(teile[r.nextInt(teile.length)]);
                }
            }
            String eingabe = sb.toString();
            try {
                ReidentificationResult mit = REIDENTIFIER.restore(eingabe, zuordnung);
                ReidentificationResult ohne = REIDENTIFIER.restore(eingabe, PlaceholderMapping.empty());
                assertNotNull(mit.text());
                assertNotNull(ohne.text());
                lauf.eingabenOhneAusnahme++;
                if (!mit.text().equals(eingabe)) {
                    lauf.klasse("eingabe-aufgeloest");
                }
                if (!mit.unresolved().isEmpty()) {
                    lauf.klasse("eingabe-meldung");
                }
            } catch (RuntimeException | AssertionError e) {
                lauf.verletzt("restore-ausnahme", it);
            }
        }
        return lauf;
    }

    private static void pruefeText(Lauf lauf, TextScanner scanner, Random auswahl, int it, Fall fall,
            String text) {
        // Kandidaten jedes Standard-Erkenners einzeln
        List<List<Finding>> kandidaten = new ArrayList<>();
        List<int[]> kandidatenBereiche = new ArrayList<>();
        for (Recognizer erkenner : STANDARD_ERKENNER) {
            List<Finding> k = erkenner.detect(text);
            kandidaten.add(k);
            for (Finding f : k) {
                kandidatenBereiche.add(new int[] {f.start(), f.end()});
            }
        }
        List<Finding> scan = scanner.scan(text);

        // AK-1: Teilstring-Invariante, sortiert, überlappungsfrei, keine Grenze im Surrogatpaar (Scan-Liste)
        if (!invariante(text, scan)) {
            lauf.verletzt("ak1-scan-invariante", it);
        }

        // AK-7 (a): Kandidatenpositionen = Scan-Positionen (beide Richtungen)
        boolean[] kand = abdeckung(text.length(), kandidatenBereiche);
        List<int[]> scanBereiche = new ArrayList<>();
        for (Finding f : scan) {
            scanBereiche.add(new int[] {f.start(), f.end()});
        }
        boolean[] gedeckt = abdeckung(text.length(), scanBereiche);
        boolean kandidatenImScan = true;
        boolean scanNurKandidaten = true;
        for (int i = 0; i < text.length(); i++) {
            if (kand[i] && !gedeckt[i]) {
                kandidatenImScan = false;
            }
            if (gedeckt[i] && !kand[i]) {
                scanNurKandidaten = false;
            }
        }
        if (!kandidatenImScan) {
            lauf.verletzt("ak7a-kandidat-nicht-im-scan", it);
        }
        if (!scanNurKandidaten) {
            lauf.verletzt("ak7e-fremdtext-im-scan", it);
        }
        // AK-7 (e) und (b) bis (d): Scan-Bereiche = verschmolzene Kandidatenbereiche (nur echte Überlappung)
        if (!verschmolzen(kandidatenBereiche).equals(listeAlsString(scanBereiche))) {
            lauf.verletzt("ak7e-bereiche-nicht-verschmolzen", it);
        }

        // Klassen
        if (!scan.isEmpty()) {
            lauf.klasse("scan-fund");
        }
        boolean zusammengelegt = false;
        for (Finding f : scan) {
            if (Finding.SOURCE_MERGED.equals(f.source())) {
                zusammengelegt = true;
            }
        }
        if (zusammengelegt) {
            lauf.klasse("zusammengelegter-fund");
        }
        boolean teilueberlappung = false;
        for (int a = 0; a < kandidaten.size(); a++) {
            for (int b = a + 1; b < kandidaten.size(); b++) {
                for (Finding fa : kandidaten.get(a)) {
                    for (Finding fb : kandidaten.get(b)) {
                        if (teilueberlappt(fa, fb)) {
                            teilueberlappung = true;
                        }
                    }
                }
            }
        }
        if (teilueberlappung) {
            lauf.klasse("teilueberlappung-verschiedener-erkenner");
        }
        for (int i = 0; i + 1 < scan.size(); i++) {
            if (scan.get(i).end() == scan.get(i + 1).start()) {
                lauf.klasse("aneinandergrenzende-scan-funde");
                break;
            }
        }
        if (fall.ungeschlossen()) {
            lauf.klasse("ungeschlossenes-anfuehrungszeichen");
        }
        if (hatSurrogatpaar(text)) {
            lauf.klasse("zeichen-ausserhalb-bmp");
        }
        List<Finding> pfadKandidaten = kandidaten.get(3);
        boolean[] formGesehen = new boolean[FORMEN.length];
        for (int[] block : fall.pfade()) {
            for (Finding c : pfadKandidaten) {
                if (c.start() >= block[1] && c.start() < block[2] && c.source().equals(QUELLEN[block[0]])) {
                    formGesehen[block[0]] = true;
                }
            }
        }
        for (int f = 0; f < FORMEN.length; f++) {
            if (formGesehen[f]) {
                lauf.klasse("pfadform-" + FORMEN[f]);
            }
        }

        // Teilmenge der Scan-Funde plus manuelle Funde
        List<Finding> gewaehlt = new ArrayList<>();
        for (Finding f : scan) {
            if (auswahl.nextInt(3) != 0) {
                gewaehlt.add(f);
            }
        }
        if (!scan.isEmpty() && gewaehlt.size() < scan.size()) {
            lauf.klasse("echte-teilmenge-der-scan-funde");
        }
        int manuelle = 0;
        for (int versuch = 0; versuch < 3; versuch++) {
            Finding m = manuellerFund(auswahl, text, gewaehlt);
            if (m != null) {
                gewaehlt.add(m);
                manuelle++;
            }
        }
        if (manuelle > 0) {
            lauf.klasse("manueller-fund");
        }
        gewaehlt.sort(Comparator.comparingInt(Finding::start));

        // Teilstring-Invariante für die zu pseudonymisierende Liste (AK-1)
        if (!invariante(text, gewaehlt)) {
            lauf.verletzt("ak1-liste-invariante", it);
        }

        // Pseudonymisierung und Rückübersetzung (AK-45)
        PseudonymizationResult pseudo;
        try {
            pseudo = PSEUDONYMIZER.pseudonymize(text, gewaehlt);
        } catch (RuntimeException e) {
            lauf.verletzt("ak45-pseudonymisierung-wirft", it);
            return;
        }
        if (pseudo.text().contains("[[TEXT_")) {
            lauf.klasse("literalschutz");
        }
        ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
        if (!text.equals(zurueck.text())) {
            lauf.verletzt("ak45-roundtrip-ungleich", it);
        }
        if (!zurueck.unresolved().isEmpty()) {
            lauf.verletzt("ak45-meldung", it);
        }
    }

    private static boolean hatSurrogatpaar(String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (Character.isHighSurrogate(text.charAt(i)) && Character.isLowSurrogate(text.charAt(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean invariante(String text, List<Finding> funde) {
        int vorher = 0;
        for (Finding f : funde) {
            if (f.start() < vorher || f.end() > text.length()
                    || !text.substring(f.start(), f.end()).equals(f.originalValue())
                    || paarGetrennt(text, f.start()) || paarGetrennt(text, f.end())) {
                return false;
            }
            vorher = f.end();
        }
        return true;
    }

    /** Erwartete Bereiche: Kandidaten, die mindestens ein Zeichen teilen, werden transitiv verschmolzen. */
    private static String verschmolzen(List<int[]> bereiche) {
        List<int[]> sortiert = new ArrayList<>(bereiche);
        sortiert.sort(Comparator.<int[]>comparingInt(b -> b[0]).thenComparingInt(b -> b[1]));
        List<int[]> ergebnis = new ArrayList<>();
        for (int[] b : sortiert) {
            if (!ergebnis.isEmpty() && b[0] < ergebnis.get(ergebnis.size() - 1)[1]) {
                int[] letzter = ergebnis.get(ergebnis.size() - 1);
                letzter[1] = Math.max(letzter[1], b[1]);
            } else {
                ergebnis.add(new int[] {b[0], b[1]});
            }
        }
        return listeAlsString(ergebnis);
    }

    private static String listeAlsString(List<int[]> bereiche) {
        StringBuilder sb = new StringBuilder();
        for (int[] b : bereiche) {
            sb.append(b[0]).append('-').append(b[1]).append(';');
        }
        return sb.toString();
    }

    /** Manueller Fund an einer Codepunkt-Grenze, überlappungsfrei zu den bereits gewählten Funden, oder null. */
    private static Finding manuellerFund(Random r, String text, List<Finding> vorhanden) {
        if (text.isEmpty()) {
            return null;
        }
        int start = r.nextInt(text.length());
        int ende = Math.min(text.length(), start + 1 + r.nextInt(12));
        if (paarGetrennt(text, start)) {
            start--;
        }
        if (paarGetrennt(text, ende)) {
            ende++;
        }
        if (ende <= start) {
            return null;
        }
        for (Finding f : vorhanden) {
            if (start < f.end() && f.start() < ende) {
                return null;
            }
        }
        String typ = r.nextBoolean() ? "PERSON" : "WERT";
        return Finding.manual(new FindingType(typ), text.substring(start, ende), start, ende);
    }

    // ------------------------------------------------------------------ Tests: Eigenschaften

    @Test
    void ak45_roundtripIstZeichengleich() {
        assertEquals(0, LAUF.anzahl("ak45-roundtrip-ungleich"), LAUF.meldung("ak45-roundtrip-ungleich"));
        assertEquals(0, LAUF.anzahl("ak45-pseudonymisierung-wirft"), LAUF.meldung("ak45-pseudonymisierung-wirft"));
        assertEquals(0, LAUF.anzahl("ausnahme-im-lauf"), LAUF.meldung("ausnahme-im-lauf"));
    }

    @Test
    void ak45_roundtripMeldetKeineUngeloestenPlatzhalter() {
        assertEquals(0, LAUF.anzahl("ak45-meldung"), LAUF.meldung("ak45-meldung"));
    }

    @Test
    void ak1_teilstringInvarianteDerScanFunde() {
        assertEquals(0, LAUF.anzahl("ak1-scan-invariante"), LAUF.meldung("ak1-scan-invariante"));
    }

    @Test
    void ak1_teilstringInvarianteDerPseudonymisiertenListe() {
        assertEquals(0, LAUF.anzahl("ak1-liste-invariante"), LAUF.meldung("ak1-liste-invariante"));
    }

    @Test
    void ak7a_schutzgarantie_jederKandidatIstImScanAbgedeckt() {
        assertEquals(0, LAUF.anzahl("ak7a-kandidat-nicht-im-scan"), LAUF.meldung("ak7a-kandidat-nicht-im-scan"));
    }

    @Test
    void ak7e_keinFremdtextImScan_abgedecktSindNurKandidatenpositionen() {
        assertEquals(0, LAUF.anzahl("ak7e-fremdtext-im-scan"), LAUF.meldung("ak7e-fremdtext-im-scan"));
    }

    @Test
    void ak7e_scanBereicheSindDieVerschmolzenenKandidatenbereiche() {
        // Nur Kandidaten mit gemeinsamem Zeichen werden vereinigt; Angrenzendes bleibt getrennt (AK-7 (b) bis (e)).
        assertEquals(0, LAUF.anzahl("ak7e-bereiche-nicht-verschmolzen"),
                LAUF.meldung("ak7e-bereiche-nicht-verschmolzen"));
    }

    @Test
    void ak45_restoreMitZufallseingabenWirftNie() {
        assertEquals(0, LAUF.anzahl("restore-ausnahme"), "Startwert " + SEED_EINGABEN + ", erste Iteration "
                + (LAUF.verletzungen.containsKey("restore-ausnahme") ? LAUF.verletzungen.get("restore-ausnahme")[0]
                        : -1));
        assertEquals(ANZAHL_EINGABEN, LAUF.eingabenOhneAusnahme, "Startwert " + SEED_EINGABEN);
    }

    // ------------------------------------------------------------------ Tests: Determinismus und Abdeckung

    @Test
    void ak45_zweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE)) {
            a.add(f.text());
        }
        for (Fall f : erzeuge(SEED_TEXTE)) {
            b.add(f.text());
        }
        assertEquals(ANZAHL_TEXTE, a.size());
        assertEquals(a.hashCode(), b.hashCode(), "Hashwert der Textfolge, Startwert " + SEED_TEXTE);
        assertEquals(a, b, "Textfolge, Startwert " + SEED_TEXTE);
        List<String> anders = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE + 1)) {
            anders.add(f.text());
        }
        assertNotEquals(a.hashCode(), anders.hashCode(), "anderer Startwert muss andere Texte liefern");
    }

    @Test
    void fuzzPruftNichtInsLeere_jedeKlasseKommtVor() {
        mindestens("scan-fund", 900);
        mindestens("zusammengelegter-fund", 100);
        mindestens("teilueberlappung-verschiedener-erkenner", 100);
        mindestens("aneinandergrenzende-scan-funde", 1);
        mindestens("literalschutz", 50);
        mindestens("manueller-fund", 300);
        mindestens("ungeschlossenes-anfuehrungszeichen", 200);
        mindestens("zeichen-ausserhalb-bmp", 200);
        mindestens("echte-teilmenge-der-scan-funde", 300);
    }

    @Test
    void fuzzPruftNichtInsLeere_jedePfadformIstQuelleEinesKandidaten() {
        for (String form : FORMEN) {
            mindestens("pfadform-" + form, 50);
        }
    }

    @Test
    void fuzzPruftNichtInsLeere_restoreEingabenWerdenAufgeloestUndGemeldet() {
        mindestens("eingabe-aufgeloest", 100);
        mindestens("eingabe-meldung", 100);
    }

    private static void mindestens(String klasse, int minimum) {
        int anzahl = LAUF.klasse_(klasse);
        assertTrue(anzahl >= minimum, "Klasse " + klasse + " kam in " + anzahl + " von " + ANZAHL_TEXTE
                + " Fällen vor, erwartet mindestens " + minimum + " (Startwert " + SEED_TEXTE + ")");
    }
}
