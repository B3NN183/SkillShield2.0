package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.KvnrKollisionsNachrechnung.Zuschnitt;
import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest zum Vorrang des Bestands (Feature kvnr-bsnr-erkennung, Nachtrag 2 zu T-6, Punkt 5 b): Fuer
 * zufaellige Mischtexte aus Bausteinen aller Typen (E-Mail, IP mit {@code /Praefix}, Port, Ziffern davor und
 * dahinter, URL, Windows- und Unix-Pfad, Dateiname, LANR, IK, KVNR-Formen nach A-6, KVNR-Kontextwoerter, Sperrschrift,
 * Kompositum, Ziffernfolgen, Trenner, unsichtbare Zeichen) gilt:
 * <ul>
 * <li>Die Teilliste der Funde der Typen EMAIL, IP, URL und PFAD von {@code TextScanner.standard()} ist in allen
 * Feldern und in der Reihenfolge gleich der des alten Scanners {@code KvnrBsnrHilfen.ALT_TRENNER}.</li>
 * <li>Jede vom alten Scanner abgedeckte Position bleibt abgedeckt.</li>
 * <li>Jeder Text besteht den Laufzeit-Waechter fuer synthetische KVNR (Texte mit einer gueltigen KVNR-Form ohne die
 * Ziffern 0000 werden neu erzeugt; ihre Zahl steht im Zaehler).</li>
 * </ul>
 * Verglichen werden nur die Nicht-KV-Funde (Aussage von Nachtrag 2 zu T-6, Punkt 5 b); die Pruefregel
 * {@code bestandsabweichungen} fuer LANR und IK wird hier nicht angewendet. Seit Nachtrag 3 zu T-6 gilt zusaetzlich:
 * Jede Position eines Restfundes der unabhaengigen Nachrechnung ({@code KvnrKollisionsNachrechnung#kvnrZuschnitt}:
 * Reststueck eines KVNR-Erkenner-Kandidaten ausserhalb des Bestands, mit mindestens drei Ziffern) ist von einem
 * Fund abgedeckt, und ein Fund mit der Quelle {@code kvnr-rest} schneidet keinen Fund von E-Mail, IP, URL oder
 * Pfad.
 *
 * <p>Zaehler (fester Seed, Mindestzahlen 70 Prozent oder weniger des Istwerts, Istwert im Kommentar) belegen, dass
 * die Pruefung nicht ins Leere laeuft: Texte mit mindestens einem zugeschnittenen KVNR-Kandidaten, Texte mit
 * Restfund, Restfunde gesamt, entfallene Reststuecke unter der Drei-Ziffern-Schwelle, Texte je Alttyp und Texte mit
 * KVNR-Fund. Selbsttests: Ein Scanner ohne Filterstufe (dieselben sieben Erkenner direkt ueber
 * {@code ConflictResolver.resolve}) wird in einer Mindestzahl von Texten als Abweichung der Bestandsliste gemeldet;
 * die Verwerfen-Variante aus Durchlauf 3 (Kandidaten, die den Bestand schneiden, entfallen ganz) haelt die
 * Bestandsliste ein, wird aber in einer Mindestzahl von Texten ueber ein ungeschuetztes Reststueck gemeldet.
 * Meldungen nennen nur Seed, Iteration und Zaehler, nie Text oder Ziffern.
 */
class KvnrBestandVorrangEigenschaftTest {

    private static final long SEED = 20261123L;
    private static final int TEXTE = 24_000;
    private static final String BS = String.valueOf((char) 0x5C);
    private static final String EMAIL_ENDE = "@example.invalid";
    private static final char ZWSP = (char) 0x200B;
    private static final char SHY = (char) 0xAD;
    private static final char BOM = (char) 0xFEFF;
    private static final char WORD_JOINER = (char) 0x2060;
    private static final char LRM = (char) 0x200E;
    private static final char NBSP = (char) 0xA0;
    private static final List<String> KONTEXT = List.of("KVNR", "Versichertennummer", "KV-Nr.", "K V N R",
            "eGK-Nummer", "KVNR:", "Versichertennr.", "K. V. N. R.", "kvnr", "Krankenversichertennummer", "LANR", "IK",
            "Server", "Mail", "Datei");
    private static final List<String> KONTEXT_KVNR = KONTEXT.subList(0, 10);
    private static final List<String> TRENNER = List.of(" ", " ", " ", ", ", "\n", " / ", "-", ":", "", "; ", " (",
            String.valueOf(ZWSP), String.valueOf(SHY), String.valueOf(BOM), String.valueOf(WORD_JOINER),
            String.valueOf(LRM), String.valueOf(NBSP), "\n\n", ". ");
    private static final List<String> KOMPOSITA = List.of(" (Patienten-KVNR)", ", Pat.-KVNR",
            " (eGK-Versichertennummer)",
            " Patienten-KVNR");

    private static final List<FindingType> ALTTYPEN = List.of(FindingType.EMAIL, FindingType.IP, FindingType.URL,
            FindingType.PFAD, FindingType.LANR, FindingType.IK);

    /** Zaehler eines Laufs. */
    private static final class Zaehler {
        int texte;
        int verworfeneNeuErzeugt;
        int mitZugeschnittenem;
        int mitKvnrFund;
        int ohneFilterAbweichend;
        int abweichungen;
        int abdeckungVerloren;
        int mitRestfund;
        int restfundeGesamt;
        int unterSchwelleGesamt;
        int restLueckeTexte;
        int restUeberschneidung;
        final Map<String, Integer> jeAlttyp = new TreeMap<>();

        String kurz() {
            return "Texte " + texte + ", neu erzeugt " + verworfeneNeuErzeugt + ", mit zugeschnittenem Kandidaten "
                    + mitZugeschnittenem + ", mit KVNR-Fund " + mitKvnrFund + ", ohne Filter abweichend "
                    + ohneFilterAbweichend + ", Abweichungen " + abweichungen + ", Abdeckung verloren "
                    + abdeckungVerloren + ", mit Restfund " + mitRestfund + ", Restfunde " + restfundeGesamt
                    + ", Reststuecke unter der Schwelle " + unterSchwelleGesamt + ", Texte mit Rest-Luecke "
                    + restLueckeTexte + ", Restfund ueberschneidet Bestand " + restUeberschneidung
                    + ", je Alttyp " + jeAlttyp;
        }
    }

    /** Welcher Scanner verglichen wird. */
    private enum Variante {
        /** {@code TextScanner.standard()}. */
        STANDARD,
        /** Dieselben Erkenner direkt ueber den Aufloeser, ohne Filterstufe. */
        OHNE_FILTER,
        /** Verwerfen-Variante aus Durchlauf 3: Kandidaten, die den Bestand schneiden, entfallen ganz. */
        VERWERFEN
    }

    // ------------------------------------------------------------------ Erzeugung

    private static String pick(Random r, List<String> liste) {
        return liste.get(r.nextInt(liste.size()));
    }

    private static String ziffern(Random r, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append((char) ('0' + r.nextInt(10)));
        }
        return sb.toString();
    }

    /** Neun Ziffern einer KVNR: die ersten vier sind 0000 (A-6), die Pruefziffer ist gueltig oder verschoben. */
    private static String neunZiffern(Random r, char buchstabeGross, boolean gueltig) {
        String neun = KvnrBsnrHilfen.kvnrNeunZiffern(buchstabeGross, "0000" + ziffern(r, 4));
        if (gueltig) {
            return neun;
        }
        char letzte = (char) ('0' + (neun.charAt(8) - '0' + 1) % 10);
        return neun.substring(0, 8) + letzte;
    }

    /** Eine KVNR-Form: Buchstabe (gross oder klein), optional ein Trenner, neun Ziffern, oft gruppiert. */
    private static String kvnrForm(Random r) {
        char gross = (char) ('A' + r.nextInt(26));
        char buchstabe = r.nextInt(4) == 0 ? Character.toLowerCase(gross) : gross;
        String neun = neunZiffern(r, gross, r.nextInt(3) > 0);
        String trenner = r.nextInt(3) == 0 ? pick(r, List.of(" ", "-", ".", "/", "_")) : "";
        String nummer = neun;
        int g = r.nextInt(5);
        if (g == 1) {
            String t = pick(r, List.of(" ", "-", ".", "/"));
            nummer = neun.substring(0, 3) + t + neun.substring(3, 6) + t + neun.substring(6);
        } else if (g == 2) {
            String t = pick(r, List.of(" ", "-"));
            nummer = neun.substring(0, 4) + t + neun.substring(4);
        }
        return buchstabe + trenner + nummer;
    }

    private static String ipBasis(Random r) {
        int w = r.nextInt(4);
        if (w == 0) {
            return "192.0.2." + (1 + r.nextInt(250));
        }
        if (w == 1) {
            return "203.0.113." + (1 + r.nextInt(250));
        }
        return IK_FORM_DOKU_IP;
    }

    private static String ipBaustein(Random r) {
        String ip = ipBasis(r);
        int w = r.nextInt(12);
        switch (w) {
            case 0:
                return ip + "/" + (8 + r.nextInt(25));
            case 1:
                return ip + ":" + (1 + r.nextInt(65_000));
            case 2:
                return ip + " " + ziffern(r, 1 + r.nextInt(8));
            case 3:
                return ziffern(r, 4 + r.nextInt(3)) + "." + ip;
            case 4:
                return ip + "." + ziffern(r, 1 + r.nextInt(4));
            case 5:
                return (char) ('A' + r.nextInt(26)) + ip;
            case 6:
                return (char) ('a' + r.nextInt(26)) + " " + ip + "/" + (8 + r.nextInt(25));
            case 7:
                return (char) ('A' + r.nextInt(26)) + ip.substring(0, ip.lastIndexOf('.') + 1) + ziffern(r, 4)
                        + "." + ipBasis(r);
            case 8:
                return ip + " " + kvnrForm(r);
            case 9:
                return (char) ('A' + r.nextInt(26)) + ip + "." + ziffern(r, 1 + r.nextInt(3)) + "."
                        + ziffern(r, 1 + r.nextInt(3));
            default:
                return ip;
        }
    }

    private static String emailBaustein(Random r) {
        int w = r.nextInt(7);
        switch (w) {
            case 0:
                return kvnrForm(r) + EMAIL_ENDE;
            case 1:
                return "a." + kvnrForm(r) + EMAIL_ENDE;
            case 2:
                return "a" + ziffern(r, 1 + r.nextInt(10)) + EMAIL_ENDE;
            case 3:
                return "a" + EMAIL_ENDE + pick(r, List.of(" ", "", "-", "/")) + kvnrForm(r);
            case 4:
                return "a" + EMAIL_ENDE + " " + (char) ('A' + r.nextInt(26)) + " " + ziffern(r, 6 + r.nextInt(7));
            case 5:
                return "n" + ziffern(r, 3) + "@x" + ziffern(r, 4) + ".invalid";
            default:
                return "erika.beispiel" + EMAIL_ENDE;
        }
    }

    private static String objektNameTeil(Random r) {
        int w = r.nextInt(4);
        if (w == 0) {
            return kvnrForm(r);
        }
        if (w == 1) {
            return ziffern(r, 6 + r.nextInt(7));
        }
        if (w == 2) {
            return "befund-" + kvnrForm(r);
        }
        return "x";
    }

    private static String urlBaustein(Random r) {
        int w = r.nextInt(5);
        switch (w) {
            case 0:
                return "https://example.invalid/" + objektNameTeil(r);
            case 1:
                return "http://srv.example/?id=" + objektNameTeil(r);
            case 2:
                return objektNameTeil(r) + "https://example.invalid/x";
            case 3:
                return "https://example.invalid/" + (char) ('A' + r.nextInt(26)) + ziffern(r, 9) + "/"
                        + objektNameTeil(r);
            default:
                return "https://example.invalid/x";
        }
    }

    private static String pfadBaustein(Random r) {
        int w = r.nextInt(6);
        switch (w) {
            case 0:
                return "C:" + BS + "Praxis" + BS + objektNameTeil(r) + ".txt";
            case 1:
                return "/home/praxis/" + objektNameTeil(r) + ".txt";
            case 2:
                return objektNameTeil(r) + ".pdf";
            case 3:
                return "/srv/x/" + (char) ('A' + r.nextInt(26)) + " " + ziffern(r, 6 + r.nextInt(7)) + ".pdf";
            case 4:
                return "D:" + BS + (char) ('a' + r.nextInt(26)) + ziffern(r, 9) + BS + "y.doc";
            default:
                return "befund_" + ziffern(r, 6 + r.nextInt(7)) + ".pdf";
        }
    }

    private static String ziffernBaustein(Random r) {
        int laenge = 1 + r.nextInt(13);
        String z = ziffern(r, laenge);
        if (laenge > 6 && r.nextInt(2) == 0) {
            String t = pick(r, List.of(" ", "-", ".", "/", "_"));
            return z.substring(0, 3) + t + z.substring(3, 6) + t + z.substring(6);
        }
        return z;
    }

    private static String baustein(Random r) {
        int w = r.nextInt(14);
        switch (w) {
            case 0:
                return emailBaustein(r);
            case 1:
            case 2:
                return ipBaustein(r);
            case 3:
                return urlBaustein(r);
            case 4:
            case 5:
                return pfadBaustein(r);
            case 6:
                return (r.nextInt(5) < 3 ? "LANR " : "") + SynthetischeKvNummern.bildeLanr(r);
            case 7:
                return (r.nextInt(5) < 3 ? "IK " : "") + SynthetischeKvNummern.bildeIk(r);
            case 8:
            case 9:
                return kvnrForm(r);
            case 10:
                return pick(r, KONTEXT);
            case 11:
                return ziffernBaustein(r);
            case 12:
                return pick(r, KONTEXT_KVNR) + " " + kvnrForm(r);
            default:
                return pick(r, List.of("Praxis", "Patient", "und", "Befund", "ist", "am"));
        }
    }

    private static boolean istBestandBaustein(Random r) {
        return r.nextInt(2) == 0;
    }

    /** Ein Mischtext: 2 bis 9 Bausteine; Bestandsbausteine oft mit KVNR-Kontextwort davor und Kompositum dahinter. */
    private static String text(Random r) {
        StringBuilder sb = new StringBuilder();
        int n = 2 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            if (istBestandBaustein(r)) {
                int w = r.nextInt(4);
                String objekt = w == 0 ? emailBaustein(r) : w == 1 ? ipBaustein(r) : w == 2 ? urlBaustein(r)
                        : pfadBaustein(r);
                if (r.nextInt(5) < 3) {
                    sb.append(pick(r, KONTEXT_KVNR)).append(pick(r, TRENNER.subList(0, 3)));
                }
                sb.append(objekt);
                if (r.nextInt(5) == 0) {
                    sb.append(pick(r, KOMPOSITA));
                }
            } else {
                sb.append(baustein(r));
            }
            sb.append(pick(r, TRENNER));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Vergleich

    private static boolean istBestandstyp(Finding f) {
        FindingType t = f.type();
        return t.equals(FindingType.EMAIL) || t.equals(FindingType.IP) || t.equals(FindingType.URL)
                || t.equals(FindingType.PFAD);
    }

    private static List<Finding> bestand(List<Finding> funde) {
        List<Finding> r = new ArrayList<>();
        for (Finding f : funde) {
            if (istBestandstyp(f)) {
                r.add(f);
            }
        }
        return r;
    }

    /** Der Scanner ohne Filterstufe: dieselben Erkenner wie {@code standard()}, direkt ueber den Aufloeser. */
    private static List<Finding> ohneFilter(String text) {
        List<Finding> kandidaten = new ArrayList<>();
        for (Recognizer r : TextScanner.standard().recognizers()) {
            kandidaten.addAll(r.detect(text));
        }
        return ConflictResolver.resolve(text, kandidaten);
    }

    /**
     * Nachbildung der Verwerfen-Variante aus Durchlauf 3: Ein Kandidat eines nachrangigen Erkenners, der einen
     * Kandidaten von E-Mail, IP, URL oder Pfad schneidet, entfaellt ganz (kein Zuschneiden).
     */
    private static List<Finding> verwerfenVariante(String text) {
        List<Finding> kandidaten = new ArrayList<>();
        List<Finding> nachrangig = new ArrayList<>();
        for (Recognizer r : TextScanner.standard().recognizers()) {
            (r.nachrangig() ? nachrangig : kandidaten).addAll(r.detect(text));
        }
        List<Finding> bestand = bestand(kandidaten);
        for (Finding k : nachrangig) {
            boolean schneidet = false;
            for (Finding b : bestand) {
                schneidet |= b.start() < k.end() && k.start() < b.end();
            }
            if (!schneidet) {
                kandidaten.add(k);
            }
        }
        return ConflictResolver.resolve(text, kandidaten);
    }

    /** Ob ein Restfund der Nachrechnung (Quelle {@code kvnr-rest}) eine Position hat, die kein Fund deckt. */
    private static boolean restLuecke(int laenge, Zuschnitt zuschnitt, List<Finding> neu) {
        boolean[] abgedeckt = new boolean[laenge];
        for (Finding f : neu) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        for (Finding r : zuschnitt.kandidaten()) {
            if (r.source().equals(KvnrRecognizer.SOURCE_REST)) {
                for (int i = r.start(); i < r.end(); i++) {
                    if (!abgedeckt[i]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ob ein Fund mit der Quelle {@code kvnr-rest} einen Fund von E-Mail, IP, URL oder Pfad schneidet. */
    private static boolean restSchneidetBestand(List<Finding> neu) {
        for (Finding r : neu) {
            if (r.source().equals(KvnrRecognizer.SOURCE_REST)) {
                for (Finding b : bestand(neu)) {
                    if (b.start() < r.end() && r.start() < b.end()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean abdeckungVerloren(int laenge, List<Finding> alt, List<Finding> neu) {
        boolean[] abgedeckt = new boolean[laenge];
        for (Finding f : neu) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                if (!abgedeckt[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fuehrt den Lauf mit der gewaehlten Variante aus; Zaehler zum Zuschneiden nur bei {@code STANDARD}. */
    private static Zaehler lauf(long seed, int anzahl, Variante variante) {
        Random r = new Random(seed);
        TextScanner neuScanner = TextScanner.standard();
        Zaehler z = new Zaehler();
        for (int i = 0; i < anzahl; i++) {
            String text = text(r);
            while (KvnrSynthetischWaechter.pruefe(text).verstoesse() > 0) {
                z.verworfeneNeuErzeugt++;
                text = text(r);
            }
            z.texte++;
            List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
            List<Finding> neu = switch (variante) {
                case STANDARD -> neuScanner.scan(text);
                case OHNE_FILTER -> ohneFilter(text);
                case VERWERFEN -> verwerfenVariante(text);
            };
            Zuschnitt zuschnitt = KvnrKollisionsNachrechnung.kvnrZuschnitt(text);
            if (!bestand(neu).equals(bestand(alt))) {
                z.abweichungen++;
            }
            if (abdeckungVerloren(text.length(), alt, neu)) {
                z.abdeckungVerloren++;
            }
            if (restLuecke(text.length(), zuschnitt, neu)) {
                z.restLueckeTexte++;
            }
            if (restSchneidetBestand(neu)) {
                z.restUeberschneidung++;
            }
            if (variante == Variante.STANDARD) {
                z.mitZugeschnittenem += zuschnitt.zugeschnitten() > 0 ? 1 : 0;
                z.mitRestfund += zuschnitt.restfunde() > 0 ? 1 : 0;
                z.restfundeGesamt += zuschnitt.restfunde();
                z.unterSchwelleGesamt += zuschnitt.unterSchwelle();
                z.mitKvnrFund += neu.stream().anyMatch(f -> f.type().equals(FindingType.KVNR)) ? 1 : 0;
                z.ohneFilterAbweichend += bestand(ohneFilter(text)).equals(bestand(alt)) ? 0 : 1;
                for (FindingType t : ALTTYPEN) {
                    if (alt.stream().anyMatch(f -> f.type().equals(t))) {
                        z.jeAlttyp.merge(t.name(), 1, Integer::sum);
                    }
                }
            }
        }
        return z;
    }

    // ------------------------------------------------------------------ Tests

    /**
     * Mit Filterstufe und Zuschneiden: Teilliste der Bestandsfunde gleich der des alten Scanners, Abdeckung bleibt,
     * und jede Position eines Restfundes der unabhaengigen Nachrechnung (Reststueck mit mindestens drei Ziffern
     * ausserhalb des Bestands) ist von einem Fund abgedeckt; Restfunde schneiden keinen Bestandsfund. Istwerte
     * (Seed fest, 24 000 Texte, 2 352 wegen des Waechters neu erzeugt): 15 178 Texte mit zugeschnittenem Kandidaten,
     * 3 053 mit Restfund (3 272 Restfunde, 2 110 entfallene Reststuecke unter der Schwelle), 12 637 mit KVNR-Fund,
     * 4 808, in denen der Scanner ohne Filter abweicht; Texte mit altem Fund je Typ: E-Mail 13 526, IP 14 539,
     * URL 13 634, Pfad 10 964, LANR 2 457, IK 2 346. Die Verwerfen-Variante laesst in 3 041 Texten ein Reststueck
     * ungeschuetzt.
     */
    @Test
    void standard_bestandsfundeGleichDemAltenScanner_abdeckungUndRestabdeckungBleiben() {
        Zaehler z = lauf(SEED, TEXTE, Variante.STANDARD);
        assertEquals(TEXTE, z.texte, "Zahl der Texte; " + z.kurz());
        assertEquals(0, z.abweichungen, "Texte mit abweichender Bestandsliste; " + z.kurz());
        assertEquals(0, z.abdeckungVerloren, "Texte mit verlorener Abdeckung; " + z.kurz());
        assertEquals(0, z.restLueckeTexte, "Texte mit nicht abgedecktem Reststueck; " + z.kurz());
        assertEquals(0, z.restUeberschneidung, "Texte mit Restfund im Bestand; " + z.kurz());
        assertTrue(z.mitZugeschnittenem >= MIN_MIT_ZUGESCHNITTENEM, "zu wenig Texte mit zugeschnittenem Kandidaten; "
                + z.kurz());
        assertTrue(z.mitRestfund >= MIN_MIT_RESTFUND, "zu wenig Texte mit Restfund; " + z.kurz());
        assertTrue(z.restfundeGesamt >= MIN_RESTFUNDE, "zu wenig Restfunde; " + z.kurz());
        assertTrue(z.unterSchwelleGesamt >= MIN_UNTER_SCHWELLE, "zu wenig entfallene Reststuecke; " + z.kurz());
        assertTrue(z.mitKvnrFund >= MIN_MIT_KVNR_FUND, "zu wenig Texte mit KVNR-Fund; " + z.kurz());
        assertTrue(z.ohneFilterAbweichend >= MIN_OHNE_FILTER, "zu wenig Texte, die ohne Filter abweichen; "
                + z.kurz());
        int[] mins = {MIN_EMAIL, MIN_IP, MIN_URL, MIN_PFAD, MIN_LANR, MIN_IK};
        for (int i = 0; i < ALTTYPEN.size(); i++) {
            String name = ALTTYPEN.get(i).name();
            assertTrue(z.jeAlttyp.getOrDefault(name, 0) >= mins[i], "zu wenig Texte mit Alttyp " + name + "; "
                    + z.kurz());
        }
    }

    /** Selbsttest: Der Scanner ohne Filterstufe wird in einer Mindestzahl von Texten als Abweichung gemeldet. */
    @Test
    void selbsttest_scannerOhneFilterstufeWirdGemeldet() {
        Zaehler z = lauf(SEED, TEXTE, Variante.OHNE_FILTER);
        assertTrue(z.abweichungen >= MIN_OHNE_FILTER, "Scanner ohne Filter zu selten gemeldet; " + z.kurz());
    }

    /**
     * Selbsttest: Die Verwerfen-Variante aus Durchlauf 3 haelt die Bestandsliste ein, laesst aber Ziffern der
     * Reststuecke im Klartext; sie wird ueber die Rest-Abdeckung in einer Mindestzahl von Texten gemeldet.
     */
    @Test
    void selbsttest_verwerfenVarianteWirdUeberDieRestabdeckungGemeldet() {
        Zaehler z = lauf(SEED, TEXTE, Variante.VERWERFEN);
        assertEquals(0, z.abweichungen, "Die Verwerfen-Variante haelt die Bestandsliste ein; " + z.kurz());
        assertTrue(z.restLueckeTexte >= MIN_VERWERFEN_GEMELDET, "Verwerfen-Variante zu selten gemeldet; "
                + z.kurz());
    }

    /** Selbsttest: Die Wiederholung desselben Seeds liefert dieselben Zaehler (Determinismus der Erzeugung). */
    @Test
    void erzeugung_istDeterministisch() {
        Zaehler a = lauf(SEED, 300, Variante.STANDARD);
        Zaehler b = lauf(SEED, 300, Variante.STANDARD);
        assertEquals(a.kurz(), b.kurz(), "Zaehler bei Wiederholung");
    }

    /** Mindestzahlen: 70 Prozent des Istwerts (abgerundet); Istwerte im Kommentar der Testmethode. */
    private static final int MIN_MIT_ZUGESCHNITTENEM = 10_624;
    private static final int MIN_MIT_RESTFUND = 2_137;
    private static final int MIN_RESTFUNDE = 2_290;
    private static final int MIN_UNTER_SCHWELLE = 1_477;
    private static final int MIN_VERWERFEN_GEMELDET = 2_128;
    private static final int MIN_MIT_KVNR_FUND = 8_845;
    private static final int MIN_OHNE_FILTER = 3_365;
    private static final int MIN_EMAIL = 9_468;
    private static final int MIN_IP = 10_177;
    private static final int MIN_URL = 9_543;
    private static final int MIN_PFAD = 7_674;
    private static final int MIN_LANR = 1_719;
    private static final int MIN_IK = 1_642;
}
