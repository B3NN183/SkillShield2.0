package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
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
 * <p>Stand T-4: nur die Kontextregel (K1 und Umbruch). Die Faelle fuer K2 bis K4, Tokens, JWT, PEM, ODBC, Oracle und
 * Kommandozeile folgen mit den jeweiligen Tasks. Die Texte entstehen zur Laufzeit; keine Meldung nennt Text oder
 * Wert.
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
        return Stream.of(
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
                Arguments.of("viele schliessende Klammern in einem Wort", "Passwort: " + ")".repeat(MIN_ZEICHEN), 0));
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
