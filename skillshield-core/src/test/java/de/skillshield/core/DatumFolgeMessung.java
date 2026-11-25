package de.skillshield.core;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Neue Messhilfe fuer T-3 des Features datum-und-kontextvorrang (Nachtrag 3 der Hauptsitzung): Sie vergleicht die
 * Fundliste von {@code ALT_DATUM} mit der des Produkts je Text und trennt die erwartete Wirkung (Klasse A: Funde, deren
 * Bereich ein Datumssegment ist, entfallen) von den Folgeaenderungen neben einem entfallenen Datum (Klasse Z) und von
 * den harten Bedingungen (kein Schutzverlust ausserhalb der Datumssegmente, keine sinkende Konfidenz). Die
 * eingefrorene {@link DatumMessung} bleibt unveraendert und liefert weiter die Zahl der entfallenen Datumsfunde.
 *
 * <p><b>Datumssegmente</b> eines Textes sind die Bereiche der rohen Funde der eingefrorenen Kopie (alle vier Arten, vor
 * Zuschnitt und Konfliktaufloesung), die nach {@link DatumReferenz} ein Datumssegment sind. Die Messung verwendet nur
 * diese Referenz, nie das Produkt.
 *
 * <p><b>Klasse Z</b> wird je Text ueber die Funde gezaehlt, die nach Abzug der entfallenen Datumsfunde nicht in
 * beiden Listen stehen. Jeder solche alte Fund und jeder solche neue Fund faellt in genau eine Art (Reihenfolge der
 * Pruefung wie unten): bei gleichem Bereich {@code typ} (Typ anders), {@code konfidenz} (Typ gleich, Konfidenz
 * anders), {@code quelle} (nur die Quelle anders); sonst fuer alte Funde {@code altBereich} (der Bereich kommt in der
 * neuen Liste nicht mehr vor: Fund geteilt, verkuerzt oder zusammengelegt) und fuer neue Funde {@code ueberDatum}
 * (liegt ganz in einem entfallenen Datumsfund), {@code reststueck} (liegt ganz in einem anderen alten Fund),
 * {@code neuFrei} (beruehrt keinen alten Fund) oder {@code sonst} (alles andere). Meldungen nennen nur Zahlen.
 */
final class DatumFolgeMessung {

    /** Abstand, unter dem zwei Konfidenzwerte als gleich gelten. */
    private static final double EPSILON = 1e-9;

    private DatumFolgeMessung() {
    }

    /**
     * Zahl der Folgeaenderungen (Klasse Z), nach Art getrennt.
     *
     * @param typ gleicher Bereich, anderer Typ
     * @param konfidenz gleicher Bereich und Typ, andere Konfidenz
     * @param quelle gleicher Bereich, Typ und Konfidenz, andere Quelle
     * @param altBereich alter Fund, dessen Bereich in der neuen Liste nicht mehr vorkommt
     * @param ueberDatum neuer Fund ganz innerhalb eines entfallenen Datumsfunds
     * @param reststueck neuer Fund ganz innerhalb eines anderen alten Funds, mit anderem Bereich
     * @param neuFrei neuer Fund, der keinen alten Fund beruehrt
     * @param sonst neuer Fund mit anderem Bereich, der in keinen alten Fund passt
     */
    record Z(int typ, int konfidenz, int quelle, int altBereich, int ueberDatum, int reststueck, int neuFrei,
            int sonst) {

        static final Z KEIN = new Z(0, 0, 0, 0, 0, 0, 0, 0);

        Z plus(Z o) {
            return new Z(typ + o.typ, konfidenz + o.konfidenz, quelle + o.quelle, altBereich + o.altBereich,
                    ueberDatum + o.ueberDatum, reststueck + o.reststueck, neuFrei + o.neuFrei, sonst + o.sonst);
        }

        /** Summe aller Arten. */
        int summe() {
            return typ + konfidenz + quelle + altBereich + ueberDatum + reststueck + neuFrei + sonst;
        }

        /** Kurzform fuer Meldungen. */
        String kurz() {
            return "typ=" + typ + ", konfidenz=" + konfidenz + ", quelle=" + quelle + ", altBereich=" + altBereich
                    + ", ueberDatum=" + ueberDatum + ", reststueck=" + reststueck + ", neuFrei=" + neuFrei
                    + ", sonst=" + sonst;
        }
    }

    /**
     * Messergebnis eines Textes oder (summiert) einer Menge.
     *
     * @param datumEntfallen Klasse A: alte KV-Funde ohne neuen Fund, deren Bereich ein Datumssegment ist
     * @param z Klasse Z
     * @param verlorenePositionen Positionen ausserhalb der Datumssegmente, die alt geschuetzt waren und neu nicht
     * @param konfidenzGesunken Positionen, die in beiden Listen geschuetzt sind und neu eine kleinere Konfidenz haben
     * @param neuGeschuetzt Positionen, die neu geschuetzt sind und alt nicht
     * @param datumSegmente Zahl der Datumssegmente der Referenz (rohe Funde der Kopie, ohne Doppelte)
     * @param datumSegmenteGeschuetzt davon Segmente, von denen neu mindestens eine Position noch von einem KV-Fund
     *        (LANR, IK, KVNR, BSNR) geschuetzt ist (ein Datum in einem Pfad oder einer URL zaehlt nicht)
     * @param datumPositionenGeschuetzt Positionen innerhalb der Datumssegmente, die neu noch von einem KV-Fund
     *        geschuetzt sind
     * @param texteMitAbweichung 1, wenn die alte und die neue Liste verschieden sind, sonst 0
     * @param abweichungOhneDatum 1, wenn die Listen verschieden sind, obwohl kein Datumssegment vorhanden ist
     * @param rohBestandUngeschuetzt Positionen, die ein roher Fund der Erkenner fuer E-Mail, IP, URL und Pfad
     *        abdeckt und die in der neuen Liste von keinem Fund geschuetzt sind (auch innerhalb von Datumssegmenten)
     */
    record Ergebnis(int datumEntfallen, Z z, int verlorenePositionen, int konfidenzGesunken, int neuGeschuetzt,
            int datumSegmente, int datumSegmenteGeschuetzt, int datumPositionenGeschuetzt, int texteMitAbweichung,
            int abweichungOhneDatum, int rohBestandUngeschuetzt) {

        static final Ergebnis KEIN = new Ergebnis(0, Z.KEIN, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        Ergebnis plus(Ergebnis o) {
            return new Ergebnis(datumEntfallen + o.datumEntfallen, z.plus(o.z), verlorenePositionen
                    + o.verlorenePositionen, konfidenzGesunken + o.konfidenzGesunken, neuGeschuetzt
                    + o.neuGeschuetzt, datumSegmente + o.datumSegmente, datumSegmenteGeschuetzt
                    + o.datumSegmenteGeschuetzt, datumPositionenGeschuetzt + o.datumPositionenGeschuetzt,
                    texteMitAbweichung + o.texteMitAbweichung, abweichungOhneDatum + o.abweichungOhneDatum,
                    rohBestandUngeschuetzt + o.rohBestandUngeschuetzt);
        }

        /** Ob die harten Bedingungen (Schutz, Konfidenz, roher Bestandsfund bleibt geschuetzt) erfuellt sind. */
        boolean hartErfuellt() {
            return verlorenePositionen == 0 && konfidenzGesunken == 0 && rohBestandUngeschuetzt == 0;
        }

        /** Kurzform fuer Meldungen. */
        String kurz() {
            return "A=" + datumEntfallen + ", Z[" + z.kurz() + "], verloren=" + verlorenePositionen
                    + ", konfidenzGesunken=" + konfidenzGesunken + ", neuGeschuetzt=" + neuGeschuetzt
                    + ", datumSegmente=" + datumSegmente + ", geschuetzt=" + datumSegmenteGeschuetzt
                    + ", datumPositionenGeschuetzt=" + datumPositionenGeschuetzt + ", texteAbweichung="
                    + texteMitAbweichung + ", ohneDatum=" + abweichungOhneDatum + ", rohBestandUngeschuetzt="
                    + rohBestandUngeschuetzt;
        }
    }

    /** Die Datumssegmente des Textes als {start, end}: rohe Funde der Kopie, die nach der Referenz Datum sind. */
    static List<int[]> datumSegmente(String text) {
        List<int[]> segmente = new ArrayList<>();
        Set<Long> gesehen = new HashSet<>();
        for (Recognizer kopie : KvStandDatum.alle()) {
            for (Finding f : kopie.detect(text)) {
                long schluessel = ((long) f.start() << 32) | f.end();
                if (DatumReferenz.istDatumssegment(text, f.start(), f.end()) && gesehen.add(schluessel)) {
                    segmente.add(new int[] {f.start(), f.end()});
                }
            }
        }
        return segmente;
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

    /** Konfidenz je Position; -1, wo kein Fund liegt. */
    private static double[] konfidenz(int laenge, List<Finding> funde) {
        double[] k = new double[laenge];
        Arrays.fill(k, -1.0);
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                k[i] = f.confidence();
            }
        }
        return k;
    }

    private static boolean gleicherBereich(Finding a, Finding b) {
        return a.start() == b.start() && a.end() == b.end();
    }

    private static boolean enthaelt(Finding aussen, Finding innen) {
        return aussen.start() <= innen.start() && innen.end() <= aussen.end();
    }

    private static boolean beruehrt(Finding a, Finding b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    /** Misst einen Text: {@code alt} ist die Liste von {@code ALT_DATUM}, {@code neu} die des Produkts. */
    static Ergebnis messe(String text, List<Finding> alt, List<Finding> neu) {
        int laenge = text.length();
        Set<Finding> altMenge = new HashSet<>(alt);
        Set<Finding> neuMenge = new HashSet<>(neu);

        List<Finding> entfallen = new ArrayList<>();
        List<Finding> altRest = new ArrayList<>();
        for (Finding f : alt) {
            if (neuMenge.contains(f)) {
                continue;
            }
            if (DatumMessung.istKv(f) && DatumReferenz.istDatumssegment(text, f.start(), f.end())) {
                entfallen.add(f);
            } else {
                altRest.add(f);
            }
        }
        List<Finding> neuRest = new ArrayList<>();
        for (Finding g : neu) {
            if (!altMenge.contains(g)) {
                neuRest.add(g);
            }
        }

        int typ = 0;
        int konfidenz = 0;
        int quelle = 0;
        int altBereich = 0;
        List<Finding> zugeordnet = new ArrayList<>();
        for (Finding f : altRest) {
            Finding partner = null;
            for (Finding g : neuRest) {
                if (gleicherBereich(f, g)) {
                    partner = g;
                    break;
                }
            }
            if (partner == null) {
                altBereich++;
            } else {
                zugeordnet.add(partner);
                if (f.type() != partner.type()) {
                    typ++;
                } else if (Math.abs(f.confidence() - partner.confidence()) > EPSILON) {
                    konfidenz++;
                } else {
                    quelle++;
                }
            }
        }
        int ueberDatum = 0;
        int reststueck = 0;
        int neuFrei = 0;
        int sonst = 0;
        for (Finding g : neuRest) {
            if (zugeordnet.contains(g)) {
                continue;
            }
            if (entfallen.stream().anyMatch(e -> enthaelt(e, g))) {
                ueberDatum++;
            } else if (alt.stream().anyMatch(a -> enthaelt(a, g))) {
                reststueck++;
            } else if (alt.stream().noneMatch(a -> beruehrt(a, g))) {
                neuFrei++;
            } else {
                sonst++;
            }
        }
        Z z = new Z(typ, konfidenz, quelle, altBereich, ueberDatum, reststueck, neuFrei, sonst);

        List<int[]> segmente = datumSegmente(text);
        boolean[] datum = abdeckung(laenge, segmente);
        boolean[] altDeckt = abdeckung(laenge, boundsOf(alt));
        boolean[] neuDeckt = abdeckung(laenge, boundsOf(neu));
        List<Finding> neuKv = new ArrayList<>();
        for (Finding g : neu) {
            if (DatumMessung.istKv(g)) {
                neuKv.add(g);
            }
        }
        boolean[] neuKvDeckt = abdeckung(laenge, boundsOf(neuKv));
        double[] altKonf = konfidenz(laenge, alt);
        double[] neuKonf = konfidenz(laenge, neu);
        int verloren = 0;
        int gesunken = 0;
        int neuGeschuetzt = 0;
        int datumPositionen = 0;
        for (int i = 0; i < laenge; i++) {
            verloren += altDeckt[i] && !neuDeckt[i] && !datum[i] ? 1 : 0;
            gesunken += altDeckt[i] && neuDeckt[i] && neuKonf[i] < altKonf[i] - EPSILON ? 1 : 0;
            neuGeschuetzt += neuDeckt[i] && !altDeckt[i] ? 1 : 0;
            datumPositionen += datum[i] && neuKvDeckt[i] ? 1 : 0;
        }
        int segmenteGeschuetzt = 0;
        for (int[] s : segmente) {
            boolean irgendwo = false;
            for (int i = s[0]; i < s[1]; i++) {
                irgendwo |= neuKvDeckt[i];
            }
            segmenteGeschuetzt += irgendwo ? 1 : 0;
        }
        int abweichung = alt.equals(neu) ? 0 : 1;
        int rohUngeschuetzt = 0;
        for (Recognizer erkenner : bestandserkenner()) {
            for (Finding roh : erkenner.detect(text)) {
                for (int i = roh.start(); i < roh.end(); i++) {
                    rohUngeschuetzt += neuDeckt[i] ? 0 : 1;
                }
            }
        }
        return new Ergebnis(entfallen.size(), z, verloren, gesunken, neuGeschuetzt, segmente.size(),
                segmenteGeschuetzt, datumPositionen, abweichung, segmente.isEmpty() ? abweichung : 0,
                rohUngeschuetzt);
    }

    /** Die vier Erkenner E-Mail, IP, URL, Pfad aus dem Standard, nach Klasse gewaehlt (Bestandstypen ohne KV-Bezug). */
    private static List<Recognizer> bestandserkenner() {
        List<Recognizer> r = new ArrayList<>();
        for (Recognizer e : TextScanner.standard().recognizers()) {
            if (e instanceof EmailRecognizer || e instanceof Ipv4Recognizer || e instanceof UrlRecognizer
                    || e instanceof PathRecognizer) {
                r.add(e);
            }
        }
        if (r.size() != 4) {
            throw new IllegalStateException("Erwartet vier Bestandserkenner, gefunden " + r.size());
        }
        return r;
    }

    private static List<int[]> boundsOf(List<Finding> funde) {
        List<int[]> r = new ArrayList<>(funde.size());
        for (Finding f : funde) {
            r.add(new int[] {f.start(), f.end()});
        }
        return r;
    }
}
