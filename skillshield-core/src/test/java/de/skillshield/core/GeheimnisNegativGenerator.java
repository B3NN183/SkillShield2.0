package de.skillshield.core;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Negativ-Generator (Plan geheimnisse-erkennung, T-2; Anforderung AK-36): 100 000 Saetze aus Kontextwort (jede Form
 * aus D-4 und D-5) mal Verbinder und Zwischenteil (jede Form aus D-6) mal einem Wert, der nach D-8 kein Geheimnis ist:
 * einem Wort der synthetischen Wortliste ({@code wortliste.txt}), einer Ziffernfolge unter neun Stellen, einem Datum,
 * einer Versionsnummer, einer Ticket- oder Aenderungsnummer mit mindestens drei Ziffern oder einem Zahlwort-Verbund.
 * Jeder Satz muss 0 Funde vom Typ PASSWORT oder TOKEN liefern.
 *
 * <p>Die Zahlwort-Verbuende ({@link Klasse#ZAHLWORT}) enthalten Formen mit Umlaut (zum Beispiel "90-taegig" mit ae-
 * Umlaut); die Regel W3 (c) meint nach Entscheidung 2 des Plans (25.11.2026) Kleinbuchstaben einschliesslich Umlaute
 * und scharfem s (Pruefung mit {@code Character.isLowerCase}), nicht nur ASCII.
 *
 * <p>Der Generator ist deterministisch (fester Seed {@link #SEED}); gleiche Aufrufe liefern gleiche Saetze. Er
 * enthaelt keine Geheimnisse, nur Woerter, Zahlen und Fachformen. Eingefroren ab Abnahme T-2 samt Seed, Listen und
 * Reihenfolge der Zufallsaufrufe (die Siegel in {@code GeheimnisFehlalarmTest} sichern das).
 *
 * <p>Die Klasse enthaelt ausserdem die kleinen Hilfen, die beide Generatoren und die Pruefstaende teilen
 * ({@link #siegel(String)}, {@link #woerter()}).
 */
final class GeheimnisNegativGenerator {

    /** Fester Seed des Negativ-Generators. */
    static final long SEED = 20261125L;

    /** Zahl der erzeugten Saetze (AK-36). */
    static final int ANZAHL = 100_000;

    /** Zahl der ersten Saetze, ueber die das Siegel gebildet wird. */
    static final int SIEGEL_ANZAHL = 1000;

    static final String UE = String.valueOf((char) 0xFC);
    static final String AE = String.valueOf((char) 0xE4);
    static final String NBSP = String.valueOf((char) 0xA0);
    static final String DUENNES_LEERZEICHEN = String.valueOf((char) 0x2009);

    /** Platzhalter fuer das Kontextwort in einer Vorlage. */
    static final String K = "{K}";

    /** Platzhalter fuer den Wert in einer Vorlage. */
    static final String W = "{W}";

    /** Alle Formen der Kontextwoerter aus D-4 und D-5 (Schreibweisen aus AK-2 und AK-17, dazu die Staemme). */
    static final List<String> KONTEXTWOERTER = List.of(
            "Passwort", "passwort", "KENNWORT", "Kennwort", "password", "Password", "passwd", "Passphrase",
            "Initialpasswort", "Einmalkennwort", "Adminpasswort", "Root-Passwort", "db-passwort", "PW", "pw", "Pwd",
            "pwd", "DB_PASSWORD", "dbPassword", "PASSWD", "PassPhrase", "kennwort", "PASSWORT",
            "Token", "access_token", "accessToken", "refresh_token", "idToken", "API-Key", "apikey", "api_key",
            "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret", "secretkey", "SecretKey",
            "accesskey", "AccessKey", "privatekey", "private_key", "Private-Key", "TOKEN", "secret",
            "Bearer", "Authorization");

    /** Die Vorlagen fuer Verbinder, Zwischenteil, Anfuehrungszeichen und Zeilenumbruch (D-6, D-7). */
    static final List<String> STILE = List.of(
            K + ": " + W, K + " : " + W, K + ":" + W, K + "=" + W, K + " = " + W, K + " =" + W,
            K + " ist " + W, K + " lautet " + W, K + " is " + W,
            K + "\t" + W, "| " + K + " | " + W + " |",
            "<" + K + ">" + W + "</" + K + ">",
            "\"" + K + "\": \"" + W + "\"", K + ": '" + W + "'", K + " = \"" + W + "\"",
            K + ": `" + W + "`", K + ": " + (char) 0x201E + W + (char) 0x201C,
            K + ":\n" + W, K + ":\r\n    " + W, K + " =\n" + W,
            K + " f" + UE + "r svc_x: " + W, K + " f" + UE + "r den Benutzer svc_x lautet " + W,
            K + " des Kontos KVBSP\\svc_x: " + W, K + " for root: " + W, K + " von SRV-APP07 ist " + W,
            K + ":" + NBSP + W, K + ":" + DUENNES_LEERZEICHEN + W,
            K + " zum Konto svc_x: " + W, K + " zur Anmeldung von svc_x lautet " + W,
            K + " vom Dienstkonto svc_x = " + W, K + " der Anwendung KVBSP-App: " + W,
            K + " of svc_x: " + W, K + " to svc_x is " + W, K + " zu svc_x: " + W);

    /** Die Klassen der Werte. */
    enum Klasse {
        WORT, ZIFFERN, DATUM, VERSION, TICKET, ZAHLWORT
    }

    private static final List<String> VORSAETZE = List.of("", "Hinweis: ", "Ticket: ", "Im Wiki steht: ",
            "Kommentar des Service-Desks: ", "Beispiel: ");

    private static final List<String> NACHSAETZE = List.of("", " bitte pr" + UE + "fen", " (siehe Tresor)",
            " und danach neu starten");

    private static final List<String> TICKET_VORSILBEN = List.of("CHG", "INC", "JIRA", "KVBSP", "REQ", "PRB",
            "ITSM", "TASK");

    private static final List<String> ZAHLWORT_ENDUNGEN = List.of("stellig", "t" + AE + "gig", "st" + UE + "ndig",
            "min" + UE + "tig", "seitig", "zeilig", "j" + AE + "hrig");

    private static final List<String> VERSIONS_ENDUNGEN = List.of("", "", "-rc1", "-beta2", "-SNAPSHOT", "-rc12");

    /** Das Ergebnis eines Laufs mit den Kennzahlen. */
    record Lauf(List<String> texte, int[] jeKontext, int[] jeStil, int[] jeKlasse) {
    }

    private GeheimnisNegativGenerator() {
    }

    /** Die Woerter der Wortliste (ohne Kommentarzeilen, ohne Doppelte, in Dateireihenfolge). */
    static List<String> woerter() {
        Set<String> woerter = new LinkedHashSet<>();
        for (String zeile : SynthetischeGeheimnisse.korpusText("wortliste.txt").split("\n", -1)) {
            if (!zeile.isEmpty() && zeile.charAt(0) != '#') {
                woerter.add(zeile);
            }
        }
        return List.copyOf(woerter);
    }

    /** Erzeugt {@link #ANZAHL} Saetze mit dem festen Seed. */
    static Lauf lauf() {
        return lauf(SEED, ANZAHL);
    }

    /** Erzeugt {@code anzahl} Saetze mit dem Seed {@code seed}; ein kurzer Lauf ist Praefix des laengeren. */
    static Lauf lauf(long seed, int anzahl) {
        Random r = new Random(seed);
        List<String> woerter = woerter();
        List<String> texte = new ArrayList<>(anzahl);
        int[] jeKontext = new int[KONTEXTWOERTER.size()];
        int[] jeStil = new int[STILE.size()];
        int[] jeKlasse = new int[Klasse.values().length];
        for (int i = 0; i < anzahl; i++) {
            int kontext = r.nextInt(KONTEXTWOERTER.size());
            int stil = r.nextInt(STILE.size());
            Klasse klasse = Klasse.values()[r.nextInt(Klasse.values().length)];
            String wert = wert(r, klasse, woerter);
            String vor = VORSAETZE.get(r.nextInt(VORSAETZE.size()));
            String nach = NACHSAETZE.get(r.nextInt(NACHSAETZE.size()));
            texte.add(vor + STILE.get(stil).replace(K, KONTEXTWOERTER.get(kontext)).replace(W, wert) + nach);
            jeKontext[kontext]++;
            jeStil[stil]++;
            jeKlasse[klasse.ordinal()]++;
        }
        return new Lauf(texte, jeKontext, jeStil, jeKlasse);
    }

    private static String ziffern(Random r, int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            sb.append((char) ('0' + r.nextInt(10)));
        }
        return sb.toString();
    }

    private static String zweistellig(int zahl) {
        return zahl < 10 ? "0" + zahl : String.valueOf(zahl);
    }

    private static String wert(Random r, Klasse klasse, List<String> woerter) {
        switch (klasse) {
            case WORT:
                return woerter.get(r.nextInt(woerter.size()));
            case ZIFFERN:
                return ziffern(r, 1 + r.nextInt(8));
            case DATUM:
                return datum(r);
            case VERSION:
                return version(r);
            case TICKET:
                return TICKET_VORSILBEN.get(r.nextInt(TICKET_VORSILBEN.size())) + (r.nextBoolean() ? "-" : "")
                        + ziffern(r, 3 + r.nextInt(5));
            default:
                return (1 + r.nextInt(99)) + "-" + ZAHLWORT_ENDUNGEN.get(r.nextInt(ZAHLWORT_ENDUNGEN.size()));
        }
    }

    private static String datum(Random r) {
        int jahr = 2020 + r.nextInt(11);
        int monat = 1 + r.nextInt(12);
        int tag = 1 + r.nextInt(28);
        switch (r.nextInt(3)) {
            case 0:
                return jahr + "-" + zweistellig(monat) + "-" + zweistellig(tag);
            case 1:
                return zweistellig(tag) + "." + zweistellig(monat) + "." + jahr;
            default:
                return zweistellig(tag) + "." + zweistellig(monat) + "." + zweistellig(jahr % 100);
        }
    }

    private static String version(Random r) {
        StringBuilder sb = new StringBuilder();
        if (r.nextBoolean()) {
            sb.append('v');
        }
        sb.append(r.nextInt(21)).append('.').append(r.nextInt(31));
        if (r.nextBoolean()) {
            sb.append('.').append(r.nextInt(100));
        }
        sb.append(VERSIONS_ENDUNGEN.get(r.nextInt(VERSIONS_ENDUNGEN.size())));
        return sb.toString();
    }

    /** SHA-256 des Textes (UTF-8), die ersten acht Hexziffern; das Siegel der eingefrorenen Pruefstaende. */
    static String siegel(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i] & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt");
        }
    }

    /** Siegel ueber die ersten {@code n} Texte (getrennt durch das Zeichen 1). */
    static String siegelUeberTexte(List<String> texte, int n) {
        return siegel(String.join(String.valueOf((char) 1), texte.subList(0, n)));
    }

    // ------------------------------------------------------------------ kuenstliche Erkenner (Gegenproben)

    private static final String KONTEXT = "(?:passwort|kennwort|password|passwd|passphrase|pwd|pw|token|apikey"
            + "|api[ _-]?key|secret|secretkey|accesskey|privatekey|bearer|authorization)";
    private static final String ZWISCHEN = "(?:[ \\t]+(?:f" + UE + "r|for|von|vom|des|der|zu|zum|zur|of|to)"
            + "(?:[ \\t]+[^\\s:=]+){1,3})?";

    /** Satz hat ein Kontextwort mit K1 (Doppelpunkt oder Gleichheitszeichen), unabhaengig davon, ob ein Wert folgt. */
    static final Pattern HAT_K1 = Pattern.compile("(?i)" + KONTEXT + "[\"']?" + ZWISCHEN + "[ \\t]*[:=]");
    static final Pattern HAT_K2 = Pattern.compile("(?i)" + KONTEXT + ZWISCHEN + "[ \\t]+(?:ist|lautet|is)[ \\t]");
    static final Pattern HAT_K3 = Pattern.compile("(?i)" + KONTEXT + "(?:[ \\t]*\\|\\s*|\\t)[^\\s]");
    static final Pattern HAT_K4 = Pattern.compile("(?i)<" + KONTEXT + ">[^<\\s]");

    private static final Pattern WERT_K1 = Pattern.compile("(?i)" + KONTEXT + "[\"']?" + ZWISCHEN
            + "[ \\t]*[:=][ \\t]*(?:\\r?\\n[ \\t]*)?(\\S+)");
    private static final Pattern WERT_K2 = Pattern.compile("(?i)" + KONTEXT + ZWISCHEN
            + "[ \\t]+(?:ist|lautet|is)[ \\t]+(\\S+)");
    private static final Pattern WERT_K3 = Pattern.compile("(?i)" + KONTEXT + "(?:[ \\t]*\\|[ \\t]*|\\t+)(\\S+)");
    private static final Pattern WERT_K4 = Pattern.compile("(?i)<" + KONTEXT + ">([^<\\s]+)");

    private static final FindingType KUENSTLICH = new FindingType("PASSWORT");

    private static List<Finding> ersteWoerter(String text, List<Pattern> muster) {
        List<Finding> funde = new ArrayList<>();
        for (Pattern p : muster) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                funde.add(new Finding(KUENSTLICH, m.group(1), m.start(1), m.end(1), 0.95, "kuenstlich"));
            }
        }
        return funde;
    }

    /** Gegenprobe: das erste Wort hinter Kontextwort und {@code :} oder {@code =}, ohne jeden Wert-Test. */
    static Recognizer ersterWortNachK1() {
        return text -> ersteWoerter(text, List.of(WERT_K1));
    }

    /** Gegenprobe: das erste Wort hinter Kontextwort und jedem Verbinder (K1 bis K4), ohne jeden Wert-Test. */
    static Recognizer ersterWortNachJedemVerbinder() {
        return text -> ersteWoerter(text, List.of(WERT_K1, WERT_K2, WERT_K3, WERT_K4));
    }

    /** Gegenprobe: liefert fuer genau die bekannten Texte die vorgegebenen Funde, sonst keine. */
    static Recognizer liefertVorgegebene(Map<String, List<Finding>> nachText) {
        return text -> nachText.getOrDefault(text, List.of());
    }
}
