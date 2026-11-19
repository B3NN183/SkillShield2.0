package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.InvalidFindingException.Reason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Eingabeprüfung der Pseudonymisierung (T-7; AK-32, AK-47, AK-50). Alle Daten sind synthetisch. */
class PseudonymizerValidierungTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";
    private static final FindingType PERSON = new FindingType("PERSON");
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private final Pseudonymizer p = new Pseudonymizer();

    private static Finding f(String text, int start, int end) {
        return new Finding(PERSON, text.substring(start, end), start, end, 1.0, "manuell");
    }

    private InvalidFindingException ablehnung(String text, List<Finding> funde) {
        return assertThrows(InvalidFindingException.class, () -> p.pseudonymize(text, funde));
    }

    private static void assertKlartextfrei(InvalidFindingException e) {
        String alles = e.getMessage() + "|" + e + "|" + e.getCause();
        assertFalse(alles.contains(MARKER), alles);
        assertFalse(alles.contains("mustermann"), alles);
        assertFalse(alles.contains("GEHEIM"), alles);
        assertNull(e.getCause());
        assertEquals("Ungültiger Fund an Listenindex " + e.index() + ": " + e.reason(), e.getMessage());
    }

    @Test
    void nullElementWirdMitNullElementAbgelehnt() {
        String text = "abc " + MARKER;
        InvalidFindingException e = ablehnung(text, Arrays.asList(f(text, 0, 3), null));
        assertEquals(Reason.NULL_ELEMENT, e.reason());
        assertEquals(1, e.index());
        assertEquals("Ungültiger Fund an Listenindex 1: NULL_ELEMENT", e.getMessage());
        assertKlartextfrei(e);
    }

    @Test
    void endeHinterTextendeWirdMitOutOfRangeAbgelehnt() {
        String lang = "abc " + MARKER + " def";
        Finding zuWeit = f(lang, 4, 4 + MARKER.length());
        String text = "abc " + MARKER.substring(0, 5);
        InvalidFindingException e = ablehnung(text, List.of(zuWeit));
        assertEquals(Reason.OUT_OF_RANGE, e.reason());
        assertEquals(0, e.index());
        assertKlartextfrei(e);
    }

    @Test
    void ende_genauAmTextende_istGueltig() {
        String text = "abc " + MARKER;
        assertEquals("abc [[PERSON_1]]", p.pseudonymize(text, List.of(f(text, 4, text.length()))).text());
    }

    @Test
    void ueberlappungUnsortiertWirdMitOverlapAbgelehnt() {
        String text = "0123456789" + MARKER;
        // Listenreihenfolge: B(5,8) zuerst, dann A(0,6); sortiert: A, B. Gemeldet wird der spätere (B) mit Listenindex 0.
        InvalidFindingException e = ablehnung(text, List.of(f(text, 5, 8), f(text, 0, 6)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(0, e.index());
        assertEquals("Ungültiger Fund an Listenindex 0: OVERLAP", e.getMessage());
        assertKlartextfrei(e);
    }

    @Test
    void ueberlappungSortiertWirdMitListenindexDesSpaeterenFundesAbgelehnt() {
        String text = "0123456789" + MARKER;
        InvalidFindingException e = ablehnung(text, List.of(f(text, 0, 6), f(text, 5, 8)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(1, e.index());
    }

    @Test
    void teilweiseUeberlappendeFundeWerdenAbgelehntUndNichtZusammengelegt() {
        String text = "Start " + MARKER + " Ende";
        int s = text.indexOf(MARKER);
        Finding a = f(text, s, s + 10);
        Finding b = f(text, s + 6, s + MARKER.length());
        InvalidFindingException e = ablehnung(text, List.of(a, b));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(1, e.index());
        assertKlartextfrei(e);
    }

    @Test
    void enthaltenerFundWirdMitOverlapAbgelehnt() {
        String text = "0123456789";
        InvalidFindingException e = ablehnung(text, List.of(f(text, 0, 10), f(text, 2, 4)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(1, e.index());
    }

    @Test
    void identischeDoppelteFundeWerdenMitIndexDesZweitenAbgelehnt() {
        String text = "0123456789";
        InvalidFindingException e = ablehnung(text, List.of(f(text, 2, 4), f(text, 2, 4)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(1, e.index());
    }

    @Test
    void gleicherStartMeldetDenLaengerenAlsSpaeteren() {
        String text = "0123456789";
        // sortiert nach start, dann end: (0,3) vor (0,5); der spätere in Textreihenfolge ist (0,5) = Listenindex 0
        InvalidFindingException e = ablehnung(text, List.of(f(text, 0, 5), f(text, 0, 3)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(0, e.index());
    }

    @Test
    void ueberlappungHinterLangemEnthaltenemFundWirdErkannt() {
        String text = "0123456789";
        // A(0,8) enthält B(1,2); C(3,4) liegt ebenfalls in A. Sortiert: A, B, C; der erste Verstoß ist B (Listenindex 2).
        InvalidFindingException e = ablehnung(text, List.of(f(text, 0, 8), f(text, 3, 4), f(text, 1, 2)));
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(2, e.index());
    }

    @Test
    void angrenzendeFundeSindKeineUeberlappung() {
        String text = "0123456789";
        PseudonymizationResult r = p.pseudonymize(text, List.of(f(text, 5, 8), f(text, 0, 5)));
        assertEquals("[[PERSON_1]][[PERSON_2]]89", r.text());
    }

    @Test
    void abweichenderWertWirdMitValueMismatchAbgelehnt() {
        String text = "abc " + MARKER + " def";
        int s = text.indexOf(MARKER);
        Finding falsch = new Finding(PERSON, MARKER, 0, MARKER.length(), 1.0, "manuell");
        InvalidFindingException e = ablehnung(text, List.of(f(text, s, s + 5), falsch));
        assertEquals(Reason.VALUE_MISMATCH, e.reason());
        assertEquals(1, e.index());
        assertKlartextfrei(e);
    }

    @Test
    void surrogatpaarAmAnfangWirdMitSplitsSurrogatePairAbgelehnt() {
        String text = "a" + EMOJI + "b" + MARKER;
        // Start in der Mitte des Paares: Fund beginnt beim Low-Surrogat (Index 2)
        Finding f = new Finding(PERSON, text.substring(2, 4), 2, 4, 1.0, "manuell");
        InvalidFindingException e = ablehnung(text, List.of(f));
        assertEquals(Reason.SPLITS_SURROGATE_PAIR, e.reason());
        assertEquals(0, e.index());
        assertKlartextfrei(e);
    }

    @Test
    void surrogatpaarAmEndeWirdMitSplitsSurrogatePairAbgelehnt() {
        String text = "a" + EMOJI + "b" + MARKER;
        // Ende in der Mitte des Paares: Fund endet nach dem High-Surrogat (end = 2)
        Finding f = new Finding(PERSON, text.substring(0, 2), 0, 2, 1.0, "manuell");
        InvalidFindingException e = ablehnung(text, List.of(f));
        assertEquals(Reason.SPLITS_SURROGATE_PAIR, e.reason());
        assertEquals(0, e.index());
        assertKlartextfrei(e);
    }

    @Test
    void surrogatpaarSelbstAlsGanzerFundIstGueltig() {
        String text = "a" + EMOJI + "b";
        PseudonymizationResult r = p.pseudonymize(text, List.of(f(text, 1, 3)));
        assertEquals("a[[PERSON_1]]b", r.text());
        assertFalse(Character.isSurrogate(r.text().charAt(r.text().length() - 1)));
    }

    @Test
    void nullTextWirdMitNpeUndParameternamenAbgelehnt() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> p.pseudonymize(null, List.of()));
        assertEquals("text", e.getMessage());
    }

    @Test
    void nullListeWirdMitNpeUndParameternamenAbgelehnt() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> p.pseudonymize("a", null));
        assertEquals("findings", e.getMessage());
    }

    @Test
    void ungueltigeEingabeLiefertKeinTeilergebnisUndLaesstListeUnveraendert() {
        String text = "0123456789" + MARKER;
        List<Finding> funde = new ArrayList<>(List.of(f(text, 0, 3), f(text, 4, 6), f(text, 5, 8)));
        List<Finding> kopie = new ArrayList<>(funde);
        assertThrows(InvalidFindingException.class, () -> p.pseudonymize(text, funde));
        assertEquals(kopie, funde);
        // ein späterer Aufruf mit gültiger Liste ist unbeeinflusst (zustandslos, Zähler ab 1)
        assertEquals("[[PERSON_1]]3456789" + MARKER, p.pseudonymize(text, List.of(f(text, 0, 3))).text());
    }

    @Test
    void jederReasonMeldetKeinenKlartext() {
        String text = "abc " + MARKER + " def";
        int s = text.indexOf(MARKER);
        int e0 = s + MARKER.length();
        String surrogatText = MARKER + EMOJI;
        List<InvalidFindingException> alle = new ArrayList<>();
        alle.add(ablehnung(text, Arrays.asList(f(text, s, e0), null)));
        alle.add(ablehnung("abc", List.of(f(text, s, e0))));
        alle.add(ablehnung(text, List.of(f(text, s, e0), f(text, s + 2, e0))));
        alle.add(ablehnung(text, List.of(new Finding(PERSON, MARKER, 0, MARKER.length(), 1.0, "manuell"))));
        int hi = MARKER.length();
        alle.add(ablehnung(surrogatText,
                List.of(new Finding(PERSON, surrogatText.substring(0, hi + 1), 0, hi + 1, 1.0, "manuell"))));
        List<Reason> gesehen = new ArrayList<>();
        for (InvalidFindingException e : alle) {
            assertKlartextfrei(e);
            gesehen.add(e.reason());
        }
        assertEquals(List.of(Reason.values()), gesehen.stream().sorted().toList());
    }

    @Test
    void ausnahmeIstIllegalArgumentExceptionUndNurReasonUndIndex() {
        InvalidFindingException e = new InvalidFindingException(Reason.OVERLAP, 3);
        assertInstanceOf(IllegalArgumentException.class, e);
        assertEquals("Ungültiger Fund an Listenindex 3: OVERLAP", e.getMessage());
        assertEquals(Reason.OVERLAP, e.reason());
        assertEquals(3, e.index());
        assertEquals("de.skillshield.core.pseudonymize.InvalidFindingException: Ungültiger Fund an Listenindex 3: OVERLAP",
                e.toString());
        assertThrows(NullPointerException.class, () -> new InvalidFindingException(null, 0));
        // keine Felder außer Reason und Index
        long felder = Arrays.stream(InvalidFindingException.class.getDeclaredFields())
                .filter(fld -> !java.lang.reflect.Modifier.isStatic(fld.getModifiers())).count();
        assertEquals(2, felder);
    }

    @Test
    void reihenfolgeDerPruefungenIstDeterministisch() {
        String text = "0123456789" + EMOJI;
        // Listenreihenfolge: das erste fehlerhafte Element gewinnt, auch wenn ein späteres Element anders fehlerhaft ist
        assertEquals(Reason.VALUE_MISMATCH, ablehnung(text,
                Arrays.asList(new Finding(PERSON, "zz", 0, 2, 1.0, "manuell"), null)).reason());
        assertEquals(Reason.NULL_ELEMENT, ablehnung(text,
                Arrays.asList(null, new Finding(PERSON, "zz", 0, 2, 1.0, "manuell"))).reason());
        // je Element: OUT_OF_RANGE vor VALUE_MISMATCH
        assertEquals(Reason.OUT_OF_RANGE, ablehnung("abc",
                List.of(new Finding(PERSON, "zzzzzz", 1, 7, 1.0, "manuell"))).reason());
        // je Element: VALUE_MISMATCH vor SPLITS_SURROGATE_PAIR (Fund beginnt im Paar, Wert falsch)
        int lo = text.length() - 1;
        assertEquals(Reason.VALUE_MISMATCH, ablehnung(text,
                List.of(new Finding(PERSON, "z", lo, lo + 1, 1.0, "manuell"))).reason());
        // Elementfehler vor Überlappung, auch wenn der Elementfehler später in der Liste steht
        InvalidFindingException e = ablehnung(text, List.of(f(text, 0, 5), f(text, 3, 6),
                new Finding(PERSON, "zz", 7, 9, 1.0, "manuell")));
        assertEquals(Reason.VALUE_MISMATCH, e.reason());
        assertEquals(2, e.index());
        // SPLITS_SURROGATE_PAIR vor OVERLAP
        InvalidFindingException e2 = ablehnung(text, List.of(f(text, 0, 5), f(text, 3, 6),
                new Finding(PERSON, text.substring(lo, lo + 1), lo, lo + 1, 1.0, "manuell")));
        assertEquals(Reason.SPLITS_SURROGATE_PAIR, e2.reason());
        assertEquals(2, e2.index());
    }

    @Test
    void validierungMit100000FundenIstSchnell() {
        int n = 100_000;
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            sb.append("ab ");
        }
        String text = sb.toString();
        List<Finding> gueltig = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            gueltig.add(f(text, i * 3, i * 3 + 2));
        }
        Collections.shuffle(gueltig, new Random(7));
        PseudonymizationResult r = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> p.pseudonymize(text, gueltig));
        assertEquals(n, text.length() / 3);
        assertTrue(r.text().startsWith("[[PERSON_1]] [[PERSON_1]]"));

        // Überlappung am Ende der unsortierten Liste: wird ebenfalls schnell gefunden
        List<Finding> ungueltig = new ArrayList<>(gueltig);
        ungueltig.add(f(text, n * 3 - 4, n * 3 - 1));
        InvalidFindingException e = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> ablehnung(text, ungueltig));
        assertEquals(Reason.OVERLAP, e.reason());
    }
}
