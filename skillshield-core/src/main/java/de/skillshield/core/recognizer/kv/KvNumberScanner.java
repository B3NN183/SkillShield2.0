package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gemeinsamer Zeichendurchlauf der KV-Nummern-Erkenner (LANR und IK, je Art konfiguriert ueber {@link KvArt}). Der
 * Text wird einmal von links nach rechts gelesen und in Ereignisse zerlegt: Kontextwort, Fuellwort, Ziffernkette,
 * Satzende und Zeilenumbruch. Eine Ziffernkette wird nur dann ein Fund, wenn ein Kontextwort sie erschliesst. Die
 * Pruefziffer bestimmt nur Stufe und Quelle, nie, ob ein Fund entsteht.
 *
 * <p><b>Zeichenklassen.</b> Ziffer: nur ASCII {@code 0} bis {@code 9}. Buchstabe: Codepunkt mit
 * {@link Character#isLetter(int)} oder Kombinationszeichen. Unsichtbares Zeichen: Formatzeichen
 * ({@link Character#FORMAT}, z. B. U+00AD, U+200B, U+FEFF) und Variantenwaehler U+FE00 bis U+FE0F. Lauf: maximale
 * Folge aus Buchstaben und unsichtbaren Zeichen. Teilstueck: maximale Buchstabenfolge innerhalb eines Laufs; die
 * unsichtbaren Zeichen trennen die Teilstuecke. Wort (Kontextwort, Vorwort, Nachsatzwort): eine zusammenhaengende
 * Folge ganzer Teilstuecke eines Laufs, verglichen ohne die unsichtbaren Zeichen dazwischen und mit Faltung von
 * {@code A-Z} (kein {@code toLowerCase}, damit die tuerkischen i-Formen und das Kelvin-Zeichen nicht zu {@code i}
 * und {@code k} werden). Teilstueckgrenzen sind Wortgrenzen: {@code PraxisLANR} ohne unsichtbares Zeichen, {@code
 * LANRX}, {@code Technik} und {@code IKEA} haben kein Kontextwort, {@code Praxis}+U+200B+{@code LANR} und
 * {@code LA}+U+00AD+{@code NR} haben eines. Ein Kombinationszeichen direkt am Kontextwort bleibt Teil seines
 * Teilstuecks und ergibt damit ein anderes Wort. Kontextwort: ein Wort, das einem Eintrag von
 * {@link KvArt#kontextwoerter()} gleicht, oder ein Wort aus {@link KvArt#vorwoerter()}, dem nach hoechstens einem
 * {@code -}, Leerzeichen, U+00A0, U+2011 oder U+2013 die ersten Teilstuecke des naechsten Laufs als Wort {@code nr}
 * oder {@code nummer} folgen. Ein Lauf ohne Buchstaben im Sinne von {@link Character#isLetter(int)} ist neutral.
 * Zeilenumbruch: CR LF (zaehlt als einer), LF, CR, U+0085, U+2028, U+2029. Leerraum: Leerzeichen, Tab, U+00A0 und
 * alle anderen Zeichen mit {@link Character#isWhitespace(int)} oder {@link Character#isSpaceChar(int)} ausser dem
 * Zeilenumbruch. Gruppentrenner in einer Ziffernkette: genau eines von Leerzeichen, U+00A0, U+2009, U+202F,
 * {@code -}, {@code .}, {@code /}.
 *
 * <p><b>Ziffernketten.</b> Eine Kette besteht aus Ziffernbloecken, die durch je genau einen Gruppentrenner
 * verbunden sind, dem wieder eine Ziffer folgt. Zwei Trenner hintereinander oder ein Trenner am Ende beenden die
 * Kette. Ab dem Kettenanfang werden Praefixe aus ganzen Bloecken betrachtet: Gibt es ein Praefix mit genau neun
 * Ziffern, ist es das Segment (Pruefziffer ueber die neun Ziffern ohne Trenner). Sonst ist das laengste Praefix mit
 * 6 bis 12 Ziffern das Segment mit Laengenabweichung. Sonst gibt es kein Segment, und der erste Block zaehlt als
 * Fuellwort. Danach geht der Durchlauf hinter dem Segment bzw. hinter dem ersten Block weiter; der Rest der Kette
 * wird als neue Kette gelesen. Die Praefixsuche bricht ab, sobald mehr als 12 Ziffern gezaehlt sind (auch mitten in
 * einem Block). Unsichtbare Zeichen innerhalb einer Nummer beenden die Kette (bekannte Grenze, nicht veraendert).
 *
 * <p><b>Zustand und Ereignisse.</b> Zustand: {@code aktiv} (ein Kontextwort erschliesst Zahlen), {@code direkt}
 * (seit dem Kontextwort kam nichts ausser Sonstigem und Nachsatzwoertern), {@code woerter} (Fuellwoerter seit
 * Kontextwort bzw. letztem Segment), {@code umbrueche} (ebenso) und die Folge der offenen Segmente samt
 * Luecken-Merkern (fuer nachgestellten Kontext).
 * <ul>
 * <li>Kontextwort: Sind Segmente offen und die Luecke hinter dem letzten sauber oder mit genau einem {@code ,}
 * bzw. {@code ;} (siehe unten), werden alle ausgegeben (nachgestellter Kontext). Dann {@code aktiv = direkt = true},
 * {@code woerter = umbrueche = 0}.</li>
 * <li>Segment bei {@code aktiv}: wird ausgegeben; danach {@code direkt = false}, {@code woerter = umbrueche = 0}
 * (Aufzaehlung, auch zeilenweise). Segment ohne {@code aktiv}: wird offen (siehe unten).</li>
 * <li>Fuellwort (Wort, Ziffernblock ohne Segment, Nachsatzwort ausserhalb von {@code direkt}): {@code woerter++},
 * {@code direkt = false}; bei mehr als drei Fuellwoertern {@code aktiv = false}. Von den Teilstuecken eines Laufs
 * zaehlen die vor einem Kontextwort desselben Laufs nur als Fuellwort im Sinne von "leert die offenen Segmente";
 * die hinter dem letzten Kontextwort (ohne dessen Nachsatzwort) zaehlen zusammen als genau ein Fuellwort. Ein Lauf
 * ganz ohne Kontextwort zaehlt als genau ein Fuellwort.</li>
 * <li>Nachsatzwort ({@code nr}, {@code nummer}, auch aus mehreren Teilstuecken): Unmittelbar hinter einem
 * Kontextwort ({@code direkt}), im selben oder im folgenden Lauf, ist es kein Fuellwort.</li>
 * <li>{@code .}: bei {@code aktiv} und nicht {@code direkt} Satzende ({@code aktiv = false}). Kein Satzende ist
 * der Punkt direkt hinter dem Kontextwort ({@code LANR-Nr.}) und der Abkuerzungspunkt unmittelbar hinter einem Lauf
 * mit Buchstaben aus hoechstens vier Buchstaben ({@code Dr.}, {@code e.V.}, {@code Prof.}); das Wort zaehlt
 * weiter als Fuellwort. Ein ein- bis zweistelliger Ziffernblock, der das erste Zeichen seiner Zeile nach
 * optionalem Leerraum ist und dem unmittelbar ein {@code .} folgt, das nicht Gruppentrenner einer Kette ist, ist
 * samt Punkt neutral (nummerierte Liste). {@code !} und {@code ?}: {@code aktiv = false}.</li>
 * <li>Zeilenumbruch: bei {@code aktiv} {@code umbrueche++}, {@code direkt = false}; bei mehr als einem Umbruch
 * {@code aktiv = false} (Leerzeile).</li>
 * </ul>
 *
 * <p><b>Offene Segmente (nachgestellter Kontext).</b> Ein Segment ohne {@code aktiv} wird an die Folge der offenen
 * Segmente angehaengt, wenn zwischen ihm und dem vorigen offenen Segment nur Leerraum, {@code ,}, {@code ;},
 * {@code /} oder das Wort {@code und} (ASCII, fallunabhaengig, hoechstens einmal je Luecke) stehen; sonst ersetzt es
 * die Folge. Jedes andere Ereignis (Wort, Ziffernblock ohne Segment, Satzzeichen, Zeilenumbruch, sonstiges Zeichen)
 * leert die ganze Folge. Zwischen dem letzten Segment und dem Kontextwort sind nur Leerraum, neutrale Laeufe und
 * hoechstens ein {@code (} oder {@code [} erlaubt. Die Luecke hinter dem letzten offenen Segment wird beim
 * Kontextwort so ausgewertet: Merker sind die Zahl der {@code ,} und {@code ;} (saettigt bei 2), ein Flag fuer
 * {@code /}, das Wort {@code und} und ein Flag "Klammer vor dem Komma". Ohne {@code ,}, {@code ;}, {@code /} und
 * {@code und} werden alle offenen Segmente ausgegeben (Deckel 1,0). Mit genau einem {@code ,} oder {@code ;}, ohne
 * {@code /}, {@code und} und ohne Klammer vor dem Komma werden sie ebenfalls ausgegeben, aber mit dem Deckel
 * {@link #CONFIDENCE_KOMMA_NACHGESTELLT} (0,60; {@code N, LANR}, {@code N; (IK)}, {@code N1, N2, LANR}). In allen
 * anderen Faellen ({@code N,, KW}, {@code N, und KW}, {@code N, / KW}, {@code N(, KW)}) wird nichts ausgegeben.
 * Das Komma steht nie im Fundbereich.
 *
 * <p><b>Konfidenz, Stufe, Quelle.</b> Neun Ziffern mit gueltiger Pruefziffer: Konfidenz der Art, Quelle der Art
 * (bei {@code direkt}; sonst {@link #CONFIDENCE_KONTEXT_ENTFERNT}, auch fuer nachgestellten Kontext; mit einem
 * {@code ,} oder {@code ;} vor dem nachgestellten Kontextwort hoechstens {@link #CONFIDENCE_KOMMA_NACHGESTELLT},
 * Quelle unveraendert). Ungueltige Pruefziffer: {@link #CONFIDENCE_PRUEFZIFFER_UNGUELTIG}. Sechs bis zwoelf Ziffern
 * ausser neun: {@link #CONFIDENCE_LAENGE_ABWEICHEND}.
 * Der Fundbereich ist genau das Segment (Ziffern samt inneren Trennern), nie
 * das Kontextwort und nie ein unsichtbares Zeichen; unsichtbare Zeichen veraendern keinen Index. Die Funde sind nach
 * Position sortiert und ueberlappen nicht, jedes Segment wird hoechstens einmal ausgegeben.
 *
 * <p><b>Linearitaet.</b> Ein Durchlauf, keine Rekursion, kein regulaerer Ausdruck, kein Zuruecksetzen der
 * Leseposition der Hauptschleife. Jeder Lauf wird in einem Zug gelesen. Dabei fuehrt der Durchlauf nur ein Fenster
 * der letzten 24 Buchstaben des Laufs (laengere Teilstuecke koennen keinem Eintrag gleichen, das laengste Kontextwort
 * hat 23 Zeichen) und eine Bitmaske der Teilstueckanfaenge darin. Am Ende jedes Teilstuecks werden die Fenstersuffixe
 * ab einem Teilstueckanfang verglichen: hoechstens 24 Vergleiche mit Texten von hoechstens 23 Zeichen, also
 * konstanter Aufwand je Teilstueck; jedes Zeichen des Laufs wird einmal gelesen, unsichtbare Zeichen werden ohne
 * Speicherung uebersprungen. Das Nachsatzwort hinter einem Kontextwort desselben Laufs wird ueber dieselbe Zaehlung
 * der Buchstaben seit dem Kontextwort (hoechstens sechs) erkannt, ohne Vorausschau. Hinter einem Vorwort am Ende
 * eines Laufs wird der Anfang des folgenden Laufs fuer das Nachsatzwort einmal vorausgelesen (hoechstens sechs
 * Buchstaben, abgebrochen beim ersten Widerspruch); die Hauptschleife liest ihn danach noch einmal, jedes Zeichen wird
 * damit hoechstens zweimal beruehrt, und da der Vorgriff nur am Ende eines Laufs mit Vorwort beginnt, ueberlappen
 * sich die Vorgriffe nicht. Die Praefixsuche einer Kette liest den ersten Block ganz und danach hoechstens 13 Ziffern
 * und 13 Trenner; da der Durchlauf mindestens hinter dem ersten Block weitergeht, wird jeder Block hoechstens
 * 13-mal beruehrt. Der Zeilenanfang-Merker ({@code zeilenanfang}) wird vorwaerts mitgefuehrt, nicht rueckwaerts
 * gesucht. Die Zustandsmerker aendern sich je Ereignis in konstanter Zeit; die Folge der offenen Segmente waechst
 * um drei Zahlen je Segment und wird hoechstens einmal ausgegeben oder verworfen. Gesamtaufwand O(n), Zusatzspeicher
 * O(1) plus Fundliste plus Folge der offenen Segmente.
 *
 * <p>Der Durchlauf wirft nur {@link NullPointerException} mit den festen Meldungen {@code art} und {@code text}; er
 * schreibt nichts aus, und kein Fund und keine Meldung enthaelt Ziffern des Textes.
 */
final class KvNumberScanner {

    /** Konfidenz bei gueltiger Pruefziffer und weiter entferntem Kontextwort. */
    static final double CONFIDENCE_KONTEXT_ENTFERNT = 0.80;

    /** Konfidenz bei neun Ziffern mit ungueltiger Pruefziffer. */
    static final double CONFIDENCE_PRUEFZIFFER_UNGUELTIG = 0.50;

    /** Konfidenz bei einer Ziffernzahl von 6 bis 12 ausser neun. */
    static final double CONFIDENCE_LAENGE_ABWEICHEND = 0.40;

    /** Deckel bei Komma oder Semikolon vor dem nachgestellten Kontextwort (D-5). */
    static final double CONFIDENCE_KOMMA_NACHGESTELLT = 0.60;

    private static final int NEUN = 9;
    private static final int MIN_LAENGE = 6;
    private static final int MAX_LAENGE = 12;
    private static final int MAX_FUELLWOERTER = 3;
    private static final int FENSTER = 24;
    private static final int MAX_ABKUERZUNG = 4;
    private static final String NR = "nr";
    private static final String NUMMER = "nummer";
    private static final String UND = "und";
    private static final char PLATZHALTER = (char) 0xFFFF;
    private static final char NBSP = (char) 0xA0;
    private static final char SCHMALES_LEERZEICHEN = (char) 0x2009;
    private static final char SCHMALES_NBSP = (char) 0x202F;
    private static final char GESCHUETZTER_BINDESTRICH = (char) 0x2011;
    private static final char HALBGEVIERTSTRICH = (char) 0x2013;
    private static final int NEL = 0x85;
    private static final int ZEILENTRENNER = 0x2028;
    private static final int ABSATZTRENNER = 0x2029;
    private static final int VARIANTENWAEHLER_ERSTER = 0xFE00;
    private static final int VARIANTENWAEHLER_LETZTER = 0xFE0F;

    private KvNumberScanner() {
    }

    /**
     * Liefert die Funde der Art im Text, nach Position sortiert.
     *
     * @param art  Nummernart, nicht {@code null}
     * @param text zu durchsuchender Text, nicht {@code null}
     * @return die Funde, nie {@code null}
     * @throws NullPointerException mit der Meldung {@code art} oder {@code text} bei {@code null}
     */
    static List<Finding> detect(KvArt art, String text) {
        Objects.requireNonNull(art, "art");
        Objects.requireNonNull(text, "text");
        Lauf lauf = new Lauf(art, text);
        lauf.durchlaufen();
        return lauf.funde;
    }

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean istBuchstabe(int cp) {
        if (Character.isLetter(cp)) {
            return true;
        }
        int typ = Character.getType(cp);
        return typ == Character.NON_SPACING_MARK || typ == Character.ENCLOSING_MARK
                || typ == Character.COMBINING_SPACING_MARK;
    }

    /** Formatzeichen und Variantenwaehler: trennen Teilstuecke und werden beim Wortvergleich uebergangen. */
    private static boolean istUnsichtbar(int cp) {
        return Character.getType(cp) == Character.FORMAT
                || (cp >= VARIANTENWAEHLER_ERSTER && cp <= VARIANTENWAEHLER_LETZTER);
    }

    private static boolean istZeilenumbruch(int cp) {
        return cp == '\n' || cp == '\r' || cp == NEL || cp == ZEILENTRENNER || cp == ABSATZTRENNER;
    }

    private static boolean istLeerraum(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean istTrenner(char c) {
        return c == ' ' || c == NBSP || c == SCHMALES_LEERZEICHEN || c == SCHMALES_NBSP || c == '-' || c == '.'
                || c == '/';
    }

    private static boolean istNachsatzTrenner(char c) {
        return c == '-' || c == ' ' || c == NBSP || c == GESCHUETZTER_BINDESTRICH || c == HALBGEVIERTSTRICH;
    }

    /** Gefaltetes Vergleichszeichen: {@code A-Z} klein, sonst unveraendert, ausserhalb der BMP ein Platzhalter. */
    private static char vergleichszeichen(int cp) {
        if (cp >= 'A' && cp <= 'Z') {
            return (char) (cp + ('a' - 'A'));
        }
        return cp > 0xFFFF ? PLATZHALTER : (char) cp;
    }

    /** Gleicht {@code b[0, len)} genau {@code ref}? */
    private static boolean gleich(String ref, char[] b, int from, int len) {
        if (ref.length() != len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (ref.charAt(i) != b[from + i]) {
                return false;
            }
        }
        return true;
    }

    /** Ist {@code b[from, from+len)} ein Anfangsstueck von {@code ref}? */
    private static boolean praefix(String ref, char[] b, int from, int len) {
        if (len > ref.length()) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (ref.charAt(i) != b[from + i]) {
                return false;
            }
        }
        return true;
    }

    /** Zustand und Ereignisbehandlung eines Durchlaufs. */
    private static final class Lauf {

        private final KvArt art;
        private final String text;
        private final int n;
        private final List<Finding> funde = new ArrayList<>();
        private final boolean[] kontextAnfang = new boolean[128];
        private final boolean[] kontextLaenge = new boolean[FENSTER + 1];
        private final boolean[] vorwortLaenge = new boolean[FENSTER + 1];
        private boolean aktiv;
        private boolean direkt;
        private int woerter;
        private int umbrueche;
        private boolean zeilenanfang = true;

        /** Offene Segmente als Tripel (Start, Ende, Ziffernzahl). */
        private int[] offenDaten = new int[12];
        private int offenAnzahl;
        private int offenKlammern;
        /** Luecke hinter dem letzten offenen Segment: Zahl der {@code ,} und {@code ;} (saettigt bei 2). */
        private int offenKomma;
        private boolean offenSchraeg;
        /** Ein {@code ,} oder {@code ;} kam nach einer {@code (} bzw. {@code [} der Luecke. */
        private boolean offenKlammerVorKomma;
        private boolean offenUnd;

        /** Ende des letzten Laufs und Zahl seiner Buchstaben (0, wenn ohne Buchstaben), fuer den Abkuerzungspunkt. */
        private int laufEnde = -1;
        private int laufBuchstaben;

        // Zustand des gerade gelesenen Laufs
        private final char[] fenster = new char[FENSTER];
        private int fensterLaenge;
        private int teilstueckAnfaenge;
        private int buchstaben;
        private boolean kontextImLauf;
        private int seitKontext;
        private boolean restBuchstabe;
        private boolean restWiderspruch;

        Lauf(KvArt art, String text) {
            this.art = art;
            this.text = text;
            this.n = text.length();
            for (String w : art.kontextwoerter()) {
                kontextAnfang[w.charAt(0)] = true;
                kontextLaenge[w.length()] = true;
            }
            for (String w : art.vorwoerter()) {
                kontextAnfang[w.charAt(0)] = true;
                vorwortLaenge[w.length()] = true;
            }
        }

        void durchlaufen() {
            int i = 0;
            while (i < n) {
                char c = text.charAt(i);
                if (istZiffer(c)) {
                    i = ziffernkette(i);
                } else if (c == '.') {
                    punkt(i);
                    i++;
                } else if (c == '!' || c == '?') {
                    satzende();
                    i++;
                } else {
                    int cp = text.codePointAt(i);
                    int laenge = Character.charCount(cp);
                    if (istZeilenumbruch(cp)) {
                        i += laenge;
                        if (cp == '\r' && i < n && text.charAt(i) == '\n') {
                            i++;
                        }
                        zeilenumbruch();
                    } else if (istUnsichtbar(cp) || istBuchstabe(cp)) {
                        i = lauf(i);
                    } else {
                        sonstiges(cp);
                        i += laenge;
                    }
                }
            }
        }

        // ------------------------------------------------------------------ Ereignisse

        private void offenLeeren() {
            offenAnzahl = 0;
            offenKlammern = 0;
            offenKomma = 0;
            offenSchraeg = false;
            offenKlammerVorKomma = false;
            offenUnd = false;
        }

        private void kontextwort() {
            if (offenAnzahl > 0) {
                if (!offenSchraeg && !offenUnd) {
                    if (offenKomma == 0) {
                        offenAusgeben(1.0);
                    } else if (offenKomma == 1 && !offenKlammerVorKomma) {
                        offenAusgeben(CONFIDENCE_KOMMA_NACHGESTELLT);
                    }
                }
                offenLeeren();
            }
            aktiv = true;
            direkt = true;
            woerter = 0;
            umbrueche = 0;
        }

        private void offenAusgeben(double deckel) {
            for (int k = 0; k < offenAnzahl; k++) {
                ausgeben(offenDaten[3 * k], offenDaten[3 * k + 1], offenDaten[3 * k + 2], false, deckel);
            }
        }

        private void fuellwort() {
            offenLeeren();
            direkt = false;
            if (aktiv) {
                woerter++;
                if (woerter > MAX_FUELLWOERTER) {
                    aktiv = false;
                }
            }
        }

        private void punkt(int index) {
            zeilenanfang = false;
            offenLeeren();
            boolean abkuerzung = index == laufEnde && laufBuchstaben >= 1 && laufBuchstaben <= MAX_ABKUERZUNG;
            if (aktiv && !direkt && !abkuerzung) {
                aktiv = false;
            }
        }

        private void satzende() {
            zeilenanfang = false;
            offenLeeren();
            aktiv = false;
            direkt = false;
        }

        private void zeilenumbruch() {
            zeilenanfang = true;
            offenLeeren();
            if (aktiv) {
                umbrueche++;
                direkt = false;
                if (umbrueche > 1) {
                    aktiv = false;
                }
            }
        }

        /** Leerraum und Sonstiges: neutral; fuer offene Segmente gilt die Lueckenregel. */
        private void sonstiges(int cp) {
            if (!istLeerraum(cp)) {
                zeilenanfang = false;
            }
            if (offenAnzahl == 0) {
                return;
            }
            if (cp == '(' || cp == '[') {
                offenKlammern++;
                if (offenKlammern > 1) {
                    offenLeeren();
                }
            } else if (cp == ',' || cp == ';') {
                offenKomma = Math.min(offenKomma + 1, 2);
                if (offenKlammern > 0) {
                    offenKlammerVorKomma = true;
                }
            } else if (cp == '/') {
                offenSchraeg = true;
            } else if (!istLeerraum(cp)) {
                offenLeeren();
            }
        }

        /** Ein Segment [start, end) mit {@code anzahl} Ziffern wurde gelesen. */
        private void segment(int start, int end, int anzahl) {
            if (aktiv) {
                ausgeben(start, end, anzahl, direkt, 1.0);
                direkt = false;
                woerter = 0;
                umbrueche = 0;
            } else {
                if (offenAnzahl > 0 && offenKlammern > 0) {
                    offenLeeren();
                }
                if (3 * (offenAnzahl + 1) > offenDaten.length) {
                    offenDaten = Arrays.copyOf(offenDaten, offenDaten.length * 2);
                }
                offenDaten[3 * offenAnzahl] = start;
                offenDaten[3 * offenAnzahl + 1] = end;
                offenDaten[3 * offenAnzahl + 2] = anzahl;
                offenAnzahl++;
                offenKlammern = 0;
                offenKomma = 0;
                offenSchraeg = false;
                offenKlammerVorKomma = false;
                offenUnd = false;
            }
        }

        private void ausgeben(int start, int end, int anzahl, boolean mitDirektemKontext, double deckel) {
            double konfidenz;
            String quelle;
            if (anzahl == NEUN) {
                if (art.isValid(ziffernwerte(start, end))) {
                    konfidenz = mitDirektemKontext ? art.confidenceDirekt() : CONFIDENCE_KONTEXT_ENTFERNT;
                    quelle = art.source();
                } else {
                    konfidenz = CONFIDENCE_PRUEFZIFFER_UNGUELTIG;
                    quelle = art.sourcePruefzifferUngueltig();
                }
            } else {
                konfidenz = CONFIDENCE_LAENGE_ABWEICHEND;
                quelle = art.sourceLaengeAbweichend();
            }
            konfidenz = Math.min(konfidenz, deckel);
            funde.add(new Finding(art.type(), text.substring(start, end), start, end, konfidenz, quelle));
        }

        // ------------------------------------------------------------------ Laeufe, Teilstuecke, Woerter

        /** Verarbeitet den Lauf ab {@code start}; liefert die Position dahinter. */
        private int lauf(int start) {
            fensterLaenge = 0;
            teilstueckAnfaenge = 0;
            buchstaben = 0;
            kontextImLauf = direkt; // direkt: ein Nachsatzwort darf folgen
            seitKontext = 0;
            restBuchstabe = false;
            restWiderspruch = false;
            boolean hatBuchstabe = false;
            boolean imTeilstueck = false;
            int p = start;
            while (p < n) {
                int cp = text.codePointAt(p);
                boolean unsichtbar = istUnsichtbar(cp);
                if (!unsichtbar && !istBuchstabe(cp)) {
                    break;
                }
                if (unsichtbar) {
                    if (imTeilstueck) {
                        teilstueckEnde();
                        imTeilstueck = false;
                    }
                } else {
                    if (Character.isLetter(cp)) {
                        hatBuchstabe = true;
                        if (kontextImLauf) {
                            restBuchstabe = true;
                        }
                    }
                    buchstaben++;
                    if (kontextImLauf) {
                        seitKontext++;
                    }
                    anhaengen(cp, !imTeilstueck);
                    imTeilstueck = true;
                }
                p += Character.charCount(cp);
            }
            if (imTeilstueck) {
                teilstueckEnde();
            }
            laufEnde = p;
            laufBuchstaben = hatBuchstabe ? buchstaben : 0;
            if (!hatBuchstabe) {
                return p; // nur unsichtbare Zeichen oder alleinstehende Kombinationszeichen: neutral
            }
            zeilenanfang = false;
            int vorwort = suffixLaenge(art.vorwoerter().isEmpty() ? null : art.vorwoerter(), vorwortLaenge);
            if (vorwort > 0 && nachsatzFolgt(p)) {
                if (buchstaben > vorwort) {
                    offenLeeren();
                }
                kontextwort();
            } else if (kontextImLauf) {
                if (seitKontext > 0 && restBuchstabe) {
                    fuellwort();
                }
            } else if (istUnd() && offenAnzahl > 0 && offenKlammern == 0 && !offenUnd) {
                offenUnd = true;
            } else {
                fuellwort();
            }
            return p;
        }

        private void anhaengen(int cp, boolean neuesTeilstueck) {
            if (fensterLaenge == FENSTER) {
                System.arraycopy(fenster, 1, fenster, 0, FENSTER - 1);
                fensterLaenge--;
                teilstueckAnfaenge >>>= 1;
            }
            fenster[fensterLaenge] = vergleichszeichen(cp);
            if (neuesTeilstueck) {
                teilstueckAnfaenge |= 1 << fensterLaenge;
            }
            fensterLaenge++;
        }

        /** Das Teilstueck endet; erkennt ein Kontextwort oder verfolgt das Nachsatzwort. */
        private void teilstueckEnde() {
            int treffer = suffixLaenge(art.kontextwoerter(), kontextLaenge);
            if (treffer > 0) {
                if (buchstaben > treffer) {
                    offenLeeren(); // Teilstuecke davor wirken wie ein Fuellwort
                }
                kontextwort();
                kontextImLauf = true;
                seitKontext = 0;
                restBuchstabe = false;
                restWiderspruch = false;
                return;
            }
            if (!kontextImLauf || restWiderspruch) {
                return;
            }
            int s = seitKontext;
            int von = fensterLaenge - s;
            if (s <= NUMMER.length() && (gleich(NR, fenster, von, s) || gleich(NUMMER, fenster, von, s))) {
                seitKontext = 0; // Nachsatzwort, kein Fuellwort
                restBuchstabe = false;
            } else if (!(praefix(NR, fenster, von, s) || praefix(NUMMER, fenster, von, s))) {
                restWiderspruch = true;
            }
        }

        /**
         * Laenge des laengsten Fenstersuffixes, das an einem Teilstueckanfang beginnt und einem Eintrag von
         * {@code woerter} gleicht; 0, wenn keiner passt.
         */
        private int suffixLaenge(Set<String> woerter, boolean[] laengen) {
            if (woerter == null) {
                return 0;
            }
            int m = teilstueckAnfaenge;
            while (m != 0) {
                int k = Integer.numberOfTrailingZeros(m);
                m &= m - 1;
                int len = fensterLaenge - k;
                char c0 = fenster[k];
                if (len < laengen.length && laengen[len] && c0 < 128 && kontextAnfang[c0]
                        && woerter.contains(new String(fenster, k, len))) {
                    return len;
                }
            }
            return 0;
        }

        /** Besteht der Lauf genau aus dem Wort {@code und}? */
        private boolean istUnd() {
            return buchstaben == UND.length() && fensterLaenge == UND.length()
                    && gleich(UND, fenster, 0, UND.length());
        }

        /**
         * Bilden die ersten Teilstuecke des Laufs hinter {@code ende} (nach hoechstens einem Nachsatztrenner) das Wort
         * {@code nr} oder {@code nummer}? Liest hoechstens sechs Buchstaben und bricht beim ersten Widerspruch ab.
         */
        private boolean nachsatzFolgt(int ende) {
            int j = ende;
            if (j < n && istNachsatzTrenner(text.charAt(j))) {
                j++;
            }
            char[] puffer = new char[NUMMER.length()];
            int len = 0;
            boolean imTeilstueck = false;
            while (j < n) {
                int cp = text.codePointAt(j);
                if (istUnsichtbar(cp)) {
                    if (imTeilstueck) {
                        if (gleich(NR, puffer, 0, len) || gleich(NUMMER, puffer, 0, len)) {
                            return true;
                        }
                        imTeilstueck = false;
                    }
                } else if (istBuchstabe(cp)) {
                    if (len == puffer.length) {
                        return false;
                    }
                    puffer[len++] = vergleichszeichen(cp);
                    if (!(praefix(NR, puffer, 0, len) || praefix(NUMMER, puffer, 0, len))) {
                        return false;
                    }
                    imTeilstueck = true;
                } else {
                    break;
                }
                j += Character.charCount(cp);
            }
            return imTeilstueck && (gleich(NR, puffer, 0, len) || gleich(NUMMER, puffer, 0, len));
        }

        // ------------------------------------------------------------------ Ziffernketten

        /** Verarbeitet die Kette ab {@code start}; liefert die Position, ab der weitergelesen wird. */
        private int ziffernkette(int start) {
            boolean anfang = zeilenanfang;
            zeilenanfang = false;
            int blockEnde = ziffernEnde(start);
            int ersterBlock = blockEnde - start;
            int anzahl = ersterBlock;
            int neunEnde = -1;
            int besteEnde = -1;
            int besteAnzahl = 0;
            if (anzahl == NEUN) {
                neunEnde = blockEnde;
            } else if (anzahl >= MIN_LAENGE && anzahl <= MAX_LAENGE) {
                besteEnde = blockEnde;
                besteAnzahl = anzahl;
            }
            int p = blockEnde;
            while (neunEnde < 0 && anzahl <= MAX_LAENGE && p + 1 < n && istTrenner(text.charAt(p))
                    && istZiffer(text.charAt(p + 1))) {
                int q = p + 1;
                int c = 0;
                while (q + c < n && istZiffer(text.charAt(q + c)) && anzahl + c <= MAX_LAENGE) {
                    c++;
                }
                if (anzahl + c > MAX_LAENGE) {
                    break;
                }
                anzahl += c;
                p = q + c;
                if (anzahl == NEUN) {
                    neunEnde = p;
                } else if (anzahl >= MIN_LAENGE) {
                    besteEnde = p;
                    besteAnzahl = anzahl;
                }
            }
            if (neunEnde >= 0) {
                segment(start, neunEnde, NEUN);
                return neunEnde;
            }
            if (besteEnde >= 0) {
                segment(start, besteEnde, besteAnzahl);
                return besteEnde;
            }
            if (anfang && ersterBlock <= 2 && blockEnde < n && text.charAt(blockEnde) == '.'
                    && (blockEnde + 1 >= n || !istZiffer(text.charAt(blockEnde + 1)))) {
                return blockEnde + 1; // nummerierte Liste: Ziffern samt Punkt sind neutral
            }
            fuellwort();
            return blockEnde;
        }

        private int ziffernEnde(int start) {
            int p = start;
            while (p < n && istZiffer(text.charAt(p))) {
                p++;
            }
            return p;
        }

        /** Die neun Ziffernwerte des Segments [start, end), Trenner uebersprungen. */
        private int[] ziffernwerte(int start, int end) {
            int[] d = new int[NEUN];
            int k = 0;
            for (int p = start; p < end; p++) {
                char c = text.charAt(p);
                if (istZiffer(c)) {
                    d[k++] = c - '0';
                }
            }
            return d;
        }
    }
}
