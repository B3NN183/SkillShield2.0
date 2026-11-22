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
 * unveraendert (Hashwert {@link #HASH_TEIL_1}); Teil 1b ist ebenfalls festgehalten ({@link #HASH_TEIL_1B}). Teil 1c
 * ({@code SEED_TEXTE_KV_C}, kv-kontext-luecken T-6, eigener Generator {@link #erzeugeKvC}, Hash
 * {@link #HASH_TEIL_1C}): Sperrschrift beider Arten mit allen Trennern, {@code I K}, Komma oder Semikolon vor dem
 * Kontextwort (auch Folgen), Gegenproben und Initialen; dieselben Eigenschaften wie Teil 1b samt Schutzgarantie, dazu
 * vollstaendiges Ersetzen aller Funde. Teil 2: {@value #ANZAHL_EINGABEN} Zufallseingaben an {@code restore}.
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
     * ({@link #erzeugeTextC}) und deren Nummernbereich von keinem Scan-Fund ganz abgedeckt ist. Das ist die
     * offene Grenze 14h (Plan Kap. 11): laufender IK-Kontext, Vorwort, gesperrtes {@code I K}, Nachsatzwort,
     * gueltige IK, z. B. {@code IK: Praxis I K Nr N}. Messwert 20 bei festem Seed, von security einzeln
     * bestaetigt (alle IK); T-7 stellt die Erwartung auf 0 um (siehe
     * {@code t6_kvC_blockOhneScanAbdeckung_nurOffeneGrenze14h}). Nicht geprueft werden die Konfidenz der
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
     * Mindestzahlen der Klassen (Messwerte bei festem Seed in Klammern, Abstand mindestens ein Drittel):
     * Fund der neuen Regel je Blockart und Art (Sperrschrift ab drei Buchstaben 656 / LANR 446 / IK 388, I K 510,
     * Komma 699 / LANR 452 / IK 464), Fund aus neuer Regel insgesamt 960 (Untergrenze 600), Funde je Typ
     * (LANR 843, IK 939; je 500), fraglich 947 (500), Scan-Funde 998 (600), manuelle Funde 947 (500), echte
     * Teilmengen der Scan-Funde 854 (500).
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
     * OFFENE GRENZE 14h, wird in T-7 geschlossen; dann Erwartung auf 0 umdrehen (Plan Kap. 11, 14h). Die als
     * 14e markierten Bloecke ohne Scan-Abdeckung (69: 53 Sperrbloecke hinter {@code " x "}, 16 Kommabloecke
     * vor {@code " x "}) sind per Konstruktion markiert und zaehlen nicht. Die uebrigen Bloecke ohne Abdeckung
     * haben alle das Muster 14h: laufender IK-Kontext, Vorwort, gesperrtes {@code I K}, Nachsatzwort, gueltige IK
     * ({@code IK: Praxis I K Nr N}). Beim Konstruieren laesst sich "aktiver Kontext" nicht sicher bestimmen (er
     * haengt von den Vorbausteinen, Trennern und Fuellwoertern ab), eine Markierung ist daher nicht sauber
     * moeglich; stattdessen gilt der exakte Messwert bei festem Seed. Ein Wert ungleich 20 ist ein Befund.
     */
    @Test
    void t6_kvC_blockOhneScanAbdeckung_nurOffeneGrenze14h() {
        assertEquals(20, LAUF_KV_C.klasse_("block-ohne-scan-abdeckung"),
                "Bloecke ohne Scan-Abdeckung (offene Grenze 14h, T-7 stellt auf 0), Startwert " + SEED_TEXTE_KV_C);
    }

    /** Strukturbeleg 14h: nicht markierte Bloecke ohne Abdeckung nach Blockart und Art (T-7 stellt alle auf 0). */
    @Test
    void t6_kvC_blockOhneScanAbdeckung_nachBlockartUndArt() {
        String basis = "block-ohne-scan-abdeckung-";
        assertEquals(17, LAUF_KV_C.klasse_(basis + "sperr-zwei-ik"), "Sperrschrift zwei Buchstaben, IK");
        assertEquals(3, LAUF_KV_C.klasse_(basis + "sperr-drei-ik"), "Sperrschrift ab drei Buchstaben, IK");
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
