package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvTestHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * unveraendert (Hashwert {@link #HASH_TEIL_1}); Teil 1b ist ebenfalls festgehalten ({@link #HASH_TEIL_1B}). Teil 1c
 * ({@code SEED_TEXTE_KV_C}, kv-kontext-luecken T-6, eigener Generator {@link #erzeugeKvC}, Hash
 * {@link #HASH_TEIL_1C}): Sperrschrift beider Arten mit allen Trennern, {@code I K}, Komma oder Semikolon vor dem
 * Kontextwort (auch Folgen), Gegenproben und Initialen; dieselben Eigenschaften wie Teil 1b samt Schutzgarantie, dazu
 * vollstaendiges Ersetzen aller Funde. Teil 1d ({@code SEED_TEXTE_KV_D}, kv-trenner-und-umbruch T-4, eigener
 * Generator {@link #erzeugeKvD}, eigene Records {@link BlockD} und {@link FallD}, Hash {@link #HASH_TEIL_1D}): Texte
 * mit {@code CR UZ+ LF} hinter Kontextwoertern (normal und gesperrt), {@code KW N CR UZ+ LF N2}, Gegenproben und
 * Mischung mit Bausteinen aus 1c; dieselben Eigenschaften wie 1c, dazu der strikte Zaehler "Block ohne Scan-Abdeckung".
 * Teil 1e ({@code SEED_TEXTE_KV_E}, kv-trenner-und-umbruch T-9, eigener Generator {@link #erzeugeKvE}, eigene Records
 * {@link BlockE} und {@link FallE}, Hash {@link #HASH_TEIL_1E}): Weit-Kontextwoerter beider Arten mit zufaelligen
 * Luecken (vor der Nummer, nachgestellt, mit Komma), Gegenproben aus AK-13, AK-14 und AK-17, Initialen und Mischung
 * mit Bausteinen aus 1c und 1d; dieselben Eigenschaften wie 1d, dazu der strikte Zaehler "Block ohne Scan-Abdeckung"
 * fuer nicht markierte Bloecke (markiert wird nach Grenze 14e, {@link #verbindbar}).
 * Teil 1f ({@code SEED_TEXTE_KV_F}, kvnr-bsnr-erkennung T-7, eigener Generator {@link #erzeugeKvF}, eigene Records
 * {@link BlockF}, {@link GegenprobeF} und {@link FallF}, Hash {@link #HASH_TEIL_1F}): KVNR-Bloecke mit allen
 * Kontextwoertern aus D-2, Schreibweisen, Fuellwoertern, Umbruch, Aufzaehlung, nachgestelltem Kontextwort (auch
 * Kompositum), gesperrter Schrift, den Kollisionsfaellen K-1 und K-2 und langen Aufzaehlungen, Gegenproben nach Regel
 * 12, Bestand-Bausteinen (KVNR in oder an E-Mail, Pfad, URL, IP) und Mischung mit Bausteinen aus 1b bis 1e; dieselben
 * Eigenschaften wie 1e, dazu Platzhalter-Konsistenz, Ausgabetext ohne Rest eines Fundes, der strikte Zaehler "Block
 * ohne Scan-Abdeckung" fuer nicht markierte Bloecke und die Markierung dokumentierter Grenzen aus dem Text
 * ({@link #grenzklasseF}); der Bestandsschutz gegen die eingefrorene Kopie steht in {@code KvnrFuzzBestandTest}.
 * Teil 1g ({@code SEED_TEXTE_KV_G}, kvnr-bsnr-erkennung T-11, eigener Generator {@link #erzeugeKvG}, eigene Records
 * {@link BlockG}, {@link GegenprobeG}, {@link BestandG}, {@link RestG} und {@link FallG}, Hash {@link #HASH_TEIL_1G}):
 * KVNR ohne Kontextwort (Teil 1b) mit allen zulaessigen Grenzzeichen, am Textanfang und -ende, mit LANR- oder
 * IK-Kontextwort davor (K-1), Gegenproben aus AK-12 bis AK-14 ohne Block, Bestand-Bausteinen (Nummer in oder an
 * E-Mail, Pfad, URL; Restfund mit genau drei Ziffern; die IP ist kein Bestand-Baustein in {@code BESTAND_G}, sie
 * steht im Block {@code nachbar-ip}) und Mischung mit Bausteinen aus 1b bis 1f. Die Erwartung
 * stammt aus der Konstruktion und aus der unabhaengigen Referenz der Regel 1b; nicht markierte Bloecke ohne
 * Scan-Abdeckung zaehlen strikt 0, markierte Grenzbloecke (G-K9, Nicht-ASCII-Ziffer als Nachbar) einzeln. Der
 * Bestandsschutz gegen die eingefrorene Kopie steht in {@code KvnrOhneKontextFuzzBestandTest}; der ganze Teil gehoert
 * zum 1b-Inventar (Plan Regel 14).
 * Teil 2: {@value #ANZAHL_EINGABEN} Zufallseingaben an {@code restore}.
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
    /** Teil 1c (kv-kontext-luecken T-6): eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit den neuen Formen. */
    static final long SEED_TEXTE_KV_C = 20261123L;
    static final long SEED_AUSWAHL_KV_C = 616161L;
    static final int MIN_KV_C_KOMMA = 400;
    static final int MIN_KV_C_SPERR = 400;
    static final int MIN_KV_C_ZWEI = 300;
    static final int MIN_KV_C_JE_ART = 250;

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
    private static Lauf LAUF_KV_C;
    private static Lauf LAUF_KV_D;
    private static Lauf LAUF_KV_E;
    private static Lauf LAUF_KV_F;
    private static Lauf LAUF_KV_G;

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

    // ------------------------------------------------------------------ Teil 1c: Sperrschrift, Komma (T-6)

    /** Blockarten von Teil 1c: Sperrschrift ab drei Buchstaben, {@code I K}, Komma. */
    static final int BLOCK_SPERR_DREI = 0;
    static final int BLOCK_SPERR_ZWEI = 1;
    static final int BLOCK_KOMMA = 2;
    private static final int ANZAHL_BLOCKARTEN = 3;
    /** Positionen von LANR und IK in {@code STANDARD_ERKENNER} (wie in {@code TextScanner.standard()}). */
    private static final int ERKENNER_LANR = 4;
    private static final int ERKENNER_IK = 5;

    /**
     * Ein erwarteter Fund einer gueltigen Nummer in einem Text von Teil 1c (Konstruktion als Zaehlgrundlage der
     * Klassen): Blockart, Art der Nummer, Bereich der Nummer im Text. {@code grenze} markiert Bloecke, die
     * absichtlich an einer dokumentierten Grenze liegen und deshalb keinen Scan-Fund haben muessen.
     */
    record BlockC(int blockart, boolean lanr, int start, int ende, boolean grenze) {
        BlockC markiert() {
            return new BlockC(blockart, lanr, start, ende, true);
        }
    }

    /** Ein Text von Teil 1c mit den Bloecken seiner Konstruktion (getrennt von den Pfadbloecken von {@link Fall}). */
    record FallC(String text, List<BlockC> bloecke) {
    }

    private static final String NB = String.valueOf((char) 0xA0);
    private static final String DUENN_C = String.valueOf((char) 0x2009);
    private static final String NNBSP_C = String.valueOf((char) 0x202F);
    private static final String GESCH_C = String.valueOf((char) 0x2011);
    private static final String HALBG_C = String.valueOf((char) 0x2013);
    /** Die acht Sperr-Trenner (Plan D-1). */
    private static final String[] SPERR_TRENNER_C = {" ", ".", "-", NB, DUENN_C, NNBSP_C, GESCH_C, HALBG_C};
    private static final String[] SPERR_LANR_C = {"LANR", "LANRNR", "ARZTNUMMER", "ARZTNR"};
    private static final String[] SPERR_IK_C = {"IK", "IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"};
    private static final String[] SPERR_IK_DREI_C = {"IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"};
    private static final String[] PRAEFIX_C = {"", "", "Praxis ", "Praxis-", "Rechnung an ", "der "};
    private static final String[] DIR_C = {" ", ": ", "=", ":", "\t", " : ", "", NB, "  ", " Nr ", "-Nr. ",
        "-Nummer: ", " - ", "= "};
    private static final String[] WS_C = {"", " ", "  ", "\t", NB, DUENN_C, NNBSP_C, " \t"};
    private static final String[] INNEN_C = {", ", "; ", " / ", " und ", ",", ";", " , ", ", und "};
    /** Trenner zwischen Bausteinen von Teil 1c: nie ein einzelnes Gruppentrennerzeichen zwischen Ziffern. */
    private static final String[] TRENNER_C = {"\n", "\r\n", "; ", ". ", "! ", "\t", " | ", " x ", ", "};
    private static final String[] INITIALEN_C = {"I. K.", "A. B.", "I. K. Mustermann", "Prof. I. K. Mustermann",
        "L. A. N. R.", "I.K.", "I-K", "A. I. K."};
    private static final String[] UNSICHTBAR_C = {String.valueOf((char) 0xAD), String.valueOf((char) 0x200B),
        String.valueOf((char) 0x200C), String.valueOf((char) 0x2060), String.valueOf((char) 0xFEFF)};
    /** Ohne Doppelklammern (kein Literalschutz): die Luecken zwischen den Funden bleiben unveraendert. */
    private static final String[] SONDER_C = {"Hallo", "Text", ",", ".", "(", ")", "\"", "'", "$1", "%", "\\",
        String.valueOf((char) 0xE4), new String(Character.toChars(0x1F600)), "Frau Beispiel", "Rechnung"};

    /** Teil 1c: Texte mit Sperrschrift, {@code I K}, Komma vor dem Kontextwort, Gegenproben und Initialen. */
    static List<FallC> erzeugeKvC(long seed) {
        Random r = new Random(seed);
        List<FallC> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeTextC(r));
        }
        return faelle;
    }

    /**
     * Ein Text aus 3 bis 10 Bausteinen mit den Bloecken je erwartetem Fund einer gueltigen Nummer. Der Trenner
     * {@code " x "} haengt einen Einzelbuchstaben an eine benachbarte Sperrfolge. Als absichtliche Grenze (14e)
     * markiert werden genau zwei Faelle: (a) Sperrbloecke, deren Sperrfolge ohne Praefix unmittelbar auf
     * {@code " x "} folgt, und (b) Kommabloecke, deren gesperrtes nachgestelltes Kontextwort unmittelbar vor
     * {@code " x "} endet (keine schliessende Klammer dazwischen; ein unsichtbares Zeichen dazwischen zaehlt
     * nicht, der Scanner ignoriert es; maximale Folge {@code L A N R X N}). Die Markierung zieht keine Zufallszahlen.
     */
    static FallC erzeugeTextC(Random r) {
        StringBuilder sb = new StringBuilder();
        List<BlockC> bloecke = new ArrayList<>();
        int n = 3 + r.nextInt(8);
        boolean nachX = false;
        for (int i = 0; i < n; i++) {
            int vorher = bloecke.size();
            int form = kvBausteinC(r, sb, bloecke);
            int bis = bloecke.size();
            if (r.nextInt(6) == 0) {
                sb.append(waehle(r, UNSICHTBAR_C));
            }
            String trenner = waehle(r, TRENNER_C);
            boolean vorX = " x ".equals(trenner);
            for (int b = vorher; b < bis; b++) {
                boolean komma = bloecke.get(b).blockart() == BLOCK_KOMMA;
                boolean grenze = komma ? vorX && form == FORM_KOMMA_GESPERRT_ENDE
                        : nachX && form == FORM_SPERR_OHNE_PRAEFIX;
                if (grenze) {
                    bloecke.set(b, bloecke.get(b).markiert());
                }
            }
            nachX = " x ".equals(trenner);
            sb.append(trenner);
        }
        return new FallC(sb.toString(), bloecke);
    }

    /** Das Wort gesperrt: je Buchstabe zufaellige Schreibweise, dazwischen genau ein Sperr-Trenner. */
    private static String sperrFolge(Random r, String wort, boolean punktEnde) {
        boolean gemischt = r.nextBoolean();
        String fest = waehle(r, SPERR_TRENNER_C);
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            if (i > 0) {
                f.append(gemischt ? waehle(r, SPERR_TRENNER_C) : fest);
            }
            char c = wort.charAt(i);
            f.append(r.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return punktEnde ? f + "." : f.toString();
    }

    /** Beliebige Nummer der Art: meist gueltig, sonst ungueltig, Laenge oder mit Gruppentrennern. */
    private static String nummerC(Random r, boolean lanr) {
        switch (r.nextInt(6)) {
            case 0:
                return waehle(r, lanr ? LANR_UNGUELTIG : IK_UNGUELTIG);
            case 1:
                return waehle(r, LAENGEN);
            case 2:
                return waehle(r, lanr ? LANR_TRENNERFORMEN : IK_TRENNERFORMEN);
            default:
                return kvNummer(r, lanr);
        }
    }

    /** Eine gueltige, schlichte Nummer an der aktuellen Stelle; der erwartete Fund wird als Block vermerkt. */
    private static void fundNummerC(Random r, StringBuilder sb, List<BlockC> bloecke, int blockart, boolean lanr) {
        int start = sb.length();
        sb.append(kvNummer(r, lanr));
        bloecke.add(new BlockC(blockart, lanr, start, sb.length(), false));
    }

    /** Nachgestelltes Kontextwort der Art, normal (ohne Vorwort) oder gesperrt ab drei Buchstaben. */
    private static String nachgestelltesKwC(Random r, boolean lanr) {
        return nachgestelltesKwC(r, lanr, new boolean[1]);
    }

    /** Wie oben; {@code gesperrt[0]} wird gesetzt, wenn das Kontextwort gesperrt gewaehlt wurde. */
    private static String nachgestelltesKwC(Random r, boolean lanr, boolean[] gesperrt) {
        if (r.nextInt(100) < 30) {
            gesperrt[0] = true;
            return sperrFolge(r, waehle(r, lanr ? SPERR_LANR_C : SPERR_IK_DREI_C), false);
        }
        String kw = waehle(r, lanr ? KW_LANR_F : KW_IK_F);
        while (kw.startsWith("Praxis") || kw.startsWith("Lebenslange")) {
            kw = waehle(r, lanr ? KW_LANR_F : KW_IK_F);
        }
        return kw;
    }

    /** Form eines Bausteins fuer die Grenz-Markierung: kein Sonderfall. */
    private static final int FORM_ANDERE = 0;
    /** Sperrbaustein ohne Praefix: die Sperrfolge steht am Anfang des Bausteins. */
    private static final int FORM_SPERR_OHNE_PRAEFIX = 1;
    /** Kommabaustein, der unmittelbar mit einem gesperrten nachgestellten Kontextwort endet. */
    private static final int FORM_KOMMA_GESPERRT_ENDE = 2;

    /** Haengt einen Baustein an und liefert seine Form ({@code FORM_*}); die Zufallsfolge ist die der Erzeugung. */
    private static int kvBausteinC(Random r, StringBuilder sb, List<BlockC> bloecke) {
        boolean lanr = r.nextBoolean();
        switch (r.nextInt(21)) {
            case 0:
            case 1:
            case 2:
            case 3:
                // Sperrschrift ab drei Buchstaben, Nummer direkt dahinter
                String praefix = waehle(r, PRAEFIX_C);
                sb.append(praefix)
                        .append(sperrFolge(r, waehle(r, lanr ? SPERR_LANR_C : SPERR_IK_C), r.nextInt(4) == 0))
                        .append(waehle(r, DIR_C));
                fundNummerC(r, sb, bloecke, BLOCK_SPERR_DREI, lanr);
                return praefix.isEmpty() ? FORM_SPERR_OHNE_PRAEFIX : FORM_ANDERE;
            case 4:
            case 5:
            case 6:
                // I K (zwei Buchstaben): nur die unmittelbar folgende Nummer, fraglich
                String praefixZwei = waehle(r, PRAEFIX_C);
                sb.append(praefixZwei).append(sperrFolge(r, "IK", r.nextInt(4) == 0))
                        .append(waehle(r, DIR_C));
                fundNummerC(r, sb, bloecke, BLOCK_SPERR_ZWEI, false);
                return praefixZwei.isEmpty() ? FORM_SPERR_OHNE_PRAEFIX : FORM_ANDERE;
            case 7:
            case 8:
            case 9:
                // Nummer, Komma oder Semikolon, Leerraum, optional Klammer, Kontextwort (normal oder gesperrt)
                return kommaBlock(r, sb, bloecke, lanr, 1);
            case 10:
            case 11:
                return kommaBlock(r, sb, bloecke, lanr, 2 + r.nextInt(2));
            case 12:
                // Komma mit beliebigen Nummernformen (ohne Vermerk)
                sb.append(nummerC(r, lanr)).append(waehle(r, WS_C)).append(r.nextBoolean() ? ',' : ';')
                        .append(waehle(r, WS_C)).append(nachgestelltesKwC(r, lanr));
                break;
            case 13:
            case 14:
            case 15:
                gegenprobeC(r, sb, lanr);
                break;
            case 16:
                sb.append(waehle(r, INITIALEN_C)).append(r.nextBoolean() ? " " + nummerC(r, lanr) : "");
                break;
            case 17:
                // Bestandsform: normales Kontextwort mit Nummer
                sb.append(waehle(r, lanr ? KW_LANR_F : KW_IK_F)).append(waehle(r, KV_ZWISCHEN))
                        .append(nummerC(r, lanr));
                break;
            case 18:
                sb.append(nummerC(r, lanr)).append(", ").append(sperrFolge(r, lanr ? "LANR" : "IKNR", false));
                break;
            case 19:
                umgebungC(r, sb);
                break;
            default:
                sb.append(waehle(r, SONDER_C));
                break;
        }
        return FORM_ANDERE;
    }

    /** Umgebung ohne KV-Bezug: Pfad, E-Mail, IP, URL. */
    private static void umgebungC(Random r, StringBuilder sb) {
        switch (r.nextInt(4)) {
            case 0:
                sb.append(pfadOhneAnfuehrung(r, r.nextInt(FORMEN.length)));
                break;
            case 1:
                sb.append(waehle(r, LOKAL)).append('@').append(waehle(r, DOMAINS));
                break;
            case 2:
                sb.append(ipKv(r));
                break;
            default:
                sb.append(url(r));
                break;
        }
    }

    /** {@code N, KW}, {@code N; (KW)}, {@code N1, N2, KW}: gueltige Nummern, genau ein Komma vor dem Kontextwort. */
    private static int kommaBlock(Random r, StringBuilder sb, List<BlockC> bloecke, boolean lanr, int anzahl) {
        for (int i = 0; i < anzahl; i++) {
            if (i > 0) {
                sb.append(waehle(r, INNEN_C));
            }
            fundNummerC(r, sb, bloecke, BLOCK_KOMMA, lanr);
        }
        sb.append(waehle(r, WS_C)).append(r.nextBoolean() ? ',' : ';').append(waehle(r, WS_C));
        int klammer = r.nextInt(3);
        sb.append(klammer == 0 ? "(" : klammer == 1 ? "[" : "");
        boolean[] gesperrt = new boolean[1];
        sb.append(nachgestelltesKwC(r, lanr, gesperrt));
        boolean geschlossen = false;
        if (klammer < 2 && r.nextInt(4) != 0) {
            sb.append(klammer == 0 ? ")" : "]");
            geschlossen = true;
        }
        return gesperrt[0] && !geschlossen ? FORM_KOMMA_GESPERRT_ENDE : FORM_ANDERE;
    }

    /**
     * Gegenproben aus AK-6, AK-7, AK-18 (Plan Kap. 3.1, 3.2.4): Texte nahe an den neuen Regeln, die sie nicht
     * ausloesen sollen. Teil 1c prueft hier nur Roundtrip, Schutzgarantie und vollstaendiges Ersetzen, keine
     * Nicht-Erkennung (die uebrigen Regeln, etwa das Komma vor dem Kontextwort, duerfen Funde erzeugen).
     * Die Nicht-Erkennung pruefen die Korpus- und Eigenschaftstests.
     */
    private static void gegenprobeC(Random r, StringBuilder sb, boolean lanr) {
        String kw = nachgestelltesKwC(r, lanr);
        String w = lanr ? "LANR" : "IKNR";
        String nummer = nummerC(r, lanr);
        switch (r.nextInt(16)) {
            case 0:
                sb.append(nummer).append(",, ").append(kw);
                break;
            case 1:
                sb.append(nummer).append(", und ").append(kw);
                break;
            case 2:
                sb.append(nummer).append(" / ").append(kw);
                break;
            case 3:
                sb.append(nummer).append(" und ").append(kw);
                break;
            case 4:
                sb.append(nummer).append(": ").append(kw);
                break;
            case 5:
                sb.append(nummer).append(". ").append(kw);
                break;
            case 6:
                sb.append("I. K. ").append(nummer);
                break;
            case 7:
                sb.append("L. A. N. R. ").append(nummer);
                break;
            case 8:
                sb.append("A ").append(sperrFolge(r, "IK", false)).append(' ').append(nummer);
                break;
            case 9:
                sb.append(sperrFolge(r, w + "X", false)).append(' ').append(nummer);
                break;
            case 10:
                // Teilgruppe (Grenze 14a)
                sb.append(lanr ? "LA NR " : "I KNR ").append(nummer);
                break;
            case 11:
                sb.append(sperrFolge(r, w, false)).append("\n\n").append(nummer);
                break;
            case 12:
                sb.append(nummer).append(" (").append(sperrFolge(r, "IK", false)).append(')');
                break;
            case 13:
                sb.append(sperrFolge(r, "IK", false)).append(" der Praxis ").append(nummer);
                break;
            case 14:
                sb.append(sperrFolge(r, w + w, false)).append(' ').append(nummer);
                break;
            default:
                sb.append(sperrFolge(r, "IKEA", false)).append(' ').append(nummer);
                break;
        }
    }

    // ------------------------------------------------------------ Teil 1d: CR UZ+ LF (kv-trenner-und-umbruch T-4)

    /** Teil 1d: eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit {@code CR UZ+ LF} und Mischbausteinen aus 1c. */
    static final long SEED_TEXTE_KV_D = 20261124L;
    static final long SEED_AUSWAHL_KV_D = 717171L;

    /**
     * Blockarten von Teil 1d, je ein erwarteter Fund einer gueltigen Nummer: {@code KW(:) CR UZ+ LF N} mit normalem
     * Kontextwort, dasselbe mit gesperrt geschriebenem Kontextwort ab drei Buchstaben, und {@code KW N CR UZ+ LF N2}
     * (die erste Nummer direkt hinter dem Kontextwort, die zweite hinter dem Umbruch).
     */
    static final int BLOCK_D_KW = 0;
    static final int BLOCK_D_SPERR = 1;
    static final int BLOCK_D_ERSTE = 2;
    static final int BLOCK_D_ZWEITE = 3;
    private static final int ANZAHL_BLOCKARTEN_D = 4;
    private static final String[] BLOCK_NAMEN_D = {"kw", "sperr", "erste", "zweite"};
    private static final int MAX_UZ_D = 3;
    private static final String NEL_D = String.valueOf((char) 0x85);

    /** Alle sieben unsichtbaren Zeichen von AK-1 und ein Zeichen ausserhalb der BMP (U+E0020, Kategorie Format). */
    private static final String[] UZ_D = unsichtbareD();

    private static String[] unsichtbareD() {
        List<String> zeichen = new ArrayList<>(List.of(KvTestHilfen.uzAk1Texte()));
        zeichen.add(new String(Character.toChars(0xE0020)));
        return zeichen.toArray(new String[0]);
    }

    /**
     * Ein erwarteter Fund einer gueltigen Nummer in einem Text von Teil 1d: Blockart ({@code BLOCK_D_*}), Art der
     * Nummer, Zahl der unsichtbaren Zeichen im Umbruch unmittelbar vor der Nummer (0 bei {@code BLOCK_D_ERSTE}),
     * Bereich der Nummer. {@code grenze} markiert einen Block an einer dokumentierten Grenze (siehe
     * {@link #erzeugeTextD}); er muss keinen Scan-Fund haben.
     */
    record BlockD(int blockart, boolean lanr, int uz, int start, int ende, boolean grenze) {
        BlockD markiert() {
            return new BlockD(blockart, lanr, uz, start, ende, true);
        }
    }

    /**
     * Ein Text von Teil 1d mit den Bloecken seiner Konstruktion und den Namen der eingebauten Gegenproben (ohne
     * Block, ohne Erwartung an den Scan). Eigener Typ, getrennt von {@link Fall} und {@link FallC}.
     */
    record FallD(String text, List<BlockD> bloecke, List<String> gegenproben) {
    }

    /** Teil 1d: Texte mit {@code CR UZ+ LF} in Fund-Bloecken, Gegenproben und Mischung mit Bausteinen aus 1c. */
    static List<FallD> erzeugeKvD(long seed) {
        Random r = new Random(seed);
        List<FallD> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeTextD(r));
        }
        return faelle;
    }

    /**
     * Ein Text aus 3 bis 10 Bausteinen. Die Bausteine von 1c ziehen aus einem eigenen {@code Random}, dessen Startwert
     * je Text eine Zufallszahl des Hauptstroms ist; ihre Bloecke werden nicht erfasst. Als bekannte Grenze (14e)
     * markiert wird genau ein Fall: ein Sperrblock ({@code BLOCK_D_SPERR}) ohne Praefix, dessen Sperrfolge
     * unmittelbar auf den Trenner {@code " x "} des vorigen Bausteins folgt (der Einzelbuchstabe haengt an der
     * Sperrfolge, maximale Folge {@code x L A N R}). Ein unsichtbares Zeichen zwischen Baustein und Trenner zaehlt
     * nicht. Die Markierung zieht keine Zufallszahlen.
     */
    static FallD erzeugeTextD(Random r) {
        StringBuilder sb = new StringBuilder();
        List<BlockD> bloecke = new ArrayList<>();
        List<String> gegenproben = new ArrayList<>();
        Random mischung = new Random(r.nextLong());
        int n = 3 + r.nextInt(8);
        boolean nachX = false;
        for (int i = 0; i < n; i++) {
            int vorher = bloecke.size();
            int form = kvBausteinD(r, mischung, sb, bloecke, gegenproben);
            int bis = bloecke.size();
            if (r.nextInt(6) == 0) {
                sb.append(waehle(r, UNSICHTBAR_C));
            }
            String trenner = waehle(r, TRENNER_C);
            for (int b = vorher; b < bis; b++) {
                if (nachX && form == FORM_SPERR_OHNE_PRAEFIX && bloecke.get(b).blockart() == BLOCK_D_SPERR) {
                    bloecke.set(b, bloecke.get(b).markiert());
                }
            }
            nachX = " x ".equals(trenner);
            sb.append(trenner);
        }
        return new FallD(sb.toString(), bloecke, gegenproben);
    }

    /** {@code CR}, {@code anzahl} zufaellige unsichtbare Zeichen, {@code LF}. */
    private static String crUzLf(Random r, int anzahl) {
        StringBuilder umbruch = new StringBuilder("\r");
        for (int i = 0; i < anzahl; i++) {
            umbruch.append(waehle(r, UZ_D));
        }
        return umbruch.append('\n').toString();
    }

    private static void fundNummerD(Random r, StringBuilder sb, List<BlockD> bloecke, int blockart, boolean lanr,
            int uz) {
        int start = sb.length();
        sb.append(kvNummer(r, lanr));
        bloecke.add(new BlockD(blockart, lanr, uz, start, sb.length(), false));
    }

    /**
     * Haengt einen Baustein von Teil 1d an und liefert seine Form ({@code FORM_*} wie bei {@link #kvBausteinC});
     * Bloecke und Gegenproben werden vermerkt.
     */
    private static int kvBausteinD(Random r, Random mischung, StringBuilder sb, List<BlockD> bloecke,
            List<String> gegenproben) {
        boolean lanr = r.nextBoolean();
        int uz = 1 + r.nextInt(MAX_UZ_D);
        int wahl = r.nextInt(100);
        if (wahl < 22) {
            sb.append(waehle(r, lanr ? KW_LANR_F : KW_IK_F)).append(r.nextBoolean() ? ":" : "")
                    .append(crUzLf(r, uz));
            fundNummerD(r, sb, bloecke, BLOCK_D_KW, lanr, uz);
            return FORM_ANDERE;
        }
        if (wahl < 42) {
            String praefix = waehle(r, PRAEFIX_C);
            sb.append(praefix).append(sperrFolge(r, waehle(r, lanr ? SPERR_LANR_C : SPERR_IK_DREI_C), false))
                    .append(r.nextBoolean() ? ":" : "").append(crUzLf(r, uz));
            fundNummerD(r, sb, bloecke, BLOCK_D_SPERR, lanr, uz);
            return praefix.isEmpty() ? FORM_SPERR_OHNE_PRAEFIX : FORM_ANDERE;
        }
        if (wahl < 57) {
            sb.append(waehle(r, lanr ? KW_LANR_F : KW_IK_F)).append(r.nextBoolean() ? " " : ": ");
            fundNummerD(r, sb, bloecke, BLOCK_D_ERSTE, lanr, 0);
            sb.append(crUzLf(r, uz));
            fundNummerD(r, sb, bloecke, BLOCK_D_ZWEITE, lanr, uz);
            return FORM_ANDERE;
        }
        if (wahl < 72) {
            gegenprobeD(r, sb, lanr, gegenproben);
            return FORM_ANDERE;
        }
        kvBausteinC(mischung, sb, new ArrayList<>());
        return FORM_ANDERE;
    }

    /**
     * Gegenproben aus dem Plan (T-4): Leerzeile aus zwei Umbruechen, {@code I K:} mit Umbruch, Nummer vor dem
     * Umbruch und Kontextwort danach, {@code CR} Leerzeichen {@code LF}, {@code CR} U+0085. Sie haben keinen Block:
     * Teil 1d prueft hier nur Roundtrip, Schutzgarantie und vollstaendiges Ersetzen, keine Nicht-Erkennung (diese
     * pruefen die Korpus- und Eigenschaftstests, ein Nachbar-Baustein kann den Kontext neu eroeffnen).
     */
    private static void gegenprobeD(Random r, StringBuilder sb, boolean lanr, List<String> gegenproben) {
        String kw = waehle(r, lanr ? KW_LANR_F : KW_IK_F);
        String nummer = kvNummer(r, lanr);
        int uz = 1 + r.nextInt(MAX_UZ_D);
        switch (r.nextInt(5)) {
            case 0:
                sb.append(kw).append(':').append(crUzLf(r, uz)).append(crUzLf(r, 1 + r.nextInt(MAX_UZ_D)))
                        .append(nummer);
                gegenproben.add("leerzeile");
                break;
            case 1:
                sb.append("I K:").append(crUzLf(r, uz)).append(kvNummer(r, false));
                gegenproben.add("ik-zwei");
                break;
            case 2:
                sb.append(nummer).append(crUzLf(r, uz)).append(kw);
                gegenproben.add("nummer-vor-umbruch");
                break;
            case 3:
                sb.append(kw).append(':').append("\r \n").append(nummer);
                gegenproben.add("cr-leerzeichen-lf");
                break;
            default:
                sb.append(kw).append(':').append('\r').append(NEL_D).append(nummer);
                gegenproben.add("cr-nel");
                break;
        }
    }

    // ------------------------------------------------------------ Teil 1e: Weit-Folgen (kv-trenner-und-umbruch T-9)

    /** Teil 1e: eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit Weit-Kontextwoertern und Mischbausteinen. */
    static final long SEED_TEXTE_KV_E = 20261125L;
    static final long SEED_AUSWAHL_KV_E = 818181L;

    /**
     * Blockarten von Teil 1e, je ein erwarteter Fund einer gueltigen Nummer: Weit-Kontextwort ab vier Buchstaben mit
     * mindestens einer erweiterten Luecke vor der Nummer (0,80); dieselbe Nummer vor dem Weit-Kontextwort, in Klammern
     * oder ohne (nachgestellt, 0,80); dieselbe Nummer mit genau einem Komma oder Semikolon vor dem Weit-Kontextwort
     * (0,60).
     */
    static final int BLOCK_E_WEIT = 0;
    static final int BLOCK_E_NACH = 1;
    static final int BLOCK_E_KOMMA = 2;
    private static final int ANZAHL_BLOCKARTEN_E = 3;
    private static final String[] BLOCK_NAMEN_E = {"weit", "nachgestellt", "komma"};
    private static final String[] KLASSE_E = {"kv-weit-fund", "kv-weit-nachgestellt", "kv-weit-komma"};
    private static final double[] KONFIDENZ_E = {0.80, 0.80, 0.60};

    /** Luecken nach D-2: (a) streng wie im Bestand, (b) zwei bis drei WS4, (c) Punkt und ein bis drei WS4, (d) Tab. */
    private static final int LUECKE_A = 0;
    private static final int LUECKE_B = 1;
    private static final int LUECKE_C = 2;
    private static final int LUECKE_D = 3;
    private static final String[] LUECKEN_NAMEN_E = {"a", "b", "c", "d"};
    private static final String[] WS4_E = {" ", NB, DUENN_C, NNBSP_C};

    /** Unsichtbare Zeichen der Kategorie Format (Teil 1e streut sie in Luecken und an Woerter). */
    private static final String[] UZ_E = unsichtbareE();

    private static String[] unsichtbareE() {
        List<String> zeichen = new ArrayList<>(List.of(UZ_D).subList(0, 5));
        zeichen.add(UZ_D[UZ_D.length - 1]);
        zeichen.add(String.valueOf((char) 0x200C));
        return zeichen.toArray(new String[0]);
    }

    /**
     * Ein erwarteter Fund einer gueltigen Nummer in einem Text von Teil 1e: Blockart ({@code BLOCK_E_*}), Art der
     * Nummer, Maske der erweiterten Luecken im Weit-Kontextwort (Bit je {@code LUECKE_*}), Bereich der Nummer,
     * Bereich der Buchstaben des Weit-Kontextworts (ohne Schlusspunkt und unsichtbare Zeichen davor und danach).
     * {@code grenze} markiert einen Block, dessen Weit-Folge per Konstruktion ueber eine Luecke nach D-2 mit einem
     * benachbarten Einzelbuchstaben verbunden sein kann (Grenze 14e, siehe {@link #verbindbar}).
     */
    record BlockE(int blockart, boolean lanr, int luecken, int start, int ende, int wortStart, int wortEnde,
            boolean grenze) {
        BlockE markiert() {
            return new BlockE(blockart, lanr, luecken, start, ende, wortStart, wortEnde, true);
        }
    }

    /** Ein Text von Teil 1e mit den Bloecken seiner Konstruktion und den Namen der Gegenproben (ohne Block). */
    record FallE(String text, List<BlockE> bloecke, List<String> gegenproben) {
    }

    /** Teil 1e: Texte mit Weit-Kontextwoertern, Gegenproben und Mischung mit Bausteinen aus 1c und 1d. */
    static List<FallE> erzeugeKvE(long seed) {
        Random r = new Random(seed);
        List<FallE> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeTextE(r));
        }
        return faelle;
    }

    /**
     * Ein Text aus 3 bis 10 Bausteinen. Die Bausteine von 1c und 1d ziehen aus einem eigenen {@code Random}, dessen
     * Startwert je Text eine Zufallszahl des Hauptstroms ist; ihre Bloecke werden nicht erfasst. Nach dem Aufbau
     * werden die Bloecke markiert, deren Weit-Folge ueber eine Luecke nach D-2 mit einem davor oder dahinter
     * stehenden Einzelbuchstaben verbunden sein kann ({@link #verbindbar}). Die Markierung zieht keine Zufallszahlen.
     */
    static FallE erzeugeTextE(Random r) {
        StringBuilder sb = new StringBuilder();
        List<BlockE> bloecke = new ArrayList<>();
        List<String> gegenproben = new ArrayList<>();
        Random mischung = new Random(r.nextLong());
        int n = 3 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            kvBausteinE(r, mischung, sb, bloecke, gegenproben);
            if (r.nextInt(6) == 0) {
                sb.append(waehle(r, UNSICHTBAR_C));
            }
            sb.append(waehle(r, TRENNER_C));
        }
        String text = sb.toString();
        List<BlockE> endgueltig = new ArrayList<>(bloecke.size());
        for (BlockE block : bloecke) {
            endgueltig.add(verbindbar(text, block) ? block.markiert() : block);
        }
        return new FallE(text, endgueltig, gegenproben);
    }

    private static String uzE(Random r) {
        return UZ_E[r.nextInt(UZ_E.length)];
    }

    /** Eine Luecke der Art {@code art}, mit Wahrscheinlichkeit 1/5 mit einem unsichtbaren Zeichen darin. */
    private static String lueckeE(Random r, int art) {
        StringBuilder g = new StringBuilder();
        switch (art) {
            case LUECKE_A:
                g.append(waehle(r, SPERR_TRENNER_C));
                break;
            case LUECKE_B:
                for (int i = 2 + r.nextInt(2); i > 0; i--) {
                    g.append(waehle(r, WS4_E));
                }
                break;
            case LUECKE_C:
                g.append('.');
                for (int i = 1 + r.nextInt(3); i > 0; i--) {
                    g.append(waehle(r, WS4_E));
                }
                break;
            default:
                g.append('\t');
                break;
        }
        if (r.nextInt(5) == 0) {
            g.insert(r.nextInt(g.length() + 1), uzE(r));
        }
        return g.toString();
    }

    /**
     * Haengt das Wort an: je Buchstabe zufaellige Schreibweise, zwischen zwei Buchstaben eine Luecke, jede einzeln
     * aus (a) bis (d) gewuerfelt, mindestens eine erweitert; optional ein Schlusspunkt; unsichtbare Zeichen nie
     * zwischen zwei Buchstaben (das waere ein Lauf mit zwei Buchstaben, Grenze 14a). {@code info}:
     * {Maske der erweiterten Luecken, Start, Ende der Buchstaben}.
     */
    private static void weitWortE(Random r, StringBuilder sb, String wort, boolean schlusspunkt, int[] info) {
        int n = wort.length();
        int[] arten = new int[n - 1];
        boolean erweitert = false;
        for (int i = 0; i < arten.length; i++) {
            arten[i] = r.nextInt(4);
            erweitert |= arten[i] != LUECKE_A;
        }
        if (!erweitert) {
            arten[r.nextInt(arten.length)] = 1 + r.nextInt(3);
        }
        int maske = 0;
        for (int art : arten) {
            maske |= art == LUECKE_A ? 0 : 1 << art;
        }
        if (r.nextInt(6) == 0) {
            sb.append(uzE(r));
        }
        info[0] = maske;
        info[1] = sb.length();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(lueckeE(r, arten[i - 1]));
            }
            char c = wort.charAt(i);
            sb.append(r.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        info[2] = sb.length();
        if (schlusspunkt) {
            sb.append('.');
        }
        if (r.nextInt(6) == 0) {
            sb.append(uzE(r));
        }
    }

    /** Eine gueltige Nummer an der aktuellen Stelle; der erwartete Fund wird als Block vermerkt. */
    private static void fundNummerE(Random r, StringBuilder sb, List<BlockE> bloecke, int blockart, boolean lanr,
            int[] info) {
        int start = sb.length();
        sb.append(kvNummer(r, lanr));
        bloecke.add(new BlockE(blockart, lanr, info[0], start, sb.length(), info[1], info[2], false));
    }

    /** Haengt einen Baustein von Teil 1e an; Bloecke und Gegenproben werden vermerkt. */
    private static void kvBausteinE(Random r, Random mischung, StringBuilder sb, List<BlockE> bloecke,
            List<String> gegenproben) {
        boolean lanr = r.nextBoolean();
        String wort = waehle(r, lanr ? SPERR_LANR_C : SPERR_IK_DREI_C);
        int[] info = new int[3];
        int wahl = r.nextInt(100);
        if (wahl < 30) {
            sb.append(waehle(r, PRAEFIX_C));
            weitWortE(r, sb, wort, r.nextInt(3) == 0, info);
            sb.append(waehle(r, DIR_C));
            fundNummerE(r, sb, bloecke, BLOCK_E_WEIT, lanr, info);
        } else if (wahl < 42) {
            // Nummer, dahinter das Weit-Kontextwort in Klammern oder ohne Klammer und ohne Komma
            int start = sb.length();
            sb.append(kvNummer(r, lanr));
            int ende = sb.length();
            boolean klammer = r.nextBoolean();
            sb.append(klammer ? " (" : " ");
            weitWortE(r, sb, wort, r.nextInt(3) == 0, info);
            sb.append(klammer ? ")" : "");
            bloecke.add(new BlockE(BLOCK_E_NACH, lanr, info[0], start, ende, info[1], info[2], false));
        } else if (wahl < 50) {
            // Nummer, genau ein Komma oder Semikolon, Luecke, optional Klammer, Weit-Kontextwort
            int start = sb.length();
            sb.append(kvNummer(r, lanr));
            int ende = sb.length();
            sb.append(r.nextBoolean() ? ',' : ';').append(waehle(r, WS_C));
            int klammer = r.nextInt(3);
            sb.append(klammer == 0 ? "(" : klammer == 1 ? "[" : "");
            weitWortE(r, sb, wort, r.nextInt(3) == 0, info);
            if (klammer < 2 && r.nextInt(4) != 0) {
                sb.append(klammer == 0 ? ")" : "]");
            }
            bloecke.add(new BlockE(BLOCK_E_KOMMA, lanr, info[0], start, ende, info[1], info[2], false));
        } else if (wahl < 68) {
            gegenprobeE(r, sb, lanr, gegenproben);
        } else if (wahl < 75) {
            sb.append(waehle(r, INITIALEN_C)).append(r.nextBoolean() ? " " + nummerC(r, lanr) : "");
        } else if (wahl < 95) {
            kvBausteinC(mischung, sb, new ArrayList<>());
        } else {
            kvBausteinD(mischung, mischung, sb, new ArrayList<>(), new ArrayList<>());
        }
    }

    private static final String[] IK_ZWEI_E = {"I. K. ", "I. K.: ", "i. k. ", "I. K ", "I  K ", "I   K ", "I\tK ",
        "I.  K. ", "Dr. I. K. ", "Prof. I. K. ", "I. K.-Nr. ", "I. K. Mustermann ", "I" + NB + NB + "K "};
    private static final String[] MAXIMAL_LANR_E = {"A. L. A. N. R. ", "L. A. N. R. X ", "L. A. N. ",
        "L. A. N. X. R. ", "L. A. N. R. L. A. N. R. ", "LA. NR. ", "L. ANR ", "Herr A. L. A. N. R. "};
    private static final String[] MAXIMAL_IK_E = {"I. K. N. ", "A. I. K. N. R. X ", "I. K. N. X. R. ",
        "IK. NR. ", "I. KNR "};

    /** Die Buchstaben des Wortes, je durch {@code trenner} getrennt. */
    private static String gesperrtE(String wort, String trenner) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            s.append(i > 0 ? trenner : "").append(wort.charAt(i));
        }
        return s.toString();
    }

    /**
     * Gegenproben aus AK-13, AK-14 und AK-17 (ohne Block): das Zwei-Buchstaben-Wort mit Trennern ausserhalb des
     * Bestands, vier Leerraumzeichen, zwei Tabs, Zeilenumbruch, Satzzeichen, Punkt vor der Luecke, Bindestrich mit
     * Leerzeichen, die maximale Folge und das Weit-Kontextwort ohne Nummer. Teil 1e prueft hier nur Roundtrip,
     * Schutzgarantie und vollstaendiges Ersetzen (die Nicht-Erkennung pruefen die Korpus- und Eigenschaftstests, ein
     * Nachbar-Baustein kann den Kontext neu eroeffnen).
     */
    private static void gegenprobeE(Random r, StringBuilder sb, boolean lanr, List<String> gegenproben) {
        String wort = lanr ? "LANR" : "IKNR";
        String nummer = kvNummer(r, lanr);
        switch (r.nextInt(9)) {
            case 0:
                sb.append(waehle(r, IK_ZWEI_E)).append(kvNummer(r, false));
                gegenproben.add("ik-zwei-buchstaben");
                break;
            case 1:
                sb.append(gesperrtE(wort, WS4_E[r.nextInt(4)] + WS4_E[r.nextInt(4)] + WS4_E[r.nextInt(4)]
                        + WS4_E[r.nextInt(4)])).append(' ').append(nummer);
                gegenproben.add("vier-leerraumzeichen");
                break;
            case 2:
                sb.append(gesperrtE(wort, r.nextBoolean() ? "\t\t" : "\t ")).append(' ').append(nummer);
                gegenproben.add("zwei-tabs");
                break;
            case 3:
                sb.append(lanr ? "L. A." : "I. K.").append(r.nextBoolean() ? "\n" : "\r\n")
                        .append(lanr ? "N. R. " : "N R ").append(nummer);
                gegenproben.add("zeilenumbruch");
                break;
            case 4:
                sb.append(gesperrtE(wort, waehle(r, new String[] {", ", "; ", "/", "_", ": "}))).append(' ')
                        .append(nummer);
                gegenproben.add("satzzeichen");
                break;
            case 5:
                boolean doppelt = r.nextBoolean();
                sb.append(gesperrtE(wort, doppelt ? ".. " : " .")).append(doppelt ? ".. " : " ").append(nummer);
                gegenproben.add("punkt-vor-luecke");
                break;
            case 6:
                sb.append(gesperrtE(wort, r.nextBoolean() ? " - " : " " + (char) 0x2013 + " ")).append(' ')
                        .append(nummer);
                gegenproben.add("bindestrich-luecke");
                break;
            case 7:
                sb.append(waehle(r, lanr ? MAXIMAL_LANR_E : MAXIMAL_IK_E)).append(nummer);
                gegenproben.add("maximale-folge");
                break;
            default:
                sb.append(gesperrtE(wort, ". ")).append('.').append(r.nextBoolean() ? " Ende" : "");
                gegenproben.add("ohne-nummer");
                break;
        }
    }

    private static boolean istWs4E(char c) {
        return c == ' ' || c == (char) 0xA0 || c == (char) 0x2009 || c == (char) 0x202F;
    }

    private static boolean istStrengerTrennerE(char c) {
        return istWs4E(c) || c == '.' || c == '-' || c == (char) 0x2011 || c == (char) 0x2013;
    }

    /** Der Text ohne unsichtbare Zeichen (je Codepunkt, unabhaengig vom Produkt). */
    private static String ohneUnsichtbareE(String text) {
        StringBuilder s = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!KvTestHilfen.unsichtbar(cp)) {
                s.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return s.toString();
    }

    /** Steht an {@code pos} ein Buchstabe, der nicht Teil eines laengeren Buchstabenlaufs ist? */
    private static boolean einzelbuchstabeE(String s, int pos) {
        return pos >= 0 && pos < s.length() && Character.isLetter(s.charAt(pos))
                && (pos == 0 || !Character.isLetter(s.charAt(pos - 1)))
                && (pos + 1 >= s.length() || !Character.isLetter(s.charAt(pos + 1)));
    }

    /** Endet {@code s} mit einem Einzelbuchstaben und einer Luecke nach D-2 (a) bis (d)? */
    private static boolean endetMitEinzelbuchstabeUndLueckeE(String s) {
        int i = s.length();
        if (i > 0 && s.charAt(i - 1) == '\t') {
            return einzelbuchstabeE(s, i - 2);
        }
        int k = 0;
        while (k < i && istWs4E(s.charAt(i - 1 - k))) {
            k++;
        }
        if (k > 3) {
            return false;
        }
        int j = i - k;
        if (k > 0 && j > 0 && s.charAt(j - 1) == '.') {
            return einzelbuchstabeE(s, j - 2);
        }
        if (k > 0) {
            return einzelbuchstabeE(s, j - 1);
        }
        return i > 0 && istStrengerTrennerE(s.charAt(i - 1)) && einzelbuchstabeE(s, i - 2);
    }

    /** Beginnt {@code s} mit einer Luecke nach D-2 (a) bis (d) und dann einem Einzelbuchstaben? */
    private static boolean beginntMitLueckeUndEinzelbuchstabeE(String s) {
        int i = 0;
        if (s.startsWith("\t")) {
            i = 1;
        } else if (s.startsWith(".")) {
            i = 1;
            while (i < s.length() && istWs4E(s.charAt(i))) {
                i++;
            }
            if (i > 4) {
                return false;
            }
        } else if (!s.isEmpty() && istWs4E(s.charAt(0))) {
            while (i < s.length() && istWs4E(s.charAt(i))) {
                i++;
            }
            if (i > 3) {
                return false;
            }
        } else if (!s.isEmpty() && istStrengerTrennerE(s.charAt(0))) {
            i = 1;
        }
        return i > 0 && einzelbuchstabeE(s, i);
    }

    /**
     * Grenze 14e fuer Teil 1e: Kann die Weit-Folge des Blocks ueber eine Luecke nach D-2 mit einem Einzelbuchstaben
     * davor (Bloecke aller Arten) oder dahinter (nachgestellte Bloecke; bei {@code BLOCK_E_WEIT} folgt die Nummer)
     * verbunden sein? Dann ergibt die maximale Folge nicht mehr genau das Kontextwort. Unsichtbare Zeichen zaehlen
     * nicht. Konstruktion nach dem Text, unabhaengig vom Produkt.
     */
    static boolean verbindbar(String text, BlockE block) {
        int von = Math.max(0, block.wortStart() - 64);
        if (endetMitEinzelbuchstabeUndLueckeE(ohneUnsichtbareE(text.substring(von, block.wortStart())))) {
            return true;
        }
        if (block.blockart() == BLOCK_E_WEIT) {
            return false;
        }
        int bis = Math.min(text.length(), block.wortEnde() + 64);
        return beginntMitLueckeUndEinzelbuchstabeE(ohneUnsichtbareE(text.substring(block.wortEnde(), bis)));
    }

    // ------------------------------------------------------------ Teil 1f: KVNR mit Kontextwort (kvnr-bsnr T-7)

    // Aufbau dieses Abschnitts: zuerst der Pruefcode (Lauf und Pruefung je Text, ab fuehreKvFAus), danach ab
    // "Teil 1f: eigene Startwerte" die Seeds, die Blockarten, die Records und der Generator. Das ist anders als in den
    // Teilen 1d und 1e, wo die Konstanten am Abschnittsanfang stehen. Einen technischen Grund gibt es nicht: Java
    // erlaubt Vorwaertsbezuege auf statische Konstanten, Methoden und Records, und die Seeds sind Konstanten zur
    // Uebersetzungszeit; die Reihenfolge der Deklarationen aendert weder die Textfolge noch den Hashwert
    // HASH_TEIL_1F noch eine Mindestzahl. Wer die Seeds oder Blockarten sucht, findet sie hinter dem Pruefcode.

    /**
     * Teil 1f (kvnr-bsnr-erkennung T-7): dieselben Eigenschaften wie Teil 1e ({@code pruefeText}: Invarianten,
     * Roundtrip mit Teilmenge und manuellen Funden, Kandidaten gegen Scan) plus {@link #pruefeKvF}: Klassen nach
     * Konstruktion, Zaehler der Bloecke ohne Scan-Abdeckung, Gegenproben, vollstaendiges Ersetzen, Konsistenz der
     * Platzhalter und Ausgabetext ohne Rest eines Fundes. Die Pruefungen {@code ak7a} und {@code ak7e-bereiche} von
     * {@code pruefeText} gelten wegen des Zuschneidens am Bestand nur mit Ausnahme (siehe
     * {@link #pruefeKvFText}).
     */
    private static Lauf fuehreKvFAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_F);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV_F);
        List<FallF> faelle = erzeugeKvF(SEED_TEXTE_KV_F);
        for (int it = 0; it < faelle.size(); it++) {
            FallF fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            if (KvnrSynthetischWaechter.pruefe(text).verstoesse() != 0) {
                lauf.verletzt("waechter-kvnr-synthetisch", it);
                continue;
            }
            if (text.contains("[[")) {
                lauf.verletzt("generator-doppelklammer", it);
                continue;
            }
            try {
                pruefeKvFText(lauf, scanner, auswahl, it, text);
                pruefeKvF(lauf, scanner, it, fall);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    private static boolean bestandstyp(Finding f) {
        return f.type() == FindingType.EMAIL || f.type() == FindingType.IP || f.type() == FindingType.URL
                || f.type() == FindingType.PFAD;
    }

    /**
     * Die allgemeinen Eigenschaften ({@code pruefeText}) mit zwei Anpassungen an das Zuschneiden der KVNR-Kandidaten
     * am Bestand (Nachtrag 3 und 4 zu T-6): (1) Eine Kandidatenposition, die kein Fund abdeckt, ist nur dann kein
     * Verstoss, wenn sie in einem Kandidaten eines nachrangigen Erkenners liegt, der einen Fund von E-Mail, IP,
     * URL oder Pfad schneidet (Klasse {@code kandidat-position-am-bestand-zugeschnitten}, je Text einmal); jede andere
     * ist {@code ak7a-kandidat-nicht-im-scan}, auch jede Luecke mit drei oder mehr ASCII-Ziffern in einer
     * zusammenhaengenden, von keinem Fund abgedeckten Strecke ({@link #luckeMitDreiZiffernF}). (2) Dass die
     * Scan-Bereiche die verschmolzenen Kandidatenbereiche sind, gilt nur fuer Texte, in denen kein KVNR-Kandidat
     * zugeschnitten wird (Klasse {@code bereiche-wegen-zuschnitt} sonst); die Gleichheit der ganzen Liste mit der
     * unabhaengigen Nachrechnung prueft {@code KvnrFuzzBestandTest}.
     */
    private static void pruefeKvFText(Lauf lauf, TextScanner scanner, Random auswahl, int it, String text) {
        Lauf roh = new Lauf(lauf.seed);
        pruefeText(roh, scanner, auswahl, it, new Fall(text, List.of(), false), text);
        for (String name : roh.verletzungen.keySet()) {
            if (!"ak7a-kandidat-nicht-im-scan".equals(name) && !"ak7e-bereiche-nicht-verschmolzen".equals(name)) {
                lauf.verletzt(name, it);
            }
        }
        for (String name : roh.klassen.keySet()) {
            lauf.klasse(name);
        }
        if (roh.anzahl("ak7a-kandidat-nicht-im-scan") > 0) {
            List<Finding> scan = scanner.scan(text);
            List<int[]> bereiche = new ArrayList<>();
            for (Finding f : scan) {
                bereiche.add(new int[] {f.start(), f.end()});
            }
            boolean[] gedeckt = abdeckung(text.length(), bereiche);
            boolean unerklaert = false;
            boolean erklaert = false;
            for (Recognizer erkenner : STANDARD_ERKENNER) {
                for (Finding k : erkenner.detect(text)) {
                    boolean luecke = false;
                    for (int i = k.start(); i < k.end(); i++) {
                        luecke |= !gedeckt[i];
                    }
                    boolean amBestand = false;
                    for (Finding f : scan) {
                        amBestand |= bestandstyp(f) && f.start() < k.end() && k.start() < f.end();
                    }
                    boolean ausnahme = erkenner.nachrangig() && amBestand;
                    boolean zuGrosse = luecke && ausnahme && luckeMitDreiZiffernF(text, k, gedeckt);
                    unerklaert |= luecke && (!ausnahme || zuGrosse);
                    erklaert |= luecke && ausnahme && !zuGrosse;
                }
            }
            if (unerklaert) {
                lauf.verletzt("ak7a-kandidat-nicht-im-scan", it);
            }
            if (erklaert) {
                lauf.klasse("kandidat-position-am-bestand-zugeschnitten");
            }
        }
        if (roh.anzahl("ak7e-bereiche-nicht-verschmolzen") > 0) {
            if (KvnrKollisionsNachrechnung.kvnrZuschnitt(text).zugeschnitten() == 0) {
                lauf.verletzt("ak7e-bereiche-nicht-verschmolzen", it);
            } else {
                lauf.klasse("bereiche-wegen-zuschnitt");
            }
        }
    }

    /**
     * Ob im Kandidaten eine zusammenhaengende, von keinem Fund abgedeckte Strecke mindestens drei ASCII-Ziffern
     * enthaelt (Nachtrag 2 zu T-7, Punkt 4, nach Nachtrag 3 zu T-6: nur kuerzere Luecken darf der Zuschnitt am
     * Bestand lassen). Die Positionen des Bestands (E-Mail, IP, URL, Pfad) sind Scan-Funde, also abgedeckt.
     */
    static boolean luckeMitDreiZiffernF(String text, Finding kandidat, boolean[] gedeckt) {
        int ziffern = 0;
        for (int i = kandidat.start(); i < kandidat.end(); i++) {
            if (gedeckt[i]) {
                ziffern = 0;
            } else {
                ziffern += text.charAt(i) >= '0' && text.charAt(i) <= '9' ? 1 : 0;
                if (ziffern >= SCHWELLE_LUECKE_ZIFFERN_F) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Klassen je Text, hoechstens einmal: Ein Kandidat des KVNR-Erkenners liegt genau auf der Nummer eines Blocks, hat
     * die aus der Konstruktion erwartete Konfidenz und die Quelle der Nummer (gueltig, Pruefziffer ungueltig, Form
     * abweichend); gezaehlt wird nach Blockart ({@code kvnr-fund-<blockart>}), nach
     * Kontextwortgruppe und Stufe ({@code kvnr-wort-<gruppe>[-<stufe>]}), Stufe und Quelle. Je Block zaehlt
     * {@code block-ohne-scan-abdeckung} die nicht markierten Bloecke, deren Nummernbereich von keinem Scan-Fund ganz
     * abgedeckt ist (strikt 0 erwartet); markierte Bloecke (dokumentierte Grenze, {@link #grenzklasseF}) zaehlen
     * getrennt, auch je Grenzklasse. Gegenproben: allein gescannt liefert der KVNR-Erkenner keinen Fund. Danach
     * werden alle Scan-Funde pseudonymisiert: jeder Fund ist vollstaendig durch einen Platzhalter ersetzt, derselbe
     * Wert (je Typ) hat denselben Platzhalter, verschiedene Werte haben verschiedene (auch dieselbe Nummer mit und
     * ohne Trenner), die Form eines KVNR-Fundes (Buchstabe und Ziffern) steht nicht mehr im Ausgabetext, und die
     * Rueckuebersetzung ist zeichengleich ohne Meldung.
     */
    static void pruefeKvF(Lauf lauf, TextScanner scanner, int it, FallF fall) {
        String text = fall.text();
        Recognizer kvnr = STANDARD_ERKENNER.get(ERKENNER_KVNR);
        List<Finding> kandidaten = kvnr.detect(text);
        Set<String> gesehen = new TreeSet<>();
        for (int i = 0; i < fall.verworfene(); i++) {
            lauf.klasse("baustein-verworfen");
        }
        for (int i = 0; i < fall.aufgegebene(); i++) {
            lauf.klasse("baustein-aufgegeben");
        }
        for (BlockF block : fall.bloecke()) {
            if (block.konfidenz() < 0) {
                continue;
            }
            for (Finding c : kandidaten) {
                if (c.start() == block.start() && c.end() == block.ende() && c.confidence() == block.konfidenz()
                        && block.quelle().equals(c.source())) {
                    String stufe = ConfidenceLevel.of(block.konfidenz()).name().toLowerCase(Locale.ROOT);
                    gesehen.add("kvnr-fund");
                    gesehen.add("kvnr-fund-" + BLOCK_NAMEN_F[block.blockart()]);
                    gesehen.add("kvnr-wort-" + GRUPPEN_NAMEN_F[block.gruppe()]);
                    gesehen.add("kvnr-wort-" + GRUPPEN_NAMEN_F[block.gruppe()] + "-" + stufe);
                    gesehen.add("kvnr-stufe-" + stufe);
                    gesehen.add("kvnr-quelle-" + block.quelle());
                }
            }
        }
        for (GegenprobeF gegenprobe : fall.gegenproben()) {
            gesehen.add("gegenprobe-" + gegenprobe.name());
            if (!kvnr.detect(gegenprobe.text()).isEmpty()) {
                lauf.verletzt("gegenprobe-mit-kvnr-fund", it);
            }
        }
        for (String baustein : fall.bausteine()) {
            gesehen.add("baustein-" + baustein);
        }
        if (!KvnrBsnrHilfen.kvnrFormEngTreffer(text).isEmpty()) {
            gesehen.add("enge-form-im-block");
        }
        if (engeFormenAusserhalb(text, fall.bloecke()) != 0) {
            lauf.verletzt("kvnr-form-ausserhalb-der-bloecke", it);
        }
        List<Finding> scan = scanner.scan(text);
        for (Finding f : scan) {
            if (FindingType.KVNR.equals(f.type())) {
                gesehen.add("scan-kvnr-fund");
                gesehen.add(KvnrRecognizer.SOURCE_REST.equals(f.source()) ? "scan-kvnr-restfund" : "scan-kvnr-voll");
            }
        }
        for (String klasse : gesehen) {
            lauf.klasse(klasse);
        }
        for (BlockF block : fall.bloecke()) {
            String art = BLOCK_NAMEN_F[block.blockart()];
            lauf.klasse("bloecke-" + art);
            boolean abgedeckt = imScanAbgedeckt(scan, block.start(), block.ende());
            if (!block.grenze().isEmpty()) {
                lauf.klasse("block-markiert");
                lauf.klasse("block-markiert-" + block.grenze());
                lauf.klasse(abgedeckt ? "markiert-mit-abdeckung" : "markiert-ohne-abdeckung");
                lauf.klasse((abgedeckt ? "markiert-mit-abdeckung-" : "markiert-ohne-abdeckung-") + block.grenze());
            } else if (!abgedeckt) {
                lauf.klasse("block-ohne-scan-abdeckung");
                lauf.klasse("block-ohne-scan-abdeckung-" + art);
            }
        }
        pruefeErsetzenF(lauf, it, text, scan);
    }

    private static void pruefeErsetzenF(Lauf lauf, int it, String text, List<Finding> scan) {
        PseudonymizationResult alle = PSEUDONYMIZER.pseudonymize(text, scan);
        if (!alleFundeErsetzt(text, scan, alle.text())) {
            lauf.verletzt("ak23-fund-nicht-vollstaendig-ersetzt", it);
        }
        ReidentificationResult zurueck = REIDENTIFIER.restore(alle.text(), alle.mapping());
        if (!text.equals(zurueck.text())) {
            lauf.verletzt("ak17-roundtrip-alle-funde-ungleich", it);
        }
        if (!zurueck.unresolved().isEmpty()) {
            lauf.verletzt("ak17-roundtrip-meldung", it);
        }
        List<String> platzhalter = platzhalterJeFund(text, scan, alle.text());
        if (platzhalter == null) {
            lauf.verletzt("ak17-platzhalter-nicht-lesbar", it);
            return;
        }
        Map<String, String> platzhalterJeWert = new LinkedHashMap<>();
        Map<String, String> wertJePlatzhalter = new LinkedHashMap<>();
        Map<String, String> kompaktJeWert = new LinkedHashMap<>();
        boolean wiederholung = false;
        boolean trennerVariante = false;
        for (int i = 0; i < scan.size(); i++) {
            Finding f = scan.get(i);
            String wert = f.type().name() + "|" + f.originalValue();
            String vorher = platzhalterJeWert.put(wert, platzhalter.get(i));
            if (vorher != null && !vorher.equals(platzhalter.get(i))) {
                lauf.verletzt("ak17-konsistenz", it);
            }
            String vorherWert = wertJePlatzhalter.put(platzhalter.get(i), wert);
            if (vorherWert != null && !vorherWert.equals(wert)) {
                lauf.verletzt("ak17-konsistenz", it);
            }
            if (FindingType.KVNR.equals(f.type())) {
                wiederholung |= vorher != null;
                String kompakt = kompaktF(f.originalValue());
                String anderer = kompaktJeWert.put(kompakt, wert);
                trennerVariante |= anderer != null && !anderer.equals(wert);
                if (!rest0F(text, scan, alle.text(), f)) {
                    lauf.verletzt("ak17-form-eines-fundes-im-ausgabetext", it);
                }
            }
        }
        if (wiederholung) {
            lauf.klasse("kvnr-gleicher-wert-gleicher-platzhalter");
        }
        if (trennerVariante) {
            lauf.klasse("kvnr-mit-und-ohne-trenner-verschiedene-platzhalter");
        }
    }

    /** Buchstabe (gross) und Ziffern einer Nummer ohne Trenner. */
    private static String kompaktF(String nummer) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nummer.length(); i++) {
            char c = nummer.charAt(i);
            if (c >= '0' && c <= '9' || KvnrFensterfernPruefung.asciiBuchstabe(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Ist von einem Fund kein Rest im Ausgabetext uebrig? Geprueft werden der Originalwert (bei Buchstabe vorn und
     * mindestens sechs Ziffern), jede Ziffernkette des Fundes ab sechs Ziffern, jedes Stueck von sechs Ziffern daraus
     * (damit auch ein Rest ab sechs Ziffern auffaellt) und, steht ein ASCII-Buchstabe unmittelbar davor, der
     * Buchstabe mit der Kette. Jedes dieser Muster steht im Ausgabetext nur so oft wie im Text ausserhalb aller Funde
     * (die Pruefung zieht keine Zufallszahlen).
     */
    private static boolean rest0F(String text, List<Finding> funde, String ausgabe, Finding fund) {
        String wert = fund.originalValue();
        List<String> muster = new ArrayList<>();
        boolean buchstabeVorn = !wert.isEmpty() && KvnrFensterfernPruefung.asciiBuchstabe(wert.charAt(0));
        if (buchstabeVorn && laengsteZiffernfolge(wert) >= MIN_KETTE_F) {
            muster.add(wert);
        }
        Matcher kette = ZIFFERNKETTE_F.matcher(wert);
        while (kette.find()) {
            muster.add(kette.group());
            for (int i = 0; i + MIN_KETTE_F <= kette.group().length(); i++) {
                muster.add(kette.group().substring(i, i + MIN_KETTE_F));
            }
            if (kette.start() > 0 && KvnrFensterfernPruefung.asciiBuchstabe(wert.charAt(kette.start() - 1))) {
                muster.add(wert.substring(kette.start() - 1, kette.end()));
            }
        }
        for (String m : muster) {
            if (vorkommenF(ausgabe, m, List.of()) != vorkommenF(text, m, funde)) {
                return false;
            }
        }
        return true;
    }

    /** Zahl der Vorkommen von {@code muster} in {@code s}, die sich mit keinem der Funde ueberschneiden. */
    private static int vorkommenF(String s, String muster, List<Finding> funde) {
        int zahl = 0;
        for (int i = s.indexOf(muster); i >= 0; i = s.indexOf(muster, i + 1)) {
            boolean frei = true;
            for (Finding f : funde) {
                frei &= f.end() <= i || f.start() >= i + muster.length();
            }
            zahl += frei ? 1 : 0;
        }
        return zahl;
    }

    /** Die Platzhalter in der Reihenfolge der Funde aus dem Ausgabetext, oder {@code null}, wenn er nicht passt. */
    static List<String> platzhalterJeFund(String text, List<Finding> funde, String ausgabe) {
        List<String> platzhalter = new ArrayList<>();
        int pos = 0;
        int vorher = 0;
        for (Finding f : funde) {
            String luecke = text.substring(vorher, f.start());
            if (!ausgabe.startsWith(luecke, pos)) {
                return null;
            }
            pos += luecke.length();
            int ende = ausgabe.indexOf("]]", pos + 2);
            if (!ausgabe.startsWith("[[", pos) || ende < 0) {
                return null;
            }
            platzhalter.add(ausgabe.substring(pos, ende + 2));
            pos = ende + 2;
            vorher = f.end();
        }
        return platzhalter;
    }

    /** Teil 1f: eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit KVNR-Bloecken und Mischbausteinen. */
    static final long SEED_TEXTE_KV_F = 20261126L;
    static final long SEED_AUSWAHL_KV_F = 929292L;
    /** Position des KVNR-Erkenners in {@code STANDARD_ERKENNER} (wie in {@code TextScanner.standard()}). */
    private static final int ERKENNER_KVNR = 6;

    /**
     * Blockarten von Teil 1f, je ein erwarteter Fund einer KVNR-Form: Kontextwort und Nummer direkt, mit
     * Fuellwoertern, mit Umbruch, Aufzaehlung, nachgestellt, gesperrt geschriebenes Kontextwort, KVNR-Kontextwort
     * vor LANR-/IK-Kontextwort und KVNR-Form (Kollisionsfall K-1), dasselbe vor einer Ziffernfolge (K-2), die
     * Grenzfaelle "Kontextwort direkt am Buchstaben" und "langer Pfad zwischen Kontextwort und Nummer" und die
     * Wiederholung derselben Nummer.
     */
    static final int BLOCK_F_DIREKT = 0;
    static final int BLOCK_F_FUELLWOERTER = 1;
    static final int BLOCK_F_UMBRUCH = 2;
    static final int BLOCK_F_AUFZAEHLUNG = 3;
    static final int BLOCK_F_NACHGESTELLT = 4;
    static final int BLOCK_F_SPERR = 5;
    static final int BLOCK_F_K1 = 6;
    static final int BLOCK_F_K2 = 7;
    static final int BLOCK_F_AM_BUCHSTABEN = 8;
    static final int BLOCK_F_LANGER_PFAD = 9;
    static final int BLOCK_F_WIEDERHOLUNG = 10;
    static final int BLOCK_F_LISTE_LANG = 11;
    private static final int ANZAHL_BLOCKARTEN_F = 12;
    private static final String[] BLOCK_NAMEN_F = {"direkt", "fuellwoerter", "umbruch", "aufzaehlung", "nachgestellt",
        "sperr", "k1", "k2", "am-buchstaben", "langer-pfad", "wiederholung", "liste-lang"};
    /** Gruppen der Kontextwoerter aus D-2; die Gruppe "sperr" ist das gesperrt geschriebene Kontextwort. */
    private static final String[] GRUPPEN_NAMEN_F = {"kvnr", "versicherten", "krankenversichert", "egk", "kv-nr",
        "kompositum", "sperr"};
    private static final int GRUPPE_SPERR_F = 6;
    private static final int GRUPPE_KOMPOSITUM_F = 5;
    /** Grenzklassen (dokumentierte Grenzen, Markierung aus dem Text): Name und Plan-Bezug im Javadoc von pruefeKvF. */
    static final String GRENZE_AM_BUCHSTABEN = "kw-am-buchstaben";
    static final String GRENZE_LANGER_PFAD = "langer-pfad";
    static final String GRENZE_SPERR_EINZELBUCHSTABE = "sperr-einzelbuchstabe";
    static final String GRENZE_KV_VORWORT_ZIFFERN = "kv-vorwort-ziffern";
    /** Ereignisse der Fuellwortzaehlung: Zeilenumbruch, Wort oder Nummer (samt Gruppentrennern), Satzzeichen. */
    private static final Pattern EREIGNIS_F = Pattern.compile(
            "(\\r\\n|\\R)|(\\p{L}*\\p{N}+(?:[ .\\-/\\xA0]\\p{N}+)*|\\p{L}+)|([.!?])");
    private static final int MAX_FUELLWOERTER_F = 3;
    private static final int MAX_UMBRUECHE_F = 1;
    private static final int FENSTER_KW_F = 40;
    private static final int MIN_KETTE_F = 6;
    private static final Pattern ZIFFERNKETTE_F = Pattern.compile("[0-9]{" + MIN_KETTE_F + ",}");
    private static final int SCHWELLE_LUECKE_ZIFFERN_F = 3;
    private static final Pattern NACHGESTELLT_F =
            Pattern.compile("[ \\t]*(?:\\([ \\t]*|[,;][ \\t]*)([\\p{L}.\\-]+(?:[ \\t]+(?:[Nn][Rr]|[Nn]ummer))?)");
    private static final Pattern KV_VORWORT_F = Pattern.compile(
            "(?<![\\p{L}\\p{N}])[Kk][Vv][\\s\\-](?:[Nn][Rr]|[Nn][Uu][Mm][Mm][Ee][Rr])(?![\\p{L}\\p{N}])");
    private static final double KONF_DIREKT_F = 0.95;
    private static final double KONF_WEITER_F = 0.80;
    private static final double KONF_KOMMA_F = 0.60;
    private static final int MAX_VERSUCHE_F = 60;
    /**
     * Mindestlaenge eines Pfad- oder URL-Fundes zwischen Kontextwort und Nummer fuer die Markierung
     * {@code langer-pfad}. Die Laenge ist nur eine Naeherung: Nach Nachtrag 2 zu T-7, Punkt 1, entscheiden die
     * Fuellwortzaehlung des Scanners (jedes Pfad- oder URL-Segment zaehlt als Fuellwort, ab dem vierten ist der
     * Kontext erloschen) und die Satzende-Regel (der Punkt der Dateiendung hinter einem Wort mit mehr als vier
     * Buchstaben gilt als Satzende). Die Regel der Markierung bleibt unveraendert; die Folge fuer den Schutz
     * (ungeschuetzte Nummer hinter einem Pfad) ist ein Kandidat fuer ein eigenes Folgefeature.
     */
    private static final int MIN_LANGER_PFAD_F = 24;

    /** Ein Kontextwort aus D-2; {@code kv} heisst: Vorwort KV, nur fuer die Buchstabenform. */
    private record KwF(String text, boolean kv, int gruppe) {
    }

    private static final KwF[] KW_F = {new KwF("KVNR", false, 0), new KwF("Versichertennummer", false, 1),
        new KwF("Versichertennr", false, 1), new KwF("Versichertennr.", false, 1),
        new KwF("Krankenversichertennummer", false, 2), new KwF("Krankenversichertennr", false, 2),
        new KwF("Krankenversicherungsnummer", false, 2), new KwF("Krankenversicherungsnr", false, 2),
        new KwF("eGK-Nummer", false, 3), new KwF("eGKNummer", false, 3), new KwF("eGK-Nr.", false, 3),
        new KwF("eGKNr", false, 3), new KwF("Versicherten-Nr.", false, 1), new KwF("Versicherten-Nummer", false, 1),
        new KwF("Krankenversicherten-Nr.", false, 2), new KwF("Krankenversicherten-Nummer", false, 2),
        new KwF("eGK Nr.", false, 3), new KwF("Versicherten Nr.", false, 1),
        new KwF("Patienten-KVNR", false, GRUPPE_KOMPOSITUM_F), new KwF("KV-Nr.", true, 4),
        new KwF("KV-Nummer", true, 4), new KwF("KV Nr.", true, 4), new KwF("KV Nummer", true, 4)};

    private static final String[] PRAEFIX_F = {"", "", "Patient, ", "Der Patient hat die ", "Daten: "};
    private static final String[] DIREKT_F = {" ", ": ", ":", " : ", "=", "= ", "\t", NB, "  ", ":  "};
    private static final String[] FUELLWOERTER_F = {"des", "Patienten", "Herrn", "Muster", "bitte", "siehe", "unten",
        "heute", "wurde"};
    private static final String[] UMBRUECHE_F = {"\n", "\r\n", "\r"};
    private static final String[] AUFZAEHLUNG_F = {", ", "; ", " und ", " / ", "\n"};
    private static final String[] WEIT_F = {". ", ".  ", "  ", "   "};
    private static final String[] SPERR_WOERTER_F = {"KVNR", "EGKNR"};
    private static final String[] KOMPOSITUM_F = {"Patienten", "Pat.", "eGK", "Versicherter", "Herr"};
    private static final String[] FREMDWOERTER_F = {"VKNR", "KVNRX", "Versicherungsnummer", "Rentenversicherungsnummer",
        "KV", "Nr.", "KVID", "LANR", "IK"};
    /** Zerlegungen der neun Ziffern in zwei oder drei Gruppen (nie vier: keine IPv4-Gestalt). */
    private static final int[][] GRUPPEN_F = {{3, 3, 3}, {4, 5}, {1, 8}, {5, 4}, {2, 7}, {2, 2, 5}};
    /** Trenner zwischen Bausteinen von Teil 1f: nie ein einzelner Buchstabe davor, nie ein Gruppentrenner. */
    private static final String[] TRENNER_F = {"\n", "\r\n", "; ", ". ", "! ", "\t", " | ", ", ", "\n\n", " und "};
    private static final String[] LANGE_PFADE_F = {"/opt/beispiel/archiv/akten/befund.pdf",
        "/srv/freigabe/praxis/unterlagen/bericht.txt", "/home/erika/dokumente/archiv/notiz.txt"};
    private static final String BS_F = String.valueOf((char) 0x5C);

    /** Art der Nummer eines Blocks in einem Text von Teil 1f (erwartete Quelle, aus der Nummer abgeleitet). */
    private static final int ART_GUELTIG = 0;
    private static final int ART_UNGUELTIG = 1;
    private static final int ART_FORM_BUCHSTABE = 2;
    private static final int ART_FORM_ZIFFERN = 3;

    /**
     * Ein erwarteter Fund einer Nummer in einem Text von Teil 1f: Blockart ({@code BLOCK_F_*}), Gruppe des
     * Kontextworts, erwartete Konfidenz und Quelle des Kandidaten (aus der Art der Nummer, nicht aus der Konfidenz;
     * Konfidenz kleiner 0 heisst: keine Erwartung an den Kandidaten, Grenzfall), Bereich der Nummer, bei gesperrt
     * geschriebenem Kontextwort der Bereich seiner Buchstaben (sonst -1). {@code grenze} ist leer oder die Grenzklasse
     * ({@code GRENZE_*}), nach der der Block an einer dokumentierten Grenze liegt; er muss keinen Scan-Fund haben.
     */
    record BlockF(int blockart, int gruppe, double konfidenz, String quelle, int start, int ende, int wortStart,
            int wortEnde, String grenze) {
        BlockF markiert(String klasse) {
            return new BlockF(blockart, gruppe, konfidenz, quelle, start, ende, wortStart, wortEnde, klasse);
        }
    }

    /** Eine Gegenprobe: Name und Text; allein gescannt liefert der KVNR-Erkenner keinen Fund (aus der Konstruktion). */
    record GegenprobeF(String name, String text) {
    }

    /**
     * Ein Text von Teil 1f mit den Bloecken seiner Konstruktion, den Gegenproben und den Namen der Bausteine ohne
     * Block (Bestand-Vorrang, Wiederholung). Eigener Typ, getrennt von {@link Fall} und {@link FallE}.
     */
    record FallF(String text, List<BlockF> bloecke, List<GegenprobeF> gegenproben, List<String> bausteine,
            int verworfene, int aufgegebene) {

        /** Ein Fall ohne verworfene Bausteine (Selbsttests). */
        FallF(String text, List<BlockF> bloecke, List<GegenprobeF> gegenproben, List<String> bausteine) {
            this(text, bloecke, gegenproben, bausteine, 0, 0);
        }
    }

    private record NummerF(String text, boolean buchstabenform, int art) {
    }

    /** Der im Aufbau befindliche Text; {@link #zurueck} nimmt einen verworfenen Baustein ohne Rest zurueck. */
    private static final class AufbauF {
        final StringBuilder sb = new StringBuilder();
        final List<BlockF> bloecke = new ArrayList<>();
        final List<GegenprobeF> gegenproben = new ArrayList<>();
        final List<String> bausteine = new ArrayList<>();
        /** Zahl der verworfenen Versuche und der Bausteine, die nach allen Versuchen wegfielen (nur gezaehlt). */
        int verworfene;
        int aufgegebene;

        int[] marke() {
            return new int[] {sb.length(), bloecke.size(), gegenproben.size(), bausteine.size()};
        }

        void zurueck(int[] marke) {
            sb.setLength(marke[0]);
            bloecke.subList(marke[1], bloecke.size()).clear();
            gegenproben.subList(marke[2], gegenproben.size()).clear();
            bausteine.subList(marke[3], bausteine.size()).clear();
        }
    }

    /** Teil 1f: Texte mit KVNR-Bloecken, Gegenproben, Bestand-Bausteinen und Mischung mit Bausteinen aus 1b bis 1e. */
    static List<FallF> erzeugeKvF(long seed) {
        Random r = new Random(seed);
        List<FallF> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeTextF(r));
        }
        return faelle;
    }

    /**
     * Ein Text aus 3 bis 10 Bausteinen. Die Mischbausteine aus 1b bis 1e und die Umgebung ziehen aus einem eigenen
     * {@code Random}, dessen Startwert je Text eine Zufallszahl des Hauptstroms ist; ihre Bloecke werden nicht
     * erfasst. Ein Baustein samt Trenner wird verworfen und neu gezogen, wenn der Text danach den Waechter der
     * LANR-/IK-Testdatenregel oder den der KVNR-Testdatenregel ({@code KvnrSynthetischWaechter}) verletzt (ein
     * Kontextwortbuchstabe vor einer LANR- oder IK-Nummer bildet zufaellig eine gueltige KVNR-Form) oder eine enge
     * KVNR-Form ausserhalb der KVNR-Bloecke entsteht ({@link #engeFormenAusserhalb}). Nach dem Aufbau werden die
     * Bloecke markiert, die an einer dokumentierten Grenze liegen ({@link #grenzklasseF}); die Markierung zieht keine
     * Zufallszahlen.
     */
    static FallF erzeugeTextF(Random r) {
        AufbauF a = new AufbauF();
        Random mischung = new Random(r.nextLong());
        int n = 3 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            boolean angenommen = false;
            for (int versuch = 0; versuch < MAX_VERSUCHE_F && !angenommen; versuch++) {
                int[] marke = a.marke();
                kvBausteinF(r, mischung, a);
                if (r.nextInt(6) == 0) {
                    a.sb.append(waehle(r, UNSICHTBAR_C));
                }
                a.sb.append(waehle(r, TRENNER_F));
                angenommen = aufbauOkF(a);
                if (!angenommen) {
                    a.zurueck(marke);
                    a.verworfene++;
                }
            }
            a.aufgegebene += angenommen ? 0 : 1;
        }
        String text = a.sb.toString();
        List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
        List<BlockF> endgueltig = new ArrayList<>(a.bloecke.size());
        for (BlockF block : a.bloecke) {
            String klasse = grenzklasseF(text, block, alt);
            endgueltig.add(klasse.isEmpty() ? block : block.markiert(klasse));
        }
        return new FallF(text, endgueltig, List.copyOf(a.gegenproben), List.copyOf(a.bausteine), a.verworfene,
                a.aufgegebene);
    }

    private static boolean aufbauOkF(AufbauF a) {
        String text = a.sb.toString();
        return KvTestHilfen.neunerfenster(text, "00")[1] == 0 && KvnrSynthetischWaechter.pruefe(text).verstoesse() == 0
                && engeFormenAusserhalb(text, a.bloecke) == 0;
    }

    /**
     * Zahl der Treffer der engen KVNR-Form (Grossbuchstabe, neun Ziffern, Wortgrenzen, gueltige Pruefziffer;
     * {@code KvnrBsnrHilfen.kvnrFormEngTreffer}), die nicht ganz in einem KVNR-Block liegen. In Teil 1f ist die Zahl 0:
     * Eine geschlossene, grossgeschriebene, gueltige KVNR steht nur in Bloecken mit Kontextwort (Regel 12; sichert,
     * dass die Erkennung ohne Kontextwort nichts an Teil 1f aendert).
     */
    static int engeFormenAusserhalb(String text, List<BlockF> bloecke) {
        int ausserhalb = 0;
        for (KvnrBsnrHilfen.Bereich b : KvnrBsnrHilfen.kvnrFormEngTreffer(text)) {
            boolean imBlock = false;
            for (BlockF block : bloecke) {
                imBlock |= block.start() <= b.start() && b.ende() <= block.ende();
            }
            ausserhalb += imBlock ? 0 : 1;
        }
        return ausserhalb;
    }

    /**
     * Grenzklasse eines Blocks, ermittelt aus dem fertigen Text und aus {@code alt} (Funde von E-Mail, IP, URL, Pfad,
     * LANR und IK der eingefrorenen Kopie), unabhaengig vom KVNR-Erkenner; leer, wenn der Block an keiner
     * dokumentierten Grenze liegt. Vier Klassen, je mit Plan-Bezug: (a) {@link #GRENZE_AM_BUCHSTABEN}: unmittelbar vor
     * dem Block steht ein ASCII-Buchstabe, und der Buchstabenlauf davor endet auf ein KVNR-Kontextwort
     * ({@link #endetAufKvnrKontextwortF}; Kontextwort direkt am Buchstaben der Nummer ohne Trenner, Nachtrag zu T-4,
     * Punkt 3); (b) {@link #GRENZE_LANGER_PFAD}: unmittelbar vor dem Block (nur Leerraum) endet ein Pfad- oder
     * URL-Fund von mindestens {@value #MIN_LANGER_PFAD_F} Zeichen, und hoechstens 40 Zeichen davor steht ein
     * KVNR-Kontextwort (Nachtrag 5 zu T-6, Punkt 3, durch Nachtrag 2 zu T-7, Punkt 1, korrigiert: maassgeblich ist
     * nicht die Laenge, sondern die Fuellwortzaehlung des Scanners, bei der jedes Pfad- oder URL-Segment als
     * Fuellwort zaehlt und der Kontext ab dem vierten erloschen ist, und die Satzende-Regel, nach der der Punkt der
     * Dateiendung hinter einem Wort mit mehr als vier Buchstaben ein Satzende ist; die Mindestlaenge ist nur eine
     * Naeherung dieser Regeln; die Markierungsregel bleibt unveraendert, ihre Bloecke sind einzeln und exakt
     * gezaehlt); (c) {@link #GRENZE_SPERR_EINZELBUCHSTABE}: das
     * gesperrt geschriebene Kontextwort kann ueber eine Luecke nach D-2 mit einem davor stehenden Einzelbuchstaben
     * verbunden sein (Grenze 14e der Vorgaenger, {@link #verbindbar}, sinngemaess auch fuer die KVNR laut Plan
     * Kap. 11); (d) {@link #GRENZE_KV_VORWORT_ZIFFERN}, siehe {@link #kvVorwortZiffernF}.
     */
    static String grenzklasseF(String text, BlockF block, List<Finding> alt) {
        if (block.start() > 0 && endetAufKvnrKontextwortF(text, block.start())) {
            return GRENZE_AM_BUCHSTABEN;
        }
        if (block.wortStart() >= 0 && verbindbar(text, new BlockE(BLOCK_E_WEIT, false, 0, block.start(), block.ende(),
                block.wortStart(), block.wortEnde(), false))) {
            return GRENZE_SPERR_EINZELBUCHSTABE;
        }
        for (Finding p : alt) {
            boolean pfadOderUrl = p.type() == FindingType.PFAD || p.type() == FindingType.URL;
            if (pfadOderUrl && p.end() <= block.start() && block.start() - p.end() <= 3
                    && text.substring(p.end(), block.start()).isBlank() && p.end() - p.start() >= MIN_LANGER_PFAD_F
                    && KvnrBsnrHilfen.kvnrKontextReferenz(text.substring(Math.max(0, p.start() - 40), p.start()))) {
                return GRENZE_LANGER_PFAD;
            }
        }
        return kvVorwortZiffernF(text, block) ? GRENZE_KV_VORWORT_ZIFFERN : "";
    }

    /**
     * Ob der Text vor {@code ende} auf ein KVNR-Kontextwort endet: Eine Strecke von hoechstens {@value #FENSTER_KW_F}
     * Zeichen, die mit einem ASCII-Buchstaben aufhoert und hoechstens zwei Zeichen enthaelt, die keine Buchstaben
     * sind (Bindestrich bei {@code Krankenversicherten-Nummer}), ist genau ein Kontextwort (Referenz
     * {@code KvnrBsnrHilfen.kvnrKontextReferenz} auf dem Buchstabenstrom): Sie enthaelt eines, aber ohne ihr erstes
     * und ohne ihr letztes Zeichen enthaelt sie keines. Das Wort steht also am Ende, nicht irgendwo davor.
     */
    static boolean endetAufKvnrKontextwortF(String text, int ende) {
        if (ende == 0 || !KvnrFensterfernPruefung.asciiBuchstabe(text.charAt(ende - 1))) {
            return false;
        }
        for (int k = ende - 1; k >= Math.max(0, ende - FENSTER_KW_F); k--) {
            String ab = text.substring(k, ende);
            if (ab.chars().filter(c -> !Character.isLetter(c)).count() > 2) {
                return false;
            }
            if (KvnrBsnrHilfen.kvnrKontextReferenz(ab) && !KvnrBsnrHilfen.kvnrKontextReferenz(ab.substring(1))
                    && !KvnrBsnrHilfen.kvnrKontextReferenz(ab.substring(0, ab.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grenzklasse (d) {@link #GRENZE_KV_VORWORT_ZIFFERN}, Nachtrag zu T-7 (Plan Kap. 4.2, D-2, G-K7) mit Nachtrag 2
     * zu T-7, Punkt 3: hinter einem KV-Vorwort ({@code KV-Nr.}, {@code KV-Nummer}, {@code KV Nr.},
     * {@code KV Nummer}, Gross- oder Kleinschreibung) zaehlt eine reine Ziffernfolge als Fuellwort; ein
     * nachgestelltes vollwertiges Kontextwort dahinter bleibt dann ohne Fund (dokumentierte Grenze, keine
     * Produktaenderung). Die Regel gilt nur dort, wo dieser Mechanismus wirken kann, und wird aus dem fertigen Text
     * bestimmt, unabhaengig vom KVNR-Erkenner. Alle Bedingungen: (1) Die Nummer des Blocks enthaelt keinen
     * ASCII-Buchstaben. (2) Im selben Absatz davor ({@code KvnrFensterfernPruefung#absatzAnfang}) steht ein
     * KV-Vorwort mit {@code Nr} oder {@code Nummer} als eigenes Wort; massgeblich ist das letzte. (3) Zwischen ihm und
     * dem Block steht kein vollwertiges KVNR-Kontextwort (zusammenhaengend, {@code KvnrKollisionsNachrechnung}),
     * hoechstens {@value #MAX_FUELLWOERTER_F} Fuellwoerter (Woerter und Nummern, eine Nummer mit Gruppentrennern
     * zaehlt einfach), hoechstens {@value #MAX_UMBRUECHE_F} Zeilenumbruch und kein Satzende (Punkt ausser direkt
     * hinter dem Vorwort und hinter einem Kuerzel aus hoechstens vier Buchstaben, {@code !}, {@code ?}); unsichtbare
     * Zeichen zaehlen nicht. (4) Unmittelbar hinter der Nummer steht ein nachgestelltes Kontextwort (in Klammern oder
     * hinter Komma oder Semikolon, auch als Kompositum; Referenz {@code KvnrBsnrHilfen.kvnrKontextReferenz}) ohne
     * weitere Nummer dazwischen, und es gehoert zu dieser Nummer: dahinter steht kein Wort, keine Nummer und kein
     * Doppelpunkt, sondern ein Satzzeichen, ein Zeilenumbruch oder das Textende (sonst ist es das Kontextwort des
     * naechsten Bausteins). Messwerte bei festem Seed: Ohne die Bedingungen 3 und 4 waeren es 127 markierte Bloecke
     * (125 mit Abdeckung), mit der Eingrenzung durch ein nachgestelltes Kontextwort allein 7 (5 mit Abdeckung), mit
     * allen Bedingungen 2, beide ohne Abdeckung (Texte 48 und 439 der Folge): Das sind die Bloecke, an denen der
     * Mechanismus aus G-K7 den Scan-Fund verhindert. Kein markierter Block hat eine Scan-Abdeckung. Die fuenf
     * Bloecke, die nur die weitere Regel markierte, haben Funde, weil in allen fuenf zwischen dem KV-Vorwort und dem
     * Block ein vollwertiges KVNR-Kontextwort (mehrfach auch ein Satzende) steht: Es beginnt den Kontext neu und hebt
     * die Beschraenkung des KV-Vorworts auf Nummern in Buchstabenform auf, der Scanner findet die Nummer also aus einem
     * anderen Grund als G-K7.
     */
    private static boolean kvVorwortZiffernF(String text, BlockF block) {
        for (int i = block.start(); i < block.ende(); i++) {
            if (KvnrFensterfernPruefung.asciiBuchstabe(text.charAt(i))) {
                return false;
            }
        }
        String davor = text.substring(KvnrFensterfernPruefung.absatzAnfang(text, block.start()), block.start());
        Matcher vorwort = KV_VORWORT_F.matcher(davor);
        int vorwortEnde = -1;
        while (vorwort.find()) {
            vorwortEnde = vorwort.end();
        }
        return vorwortEnde >= 0 && imFuellwortFensterF(davor.substring(vorwortEnde))
                && nachgestelltGehoertZurNummerF(text, block.ende());
    }

    /**
     * Liegt die Strecke zwischen KV-Vorwort und Block im Fuellwortfenster des Scanners (siehe
     * {@link #kvVorwortZiffernF}, Bedingung 3)? Die Strecke wird in der Reihenfolge ihrer Ereignisse gelesen
     * (unsichtbare Zeichen entfernt): Ein Zeilenumbruch zaehlt, ein Wort oder eine Nummer ohne Buchstaben vorn zaehlt
     * als Fuellwort, eine Nummer mit ASCII-Buchstaben vorn ist bei laufendem Kontext ein Fund des Scanners und setzt
     * beide Zaehler zurueck; ein Satzende beendet den Kontext.
     */
    private static boolean imFuellwortFensterF(String zwischen) {
        String z = zwischen.replaceAll("\\p{Cf}", "");
        if (KvnrKollisionsNachrechnung.zusammenhaengendesKontextwort(z)) {
            return false;
        }
        Matcher ereignis = EREIGNIS_F.matcher(z);
        int woerter = 0;
        int umbrueche = 0;
        int vorherEnde = 0;
        String vorher = "";
        while (ereignis.find()) {
            if (ereignis.group(1) != null) {
                umbrueche++;
            } else if (ereignis.group(2) != null) {
                String wort = ereignis.group(2);
                boolean fund = KvnrFensterfernPruefung.asciiBuchstabe(wort.charAt(0)) && wort.length() > 1
                        && Character.isDigit(wort.charAt(1));
                woerter = fund ? 0 : woerter + 1;
                umbrueche = fund ? 0 : umbrueche;
                vorher = wort;
            } else if (!punktOhneSatzendeF(ereignis, vorher, vorherEnde)) {
                return false;
            }
            if (woerter > MAX_FUELLWOERTER_F || umbrueche > MAX_UMBRUECHE_F) {
                return false;
            }
            vorherEnde = ereignis.end();
        }
        return true;
    }

    /** Ist das Satzzeichen ein Punkt direkt hinter dem Vorwort oder einem Kuerzel aus hoechstens vier Buchstaben? */
    private static boolean punktOhneSatzendeF(Matcher ereignis, String vorherigesWort, int vorherEnde) {
        boolean direkt = ereignis.start() == vorherEnde;
        boolean kuerzel = direkt && !vorherigesWort.isEmpty() && vorherigesWort.length() <= 4
                && vorherigesWort.chars().allMatch(Character::isLetter);
        return ".".equals(ereignis.group(3)) && (kuerzel || (direkt && vorherigesWort.isEmpty()));
    }

    /**
     * Gehoert das nachgestellte Kontextwort hinter {@code ende} zu der Nummer davor (siehe
     * {@link #kvVorwortZiffernF}, Bedingung 4)? In Klammern: ja, wenn die Klammer danach schliesst. Hinter Komma oder
     * Semikolon: ja, ausser das Kontextwort steht unmittelbar vor einer Nummer (nur Leerraum, Doppelpunkt,
     * Gleichheitszeichen dazwischen), dann ist es das Kontextwort dieser naechsten Nummer.
     */
    private static boolean nachgestelltGehoertZurNummerF(String text, int ende) {
        Matcher nach = NACHGESTELLT_F.matcher(text);
        if (!nach.region(ende, text.length()).lookingAt() || !KvnrBsnrHilfen.kvnrKontextReferenz(nach.group(1))) {
            return false;
        }
        int i = nach.end(1);
        while (i < text.length() && (" \t:=" + (char) 0xA0).indexOf(text.charAt(i)) >= 0) {
            i++;
        }
        boolean klammer = text.substring(ende, nach.start(1)).contains("(");
        if (klammer) {
            return i < text.length() && text.charAt(i) == ')';
        }
        boolean nummerFolgt = i < text.length() && (Character.isDigit(text.charAt(i))
                || (KvnrFensterfernPruefung.asciiBuchstabe(text.charAt(i)) && i + 1 < text.length()
                        && Character.isDigit(text.charAt(i + 1))));
        return !nummerFolgt;
    }

    private static String ziffernF(Random r, int anzahl) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < anzahl; i++) {
            sb.append((char) ('0' + r.nextInt(10)));
        }
        return sb.toString();
    }

    /** Schreibung des Kontextworts: unveraendert, gross, klein oder wechselnd (je Zeichen, ohne Locale). */
    private static String schreibungF(Random r, String wort) {
        int v = r.nextInt(4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            char c = wort.charAt(i);
            if (v == 1 || (v == 3 && i % 2 == 0)) {
                sb.append(Character.toUpperCase(c));
            } else if (v == 2 || v == 3) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Eine Nummer, meist gueltig; hinter {@code KV-Nr.} nur die Buchstabenform; Ziffern beginnen mit 0000 (A-6). */
    private static NummerF nummerF(Random r, boolean buchstabeNoetig) {
        int w = r.nextInt(100);
        int art;
        if (w < 55) {
            art = ART_GUELTIG;
        } else if (w < 75) {
            art = ART_UNGUELTIG;
        } else if (buchstabeNoetig || w < 90) {
            art = ART_FORM_BUCHSTABE;
        } else {
            art = ART_FORM_ZIFFERN;
        }
        return nummerF(r, art);
    }

    private static NummerF nummerF(Random r, int art) {
        if (art == ART_FORM_ZIFFERN) {
            return new NummerF("0000" + ziffernF(r, 2 + r.nextInt(7)), false, art);
        }
        char gross = (char) ('A' + r.nextInt(26));
        String ziffern;
        if (art == ART_FORM_BUCHSTABE) {
            int[] laengen = {6, 7, 8, 10, 11, 12};
            ziffern = "0000" + ziffernF(r, laengen[r.nextInt(laengen.length)] - 4);
        } else {
            String acht = "0000" + ziffernF(r, 4);
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer(gross, acht);
            if (art == ART_UNGUELTIG) {
                pruef = (pruef + 1 + r.nextInt(9)) % 10;
            }
            ziffern = acht + pruef;
        }
        StringBuilder sb = new StringBuilder().append(r.nextInt(4) == 0 ? Character.toLowerCase(gross) : gross);
        if (r.nextInt(100) < 35) {
            sb.append(waehle(r, GRUPPENTRENNER));
        }
        if (ziffern.length() == 9 && r.nextInt(100) < 40) {
            int[] gruppen = GRUPPEN_F[r.nextInt(GRUPPEN_F.length)];
            String t = waehle(r, GRUPPENTRENNER);
            int pos = 0;
            for (int i = 0; i < gruppen.length; i++) {
                sb.append(i > 0 ? t : "").append(ziffern, pos, pos + gruppen[i]);
                pos += gruppen[i];
            }
        } else {
            sb.append(ziffern);
        }
        return new NummerF(sb.toString(), true, art);
    }

    private static double stufeF(int art) {
        return art == ART_GUELTIG ? 0.95 : art == ART_UNGUELTIG ? 0.50 : 0.40;
    }

    private static String quelleF(int art) {
        return art == ART_GUELTIG ? KvnrRecognizer.SOURCE : art == ART_UNGUELTIG
                ? KvnrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG : KvnrRecognizer.SOURCE_FORM_ABWEICHEND;
    }

    /** Haengt die Nummer an, vermerkt den Block mit {@code min(deckel, Stufe)}; {@code deckel < 0}: keine Erwartung. */
    private static void fundF(AufbauF a, NummerF n, int blockart, int gruppe, double deckel, int[] wort) {
        int start = a.sb.length();
        a.sb.append(n.text());
        double konf = deckel < 0 ? -1 : Math.min(deckel, stufeF(n.art()));
        a.bloecke.add(new BlockF(blockart, gruppe, konf, quelleF(n.art()), start, a.sb.length(), wort[0], wort[1],
                ""));
    }

    private static final int[] OHNE_WORT_F = {-1, -1};

    private static void fundF(AufbauF a, NummerF n, int blockart, int gruppe, double deckel) {
        fundF(a, n, blockart, gruppe, deckel, OHNE_WORT_F);
    }

    private static KwF kwF(Random r, boolean kvErlaubt) {
        KwF kw = KW_F[r.nextInt(KW_F.length)];
        while (!kvErlaubt && kw.kv()) {
            kw = KW_F[r.nextInt(KW_F.length)];
        }
        return kw;
    }

    private static final int[] GEWICHT_BLOCK_F = {20, 12, 10, 8, 12, 9, 6, 4, 1, 2, 5, 4};

    private static void kvBausteinF(Random r, Random mischung, AufbauF a) {
        int wahl = r.nextInt(100);
        if (wahl < 40) {
            int summe = 0;
            for (int g : GEWICHT_BLOCK_F) {
                summe += g;
            }
            int x = r.nextInt(summe);
            int b = 0;
            while (x >= GEWICHT_BLOCK_F[b]) {
                x -= GEWICHT_BLOCK_F[b];
                b++;
            }
            kvnrBlockF(r, a, b);
        } else if (wahl < 50) {
            gegenprobeF(r, a);
        } else if (wahl < 58) {
            bestandBausteinF(r, a);
        } else {
            mischbausteinF(mischung, a.sb);
        }
    }

    /** Mischbaustein aus Teil 1b bis 1e oder Umgebung (E-Mail, IP, URL, Pfad) mit dem eigenen {@code Random}. */
    private static void mischbausteinF(Random m, StringBuilder sb) {
        int w = m.nextInt(100);
        if (w < 15) {
            kvBaustein(m, sb);
        } else if (w < 50) {
            kvBausteinC(m, sb, new ArrayList<>());
        } else if (w < 70) {
            kvBausteinD(m, m, sb, new ArrayList<>(), new ArrayList<>());
        } else if (w < 90) {
            kvBausteinE(m, m, sb, new ArrayList<>(), new ArrayList<>());
        } else {
            umgebungC(m, sb);
        }
    }

    private static void kvnrBlockF(Random r, AufbauF a, int blockart) {
        switch (blockart) {
            case BLOCK_F_DIREKT:
                blockDirektF(r, a);
                break;
            case BLOCK_F_FUELLWOERTER:
                blockFuellwoerterF(r, a);
                break;
            case BLOCK_F_UMBRUCH:
                blockUmbruchF(r, a);
                break;
            case BLOCK_F_AUFZAEHLUNG:
                blockAufzaehlungF(r, a);
                break;
            case BLOCK_F_NACHGESTELLT:
                blockNachgestelltF(r, a);
                break;
            case BLOCK_F_SPERR:
                blockSperrF(r, a);
                break;
            case BLOCK_F_K1:
                blockK1F(r, a);
                break;
            case BLOCK_F_K2:
                blockK2F(r, a);
                break;
            case BLOCK_F_AM_BUCHSTABEN:
                blockAmBuchstabenF(r, a);
                break;
            case BLOCK_F_LANGER_PFAD:
                blockLangerPfadF(r, a);
                break;
            case BLOCK_F_LISTE_LANG:
                blockListeLangF(r, a);
                break;
            default:
                blockWiederholungF(r, a);
                break;
        }
    }

    private static void blockDirektF(Random r, AufbauF a) {
        KwF kw = kwF(r, true);
        NummerF n = nummerF(r, kw.kv());
        a.sb.append(waehle(r, PRAEFIX_F)).append(schreibungF(r, kw.text())).append(waehle(r, DIREKT_F));
        if (n.buchstabenform() && r.nextInt(5) == 0) {
            a.sb.append(waehle(r, UNSICHTBAR_C));
        }
        fundF(a, n, BLOCK_F_DIREKT, kw.gruppe(), KONF_DIREKT_F);
    }

    private static void blockFuellwoerterF(Random r, AufbauF a) {
        KwF kw = kwF(r, true);
        NummerF n = nummerF(r, kw.kv());
        a.sb.append(waehle(r, PRAEFIX_F)).append(schreibungF(r, kw.text())).append(waehle(r, DIREKT_F));
        for (int i = 1 + r.nextInt(3); i > 0; i--) {
            a.sb.append(waehle(r, FUELLWOERTER_F)).append(' ');
        }
        fundF(a, n, BLOCK_F_FUELLWOERTER, kw.gruppe(), KONF_WEITER_F);
    }

    private static void blockUmbruchF(Random r, AufbauF a) {
        KwF kw = kwF(r, true);
        NummerF n = nummerF(r, kw.kv());
        a.sb.append(waehle(r, PRAEFIX_F)).append(schreibungF(r, kw.text())).append(r.nextBoolean() ? ":" : "");
        int v = r.nextInt(4);
        a.sb.append(v < 3 ? UMBRUECHE_F[v] : crUzLf(r, 1 + r.nextInt(MAX_UZ_D)));
        if (r.nextInt(100) < 30) {
            a.sb.append(' ');
        }
        fundF(a, n, BLOCK_F_UMBRUCH, kw.gruppe(), KONF_WEITER_F);
    }

    private static void blockAufzaehlungF(Random r, AufbauF a) {
        KwF kw = kwF(r, true);
        a.sb.append(waehle(r, PRAEFIX_F)).append(schreibungF(r, kw.text())).append(waehle(r, DIREKT_F));
        for (int i = 0, anzahl = 2 + r.nextInt(3); i < anzahl; i++) {
            if (i > 0) {
                a.sb.append(waehle(r, AUFZAEHLUNG_F));
            }
            fundF(a, nummerF(r, true), BLOCK_F_AUFZAEHLUNG, kw.gruppe(), i == 0 ? KONF_DIREKT_F : KONF_WEITER_F);
        }
    }

    private static void blockNachgestelltF(Random r, AufbauF a) {
        KwF kw = kwF(r, true);
        NummerF n = nummerF(r, kw.kv());
        a.sb.append(r.nextBoolean() ? "Wert " : "");
        int v = r.nextInt(3);
        boolean kompositum = !kw.text().startsWith("Patienten-") && r.nextInt(100) < 35;
        fundF(a, n, BLOCK_F_NACHGESTELLT, kompositum ? GRUPPE_KOMPOSITUM_F : kw.gruppe(),
                v == 0 ? KONF_WEITER_F : KONF_KOMMA_F);
        String wort = schreibungF(r, kw.text());
        if (kompositum) {
            wort = waehle(r, KOMPOSITUM_F) + "-" + wort;
        }
        a.sb.append(v == 0 ? " (" + wort + ")" : (v == 1 ? ", " : "; ") + wort);
    }

    private static void blockSperrF(Random r, AufbauF a) {
        String wort = waehle(r, SPERR_WOERTER_F);
        boolean einzeichen = r.nextInt(3) == 0;
        String trenner = einzeichen ? waehle(r, SPERR_TRENNER_C) : waehle(r, WEIT_F);
        a.sb.append(waehle(r, PRAEFIX_F));
        int wortStart = a.sb.length();
        a.sb.append(schreibungF(r, gesperrtE(wort, trenner)));
        int wortEnde = a.sb.length();
        if (!einzeichen && trenner.startsWith(".") && r.nextBoolean()) {
            a.sb.append('.');
        }
        NummerF n = nummerF(r, !einzeichen);
        a.sb.append(einzeichen || r.nextBoolean() ? " " : ": ");
        fundF(a, n, BLOCK_F_SPERR, GRUPPE_SPERR_F, KONF_WEITER_F, new int[] {wortStart, wortEnde});
    }

    /** KVNR-Kontextwort, LANR- oder IK-Kontextwort und KVNR-Form (K-1); auch die Nummer vor beiden Woertern. */
    private static void blockK1F(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        String fremd = r.nextBoolean() ? "LANR" : "IK";
        NummerF n = nummerF(r, true);
        if (r.nextInt(100) < 25) {
            a.sb.append(fremd).append(' ');
            fundF(a, n, BLOCK_F_K1, kw.gruppe(), KONF_WEITER_F);
            a.sb.append(' ').append(kw.text());
        } else {
            a.sb.append(kw.text()).append(' ').append(fremd).append(' ');
            fundF(a, n, BLOCK_F_K1, kw.gruppe(), KONF_WEITER_F);
        }
    }

    /** KVNR-Kontextwort, LANR- oder IK-Kontextwort und eine reine Ziffernfolge mit acht Ziffern (K-2 bei LANR). */
    private static void blockK2F(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        a.sb.append(kw.text()).append(' ').append(r.nextBoolean() ? "LANR" : "IK").append(' ');
        fundF(a, new NummerF("0000" + ziffernF(r, 4), false, ART_FORM_ZIFFERN), BLOCK_F_K2, kw.gruppe(),
                KONF_WEITER_F);
    }

    /** Grenzfall (a): das Kontextwort haengt ohne Trenner am Buchstaben der Nummer; keine Erwartung an den Fund. */
    private static void blockAmBuchstabenF(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        NummerF n = nummerF(r, ART_GUELTIG);
        a.sb.append(kw.text());
        fundF(a, n, BLOCK_F_AM_BUCHSTABEN, kw.gruppe(), -1);
    }

    /** Grenzfall (b): ein langer Pfad steht zwischen Kontextwort und Nummer; keine Erwartung an den Fund. */
    private static void blockLangerPfadF(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        a.sb.append(kw.text()).append(' ').append(waehle(r, LANGE_PFADE_F)).append(' ');
        fundF(a, nummerF(r, ART_GUELTIG), BLOCK_F_LANGER_PFAD, kw.gruppe(), -1);
    }

    /** Dieselbe Nummer zweimal mit Kontextwort, zeichengleich oder mit Trenner hinter dem Buchstaben. */
    private static void blockWiederholungF(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        char gross = (char) ('A' + r.nextInt(26));
        String acht = "0000" + ziffernF(r, 4);
        String ziffern = acht + KvnrBsnrHilfen.kvnrPruefziffer(gross, acht);
        boolean gleich = r.nextBoolean();
        a.sb.append(kw.text()).append(' ');
        fundF(a, new NummerF(gross + ziffern, true, ART_GUELTIG), BLOCK_F_WIEDERHOLUNG, kw.gruppe(), KONF_DIREKT_F);
        a.sb.append(" ... ").append(kw.text()).append(' ');
        String zweite = gleich ? gross + ziffern : gross + " " + ziffern;
        fundF(a, new NummerF(zweite, true, ART_GUELTIG), BLOCK_F_WIEDERHOLUNG, kw.gruppe(), KONF_DIREKT_F);
        a.bausteine.add(gleich ? "wiederholung-gleich" : "wiederholung-verschieden");
    }

    /**
     * Lange Aufzaehlung hinter KVNR- und LANR- oder IK-Kontextwort: 14 bis 20 Nummern (reine Ziffernfolgen mit acht
     * Ziffern oder Buchstabenformen), durch Komma getrennt. Die hinteren Nummern liegen weiter als 120 Zeichen vom
     * Kontextwort entfernt; der Listenkontext wirkt weiter (fensterferne Faelle von K-1 und K-2, Nachtrag 2 zu T-6,
     * Punkt 6). Keine Erwartung an die Konfidenz der Kandidaten.
     */
    private static void blockListeLangF(Random r, AufbauF a) {
        KwF kw = kwF(r, false);
        boolean buchstabe = r.nextBoolean();
        a.sb.append(kw.text()).append(' ').append(r.nextBoolean() ? "LANR" : "IK").append(' ');
        for (int i = 0, anzahl = 14 + r.nextInt(7); i < anzahl; i++) {
            a.sb.append(i > 0 ? ", " : "");
            NummerF n = buchstabe ? nummerF(r, ART_GUELTIG)
                    : new NummerF("0000" + ziffernF(r, 4), false, ART_FORM_ZIFFERN);
            fundF(a, n, BLOCK_F_LISTE_LANG, kw.gruppe(), -1);
        }
    }

    private static final String[] GEGENPROBEN_F = {"vier-fuellwoerter", "leerzeile", "kv-nr-ziffern", "fremdwort",
        "ohne-kontext", "zwei-buchstaben", "kw-ohne-nummer"};

    /**
     * Gegenproben nach Regel 12 (nur ungueltige Nummern oder Formen, die der Kontext nicht trifft): vier oder
     * fuenf Fuellwoerter, Leerzeile, {@code KV-Nr.} vor einer reinen Ziffernfolge (auch nachgestellt), Woerter
     * ausserhalb von D-2, Nummer ohne Kontext, zwei Buchstaben direkt an den Ziffern, Kontextwort ohne Nummer. Der
     * Text steht auch allein; allein liefert der KVNR-Erkenner dort keinen Fund (geprueft in {@code pruefeKvF}).
     */
    private static void gegenprobeF(Random r, AufbauF a) {
        int v = r.nextInt(GEGENPROBEN_F.length);
        int vor = a.sb.length();
        KwF kw = kwF(r, true);
        switch (v) {
            case 0:
                a.sb.append(schreibungF(r, kw.text())).append(waehle(r, DIREKT_F));
                for (int i = 4 + r.nextInt(2); i > 0; i--) {
                    a.sb.append(waehle(r, FUELLWOERTER_F)).append(' ');
                }
                a.sb.append(nummerF(r, r.nextBoolean() ? ART_UNGUELTIG : ART_FORM_BUCHSTABE).text());
                break;
            case 1:
                a.sb.append(schreibungF(r, kw.text())).append(r.nextBoolean() ? ":" : "")
                        .append(waehle(r, new String[] {"\n\n", "\r\n\r\n", "\n  \n", "\r\r"}))
                        .append(nummerF(r, ART_UNGUELTIG).text());
                break;
            case 2:
                KwF kv = kwF(r, true);
                while (!kv.kv()) {
                    kv = kwF(r, true);
                }
                String ziffern = nummerF(r, ART_FORM_ZIFFERN).text();
                a.sb.append(r.nextBoolean() ? schreibungF(r, kv.text()) + " " + ziffern
                        : ziffern + " (" + kv.text() + ")");
                break;
            case 3:
                a.sb.append(waehle(r, FREMDWOERTER_F)).append(r.nextBoolean() ? ": " : " ")
                        .append(nummerF(r, ART_UNGUELTIG).text());
                break;
            case 4:
                a.sb.append(r.nextBoolean() ? "Patient " : "Der Wert ").append(nummerF(r, ART_UNGUELTIG).text());
                break;
            case 5:
                KwF ohneKv = kwF(r, false);
                a.sb.append(ohneKv.text()).append(' ').append("AB").append("0000").append(ziffernF(r, 5));
                break;
            default:
                a.sb.append(schreibungF(r, kw.text())).append(waehle(r, new String[] {":", " fehlt", ""}));
                break;
        }
        a.gegenproben.add(new GegenprobeF(GEGENPROBEN_F[v], a.sb.substring(vor)));
    }

    private static final String[] BESTAND_F = {"email", "email-lokal", "pfad-unix", "pfad-windows", "url",
        "ip-buchstabe", "ip-prefix"};

    /**
     * Bestand-Bausteine (ohne Block, nur Roundtrip, Schutzgarantie und Bestandsschutz): KVNR-Kontextwort und eine
     * ungueltige KVNR-Form in oder an einer E-Mail-Adresse, einem Pfad oder einer URL; Buchstabe vor einer
     * IPv4-Gestalt. Hier greift das Zuschneiden der KVNR-Kandidaten am Bestand (Nachtrag 3 zu T-6).
     */
    private static void bestandBausteinF(Random r, AufbauF a) {
        int v = r.nextInt(BESTAND_F.length);
        KwF kw = kwF(r, true);
        String nummer = nummerF(r, ART_UNGUELTIG).text();
        a.sb.append(waehle(r, PRAEFIX_F)).append(schreibungF(r, kw.text())).append(' ');
        switch (v) {
            case 0:
                a.sb.append(nummer).append("@example.invalid");
                break;
            case 1:
                a.sb.append("erika.").append(nummer).append("@example.invalid");
                break;
            case 2:
                a.sb.append("/srv/x/").append(nummer).append(".pdf");
                break;
            case 3:
                a.sb.append("C:").append(BS_F).append("Praxis").append(BS_F).append(nummer).append(".txt");
                break;
            case 4:
                a.sb.append("http://a.invalid/?id=").append(nummer);
                break;
            case 5:
                a.sb.append((char) ('A' + r.nextInt(26))).append(ipKv(r));
                break;
            default:
                a.sb.append(ipKv(r)).append("/24 ").append(nummer);
                break;
        }
        a.bausteine.add("bestand-" + BESTAND_F[v]);
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
        LAUF_KV_C = fuehreKvCAus();
        LAUF_KV_D = fuehreKvDAus();
        LAUF_KV_E = fuehreKvEAus();
        LAUF_KV_F = fuehreKvFAus();
        LAUF_KV_G = fuehreKvGAus();
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

    /**
     * Teil 1c (kv-kontext-luecken T-6): dieselben Eigenschaften wie Teil 1b ({@code pruefeText}: Invarianten,
     * Schutzgarantie, Roundtrip, Waechter) plus {@link #pruefeKvC}: Klassen nach Konstruktion und vollstaendiges
     * Ersetzen aller Funde.
     */
    private static Lauf fuehreKvCAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_C);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV_C);
        List<FallC> faelle = erzeugeKvC(SEED_TEXTE_KV_C);
        for (int it = 0; it < faelle.size(); it++) {
            FallC fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            if (text.contains("[[")) {
                lauf.verletzt("generator-doppelklammer", it);
                continue;
            }
            try {
                pruefeText(lauf, scanner, auswahl, it, new Fall(text, List.of(), false), text);
                pruefeKvC(lauf, scanner, it, fall);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    /**
     * Klassen je Text, hoechstens einmal: Ein Erkenner-Kandidat (LANR und IK wie in {@code TextScanner.standard()})
     * liegt genau auf der Nummer eines Bausteins und hat die erwartete Konfidenz: Sperrschrift ab drei Buchstaben
     * 0,80, {@code I K} 0,50 mit Quelle {@code ik}, Komma 0,60. Dann werden alle Scan-Funde pseudonymisiert und
     * geprueft, dass jeder Fund vollstaendig durch einen Platzhalter ersetzt ist. Zusaetzlich zaehlt die Klasse
     * {@code block-ohne-scan-abdeckung} die vermerkten Bloecke, die nicht als Grenze 14e markiert sind
     * ({@link #erzeugeTextC}) und deren Nummernbereich von keinem Scan-Fund ganz abgedeckt ist. Bis T-6 waren das
     * 20 Bloecke mit dem Muster 14h (laufender IK-Kontext, Vorwort, gesperrtes {@code I K}, Nachsatzwort, gueltige
     * IK, z. B. {@code IK: Praxis I K Nr N}); die Grenze ist in T-7a geschlossen, der Messwert ist 0 (siehe
     * {@code t6_kvC_blockOhneScanAbdeckung_grenze14hGeschlossen}). Nicht geprueft werden die Konfidenz der
     * Abdeckung sowie Nummern ohne Vermerk (Gegenproben, ungueltige Formen).
     */
    private static void pruefeKvC(Lauf lauf, TextScanner scanner, int it, FallC fall) {
        String text = fall.text();
        boolean[][] gesehen = new boolean[ANZAHL_BLOCKARTEN][2];
        for (BlockC block : fall.bloecke()) {
            int erkenner = block.lanr() ? ERKENNER_LANR : ERKENNER_IK;
            for (Finding c : STANDARD_ERKENNER.get(erkenner).detect(text)) {
                if (c.start() != block.start() || c.end() != block.ende()) {
                    continue;
                }
                boolean treffer;
                if (block.blockart() == BLOCK_SPERR_DREI) {
                    treffer = c.confidence() == 0.80;
                } else if (block.blockart() == BLOCK_SPERR_ZWEI) {
                    treffer = c.confidence() == 0.50 && "ik".equals(c.source());
                } else {
                    treffer = c.confidence() == 0.60;
                }
                gesehen[block.blockart()][block.lanr() ? 0 : 1] |= treffer;
            }
        }
        String[] arten = {"lanr", "ik"};
        String[] namen = new String[ANZAHL_BLOCKARTEN];
        namen[BLOCK_SPERR_DREI] = "kv-sperr-fund";
        namen[BLOCK_SPERR_ZWEI] = "kv-sperr-zwei-fund";
        namen[BLOCK_KOMMA] = "kv-komma-fund";
        boolean irgendwo = false;
        for (int b = 0; b < ANZAHL_BLOCKARTEN; b++) {
            boolean beide = false;
            for (int a = 0; a < 2; a++) {
                if (gesehen[b][a]) {
                    lauf.klasse(namen[b] + "-" + arten[a]);
                    beide = true;
                }
            }
            if (beide) {
                lauf.klasse(namen[b]);
                irgendwo = true;
            }
        }
        if (irgendwo) {
            lauf.klasse("kv-fund-aus-neuer-regel");
        }
        List<Finding> scan = scanner.scan(text);
        String[] blockNamen = {"sperr-drei", "sperr-zwei", "komma"};
        for (BlockC block : fall.bloecke()) {
            boolean abgedeckt = vonScanAbgedeckt(scan, block);
            if (block.grenze()) {
                lauf.klasse("block-markiert");
            }
            String detail = blockNamen[block.blockart()] + "-" + (block.lanr() ? "lanr" : "ik");
            if (block.grenze() && !abgedeckt) {
                lauf.klasse("markiert-ohne-abdeckung");
                lauf.klasse("markiert-ohne-abdeckung-" + (block.blockart() == BLOCK_KOMMA ? "komma" : "sperr"));
            } else if (!block.grenze() && !abgedeckt) {
                lauf.klasse("block-ohne-scan-abdeckung");
                lauf.klasse("block-ohne-scan-abdeckung-" + detail);
            }
        }
        PseudonymizationResult alle = PSEUDONYMIZER.pseudonymize(text, scan);
        if (!alleFundeErsetzt(text, scan, alle.text())) {
            lauf.verletzt("ak23-fund-nicht-vollstaendig-ersetzt", it);
        }
    }

    private static boolean vonScanAbgedeckt(List<Finding> scan, BlockC block) {
        for (Finding f : scan) {
            if (f.start() <= block.start() && f.end() >= block.ende()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teil 1d (kv-trenner-und-umbruch T-4): dieselben Eigenschaften wie Teil 1c ({@code pruefeText}: Invarianten,
     * Schutzgarantie, Roundtrip, Waechter) plus {@link #pruefeKvD}: Klassen nach Konstruktion, Zaehler der Bloecke
     * ohne Scan-Abdeckung und vollstaendiges Ersetzen aller Funde.
     */
    private static Lauf fuehreKvDAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_D);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV_D);
        List<FallD> faelle = erzeugeKvD(SEED_TEXTE_KV_D);
        for (int it = 0; it < faelle.size(); it++) {
            FallD fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            if (text.contains("[[")) {
                lauf.verletzt("generator-doppelklammer", it);
                continue;
            }
            try {
                pruefeText(lauf, scanner, auswahl, it, new Fall(text, List.of(), false), text);
                pruefeKvD(lauf, scanner, it, fall);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    /**
     * Klassen je Text, hoechstens einmal: Ein Erkenner-Kandidat (LANR und IK wie in {@code TextScanner.standard()})
     * liegt genau auf der Nummer eines Blocks, hat die Quelle der gueltigen Nummer und die erwartete Konfidenz:
     * 0,80 fuer Kontextwort mit {@code CR UZ+ LF} (normal oder gesperrt) und fuer die zweite Nummer hinter dem
     * Umbruch, 0,95 (LANR) bzw. 0,92 (IK) fuer die erste Nummer direkt hinter dem Kontextwort. Gezaehlt wird nach
     * Blockart und Art ({@code kv-umbruch-fund-<blockart>-<art>}) und nach Zahl der unsichtbaren Zeichen im Umbruch
     * ({@code kv-umbruch-fund-uz<n>}). Je Block zaehlt {@code block-ohne-scan-abdeckung} die nicht markierten Bloecke,
     * deren Nummernbereich von keinem Scan-Fund ganz abgedeckt ist (strikt 0 erwartet); markierte Bloecke zaehlen
     * getrennt ({@code markiert-ohne-abdeckung}, {@code markiert-mit-abdeckung}). Danach werden alle Scan-Funde
     * pseudonymisiert und geprueft, dass jeder Fund vollstaendig durch einen Platzhalter ersetzt ist.
     */
    static void pruefeKvD(Lauf lauf, TextScanner scanner, int it, FallD fall) {
        String text = fall.text();
        List<List<Finding>> kandidaten = List.of(STANDARD_ERKENNER.get(ERKENNER_LANR).detect(text),
                STANDARD_ERKENNER.get(ERKENNER_IK).detect(text));
        boolean[][] gesehen = new boolean[ANZAHL_BLOCKARTEN_D][2];
        boolean[] uzGesehen = new boolean[MAX_UZ_D + 1];
        for (BlockD block : fall.bloecke()) {
            int art = block.lanr() ? 0 : 1;
            double erwartet = block.blockart() == BLOCK_D_ERSTE ? (block.lanr() ? 0.95 : 0.92) : 0.80;
            String quelle = block.lanr() ? LanrRecognizer.SOURCE : IkRecognizer.SOURCE;
            for (Finding c : kandidaten.get(art)) {
                if (c.start() == block.start() && c.end() == block.ende() && c.confidence() == erwartet
                        && quelle.equals(c.source())) {
                    gesehen[block.blockart()][art] = true;
                    uzGesehen[block.uz()] = true;
                }
            }
        }
        boolean irgendwo = false;
        for (int b = 0; b < ANZAHL_BLOCKARTEN_D; b++) {
            boolean mindestensEineArt = false;
            for (int a = 0; a < 2; a++) {
                if (gesehen[b][a]) {
                    lauf.klasse("kv-umbruch-fund-" + BLOCK_NAMEN_D[b] + "-" + (a == 0 ? "lanr" : "ik"));
                    mindestensEineArt = true;
                }
            }
            if (mindestensEineArt) {
                lauf.klasse("kv-umbruch-fund-" + BLOCK_NAMEN_D[b]);
                irgendwo = true;
            }
        }
        for (int u = 1; u <= MAX_UZ_D; u++) {
            if (uzGesehen[u]) {
                lauf.klasse("kv-umbruch-fund-uz" + u);
            }
        }
        if (irgendwo) {
            lauf.klasse("kv-umbruch-fund");
        }
        for (String gegenprobe : fall.gegenproben()) {
            lauf.klasse("gegenprobe-" + gegenprobe);
        }
        List<Finding> scan = scanner.scan(text);
        for (BlockD block : fall.bloecke()) {
            String art = BLOCK_NAMEN_D[block.blockart()];
            String detail = art + "-" + (block.lanr() ? "lanr" : "ik");
            lauf.klasse("bloecke-" + detail);
            boolean abgedeckt = imScanAbgedeckt(scan, block.start(), block.ende());
            if (block.grenze()) {
                lauf.klasse("block-markiert");
                lauf.klasse(abgedeckt ? "markiert-mit-abdeckung" : "markiert-ohne-abdeckung");
                lauf.klasse((abgedeckt ? "markiert-mit-abdeckung-" : "markiert-ohne-abdeckung-") + art);
            } else if (!abgedeckt) {
                lauf.klasse("block-ohne-scan-abdeckung");
                lauf.klasse("block-ohne-scan-abdeckung-" + detail);
            }
        }
        PseudonymizationResult alle = PSEUDONYMIZER.pseudonymize(text, scan);
        if (!alleFundeErsetzt(text, scan, alle.text())) {
            lauf.verletzt("ak23-fund-nicht-vollstaendig-ersetzt", it);
        }
    }

    private static boolean imScanAbgedeckt(List<Finding> scan, int start, int ende) {
        for (Finding f : scan) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teil 1e (kv-trenner-und-umbruch T-9): dieselben Eigenschaften wie Teil 1d ({@code pruefeText}: Invarianten,
     * Schutzgarantie, Roundtrip, Waechter) plus {@link #pruefeKvE}: Klassen nach Konstruktion, Zaehler der Bloecke
     * ohne Scan-Abdeckung und vollstaendiges Ersetzen aller Funde.
     */
    private static Lauf fuehreKvEAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_E);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV_E);
        List<FallE> faelle = erzeugeKvE(SEED_TEXTE_KV_E);
        for (int it = 0; it < faelle.size(); it++) {
            FallE fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            if (text.contains("[[")) {
                lauf.verletzt("generator-doppelklammer", it);
                continue;
            }
            try {
                pruefeText(lauf, scanner, auswahl, it, new Fall(text, List.of(), false), text);
                pruefeKvE(lauf, scanner, it, fall);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    /**
     * Klassen je Text, hoechstens einmal: Ein Erkenner-Kandidat (LANR und IK wie in {@code TextScanner.standard()})
     * liegt genau auf der Nummer eines Blocks, hat die Quelle der gueltigen Nummer und die erwartete Konfidenz
     * (0,80 fuer Weit-Kontextwort vor der Nummer und nachgestellt, 0,60 mit Komma). Gezaehlt wird nach Blockart
     * ({@code kv-weit-fund}, {@code kv-weit-nachgestellt}, {@code kv-weit-komma}, je mit Art), nach Luecke im
     * Weit-Kontextwort des Fund-Blocks ({@code kv-weit-luecke-b} bis {@code -d}) und fuer Texte mit
     * {@code CR UZ+ LF} und Fund-Block ({@code kv-weit-mit-cr-uz-lf}). Je Block zaehlt
     * {@code block-ohne-scan-abdeckung} die nicht markierten Bloecke, deren Nummernbereich von keinem Scan-Fund ganz
     * abgedeckt ist (strikt 0 erwartet); markierte Bloecke zaehlen getrennt. Danach werden alle Scan-Funde
     * pseudonymisiert und geprueft, dass jeder Fund vollstaendig durch einen Platzhalter ersetzt ist.
     */
    static void pruefeKvE(Lauf lauf, TextScanner scanner, int it, FallE fall) {
        String text = fall.text();
        List<List<Finding>> kandidaten = List.of(STANDARD_ERKENNER.get(ERKENNER_LANR).detect(text),
                STANDARD_ERKENNER.get(ERKENNER_IK).detect(text));
        boolean[][] gesehen = new boolean[ANZAHL_BLOCKARTEN_E][2];
        boolean[] lueckeGesehen = new boolean[LUECKE_D + 1];
        for (BlockE block : fall.bloecke()) {
            int art = block.lanr() ? 0 : 1;
            String quelle = block.lanr() ? LanrRecognizer.SOURCE : IkRecognizer.SOURCE;
            for (Finding c : kandidaten.get(art)) {
                if (c.start() == block.start() && c.end() == block.ende()
                        && c.confidence() == KONFIDENZ_E[block.blockart()] && quelle.equals(c.source())) {
                    gesehen[block.blockart()][art] = true;
                    for (int l = LUECKE_B; l <= LUECKE_D; l++) {
                        lueckeGesehen[l] |= block.blockart() == BLOCK_E_WEIT && (block.luecken() >> l & 1) != 0;
                    }
                }
            }
        }
        boolean irgendwo = false;
        for (int b = 0; b < ANZAHL_BLOCKARTEN_E; b++) {
            boolean mindestensEineArt = false;
            for (int a = 0; a < 2; a++) {
                if (gesehen[b][a]) {
                    lauf.klasse(KLASSE_E[b] + "-" + (a == 0 ? "lanr" : "ik"));
                    mindestensEineArt = true;
                }
            }
            if (mindestensEineArt) {
                lauf.klasse(KLASSE_E[b]);
                irgendwo = true;
            }
        }
        for (int l = LUECKE_B; l <= LUECKE_D; l++) {
            if (lueckeGesehen[l]) {
                lauf.klasse("kv-weit-luecke-" + LUECKEN_NAMEN_E[l]);
            }
        }
        if (irgendwo) {
            lauf.klasse("kv-weit-fund-aus-regel");
            if (KvTestHilfen.enthaeltCrUnsichtbarLf(text)) {
                lauf.klasse("kv-weit-mit-cr-uz-lf");
            }
        }
        for (String gegenprobe : fall.gegenproben()) {
            lauf.klasse("gegenprobe-" + gegenprobe);
        }
        List<Finding> scan = scanner.scan(text);
        for (BlockE block : fall.bloecke()) {
            String art = BLOCK_NAMEN_E[block.blockart()];
            String detail = art + "-" + (block.lanr() ? "lanr" : "ik");
            lauf.klasse("bloecke-" + detail);
            boolean abgedeckt = imScanAbgedeckt(scan, block.start(), block.ende());
            if (block.grenze()) {
                lauf.klasse("block-markiert");
                lauf.klasse(abgedeckt ? "markiert-mit-abdeckung" : "markiert-ohne-abdeckung");
                lauf.klasse((abgedeckt ? "markiert-mit-abdeckung-" : "markiert-ohne-abdeckung-") + art);
            } else if (!abgedeckt) {
                lauf.klasse("block-ohne-scan-abdeckung");
                lauf.klasse("block-ohne-scan-abdeckung-" + detail);
            }
        }
        PseudonymizationResult alle = PSEUDONYMIZER.pseudonymize(text, scan);
        if (!alleFundeErsetzt(text, scan, alle.text())) {
            lauf.verletzt("ak23-fund-nicht-vollstaendig-ersetzt", it);
        }
    }

    /**
     * Der Ausgabetext besteht genau aus den Luecken zwischen den Funden (unveraendert) und je einem Platzhalter
     * {@code [[TYP_n]]} anstelle jedes Fundes; ein Platzhalter enthaelt nie eine Ziffernkette ab sechs Ziffern. Damit
     * steht keine Ziffernkette eines Fundes mehr im Ausgabetext.
     */
    static boolean alleFundeErsetzt(String text, List<Finding> funde, String ausgabe) {
        int pos = 0;
        int vorher = 0;
        for (Finding f : funde) {
            String luecke = text.substring(vorher, f.start());
            if (!ausgabe.startsWith(luecke, pos)) {
                return false;
            }
            pos += luecke.length();
            if (!ausgabe.startsWith("[[", pos)) {
                return false;
            }
            int ende = ausgabe.indexOf("]]", pos + 2);
            if (ende < 0 || laengsteZiffernfolge(ausgabe.substring(pos, ende)) >= 6) {
                return false;
            }
            pos = ende + 2;
            vorher = f.end();
        }
        String rest = text.substring(vorher);
        return ausgabe.startsWith(rest, pos) && pos + rest.length() == ausgabe.length();
    }

    private static int laengsteZiffernfolge(String s) {
        int beste = 0;
        int aktuell = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            aktuell = c >= '0' && c <= '9' ? aktuell + 1 : 0;
            beste = Math.max(beste, aktuell);
        }
        return beste;
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

    /** Hashwert der Teil-1b-Textfolge (kv-kontext-luecken T-1, am unveraenderten Generator gemessen). */
    static final int HASH_TEIL_1B = 0x4D46323A;

    @Test
    void t1_teil1bTextfolgeIstUnveraendert() {
        List<String> texte = new ArrayList<>();
        for (Fall f : erzeugeKv(SEED_TEXTE_KV)) {
            texte.add(f.text());
        }
        assertEquals(HASH_TEIL_1B, texte.hashCode(),
                "Hashwert der Teil-1b-Textfolge, Startwert " + SEED_TEXTE_KV + ", gemessen 0x"
                        + Integer.toHexString(texte.hashCode()));
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

    // ------------------------------------------------------------------ Teil 1c (T-6): Eigenschaften

    /** Hashwert der Teil-1c-Textfolge (kv-kontext-luecken T-6, nach dem Anlegen des Generators gemessen). */
    static final int HASH_TEIL_1C = 0xC32923F8;

    private static List<String> texteTeil1c() {
        List<String> texte = new ArrayList<>();
        for (FallC f : erzeugeKvC(SEED_TEXTE_KV_C)) {
            texte.add(f.text());
        }
        return texte;
    }

    @Test
    void t6_teil1cTextfolgeIstUnveraendert() {
        List<String> texte = texteTeil1c();
        assertEquals(HASH_TEIL_1C, texte.hashCode(), "Hashwert der Teil-1c-Textfolge, Startwert " + SEED_TEXTE_KV_C
                + ", gemessen 0x" + Integer.toHexString(texte.hashCode()));
    }

    @Test
    void t6_teil1cZweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = texteTeil1c();
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, texteTeil1c(), SEED_TEXTE_KV_C, "Teil 1c: Textfolge");
        List<String> teil1b = new ArrayList<>();
        for (Fall f : erzeugeKv(SEED_TEXTE_KV_C)) {
            teil1b.add(f.text());
        }
        assertNotEquals(a.hashCode(), teil1b.hashCode(), "Teil 1c unterscheidet sich von Teil 1b gleichen Seeds");
        assertNotEquals(HASH_TEIL_1B, a.hashCode(), "Teil 1c unterscheidet sich von Teil 1b");
        assertNotEquals(HASH_TEIL_1, a.hashCode(), "Teil 1c unterscheidet sich von Teil 1");
    }

    private static void keineC(String eigenschaft) {
        assertEquals(0, LAUF_KV_C.anzahl(eigenschaft), "Teil 1c: " + LAUF_KV_C.meldung(eigenschaft));
    }

    @Test
    void t6_kvC_waechterUndGeneratorSindSauber() {
        keineC("waechter-gueltiges-neunerfenster-ohne-00");
        keineC("generator-doppelklammer");
    }

    @Test
    void t6_kvC_roundtripIstZeichengleichOhneMeldung() {
        keineC("ak45-roundtrip-ungleich");
        keineC("ak45-pseudonymisierung-wirft");
        keineC("ak45-meldung");
        keineC("ausnahme-im-lauf");
    }

    @Test
    void t6_kvC_scanInvarianteUndListenInvariante() {
        keineC("ak1-scan-invariante");
        keineC("ak1-liste-invariante");
    }

    @Test
    void t6_kvC_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keineC("ak7a-kandidat-nicht-im-scan");
    }

    @Test
    void t6_kvC_keinFremdtextImScanUndBereicheSindDieVerschmolzenenKandidaten() {
        keineC("ak7e-fremdtext-im-scan");
        keineC("ak7e-bereiche-nicht-verschmolzen");
    }

    @Test
    void t6_kvC_keineZiffernketteEinesFundesImAusgabetext() {
        keineC("ak23-fund-nicht-vollstaendig-ersetzt");
    }

    @Test
    void t6_kvC_ersetzPruefungLaeuftNichtInsLeere() {
        String nummer = SynthetischeKvNummern.LANR_GUELTIG;
        String text = "LANR " + nummer + " Ende";
        List<Finding> funde = TextScanner.standard().scan(text);
        assertEquals(1, funde.size());
        assertTrue(alleFundeErsetzt(text, funde, PSEUDONYMIZER.pseudonymize(text, funde).text()));
        assertFalse(alleFundeErsetzt(text, funde, text), "unveraenderter Text: Fund nicht ersetzt");
        assertFalse(alleFundeErsetzt(text, funde, "LANR [[LANR_1]] Ende!"), "Rest weicht ab");
        assertFalse(alleFundeErsetzt(text, funde, "LANR [[LANR_" + nummer + "]] Ende"),
                "Platzhalter mit langer Ziffernkette");
    }

    /**
     * Mindestzahlen der Klassen (Messwerte bei festem Seed in Klammern, Abstand mindestens ein Drittel; Stand nach
     * T-7a, die Werte vor T-7a standen bei Sperrschrift ab drei Buchstaben 656 / IK 388, I K 510, Komma 699 / IK 464,
     * insgesamt 960, IK 939, fraglich 947, manuelle Funde 947, echte Teilmengen 854): Fund der neuen Regel je
     * Blockart und Art (Sperrschrift ab drei Buchstaben 659 / LANR 446 / IK 391, I K 507, Komma 698 / LANR 452 /
     * IK 461), Fund aus neuer Regel insgesamt 961 (Untergrenze 600), Funde je Typ (LANR 843, IK 940; je 500),
     * fraglich 948 (500), Scan-Funde 998 (600), manuelle Funde 944 (500), echte Teilmengen der Scan-Funde 866
     * (500). Gegenueber dem Stand vor T-7a sinken drei Zaehler um je drei ({@code kv-sperr-zwei-fund} 510 auf 507,
     * {@code kv-komma-fund-ik} 464 auf 461, {@code manueller-fund} 947 auf 944), zwei steigen um eins
     * ({@code kv-fund-aus-neuer-regel} 960 auf 961, {@code kv-fraglich} 947 auf 948). Belegt ist die Erklaerung "der
     * Block zaehlt wegen hoeherer Konfidenz nicht mehr in der Klasse" nur fuer {@code kv-sperr-zwei-fund} und
     * {@code kv-komma-fund-ik}; {@code manueller-fund} haengt an der Zufallsauswahl (vermutlich Folgeeffekt der
     * Auswahl, nicht belegt). Im Zufallsvergleich gegen den Stand T-6 ist kein Fund gesenkt oder verschwunden (T-7a).
     */
    @Test
    void t6_kvC_fuzzPruftNichtInsLeere_jedeKlasseKommtVor() {
        mindestens(LAUF_KV_C, "kv-komma-fund", MIN_KV_C_KOMMA);
        mindestens(LAUF_KV_C, "kv-komma-fund-lanr", MIN_KV_C_JE_ART);
        mindestens(LAUF_KV_C, "kv-komma-fund-ik", MIN_KV_C_JE_ART);
        mindestens(LAUF_KV_C, "kv-sperr-fund", MIN_KV_C_SPERR);
        mindestens(LAUF_KV_C, "kv-sperr-fund-lanr", MIN_KV_C_JE_ART);
        mindestens(LAUF_KV_C, "kv-sperr-fund-ik", MIN_KV_C_JE_ART);
        mindestens(LAUF_KV_C, "kv-sperr-zwei-fund", MIN_KV_C_ZWEI);
        mindestens(LAUF_KV_C, "kv-sperr-zwei-fund-ik", MIN_KV_C_ZWEI);
        mindestens(LAUF_KV_C, "kv-fund-aus-neuer-regel", 600);
        mindestens(LAUF_KV_C, "kv-fund-lanr", 500);
        mindestens(LAUF_KV_C, "kv-fund-ik", 500);
        mindestens(LAUF_KV_C, "kv-fraglich", 500);
        mindestens(LAUF_KV_C, "scan-fund", 600);
        mindestens(LAUF_KV_C, "manueller-fund", 500);
        mindestens(LAUF_KV_C, "echte-teilmenge-der-scan-funde", 500);
    }

    /**
     * GRENZE 14h GESCHLOSSEN (T-7a, Plan Kap. 11). Die als 14e markierten Bloecke ohne Scan-Abdeckung (69: 53
     * Sperrbloecke hinter {@code " x "}, 16 Kommabloecke vor {@code " x "}) sind per Konstruktion markiert und
     * zaehlen nicht. Alle uebrigen Bloecke sind abgedeckt; vor T-7a waren es 20 mit dem Muster 14h (laufender
     * IK-Kontext, Vorwort, gesperrtes {@code I K}, Nachsatzwort, gueltige IK, {@code IK: Praxis I K Nr N}). Ein
     * Wert ungleich 0 ist ein Befund.
     */
    @Test
    void t6_kvC_blockOhneScanAbdeckung_grenze14hGeschlossen() {
        assertEquals(0, LAUF_KV_C.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (Grenze 14h geschlossen), Startwert " + SEED_TEXTE_KV_C);
    }

    /** Strukturbeleg 14h: nicht markierte Bloecke ohne Abdeckung nach Blockart und Art, alle 0. */
    @Test
    void t6_kvC_blockOhneScanAbdeckung_nachBlockartUndArt() {
        String basis = "block-ohne-scan-abdeckung-";
        assertEquals(0, LAUF_KV_C.klasse_(basis + "sperr-zwei-ik"), "Sperrschrift zwei Buchstaben, IK");
        assertEquals(0, LAUF_KV_C.klasse_(basis + "sperr-drei-ik"), "Sperrschrift ab drei Buchstaben, IK");
        assertEquals(0, LAUF_KV_C.klasse_(basis + "sperr-drei-lanr"), "Sperrschrift ab drei Buchstaben, LANR");
        assertEquals(0, LAUF_KV_C.klasse_(basis + "sperr-zwei-lanr"), "Sperrschrift zwei Buchstaben, LANR");
        assertEquals(0, LAUF_KV_C.klasse_(basis + "komma-lanr"), "Komma, LANR");
        assertEquals(0, LAUF_KV_C.klasse_(basis + "komma-ik"), "Komma, IK");
    }

    /** Markierte 14e-Bloecke ohne Scan-Abdeckung werden einzeln gezaehlt, damit nichts verdeckt wird. */
    @Test
    void t6_kvC_markierteBloeckeOhneAbdeckung_sindGezaehlt() {
        assertEquals(69, LAUF_KV_C.klasse_("markiert-ohne-abdeckung"), "markierte Bloecke ohne Abdeckung");
        assertEquals(53, LAUF_KV_C.klasse_("markiert-ohne-abdeckung-sperr"), "davon Sperrbloecke hinter x");
        assertEquals(16, LAUF_KV_C.klasse_("markiert-ohne-abdeckung-komma"), "davon Kommabloecke vor x");
    }

    // ------------------------------------------------------------------ Teil 1d (kv-trenner-und-umbruch T-4)

    /** Hashwert der Teil-1d-Textfolge (kv-trenner-und-umbruch T-4, nach dem Anlegen des Generators gemessen). */
    static final int HASH_TEIL_1D = 0x064705DE;

    private static List<String> texteTeil1d() {
        List<String> texte = new ArrayList<>();
        for (FallD f : erzeugeKvD(SEED_TEXTE_KV_D)) {
            texte.add(f.text());
        }
        return texte;
    }

    @Test
    void t4_teil1dTextfolgeIstUnveraendert() {
        List<String> texte = texteTeil1d();
        assertEquals(HASH_TEIL_1D, texte.hashCode(), "Hashwert der Teil-1d-Textfolge, Startwert " + SEED_TEXTE_KV_D
                + ", gemessen 0x" + Integer.toHexString(texte.hashCode()));
    }

    @Test
    void t4_teil1dZweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = texteTeil1d();
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, texteTeil1d(), SEED_TEXTE_KV_D, "Teil 1d: Textfolge");
        List<String> teil1c = new ArrayList<>();
        for (FallC f : erzeugeKvC(SEED_TEXTE_KV_D)) {
            teil1c.add(f.text());
        }
        assertNotEquals(a.hashCode(), teil1c.hashCode(), "Teil 1d unterscheidet sich von Teil 1c gleichen Seeds");
        assertNotEquals(HASH_TEIL_1C, a.hashCode(), "Teil 1d unterscheidet sich von Teil 1c");
        assertNotEquals(HASH_TEIL_1B, a.hashCode(), "Teil 1d unterscheidet sich von Teil 1b");
        assertNotEquals(HASH_TEIL_1, a.hashCode(), "Teil 1d unterscheidet sich von Teil 1");
    }

    private static void keineD(String eigenschaft) {
        assertEquals(0, LAUF_KV_D.anzahl(eigenschaft), "Teil 1d: " + LAUF_KV_D.meldung(eigenschaft));
    }

    @Test
    void t4_kvD_waechterUndGeneratorSindSauber() {
        keineD("waechter-gueltiges-neunerfenster-ohne-00");
        keineD("generator-doppelklammer");
    }

    @Test
    void t4_kvD_roundtripIstZeichengleichOhneMeldung() {
        keineD("ak45-roundtrip-ungleich");
        keineD("ak45-pseudonymisierung-wirft");
        keineD("ak45-meldung");
        keineD("ausnahme-im-lauf");
    }

    @Test
    void t4_kvD_scanInvarianteUndListenInvariante() {
        keineD("ak1-scan-invariante");
        keineD("ak1-liste-invariante");
    }

    @Test
    void t4_kvD_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keineD("ak7a-kandidat-nicht-im-scan");
    }

    @Test
    void t4_kvD_keinFremdtextImScanUndBereicheSindDieVerschmolzenenKandidaten() {
        keineD("ak7e-fremdtext-im-scan");
        keineD("ak7e-bereiche-nicht-verschmolzen");
    }

    @Test
    void t4_kvD_keineZiffernketteEinesFundesImAusgabetext() {
        keineD("ak23-fund-nicht-vollstaendig-ersetzt");
    }

    /**
     * Mindestzahlen der Klassen von Teil 1d. Messwerte bei festem Seed (T-4), je Text hoechstens einmal gezaehlt,
     * Untergrenzen mit mindestens 30 % Abstand. Fund mit der erwarteten Konfidenz je Blockart und Art: Kontextwort
     * (KW) 773 (LANR 518, IK 531, Untergrenzen 500, 350, 350), Sperrschrift 722 (461, 473; 480, 300, 300), erste
     * Nummer direkt hinter dem Kontextwort 664 (419, 406; 440, 280, 270), zweite Nummer hinter dem Umbruch ebenso
     * 664 (419, 406; 440, 280, 270); Fund aus Block insgesamt 977 (650); je Zahl unsichtbarer Zeichen im Umbruch 708,
     * 704, 736 (je 450). Bloecke je Blockart und Art (Zahl der Bloecke, nicht der Texte): KW 713 / 721 (480), Sperr
     * 649 / 667 (430, 440), erste und zweite Nummer je 531 / 517 (350, 340). Gegenproben (Vorkommen): Leerzeile 180,
     * {@code I K} 184, Nummer vor dem Umbruch 202, {@code CR} Leerzeichen {@code LF} 202, {@code CR} U+0085 187 (je
     * 120). Funde je Typ im Scan LANR 918, IK 936 (600), fraglich 618 (400), Scan-Funde 1000 (650), manuelle Funde
     * 949 (600), echte Teilmengen der Scan-Funde 898 (600).
     */
    @Test
    void t4_kvD_fuzzPruftNichtInsLeere_jedeKlasseKommtVor() {
        mindestens(LAUF_KV_D, "kv-umbruch-fund", 650);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-kw", 500);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-kw-lanr", 350);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-kw-ik", 350);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-sperr", 480);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-sperr-lanr", 300);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-sperr-ik", 300);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-erste", 440);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-erste-lanr", 280);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-erste-ik", 270);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-zweite", 440);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-zweite-lanr", 280);
        mindestens(LAUF_KV_D, "kv-umbruch-fund-zweite-ik", 270);
        for (int uz = 1; uz <= MAX_UZ_D; uz++) {
            mindestens(LAUF_KV_D, "kv-umbruch-fund-uz" + uz, 450);
        }
        mindestens(LAUF_KV_D, "bloecke-kw-lanr", 480);
        mindestens(LAUF_KV_D, "bloecke-kw-ik", 480);
        mindestens(LAUF_KV_D, "bloecke-sperr-lanr", 430);
        mindestens(LAUF_KV_D, "bloecke-sperr-ik", 440);
        mindestens(LAUF_KV_D, "bloecke-erste-lanr", 350);
        mindestens(LAUF_KV_D, "bloecke-erste-ik", 340);
        mindestens(LAUF_KV_D, "bloecke-zweite-lanr", 350);
        mindestens(LAUF_KV_D, "bloecke-zweite-ik", 340);
        for (String gegenprobe : List.of("leerzeile", "ik-zwei", "nummer-vor-umbruch", "cr-leerzeichen-lf",
                "cr-nel")) {
            mindestens(LAUF_KV_D, "gegenprobe-" + gegenprobe, 120);
        }
        mindestens(LAUF_KV_D, "kv-fund-lanr", 600);
        mindestens(LAUF_KV_D, "kv-fund-ik", 600);
        mindestens(LAUF_KV_D, "kv-fraglich", 400);
        mindestens(LAUF_KV_D, "scan-fund", 650);
        mindestens(LAUF_KV_D, "manueller-fund", 600);
        mindestens(LAUF_KV_D, "echte-teilmenge-der-scan-funde", 600);
    }

    /**
     * AK-25: Der Zaehler "Block ohne Scan-Abdeckung" ist strikt 0 fuer jeden nicht markierten Block von Teil 1d,
     * insgesamt und nach Blockart und Art. Ein Wert ungleich 0 ist ein Befund, keine Erwartung zum Anpassen.
     */
    @Test
    void t4_kvD_blockOhneScanAbdeckung_istStriktNull_insgesamtUndNachBlockartUndArt() {
        assertEquals(0, LAUF_KV_D.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (strikt 0), Startwert " + SEED_TEXTE_KV_D);
        String basis = "block-ohne-scan-abdeckung-";
        for (String blockart : BLOCK_NAMEN_D) {
            assertEquals(0, LAUF_KV_D.klasse_(basis + blockart + "-lanr"), "Blockart " + blockart + ", LANR");
            assertEquals(0, LAUF_KV_D.klasse_(basis + blockart + "-ik"), "Blockart " + blockart + ", IK");
        }
    }

    /**
     * Die markierten Bloecke (bekannte Grenze 14e: Sperrblock ohne Praefix unmittelbar hinter {@code " x "}) werden
     * einzeln gezaehlt, damit nichts verdeckt wird. Messwerte bei festem Seed: 42 markierte Bloecke, davon 36 ohne
     * und 6 mit Scan-Abdeckung, alle von der Blockart Sperrschrift. Eine Aenderung der Zahlen ist ein Befund.
     */
    @Test
    void t4_kvD_markierteBloecke_sindEinzelnGezaehlt() {
        assertEquals(42, LAUF_KV_D.klasse_("block-markiert"), "markierte Bloecke");
        assertEquals(36, LAUF_KV_D.klasse_("markiert-ohne-abdeckung"), "markierte Bloecke ohne Abdeckung");
        assertEquals(6, LAUF_KV_D.klasse_("markiert-mit-abdeckung"), "markierte Bloecke mit Abdeckung");
        assertEquals(36, LAUF_KV_D.klasse_("markiert-ohne-abdeckung-sperr"), "davon Sperrbloecke ohne Abdeckung");
        assertEquals(6, LAUF_KV_D.klasse_("markiert-mit-abdeckung-sperr"), "davon Sperrbloecke mit Abdeckung");
        assertEquals(LAUF_KV_D.klasse_("block-markiert"), LAUF_KV_D.klasse_("markiert-ohne-abdeckung")
                + LAUF_KV_D.klasse_("markiert-mit-abdeckung"), "jeder markierte Block ist einmal eingeordnet");
        for (String blockart : List.of("kw", "erste", "zweite")) {
            assertEquals(0, LAUF_KV_D.klasse_("markiert-ohne-abdeckung-" + blockart), "nur Sperrbloecke: " + blockart);
            assertEquals(0, LAUF_KV_D.klasse_("markiert-mit-abdeckung-" + blockart), "nur Sperrbloecke: " + blockart);
        }
    }

    /** Die Markierung im Generator: jeder markierte Block ist ein Sperrblock, die Zahl stimmt mit dem Lauf ueberein. */
    @Test
    void t4_kvD_generatorMarkiertNurSperrbloeckeUndHaeltDieZahlDesLaufs() {
        int markiert = 0;
        for (FallD fall : erzeugeKvD(SEED_TEXTE_KV_D)) {
            for (BlockD block : fall.bloecke()) {
                if (block.grenze()) {
                    markiert++;
                    assertEquals(BLOCK_D_SPERR, block.blockart(), "markierter Block ist kein Sperrblock");
                }
            }
        }
        assertEquals(LAUF_KV_D.klasse_("block-markiert"), markiert, "markierte Bloecke: Generator gegen Lauf");
    }

    /**
     * Selbsttest der Zaehler: Ein abgedeckter Block, ein nicht markierter Block ohne Abdeckung und ein markierter
     * Block ohne Abdeckung werden je in der richtigen Klasse gezaehlt (der strikte Zaehler laeuft nicht ins Leere).
     */
    @Test
    void t4_kvD_selbsttest_zaehlerErkennenBlockOhneAbdeckungUndMarkierte() {
        String vor = "LANR:\r" + KvTestHilfen.ZWSP + "\n";
        String nummer = SynthetischeKvNummern.LANR_GUELTIG;
        String text = vor + nummer + " Hallo Welt";
        int ende = vor.length() + nummer.length();
        BlockD gut = new BlockD(BLOCK_D_KW, true, 1, vor.length(), ende, false);
        BlockD leer = new BlockD(BLOCK_D_KW, true, 1, ende + 1, ende + 6, false);
        Lauf lauf = new Lauf(SEED_TEXTE_KV_D);
        pruefeKvD(lauf, TextScanner.standard(), 0, new FallD(text, List.of(gut), List.of()));
        assertEquals(0, lauf.klasse_("block-ohne-scan-abdeckung"), "abgedeckter Block");
        assertEquals(1, lauf.klasse_("kv-umbruch-fund-kw-lanr"), "Fund mit 0,80 auf der Nummer erkannt");
        assertEquals(1, lauf.klasse_("kv-umbruch-fund-uz1"), "ein unsichtbares Zeichen");
        pruefeKvD(lauf, TextScanner.standard(), 1, new FallD(text, List.of(leer), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "nicht markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung-kw-lanr"), "nach Blockart und Art");
        pruefeKvD(lauf, TextScanner.standard(), 2, new FallD(text, List.of(leer.markiert()), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "markierter Block zaehlt nicht dort");
        assertEquals(1, lauf.klasse_("markiert-ohne-abdeckung"), "markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("block-markiert"), "markierter Block gezaehlt");
        assertEquals(0, lauf.anzahl("ak23-fund-nicht-vollstaendig-ersetzt"), "Ersetzen vollstaendig");
    }

    // ------------------------------------------------------------------ Teil 1e (kv-trenner-und-umbruch T-9)

    /** Hashwert der Teil-1e-Textfolge (kv-trenner-und-umbruch T-9, nach dem Anlegen des Generators gemessen). */
    static final int HASH_TEIL_1E = 0x3DB93C45;

    private static List<String> texteTeil1e() {
        List<String> texte = new ArrayList<>();
        for (FallE f : erzeugeKvE(SEED_TEXTE_KV_E)) {
            texte.add(f.text());
        }
        return texte;
    }

    @Test
    void t9_teil1eTextfolgeIstUnveraendert() {
        List<String> texte = texteTeil1e();
        assertEquals(HASH_TEIL_1E, texte.hashCode(), "Hashwert der Teil-1e-Textfolge, Startwert " + SEED_TEXTE_KV_E
                + ", gemessen 0x" + Integer.toHexString(texte.hashCode()));
    }

    @Test
    void t9_teil1eZweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = texteTeil1e();
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, texteTeil1e(), SEED_TEXTE_KV_E, "Teil 1e: Textfolge");
        List<FallE> ersterLauf = erzeugeKvE(SEED_TEXTE_KV_E);
        List<FallE> zweiterLauf = erzeugeKvE(SEED_TEXTE_KV_E);
        assertTrue(ersterLauf.equals(zweiterLauf), "Teil 1e: Bloecke und Gegenproben sind deterministisch");
        List<String> teil1d = new ArrayList<>();
        for (FallD f : erzeugeKvD(SEED_TEXTE_KV_E)) {
            teil1d.add(f.text());
        }
        assertNotEquals(a.hashCode(), teil1d.hashCode(), "Teil 1e unterscheidet sich von Teil 1d gleichen Seeds");
        assertNotEquals(HASH_TEIL_1D, a.hashCode(), "Teil 1e unterscheidet sich von Teil 1d");
        assertNotEquals(HASH_TEIL_1C, a.hashCode(), "Teil 1e unterscheidet sich von Teil 1c");
        assertNotEquals(HASH_TEIL_1B, a.hashCode(), "Teil 1e unterscheidet sich von Teil 1b");
        assertNotEquals(HASH_TEIL_1, a.hashCode(), "Teil 1e unterscheidet sich von Teil 1");
    }

    /**
     * Die Textfolgen von Teil 1, 1b, 1c und 1d sind unveraendert (Plan Regel 6): Ihre Hashwerte werden hier noch
     * einmal gegen die Konstanten geprueft, nachdem Teil 1e dazugekommen ist (kein Generator wurde veraendert).
     */
    @Test
    void t9_bestandsHashesVonTeil1Bis1dSindUnveraendert() {
        List<String> teil1 = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE)) {
            teil1.add(f.text());
        }
        List<String> teil1b = new ArrayList<>();
        for (Fall f : erzeugeKv(SEED_TEXTE_KV)) {
            teil1b.add(f.text());
        }
        List<String> teil1c = new ArrayList<>();
        for (FallC f : erzeugeKvC(SEED_TEXTE_KV_C)) {
            teil1c.add(f.text());
        }
        assertEquals(HASH_TEIL_1, teil1.hashCode(), "Hashwert Teil 1");
        assertEquals(HASH_TEIL_1B, teil1b.hashCode(), "Hashwert Teil 1b");
        assertEquals(HASH_TEIL_1C, teil1c.hashCode(), "Hashwert Teil 1c");
        assertEquals(HASH_TEIL_1D, texteTeil1d().hashCode(), "Hashwert Teil 1d");
    }

    private static void keineE(String eigenschaft) {
        assertEquals(0, LAUF_KV_E.anzahl(eigenschaft), "Teil 1e: " + LAUF_KV_E.meldung(eigenschaft));
    }

    @Test
    void t9_kvE_waechterUndGeneratorSindSauber() {
        keineE("waechter-gueltiges-neunerfenster-ohne-00");
        keineE("generator-doppelklammer");
    }

    @Test
    void t9_kvE_roundtripIstZeichengleichOhneMeldung() {
        keineE("ak45-roundtrip-ungleich");
        keineE("ak45-pseudonymisierung-wirft");
        keineE("ak45-meldung");
        keineE("ausnahme-im-lauf");
    }

    @Test
    void t9_kvE_scanInvarianteUndListenInvariante() {
        keineE("ak1-scan-invariante");
        keineE("ak1-liste-invariante");
    }

    @Test
    void t9_kvE_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keineE("ak7a-kandidat-nicht-im-scan");
    }

    @Test
    void t9_kvE_keinFremdtextImScanUndBereicheSindDieVerschmolzenenKandidaten() {
        keineE("ak7e-fremdtext-im-scan");
        keineE("ak7e-bereiche-nicht-verschmolzen");
    }

    /** AK-25: keine Ziffernkette eines Fundes im Ausgabetext, die Luecken bleiben zeichengleich. */
    @Test
    void t9_kvE_keineZiffernketteEinesFundesImAusgabetext() {
        keineE("ak23-fund-nicht-vollstaendig-ersetzt");
    }

    /** Der Scan ist deterministisch: zwei Laeufe liefern auf jedem Text von Teil 1e dieselbe Fundliste. */
    @Test
    void t9_kvE_scanIstDeterministisch() {
        TextScanner scanner = TextScanner.standard();
        int abweichend = 0;
        for (FallE fall : erzeugeKvE(SEED_TEXTE_KV_E)) {
            abweichend += scanner.scan(fall.text()).equals(scanner.scan(fall.text())) ? 0 : 1;
        }
        assertEquals(0, abweichend, "Teil 1e: Texte mit abweichender Fundliste im zweiten Lauf");
    }

    /**
     * Mindestzahlen der Klassen von Teil 1e. Messwerte bei festem Seed (T-9), je Text hoechstens einmal gezaehlt
     * (ausser den Bloecken), Untergrenzen bei hoechstens 70 % des Messwerts. Fund mit der erwarteten Konfidenz je
     * Blockart und Art: Weit-Kontextwort vor der Nummer 848 (LANR 617, IK 603; Untergrenzen 590, 430, 420),
     * nachgestellt 500 (294, 300; 340, 200, 205), mit Komma 298 (173, 155; 205, 118, 105); Fund aus der neuen Regel
     * insgesamt 953 (660); je Lueckenart im Weit-Kontextwort (b) 766, (c) 779, (d) 776 (530, 540, 540); Texte mit
     * {@code CR UZ+ LF} und Fund 219 (150). Bloecke je Blockart und Art (Zahl der Bloecke, nicht der Texte): Weit
     * 984 / 973 (680, 670), nachgestellt 382 / 404 (260, 275), Komma 259 / 254 (175, 170). Gegenproben (Vorkommen):
     * kleinster Wert 116 (je 80). Funde je Typ im Scan LANR 887, IK 910 (610, 625), fraglich 717 (490), Scan-Funde 992
     * (680), manuelle Funde 984 (680), echte Teilmengen der Scan-Funde 815 (560).
     */
    @Test
    void t9_kvE_fuzzPruftNichtInsLeere_jedeKlasseKommtVor() {
        mindestens(LAUF_KV_E, "kv-weit-fund", 590);
        mindestens(LAUF_KV_E, "kv-weit-fund-lanr", 430);
        mindestens(LAUF_KV_E, "kv-weit-fund-ik", 420);
        mindestens(LAUF_KV_E, "kv-weit-nachgestellt", 340);
        mindestens(LAUF_KV_E, "kv-weit-nachgestellt-lanr", 200);
        mindestens(LAUF_KV_E, "kv-weit-nachgestellt-ik", 205);
        mindestens(LAUF_KV_E, "kv-weit-komma", 205);
        mindestens(LAUF_KV_E, "kv-weit-komma-lanr", 118);
        mindestens(LAUF_KV_E, "kv-weit-komma-ik", 105);
        mindestens(LAUF_KV_E, "kv-weit-fund-aus-regel", 660);
        mindestens(LAUF_KV_E, "kv-weit-luecke-b", 530);
        mindestens(LAUF_KV_E, "kv-weit-luecke-c", 540);
        mindestens(LAUF_KV_E, "kv-weit-luecke-d", 540);
        mindestens(LAUF_KV_E, "kv-weit-mit-cr-uz-lf", 150);
        mindestens(LAUF_KV_E, "bloecke-weit-lanr", 680);
        mindestens(LAUF_KV_E, "bloecke-weit-ik", 670);
        mindestens(LAUF_KV_E, "bloecke-nachgestellt-lanr", 260);
        mindestens(LAUF_KV_E, "bloecke-nachgestellt-ik", 275);
        mindestens(LAUF_KV_E, "bloecke-komma-lanr", 175);
        mindestens(LAUF_KV_E, "bloecke-komma-ik", 170);
        for (String gegenprobe : List.of("ik-zwei-buchstaben", "vier-leerraumzeichen", "zwei-tabs", "zeilenumbruch",
                "satzzeichen", "punkt-vor-luecke", "bindestrich-luecke", "maximale-folge", "ohne-nummer")) {
            mindestens(LAUF_KV_E, "gegenprobe-" + gegenprobe, 80);
        }
        mindestens(LAUF_KV_E, "kv-fund-lanr", 610);
        mindestens(LAUF_KV_E, "kv-fund-ik", 625);
        mindestens(LAUF_KV_E, "kv-fraglich", 490);
        mindestens(LAUF_KV_E, "scan-fund", 680);
        mindestens(LAUF_KV_E, "manueller-fund", 680);
        mindestens(LAUF_KV_E, "echte-teilmenge-der-scan-funde", 560);
    }

    /**
     * AK-25: Der Zaehler "Block ohne Scan-Abdeckung" ist strikt 0 fuer jeden nicht markierten Block von Teil 1e,
     * insgesamt und nach Blockart und Art. Ein Wert ungleich 0 ist ein Befund, keine Erwartung zum Anpassen.
     */
    @Test
    void t9_kvE_blockOhneScanAbdeckung_istStriktNull_insgesamtUndNachBlockartUndArt() {
        assertEquals(0, LAUF_KV_E.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (strikt 0), Startwert " + SEED_TEXTE_KV_E);
        String basis = "block-ohne-scan-abdeckung-";
        for (String blockart : BLOCK_NAMEN_E) {
            assertEquals(0, LAUF_KV_E.klasse_(basis + blockart + "-lanr"), "Blockart " + blockart + ", LANR");
            assertEquals(0, LAUF_KV_E.klasse_(basis + blockart + "-ik"), "Blockart " + blockart + ", IK");
        }
    }

    /**
     * Die markierten Bloecke (Grenze 14e: die Weit-Folge kann ueber eine Luecke nach D-2 mit einem Einzelbuchstaben
     * davor oder dahinter verbunden sein) werden einzeln gezaehlt, damit nichts verdeckt wird. Messwerte bei festem
     * Seed: 188 markierte Bloecke (5,8 % aller Bloecke), davon 132 ohne und 56 mit Scan-Abdeckung; Weit 66 und 11,
     * nachgestellt 41 und 25, Komma 25 und 20. Eine Aenderung der Zahlen ist ein Befund.
     */
    @Test
    void t9_kvE_markierteBloecke_sindEinzelnGezaehlt() {
        assertEquals(188, LAUF_KV_E.klasse_("block-markiert"), "markierte Bloecke");
        assertEquals(132, LAUF_KV_E.klasse_("markiert-ohne-abdeckung"), "markierte Bloecke ohne Abdeckung");
        assertEquals(56, LAUF_KV_E.klasse_("markiert-mit-abdeckung"), "markierte Bloecke mit Abdeckung");
        assertEquals(66, LAUF_KV_E.klasse_("markiert-ohne-abdeckung-weit"), "davon Weit ohne Abdeckung");
        assertEquals(11, LAUF_KV_E.klasse_("markiert-mit-abdeckung-weit"), "davon Weit mit Abdeckung");
        assertEquals(41, LAUF_KV_E.klasse_("markiert-ohne-abdeckung-nachgestellt"), "nachgestellt ohne Abdeckung");
        assertEquals(25, LAUF_KV_E.klasse_("markiert-mit-abdeckung-nachgestellt"), "nachgestellt mit Abdeckung");
        assertEquals(25, LAUF_KV_E.klasse_("markiert-ohne-abdeckung-komma"), "Komma ohne Abdeckung");
        assertEquals(20, LAUF_KV_E.klasse_("markiert-mit-abdeckung-komma"), "Komma mit Abdeckung");
        assertEquals(LAUF_KV_E.klasse_("block-markiert"), LAUF_KV_E.klasse_("markiert-ohne-abdeckung")
                + LAUF_KV_E.klasse_("markiert-mit-abdeckung"), "jeder markierte Block ist einmal eingeordnet");
    }

    /**
     * Die Konstruktion haelt, was sie vermerkt: Je Block stehen an der Nummer neun Ziffern, die Buchstaben des
     * Weit-Kontextworts ergeben ein Wort der Art, die Maske der erweiterten Luecken ist nicht leer, und die Zahl
     * der markierten Bloecke stimmt mit dem Lauf ueberein und bleibt die Ausnahme (unter 10 % aller Bloecke).
     */
    @Test
    void t9_kvE_generatorBloeckeStimmenMitDemTextUeberein() {
        int markiert = 0;
        int bloecke = 0;
        for (FallE fall : erzeugeKvE(SEED_TEXTE_KV_E)) {
            for (BlockE block : fall.bloecke()) {
                bloecke++;
                markiert += block.grenze() ? 1 : 0;
                String nummer = fall.text().substring(block.start(), block.ende());
                assertTrue(nummer.length() == 9 && nummer.chars().allMatch(Character::isDigit), "Nummer des Blocks");
                StringBuilder buchstaben = new StringBuilder();
                fall.text().substring(block.wortStart(), block.wortEnde()).chars()
                        .filter(Character::isLetter).forEach(c -> buchstaben.append(Character.toLowerCase((char) c)));
                List<String> woerter = block.lanr() ? List.of("lanr", "lanrnr", "arztnummer", "arztnr")
                        : List.of("iknr", "iknummer", "institutionskennzeichen");
                assertTrue(woerter.contains(buchstaben.toString()), "Buchstaben des Weit-Kontextworts");
                assertTrue(block.luecken() != 0, "mindestens eine erweiterte Luecke");
            }
        }
        assertEquals(LAUF_KV_E.klasse_("block-markiert"), markiert, "markierte Bloecke: Generator gegen Lauf");
        assertTrue(markiert * 10 < bloecke, "Markierung bleibt die Ausnahme: " + markiert + " von " + bloecke);
    }

    /** Ein Block, dessen Weit-Kontextwort {@code wort} im {@code text} steht (Nummer: letztes Zeichen, unerheblich). */
    private static BlockE blockE(String text, int blockart, String wort) {
        int start = text.indexOf(wort);
        assertTrue(start >= 0, "Testdaten: Wort nicht im Text");
        return new BlockE(blockart, true, 1 << LUECKE_B, text.length() - 1, text.length(), start,
                start + wort.length(), false);
    }

    /**
     * Selbsttest der Markierung: Ein Einzelbuchstabe vor der Weit-Folge (durch eine Luecke nach D-2 getrennt, auch
     * mit unsichtbarem Zeichen dazwischen) und einer dahinter (bei nachgestellten Bloecken) markiert; ein Wort, zwei
     * Buchstaben, vier Leerzeichen, ein Komma, ein Umbruch, die Klammer und ein Punkt mit Tab nicht.
     */
    @Test
    void t9_selbsttest_markierungErkenntEinzelbuchstabenAnDerWeitFolge() {
        String uz = String.valueOf((char) 0x200B);
        String wort = "L. A. N. R.";
        for (String vor : List.of("x ", "x\t", "x.  ", "x. ", "x-", "x" + uz + " ", "x   ", "x." + uz + " ")) {
            String text = vor + wort + " 7";
            assertTrue(verbindbar(text, blockE(text, BLOCK_E_WEIT, wort)), "Einzelbuchstabe davor");
        }
        for (String vor : List.of("Hallo ", "AB ", "x    ", "x, ", "x\n", "x.\t", "x . ", "", "7 ")) {
            String text = vor + wort + " 7";
            assertFalse(verbindbar(text, blockE(text, BLOCK_E_WEIT, wort)), "kein Einzelbuchstabe davor");
        }
        String nachgestellt = "L  A  N  R";
        for (String nach : List.of(" x", ". x", ".x", "\tx", "  x", "-x", ". " + uz + "x")) {
            String text = "7 " + nachgestellt + nach;
            assertTrue(verbindbar(text, blockE(text, BLOCK_E_NACH, nachgestellt)), "Einzelbuchstabe dahinter");
        }
        for (String nach : List.of(")", " Hallo", ". AB", "    x", ", x", "\nx", ".\tx", "")) {
            String text = "7 " + nachgestellt + nach;
            assertFalse(verbindbar(text, blockE(text, BLOCK_E_NACH, nachgestellt)), "kein Einzelbuchstabe dahinter");
        }
        String davor = "x " + wort + " 7";
        assertTrue(verbindbar(davor, blockE(davor, BLOCK_E_NACH, wort)), "davor zaehlt bei jeder Blockart");
        String dahinter = wort + " x";
        assertFalse(verbindbar(dahinter, blockE(dahinter, BLOCK_E_WEIT, wort)), "dahinter zaehlt nur nachgestellt");
    }

    /**
     * Selbsttest der Zaehler: Ein abgedeckter Block, ein nicht markierter Block ohne Abdeckung und ein markierter
     * Block ohne Abdeckung werden je in der richtigen Klasse gezaehlt (der strikte Zaehler laeuft nicht ins Leere).
     */
    @Test
    void t9_kvE_selbsttest_zaehlerErkennenBlockOhneAbdeckungUndMarkierte() {
        String vor = "L. A. N. R. ";
        String nummer = SynthetischeKvNummern.LANR_GUELTIG;
        String text = vor + nummer + " Hallo Welt";
        int ende = vor.length() + nummer.length();
        BlockE gut = new BlockE(BLOCK_E_WEIT, true, 1 << LUECKE_C, vor.length(), ende, 0, 10, false);
        BlockE leer = new BlockE(BLOCK_E_WEIT, true, 1 << LUECKE_C, ende + 1, ende + 6, 0, 10, false);
        Lauf lauf = new Lauf(SEED_TEXTE_KV_E);
        pruefeKvE(lauf, TextScanner.standard(), 0, new FallE(text, List.of(gut), List.of()));
        assertEquals(0, lauf.klasse_("block-ohne-scan-abdeckung"), "abgedeckter Block");
        assertEquals(1, lauf.klasse_("kv-weit-fund-lanr"), "Fund mit 0,80 auf der Nummer erkannt");
        assertEquals(1, lauf.klasse_("kv-weit-luecke-c"), "Luecke (c) im Weit-Kontextwort");
        assertEquals(0, lauf.klasse_("kv-weit-luecke-b"), "keine Luecke (b)");
        pruefeKvE(lauf, TextScanner.standard(), 1, new FallE(text, List.of(leer), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "nicht markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung-weit-lanr"), "nach Blockart und Art");
        pruefeKvE(lauf, TextScanner.standard(), 2, new FallE(text, List.of(leer.markiert()), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "markierter Block zaehlt nicht dort");
        assertEquals(1, lauf.klasse_("markiert-ohne-abdeckung"), "markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("block-markiert"), "markierter Block gezaehlt");
        assertEquals(0, lauf.anzahl("ak23-fund-nicht-vollstaendig-ersetzt"), "Ersetzen vollstaendig");
    }

    // ------------------------------------------------------------------ Teil 1f (kvnr-bsnr-erkennung T-7)

    /** Hashwert der Teil-1f-Textfolge (kvnr-bsnr-erkennung T-7, nach dem Anlegen des Generators gemessen). */
    static final int HASH_TEIL_1F = 0x54652368;

    private static List<String> texteTeil1f() {
        List<String> texte = new ArrayList<>();
        for (FallF f : erzeugeKvF(SEED_TEXTE_KV_F)) {
            texte.add(f.text());
        }
        return texte;
    }

    @Test
    void t7_teil1fTextfolgeIstFestgehalten() {
        List<String> texte = texteTeil1f();
        assertEquals(HASH_TEIL_1F, texte.hashCode(), "Hashwert der Teil-1f-Textfolge, Startwert " + SEED_TEXTE_KV_F
                + ", gemessen 0x" + Integer.toHexString(texte.hashCode()));
    }

    @Test
    void t7_teil1fZweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = texteTeil1f();
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, texteTeil1f(), SEED_TEXTE_KV_F, "Teil 1f: Textfolge");
        assertTrue(erzeugeKvF(SEED_TEXTE_KV_F).equals(erzeugeKvF(SEED_TEXTE_KV_F)),
                "Teil 1f: Bloecke, Gegenproben und Bausteine sind deterministisch");
        List<String> teil1e = new ArrayList<>();
        for (FallE f : erzeugeKvE(SEED_TEXTE_KV_F)) {
            teil1e.add(f.text());
        }
        assertNotEquals(a.hashCode(), teil1e.hashCode(), "Teil 1f unterscheidet sich von Teil 1e gleichen Seeds");
        assertNotEquals(HASH_TEIL_1E, a.hashCode(), "Teil 1f unterscheidet sich von Teil 1e");
        assertNotEquals(HASH_TEIL_1D, a.hashCode(), "Teil 1f unterscheidet sich von Teil 1d");
        assertNotEquals(HASH_TEIL_1C, a.hashCode(), "Teil 1f unterscheidet sich von Teil 1c");
        assertNotEquals(HASH_TEIL_1B, a.hashCode(), "Teil 1f unterscheidet sich von Teil 1b");
        assertNotEquals(HASH_TEIL_1, a.hashCode(), "Teil 1f unterscheidet sich von Teil 1");
    }

    /**
     * Die Textfolgen von Teil 1 bis 1e sind unveraendert (Plan Regel 6): Ihre Hashwerte werden hier noch einmal
     * gegen die Konstanten geprueft, nachdem Teil 1f dazugekommen ist (kein Generator wurde veraendert).
     */
    @Test
    void t7_bestandsHashesVonTeil1Bis1eSindUnveraendert() {
        List<String> teil1 = new ArrayList<>();
        for (Fall f : erzeuge(SEED_TEXTE)) {
            teil1.add(f.text());
        }
        List<String> teil1b = new ArrayList<>();
        for (Fall f : erzeugeKv(SEED_TEXTE_KV)) {
            teil1b.add(f.text());
        }
        List<String> teil1c = new ArrayList<>();
        for (FallC f : erzeugeKvC(SEED_TEXTE_KV_C)) {
            teil1c.add(f.text());
        }
        assertEquals(HASH_TEIL_1, teil1.hashCode(), "Hashwert Teil 1");
        assertEquals(HASH_TEIL_1B, teil1b.hashCode(), "Hashwert Teil 1b");
        assertEquals(HASH_TEIL_1C, teil1c.hashCode(), "Hashwert Teil 1c");
        assertEquals(HASH_TEIL_1D, texteTeil1d().hashCode(), "Hashwert Teil 1d");
        assertEquals(HASH_TEIL_1E, texteTeil1e().hashCode(), "Hashwert Teil 1e");
    }

    private static void keineF(String eigenschaft) {
        assertEquals(0, LAUF_KV_F.anzahl(eigenschaft), "Teil 1f: " + LAUF_KV_F.meldung(eigenschaft));
    }

    @Test
    void t7_kvF_waechterUndGeneratorSindSauber() {
        keineF("waechter-gueltiges-neunerfenster-ohne-00");
        keineF("waechter-kvnr-synthetisch");
        keineF("generator-doppelklammer");
    }

    /** AK-17: Roundtrip zeichengleich ohne Meldung, auch wenn alle Funde ersetzt werden. */
    @Test
    void t7_kvF_roundtripIstZeichengleichOhneMeldung() {
        keineF("ak45-roundtrip-ungleich");
        keineF("ak45-pseudonymisierung-wirft");
        keineF("ak45-meldung");
        keineF("ak17-roundtrip-alle-funde-ungleich");
        keineF("ak17-roundtrip-meldung");
        keineF("ausnahme-im-lauf");
    }

    @Test
    void t7_kvF_scanInvarianteUndListenInvariante() {
        keineF("ak1-scan-invariante");
        keineF("ak1-liste-invariante");
    }

    /**
     * Schutzgarantie ueber {@code STANDARD_ERKENNER}: Jede Position, die ein Kandidat eines Erkenners abdeckt, ist von
     * einem Scan-Fund abgedeckt, ausser in Kandidaten des nachrangigen KVNR-Erkenners, die einen Bestandsfund
     * schneiden (zugeschnitten, Nachtrag 3 zu T-6). In diesen Kandidaten ist aber nicht jede Luecke befreit:
     * Jede zusammenhaengende, von keinem Fund abgedeckte Strecke enthaelt weniger als drei ASCII-Ziffern
     * ({@link #luckeMitDreiZiffernF}, Nachtrag 2 zu T-7, Punkt 4); sonst ist es {@code ak7a-kandidat-nicht-im-scan}.
     * Solche Texte sind exakt gezaehlt: 98. Die Zahl hat nichts mit den "98 = 2 mal 49" Abweichungen der S-3-Pruefung
     * in {@code KvnrFuzzBestandTest} zu tun; beide Zaehlungen messen anderes und treffen zufaellig denselben Wert.
     */
    @Test
    void t7_kvF_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keineF("ak7a-kandidat-nicht-im-scan");
        assertEquals(98, LAUF_KV_F.klasse_("kandidat-position-am-bestand-zugeschnitten"),
                "Texte mit zugeschnittenem Kandidaten, Startwert " + SEED_TEXTE_KV_F);
    }

    /**
     * Selbsttest der Ausnahme der Schutzgarantie: Eine kuenstliche Luecke mit drei Ziffern im Kandidaten wird
     * gemeldet, eine mit zwei Ziffern nicht; eine abgedeckte Stelle trennt die Strecken, Ziffern ausserhalb des
     * Kandidaten und abgedeckte Ziffern zaehlen nicht.
     */
    @Test
    void t7_kvF_selbsttest_ausnahmeDerSchutzgarantieMeldetLueckeMitDreiZiffern() {
        String text = "ab123cd";
        Finding kandidat = Finding.manual(FindingType.KVNR, text, 0, text.length());
        assertTrue(luckeMitDreiZiffernF(text, kandidat, new boolean[text.length()]), "drei Ziffern in der Luecke");
        String zwei = "ab12cd";
        Finding kandidatZwei = Finding.manual(FindingType.KVNR, zwei, 0, zwei.length());
        assertFalse(luckeMitDreiZiffernF(zwei, kandidatZwei, new boolean[zwei.length()]), "zwei Ziffern");
        boolean[] getrennt = new boolean[text.length()];
        getrennt[3] = true;
        assertFalse(luckeMitDreiZiffernF(text, kandidat, getrennt), "eine abgedeckte Stelle trennt die Strecke");
        boolean[] abgedeckt = new boolean[text.length()];
        abgedeckt[2] = true;
        abgedeckt[3] = true;
        abgedeckt[4] = true;
        assertFalse(luckeMitDreiZiffernF(text, kandidat, abgedeckt), "abgedeckte Ziffern zaehlen nicht");
        Finding teil = Finding.manual(FindingType.KVNR, text.substring(0, 4), 0, 4);
        assertFalse(luckeMitDreiZiffernF(text, teil, new boolean[text.length()]), "Ziffern ausserhalb des Kandidaten");
        String zerstreut = "a1b2c3d";
        Finding streut = Finding.manual(FindingType.KVNR, zerstreut, 0, zerstreut.length());
        assertTrue(luckeMitDreiZiffernF(zerstreut, streut, new boolean[zerstreut.length()]),
                "drei Ziffern in einer zusammenhaengenden Strecke mit Buchstaben dazwischen");
    }

    @Test
    void t7_kvF_keinFremdtextImScanUndBereicheSindDieVerschmolzenenKandidatenAusserBeimZuschnitt() {
        keineF("ak7e-fremdtext-im-scan");
        keineF("ak7e-bereiche-nicht-verschmolzen");
        assertEquals(98, LAUF_KV_F.klasse_("bereiche-wegen-zuschnitt"),
                "Texte, deren Bereiche wegen des Zuschneidens nicht verschmolzen sind, Startwert " + SEED_TEXTE_KV_F);
    }

    /**
     * AK-17, AK-21: keine Ziffernkette und keine Form (Buchstabe und Ziffern) eines Fundes im Ausgabetext, die
     * Luecken bleiben zeichengleich, derselbe Wert hat denselben Platzhalter, verschiedene Werte (auch mit und ohne
     * Trenner) haben verschiedene.
     */
    @Test
    void t7_kvF_ausgabetextOhneRestEinesFundesUndPlatzhalterSindKonsistent() {
        keineF("ak23-fund-nicht-vollstaendig-ersetzt");
        keineF("ak17-form-eines-fundes-im-ausgabetext");
        keineF("ak17-platzhalter-nicht-lesbar");
        keineF("ak17-konsistenz");
    }

    /** Gegenproben nach Regel 12: allein gescannt liefert der KVNR-Erkenner keinen Fund. */
    @Test
    void t7_kvF_gegenprobenLiefernAlleinKeinenFund() {
        keineF("gegenprobe-mit-kvnr-fund");
    }

    /**
     * Plan T-7: Ausserhalb der KVNR-Bloecke liegt in Teil 1f keine enge KVNR-Form (Grossbuchstabe, neun Ziffern,
     * Wortgrenzen, gueltige Pruefziffer). Das sichert, dass die Erkennung ohne Kontextwort (Teil 1b) spaeter nichts
     * an Teil 1f aendert. Texte mit einer engen Form im Block: Messwert 484, Untergrenze 338 (70 %).
     */
    @Test
    void t7_kvF_keineEngeKvnrFormAusserhalbDerBloecke() {
        keineF("kvnr-form-ausserhalb-der-bloecke");
        mindestens(LAUF_KV_F, "enge-form-im-block", 338);
    }

    /** Der Scan ist deterministisch: zwei Laeufe liefern auf jedem Text von Teil 1f dieselbe Fundliste. */
    @Test
    void t7_kvF_scanIstDeterministisch() {
        TextScanner scanner = TextScanner.standard();
        int abweichend = 0;
        for (FallF fall : erzeugeKvF(SEED_TEXTE_KV_F)) {
            abweichend += scanner.scan(fall.text()).equals(scanner.scan(fall.text())) ? 0 : 1;
        }
        assertEquals(0, abweichend, "Teil 1f: Texte mit abweichender Fundliste im zweiten Lauf");
    }

    /**
     * Mindestzahlen der Klassen von Teil 1f. Messwerte bei festem Seed (T-7), je Text hoechstens einmal gezaehlt
     * (ausser den Bloecken), Untergrenzen bei hoechstens 70 % des Messwerts (in Klammern der Messwert). Kandidat mit
     * erwarteter Konfidenz und Quelle: insgesamt (913), je Blockart: direkt (416), Fuellwoerter (281), Umbruch (257),
     * Aufzaehlung (213), nachgestellt (255), gesperrt (204), K-1 (164), K-2 (101), Wiederholung (129); je
     * Kontextwortgruppe (KVNR 91, Versicherten 408, Krankenversicherten 422, eGK 361, KV-Nr. 254, Kompositum 188,
     * gesperrt 204) und je Gruppe und Stufe (siehe die Werte unten), je Stufe (sicher 438, wahrscheinlich 561,
     * fraglich 710) und je Quelle (kvnr 742, Pruefziffer ungueltig 390, Form abweichend 511).
     */
    @Test
    void t7_kvF_fuzzPruftNichtInsLeere_jedeKlasseKommtVor() {
        mindestens(LAUF_KV_F, "kvnr-fund", 630);
        String[] arten = {"direkt", "fuellwoerter", "umbruch", "aufzaehlung", "nachgestellt", "sperr", "k1", "k2",
            "wiederholung"};
        int[] minArt = {291, 196, 179, 149, 178, 142, 114, 70, 90};
        for (int i = 0; i < arten.length; i++) {
            mindestens(LAUF_KV_F, "kvnr-fund-" + arten[i], minArt[i]);
        }
        String[] gruppen = {"kvnr", "versicherten", "krankenversichert", "egk", "kv-nr", "kompositum", "sperr"};
        int[] minGruppe = {63, 285, 295, 252, 177, 131, 142};
        for (int i = 0; i < gruppen.length; i++) {
            mindestens(LAUF_KV_F, "kvnr-wort-" + gruppen[i], minGruppe[i]);
        }
        mindestens(LAUF_KV_F, "kvnr-wort-kvnr-sicher", 16);
        mindestens(LAUF_KV_F, "kvnr-wort-kvnr-wahrscheinlich", 21);
        mindestens(LAUF_KV_F, "kvnr-wort-kvnr-fraglich", 38);
        mindestens(LAUF_KV_F, "kvnr-wort-versicherten-sicher", 101);
        mindestens(LAUF_KV_F, "kvnr-wort-versicherten-wahrscheinlich", 105);
        mindestens(LAUF_KV_F, "kvnr-wort-versicherten-fraglich", 151);
        mindestens(LAUF_KV_F, "kvnr-wort-krankenversichert-sicher", 99);
        mindestens(LAUF_KV_F, "kvnr-wort-krankenversichert-wahrscheinlich", 115);
        mindestens(LAUF_KV_F, "kvnr-wort-krankenversichert-fraglich", 182);
        mindestens(LAUF_KV_F, "kvnr-wort-egk-sicher", 86);
        mindestens(LAUF_KV_F, "kvnr-wort-egk-wahrscheinlich", 93);
        mindestens(LAUF_KV_F, "kvnr-wort-egk-fraglich", 147);
        mindestens(LAUF_KV_F, "kvnr-wort-kv-nr-sicher", 51);
        mindestens(LAUF_KV_F, "kvnr-wort-kv-nr-wahrscheinlich", 67);
        mindestens(LAUF_KV_F, "kvnr-wort-kv-nr-fraglich", 102);
        mindestens(LAUF_KV_F, "kvnr-wort-kompositum-sicher", 21);
        mindestens(LAUF_KV_F, "kvnr-wort-kompositum-wahrscheinlich", 40);
        mindestens(LAUF_KV_F, "kvnr-wort-kompositum-fraglich", 85);
        mindestens(LAUF_KV_F, "kvnr-wort-sperr-wahrscheinlich", 86);
        mindestens(LAUF_KV_F, "kvnr-wort-sperr-fraglich", 66);
        mindestens(LAUF_KV_F, "kvnr-stufe-sicher", 306);
        mindestens(LAUF_KV_F, "kvnr-stufe-wahrscheinlich", 392);
        mindestens(LAUF_KV_F, "kvnr-stufe-fraglich", 497);
        mindestens(LAUF_KV_F, "kvnr-quelle-kvnr", 519);
        mindestens(LAUF_KV_F, "kvnr-quelle-kvnr-pruefziffer-ungueltig", 273);
        mindestens(LAUF_KV_F, "kvnr-quelle-kvnr-form-abweichend", 357);
    }

    /**
     * Mindestzahlen der Bloecke je Blockart (Zahl der Bloecke, Messwerte in Klammern: direkt 536, Fuellwoerter 332,
     * Umbruch 300, Aufzaehlung 721, nachgestellt 340, gesperrt 236, K-1 182, K-2 108, Kontextwort am Buchstaben 31,
     * langer Pfad 59, Wiederholung 272, lange Liste 1 702), der Gegenproben (kleinster Wert 73, je 51), der
     * Bestand-Bausteine (kleinster Wert 61, je 42), der Wiederholungen und der Scan-Klassen aus Teil 1.
     * Messwerte der uebrigen Klassen (je Text hoechstens einmal gezaehlt; Mindestzahl je hoechstens 70 %): enge Form im
     * Block 484 (338), Baustein Wiederholung gleich 78 (54) und verschieden 54 (37), gleicher Wert gleicher Platzhalter
     * 79 (55), mit und ohne Trenner verschiedene Platzhalter 54 (37), Scan-Fund KVNR voll 925 (647) und mit Restfund
     * 64 (44), zusammengelegter Fund 28 (19), KV-Fund LANR 559 (391) und IK 671 (469), KV-Fund fraglich 592 (414),
     * Scan-Fund 1 000 (700), manueller Fund 931 (651), echte Teilmenge der Scan-Funde 896 (627).
     */
    @Test
    void t7_kvF_fuzzPruftNichtInsLeere_blockartenGegenprobenUndBausteine() {
        String[] arten = {"direkt", "fuellwoerter", "umbruch", "aufzaehlung", "nachgestellt", "sperr", "k1", "k2",
            "am-buchstaben", "langer-pfad", "wiederholung", "liste-lang"};
        int[] minBloecke = {375, 232, 210, 504, 238, 165, 127, 75, 21, 41, 190, 1191};
        for (int i = 0; i < arten.length; i++) {
            mindestens(LAUF_KV_F, "bloecke-" + arten[i], minBloecke[i]);
        }
        for (String gegenprobe : GEGENPROBEN_F) {
            mindestens(LAUF_KV_F, "gegenprobe-" + gegenprobe, 51);
        }
        for (String baustein : BESTAND_F) {
            mindestens(LAUF_KV_F, "baustein-bestand-" + baustein, 42);
        }
        mindestens(LAUF_KV_F, "baustein-wiederholung-gleich", 54);
        mindestens(LAUF_KV_F, "baustein-wiederholung-verschieden", 37);
        mindestens(LAUF_KV_F, "kvnr-gleicher-wert-gleicher-platzhalter", 55);
        mindestens(LAUF_KV_F, "kvnr-mit-und-ohne-trenner-verschiedene-platzhalter", 37);
        mindestens(LAUF_KV_F, "scan-kvnr-voll", 647);
        mindestens(LAUF_KV_F, "scan-kvnr-restfund", 44);
        mindestens(LAUF_KV_F, "zusammengelegter-fund", 19);
        mindestens(LAUF_KV_F, "kv-fund-lanr", 391);
        mindestens(LAUF_KV_F, "kv-fund-ik", 469);
        mindestens(LAUF_KV_F, "kv-fraglich", 414);
        mindestens(LAUF_KV_F, "scan-fund", 700);
        mindestens(LAUF_KV_F, "manueller-fund", 651);
        mindestens(LAUF_KV_F, "echte-teilmenge-der-scan-funde", 627);
    }

    /**
     * AK-17, AK-21: Der Zaehler "Block ohne Scan-Abdeckung" ist strikt 0 fuer jeden nicht markierten KVNR-Block von
     * Teil 1f, insgesamt und je Blockart. Ein Wert ungleich 0 ist ein Befund, keine Erwartung zum Anpassen.
     */
    @Test
    void t7_kvF_blockOhneScanAbdeckung_istStriktNull_insgesamtUndNachBlockart() {
        assertEquals(0, LAUF_KV_F.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (strikt 0), Startwert " + SEED_TEXTE_KV_F);
        for (String art : BLOCK_NAMEN_F) {
            assertEquals(0, LAUF_KV_F.klasse_("block-ohne-scan-abdeckung-" + art), "Blockart " + art);
        }
    }

    /**
     * Die markierten Bloecke (dokumentierte Grenzen, Markierung aus dem fertigen Text, {@link #grenzklasseF}) sind
     * einzeln gezaehlt, damit nichts verdeckt wird. Messwerte bei festem Seed: 82 markierte Bloecke (1,7 % aller
     * Bloecke), davon 60 ohne und 22 mit Scan-Abdeckung (Nachtrag 2 zu T-10: vorher 77 und 5; Teil 1b deckt 17 Bloecke
     * der Klasse langer Pfad ab). Kontextwort am Buchstaben (Nachtrag zu T-4, Punkt 3): 20, alle
     * ohne Abdeckung. Langer Pfad zwischen Kontextwort und Nummer (Nachtrag 5 zu T-6, Punkt 3): 60, davon 38 ohne und
     * 22 mit Abdeckung (vorher 55 und 5; ein markierter Block ist ein nachgestellter Block hinter einem langen
     * Pfad). KV-Vorwort vor
     * reiner Ziffernfolge mit nachgestelltem Kontextwort (Nachtrag zu T-7, G-K7, engere Regel nach Nachtrag 2 zu
     * T-7, Punkt 3): 2, beide ohne Abdeckung; es sind die Bloecke, die ohne diese Markierung den strikten Zaehler
     * rissen, und kein markierter Block dieser Klasse hat eine Abdeckung. Die Grenze 14e beim gesperrt geschriebenen
     * Kontextwort kommt bei diesem Seed nicht vor. Eine Aenderung der Zahlen ist ein Befund.
     */
    @Test
    void t7_kvF_markierteBloecke_sindEinzelnGezaehlt() {
        assertEquals(82, LAUF_KV_F.klasse_("block-markiert"), "markierte Bloecke");
        // Nachtrag 2 zu T-10: Teil 1b (KVNR ohne Kontextwort) deckt 17 markierte Bloecke ab, alle in der Klasse
        // langer Pfad; alte Werte: ohne Abdeckung 77, mit Abdeckung 5.
        assertEquals(60, LAUF_KV_F.klasse_("markiert-ohne-abdeckung"), "markierte Bloecke ohne Abdeckung");
        assertEquals(22, LAUF_KV_F.klasse_("markiert-mit-abdeckung"), "markierte Bloecke mit Abdeckung");
        assertEquals(2, LAUF_KV_F.klasse_("block-markiert-" + GRENZE_KV_VORWORT_ZIFFERN), "KV-Vorwort, Ziffern");
        assertEquals(2, LAUF_KV_F.klasse_("markiert-ohne-abdeckung-" + GRENZE_KV_VORWORT_ZIFFERN),
                "KV-Vorwort, Ziffern, ohne Abdeckung");
        assertEquals(0, LAUF_KV_F.klasse_("markiert-mit-abdeckung-" + GRENZE_KV_VORWORT_ZIFFERN),
                "KV-Vorwort, Ziffern, mit Abdeckung");
        assertEquals(20, LAUF_KV_F.klasse_("block-markiert-" + GRENZE_AM_BUCHSTABEN), "Kontextwort am Buchstaben");
        assertEquals(20, LAUF_KV_F.klasse_("markiert-ohne-abdeckung-" + GRENZE_AM_BUCHSTABEN),
                "Kontextwort am Buchstaben ohne Abdeckung");
        assertEquals(60, LAUF_KV_F.klasse_("block-markiert-" + GRENZE_LANGER_PFAD), "langer Pfad");
        // Nachtrag 2 zu T-10: alte Werte langer Pfad ohne Abdeckung 55, mit Abdeckung 5 (Teil 1b deckt 17 ab).
        assertEquals(38, LAUF_KV_F.klasse_("markiert-ohne-abdeckung-" + GRENZE_LANGER_PFAD),
                "langer Pfad ohne Abdeckung");
        assertEquals(22, LAUF_KV_F.klasse_("markiert-mit-abdeckung-" + GRENZE_LANGER_PFAD),
                "langer Pfad mit Abdeckung");
        assertEquals(0, LAUF_KV_F.klasse_("block-markiert-" + GRENZE_SPERR_EINZELBUCHSTABE),
                "Einzelbuchstabe vor gesperrtem Kontextwort");
        assertEquals(LAUF_KV_F.klasse_("block-markiert"), LAUF_KV_F.klasse_("markiert-ohne-abdeckung")
                + LAUF_KV_F.klasse_("markiert-mit-abdeckung"), "jeder markierte Block ist einmal eingeordnet");
    }

    /**
     * Der Generator verwirft einen Baustein samt Trenner und zieht neu, wenn der Waechter der Testdatenregeln oder die
     * Pruefung "keine enge KVNR-Form ausserhalb der Bloecke" anschlaegt. Gezaehlt wird das ohne Zufallszahlen
     * ({@code FallF#verworfene}, {@code FallF#aufgegebene}); der Hash der Textfolge bleibt dadurch gleich. Messwert bei
     * festem Seed: 62 verworfene Versuche (bei rund 4 800 Bloecken), kein Baustein, der nach allen
     * {@value #MAX_VERSUCHE_F} Versuchen wegfaellt. Eine Aenderung der Zahl ist ein Befund (der Generator hat sich
     * geaendert), keine Erwartung zum Anpassen.
     */
    @Test
    void t7_kvF_verworfeneBausteine_sindGezaehlt() {
        assertEquals(62, LAUF_KV_F.klasse_("baustein-verworfen"), "verworfene Versuche, Startwert " + SEED_TEXTE_KV_F);
        assertEquals(0, LAUF_KV_F.klasse_("baustein-aufgegeben"), "aufgegebene Bausteine, Startwert "
                + SEED_TEXTE_KV_F);
    }

    /**
     * Die Konstruktion haelt, was sie vermerkt: Je Block mit Erwartung steht an der Nummer eine Form aus Ziffern mit
     * hoechstens einem Buchstaben vorn, und die erwartete Quelle ist nach der unabhaengigen Pruefziffer-Referenz
     * ({@code KvnrBsnrHilfen.kvnrPruefziffer}) aus der Nummer abgeleitet (Regel 9), nicht aus der Konfidenz. Die
     * Zahl der markierten Bloecke stimmt mit dem Lauf ueberein und bleibt die Ausnahme (unter 10 % aller Bloecke).
     */
    @Test
    void t7_kvF_generatorBloeckeStimmenMitDemTextUeberein() {
        assertEquals(ANZAHL_BLOCKARTEN_F, BLOCK_NAMEN_F.length, "Namen der Blockarten");
        assertEquals(ANZAHL_BLOCKARTEN_F, GEWICHT_BLOCK_F.length, "Gewichte der Blockarten");
        int markiert = 0;
        int bloecke = 0;
        for (FallF fall : erzeugeKvF(SEED_TEXTE_KV_F)) {
            for (BlockF block : fall.bloecke()) {
                bloecke++;
                markiert += block.grenze().isEmpty() ? 0 : 1;
                String kompakt = kompaktF(fall.text().substring(block.start(), block.ende()));
                assertFalse(kompakt.isEmpty(), "Nummer des Blocks");
                boolean mitBuchstabe = KvnrFensterfernPruefung.asciiBuchstabe(kompakt.charAt(0));
                String ziffern = mitBuchstabe ? kompakt.substring(1) : kompakt;
                assertTrue(ziffern.chars().allMatch(c -> c >= '0' && c <= '9'), "nur Ziffern hinter dem Buchstaben");
                assertTrue(ziffern.length() >= 6 && ziffern.length() <= 12, "Ziffernzahl 6 bis 12");
                String erwartet = KvnrRecognizer.SOURCE_FORM_ABWEICHEND;
                if (mitBuchstabe && ziffern.length() == 9) {
                    int soll = KvnrBsnrHilfen.kvnrPruefziffer(kompakt.charAt(0), ziffern.substring(0, 8));
                    erwartet = soll == ziffern.charAt(8) - '0' ? KvnrRecognizer.SOURCE
                            : KvnrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG;
                }
                assertEquals(erwartet, block.quelle(), "Quelle des Blocks aus der Nummer, Blockart "
                        + BLOCK_NAMEN_F[block.blockart()]);
                assertTrue(block.konfidenz() < 0 || block.konfidenz() <= 0.95, "Konfidenz der Erwartung");
            }
        }
        assertEquals(LAUF_KV_F.klasse_("block-markiert"), markiert, "markierte Bloecke: Generator gegen Lauf");
        assertTrue(markiert * 10 < bloecke, "Markierung bleibt die Ausnahme: " + markiert + " von " + bloecke);
    }

    private static final String KONTEXT_F = "KVNR ";

    /** Ein Block ueber den Bereich {@code von} bis {@code bis} mit Erwartung (Quelle gueltig) ohne Markierung. */
    private static BlockF blockF(int von, int bis) {
        return new BlockF(BLOCK_F_DIREKT, 0, 0.95, KvnrRecognizer.SOURCE, von, bis, -1, -1, "");
    }

    /**
     * Selbsttest der Zaehler: Ein abgedeckter Block, ein nicht markierter Block ohne Abdeckung und ein markierter Block
     * ohne Abdeckung werden je in der richtigen Klasse gezaehlt (der strikte Zaehler laeuft nicht ins Leere); eine
     * Gegenprobe, die allein einen Fund liefert, wird gemeldet.
     */
    @Test
    void t7_kvF_selbsttest_zaehlerErkennenBlockOhneAbdeckungMarkierteUndGegenprobeMitFund() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = KONTEXT_F + nummer + " Hallo Welt";
        int ende = KONTEXT_F.length() + nummer.length();
        BlockF gut = blockF(KONTEXT_F.length(), ende);
        BlockF leer = blockF(ende + 1, ende + 6);
        Lauf lauf = new Lauf(SEED_TEXTE_KV_F);
        pruefeKvF(lauf, TextScanner.standard(), 0, new FallF(text, List.of(gut), List.of(), List.of()));
        assertEquals(0, lauf.klasse_("block-ohne-scan-abdeckung"), "abgedeckter Block");
        assertEquals(1, lauf.klasse_("kvnr-fund-direkt"), "Kandidat mit 0,95 auf der Nummer erkannt");
        assertEquals(1, lauf.klasse_("kvnr-stufe-sicher"), "Stufe sicher");
        assertEquals(1, lauf.klasse_("kvnr-quelle-kvnr"), "Quelle aus der Nummer");
        pruefeKvF(lauf, TextScanner.standard(), 1, new FallF(text, List.of(leer), List.of(), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "nicht markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung-direkt"), "nach Blockart");
        pruefeKvF(lauf, TextScanner.standard(), 2, new FallF(text, List.of(leer.markiert(GRENZE_LANGER_PFAD)),
                List.of(), List.of()));
        assertEquals(1, lauf.klasse_("block-ohne-scan-abdeckung"), "markierter Block zaehlt nicht dort");
        assertEquals(1, lauf.klasse_("markiert-ohne-abdeckung"), "markierter Block ohne Abdeckung");
        assertEquals(1, lauf.klasse_("markiert-ohne-abdeckung-" + GRENZE_LANGER_PFAD), "nach Grenzklasse");
        assertEquals(1, lauf.klasse_("block-markiert"), "markierter Block gezaehlt");
        assertEquals(0, lauf.anzahl("ak23-fund-nicht-vollstaendig-ersetzt"), "Ersetzen vollstaendig");
        assertEquals(0, lauf.anzahl("gegenprobe-mit-kvnr-fund"), "keine Gegenprobe");
        FallF mitGegenprobe = new FallF(text, List.of(), List.of(new GegenprobeF("test", text)), List.of());
        pruefeKvF(lauf, TextScanner.standard(), 3, mitGegenprobe);
        assertEquals(1, lauf.anzahl("gegenprobe-mit-kvnr-fund"), "Gegenprobe mit Fund wird gemeldet");
    }

    /**
     * Selbsttest der Markierung aus dem Text: Ein Kontextwort unmittelbar vor dem Block (auch mit Bindestrich, auch am
     * Ende eines laengeren Buchstabenlaufs), ein langer Pfad unmittelbar davor mit Kontextwort dahinter und ein
     * Einzelbuchstabe vor dem gesperrten Kontextwort markieren; ein Trenner, ein Buchstabe ohne Kontextwort, ein
     * Kontextwort, das nicht am Ende des Laufs steht, ein kurzer Pfad, ein Pfad ohne Kontextwort, Leerraum ueber drei
     * Zeichen und ein Wort statt des Einzelbuchstabens nicht.
     */
    @Test
    void t7_kvF_selbsttest_markierungErkenntDieGrenzklassenUndGrenztAb() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String pfad = LANGE_PFADE_F[0];
        assertEquals(GRENZE_AM_BUCHSTABEN, markiereF("KVNR" + nummer, 4, 4 + nummer.length(), -1), "Buchstabe davor");
        assertEquals("", markiereF("KVNR " + nummer, 5, 5 + nummer.length(), -1), "Trenner davor");
        assertEquals("", markiereF("Hallo" + nummer, 5, 5 + nummer.length(), -1), "Buchstabe, aber kein Kontextwort");
        assertEquals("", markiereF("KVNRX" + nummer, 5, 5 + nummer.length(), -1), "Kontextwort nicht am Ende");
        assertEquals(GRENZE_AM_BUCHSTABEN, markiereF("xKVNR" + nummer, 5, 5 + nummer.length(), -1),
                "Kontextwort am Ende eines laengeren Buchstabenlaufs");
        String kompositum = "Krankenversicherten-Nummer";
        assertEquals(GRENZE_AM_BUCHSTABEN, markiereF(kompositum + nummer, kompositum.length(),
                kompositum.length() + nummer.length(), -1), "Kontextwort mit Bindestrich");
        String nurNummer = "Wert-Nummer";
        assertEquals("", markiereF(nurNummer + nummer, nurNummer.length(), nurNummer.length() + nummer.length(), -1),
                "Nummer ohne KVNR-Vorwort");
        String lang = "KVNR " + pfad + " " + nummer;
        int start = lang.length() - nummer.length();
        assertEquals(GRENZE_LANGER_PFAD, markiereF(lang, start, lang.length(), -1), "langer Pfad");
        String langDirekt = "KVNR " + pfad + nummer;
        assertEquals("", markiereF(langDirekt, langDirekt.length() - nummer.length(), langDirekt.length(), -1),
                "Buchstabe der Pfadendung vor dem Block, aber kein Kontextwort am Ende des Buchstabenlaufs");
        String kurz = "KVNR /opt/x.pdf " + nummer;
        assertEquals("", markiereF(kurz, kurz.length() - nummer.length(), kurz.length(), -1), "kurzer Pfad");
        String ohneKontext = "Bitte " + pfad + " " + nummer;
        assertEquals("", markiereF(ohneKontext, ohneKontext.length() - nummer.length(), ohneKontext.length(), -1),
                "Pfad ohne Kontextwort davor");
        String weit = "KVNR " + pfad + "    " + nummer;
        assertEquals("", markiereF(weit, weit.length() - nummer.length(), weit.length(), -1),
                "mehr als drei Zeichen Leerraum");
        String sperr = "x K V N R " + nummer;
        assertEquals(GRENZE_SPERR_EINZELBUCHSTABE, markiereF(sperr, sperr.length() - nummer.length(), sperr.length(),
                2), "Einzelbuchstabe vor gesperrtem Kontextwort");
        String sperrOhne = "Hallo K V N R " + nummer;
        assertEquals("", markiereF(sperrOhne, sperrOhne.length() - nummer.length(), sperrOhne.length(), 6),
                "ein Wort vor dem gesperrten Kontextwort");
    }

    /**
     * Selbsttest der Markierung {@link #GRENZE_KV_VORWORT_ZIFFERN} (Nachtrag zu T-7, G-K7, Nachtrag 2 zu T-7, Punkt 3):
     * Markiert wird eine reine Ziffernfolge hinter einem KV-Vorwort im selben Absatz mit hoechstens drei Fuellwoertern
     * und einem Zeilenumbruch dazwischen (auch mit Kuerzelpunkt, mit einer Buchstabenform, die die Zaehlung
     * zuruecksetzt) und mit nachgestelltem Kontextwort in Klammern, hinter Komma oder Semikolon, auch als Kompositum.
     * Nicht markiert werden: die Buchstabenform, ein KV-Vorwort in einem anderen Absatz, kein KV-Vorwort, ein
     * vollwertiges Kontextwort statt des KV-Vorworts oder dazwischen, mehr als drei Fuellwoerter, zwei Umbrueche, ein
     * Satzende dazwischen, ein Vorwort ohne Nr/Nummer, ein fehlendes oder kein Kontextwort hinter der Nummer und ein
     * nachgestelltes Kontextwort, das zu einer anderen Nummer gehoert (unmittelbar vor ihr steht oder die Klammer
     * nicht schliesst).
     */
    @Test
    void t7_kvF_selbsttest_markierungKvVorwortZiffern() {
        String ziffern = SynthetischeKvNummern.LAENGE_8;
        String buchstabenform = SynthetischeKvNummern.KVNR_GUELTIG;
        String[] positiv = {"KV-Nr. " + ziffern + " (KVNR)", "KV-Nr.: bitte siehe " + ziffern + ", eGK Nr.",
            "kv nummer\n" + ziffern + "; Patienten-KVNR", "KV-Nummer, " + ziffern + " (Versicherten-Nr.)",
            "Daten KV Nr. des Herrn\r\n" + ziffern + " (Pat.-eGK-Nr.)",
            "KV Nummer " + ziffern + " ,Krankenversichertennummer", "KV-Nr. Herrn des Muster " + ziffern + " (KVNR)",
            "KV-Nr.\nwurde " + ziffern + " (KVNR)", "KV-Nr. Herr x. " + ziffern + " (KVNR)",
            "KV-Nr. a b " + buchstabenform + " c d " + ziffern + " (KVNR)", "KV-Nr. " + ziffern + ", KVNR\nHallo"};
        for (String text : positiv) {
            int start = text.indexOf(ziffern);
            assertEquals(GRENZE_KV_VORWORT_ZIFFERN, markiereF(text, start, start + ziffern.length(), -1),
                    "Positivfall " + text.length());
        }
        String[] negativ = {"KV-Nr. " + ziffern + " Hallo", "KV-Nr. " + ziffern + " (Hallo)", "KV-Nr. " + ziffern,
            "KV-Nr.\n\n" + ziffern + " (KVNR)", "KV-Nr.\r\n \r\n" + ziffern + " (KVNR)", "Wert " + ziffern + " (KVNR)",
            "KVNR " + ziffern + " (KVNR)", "eGK-Nr. " + ziffern + " (KVNR)", "KV " + ziffern + " (KVNR)",
            "MKV-Nr. " + ziffern + " (KVNR)", "KV-Nrx " + ziffern + " (KVNR)",
            "KV-Nr. Versichertennummer " + ziffern + " (KVNR)", "KV-Nr. Herrn des Muster Hans " + ziffern + " (KVNR)",
            "KV-Nr.\nwurde\n" + ziffern + " (KVNR)", "KV-Nr. Herrn Muster. " + ziffern + " (KVNR)",
            "KV-Nr. Wert! " + ziffern + " (KVNR)", "KV-Nr. " + ziffern + ", KVNR: " + ziffern,
            "KV-Nr. " + ziffern + "; eGK-Nr.= " + ziffern, "KV-Nr. " + ziffern + " (KVNR: " + ziffern + ")",
            "KV-Nr. a b c d " + ziffern + " (KVNR)", "KV-Nr. " + ziffern + " (KVNR"};
        for (String text : negativ) {
            int start = text.indexOf(ziffern);
            assertEquals("", markiereF(text, start, start + ziffern.length(), -1), "Negativfall " + text.length());
        }
        String form = "KV-Nr. " + buchstabenform + " (KVNR)";
        int start = form.indexOf(buchstabenform);
        assertEquals("", markiereF(form, start, start + buchstabenform.length(), -1), "Buchstabenform");
    }

    /** Die Grenzklasse eines Blocks von {@code start} bis {@code ende}; {@code wortStart < 0}: kein gesperrtes Wort. */
    private static String markiereF(String text, int start, int ende, int wortStart) {
        BlockF block = new BlockF(BLOCK_F_DIREKT, 0, 0.95, KvnrRecognizer.SOURCE, start, ende, wortStart,
                wortStart < 0 ? -1 : wortStart + 7, "");
        return grenzklasseF(text, block, KvnrBsnrHilfen.ALT_TRENNER.scan(text));
    }

    /**
     * Selbsttest der Pruefung "keine enge KVNR-Form ausserhalb der Bloecke": Eine gueltige, geschlossene,
     * grossgeschriebene KVNR mit Wortgrenzen ausserhalb eines Blocks wird gezaehlt, innerhalb eines Blocks, mit
     * ungueltiger Pruefziffer, mit Buchstabe davor und mit Trenner nicht.
     */
    @Test
    void t7_kvF_selbsttest_engeFormAusserhalbDerBloeckeWirdGezaehlt() {
        String gueltig = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = "Der Patient " + gueltig + " kam";
        assertEquals(1, engeFormenAusserhalb(text, List.of()), "ausserhalb");
        assertEquals(0, engeFormenAusserhalb(text, List.of(blockF(12, 12 + gueltig.length()))), "im Block");
        assertEquals(1, engeFormenAusserhalb(text, List.of(blockF(12, 15))), "Block deckt die Form nicht ganz");
        String ungueltig = "Der Patient " + SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN + " kam";
        assertEquals(0, engeFormenAusserhalb(ungueltig, List.of()), "ungueltige Pruefziffer");
        assertEquals(0, engeFormenAusserhalb("Der Patient x" + gueltig + " kam", List.of()), "Buchstabe davor");
        String getrennt = "Der Patient " + gueltig.charAt(0) + " " + gueltig.substring(1) + " kam";
        assertEquals(0, engeFormenAusserhalb(getrennt, List.of()), "Trenner hinter dem Buchstaben");
    }

    /**
     * Selbsttest der Rest-Pruefung (AK-17): Steht der Wert eines Fundes im Ausgabetext zusaetzlich zu den Stellen im
     * Text ausserhalb der Funde, ist von dem Fund ein Rest uebrig.
     */
    @Test
    void t7_kvF_selbsttest_restPruefungErkenntDieFormImAusgabetext() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = "KVNR " + nummer + " Ende";
        Finding fund = Finding.manual(FindingType.KVNR, nummer, 5, 5 + nummer.length());
        assertTrue(rest0F(text, List.of(fund), "KVNR [[KVNR_1]] Ende", fund), "ersetzt");
        assertFalse(rest0F(text, List.of(fund), "KVNR " + nummer + " Ende", fund), "nicht ersetzt");
        assertFalse(rest0F(text, List.of(fund), "KVNR [[KVNR_1]] " + nummer, fund), "Rest hinter dem Platzhalter");
        String zweiMal = "KVNR " + nummer + " und " + nummer;
        Finding erster = Finding.manual(FindingType.KVNR, nummer, 5, 5 + nummer.length());
        assertTrue(rest0F(zweiMal, List.of(erster), "KVNR [[KVNR_1]] und " + nummer, erster),
                "dieselbe Nummer ausserhalb des Fundes im Text bleibt im Text (kein Rest des Fundes)");
        assertFalse(rest0F(text, List.of(fund), "KVNR [[KVNR_1]] " + nummer.substring(1), fund),
                "Ziffernkette ohne den Buchstaben bleibt stehen");
        assertFalse(rest0F(text, List.of(fund), "KVNR [[KVNR_1]]" + nummer.charAt(0) + nummer.substring(1, 8), fund),
                "Buchstabe und sieben Ziffern, die Kette von sieben bleibt");
        String ziffern = SynthetischeKvNummern.LAENGE_8;
        String reine = "KVNR " + ziffern + " Ende";
        Finding reinerFund = Finding.manual(FindingType.KVNR, ziffern, 5, 5 + ziffern.length());
        assertTrue(rest0F(reine, List.of(reinerFund), "KVNR [[KVNR_1]] Ende", reinerFund),
                "reine Ziffernfolge ersetzt");
        assertFalse(rest0F(reine, List.of(reinerFund), "KVNR [[KVNR_1]] " + ziffern, reinerFund),
                "reine Ziffernfolge bleibt stehen");
        assertFalse(rest0F(reine, List.of(reinerFund), "KVNR [[KVNR_1]] " + ziffern.substring(0, 6), reinerFund),
                "Kette von sechs Ziffern bleibt stehen");
        assertTrue(rest0F(reine, List.of(reinerFund), "KVNR [[KVNR_1]] " + ziffern.substring(0, 5), reinerFund),
                "fuenf Ziffern sind keine Kette ab sechs");
    }

    // ------------------------------------------------------------------ Teil 1g (kvnr-bsnr-erkennung T-11)

    // Teil 1g misst die KVNR ohne Kontextwort (Teil 1b) ueber die ganze Kette: Erkenner, Konfliktaufloesung,
    // Zuschneiden am Bestand, Pseudonymisierung und Rueckuebersetzung. Aufbau wie bei Teil 1f: zuerst Seeds, Blockarten
    // und Records, dann Generator, Pruefung und Tests. Das gesamte Teil gehoert zum 1b-Inventar (Plan Regel 14).

    // Der Wert gleicht numerisch dem Seed (c) des Pruefstands (SEED_C in KvnrOhneKontextFehlalarmTest); die
    // Generatoren sind getrennt, die Textfolgen unabhaengig. Eine Aenderung verschoebe alle Messwerte von Teil 1g.
    /** Teil 1g: eigene Startwerte, {@value #ANZAHL_TEXTE} Texte mit KVNR ohne Kontextwort und Mischbausteinen. */
    static final long SEED_TEXTE_KV_G = 20261128L;
    static final long SEED_AUSWAHL_KV_G = 949494L;
    private static final String QUELLE_1B_G = KvnrOhneKontextHilfen.QUELLE;
    private static final double KONF_1B_G = 0.60;
    private static final double KONF_DIREKT_G = 0.95;
    private static final double KONF_REST_G = 0.80;
    /** Keine feste Erwartung an Konfidenz und Quelle: mindestens 0,60 aus Teil 1a oder Teil 1b. */
    private static final double KONF_SCHWACH_G = -1;
    /** Grenzblock: keine Erwartung an einen Kandidaten (dokumentierte Grenze, Markierung aus dem Text). */
    private static final double KONF_KEINE_G = -2;
    private static final int MAX_VERSUCHE_G = 60;
    private static final double TOLERANZ_G = 1e-9;
    private static final int ZIFFERN_NUMMER_G = 9;

    /**
     * Blockarten von Teil 1g, je ein erwarteter Fund einer geschlossenen, grossgeschriebenen KVNR mit gueltiger
     * Pruefziffer ohne Kontextwort davor: mit allen zulaessigen Grenzzeichen davor und dahinter (frei), am Textanfang
     * und am Textende, Kontextwort weiter weg als drei Woerter, Kontextwort mit Leerzeile davor, Wort der Negativliste
     * im Umfeld, LANR- oder IK-Kontextwort direkt davor (K-1), Wiederholung derselben Nummer, Dateiname, Nachbar einer
     * IP-Adresse, und die Grenzbloecke "Nicht-ASCII-Ziffer als Nachbar" und "Kontextwort direkt am Buchstaben" sowie
     * der lange Pfad zwischen Kontextwort und Nummer.
     */
    static final int BLOCK_G_FREI = 0;
    static final int BLOCK_G_ANFANG = 1;
    static final int BLOCK_G_ENDE = 2;
    static final int BLOCK_G_KW_WEIT = 3;
    static final int BLOCK_G_LEERZEILE = 4;
    static final int BLOCK_G_NEGATIV = 5;
    static final int BLOCK_G_K1 = 6;
    static final int BLOCK_G_WIEDERHOLUNG = 7;
    static final int BLOCK_G_DATEINAME = 8;
    static final int BLOCK_G_NACHBAR_IP = 9;
    static final int BLOCK_G_NICHT_ASCII = 10;
    static final int BLOCK_G_AM_BUCHSTABEN = 11;
    static final int BLOCK_G_LANGER_PFAD = 12;
    private static final int ANZAHL_BLOCKARTEN_G = 13;
    private static final String[] BLOCK_NAMEN_G = {"frei", "anfang", "ende", "kw-weit", "leerzeile", "negativ", "k1",
        "wiederholung", "dateiname", "nachbar-ip", "nicht-ascii", "am-buchstaben", "langer-pfad"};
    /** Gewichte der Blockarten bei der Wahl im Generator; Anfang und Ende ergeben sich aus der Stellung im Text. */
    private static final int[] GEWICHT_BLOCK_G = {30, 0, 0, 8, 6, 10, 12, 8, 6, 6, 4, 3, 4};
    /** Grenzklassen von Teil 1g (Markierung aus dem Text); {@code GRENZE_AM_BUCHSTABEN} gilt wie in Teil 1f. */
    static final String GRENZE_NICHT_ASCII_ZIFFER = "nichtascii-ziffer";

    private static final String[][] VORN_G = {{" ", "leerzeichen"}, {"\t", "tab"}, {"\n", "zeilenumbruch"},
        {"\r\n", "crlf"}, {NB, "nbsp"}, {"(", "klammer-auf"}, {"\"", "anfuehrung"}, {":", "doppelpunkt"},
        {"=", "gleich"}, {",", "komma"}, {";", "semikolon"}, {".", "punkt"}, {"!", "ausrufezeichen"},
        {"?", "fragezeichen"}, {"[", "eckige-klammer"}, {"'", "apostroph"}, {"#", "raute"}, {"*", "stern"},
        {"|", "senkrechter-strich"}, {String.valueOf((char) 0x200B), "unsichtbar"}};
    private static final String[][] HINTEN_G = {{" ", "leerzeichen"}, {"\t", "tab"}, {"\n", "zeilenumbruch"},
        {"\r\n", "crlf"}, {NB, "nbsp"}, {")", "klammer-zu"}, {"\"", "anfuehrung"}, {".", "punkt"}, {",", "komma"},
        {";", "semikolon"}, {":", "doppelpunkt"}, {"!", "ausrufezeichen"}, {"?", "fragezeichen"},
        {"]", "eckige-klammer"}, {"'", "apostroph"}, {"|", "senkrechter-strich"}, {"*", "stern"},
        {String.valueOf((char) 0x200B), "unsichtbar"}};
    private static final String[] PRAEFIX_G = {"", "", "Der Patient ", "Notiz ", "Datei ", "Nummer ", "ID ",
        "Herr Beispiel, "};
    /**
     * Trenner zwischen den Bausteinen: nie Komma, Semikolon, "und" oder reiner Leerraum. Sonst koennte ein
     * Kontextwort am Anfang des naechsten Bausteins zum nachgestellten Kontextwort der Nummer davor werden (Fund aus
     * Teil 1a statt Teil 1b).
     */
    private static final String[] TRENNER_G = {"\n", "\r\n", ". ", "! ", " | ", "\n\n", "? "};
    private static final String[] ENDUNGEN_G = {".pdf", ".txt", ".docx", ".csv"};
    private static final String[] WORTGRENZE_G = {"x", "7", "_", "-", "/", BS_F, "@", "Q", "3"};
    private static final String[] NEGATIV_WOERTER_G =
            KvnrOhneKontextHilfen.NEGATIVKONTEXT_SCHREIBWEISE.toArray(new String[0]);
    private static final String[] NEGATIV_TRENNER_G = {" ", ": ", ":", "=", " = ", "\t", NB, "\n", "  "};
    private static final String[] NICHT_ASCII_ZIFFERN_G = {String.valueOf((char) 0x665),
        String.valueOf((char) 0xFF15), String.valueOf((char) 0x968)};
    private static final String[] GEGENPROBEN_G = {"ungueltig", "klein", "trenner", "zwei-buchstaben",
        "acht-ziffern", "zehn-ziffern", "wortgrenze-vorn", "wortgrenze-hinten", "dezimal-hinten", "negativkontext"};
    private static final String[] BESTAND_G = {"email-lokal", "email-anfang", "pfad-unix", "pfad-windows",
        "url-query", "url-pfad", "rest-drei", "rest-zwei"};

    /**
     * Ein erwarteter Fund einer Nummer in einem Text von Teil 1g. {@code konfidenz} und {@code quelle} gelten fuer den
     * Kandidaten des KVNR-Erkenners ({@code konfidenz >= 0}: genau diese; {@code KONF_SCHWACH_G}: mindestens 0,60 aus
     * Teil 1a oder 1b; {@code KONF_KEINE_G}: keine Erwartung, Grenzblock). {@code referenz} heisst: Die unabhaengige
     * Referenz der Regel 1b trifft genau diesen Bereich. {@code kontextPruefen}: Steht im selben Absatz davor ein
     * KVNR-Kontextwort (Referenz auf dem Buchstabenstrom, ueberschaetzt), wird die Erwartung auf schwach gesetzt, weil
     * dann auch ein Fund aus Teil 1a moeglich ist. {@code vorn} und {@code hinten} nennen das Grenzzeichen (zum
     * Zaehlen). {@code grenze} ist leer oder die Grenzklasse, nach der der Block an einer dokumentierten Grenze
     * liegt (Markierung aus dem fertigen Text); er muss dann keinen Scan-Fund haben.
     */
    record BlockG(int blockart, double konfidenz, String quelle, boolean referenz, boolean kontextPruefen, int start,
            int ende, String vorn, String hinten, String grenze) {
        BlockG schwach() {
            return new BlockG(blockart, KONF_SCHWACH_G, quelle, referenz, kontextPruefen, start, ende, vorn, hinten,
                    grenze);
        }

        BlockG markiert(String klasse) {
            return new BlockG(blockart, konfidenz, quelle, referenz, kontextPruefen, start, ende, vorn, hinten,
                    klasse);
        }
    }

    /** Eine Gegenprobe (Bereich im Text): allein gescannt liefern weder KVNR-Erkenner noch Referenz einen Fund. */
    record GegenprobeG(String name, int start, int ende) {
    }

    /** Eine Nummer in oder an E-Mail, Pfad oder URL: vom Bestand gedeckt, kein KVNR-Fund im Bereich. */
    record BestandG(String name, int start, int ende) {
    }

    /** Erwarteter Restfund am Bestand; {@code konfidenz < 0}: kein Restfund (weniger als drei Ziffern). */
    record RestG(int start, int ende, double konfidenz) {
    }

    /** Ein Text von Teil 1g mit Bloecken, Gegenproben, Bestand-Bausteinen, Mischbereichen und den Zaehlern. */
    record FallG(String text, List<BlockG> bloecke, List<GegenprobeG> gegenproben, List<String> bausteine,
            List<BestandG> bestand, List<RestG> reste, List<KvnrBsnrHilfen.Bereich> misch, int verworfene,
            int aufgegebene) {
    }

    /** Der Text von Teil 1g im Aufbau; {@link #zurueck} nimmt einen verworfenen Baustein ohne Rest zurueck. */
    private static final class AufbauG {
        final StringBuilder sb = new StringBuilder();
        final List<BlockG> bloecke = new ArrayList<>();
        final List<GegenprobeG> gegenproben = new ArrayList<>();
        final List<String> bausteine = new ArrayList<>();
        final List<BestandG> bestand = new ArrayList<>();
        final List<RestG> reste = new ArrayList<>();
        final List<KvnrBsnrHilfen.Bereich> misch = new ArrayList<>();
        int verworfene;
        int aufgegebene;

        int[] marke() {
            return new int[] {sb.length(), bloecke.size(), gegenproben.size(), bausteine.size(), bestand.size(),
                reste.size(), misch.size()};
        }

        void zurueck(int[] marke) {
            sb.setLength(marke[0]);
            bloecke.subList(marke[1], bloecke.size()).clear();
            gegenproben.subList(marke[2], gegenproben.size()).clear();
            bausteine.subList(marke[3], bausteine.size()).clear();
            bestand.subList(marke[4], bestand.size()).clear();
            reste.subList(marke[5], reste.size()).clear();
            misch.subList(marke[6], misch.size()).clear();
        }
    }

    /** Teil 1g: Texte mit KVNR ohne Kontextwort, Gegenproben, Bestand-Bausteinen und Mischung mit Teil 1b bis 1f. */
    static List<FallG> erzeugeKvG(long seed) {
        Random r = new Random(seed);
        List<FallG> faelle = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            faelle.add(erzeugeTextG(r));
        }
        return faelle;
    }

    /**
     * Ein Text aus 3 bis 10 Bausteinen. Die Mischbausteine (Teil 1b bis 1f) ziehen aus einem eigenen {@code Random},
     * dessen Startwert je Text eine Zufallszahl des Hauptstroms ist; ihre Bloecke werden nicht erfasst, ihr Bereich
     * steht in {@code misch}. Ein Baustein samt Trenner wird verworfen und neu gezogen, wenn der Text danach den
     * Waechter der Testdatenregeln verletzt oder die Referenz der Regel 1b (unabhaengige Nachbildung von Kap. 4.3)
     * nicht genau auf den erwarteten Bloecken trifft ({@link #referenzAbweichungenG}). Nach dem Aufbau werden die
     * Bloecke markiert, die an einer dokumentierten Grenze liegen ({@link #grenzklasseG}), und die Erwartung der
     * Bloecke geschwaecht, vor denen im Absatz ein KVNR-Kontextwort steht; beides zieht keine Zufallszahlen.
     */
    static FallG erzeugeTextG(Random r) {
        AufbauG a = new AufbauG();
        Random mischung = new Random(r.nextLong());
        int n = 3 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            boolean angenommen = false;
            for (int versuch = 0; versuch < MAX_VERSUCHE_G && !angenommen; versuch++) {
                int[] marke = a.marke();
                boolean ohneTrenner = bausteinG(r, mischung, a, i == 0, i == n - 1);
                if (!ohneTrenner) {
                    if (r.nextInt(8) == 0) {
                        a.sb.append(waehle(r, UNSICHTBAR_C));
                    }
                    a.sb.append(waehle(r, TRENNER_G));
                }
                angenommen = aufbauOkG(a);
                if (!angenommen) {
                    a.zurueck(marke);
                    a.verworfene++;
                }
            }
            a.aufgegebene += angenommen ? 0 : 1;
        }
        String text = a.sb.toString();
        List<BlockG> endgueltig = new ArrayList<>(a.bloecke.size());
        for (BlockG block : a.bloecke) {
            BlockG b = block;
            String klasse = grenzklasseG(text, b);
            if (!klasse.isEmpty()) {
                b = b.markiert(klasse);
            } else if (b.kontextPruefen() && b.konfidenz() >= 0 && KvnrBsnrHilfen.kvnrKontextReferenz(
                    text.substring(KvnrFensterfernPruefung.absatzAnfang(text, b.start()), b.start()))) {
                b = b.schwach();
            }
            endgueltig.add(b);
        }
        return new FallG(text, List.copyOf(endgueltig), List.copyOf(a.gegenproben), List.copyOf(a.bausteine),
                List.copyOf(a.bestand), List.copyOf(a.reste), List.copyOf(a.misch), a.verworfene, a.aufgegebene);
    }

    private static boolean aufbauOkG(AufbauG a) {
        String text = a.sb.toString();
        return KvTestHilfen.neunerfenster(text, "00")[1] == 0 && KvnrSynthetischWaechter.pruefe(text).verstoesse() == 0
                && referenzAbweichungenG(text, a.bloecke, a.bestand, a.misch) == 0;
    }

    /**
     * Zahl der Abweichungen zwischen der Referenz der Regel 1b ({@code KvnrOhneKontextHilfen.kvnrOhneKontextReferenz},
     * unabhaengig vom Produkt) und der Konstruktion: (a) ein Treffer der Referenz, der weder genau auf einem Block mit
     * Referenz-Erwartung noch in einer Nummer des Bestand-Bausteins noch in einem Mischbereich liegt; (b) ein Block mit
     * Referenz-Erwartung, den die Referenz nicht genau trifft; (c) ein Block ohne Referenz-Erwartung, auf dem oder an
     * dem (Ueberschneidung) die Referenz trifft. In Teil 1g steht eine gueltige, geschlossene, grossgeschriebene KVNR
     * mit Wortgrenzen und ohne Negativkontext also nur dort, wo es die Konstruktion vorsieht.
     */
    static int referenzAbweichungenG(String text, List<BlockG> bloecke, List<BestandG> bestand,
            List<KvnrBsnrHilfen.Bereich> misch) {
        int abweichungen = 0;
        List<KvnrBsnrHilfen.Bereich> treffer = KvnrOhneKontextHilfen.kvnrOhneKontextReferenz(text);
        for (KvnrBsnrHilfen.Bereich t : treffer) {
            boolean erlaubt = false;
            for (BlockG block : bloecke) {
                erlaubt |= block.referenz() && block.start() == t.start() && block.ende() == t.ende();
            }
            for (BestandG s : bestand) {
                erlaubt |= s.start() <= t.start() && t.ende() <= s.ende();
            }
            for (KvnrBsnrHilfen.Bereich m : misch) {
                erlaubt |= m.start() <= t.start() && t.ende() <= m.ende();
            }
            abweichungen += erlaubt ? 0 : 1;
        }
        for (BlockG block : bloecke) {
            boolean genau = false;
            boolean beruehrt = false;
            for (KvnrBsnrHilfen.Bereich t : treffer) {
                genau |= block.start() == t.start() && block.ende() == t.ende();
                beruehrt |= block.start() < t.ende() && t.start() < block.ende();
            }
            abweichungen += block.referenz() ? (genau ? 0 : 1) : (beruehrt ? 1 : 0);
        }
        return abweichungen;
    }

    private static boolean nichtAsciiZiffer(int codepunkt) {
        return codepunkt > 0x7F && Character.isDigit(codepunkt);
    }

    /**
     * Grenzklasse eines Blocks, ermittelt aus dem fertigen Text, unabhaengig vom KVNR-Erkenner; leer, wenn der Block an
     * keiner dokumentierten Grenze liegt. Zwei Klassen: (a) {@link #GRENZE_AM_BUCHSTABEN}: unmittelbar vor dem Block
     * steht ein ASCII-Buchstabe, und der Buchstabenlauf davor endet auf ein KVNR-Kontextwort
     * ({@link #endetAufKvnrKontextwortF}; G-K9, auch Teil 1b schuetzt hier nicht, weil er vor dem Buchstaben eine
     * Wortgrenze verlangt); (b) {@link #GRENZE_NICHT_ASCII_ZIFFER}: unmittelbar vor oder hinter dem Block steht eine
     * Ziffer ausserhalb von ASCII (Lesart "sperrend" im Javadoc von {@code KvnrOhneKontext}, Nachtrag zu T-9, A-3).
     */
    static String grenzklasseG(String text, BlockG block) {
        if (block.start() > 0 && endetAufKvnrKontextwortF(text, block.start())) {
            return GRENZE_AM_BUCHSTABEN;
        }
        boolean davor = block.start() > 0 && nichtAsciiZiffer(Character.codePointBefore(text, block.start()));
        boolean dahinter = block.ende() < text.length() && nichtAsciiZiffer(text.codePointAt(block.ende()));
        return davor || dahinter ? GRENZE_NICHT_ASCII_ZIFFER : "";
    }

    private static String[] waehleG(Random r, String[][] liste) {
        return liste[r.nextInt(liste.length)];
    }

    private static String gueltigG(Random r) {
        return SynthetischeKvNummern.fremdKvnr(r, true);
    }

    private static String ungueltigG(Random r) {
        return SynthetischeKvNummern.fremdKvnr(r, false);
    }

    private static void fundG(AufbauG a, String nummer, int blockart, double konfidenz, String quelle,
            boolean referenz, boolean kontextPruefen, String vorn, String hinten) {
        int start = a.sb.length();
        a.sb.append(nummer);
        a.bloecke.add(new BlockG(blockart, konfidenz, quelle, referenz, kontextPruefen, start, a.sb.length(), vorn,
                hinten, ""));
    }

    /** Ein Block mit dem erwarteten Fund der Regel 1b (0,60, Quelle {@code kvnr-ohne-kontext}). */
    private static void fund1bG(AufbauG a, String nummer, int blockart, boolean kontextPruefen, String vorn,
            String hinten) {
        fundG(a, nummer, blockart, KONF_1B_G, QUELLE_1B_G, true, kontextPruefen, vorn, hinten);
    }

    /** Ein Block mit dem erwarteten Fund aus Teil 1a bei direktem Kontext (0,95, Quelle {@code kvnr}). */
    private static void fund1aG(AufbauG a, String nummer, int blockart, boolean referenz, String vorn,
            String hinten) {
        fundG(a, nummer, blockart, KONF_DIREKT_G, KvnrRecognizer.SOURCE, referenz, false, vorn, hinten);
    }

    /** Ein Baustein; {@code true}, wenn kein Trenner folgen soll (Block am Textende). */
    private static boolean bausteinG(Random r, Random mischung, AufbauG a, boolean erste, boolean letzte) {
        int wahl = r.nextInt(100);
        if (erste && wahl < 12) {
            blockFreiG(r, a, BLOCK_G_ANFANG);
            return false;
        }
        if (letzte && wahl < 12) {
            blockFreiG(r, a, BLOCK_G_ENDE);
            return true;
        }
        if (wahl < 52) {
            int summe = 0;
            for (int g : GEWICHT_BLOCK_G) {
                summe += g;
            }
            int x = r.nextInt(summe);
            int b = 0;
            while (x >= GEWICHT_BLOCK_G[b]) {
                x -= GEWICHT_BLOCK_G[b];
                b++;
            }
            kvnrBlockG(r, a, b);
        } else if (wahl < 64) {
            gegenprobeG(r, a);
        } else if (wahl < 74) {
            bestandBausteinG(r, a);
        } else {
            mischbausteinG(mischung, a);
        }
        return false;
    }

    private static void kvnrBlockG(Random r, AufbauG a, int blockart) {
        switch (blockart) {
            case BLOCK_G_FREI:
                blockFreiG(r, a, BLOCK_G_FREI);
                break;
            case BLOCK_G_KW_WEIT:
                blockKwWeitG(r, a);
                break;
            case BLOCK_G_LEERZEILE:
                blockLeerzeileG(r, a);
                break;
            case BLOCK_G_NEGATIV:
                blockNegativG(r, a);
                break;
            case BLOCK_G_K1:
                blockK1G(r, a);
                break;
            case BLOCK_G_WIEDERHOLUNG:
                blockWiederholungG(r, a);
                break;
            case BLOCK_G_DATEINAME:
                blockDateinameG(r, a);
                break;
            case BLOCK_G_NACHBAR_IP:
                blockNachbarIpG(r, a);
                break;
            case BLOCK_G_NICHT_ASCII:
                blockNichtAsciiG(r, a);
                break;
            case BLOCK_G_AM_BUCHSTABEN:
                blockAmBuchstabenG(r, a);
                break;
            default:
                blockLangerPfadG(r, a);
                break;
        }
    }

    /** Frei, am Textanfang oder am Textende: Praefix, Grenzzeichen davor, die Nummer, Grenzzeichen dahinter. */
    private static void blockFreiG(Random r, AufbauG a, int blockart) {
        String[] vorn = blockart == BLOCK_G_ANFANG ? new String[] {"", "textanfang"} : waehleG(r, VORN_G);
        String[] hinten = blockart == BLOCK_G_ENDE ? new String[] {"", "textende"} : waehleG(r, HINTEN_G);
        if (blockart != BLOCK_G_ANFANG) {
            a.sb.append(waehle(r, PRAEFIX_G));
        }
        a.sb.append(vorn[0]);
        fund1bG(a, gueltigG(r), blockart, true, vorn[1], hinten[1]);
        a.sb.append(hinten[0]);
    }

    /** Kontextwort und mindestens vier Fuellwoerter vor der Nummer: der Kontext ist erloschen (AK-11). */
    private static void blockKwWeitG(Random r, AufbauG a) {
        KwF kw = kwF(r, true);
        String[] hinten = waehleG(r, HINTEN_G);
        a.sb.append(schreibungF(r, kw.text())).append(waehle(r, DIREKT_F));
        for (int i = 4 + r.nextInt(3); i > 0; i--) {
            a.sb.append(waehle(r, FUELLWOERTER_F)).append(' ');
        }
        fund1bG(a, gueltigG(r), BLOCK_G_KW_WEIT, false, "leerzeichen", hinten[1]);
        a.sb.append(hinten[0]);
    }

    /** Kontextwort, Leerzeile, Nummer: die Leerzeile beendet den Kontext (AK-9 der Vorgaenger). */
    private static void blockLeerzeileG(Random r, AufbauG a) {
        KwF kw = kwF(r, true);
        String[] hinten = waehleG(r, HINTEN_G);
        a.sb.append(schreibungF(r, kw.text())).append(r.nextBoolean() ? ":" : "");
        a.sb.append(waehle(r, new String[] {"\n\n", "\r\n\r\n", "\n  \n", "\r\r"}));
        fund1bG(a, gueltigG(r), BLOCK_G_LEERZEILE, false, "zeilenumbruch", hinten[1]);
        a.sb.append(hinten[0]);
    }

    /**
     * Negativliste im Umfeld (AK-14): Fuellwort statt Negativkontext direkt davor (Fund 0,60) oder ein Wort der Liste
     * und davor {@code KVNR} (Fund 0,95 aus Teil 1a); die Gegenproben mit dem Wort direkt davor stehen bei den
     * Gegenproben.
     */
    private static void blockNegativG(Random r, AufbauG a) {
        String[] hinten = waehleG(r, HINTEN_G);
        String wort = waehle(r, NEGATIV_WOERTER_G);
        int v = r.nextInt(3);
        if (v == 0) {
            a.sb.append("Rechnung fuer Patient ");
            fund1bG(a, gueltigG(r), BLOCK_G_NEGATIV, true, "leerzeichen", hinten[1]);
        } else if (v == 1) {
            a.sb.append(wort).append(waehle(r, NEGATIV_TRENNER_G)).append(waehle(r, FUELLWOERTER_F)).append(' ');
            fund1bG(a, gueltigG(r), BLOCK_G_NEGATIV, true, "leerzeichen", hinten[1]);
        } else {
            a.sb.append(wort).append(' ').append(waehle(r, new String[] {"KVNR", "kvnr", "Versichertennummer"}))
                    .append(waehle(r, new String[] {" ", ": ", " = "}));
            fund1aG(a, gueltigG(r), BLOCK_G_NEGATIV, true, "leerzeichen", hinten[1]);
        }
        a.sb.append(hinten[0]);
    }

    /**
     * K-1: LANR- oder IK-Kontextwort direkt vor der KVNR-Form (oder nachgestellt in Klammern). Auf Erkennerebene ein
     * Fund 0,60; im Scan umschliesst er den LANR- oder IK-Fund auf den Ziffern.
     */
    private static void blockK1G(Random r, AufbauG a) {
        boolean lanr = r.nextBoolean();
        String kw = waehle(r, lanr ? KW_LANR_F : KW_IK_F);
        String[] hinten = waehleG(r, HINTEN_G);
        if (r.nextInt(100) < 25) {
            String[] vorn = waehleG(r, VORN_G);
            a.sb.append(vorn[0]);
            fund1bG(a, gueltigG(r), BLOCK_G_K1, true, vorn[1], "leerzeichen");
            a.sb.append(" (").append(kw).append(')');
            a.bausteine.add("k1-nachgestellt");
        } else {
            a.sb.append(kw).append(waehle(r, KV_ZWISCHEN));
            fund1bG(a, gueltigG(r), BLOCK_G_K1, true, "leerzeichen", hinten[1]);
            a.sb.append(hinten[0]);
            a.bausteine.add("k1-davor");
        }
    }

    /**
     * Dieselbe Nummer zweimal (AK-17): beide ohne Kontextwort; mit Kontextwort vor der ersten (Teil 1a, dann Teil 1b);
     * Kontextwort vor der zweiten; die zweite mit Trenner hinter dem Buchstaben (anderer Platzhalter).
     */
    private static void blockWiederholungG(Random r, AufbauG a) {
        String n = gueltigG(r);
        int v = r.nextInt(4);
        if (v == 0) {
            fund1bG(a, n, BLOCK_G_WIEDERHOLUNG, true, "", "leerzeichen");
            a.sb.append(" ... ");
            fund1bG(a, n, BLOCK_G_WIEDERHOLUNG, true, "leerzeichen", "");
        } else if (v == 1) {
            a.sb.append("KVNR ");
            fund1aG(a, n, BLOCK_G_WIEDERHOLUNG, true, "leerzeichen", "leerzeichen");
            a.sb.append(" ... ");
            fundG(a, n, BLOCK_G_WIEDERHOLUNG, KONF_SCHWACH_G, QUELLE_1B_G, true, false, "leerzeichen", "");
        } else if (v == 2) {
            fund1bG(a, n, BLOCK_G_WIEDERHOLUNG, true, "", "leerzeichen");
            a.sb.append(" ... KVNR ");
            fund1aG(a, n, BLOCK_G_WIEDERHOLUNG, true, "leerzeichen", "");
        } else {
            fund1bG(a, n, BLOCK_G_WIEDERHOLUNG, true, "", "leerzeichen");
            a.sb.append(" ... KVNR ");
            String getrennt = n.charAt(0) + waehle(r, GRUPPENTRENNER) + n.substring(1);
            fund1aG(a, getrennt, BLOCK_G_WIEDERHOLUNG, false, "leerzeichen", "");
        }
        a.bausteine.add("wiederholung-" + v);
    }

    /** Dateiname ohne Pfad (AK-15): die Nummer und eine Endung ergeben einen KVNR-Fund. */
    private static void blockDateinameG(Random r, AufbauG a) {
        String[] vorn = waehleG(r, VORN_G);
        a.sb.append("Datei ").append(vorn[0]);
        fund1bG(a, gueltigG(r), BLOCK_G_DATEINAME, true, vorn[1], "punkt-endung");
        a.sb.append(waehle(r, ENDUNGEN_G));
    }

    /** Die Nummer neben einer IP-Adresse, durch Komma und Leerzeichen getrennt (aneinandergrenzende Funde). */
    private static void blockNachbarIpG(Random r, AufbauG a) {
        String[] vorn = waehleG(r, VORN_G);
        a.sb.append(vorn[0]);
        if (r.nextBoolean()) {
            fund1bG(a, gueltigG(r), BLOCK_G_NACHBAR_IP, true, vorn[1], "komma");
            a.sb.append(", ").append(ipKv(r));
        } else {
            a.sb.append(ipKv(r)).append(", ");
            fund1bG(a, gueltigG(r), BLOCK_G_NACHBAR_IP, true, "leerzeichen", "");
        }
    }

    /** Grenzblock: eine Ziffer ausserhalb von ASCII direkt vor oder hinter der Nummer (Lesart "sperrend"). */
    private static void blockNichtAsciiG(Random r, AufbauG a) {
        String ziffer = waehle(r, NICHT_ASCII_ZIFFERN_G);
        boolean davor = r.nextBoolean();
        a.sb.append(waehle(r, PRAEFIX_G)).append(davor ? ziffer : " ");
        fundG(a, gueltigG(r), BLOCK_G_NICHT_ASCII, KONF_KEINE_G, QUELLE_1B_G, false, false,
                davor ? "nicht-ascii-ziffer" : "leerzeichen", davor ? "leerzeichen" : "nicht-ascii-ziffer");
        a.sb.append(davor ? " " : ziffer);
    }

    /** Grenzblock: Kontextwort ohne Trenner am Buchstaben der Nummer (G-K9); das Wort endet auf einen Buchstaben. */
    private static void blockAmBuchstabenG(Random r, AufbauG a) {
        KwF kw = kwF(r, false);
        while (!Character.isLetter(kw.text().charAt(kw.text().length() - 1))) {
            kw = kwF(r, false);
        }
        a.sb.append(kw.text());
        fundG(a, gueltigG(r), BLOCK_G_AM_BUCHSTABEN, KONF_KEINE_G, QUELLE_1B_G, false, false, "", "leerzeichen");
        a.sb.append(' ');
    }

    /** Ein langer Pfad zwischen Kontextwort und Nummer: Kontext erloschen (G-K15), Teil 1b findet die Nummer. */
    private static void blockLangerPfadG(Random r, AufbauG a) {
        KwF kw = kwF(r, false);
        String[] hinten = waehleG(r, HINTEN_G);
        a.sb.append(kw.text()).append(' ').append(waehle(r, LANGE_PFADE_F)).append(' ');
        fund1bG(a, gueltigG(r), BLOCK_G_LANGER_PFAD, false, "leerzeichen", hinten[1]);
        a.sb.append(hinten[0]);
    }

    /**
     * Gegenproben nach AK-12 bis AK-14: ungueltige Pruefziffer, Kleinbuchstabe, Trenner in der Nummer, zwei Buchstaben,
     * acht oder zehn Ziffern, verletzte Wortgrenze vorn oder hinten (jedes Zeichen aus AK-13), Dezimalpunkt oder
     * -komma mit Ziffer dahinter und ein Wort der Negativliste direkt davor (mit allen Trennern, auch Tab,
     * Zeilenumbruch und geschuetztem Leerzeichen). Die Gegenproben tragen keinen Block; allein liefern weder der
     * KVNR-Erkenner noch die Referenz einen Fund (geprueft in {@code pruefeKvG}).
     */
    private static void gegenprobeG(Random r, AufbauG a) {
        int v = r.nextInt(GEGENPROBEN_G.length);
        int vor = a.sb.length();
        String gueltig = gueltigG(r);
        switch (v) {
            case 0:
                a.sb.append(waehle(r, PRAEFIX_G)).append(ungueltigG(r));
                break;
            case 1:
                a.sb.append(waehle(r, PRAEFIX_G)).append(gueltig.toLowerCase(Locale.ROOT));
                break;
            case 2:
                String t = waehle(r, GRUPPENTRENNER);
                if (r.nextBoolean()) {
                    a.sb.append(gueltig.charAt(0)).append(t).append(gueltig, 1, gueltig.length());
                } else {
                    int[] gruppen = GRUPPEN_F[r.nextInt(GRUPPEN_F.length)];
                    int pos = 1;
                    a.sb.append(gueltig.charAt(0));
                    for (int i = 0; i < gruppen.length; i++) {
                        a.sb.append(i > 0 ? t : "").append(gueltig, pos, pos + gruppen[i]);
                        pos += gruppen[i];
                    }
                }
                break;
            case 3:
                a.sb.append((char) ('A' + r.nextInt(26))).append(gueltig);
                break;
            case 4:
                a.sb.append(gueltig, 0, gueltig.length() - 1);
                break;
            case 5:
                a.sb.append(gueltig).append(r.nextInt(10));
                break;
            case 6:
                a.sb.append(waehle(r, WORTGRENZE_G)).append(gueltig);
                break;
            case 7:
                a.sb.append(gueltig).append(waehle(r, WORTGRENZE_G));
                break;
            case 8:
                a.sb.append(gueltig).append(r.nextBoolean() ? '.' : ',').append(1 + r.nextInt(9));
                break;
            default:
                String wort = waehle(r, NEGATIV_WOERTER_G);
                int fall = r.nextInt(3);
                wort = fall == 0 ? wort : fall == 1 ? wort.toLowerCase(Locale.ROOT) : wort.toUpperCase(Locale.ROOT);
                a.sb.append(wort).append(waehle(r, NEGATIV_TRENNER_G)).append(gueltig);
                break;
        }
        a.gegenproben.add(new GegenprobeG(GEGENPROBEN_G[v], vor, a.sb.length()));
    }

    /**
     * Bestand-Bausteine (Nachtrag zu T-11, Punkt 2): Eine gueltige KVNR-Form in oder an E-Mail, Pfad oder URL: vom
     * Bestand gedeckt, kein KVNR-Fund im Bereich (AK-15); und KVNR-Kontextwort, Buchstabe, Ziffern in zwei Gruppen,
     * deren hintere der lokale Teil einer E-Mail verschluckt: bleiben genau drei Ziffern ausserhalb, entsteht ein
     * Restfund (Quelle {@code kvnr-rest}, hoechstens 0,80 trotz Konfidenz 0,95 des Kandidaten, Mutanten der Schwelle
     * und des Deckels fallen auf); bleiben zwei, gibt es keinen Restfund (G-K14).
     */
    private static void bestandBausteinG(Random r, AufbauG a) {
        int v = r.nextInt(BESTAND_G.length);
        String n = gueltigG(r);
        a.sb.append(waehle(r, new String[] {"", "Datei ", "Wert "}));
        if (v >= 6) {
            int vorne = v == 6 ? 3 : 2;
            a.sb.append("KVNR ");
            int start = a.sb.length();
            a.sb.append(n, 0, 1 + vorne).append(' ').append(n, 1 + vorne, n.length()).append("@example.invalid");
            a.reste.add(new RestG(start, start + 1 + vorne, v == 6 ? KONF_REST_G : KONF_SCHWACH_G));
        } else {
            int start;
            switch (v) {
                case 0:
                    start = a.sb.length();
                    a.sb.append(n).append("@example.invalid");
                    break;
                case 1:
                    start = a.sb.length();
                    a.sb.append(n).append(".erika@example.invalid");
                    break;
                case 2:
                    a.sb.append("/srv/x/");
                    start = a.sb.length();
                    a.sb.append(n).append(".pdf");
                    break;
                case 3:
                    a.sb.append("C:").append(BS_F).append("Praxis").append(BS_F);
                    start = a.sb.length();
                    a.sb.append(n).append(BS_F).append("x.txt");
                    break;
                case 4:
                    a.sb.append("http://a.invalid/?id=");
                    start = a.sb.length();
                    a.sb.append(n);
                    break;
                default:
                    a.sb.append("http://a.invalid/x/");
                    start = a.sb.length();
                    a.sb.append(n);
                    break;
            }
            a.bestand.add(new BestandG(BESTAND_G[v], start, start + n.length()));
        }
        a.bausteine.add("bestand-" + BESTAND_G[v]);
    }

    /**
     * Mischbaustein: aus Teil 1f (ein Baustein samt Pruefung der Teil-1f-Regel, dass eine enge KVNR-Form nur in den
     * Bloecken von 1f steht) oder aus Teil 1b bis 1e und der Umgebung, mit dem eigenen {@code Random}. Der Bereich
     * steht in {@code misch}: Treffer der Referenz sind dort erlaubt und nicht erwartet.
     */
    private static void mischbausteinG(Random m, AufbauG a) {
        int start = a.sb.length();
        if (m.nextInt(100) < 50) {
            AufbauF f = new AufbauF();
            int[] marke = f.marke();
            boolean ok = false;
            for (int v = 0; v < MAX_VERSUCHE_F && !ok; v++) {
                f.zurueck(marke);
                kvBausteinF(m, new Random(m.nextLong()), f);
                ok = aufbauOkF(f);
            }
            if (ok) {
                a.sb.append(f.sb);
            }
            a.bausteine.add("misch-1f");
        } else {
            mischbausteinF(m, a.sb);
            a.bausteine.add("misch-1b-bis-1e");
        }
        if (a.sb.length() > start) {
            a.misch.add(new KvnrBsnrHilfen.Bereich(start, a.sb.length()));
        }
    }

    // ------------------------------------------------------------------ Teil 1g: Lauf und Pruefung

    private static Lauf fuehreKvGAus() {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_G);
        TextScanner scanner = TextScanner.standard();
        Random auswahl = new Random(SEED_AUSWAHL_KV_G);
        List<FallG> faelle = erzeugeKvG(SEED_TEXTE_KV_G);
        for (int it = 0; it < faelle.size(); it++) {
            FallG fall = faelle.get(it);
            String text = fall.text();
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                lauf.verletzt("waechter-gueltiges-neunerfenster-ohne-00", it);
                continue;
            }
            if (KvnrSynthetischWaechter.pruefe(text).verstoesse() != 0) {
                lauf.verletzt("waechter-kvnr-synthetisch", it);
                continue;
            }
            if (text.contains("[[")) {
                lauf.verletzt("generator-doppelklammer", it);
                continue;
            }
            try {
                pruefeKvFText(lauf, scanner, auswahl, it, text);
                pruefeKvG(lauf, scanner, it, fall);
            } catch (RuntimeException e) {
                lauf.verletzt("ausnahme-im-lauf", it);
            }
        }
        return lauf;
    }

    /**
     * Die Kandidaten mit der Quelle der Regel 1b sind genau die Treffer der Referenz, die kein Kandidat aus Teil 1a
     * ueberlappt (Kap. 4.3: ein Kandidat, der einen Fund mit Kontextwort ueberlappt, entfaellt). Der Vergleich ist die
     * Gleichheit der Bereichsmengen in beiden Richtungen: kein Kandidat ohne Referenz, keine Referenz ohne Kandidat.
     */
    static boolean kandidaten1bGleichReferenzG(List<Finding> kandidaten, List<KvnrBsnrHilfen.Bereich> referenz) {
        Set<String> erwartet = new TreeSet<>();
        for (KvnrBsnrHilfen.Bereich b : referenz) {
            boolean ueberlappt = false;
            for (Finding c : kandidaten) {
                ueberlappt |= !QUELLE_1B_G.equals(c.source()) && c.start() < b.ende() && b.start() < c.end();
            }
            if (!ueberlappt) {
                erwartet.add(b.start() + ":" + b.ende());
            }
        }
        Set<String> ist = new TreeSet<>();
        for (Finding c : kandidaten) {
            if (QUELLE_1B_G.equals(c.source())) {
                ist.add(c.start() + ":" + c.end());
            }
        }
        return erwartet.equals(ist);
    }

    private static Finding kandidatAufG(List<Finding> kandidaten, int start, int ende) {
        for (Finding c : kandidaten) {
            if (c.start() == start && c.end() == ende && FindingType.KVNR.equals(c.type())) {
                return c;
            }
        }
        return null;
    }

    private static boolean schwachOkG(Finding c) {
        return c.confidence() >= KONF_1B_G - TOLERANZ_G
                && (QUELLE_1B_G.equals(c.source()) || KvnrRecognizer.SOURCE.equals(c.source()));
    }

    /**
     * Klassen und strikte Pruefungen je Text. Je Text einmal ({@code kvnr-*}) bzw. je Block ({@code bloecke-*},
     * {@code vorn-*}, {@code hinten-*}): (1) Die Referenz der Regel 1b trifft genau die Bloecke mit Referenz-Erwartung
     * und liegt sonst nur in Bestand-Bausteinen und Mischbereichen ({@code referenz-abweichung}, strikt 0); jeder
     * Treffer der Referenz ist im Scan abgedeckt ({@code referenz-treffer-ungedeckt}, strikt 0: eine gueltige,
     * geschlossene, grossgeschriebene Form mit Wortgrenzen und ohne Negativkontext bleibt nie im Klartext, ausser der
     * Bestand deckt sie). (2) Jeder nicht markierte Block hat auf Erkennerebene genau einen Kandidaten auf genau
     * seinem Bereich ({@code kandidat-fehlt}, {@code kandidat-nicht-eindeutig}) mit der erwarteten Konfidenz und
     * Quelle ({@code kandidat-abweichend}); schwache Erwartung: mindestens 0,60 aus Teil 1a oder 1b. (3) Je Block die
     * Zaehler: {@code block-ohne-scan-abdeckung} fuer nicht markierte Bloecke ohne Scan-Fund, der den Bereich ganz
     * deckt (strikt 0); markierte Bloecke zaehlen getrennt, auch je Grenzklasse. (4) Gegenproben: allein gescannt
     * liefern weder Erkenner noch Referenz einen Fund; im Text vermerkt, ob ein Scan-Fund sie beruehrt (nur Zaehler,
     * die Gegenprobe ist Absicht). (5) Bestand-Bausteine: Die Nummer liegt in einem Fund von E-Mail, Pfad oder URL, und
     * kein KVNR-Fund beruehrt sie; Restfunde genau wie erwartet. Danach pseudonymisiert {@code pruefeErsetzenF} alle
     * Scan-Funde (vollstaendig ersetzt, Platzhalter konsistent, kein Rest, Roundtrip).
     */
    static void pruefeKvG(Lauf lauf, TextScanner scanner, int it, FallG fall) {
        pruefeKvG(lauf, scanner, STANDARD_ERKENNER.get(ERKENNER_KVNR), it, fall);
    }

    /** Wie oben mit einem anderen KVNR-Erkenner (Selbsttests mit kuenstlichen Erkennern). */
    static void pruefeKvG(Lauf lauf, TextScanner scanner, Recognizer kvnr, int it, FallG fall) {
        String text = fall.text();
        List<Finding> kandidaten = kvnr.detect(text);
        List<Finding> scan = scanner.scan(text);
        for (int i = 0; i < fall.verworfene(); i++) {
            lauf.klasse("baustein-verworfen");
        }
        for (int i = 0; i < fall.aufgegebene(); i++) {
            lauf.klasse("baustein-aufgegeben");
        }
        for (String baustein : fall.bausteine()) {
            lauf.klasse("baustein-" + baustein);
        }
        if (referenzAbweichungenG(text, fall.bloecke(), fall.bestand(), fall.misch()) != 0) {
            lauf.verletzt("referenz-abweichung", it);
        }
        List<KvnrBsnrHilfen.Bereich> referenz = KvnrOhneKontextHilfen.kvnrOhneKontextReferenz(text);
        if (!kandidaten1bGleichReferenzG(kandidaten, referenz)) {
            lauf.verletzt("kandidaten-1b-ungleich-referenz", it);
        }
        for (KvnrBsnrHilfen.Bereich b : referenz) {
            lauf.klasse("referenz-treffer");
            if (!imScanAbgedeckt(scan, b.start(), b.ende())) {
                lauf.verletzt("referenz-treffer-ungedeckt", it);
            }
        }
        for (BlockG block : fall.bloecke()) {
            pruefeBlockG(lauf, it, block, kandidaten, scan);
        }
        for (GegenprobeG g : fall.gegenproben()) {
            String probe = text.substring(g.start(), g.ende());
            lauf.klasse("gegenprobe-" + g.name());
            if (!kvnr.detect(probe).isEmpty()) {
                lauf.verletzt("gegenprobe-mit-kvnr-fund", it);
            }
            if (!KvnrOhneKontextHilfen.kvnrOhneKontextReferenz(probe).isEmpty()) {
                lauf.verletzt("gegenprobe-referenz-trifft", it);
            }
            boolean beruehrt = false;
            for (Finding f : scan) {
                beruehrt |= f.start() < g.ende() && g.start() < f.end();
            }
            lauf.klasse((beruehrt ? "gegenprobe-im-text-mit-fund-" : "gegenprobe-im-text-ohne-fund-") + g.name());
        }
        for (BestandG s : fall.bestand()) {
            boolean vomBestand = false;
            boolean kvnrFund = false;
            for (Finding f : scan) {
                vomBestand |= bestandstyp(f) && f.type() != FindingType.IP && f.start() <= s.start()
                        && f.end() >= s.ende();
                kvnrFund |= FindingType.KVNR.equals(f.type()) && f.start() < s.ende() && s.start() < f.end();
            }
            lauf.klasse("bestand-" + s.name() + (vomBestand && !kvnrFund ? "-ok" : "-abweichend"));
            if (!vomBestand) {
                lauf.verletzt("bestand-nummer-nicht-vom-bestand-gedeckt", it);
            }
            if (kvnrFund) {
                lauf.verletzt("bestand-kvnr-fund-in-bestand", it);
            }
        }
        for (RestG rest : fall.reste()) {
            pruefeRestG(lauf, it, rest, scan);
        }
        pruefeErsetzenF(lauf, it, text, scan);
    }

    /**
     * Restfund am Bestand (Nachtrag zu T-11, Punkt 2): Die Erwartung gilt, wenn der Fund der E-Mail genau hinter dem
     * Leerzeichen nach den vorderen Ziffern beginnt, wie konstruiert. Beginnt er davor (ein LANR- oder IK-Kandidat aus
     * einem Kontext weiter vorn verschmilzt mit der E-Mail und deckt die vorderen Ziffern, G-K14), ist der Fall
     * ueberlagert. Ein ueberlagerter Fall wird gezaehlt und inhaltlich geprueft ({@link #ueberlagertGedecktG}): Alle
     * neun Ziffern der Nummer muessen von einem einzigen Fund von E-Mail, IP, URL oder Pfad gedeckt sein (hoechstens
     * der Buchstabe bleibt frei); fehlt der E-Mail-Fund ganz oder bleibt eine Ziffer frei, ist das ein Verstoss
     * ({@code rest-ueberlagert-ziffern-ungedeckt}). Der deckende zusammengelegte Fund hat die Stufe "fraglich"
     * (0,50, Nachtrag 2 zu T-11); das ist beim Anzeigen im UI zu beachten.
     */
    private static void pruefeRestG(Lauf lauf, int it, RestG rest, List<Finding> scan) {
        boolean wieKonstruiert = false;
        Finding gefunden = null;
        boolean beruehrt = false;
        for (Finding f : scan) {
            wieKonstruiert |= bestandstyp(f) && f.start() == rest.ende() + 1;
            if (FindingType.KVNR.equals(f.type()) && f.start() < rest.ende() && rest.start() < f.end()) {
                beruehrt = true;
                if (f.start() == rest.start() && f.end() == rest.ende()) {
                    gefunden = f;
                }
            }
        }
        String art = rest.konfidenz() < 0 ? "unter-drei" : "drei";
        if (!wieKonstruiert) {
            lauf.klasse("rest-" + art + "-ueberlagert");
            boolean gedeckt = ueberlagertGedecktG(rest, scan);
            lauf.klasse(gedeckt ? "rest-ueberlagert-gedeckt" : "rest-ueberlagert-ungedeckt");
            if (!gedeckt) {
                lauf.verletzt("rest-ueberlagert-ziffern-ungedeckt", it);
            }
            return;
        }
        if (rest.konfidenz() < 0) {
            lauf.klasse(beruehrt ? "rest-unter-drei-mit-fund" : "rest-unter-drei-ohne-fund");
            if (beruehrt) {
                lauf.verletzt("rest-fund-unerwartet", it);
            }
            return;
        }
        boolean passt = gefunden != null && KvnrRecognizer.SOURCE_REST.equals(gefunden.source())
                && Math.abs(gefunden.confidence() - rest.konfidenz()) < TOLERANZ_G;
        lauf.klasse(passt ? "rest-drei-ziffern-ok" : "rest-drei-ziffern-abweichend");
        if (!passt) {
            lauf.verletzt("rest-fund-abweichend", it);
        }
    }

    /**
     * Ueberlagerter Restfall: Ein einziger Bestandsfund (E-Mail, IP, URL, Pfad) beginnt spaetestens an der ersten
     * Ziffer der Nummer und endet nicht vor ihrer letzten Ziffer. Die Ziffern liegen von {@code rest.start() + 1} bis
     * zum Ende der hinteren Gruppe hinter dem Leerzeichen bei {@code rest.ende()}; nur der Buchstabe davor darf frei
     * bleiben.
     */
    private static boolean ueberlagertGedecktG(RestG rest, List<Finding> scan) {
        int vorne = rest.ende() - rest.start() - 1;
        int ersteZiffer = rest.start() + 1;
        int hinterLetzterZiffer = rest.ende() + 1 + (ZIFFERN_NUMMER_G - vorne);
        for (Finding f : scan) {
            if (bestandstyp(f) && f.start() <= ersteZiffer && f.end() >= hinterLetzterZiffer) {
                return true;
            }
        }
        return false;
    }

    private static void pruefeBlockG(Lauf lauf, int it, BlockG block, List<Finding> kandidaten,
            List<Finding> scan) {
        String art = BLOCK_NAMEN_G[block.blockart()];
        lauf.klasse("bloecke-" + art);
        if (!block.vorn().isEmpty()) {
            lauf.klasse("vorn-" + block.vorn());
        }
        if (!block.hinten().isEmpty()) {
            lauf.klasse("hinten-" + block.hinten());
        }
        Finding c = kandidatAufG(kandidaten, block.start(), block.ende());
        boolean abgedeckt = imScanAbgedeckt(scan, block.start(), block.ende());
        if (!block.grenze().isEmpty()) {
            lauf.klasse("block-markiert");
            lauf.klasse("block-markiert-" + block.grenze());
            lauf.klasse(abgedeckt ? "markiert-mit-abdeckung" : "markiert-ohne-abdeckung");
            lauf.klasse((abgedeckt ? "markiert-mit-abdeckung-" : "markiert-ohne-abdeckung-") + block.grenze());
            lauf.klasse(c != null ? "markiert-mit-kandidat" : "markiert-ohne-kandidat");
            return;
        }
        if (block.konfidenz() == KONF_KEINE_G) {
            lauf.verletzt("grenzblock-nicht-markiert", it);
            return;
        }
        int ueberlappend = 0;
        for (Finding k : kandidaten) {
            ueberlappend += k.start() < block.ende() && block.start() < k.end() ? 1 : 0;
        }
        if (c == null) {
            lauf.verletzt("kandidat-fehlt", it);
        } else {
            if (ueberlappend != 1) {
                lauf.verletzt("kandidat-nicht-eindeutig", it);
            }
            boolean passt = block.konfidenz() == KONF_SCHWACH_G ? schwachOkG(c)
                    : Math.abs(c.confidence() - block.konfidenz()) < TOLERANZ_G && block.quelle().equals(c.source());
            if (!passt) {
                lauf.verletzt("kandidat-abweichend", it);
            }
            boolean eins = QUELLE_1B_G.equals(c.source());
            lauf.klasse(eins ? "kvnr-fund-1b" : "kvnr-fund-1a");
            lauf.klasse((eins ? "kvnr-fund-1b-" : "kvnr-fund-1a-") + art);
            lauf.klasse(block.konfidenz() == KONF_SCHWACH_G ? "block-erwartung-schwach" : "block-erwartung-strikt");
        }
        if (!abgedeckt) {
            lauf.klasse("block-ohne-scan-abdeckung");
            lauf.klasse("block-ohne-scan-abdeckung-" + art);
        }
        if (block.blockart() == BLOCK_G_K1) {
            boolean ersetzt = false;
            for (Finding f : scan) {
                ersetzt |= FindingType.KVNR.equals(f.type()) && f.start() <= block.start() && f.end() >= block.ende();
            }
            lauf.klasse(ersetzt ? "k1-scan-kvnr" : "k1-scan-ohne-kvnr");
        }
    }

    // ------------------------------------------------------------------ Teil 1g: Tests (kvnr-bsnr-erkennung T-11)

    /** Hashwert der Teil-1g-Textfolge (kvnr-bsnr-erkennung T-11, nach dem Anlegen des Generators gemessen). */
    static final int HASH_TEIL_1G = 0xffbba9d5;

    private static List<String> texteTeil1g() {
        List<String> texte = new ArrayList<>();
        for (FallG f : erzeugeKvG(SEED_TEXTE_KV_G)) {
            texte.add(f.text());
        }
        return texte;
    }

    @Test
    void t11_teil1gTextfolgeIstFestgehalten() {
        List<String> texte = texteTeil1g();
        assertEquals(HASH_TEIL_1G, texte.hashCode(), "Hashwert der Teil-1g-Textfolge, Startwert " + SEED_TEXTE_KV_G
                + ", gemessen 0x" + Integer.toHexString(texte.hashCode()));
    }

    @Test
    void t11_teil1gZweiLaeufeMitDemselbenStartwertErzeugenDieselbeTextfolge() {
        List<String> a = texteTeil1g();
        assertEquals(ANZAHL_TEXTE, a.size());
        assertTextfolgeGleich(a, texteTeil1g(), SEED_TEXTE_KV_G, "Teil 1g: Textfolge");
        assertTrue(erzeugeKvG(SEED_TEXTE_KV_G).equals(erzeugeKvG(SEED_TEXTE_KV_G)),
                "Teil 1g: Bloecke, Gegenproben, Bestand-Bausteine und Mischbereiche sind deterministisch");
        assertNotEquals(HASH_TEIL_1F, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1f");
        assertNotEquals(HASH_TEIL_1E, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1e");
        assertNotEquals(HASH_TEIL_1D, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1d");
        assertNotEquals(HASH_TEIL_1C, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1c");
        assertNotEquals(HASH_TEIL_1B, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1b");
        assertNotEquals(HASH_TEIL_1, a.hashCode(), "Teil 1g unterscheidet sich von Teil 1");
    }

    private static void keineG(String eigenschaft) {
        assertEquals(0, LAUF_KV_G.anzahl(eigenschaft), "Teil 1g: " + LAUF_KV_G.meldung(eigenschaft));
    }

    @Test
    void t11_teil1g_waechterUndGeneratorSindSauber() {
        keineG("waechter-gueltiges-neunerfenster-ohne-00");
        keineG("waechter-kvnr-synthetisch");
        keineG("generator-doppelklammer");
    }

    /** AK-17: Roundtrip zeichengleich ohne Meldung, auch wenn alle Funde ersetzt werden. */
    @Test
    void t11_teil1g_roundtripIstZeichengleichOhneMeldung() {
        keineG("ak45-roundtrip-ungleich");
        keineG("ak45-pseudonymisierung-wirft");
        keineG("ak45-meldung");
        keineG("ak17-roundtrip-alle-funde-ungleich");
        keineG("ak17-roundtrip-meldung");
        keineG("ausnahme-im-lauf");
    }

    @Test
    void t11_teil1g_scanInvarianteUndListenInvariante() {
        keineG("ak1-scan-invariante");
        keineG("ak1-liste-invariante");
    }

    /**
     * Schutzgarantie ueber {@code STANDARD_ERKENNER}: Jede Position, die ein Kandidat eines Erkenners abdeckt, ist von
     * einem Scan-Fund abgedeckt, ausser in Kandidaten des nachrangigen KVNR-Erkenners, die einen Bestandsfund schneiden
     * (zugeschnitten, mit weniger als drei Ziffern je Luecke; wie in Teil 1f). Texte mit zugeschnittenem Kandidaten:
     * exakt 155 (die Bestand-Bausteine mit Nummer in E-Mail, URL und Pfad und die Restfunde).
     */
    @Test
    void t11_teil1g_schutzgarantie_jederKandidatDerStandardErkennerIstImScanAbgedeckt() {
        keineG("ak7a-kandidat-nicht-im-scan");
        assertEquals(155, LAUF_KV_G.klasse_("kandidat-position-am-bestand-zugeschnitten"),
                "Texte mit zugeschnittenem Kandidaten, Startwert " + SEED_TEXTE_KV_G);
        keineG("ak7e-fremdtext-im-scan");
        keineG("ak7e-bereiche-nicht-verschmolzen");
        assertEquals(155, LAUF_KV_G.klasse_("bereiche-wegen-zuschnitt"),
                "Texte, deren Bereiche wegen des Zuschneidens nicht verschmolzen sind, Startwert " + SEED_TEXTE_KV_G);
    }

    /**
     * AK-17, AK-21: keine Ziffernkette und keine Form (Buchstabe und Ziffern) eines Fundes im Ausgabetext, derselbe
     * Wert hat denselben Platzhalter (auch einmal mit und einmal ohne Kontextwort), verschiedene Werte (auch mit und
     * ohne Trenner) haben verschiedene.
     */
    @Test
    void t11_teil1g_ausgabetextOhneRestEinesFundesUndPlatzhalterSindKonsistent() {
        keineG("ak23-fund-nicht-vollstaendig-ersetzt");
        keineG("ak17-form-eines-fundes-im-ausgabetext");
        keineG("ak17-platzhalter-nicht-lesbar");
        keineG("ak17-konsistenz");
    }

    /**
     * Die Erkennung ohne Kontextwort ueber die ganze Kette, alles strikt 0: Die Kandidaten mit der Quelle der Regel 1b
     * sind genau die Treffer der unabhaengigen Referenz (ohne die von einem Fund aus Teil 1a ueberlappten); die
     * Referenz trifft genau die erwarteten Bloecke; jeder nicht markierte Block hat genau einen Kandidaten auf seinem
     * Bereich mit der erwarteten Konfidenz und Quelle; jeder Treffer der Referenz ist im Scan abgedeckt (eine gueltige,
     * geschlossene, grossgeschriebene Form mit Wortgrenzen und ohne Negativkontext bleibt nie im Klartext, wenn nicht
     * der Bestand sie deckt). Ein Wert ungleich 0 ist ein Befund, keine Erwartung zum Anpassen.
     */
    @Test
    void t11_teil1g_referenzUndKandidaten_stimmenMitDerKonstruktionUeberein() {
        keineG("referenz-abweichung");
        keineG("kandidaten-1b-ungleich-referenz");
        keineG("referenz-treffer-ungedeckt");
        keineG("kandidat-fehlt");
        keineG("kandidat-nicht-eindeutig");
        keineG("kandidat-abweichend");
        keineG("grenzblock-nicht-markiert");
    }

    /**
     * Gegenproben (AK-12 bis AK-14), Bestand-Bausteine (AK-15, Nachtrag zu T-11 Punkt 2) und Restfunde, alles strikt 0.
     */
    @Test
    void t11_teil1g_gegenprobenBestandUndRestfunde_stimmenMitDerKonstruktionUeberein() {
        keineG("gegenprobe-mit-kvnr-fund");
        keineG("gegenprobe-referenz-trifft");
        keineG("bestand-nummer-nicht-vom-bestand-gedeckt");
        keineG("bestand-kvnr-fund-in-bestand");
        keineG("rest-fund-abweichend");
        keineG("rest-fund-unerwartet");
        keineG("rest-ueberlagert-ziffern-ungedeckt");
    }

    /**
     * AK-17, AK-21: Der Zaehler "Block ohne Scan-Abdeckung" ist strikt 0 fuer jeden nicht markierten Block von Teil 1g,
     * insgesamt und je Blockart. Ein Wert ungleich 0 ist ein Befund, keine Erwartung zum Anpassen.
     */
    @Test
    void t11_teil1g_blockOhneScanAbdeckung_istStriktNull_insgesamtUndNachBlockart() {
        assertEquals(0, LAUF_KV_G.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (strikt 0), Startwert " + SEED_TEXTE_KV_G);
        for (String art : BLOCK_NAMEN_G) {
            assertEquals(0, LAUF_KV_G.klasse_("block-ohne-scan-abdeckung-" + art), "Blockart " + art);
        }
    }

    /**
     * Die markierten Bloecke (dokumentierte Grenzen, Markierung aus dem fertigen Text, {@link #grenzklasseG}) sind
     * einzeln gezaehlt, damit nichts verdeckt wird. Messwerte bei festem Seed: 233 markierte Bloecke (6,5 % aller
     * 3 586 Bloecke). Kontextwort direkt am Buchstaben (G-K9, Nachtrag 3 zu T-7 (b); auch Teil 1b schuetzt hier nicht):
     * 89, alle ohne Abdeckung. Nicht-ASCII-Ziffer als Nachbar (Lesart "sperrend", Nachtrag 3 zu T-9 und Nachtrag zu
     * T-10 Punkt 4, Javadoc von {@code KvnrOhneKontext}): 144, davon 140 ohne und 4 mit Abdeckung; die vier haben ein
     * KVNR-Kontextwort davor im Text, dann findet Teil 1a die Form. Kein markierter Block ohne Abdeckung hat einen
     * Kandidaten. Eine Aenderung der Zahlen ist ein Befund.
     */
    @Test
    void t11_teil1g_markierteBloecke_sindEinzelnGezaehlt() {
        assertEquals(233, LAUF_KV_G.klasse_("block-markiert"), "markierte Bloecke");
        assertEquals(229, LAUF_KV_G.klasse_("markiert-ohne-abdeckung"), "markierte Bloecke ohne Abdeckung");
        assertEquals(4, LAUF_KV_G.klasse_("markiert-mit-abdeckung"), "markierte Bloecke mit Abdeckung");
        assertEquals(89, LAUF_KV_G.klasse_("block-markiert-" + GRENZE_AM_BUCHSTABEN), "Kontextwort am Buchstaben");
        assertEquals(89, LAUF_KV_G.klasse_("markiert-ohne-abdeckung-" + GRENZE_AM_BUCHSTABEN),
                "Kontextwort am Buchstaben ohne Abdeckung");
        assertEquals(0, LAUF_KV_G.klasse_("markiert-mit-abdeckung-" + GRENZE_AM_BUCHSTABEN),
                "Kontextwort am Buchstaben mit Abdeckung");
        assertEquals(144, LAUF_KV_G.klasse_("block-markiert-" + GRENZE_NICHT_ASCII_ZIFFER), "Nicht-ASCII-Ziffer");
        assertEquals(140, LAUF_KV_G.klasse_("markiert-ohne-abdeckung-" + GRENZE_NICHT_ASCII_ZIFFER),
                "Nicht-ASCII-Ziffer ohne Abdeckung");
        assertEquals(4, LAUF_KV_G.klasse_("markiert-mit-abdeckung-" + GRENZE_NICHT_ASCII_ZIFFER),
                "Nicht-ASCII-Ziffer mit Abdeckung");
        assertEquals(4, LAUF_KV_G.klasse_("markiert-mit-kandidat"), "markierte Bloecke mit Kandidat");
        assertEquals(229, LAUF_KV_G.klasse_("markiert-ohne-kandidat"), "markierte Bloecke ohne Kandidat");
        assertEquals(LAUF_KV_G.klasse_("block-markiert"), LAUF_KV_G.klasse_("markiert-ohne-abdeckung")
                + LAUF_KV_G.klasse_("markiert-mit-abdeckung"), "jeder markierte Block ist einmal eingeordnet");
    }

    /**
     * Rest am Bestand (Nachtrag zu T-11, Punkt 2): Mit genau drei Ziffern ausserhalb der E-Mail entsteht ein Restfund
     * mit Quelle {@code kvnr-rest} und Konfidenz 0,80, obwohl der Kandidat 0,95 hat (Mutanten der Schwelle 3 zu 4 und
     * des Deckels 0,80 zu 0,95 fallen auf); mit zwei Ziffern keiner (G-K14). Ueberlagerte Faelle (ein LANR- oder
     * IK-Kandidat aus einem Kontext weiter vorn verschmilzt mit der E-Mail und deckt die vorderen Ziffern): exakt
     * 5 bzw. 11; in allen 16 sind die Ziffern der Nummer ganz von einem Bestandsfund gedeckt (nur der Buchstabe darf
     * frei bleiben, der deckende zusammengelegte Fund hat die Stufe "fraglich", Nachtrag 2 zu T-11). Messwerte: 75 mit
     * drei Ziffern wie konstruiert, 61 mit zwei Ziffern wie konstruiert.
     */
    @Test
    void t11_teil1g_restAmBestand_dreiZiffernRestfundUndZweiZiffernKeiner_exakteZahlen() {
        assertEquals(75, LAUF_KV_G.klasse_("rest-drei-ziffern-ok"), "Restfund mit drei Ziffern");
        assertEquals(0, LAUF_KV_G.klasse_("rest-drei-ziffern-abweichend"), "Restfund mit drei Ziffern abweichend");
        assertEquals(61, LAUF_KV_G.klasse_("rest-unter-drei-ohne-fund"), "kein Restfund mit zwei Ziffern");
        assertEquals(0, LAUF_KV_G.klasse_("rest-unter-drei-mit-fund"), "Restfund mit zwei Ziffern");
        assertEquals(5, LAUF_KV_G.klasse_("rest-drei-ueberlagert"), "ueberlagert, drei Ziffern");
        assertEquals(11, LAUF_KV_G.klasse_("rest-unter-drei-ueberlagert"), "ueberlagert, zwei Ziffern");
        assertEquals(16, LAUF_KV_G.klasse_("rest-ueberlagert-gedeckt"), "ueberlagert, von Bestandsfund gedeckt");
        assertEquals(0, LAUF_KV_G.klasse_("rest-ueberlagert-ungedeckt"), "ueberlagert, Ziffern nicht gedeckt");
    }

    /**
     * K-1 (LANR- oder IK-Kontextwort direkt vor der KVNR-Form): Auf Erkennerebene ein Fund 0,60; im Scan umschliesst er
     * den LANR- oder IK-Fund auf den Ziffern, die Form ist ganz von einem KVNR-Fund gedeckt. Messwert: alle 370
     * Bloecke; die Zahl der Bloecke ohne KVNR-Fund im Scan ist exakt 0 (Plan Kap. 2, Risiko R-2: die Stufe sinkt, die
     * Position bleibt geschuetzt und wird um den Buchstaben erweitert).
     */
    @Test
    void t11_teil1g_k1_dieFormIstImScanVonEinemKvnrFundGedeckt() {
        assertEquals(0, LAUF_KV_G.klasse_("k1-scan-ohne-kvnr"), "K-1-Bloecke ohne KVNR-Fund im Scan");
        assertEquals(LAUF_KV_G.klasse_("bloecke-k1"), LAUF_KV_G.klasse_("k1-scan-kvnr"), "K-1-Bloecke mit KVNR-Fund");
        mindestens(LAUF_KV_G, "k1-scan-kvnr", 259);
    }

    /**
     * Mindestzahlen der Klassen von Teil 1g (je Text hoechstens einmal gezaehlt, ausser den Bloecken, Untergrenzen bei
     * hoechstens 70 % des Messwerts). Bloecke je Blockart (Messwert in Klammern): frei 939, Anfang 127, Ende 121,
     * Kontextwort weit weg 267, Leerzeile 199, Negativliste im Umfeld 353, K-1 370, Wiederholung 460, Dateiname 198,
     * Nachbar IP 184, Nicht-ASCII-Ziffer 144, Kontextwort am Buchstaben 89, langer Pfad 135. Kandidaten mit Quelle der
     * Regel 1b je Blockart: frei 902, Anfang 127, Ende 115, weit weg 267, Leerzeile 199, Negativliste 234, K-1 359,
     * Wiederholung 286, Dateiname 191, Nachbar IP 173, langer Pfad 135; insgesamt 2 988, dazu 365 Kandidaten aus
     * Teil 1a in Bloecken (mit Kontextwort davor oder erwartet). Erwartung strikt 2 554, schwach 799.
     */
    @Test
    void t11_teil1g_fuzzPruftNichtInsLeere_blockartenUndKandidaten() {
        int[] minBloecke = {657, 88, 84, 186, 139, 247, 259, 322, 138, 128, 100, 62, 94};
        for (int i = 0; i < BLOCK_NAMEN_G.length; i++) {
            mindestens(LAUF_KV_G, "bloecke-" + BLOCK_NAMEN_G[i], minBloecke[i]);
        }
        String[] arten1b = {"frei", "anfang", "ende", "kw-weit", "leerzeile", "negativ", "k1", "wiederholung",
            "dateiname", "nachbar-ip", "langer-pfad"};
        int[] min1b = {631, 88, 80, 186, 139, 163, 251, 200, 133, 121, 94};
        for (int i = 0; i < arten1b.length; i++) {
            mindestens(LAUF_KV_G, "kvnr-fund-1b-" + arten1b[i], min1b[i]);
        }
        mindestens(LAUF_KV_G, "kvnr-fund-1b", 2091);
        mindestens(LAUF_KV_G, "kvnr-fund-1a", 255);
        mindestens(LAUF_KV_G, "block-erwartung-strikt", 1787);
        mindestens(LAUF_KV_G, "block-erwartung-schwach", 559);
        mindestens(LAUF_KV_G, "referenz-treffer", 2538);
    }

    /**
     * Jedes zulaessige Grenzzeichen aus AK-13 kommt vor und hinter der Nummer oft genug vor: Messwerte je Zeichen
     * mindestens 54 davor (kleinster Wert, Untergrenze 37) und 106 dahinter (Untergrenze 74); dazu Textanfang 127 und
     * Textende 121 (Untergrenze 88 und 84), Dateiendung 198 (138).
     */
    @Test
    void t11_teil1g_fuzzPruftNichtInsLeere_jedesGrenzzeichenKommtVorUndDahinter() {
        for (String[] vorn : VORN_G) {
            mindestens(LAUF_KV_G, "vorn-" + vorn[1], 37);
        }
        for (String[] hinten : HINTEN_G) {
            mindestens(LAUF_KV_G, "hinten-" + hinten[1], 74);
        }
        mindestens(LAUF_KV_G, "vorn-textanfang", 88);
        mindestens(LAUF_KV_G, "hinten-textende", 84);
        mindestens(LAUF_KV_G, "hinten-punkt-endung", 138);
    }

    /**
     * Mindestzahlen der Gegenproben (kleinster Wert 62, je 43; im Text ohne Scan-Fund an der Gegenprobe kleinster Wert
     * 56, je 39), der Bestand-Bausteine (kleinster Wert 72, je 50; vom Bestand gedeckt kleinster Wert 82, je 57), der
     * Bausteine fuer K-1 (davor 294, nachgestellt 76), der Wiederholungen (kleinster Wert 42, je 29), der Mischung
     * (Teil 1f 944, Teil 1b bis 1e 827) und der Klassen aus den allgemeinen Pruefungen (Istwert, Mindestzahl, jeweils
     * rund 70 Prozent): gleicher Wert gleicher Platzhalter 166 (116), mit und ohne Trenner verschiedene Platzhalter
     * 69 (48), Scan-Fund 998 (698), zusammengelegter Fund 32 (22), KV-Fund LANR 304 (212), KV-Fund IK 354 (247),
     * KV-Fund fraglich 338 (236), manueller Fund 913 (639), echte Teilmenge der Scan-Funde 874 (611).
     */
    @Test
    void t11_teil1g_fuzzPruftNichtInsLeere_gegenprobenBausteineUndAllgemeineKlassen() {
        for (String gegenprobe : GEGENPROBEN_G) {
            mindestens(LAUF_KV_G, "gegenprobe-" + gegenprobe, 43);
            mindestens(LAUF_KV_G, "gegenprobe-im-text-ohne-fund-" + gegenprobe, 39);
        }
        for (String baustein : BESTAND_G) {
            mindestens(LAUF_KV_G, "baustein-bestand-" + baustein, 50);
        }
        for (String baustein : List.of("email-lokal", "email-anfang", "pfad-unix", "pfad-windows", "url-query",
                "url-pfad")) {
            mindestens(LAUF_KV_G, "bestand-" + baustein + "-ok", 57);
        }
        mindestens(LAUF_KV_G, "baustein-k1-davor", 205);
        mindestens(LAUF_KV_G, "baustein-k1-nachgestellt", 53);
        for (int i = 0; i < 4; i++) {
            mindestens(LAUF_KV_G, "baustein-wiederholung-" + i, 29);
        }
        mindestens(LAUF_KV_G, "baustein-misch-1f", 660);
        mindestens(LAUF_KV_G, "baustein-misch-1b-bis-1e", 578);
        mindestens(LAUF_KV_G, "kvnr-gleicher-wert-gleicher-platzhalter", 116);
        mindestens(LAUF_KV_G, "kvnr-mit-und-ohne-trenner-verschiedene-platzhalter", 48);
        mindestens(LAUF_KV_G, "rest-drei-ziffern-ok", 52);
        mindestens(LAUF_KV_G, "rest-unter-drei-ohne-fund", 42);
        mindestens(LAUF_KV_G, "scan-fund", 698);
        mindestens(LAUF_KV_G, "zusammengelegter-fund", 22);
        mindestens(LAUF_KV_G, "kv-fund-lanr", 212);
        mindestens(LAUF_KV_G, "kv-fund-ik", 247);
        mindestens(LAUF_KV_G, "kv-fraglich", 236);
        mindestens(LAUF_KV_G, "manueller-fund", 639);
        mindestens(LAUF_KV_G, "echte-teilmenge-der-scan-funde", 611);
    }

    /**
     * Der Generator verwirft einen Baustein samt Trenner und zieht neu, wenn der Waechter der Testdatenregeln oder die
     * Referenz der Regel 1b gegen die Konstruktion anschlaegt. Gezaehlt wird das ohne Zufallszahlen; der Hash der
     * Textfolge bleibt dadurch gleich. Messwert bei festem Seed: 19 verworfene Versuche (bei 3 586 Bloecken), kein
     * Baustein, der nach allen {@value #MAX_VERSUCHE_G} Versuchen wegfaellt. Eine Aenderung der Zahl ist ein Befund
     * (der Generator hat sich geaendert), keine Erwartung zum Anpassen.
     */
    @Test
    void t11_teil1g_verworfeneBausteine_sindGezaehlt() {
        assertEquals(19, LAUF_KV_G.klasse_("baustein-verworfen"), "verworfene Versuche, Startwert " + SEED_TEXTE_KV_G);
        assertEquals(0, LAUF_KV_G.klasse_("baustein-aufgegeben"), "aufgegebene Bausteine, Startwert "
                + SEED_TEXTE_KV_G);
    }

    /**
     * Die Konstruktion haelt, was sie vermerkt: Je Block mit Referenz-Erwartung steht an der Nummer genau Buchstabe und
     * neun Ziffern, bei der erwarteten Quelle {@code kvnr-ohne-kontext} nach der unabhaengigen Pruefziffer-Referenz
     * ({@code KvnrBsnrHilfen.kvnrPruefziffer}) gueltig (Regel 9), nie ein Trenner; die Bloecke liegen geordnet und
     * ueberlappungsfrei im Text; markiert sind genau die Bloecke der Arten "Nicht-ASCII-Ziffer" und "Kontextwort am
     * Buchstaben", und ihre Zahl stimmt mit dem Lauf ueberein.
     */
    @Test
    void t11_teil1g_generatorBloeckeStimmenMitDemTextUeberein() {
        assertEquals(ANZAHL_BLOCKARTEN_G, BLOCK_NAMEN_G.length, "Namen der Blockarten");
        assertEquals(ANZAHL_BLOCKARTEN_G, GEWICHT_BLOCK_G.length, "Gewichte der Blockarten");
        int markiert = 0;
        int bloecke = 0;
        for (FallG fall : erzeugeKvG(SEED_TEXTE_KV_G)) {
            int vorigesEnde = 0;
            for (BlockG block : fall.bloecke()) {
                bloecke++;
                assertTrue(block.start() >= vorigesEnde && block.ende() <= fall.text().length(), "Lage des Blocks");
                vorigesEnde = block.ende();
                boolean grenzart = block.blockart() == BLOCK_G_NICHT_ASCII
                        || block.blockart() == BLOCK_G_AM_BUCHSTABEN;
                assertEquals(grenzart, !block.grenze().isEmpty(), "markiert sind genau die Grenzarten, Blockart "
                        + BLOCK_NAMEN_G[block.blockart()]);
                markiert += block.grenze().isEmpty() ? 0 : 1;
                String nummer = fall.text().substring(block.start(), block.ende());
                if (block.referenz()) {
                    assertEquals(10, nummer.length(), "zehn Zeichen");
                    assertTrue(nummer.charAt(0) >= 'A' && nummer.charAt(0) <= 'Z', "Grossbuchstabe vorn");
                    assertTrue(nummer.substring(1).chars().allMatch(c -> c >= '0' && c <= '9'), "neun Ziffern");
                    int soll = KvnrBsnrHilfen.kvnrPruefziffer(nummer.charAt(0), nummer.substring(1, 9));
                    assertTrue(soll == nummer.charAt(9) - '0', "gueltige Pruefziffer, Blockart "
                            + BLOCK_NAMEN_G[block.blockart()]);
                }
                assertTrue(block.konfidenz() <= KONF_DIREKT_G, "Konfidenz der Erwartung");
            }
        }
        assertEquals(LAUF_KV_G.klasse_("block-markiert"), markiert, "markierte Bloecke: Generator gegen Lauf");
        assertTrue(markiert * 10 < bloecke, "Markierung bleibt die Ausnahme: " + markiert + " von " + bloecke);
    }

    /** Der Scan ist deterministisch: zwei Laeufe liefern auf jedem Text von Teil 1g dieselbe Fundliste. */
    @Test
    void t11_teil1g_scanIstDeterministisch() {
        TextScanner scanner = TextScanner.standard();
        int abweichend = 0;
        for (FallG fall : erzeugeKvG(SEED_TEXTE_KV_G)) {
            abweichend += scanner.scan(fall.text()).equals(scanner.scan(fall.text())) ? 0 : 1;
        }
        assertEquals(0, abweichend, "Teil 1g: Texte mit abweichender Fundliste im zweiten Lauf");
    }

    // ------------------------------------------------------------------ Teil 1g: Selbsttests

    private static final String KONTEXT_G = "Notiz ";
    private static final int SELBSTTEST_TEXTE_G = 300;

    /** Ein Block mit der strikten Erwartung der Regel 1b ueber {@code von} bis {@code bis}, ohne Markierung. */
    private static BlockG blockG(int von, int bis) {
        return new BlockG(BLOCK_G_FREI, KONF_1B_G, QUELLE_1B_G, true, false, von, bis, "", "", "");
    }

    private static FallG fallG(String text, List<BlockG> bloecke, List<GegenprobeG> gegenproben,
            List<BestandG> bestand, List<RestG> reste, List<KvnrBsnrHilfen.Bereich> misch) {
        return new FallG(text, bloecke, gegenproben, List.of(), bestand, reste, misch, 0, 0);
    }

    private static Lauf pruefeG(FallG fall) {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_G);
        pruefeKvG(lauf, TextScanner.standard(), 0, fall);
        return lauf;
    }

    /**
     * Selbsttest der Zaehler und der Kandidatenpruefung: Ein abgedeckter Block mit der erwarteten Konfidenz und Quelle
     * besteht; ein Block, dessen Erwartung nicht zum Kandidaten passt, ein Block ohne Kandidat und ein Block ohne
     * Scan-Abdeckung werden je gemeldet; ein markierter Block ohne Abdeckung zaehlt getrennt.
     */
    @Test
    void t11_teil1g_selbsttest_zaehlerUndKandidatenpruefung() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = KONTEXT_G + nummer + " Ende";
        int start = KONTEXT_G.length();
        int ende = start + nummer.length();
        Lauf gut = pruefeG(fallG(text, List.of(blockG(start, ende)), List.of(), List.of(), List.of(), List.of()));
        assertTrue(gut.verletzungen.isEmpty(), "abgedeckter Block mit erwartetem Kandidaten: " + gut.meldung("x"));
        assertEquals(1, gut.klasse_("kvnr-fund-1b"), "Kandidat der Regel 1b");
        assertEquals(1, gut.klasse_("kvnr-fund-1b-frei"), "nach Blockart");
        assertEquals(1, gut.klasse_("block-erwartung-strikt"), "strikte Erwartung");
        assertEquals(0, gut.klasse_("block-ohne-scan-abdeckung"), "abgedeckter Block");
        BlockG falsch = new BlockG(BLOCK_G_FREI, KONF_DIREKT_G, KvnrRecognizer.SOURCE, true, false, start, ende, "",
                "", "");
        assertEquals(1, pruefeG(fallG(text, List.of(falsch), List.of(), List.of(), List.of(), List.of()))
                .anzahl("kandidat-abweichend"), "falsche Konfidenz und Quelle der Erwartung");
        BlockG schwach = blockG(start, ende).schwach();
        assertEquals(0, pruefeG(fallG(text, List.of(schwach), List.of(), List.of(), List.of(), List.of()))
                .verletzungen.size(), "schwache Erwartung: Teil 1b genuegt");
        BlockG leer = blockG(0, KONTEXT_G.length() - 1);
        Lauf ohne = pruefeG(fallG(text, List.of(leer), List.of(), List.of(), List.of(), List.of()));
        assertEquals(1, ohne.anzahl("kandidat-fehlt"), "Block ohne Kandidat");
        assertEquals(1, ohne.klasse_("block-ohne-scan-abdeckung"), "nicht markierter Block ohne Abdeckung");
        assertEquals(1, ohne.klasse_("block-ohne-scan-abdeckung-frei"), "nach Blockart");
        BlockG grenze = new BlockG(BLOCK_G_NICHT_ASCII, KONF_KEINE_G, QUELLE_1B_G, false, false, 0,
                KONTEXT_G.length() - 1, "", "", GRENZE_NICHT_ASCII_ZIFFER);
        Lauf markiert = pruefeG(fallG(text, List.of(grenze), List.of(), List.of(), List.of(), List.of()));
        assertEquals(1, markiert.klasse_("markiert-ohne-abdeckung"), "markierter Block ohne Abdeckung");
        assertEquals(1, markiert.klasse_("markiert-ohne-abdeckung-" + GRENZE_NICHT_ASCII_ZIFFER), "nach Grenzklasse");
        assertEquals(0, markiert.klasse_("block-ohne-scan-abdeckung"), "markierter Block zaehlt nicht dort");
        BlockG ungrenze = new BlockG(BLOCK_G_NICHT_ASCII, KONF_KEINE_G, QUELLE_1B_G, false, false, 0,
                KONTEXT_G.length() - 1, "", "", "");
        assertEquals(1, pruefeG(fallG(text, List.of(ungrenze), List.of(), List.of(), List.of(), List.of()))
                .anzahl("grenzblock-nicht-markiert"), "Grenzblock ohne Markierung wird gemeldet");
    }

    /**
     * Selbsttest der Referenzpruefung: Eine gueltige, geschlossene Form ohne Block, ohne Bestand-Baustein und ohne
     * Mischbereich ist eine Abweichung; mit Block, mit Bestand-Baustein oder in einem Mischbereich nicht; ein Block mit
     * Referenz-Erwartung ohne Treffer und ein Block ohne Erwartung mit Treffer sind Abweichungen.
     */
    @Test
    void t11_teil1g_selbsttest_referenzpruefungMeldetAbweichungen() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = KONTEXT_G + nummer + " Ende";
        int start = KONTEXT_G.length();
        int ende = start + nummer.length();
        BlockG block = blockG(start, ende);
        assertEquals(1, referenzAbweichungenG(text, List.of(), List.of(), List.of()), "Treffer ohne Block");
        assertEquals(0, referenzAbweichungenG(text, List.of(block), List.of(), List.of()), "Treffer im Block");
        assertEquals(0, referenzAbweichungenG(text, List.of(), List.of(new BestandG("x", start, ende)), List.of()),
                "Treffer im Bestand-Baustein");
        assertEquals(0, referenzAbweichungenG(text, List.of(), List.of(),
                List.of(new KvnrBsnrHilfen.Bereich(0, text.length()))), "Treffer im Mischbereich");
        assertEquals(1, referenzAbweichungenG(text, List.of(blockG(0, KONTEXT_G.length() - 1)), List.of(),
                List.of(new KvnrBsnrHilfen.Bereich(0, text.length()))), "Block mit Erwartung ohne Treffer");
        BlockG ohneErwartung = new BlockG(BLOCK_G_NICHT_ASCII, KONF_KEINE_G, QUELLE_1B_G, false, false, start, ende,
                "", "", "");
        assertEquals(1, referenzAbweichungenG(text, List.of(ohneErwartung), List.of(),
                List.of(new KvnrBsnrHilfen.Bereich(0, text.length()))), "Block ohne Erwartung mit Treffer");
        String klein = KONTEXT_G + nummer.toLowerCase(Locale.ROOT) + " Ende";
        assertEquals(0, referenzAbweichungenG(klein, List.of(), List.of(), List.of()), "Kleinbuchstabe: kein Treffer");
    }

    /** Selbsttest: Gleichheit der Kandidaten der Regel 1b mit der Referenz, in beiden Richtungen. */
    @Test
    void t11_teil1g_selbsttest_kandidaten1bGleichReferenz() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = KONTEXT_G + nummer + " Ende";
        int start = KONTEXT_G.length();
        int ende = start + nummer.length();
        List<KvnrBsnrHilfen.Bereich> referenz = KvnrOhneKontextHilfen.kvnrOhneKontextReferenz(text);
        Finding eins = new Finding(FindingType.KVNR, nummer, start, ende, KONF_1B_G, QUELLE_1B_G);
        assertTrue(kandidaten1bGleichReferenzG(List.of(eins), referenz), "gleich");
        assertFalse(kandidaten1bGleichReferenzG(List.of(), referenz), "Referenz ohne Kandidat");
        Finding extra = new Finding(FindingType.KVNR, text.substring(0, 4), 0, 4, KONF_1B_G, QUELLE_1B_G);
        assertFalse(kandidaten1bGleichReferenzG(List.of(eins, extra), referenz), "Kandidat ohne Referenz");
        Finding mitKontext = new Finding(FindingType.KVNR, nummer, start, ende, KONF_DIREKT_G,
                KvnrRecognizer.SOURCE);
        assertTrue(kandidaten1bGleichReferenzG(List.of(mitKontext), referenz),
                "ein Fund aus Teil 1a verdraengt den Treffer der Referenz");
        assertFalse(kandidaten1bGleichReferenzG(List.of(mitKontext, eins), referenz),
                "zweiter Kandidat neben dem Fund aus Teil 1a");
    }

    /**
     * Selbsttest der Markierung aus dem Text: Ein Kontextwort unmittelbar vor dem Block (am Ende eines Buchstabenlaufs)
     * und eine Ziffer ausserhalb von ASCII direkt davor oder dahinter markieren; ein Trenner, ein Buchstabe ohne
     * Kontextwort, eine ASCII-Ziffer, ein Buchstabe ausserhalb von ASCII und Leerzeichen markieren nicht.
     */
    @Test
    void t11_teil1g_selbsttest_markierungErkenntDieGrenzklassenUndGrenztAb() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String arabisch = String.valueOf((char) 0x665);
        String vollbreit = String.valueOf((char) 0xFF15);
        assertEquals(GRENZE_AM_BUCHSTABEN, markiereG("KVNR" + nummer, 4), "Kontextwort am Buchstaben");
        assertEquals(GRENZE_AM_BUCHSTABEN, markiereG("xKVNR" + nummer, 5), "Kontextwort am Ende eines Buchstabenlaufs");
        assertEquals("", markiereG("KVNR " + nummer, 5), "Trenner davor");
        assertEquals("", markiereG("Hallo" + nummer, 5), "Buchstabe, aber kein Kontextwort");
        assertEquals(GRENZE_NICHT_ASCII_ZIFFER, markiereG("Notiz " + arabisch + nummer, 7), "arabische Ziffer davor");
        assertEquals(GRENZE_NICHT_ASCII_ZIFFER, markiereG("Notiz " + vollbreit + nummer, 7), "Vollbreite davor");
        assertEquals(GRENZE_NICHT_ASCII_ZIFFER, markiereG("Notiz " + nummer + arabisch, 6),
                "arabische Ziffer dahinter");
        assertEquals("", markiereG("Notiz 7" + nummer, 7), "ASCII-Ziffer davor");
        assertEquals("", markiereG("Notiz " + (char) 0xE4 + nummer, 7), "Nicht-ASCII-Buchstabe davor");
        assertEquals("", markiereG("Notiz " + nummer + " Ende", 6), "Leerzeichen");
        assertTrue(nichtAsciiZiffer(0x665) && nichtAsciiZiffer(0xFF15) && !nichtAsciiZiffer('5') && !nichtAsciiZiffer(
                0xE4), "Nicht-ASCII-Ziffer");
    }

    /** Die Grenzklasse eines Blocks der Laenge zehn ab {@code start}. */
    private static String markiereG(String text, int start) {
        return grenzklasseG(text, blockG(start, start + 10));
    }

    /**
     * Selbsttest der Pruefung von Gegenprobe, Bestand-Baustein und Restfund: Eine Gegenprobe, die allein einen Fund
     * oder einen Treffer der Referenz liefert, wird gemeldet, eine kleingeschriebene nicht; ein Bestand-Baustein,
     * dessen Nummer nicht im Bestand liegt, wird gemeldet; ein Restfund mit falscher Konfidenz oder Quelle, ein
     * fehlender Restfund (Mutant der Schwelle 3 zu 4) und ein Restfund mit zwei Ziffern (Mutant der Schwelle 3 zu 2)
     * werden gemeldet.
     */
    @Test
    void t11_teil1g_selbsttest_gegenprobeBestandUndRestfund() {
        String nummer = SynthetischeKvNummern.KVNR_GUELTIG;
        String klein = nummer.toLowerCase(Locale.ROOT);
        Lauf mitFund = pruefeG(fallG(nummer, List.of(), List.of(new GegenprobeG("x", 0, nummer.length())), List.of(),
                List.of(), List.of(new KvnrBsnrHilfen.Bereich(0, nummer.length()))));
        assertEquals(1, mitFund.anzahl("gegenprobe-mit-kvnr-fund"), "Gegenprobe mit Fund wird gemeldet");
        assertEquals(1, mitFund.anzahl("gegenprobe-referenz-trifft"), "Gegenprobe mit Treffer wird gemeldet");
        Lauf ohneFund = pruefeG(fallG(klein, List.of(), List.of(new GegenprobeG("klein", 0, klein.length())),
                List.of(), List.of(), List.of()));
        assertTrue(ohneFund.verletzungen.isEmpty(), "kleingeschriebene Gegenprobe besteht");
        assertEquals(1, ohneFund.klasse_("gegenprobe-im-text-ohne-fund-klein"), "im Text ohne Fund");
        String email = nummer + "@example.invalid";
        Lauf imBestand = pruefeG(fallG(email, List.of(), List.of(), List.of(new BestandG("email-lokal", 0,
                nummer.length())), List.of(), List.of()));
        assertTrue(imBestand.verletzungen.isEmpty(), "Nummer in der E-Mail besteht: " + imBestand.meldung("x"));
        assertEquals(1, imBestand.klasse_("bestand-email-lokal-ok"), "vom Bestand gedeckt");
        Lauf nichtImBestand = pruefeG(fallG(KONTEXT_G + nummer, List.of(), List.of(), List.of(new BestandG("x",
                KONTEXT_G.length(), KONTEXT_G.length() + nummer.length())), List.of(),
                List.of(new KvnrBsnrHilfen.Bereich(0, KONTEXT_G.length() + nummer.length()))));
        assertEquals(1, nichtImBestand.anzahl("bestand-nummer-nicht-vom-bestand-gedeckt"), "kein Bestand");
        assertEquals(1, nichtImBestand.anzahl("bestand-kvnr-fund-in-bestand"), "KVNR-Fund in der Nummer");
        String drei = "KVNR " + nummer.substring(0, 4) + " " + nummer.substring(4) + "@example.invalid";
        int s = "KVNR ".length();
        assertTrue(pruefeG(fallG(drei, List.of(), List.of(), List.of(), List.of(new RestG(s, s + 4, KONF_REST_G)),
                List.of())).verletzungen.isEmpty(), "Restfund mit drei Ziffern, 0,80, Quelle kvnr-rest");
        assertEquals(1, pruefeG(fallG(drei, List.of(), List.of(), List.of(), List.of(new RestG(s, s + 4,
                KONF_DIREKT_G)), List.of())).anzahl("rest-fund-abweichend"), "falsche Konfidenz (Deckel)");
        assertEquals(1, pruefeG(fallG(drei, List.of(), List.of(), List.of(), List.of(new RestG(s, s + 3, KONF_REST_G)),
                List.of())).klasse_("rest-drei-ueberlagert"), "Restfund nicht wie konstruiert");
        String zwei = "KVNR " + nummer.substring(0, 3) + " " + nummer.substring(3) + "@example.invalid";
        Lauf ohneRest = pruefeG(fallG(zwei, List.of(), List.of(), List.of(), List.of(new RestG(s, s + 3, -1)),
                List.of()));
        assertTrue(ohneRest.verletzungen.isEmpty(), "kein Restfund mit zwei Ziffern");
        assertEquals(1, ohneRest.klasse_("rest-unter-drei-ohne-fund"), "gezaehlt");
        Finding quelleFalsch = new Finding(FindingType.KVNR, drei.substring(s, s + 4), s, s + 4, KONF_REST_G,
                KvnrRecognizer.SOURCE);
        Lauf mutant = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(mutant, 0, new RestG(s, s + 4, KONF_REST_G), List.of(new Finding(FindingType.EMAIL,
                drei.substring(s + 5), s + 5, drei.length(), 0.9, "email"), quelleFalsch));
        assertEquals(1, mutant.anzahl("rest-fund-abweichend"), "falsche Quelle");
        Lauf schwelle = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(schwelle, 0, new RestG(s, s + 4, KONF_REST_G), List.of(new Finding(FindingType.EMAIL,
                drei.substring(s + 5), s + 5, drei.length(), 0.9, "email")));
        assertEquals(1, schwelle.anzahl("rest-fund-abweichend"), "fehlender Restfund (Schwelle 4)");
        Lauf zuViel = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(zuViel, 0, new RestG(s, s + 3, -1), List.of(new Finding(FindingType.EMAIL, zwei.substring(s + 4),
                s + 4, zwei.length(), 0.9, "email"), new Finding(FindingType.KVNR, zwei.substring(s, s + 3), s, s + 3,
                KONF_REST_G, KvnrRecognizer.SOURCE_REST)));
        assertEquals(1, zuViel.anzahl("rest-fund-unerwartet"), "Restfund mit zwei Ziffern (Schwelle 2)");
        // Ueberlagert: gedeckt nur, wenn ein Bestandsfund alle Ziffern deckt (der Buchstabe darf frei bleiben)
        Lauf gedeckt = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(gedeckt, 0, new RestG(s, s + 4, KONF_REST_G), List.of(new Finding(FindingType.EMAIL,
                drei.substring(s + 1), s + 1, drei.length(), 0.5, Finding.SOURCE_MERGED)));
        assertEquals(0, gedeckt.anzahl("rest-ueberlagert-ziffern-ungedeckt"), "Ziffern gedeckt, Buchstabe frei");
        assertEquals(1, gedeckt.klasse_("rest-ueberlagert-gedeckt"), "als gedeckt gezaehlt");
        Lauf ohneEmail = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(ohneEmail, 0, new RestG(s, s + 4, KONF_REST_G), List.of());
        assertEquals(1, ohneEmail.anzahl("rest-ueberlagert-ziffern-ungedeckt"), "E-Mail-Fund fehlt ganz");
        Lauf nurKvnr = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(nurKvnr, 0, new RestG(s, s + 4, KONF_REST_G), List.of(new Finding(FindingType.KVNR,
                drei.substring(s, s + 10), s, s + 10, KONF_REST_G, KvnrRecognizer.SOURCE)));
        assertEquals(1, nurKvnr.anzahl("rest-ueberlagert-ziffern-ungedeckt"), "kein Bestandsfund, nur KVNR-Fund");
        Lauf eineZiffer = new Lauf(SEED_TEXTE_KV_G);
        pruefeRestG(eineZiffer, 0, new RestG(s, s + 4, KONF_REST_G), List.of(new Finding(FindingType.EMAIL,
                drei.substring(s + 2), s + 2, drei.length(), 0.5, Finding.SOURCE_MERGED)));
        assertEquals(1, eineZiffer.anzahl("rest-ueberlagert-ziffern-ungedeckt"), "eine Ziffer vorn frei");
    }

    private static int mutantenG(Recognizer mutant, String eigenschaft) {
        Lauf lauf = new Lauf(SEED_TEXTE_KV_G);
        TextScanner scanner = TextScanner.standard();
        List<FallG> faelle = erzeugeKvG(SEED_TEXTE_KV_G);
        for (int it = 0; it < SELBSTTEST_TEXTE_G; it++) {
            pruefeKvG(lauf, scanner, mutant, it, faelle.get(it));
        }
        return lauf.anzahl(eigenschaft);
    }

    /**
     * Selbsttest des Fuzz-Laufs gegen Mutanten des Erkenners (kuenstliche Erkenner der Referenz, je eine Eigenschaft
     * kaputt, auf den ersten {@value #SELBSTTEST_TEXTE_G} Texten): Ohne Wortgrenzen, ohne Pruefziffer, ohne
     * Negativkontext und mit Kleinbuchstaben schlagen die Gegenproben (Erkenner allein) und der Vergleich der
     * Kandidaten mit der Referenz an; die Referenz selbst als Erkenner besteht beides. Messwerte (Gegenproben mit
     * Fund, Texte mit abweichendem Vergleich), Untergrenzen bei 70 %: ohne Wortgrenzen 117 und 184, ohne Pruefziffer
     * 24 und 30, ohne Negativkontext 18 und 18, mit Kleinbuchstaben 17 und 23.
     */
    @Test
    void t11_teil1g_selbsttest_mutantenDesErkennersWerdenGemeldet() {
        KvnrOhneKontextHilfen.Optionen referenz = KvnrOhneKontextHilfen.Optionen.REFERENZ;
        List<Recognizer> mutanten = List.of(KvnrOhneKontextHilfen.kuenstlicherErkenner(referenz.ohneWortgrenzen()),
                KvnrOhneKontextHilfen.kuenstlicherErkenner(referenz.ohnePruefziffer()),
                KvnrOhneKontextHilfen.kuenstlicherErkenner(referenz.ohneNegativkontext()),
                KvnrOhneKontextHilfen.kuenstlicherErkenner(referenz.mitKleinbuchstaben()));
        String[] namen = {"ohne Wortgrenzen", "ohne Pruefziffer", "ohne Negativkontext", "mit Kleinbuchstaben"};
        int[] minGegenproben = {81, 16, 12, 11};
        int[] minVergleich = {128, 21, 12, 16};
        for (int i = 0; i < mutanten.size(); i++) {
            int gegenproben = mutantenG(mutanten.get(i), "gegenprobe-mit-kvnr-fund");
            int vergleich = mutantenG(mutanten.get(i), "kandidaten-1b-ungleich-referenz");
            assertTrue(gegenproben >= minGegenproben[i], namen[i] + ": Gegenproben mit Fund " + gegenproben);
            assertTrue(vergleich >= minVergleich[i], namen[i] + ": Texte mit abweichendem Vergleich " + vergleich);
        }
        Recognizer exakt = KvnrOhneKontextHilfen.kuenstlicherErkenner(referenz);
        assertEquals(0, mutantenG(exakt, "gegenprobe-mit-kvnr-fund"), "die Referenz als Erkenner: Gegenproben");
        assertEquals(0, mutantenG(exakt, "kandidaten-1b-ungleich-referenz"), "die Referenz als Erkenner: Vergleich");
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
