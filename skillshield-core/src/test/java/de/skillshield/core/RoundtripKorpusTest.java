package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
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
import org.junit.jupiter.api.Test;

/**
 * Roundtrip-Korpus (T-17; AK-7 (g), AK-25, AK-26, AK-28, AK-37, AK-38, AK-39, AK-54).
 *
 * <p>Je Korpus-Eintrag: {@code TextScanner.standard().scan} (plus ggf. manuelle Funde), alle Funde
 * pseudonymisieren, den erwarteten Ausgabetext, die erwarteten Fundtypen in Textreihenfolge (Fundzahl = Anzahl der
 * Typen) und die Abwesenheit einer ausdrücklich angegebenen Liste verbotener Bestandteile prüfen, dann
 * zurückübersetzen: Ergebnis zeichengleich mit dem Original, keine Meldung. Die Tests dokumentieren auch die
 * bekannten Grenzen aus dem Architektur-Plan (D-5, unsichtbare Zeichen im Kopf).
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
}
