package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Die kontextfreien Formen von Tokens (Feature geheimnisse-erkennung, Anforderung D-10, D-11; Plan Kapitel 1.5):
 * JWT, Koerper eines privaten PEM-Schluessels und Praefix-Token. Ohne Kontextwort wird nur erkannt, was die Form
 * eindeutig macht; Hashes, UUIDs, Base64 ohne Kontext, Fingerprints und Commit-IDs sind kein Fund (D-10). Die Klasse
 * ist paketintern, hat keinen Zustand und liest den Text von Hand (kein regulaerer Ausdruck, kein Netzwerk- oder
 * Dateizugriff). Typ {@code TOKEN}, Konfidenz 0,95 (sicher), Quellen {@link TokenRecognizer#QUELLE_JWT},
 * {@link TokenRecognizer#QUELLE_PEM} und {@link TokenRecognizer#QUELLE_PRAEFIX}. Der {@code TokenRecognizer}
 * vereinigt diese Kandidaten mit denen der Kontextregel; die Kontextregel wird von dieser Klasse nicht beruehrt.
 *
 * <p><b>JWT (D-11).</b> Drei Teile aus {@code A-Z a-z 0-9 _ -}, durch genau zwei Punkte getrennt; die ersten zwei Teile
 * beginnen mit {@code eyJ}, jeder Teil hat mindestens 8 Zeichen. Davor steht kein Zeichen aus {@code A-Z a-z 0-9 _ . -}
 * und kein Buchstabe oder keine Ziffer, dahinter ebenfalls nicht; der Fund ist das ganze JWT. Praezisierung in
 * Richtung Schutz: Alle Punkte unmittelbar hinter dem dritten Teil (Satzende, Auslassung {@code ...}) gehoeren nicht
 * zum JWT und verhindern den Fund nicht; ein vierter Teil verhindert ihn. Gelesen wird jeder Lauf aus
 * JWT-Zeichen und Punkt einmal; danach geht es hinter dem Lauf weiter.
 *
 * <p><b>PEM-Koerper (D-11).</b> Eine Zeile mit fuenf Bindestrichen, dem Wort BEGIN, einer Art und fuenf Bindestrichen,
 * deren Art (hoechstens 40 Zeichen aus Grossbuchstaben und Leerzeichen) auf PRIVATE KEY oder PRIVATE KEY BLOCK endet
 * (private Schluessel, auch verschluesselt, OpenSSH, PGP). Die Zeile steht am Zeilenanfang (davor nur Leerraum) und
 * hat dahinter nur Leerraum. <b>Der Fund ist der Koerper</b>: alles zwischen dieser Zeile und der END-Zeile samt
 * Zeilenumbruechen (LF, CR LF, CR), auch Kopfzeilen wie {@code Proc-Type} und {@code DEK-Info}; die BEGIN- und die
 * END-Zeile bleiben lesbar. Der Fund beginnt am ersten Zeichen, das kein Leerraum ist, und endet am letzten solchen
 * Zeichen vor der END-Zeile. Fehlt die END-Zeile (die naechste Zeile, die mit fuenf Bindestrichen beginnt, ist keine
 * END-Zeile, oder der Text endet), reicht der Fund von der ersten Koerperzeile ueber optionale Kopfzeilen der Form
 * {@code Name: Wert} und eine Leerzeile bis zur letzten zusammenhaengenden Base64-Zeile (Zeichen
 * {@code A-Z a-z 0-9 + / =}, hoechstens 100 Zeichen je Zeile). Oeffentliche Arten (Zertifikat, oeffentlicher
 * Schluessel, Zertifikatsanforderung) sind kein Fund. Ein leerer Koerper ist kein Fund; ebenso ein Koerper ohne
 * Schluesselinhalt, also ohne eine Folge von mindestens 16 Base64-Zeichen in Folge (Platzhalter wie
 * {@code ...} oder eine Beschreibung zwischen BEGIN und END; Praezisierung in Richtung weniger Fehlalarme). Jede Zeile
 * wird hoechstens zweimal gelesen, ein Block endet an der naechsten Zeile mit fuenf Bindestrichen: Bloecke
 * ueberlappen nicht, die Laufzeit ist linear (auch bei wiederholter BEGIN-Zeile ohne END).
 *
 * <p><b>PEM zwischen Markierungen (Entscheidung nach T-7, Durchlauf 1).</b> Zusaetzlich zur Zeilenform: Steht
 * irgendwo im Text (nicht nur am Zeilenanfang) die BEGIN-Markierung eines privaten Schluessels und danach die
 * passende END-Markierung (gleiche Art), ist der Bereich dazwischen ein Fund, gleich was vor der Markierung steht
 * ({@code > }, {@code # }, {@code KEY="}, Tabellenzelle, Code-String) und wie die Koerperzeilen getrennt sind (echter
 * Zeilenumbruch, die Zeichenfolgen {@code \n} oder {@code \r\n} aus zwei Zeichen, Leerzeichen, gar nichts). Der Fund
 * beginnt hinter der BEGIN-Markierung (fuehrender Leerraum und {@code \n} gehoeren nicht dazu) und endet vor der
 * END-Markierung (folgender Leerraum und {@code \n} ebenso); Zeichen dazwischen, die zu einem Zeilenpraefix
 * gehoeren, werden mitersetzt (Ueberschutz). Bedingungen: keine weitere BEGIN-Markierung dazwischen, hoechstens
 * 16 KiB Abstand und ueberwiegend Schluesselinhalt (T-7, Durchlauf 3): Nach Abzug von Leerraum, Zeilenumbruechen und
 * den Zeichenfolgen {@code \n} und {@code \r} liegen mindestens 70 Prozent der uebrigen Zeichen in Base64-Laeufen von
 * mindestens 16 Zeichen (ein maskierter Schraegstrich {@code \/} zaehlt als Base64-Zeichen); sonst ist es ein
 * Erklaertext oder Platzhalter, kein Fund. Zeilenpraefixe ({@code > }, {@code # }, {@code //}), Anfuehrungszeichen und
 * {@code +} fallen nicht ins Gewicht; ein Erklaertext ueber mehrere Absaetze mit einem langen Wort, Pfad oder einer URL
 * erreicht hoechstens etwa 21 Prozent (gemessen). Die Schwelle steht in {@link #SCHLUESSEL_ANTEIL_PROZENT}. Beide
 * Formen zaehlen nicht doppelt: ueberschneiden sich ihre Funde, entsteht ein Fund ueber die Vereinigung (linear:
 * Start und Ende der Gruppe werden gefuehrt, der Fund entsteht einmal je Gruppe). Jede Markierung wird einmal
 * gesucht, die naechste END-Markierung nur dann neu, wenn die zuletzt gefundene hinter uns liegt; jede Stelle wird
 * konstant oft gelesen, die Laufzeit ist linear.
 *
 * <p><b>Praefix-Token (D-11).</b> Ein Wort, das mit einem Praefix der geschlossenen, pflegbaren Liste
 * {@link #PRAEFIXE} beginnt (Gross-/Kleinschreibung zaehlt), dessen Rest nur aus {@code A-Z a-z 0-9 _ -} besteht,
 * mindestens eine Ziffer enthaelt und das insgesamt mindestens 20 Zeichen hat. Davor steht kein Buchstabe, keine
 * Ziffer, kein {@code _} und kein {@code -}, dahinter ebenfalls nicht. Praezisierung in Richtung weniger
 * Fehlalarme (T-7, gemessen an der eigenen Stichprobe): Bei den fuenf klassischen GitHub-Praefixen
 * ({@link #NUR_ALPHANUMERISCH}) besteht der Rest nur aus Buchstaben und Ziffern; ein Bezeichner wie
 * {@code Praefix_name_2} ist kein Token. Gelesen wird jeder Lauf einmal.
 *
 * <p><b>Ueberlappungsfrei.</b> Die Kandidaten sind aufsteigend und ueberlappungsfrei: Liegt ein JWT oder Praefix-Token
 * in einem PEM-Koerper oder ein Praefix-Token in einem JWT, entfaellt der innere Kandidat (die Position bleibt
 * durch den aeusseren geschuetzt).
 *
 * <p><b>Bekannte Grenzen</b> (Tests {@code bekannteGrenze...} in {@code TokenGrenzenTest}): G-16 (Hersteller-Tokens
 * ausserhalb der Liste nur mit Kontextwort), G-17 (PEM-Koerper mehrzeilig, die Fundtabelle zeigt eine Zelle),
 * G-18 (liegt ein Fund eines bestehenden Erkenners ganz im PEM-Koerper, zerfaellt der Fund in Stuecke), dazu:
 * <ul>
 * <li>PEM: eine BEGIN-Markierung ohne END-Markierung, die nicht am Zeilenanfang steht (Text davor), ist kein Fund;
 * ebenso BEGIN und END mehr als 16 KiB auseinander, eine weitere BEGIN-Markierung dazwischen, ein Koerper ohne
 * 16 Base64-Zeichen in Folge, ein Bereich mit weniger als 70 Prozent Schluesselinhalt (Erklaertext) und eine
 * END-Markierung anderer Art, auch die einer oeffentlichen Art zwischen BEGIN und END privater Art. Umgekehrt ist
 * ein Satz, der fast nur aus einem Wort von 16 Buchstaben oder mehr besteht (mindestens 70 Prozent der Zeichen
 * zwischen den Markierungen), ein Fund (Fehlalarm, Ueberschutz), auch ein Satz mit einem Pfad wie
 * {@code /etc/ssl/private/...} (Test {@code bekannteGrenze_langesWortZwischenDenMarkierungen_nurAlleinEinFund}).
 * Ein verschluesselter Schluessel mit Kopfzeilen ({@code Proc-Type}, {@code DEK-Info}) zwischen Markierungen, dessen
 * Zeilen durch Markup getrennt sind ({@code &#13;&#10;}, {@code <br />} mit Zitat, {@code " + Environment.NewLine}),
 * liegt unter der 70-Prozent-Schwelle und ist kein Fund, am Zeilenanfang und mit {@code \n} dagegen schon (Test
 * {@code bekannteGrenze_pemZwischenMarkierungenMitKopfzeilenUndMarkupTrennern_unterSchwelle_keinFund}; Entscheidung
 * des Nutzers vom 28.11.2026, Behebung offen: Markup-Trenner wie Zeilenumbrueche werten, Standard-Kopfzeilen nicht
 * mitzaehlen). Ein PuTTY-Schluessel (Datei
 * {@code PuTTY-User-Key-File}, ohne BEGIN-Markierung) wird nicht erkannt.</li>
 * <li>JWT: vier Teile oder Zeichen ausserhalb von {@code A-Z a-z 0-9 _ -} in den Teilen (zum Beispiel gekuerzt mit
 * einem Auslassungszeichen), ein vierter Teil wie {@code .Wort} unmittelbar dahinter, JWE mit fuenf Teilen und ein
 * durch Zeilenumbruch gebrochenes JWT. Punkte am Ende gehoeren nicht zum JWT (Satzende, Auslassung).</li>
 * <li>Praefix-Token: unter 20 Zeichen, ohne Ziffer (etwa 3 % der {@code glpat-}-Tokens) oder ein Bezeichner mit
 * {@code glpat-}, {@code xoxb-}, {@code xoxp-} oder {@code github_pat_} am Anfang und einer Ziffer (Fehlalarm).</li>
 * <li>Typwechsel (Ersatz eines Funds, keine Position wird frei): (1) Ein PASSWORT-Fund 0,80, dessen Wert ein
 * Praefix-Token ist, wird durch den TOKEN-Fund 0,95 gleichen Bereichs ersetzt; ebenso {@code Passwort ist <JWT>}, das
 * bis T-6 einen PASSWORT-Fund 0,80 lieferte. (2) Ein PASSWORT-Fund, der ganz in einem PEM-Fund zwischen den
 * Markierungen liegt, geht in diesem auf. Beides zaehlt der Vergleichstest {@code TokenFormenVergleichT6Test}.</li>
 * </ul>
 */
final class TokenFormen {

    /** Konfidenz aller kontextfreien Formen (D-9: sicher). */
    static final double KONFIDENZ = 0.95;

    /**
     * Die Praefixe der Praefix-Token: geschlossene, pflegbare Liste (D-11); kurz und herstellerdefiniert, Gross- und
     * Kleinschreibung zaehlt. Andere Hersteller sind nur mit Kontextwort erfasst (G-16).
     */
    private static final List<String> PRAEFIXE = List.of("ghp_", "gho_", "ghu_", "ghs_", "ghr_", "github_pat_",
            "glpat-", "xoxb-", "xoxp-");

    /**
     * Die Praefixe aus {@link #PRAEFIXE}, deren Rest nur aus Buchstaben und Ziffern besteht (klassische GitHub-Token;
     * ein {@code _} oder {@code -} im Rest ist dort kein Token, sondern ein Bezeichner wie
     * {@code Praefix_name_2}; Praezisierung in Richtung weniger Fehlalarme, gemessen in T-7).
     */
    private static final List<String> NUR_ALPHANUMERISCH = List.of("ghp_", "gho_", "ghu_", "ghs_", "ghr_");

    /** Mindestlaenge eines Praefix-Tokens einschliesslich Praefix (D-11). */
    private static final int PRAEFIX_MINDESTLAENGE = 20;

    /** Anfang der ersten zwei JWT-Teile. */
    private static final String JWT_KOPF = "eyJ";

    /** Mindestlaenge jedes JWT-Teils. */
    private static final int JWT_TEIL_MINDESTLAENGE = 8;

    /** Fuenf Bindestriche: Beginn der BEGIN- und END-Zeilen. */
    private static final String STRICHE = "-----";

    private static final String BEGIN_ZEILE = STRICHE + "BEGIN ";
    private static final String END_ZEILE = STRICHE + "END ";

    /** Endungen der Art eines privaten Schluessels (D-11). */
    private static final List<String> PRIVATE_ARTEN = List.of("PRIVATE KEY", "PRIVATE KEY BLOCK");

    /** Hoechstlaenge der Art in der BEGIN-Zeile. */
    private static final int ART_HOECHSTLAENGE = 40;

    /** Hoechstlaenge einer Base64-Zeile, wenn die END-Zeile fehlt (D-11). */
    private static final int BASE64_ZEILE_HOECHST = 100;

    /** Mindestlaenge der Base64-Folge, die einen Koerper zum Schluesselinhalt macht. */
    private static final int SCHLUESSEL_LAUF = 16;

    /**
     * Mindestanteil (in Prozent) der Zeichen zwischen den Markierungen, die in Base64-Laeufen von mindestens
     * {@link #SCHLUESSEL_LAUF} Zeichen liegen: sonst ist es ein Erklaertext, kein Schluessel (T-7, Durchlauf 3).
     * Gemessen an echten Schluesseln: 50,7 bis 100 Prozent (nicht 79 bis 100 Prozent, wie es zuvor hier stand),
     * Erklaertexte mit einem langen Wort bei hoechstens 21 Prozent. Schluessel mit Kopfzeilen ({@code Proc-Type},
     * {@code DEK-Info}) und Markup-Trennern ({@code &#13;&#10;}, {@code <br />}) fallen unter 70 Prozent und bleiben
     * ungeschuetzt: bekannte Grenze, siehe {@code TokenGrenzenTest}.
     */
    private static final int SCHLUESSEL_ANTEIL_PROZENT = 70;

    /** Hoechster Abstand zwischen BEGIN- und END-Markierung bei der Form zwischen Markierungen (16 KiB). */
    private static final int PEM_FENSTER = 16 * 1024;

    private TokenFormen() {
    }

    /**
     * Die Kandidaten aller drei Formen, aufsteigend sortiert und ueberlappungsfrei.
     *
     * @param text der Text, nicht {@code null}
     */
    static List<Finding> finde(String text) {
        List<Finding> alle = new ArrayList<>();
        jwt(text, alle);
        pem(text, alle);
        praefix(text, alle);
        if (alle.size() < 2) {
            return List.copyOf(alle);
        }
        alle.sort(Comparator.comparingInt(Finding::start).thenComparing(Comparator.comparingInt(Finding::end)
                .reversed()));
        List<Finding> frei = new ArrayList<>(alle.size());
        int belegtBis = 0;
        for (Finding f : alle) {
            if (f.end() <= belegtBis) {
                continue;
            }
            if (f.start() < belegtBis) {
                frei.add(new Finding(f.type(), text.substring(belegtBis, f.end()), belegtBis, f.end(), f.confidence(),
                        f.source()));
            } else {
                frei.add(f);
            }
            belegtBis = f.end();
        }
        return List.copyOf(frei);
    }

    // ------------------------------------------------------------------ JWT

    private static boolean istJwtZeichen(char c) {
        return GeheimnisZeichen.istAsciiBuchstabeOderZiffer(c) || c == '_' || c == '-' || c == '.';
    }

    private static boolean istPraefixZeichen(char c) {
        return GeheimnisZeichen.istAsciiBuchstabeOderZiffer(c) || c == '_' || c == '-';
    }

    /** Ob vor {@code pos} ein Zeichen steht, das einen Lauf nach links fortsetzt (Buchstabe, Ziffer, kombinierend). */
    private static boolean fortsetzungVor(String text, int pos) {
        return pos > 0 && GeheimnisZeichen.istWortkoerperZeichen(text.codePointBefore(pos));
    }

    /** Ob an {@code pos} ein Zeichen steht, das einen Lauf nach rechts fortsetzt (Buchstabe, Ziffer, kombinierend). */
    private static boolean fortsetzungAn(String text, int pos) {
        return pos < text.length() && GeheimnisZeichen.istWortkoerperZeichen(text.codePointAt(pos));
    }

    private static void jwt(String text, List<Finding> ziel) {
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (!istJwtZeichen(text.charAt(i))) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < n && istJwtZeichen(text.charAt(j))) {
                j++;
            }
            // Der Lauf beginnt hinter einem Zeichen, das kein JWT-Zeichen ist; ein Buchstabe ausserhalb von ASCII davor
            // oder dahinter verhindert den Fund ebenfalls.
            if (text.startsWith(JWT_KOPF, i) && !fortsetzungVor(text, i) && !fortsetzungAn(text, j)) {
                pruefeJwt(text, i, j, ziel);
            }
            i = j;
        }
    }

    /** Prueft den Lauf {@code [von, bis)} als JWT; Punkte am Ende (Satzende, Auslassung) gehoeren nicht zum JWT. */
    private static void pruefeJwt(String text, int von, int bis, List<Finding> ziel) {
        int ende = bis;
        while (ende > von && text.charAt(ende - 1) == '.') {
            ende--;
        }
        int punkt1 = -1;
        int punkt2 = -1;
        for (int k = von; k < ende; k++) {
            if (text.charAt(k) != '.') {
                continue;
            }
            if (punkt1 < 0) {
                punkt1 = k;
            } else if (punkt2 < 0) {
                punkt2 = k;
            } else {
                return; // mehr als zwei Punkte
            }
        }
        if (punkt2 < 0) {
            return;
        }
        boolean laenge = punkt1 - von >= JWT_TEIL_MINDESTLAENGE && punkt2 - punkt1 - 1 >= JWT_TEIL_MINDESTLAENGE
                && ende - punkt2 - 1 >= JWT_TEIL_MINDESTLAENGE;
        if (laenge && text.startsWith(JWT_KOPF, punkt1 + 1)) {
            ziel.add(new Finding(GeheimnisTypen.TOKEN, text.substring(von, ende), von, ende, KONFIDENZ,
                    TokenRecognizer.QUELLE_JWT));
        }
    }

    // ------------------------------------------------------------------ Praefix-Token

    private static void praefix(String text, List<Finding> ziel) {
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (!istPraefixZeichen(text.charAt(i))) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < n && istPraefixZeichen(text.charAt(j))) {
                j++;
            }
            if (j - i >= PRAEFIX_MINDESTLAENGE && !fortsetzungVor(text, i) && !fortsetzungAn(text, j)) {
                String praefix = praefixAn(text, i);
                if (praefix != null && restPasst(text, i + praefix.length(), j, NUR_ALPHANUMERISCH.contains(praefix))) {
                    ziel.add(new Finding(GeheimnisTypen.TOKEN, text.substring(i, j), i, j, KONFIDENZ,
                            TokenRecognizer.QUELLE_PRAEFIX));
                }
            }
            i = j;
        }
    }

    private static String praefixAn(String text, int pos) {
        for (String p : PRAEFIXE) {
            if (text.startsWith(p, pos)) {
                return p;
            }
        }
        return null;
    }

    /** Ob der Rest {@code [von, bis)} eine Ziffer enthaelt und, wenn verlangt, nur aus Buchstaben und Ziffern ist. */
    private static boolean restPasst(String text, int von, int bis, boolean nurAlphanumerisch) {
        boolean ziffer = false;
        for (int k = von; k < bis; k++) {
            char c = text.charAt(k);
            if (GeheimnisZeichen.istAsciiZiffer(c)) {
                ziffer = true;
            } else if (nurAlphanumerisch && (c == '_' || c == '-')) {
                return false;
            }
        }
        return ziffer;
    }

    // ------------------------------------------------------------------ PEM

    /** Index des ersten Zeilenumbruchzeichens ab {@code pos} oder die Textlaenge. */
    private static int zeilenEnde(String text, int pos) {
        int k = pos;
        while (k < text.length() && !GeheimnisZeichen.istZeilenumbruch(text.charAt(k))) {
            k++;
        }
        return k;
    }

    /** Erstes Zeichen ab {@code von}, das kein Leerraum ist, hoechstens bis {@code bis}. */
    private static int ueberspringeLeerraum(String text, int von, int bis) {
        int k = von;
        while (k < bis && GeheimnisZeichen.istLeerraum(text.charAt(k))) {
            k++;
        }
        return k;
    }

    /** Ende hinter dem letzten Zeichen in {@code [von, bis)}, das kein Leerraum ist (mindestens {@code von}). */
    private static int ohneLeerraumHinten(String text, int von, int bis) {
        int k = bis;
        while (k > von && GeheimnisZeichen.istLeerraum(text.charAt(k - 1))) {
            k--;
        }
        return k;
    }

    /**
     * Beide PEM-Formen (Zeilenform und Form zwischen Markierungen); Funde, die sich ueberschneiden, werden zu einem
     * Fund vereinigt.
     */
    private static void pem(String text, List<Finding> ziel) {
        List<Finding> alle = new ArrayList<>();
        pemZeilenform(text, alle);
        pemZwischenMarkierungen(text, alle);
        if (alle.size() > 1) {
            alle.sort(Comparator.comparingInt(Finding::start));
        }
        // Start und Ende der laufenden Gruppe als int; das Finding entsteht einmal je Gruppe (linear).
        int start = -1;
        int ende = -1;
        for (Finding f : alle) {
            if (ende >= 0 && f.start() < ende) {
                ende = Math.max(ende, f.end());
            } else {
                if (ende >= 0) {
                    ziel.add(pemFund(text, start, ende));
                }
                start = f.start();
                ende = f.end();
            }
        }
        if (ende >= 0) {
            ziel.add(pemFund(text, start, ende));
        }
    }

    private static Finding pemFund(String text, int start, int ende) {
        return new Finding(GeheimnisTypen.TOKEN, text.substring(start, ende), start, ende, KONFIDENZ,
                TokenRecognizer.QUELLE_PEM);
    }

    /**
     * Form zwischen Markierungen: eine BEGIN-Markierung privater Art irgendwo im Text und danach die passende
     * END-Markierung (gleiche Art), ohne weitere BEGIN-Markierung dazwischen und hoechstens {@link #PEM_FENSTER}
     * Zeichen dahinter. Der Fund ist der Bereich dazwischen ohne fuehrenden und folgenden Leerraum und ohne die
     * Zeichenfolgen {@code \n} und {@code \r} (zwei Zeichen) an den Raendern. Jede Markierung wird einmal gesucht und
     * die naechste END-Markierung nur dann neu, wenn die zuletzt gefundene hinter uns liegt: die Laufzeit ist linear.
     */
    private static void pemZwischenMarkierungen(String text, List<Finding> ziel) {
        int begin = text.indexOf(BEGIN_ZEILE);
        int endPos = -1;
        boolean keineEnde = false;
        while (begin >= 0) {
            int naechsteBegin = text.indexOf(BEGIN_ZEILE, begin + 1);
            int artEnde = privateMarkerArtEnde(text, begin);
            if (artEnde >= 0) {
                int markerEnde = artEnde + STRICHE.length();
                while (!keineEnde && endPos < markerEnde) {
                    endPos = text.indexOf(END_ZEILE, markerEnde);
                    keineEnde = endPos < 0;
                }
                if (!keineEnde && (naechsteBegin < 0 || endPos < naechsteBegin)
                        && endPos - markerEnde <= PEM_FENSTER && istPassendeEnde(text, begin, artEnde, endPos)) {
                    meldeZwischenBereich(text, markerEnde, endPos, ziel);
                }
            }
            begin = naechsteBegin;
        }
    }

    /** Ob an {@code endPos} die END-Markierung steht, die zur BEGIN-Markierung an {@code begin} passt. */
    private static boolean istPassendeEnde(String text, int begin, int artEnde, int endPos) {
        int artVon = begin + BEGIN_ZEILE.length();
        int artLaenge = artEnde - artVon;
        int endArt = endPos + END_ZEILE.length();
        return text.regionMatches(endArt, text, artVon, artLaenge) && text.startsWith(STRICHE, endArt + artLaenge);
    }

    private static void meldeZwischenBereich(String text, int markerEnde, int endPos, List<Finding> ziel) {
        int von = markerEnde;
        int bis = endPos;
        while (von < bis) {
            if (istLeerOderUmbruch(text.charAt(von))) {
                von++;
            } else if (istMaskierterUmbruchAn(text, von, bis)) {
                von += 2;
            } else {
                break;
            }
        }
        while (bis > von) {
            if (istLeerOderUmbruch(text.charAt(bis - 1))) {
                bis--;
            } else if (bis - 2 >= von && istMaskierterUmbruchAn(text, bis - 2, bis)) {
                bis -= 2;
            } else {
                break;
            }
        }
        if (bis > von && istUeberwiegendSchluesselinhalt(text, von, bis)) {
            ziel.add(new Finding(GeheimnisTypen.TOKEN, text.substring(von, bis), von, bis, KONFIDENZ,
                    TokenRecognizer.QUELLE_PEM));
        }
    }

    /**
     * Ob in {@code [von, bis)} mindestens {@link #SCHLUESSEL_ANTEIL_PROZENT} Prozent der Zeichen in Base64-Laeufen von
     * mindestens {@link #SCHLUESSEL_LAUF} Zeichen liegen. Nicht gezaehlt werden Leerraum, Zeilenumbrueche und die
     * Zeichenfolgen {@code \n} und {@code \r} (zwei Zeichen); sie beenden einen Lauf. Bei {@code \/} (JSON) zaehlt nur
     * der Schraegstrich, der Lauf geht weiter. Eine Schleife, linear.
     */
    private static boolean istUeberwiegendSchluesselinhalt(String text, int von, int bis) {
        int gezaehlt = 0;
        int abgedeckt = 0;
        int lauf = 0;
        int k = von;
        while (k < bis) {
            char c = text.charAt(k);
            if (istLeerOderUmbruch(c)) {
                lauf = 0;
                k++;
            } else if (istMaskierterUmbruchAn(text, k, bis)) {
                lauf = 0;
                k += 2;
            } else if (c == (char) 0x5C && k + 1 < bis && text.charAt(k + 1) == '/') {
                k++; // maskierter Schraegstrich (JSON): der Rueckwaertsstrich zaehlt nicht und beendet keinen Lauf
            } else {
                gezaehlt++;
                lauf = istBase64Zeichen(c) ? lauf + 1 : 0;
                if (lauf == SCHLUESSEL_LAUF) {
                    abgedeckt += SCHLUESSEL_LAUF;
                } else if (lauf > SCHLUESSEL_LAUF) {
                    abgedeckt++;
                }
                k++;
            }
        }
        return abgedeckt > 0 && abgedeckt * 100L >= (long) SCHLUESSEL_ANTEIL_PROZENT * gezaehlt;
    }

    private static boolean istLeerOderUmbruch(char c) {
        return GeheimnisZeichen.istLeerraum(c) || GeheimnisZeichen.istZeilenumbruch(c);
    }

    /** Ob an {@code pos} (vor {@code grenze}) ein Rueckwaertsstrich und {@code n} oder {@code r} stehen. */
    private static boolean istMaskierterUmbruchAn(String text, int pos, int grenze) {
        return pos + 1 < grenze && text.charAt(pos) == (char) 0x5C
                && (text.charAt(pos + 1) == 'n' || text.charAt(pos + 1) == 'r');
    }

    private static void pemZeilenform(String text, List<Finding> ziel) {
        int n = text.length();
        int pos = 0;
        while (pos < n) {
            int ze = zeilenEnde(text, pos);
            int ein = ueberspringeLeerraum(text, pos, ze);
            int naechste = ze + GeheimnisZeichen.zeilenumbruchLaenge(text, ze);
            if (text.startsWith(BEGIN_ZEILE, ein) && istPrivateBeginZeile(text, ein, ze)) {
                pos = leseBlock(text, naechste, ziel);
            } else {
                pos = naechste;
            }
        }
    }

    /**
     * Ob die Zeile {@code [ein, ze)}, die mit der BEGIN-Zeichenfolge beginnt, die BEGIN-Zeile eines privaten
     * Schluessels ist.
     */
    private static boolean istPrivateBeginZeile(String text, int ein, int ze) {
        int k = privateMarkerArtEnde(text, ein);
        return k >= 0 && ueberspringeLeerraum(text, k + STRICHE.length(), ze) == ze;
    }

    /**
     * Index der schliessenden fuenf Bindestriche, wenn an {@code pos} (dort steht die BEGIN-Zeichenfolge) die
     * BEGIN-Markierung eines privaten Schluessels steht, sonst {@code -1}. Gelesen werden hoechstens 41 Zeichen der
     * Art.
     */
    private static int privateMarkerArtEnde(String text, int pos) {
        int artVon = pos + BEGIN_ZEILE.length();
        int k = artVon;
        while (k < text.length() && k - artVon <= ART_HOECHSTLAENGE
                && (GeheimnisZeichen.istAsciiGross(text.charAt(k)) || text.charAt(k) == ' ')) {
            k++;
        }
        if (k - artVon > ART_HOECHSTLAENGE || !text.startsWith(STRICHE, k)) {
            return -1;
        }
        for (String ende : PRIVATE_ARTEN) {
            int von = k - ende.length();
            if (von >= artVon && text.regionMatches(von, ende, 0, ende.length())
                    && (von == artVon || text.charAt(von - 1) == ' ')) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Liest den Block hinter einer BEGIN-Zeile ab {@code start} (Beginn der ersten Koerperzeile) und meldet den
     * Koerper, wenn es einen gibt. Liefert die Position, ab der der Haupt-Durchlauf weitersucht: der Beginn der Zeile
     * mit fuenf Bindestrichen, die den Block beendet, oder das Textende.
     */
    private static int leseBlock(String text, int start, List<Finding> ziel) {
        int n = text.length();
        int pos = start;
        int von = -1;
        int bis = -1;
        boolean endZeile = false;
        while (pos < n) {
            int ze = zeilenEnde(text, pos);
            int ein = ueberspringeLeerraum(text, pos, ze);
            if (text.startsWith(STRICHE, ein)) {
                endZeile = text.startsWith(END_ZEILE, ein);
                break;
            }
            if (ein < ze) {
                if (von < 0) {
                    von = ein;
                }
                bis = ohneLeerraumHinten(text, ein, ze);
            }
            pos = ze + GeheimnisZeichen.zeilenumbruchLaenge(text, ze);
        }
        int weiter = Math.min(pos, n);
        if (!endZeile) {
            int[] bereich = bereichOhneEnde(text, start, weiter);
            von = bereich == null ? -1 : bereich[0];
            bis = bereich == null ? -1 : bereich[1];
        }
        if (von >= 0 && bis > von && hatSchluesselLauf(text, von, bis)) {
            ziel.add(new Finding(GeheimnisTypen.TOKEN, text.substring(von, bis), von, bis, KONFIDENZ,
                    TokenRecognizer.QUELLE_PEM));
        }
        return weiter;
    }

    /**
     * Der Koerper ohne END-Zeile in {@code [start, stopp)}: optionale Kopfzeilen, eine Leerzeile, dann Base64-Zeilen;
     * der Bereich reicht bis zur letzten zusammenhaengenden Base64-Zeile. {@code null}, wenn es keine gibt.
     */
    private static int[] bereichOhneEnde(String text, int start, int stopp) {
        int pos = start;
        int von = -1;
        int bis = -1;
        boolean kopf = true;
        boolean leerzeileFrei = true;
        boolean basis = false;
        while (pos < stopp) {
            int ze = zeilenEnde(text, pos);
            int ein = ueberspringeLeerraum(text, pos, ze);
            int hinten = ohneLeerraumHinten(text, ein, ze);
            if (ein == ze) {
                if (basis || !leerzeileFrei) {
                    break;
                }
                leerzeileFrei = false;
                kopf = false;
            } else if (kopf && istKopfzeile(text, ein, hinten)) {
                if (von < 0) {
                    von = ein;
                }
            } else if (istBase64Zeile(text, ein, hinten)) {
                kopf = false;
                leerzeileFrei = false;
                basis = true;
                if (von < 0) {
                    von = ein;
                }
                bis = hinten;
            } else {
                break;
            }
            pos = ze + GeheimnisZeichen.zeilenumbruchLaenge(text, ze);
        }
        return basis ? new int[] {von, bis} : null;
    }

    /** Kopfzeile {@code Name: Wert}: Name aus Buchstaben, Ziffern und {@code -}, beginnt mit einem Buchstaben. */
    private static boolean istKopfzeile(String text, int von, int bis) {
        if (von >= bis || !(GeheimnisZeichen.istAsciiBuchstabeOderZiffer(text.charAt(von))
                && !GeheimnisZeichen.istAsciiZiffer(text.charAt(von)))) {
            return false;
        }
        int k = von;
        while (k < bis && (GeheimnisZeichen.istAsciiBuchstabeOderZiffer(text.charAt(k)) || text.charAt(k) == '-')) {
            k++;
        }
        return k < bis && text.charAt(k) == ':';
    }

    private static boolean istBase64Zeichen(char c) {
        return GeheimnisZeichen.istAsciiBuchstabeOderZiffer(c) || c == '+' || c == '/' || c == '=';
    }

    private static boolean istBase64Zeile(String text, int von, int bis) {
        if (bis - von > BASE64_ZEILE_HOECHST || bis <= von) {
            return false;
        }
        for (int k = von; k < bis; k++) {
            if (!istBase64Zeichen(text.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    /** Ob in {@code [von, bis)} eine Folge von mindestens 16 Base64-Zeichen steht. */
    private static boolean hatSchluesselLauf(String text, int von, int bis) {
        int lauf = 0;
        for (int k = von; k < bis; k++) {
            if (istBase64Zeichen(text.charAt(k))) {
                lauf++;
                if (lauf >= SCHLUESSEL_LAUF) {
                    return true;
                }
            } else {
                lauf = 0;
            }
        }
        return false;
    }
}
