package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.skillshield.core.recognizer.kv.BsnrSynthetischWaechter;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Testdaten sind synthetisch (T-20; AK-53). Der Test liest alle {@code *.java} unter {@code src/test/java}
 * (Surefire-Arbeitsverzeichnis ist das Modulverzeichnis) und setzt die Regeln des Plan-Nachtrags 21.11.2026 um.
 * Pfade, Namen und Personen prüft er nicht; dafür gilt die Durchsicht durch den reviewer.
 *
 * <p><b>E-Mail.</b> Eine E-Mail-ähnliche Zeichenfolge ist ein {@code @}, direkt gefolgt von einem Domain-Lauf aus
 * Labels (Buchstaben, Ziffern, {@code -}, {@code _}) mit mindestens einem Punkt; abschließende Punkte und
 * Bindestriche des Laufs zählen nicht, Groß/klein ist egal. Als Zeichenfolge gilt der lokale Teil (Buchstaben,
 * Ziffern, {@code . _ % + -}) vor dem {@code @}, das {@code @} und der Domain-Lauf. Zulässig ist sie, wenn
 * (a) das letzte Label {@code example}, {@code invalid}, {@code test} oder {@code localhost} lautet oder die Domain
 * auf {@code example.com}, {@code example.org} oder {@code example.net} endet (als ganze Labelfolge), oder
 * (b) das letzte Label kürzer als zwei Zeichen ist (Kunstproben der Erkenner-Tests; keine echte Endung ist
 * einbuchstabig), oder (c) sie in der geschlossenen Ausnahmeliste steht.
 *
 * <p><b>IPv4.</b> Geprüft wird jede alleinstehende gültige IPv4: eine maximale Kette aus Ziffern und Punkten (Punkte
 * am Anfang und Ende zählen nicht zur Kette) mit genau vier Blöcken, jeder numerisch 0 bis 255 (führende Nullen
 * erlaubt). Sie muss numerisch in 192.0.2.0/24, 198.51.100.0/24 oder 203.0.113.0/24 liegen. Ketten mit anderer
 * Blockzahl oder einem Block über 255 sind keine alleinstehende gültige Adresse und werden nicht geprüft.
 *
 * <p><b>Geschlossene Ausnahmeliste.</b> Jeder Eintrag ist eine exakte Zeichenfolge, so wie die Prüfung sie aus dem
 * Quelltext liest, mit Begründung. Jede nicht zulässige Zeichenfolge außerhalb der Liste macht den Test rot; ein
 * Eintrag, der im Quelltext nicht mehr als nicht zulässige Zeichenfolge vorkommt, ebenfalls (keine verwaisten
 * Ausnahmen). Diese Datei prüft sich selbst mit; nur die Zeilen der Ausnahmeliste (zwischen den beiden
 * Markierungszeilen) sind ausgenommen, denn dort stehen die Einträge im Wortlaut. Beispiele für unzulässige
 * Zeichenfolgen stehen in den Selbsttests nur verkettet, nie als zusammenhängendes Literal.
 *
 * <p><b>Ziffernketten</b> (Feature kv-nummern-erkennung, AK-28). Gepruefte Quellen dieser Regel: der Baum
 * {@code src/test/java} dieses Moduls, {@code src/test/resources} dieses Moduls (alle Textdateien) und die Testbaeume
 * {@code src/test} der Module io, vault, ui und app; ohne Projektwurzel oder ohne Testbaum eines dieser Module ist der
 * Test rot (Ausnahme: {@code src/test/resources} dieses Moduls darf fehlen). Es werden nur normale Dateien gelesen
 * (Symlinks werden nicht verfolgt); eine Datei ueber 5 MB ist ein Testfehler. Dateien, die kein gueltiges UTF-8 sind,
 * werden als ISO-8859-1 gelesen. Je Zeile werden vor der Kettenpruefung
 * Unicode-Escapes (Backslash, ein oder mehrere {@code u}, vier Hexziffern) und Oktal-Escapes aufgeloest, die ein
 * druckbares ASCII-Zeichen oder das geschuetzte Leerzeichen ergeben, ebenso die Escapes fuer Tab und Leerzeichen.
 * Spalten in Meldungen beziehen sich auf die aufgeloeste Zeile. E-Mail- und
 * IPv4-Regel gelten nur im Baum {@code src/test/java} dieses Moduls.
 *
 * <p>Eine Kette besteht aus Ziffernbloecken (ASCII-Ziffern), die durch je genau einen Trenner verbunden sind:
 * Leerzeichen, Tab, geschuetztes Leerzeichen, {@code -}, {@code .}, {@code /} oder ein Unterstrich zwischen zwei
 * Ziffern; zwei Trenner hintereinander beenden die Kette. Verstoss ist eine Kette (1) mit insgesamt genau neun
 * Ziffern, (2) mit einem Block aus genau neun Ziffern, (3) mit irgendeiner Folge aufeinanderfolgender Bloecke, die
 * zusammen genau neun Ziffern haben (gleitende Neunerfolge), wobei die Kette zuerst an den Punkten in punktfreie
 * Teilketten zerlegt wird und jede Teilkette einzeln gleitend geprueft wird; Teilketten, deren Trenner alle
 * Unterstriche sind (Java-Zahlenliteral), werden nicht gleitend geprueft. Ein Unicode-Escape, das einen
 * Rueckwaertsstrich ergibt (Backslash, ein oder mehrere {@code u}, 005C in beliebiger Schreibweise), ist ein eigener
 * Verstoss, weil er die Escape-Aufloesung umgehen koennte; im Bestand kommt das Muster nicht vor, es gibt keine
 * Ausnahme dafuer. Zulaessig ist
 * sie, wenn (a) sie Teil eines Platzhalters ist: ein einzelner Ziffernblock, unmittelbar davor {@code [[}, ein
 * Typname ({@code [A-Z][A-Z0-9]{0,31}}) und {@code _}, unmittelbar danach {@code ]]}; (b) sie selbst eine gueltige
 * IPv4 im Dokumentationsbereich ist; (c) sie in der Datei {@code SynthetischeKvNummern.java} steht und jede ihrer
 * neunstelligen Ziffernfolgen (Gesamtzahl neun oder Block aus neun) mit {@code 00} beginnt oder an den Stellen 3 bis 7
 * {@code 00000} hat (BSNR-Form nach A-6, Feature kvnr-bsnr-erkennung, T-12) oder die eng gefasste Sonderkennung
 * (Praefix 35 oder 75, Schluessel der Anlage 1 an Stelle 3 und 4, {@code 00000} an Stelle 5 bis 9; T-13; sie muss
 * zusaetzlich nach dem LANR- und nach dem IK-Verfahren ungueltig sein, das prueft
 * {@code BsnrSynthetischWaechter#istSonderkennungMitSchluessel} mit Selbsttest); alle anderen Folgen bleiben
 * {@code ZENTRALE_OHNE_PRAEFIX_00}); oder (d) sie in der
 * geschlossenen Ausnahmeliste {@code AUSNAHMEN_ZIFFERN} steht. Ein Eintrag gilt nur fuer genau eine benannte Datei
 * (Dateiname) und genau diese Kette; ein Eintrag, der in dieser Datei nicht mehr als Verstoss vorkommt, macht den
 * Test rot. Meldungen nennen Datei, Zeile, Spalte und die Art des Verstosses, nie die Ziffern.
 *
 * <p><b>KVNR-Form</b> (Feature kvnr-bsnr-erkennung, T-3). Steht unmittelbar vor einer Kette ein einzelner
 * ASCII-Buchstabe (davor kein weiterer Buchstabe) oder ein einzelner Buchstabe und genau ein Trenner (Buchstabe,
 * optional ein Trenner, Ziffern), ist die Kette der Ziffernteil einer KVNR-Form (zwei Buchstaben oder das Ende eines
 * Wortes sind keine). Ausserhalb der zentralen Klasse ist eine KVNR-Form mit neunstelliger Folge im Sinne der Regeln
 * (1) bis (3) ein Verstoss ohne jede Ausnahme: Eine Kette, die {@code AUSNAHMEN_ZIFFERN} sonst zulaesst, bleibt als
 * {@code KVNR_FORM} ein Verstoss. Eine Kette, die ohnehin ein Verstoss ist, behaelt ihre bisherige Art
 * ({@code NEUN_ZIFFERN_*}), damit sich keine bestehende Erwartung aendert. In der zentralen Klasse muss jede
 * KVNR-Form mit neunstelliger Folge mit den Ziffern {@code 0000} beginnen (Stelle 2 bis 5, Anforderung A-6, H-1;
 * Verstoss {@code ZENTRALE_KVNR_OHNE_0000}), alle anderen neunstelligen Folgen weiter mit {@code 00}. Das
 * Lehrbeispiel der Quelle hat keine Ziffern {@code 0000} und steht in der zentralen Klasse aus Teilen zusammengesetzt
 * (keine zusammenhaengende neunstellige Folge); der Laufzeit-Waechter {@code KvnrSynthetischWaechter} kennt es als
 * einzige Ausnahme. Acht oder zehn Ziffern hinter dem Buchstaben sind (wie bei jeder Kette) keine neunstellige Folge.
 *
 * <p><b>Verbleibende Grenzen der Ziffernketten-Regel.</b> Nicht erkannt werden: verkettete Literale (Teilstuecke, die
 * erst zur Laufzeit eine Neunerfolge ergeben), reine Zahlenliterale mit Unterstrichen (nur Gesamtzahl und Block,
 * nicht gleitend), {@code char}-Arrays, Neunerfolgen, die ueber Zeilen oder Textbloecke
 * verteilt sind, Trenner ausserhalb der genannten Liste sowie Dateien ausserhalb der geprueften Baeume
 * (zum Beispiel {@code docs/} und {@code profiles/}), UTF-16-Dateien und Binaerformate. Ebenfalls nicht erkannt
 * wird eine Neunerfolge, die nur ueber Punkte hinweg als Praefix einer laengeren Kette entsteht (die ersten Bloecke
 * einer Kette mit Punkt ergeben zusammen neun Ziffern, danach folgen weitere Bloecke, zum Beispiel
 * IP-Adressketten wie 300.300.192.0.2.10): eine solche Regel schlaegt im Bestand an 17 Stellen in
 * {@code Ipv4RecognizerTest} an (zu viele fuer Einzelausnahmen), deshalb gilt sie nicht. Vollbreite Ziffern
 * (U+FF10 bis U+FF19) zaehlen hier nicht als Ziffern: Das legt der bestehende Selbsttest
 * {@code selbsttest_ziffern_keinVerstoss_andereLaengenUndZweiTrenner} fest, eine Aenderung wuerde diese Erwartung
 * brechen. Der Laufzeit-Waechter {@code KvnrSynthetischWaechter} zaehlt sie als Ziffern und deckt die Felder der
 * zentralen Klasse zur Laufzeit ab. Diese Faelle werden durch die Durchsicht von reviewer und security gedeckt.
 */
class TestdatenSynthetischTest {

    private static final Path QUELLE = Path.of("src", "test", "java");
    private static final String EIGENE_DATEI = "de/skillshield/core/TestdatenSynthetischTest.java";
    private static final String ZENTRALE_DATEI = "de/skillshield/core/SynthetischeKvNummern.java";
    private static final String LISTE_ANFANG = "// AUSNAHMELISTE-BEGIN";
    private static final String LISTE_ENDE = "// AUSNAHMELISTE-ENDE";

    /**
     * Untergrenzen, damit die Pruefung nicht ins Leere laeuft. Sie liegen nahe am Istwert und sind bei einem
     * Abbau von Tests bewusst anzupassen.
     */
    private static final int MIN_DATEIEN = 50;
    private static final int MIN_EMAILS = 405;
    private static final int MIN_IPV4 = 315;
    private static final int MIN_KETTEN = 12300;
    private static final int MIN_NEUNER_KETTEN = 92;
    private static final char NBSP = 0xA0;

    private static final Set<String> RESERVIERTE_LABELS = Set.of("example", "invalid", "test", "localhost");
    private static final List<String> RESERVIERTE_ENDUNGEN = List.of("example.com", "example.org", "example.net");

    private static final Pattern AT_UND_DOMAIN = Pattern.compile("@([A-Za-z0-9_.-]+)");
    private static final Pattern IP_KETTE = Pattern.compile("[0-9.]+");

    // AUSNAHMELISTE-BEGIN
    private static final Map<String, String> AUSNAHMEN = Map.ofEntries(
        Map.entry("a@b.invalidC", "Grenzfallprobe: reservierte Endung mit angehaengtem Buchstaben (EmailRecognizer)"),
        Map.entry("erika.mustermann@example.invalidC",
            "Grenzfallprobe: reservierte Domain mit angehaengtem Buchstaben, erfundener Name"),
        Map.entry("d@example.invalid.erika",
            "Grenzfallprobe: reservierte Domain, dahinter ein weiteres Label (Mehrfachpunkt-Grenze)"),
        Map.entry("erika@praxis.example.max",
            "Grenzfallprobe: reserviertes Label example in der Mitte, dahinter ein erfundenes Label"),
        Map.entry("erika@example.invalid_x",
            "Grenzfallprobe: Unterstrich im letzten Label der reservierten Domain"),
        Map.entry("erika@example.invalid_",
            "Grenzfallprobe: Unterstrich am Ende des letzten Labels der reservierten Domain"),
        Map.entry("@gemeinschaftspraxis-dr-mustermann.allgemeinmedizin-musterstadt-nord",
            "Grenzfallprobe: Bruchstueck einer Probe mit ueberlangem Label, erfundene Praxis- und Ortsnamen"),
        Map.entry("nutzer@192.0.2.10",
            "Grenzfallprobe: Benutzerteil vor IP-Adresse im Dokumentationsbereich 192.0.2.0/24 (RFC 5737)"),
        Map.entry("geheim@192.0.2.10",
            "Grenzfallprobe: Userinfo vor IP-Adresse im Dokumentationsbereich 192.0.2.0/24 (RFC 5737)"));

    /**
     * Ausnahmen der Regel "Ziffernketten": je Eintrag genau eine Datei (Dateiname) und genau die Kette, so wie sie nach
     * dem Aufloesen der Escapes im Quelltext steht, mit Begruendung.
     */
    private static final Map<ZiffernAusnahme, String> AUSNAHMEN_ZIFFERN = Map.ofEntries(
        Map.entry(new ZiffernAusnahme("PlaceholderMappingTest.java", "123456789"),
            "Zaehlfolge als Argument von PlaceholderSyntax.format, keine KV-Nummer, "
                + "nach beiden Pruefziffernverfahren ungueltig"),
        Map.entry(new ZiffernAusnahme("Ipv4RecognizerTest.java", "110.0.0.1234"),
            "Kettenprobe: Block ueber 255, keine gueltige IPv4 und keine KV-Nummer"),
        Map.entry(new ZiffernAusnahme("Ipv4RecognizerTest.java", "192.0.2.10.5.7"),
            "Kettenprobe: Dokumentationsadresse mit angehaengten Bloecken, keine KV-Nummer; "
                + "die Ziffern sind nach dem LANR-Verfahren zufaellig rechnerisch gueltig"),
        Map.entry(new ZiffernAusnahme("Ipv4RecognizerTest.java", "192.0.2.10/24"),
            "Probe: Dokumentationsadresse mit Praefixlaenge (CIDR), keine KV-Nummer; "
                + "die Ziffern sind nach dem LANR-Verfahren zufaellig rechnerisch gueltig"),
        Map.entry(new ZiffernAusnahme("Ipv4RecognizerTest.java", "192.0.2.1234"),
            "Probe: Dokumentationsadresse mit Block ueber 255, keine KV-Nummer"),
        Map.entry(new ZiffernAusnahme("PlaceholderSyntaxTest.java", "999_999_999"),
            "Grenzwert fuer Platzhalternummern (Zahlenliteral), keine KV-Nummer; nach dem IK-Verfahren ungueltig, "
                + "nach dem LANR-Verfahren zufaellig rechnerisch gueltig (Summe 351 + 9 = 360)"));
    // AUSNAHMELISTE-ENDE

    /** Art eines Verstosses der Regel "Ziffernketten". Die Beschreibung nennt nie die Ziffern selbst. */
    enum Art {
        NEUN_ZIFFERN_GESAMT("Kette mit insgesamt neun Ziffern"),
        NEUN_ZIFFERN_BLOCK("Kette mit einem Block aus neun Ziffern"),
        NEUN_ZIFFERN_GLEITEND("Kette mit aufeinanderfolgenden Bloecken aus zusammen neun Ziffern"),
        BACKSLASH_ESCAPE("Unicode-Escape fuer den Rueckwaertsstrich (Escape-Aufloesung waere umgehbar)"),
        ZENTRALE_OHNE_PRAEFIX_00("neunstellige Folge ohne fuehrende Doppelnull und ohne Nullblock nach dem "
                + "Regionalcode in der zentralen Klasse"),
        KVNR_FORM("KVNR-Form (Buchstabe, optional ein Trenner, neun Ziffern) trotz Ausnahme ausserhalb der Zentrale"),
        ZENTRALE_KVNR_OHNE_0000("KVNR-Form in der zentralen Klasse ohne vier Nullen am Anfang der Ziffern");

        private final String beschreibung;

        Art(String beschreibung) {
            this.beschreibung = beschreibung;
        }

        String beschreibung() {
            return beschreibung;
        }
    }

    /** Ein Verstoss der Regel "Ziffernketten": Fundstelle (Spalte in der aufgeloesten Zeile) und Art. */
    record KettenVerstoss(String datei, int zeile, int spalte, Art art) {

        /** Meldung mit Datei, Zeile, Spalte und Art; die Ziffern der Kette stehen nicht darin. */
        String meldung() {
            return datei + ":" + zeile + ":" + spalte + ": Ziffernkette, " + art.beschreibung();
        }
    }

    /** Schluessel einer Ausnahme der Regel "Ziffernketten": genau eine Datei (Dateiname) und genau eine Kette. */
    record ZiffernAusnahme(String datei, String kette) {
    }

    /** Ergebnis der Pruefung eines oder mehrerer Texte. */
    record Ergebnis(List<String> verstoesse, List<KettenVerstoss> kettenVerstoesse, Set<String> benutzteAusnahmen,
            Set<ZiffernAusnahme> benutzteZiffernAusnahmen, int emails, int ipv4, int ketten, int neunerKetten) {

        Ergebnis() {
            this(new ArrayList<>(), new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), 0, 0, 0, 0);
        }

        Ergebnis plus(Ergebnis o) {
            List<String> v = new ArrayList<>(verstoesse);
            v.addAll(o.verstoesse);
            List<KettenVerstoss> kv = new ArrayList<>(kettenVerstoesse);
            kv.addAll(o.kettenVerstoesse);
            Set<String> b = new LinkedHashSet<>(benutzteAusnahmen);
            b.addAll(o.benutzteAusnahmen);
            Set<ZiffernAusnahme> bz = new LinkedHashSet<>(benutzteZiffernAusnahmen);
            bz.addAll(o.benutzteZiffernAusnahmen);
            return new Ergebnis(v, kv, b, bz, emails + o.emails, ipv4 + o.ipv4, ketten + o.ketten,
                    neunerKetten + o.neunerKetten);
        }
    }

    // ------------------------------------------------------------------ Ziffernketten

    /**
     * Eine Kette aus Ziffernbloecken, die durch je genau einen Trenner verbunden sind.
     *
     * @param start Position des ersten Zeichens in der (aufgeloesten) Zeile
     * @param text die Kette so, wie sie in der aufgeloesten Zeile steht (samt Trennern)
     * @param bloecke die Ziffernbloecke ohne Trenner
     * @param trenner die Trenner zwischen den Bloecken; Zeichen {@code i} steht zwischen Block {@code i} und
     *        {@code i + 1}
     * @param platzhalter ob die Kette Teil eines Platzhalters ist: ein einzelner Ziffernblock, davor Klammerpaar,
     *        Typname und Unterstrich, danach zwei schliessende eckige Klammern
     */
    record Kette(int start, String text, List<String> bloecke, String trenner, boolean platzhalter) {

        int ziffern() {
            return bloecke.stream().mapToInt(String::length).sum();
        }

        /** Ist mindestens ein Trenner ein Punkt? Dann wird die Kette an den Punkten in Teilketten zerlegt. */
        boolean mitPunkt() {
            return trenner.indexOf('.') >= 0;
        }

        /** Sind alle Trenner Unterstriche (oder gibt es keinen)? Reines Zahlenliteral, nicht gleitend geprueft. */
        boolean nurUnterstriche() {
            return trenner.chars().allMatch(c -> c == '_');
        }

        /** Neunstellige Ziffernfolgen der Kette: die ganze Kette bei genau neun Ziffern, sonst Bloecke mit neun. */
        List<String> neunerFolgen() {
            if (ziffern() == 9) {
                return List.of(String.join("", bloecke));
            }
            return bloecke.stream().filter(b -> b.length() == 9).toList();
        }

        /** Art des Verstosses der Regel "Ziffernketten" (ohne Ausnahmen) oder {@code null}, wenn keiner vorliegt. */
        Art verstossArt() {
            if (ziffern() == 9) {
                return Art.NEUN_ZIFFERN_GESAMT;
            }
            if (bloecke.stream().anyMatch(b -> b.length() == 9)) {
                return Art.NEUN_ZIFFERN_BLOCK;
            }
            if (hatGleitendeNeunerfolgeInTeilketten()) {
                return Art.NEUN_ZIFFERN_GLEITEND;
            }
            return null;
        }

        /**
         * Gibt es in einer Teilkette (maximale Folge von Bloecken ohne Punkt als Trenner) aufeinanderfolgende
         * Bloecke mit zusammen genau neun Ziffern? Teilketten, deren Trenner alle Unterstriche sind (Zahlenliteral),
         * zaehlen nicht. Linear.
         */
        private boolean hatGleitendeNeunerfolgeInTeilketten() {
            int von = 0;
            for (int i = 0; i <= trenner.length(); i++) {
                if (i == trenner.length() || trenner.charAt(i) == '.') {
                    boolean literal = trenner.substring(von, i).chars().allMatch(c -> c == '_');
                    if (!literal && hatGleitendeNeunerfolge(bloecke.subList(von, i + 1))) {
                        return true;
                    }
                    von = i + 1;
                }
            }
            return false;
        }

        /** Gibt es aufeinanderfolgende Bloecke mit zusammen genau neun Ziffern? Linear (Fenster ueber Bloecke). */
        private static boolean hatGleitendeNeunerfolge(List<String> bloecke) {
            int summe = 0;
            int von = 0;
            for (int bis = 0; bis < bloecke.size(); bis++) {
                summe += bloecke.get(bis).length();
                while (summe > 9 && von <= bis) {
                    summe -= bloecke.get(von).length();
                    von++;
                }
                if (summe == 9) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Anfang eines Platzhalters: Klammerpaar, Typname, Unterstrich. */
    private static final Pattern PLATZHALTER_ANFANG = Pattern.compile("\\[\\[[A-Z][A-Z0-9]{0,31}_");

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    /** Trenner innerhalb einer Kette (der Aufrufer prueft Ziffern auf beiden Seiten, auch beim Unterstrich). */
    private static boolean istTrenner(char c) {
        return c == ' ' || c == '\t' || c == NBSP || c == '-' || c == '.' || c == '/' || c == '_';
    }

    /** Darf ein Escape zu diesem Zeichen aufgeloest werden? Druckbares ASCII (ohne Backslash), Tab, NBSP. */
    private static boolean istAufloesbar(int zeichen) {
        return (zeichen >= 0x20 && zeichen <= 0x7E && zeichen != '\\') || zeichen == '\t' || zeichen == NBSP;
    }

    private static int hexWert(String z, int von) {
        int wert = 0;
        for (int k = von; k < von + 4; k++) {
            int d = Character.digit(z.charAt(k), 16);
            if (d < 0 || z.charAt(k) > 'f') {
                return -1;
            }
            wert = wert * 16 + d;
        }
        return wert;
    }

    private static boolean istOktal(String z, int i) {
        return i < z.length() && z.charAt(i) >= '0' && z.charAt(i) <= '7';
    }

    /**
     * Loest in einer Quelltextzeile die Escapes auf, die ein druckbares ASCII-Zeichen, Tab oder NBSP ergeben:
     * Unicode-Escapes (Backslash, ein oder mehrere {@code u}, vier Hexziffern), Oktal-Escapes (eine bis drei Ziffern
     * nach der Java-Regel) und die Escapes fuer Tab und Leerzeichen. Ein doppelter Backslash ist ein Backslash und
     * leitet kein Escape ein. Alles andere bleibt unveraendert.
     */
    static String loeseEscapesAuf(String z) {
        StringBuilder sb = new StringBuilder(z.length());
        int n = z.length();
        int i = 0;
        while (i < n) {
            char c = z.charAt(i);
            if (c != '\\' || i + 1 >= n) {
                sb.append(c);
                i++;
                continue;
            }
            char naechstes = z.charAt(i + 1);
            if (naechstes == '\\') {
                sb.append("\\\\");
                i += 2;
            } else if (naechstes == 'u') {
                int j = i + 1;
                while (j < n && z.charAt(j) == 'u') {
                    j++;
                }
                int wert = j + 4 <= n ? hexWert(z, j) : -1;
                if (wert >= 0 && istAufloesbar(wert)) {
                    sb.append((char) wert);
                    i = j + 4;
                } else {
                    sb.append(c);
                    i++;
                }
            } else if (naechstes == 't' || naechstes == 's') {
                sb.append(naechstes == 't' ? '\t' : ' ');
                i += 2;
            } else if (istOktal(z, i + 1)) {
                int ende = i + 2;
                int maxEnde = naechstes <= '3' ? i + 4 : i + 3;
                while (ende < maxEnde && istOktal(z, ende)) {
                    ende++;
                }
                int wert = Integer.parseInt(z.substring(i + 1, ende), 8);
                if (istAufloesbar(wert)) {
                    sb.append((char) wert);
                    i = ende;
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Position (ab 0) des ersten Unicode-Escapes der (nicht aufgeloesten) Zeile, das einen Rueckwaertsstrich ergibt
     * (Backslash, ein oder mehrere {@code u}, Hexziffern 005C in beliebiger Gross- und Kleinschreibung), sonst -1.
     * Ein doppelter Backslash leitet kein Escape ein. Solche Escapes sind verboten, weil sie die Escape-Aufloesung
     * umgehen koennten.
     */
    static int backslashEscapePosition(String z) {
        int n = z.length();
        int i = 0;
        while (i < n) {
            if (z.charAt(i) != '\\') {
                i++;
            } else if (i + 1 < n && z.charAt(i + 1) == '\\') {
                i += 2;
            } else if (i + 1 < n && z.charAt(i + 1) == 'u') {
                int j = i + 1;
                while (j < n && z.charAt(j) == 'u') {
                    j++;
                }
                if (j + 4 <= n && hexWert(z, j) == '\\') {
                    return i;
                }
                i++;
            } else {
                i++;
            }
        }
        return -1;
    }

    /** Alle Ziffernketten einer (aufgeloesten) Zeile, von links nach rechts, ohne Ueberlappung. */
    static List<Kette> ziffernKetten(String zeile) {
        char[] z = zeile.toCharArray();
        // Platzhalteranfaenge maskieren: Ziffern im Typnamen (zum Beispiel X1) bilden keine Kette
        Set<Integer> platzhalterEnden = new HashSet<>();
        Matcher m = PLATZHALTER_ANFANG.matcher(zeile);
        while (m.find()) {
            Arrays.fill(z, m.start(), m.end(), '#');
            platzhalterEnden.add(m.end());
        }
        List<Kette> ketten = new ArrayList<>();
        int n = z.length;
        int i = 0;
        while (i < n) {
            if (!istZiffer(z[i])) {
                i++;
                continue;
            }
            int start = i;
            List<String> bloecke = new ArrayList<>();
            StringBuilder trenner = new StringBuilder();
            while (true) {
                int blockAnfang = i;
                while (i < n && istZiffer(z[i])) {
                    i++;
                }
                bloecke.add(zeile.substring(blockAnfang, i));
                if (i + 1 < n && istTrenner(z[i]) && istZiffer(z[i + 1])) {
                    trenner.append(z[i]);
                    i++;
                } else {
                    break;
                }
            }
            boolean platzhalter = bloecke.size() == 1 && platzhalterEnden.contains(start)
                    && zeile.startsWith("]]", i);
            ketten.add(new Kette(start, zeile.substring(start, i), bloecke, trenner.toString(), platzhalter));
        }
        return ketten;
    }

    /** Dateiname ohne Verzeichnisanteil (letzter Teil hinter dem Schraegstrich). */
    static String dateiname(String datei) {
        return datei.substring(datei.lastIndexOf('/') + 1);
    }

    /** Ist die Kette selbst eine alleinstehende gueltige IPv4 im Dokumentationsbereich (Regel b)? */
    private static boolean istDokuIpv4(String kette) {
        List<String> a = ipv4Adressen(kette);
        return a.size() == 1 && a.get(0).equals(kette) && ipv4InDokumentationsbereich(kette);
    }

    // ------------------------------------------------------------------ E-Mail

    /** Alle E-Mail-ähnlichen Zeichenfolgen einer Zeile (lokaler Teil, {@code @}, Domain-Lauf ohne Endpunkte). */
    static List<String> emailKandidaten(String zeile) {
        List<String> treffer = new ArrayList<>();
        Matcher m = AT_UND_DOMAIN.matcher(zeile);
        while (m.find()) {
            String domain = m.group(1);
            int ende = domain.length();
            while (ende > 0 && (domain.charAt(ende - 1) == '.' || domain.charAt(ende - 1) == '-')) {
                ende--;
            }
            domain = domain.substring(0, ende);
            if (domain.indexOf('.') < 0) {
                continue;
            }
            int anfang = m.start();
            while (anfang > 0 && istLokalesZeichen(zeile.charAt(anfang - 1))) {
                anfang--;
            }
            treffer.add(zeile.substring(anfang, m.start()) + "@" + domain);
        }
        return treffer;
    }

    private static boolean istLokalesZeichen(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_'
                || c == '%' || c == '+' || c == '-';
    }

    /** Regeln (a) und (b): zulässig ohne Ausnahmeliste. */
    static boolean emailZulaessigOhneListe(String zeichenfolge) {
        String domain = zeichenfolge.substring(zeichenfolge.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        String letztes = domain.substring(domain.lastIndexOf('.') + 1);
        if (RESERVIERTE_LABELS.contains(letztes)) {
            return true;
        }
        for (String endung : RESERVIERTE_ENDUNGEN) {
            if (domain.equals(endung) || domain.endsWith("." + endung)) {
                return true;
            }
        }
        return letztes.length() < 2;
    }

    // ------------------------------------------------------------------ IPv4

    /** Alle alleinstehenden gültigen IPv4-Adressen einer Zeile (siehe Klassenbeschreibung). */
    static List<String> ipv4Adressen(String zeile) {
        List<String> treffer = new ArrayList<>();
        Matcher m = IP_KETTE.matcher(zeile);
        while (m.find()) {
            String kette = m.group();
            int von = 0;
            int bis = kette.length();
            while (von < bis && kette.charAt(von) == '.') {
                von++;
            }
            while (bis > von && kette.charAt(bis - 1) == '.') {
                bis--;
            }
            kette = kette.substring(von, bis);
            if (istGueltigeIpv4(kette)) {
                treffer.add(kette);
            }
        }
        return treffer;
    }

    private static boolean istGueltigeIpv4(String kette) {
        String[] bloecke = kette.split("\\.", -1);
        if (bloecke.length != 4) {
            return false;
        }
        for (String b : bloecke) {
            if (b.isEmpty() || blockWert(b) > 255) {
                return false;
            }
        }
        return true;
    }

    /** Numerischer Wert eines Blocks aus Ziffern; führende Nullen zählen nicht, Werte über 255 liefern 256. */
    private static int blockWert(String block) {
        int i = 0;
        while (i < block.length() - 1 && block.charAt(i) == '0') {
            i++;
        }
        String ohneNullen = block.substring(i);
        return ohneNullen.length() > 3 ? 256 : Integer.parseInt(ohneNullen);
    }

    /** Liegt die (gültige) Adresse numerisch in einem der drei Dokumentationsbereiche (RFC 5737)? */
    static boolean ipv4InDokumentationsbereich(String ip) {
        String[] b = ip.split("\\.", -1);
        int a = blockWert(b[0]);
        int bb = blockWert(b[1]);
        int c = blockWert(b[2]);
        return (a == 192 && bb == 0 && c == 2) || (a == 198 && bb == 51 && c == 100)
                || (a == 203 && bb == 0 && c == 113);
    }

    // ------------------------------------------------------------------ Prüfung eines Textes

    /**
     * Prueft einen Quelltext zeilenweise. In der eigenen Datei sind die Zeilen zwischen den Markierungszeilen der
     * Ausnahmeliste ausgenommen.
     */
    static Ergebnis pruefe(String datei, String text, Set<String> ausnahmen) {
        return pruefe(datei, text, ausnahmen, Set.of());
    }

    /** Wie {@link #pruefe(String, String, Set)}, mit der Ausnahmeliste der Regel "Ziffernketten". */
    static Ergebnis pruefe(String datei, String text, Set<String> ausnahmen, Set<ZiffernAusnahme> ziffernAusnahmen) {
        return pruefe(datei, text, ausnahmen, ziffernAusnahmen, false);
    }

    /** Wie oben; bei {@code nurKetten} gilt nur die Regel "Ziffernketten" (E-Mail und IPv4 werden nicht geprueft). */
    static Ergebnis pruefe(String datei, String text, Set<String> ausnahmen, Set<ZiffernAusnahme> ziffernAusnahmen,
            boolean nurKetten) {
        String[] zeilen = text.split("\\R", -1);
        boolean ausgenommen = false;
        Ergebnis erg = new Ergebnis();
        for (int i = 0; i < zeilen.length; i++) {
            String zeile = zeilen[i];
            if (datei.equals(EIGENE_DATEI)) {
                if (zeile.trim().equals(LISTE_ANFANG)) {
                    ausgenommen = true;
                    continue;
                }
                if (zeile.trim().equals(LISTE_ENDE)) {
                    ausgenommen = false;
                    continue;
                }
                if (ausgenommen) {
                    continue;
                }
            }
            String ort = datei + ":" + (i + 1) + ": ";
            int emails = 0;
            int ips = 0;
            if (!nurKetten) {
                for (String kandidat : emailKandidaten(zeile)) {
                    emails++;
                    if (emailZulaessigOhneListe(kandidat)) {
                        continue;
                    }
                    if (ausnahmen.contains(kandidat)) {
                        erg.benutzteAusnahmen().add(kandidat);
                    } else {
                        erg.verstoesse().add(ort + "E-Mail-aehnlich, nicht zulaessig: " + kandidat);
                    }
                }
                for (String ip : ipv4Adressen(zeile)) {
                    ips++;
                    if (!ipv4InDokumentationsbereich(ip)) {
                        erg.verstoesse().add(ort + "IPv4 ausserhalb der Dokumentationsbereiche: " + ip);
                    }
                }
            }
            Ergebnis ketten = pruefeKetten(datei, i + 1, loeseEscapesAuf(zeile), ziffernAusnahmen);
            int escapePosition = backslashEscapePosition(zeile);
            if (escapePosition >= 0) {
                KettenVerstoss v = new KettenVerstoss(datei, i + 1, escapePosition + 1, Art.BACKSLASH_ESCAPE);
                ketten.kettenVerstoesse().add(v);
                ketten.verstoesse().add(v.meldung());
            }
            erg = erg.plus(new Ergebnis(new ArrayList<>(), new ArrayList<>(), new LinkedHashSet<>(),
                    new LinkedHashSet<>(), emails, ips, 0, 0)).plus(ketten);
        }
        return erg;
    }

    /** Die Ziffern, mit denen jede KVNR-Form der zentralen Klasse beginnt (Stelle 2 bis 5 der KVNR, A-6, H-1). */
    private static final String KVNR_ZIFFERN_PRAEFIX = "0000";

    /** Die Ziffern an Stelle 3 bis 7 einer BSNR-Testnummer nach A-6 (Feature kvnr-bsnr-erkennung, T-12). */
    private static final String BSNR_NULLBLOCK = "00000";

    /**
     * Ist die neunstellige Folge eine BSNR-Form nach A-6: {@code 00000} an Stelle 3 bis 7, oder die eng gefasste
     * Sonderkennung (Praefix 35 oder 75, Schluessel der Anlage 1 an Stelle 3 und 4, {@code 00000} an Stelle 5 bis 9;
     * Nachtrag zu T-13); die Sonderkennung muss nach dem LANR- und nach dem IK-Verfahren ungueltig sein (das
     * prueft {@code BsnrSynthetischWaechter#istSonderkennungMitSchluessel}; sonst entstuende ein LANR- oder IK-Fund).
     *
     * <p>Eine Folge mit {@code 00000} an Stelle 3 bis 7 ist nur zulaessig, wenn sie nach dem LANR- und nach dem
     * IK-Verfahren ungueltig ist (sonst entstuende bei Kontextwort ein LANR- oder IK-Fund; Nachtrag 2 zu T-15) oder
     * eine der ausdruecklich benannten Ausnahmen ist: {@code BSNR_NULL} (Nullnummer, nach beiden Verfahren
     * zufaellig gueltig; nur fuer BSNR-Faelle ohne LANR- oder IK-Kontext, im Javadoc der Konstante begruendet) und
     * {@code BSNR_LANR_GUELTIG} (Gleichstand BSNR/LANR im Formularfeld, G-D1; im Javadoc der Konstante begruendet).
     * Jede weitere Ausnahme braucht eine Entscheidung und einen Eintrag hier.
     */
    static boolean istBsnrForm(String neunerFolge) {
        if (neunerFolge.length() == 9 && neunerFolge.startsWith(BSNR_NULLBLOCK, 2)) {
            return BSNR_AUSNAHMEN_GUELTIG.contains(neunerFolge)
                    || (!SynthetischeKvNummern.lanrGueltigReferenz(neunerFolge)
                            && !SynthetischeKvNummern.ikGueltigReferenz(neunerFolge));
        }
        return BsnrSynthetischWaechter.istSonderkennungMitSchluessel(neunerFolge);
    }

    /** Die ausdruecklich benannten Nullblock-Folgen, die nach LANR oder IK gueltig sein duerfen (siehe oben). */
    private static final Set<String> BSNR_AUSNAHMEN_GUELTIG =
            Set.of(SynthetischeKvNummern.BSNR_NULL, SynthetischeKvNummern.BSNR_LANR_GUELTIG);

    private static boolean istAsciiBuchstabe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Steht unmittelbar vor der Kette ein einzelner ASCII-Buchstabe, oder ein einzelner Buchstabe und genau ein
     * Trenner? Dann ist die Kette der Ziffernteil einer KVNR-Form (Buchstabe, optional ein Trenner, Ziffern). Ein
     * Buchstabe gilt nur als einzeln, wenn davor kein weiterer ASCII-Buchstabe steht: Zwei oder mehr Buchstaben sind
     * keine KVNR-Form (Anforderung D-1), das Ende eines Wortes (zum Beispiel {@code Quelle}) ebenso nicht.
     */
    static boolean buchstabeDavor(String zeile, int start) {
        int buchstabe = start - 1;
        if (buchstabe >= 1 && istTrenner(zeile.charAt(buchstabe)) && istAsciiBuchstabe(zeile.charAt(buchstabe - 1))) {
            buchstabe--;
        }
        if (buchstabe < 0 || !istAsciiBuchstabe(zeile.charAt(buchstabe))) {
            return false;
        }
        return buchstabe == 0 || !istAsciiBuchstabe(zeile.charAt(buchstabe - 1));
    }

    /**
     * Ist eine neunstellige Folge ohne Vorlauf {@code 0000} nach der Referenz eine gueltige KVNR, wenn einer der bis
     * zu drei Zeichen vor der Kette ein ASCII-Buchstabe ist? Gilt in der zentralen Klasse auch dann, wenn
     * {@link #buchstabeDavor} nicht greift (zum Beispiel Doppelpunkt und Leerzeichen oder zwei Leerzeichen). Grenze:
     * Die Regel arbeitet je Zeile; Buchstabe und Ziffern in verschiedenen Zeilen oder in verketteten Literalen
     * erfasst sie nicht (dort gilt nur die Praefixregel 00). Zur Laufzeit schliesst der Test der Felder der zentralen
     * Klasse diese Luecke.
     */
    static boolean gueltigeKvnrOhne0000(String zeile, int start, List<String> folgen) {
        char buchstabe = 0;
        for (int abstand = 1; abstand <= 3 && buchstabe == 0 && start - abstand >= 0; abstand++) {
            char c = zeile.charAt(start - abstand);
            buchstabe = istAsciiBuchstabe(c) ? Character.toUpperCase(c) : 0;
        }
        if (buchstabe == 0) {
            return false;
        }
        for (String f : folgen) {
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, f.substring(0, 8));
            if (!f.startsWith(KVNR_ZIFFERN_PRAEFIX) && pruef == f.charAt(8) - '0') {
                return true;
            }
        }
        return false;
    }

    /**
     * Regel "Ziffernketten" fuer eine aufgeloeste Zeile. In der zentralen Klasse gilt die Praefixregel (jede
     * neunstellige Folge nach Gesamtzahl oder Block beginnt mit {@code 00}), dort ohne gleitende Neunerfolge und ohne
     * Ausnahmeliste; in allen anderen Dateien gilt die volle Regel samt Ausnahmeliste.
     */
    private static Ergebnis pruefeKetten(String datei, int zeilenNummer, String zeile,
            Set<ZiffernAusnahme> ziffernAusnahmen) {
        boolean zentral = datei.equals(ZENTRALE_DATEI);
        List<KettenVerstoss> verstoesse = new ArrayList<>();
        Set<ZiffernAusnahme> benutzt = new LinkedHashSet<>();
        int ketten = 0;
        int neuner = 0;
        for (Kette kette : ziffernKetten(zeile)) {
            ketten++;
            List<String> folgen = kette.neunerFolgen();
            if (!folgen.isEmpty()) {
                neuner++;
            }
            boolean verdaechtig = zentral ? !folgen.isEmpty() : kette.verstossArt() != null;
            if (!verdaechtig || kette.platzhalter() || istDokuIpv4(kette.text())) {
                continue;
            }
            int spalte = kette.start() + 1;
            boolean kvnrForm = buchstabeDavor(zeile, kette.start());
            if (zentral) {
                if (kvnrForm) {
                    if (!folgen.stream().allMatch(f -> f.startsWith(KVNR_ZIFFERN_PRAEFIX))) {
                        verstoesse.add(new KettenVerstoss(datei, zeilenNummer, spalte, Art.ZENTRALE_KVNR_OHNE_0000));
                    }
                } else if (!folgen.stream().allMatch(f -> f.startsWith("00") || istBsnrForm(f))) {
                    verstoesse.add(new KettenVerstoss(datei, zeilenNummer, spalte, Art.ZENTRALE_OHNE_PRAEFIX_00));
                } else if (gueltigeKvnrOhne0000(zeile, kette.start(), folgen)) {
                    verstoesse.add(new KettenVerstoss(datei, zeilenNummer, spalte, Art.ZENTRALE_KVNR_OHNE_0000));
                }
                continue;
            }
            ZiffernAusnahme schluessel = new ZiffernAusnahme(dateiname(datei), kette.text());
            if (ziffernAusnahmen.contains(schluessel)) {
                if (kvnrForm) {
                    // Eine KVNR-Form kennt keine Ausnahme: Die Ausnahmeliste der Ziffernketten gilt nicht fuer sie.
                    verstoesse.add(new KettenVerstoss(datei, zeilenNummer, spalte, Art.KVNR_FORM));
                } else {
                    benutzt.add(schluessel);
                }
            } else {
                verstoesse.add(new KettenVerstoss(datei, zeilenNummer, spalte, kette.verstossArt()));
            }
        }
        List<String> meldungen = new ArrayList<>();
        verstoesse.forEach(v -> meldungen.add(v.meldung()));
        return new Ergebnis(meldungen, verstoesse, new LinkedHashSet<>(), benutzt, 0, 0, ketten, neuner);
    }

    /** Ausnahmen, die im Quelltext nicht (mehr) als nicht zulässige Zeichenfolge vorkommen. */
    static <T> Set<T> verwaiste(Set<T> ausnahmen, Set<T> benutzt) {
        Set<T> v = new LinkedHashSet<>(ausnahmen);
        v.removeAll(benutzt);
        return v;
    }

    // ------------------------------------------------------------------ Lesen der Quellen

    /** Obergrenze der Dateigroesse; eine groessere Datei ist ein Testfehler, kein stilles Ueberspringen. */
    private static final long MAX_DATEIGROESSE = 5L * 1024 * 1024;

    private static boolean istNormaleDatei(Path p) {
        return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static byte[] liesBytes(Path datei, String name) throws IOException {
        long groesse = Files.size(datei);
        if (groesse > MAX_DATEIGROESSE) {
            fail("Datei zu gross fuer die Pruefung (Obergrenze " + MAX_DATEIGROESSE + " Bytes): " + name);
        }
        return Files.readAllBytes(datei);
    }

    private static List<Path> testdateien() {
        assertTrue(Files.isDirectory(QUELLE), "Testquellverzeichnis fehlt: " + QUELLE.toAbsolutePath());
        try (Stream<Path> s = Files.walk(QUELLE)) {
            return s.filter(p -> istNormaleDatei(p) && p.getFileName().toString().endsWith(".java")).sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relativ(Path datei) {
        return QUELLE.relativize(datei).toString().replace('\\', '/');
    }

    private static String lies(Path datei) {
        return liesTextDatei(datei, relativ(datei));
    }

    /** Zusaetzliche Quelle der Regel "Ziffernketten": Datei und Name (Praefix, relativer Pfad). */
    record Quelle(Path pfad, String name) {
    }

    private static final List<String> NACHBARMODULE = List.of("skillshield-io", "skillshield-vault", "skillshield-ui",
            "skillshield-app");

    /** Wurzelverzeichnis des Projekts: erstes uebergeordnetes Verzeichnis von {@code QUELLE} mit dem Modul. */
    private static Optional<Path> projektWurzel() {
        for (Path p = QUELLE.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("skillshield-core").resolve("src"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static void sammle(Path basis, String praefix, List<Quelle> ziel) {
        if (!Files.isDirectory(basis)) {
            return;
        }
        try (Stream<Path> s = Files.walk(basis)) {
            s.filter(TestdatenSynthetischTest::istNormaleDatei).sorted().forEach(p -> ziel.add(
                    new Quelle(p, praefix + basis.relativize(p).toString().replace('\\', '/'))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Quellen ausserhalb von {@code src/test/java}: eigene Ressourcen und Testbaeume der Nachbarmodule. Die
     * Projektwurzel und die Testbaeume der Nachbarmodule muessen vorhanden sein, sie duerfen nicht stillschweigend
     * entfallen.
     */
    private static List<Quelle> weitereQuellen() {
        List<Quelle> quellen = new ArrayList<>();
        sammle(QUELLE.resolveSibling("resources"), "resources/", quellen);
        Optional<Path> wurzel = projektWurzel();
        assertTrue(wurzel.isPresent(), "Projektwurzel (Verzeichnis mit skillshield-core/src) nicht gefunden");
        for (String modul : NACHBARMODULE) {
            Path baum = wurzel.get().resolve(modul).resolve("src").resolve("test");
            assertTrue(Files.isDirectory(baum), "Testbaum des Nachbarmoduls fehlt: " + modul);
            sammle(baum, modul + "/", quellen);
        }
        return quellen;
    }

    /**
     * Liest eine Datei als Text: zuerst strikt als UTF-8; ist sie kein gueltiges UTF-8, wird sie als ISO-8859-1
     * gelesen (jedes Byte ein Zeichen), damit sie nicht uebersprungen wird. Grenzen: UTF-16-Dateien (Nullbytes
     * zwischen den Zeichen) und Binaerformate (zum Beispiel komprimierte Archive oder Bilder) werden nicht sinnvoll
     * als Text gelesen; Ziffernfolgen darin werden nicht zuverlaessig erkannt.
     */
    private static String liesTextDatei(Path datei, String name) {
        try {
            byte[] bytes = liesBytes(datei, name);
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Ergebnis pruefeAlle(List<Path> dateien) {
        Ergebnis gesamt = new Ergebnis();
        for (Path datei : dateien) {
            gesamt = gesamt.plus(pruefe(relativ(datei), lies(datei), AUSNAHMEN.keySet(),
                    AUSNAHMEN_ZIFFERN.keySet()));
        }
        return gesamt;
    }

    /** Nur die Regel "Ziffernketten", angewandt auf die weiteren Quellen. */
    private static Ergebnis pruefeWeitere() {
        Ergebnis gesamt = new Ergebnis();
        for (Quelle quelle : weitereQuellen()) {
            String text = liesTextDatei(quelle.pfad(), quelle.name());
            gesamt = gesamt.plus(pruefe(quelle.name(), text, Set.of(), AUSNAHMEN_ZIFFERN.keySet(), true));
        }
        return gesamt;
    }

    /** Alle Quellen der Regel "Ziffernketten": der Baum dieses Moduls und die weiteren Quellen. */
    private static Ergebnis pruefeGesamt() {
        return pruefeAlle(testdateien()).plus(pruefeWeitere());
    }

    // ------------------------------------------------------------------ Prüfung des Testbestands

    @Test
    void ak53_alleTestdatenSindSynthetisch_emailsUndIpv4() {
        Ergebnis erg = pruefeAlle(testdateien());
        assertTrue(erg.verstoesse().isEmpty(), "Nicht synthetische Testdaten: " + erg.verstoesse());
    }

    @Test
    void ak53_ausnahmelisteHatKeineVerwaistenEintraege_undJederEintragHatBegruendung() {
        Ergebnis erg = pruefeAlle(testdateien());
        assertEquals(Set.of(), verwaiste(AUSNAHMEN.keySet(), erg.benutzteAusnahmen()),
                "Verwaiste Ausnahmen (im Quelltext nicht mehr vorhanden)");
        AUSNAHMEN.forEach((eintrag, begruendung) -> {
            assertFalse(begruendung.isBlank(), "Begruendung fehlt: " + eintrag);
            assertTrue(eintrag.contains("@"), "Kein E-Mail-Eintrag: " + eintrag);
            assertEquals(List.of(eintrag), emailKandidaten(eintrag), "Eintrag ist keine E-Mail-aehnliche Zeichenfolge");
            assertFalse(emailZulaessigOhneListe(eintrag), "Eintrag ist ohnehin zulaessig: " + eintrag);
        });
    }

    @Test
    void ak53_pruefungLaeuftNichtInsLeere() {
        List<Path> dateien = testdateien();
        assertTrue(dateien.size() >= MIN_DATEIEN, "Gelesene Dateien: " + dateien.size());
        Ergebnis erg = pruefeAlle(dateien);
        assertTrue(erg.emails() >= MIN_EMAILS, "Geprueft E-Mail-aehnliche Zeichenfolgen: " + erg.emails());
        assertTrue(erg.ipv4() >= MIN_IPV4, "Geprueft IPv4-Adressen: " + erg.ipv4());
        // Die eigene Datei ist dabei und enthaelt genau eine Ausnahmeliste
        assertTrue(dateien.stream().map(TestdatenSynthetischTest::relativ).toList().contains(EIGENE_DATEI));
        String eigener = lies(QUELLE.resolve(EIGENE_DATEI));
        assertEquals(1, zaehleZeilen(eigener, LISTE_ANFANG));
        assertEquals(1, zaehleZeilen(eigener, LISTE_ENDE));
    }

    /** Anzahl der Zeilen, die (ohne Randleerraum) genau {@code inhalt} sind. */
    private static int zaehleZeilen(String text, String inhalt) {
        int n = 0;
        for (String zeile : text.split("\\R", -1)) {
            if (zeile.trim().equals(inhalt)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Ziffernketten: Pruefung des Bestands

    @Test
    void ak28_ausnahmelisteZiffernHatKeineVerwaistenEintraege_undJederEintragHatBegruendung() {
        Ergebnis erg = pruefeGesamt();
        assertEquals(Set.of(), verwaiste(AUSNAHMEN_ZIFFERN.keySet(), erg.benutzteZiffernAusnahmen()),
                "Verwaiste Ziffern-Ausnahmen (Kette in der genannten Datei nicht mehr als Verstoss vorhanden)");
        List<String> namen = new ArrayList<>(testdateien().stream().map(p -> dateiname(relativ(p))).toList());
        weitereQuellen().forEach(q -> namen.add(dateiname(q.name())));
        AUSNAHMEN_ZIFFERN.forEach((eintrag, begruendung) -> {
            assertFalse(begruendung.isBlank(), "Begruendung fehlt: " + eintrag.datei());
            assertEquals(eintrag.datei(), dateiname(eintrag.datei()), "Nur der Dateiname, kein Pfad");
            assertEquals(1, Collections.frequency(namen, eintrag.datei()),
                    "Dateiname nicht genau einmal in den geprueften Baeumen: " + eintrag.datei());
            List<Kette> ketten = ziffernKetten(eintrag.kette());
            assertEquals(1, ketten.size(), "Eintrag ist keine einzelne Ziffernkette: " + eintrag.datei());
            assertEquals(eintrag.kette(), ketten.get(0).text());
            assertFalse(ketten.get(0).verstossArt() == null, "Eintrag ist ohnehin zulaessig: " + eintrag.datei());
            assertFalse(istDokuIpv4(eintrag.kette()), "Eintrag ist ohnehin zulaessig: " + eintrag.datei());
        });
        assertEquals(6, AUSNAHMEN_ZIFFERN.size());
    }

    @Test
    void ak28_zentraleKlasse_jedeNeunstelligeFolgeBeginntMit00() {
        List<Path> dateien = testdateien();
        assertTrue(dateien.stream().map(TestdatenSynthetischTest::relativ).toList().contains(ZENTRALE_DATEI));
        String text = lies(QUELLE.resolve(ZENTRALE_DATEI));
        int neunstellige = 0;
        for (String zeile : text.split("\\R", -1)) {
            for (Kette k : ziffernKetten(loeseEscapesAuf(zeile))) {
                if (istDokuIpv4(k.text())) {
                    continue;
                }
                for (String folge : k.neunerFolgen()) {
                    neunstellige++;
                    assertTrue(folge.startsWith("00") || istBsnrForm(folge),
                            "Neunstellige Folge ohne Praefix 00 und ohne BSNR-Form in der zentralen Klasse");
                }
            }
        }
        assertTrue(neunstellige >= 13, "Geprueft neunstellige Folgen in der zentralen Klasse: " + neunstellige);
        assertEquals(List.of(), pruefe(ZENTRALE_DATEI, text, Set.of(), Set.of()).verstoesse());
    }

    @Test
    void ak28_ziffernkettenPruefungLaeuftNichtInsLeere() {
        Ergebnis erg = pruefeAlle(testdateien());
        assertTrue(erg.ketten() >= MIN_KETTEN, "Geprueft Ziffernketten: " + erg.ketten());
        assertTrue(erg.neunerKetten() >= MIN_NEUNER_KETTEN, "Ketten mit neun Ziffern: " + erg.neunerKetten());
    }

    @Test
    void ak28_weitereTestbaeume_ziffernkettenOhneVerstoesse_undNurDieseRegel() {
        Ergebnis erg = pruefeWeitere();
        assertEquals(List.of(), erg.verstoesse(), "Verstoesse in den weiteren Testbaeumen");
        assertEquals(0, erg.emails());
        assertEquals(0, erg.ipv4());
        List<Quelle> quellen = weitereQuellen();
        for (String modul : NACHBARMODULE) {
            assertTrue(quellen.stream().anyMatch(q -> q.name().startsWith(modul + "/")),
                    "Keine Datei gefunden im Testbaum von " + modul);
        }
    }

    // ------------------------------------------------------------------ Selbsttests: Ziffernketten

    /** Escape-Folge fuer ein Zeichen (Backslash, kleines u, vier Hexziffern), nur zur Laufzeit zusammengesetzt. */
    private static String esc(int zeichen) {
        return "\\u" + String.format("%04X", zeichen);
    }

    private static List<Art> arten(String datei, String text, Set<ZiffernAusnahme> ausnahmen) {
        return pruefe(datei, text, Set.of(), ausnahmen, true).kettenVerstoesse().stream()
                .map(KettenVerstoss::art).toList();
    }

    private static List<Art> arten(String text) {
        return arten("a/B.java", text, Set.of());
    }

    private static List<String> kettenTexte(String zeile) {
        return ziffernKetten(zeile).stream().map(Kette::text).toList();
    }

    @Test
    void selbsttest_ziffern_verstoss_genauNeunZiffernInAllenTrennerformen() {
        List<String> beispiele = List.of(
                "001" + "235101", "001" + " 235" + " 101", "001" + "-235" + "-101", "001" + ".235" + ".101",
                "001" + "/235" + "/101", "0012351" + " 01", "001" + NBSP + "235" + NBSP + "101",
                "001" + "\t235" + "\t101", "001" + "_235" + "_101", "001" + " 235" + "-101",
                "x 001" + "235101 y", "(001" + "235101)", "172.16" + ".100.1");
        for (int i = 0; i < beispiele.size(); i++) {
            assertEquals(1, arten("ok\n" + beispiele.get(i) + "\nok").size(), "Beispiel " + i);
        }
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("001" + "235101"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("172.16" + ".100.1"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_BLOCK), arten("12 " + "001" + "235101"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_BLOCK), arten("001" + "235101" + "-2025"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_BLOCK), arten("001" + "235101" + " 12 34"));
    }

    @Test
    void selbsttest_ziffern_verstoss_escapesWerdenAufgeloest() {
        String nbsp = esc(NBSP);
        List<String> beispiele = List.of(
                "001" + nbsp + "235" + nbsp + "101", "001" + esc(NBSP).toLowerCase(Locale.ROOT) + "235101",
                "001" + "\\uu" + "00A0" + "235" + "\\uuu" + "00a0" + "101",
                "001" + esc(' ') + "235" + esc('-') + "101", "001" + esc('.') + "235" + esc('/') + "101",
                "001" + esc('\t') + "235" + esc('_') + "101",
                esc('0') + esc('0') + "1235101", "00" + esc('1') + esc('2') + "35101",
                "\\" + "060" + "\\" + "060" + "1235101", "00" + "\\" + "61" + "\\" + "62" + "35101",
                "001" + "\\" + "40" + "235" + "\\" + "55" + "101", "001" + "\\" + "t235" + "\\" + "s101");
        for (int i = 0; i < beispiele.size(); i++) {
            assertEquals(1, arten(beispiele.get(i)).size(), "Beispiel " + i);
        }
    }

    @Test
    void selbsttest_ziffern_verstoss_gleitendeNeunerfolgeOhnePunkt() {
        List<String> beispiele = List.of(
                "001" + " 235" + " 101" + " 2025", "12" + " 001" + " 235" + " 101", "001" + " 235" + " 101" + "-2025",
                "12" + "/001" + "_235" + " 101", "5" + " 4" + " 123" + " 456" + " 78",
                "1" + "\t2" + "\t3" + "\t4" + "\t5" + "\t6" + "\t7" + "\t8" + "\t9" + "\t11",
                "123" + NBSP + "456" + NBSP + "78" + NBSP + "9" + NBSP + "10");
        for (int i = 0; i < beispiele.size(); i++) {
            Kette kette = ziffernKetten(beispiele.get(i)).get(0);
            assertFalse(kette.mitPunkt(), "Beispiel " + i);
            assertEquals(1, arten(beispiele.get(i)).size(), "Beispiel " + i);
        }
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("001" + " 235" + " 101" + " 2025"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("12" + " 001" + " 235" + " 101"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("001" + " 235" + " 101" + "-2025"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("5" + " 4" + " 123" + " 456" + " 78"));
    }

    @Test
    void selbsttest_ziffern_zahlenliteralMitUnterstrichen_nichtGleitend_gemischtSchon() {
        assertEquals(List.of(), arten("2" + "_000" + "_000" + "_000"));
        assertEquals(List.of(), arten("10" + "_000" + "_000" + "_000"));
        assertEquals(List.of(), arten("123" + "_000" + "_000" + "_000"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("123" + "_456" + "_789"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_BLOCK), arten("1" + "_123456" + "789"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("1" + "_000" + "_000" + " 000"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("12" + " 000" + "_000" + "-000"));
        assertTrue(ziffernKetten("2" + "_000" + "_000").get(0).nurUnterstriche());
        assertFalse(ziffernKetten("2" + "_000" + " 000").get(0).nurUnterstriche());
    }

    @Test
    void selbsttest_ziffern_verstoss_gleitendeNeunerfolgeInPunktfreierTeilkette() {
        // die Kette wird an den Punkten aufgeteilt; jede punktfreie Teilkette wird gleitend geprueft
        for (String beispiel : List.of("001" + " 235" + " 101" + ".2025", "001" + " 235" + " 101" + "-2025" + ".1",
                "1.2." + "001" + " 235" + " 101", "7.001" + " 235" + " 101" + ".8",
                "2025." + "001" + " 235" + "/101")) {
            assertTrue(ziffernKetten(beispiel).get(0).mitPunkt(), beispiel.length() + " Zeichen");
            assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten(beispiel), beispiel.length() + " Zeichen");
        }
    }

    @Test
    void selbsttest_ziffern_bekannteGrenze_neunerPraefixUeberPunkteHinweg_wirdNichtErkannt() {
        // Erste Bloecke der ganzen Kette ergeben ueber Punkte hinweg neun Ziffern, danach folgen weitere Bloecke:
        // wird bewusst nicht geprueft (im Bestand 17 Treffer in Ipv4RecognizerTest), siehe Javadoc der Klasse
        for (String beispiel : List.of("001" + ".235" + ".101" + ".2025", "12" + ".345" + ".678" + ".9" + ".11",
                "1234" + ".56789" + ".1", "198.51" + ".100.1" + ".2.3", "123" + "_456" + "_789" + ".5")) {
            assertEquals(List.of(), arten(beispiel), beispiel.length() + " Zeichen");
        }
        // an den Punkten aufgeteilt greift die gleitende Pruefung weiterhin
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("001" + " 235" + " 101" + ".2025"));
    }

    @Test
    void selbsttest_ziffern_keinVerstoss_beiPunktOhneNeunerfolge() {
        List<String> beispiele = List.of(
                "12" + ".001" + ".235" + ".101", "1" + ".001" + ".235" + ".101",
                // weder eine Teilkette noch die Gesamtzahl ergibt neun (Praefix 2 + 4 + 5 = 11 ist zudem keine Regel)
                "12" + ".3456" + ".78901" + ".2",
                // ohne Punkt, aber keine Folge mit Summe neun
                "12" + " 3456" + " 78" + " 90", "0012" + " 3510" + " 12", "001" + " 235" + " 10",
                "8" + " 4" + " 123" + " 456",
                "2026" + "-11" + "-21" + " 12");
        for (int i = 0; i < beispiele.size(); i++) {
            assertEquals(List.of(), arten(beispiele.get(i)), "Beispiel " + i);
        }
    }

    @Test
    void selbsttest_ziffern_backslashEscape_istEigenerVerstoss_ohneInhalt() {
        String u = "\\u";
        for (String beispiel : List.of("a" + u + "005c" + "b", u + "005C", "\\uu" + "005c", "\\uuuu" + "005C",
                "x = \"" + u + "005c" + u + "0030\"")) {
            assertTrue(backslashEscapePosition(beispiel) >= 0, beispiel.length() + " Zeichen");
            assertEquals(List.of(Art.BACKSLASH_ESCAPE), arten(beispiel), beispiel.length() + " Zeichen");
        }
        // kein Escape fuer den Backslash: doppelter Backslash, andere Zeichen, zu kurz, keine Hexziffern
        for (String beispiel : List.of("\\\\" + "u" + "005c", u + "005d", u + "005", u + "00", u, "005c", u + "005g",
                "\\" + "134")) {
            assertEquals(-1, backslashEscapePosition(beispiel), beispiel.length() + " Zeichen");
            assertEquals(List.of(), arten(beispiel), beispiel.length() + " Zeichen");
        }
        // Spalte bezieht sich auf die nicht aufgeloeste Zeile; der doppelte Backslash davor zaehlt mit
        assertEquals(3, backslashEscapePosition("\\\\" + "x" + u + "005c"));
        Ergebnis erg = pruefe("a/B.java", "ok\nab " + u + "005c" + "\nok", Set.of(), Set.of(), true);
        assertEquals(List.of("a/B.java:2:4: Ziffernkette, " + Art.BACKSLASH_ESCAPE.beschreibung()),
                erg.verstoesse());
        assertEquals(List.of(new KettenVerstoss("a/B.java", 2, 4, Art.BACKSLASH_ESCAPE)), erg.kettenVerstoesse());
        // auch in der zentralen Klasse, ohne Ausnahmeliste
        assertEquals(List.of(Art.BACKSLASH_ESCAPE), arten(ZENTRALE_DATEI, u + "005c", Set.of()));
    }

    @Test
    void selbsttest_ziffern_dateilesen_utf8_latin1Rueckfall_groessenobergrenze() throws IOException {
        Path verzeichnis = Files.createTempDirectory(Path.of("target"), "ziffern");
        Path utf8 = verzeichnis.resolve("utf8.txt");
        Path latin1 = verzeichnis.resolve("latin1.txt");
        Path gross = verzeichnis.resolve("gross.txt");
        try {
            Files.write(utf8, new byte[] {'a', (byte) 0xC3, (byte) 0xA4, '1'});
            Files.write(latin1, new byte[] {'a', (byte) 0xE4, '1'});
            // genau an der Obergrenze: erlaubt, eins darueber: Testfehler mit Dateiname
            Files.write(gross, new byte[(int) MAX_DATEIGROESSE]);
            assertEquals("a\u00E41", liesTextDatei(utf8, "utf8.txt"));
            assertEquals("a\u00E41", liesTextDatei(latin1, "latin1.txt"));
            assertEquals((int) MAX_DATEIGROESSE, liesTextDatei(gross, "gross.txt").length());
            Files.write(gross, new byte[(int) MAX_DATEIGROESSE + 1]);
            AssertionError fehler = assertThrows(AssertionError.class, () -> liesTextDatei(gross, "gross.txt"));
            assertTrue(fehler.getMessage().contains("gross.txt"), fehler.getMessage());
            assertTrue(istNormaleDatei(utf8));
            assertFalse(istNormaleDatei(verzeichnis));
        } finally {
            Files.deleteIfExists(utf8);
            Files.deleteIfExists(latin1);
            Files.deleteIfExists(gross);
            Files.deleteIfExists(verzeichnis);
        }
    }

    @Test
    void selbsttest_ziffern_keinVerstoss_andereLaengenUndZweiTrenner() {
        List<String> beispiele = List.of(
                "0012351" + "0", "0123456" + "789" + "0", "001235", "0012351" + "01234",
                "001" + "  235" + " 101", "001" + " - 235" + " 101", "0012" + ". 35101", "001" + "__235" + "_101",
                "\uFF10\uFF10\uFF11\uFF12\uFF13\uFF15\uFF11\uFF10\uFF11", "001" + "\\u" + "00A1" + "235" + "101",
                "001" + "\\u" + "00" + " 235" + " 101", "001" + " 235" + " 1" + "\\u" + "00",
                "001" + "\\u" + "00G0" + "235" + "101",
                "2026-11-" + "21 12:30", "198.51" + ".100", "001" + "a235" + "b101", "001" + ":235" + ":101");
        for (int i = 0; i < beispiele.size(); i++) {
            assertEquals(List.of(), arten(beispiele.get(i)), "Beispiel " + i);
        }
        // Unterstrich trennt nur zwischen zwei Ziffern
        assertEquals(List.of(), arten("a_" + "123456" + "_b"));
        assertEquals(List.of("123456"), kettenTexte("a_" + "123456" + "_b"));
    }

    @Test
    void selbsttest_ziffern_platzhalter_unterstrichNurImPlatzhalterZulaessig() {
        String neun = "123456" + "789";
        // zulaessig: Klammerpaar, Typname, Unterstrich, genau ein Ziffernblock, schliessendes Klammerpaar
        assertEquals(List.of(), arten("[[EMAIL_" + "123456" + "789]]"));
        assertEquals(List.of(), arten("[[X1_" + "123456" + "789]]"));
        assertEquals(List.of(), arten("[[" + "A".repeat(32) + "_" + "123456" + "789]]"));
        assertEquals(List.of(), arten("a [[IP4_" + "123456" + "789]] b [[IP_" + "123456" + "789]]"));
        // Verstoss: Unterstrich ohne Klammerpaar und Typname
        assertEquals(1, arten("Datei_" + neun).size());
        assertEquals(1, arten("LANR_" + neun).size());
        assertEquals(1, arten("LANR_" + neun + "]]").size());
        assertEquals(1, arten("[EMAIL_" + neun + "]]").size());
        assertEquals(1, arten("EMAIL_" + neun + "]]").size());
        assertEquals(1, arten("[[email_" + neun + "]]").size());
        assertEquals(1, arten("[[1X_" + neun + "]]").size());
        assertEquals(1, arten("[[]]_" + neun + "]]").size());
        assertEquals(1, arten("[[" + "A".repeat(33) + "_" + neun + "]]").size());
        assertEquals(1, arten("[[EMAIL_X" + neun + "]]").size());
        assertEquals(1, arten("[[EMAIL" + "_ " + neun + "]]").size());
        // mehrere Bloecke im Platzhalter: keine Platzhalternummer, sondern ein Versteck fuer eine Nummer
        assertEquals(1, arten("[[EMAIL_" + neun + "_1]]").size());
        assertEquals(1, arten("[[X1_" + "001 235" + " 101]]").size());
        assertEquals(1, arten("[[X_" + "123_456" + "_789]]").size());
        // nur direkt hinter dem Unterstrich und vor dem schliessenden Klammerpaar
        assertEquals(1, arten("[[EMAIL_" + neun + " ]]").size());
        assertEquals(1, arten("[[EMAIL_" + neun).size());
        assertEquals(1, arten("_ " + neun).size());
        assertEquals(1, arten("_a" + neun).size());
        assertEquals(1, arten(neun + "_").size());
        // Unterstrich zwischen zwei Ziffern ist ein Trenner: das Java-Zahlenliteral mit Unterstrichen
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("123" + "_456" + "_789"));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("int x = 123" + "_456" + "_789;"));
        // ein Platzhalter mit Escape-Unterstrich ist ebenfalls ein Platzhalter
        assertEquals(List.of(), arten("[[EMAIL" + esc('_') + "123456" + "789]]"));
    }

    @Test
    void selbsttest_ziffern_keinVerstoss_dokuIpv4_aberVerstossBeiAndererAdresse() {
        assertEquals(List.of(), arten("IP 198.51." + "100.1 ok"));
        assertEquals(List.of(), arten("203.0." + "113.99"));
        // neun Ziffern in vier Bloecken, aber Adresse ausserhalb der Dokumentationsbereiche
        assertEquals(1, arten("172.16." + "100.1").size());
        // neun Ziffern in vier Bloecken, aber keine gueltige IPv4 (Block ueber 255)
        assertEquals(1, arten("198.51." + "300.1").size());
    }

    @Test
    void selbsttest_ziffern_ausnahmeliste_istGeschlossen_giltNurFuerDieBenannteDatei_undErkenntVerwaiste() {
        String kette = "123" + "456" + "789";
        String text = "a\nf(" + kette + ")\n";
        ZiffernAusnahme eintrag = new ZiffernAusnahme("B.java", kette);
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("a/B.java", text, Set.of()));
        Ergebnis mit = pruefe("a/B.java", text, Set.of(), Set.of(eintrag), true);
        assertEquals(List.of(), mit.verstoesse());
        assertEquals(Set.of(eintrag), mit.benutzteZiffernAusnahmen());
        assertEquals(Set.of(), verwaiste(Set.of(eintrag), mit.benutzteZiffernAusnahmen()));
        // gleicher Dateiname in einem anderen Verzeichnis zaehlt als dieselbe Datei
        assertEquals(List.of(), pruefe("x/y/B.java", text, Set.of(), Set.of(eintrag), true).verstoesse());
        // falsche Datei: Verstoss, und der Eintrag ist verwaist
        Ergebnis falsch = pruefe("a/C.java", text, Set.of(), Set.of(eintrag), true);
        assertEquals(1, falsch.verstoesse().size());
        assertEquals(Set.of(eintrag), verwaiste(Set.of(eintrag), falsch.benutzteZiffernAusnahmen()));
        // eine andere Kette in der richtigen Datei deckt die Zeichenfolge nicht ab und ist verwaist
        ZiffernAusnahme andere = new ZiffernAusnahme("B.java", "123" + "456" + "788");
        Ergebnis ander = pruefe("a/B.java", text, Set.of(), Set.of(andere), true);
        assertEquals(1, ander.verstoesse().size());
        assertEquals(Set.of(andere), verwaiste(Set.of(andere), ander.benutzteZiffernAusnahmen()));
        // zulaessige Ketten verbrauchen keine Ausnahme
        assertEquals(Set.of(), pruefe("a/B.java", "[[E_" + kette + "]]", Set.of(), Set.of(eintrag), true)
                .benutzteZiffernAusnahmen());
        assertEquals(Set.of(), pruefe("a/B.java", "x 12345" + "678", Set.of(), Set.of(eintrag), true)
                .benutzteZiffernAusnahmen());
        // der Dateiname ist der Teil hinter dem letzten Schraegstrich
        assertEquals("B.java", dateiname("a/b/B.java"));
        assertEquals("B.java", dateiname("B.java"));
    }

    @Test
    void selbsttest_ziffern_zentraleKlasse_praefix00Pflicht_ohneGleitendeRegel() {
        String ok = "x = \"00" + "1235101\";";
        String falsch = "x = \"12" + "3456789\";";
        assertEquals(List.of(), arten(ZENTRALE_DATEI, ok, Set.of()));
        assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00), arten(ZENTRALE_DATEI, falsch, Set.of()));
        // in jeder anderen Datei ist auch die Folge mit 00 ein Verstoss
        assertEquals(1, arten("a/B.java", ok, Set.of()).size());
        // in der zentralen Klasse zaehlt jede Folge einer Kette mit mehreren Bloecken
        assertEquals(1, arten(ZENTRALE_DATEI, "00" + "1235101" + "-" + "12" + "3456789", Set.of()).size());
        // Ausnahmeliste gilt dort nicht als Freibrief
        assertEquals(1, arten(ZENTRALE_DATEI, falsch, Set.of(new ZiffernAusnahme("SynthetischeKvNummern.java",
                "12" + "3456789"))).size());
        // die gleitende Neunerfolge gilt dort nicht, in jeder anderen Datei schon
        String gleitend = "12" + " 001" + " 235" + " 101";
        assertEquals(List.of(), arten(ZENTRALE_DATEI, gleitend, Set.of()));
        assertEquals(List.of(Art.NEUN_ZIFFERN_GLEITEND), arten("a/B.java", gleitend, Set.of()));
        // Escapes werden auch dort aufgeloest
        assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00),
                arten(ZENTRALE_DATEI, "12" + esc('3') + "456789", Set.of()));
    }

    @Test
    void selbsttest_ziffern_eigeneDatei_nurAusnahmelistenzeilenSindAusgenommen() {
        String kette = "123" + "456" + "789";
        String text = String.join("\n", kette, LISTE_ANFANG, kette, LISTE_ENDE, kette);
        Ergebnis erg = pruefe(EIGENE_DATEI, text, Set.of(), Set.of(), true);
        assertEquals(List.of(new KettenVerstoss(EIGENE_DATEI, 1, 1, Art.NEUN_ZIFFERN_GESAMT),
                new KettenVerstoss(EIGENE_DATEI, 5, 1, Art.NEUN_ZIFFERN_GESAMT)), erg.kettenVerstoesse());
    }

    @Test
    void selbsttest_ziffern_nurKetten_ignoriertEmailUndIpv4_pruefungMitSieGilt() {
        String text = "n@" + "gmail.com 8.8." + "8.8";
        Ergebnis nur = pruefe("a/B.java", text, Set.of(), Set.of(), true);
        assertEquals(List.of(), nur.verstoesse());
        assertEquals(0, nur.emails());
        assertEquals(0, nur.ipv4());
        assertEquals(1, nur.ketten());
        Ergebnis alle = pruefe("a/B.java", text, Set.of());
        assertEquals(1, alle.emails());
        assertEquals(1, alle.ipv4());
        assertEquals(2, alle.verstoesse().size());
    }

    @Test
    void selbsttest_ziffern_meldungNenntDateiZeileSpalteUndArt_nichtDieZiffern() {
        String text = "ok\nab " + "001" + "235101" + " x\nok";
        Ergebnis erg = pruefe("a/B.java", text, Set.of(), Set.of(), true);
        assertEquals(List.of("a/B.java:2:4: Ziffernkette, Kette mit insgesamt neun Ziffern"), erg.verstoesse());
        assertEquals(List.of(new KettenVerstoss("a/B.java", 2, 4, Art.NEUN_ZIFFERN_GESAMT)),
                erg.kettenVerstoesse());
        for (Art art : Art.values()) {
            assertFalse(art.beschreibung().chars().anyMatch(Character::isDigit), art.name());
        }
        // Spalte bezieht sich auf die aufgeloeste Zeile: ein Escape zaehlt als ein Zeichen
        Ergebnis mitEscape = pruefe("a/B.java", esc('A') + " " + "001" + "235101", Set.of(), Set.of(), true);
        assertEquals(List.of(new KettenVerstoss("a/B.java", 1, 3, Art.NEUN_ZIFFERN_GESAMT)),
                mitEscape.kettenVerstoesse());
    }

    @Test
    void selbsttest_ziffern_loeseEscapesAuf() {
        assertEquals("a0b", loeseEscapesAuf("a" + esc('0') + "b"));
        assertEquals("9", loeseEscapesAuf(esc('9')));
        assertEquals(String.valueOf(NBSP), loeseEscapesAuf("\\u" + "00A0"));
        assertEquals(String.valueOf(NBSP), loeseEscapesAuf("\\u" + "00a0"));
        assertEquals("1", loeseEscapesAuf("\\uu" + "0031"));
        assertEquals("1", loeseEscapesAuf("\\uuuu" + "0031"));
        assertEquals("-./_ \t", loeseEscapesAuf(esc('-') + esc('.') + esc('/') + esc('_') + esc(' ') + esc('\t')));
        // nicht aufloesbar: andere Zeichen, zu kurz, keine Hexziffern, Steuerzeichen, Backslash
        for (String s : List.of("\\u" + "00A1", "\\u" + "00", "\\u" + "00G0", "\\u" + "FF10", esc('\n'), esc('\\'),
                "\\u", "\\", "\\u" + "0030".substring(0, 3))) {
            assertEquals(s, loeseEscapesAuf(s), s.length() + " Zeichen");
        }
        // doppelter Backslash leitet kein Escape ein, dreifacher schon
        assertEquals("\\\\" + "u0030", loeseEscapesAuf("\\\\" + "u0030"));
        assertEquals("\\\\" + "0", loeseEscapesAuf("\\\\" + esc('0')));
        assertEquals("\\\\" + "060", loeseEscapesAuf("\\\\" + "060"));
        // Oktal: eine bis drei Ziffern nach der Java-Regel
        assertEquals("0", loeseEscapesAuf("\\" + "060"));
        assertEquals("0", loeseEscapesAuf("\\" + "60"));
        assertEquals("01", loeseEscapesAuf("\\" + "0601"));
        assertEquals("9", loeseEscapesAuf("\\" + "71"));
        assertEquals("A", loeseEscapesAuf("\\" + "101"));
        assertEquals(" 0", loeseEscapesAuf("\\" + "400"));
        assertEquals("\\" + "0", loeseEscapesAuf("\\" + "0"));
        assertEquals("\\" + "1", loeseEscapesAuf("\\" + "1"));
        assertEquals("\\" + "8", loeseEscapesAuf("\\" + "8"));
        // Escapes fuer Tab und Leerzeichen; andere Buchstaben-Escapes bleiben
        assertEquals("\t", loeseEscapesAuf("\\" + "t"));
        assertEquals(" ", loeseEscapesAuf("\\" + "s"));
        assertEquals("\\" + "n", loeseEscapesAuf("\\" + "n"));
        assertEquals("ohne Escapes 12", loeseEscapesAuf("ohne Escapes 12"));
    }

    @Test
    void selbsttest_ziffern_kettenzerlegung() {
        assertEquals(List.of(), ziffernKetten("keine Ziffern"));
        assertEquals(List.of("12"), kettenTexte("a12b"));
        assertEquals(List.of("1 2-3.4/5"), kettenTexte("x 1 2-3.4/5 y"));
        // Tab, NBSP und Unterstrich zwischen Ziffern sind Trenner
        assertEquals(List.of("1\t2"), kettenTexte("1\t2"));
        assertEquals(List.of("1" + NBSP + "2"), kettenTexte("1" + NBSP + "2"));
        assertEquals(List.of("1_2_3"), kettenTexte("x_1_2_3_y"));
        assertEquals(List.of("12"), kettenTexte("A_12"));
        assertEquals(List.of("12"), kettenTexte("12_"));
        assertEquals(List.of("12"), kettenTexte("_12"));
        // Trenner am Ende und Doppeltrenner gehoeren nicht zur Kette
        assertEquals(List.of("12 34"), kettenTexte("12 34 "));
        assertEquals(List.of("12", "34"), kettenTexte("12  34"));
        assertEquals(List.of("12", "34"), kettenTexte("12-.34"));
        assertEquals(List.of("12", "34"), kettenTexte("12_ 34"));
        assertEquals(List.of("12"), kettenTexte("12."));
        assertEquals(List.of("12", "34"), kettenTexte("12:34"));
        // Escapes werden nicht von der Zerlegung, sondern vorher aufgeloest
        assertEquals(3, ziffernKetten("1" + "\\u" + "00A" + "2").size());
        assertEquals(List.of("1" + NBSP + "2"), kettenTexte(loeseEscapesAuf("1" + esc(NBSP) + "2")));
        // Position, Bloecke, Punkt und Platzhalter
        List<Kette> k = ziffernKetten("ab 12 345 c 6");
        assertEquals(2, k.size());
        assertEquals(3, k.get(0).start());
        assertEquals(List.of("12", "345"), k.get(0).bloecke());
        assertEquals(5, k.get(0).ziffern());
        assertFalse(k.get(0).mitPunkt());
        assertEquals(12, k.get(1).start());
        assertTrue(k.get(1).neunerFolgen().isEmpty());
        assertTrue(ziffernKetten("1.2").get(0).mitPunkt());
        assertTrue(ziffernKetten("[[X1_" + "1234]]").get(0).platzhalter());
        assertEquals(1, ziffernKetten("[[X1_" + "1234]]").size());
        assertFalse(ziffernKetten("[[X1_" + "12 34]]").get(0).platzhalter());
        assertFalse(ziffernKetten("[[X1_" + "1234").get(0).platzhalter());
        assertFalse(ziffernKetten("[[X1_" + "1234]").get(0).platzhalter());
        assertFalse(ziffernKetten("x[[X1" + "1234]]").get(0).platzhalter());
    }

    // ------------------------------------------------------------------ KVNR-Form (Feature kvnr-bsnr-erkennung, T-3)

    /** Die Ketten einer Zeile mit Buchstabe davor und einer neunstelligen Folge (KVNR-Formen im Sinne der Regel). */
    private static List<Kette> kvnrFormen(String zeile) {
        return ziffernKetten(zeile).stream()
                .filter(k -> buchstabeDavor(zeile, k.start()) && !k.neunerFolgen().isEmpty()).toList();
    }

    @Test
    void kvnr_zentraleKlasse_jedeKvnrFormHatNull000AnStelle2Bis5_undDieRegelLaeuftNichtInsLeere() {
        String text = lies(QUELLE.resolve(ZENTRALE_DATEI));
        int formen = 0;
        for (String zeile : text.split("\\R", -1)) {
            for (Kette k : kvnrFormen(loeseEscapesAuf(zeile))) {
                formen++;
                for (String folge : k.neunerFolgen()) {
                    assertTrue(folge.startsWith(KVNR_ZIFFERN_PRAEFIX), "KVNR-Form ohne die Ziffern 0000 in der "
                            + "zentralen Klasse");
                }
            }
        }
        // Istwert 7 (je ein Literal der Vektoren mit neun Ziffern nach dem Buchstaben), Mindestzahl unter 70 Prozent
        assertTrue(formen >= 4, "Geprueft KVNR-Formen in der zentralen Klasse: " + formen);
        assertEquals(List.of(), pruefe(ZENTRALE_DATEI, text, Set.of(), Set.of()).verstoesse());
    }

    /** Eine Ausnahmeliste, die jede Kette der Zeile (nach dem Aufloesen der Escapes) fuer {@code B.java} zulaesst. */
    private static Set<ZiffernAusnahme> alleKettenAlsAusnahme(String zeile) {
        return alleKettenAlsAusnahme("B.java", zeile);
    }

    private static Set<ZiffernAusnahme> alleKettenAlsAusnahme(String dateiname, String zeile) {
        Set<ZiffernAusnahme> ausnahmen = new HashSet<>();
        for (Kette k : ziffernKetten(loeseEscapesAuf(zeile))) {
            ausnahmen.add(new ZiffernAusnahme(dateiname, k.text()));
        }
        return ausnahmen;
    }

    @Test
    void selbsttest_kvnrForm_ausserhalbDerZentralenKlasse_ausnahmeGiltNicht_auchMitTrennernUndKleinbuchstabe() {
        String ziffern = "0000" + "12341";
        List<String> formen = List.of("Z" + ziffern, "z" + ziffern, "Z " + ziffern, "Z-" + ziffern, "Z." + ziffern,
                "Z/" + ziffern, "Z" + NBSP + ziffern, "Z" + "0000 " + "12341", "Z " + "000 " + "012 " + "341",
                "Z" + "0000" + "-12341", "Z" + "00001" + "2341", "(Z" + ziffern + ")", "KVNR: Z" + ziffern + ",",
                "1 Z" + ziffern, "1" + "Z" + ziffern, esc('Z') + ziffern);
        for (int i = 0; i < formen.size(); i++) {
            String text = formen.get(i);
            // ohne Ausnahme bleibt die bisherige Art, mit Ausnahme meldet der Waechter die KVNR-Form
            assertEquals(1, arten(text).size(), "Beispiel " + i);
            assertTrue(arten(text).get(0) != Art.KVNR_FORM, "Beispiel " + i);
            assertEquals(List.of(Art.KVNR_FORM), arten("a/B.java", text, alleKettenAlsAusnahme(text)),
                    "Beispiel " + i + " mit Ausnahme");
        }
    }

    @Test
    void selbsttest_kvnrForm_keineKvnrForm_wennKeinEinzelnerBuchstabeDavorSteht_unddieAusnahmeGilt() {
        String ziffern = "0000" + "12341";
        List<String> keine = List.of(ziffern, "AB" + ziffern, "Quelle " + ziffern, "Quelle" + ziffern,
                "Z" + ", " + ziffern, "Z" + "  " + ziffern, "1" + " " + ziffern.substring(0, 8), "ab-" + ziffern,
                "[[KVNR_" + "12345" + "6789" + "]]");
        for (int i = 0; i < keine.size(); i++) {
            String text = keine.get(i);
            assertEquals(List.of(), arten("a/B.java", text, alleKettenAlsAusnahme(text)), "Beispiel " + i);
        }
        assertEquals(List.of(), arten("Z" + "00001234"));
        assertEquals(List.of(), arten("x" + "198.51.100.1"));
    }

    @Test
    void selbsttest_kvnrForm_zentraleKlasse_0000Pflicht_undPraefix00FuerAlleAnderenNeunerfolgen() {
        String gut = "Z" + "0000" + "12341";
        String ohne0000 = "Z" + "0012" + "34561";
        String lehrbeispielZusammenhaengend = "A" + "1234" + "5678" + "0";
        assertEquals(List.of(), arten(ZENTRALE_DATEI, gut, Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "Z " + "0000 " + "12341", Set.of()));
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, ohne0000, Set.of()));
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, "z-" + "0012" + "34561", Set.of()));
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, lehrbeispielZusammenhaengend,
                Set.of()));
        // keine KVNR-Form (kein Buchstabe davor): weiter nur die Praefixregel 00
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "00" + "1235101", Set.of()));
        assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00), arten(ZENTRALE_DATEI, "12" + "3456789", Set.of()));
        // acht und zehn Ziffern sind keine neunstellige Folge (bekannte Grenze der Regel)
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "Z" + "12345678", Set.of()));
        // in jeder anderen Datei ist auch die gute Form ein Verstoss, mit Ausnahme als KVNR_FORM
        assertEquals(1, arten("a/B.java", gut, Set.of()).size());
        assertEquals(List.of(Art.KVNR_FORM), arten("a/B.java", gut, alleKettenAlsAusnahme(gut)));
    }

    @Test
    void selbsttest_kvnrForm_zentraleKlasse_gueltigeKvnrMitBuchstabeInDreiZeichen_brauchtDieZiffern0000() {
        String acht = "0012" + "3456";
        String neun = KvnrBsnrHilfen.kvnrNeunZiffern('Z', acht);
        String falsch = acht + (KvnrBsnrHilfen.kvnrPruefziffer('Z', acht) + 1) % 10;
        // Buchstabe, Doppelpunkt oder Komma und Leerzeichen, zwei Leerzeichen, Buchstabe vor dem Wort: Verstoss
        for (String vor : List.of("Z, ", "Z: ", "Z  ", "z: ", "Z ", "Z", "z\t")) {
            assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, vor + neun, Set.of()), vor);
        }
        // der Buchstabe geht mit seinem eigenen Wert in die Pruefziffer ein (hier R an dritter Stelle vor der Kette)
        String neunR = KvnrBsnrHilfen.kvnrNeunZiffern('R', acht);
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, "KVNR: " + neunR, Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "KVNR: " + neun, Set.of()));
        // Prefix 0000, falsche Pruefziffer, Buchstabe weiter als drei Zeichen entfernt: kein Verstoss
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "Z, " + KvnrBsnrHilfen.kvnrNeunZiffern('Z', "00001234"),
                Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "Z, " + falsch, Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "Z   " + neun, Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "= \"" + neun, Set.of()));
        // die Meldung nennt keine Ziffern
        String meldung = pruefe(ZENTRALE_DATEI, "Z, " + neun, Set.of(), Set.of(), true).verstoesse().get(0);
        assertFalse(meldung.contains(neun) || meldung.contains(acht));
        // ausserhalb der zentralen Klasse aendert sich nichts: weiter der Verstoss der Neunerfolge
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("a/B.java", "Z, " + neun, Set.of()));
    }

    @Test
    void selbsttest_kvnrForm_waechterFindetEingeschleusteNummer_auchMitAusnahmeliste_ohneZiffernInDerMeldung() {
        String eingeschleust = "Z" + "0000" + "12341";
        String quelltext = "class A {\n    String s = \"" + eingeschleust + "\";\n}\n";
        Ergebnis erg = pruefe("de/x/A.java", quelltext, Set.of(), Set.of(), true);
        assertEquals(List.of(new KettenVerstoss("de/x/A.java", 2, 18, Art.NEUN_ZIFFERN_GESAMT)),
                erg.kettenVerstoesse());
        // auch eine Ausnahmeliste hilft nicht: dann meldet der Waechter die KVNR-Form
        Ergebnis mitAusnahme = pruefe("de/x/A.java", quelltext, Set.of(), alleKettenAlsAusnahme("A.java", quelltext),
                true);
        assertEquals(List.of(new KettenVerstoss("de/x/A.java", 2, 18, Art.KVNR_FORM)),
                mitAusnahme.kettenVerstoesse());
        for (String meldung : erg.verstoesse()) {
            assertFalse(meldung.contains("0000"), "Die Meldung nennt Ziffern");
            assertFalse(meldung.contains("12341"), "Die Meldung nennt Ziffern");
        }
        // dieselbe Nummer in der zentralen Klasse ist zulaessig, in einer Datei des Testbaums nicht
        assertEquals(List.of(), pruefe(ZENTRALE_DATEI, quelltext, Set.of(), Set.of(), true).verstoesse());
        // ein Unicode-Escape fuer den Buchstaben wird vor der Pruefung aufgeloest, die Form wird ebenso gefunden
        String mitEscape = esc('Z') + "0000" + "12341";
        Ergebnis escape = pruefe("de/x/A.java", mitEscape, Set.of(), alleKettenAlsAusnahme("A.java", mitEscape),
                true);
        assertEquals(List.of(Art.KVNR_FORM), escape.kettenVerstoesse().stream().map(KettenVerstoss::art).toList());
    }

    // ------------------------------------------------------------------ BSNR-Form (Feature kvnr-bsnr-erkennung, T-12)

    @Test
    void selbsttest_bsnrForm_zentraleKlasse_nullblockAnStelle3Bis7ZulaessigAlleAnderenBleibenVerstoss() {
        // alle Nummern sind aus der zentralen Klasse abgeleitet, nie als Literal geschrieben
        String bekannt = SynthetischeKvNummern.BSNR_BEKANNT;
        String zwei = SynthetischeKvNummern.BSNR_BEKANNT_ZWEI;
        String nullen = "0".repeat(5);
        String ohneNullblock = bekannt.substring(0, 4) + "7" + bekannt.substring(5);
        String nullblockZuFrueh = bekannt.substring(0, 1) + nullen + bekannt.substring(1, 2) + bekannt.substring(7);
        String nullblockZuSpaet = bekannt.substring(0, 2) + bekannt.charAt(1) + nullen + bekannt.charAt(8);
        String teilweise = bekannt.substring(0, 4) + "5" + bekannt.substring(5);
        // Zulaessig: Nullblock an Stelle 3 bis 7 ohne Praefix 00, mit Trennern und als Block einer Kette
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "x = \"" + bekannt + "\";", Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, bekannt.substring(0, 2) + " " + bekannt.substring(2, 7) + " "
                + bekannt.substring(7), Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, zwei.substring(0, 2) + "-" + zwei.substring(2, 7) + "-"
                + zwei.substring(7), Set.of()));
        // Verstoss wie bisher: der Nullblock steht an einer anderen Stelle oder ist nicht vollstaendig
        for (String falsch : List.of(ohneNullblock, nullblockZuFrueh, nullblockZuSpaet, teilweise)) {
            assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00), arten(ZENTRALE_DATEI, falsch, Set.of()));
        }
        // eine Kette aus zwei Folgen: eine davon ohne Nullblock und ohne Praefix genuegt fuer den Verstoss
        assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00),
                arten(ZENTRALE_DATEI, bekannt + "-" + ohneNullblock, Set.of()));
        // die Erweiterung gilt nur in der zentralen Klasse: in jeder anderen Datei bleibt die BSNR-Form ein Verstoss
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("a/B.java", bekannt, Set.of()));
        // Praefix 00 bleibt unveraendert zulaessig, auch ohne Nullblock
        assertEquals(List.of(), arten(ZENTRALE_DATEI, SynthetischeKvNummern.BSNR_UNBEKANNT, Set.of()));
        // Hilfsmethode: nur neunstellige Folgen mit Nullblock an Stelle 3 bis 7
        assertTrue(istBsnrForm(bekannt));
        assertFalse(istBsnrForm(ohneNullblock));
        assertFalse(istBsnrForm(bekannt.substring(0, 7)));
        assertFalse(istBsnrForm(bekannt + "0"));
    }

    /**
     * Nachtrag 2 zu T-15: Eine Folge mit Nullblock an Stelle 3 bis 7, die nach dem LANR- oder IK-Verfahren gueltig ist,
     * ist nur als benannte Ausnahme zulaessig. Die Folge wird zur Laufzeit gesucht (kein Literal): Praefix ab 10
     * (nicht {@code 00}), Stellen 8 und 9 frei; ausser den beiden benannten Ausnahmen ist jede solche Folge ein
     * Verstoss in der zentralen Klasse.
     */
    @Test
    void selbsttest_bsnrForm_nullblockMitGueltigerLanrOderIk_nurAlsBenannteAusnahmeZulaessig() {
        assertTrue(istBsnrForm(SynthetischeKvNummern.BSNR_NULL));
        assertTrue(istBsnrForm(SynthetischeKvNummern.BSNR_LANR_GUELTIG));
        int lanrGueltig = 0;
        int ikGueltig = 0;
        for (int praefix = 10; praefix <= 99; praefix++) {
            for (int schluss = 0; schluss <= 99; schluss++) {
                String folge = praefix + BSNR_NULLBLOCK + (schluss < 10 ? "0" : "") + schluss;
                if (SynthetischeKvNummern.BSNR_LANR_GUELTIG.equals(folge)) {
                    continue;
                }
                boolean lanr = SynthetischeKvNummern.lanrGueltigReferenz(folge);
                boolean ik = SynthetischeKvNummern.ikGueltigReferenz(folge);
                lanrGueltig += lanr ? 1 : 0;
                ikGueltig += ik ? 1 : 0;
                if (lanr || ik) {
                    assertFalse(istBsnrForm(folge), "Nullblock-Folge, nach LANR oder IK gueltig, ist keine BSNR-Form");
                    assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00), arten(ZENTRALE_DATEI, folge, Set.of()));
                } else {
                    assertTrue(istBsnrForm(folge), "Nullblock-Folge, nach LANR und IK ungueltig, ist eine BSNR-Form");
                }
            }
        }
        // Die Suche ist nicht leer: es gibt weitere solche Folgen (sonst prueft der Test nichts)
        assertTrue(lanrGueltig > 0, "gefundene LANR-gueltige Nullblock-Folgen: " + lanrGueltig);
        assertTrue(ikGueltig > 0, "gefundene IK-gueltige Nullblock-Folgen: " + ikGueltig);
    }

    @Test
    void selbsttest_bsnrForm_zentraleKlasse_kvnrFormBrauchtWeiterDieZiffern0000_auchMitNullblock() {
        // Buchstabe davor: KVNR-Form, dann gilt die Pflicht 0000 statt des Nullblocks an Stelle 3 bis 7
        String bekannt = SynthetischeKvNummern.BSNR_BEKANNT;
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, "Z" + bekannt, Set.of()));
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, "Z " + bekannt, Set.of()));
        // Gegenprobe: ohne Buchstaben davor zulaessig, und die KVNR-Form mit 0000 bleibt zulaessig
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "= " + bekannt, Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, SynthetischeKvNummern.KVNR_GUELTIG, Set.of()));
    }

    @Test
    void selbsttest_bsnrForm_zentraleKlasse_sonderkennungMitSchluessel_engZulaessig() {
        String s35 = SynthetischeKvNummern.BSNR_SONDER_35_SCHLUESSEL;
        String s75 = SynthetischeKvNummern.BSNR_SONDER_75_SCHLUESSEL;
        // Zulaessig, auch mit Trennern
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "x = \"" + s35 + "\";", Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, "x = \"" + s75 + "\";", Set.of()));
        assertEquals(List.of(), arten(ZENTRALE_DATEI, s35.substring(0, 2) + " " + s35.substring(2, 4) + " "
                + s35.substring(4), Set.of()));
        assertTrue(istBsnrForm(s35) && istBsnrForm(s75));
        // knapp daneben bleibt Verstoss: anderes Praefix, Schluessel ausserhalb der Anlage 1, Nullblock unvollstaendig
        String anderesPraefix = "36" + s35.substring(2);
        String schluesselUnbekannt = s35.substring(0, 3) + "4" + s35.substring(4);
        String nullblockUnvollstaendig = s75.substring(0, 8) + "3";
        for (String falsch : List.of(anderesPraefix, schluesselUnbekannt, nullblockUnvollstaendig)) {
            assertEquals(List.of(Art.ZENTRALE_OHNE_PRAEFIX_00), arten(ZENTRALE_DATEI, falsch, Set.of()));
            assertFalse(istBsnrForm(falsch));
        }
        // in jeder anderen Datei bleibt die Form ein Verstoss
        assertEquals(List.of(Art.NEUN_ZIFFERN_GESAMT), arten("a/B.java", s35, Set.of()));
        // die KVNR-Form (Buchstabe davor) braucht weiter die Ziffern 0000
        assertEquals(List.of(Art.ZENTRALE_KVNR_OHNE_0000), arten(ZENTRALE_DATEI, "Z" + s35, Set.of()));
    }

    @Test
    void zentraleKlasse_sonderkennungMitSchluessel_istErfasst() {
        String text = lies(QUELLE.resolve(ZENTRALE_DATEI));
        int sonder = 0;
        for (String zeile : text.split("\\R", -1)) {
            for (Kette k : ziffernKetten(loeseEscapesAuf(zeile))) {
                for (String folge : k.neunerFolgen()) {
                    sonder += BsnrSynthetischWaechter.istSonderkennungMitSchluessel(folge) ? 1 : 0;
                }
            }
        }
        // Istwert 2 (35 und 75 mit Schluessel); Mindestzahl 1 (unter 70 Prozent, aufgerundet)
        assertTrue(sonder >= 1, "Sonderkennungen mit Schluessel in der zentralen Klasse: " + sonder);
    }

    @Test
    void zentraleKlasse_bsnrAbschnitt_wirdErfasstUndKeineMindestzahlSinkt() {
        String text = lies(QUELLE.resolve(ZENTRALE_DATEI));
        int bsnrFormen = 0;
        for (String zeile : text.split("\\R", -1)) {
            for (Kette k : ziffernKetten(loeseEscapesAuf(zeile))) {
                for (String folge : k.neunerFolgen()) {
                    bsnrFormen += istBsnrForm(folge) && !folge.startsWith("00") ? 1 : 0;
                }
            }
        }
        // Istwert 8 (Nullblock ohne Praefix 00: die BSNR-Konstanten mit bekanntem Code, Netzverbund und
        // Sonderkennungen, dazu die zwei Sonderkennungen mit Schluessel); die Mindestzahl 2 bleibt unveraendert
        assertTrue(bsnrFormen >= 2, "BSNR-Formen ohne Praefix 00 in der zentralen Klasse: " + bsnrFormen);
        assertEquals(List.of(), pruefe(ZENTRALE_DATEI, text, Set.of(), Set.of()).verstoesse());
    }

    // ------------------------------------------------------------------ Selbsttests: E-Mail

    @Test
    void selbsttest_email_zulaessigNachRegelA() {
        for (String z : List.of("erika@example.invalid", "a@srv.example", "a@x.test", "a@x.localhost",
                "A@Example.ORG", "a@sub.example.com", "a@example.net.", "x@y.invalid-", "a.b+c_d%e@x.INVALID",
                "a@example.net", "a@b.example.org")) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertTrue(emailZulaessigOhneListe(k.get(0)), "sollte zulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_zulaessigNachRegelB() {
        for (String z : List.of("a@b.c", "a@b.c.", "x@y.z-", "x@a.b.c")) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertTrue(emailZulaessigOhneListe(k.get(0)), "sollte zulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_unzulaessigOhneListe() {
        List<String> beispiele = List.of(
                "name@" + "firma.de", "n@" + "gmail.com", "n@" + "myexample.com", "n@" + "example.com.de",
                "n@" + "x.invalidC", "n@" + "x.example.max", "n@" + "a.b.c.de", "n@" + "sub.web.org",
                "n@" + "x.invalid.erika", "n@" + "x.testx", "n@" + "x.localhost1");
        for (String z : beispiele) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertEquals(z, k.get(0));
            assertFalse(emailZulaessigOhneListe(k.get(0)), "sollte unzulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_keineKandidaten() {
        assertEquals(List.of(), emailKandidaten("@" + " param x.y"));
        assertEquals(List.of(), emailKandidaten("x@" + "ohnepunkt"));
        assertEquals(List.of(), emailKandidaten("x@" + "a."));
        assertEquals(List.of(), emailKandidaten("x@" + "a.-"));
        assertEquals(List.of(), emailKandidaten("n@\"" + "x.de"));
        assertEquals(List.of(), emailKandidaten("@"));
    }

    @Test
    void selbsttest_email_zeichenfolgeEnthaeltLokalenTeilUndEndet_ohneSatzzeichen() {
        assertEquals(List.of("a.b@x.invalid"), emailKandidaten("Mail: \"a.b@x.invalid\", Ende."));
        assertEquals(List.of("a@b.c", "d@e.invalid"), emailKandidaten("a@b.c d@e.invalid."));
    }

    @Test
    void selbsttest_pruefung_meldetDateiZeileUndZeichenfolge() {
        String text = "ok\nn@" + "gmail.com\nok\n";
        Ergebnis erg = pruefe("a/B.java", text, Set.of());
        assertEquals(List.of("a/B.java:2: E-Mail-aehnlich, nicht zulaessig: n@" + "gmail.com"), erg.verstoesse());
        assertEquals(1, erg.emails());
    }

    @Test
    void selbsttest_ausnahmeliste_istGeschlossen_undErkenntVerwaiste() {
        String zeichenfolge = "n@" + "x.invalidC";
        String text = "a\n" + zeichenfolge + "\n";
        // nicht in der Liste: Verstoss
        assertEquals(1, pruefe("a/B.java", text, Set.of()).verstoesse().size());
        // in der Liste: kein Verstoss, Eintrag benutzt
        Ergebnis mit = pruefe("a/B.java", text, Set.of(zeichenfolge));
        assertEquals(List.of(), mit.verstoesse());
        assertEquals(Set.of(zeichenfolge), mit.benutzteAusnahmen());
        assertEquals(Set.of(), verwaiste(Set.of(zeichenfolge), mit.benutzteAusnahmen()));
        // ein anderer Eintrag deckt die Zeichenfolge nicht ab (exakte Zeichenfolge) und ist verwaist
        Ergebnis andere = pruefe("a/B.java", text, Set.of("n@" + "x.invalidD"));
        assertEquals(1, andere.verstoesse().size());
        assertEquals(Set.of("n@" + "x.invalidD"), verwaiste(Set.of("n@" + "x.invalidD"), andere.benutzteAusnahmen()));
        // Eintrag, der gar nicht im Text steht, ist verwaist
        assertEquals(Set.of(zeichenfolge), verwaiste(Set.of(zeichenfolge), Set.of()));
        // zulaessige Zeichenfolgen verbrauchen keine Ausnahme
        assertEquals(Set.of(), pruefe("a/B.java", "a@x.invalid\n", Set.of("a@x.invalid")).benutzteAusnahmen());
    }

    @Test
    void selbsttest_eigeneDatei_nurDieAusnahmelistenzeilenSindAusgenommen() {
        String unzulaessig = "n@" + "gmail.com";
        String text = String.join("\n", unzulaessig, LISTE_ANFANG, unzulaessig, LISTE_ENDE, unzulaessig);
        Ergebnis erg = pruefe(EIGENE_DATEI, text, Set.of());
        assertEquals(2, erg.verstoesse().size());
        assertTrue(erg.verstoesse().get(0).contains(":1: "));
        assertTrue(erg.verstoesse().get(1).contains(":5: "));
        // in jeder anderen Datei gibt es keine Ausnahmezeilen
        assertEquals(3, pruefe("a/B.java", text, Set.of()).verstoesse().size());
    }

    // ------------------------------------------------------------------ Selbsttests: IPv4

    @Test
    void selbsttest_ipv4_innerhalbDerDokumentationsbereiche() {
        for (String ip : List.of("192.0.2.0", "192.0.2.255", "198.51.100.7", "203.0.113.99", "192.000.002.004",
                "203.0.113.099", "x 192.0.2.1:80", "192.0.2.1.", "(198.51.100.1)")) {
            List<String> a = ipv4Adressen(ip);
            assertEquals(1, a.size(), ip);
            assertTrue(ipv4InDokumentationsbereich(a.get(0)), "sollte innerhalb liegen: " + ip);
        }
    }

    @Test
    void selbsttest_ipv4_ausserhalb() {
        for (String ip : List.of("8.8." + "8.8", "1.2." + "3.4", "10.0." + "0.1", "192.0." + "3.1", "192.1." + "2.1",
                "198.51." + "101.1", "203.0." + "114.1", "127.0." + "0.1", "255.255." + "255.255", "0.0." + "0.0",
                "193.0." + "2.1")) {
            List<String> a = ipv4Adressen(ip);
            assertEquals(1, a.size(), ip);
            assertFalse(ipv4InDokumentationsbereich(a.get(0)), "sollte ausserhalb liegen: " + ip);
        }
    }

    @Test
    void selbsttest_ipv4_keineAlleinstehendeGueltigeAdresse_wirdNichtGeprueft() {
        // Kette mit fuenf Bloecken, vier Bloecken plus Rest, drei Bloecke
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.4.5"));
        assertEquals(List.of(), ipv4Adressen("8.8." + "8.8.8"));
        assertEquals(List.of(), ipv4Adressen("8.8." + "8"));
        assertEquals(List.of(), ipv4Adressen("8." + "8"));
        // Block ueber 255
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.256"));
        assertEquals(List.of(), ipv4Adressen("300.1." + "1.1"));
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.1000"));
        // leere Bloecke
        assertEquals(List.of(), ipv4Adressen("1.." + "2.3"));
        // Ziffernreihe ohne Punkt
        assertEquals(List.of(), ipv4Adressen("12345678"));
    }

    @Test
    void selbsttest_ipv4_fuehrendeNullenSindErlaubt_undZaehlenNumerisch() {
        assertEquals(List.of("192.00.002.004"), ipv4Adressen("a 192.00.002.004 b"));
        assertTrue(ipv4InDokumentationsbereich("192.00.002.004"));
        String vierte = "0001.0002." + "0003.0004";
        assertEquals(List.of(vierte), ipv4Adressen(vierte));
        assertFalse(ipv4InDokumentationsbereich(vierte));
        // ein Block mit vielen Nullen und Wert ueber 255 bleibt ungueltig
        assertEquals(List.of(), ipv4Adressen("0001.0002.0003.0256"));
        String grenze = "0001.0002." + "0003.0255";
        assertEquals(List.of(grenze), ipv4Adressen(grenze));
    }

    @Test
    void selbsttest_ipv4_pruefungMeldetDateiZeileUndAdresse() {
        Ergebnis erg = pruefe("a/B.java", "a\nb 8.8." + "8.8 c\n", Set.of());
        assertEquals(List.of("a/B.java:2: IPv4 ausserhalb der Dokumentationsbereiche: 8.8." + "8.8"),
                erg.verstoesse());
        assertEquals(1, erg.ipv4());
        assertEquals(List.of(), pruefe("a/B.java", "x 192.0.2.4:/x\n", Set.of()).verstoesse());
    }
}
