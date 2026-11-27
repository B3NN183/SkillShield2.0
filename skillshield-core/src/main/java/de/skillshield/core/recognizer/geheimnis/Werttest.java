package de.skillshield.core.recognizer.geheimnis;

import java.util.List;

/**
 * Wert-Test der Geheimnis-Erkenner (Anforderung D-8, Plan Kapitel 1.4): Fehlalarmschutz fuer alle Kontextregeln. Ein
 * Wert ist nur dann ein Geheimnis, wenn alle Bedingungen gelten:
 * <ul>
 * <li><b>W1 Laenge</b> 6 bis 4 096 Zeichen (Codepoints), in Anfuehrungszeichen 6 bis 256.</li>
 * <li><b>W2 Zeichenmischung:</b> mindestens ein Buchstabe und (mindestens eine Ziffer oder ein Zeichen aus
 * {@link #SONDERZEICHEN}, das nicht das letzte Zeichen des Werts ist). Reine Buchstabenwoerter, reine Zahlen und
 * Woerter mit nur einem Ausrufezeichen am Ende sind kein Wert; {@code - _ . ,} zaehlen nicht als Sonderzeichen.</li>
 * <li><b>W3 Keine Versions-, Ticket- und Zahlwortform:</b> nicht (a) eine Versionsform ({@code 4.2.1}, {@code v4.2},
 * {@code 2.0-rc1}), nicht (b) eine Ticket- oder Aenderungsnummer (2 bis 6 Grossbuchstaben, optional {@code -},
 * mindestens drei Ziffern), nicht (c) ein Zahlwort-Verbund (Ziffern, {@code -}, nur Kleinbuchstaben; der
 * Kleinbuchstabenteil mit {@link Character#isLowerCase(int)}, Umlaute und Eszett eingeschlossen), nicht (d) ein
 * Fachbegriff aus {@link #FACHBEGRIFFE}, nicht (e) ein ISO-Zeitstempel ({@code 2026-03-12T10:15:00Z}; Plan
 * Kapitel 11 Punkt 4). Formen (a), (b), (d) und (e) nur ASCII.</li>
 * <li><b>W4 Keine Pfad- oder Adressform:</b> nicht mit {@code /}, {@code \}, {@code ~}, {@code ./}, {@code ../} oder
 * {@code X:\} beginnend und kein {@code ://} enthaltend.</li>
 * <li><b>W5 Keine Maske, Variable oder Vorlage:</b> nicht mit {@code [[}, {@code $}, {@code %}, einer
 * geschweiften oder spitzen oeffnenden Klammer oder {@code [} beginnend und nicht nur ein einziges wiederholtes
 * Zeichen.</li>
 * <li><b>W6</b> zusaetzlich fuer die schwaecheren Verbinder ({@link Verbinder#SCHWACH}): Laenge mindestens 8 und
 * mindestens drei der vier Klassen Grossbuchstabe, Kleinbuchstabe, Ziffer, Sonderzeichen; als Sonderzeichen zaehlt
 * dieselbe Liste wie in W2 (Plan Kapitel 11 Punkt 3), das letzte Zeichen des Werts wie dort nicht.</li>
 * <li><b>Schema</b> ({@link Verbinder#SCHEMA}, T-6) fuer {@code Bearer}, {@code Authorization} und die Schema-Formen
 * {@code Bearer}, {@code Basic}, {@code Token} hinter {@code Authorization}: W1 bis W5 und Laenge mindestens 16, ohne
 * die Klassenregel von W6 (D-8 W6, D-11).</li>
 * </ul>
 * Die Klasse liest den Wert in einem Durchlauf (Zaehler je Klasse); sie hat keinen Zustand und gibt keinen Wert
 * aus. Sie wirft keine Ausnahme mit Inhalt.
 */
final class Werttest {

    /** Welche Strenge der Verbinder verlangt (D-8 W6). */
    enum Verbinder {
        /** K1 ({@code :} oder {@code =}) in derselben Zeile: W1 bis W5. */
        STARK,
        /** Schwaecherer Verbinder, auch der Zeilenumbruch nach K1: zusaetzlich W6. */
        SCHWACH,
        /**
         * Schema-Formen und Kontextwoerter {@code Bearer} und {@code Authorization} (D-8 W6 letzter Satz, D-11, T-6):
         * W1 bis W5 und Laenge mindestens {@value Werttest#MIN_LAENGE_SCHEMA}, ohne die Klassenregel von W6.
         */
        SCHEMA
    }

    /** Kleinste Laenge eines Werts (W1). */
    static final int MIN_LAENGE = 6;
    /** Groesste Laenge eines Werts ohne Anfuehrungszeichen (W1). */
    static final int MAX_LAENGE = 4096;
    /** Groesste Laenge eines Werts in Anfuehrungszeichen (W1). */
    static final int MAX_LAENGE_ANFUEHRUNG = 256;
    /** Kleinste Laenge fuer die schwaecheren Verbinder (W6). */
    static final int MIN_LAENGE_SCHWACH = 8;
    /** Kleinste Laenge fuer die Schema-Formen und die Kontextwoerter Bearer und Authorization (D-8 W6, D-11). */
    static final int MIN_LAENGE_SCHEMA = 16;
    /** Kleinste Laenge fuer die Konfidenz 0,95 (D-9). */
    static final int MIN_LAENGE_SICHER = 8;

    /**
     * Sonderzeichen von W2 und W6 (geschlossene, pflegbare Liste, D-8): {@code ! ? $ % & = * + # ~ @ ^ { } | < >}
     * sowie der Paragraph (U+00A7) und das Euro-Zeichen (U+20AC).
     */
    private static final String SONDERZEICHEN = "!?$%&=*+#~@^{}|<>" + (char) 0xA7 + (char) 0x20AC;

    /**
     * Fachbegriffe, die nie ein Wert sind (geschlossene, pflegbare Liste, D-8 W3 (d)); Kleinbuchstaben, Vergleich
     * ohne Beachtung von Gross/klein.
     */
    static final List<String> FACHBEGRIFFE = List.of("md5", "sha1", "sha-1", "sha256", "sha-256", "sha384",
            "sha-384", "sha512", "sha-512", "aes128", "aes-128", "aes256", "aes-256", "rsa2048", "rsa-2048",
            "rsa4096", "rsa-4096", "utf-8", "utf8", "tls1.2", "tls1.3", "tls-1.2", "tls-1.3", "ntlmv2", "pbkdf2",
            "base64", "argon2id", "windows10", "windows11", "server2019", "server2022", "office365");

    private Werttest() {
    }

    /** Ob das Zeichen zur Liste der Sonderzeichen gehoert. */
    static boolean istSonderzeichen(int cp) {
        return cp < 0x10000 && SONDERZEICHEN.indexOf(cp) >= 0;
    }

    /**
     * Prueft {@code text[start, end)} gegen W1 bis W5, bei {@link Verbinder#SCHWACH} zusaetzlich W6.
     *
     * @param text         der Text
     * @param start        Beginn des Werts, inklusive
     * @param end          Ende des Werts, exklusive
     * @param v            die Strenge des Verbinders
     * @param inAnfuehrung ob der Wert in Anfuehrungszeichen stand (Obergrenze 256 statt 4 096)
     * @return {@code true}, wenn der Wert alle Bedingungen erfuellt
     */
    static boolean besteht(String text, int start, int end, Verbinder v, boolean inAnfuehrung) {
        if (start < 0 || end > text.length() || start >= end) {
            return false;
        }
        int laenge = text.codePointCount(start, end);
        int max = inAnfuehrung ? MAX_LAENGE_ANFUEHRUNG : MAX_LAENGE;
        if (laenge < MIN_LAENGE || laenge > max) {
            return false;
        }
        int buchstaben = 0;
        int gross = 0;
        int klein = 0;
        int ziffern = 0;
        int sonder = 0;
        int ersterCp = text.codePointAt(start);
        boolean einZeichen = true;
        int letzterCp = ersterCp;
        for (int i = start; i < end; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp != ersterCp) {
                einZeichen = false;
            }
            letzterCp = cp;
            if (GeheimnisZeichen.istBuchstabe(cp)) {
                buchstaben++;
            }
            if (Character.isUpperCase(cp)) {
                gross++;
            }
            if (Character.isLowerCase(cp)) {
                klein++;
            }
            if (GeheimnisZeichen.istZiffer(cp)) {
                ziffern++;
            }
            if (istSonderzeichen(cp)) {
                sonder++;
            }
        }
        if (istSonderzeichen(letzterCp)) {
            sonder--;
        }
        if (buchstaben == 0 || (ziffern == 0 && sonder == 0)) {
            return false;
        }
        String wert = text.substring(start, end);
        if (!istPfadOderAdresse(wert) && !istMaske(wert, einZeichen) && !istFachform(wert)) {
            if (v == Verbinder.SCHEMA) {
                return laenge >= MIN_LAENGE_SCHEMA;
            }
            return v != Verbinder.SCHWACH || schwachBesteht(laenge, gross, klein, ziffern, sonder);
        }
        return false;
    }

    private static boolean schwachBesteht(int laenge, int gross, int klein, int ziffern, int sonder) {
        if (laenge < MIN_LAENGE_SCHWACH) {
            return false;
        }
        int klassen = (gross > 0 ? 1 : 0) + (klein > 0 ? 1 : 0) + (ziffern > 0 ? 1 : 0) + (sonder > 0 ? 1 : 0);
        return klassen >= 3;
    }

    // ------------------------------------------------------------------ W4

    private static boolean istPfadOderAdresse(String w) {
        char c = w.charAt(0);
        if (c == '/' || c == '\\' || c == '~' || w.startsWith("./") || w.startsWith("../")) {
            return true;
        }
        if (w.length() >= 3 && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) && w.charAt(1) == ':'
                && w.charAt(2) == '\\') {
            return true;
        }
        return w.contains("://");
    }

    // ------------------------------------------------------------------ W5

    private static boolean istMaske(String w, boolean einZeichen) {
        char c = w.charAt(0);
        return c == '$' || c == '%' || c == '{' || c == '<' || c == '[' || einZeichen;
    }

    // ------------------------------------------------------------------ W3

    private static boolean istFachform(String w) {
        return istVersion(w) || istTicketnummer(w) || istZahlwort(w) || GeheimnisZeichen.inListe(w, FACHBEGRIFFE)
                || istZeitstempel(w);
    }

    /** W3 (a): optional {@code v}/{@code V}, mindestens zwei Ziffernbloecke mit Punkt, hoechstens ein {@code -Rest}. */
    static boolean istVersion(String w) {
        int n = w.length();
        int i = 0;
        if (n > 0 && (w.charAt(0) == 'v' || w.charAt(0) == 'V')) {
            i++;
        }
        int bloecke = 0;
        while (true) {
            int s = i;
            while (i < n && GeheimnisZeichen.istAsciiZiffer(w.charAt(i))) {
                i++;
            }
            if (i == s) {
                return false;
            }
            bloecke++;
            if (i < n && w.charAt(i) == '.') {
                i++;
            } else {
                break;
            }
        }
        if (bloecke < 2) {
            return false;
        }
        if (i == n) {
            return true;
        }
        if (w.charAt(i) != '-' || i + 1 >= n) {
            return false;
        }
        for (int k = i + 1; k < n; k++) {
            if (!GeheimnisZeichen.istAsciiBuchstabeOderZiffer(w.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    /** W3 (b): 2 bis 6 ASCII-Grossbuchstaben, optional {@code -}, mindestens drei ASCII-Ziffern, sonst nichts. */
    static boolean istTicketnummer(String w) {
        int n = w.length();
        int i = 0;
        while (i < n && GeheimnisZeichen.istAsciiGross(w.charAt(i))) {
            i++;
        }
        if (i < 2 || i > 6) {
            return false;
        }
        if (i < n && w.charAt(i) == '-') {
            i++;
        }
        if (n - i < 3) {
            return false;
        }
        for (int k = i; k < n; k++) {
            if (!GeheimnisZeichen.istAsciiZiffer(w.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    /** W3 (c): ASCII-Ziffern, {@code -}, nur Kleinbuchstaben ({@link Character#isLowerCase(int)}). */
    static boolean istZahlwort(String w) {
        int n = w.length();
        int i = 0;
        while (i < n && GeheimnisZeichen.istAsciiZiffer(w.charAt(i))) {
            i++;
        }
        if (i == 0 || i >= n - 1 || w.charAt(i) != '-') {
            return false;
        }
        for (int k = i + 1; k < n; ) {
            int cp = w.codePointAt(k);
            if (!Character.isLowerCase(cp)) {
                return false;
            }
            k += Character.charCount(cp);
        }
        return true;
    }

    /**
     * W3 (e): ISO-Zeitstempel {@code JJJJ-MM-TTThh:mm}, optional {@code :ss}, Bruchteil ({@code .} oder {@code ,} und
     * Ziffern), optional {@code Z} oder {@code +hh}/{@code -hh}/{@code +hh:mm}/{@code -hh:mm}; nur ASCII.
     */
    static boolean istZeitstempel(String w) {
        int n = w.length();
        if (n < 16 || !ziffern(w, 0, 4) || w.charAt(4) != '-' || !ziffern(w, 5, 7) || w.charAt(7) != '-'
                || !ziffern(w, 8, 10) || w.charAt(10) != 'T' || !ziffern(w, 11, 13) || w.charAt(13) != ':'
                || !ziffern(w, 14, 16)) {
            return false;
        }
        int i = 16;
        if (i < n && w.charAt(i) == ':') {
            if (!(ziffern(w, i + 1, i + 3))) {
                return false;
            }
            i += 3;
            if (i < n && (w.charAt(i) == '.' || w.charAt(i) == ',')) {
                int s = i + 1;
                int k = s;
                while (k < n && GeheimnisZeichen.istAsciiZiffer(w.charAt(k))) {
                    k++;
                }
                if (k == s) {
                    return false;
                }
                i = k;
            }
        }
        if (i == n) {
            return true;
        }
        if (w.charAt(i) == 'Z') {
            return i + 1 == n;
        }
        if (w.charAt(i) == '+' || w.charAt(i) == '-') {
            if (!ziffern(w, i + 1, i + 3)) {
                return false;
            }
            i += 3;
            if (i == n) {
                return true;
            }
            return w.charAt(i) == ':' && ziffern(w, i + 1, i + 3) && i + 3 == n;
        }
        return false;
    }

    /** Ob {@code w[von, bis)} vollstaendig aus ASCII-Ziffern besteht (und im Bereich liegt). */
    private static boolean ziffern(String w, int von, int bis) {
        if (bis > w.length()) {
            return false;
        }
        for (int i = von; i < bis; i++) {
            if (!GeheimnisZeichen.istAsciiZiffer(w.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
