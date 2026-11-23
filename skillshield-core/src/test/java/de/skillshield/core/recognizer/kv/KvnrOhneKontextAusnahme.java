package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.List;

/**
 * Eng gefasste Ausnahme fuer die Bestandstests (Nachtrag 2 zu T-10, Entscheidung zu D-5 und Regel 15): Teil 1b
 * ({@code KvnrOhneKontext}) macht aus einer vollstaendig gueltigen KVNR-Form ohne Kontextwort einen Fund. Auf den
 * Bestandsmengen ersetzt er dabei einen alten LANR-/IK-Fund auf denselben neun Ziffern (Fall K-1 der Pruefregel) oder
 * liegt auf einer bisher freien Stelle.
 *
 * <p>Ein neuer Fund gehoert nur dann zur Ausnahme, wenn alle Bedingungen gelten: Typ KVNR, Quelle
 * {@value #QUELLE}, Konfidenz 0,60 (Toleranzvergleich), genau zehn Zeichen, davon ein ASCII-Grossbuchstabe und neun
 * ASCII-Ziffern, und entweder
 * <ul>
 * <li>(ersetzt) genau ein alter Fund ueberlappt ihn, dieser ist LANR oder IK und umfasst genau die neun Ziffern
 * (ein Zeichen hinter dem Anfang, gleiches Ende), oder</li>
 * <li>(frei) kein alter Fund ueberlappt ihn.</li>
 * </ul>
 * Jeder andere neue Fund bleibt eine Abweichung. Die Zahl der Ausnahmefaelle je Textmenge ist exakt festgehalten
 * ({@link #erwartet}); {@link #pruefe} bricht bei jeder anderen Zahl ab. Die Hilfe aendert keine Pruefregel; sie
 * steht in einer eigenen Klasse, weil {@link KvnrBsnrHilfen} eingefroren ist. Meldungen nennen nur Zahlen und Namen
 * der Textmengen.
 */
public final class KvnrOhneKontextAusnahme {

    /** Quelle der Funde von Teil 1b. */
    public static final String QUELLE = "kvnr-ohne-kontext";

    private static final double KONFIDENZ = 0.60;
    private static final double TOLERANZ = 1e-9;
    private static final int ZIFFERN = 9;
    private static final int LAENGE = ZIFFERN + 1;

    private KvnrOhneKontextAusnahme() {
    }

    /**
     * Zaehler einer Textmenge.
     *
     * @param ersetzt neue Funde der Ausnahme, die einen alten LANR-/IK-Fund auf denselben neun Ziffern ersetzen
     * @param frei    neue Funde der Ausnahme auf einer Stelle ohne alten Fund
     * @param gesamt  alle neuen Funde mit der Quelle {@value #QUELLE}, auch solche ausserhalb der Ausnahme
     */
    public record Zaehlung(int ersetzt, int frei, int gesamt) {

        /** Die Null der Summenbildung. */
        public static final Zaehlung NULL = new Zaehlung(0, 0, 0);

        /** Summe je Feld. */
        public Zaehlung plus(Zaehlung o) {
            return new Zaehlung(ersetzt + o.ersetzt, frei + o.frei, gesamt + o.gesamt);
        }

        /** Kurzform ohne Text und Ziffern, fuer Meldungen. */
        public String kurz() {
            return "ersetzt " + ersetzt + ", frei " + frei + ", alle mit Quelle " + QUELLE + " " + gesamt;
        }
    }

    private enum Art {
        ERSETZT, FREI, KEINE
    }

    /**
     * Die erwartete Zahl je Textmenge (gemessen in T-10, Nachtrag 2): Teil 1c 5 ersetzt, Teil 1d 1, Teil 1e 5,
     * Generatoren 1 ersetzt und 1 frei, alle anderen Mengen 0.
     *
     * @param menge Name der Textmenge ("Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
     *              "Korpus Teil 2", "Generatoren")
     */
    public static Zaehlung erwartet(String menge) {
        return switch (menge) {
            case "Teil 1c" -> new Zaehlung(5, 0, 5);
            case "Teil 1d" -> new Zaehlung(1, 0, 1);
            case "Teil 1e" -> new Zaehlung(5, 0, 5);
            case "Generatoren" -> new Zaehlung(1, 1, 2);
            case "Teil 1", "Teil 1b", "Korpus", "Korpus Teil 2" -> Zaehlung.NULL;
            default -> throw new IllegalArgumentException("unbekannte Textmenge");
        };
    }

    private static boolean istFormUndQuelle(String text, Finding f) {
        if (f.type() != FindingType.KVNR || !f.source().equals(QUELLE)
                || Math.abs(f.confidence() - KONFIDENZ) > TOLERANZ || f.end() - f.start() != LAENGE) {
            return false;
        }
        char anfang = text.charAt(f.start());
        if (anfang < 'A' || anfang > 'Z') {
            return false;
        }
        for (int i = f.start() + 1; i < f.end(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static Art einordnen(String text, Finding neu, List<Finding> alt) {
        if (!istFormUndQuelle(text, neu)) {
            return Art.KEINE;
        }
        Finding ueberlappend = null;
        int zahl = 0;
        for (Finding a : alt) {
            if (a.start() < neu.end() && a.end() > neu.start()) {
                ueberlappend = a;
                zahl++;
            }
        }
        if (zahl == 0) {
            return Art.FREI;
        }
        boolean kv = zahl == 1 && (ueberlappend.type() == FindingType.LANR
                || ueberlappend.type() == FindingType.IK);
        if (kv && ueberlappend.start() == neu.start() + 1 && ueberlappend.end() == neu.end()) {
            return Art.ERSETZT;
        }
        return Art.KEINE;
    }

    /** Zaehlt die Ausnahmefaelle in einem Text. */
    public static Zaehlung zaehle(String text, List<Finding> alt, List<Finding> neu) {
        int ersetzt = 0;
        int frei = 0;
        int gesamt = 0;
        for (Finding f : neu) {
            gesamt += f.source().equals(QUELLE) ? 1 : 0;
            Art art = einordnen(text, f, alt);
            ersetzt += art == Art.ERSETZT ? 1 : 0;
            frei += art == Art.FREI ? 1 : 0;
        }
        return new Zaehlung(ersetzt, frei, gesamt);
    }

    /** Zaehlt die Ausnahmefaelle in einer Textmenge. */
    public static Zaehlung zaehle(List<String> texte, TextScanner alt, TextScanner neu) {
        Zaehlung summe = Zaehlung.NULL;
        for (String text : texte) {
            summe = summe.plus(zaehle(text, alt.scan(text), neu.scan(text)));
        }
        return summe;
    }

    /** Die neue Fundliste ohne die Funde der Ausnahme; alle anderen Funde bleiben unveraendert. */
    public static List<Finding> ohneAusnahme(String text, List<Finding> alt, List<Finding> neu) {
        List<Finding> rest = new ArrayList<>(neu.size());
        for (Finding f : neu) {
            if (einordnen(text, f, alt) == Art.KEINE) {
                rest.add(f);
            }
        }
        return rest;
    }

    /**
     * Prueft die Zahl der Ausnahmefaelle einer Textmenge exakt gegen {@link #erwartet}: ersetzt, frei und die Summe
     * aller Funde mit der Quelle {@value #QUELLE} (kein Fund dieser Quelle liegt ausserhalb der Ausnahme).
     */
    public static void pruefe(String menge, List<String> texte, TextScanner alt, TextScanner neu) {
        pruefe(menge, texte, alt, neu, erwartet(menge));
    }

    /**
     * Die erwartete Zahl je Textmenge, wenn der alte Scanner die Kopie {@code KvStandLuecken} enthaelt (Tests in
     * {@code KvStandLueckenDifferenzTest}). Sie kennt keine Trenner zwischen den Ziffern; auf Teil 1e hat sie deshalb
     * fuer vier der fuenf Funde keinen alten LANR-/IK-Fund (frei statt ersetzt). Es sind dieselben fuenf neuen
     * Funde: die Summe ersetzt plus frei ist gleich {@link #erwartet}.
     */
    public static Zaehlung erwartetGegenLuecken(String menge) {
        return menge.equals("Teil 1e") ? new Zaehlung(1, 4, 5) : erwartet(menge);
    }

    /** Wie {@link #pruefe(String, List, TextScanner, TextScanner)} mit ausdruecklich vorgegebener Erwartung. */
    public static void pruefe(String menge, List<String> texte, TextScanner alt, TextScanner neu, Zaehlung soll) {
        Zaehlung ist = zaehle(texte, alt, neu);
        assertEquals(soll.ersetzt(), ist.ersetzt(), menge + ": Ausnahme ersetzt; " + ist.kurz());
        assertEquals(soll.frei(), ist.frei(), menge + ": Ausnahme frei; " + ist.kurz());
        assertEquals(soll.gesamt(), ist.gesamt(), menge + ": Funde der Quelle ausserhalb der Ausnahme; " + ist.kurz());
    }
}
