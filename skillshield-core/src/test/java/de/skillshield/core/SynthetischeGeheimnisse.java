package de.skillshield.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Testdaten fuer Geheimnisse (Plan geheimnisse-erkennung, Kapitel 6.2; Anforderung D-18, AK-50, AK-51).
 * Alle Werte sind eindeutig erfunden und fuer keinen echten Anbieter gueltig. Alle Token-Werte tragen die Marke
 * {@link #MARKE}; sie bricht jede Herstellerpruefsumme, und die JWT-Teile sind kein gueltiges Base64-JSON.
 *
 * <p><b>Zusammengesetzt zur Laufzeit.</b> JWT, PEM-Koepfe und Praefix-Token stehen nirgends als zusammenhaengendes
 * Literal im Quelltext, sondern entstehen in {@link #jwt()}, {@link #pemBlock(String, String, boolean)} und
 * {@link #praefixToken(String)} aus Teilen. Sonst schluegen Geheimnis-Scanner in Entwicklungswerkzeugen an; der
 * Waechter {@code GeheimnisLiteralWaechterTest} prueft das ohne Ausnahme auch fuer diese Datei.
 *
 * <p><b>Ziffern.</b> Keine Ziffernfolge ist laenger als vier, und Ziffern stehen nur einzeln zwischen Buchstaben
 * (Hex-, Base64- und UUID-Proben), damit der bestehende Ziffernketten-Waechter {@code TestdatenSynthetischTest}
 * ohne neue Ausnahme gruen bleibt. {@code SynthetischeGeheimnisseTest} prueft das auch fuer jeden zur Laufzeit
 * gebauten Wert.
 *
 * <p><b>Korpusdateien</b> (Kapitel 6.2): Ressourcen unter {@code de/skillshield/core/geheimnis/}, UTF-8 ohne BOM. Der
 * Leser ist strikt ({@code CodingErrorAction.REPORT}) und wandelt CR LF und CR nach dem Lesen in LF, unabhaengig von
 * der Checkout-Einstellung. Meldungen dieser Klasse nennen nie Inhalte.
 *
 * <p>Die Anforderung definiert nur die Beispielwerte P1, P2, P3, P6 und P7 (und T); weitere Passwoerter gibt es hier
 * nicht.
 *
 * <p>Ab Abnahme von T-1 eingefroren; es sind nur Ergaenzungen erlaubt.
 */
public final class SynthetischeGeheimnisse {

    /** Marke jedes Token-Werts. */
    public static final String MARKE = "Testwert";

    /** Das Beispielpasswort des Referenz-Tickets (elf Zeichen, Ausrufezeichen am Ende, Anforderung P1). */
    public static final String P1 = "Sommer2026!";

    /** Anforderung P2. */
    public static final String P2 = "Wolke#Fuchs7x";

    /** Anforderung P3. */
    public static final String P3 = "Kaffee-Tasse8";

    /** Anforderung P6: sechs Zeichen. */
    public static final String P6 = "Ab3!xy";

    /** Anforderung P7: sieben Zeichen. */
    public static final String P7 = "Zx9#kqw";

    /** Ergaenzung T-2, Anforderung AK-7, AK-11: wie P1, aber ohne das Ausrufezeichen am Ende. */
    public static final String P1_OHNE_AUSRUF = "Sommer2026";

    /** Ergaenzung T-2, Anforderung AK-10: fuenf Zeichen, zu kurz fuer einen Fund. */
    public static final String P5 = "Ab3!x";

    /** Ergaenzung T-2, Anforderung AK-11: Sonderzeichen mitten im Wort. */
    public static final String P_DOLLAR = "Pa$$word";

    /** Ergaenzung T-2, Anforderung AK-16: Kleinbuchstaben und eine Ziffer. */
    public static final String P_HUNTER = "hunter2x";

    /** Ergaenzung T-2, Anforderung AK-24: nur Buchstaben (ODBC-Zeichenfolge, halber Wert-Test). */
    public static final String P_GEHEIM = "geheim";

    /** Ergaenzung T-2, Anforderung AK-8: Wert mit Leerzeichen, der nur in Anfuehrungszeichen erfasst wird. */
    public static final String P_MIT_LEERZEICHEN = "Wolke Fuchs 7x!";

    /** Ergaenzung T-2, Anforderung AK-24: Wert mit Leerzeichen ohne Ausrufezeichen. */
    public static final String P_LEERZEICHEN_OHNE = "Wolke Fuchs 7x";

    /** Ergaenzung T-2, Anforderung AK-24: Wert mit Semikolon. */
    public static final String P_SEMIKOLON = "a;b1x";

    /** Erfundener Token der Anforderung (Kurzname T): 29 Zeichen, Buchstaben, eine einzelne Ziffer, mit Marke. */
    public static final String T = "AbcdTestwertEfghIjklMnop7Qrst";

    /** Rest eines Praefix-Tokens: Buchstaben, eine einzelne Ziffer und die Marke (25 Zeichen). */
    public static final String PRAEFIX_REST = "Abcd" + MARKE + "EfghIjkl7Mnop";

    /** Pfad der Korpusdateien im Klassenpfad. */
    private static final String KORPUS_PFAD = "/de/skillshield/core/geheimnis/";

    private static final String HEX_BUCHSTABEN = "abcdef";
    private static final String HEX_ZIFFERN = "1357" + "9246" + "80";
    private static final String BASE64_BUCHSTABEN = "AbCdEfGhIjKlMnOpQrStUvWxYz";
    private static final String MISCHMUSTER = "Ab3Xy#Qzw";

    private SynthetischeGeheimnisse() {
    }

    // ------------------------------------------------------------------ Mischwerte, Hex, Base64, UUID

    /**
     * Ein Wert aus Buchstaben, einzelnen Ziffern und einem Sonderzeichen von genau dieser Laenge; er beginnt mit
     * einem Buchstaben, endet mit einem Buchstaben und hat keine Marke (Passwortprobe fuer die Laengengrenzen).
     */
    public static String mischwert(int laenge) {
        if (laenge < 1) {
            throw new IllegalArgumentException("laenge");
        }
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge - 1; i++) {
            sb.append(MISCHMUSTER.charAt(i % MISCHMUSTER.length()));
        }
        sb.append('z');
        return sb.toString();
    }

    /** Wert mit 4 096 Zeichen (Obergrenze von W1), Zeichenmischung erfuellt. */
    public static String wert4096() {
        return mischwert(4096);
    }

    /** Wert mit 4 097 Zeichen (eins ueber der Obergrenze von W1). */
    public static String wert4097() {
        return mischwert(4097);
    }

    /** Hex-Probe der Laenge {@code laenge}: Buchstaben a bis f an geraden, einzelne Ziffern an ungeraden Stellen. */
    public static String hex(int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            if (i % 2 == 1) {
                sb.append(HEX_ZIFFERN.charAt((i / 2) % HEX_ZIFFERN.length()));
            } else {
                sb.append(HEX_BUCHSTABEN.charAt((i / 2) % HEX_BUCHSTABEN.length()));
            }
        }
        return sb.toString();
    }

    /** Hex-Probe mit 32 Zeichen (Hash-Laenge MD5). */
    public static String hex32() {
        return hex(32);
    }

    /** Hex-Probe mit 40 Zeichen (Hash-Laenge SHA-1, Commit-ID). */
    public static String hex40() {
        return hex(40);
    }

    /** Hex-Probe mit 64 Zeichen (Hash-Laenge SHA-256). */
    public static String hex64() {
        return hex(64);
    }

    /** Kurze Commit-ID mit sieben Hex-Zeichen. */
    public static String hex7() {
        return hex(7);
    }

    /** Zertifikat-Fingerprint der Form {@code A1:B3:C5:...} mit zwanzig Paaren. */
    public static String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append((char) ('A' + i % 6)).append(HEX_ZIFFERN.charAt(i % HEX_ZIFFERN.length()));
        }
        return sb.toString();
    }

    /** Base64-aehnliche Zeile der Laenge {@code laenge}: Buchstaben und an jeder sechsten Stelle eine Ziffer. */
    public static String base64Zeile(int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            if (i % 6 == 5) {
                sb.append(HEX_ZIFFERN.charAt((i / 6) % HEX_ZIFFERN.length()));
            } else {
                sb.append(BASE64_BUCHSTABEN.charAt(i % BASE64_BUCHSTABEN.length()));
            }
        }
        return sb.toString();
    }

    /** Base64-aehnliche Zeile mit 60 Zeichen (AK-20). */
    public static String base64Zeile60() {
        return base64Zeile(60);
    }

    /** Erfundene UUID der Form 8-4-4-4-12; jeder Block hat Buchstaben, Ziffern stehen einzeln. */
    public static String uuid() {
        int[] laengen = {8, 4, 4, 4, 12};
        StringBuilder sb = new StringBuilder(36);
        int zaehler = 0;
        for (int b = 0; b < laengen.length; b++) {
            if (b > 0) {
                sb.append('-');
            }
            for (int i = 0; i < laengen[b]; i++) {
                if (i % 2 == 1) {
                    sb.append(HEX_ZIFFERN.charAt(zaehler % HEX_ZIFFERN.length()));
                } else {
                    sb.append(HEX_BUCHSTABEN.charAt(zaehler % HEX_BUCHSTABEN.length()));
                }
                zaehler++;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ JWT, PEM, Praefix-Token

    /** Erfundenes JWT aus drei Teilen (je mindestens acht Zeichen), alle mit Marke; kein gueltiges Base64-JSON. */
    public static String jwt() {
        String kopf = "ey" + "J" + MARKE + "Kopf" + "Abcd";
        String nutzlast = "ey" + "J" + MARKE + "Nutzlast" + "Efgh";
        String signatur = MARKE + "Signatur" + "Ijkl";
        return kopf + "." + nutzlast + "." + signatur;
    }

    /** Die Arten privater Schluessel, die ein Fund sind (D-11). */
    public static List<String> pemPrivateArten() {
        return List.of("PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY", "OPENSSH PRIVATE KEY",
                "ENCRYPTED PRIVATE KEY", "PGP PRIVATE KEY BLOCK");
    }

    /** Die oeffentlichen Arten, die kein Fund sind (D-11). */
    public static List<String> pemOeffentlicheArten() {
        return List.of("CERTIFICATE", "PUBLIC KEY", "CERTIFICATE REQUEST");
    }

    /** Die BEGIN-Zeile eines PEM-Blocks der Art {@code art}, zur Laufzeit zusammengesetzt. */
    public static String pemKopfzeile(String art) {
        return "-----" + "BEGIN " + art + "-----";
    }

    /** Die END-Zeile eines PEM-Blocks der Art {@code art}, zur Laufzeit zusammengesetzt. */
    public static String pemFusszeile(String art) {
        return "-----" + "END " + art + "-----";
    }

    /** Die drei Koerperzeilen eines PEM-Blocks (Base64-aehnlich, mit Marke in der ersten Zeile). */
    public static List<String> pemKoerperZeilen() {
        return List.of("Abcd" + MARKE + base64Zeile(52), base64Zeile(64), base64Zeile(22) + "==");
    }

    /** Wie {@link #pemKoerperZeilen()}, aber die erste Zeile beginnt mit einem Schraegstrich und enthaelt einen. */
    public static List<String> pemKoerperZeilenMitSchraegstrich() {
        return List.of("/bcd" + MARKE + "/" + base64Zeile(50), base64Zeile(64), base64Zeile(22) + "==");
    }

    /**
     * Ein PEM-Block mit dem Koerper aus {@link #pemKoerperZeilen()}.
     *
     * @param art         die Art, zum Beispiel aus {@link #pemPrivateArten()}
     * @param zeilenende  das Zeilenende zwischen den Zeilen (LF oder CR LF)
     * @param mitEnde     ob die END-Zeile folgt; ohne sie endet der Text mit der letzten Koerperzeile
     */
    public static String pemBlock(String art, String zeilenende, boolean mitEnde) {
        return pemBlock(art, pemKoerperZeilen(), zeilenende, mitEnde);
    }

    /** Wie {@link #pemBlock(String, String, boolean)} mit frei gewaehlten Koerperzeilen. */
    public static String pemBlock(String art, List<String> koerper, String zeilenende, boolean mitEnde) {
        StringBuilder sb = new StringBuilder();
        sb.append(pemKopfzeile(art)).append(zeilenende).append(String.join(zeilenende, koerper));
        if (mitEnde) {
            sb.append(zeilenende).append(pemFusszeile(art));
        }
        return sb.toString();
    }

    /** Die Praefixe der Praefix-Token (D-11), zur Laufzeit zusammengesetzt. */
    public static List<String> praefixe() {
        return List.of("gh" + "p_", "gh" + "o_", "gh" + "u_", "gh" + "s_", "gh" + "r_", "github" + "_pat_",
                "gl" + "pat-", "xo" + "xb-", "xo" + "xp-");
    }

    /** Ein Praefix-Token: der Praefix und {@link #PRAEFIX_REST}; mindestens 20 Zeichen, eine Ziffer im Rest. */
    public static String praefixToken(String praefix) {
        return praefix + PRAEFIX_REST;
    }

    // ------------------------------------------------------------------ Korpusdateien

    /**
     * Liest eine Korpusdatei unter {@code de/skillshield/core/geheimnis/} als Text: strikt UTF-8, ohne BOM, mit LF als
     * einzigem Zeilenende.
     *
     * @throws IllegalStateException wenn die Datei fehlt, kein gueltiges UTF-8 ist oder mit einem BOM beginnt (die
     *                               Meldung nennt nur den Dateinamen)
     */
    public static String korpusText(String dateiname) {
        byte[] bytes;
        try (InputStream in = SynthetischeGeheimnisse.class.getResourceAsStream(KORPUS_PFAD + dateiname)) {
            if (in == null) {
                throw new IllegalStateException("Korpusdatei fehlt: " + dateiname);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Korpusdatei nicht lesbar: " + dateiname);
        }
        return dekodiere(bytes, dateiname);
    }

    /** Dekodiert Bytes strikt als UTF-8 ohne BOM und wandelt CR LF und CR in LF. */
    public static String dekodiere(byte[] bytes, String dateiname) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("Korpusdatei ist kein gueltiges UTF-8: " + dateiname);
        }
        if (!text.isEmpty() && text.charAt(0) == (char) 0xFEFF) {
            throw new IllegalStateException("Korpusdatei beginnt mit einem BOM: " + dateiname);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Die Faelle einer Korpusdatei: eine Zeile je Fall; Leerzeilen und Zeilen, die mit {@code #} beginnen
     * (Kommentar- und Kategoriezeilen), zaehlen nicht; die zwei Zeichen {@code \n} und {@code \t} stehen fuer
     * Zeilenumbruch und Tabulator und werden ersetzt.
     */
    public static List<String> korpusZeilen(String dateiname) {
        return faelleAusText(korpusText(dateiname));
    }

    /** Wie {@link #korpusZeilen(String)} fuer einen schon gelesenen Text (nur LF als Zeilenende). */
    public static List<String> faelleAusText(String text) {
        List<String> faelle = new ArrayList<>();
        for (String zeile : text.split("\n", -1)) {
            if (zeile.isEmpty() || zeile.charAt(0) == '#') {
                continue;
            }
            faelle.add(zeile.replace("\\n", "\n").replace("\\t", "\t"));
        }
        return faelle;
    }
}
