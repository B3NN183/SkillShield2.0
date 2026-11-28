package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.geheimnis.Werttest.Verbinder;
import java.util.List;

/**
 * Kontextregel der Geheimnis-Erkenner (Anforderung D-4, D-6 bis D-9; Plan Kapitel 1.3): findet ein Kontextwort, den
 * Verbinder, den Wert und prueft ihn mit {@link Werttest}. Stand T-6: Verbinder K1 ({@code :} oder {@code =}) in
 * derselben Zeile und K1 mit einem Zeilenumbruch. Das ist der erste Durchgang des {@code PasswortRecognizer}; fuer
 * die Art {@link Art#PASSWORT} ist er seit T-4 unveraendert. Die schwaecheren Verbinder K2 bis K4 wertet der zweite
 * Durchgang {@link SchwacheVerbinder} aus (T-5, nutzt Kontextwort, Zwischenteil und Wertgrenzen dieser Klasse). Die
 * Art {@link Art#TOKEN} (T-6) nutzt dieselbe Regel mit den Kontextwoertern aus {@link TokenKontextStandT6}; die
 * Schema-Formen ({@code Bearer}, {@code Authorization}) lesen den Wert mit den Klassen {@link Klasse#SCHEMA} und
 * {@link Klasse#SCHEMA_OHNE}. Die Verbindungsregeln folgen in T-8.
 *
 * <p><b>Ablauf.</b> Ein Durchlauf liest jeden Wortkoerper (maximale Folge aus Buchstaben, Ziffern, kombinierenden
 * Zeichen, {@code _} und {@code -}) einmal vorwaerts. Erst an seinem Ende wird rueckwaerts verglichen (hoechstens
 * 10 Zeichen): endet der Koerper auf einen Stamm aus {@link #STAEMME_PASSWORT} (D-4), ist er ein Kontextwort;
 * {@code pw} und {@code pwd} zaehlen nur als ganzer Wortkoerper. Dahinter folgen in dieser Reihenfolge:
 * <ol>
 * <li>optional ein Auszeichnungszeichen ({@code *}, Gravis; ein bis drei in Folge, Entscheidung 6 vom 25.11.2026)
 * und optional ein gerades Anfuehrungszeichen ({@code "password":});</li>
 * <li>optional ein Zwischenteil: ein Wort aus {@link #ZWISCHENWOERTER} und danach ein bis drei Woerter (je ohne
 * Leerraum, ohne {@code :} und {@code =}, hoechstens 64 Zeichen, nicht ueber einen Zeilenumbruch); nach jedem Wort
 * wird auf einen Verbinder geprueft, der erste gefundene beendet die Suche;</li>
 * <li>der Verbinder K1 mit Leerraum davor und dahinter in derselben Zeile; dahinter ein Auszeichnungszeichen, wenn
 * Leerraum folgt ({@code **Passwort:** Wert});</li>
 * <li>nach K1 darf ein Zeilenumbruch samt Einrueckung folgen (Konfidenz 0,80, strenge Werttest-Klasse
 * {@link Verbinder#SCHWACH}); eine Leerzeile dazwischen ergibt keinen Fund;</li>
 * <li>der Wert (D-7): beginnt er mit einem oeffnenden Anfuehrungszeichen und folgt in derselben Zeile, hoechstens
 * 256 Zeichen weiter, das passende schliessende, ist der Inhalt der Wert (mit Leerzeichen); ein maskiertes
 * ({@code \"}, {@code \'}) oder verdoppeltes ({@code ""}, {@code ''}) gerades Anfuehrungszeichen gehoert zum
 * Inhalt (Entscheidung 1 vom 25.11.2026); sonst das erste Wort bis
 * zum Leerraum oder Zeilenende (hoechstens 4 096 Zeichen), ohne ein oeffnendes Zeichen ohne Partner; danach werden
 * wiederholt {@code . , ; : ?}, eine schliessende runde, eckige oder geschweifte Klammer nur bei Ueberschuss und
 * ein schliessendes Anfuehrungszeichen ohne oeffnendes im Wert entfernt; das {@code !} am Ende bleibt;</li>
 * <li>Auszeichnung am Wert (Entscheidung 6): beginnt der Wert mit einer Folge von zwei oder drei gleichen Zeichen
 * {@code *}, {@code _} oder Gravis und steht dieselbe Folge in derselben Zeile (hoechstens 256 Zeichen weiter)
 * noch einmal, wird die erste Folge uebersprungen; der Wert ist das erste Wort dahinter, und endet es auf dieselbe
 * Folge, endet der Fund vor ihr. Beide Folgen sind nicht Teil des Funds. Eine einzelne {@code *} oder {@code _}
 * und eine Folge ohne Gegenstueck werden nicht uebersprungen und bleiben Teil des Werts (Grenze, siehe
 * {@code PasswortRecognizer}).</li>
 * </ol>
 * Konfidenz (D-9): K1 gleiche Zeile mit mindestens 8 Zeichen 0,95, mit 6 oder 7 Zeichen 0,80; K1 mit Zeilenumbruch
 * 0,80. Quelle {@code passwort-kontext}.
 *
 * <p><b>Weitersuche.</b> Nach jedem ausgewerteten Wert (Fund oder nicht) wird hinter dem gelesenen Wertbereich
 * (Wort bzw. Anfuehrungspaar) weitergesucht; ohne Wert hinter dem Kontextwort bzw. dem Verbinder. Das erfuellt D-7
 * (mehrere Werte je Zeile) und haelt den Aufwand linear. Folge ist die Grenze G-19: ein Kontextwort innerhalb eines
 * verworfenen Werts ohne Leerraum wird nicht mehr ausgewertet.
 *
 * <p><b>Linearitaet (AK-46).</b> Der Haupt-Durchlauf besucht jedes Zeichen einmal. Je Kontextwort liest die Regel
 * hoechstens ein Zwischenwort, drei Woerter zu je hoechstens 65 gelesenen Zeichen, den Leerraum dazwischen, den
 * Verbinder und den Wert; die Suche nach dem schliessenden Anfuehrungszeichen ist auf 257 Zeichen begrenzt, ein
 * Wort wird einmal bis zu seinem Ende gelesen und danach nicht noch einmal. Die Kuerzung am Ende nutzt Zaehler und
 * liest den Wert nicht mehrfach. Es gibt keinen regulaeren Ausdruck, keine Rekursion und kein Zuruecksetzen der
 * Leseposition ueber eine feste Schranke hinaus. Zusaetzlicher Speicher: konstant, plus die Kandidatenliste.
 *
 * <p>Die Funde sind je Aufruf ueberlappungsfrei und aufsteigend, weil die Suche hinter dem Wertbereich
 * weitergeht (Plan Nachtrag Entscheidung 8). Keine Meldung und keine Ausnahme nennt Text oder Wert.
 */
final class KontextRegelStandT6 {

    /**
     * Stamm-Kontextwoerter fuer PASSWORT (geschlossene, pflegbare Liste, D-4); Kleinbuchstaben. Ein Wortkoerper
     * zaehlt, wenn er auf einen dieser Staemme endet ({@code Initialpasswort}, {@code DB_PASSWORD}), nicht aber
     * {@code Passwortrichtlinie}, {@code Passwort-Reset}, {@code Passwoerter}.
     */
    private static final List<String> STAEMME_PASSWORT = List.of("passwort", "kennwort", "password", "passwd",
            "passphrase");

    /**
     * Kontextwoerter, die nur als ganzer Wortkoerper zaehlen (geschlossene, pflegbare Liste, D-4); Kleinbuchstaben.
     * {@code pwd} ist auch ein Unix-Befehl, deshalb gilt es nur mit {@code :} oder {@code =}.
     */
    private static final List<String> GANZE_WOERTER_PASSWORT = List.of("pw", "pwd");

    /**
     * Woerter, die einen Zwischenteil einleiten (geschlossene, pflegbare Liste, D-6); Kleinbuchstaben. {@code fuer}
     * ist die ASCII-Schreibweise von {@code f}{@code ue}{@code r} mit Umlaut (Entscheidung 3 vom 25.11.2026).
     */
    private static final List<String> ZWISCHENWOERTER = List.of("f" + GeheimnisZeichen.UE_KLEIN + "r", "fuer",
            "for", "von", "vom", "des", "der", "zu", "zum", "zur", "of", "to");

    /** Hoechstzahl der Woerter nach dem Zwischenwort (D-6). */
    static final int MAX_ZWISCHENTEIL_WOERTER = 3;
    /** Hoechstlaenge eines Worts im Zwischenteil (D-6). */
    static final int MAX_ZWISCHENWORT = 64;
    /** Hoechstzahl gleicher Auszeichnungszeichen in einer Folge (Entscheidung 6). */
    private static final int MAX_AUSZEICHNUNG = 3;

    /** Konfidenz eines Funds in derselben Zeile mit mindestens {@link Werttest#MIN_LAENGE_SICHER} Zeichen (D-9). */
    private static final double KONFIDENZ_SICHER = 0.95;
    /** Konfidenz bei 6 oder 7 Zeichen und bei einem Wert nach einem Zeilenumbruch (D-9). */
    private static final double KONFIDENZ_WAHRSCHEINLICH = 0.80;

    /**
     * Zugabe zur Laengengrenze eines Worts hinter einem Schema-Wort: Steht der Wert in Anfuehrungszeichen
     * ({@code "Authorization": "Bearer Wert",}), gehoeren ein schliessendes Anfuehrungszeichen und ein Komma noch zum
     * gelesenen Wort und werden erst bei der Kuerzung am Ende abgetrennt (Entscheidung 3 nach der Pruefung von T-6).
     */
    private static final int SCHLUSS_ZUGABE = 2;

    /** Zeichen, deren Zahl im Wert die Kuerzung am Ende braucht: Klammern und Anfuehrungszeichen. */
    private static final String SPEZIAL = "()[]{}" + GeheimnisZeichen.ANFUEHRUNG;

    /**
     * Klasse des Verbinders, der den Wert einleitet: K1 ({@code :} oder {@code =}, streng nach W1 bis W5, nach einem
     * Zeilenumbruch mit W6), K2 ({@code ist}, {@code lautet}, {@code is}: W6 und kein Fachbegriff mit Bindestrich) und
     * K3/K4 (Tabulator, Element, {@code |} mit Leerraum dahinter: W6). {@code K3_STRICH} ist K3 mit senkrechtem
     * Strich, der Wert beginnt unmittelbar dahinter: wie K3_K4, aber der Wert-Test gilt nur fuer den Teil bis zum
     * naechsten {@code |}, fuehrende {@code |} uebersprungen (Tabellen ohne Leerraum). K2 bis K4 wertet
     * {@link SchwacheVerbinder} aus (T-5). {@code SCHEMA} (T-6) ist K1 hinter {@code Bearer} oder {@code Authorization}
     * und hinter dem Schema-Wort der Schema-Form ({@code Authorization: Bearer Wert}): Laenge mindestens 16 statt W6,
     * kein Aufruf und keine Form {@code name="..."}, Konfidenz 0,95. {@code SCHEMA_OHNE} ist {@code Bearer Wert} ohne
     * Verbinder: wie {@code SCHEMA}, Konfidenz 0,80, kein Zeilenumbruch.
     */
    enum Klasse {
        K1, K2, K3_K4, K3_STRICH, SCHEMA, SCHEMA_OHNE
    }

    private KontextRegelStandT6() {
    }

    /**
     * Durchsucht den Text und haengt die Kandidaten der Art an {@code ziel} an (aufsteigend, ueberlappungsfrei).
     *
     * @param text der Text, nicht {@code null}
     * @param art  die Art (Typ und Quelle der Funde)
     * @param ziel die Liste, an die die Kandidaten angehaengt werden
     */
    static void finde(String text, Art art, List<Finding> ziel) {
        int n = text.length();
        int i = 0;
        while (i < n) {
            int cp = text.codePointAt(i);
            if (!GeheimnisZeichen.istWortkoerperZeichen(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int ende = i;
            while (ende < n) {
                int c = text.codePointAt(ende);
                if (!GeheimnisZeichen.istWortkoerperZeichen(c)) {
                    break;
                }
                ende += Character.charCount(c);
            }
            int weiter = ende;
            if (art == Art.TOKEN) {
                weiter = Math.max(ende, TokenKontextStandT6.auswerten(text, i, ende, ziel));
            } else if (istKontextwort(text, i, ende, true)) {
                weiter = Math.max(ende, auswerten(text, ende, art, ziel));
            }
            i = weiter;
        }
    }

    // ------------------------------------------------------------------ Kontextwort (D-4)

    /**
     * Ob der Wortkoerper {@code text[von, ende)} ein Kontextwort ist. Umschliesst dieselbe Folge von ein bis drei
     * {@code _} den Koerper ({@code __Passwort__}, Auszeichnung), zaehlt der Inhalt. {@code pw} und {@code pwd}
     * zaehlen nur mit {@code mitKurzformen} (D-4: sie gelten nur mit K1).
     */
    static boolean istKontextwort(String text, int von, int ende, boolean mitKurzformen) {
        int a = 0;
        while (von + a < ende && a <= MAX_AUSZEICHNUNG && text.charAt(von + a) == '_') {
            a++;
        }
        int b = 0;
        while (ende - 1 - b >= von && b <= MAX_AUSZEICHNUNG && text.charAt(ende - 1 - b) == '_') {
            b++;
        }
        int s = von;
        int e = ende;
        if (a >= 1 && a <= MAX_AUSZEICHNUNG && a == b && von + a < ende - b) {
            s = von + a;
            e = ende - b;
        }
        // Rueckwaertsvergleich nur ueber die Laenge des laengsten Stamms ({@code passphrase}, 10 Zeichen)
        for (String stamm : STAEMME_PASSWORT) {
            if (e - stamm.length() >= s && GeheimnisZeichen.endetMit(text, e, stamm)) {
                return true;
            }
        }
        for (String wort : GANZE_WOERTER_PASSWORT) {
            if (mitKurzformen && GeheimnisZeichen.gleichKlein(text, s, e, wort)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wie {@link #istKontextwort(String, int, int, boolean)} fuer die Art: bei {@link Art#TOKEN} gelten die Woerter
     * aus {@link TokenKontextStandT6} ({@code mitKurzformen} schaltet dort {@code Bearer} und
     * {@code Authorization} ein), bei
     * {@link Art#PASSWORT} unveraendert die Woerter aus D-4.
     */
    static boolean istKontextwort(String text, int von, int ende, boolean mitKurzformen, Art art) {
        if (art == Art.TOKEN) {
            return TokenKontextStandT6.art(text, von, ende, mitKurzformen) != TokenKontextStandT6.KEIN;
        }
        return istKontextwort(text, von, ende, mitKurzformen);
    }

    // ------------------------------------------------------------------ Verbinder und Wert

    /** Wertet hinter dem Kontextwort aus; liefert die Position, ab der weitergesucht wird (mindestens {@code kw}). */
    private static int auswerten(String text, int kw, Art art, List<Finding> ziel) {
        int nachVerbinder = verbinderK1(text, kw);
        if (nachVerbinder < 0) {
            return kw;
        }
        return wert(text, nachVerbinder, art, ziel, Klasse.K1);
    }

    /**
     * Position hinter dem Verbinder K1 nach dem Kontextwort, das bei {@code kw} endet (Auszeichnung, Anfuehrungszeichen
     * und Zwischenteil eingerechnet), sonst -1. Die Leseweite ist durch eine Konstante beschraenkt.
     */
    static int verbinderK1(String text, int kw) {
        int n = text.length();
        int p = kw;
        int auszeichnung = auszeichnungLaenge(text, p, false);
        p += auszeichnung;
        int nachAuszeichnung = p;
        if (p < n && (text.charAt(p) == '"' || text.charAt(p) == '\'')) {
            p++;
        }
        int nachVerbinder = verbinderAb(text, p);
        if (nachVerbinder < 0) {
            nachVerbinder = zwischenteil(text, nachAuszeichnung);
        }
        return nachVerbinder;
    }

    /** Position hinter dem Verbinder K1 ({@code :} oder {@code =}) nach optionalem Leerraum, sonst -1. */
    static int verbinderAb(String text, int p) {
        int q = ueberspringeLeerraum(text, p);
        if (q < text.length() && (text.charAt(q) == ':' || text.charAt(q) == '=')) {
            return q + 1;
        }
        return -1;
    }

    /**
     * Zwischenteil (D-6): Leerraum, ein Zwischenwort, dann ein bis drei Woerter; nach jedem Wort wird auf den
     * Verbinder geprueft. Liefert die Position hinter dem Verbinder oder -1.
     */
    private static int zwischenteil(String text, int p0) {
        int q = ueberspringeLeerraum(text, p0);
        if (q == p0) {
            return -1;
        }
        int w = wortEnde(text, q, MAX_ZWISCHENWORT + 1);
        if (w == q || !istZwischenwort(text, q, w)) {
            return -1;
        }
        int pos = w;
        for (int k = 0; k < MAX_ZWISCHENTEIL_WOERTER; k++) {
            q = ueberspringeLeerraum(text, pos);
            if (q == pos) {
                return -1;
            }
            w = wortEnde(text, q, MAX_ZWISCHENWORT + 1);
            if (w == q || w - q > MAX_ZWISCHENWORT) {
                return -1;
            }
            int v = verbinderAb(text, w);
            if (v >= 0) {
                return v;
            }
            pos = w;
        }
        return -1;
    }

    static boolean istZwischenwort(String text, int von, int bis) {
        for (String wort : ZWISCHENWOERTER) {
            if (GeheimnisZeichen.gleichKlein(text, von, bis, wort)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ende des Worts ab {@code q}: bis zum Leerraum, Zeilenumbruch, {@code :} oder {@code =}, gelesen hoechstens
     * {@code grenze} Zeichen.
     */
    static int wortEnde(String text, int q, int grenze) {
        int i = q;
        int n = text.length();
        while (i < n && i - q < grenze) {
            char c = text.charAt(i);
            if (c == ':' || c == '=' || GeheimnisZeichen.istLeerraum(c) || GeheimnisZeichen.istZeilenumbruch(c)) {
                break;
            }
            i++;
        }
        return i;
    }

    static int ueberspringeLeerraum(String text, int p) {
        int i = p;
        while (i < text.length() && GeheimnisZeichen.istLeerraum(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Laenge der Folge von {@code *} oder Gravis ({@code _} nur mit {@code mitUnterstrich}) an {@code p}, wenn sie
     * ein bis drei Zeichen lang ist, sonst 0.
     */
    private static int auszeichnungLaenge(String text, int p, boolean mitUnterstrich) {
        if (p >= text.length()) {
            return 0;
        }
        char c = text.charAt(p);
        if (!GeheimnisZeichen.istAuszeichnung(c) || (c == '_' && !mitUnterstrich)) {
            return 0;
        }
        int laenge = GeheimnisZeichen.folgenLaenge(text, p, MAX_AUSZEICHNUNG + 1);
        return laenge <= MAX_AUSZEICHNUNG ? laenge : 0;
    }

    /**
     * Der Wert hinter dem Verbinder der Klasse {@code klasse}; liefert die Position, ab der weitergesucht wird. Bei
     * K2 bis K4 steht der Wert in derselben Zeile (der Aufrufer prueft das), der Fund hat die Strenge SCHWACH.
     */
    static int wert(String text, int nachVerbinder, Art art, List<Finding> ziel, Klasse klasse) {
        int n = text.length();
        int v = nachVerbinder;
        int lauf = auszeichnungLaenge(text, v, true);
        if (lauf > 0 && (v + lauf >= n || GeheimnisZeichen.istLeerraum(text.charAt(v + lauf))
                || GeheimnisZeichen.istZeilenumbruch(text.charAt(v + lauf)))) {
            v += lauf;
        }
        int start = ueberspringeLeerraum(text, v);
        boolean schwach = klasse != Klasse.K1;
        int umbruch = GeheimnisZeichen.zeilenumbruchLaenge(text, start);
        if (umbruch > 0) {
            if (klasse != Klasse.K1) {
                return nachVerbinder;
            }
            start = ueberspringeLeerraum(text, start + umbruch);
            if (start >= n || GeheimnisZeichen.zeilenumbruchLaenge(text, start) > 0) {
                return nachVerbinder;
            }
            schwach = true;
        }
        if (start >= n) {
            return n;
        }
        int ausz = umschlossenAuszeichnung(text, start);
        if (ausz > 0) {
            start += ausz;
            if (start >= n || GeheimnisZeichen.istLeerraum(text.charAt(start))
                    || GeheimnisZeichen.istZeilenumbruch(text.charAt(start))) {
                return start;
            }
        }
        if (GeheimnisZeichen.istAnfuehrungOeffnend(text.charAt(start))) {
            int schluss = schliessendesZeichen(text, start);
            if (schluss >= 0) {
                meldeWert(text, start + 1, schluss, schwach, true, klasse, art, ziel);
                return schluss + 1;
            }
            start++;
        }
        return wortWert(text, start, ausz, schwach, klasse, art, ziel);
    }

    /**
     * Der Wert als erstes Wort ab {@code start} (bis zum Leerraum oder Zeilenende, hoechstens 4 096 Zeichen),
     * gekuerzt am Ende; {@code ausz} ist die Laenge der uebersprungenen Auszeichnung davor. Liefert die Position, ab
     * der weitergesucht wird (das Wortende). Hinter K2 ist ein Wort, das auf Bindestrich und ein reines
     * Kleinbuchstabenwort endet ({@code AES256-verschluesselt}), kein Fund.
     */
    private static int wortWert(String text, int start, int ausz, boolean schwach, Klasse klasse, Art art,
                                List<Finding> ziel) {
        int n = text.length();
        int we = start;
        while (we < n && !GeheimnisZeichen.istLeerraum(text.charAt(we))
                && !GeheimnisZeichen.istZeilenumbruch(text.charAt(we))) {
            we++;
        }
        boolean schema = klasse == Klasse.SCHEMA || klasse == Klasse.SCHEMA_OHNE;
        if (we - start > Werttest.MAX_LAENGE + (schema ? SCHLUSS_ZUGABE : 0)) {
            return we;
        }
        int e = kuerzeEnde(text, start, we);
        if (schema) {
            e = kuerzeSchluss(text, start, e, we);
        }
        if (ausz > 0 && e - ausz > start && endetMitFolge(text, start, e, ausz)) {
            e = kuerzeEnde(text, start, e - ausz);
        }
        if (e > start && klasse == Klasse.K3_STRICH) {
            strichWert(text, start, e, art, ziel);
        } else if (e > start && !(klasse == Klasse.K2 && endetMitBindestrichKleinwort(text, start, e))) {
            meldeWert(text, start, e, schwach, false, klasse, art, ziel);
        }
        return we;
    }

    /**
     * Schema-Wert (T-6): Ein schliessendes Anfuehrungszeichen, das in CSV verdoppelt ({@code ""}) oder in JSON im Text
     * maskiert ({@code \"}) ist, gehoert nicht zum Wert; die Kuerzung am Ende laeuft danach noch einmal. Von einem
     * maskierten Zeichen, das {@link #kuerzeEnde} schon entfernt hat, bleibt der Rueckstrich am Ende stehen und wird
     * hier abgetrennt. {@code we} ist das Wortende, {@code e} das Ende nach der ersten Kuerzung.
     */
    private static int kuerzeSchluss(String text, int start, int e, int we) {
        if (e - start > 2 && text.charAt(e - 1) == '"' && (text.charAt(e - 2) == '"' || text.charAt(e - 2) == '\\')) {
            return kuerzeEnde(text, start, e - 2);
        }
        if (e - start > 1 && e < we && text.charAt(e - 1) == '\\' && text.charAt(e) == '"') {
            return e - 1;
        }
        return e;
    }

    /**
     * Wert unmittelbar hinter {@code |} (K3, Tabelle ohne Leerraum), Wort {@code text[start, e)}: fuehrende {@code |}
     * im Wort werden fuer die Pruefung uebersprungen; geprueft wird (W1 bis W6) nur der Teil danach bis zum
     * naechsten {@code |} (oder bis {@code e}). Besteht er, reicht der Fund unveraendert von {@code start} bis
     * {@code e} ({@code |Passwort|Wert|https://...|}), sonst ist es kein Fund ({@code |Passwort|Benutzer|Rolle|},
     * {@code ||Passwort||Benutzer||}). Besteht das Wort nur aus {@code |}, ist es kein Fund. Ein Durchlauf, linear.
     */
    private static void strichWert(String text, int start, int e, Art art, List<Finding> ziel) {
        int p = start;
        while (p < e && text.charAt(p) == '|') {
            p++;
        }
        if (p >= e) {
            return;
        }
        int strich = p + 1;
        while (strich < e && text.charAt(strich) != '|') {
            strich++;
        }
        if (Werttest.besteht(text, p, strich, Verbinder.SCHWACH, false)) {
            ziel.add(new Finding(art.typ(), text.substring(start, e), start, e, KONFIDENZ_WAHRSCHEINLICH,
                    art.quelle()));
        }
    }

    /**
     * Ob {@code text[start, e)} auf einen Bindestrich und danach nur Kleinbuchstaben ({@link Character#isLowerCase})
     * endet, mindestens einen. Der Bindestrich steht nicht am Anfang.
     */
    private static boolean endetMitBindestrichKleinwort(String text, int start, int e) {
        int i = e;
        while (i > start) {
            int cp = text.codePointBefore(i);
            if (!Character.isLowerCase(cp)) {
                break;
            }
            i -= Character.charCount(cp);
        }
        return i < e && i - 1 > start && text.charAt(i - 1) == '-';
    }

    /**
     * Laenge der Auszeichnungsfolge ({@code *}, {@code _}, Gravis) am Wertanfang, wenn sie zwei oder drei Zeichen
     * lang ist und in derselben Zeile (hoechstens 256 Zeichen weiter) dieselbe Folge noch einmal steht; sonst 0.
     * Eine einzelne {@code *} oder {@code _} bleibt Teil des Werts, ebenso eine Folge ohne Gegenstueck. Der Versuch
     * vom 25.11.2026 (Entscheidung 2), ein einzelnes Zeichen mit spaeterem Gegenstueck in der Zeile wie eine Folge
     * zu behandeln, wurde zurueckgenommen: Im Positiv-Generator (66 663 Faelle) lieferte er 132 Funde ohne das
     * fuehrende Zeichen (nur 66 531 von 66 663 Faellen exakt); der Pruefstand bleibt unveraendert.
     */
    private static int umschlossenAuszeichnung(String text, int start) {
        int laenge = auszeichnungLaenge(text, start, true);
        if (laenge < 2) {
            return 0;
        }
        return schliessendeFolge(text, start, laenge) >= 0 ? laenge : 0;
    }

    /** Ob der Wert {@code text[start, e)} auf eine genau {@code laenge} lange Folge der Auszeichnung endet. */
    private static boolean endetMitFolge(String text, int start, int e, int laenge) {
        char c = text.charAt(start - laenge);
        for (int i = e - laenge; i < e; i++) {
            if (text.charAt(i) != c) {
                return false;
            }
        }
        return e - laenge - 1 < start || text.charAt(e - laenge - 1) != c;
    }

    /**
     * Position der schliessenden Folge von {@code laenge} gleichen Zeichen wie am Anfang: in derselben Zeile,
     * Inhalt von einem bis 256 Zeichen, die Folge darf nicht Teil einer laengeren sein. Sonst -1.
     */
    private static int schliessendeFolge(String text, int start, int laenge) {
        char c = text.charAt(start);
        int von = start + laenge;
        int grenze = Math.min(text.length(), von + Werttest.MAX_LAENGE_ANFUEHRUNG + laenge);
        int j = von;
        while (j < grenze) {
            char d = text.charAt(j);
            if (GeheimnisZeichen.istZeilenumbruch(d)) {
                return -1;
            }
            if (d == c) {
                int lauf = GeheimnisZeichen.folgenLaenge(text, j, MAX_AUSZEICHNUNG + 1);
                if (lauf == laenge && j > von) {
                    return j;
                }
                j += lauf;
            } else {
                j++;
            }
        }
        return -1;
    }

    /**
     * Position des zum oeffnenden Zeichen an {@code start} passenden schliessenden Zeichens: in derselben Zeile,
     * Inhalt von einem bis 256 Zeichen. Bei {@code "} und {@code '} gehoert ein mit einer ungeraden Zahl von
     * Rueckstrichen maskiertes ({@code \"}) und ein verdoppeltes ({@code ""}) Zeichen zum Inhalt (JSON, YAML, SQL,
     * CSV); der Rueckstrich zaehlt dabei nur, solange er unmittelbar vor dem Zeichen steht. Der Lauf ist ein
     * Durchgang ohne Zuruecksetzen. Sonst -1.
     */
    private static int schliessendesZeichen(String text, int start) {
        char offen = text.charAt(start);
        boolean gerade = offen == '"' || offen == '\'';
        int n = text.length();
        int grenze = Math.min(n, start + 1 + Werttest.MAX_LAENGE_ANFUEHRUNG + 1);
        int rueckstriche = 0;
        int j = start + 1;
        while (j < grenze) {
            char d = text.charAt(j);
            if (GeheimnisZeichen.istZeilenumbruch(d)) {
                return -1;
            }
            if (gerade && d == '\\') {
                rueckstriche++;
                j++;
                continue;
            }
            if (GeheimnisZeichen.schliesst(offen, d) && !(gerade && rueckstriche % 2 == 1)) {
                if (gerade && j + 1 < n && text.charAt(j + 1) == offen) {
                    rueckstriche = 0;
                    j += 2;
                    continue;
                }
                return j > start + 1 ? j : -1;
            }
            rueckstriche = 0;
            j++;
        }
        return -1;
    }

    /** Prueft den Wert nach der Klasse des Verbinders: Schema-Klassen (T-6) mit Schema-Strenge, sonst wie bisher. */
    private static void meldeWert(String text, int start, int ende, boolean schwach, boolean inAnfuehrung,
                                  Klasse klasse, Art art, List<Finding> ziel) {
        if (klasse == Klasse.SCHEMA || klasse == Klasse.SCHEMA_OHNE) {
            pruefeSchema(text, start, ende, klasse == Klasse.SCHEMA ? KONFIDENZ_SICHER : KONFIDENZ_WAHRSCHEINLICH,
                    inAnfuehrung, art, ziel);
        } else {
            pruefeUndMelde(text, start, ende, schwach, inAnfuehrung, art, ziel);
        }
    }

    /**
     * Schema-Strenge (D-8 W6 letzter Satz): W1 bis W5 und Laenge mindestens 16, ohne Klassenregel; die Konfidenz
     * ist fest ({@code Authorization} und {@code Bearer} mit K1 0,95, {@code Bearer} ohne Verbinder 0,80).
     */
    private static void pruefeSchema(String text, int start, int ende, double konfidenz, boolean inAnfuehrung,
                                     Art art, List<Finding> ziel) {
        if (!istAufrufOderZuweisung(text, start, ende)
                && Werttest.besteht(text, pruefAb(text, start, ende, inAnfuehrung), ende, Verbinder.SCHEMA,
                        inAnfuehrung)) {
            ziel.add(new Finding(art.typ(), text.substring(start, ende), start, ende, konfidenz, art.quelle()));
        }
    }

    /**
     * Ob der Wert {@code text[start, ende)} die Form {@code name="..."} hat oder eine runde Klammer enthaelt:
     * Parameter von HTTP-Kopfzeilen ({@code Bearer error="invalid_token"}) und Aufrufe
     * ({@code Basic base64(user:password)}) sind keine Tokens (Entscheidung 2 nach der Pruefung von T-6). Die Form
     * {@code name="..."} ist ein Buchstabe, eine Ziffer oder {@code _}, dann {@code =}, ein Anfuehrungszeichen im
     * Innern des Werts und dahinter nicht das Wertende; bei verdoppeltem Anfuehrungszeichen ({@code error=""x""}, CSV)
     * muss danach ein Buchstabe, eine Ziffer oder {@code _} folgen. Base64-Padding vor einem schliessenden,
     * verdoppelten ({@code ==""}) oder am Ende stehenden Anfuehrungszeichen ist kein Parameter. Ein Durchlauf ueber
     * den Wert.
     */
    private static boolean istAufrufOderZuweisung(String text, int start, int ende) {
        for (int i = start; i < ende; i++) {
            char c = text.charAt(i);
            if (c == '(' || c == ')' || (c == '=' && istParameterAnfuehrung(text, start, i, ende))) {
                return true;
            }
        }
        return false;
    }

    /** Ob an {@code text[i]} ({@code =}) die Form {@code name="x} beginnt (siehe {@link #istAufrufOderZuweisung}). */
    private static boolean istParameterAnfuehrung(String text, int start, int i, int ende) {
        if (i <= start || i + 2 >= ende || text.charAt(i + 1) != '"' || !istNamenszeichen(text.charAt(i - 1))) {
            return false;
        }
        int k = i + 2;
        if (text.charAt(k) != '"') {
            return true;
        }
        return k + 1 < ende && istNamenszeichen(text.charAt(k + 1));
    }

    private static boolean istNamenszeichen(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Beginn der Pruefung des Werts {@code text[start, ende)}: Ein maskiertes Anfuehrungszeichen am Anfang
     * (Rueckstrich und Zeichen) ist keine Pfadform (W4); der Rueckstrich zaehlt beim Wert-Test nicht, der Fund deckt
     * ihn trotzdem.
     */
    private static int pruefAb(String text, int start, int ende, boolean inAnfuehrung) {
        return inAnfuehrung && ende - start > 1 && text.charAt(start) == '\\'
                && (text.charAt(start + 1) == '"' || text.charAt(start + 1) == '\'') ? start + 1 : start;
    }

    /** Prueft den Wert und haengt bei Erfolg den Kandidaten an. */
    static void pruefeUndMelde(String text, int start, int ende, boolean schwach, boolean inAnfuehrung,
                               Art art, List<Finding> ziel) {
        int ab = pruefAb(text, start, ende, inAnfuehrung);
        boolean ok = schwach ? Werttest.besteht(text, ab, ende, Verbinder.SCHWACH, inAnfuehrung)
                : Werttest.besteht(text, ab, ende, Verbinder.STARK, inAnfuehrung);
        if (!ok) {
            return;
        }
        int laenge = text.codePointCount(start, ende);
        double konfidenz = !schwach && laenge >= Werttest.MIN_LAENGE_SICHER ? KONFIDENZ_SICHER
                : KONFIDENZ_WAHRSCHEINLICH;
        ziel.add(new Finding(art.typ(), text.substring(start, ende), start, ende, konfidenz, art.quelle()));
    }

    // ------------------------------------------------------------------ Kuerzung am Ende (D-7)

    /**
     * Ende des Werts {@code text[start, we)} nach dem wiederholten Entfernen von {@code . , ; : ?}, einer
     * schliessenden Klammer bei Ueberschuss und eines schliessenden Anfuehrungszeichens ohne oeffnendes im Wert.
     * Das {@code !} bleibt. Die Zaehler werden einmal gebildet und beim Kuerzen nachgefuehrt, der Wert wird nicht
     * mehrfach gelesen.
     */
    private static int kuerzeEnde(String text, int start, int we) {
        int[] zaehler = new int[SPEZIAL.length()];
        for (int i = start; i < we; i++) {
            int idx = SPEZIAL.indexOf(text.charAt(i));
            if (idx >= 0) {
                zaehler[idx]++;
            }
        }
        int e = we;
        while (e > start) {
            char c = text.charAt(e - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '?') {
                e--;
                continue;
            }
            int idx = SPEZIAL.indexOf(c);
            if (idx < 0 || !istUnausgeglichen(c, zaehler)) {
                break;
            }
            zaehler[idx]--;
            e--;
        }
        return e;
    }

    /** Ob das schliessende Zeichen {@code c} (am Ende des Werts) keinen oeffnenden Partner im Wert hat. */
    private static boolean istUnausgeglichen(char c, int[] zaehler) {
        switch (c) {
            case ')':
                return zaehler[SPEZIAL.indexOf(')')] > zaehler[SPEZIAL.indexOf('(')];
            case ']':
                return zaehler[SPEZIAL.indexOf(']')] > zaehler[SPEZIAL.indexOf('[')];
            case '}':
                return zaehler[SPEZIAL.indexOf('}')] > zaehler[SPEZIAL.indexOf('{')];
            default:
                break;
        }
        int partner = 0;
        boolean schliessend = false;
        for (int k = 0; k < SPEZIAL.length(); k++) {
            char o = SPEZIAL.charAt(k);
            if (GeheimnisZeichen.schliesst(o, c)) {
                schliessend = true;
                partner += o == c ? zaehler[k] - 1 : zaehler[k];
            }
        }
        return schliessend && partner <= 0;
    }
}
