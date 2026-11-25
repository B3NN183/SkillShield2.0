package de.skillshield.core;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Positiv-Generator (Plan geheimnisse-erkennung, T-2; Anforderung AK-38): 100 000 zufaellige Werte (fester Seed;
 * Laenge 6 bis 40; Zeichen aus allen Klassen, die W1 bis W6 erfuellen) hinter einem zufaelligen Kontextwort aus D-4 und
 * D-5 und dem Verbinder K1 (Doppelpunkt oder Gleichheitszeichen, auch mit Anfuehrungszeichen). Der Wert ist je Text
 * der erwartete Fund; Typ nach Kontextwort (PASSWORT oder TOKEN), Quelle {@code passwort-kontext} bzw.
 * {@code token-kontext}, Konfidenz 0,95 (ab acht Zeichen) oder 0,80 (sechs oder sieben Zeichen) nach D-9.
 *
 * <p><b>Vorbedingung je Text:</b> {@link TextScannerStandGeheimnis#ALT} (der Scanner vor dem Feature) liefert fuer den
 * ganzen Text keinen Fund. Sonst wird der Wert verworfen und gezaehlt ({@link Lauf#verworfenAlt()}); der Text mit dem
 * naechsten Wert nimmt seinen Platz ein. Damit gibt es im Lauf keinen Konfliktfall nach T-1 Regel 3.
 *
 * <p><b>Gegenstueck:</b> {@link #verletzend(int)} liefert 100 000 Texte, deren Wert W1 oder W2 verletzt (nur
 * Buchstaben, nur Ziffern, hoechstens fuenf Zeichen, Wort mit einem Ausrufezeichen am Ende); sie liefern 0 Funde.
 *
 * <p>Der Generator ist deterministisch (feste Seeds) und enthaelt keine festen Geheimnisse; die Werte sind
 * Zufallsfolgen ohne Bezug zu einem Anbieter oder einer Person. Eingefroren ab Abnahme T-2 samt Seeds und
 * Reihenfolge der Zufallsaufrufe.
 */
final class GeheimnisPositivGenerator {

    /** Fester Seed der gueltigen Werte. */
    static final long SEED = 20261126L;

    /** Fester Seed der verletzenden Werte. */
    static final long SEED_VERLETZEND = 20261127L;

    /** Zahl der Texte je Menge (AK-38). */
    static final int ANZAHL = 100_000;

    /** Kleinste und groesste Laenge eines gueltigen Werts (AK-38). */
    static final int MIN_LAENGE = 6;
    static final int MAX_LAENGE = 40;

    static final String QUELLE_PASSWORT = "passwort-kontext";
    static final String QUELLE_TOKEN = "token-kontext";
    static final FindingType PASSWORT = new FindingType("PASSWORT");
    static final FindingType TOKEN = new FindingType("TOKEN");

    /** Kontextwoerter fuer PASSWORT (D-4), alle mit K1 zulaessig. */
    static final List<String> PASSWORT_KONTEXTE = List.of(
            "Passwort", "passwort", "KENNWORT", "Kennwort", "password", "Password", "passwd", "Passphrase",
            "Initialpasswort", "Einmalkennwort", "Adminpasswort", "Root-Passwort", "db-passwort", "PW", "pw", "Pwd",
            "pwd", "DB_PASSWORD", "dbPassword", "PASSWD", "PassPhrase");

    /** Kontextwoerter fuer TOKEN (D-5), alle mit K1 zulaessig (ohne die Schema-Woerter Bearer und Authorization). */
    static final List<String> TOKEN_KONTEXTE = List.of(
            "Token", "access_token", "accessToken", "refresh_token", "idToken", "API-Key", "apikey", "api_key",
            "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret", "secretkey", "accesskey",
            "privatekey", "TOKEN", "secret");

    /** Eine Vorlage: Text vor dem Kontextwort, zwischen Kontextwort und Wert, hinter dem Wert. */
    record Stil(String vor, String mitte, String nach) {
    }

    /** Die Verbinder K1 (D-6) mit und ohne Anfuehrungszeichen (D-7). */
    static final List<Stil> STILE = List.of(
            new Stil("", ": ", ""), new Stil("", " : ", ""), new Stil("", ":", ""), new Stil("", "=", ""),
            new Stil("", " = ", ""), new Stil("", " =", ""), new Stil("", ":  ", ""),
            new Stil("\"", "\": \"", "\""), new Stil("", ": '", "'"), new Stil("", " = \"", "\""),
            new Stil("", ": `", "`"), new Stil("", ": " + (char) 0x201E, String.valueOf((char) 0x201C)));

    private static final List<String> VORSAETZE = List.of("", "Hinweis: ", "Benutzer: svc_x, ",
            "Laut Ticket gilt: ", "Zeile 5: ");

    private static final List<String> NACHSAETZE = List.of("", " bitte " + (char) 0xE4 + "ndern", " (siehe Tresor)",
            " und danach neu starten");

    private static final String BUCHSTABEN_KLEIN = "abcdefghijklmnopqrstuvwxyz" + (char) 0xE4 + (char) 0xF6
            + (char) 0xFC + (char) 0xDF;
    private static final String BUCHSTABEN_GROSS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + (char) 0xC4 + (char) 0xD6
            + (char) 0xDC;
    private static final String ZIFFERN = "0123456789";
    /** Sonderzeichen innerhalb eines Werts (Liste von W2 ohne Klammern). */
    private static final String SONDER_INNEN = "!?" + (char) 0xA7 + "$%&=*+#~@^|<>" + (char) 0x20AC;
    /** Sonderzeichen am Anfang (ohne die Zeichen, mit denen W5 beginnende Werte ausschliesst, ohne Verbinder). */
    private static final String SONDER_ANFANG = "!" + (char) 0xA7 + "&*+#@^" + (char) 0x20AC;
    /** Sonderzeichen am Ende (ohne die Satzzeichen, die D-7 abtrennt). */
    private static final String SONDER_ENDE = "!" + (char) 0xA7 + "$%&=*+#~@^|>" + (char) 0x20AC;
    private static final String FUELL = "-_";

    private static final List<String> FACHBEGRIFFE = List.of("md5", "sha1", "sha-1", "sha256", "sha-256", "sha384",
            "sha-384", "sha512", "sha-512", "aes128", "aes-128", "aes256", "aes-256", "rsa2048", "rsa-2048",
            "rsa4096", "rsa-4096", "utf-8", "utf8", "tls1.2", "tls1.3", "tls-1.2", "tls-1.3", "ntlmv2", "pbkdf2",
            "base64", "argon2id", "windows10", "windows11", "server2019", "server2022", "office365");

    /** Ein Text mit genau einem erwarteten Fund ueber {@code [start, end)}. */
    record Text(String text, FindingType typ, String quelle, int start, int end, double konfidenz) {

        /** Nur Typ, Bereich und Laenge, nie Text oder Wert (Plan Kapitel 6.1). */
        @Override
        public String toString() {
            return "Text[" + typ.name() + "," + start + "-" + end + ",laenge=" + text.length() + "]";
        }

        Finding erwartet() {
            return new Finding(typ, text.substring(start, end), start, end, konfidenz, quelle);
        }
    }

    /**
     * Das Ergebnis des Laufs.
     *
     * @param texte         die Texte mit den erwarteten Funden
     * @param verworfenAlt  Zahl der Werte, bei denen {@code ALT} einen Fund lieferte (verworfen und ersetzt)
     * @param verworfenForm Zahl der Zufallswerte, die die eigene Pruefung {@link #istGueltig(String)} nicht bestanden
     * @param passwort      Zahl der Texte mit Typ PASSWORT
     * @param token         Zahl der Texte mit Typ TOKEN
     * @param kurz          Zahl der Werte mit sechs oder sieben Zeichen (Konfidenz 0,80)
     * @param jeLaenge      Zahl der Werte je Laenge (Index = Laenge)
     */
    record Lauf(List<Text> texte, int verworfenAlt, int verworfenForm, int passwort, int token, int kurz,
                int[] jeLaenge) {
    }

    private GeheimnisPositivGenerator() {
    }

    /** Erzeugt {@link #ANZAHL} gueltige Texte. */
    static Lauf lauf() {
        return lauf(SEED, ANZAHL);
    }

    /** Erzeugt {@code anzahl} gueltige Texte mit dem Seed {@code seed}. */
    static Lauf lauf(long seed, int anzahl) {
        Random r = new Random(seed);
        List<Text> texte = new ArrayList<>(anzahl);
        int verworfenAlt = 0;
        int verworfenForm = 0;
        int passwort = 0;
        int token = 0;
        int kurz = 0;
        int[] jeLaenge = new int[MAX_LAENGE + 1];
        while (texte.size() < anzahl) {
            boolean istToken = r.nextInt(3) == 0;
            List<String> kontexte = istToken ? TOKEN_KONTEXTE : PASSWORT_KONTEXTE;
            String kontext = kontexte.get(r.nextInt(kontexte.size()));
            Stil stil = STILE.get(r.nextInt(STILE.size()));
            String vor = VORSAETZE.get(r.nextInt(VORSAETZE.size()));
            String nach = NACHSAETZE.get(r.nextInt(NACHSAETZE.size()));
            String wert = zufallswert(r);
            if (!istGueltig(wert)) {
                verworfenForm++;
                continue;
            }
            String vorWert = vor + stil.vor() + kontext + stil.mitte();
            String text = vorWert + wert + stil.nach() + nach;
            if (!TextScannerStandGeheimnis.ALT.scan(text).isEmpty()) {
                verworfenAlt++;
                continue;
            }
            double konfidenz = wert.length() >= 8 ? 0.95 : 0.80;
            texte.add(new Text(text, istToken ? TOKEN : PASSWORT, istToken ? QUELLE_TOKEN : QUELLE_PASSWORT,
                    vorWert.length(), vorWert.length() + wert.length(), konfidenz));
            if (istToken) {
                token++;
            } else {
                passwort++;
            }
            if (wert.length() < 8) {
                kurz++;
            }
            jeLaenge[wert.length()]++;
        }
        return new Lauf(texte, verworfenAlt, verworfenForm, passwort, token, kurz, jeLaenge);
    }

    private static char zeichen(Random r, String menge) {
        return menge.charAt(r.nextInt(menge.length()));
    }

    /** Ein Zufallswert der Laenge 6 bis 40; die Pruefung {@link #istGueltig(String)} folgt beim Aufrufer. */
    private static String zufallswert(Random r) {
        int laenge = MIN_LAENGE + r.nextInt(MAX_LAENGE - MIN_LAENGE + 1);
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            int w = r.nextInt(100);
            if (i == 0) {
                sb.append(w < 55 ? zeichen(r, r.nextBoolean() ? BUCHSTABEN_KLEIN : BUCHSTABEN_GROSS)
                        : w < 85 ? zeichen(r, ZIFFERN) : zeichen(r, SONDER_ANFANG));
            } else if (i == laenge - 1) {
                sb.append(w < 50 ? zeichen(r, r.nextBoolean() ? BUCHSTABEN_KLEIN : BUCHSTABEN_GROSS)
                        : w < 80 ? zeichen(r, ZIFFERN) : zeichen(r, SONDER_ENDE));
            } else {
                sb.append(w < 30 ? zeichen(r, BUCHSTABEN_KLEIN) : w < 50 ? zeichen(r, BUCHSTABEN_GROSS)
                        : w < 75 ? zeichen(r, ZIFFERN) : w < 95 ? zeichen(r, SONDER_INNEN) : zeichen(r, FUELL));
            }
        }
        return sb.toString();
    }

    private static boolean istSonder(char c) {
        return SONDER_INNEN.indexOf(c) >= 0;
    }

    /**
     * Die eigene Pruefung der Wertregeln W1 bis W5 und der Forderungen an den Wert hinter K1, unabhaengig von jedem
     * Erkenner: Laenge 6 bis 40; mindestens ein Buchstabe und eine Ziffer oder ein Sonderzeichen, das nicht am Ende
     * steht; kein Leerraum; Anfang nicht {@code $ % { < [ / \ ~} und nicht {@code [[}; am Ende kein abtrennbares
     * Satzzeichen oder Anfuehrungszeichen; keine Versions-, Ticket-, Zahlwort- oder Fachbegriffsform; nicht ein
     * einziges wiederholtes Zeichen; keine Pfad- oder Adressform.
     */
    static boolean istGueltig(String wert) {
        int n = wert.length();
        if (n < MIN_LAENGE || n > MAX_LAENGE) {
            return false;
        }
        boolean buchstabe = false;
        boolean ziffernOderSonder = false;
        for (int i = 0; i < n; i++) {
            char c = wert.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                return false;
            }
            buchstabe |= Character.isLetter(c);
            ziffernOderSonder |= ZIFFERN.indexOf(c) >= 0 || (istSonder(c) && i < n - 1);
        }
        if (!buchstabe || !ziffernOderSonder) {
            return false;
        }
        char erstes = wert.charAt(0);
        char letztes = wert.charAt(n - 1);
        if ("${<[/\\~%".indexOf(erstes) >= 0 || wert.startsWith("[[")) {
            return false;
        }
        if (".,;:?)]}\"'`".indexOf(letztes) >= 0 || wert.indexOf("://") >= 0) {
            return false;
        }
        return !istEinZeichen(wert) && !istVersionTicketZahlwortOderFachbegriff(wert);
    }

    private static boolean istEinZeichen(String wert) {
        for (int i = 1; i < wert.length(); i++) {
            if (wert.charAt(i) != wert.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nurZiffern(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (ZIFFERN.indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean istVersionTicketZahlwortOderFachbegriff(String wert) {
        if (FACHBEGRIFFE.contains(wert.toLowerCase(Locale.ROOT))) {
            return true;
        }
        // Ticketform: 2 bis 6 Grossbuchstaben, optional Bindestrich, mindestens drei Ziffern, sonst nichts
        int i = 0;
        while (i < wert.length() && wert.charAt(i) >= 'A' && wert.charAt(i) <= 'Z') {
            i++;
        }
        if (i >= 2 && i <= 6) {
            int j = i < wert.length() && wert.charAt(i) == '-' ? i + 1 : i;
            String rest = wert.substring(j);
            if (rest.length() >= 3 && nurZiffern(rest)) {
                return true;
            }
        }
        // Zahlwort-Verbund: Ziffern, Bindestrich, nur Kleinbuchstaben
        int bindestrich = wert.indexOf('-');
        if (bindestrich > 0 && nurZiffern(wert.substring(0, bindestrich))) {
            String rest = wert.substring(bindestrich + 1);
            boolean klein = !rest.isEmpty();
            for (int k = 0; k < rest.length(); k++) {
                klein &= Character.isLowerCase(rest.charAt(k));
            }
            if (klein) {
                return true;
            }
        }
        // Versionsform: nur Ziffern und Punkte mit mindestens einem Punkt (Werte mit Buchstaben haben keinen Punkt)
        return wert.indexOf('.') >= 0 && wert.chars().allMatch(c -> c == '.' || c == 'v' || c == 'V'
                || ZIFFERN.indexOf(c) >= 0);
    }

    // ------------------------------------------------------------------ Gegenstueck: verletzende Werte

    /** Die Arten der verletzenden Werte. */
    enum Verletzung {
        NUR_BUCHSTABEN, NUR_ZIFFERN, HOECHSTENS_FUENF_ZEICHEN, WORT_MIT_AUSRUFEZEICHEN_AM_ENDE
    }

    /** Das Ergebnis des Gegenstuecks mit den Kennzahlen je Art. */
    record Verletzend(List<String> texte, int[] jeArt) {
    }

    /** Erzeugt {@link #ANZAHL} Texte, deren Wert W1 oder W2 verletzt. */
    static Verletzend verletzend() {
        return verletzend(ANZAHL);
    }

    /** Erzeugt {@code anzahl} Texte, deren Wert W1 oder W2 verletzt (Seed {@link #SEED_VERLETZEND}). */
    static Verletzend verletzend(int anzahl) {
        Random r = new Random(SEED_VERLETZEND);
        List<String> texte = new ArrayList<>(anzahl);
        int[] jeArt = new int[Verletzung.values().length];
        List<String> kontexte = new ArrayList<>(PASSWORT_KONTEXTE);
        kontexte.addAll(TOKEN_KONTEXTE);
        for (int i = 0; i < anzahl; i++) {
            Verletzung art = Verletzung.values()[r.nextInt(Verletzung.values().length)];
            String kontext = kontexte.get(r.nextInt(kontexte.size()));
            Stil stil = STILE.get(r.nextInt(STILE.size()));
            String vor = VORSAETZE.get(r.nextInt(VORSAETZE.size()));
            String nach = NACHSAETZE.get(r.nextInt(NACHSAETZE.size()));
            String wert = verletzenderWert(r, art);
            texte.add(vor + stil.vor() + kontext + stil.mitte() + wert + stil.nach() + nach);
            jeArt[art.ordinal()]++;
        }
        return new Verletzend(texte, jeArt);
    }

    private static String folge(Random r, String menge, int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            sb.append(zeichen(r, menge));
        }
        return sb.toString();
    }

    private static String verletzenderWert(Random r, Verletzung art) {
        switch (art) {
            case NUR_BUCHSTABEN:
                return folge(r, BUCHSTABEN_KLEIN + BUCHSTABEN_GROSS, MIN_LAENGE + r.nextInt(35));
            case NUR_ZIFFERN:
                return folge(r, ZIFFERN, 1 + r.nextInt(8));
            case HOECHSTENS_FUENF_ZEICHEN:
                return folge(r, BUCHSTABEN_KLEIN + BUCHSTABEN_GROSS + ZIFFERN + SONDER_ANFANG, 1 + r.nextInt(5));
            default:
                return folge(r, BUCHSTABEN_KLEIN + BUCHSTABEN_GROSS, 5 + r.nextInt(25)) + "!";
        }
    }
}
