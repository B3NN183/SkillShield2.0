package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvTestHilfen;
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
 * Rueckuebersetzung. Teil 1b ({@code SEED_TEXTE_KV}): {@value #ANZAHL_TEXTE} Texte mit KV-Bausteinen (alle
 * Kontextwoerter, Trennerformen, U+00A0, Umbrueche, Aufzaehlung, nachgestellter Kontext, ungueltige Pruefziffer,
 * Laengenabweichung, KV-Nummer in URL, Pfad und E-Mail, IK mit Dokumentations-IP), dieselben Eigenschaften wie Teil 1
 * samt Schutzgarantie mit {@code TextScanner.standard().recognizers()} und Waechter der Testdatenregel; Teil 1 bleibt
 * unveraendert (Hashwert {@link #HASH_TEIL_1}). Teil 2: {@value #ANZAHL_EINGABEN} Zufallseingaben an {@code restore}.
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
    /** Teil 1b (T-7): eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit KV-Bausteinen. */
    static final long SEED_TEXTE_KV = 20261122L;
    static final long SEED_AUSWAHL_KV = 515151L;
    static final int MIN_KV_LANR = 150;
    static final int MIN_KV_IK = 150;
    static final int MIN_KV_FRAGLICH = 40;
    static final int MIN_KV_IN_ANDEREM = 80;
    static final int MIN_KV_GLEICHER_BEREICH_IP = 60;

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

    /** Teil 1b: Trenner ohne einzelnes Gruppentrennerzeichen vor einer Ziffer, damit keine Ziffernketten wachsen. */
    private static final String[] TRENNER_KV = {"\n", "\r\n", ", ", "; ", "\t", " x ", " und ", " | "};

    private static final String[] DOMAINS = {"example.invalid", "example.org", "example.net"};
    private static final String[] LOKAL = {"erika.beispiel", "max.mustermann", "a_b", "x+y"};

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** Genau die Erkenner von {@code TextScanner.standard()}, in derselben Reihenfolge. */
    private static final List<Recognizer> STANDARD_ERKENNER = TextScanner.standard().recognizers();

    private static Lauf LAUF;
    private static Lauf LAUF_KV;

    // ------------------------------------------------------------------ Erzeugung

    /** Ein erzeugter Text mit den Bereichen der eingefügten Pfadbausteine (Form, Start, Ende). */
    record Fall(String text, List<int[]> pfade, boolean ungeschlossen) {
    }

    /** Teil 1: die unveraenderte Altfolge ohne KV-Bausteine. */
    static List<Fall> erzeuge(long seed) {
        return erzeuge(seed, false);
    }

    /** Teil 1b: Texte mit KV-Bausteinen (T-7). */
    static List<Fall> erzeugeKv(long seed) {
        return erzeuge(seed, true);
    }

    private static List<Fall> erzeuge(long seed, boolean mitKv) {
        Random r = new Random(seed);
        List<Fall> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeText(r, mitKv));
        }
        return faelle;
    }

    /**
     * Bei {@code mitKv = false} verbraucht der Generator genau dieselben Zufallszahlen wie vor T-7 (Hashwert-Test
     * {@code t7_teil1TextfolgeIstUnveraendert}); die KV-Zweige ziehen nur bei {@code mitKv} Zufallszahlen.
     */
    static Fall erzeugeText(Random r, boolean mitKv) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        boolean[] ungeschlossen = {false};
        int n = 2 + r.nextInt(9);
        for (int i = 0; i < n; i++) {
            int art = r.nextInt(100);
            if (mitKv && art >= 75 && r.nextInt(5) < 3) {
                kvBaustein(r, sb);
            } else if (art < 45) {
                int form = r.nextInt(FORMEN.length);
                int start = sb.length();
                sb.append(pfad(r, form, ungeschlossen));
                pfade.add(new int[] {form, start, sb.length()});
            } else if (art < 55) {
                sb.append(LOKAL[r.nextInt(LOKAL.length)]).append('@').append(DOMAINS[r.nextInt(DOMAINS.length)]);
            } else if (art < 63) {
                sb.append(mitKv ? ipKv(r) : ip(r));
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
            String[] trenner = mitKv ? TRENNER_KV : TRENNER;
            sb.append(trenner[r.nextInt(trenner.length)]);
        }
        return new Fall(sb.toString(), pfade, ungeschlossen[0]);
    }

    private static String ip(Random r) {
        String[] netze = {"192.0.2.", "198.51.100.", "203.0.113."};
        return netze[r.nextInt(netze.length)] + r.nextInt(256);
    }

    // ------------------------------------------------------------------ Teil 1b: KV-Bausteine

    private static final String[] KW_LANR_F = {"LANR", "lanr", "Lanr", "LANR-Nr.", "LANRNR", "Arztnummer", "Arzt-Nr.",
        "Arzt Nummer", "Praxis-LANR", "Lebenslange Arztnummer", "ARZT-NR", "arztnr"};
    private static final String[] KW_IK_F = {"IK", "ik", "Ik", "IK-Nr.", "IKNR", "IK-Nummer", "IKNUMMER",
        "Institutionskennzeichen", "Praxis-IK", "INSTITUTIONSKENNZEICHEN", "iknr"};
    private static final String[] KV_ZWISCHEN = {" ", ": ", ":", " Nr. ", " ist ", " lautet ", " = ", "\u00A0",
        " bitte ", " der Praxis ", "\u00A0\u00A0", "  "};
    private static final String[] GRUPPENTRENNER = {" ", "\u00A0", "-", ".", "/"};
    private static final String[] UMBRUECHE = {"\n", "\r\n", "\r", "\n\n", "\u2028", "\u0085", "\n "};
    private static final String[] LANR_TRENNERFORMEN = {SynthetischeKvNummern.LANR_TRENNER_LEERZEICHEN,
        SynthetischeKvNummern.LANR_TRENNER_UNGLEICHE_GRUPPEN, SynthetischeKvNummern.LANR_TRENNER_NBSP,
        SynthetischeKvNummern.LANR_TRENNER_SCHRAEGSTRICH};
    private static final String[] IK_TRENNERFORMEN = {SynthetischeKvNummern.IK_TRENNER_BINDESTRICH,
        SynthetischeKvNummern.IK_TRENNER_PUNKT, SynthetischeKvNummern.IK_TRENNER_GRUPPE_6_3,
        SynthetischeKvNummern.IK_TRENNER_NBSP};
    private static final String[] LANR_UNGUELTIG = {SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN,
        SynthetischeKvNummern.LANR_UNGUELTIG_VERTAUSCHT, SynthetischeKvNummern.LANR_UNGUELTIG_ZIFFER_GEAENDERT};
    private static final String[] IK_UNGUELTIG = {SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT,
        SynthetischeKvNummern.IK_UNGUELTIG_NULL, SynthetischeKvNummern.IK_UNGUELTIG_VERTAUSCHT};
    private static final String[] LAENGEN = {SynthetischeKvNummern.LAENGE_4, SynthetischeKvNummern.LAENGE_5,
        SynthetischeKvNummern.LAENGE_6, SynthetischeKvNummern.LAENGE_7, SynthetischeKvNummern.LAENGE_8,
        SynthetischeKvNummern.LAENGE_10, SynthetischeKvNummern.LAENGE_12, SynthetischeKvNummern.LAENGE_13};
    /** Letzte Stelle der Doku-IP bei "IK" davor (die Waechterpruefung prueft jeden Text). */
    private static final String[] IP_ENDEN = {"1", "2", "3", "4", "6", "7", "8", "9"};
    private static final String[] AUFZAEHLER = {", ", " und ", "; "};

    private static String waehle(Random r, String[] auswahl) {
        return auswahl[r.nextInt(auswahl.length)];
    }

    private static String kvNummer(Random r, boolean lanr) {
        return lanr ? SynthetischeKvNummern.bildeLanr(r) : SynthetischeKvNummern.bildeIk(r);
    }

    /** Neun Ziffern in 3-3-3 mit dem Trenner {@code t}. */
    private static String gruppiert(String neun, String t) {
        return neun.substring(0, 3) + t + neun.substring(3, 6) + t + neun.substring(6);
    }

    /**
     * IP nur aus zwei Dokumentationsbereichen und so, dass die Ziffern der Adresse nie genau neun ergeben
     * (W\u00E4chter):
     * 192.0.2.x hat h\u00F6chstens acht Ziffern, bei 198.51.100.x ist x mindestens zweistellig.
     */
    private static String ipKv(Random r) {
        if (r.nextBoolean()) {
            return "192.0.2." + r.nextInt(256);
        }
        return "198.51.100." + (10 + r.nextInt(246));
    }

    /** Ein KV-Baustein aus Plan T-7: Kontextw\u00F6rter, Trennerformen, Umbr\u00FCche, Aufz\u00E4hlung. */
    private static void kvBaustein(Random r, StringBuilder sb) {
        boolean lanr = r.nextBoolean();
        String kw = waehle(r, lanr ? KW_LANR_F : KW_IK_F);
        String zw = waehle(r, KV_ZWISCHEN);
        switch (r.nextInt(15)) {
            case 0:
            case 1:
                sb.append(kw).append(zw).append(kvNummer(r, lanr));
                break;
            case 2:
                sb.append(kw).append(zw).append(gruppiert(kvNummer(r, lanr), waehle(r, GRUPPENTRENNER)));
                break;
            case 3:
                sb.append(kw).append(zw).append(waehle(r, lanr ? LANR_TRENNERFORMEN : IK_TRENNERFORMEN));
                break;
            case 4:
                sb.append(kw).append(zw).append(waehle(r, lanr ? LANR_UNGUELTIG : IK_UNGUELTIG));
                break;
            case 5:
                sb.append(kw).append(zw).append(waehle(r, LAENGEN));
                break;
            case 6:
                sb.append(kw).append(' ').append(kvNummer(r, lanr)).append(waehle(r, AUFZAEHLER))
                        .append(kvNummer(r, lanr)).append(waehle(r, AUFZAEHLER)).append(kvNummer(r, lanr));
                break;
            case 7:
                sb.append(kw).append(r.nextBoolean() ? ":" : "").append(waehle(r, UMBRUECHE))
                        .append(kvNummer(r, lanr));
                break;
            case 8:
                sb.append(kvNummer(r, lanr));
                sb.append(r.nextBoolean() ? " (" + kw + ")" : " ist die " + kw);
                break;
            case 9:
                sb.append(kw).append('\u00A0').append(gruppiert(kvNummer(r, lanr), "\u00A0"));
                break;
            case 10:
                sb.append("https://intranet.invalid/praxis?").append(waehle(r, lanr ? KW_LANR_F : KW_IK_F))
                        .append(r.nextBoolean() ? "=" : ":").append(kvNummer(r, lanr)).append("&x=1");
                break;
            case 11:
                sb.append("\"").append(bs("C:\u00A6Praxis\u00A6")).append(kw).append(' ').append(kvNummer(r, lanr))
                        .append(bs("\u00A6x.txt")).append("\"");
                break;
            case 12:
                sb.append(lanr ? "LANR" : "IK").append(r.nextBoolean() ? "-" : "+").append(kvNummer(r, lanr))
                        .append("@example.invalid");
                break;
            case 13:
                sb.append(waehle(r, KW_IK_F)).append(zw).append(r.nextBoolean() ? SynthetischeKvNummern.IK_FORM_DOKU_IP
                        : "198.51.100." + waehle(r, IP_ENDEN));
                break;
            default:
                sb.append(kvNummer(r, lanr));
                break;
        }
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
        final long seed;
        final Map<String, int[]> verletzungen = new LinkedHashMap<>();
        final Map<String, Integer> klassen = new TreeMap<>();
        int eingabenOhneAusnahme;

        Lauf(long seed) {
            this.seed = seed;
        }

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
                return "Startwert " + seed;
            }
            return "Startwert " + seed + ", Eigenschaft " + eigenschaft + ", erste Iteration " + v[0]
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
        LAUF_KV = fuehreKvAus();
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

    /** Teil 1b (T-7): Texte mit KV-Bausteinen; jeder Text wird vor dem Scan mit dem Waechter geprueft. */
    private static Lauf fuehreKvAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV);
        List<Fall> faelle = erzeugeKv(SEED_TEXTE_KV);
        for (int it = 0; it < faelle.size(); it++) {
            Fall fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            try {
                pruefeText(lauf, scanner, auswahl, it, fall, text);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    private static Lauf fuehreAus() {
        Lauf lauf = new Lauf(SEED_TEXTE);
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
        kvKlassen(lauf, kandidaten, scan);
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

    /**
     * KV-Klassen (T-7), je Text hoechstens einmal: Fund vom Typ LANR / IK im Scan, KV-Fund der Stufe fraglich,
     * KV-Kandidat innerhalb eines E-Mail-, URL- oder Pfad-Kandidaten, KV-Kandidat mit demselben Bereich wie ein
     * IP-Kandidat. Indizes wie in {@code TextScanner.standard()}: 0 E-Mail, 1 IPv4, 2 URL, 3 Pfad, 4 LANR, 5 IK.
     */
    private static void kvKlassen(Lauf lauf, List<List<Finding>> kandidaten, List<Finding> scan) {
        boolean lanr = false;
        boolean ik = false;
        boolean fraglich = false;
        for (Finding f : scan) {
            boolean istLanr = FindingType.LANR.equals(f.type());
            boolean istIk = FindingType.IK.equals(f.type());
            lanr |= istLanr;
            ik |= istIk;
            fraglich |= (istLanr || istIk) && f.level() == ConfidenceLevel.FRAGLICH;
        }
        boolean inAnderem = false;
        boolean gleichIp = false;
        for (int k = 4; k <= 5; k++) {
            for (Finding kv : kandidaten.get(k)) {
                for (int a : new int[] {0, 2, 3}) {
                    for (Finding o : kandidaten.get(a)) {
                        inAnderem |= o.start() <= kv.start() && kv.end() <= o.end();
                    }
                }
                for (Finding ip : kandidaten.get(1)) {
                    gleichIp |= ip.start() == kv.start() && ip.end() == kv.end();
                }
            }
        }
        if (lanr) {
            lauf.klasse("kv-fund-lanr");
        }
        if (ik) {
            lauf.klasse("kv-fund-ik");
        }
        if (fraglich) {
            lauf.klasse("kv-fraglich");
        }
        if (inAnderem) {
            lauf.klasse("kv-in-anderem-fund");
        }
        if (gleichIp) {
            lauf.klasse("kv-gleicher-bereich-ip");
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

    /**
     * Vergleicht zwei Textfolgen. Die Meldung nennt nur Startwert und erste abweichende Iteration (nie Texte, anders
     * als {@code assertEquals} auf Listen); die Erwartung ist unveraendert: gleiche Laenge, gleiche Texte.
     */
    private static void assertTextfolgeGleich(List<String> erwartet, List<String> ist, long seed, String name) {
        int gemeinsam = Math.min(erwartet.size(), ist.size());
        int erste = -1;
        for (int i = 0; i < gemeinsam && erste < 0; i++) {
            if (!erwartet.get(i).equals(ist.get(i))) {
                erste = i;
            }
        }
        if (erste < 0 && erwartet.size() != ist.size()) {
            erste = gemeinsam;
        }
        assertEquals(-1, erste, name + ": Startwert " + seed + ", erste abweichende Iteration " + erste);
    }

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
        assertTextfolgeGleich(a, b, SEED_TEXTE, "Textfolge");
        List<String> anders = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE + 1)) {
            anders.add(f.text());
        }
        assertNotEquals(a.hashCode(), anders.hashCode(), "anderer Startwert muss andere Texte liefern");
    }

    /** Hashwert der Teil-1-Textfolge (T-7, vor der Generator-Aenderung am unveraenderten Stand gemessen). */
    static final int HASH_TEIL_1 = 1480527293;

    @Test
    void t7_teil1TextfolgeIstUnveraendert() {
        List<String> texte = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE)) {
            texte.add(f.text());
        }
        assertEquals(HASH_TEIL_1, texte.hashCode(), "Hashwert der Teil-1-Textfolge, Startwert " + SEED_TEXTE);
    }

    // ------------------------------------------------------------------ Teil 1b (T-7): Eigenschaften

    private static void keine(String eigenschaft) {
        assertEquals(0, LAUF_KV.anzahl(eigenschaft), "Teil 1b: " + LAUF_KV.meldung(eigenschaft));
    }

    @Test
    void ak20_kv_waechterSchlaegtBeiKeinemErzeugtenTextAn() {
        keine("waechter-gueltiges-neunerfenster-ohne-00");
    }

    @Test
    void ak20_kv_roundtripIstZeichengleichOhneMeldung() {
        keine("ak45-roundtrip-ungleich");
        keine("ak45-pseudonymisierung-wirft");
        keine("ak45-meldung");
        keine("ausnahme-im-lauf");
    }

    @Test
    void ak22_kv_scanInvarianteUndListenInvariante() {
        keine("ak1-scan-invariante");
        keine("ak1-liste-invariante");
    }

    @Test
    void ak22_kv_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keine("ak7a-kandidat-nicht-im-scan");
    }

    @Test
    void ak22_kv_keinFremdtextImScanUndBereicheSindDieVerschmolzenenKandidaten() {
        keine("ak7e-fremdtext-im-scan");
        keine("ak7e-bereiche-nicht-verschmolzen");
    }

    @Test
    void ak26_kv_zweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (Fall f : erzeugeKv(SEED_TEXTE_KV)) {
            a.add(f.text());
        }
        for (Fall f : erzeugeKv(SEED_TEXTE_KV)) {
            b.add(f.text());
        }
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, b, SEED_TEXTE_KV, "Teil 1b: Textfolge");
        List<String> altfolge = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE_KV)) {
            altfolge.add(f.text());
        }
        assertNotEquals(a.hashCode(), altfolge.hashCode(), "Teil 1b unterscheidet sich von der Altfolge");
    }

    @Test
    void ak26_kv_fuzzPruftNichtInsLeere_jedeKvKlasseKommtVor() {
        mindestens(LAUF_KV, "kv-fund-lanr", MIN_KV_LANR);
        mindestens(LAUF_KV, "kv-fund-ik", MIN_KV_IK);
        mindestens(LAUF_KV, "kv-fraglich", MIN_KV_FRAGLICH);
        mindestens(LAUF_KV, "kv-in-anderem-fund", MIN_KV_IN_ANDEREM);
        mindestens(LAUF_KV, "kv-gleicher-bereich-ip", MIN_KV_GLEICHER_BEREICH_IP);
        mindestens(LAUF_KV, "scan-fund", 900);
        mindestens(LAUF_KV, "zusammengelegter-fund", 100);
        mindestens(LAUF_KV, "manueller-fund", 300);
        mindestens(LAUF_KV, "echte-teilmenge-der-scan-funde", 300);
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
        mindestens(LAUF, klasse, minimum);
    }

    private static void mindestens(Lauf lauf, String klasse, int minimum) {
        int anzahl = lauf.klasse_(klasse);
        assertTrue(anzahl >= minimum, "Klasse " + klasse + " kam in " + anzahl + " von " + ANZAHL_TEXTE
                + " F\u00E4llen vor, erwartet mindestens " + minimum + " (Startwert " + lauf.seed + ")");
    }
}
