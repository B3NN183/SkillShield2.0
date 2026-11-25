package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Grenzlast der Geheimnis-Erkennung (Plan geheimnisse-erkennung, T-4; Anforderung AK-46): Texte von mindestens einer
 * Million Zeichen, deren Aufbau jede feste Schranke der Kontextregel belastet. Scan ({@code standard()}),
 * Pseudonymisierung und Rueckuebersetzung brauchen je weniger als 2 Sekunden (je Schritt einzeln mit
 * {@code assertTimeoutPreemptively}), die Zahl der PASSWORT-Funde stimmt exakt mit der Formel im Fall ueberein, und
 * der Roundtrip ist zeichengleich. Die Grenze ist die des bestehenden {@code GrenzlastTest}.
 *
 * <p>Stand T-5: die Kontextregel mit K1, Umbruch und K2 bis K4 (zwei Durchgaenge). Die Faelle fuer Tokens, JWT, PEM,
 * ODBC, Oracle und Kommandozeile folgen mit den jeweiligen Tasks. Die Texte entstehen zur Laufzeit; keine Meldung
 * nennt Text oder Wert.
 */
class GeheimnisGrenzlastTest {

    private static final Duration GRENZE = Duration.ofSeconds(2);
    private static final int MIN_ZEICHEN = 1_000_000;

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    private static <T> T zeitbegrenzt(Supplier<T> schritt) {
        return assertTimeoutPreemptively(GRENZE, schritt::get);
    }

    /** Wiederholt {@code baustein} so oft, dass der Text mindestens {@link #MIN_ZEICHEN} Zeichen hat. */
    private static String wiederholt(String baustein) {
        int n = (MIN_ZEICHEN + baustein.length() - 1) / baustein.length();
        return baustein.repeat(n);
    }

    private static int anzahl(String baustein) {
        return (MIN_ZEICHEN + baustein.length() - 1) / baustein.length();
    }

    private static void pruefe(String name, String text, int erwarteteFunde) {
        assertTrue(text.length() >= MIN_ZEICHEN, name + ": Text zu kurz, " + text.length());
        TextScanner scanner = TextScanner.standard();
        List<Finding> funde = zeitbegrenzt(() -> scanner.scan(text));
        PseudonymizationResult pseudo = zeitbegrenzt(() -> PSEUDONYMIZER.pseudonymize(text, funde));
        ReidentificationResult zurueck = zeitbegrenzt(() -> REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()));
        long passwoerter = funde.stream().filter(f -> f.type().equals(GeheimnisTypen.PASSWORT)).count();
        assertEquals(erwarteteFunde, passwoerter, name + ": Zahl der PASSWORT-Funde");
        assertEquals(0, funde.stream().filter(f -> f.type().equals(GeheimnisTypen.TOKEN)).count(),
                name + ": TOKEN-Funde");
        assertTrue(text.equals(zurueck.text()), name + ": Roundtrip nicht zeichengleich");
        assertTrue(zurueck.unresolved().isEmpty(), name + ": unaufgeloeste Platzhalter");
        for (int i = 1; i < funde.size(); i++) {
            assertTrue(funde.get(i - 1).end() <= funde.get(i).start(), name + ": Ueberlappung bei " + i);
        }
    }

    static Stream<Arguments> faelle() {
        String wortAmEnde = "a".repeat(999_990) + "passwort" + ": ";
        String langerWert = "Passwort: " + "a1".repeat(500_000);
        String langerKontext = "x".repeat(MIN_ZEICHEN) + " passwort: ";
        String vorlage = "Passwort: " + P1 + " ";
        return Stream.concat(Stream.of(
                Arguments.of("Passwort: wiederholt", wiederholt("Passwort: "), 0),
                Arguments.of("Passwort: Wert wiederholt", wiederholt(vorlage), anzahl(vorlage)),
                Arguments.of("Passwort fuer a b c d wiederholt",
                        wiederholt("Passwort f" + (char) 0xFC + "r a b c d "), 0),
                Arguments.of("Passwort: Anfuehrungszeichen wiederholt", wiederholt("Passwort: \""), 0),
                Arguments.of("Passwort: Zeilenumbruch wiederholt", wiederholt("Passwort:\n"), 0),
                Arguments.of("ein Wort auf passwort endend, dann Doppelpunkt", wortAmEnde, 0),
                Arguments.of("pw: ohne Leerraum wiederholt", wiederholt("pw:"), 0),
                Arguments.of("ein Wert mit einer Million Zeichen", langerWert, 0),
                Arguments.of("langes Wort, dann Kontextwort", langerKontext, 0),
                Arguments.of("Passwort: Wert und Zeilenumbruch wiederholt", wiederholt("Passwort:\n" + P1 + "\n"),
                        anzahl("Passwort:\n" + P1 + "\n")),
                Arguments.of("Kennwort: Wert, Komma, wiederholt", wiederholt("Kennwort: " + P1 + ", "),
                        anzahl("Kennwort: " + P1 + ", ")),
                Arguments.of("Auszeichnung wiederholt", wiederholt("**Passwort:** **" + P1 + "** "),
                        anzahl("**Passwort:** **" + P1 + "** ")),
                Arguments.of("Zwischenteil mit langem Wort wiederholt",
                        wiederholt("Passwort f" + (char) 0xFC + "r " + "x".repeat(64) + " "), 0),
                Arguments.of("Klammern am Ende wiederholt", wiederholt("Passwort: " + P1 + ")))) "),
                        anzahl("Passwort: " + P1 + ")))) ")),
                Arguments.of("viele schliessende Klammern in einem Wort", "Passwort: " + ")".repeat(MIN_ZEICHEN), 0)),
                Stream.of(t5Faelle()));
    }

    private static final String K234_GEMISCHT = "Passwort: " + P1 + " Kennwort ist " + P2 + " <password>" + P3
            + "</password> | Kennwort | " + P1 + " |\n";
    private static final String K2_UEBERDECKT = "Passwort ist Ab3!xPasswort:" + P1 + " ";

    /** Faelle fuer K2 bis K4 (T-5): Ketten mit und ohne Leerraum, auch die frueher quadratischen. */
    static Arguments[] t5Faelle() {
        String k2 = "Passwort ist " + P1 + " ";
        String k3 = "| Passwort | " + P1 + " | ";
        String k4 = "<password>" + P1 + "</password> ";
        return new Arguments[] {
            Arguments.of("Passwort ist wiederholt", wiederholt("Passwort ist "), 0),
            Arguments.of("Passwort ist Wert wiederholt", wiederholt(k2), anzahl(k2)),
            Arguments.of("| Passwort | wiederholt", wiederholt("| Passwort | "), 0),
            Arguments.of("| Passwort | Wert | wiederholt", wiederholt(k3), anzahl(k3)),
            Arguments.of("<password> wiederholt", wiederholt("<password>"), 0),
            Arguments.of("<password>Wert</password> wiederholt", wiederholt(k4), anzahl(k4)),
            Arguments.of("Kette Passwort|a| ohne Leerraum", wiederholt("Passwort|a|"), 0),
            Arguments.of("Kette x|Passwort|ab ohne Leerraum", wiederholt("x|Passwort|ab"), 0),
            Arguments.of("Kette ||Passwort||a|| (Jira) ohne Leerraum", wiederholt("||Passwort||a||"), 0),
            Arguments.of("ein Wort von einer Million | hinter |Passwort|", "|Passwort|" + "|".repeat(1_000_000), 0),
            Arguments.of("Kette Passwort Tabulator a Tabulator", wiederholt("Passwort\ta\t"), 0),
            Arguments.of("Kette <password>a ohne Leerraum", wiederholt("<password>a"), 0),
            Arguments.of("Passwort ist mit zu langem Wort wiederholt",
                    wiederholt("Passwort ist " + "a1".repeat(2100) + " "), 0),
            Arguments.of("ein Wort von einer Million Zeichen hinter ist", "Passwort ist " + "a1".repeat(500_000), 0),
            Arguments.of("Zwischenteil und ist wiederholt", wiederholt("Passwort f" + (char) 0xFC + "r a b c ist "), 0),
            Arguments.of("viele Kandidaten aus beiden Durchgaengen", wiederholt(K234_GEMISCHT),
                    4 * anzahl(K234_GEMISCHT)),
            Arguments.of("K2-Kandidaten mit innerem K1 wiederholt", wiederholt(K2_UEBERDECKT), anzahl(K2_UEBERDECKT))
        };
    }

    private static long minNanos(PasswortRecognizer erkenner, String text, int runden, int wiederholungen) {
        long beste = Long.MAX_VALUE;
        for (int runde = 0; runde < runden; runde++) {
            long start = System.nanoTime();
            for (int i = 0; i < wiederholungen; i++) {
                erkenner.detect(text);
            }
            beste = Math.min(beste, System.nanoTime() - start);
        }
        return beste;
    }

    /**
     * Linearitaet ohne feste Zeitgrenze (T-5): Vervierfacht sich die Textlaenge (250 000 gegen 1 000 000 Zeichen),
     * waechst die Zeit bei linearem Aufwand etwa auf das Vierfache, bei quadratischem auf das Sechzehnfache. Der
     * Test verlangt einen Faktor unter 10 (bester von fuenf Laeufen, nach dem Aufwaermen).
     */
    @Test
    void ak46_vervierfachterText_zeitWaechstDeutlichSchwaecherAlsQuadratisch() {
        PasswortRecognizer erkenner = new PasswortRecognizer();
        List<String> bausteine = List.of("Passwort ist ", "| Passwort | ", "<password>", "Passwort|a|", "x|Passwort|ab",
                "||Passwort||a||", "Passwort\ta\t", "<password>a", K234_GEMISCHT, K2_UEBERDECKT);
        for (String baustein : bausteine) {
            String klein = baustein.repeat(250_000 / baustein.length() + 1);
            String gross = baustein.repeat(1_000_000 / baustein.length() + 1);
            assertTrue(klein.length() >= 250_000 && gross.length() >= 1_000_000, "Laenge " + baustein.length());
            minNanos(erkenner, klein, 2, 2);
            minNanos(erkenner, gross, 2, 1);
            long tKlein = minNanos(erkenner, klein, 5, 6);
            long tGross = minNanos(erkenner, gross, 5, 6);
            assertTrue(tGross < 10L * Math.max(tKlein, 1_000_000L),
                    "Zeit waechst zu stark bei Baustein der Laenge " + baustein.length() + ": " + tKlein + " ns gegen "
                            + tGross + " ns");
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("faelle")
    void ak46_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name, String text,
                                                                                    int erwartet) {
        pruefe(name, text, erwartet);
    }

    @Test
    void ak46_dieFormelnDerFaelleStimmen_dieTexteHabenMindestensEineMillionZeichen() {
        assertEquals(1_000_000 / "Passwort: ".length(), anzahl("Passwort: "));
        assertTrue(wiederholt("pw:").length() >= MIN_ZEICHEN);
        assertEquals(MIN_ZEICHEN, 999_990 + "passwort".length() + ": ".length());
    }
}
