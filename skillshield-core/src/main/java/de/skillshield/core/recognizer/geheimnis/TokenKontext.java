package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.geheimnis.KontextRegel.Klasse;
import java.util.List;

/**
 * Kontextwoerter und Schema-Formen fuer Tokens (Anforderung D-5, D-11, D-8 W6 letzter Satz; Plan Kapitel 1.2, 1.3):
 * die Art {@link Art#TOKEN} der {@link KontextRegel}. Alles ist paketintern und ohne Zustand; die Klasse liest nur.
 *
 * <p><b>Kontextwoerter (D-5).</b> Ein Wortkoerper (Folge aus Buchstaben, Ziffern, {@code _}, {@code -}; Gross/klein
 * gleichgueltig, nur ASCII verglichen) zaehlt, wenn er auf einen der Staemme {@link #STAEMME_TOKEN} endet; beim
 * Vergleich werden bis zu drei {@code _} oder {@code -} uebersprungen ({@code access_token}, {@code accessToken},
 * {@code X-API-Key}, {@code client_secret}), nicht aber am Ende des Koerpers. Dahinter darf kein weiteres Zeichen des
 * Koerpers stehen ({@code Tokenlaenge}, {@code Token-Ablauf}, {@code Secretary} sind kein Kontextwort). Dazu kommt die
 * Schreibweise {@code API Key} (der Koerper {@code Key} hinter genau einem Leerraumzeichen und einem Koerper, der auf
 * {@code api} endet) und die ganzen Woerter {@code Bearer} und {@code Authorization} (D-11). Vom Passwort
 * unterscheiden sich Tokens nur bei {@code Bearer} und {@code Authorization}: dort gilt die Strenge
 * {@link Werttest.Verbinder#SCHEMA} (mindestens 16 Zeichen, ohne die Klassenregel von W6, kein Aufruf und keine Form
 * {@code name="..."}). Alle anderen Kontextwoerter pruefen genau wie beim Passwort (W1 bis W5 hinter K1, W6 hinter
 * K2 bis K4 und nach dem Zeilenumbruch).
 *
 * <p><b>Schema-Formen (D-11).</b>
 * <ul>
 * <li>{@code Authorization: Bearer Wert}, {@code Authorization: Basic Wert}, {@code Authorization: Token Wert}: der
 * Fund ist nur der Wert, das Schema-Wort bleibt (Konfidenz 0,95). Steht hinter {@code Authorization} und K1 kein
 * Schema-Wort, ist das erste Wort der Wert ({@code Authorization: Wert}). Beide Kontextwoerter ({@code Authorization}
 * und {@code Bearer}) ergeben denselben Bereich und damit genau einen Fund, weil die Auswertung hinter dem Wert
 * weitergeht.</li>
 * <li>{@code Bearer Wert} ohne Verbinder: Konfidenz 0,80 (Plan Kapitel 11 Punkt 5). Wird kein Wert gefunden, geht die
 * Suche unmittelbar hinter {@code Bearer} weiter, damit ein folgendes Kontextwort ({@code Bearer Token: Wert}) nicht
 * verschluckt wird.</li>
 * </ul>
 *
 * <p><b>Linearitaet.</b> Der Vergleich der Staemme liest hoechstens 10 Zeichen plus drei Trenner rueckwaerts; die
 * Pruefung auf {@code API Key} liest vier Zeichen davor; das Schema-Wort wird hoechstens 7 Zeichen weit gelesen.
 * Alles Weitere liest {@link KontextRegel} mit den dort beschriebenen festen Schranken. Keine Meldung und keine
 * Ausnahme nennt Text oder Wert.
 */
final class TokenKontext {

    /** Kein Kontextwort. */
    static final int KEIN = 0;
    /** Kontextwort aus {@link #STAEMME_TOKEN} oder {@code API Key}. */
    static final int STAMM = 1;
    /** Das ganze Wort {@code Bearer}. */
    static final int BEARER = 2;
    /** Das ganze Wort {@code Authorization}. */
    static final int AUTHORIZATION = 3;

    /**
     * Staemme der Token-Kontextwoerter (geschlossene, pflegbare Liste, D-5); Kleinbuchstaben, ohne {@code _} und
     * {@code -}. Verglichen wird ohne diese Trenner.
     */
    private static final List<String> STAEMME_TOKEN = List.of("token", "apikey", "secret", "secretkey", "accesskey",
            "privatekey");

    /** Schema-Woerter hinter {@code Authorization} (geschlossene, pflegbare Liste, D-11); Kleinbuchstaben. */
    private static final List<String> SCHEMA_WOERTER = List.of("bearer", "basic", "token");

    /** Hoechstzahl der uebersprungenen {@code _} und {@code -} beim Vergleich eines Stamms (D-5). */
    private static final int MAX_TRENNER = 3;
    /** Hoechstzahl gleicher {@code _} um den Koerper (Auszeichnung, wie in {@code KontextRegel}). */
    private static final int MAX_UNTERSTRICH = 3;
    /** Laenge des laengsten Schema-Worts ({@code bearer}). */
    private static final int MAX_SCHEMA_WORT = 6;

    private TokenKontext() {
    }

    /**
     * Art des Kontextworts, das der Wortkoerper {@code text[von, ende)} bildet: {@link #KEIN}, {@link #STAMM},
     * {@link #BEARER} oder {@link #AUTHORIZATION}. Umschliesst dieselbe Folge von ein bis drei {@code _} den Koerper
     * ({@code __Token__}), zaehlt der Inhalt. {@code Bearer} und {@code Authorization} zaehlen nur mit
     * {@code mitSchema} (im zweiten Durchgang fuer K2 bis K4 nicht, wie {@code pw} und {@code pwd} beim Passwort).
     */
    static int art(String text, int von, int ende, boolean mitSchema) {
        int a = 0;
        while (von + a < ende && a <= MAX_UNTERSTRICH && text.charAt(von + a) == '_') {
            a++;
        }
        int b = 0;
        while (ende - 1 - b >= von && b <= MAX_UNTERSTRICH && text.charAt(ende - 1 - b) == '_') {
            b++;
        }
        int s = von;
        int e = ende;
        if (a >= 1 && a <= MAX_UNTERSTRICH && a == b && von + a < ende - b) {
            s = von + a;
            e = ende - b;
        }
        for (String stamm : STAEMME_TOKEN) {
            if (endetOhneTrenner(text, s, e, stamm)) {
                return STAMM;
            }
        }
        if (istApiKey(text, s, e)) {
            return STAMM;
        }
        if (mitSchema) {
            if (GeheimnisZeichen.gleichKlein(text, s, e, "bearer")) {
                return BEARER;
            }
            if (GeheimnisZeichen.gleichKlein(text, s, e, "authorization")) {
                return AUTHORIZATION;
            }
        }
        return KEIN;
    }

    /**
     * Ob {@code text[s, e)} auf den Stamm endet, wenn bis zu drei {@code _} oder {@code -} uebersprungen werden. Das
     * letzte Zeichen des Koerpers ist nie ein uebersprungener Trenner.
     */
    private static boolean endetOhneTrenner(String text, int s, int e, String stamm) {
        int i = e - 1;
        int k = stamm.length() - 1;
        int trenner = 0;
        while (k >= 0) {
            if (i < s) {
                return false;
            }
            char c = text.charAt(i);
            if (c == '_' || c == '-') {
                trenner++;
                if (k == stamm.length() - 1 || trenner > MAX_TRENNER) {
                    return false;
                }
            } else if (GeheimnisZeichen.asciiKlein(c) != stamm.charAt(k)) {
                return false;
            } else {
                k--;
            }
            i--;
        }
        return true;
    }

    /**
     * Ob {@code text[s, e)} das Wort {@code Key} ist, vor dem genau ein Leerraumzeichen und ein Koerper steht, der auf
     * {@code api} endet ({@code API Key}).
     */
    private static boolean istApiKey(String text, int s, int e) {
        return s >= 4 && GeheimnisZeichen.gleichKlein(text, s, e, "key")
                && GeheimnisZeichen.istLeerraum(text.charAt(s - 1))
                && GeheimnisZeichen.endetMit(text, s - 1, "api");
    }

    /**
     * Wertet hinter dem Kontextwort {@code text[von, ende)} aus; liefert die Position, ab der weitergesucht wird
     * (mindestens {@code ende}; {@code ende}, wenn das Wort kein Kontextwort ist).
     */
    static int auswerten(String text, int von, int ende, List<Finding> ziel) {
        int art = art(text, von, ende, true);
        if (art == KEIN) {
            return ende;
        }
        int nachVerbinder = KontextRegel.verbinderK1(text, ende);
        if (nachVerbinder < 0 && art == AUTHORIZATION) {
            nachVerbinder = KontextRegel.verbinderAb(text, ueberspringeAnfuehrung(text, ende));
        }
        if (nachVerbinder < 0) {
            return art == BEARER ? bearerOhneVerbinder(text, ende, ziel) : ende;
        }
        if (art == STAMM) {
            return KontextRegel.wert(text, nachVerbinder, Art.TOKEN, ziel, Klasse.K1);
        }
        return art == AUTHORIZATION ? authorization(text, nachVerbinder, ziel)
                : KontextRegel.wert(text, nachVerbinder, Art.TOKEN, ziel, Klasse.SCHEMA);
    }

    /**
     * {@code Authorization} mit K1: Steht dahinter in derselben Zeile ein Schema-Wort und danach ein Wort, ist dieses
     * der Wert; sonst gilt das erste Wort hinter dem Verbinder. Fehlt hinter dem Schema-Wort ein Wert, geht die Suche
     * hinter dem Verbinder weiter (ein folgendes Kontextwort wie {@code Authorization: Token: Wert} bleibt wirksam).
     * Steht das Schema-Wort in Anfuehrungszeichen ({@code "Authorization": "Bearer Wert"}), wird das oeffnende
     * Anfuehrungszeichen uebersprungen, auch ein verdoppeltes ({@code ""}, CSV) oder maskiertes ({@code \"}); der Wert
     * ist das Wort hinter dem Schema-Wort, das schliessende Anfuehrungszeichen faellt bei der Kuerzung am Ende weg
     * (Entscheidung 3 nach der Pruefung von T-6).
     */
    private static int authorization(String text, int nachVerbinder, List<Finding> ziel) {
        int n = text.length();
        int w0 = KontextRegel.ueberspringeLeerraum(text, nachVerbinder);
        w0 = ueberspringeAnfuehrung(text, w0);
        int w1 = KontextRegel.wortEnde(text, w0, MAX_SCHEMA_WORT + 1);
        if (w1 > w0 && istSchemaWort(text, w0, w1)) {
            int z = KontextRegel.ueberspringeLeerraum(text, w1);
            if (z > w1 && z < n && !GeheimnisZeichen.istZeilenumbruch(text.charAt(z))) {
                return KontextRegel.wert(text, z, Art.TOKEN, ziel, Klasse.SCHEMA);
            }
            return nachVerbinder;
        }
        return KontextRegel.wert(text, nachVerbinder, Art.TOKEN, ziel, Klasse.SCHEMA);
    }

    /**
     * Position hinter einem oeffnenden Anfuehrungszeichen vor dem Schema-Wort: {@code "}, {@code ""} (verdoppelt, CSV)
     * oder {@code \"} (maskiert, JSON im Text); {@code '} wie {@code "}. Ohne Anfuehrungszeichen {@code p}.
     */
    private static int ueberspringeAnfuehrung(String text, int p) {
        int n = text.length();
        int q = p;
        if (q + 1 < n && text.charAt(q) == '\\') {
            q++;
        }
        if (q >= n || (text.charAt(q) != '"' && text.charAt(q) != '\'')) {
            return p;
        }
        char c = text.charAt(q);
        return q + 1 < n && text.charAt(q + 1) == c ? q + 2 : q + 1;
    }

    /**
     * {@code Bearer Wert} ohne Verbinder: das Wort hinter {@code Bearer} in derselben Zeile ist der Wert (0,80). Ohne
     * Fund geht es hinter {@code Bearer} weiter.
     */
    private static int bearerOhneVerbinder(String text, int ende, List<Finding> ziel) {
        int z = KontextRegel.ueberspringeLeerraum(text, ende);
        if (z == ende || z >= text.length() || GeheimnisZeichen.istZeilenumbruch(text.charAt(z))) {
            return ende;
        }
        int vorher = ziel.size();
        int weiter = KontextRegel.wert(text, z, Art.TOKEN, ziel, Klasse.SCHEMA_OHNE);
        return ziel.size() > vorher ? weiter : ende;
    }

    private static boolean istSchemaWort(String text, int von, int bis) {
        for (String wort : SCHEMA_WOERTER) {
            if (GeheimnisZeichen.gleichKlein(text, von, bis, wort)) {
                return true;
            }
        }
        return false;
    }
}
