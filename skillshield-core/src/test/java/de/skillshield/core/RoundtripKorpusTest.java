package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_DREI;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_BINDESTRICH;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_GRUPPE_6_3;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_NBSP;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_PUNKT;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_NULL;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_PRUEFZIFFER_NULL;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_LEERZEICHEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_NBSP;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_SCHRAEGSTRICH;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_UNGLEICHE_GRUPPEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_ZIFFER_GEAENDERT;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static de.skillshield.core.TestTexte.bs;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.neunerfenster;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.uzAk1Texte;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Roundtrip-Korpus (T-17; AK-7 (g), AK-25, AK-26, AK-28, AK-37, AK-38, AK-39, AK-54).
 *
 * <p>Je Korpus-Eintrag: {@code TextScanner.standard().scan} (plus ggf. manuelle Funde), alle Funde
 * pseudonymisieren, den erwarteten Ausgabetext, die erwarteten Fundtypen in Textreihenfolge (Fundzahl = Anzahl der
 * Typen) und die Abwesenheit einer ausdrücklich angegebenen Liste verbotener Bestandteile prüfen, dann
 * zurückübersetzen: Ergebnis zeichengleich mit dem Original, keine Meldung. Die Tests dokumentieren auch die
 * bekannten Grenzen aus dem Architektur-Plan (D-5, unsichtbare Zeichen im Kopf).
 *
 * <p>KV-Teil (Feature kv-nummern-erkennung, AK-20, AK-1 bis AK-16): {@link #kvFaelle()} haelt alle Texte mit IK- und
 * LANR-Formen als Liste (auch fuer {@code KvDifferenzTest}). Je Fall: Waechter der Testdatenregel vor dem Scan,
 * Scan, alle Funde pseudonymisieren, erwarteter Ausgabetext und Typen, keine Ziffernkette mit 6+ Ziffern im
 * Ausgabetext (Faelle ohne Fund sind ausgenommen, dort bleibt der Text absichtlich unveraendert), Rueckuebersetzung
 * zeichengleich ohne Meldung. Meldungen nennen nur den Fallnamen. Dazu Konsistenzfaelle (AK-20, OF-5) und die bekannte
 * Grenze 13. Die Faelle {@code kl-...} und {@code bekannteGrenze12R/14a-14g...} (kv-kontext-luecken T-6) decken
 * gesperrt geschriebene Kontextwoerter, {@code I K}, Komma vor dem Kontextwort, Gegenproben und bekannte Grenzen ab;
 * die Faelle {@code ku-...} und {@code bekannteGrenze15Rest-...} (kv-trenner-und-umbruch T-4) decken {@code CR UZ+ LF}
 * als einen Zeilenumbruch, die Gegenproben mit zwei Umbruechen und die Restgrenze 15R ab.
 *
 * <p>Testdaten sind synthetisch ({@code *.invalid}, {@code *.example}, 192.0.2.0/24, erfundene Namen).
 * Backslashes werden über {@link TestTexte#bs(String)} geschrieben; Nicht-ASCII-, unsichtbare und typografische
 * Zeichen stehen nur als Escapes im Quelltext, Zeilenenden nur als Escapes.
 */
class RoundtripKorpusTest {

    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** U+1F600 als Surrogatpaar. */
    private static final String EMOJI = "\uD83D\uDE00";
    private static final String MAIL = "erika.beispiel@example.invalid";
    private static final String AK7G =
            "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende";

    // ------------------------------------------------------------------ Hilfen

    private static String typen(List<Finding> funde) {
        List<String> namen = new ArrayList<>();
        for (Finding f : funde) {
            namen.add(f.type().name());
        }
        return String.join(",", namen);
    }

    /** Roundtrip ohne manuelle Funde. */
    private static PseudonymizationResult roundtrip(String korpus, String text, String erwartet, String typen,
            String... verboten) {
        return roundtrip(korpus, text, List.of(), erwartet, typen, verboten);
    }

    /**
     * Scan plus manuelle Funde, Pseudonymisierung, Prüfung, Rückübersetzung.
     *
     * @param erwartet erwarteter pseudonymisierter Text
     * @param typen    erwartete Typen aller Funde in Textreihenfolge, kommagetrennt ({@code ""} = kein Fund)
     * @param verboten Bestandteile, die im pseudonymisierten Text nicht vorkommen dürfen
     */
    private static PseudonymizationResult roundtrip(String korpus, String text, List<Finding> manuell,
            String erwartet, String typen, String... verboten) {
        List<Finding> funde = new ArrayList<>(SCANNER.scan(text));
        funde.addAll(manuell);
        funde.sort(Comparator.comparingInt(Finding::start));
        assertEquals(typen, typen(funde), korpus + ": Fundzahl und Typen");
        PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals(erwartet, pseudo.text(), korpus + ": pseudonymisierter Text");
        for (int i = 0; i < verboten.length; i++) {
            assertFalse(pseudo.text().contains(verboten[i]), korpus + ": verbotener Bestandteil Nr. " + i);
        }
        ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
        assertEquals(text, zurueck.text(), korpus + ": Roundtrip");
        assertTrue(zurueck.unresolved().isEmpty(), korpus + ": keine Meldung");
        return pseudo;
    }

    /** Manueller Fund für das erste Vorkommen von {@code wert} ab {@code ab}. */
    private static Finding manuell(String typ, String text, String wert, int ab) {
        int start = text.indexOf(wert, ab);
        assertTrue(start >= 0, "Testdaten: Wert nicht im Text");
        return Finding.manual(new FindingType(typ), wert, start, start + wert.length());
    }

    private static ReidentificationResult umformen(PseudonymizationResult pseudo, String ki) {
        return REIDENTIFIER.restore(ki, pseudo.mapping());
    }

    // ------------------------------------------------------------------ AK-9, AK-22: Laufwerkspfade

    @Test
    void ak9Laufwerk_backslash() {
        roundtrip("ak9-laufwerk-backslash", bs("Start C:¦Tools¦BeispielTool¦tool.exe jetzt"),
                "Start [[PFAD_1]] jetzt", "PFAD", "Tools", "BeispielTool", "tool.exe", "C:");
    }

    @Test
    void ak9Laufwerk_schraegstrich() {
        roundtrip("ak9-laufwerk-schraegstrich", "Start C:/Tools/BeispielTool/tool.exe jetzt",
                "Start [[PFAD_1]] jetzt", "PFAD", "Tools", "BeispielTool", "tool.exe");
    }

    @Test
    void ak9Laufwerk_gemischteTrenner() {
        roundtrip("ak9-laufwerk-gemischt", bs("Start C:¦Tools/BeispielTool¦tool.exe jetzt"),
                "Start [[PFAD_1]] jetzt", "PFAD", "Tools", "BeispielTool", "tool.exe");
    }

    @Test
    void ak9Laufwerk_kleinbuchstaben() {
        roundtrip("ak9-laufwerk-klein", bs("Start c:¦tools¦beispieltool¦tool.exe jetzt"),
                "Start [[PFAD_1]] jetzt", "PFAD", "tools", "beispieltool", "tool.exe");
    }

    @Test
    void ak9Laufwerk_abschliessenderTrenner() {
        roundtrip("ak9-laufwerk-trenner-am-ende", bs("Ordner C:¦Tools¦BeispielTool¦ ist leer"),
                "Ordner [[PFAD_1]] ist leer", "PFAD", "Tools", "BeispielTool");
    }

    @Test
    void ak22Laufwerk_doppelteBackslashes() {
        String text = bs("Start C:¦¦Tools¦¦BeispielTool¦¦tool.exe jetzt");
        assertEquals(6, text.chars().filter(c -> c == '\\').count(), "Testdaten: Anzahl Backslashes");
        roundtrip("ak22-laufwerk-doppelt", text, "Start [[PFAD_1]] jetzt", "PFAD", "Tools", "BeispielTool", "tool.exe");
    }

    @Test
    void ak16Laufwerk_anfuehrungszeichenMitLeerzeichen() {
        roundtrip("ak16-laufwerk-doppelt", bs("Starte \"C:¦Program Files¦Mein Tool¦tool.exe\" jetzt"),
                "Starte \"[[PFAD_1]]\" jetzt", "PFAD", "Program", "Files", "Mein", "Tool", "tool.exe");
        roundtrip("ak16-laufwerk-einfach", bs("Starte 'C:¦Program Files¦Mein Tool¦tool.exe' jetzt"),
                "Starte '[[PFAD_1]]' jetzt", "PFAD", "Program", "Files", "Mein", "tool.exe");
        roundtrip("ak16-laufwerk-backtick", bs("Starte `C:¦Program Files¦Mein Tool¦tool.exe` jetzt"),
                "Starte `[[PFAD_1]]` jetzt", "PFAD", "Program", "Files", "Mein", "tool.exe");
    }

    @Test
    void ak17Laufwerk_leerzeichenOhneAnfuehrung_bekannteGrenzeD5() {
        // D-5: Ende am ersten Leerzeichen; der Rest bleibt Klartext (AK-17 (a), AK-25 letzter Satz).
        roundtrip("ak17a-laufwerk-d5", bs("Starte C:¦Program Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log."),
                bs("Starte [[PFAD_1]] Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log."), "PFAD", "Program");
    }

    // ------------------------------------------------------------------ AK-11, AK-22: UNC

    @Test
    void ak11Unc_ohneAnfuehrung() {
        roundtrip("ak11-unc", bs("Ablage ¦¦srv-beispiel¦freigabe¦ordner¦datei.txt"),
                "Ablage [[PFAD_1]]", "PFAD", "srv-beispiel", "freigabe", "ordner", "datei.txt");
    }

    @Test
    void ak11Unc_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-unc", bs("Ablage \"¦¦srv-beispiel¦Mein Freigabe¦Ordner x¦datei.txt\" ok"),
                "Ablage \"[[PFAD_1]]\" ok", "PFAD", "srv-beispiel", "Mein", "Freigabe", "Ordner", "datei.txt");
    }

    @Test
    void ak22Unc_vierBackslashesUndDoppelteTrenner() {
        String text = bs("Ablage ¦¦¦¦srv-beispiel¦¦freigabe¦¦x");
        assertEquals(8, text.chars().filter(c -> c == '\\').count(), "Testdaten: Anzahl Backslashes");
        roundtrip("ak22-unc-vierfach", text, "Ablage [[PFAD_1]]", "PFAD", "srv-beispiel", "freigabe");
    }

    @Test
    void ak24Unc_ipImPfadBleibtEinFund() {
        roundtrip("ak24-unc-ip", bs("Ablage ¦¦192.0.2.10¦freigabe¦ordner ok"),
                "Ablage [[PFAD_1]] ok", "PFAD", "192.0.2.10", "freigabe", "ordner");
    }

    @Test
    void ak17Unc_leerzeichenOhneAnfuehrung_bekannteGrenzeD5() {
        roundtrip("ak17b-unc-d5", bs("Ablage ¦¦srv-beispiel¦Mein Ordner¦datei.txt"),
                bs("Ablage [[PFAD_1]] Ordner¦datei.txt"), "PFAD", "srv-beispiel", "Mein");
    }

    // ------------------------------------------------------------------ AK-12: Unix und Home

    @Test
    void ak12Unix_ohneAnfuehrung() {
        roundtrip("ak12-unix", "Datei /opt/beispiel-tool/bin/tool laden",
                "Datei [[PFAD_1]] laden", "PFAD", "opt", "beispiel-tool", "bin");
    }

    @Test
    void ak12Unix_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-unix", "Datei \"/opt/Mein Tool/bin/tool\" laden",
                "Datei \"[[PFAD_1]]\" laden", "PFAD", "opt", "Mein", "Tool", "bin");
    }

    @Test
    void ak12Unix_schraegstrichUnc() {
        roundtrip("ak12-unix-doppelschraegstrich", "Zugriff //srv-beispiel/freigabe/x ok",
                "Zugriff [[PFAD_1]] ok", "PFAD", "srv-beispiel", "freigabe");
    }

    @Test
    void ak12Home_ohneAnfuehrung() {
        roundtrip("ak12-home", "Lade ~/beispiel/konfig.yml jetzt",
                "Lade [[PFAD_1]] jetzt", "PFAD", "beispiel", "konfig.yml");
    }

    @Test
    void ak12Home_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-home", "Lade \"~/Mein Ordner/konfig.yml\" jetzt",
                "Lade \"[[PFAD_1]]\" jetzt", "PFAD", "Mein", "Ordner", "konfig.yml");
    }

    @Test
    void ak12HomeAndererBenutzer_ohneAnfuehrung() {
        roundtrip("ak12-home-benutzer", "Lade ~erika/akte.pdf jetzt",
                "Lade [[PFAD_1]] jetzt", "PFAD", "erika", "akte.pdf");
    }

    @Test
    void ak12Host_ohneAnfuehrung() {
        roundtrip("ak12-host", "Sichere host.example:/export/home/erika jetzt",
                "Sichere [[PFAD_1]] jetzt", "PFAD", "host.example", "export", "erika");
    }

    @Test
    void ak12Host_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-host", "Sichere \"host.example:/export/Mein Ordner/x\" jetzt",
                "Sichere \"[[PFAD_1]]\" jetzt", "PFAD", "host.example", "export", "Mein", "Ordner");
    }

    // ------------------------------------------------------------------ AK-14: relativ, einzelner Backslash

    @Test
    void ak14Relativ_ohneAnfuehrung() {
        roundtrip("ak14-relativ-backslash", bs("Starte .¦tools¦tool.exe jetzt"),
                "Starte [[PFAD_1]] jetzt", "PFAD", "tools", "tool.exe");
        roundtrip("ak14-relativ-schraegstrich", "Lade ../konf/app.yml jetzt",
                "Lade [[PFAD_1]] jetzt", "PFAD", "konf", "app.yml");
    }

    @Test
    void ak14Relativ_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-relativ", bs("Lade \"..¦Mein Ordner¦app.yml\" jetzt"),
                "Lade \"[[PFAD_1]]\" jetzt", "PFAD", "Mein", "Ordner", "app.yml");
    }

    @Test
    void ak14EinzelnerBackslash_ohneAnfuehrung() {
        roundtrip("ak14-einzel-backslash", bs("Zugriff auf ¦srv¦freigabe¦a.txt pr\u00FCfen"),
                "Zugriff auf [[PFAD_1]] pr\u00FCfen", "PFAD", "srv", "freigabe", "a.txt");
    }

    @Test
    void ak14EinzelnerBackslash_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-einzel-backslash", bs("Zugriff auf \"¦srv¦Mein Ordner¦a.txt\" pr\u00FCfen"),
                "Zugriff auf \"[[PFAD_1]]\" pr\u00FCfen", "PFAD", "srv", "Mein", "Ordner", "a.txt");
    }

    @Test
    void ak14BenutzerWurzel_ohneAnfuehrung() {
        roundtrip("ak14-benutzer-wurzel", bs("Datei Users¦erika.mustermann¦Desktop¦akte.pdf lesen"),
                "Datei [[PFAD_1]] lesen", "PFAD", "erika", "mustermann", "Desktop", "akte.pdf");
    }

    @Test
    void ak14BenutzerWurzel_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-benutzer-wurzel", bs("Datei \"Users¦erika mustermann¦Desktop¦akte.pdf\" lesen"),
                "Datei \"[[PFAD_1]]\" lesen", "PFAD", "erika", "mustermann", "Desktop", "akte.pdf");
    }

    @Test
    void ak14WurzelZweiSegmente_ohneAnfuehrung() {
        roundtrip("ak14-wurzel-home", "Datei home/erika/akte.pdf lesen",
                "Datei [[PFAD_1]] lesen", "PFAD", "erika", "akte.pdf");
        roundtrip("ak14-wurzel-benutzer", "Datei Benutzer/erika/Desktop lesen",
                "Datei [[PFAD_1]] lesen", "PFAD", "erika", "Desktop");
    }

    @Test
    void ak14WurzelZweiSegmente_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-wurzel-home", "Datei \"home/erika mustermann/akte.pdf\" lesen",
                "Datei \"[[PFAD_1]]\" lesen", "PFAD", "erika", "mustermann", "akte.pdf");
    }

    // ------------------------------------------------------------------ AK-15, AK-38: Umgebungsvariablen

    @Test
    void ak15Umgebung_ohneAnfuehrung() {
        roundtrip("ak15-appdata", bs("Lade %APPDATA%¦BeispielTool¦konfig.ini jetzt"),
                "Lade [[PFAD_1]] jetzt", "PFAD", "APPDATA", "BeispielTool", "konfig.ini");
        roundtrip("ak15-home", "Lade $HOME/beispiel/konfig.yml jetzt",
                "Lade [[PFAD_1]] jetzt", "PFAD", "HOME", "beispiel", "konfig.yml");
        roundtrip("ak15-home-geschweift", "Lade ${HOME}/beispiel/konfig.yml jetzt",
                "Lade [[PFAD_1]] jetzt", "PFAD", "HOME", "beispiel", "konfig.yml");
    }

    @Test
    void ak15Umgebung_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-appdata", bs("Lade \"%APPDATA%¦Mein Tool¦konfig.ini\" jetzt"),
                "Lade \"[[PFAD_1]]\" jetzt", "PFAD", "APPDATA", "Mein", "Tool", "konfig.ini");
        roundtrip("ak16-home", "Lade \"$HOME/Mein Ordner/konfig.yml\" jetzt",
                "Lade \"[[PFAD_1]]\" jetzt", "PFAD", "HOME", "Mein", "Ordner", "konfig.yml");
    }

    // ------------------------------------------------------------------ file:

    @Test
    void ak24FileUri_ohneAnfuehrung() {
        roundtrip("ak24-file-laufwerk", "Oeffne file:///C:/Tools/BeispielTool/tool.exe jetzt",
                "Oeffne [[PFAD_1]] jetzt", "PFAD", "file", "Tools", "BeispielTool", "tool.exe");
        roundtrip("ak24-file-server", "Oeffne file://srv-beispiel/freigabe/x jetzt",
                "Oeffne [[PFAD_1]] jetzt", "PFAD", "file", "srv-beispiel", "freigabe");
    }

    @Test
    void ak24FileUri_mitAnfuehrungUndLeerzeichen() {
        roundtrip("ak16-file", "Oeffne \"file:///C:/Mein Ordner/x.txt\" jetzt",
                "Oeffne \"[[PFAD_1]]\" jetzt", "PFAD", "file", "Mein", "Ordner", "x.txt");
    }

    // ------------------------------------------------------------------ AK-18, AK-25, AK-26, AK-28

    @Test
    void ak18MehrerePfade_einzelnErkannt() {
        roundtrip("ak18-zwei-laufwerke", bs("Kopiere C:¦Tools¦a.exe nach D:¦Ablage¦b.exe"),
                "Kopiere [[PFAD_1]] nach [[PFAD_2]]", "PFAD,PFAD", "Tools", "Ablage", "a.exe", "b.exe");
        roundtrip("ak18-nur-leerzeichen", bs("C:¦Tools¦a.exe C:¦Tools¦b.exe"),
                "[[PFAD_1]] [[PFAD_2]]", "PFAD,PFAD", "Tools", "a.exe", "b.exe");
        roundtrip("ak18-unix-komma", "/opt/beispiel/a, /opt/beispiel/b",
                "[[PFAD_1]], [[PFAD_2]]", "PFAD,PFAD", "opt", "beispiel");
    }

    @Test
    void ak25PfadAlsGenauEinPlatzhalter() {
        roundtrip("ak25-beispiel", bs("Start C:¦Tools¦BeispielTool¦tool.exe jetzt"),
                "Start [[PFAD_1]] jetzt", "PFAD", "Tools", "BeispielTool", "tool", "exe", "C:", "\\");
    }

    @Test
    void ak26GemeinsamerAnfangUndPraefix_verschiedenePlatzhalter() {
        roundtrip("ak26-gemeinsamer-anfang", bs("C:¦Tools¦a.exe und C:¦Tools¦b.exe"),
                "[[PFAD_1]] und [[PFAD_2]]", "PFAD,PFAD", "Tools");
        roundtrip("ak26-praefix", bs("C:¦Tools und C:¦Tools¦a.exe"),
                "[[PFAD_1]] und [[PFAD_2]]", "PFAD,PFAD", "Tools");
    }

    @Test
    void ak28GleicherWert_gleicherPlatzhalter() {
        roundtrip("ak28-gleich", bs("C:¦Tools¦a.exe ... C:¦Tools¦a.exe"),
                "[[PFAD_1]] ... [[PFAD_1]]", "PFAD,PFAD", "Tools");
    }

    @Test
    void ak28SchreibweiseUnterscheidet_verschiedenePlatzhalter() {
        roundtrip("ak28-schreibweise", bs("C:¦Tools¦a.exe, c:¦tools¦a.exe, C:/Tools/a.exe, C:¦Tools¦a.exe¦"),
                "[[PFAD_1]], [[PFAD_2]], [[PFAD_3]], [[PFAD_4]]", "PFAD,PFAD,PFAD,PFAD", "Tools", "tools");
    }

    @Test
    void ak28GleicherManuellerWert_gleicherPlatzhalter() {
        String text = "Anna Beispiel schrieb an " + MAIL + " \u00FCber " + bs("C:¦Tools¦a.exe")
                + ". Anna Beispiel antwortete.";
        List<Finding> manuell = List.of(manuell("PERSON", text, "Anna Beispiel", 0),
                manuell("PERSON", text, "Anna Beispiel", 14));
        roundtrip("ak28-manuell-person", text, manuell,
                "[[PERSON_1]] schrieb an [[EMAIL_2]] \u00FCber [[PFAD_3]]. [[PERSON_1]] antwortete.",
                "PERSON,EMAIL,PFAD,PERSON", "Anna", "Beispiel", MAIL, "Tools");
    }

    // ------------------------------------------------------------------ E-Mail, IP, URL, manuelle Funde, AK-37

    @Test
    void ak37EmailIpUrl_einzeln() {
        roundtrip("ak37-email", "Mail an " + MAIL + " bitte", "Mail an [[EMAIL_1]] bitte", "EMAIL", MAIL, "erika");
        roundtrip("ak37-ip", "Server 192.0.2.10 antwortet", "Server [[IP_1]] antwortet", "IP", "192.0.2.10");
        roundtrip("ak37-url", "Siehe https://example.invalid/a/b?x=1 jetzt", "Siehe [[URL_1]] jetzt", "URL",
                "example.invalid", "a/b");
    }

    @Test
    void ak27ak37EmailIpPfadImSelbenSatz() {
        roundtrip("ak27-beispiel", "Mail " + MAIL + bs(", Server 192.0.2.10, Tool C:¦Tools¦a.exe"),
                "Mail [[EMAIL_1]], Server [[IP_2]], Tool [[PFAD_3]]", "EMAIL,IP,PFAD", MAIL, "192.0.2.10", "Tools");
    }

    @Test
    void ak31ManuelleFunde_programmUndServer() {
        String text = "BeispielTool l\u00E4uft auf srv-beispiel";
        List<Finding> manuell = List.of(manuell("PROGRAMM", text, "BeispielTool", 0),
                manuell("SERVER", text, "srv-beispiel", 0));
        roundtrip("ak31-manuell-programm-server", text, manuell, "[[PROGRAMM_1]] l\u00E4uft auf [[SERVER_2]]",
                "PROGRAMM,SERVER", "BeispielTool", "srv-beispiel");
    }

    @Test
    void ak31ak17Laufwerk_abgeschnittenerRestAlsManuellerFund() {
        // D-5: Der Scan endet am ersten Leerzeichen (C:\Program); der Rest wird manuell uebergeben (AK-31, AK-17).
        String text = bs("Starte C:¦Program Files¦Mein Tool¦tool.exe jetzt");
        assertEquals("PFAD", typen(SCANNER.scan(text)), "Scan: nur der Fund bis zum ersten Leerzeichen");
        assertEquals(bs("C:¦Program"), SCANNER.scan(text).get(0).originalValue(), "Scan: Wert des Fundes");
        List<Finding> manuell = List.of(manuell("PFAD", text, bs("Files¦Mein Tool¦tool.exe"), 0));
        roundtrip("ak31ak17-laufwerk-rest-manuell", text, manuell, "Starte [[PFAD_1]] [[PFAD_2]] jetzt", "PFAD,PFAD",
                "Program", "Files", "Mein", "Tool", "tool.exe", "C:");
    }

    @Test
    void ak31ak17Unc_abgeschnittenerRestAlsManuellerFund() {
        String text = bs("Ablage ¦¦srv-beispiel¦Mein Ordner¦datei.txt hier");
        assertEquals("PFAD", typen(SCANNER.scan(text)), "Scan: nur der Fund bis zum ersten Leerzeichen");
        assertEquals(bs("¦¦srv-beispiel¦Mein"), SCANNER.scan(text).get(0).originalValue(), "Scan: Wert des Fundes");
        List<Finding> manuell = List.of(manuell("PFAD", text, bs("Ordner¦datei.txt"), 0));
        roundtrip("ak31ak17-unc-rest-manuell", text, manuell, "Ablage [[PFAD_1]] [[PFAD_2]] hier", "PFAD,PFAD",
                "srv-beispiel", "Mein", "Ordner", "datei");
    }

    @Test
    void ak37AlleFormenInEinemText() {
        String text = String.join("\n",
                bs("Laufwerk C:¦Tools¦BeispielTool¦tool.exe"),
                bs("UNC ¦¦srv-beispiel¦freigabe¦ordner¦datei.txt"),
                "Unix /opt/beispiel-tool/bin/tool",
                "Home ~/beispiel/konfig.yml",
                "Host host.example:/export/home/erika",
                bs("Relativ .¦tools¦tool.exe"),
                bs("Einzel ¦srv¦freigabe¦a.txt"),
                bs("Benutzer Users¦erika.mustermann¦Desktop¦akte.pdf"),
                "Wurzel home/erika/akte.pdf",
                bs("Umgebung %APPDATA%¦BeispielTool¦konfig.ini"),
                "Datei file:///C:/Tools/BeispielTool/tool.exe",
                "Mail " + MAIL + " IP 192.0.2.10 URL https://example.invalid/a/b");
        String erwartet = String.join("\n",
                "Laufwerk [[PFAD_1]]", "UNC [[PFAD_2]]", "Unix [[PFAD_3]]", "Home [[PFAD_4]]", "Host [[PFAD_5]]",
                "Relativ [[PFAD_6]]", "Einzel [[PFAD_7]]", "Benutzer [[PFAD_8]]", "Wurzel [[PFAD_9]]",
                "Umgebung [[PFAD_10]]", "Datei [[PFAD_11]]", "Mail [[EMAIL_12]] IP [[IP_13]] URL [[URL_14]]");
        roundtrip("ak37-alle-formen", text, erwartet,
                "PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,PFAD,EMAIL,IP,URL",
                "Tools", "BeispielTool", "tool.exe", "srv-beispiel", "freigabe", "datei.txt", "opt", "konfig",
                "host.example", "export", "erika", "mustermann", "Desktop", "akte.pdf", "APPDATA", "file:",
                "example.invalid", "192.0.2.10");
    }

    // ------------------------------------------------------------------ AK-7 (g)

    @Test
    void ak7g_zusammengelegterFund_schraegstrich() {
        roundtrip("ak7g-schraegstrich", AK7G, "Link [[PFAD_1]]' ende", "PFAD",
                "intranet.invalid", "Akte", "2025", MAIL, "x.pdf");
    }

    @Test
    void ak7g_variante_backslashTrenner() {
        String text =
                bs("Link https://intranet.invalid/?f='C:¦Akte 2025¦erika.beispiel@example.invalid¦x.pdf' ende");
        roundtrip("ak7g-backslash", text, "Link [[PFAD_1]]' ende", "PFAD",
                "intranet.invalid", "Akte", "2025", MAIL, "x.pdf");
    }

    @Test
    void ak7e_anfuehrungszeichenNachUrl_zweiFunde() {
        roundtrip("ak7e-url-und-pfad", "https://intranet.invalid/?f=\"C:/Akte 2025/x.pdf\"",
                "[[URL_1]]\"[[PFAD_2]]\"", "URL,PFAD", "intranet.invalid", "Akte", "2025", "x.pdf");
    }

    // ------------------------------------------------------------------ AK-38: Zeichenerhalt in Originalwerten

    @Test
    void ak38BackslashesDollarUndGruppenverweise() {
        String text = bs("Wert C:¦Tools¦$1¦¦1¦x.exe Ende");
        roundtrip("ak38-dollar-gruppe", text, "Wert [[PFAD_1]] Ende", "PFAD", "$1", "Tools", "x.exe");
        List<Finding> manuell = List.of(manuell("WERT", bs("Preis $1 und ¦1 sowie $0"), "$1", 0));
        roundtrip("ak38-manuell-dollar", bs("Preis $1 und ¦1 sowie $0"), manuell,
                bs("Preis [[WERT_1]] und ¦1 sowie $0"), "WERT", "$1");
    }

    @Test
    void ak38UmgebungsvariablenUnveraendert() {
        String text = bs("A ${HOME}/beispiel/x B %APPDATA%¦BeispielTool¦y C $HOME/z/w D");
        roundtrip("ak38-umgebung", text, "A [[PFAD_1]] B [[PFAD_2]] C [[PFAD_3]] D", "PFAD,PFAD,PFAD",
                "HOME", "APPDATA", "beispiel", "BeispielTool");
    }

    @Test
    void ak38ManuellerWertMitBackslashUndPlatzhalterAehnlichkeit() {
        String text = bs("Datei ¦¦srv¦x und [[IP_1]] sowie 192.0.2.10");
        roundtrip("ak38-platzhalterartiger-text", text, "Datei [[PFAD_1]] und [[TEXT_2]] sowie [[IP_3]]",
                "PFAD,IP", "srv", "192.0.2.10");
    }

    // ------------------------------------------------------------------ AK-39: Zeilenenden, Leerraum, Unicode

    private static String dreiZeilen(String eol) {
        return bs("Zeile C:¦Tools¦a.exe") + eol + "Mail " + MAIL + eol + "IP 192.0.2.10" + eol;
    }

    private static String dreiZeilenPseudonymisiert(String eol) {
        return "Zeile [[PFAD_1]]" + eol + "Mail [[EMAIL_2]]" + eol + "IP [[IP_3]]" + eol;
    }

    @Test
    void ak39Zeilenenden_lf() {
        roundtrip("ak39-lf", dreiZeilen("\n"), dreiZeilenPseudonymisiert("\n"), "PFAD,EMAIL,IP", "Tools", MAIL);
    }

    @Test
    void ak39Zeilenenden_crlf() {
        roundtrip("ak39-crlf", dreiZeilen("\r\n"), dreiZeilenPseudonymisiert("\r\n"), "PFAD,EMAIL,IP", "Tools", MAIL);
    }

    @Test
    void ak39Zeilenenden_cr() {
        roundtrip("ak39-cr", dreiZeilen("\r"), dreiZeilenPseudonymisiert("\r"), "PFAD,EMAIL,IP", "Tools", MAIL);
    }

    @Test
    void ak39Zeilenenden_gemischt() {
        String text = bs("Zeile C:¦Tools¦a.exe") + "\n" + "Mail " + MAIL + "\r\n" + "IP 192.0.2.10" + "\r" + "Ende\n";
        roundtrip("ak39-gemischt", text, "Zeile [[PFAD_1]]\nMail [[EMAIL_2]]\r\nIP [[IP_3]]\rEnde\n",
                "PFAD,EMAIL,IP", "Tools", MAIL);
    }

    @Test
    void ak39FuehrenderUndAbschliessenderLeerraum() {
        roundtrip("ak39-leerraum", bs("  \t\n C:¦Tools¦a.exe  \n\n"), "  \t\n [[PFAD_1]]  \n\n", "PFAD", "Tools");
        roundtrip("ak39-leerraum-crlf", bs("\r\n\r\n  C:¦Tools¦a.exe \r\n "), "\r\n\r\n  [[PFAD_1]] \r\n ",
                "PFAD", "Tools");
        roundtrip("ak39-nur-leerraum", " \t\r\n  ", " \t\r\n  ", "");
    }

    @Test
    void ak39Tabulatoren() {
        roundtrip("ak39-tabs", bs("a\tC:¦Tools¦a.exe\tb\t192.0.2.10\t"), "a\t[[PFAD_1]]\tb\t[[IP_2]]\t",
                "PFAD,IP", "Tools", "192.0.2.10");
    }

    @Test
    void ak39UmlauteUndEszett() {
        String text = "\u00DCbersicht gr\u00F6\u00DFer: "
                + bs("C:¦Users¦mustermann¦\u00DCbersicht¦Gr\u00F6\u00DFe.txt")
                + " Ende";
        roundtrip("ak39-umlaute", text, "\u00DCbersicht gr\u00F6\u00DFer: [[PFAD_1]] Ende", "PFAD",
                "mustermann", "Gr\u00F6\u00DFe", "Users");
        String zitat = bs("Datei \"C:¦Users¦mustermann¦\u00DCbersicht¦Gr\u00F6\u00DFe \u2713.txt\" ok");
        roundtrip("ak39-umlaute-anfuehrung", zitat, "Datei \"[[PFAD_1]]\" ok", "PFAD", "mustermann",
                "Gr\u00F6\u00DFe", "\u2713");
    }

    @Test
    void ak39KombinierendesZeichenZerlegt() {
        String text = "Cafe\u0301 " + bs("C:¦Tools¦cafe\u0301¦a.exe") + " und Cafe\u0301";
        PseudonymizationResult p = roundtrip("ak39-kombinierend", text, "Cafe\u0301 [[PFAD_1]] und Cafe\u0301",
                "PFAD", "Tools", "a.exe");
        assertTrue(p.text().startsWith("Cafe\u0301 "), "zerlegte Form bleibt unnormalisiert");
    }

    @Test
    void ak39EmojiVorHinterUndZwischenFunden() {
        roundtrip("ak39-emoji-mit-leerzeichen", EMOJI + " " + bs("C:¦Tools¦a.exe")
                + " " + EMOJI + " 192.0.2.10 " + EMOJI,
                EMOJI + " [[PFAD_1]] " + EMOJI + " [[IP_2]] " + EMOJI, "PFAD,IP", "Tools", "192.0.2.10");
        roundtrip("ak39-emoji-direkt-vor-pfad", EMOJI + bs("C:¦Tools¦a.exe jetzt"), EMOJI + "[[PFAD_1]] jetzt",
                "PFAD", "Tools");
        roundtrip("ak39-emoji-zwischen-funden", MAIL + " " + EMOJI + " 192.0.2.10 " + EMOJI + EMOJI + " "
                + bs("C:¦Tools¦a.exe"), "[[EMAIL_1]] " + EMOJI + " [[IP_2]] " + EMOJI + EMOJI + " [[PFAD_3]]",
                "EMAIL,IP,PFAD", MAIL, "192.0.2.10", "Tools");
    }

    @Test
    void ak39EmojiDirektHinterPfad_gehoertZumPfad() {
        // Ein Emoji ist kein Endzeichen; es gehoert (wie jedes Nicht-Endzeichen) zum Pfadlauf (Plan 2.4.1, AK-23).
        roundtrip("ak39-emoji-direkt-hinter-pfad", bs("Start C:¦Tools¦a.exe") + EMOJI + " ende",
                "Start [[PFAD_1]] ende", "PFAD", "Tools", EMOJI);
    }

    @Test
    void ak39FuehrendesBom() {
        roundtrip("ak39-bom", "\uFEFF" + bs("Start C:¦Tools¦a.exe jetzt"), "\uFEFFStart [[PFAD_1]] jetzt", "PFAD",
                "Tools");
        roundtrip("ak39-bom-direkt-vor-pfad", "\uFEFF" + bs("C:¦Tools¦a.exe"), "\uFEFF[[PFAD_1]]", "PFAD", "Tools");
    }

    @Test
    void ak39NullbreitesLeerzeichen() {
        // Das Zeichen direkt hinter dem Pfad geht im Fund auf (Cf beendet den Pfad nicht, Plan T-10 Nachtrag 3).
        roundtrip("ak39-zwsp-im-text", bs("Start\u200B C:¦Tools¦a.exe\u200B jetzt"),
                "Start\u200B [[PFAD_1]] jetzt", "PFAD", "Tools");
        // Cf beendet den Pfad nicht (Plan T-10 Nachtrag 3): Das Zeichen im Namen bleibt im Fund.
        roundtrip("ak39-zwsp-im-pfad", bs("Start C:¦Tools¦a\u200Bb.exe jetzt"), "Start [[PFAD_1]] jetzt", "PFAD",
                "Tools", "b.exe");
    }

    @Test
    void ak39NullbreitesLeerzeichenImKopf_bekannteGrenze() {
        // Plan T-10 Nachtrag 3a (a): unsichtbares Formatzeichen im Kopf -> kein Fund; Text bleibt unveraendert.
        String text = bs("Start C\u200B:¦Tools¦a.exe jetzt");
        roundtrip("ak39-zwsp-im-kopf-grenze", text, text, "");
    }

    @Test
    void ak39RechtsNachLinksText() {
        String hebraeisch = "\u05E9\u05DC\u05D5\u05DD";
        String arabisch = "\u0645\u0631\u062D\u0628\u0627";
        String text = hebraeisch + " " + bs("C:¦Tools¦a.exe") + " " + arabisch + " 192.0.2.10 \u200F" + hebraeisch;
        roundtrip("ak39-rtl", text, hebraeisch + " [[PFAD_1]] " + arabisch + " [[IP_2]] \u200F" + hebraeisch,
                "PFAD,IP", "Tools", "192.0.2.10");
        String zitat = "\u202B" + arabisch + bs(" \"C:¦Mein Ordner¦a.exe\" ") + hebraeisch + "\u202C";
        roundtrip("ak39-rtl-anfuehrung", zitat, "\u202B" + arabisch + " \"[[PFAD_1]]\" " + hebraeisch + "\u202C",
                "PFAD", "Mein", "Ordner");
    }

    // ------------------------------------------------------------------ Literalschutz (AK-36, AK-44)

    @Test
    void ak36PlatzhalterartigerOriginaltextBleibtErhalten() {
        String text = bs("Original [[PFAD_1]] und C:¦Tools¦a.exe");
        PseudonymizationResult p = roundtrip("ak36-literal", text, "Original [[TEXT_1]] und [[PFAD_2]]", "PFAD",
                "Tools");
        assertEquals(2, p.mapping().size(), "nur tatsaechlich ersetzte Werte");
    }

    // ------------------------------------------------------------------ AK-54: KI-Simulation durch feste Umformung

    private static final String KI_ORIGINAL =
            "Mail " + MAIL + "\nServer 192.0.2.10\n" + "Tool " + "C:\\Tools\\a.exe";
    private static final String KI_PSEUDONYMISIERT = "Mail [[EMAIL_1]]\nServer [[IP_2]]\nTool [[PFAD_3]]";

    @Test
    void ak54KiSimulation_zeilenVertauschtMitPraefixUndSuffix() {
        PseudonymizationResult p = roundtrip("ak54-ausgangstext", KI_ORIGINAL, KI_PSEUDONYMISIERT, "EMAIL,IP,PFAD",
                MAIL, "192.0.2.10", "Tools");
        String ki = "Zusammenfassung:\nTool [[PFAD_3]]\nServer [[IP_2]]\nMail [[EMAIL_1]]\nEnde.";
        ReidentificationResult r = umformen(p, ki);
        assertEquals("Zusammenfassung:\nTool C:\\Tools\\a.exe\nServer 192.0.2.10\nMail " + MAIL + "\nEnde.",
                r.text(), "ak54 Zeilen vertauscht");
        assertTrue(r.unresolved().isEmpty(), "ak54 keine Meldung");
    }

    @Test
    void ak54KiSimulation_mehrfachEingebettetUndWeggelassen() {
        PseudonymizationResult p = roundtrip("ak54-ausgangstext-2", KI_ORIGINAL, KI_PSEUDONYMISIERT,
                "EMAIL,IP,PFAD", MAIL, "192.0.2.10", "Tools");
        // [[IP_2]] fehlt (kein Fehler), [[PFAD_3]] kommt doppelt vor, [[EMAIL_1]] ist in Text und Klammern eingebettet.
        String ki = "Der Pfad ([[PFAD_3]]) ist wichtig, [[PFAD_3]] auch; Absender:[[EMAIL_1]]:Ende.";
        ReidentificationResult r = umformen(p, ki);
        assertEquals("Der Pfad (C:\\Tools\\a.exe) ist wichtig, C:\\Tools\\a.exe auch; Absender:" + MAIL + ":Ende.",
                r.text(), "ak54 eingebettet");
        assertTrue(r.unresolved().isEmpty(), "ak54 keine Meldung");
    }

    @Test
    void ak54KiSimulation_zwoelfFundeEinsUndElfNichtVerwechselt() {
        StringBuilder text = new StringBuilder("Hosts");
        StringBuilder erwartet = new StringBuilder("Hosts");
        for (int i = 1; i <= 12; i++) {
            text.append(" 192.0.2.").append(i);
            erwartet.append(" [[IP_").append(i).append("]]");
        }
        PseudonymizationResult p = roundtrip("ak54-zwoelf-funde", text.toString(), erwartet.toString(),
                "IP,IP,IP,IP,IP,IP,IP,IP,IP,IP,IP,IP");
        String ki = "[[IP_11]] [[IP_1]] [[IP_12]] [[IP_2]]";
        ReidentificationResult r = umformen(p, ki);
        assertEquals("192.0.2.11 192.0.2.1 192.0.2.12 192.0.2.2", r.text(), "ak54 _1/_11 und _2/_12");
        assertTrue(r.unresolved().isEmpty(), "ak54 keine Meldung");
    }

    @Test
    void ak54KiSimulation_pfadformenUndAk7g() {
        PseudonymizationResult p = roundtrip("ak54-ak7g", AK7G, "Link [[PFAD_1]]' ende", "PFAD",
                "intranet.invalid", "Akte", MAIL);
        ReidentificationResult r = umformen(p, ">> Ergebnis: [[PFAD_1]]' ende <<\n[[PFAD_1]]");
        String pfad = AK7G.substring(5, 83);
        assertEquals(">> Ergebnis: " + pfad + "' ende <<\n" + pfad, r.text(), "ak54 AK-7 (g)");
        assertTrue(r.unresolved().isEmpty(), "ak54 keine Meldung");
    }

    // ------------------------------------------------------------------ KV-Nummern (AK-20, AK-1 bis AK-16)

    /** Ein KV-Korpusfall: Name, Text, erwarteter pseudonymisierter Text, erwartete Typen in Textreihenfolge. */
    record KvFall(String name, String text, String erwartet, String typen) {
    }

    private static KvFall kvFall(String name, String text, String erwartet, String typen) {
        return new KvFall(name, text, erwartet, typen);
    }

    /** Genau ein Fund: {@code vor + nummer + nach}, die Nummer wird zu {@code [[TYP_1]]}. */
    private static KvFall ein(String name, String vor, String nummer, String typ, String nach) {
        return kvFall(name, vor + nummer + nach, vor + "[[" + typ + "_1]]" + nach, typ);
    }

    /** Kein Fund: der Text bleibt unveraendert. */
    private static KvFall kein(String name, String text) {
        return kvFall(name, text, text, "");
    }

    private static final String[] LANR_KONTEXTE = {"lanr ", "LANR: ", "LANR=", "LANR;", "LANR\t", "LANR-Nr. ",
        "Arztnummer ", "Lebenslange Arztnummer: ", "Arzt-Nr. ", "Praxis-LANR "};
    private static final String[] IK_KONTEXTE = {"ik: ", "IK=", "IK-Nr. ", "IK-Nummer: ", "IKNR ",
        "Institutionskennzeichen ", "Praxis-IK "};
    private static final String[] LANR_TRENNERFORMEN = {LANR_TRENNER_LEERZEICHEN, LANR_TRENNER_UNGLEICHE_GRUPPEN,
        LANR_TRENNER_NBSP, LANR_TRENNER_SCHRAEGSTRICH};
    private static final String[] IK_TRENNERFORMEN = {IK_TRENNER_BINDESTRICH, IK_TRENNER_PUNKT,
        IK_TRENNER_GRUPPE_6_3, IK_TRENNER_NBSP};

    /** Alle KV-Korpusfaelle (AK-1 bis AK-16 und AK-20). Texte aus Konstanten von {@link SynthetischeKvNummern}. */
    static List<KvFall> kvFaelle() {
        List<KvFall> f = new ArrayList<>();
        // AK-1, AK-2, AK-3, AK-4: Kontextwoerter
        f.add(ein("ak1", "Arzt: LANR ", LANR_GUELTIG, "LANR", ", Praxis"));
        for (int i = 0; i < LANR_KONTEXTE.length; i++) {
            f.add(ein("ak2-" + i, LANR_KONTEXTE[i], LANR_GUELTIG, "LANR", ""));
        }
        f.add(ein("ak3", "Rechnung an IK ", IK_GUELTIG, "IK", " gesendet"));
        for (int i = 0; i < IK_KONTEXTE.length; i++) {
            f.add(ein("ak4-" + i, IK_KONTEXTE[i], IK_GUELTIG, "IK", ""));
        }
        // AK-5
        f.add(kvFall("ak5", "IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG, "IK [[IK_1]], LANR [[LANR_2]]", "IK,LANR"));
        // AK-6: Trenner (Fund umfasst die ganze Folge), Trenner am Ende gehoert nicht dazu
        for (int i = 0; i < LANR_TRENNERFORMEN.length; i++) {
            f.add(ein("ak6-lanr-" + i, "LANR ", LANR_TRENNERFORMEN[i], "LANR", ""));
        }
        for (int i = 0; i < IK_TRENNERFORMEN.length; i++) {
            f.add(ein("ak6-ik-" + i, "IK ", IK_TRENNERFORMEN[i], "IK", ""));
        }
        f.add(ein("ak6-lanr-trenner-am-ende", "LANR ", LANR_TRENNER_LEERZEICHEN, "LANR", " Ende"));
        f.add(ein("ak6-ik-trenner-am-ende", "IK ", IK_TRENNER_BINDESTRICH, "IK", "- Ende"));
        // AK-7, AK-8, AK-9: gueltige und ungueltige Pruefziffern, alle sind Funde
        for (String n : List.of(LANR_GUELTIG_FACHGRUPPE, LANR_GUELTIG_PRUEFZIFFER_NULL, LANR_GUELTIG_ZWEI,
                LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LANR_UNGUELTIG_VERTAUSCHT, LANR_UNGUELTIG_ZIFFER_GEAENDERT)) {
            f.add(ein("ak7-" + f.size(), "LANR ", n, "LANR", ""));
        }
        for (String n : List.of(IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, IK_UNGUELTIG_KOMPLEMENT, IK_UNGUELTIG_NULL,
                IK_UNGUELTIG_VERTAUSCHT)) {
            f.add(ein("ak8-" + f.size(), "IK ", n, "IK", ""));
        }
        // AK-10: ohne Kontextwort kein Fund
        f.add(kein("ak10-rueckruf", "R\u00FCckruf unter " + LANR_GUELTIG));
        f.add(kein("ak10-vorgang", "Vorgang " + IK_GUELTIG + " liegt vor"));
        f.add(kein("ak10-telefon", "Tel. " + TELEFON_PROBE));
        f.add(kein("ak10-allein", LANR_GUELTIG));
        // AK-11: Kontextwort nur als ganzes Wort, abweichende Laengen
        f.add(kein("ak11-technik", "Technik: " + IK_GUELTIG));
        f.add(kein("ak11-fabrik", "Fabrik " + IK_GUELTIG));
        f.add(kein("ak11-ikea", "IKEA " + IK_GUELTIG));
        f.add(kein("ak11-lanrx", "LANRX " + LANR_GUELTIG));
        f.add(ein("ak11-laenge-7", "LANR ", LAENGE_7, "LANR", ""));
        f.add(ein("ak11-laenge-8", "IK ", LAENGE_8, "IK", ""));
        f.add(ein("ak11-laenge-10", "LANR ", LAENGE_10, "LANR", ""));
        f.add(ein("ak11-laenge-6", "IK ", LAENGE_6, "IK", ""));
        f.add(ein("ak11-laenge-12", "LANR ", LAENGE_12, "LANR", ""));
        f.add(kein("ak11-laenge-4", "IK " + LAENGE_4));
        f.add(kein("ak11-laenge-5", "LANR " + LAENGE_5));
        f.add(kein("ak11-laenge-13", "IK " + LAENGE_13));
        f.add(ein("ak11-jahr-hinten", "LANR ", LANR_GUELTIG, "LANR", " 2025"));
        f.add(ein("ak11-bindestrich-jahr", "IK ", IK_GUELTIG, "IK", "-2025"));
        // AK-12: Fuellwoerter
        f.add(ein("ak12-lanr", "LANR des Behandlers ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("ak12-ik", "Institutionskennzeichen der Praxis: ", IK_GUELTIG, "IK", ""));
        f.add(kein("ak12-vier-woerter", "LANR der Praxis von Frau Beispiel " + LANR_GUELTIG));
        f.add(kein("ak12-satzende", "Die LANR fehlt. Vorgang " + LANR_GUELTIG));
        // AK-13: nachgestelltes Kontextwort (Kontextwort in Klammern bleibt)
        f.add(ein("ak13-lanr", "Nummer ", LANR_GUELTIG, "LANR", " (LANR)"));
        f.add(ein("ak13-ik", "Kennung ", IK_GUELTIG, "IK", " (IK)"));
        // AK-14: Zeilenumbruch
        f.add(ein("ak14-lanr-lf", "LANR:\n", LANR_GUELTIG, "LANR", ""));
        f.add(ein("ak14-lanr-crlf", "LANR:\r\n", LANR_GUELTIG, "LANR", ""));
        f.add(ein("ak14-ik-lf", "IK:\n", IK_GUELTIG, "IK", ""));
        f.add(ein("ak14-ik-crlf", "IK:\r\n", IK_GUELTIG, "IK", ""));
        f.add(kein("ak14-leerzeile-lf", "LANR:\n\n" + LANR_GUELTIG));
        f.add(kein("ak14-leerzeile-crlf", "IK:\r\n\r\n" + IK_GUELTIG));
        // AK-15: Aufzaehlung (Trenner , ; / und), je Zeile, nummerierte Liste
        f.add(kvFall("ak15-lanr-drei", "LANR " + LANR_GUELTIG + ", " + LANR_GUELTIG_ZWEI + " und "
                + LANR_GUELTIG_FACHGRUPPE, "LANR [[LANR_1]], [[LANR_2]] und [[LANR_3]]", "LANR,LANR,LANR"));
        f.add(kvFall("ak15-ik-zwei", "IK " + IK_GUELTIG + " / " + IK_UNGUELTIG_KOMPLEMENT, "IK [[IK_1]] / [[IK_2]]",
                "IK,IK"));
        f.add(kvFall("ak15-semikolon", "LANR " + LANR_GUELTIG + "; " + LANR_GUELTIG_ZWEI,
                "LANR [[LANR_1]]; [[LANR_2]]", "LANR,LANR"));
        f.add(kvFall("ak15-je-zeile-lf", "LANR:\n" + LANR_GUELTIG + "\n" + LANR_GUELTIG_ZWEI,
                "LANR:\n[[LANR_1]]\n[[LANR_2]]", "LANR,LANR"));
        f.add(kvFall("ak15-je-zeile-crlf", "IK:\r\n" + IK_GUELTIG + "\r\n" + IK_GUELTIG_ZWEI,
                "IK:\r\n[[IK_1]]\r\n[[IK_2]]", "IK,IK"));
        f.add(kvFall("ak15-nummerierte-liste", "LANR:\n1. " + LANR_GUELTIG + "\n2. " + LANR_GUELTIG_ZWEI,
                "LANR:\n1. [[LANR_1]]\n2. [[LANR_2]]", "LANR,LANR"));
        f.add(kvFall("ak15-nachgestellt-aufzaehlung", LANR_GUELTIG + ", " + LANR_GUELTIG_ZWEI + " (LANR)",
                "[[LANR_1]], [[LANR_2]] (LANR)", "LANR,LANR"));
        // AK-16: Rand- und Fehlerfaelle
        f.add(ein("ak16a-punkt", "LANR ", LANR_GUELTIG, "LANR", "."));
        f.add(ein("ak16b-textende", "LANR ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("ak16b-textanfang-nachgestellt", "", LANR_GUELTIG, "LANR", " (LANR)"));
        f.add(kein("ak16c-lanr", "LANR"));
        f.add(kein("ak16c-ik", "IK:"));
        f.add(kein("ak16c-arztnummer-fehlt", "Arztnummer fehlt"));
        f.add(kein("ak16c-kontextwort-am-ende", "Bitte die Nummer eintragen: IK"));
        f.add(kein("ak16d-leer", ""));
        f.add(kein("ak16d-leerraum", " \t\r\n  "));
        // AK-17, AK-18, AK-19: Enthaltensein, gleicher Bereich, Einbindung
        f.add(kvFall("ak17-url", "https://example.invalid/praxis?ik=" + IK_GUELTIG, "[[URL_1]]", "URL"));
        f.add(kvFall("ak17-pfad", bs("C:\u00A6Praxis\u00A6IK-") + IK_GUELTIG + bs("\u00A6x.txt"), "[[PFAD_1]]",
                "PFAD"));
        f.add(kvFall("ak17-email", "LANR " + LANR_GUELTIG + "@example.invalid", "LANR [[EMAIL_1]]", "EMAIL"));
        f.add(kvFall("ak18-gleicher-bereich-ip", "IK " + IK_FORM_DOKU_IP, "IK [[IP_1]]", "IP"));
        f.add(kvFall("ak19-sechs-funde", "Mail " + MAIL + ", Server 192.0.2.10, Link https://example.invalid/a, Pfad "
                + bs("C:\u00A6Tools\u00A6a.exe") + ", IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG,
                "Mail [[EMAIL_1]], Server [[IP_2]], Link [[URL_3]], Pfad [[PFAD_4]], IK [[IK_5]], LANR [[LANR_6]]",
                "EMAIL,IP,URL,PFAD,IK,LANR"));
        // AK-20: Konsistenz (gleiche Zahl, keine Normalisierung, gleiche Zahl unter LANR und IK)
        f.add(kvFall("ak20-gleiche-zahl-zweimal", "LANR " + LANR_GUELTIG + " ... LANR " + LANR_GUELTIG,
                "LANR [[LANR_1]] ... LANR [[LANR_1]]", "LANR,LANR"));
        f.add(kvFall("ak20-mit-und-ohne-trenner", "LANR " + LANR_GUELTIG + " / LANR " + LANR_TRENNER_LEERZEICHEN,
                "LANR [[LANR_1]] / LANR [[LANR_2]]", "LANR,LANR"));
        f.add(kvFall("ak20-lanr-dann-ik", "LANR " + IK_GUELTIG + "\n\nIK " + IK_GUELTIG,
                "LANR [[LANR_1]]\n\nIK [[IK_2]]", "LANR,IK"));
        f.add(kvFall("ak20-ik-dann-lanr", "IK " + IK_GUELTIG + "\n\nLANR " + IK_GUELTIG,
                "IK [[IK_1]]\n\nLANR [[LANR_2]]", "IK,LANR"));
        // Zeilenenden, Leerraum, Unicode (AK-39-Form fuer KV-Texte)
        String[] eolNamen = {"lf", "crlf", "cr"};
        String[] eols = {"\n", "\r\n", "\r"};
        for (int i = 0; i < eols.length; i++) {
            String eol = eols[i];
            f.add(kvFall("zeilenende-" + eolNamen[i], "LANR " + LANR_GUELTIG + eol + "IK "
                    + IK_GUELTIG + eol, "LANR [[LANR_1]]" + eol + "IK [[IK_2]]" + eol, "LANR,IK"));
        }
        f.add(kvFall("zeilenende-gemischt", "LANR " + LANR_GUELTIG + "\nIK " + IK_GUELTIG + "\r\nLANR "
                + LANR_GUELTIG_ZWEI + "\rEnde\n", "LANR [[LANR_1]]\nIK [[IK_2]]\r\nLANR [[LANR_3]]\rEnde\n",
                "LANR,IK,LANR"));
        f.add(kvFall("emoji-und-bom", "\uFEFF" + EMOJI + " LANR " + LANR_GUELTIG + " " + EMOJI + " IK " + IK_GUELTIG
                + " " + EMOJI, "\uFEFF" + EMOJI + " LANR [[LANR_1]] " + EMOJI + " IK [[IK_2]] " + EMOJI, "LANR,IK"));
        f.add(kvFall("rtl-text", "\u05E9\u05DC\u05D5\u05DD LANR " + LANR_GUELTIG + " \u0645\u0631\u062D\u0628\u0627 IK "
                + IK_GUELTIG, "\u05E9\u05DC\u05D5\u05DD LANR [[LANR_1]] \u0645\u0631\u062D\u0628\u0627 IK [[IK_2]]",
                "LANR,IK"));
        f.add(kvFall("unsichtbares-zeichen-im-kontext", "Praxis\u200BLANR " + LANR_GUELTIG,
                "Praxis\u200BLANR [[LANR_1]]", "LANR"));
        f.add(kvFall("tabelle-mit-tabulatoren", "Praxis\tLANR\t" + LANR_GUELTIG + "\tIK\t" + IK_GUELTIG + "\t",
                "Praxis\tLANR\t[[LANR_1]]\tIK\t[[IK_2]]\t", "LANR,IK"));
        kvLueckenFaelle(f);
        kvUmbruchFaelle(f);
        return f;
    }

    /**
     * Korpusfaelle zu kv-trenner-und-umbruch Teil 1 (T-4, AK-1 bis AK-5, AK-25): {@code CR UZ+ LF} gilt wie
     * {@code CR LF} als ein Zeilenumbruch. Je Fall mit Fund steht der erwartete pseudonymisierte Text da (Kontextwort,
     * {@code CR}, unsichtbare Zeichen, {@code LF} bleiben zeichengleich, nur die Nummer wird ersetzt); die
     * Gegenproben mit zwei Umbruechen sind Faelle ohne Fund ({@code kein}), darunter die Restgrenze 15R. Die
     * unsichtbaren Zeichen kommen als {@code (char)}-Konstanten, nie als Escape.
     */
    private static void kvUmbruchFaelle(List<KvFall> f) {
        String[] uzNamen = {"zwsp", "shy", "bom", "wj", "lrm", "vs16", "vs1"};
        String[] uz = uzAk1Texte();
        String zw = uz[0];
        String bom = uz[2];
        String shy = uz[1];
        String nel = String.valueOf((char) 0x85);
        String nb = String.valueOf((char) 0xA0);
        String ausserhalbBmp = new String(Character.toChars(0xE0020));
        String lanr = LANR_GUELTIG;
        String ik = IK_GUELTIG;
        // AK-1: jedes der sieben unsichtbaren Zeichen zwischen CR und LF, beide Arten
        for (int i = 0; i < uz.length; i++) {
            f.add(ein("ku-ak1-lanr-" + uzNamen[i], "LANR:\r" + uz[i] + "\n", lanr, "LANR", ""));
            f.add(ein("ku-ak1-ik-" + uzNamen[i], "IK:\r" + uz[i] + "\n", ik, "IK", ""));
        }
        // AK-2: mehrere unsichtbare Zeichen, auch ausserhalb der BMP
        f.add(ein("ku-ak2-drei-zeichen", "Arztnummer\r" + zw + bom + shy + "\n", lanr, "LANR", ""));
        f.add(ein("ku-ak2-vier-gleiche-zeichen", "IK-Nr.:\r" + zw + zw + zw + zw + "\n", ik, "IK", ""));
        f.add(ein("ku-ak2-ausserhalb-bmp", "LANR:\r" + ausserhalbBmp + "\n", lanr, "LANR", ""));
        f.add(ein("ku-ak2-ohne-doppelpunkt", "LANR\r" + zw + "\n", lanr, "LANR", ""));
        // AK-3: gesperrte Kontextwoerter ab drei Buchstaben, I K (zwei Buchstaben) bleibt ohne Fund
        f.add(ein("ku-ak3-sperr-lanr", "L A N R:\r" + zw + "\n", lanr, "LANR", ""));
        f.add(ein("ku-ak3-sperr-iknr", "I K N R:\r" + zw + "\n", ik, "IK", ""));
        f.add(kein("ku-ak3-ik-zwei-buchstaben", "I K:\r" + zw + "\n" + ik));
        f.add(kein("ku-ak3-ik-zwei-buchstaben-ohne-doppelpunkt", "I K\r" + zw + "\n" + ik));
        // AK-4: zwei Umbrueche bleiben zwei (kein Fund), genau ein Umbruch bleibt ein Fund
        f.add(kein("ku-ak4-cr-uz-cr", "LANR:\r" + zw + "\r" + lanr));
        f.add(kein("ku-ak4-lf-uz-lf", "LANR:\n" + zw + "\n" + lanr));
        f.add(kein("ku-ak4-crlf-uz-lf", "LANR:\r\n" + zw + "\n" + lanr));
        f.add(kein("ku-ak4-cr-uz-lf-uz-lf", "LANR:\r" + zw + "\n" + zw + "\n" + lanr));
        f.add(kein("ku-ak4-zweimal-cr-uz-lf", "LANR:\r" + zw + "\n\r" + zw + "\n" + lanr));
        f.add(kein("ku-ak4-cr-uz-nel", "IK:\r" + zw + nel + ik));
        f.add(kein("ku-ak4-sperr-zwei-umbrueche", "L A N R:\r" + zw + "\n\r" + zw + "\n" + lanr));
        f.add(ein("ku-ak4-cr-uz-ohne-lf-ist-ein-umbruch", "LANR:\r" + zw + zw, lanr, "LANR", ""));
        f.add(ein("ku-ak4-ein-umbruch-nel", "LANR:" + nel, lanr, "LANR", ""));
        // Restgrenze 15R: Leerraum, sichtbares Zeichen oder Kombinationszeichen zwischen CR und LF, CR NEL
        f.add(kein("bekannteGrenze15Rest-cr-leerzeichen-lf", "LANR:\r \n" + lanr));
        f.add(kein("bekannteGrenze15Rest-cr-nbsp-lf", "LANR:\r" + nb + "\n" + lanr));
        f.add(kein("bekannteGrenze15Rest-cr-uz-leerzeichen-lf", "LANR:\r" + zw + " \n" + lanr));
        f.add(kein("bekannteGrenze15Rest-cr-nel", "LANR:\r" + nel + lanr));
        f.add(kein("bekannteGrenze15Rest-cr-uz-buchstabe-lf", "LANR:\r" + zw + "A\n" + lanr));
        String akut = String.valueOf((char) 0x301);
        f.add(kein("bekannteGrenze15Rest-cr-uz-akut-lf", "LANR:\r" + zw + akut + "\n" + lanr));
        // AK-5: die Folgewirkungen eines Umbruchs gelten auch fuer CR UZ+ LF
        f.add(kein("ku-ak5a-leerzeile", "LANR:\r" + zw + "\n\r" + zw + "\n" + lanr));
        f.add(kein("ku-ak5b-nachgestellter-kontext", lanr + "\r" + zw + "\nLANR"));
        f.add(kein("ku-ak5b-nachgestellter-kontext-sperr", ik + "\r" + zw + "\nI K N R"));
        f.add(kein("ku-ak5c-komma-regel", lanr + ",\r" + zw + "\nLANR"));
        f.add(kvFall("ku-ak5d-aufzaehlung-zeilenweise", "LANR " + lanr + "\r" + zw + "\n" + LANR_GUELTIG_ZWEI,
                "LANR [[LANR_1]]\r" + zw + "\n[[LANR_2]]", "LANR,LANR"));
        f.add(kein("ku-ak5e-sperrfolge-vom-umbruch-unterbrochen", "L\r" + zw + "\nA N R " + lanr));
        f.add(ein("ku-ak5e-sperrfolge-vor-dem-umbruch-vollstaendig", "L A N R\r" + zw + "\n", lanr, "LANR", ""));
        f.add(ein("ku-ak5f-nummerierte-liste", "LANR:\r" + zw + "\n1. ", lanr, "LANR", ""));
        // Mischung mit anderen Funden und Zeilenenden, ganze Kette
        f.add(kvFall("ku-mischung-mail-sperr-ip", "Mail " + MAIL + ", L A N R:\r" + zw + "\n" + lanr
                + ", Server 192.0.2.10", "Mail [[EMAIL_1]], L A N R:\r" + zw + "\n[[LANR_2]], Server [[IP_3]]",
                "EMAIL,LANR,IP"));
        f.add(kvFall("ku-mischung-zwei-arten", "LANR:\r" + zw + "\n" + lanr + "\nIK:\r" + shy + "\n" + ik,
                "LANR:\r" + zw + "\n[[LANR_1]]\nIK:\r" + shy + "\n[[IK_2]]", "LANR,IK"));
        f.add(kvFall("ku-mischung-gleiche-nummer-zweimal", "LANR:\r" + zw + "\n" + lanr + " und LANR:\r" + bom + "\n"
                + lanr, "LANR:\r" + zw + "\n[[LANR_1]] und LANR:\r" + bom + "\n[[LANR_1]]", "LANR,LANR"));
    }

    /**
     * Korpusfaelle zu kv-kontext-luecken (T-6, AK-1 bis AK-19 des Features): gesperrt geschriebene Kontextwoerter,
     * {@code I K}, Komma oder Semikolon vor dem nachgestellten Kontextwort, Gegenproben und bekannte Grenzen
     * (Plan Kap. 11). Sonderzeichen nur als {@code (char)}-Konstanten. Wo hinter einem Fund eine zweite Nummer ohne
     * Fund stehen soll, hat sie hoechstens fuenf Ziffern (keine Ziffernkette mit 6+ Ziffern im Ausgabetext).
     */
    private static void kvLueckenFaelle(List<KvFall> f) {
        String nb = String.valueOf((char) 0xA0);
        String zw = String.valueOf((char) 0x200B);
        String[] trenner = {" ", ".", "-", nb, String.valueOf((char) 0x2009), String.valueOf((char) 0x202F),
            String.valueOf((char) 0x2011), String.valueOf((char) 0x2013)};
        String[] trennerNamen = {"leerzeichen", "punkt", "bindestrich", "nbsp", "thin", "nnbsp", "gesch-bindestrich",
            "halbgeviert"};
        // Sperrschrift ab drei Buchstaben: jeder der acht Trenner, ein Fund 0,80
        for (int i = 0; i < trenner.length; i++) {
            String t = trenner[i];
            f.add(ein("kl-sperr-lanr-" + trennerNamen[i], "L" + t + "A" + t + "N" + t + "R ", LANR_GUELTIG, "LANR",
                    ""));
            f.add(ein("kl-sperr-iknr-" + trennerNamen[i], "I" + t + "K" + t + "N" + t + "R: ", IK_GUELTIG, "IK", ""));
        }
        f.add(ein("kl-sperr-lanr-gemischt", "L.A-N R ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-lanr-punkt-am-ende", "L.A.N.R. ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-lanr-gross-klein", "l A n R ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-arztnr", "A R Z T N R ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-arztnummer", "A r z t n u m m e r: ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-iknummer", "I K N U M M E R ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-sperr-institutionskennzeichen", "I N S T I T U T I O N S K E N N Z E I C H E N ", IK_GUELTIG,
                "IK", ""));
        f.add(ein("kl-sperr-direkt-an-ziffern", "L A N R", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-nachsatz-bindestrich-nummer", "L A N R-Nummer ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-nachsatz-nr", "L A N R Nr. ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-fuellwoerter", "L A N R des Behandlers ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-praefix-praxis", "Praxis L A N R ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-umbruch", "L A N R:\n", LANR_GUELTIG, "LANR", ""));
        f.add(kvFall("kl-sperr-aufzaehlung", "L A N R " + LANR_GUELTIG + ", " + LANR_GUELTIG_ZWEI + " und "
                + LANR_GUELTIG_FACHGRUPPE, "L A N R [[LANR_1]], [[LANR_2]] und [[LANR_3]]", "LANR,LANR,LANR"));
        f.add(ein("kl-sperr-unsichtbar-vor-leerzeichen", "L" + zw + " A N R ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("kl-sperr-unsichtbar-als-wort-bestand", "L" + zw + "A" + zw + "N" + zw + "R ", LANR_GUELTIG,
                "LANR", ""));
        f.add(ein("kl-sperr-ungueltige-nummer", "L A N R ", LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "LANR", ""));
        f.add(ein("kl-sperr-laenge-abweichend", "I K N R ", LAENGE_7, "IK", ""));
        f.add(ein("kl-sperr-trennerform", "L A N R ", LANR_TRENNER_LEERZEICHEN, "LANR", ""));
        f.add(kein("kl-sperr-ohne-nummer", "Bitte die Nummer eintragen: L A N R"));
        // I K (zwei Buchstaben): nur die unmittelbar folgende Nummer, fraglich 0,50
        f.add(ein("kl-ik-zwei-leerzeichen", "I K ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-punkte-doppelpunkt", "I.K.: ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-bindestrich", "I-K ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-nbsp", "I" + nb + "K ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-nachsatz", "I K-Nr. ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-punkte-nachsatz", "I.K.-Nr. ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-satzumfeld", "Rechnung an I.K. ", IK_GUELTIG, "IK", " gesendet"));
        f.add(ein("kl-ik-zwei-praefix", "Praxis-I.K. ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-ungueltige-nummer", "I K ", IK_UNGUELTIG_KOMPLEMENT, "IK", ""));
        f.add(ein("kl-ik-zwei-zweite-nummer-ohne-fund", "I K ", IK_GUELTIG, "IK", " und " + LAENGE_5));
        f.add(ein("kl-ik-zwei-bestandskontext-laeuft", "IK: Praxis I K ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-ik-zwei-normales-ik-danach", "I K IK ", IK_GUELTIG, "IK", ""));
        f.add(kein("kl-ik-zwei-fuellwoerter", "I K der Praxis " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-umbruch", "I K\n" + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-nachgestellt-klammer", IK_GUELTIG + " (I K)"));
        f.add(kein("kl-ik-zwei-nachgestellt", IK_GUELTIG + " I K"));
        f.add(kein("kl-ik-zwei-nachgestellt-komma", IK_GUELTIG + ", I K"));
        f.add(kein("kl-ik-zwei-doppeltrenner", "I  K " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-initialen", "I. K. " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-initialen-name", "I. K. Mustermann " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-tab", "I\tK " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-komma", "I,K " + IK_GUELTIG));
        f.add(kein("kl-ik-zwei-wortende-ik-ea", "I K EA " + IK_GUELTIG));
        // Komma oder Semikolon vor dem nachgestellten Kontextwort, Fund 0,60 (Fundbereich bleibt die Nummer)
        f.add(ein("kl-komma-lanr", "", LANR_GUELTIG, "LANR", ", LANR"));
        f.add(ein("kl-komma-ik", "", IK_GUELTIG, "IK", ", IK"));
        f.add(ein("kl-semikolon-ik", "", IK_GUELTIG, "IK", "; IK"));
        f.add(ein("kl-komma-ohne-leerzeichen", "", LANR_GUELTIG, "LANR", ",LANR"));
        f.add(ein("kl-komma-leerzeichen-davor", "", LANR_GUELTIG, "LANR", " ,LANR"));
        f.add(ein("kl-komma-tab", "", LANR_GUELTIG, "LANR", ",\tLANR"));
        f.add(ein("kl-komma-klammer-rund", "", LANR_GUELTIG, "LANR", ", (LANR)"));
        f.add(ein("kl-semikolon-klammer-eckig", "", IK_GUELTIG, "IK", " ; [IK]"));
        f.add(ein("kl-komma-arztnummer", "", LANR_GUELTIG, "LANR", ", Arztnummer"));
        f.add(ein("kl-komma-institutionskennzeichen", "", IK_GUELTIG, "IK", ", Institutionskennzeichen"));
        f.add(ein("kl-komma-ungueltige-nummer", "", LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "LANR", ", LANR"));
        f.add(ein("kl-komma-laenge-abweichend", "", LAENGE_7, "LANR", ", LANR"));
        f.add(ein("kl-komma-gesperrt-lanr", "", LANR_GUELTIG, "LANR", ", L A N R"));
        f.add(ein("kl-ohne-komma-gesperrt-lanr", "", LANR_GUELTIG, "LANR", " L A N R"));
        f.add(ein("kl-klammer-gesperrt-lanr", "", LANR_GUELTIG, "LANR", " (L A N R)"));
        f.add(kvFall("kl-komma-folge-zwei", LANR_GUELTIG + ", " + LANR_GUELTIG_ZWEI + ", LANR",
                "[[LANR_1]], [[LANR_2]], LANR", "LANR,LANR"));
        f.add(kvFall("kl-komma-folge-und", IK_GUELTIG + " und " + IK_GUELTIG_ZWEI + ", IK", "[[IK_1]] und [[IK_2]], IK",
                "IK,IK"));
        f.add(kvFall("kl-komma-folge-drei", LANR_GUELTIG + "; " + LANR_GUELTIG_ZWEI + " / " + LANR_GUELTIG_FACHGRUPPE
                + ", LANR", "[[LANR_1]]; [[LANR_2]] / [[LANR_3]], LANR", "LANR,LANR,LANR"));
        // Gegenproben: kein Fund (Plan Kap. 3.1, AK-18), darunter die bekannte Grenze 12R
        f.add(kein("kl-komma-zwei-kommas", LANR_GUELTIG + ",, LANR"));
        f.add(kein("kl-komma-komma-semikolon", LANR_GUELTIG + ",; LANR"));
        f.add(kein("kl-komma-und", LANR_GUELTIG + ", und LANR"));
        f.add(kein("kl-komma-schraegstrich", LANR_GUELTIG + ", / LANR"));
        f.add(kein("kl-komma-fuellwort", LANR_GUELTIG + ", Fax LANR"));
        f.add(kein("kl-komma-klammer-vor-komma", LANR_GUELTIG + "(, LANR)"));
        f.add(kein("kl-komma-zwei-klammern", LANR_GUELTIG + ", ((LANR)"));
        f.add(kein("kl-komma-doppelpunkt", LANR_GUELTIG + ": LANR"));
        f.add(kein("kl-komma-punkt", LANR_GUELTIG + ". LANR"));
        f.add(kein("kl-komma-umbruch-lf", LANR_GUELTIG + ",\nLANR"));
        f.add(kein("kl-komma-umbruch-crlf", IK_GUELTIG + ",\r\nIK"));
        f.add(kein("bekannteGrenze12R-und", LANR_GUELTIG + " und LANR"));
        f.add(kein("bekannteGrenze12R-schraegstrich", LANR_GUELTIG + " / LANR"));
        f.add(kein("bekannteGrenze12R-komma-und", IK_GUELTIG + ", und IK"));
        // Gegenproben Sperrschrift (Plan Kap. 3.2.4) und bekannte Grenzen 14a bis 14g
        f.add(kein("kl-sperr-gegenprobe-einzelbuchstabe-davor", "A L A N R " + LANR_GUELTIG));
        f.add(kein("kl-sperr-gegenprobe-buchstabe-dahinter", "L A N R X " + LANR_GUELTIG));
        f.add(kein("kl-sperr-gegenprobe-ein-buchstabe-weniger", "L A N " + LANR_GUELTIG));
        f.add(ein("kl-sperr-zwei-trenner-weit-fund", "L  A N R ", LANR_GUELTIG, "LANR", ""));
        f.add(kein("kl-sperr-gegenprobe-unterstrich", "L_A N R " + LANR_GUELTIG));
        f.add(kein("kl-sperr-gegenprobe-zeilenumbruch-leerzeile", "L A N R\n\n" + LANR_GUELTIG));
        f.add(kein("kl-sperr-gegenprobe-vier-fuellwoerter", "L A N R der Praxis von Frau Beispiel " + LANR_GUELTIG));
        f.add(kein("bekannteGrenze14a-teilgruppe-la-nr", "LA N R " + LANR_GUELTIG));
        f.add(kein("bekannteGrenze14a-teilgruppe-l-anr", "L ANR " + LANR_GUELTIG));
        f.add(kein("bekannteGrenze14a-teilgruppe-unsichtbar", "L" + zw + "A N R " + LANR_GUELTIG));
        // 14b: das Nachsatzwort "Nr" ist gesperrt geschrieben und bricht die Sperrfolge; trotzdem Fund (LANR direkt)
        f.add(ein("bekannteGrenze14b-nachsatz-gesperrt-trotzdemFund", "L A N R - N r ", LANR_GUELTIG, "LANR", ""));
        f.add(kein("bekannteGrenze14b-arzt-nr-gesperrt", "A r z t - N r " + LANR_GUELTIG));
        f.add(ein("bekannteGrenze14cGeschlossen-initialen-lanr", "L. A. N. R. ", LANR_GUELTIG, "LANR", ""));
        f.add(ein("bekannteGrenze14cGeschlossen-tab", "L\tA\tN\tR ", LANR_GUELTIG, "LANR", ""));
        f.add(kein("bekannteGrenze14d-wiederholt", "L A N R L A N R " + LANR_GUELTIG));
        f.add(kein("bekannteGrenze14e-herr-a-ik", "Herr A I K " + IK_GUELTIG));
        f.add(kein("bekannteGrenze14e-x-y-ik", "X Y I K " + IK_GUELTIG));
        f.add(ein("bekannteGrenze14g-ik-nachgestellt-ik", "I K ", IK_GUELTIG, "IK", " (IK)"));
        f.add(ein("bekannteGrenze14g-ik-komma-ik", "I K ", IK_GUELTIG, "IK", ", IK"));
        // 14h geschlossen (T-7a): laufender IK-Kontext, Vorwort, gesperrtes I K, Nachsatzwort, gueltige IK -> Fund
        f.add(ein("kl-14h-geschlossen-ik-praxis-sperr-nr", "IK: Praxis I K Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-iknr-der-sperr-nr", "IKNR, der I K-Nr. ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-nummer", "IK: Praxis I K-Nummer ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-doppelpunkt-nr", "IK: Praxis I K: Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-nr-punkt-nr", "IK: Praxis I K Nr. Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-punktform", "IK: Praxis I.K. Nr. ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-ohne-doppelpunkt", "IK Praxis I K Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-ueber-zeile", "IK:\nPraxis I K Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-anderes-kontextwort", "IK-Nr.: Praxis I K Nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-kleinschreibung", "IK: Praxis i k nr ", IK_GUELTIG, "IK", ""));
        f.add(ein("kl-14h-geschlossen-ohne-vorwort", "IK: I K Nr der ", IK_GUELTIG, "IK", ""));
        f.add(kvFall("kl-14h-geschlossen-nachgestellter-kontext-davor",
                IK_GUELTIG + ", IK\nPraxis I K-Nr. " + IK_GUELTIG_ZWEI,
                "[[IK_1]], IK\nPraxis I K-Nr. [[IK_2]]", "IK,IK"));
        f.add(kvFall("kl-14h-geschlossen-aufzaehlung-und",
                "IK: Praxis I K Nr " + IK_GUELTIG + " und " + IK_GUELTIG_ZWEI,
                "IK: Praxis I K Nr [[IK_1]] und [[IK_2]]", "IK,IK"));
        f.add(kvFall("kl-14h-geschlossen-aufzaehlung-komma",
                "IK: Praxis I K Nr " + IK_GUELTIG + ", " + IK_GUELTIG_ZWEI,
                "IK: Praxis I K Nr [[IK_1]], [[IK_2]]", "IK,IK"));
        f.add(ein("kl-14h-geschlossen-ungueltig", "IK: Praxis I K Nr ", IK_UNGUELTIG_KOMPLEMENT, "IK", ""));
        f.add(kein("kl-14h-gegenprobe-viertes-fuellwort", "IK: Praxis der Firma Nr " + IK_GUELTIG));
        f.add(kein("kl-14h-gegenprobe-fuellwoerter-hinter-nachsatz",
                "IK: Praxis I K Nr der Firma Hallo " + IK_GUELTIG));
        // Roundtrip mit Sperrschrift neben anderen Funden und Umgebung
        f.add(kvFall("kl-sperr-neben-mail-und-ip", "Mail " + MAIL + ", L A N R " + LANR_GUELTIG + ", Server 192.0.2.10",
                "Mail [[EMAIL_1]], L A N R [[LANR_2]], Server [[IP_3]]", "EMAIL,LANR,IP"));
        f.add(kvFall("kl-sperr-zwei-arten", "L A N R " + LANR_GUELTIG + "\nI K " + IK_GUELTIG,
                "L A N R [[LANR_1]]\nI K [[IK_2]]", "LANR,IK"));
    }

    /** Die Texte aller KV-Korpusfaelle (fuer den Differenztest). */
    static List<String> kvTexte() {
        List<String> texte = new ArrayList<>();
        for (KvFall k : kvFaelle()) {
            texte.add(k.text());
        }
        return texte;
    }

    /**
     * Laengste Ziffernkette im Text: aufeinanderfolgende ASCII-Ziffern, die ueber je genau einen Gruppentrenner
     * (Leerzeichen, U+00A0, Bindestrich, Punkt, Schraegstrich) verbunden sein duerfen; gezaehlt werden die Ziffern.
     */
    static int laengsteZiffernkette(String text) {
        int beste = 0;
        int aktuell = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                aktuell++;
                beste = Math.max(beste, aktuell);
            } else if (aktuell > 0 && i + 1 < n && text.charAt(i + 1) >= '0' && text.charAt(i + 1) <= '9'
                    && (c == ' ' || c == '\u00A0' || c == '-' || c == '.' || c == '/')) {
                continue;
            } else {
                aktuell = 0;
            }
        }
        return beste;
    }

    /**
     * Scan, Pseudonymisierung aller Funde, Pruefung und Rueckuebersetzung eines KV-Textes. Waechter der Testdatenregel
     * vor dem Scan; Meldungen nennen nur den Fallnamen, nie Text oder Nummern.
     */
    private static PseudonymizationResult kvPruefe(KvFall k, boolean ohneLangeZiffernkette) {
        assertEquals(0, neunerfenster(k.text(), "00")[1], k.name() + ": Waechter der Testdatenregel");
        List<Finding> funde = SCANNER.scan(k.text());
        assertEquals(k.typen(), typen(funde), k.name() + ": Fundzahl und Typen");
        for (Finding fund : funde) {
            assertTrue(k.text().substring(fund.start(), fund.end()).equals(fund.originalValue()),
                    k.name() + ": Teilstring-Invariante");
        }
        PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(k.text(), funde);
        assertTrue(k.erwartet().equals(pseudo.text()), k.name() + ": pseudonymisierter Text");
        if (ohneLangeZiffernkette) {
            assertTrue(laengsteZiffernkette(pseudo.text()) < 6,
                    k.name() + ": Ziffernkette mit 6+ Ziffern im Ausgabetext");
        }
        ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
        assertTrue(k.text().equals(zurueck.text()), k.name() + ": Roundtrip nicht zeichengleich");
        assertTrue(zurueck.unresolved().isEmpty(), k.name() + ": Rueckuebersetzung meldet Platzhalter");
        return pseudo;
    }

    @Test
    void ak20Kv_korpusIstGrossGenugUndNamenSindEindeutig() {
        List<KvFall> faelle = kvFaelle();
        assertTrue(faelle.size() >= 100, "Korpusgroesse " + faelle.size());
        assertEquals(faelle.size(), faelle.stream().map(KvFall::name).distinct().count(), "Fallnamen eindeutig");
        assertTrue(faelle.stream().anyMatch(k -> k.typen().isEmpty()), "Korpus enthaelt Faelle ohne Fund");
        assertTrue(faelle.stream().anyMatch(k -> k.typen().contains("LANR")), "Korpus enthaelt LANR-Funde");
        assertTrue(faelle.stream().anyMatch(k -> k.typen().contains("IK")), "Korpus enthaelt IK-Funde");
        // die Pruefung der Ausgabe ist nicht wirkungslos: eine unveraenderte Nummer wuerde auffallen
        assertTrue(laengsteZiffernkette("LANR " + LANR_GUELTIG) >= 6, "Pruefung erkennt neun Ziffern");
        assertTrue(laengsteZiffernkette("LANR " + LANR_TRENNER_LEERZEICHEN) >= 6, "Pruefung erkennt Gruppen");
        assertTrue(laengsteZiffernkette("LANR " + LANR_TRENNER_NBSP) >= 6, "Pruefung erkennt NBSP-Gruppen");
        assertTrue(laengsteZiffernkette("[[LANR_12]] und [[IK_3]]") < 6, "Platzhalter sind keine Ziffernkette");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvFallStrom")
    void ak20Kv_scanPseudonymisierungRoundtrip_keineLangeZiffernketteImAusgabetext(String name, KvFall k) {
        // Faelle ohne Fund (AK-10 bis AK-12, AK-14, AK-16) haben absichtlich unveraenderte Ziffern im Text.
        kvPruefe(k, !k.typen().isEmpty());
    }

    static Stream<Arguments> kvFallStrom() {
        return kvFaelle().stream().map(k -> Arguments.of(k.name(), k));
    }

    @Test
    void ak20Kv_gleicheZahlZweimal_gleicherPlatzhalter_verschiedeneSchreibweisen_verschiedene() {
        PseudonymizationResult p = kvPruefe(kvFall("konsistenz-gleich", "LANR " + LANR_GUELTIG + " ... LANR "
                + LANR_GUELTIG, "LANR [[LANR_1]] ... LANR [[LANR_1]]", "LANR,LANR"), true);
        assertEquals(1, p.mapping().size(), "ein Platzhalter fuer die gleiche Zahl");
        PseudonymizationResult q = kvPruefe(kvFall("konsistenz-trenner", "LANR " + LANR_GUELTIG + " ... LANR "
                + LANR_TRENNER_LEERZEICHEN, "LANR [[LANR_1]] ... LANR [[LANR_2]]", "LANR,LANR"), true);
        assertEquals(2, q.mapping().size(), "keine Normalisierung: zwei Platzhalter");
        assertEquals(LANR_GUELTIG, q.mapping().resolve("[[LANR_1]]").orElseThrow(), "Platzhalter 1 fuehrt zurueck");
        assertEquals(LANR_TRENNER_LEERZEICHEN, q.mapping().resolve("[[LANR_2]]").orElseThrow(),
                "Platzhalter 2 fuehrt zurueck");
    }

    @Test
    void ak20Kv_gleicheZahlUnterLanrUndIk_platzhalterTragenDenTypDesFundes_beideFuehrenZurueck_of5() {
        PseudonymizationResult p = kvPruefe(kvFall("konsistenz-lanr-ik", "LANR " + IK_GUELTIG + "\n\nIK "
                + IK_GUELTIG, "LANR [[LANR_1]]\n\nIK [[IK_2]]", "LANR,IK"), true);
        assertEquals(2, p.mapping().size(), "zwei Platzhalter fuer dieselbe Zahl, je Typ einer");
        assertEquals(IK_GUELTIG, p.mapping().resolve("[[LANR_1]]").orElseThrow(), "LANR-Platzhalter fuehrt zurueck");
        assertEquals(IK_GUELTIG, p.mapping().resolve("[[IK_2]]").orElseThrow(), "IK-Platzhalter fuehrt zurueck");
        PseudonymizationResult umgekehrt = kvPruefe(kvFall("konsistenz-ik-lanr", "IK " + IK_GUELTIG + "\n\nLANR "
                + IK_GUELTIG, "IK [[IK_1]]\n\nLANR [[LANR_2]]", "IK,LANR"), true);
        assertEquals(2, umgekehrt.mapping().size(), "zwei Platzhalter, umgekehrte Reihenfolge");
    }

    @Test
    void ak20Kv_kiSimulation_platzhalterVertauschtUndEingebettet_ergibtOriginalwerte() {
        PseudonymizationResult p = kvPruefe(kvFall("ki-kv", "IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG,
                "IK [[IK_1]], LANR [[LANR_2]]", "IK,LANR"), true);
        ReidentificationResult r = umformen(p, "Antwort: LANR [[LANR_2]] und IK ([[IK_1]]), nochmal [[LANR_2]].");
        assertTrue(("Antwort: LANR " + LANR_GUELTIG + " und IK (" + IK_GUELTIG + "), nochmal " + LANR_GUELTIG + ".")
                .equals(r.text()), "KI-Antwort wird rueckuebersetzt");
        assertTrue(r.unresolved().isEmpty(), "keine Meldung");
    }

    // ------------------------------------------------------------------ Bekannte Grenze 13 (Plan T-6 Nachtrag)

    /**
     * Bekannte Grenze 13: Ein woertlich im Eingabetext stehender Platzhalter mit mindestens sechs Ziffern wird
     * zerstueckelt pseudonymisiert, weil {@code IK} und {@code LANR} vor dem Unterstrich als Kontextwort gelten und die
     * Ziffern als Fund (Laengenabweichung, fraglich) erkannt werden. Der Anfang {@code [[IK_} steht dann als
     * platzhalterartiger Originaltext (Literalschutz, Typ TEXT) neben dem Platzhalter der Ziffern; das schliessende
     * {@code ]]} bleibt stehen. Der Roundtrip bleibt zeichengleich und meldet nichts; im Ausgabetext bleibt keine
     * Ziffernkette mit 6+ Ziffern stehen (die Ziffern sind ersetzt), daher braucht dieser Fall keine Ausnahme von der
     * Pruefung. Das Verhalten wird festgehalten, nicht veraendert.
     */
    @Test
    void bekannteGrenze13_woertlicherPlatzhalterMitSechsZiffern_wirdZerstueckeltRoundtripZeichengleich() {
        String sechs = LAENGE_5 + "6";
        PseudonymizationResult ik = kvPruefe(kvFall("grenze13-ik-ohne-fuehrende-null",
                "Vorher [[IK_" + sechs + "]] nachher", "Vorher [[TEXT_1]][[IK_2]]]] nachher", "IK"), true);
        assertEquals(2, ik.mapping().size(), "Anfang als TEXT, Ziffern als IK");
        assertEquals(sechs, ik.mapping().resolve("[[IK_2]]").orElseThrow(), "Platzhalter fuehrt auf Ziffern zurueck");
        assertEquals("[[IK_", ik.mapping().resolve("[[TEXT_1]]").orElseThrow(), "Platzhalter fuehrt auf den Anfang");
        kvPruefe(kvFall("grenze13-lanr-ohne-fuehrende-null", "Vorher [[LANR_" + sechs + "]] nachher",
                "Vorher [[TEXT_1]][[LANR_2]]]] nachher", "LANR"), true);
        kvPruefe(kvFall("grenze13-ik-mit-fuehrender-null", "Vorher [[IK_" + LAENGE_6 + "]] nachher",
                "Vorher [[TEXT_1]][[IK_2]]]] nachher", "IK"), true);
        kvPruefe(kvFall("grenze13-ik-neun-ziffern", "Vorher [[IK_" + IK_GUELTIG + "]] nachher",
                "Vorher [[TEXT_1]][[IK_2]]]] nachher", "IK"), true);
    }
}
