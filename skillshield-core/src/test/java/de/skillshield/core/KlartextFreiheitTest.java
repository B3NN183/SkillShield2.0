package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_MARKER;
import static de.skillshield.core.SynthetischeKvNummern.LANR_MARKER;
import static de.skillshield.core.TestTexte.bs;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.InvalidFindingException;
import de.skillshield.core.pseudonymize.InvalidFindingException.Reason;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PlaceholderSyntax;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.core.reidentify.UnresolvedPlaceholder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Klartextfreiheit der Fehlerpfade und Ausgaben (T-20; AK-50, AK-47; Fehlerpfade aus AK-32, AK-41, AK-42, AK-47).
 *
 * <p>Ein synthetischer Marker-Pfad und eine Marker-E-Mail (reservierte Domain) gehen durch jeden Fehlerpfad, den
 * die öffentliche API des Fachkerns hat. Geprüft werden Meldung, die ganze {@code cause}-Kette,
 * {@code getSuppressed()} (rekursiv), {@code toString()} der Ausnahme und aller Ergebnis- und Modellobjekte sowie
 * umgeleitete {@code System.out}/{@code System.err}. Nirgends darf der Marker-Pfad, die Marker-E-Mail oder einer
 * ihrer Bestandteile stehen (ohne Beachtung der Groß-/Kleinschreibung); Position, Typ und Platzhalter sind erlaubt.
 *
 * <p>Der Test prüft nicht ins Leere: Jeder Fehlerpfad belegt per {@code assertThrows}, dass der Fehler entsteht,
 * und Selbsttests belegen, dass die Suchfunktion den Marker in Meldung, {@code cause}, {@code suppressed},
 * {@code toString()} und umgeleiteter Ausgabe findet. Die Ausgabe wird in {@code finally} zurückgesetzt.
 */
class KlartextFreiheitTest {

    private static final String MARKER_PFAD = bs("C:¦Marker¦mustermann¦MarkerTool.exe");
    private static final String MARKER_MAIL = "marker.mustermann@example.invalid";

    /** Der Marker und alle seine Bestandteile; Suche ohne Beachtung der Groß-/Kleinschreibung. */
    private static final List<String> MARKER_TEILE = List.of(MARKER_PFAD, MARKER_MAIL, "Marker", "mustermann",
            "MarkerTool", "marker.mustermann", LANR_MARKER, IK_MARKER, LANR_MARKER.substring(0, 6),
            IK_MARKER.substring(0, 6));

    private static final String TEXT = "Siehe " + MARKER_PFAD + " und " + MARKER_MAIL + ", LANR " + LANR_MARKER
            + ", IK " + IK_MARKER + " Ende";
    private static final String UE_MELDUNG = "Ung\u00FCltiger Fund an Listenindex ";

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    // ------------------------------------------------------------------ Suchfunktion

    /** Der erste im Text gefundene Bestandteil des Markers, sonst leer. */
    static Optional<String> findeMarker(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String klein = text.toLowerCase(Locale.ROOT);
        for (String teil : MARKER_TEILE) {
            if (klein.contains(teil.toLowerCase(Locale.ROOT))) {
                return Optional.of(teil);
            }
        }
        return Optional.empty();
    }

    /**
     * Alle Texte einer Ausnahme: Meldung, lokalisierte Meldung und {@code toString()} je Glied von Ursache und
     * Unterdrückten.
     */
    static List<String> alleTexte(Throwable wurzel) {
        List<String> texte = new ArrayList<>();
        Map<Throwable, Boolean> besucht = new IdentityHashMap<>();
        sammle(wurzel, besucht, texte);
        return texte;
    }

    private static void sammle(Throwable t, Map<Throwable, Boolean> besucht, List<String> texte) {
        if (t == null || besucht.put(t, Boolean.TRUE) != null) {
            return;
        }
        texte.add(t.getMessage());
        texte.add(t.getLocalizedMessage());
        texte.add(t.toString());
        sammle(t.getCause(), besucht, texte);
        for (Throwable s : t.getSuppressed()) {
            sammle(s, besucht, texte);
        }
    }

    /** Der erste Marker-Bestandteil in einem Text der Ausnahme (Meldung, cause-Kette, suppressed, toString). */
    static Optional<String> markerInAusnahme(Throwable t) {
        for (String text : alleTexte(t)) {
            Optional<String> m = findeMarker(text);
            if (m.isPresent()) {
                return m;
            }
        }
        return Optional.empty();
    }

    private static void assertKlartextfrei(String ort, Throwable t) {
        Optional<String> m = markerInAusnahme(t);
        assertTrue(m.isEmpty(), ort + ": Bestandteil des Markers in Meldung, Ursache oder toString(): " + m.orElse(""));
    }

    private static void assertToStringKlartextfrei(String ort, Object objekt) {
        Optional<String> m = findeMarker(String.valueOf(objekt));
        assertTrue(m.isEmpty(), ort + ": Bestandteil des Markers in toString(): " + m.orElse(""));
    }

    // ------------------------------------------------------------------ Umleitung von System.out / System.err

    private record Ausgabe(String out, String err) {
    }

    /** Führt die Aktion mit umgeleiteter Standardausgabe und -fehlerausgabe aus; die Umleitung endet in finally. */
    private static Ausgabe erfasse(Runnable aktion) {
        PrintStream altOut = System.out;
        PrintStream altErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            aktion.run();
        } finally {
            System.setOut(altOut);
            System.setErr(altErr);
        }
        return new Ausgabe(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static void assertAusgabeKlartextfrei(String ort, Ausgabe a) {
        assertTrue(findeMarker(a.out()).isEmpty(), ort + ": Marker auf der Standardausgabe");
        assertTrue(findeMarker(a.err()).isEmpty(), ort + ": Marker auf der Fehlerausgabe");
    }

    // ------------------------------------------------------------------ Testobjekte

    private static Finding pfadFund() {
        return new Finding(FindingType.PFAD, MARKER_PFAD, 0, MARKER_PFAD.length(), 1.0, "pfad-laufwerk");
    }

    private static Finding mailFund() {
        return new Finding(FindingType.EMAIL, MARKER_MAIL, 0, MARKER_MAIL.length(), 0.95, "email");
    }

    private static Finding fundIm(String text, FindingType typ, int start, int ende) {
        return new Finding(typ, text.substring(start, ende), start, ende, 0.9, "test");
    }

    private static PlaceholderMapping mappingMitMarker() {
        Map<String, String> m = new HashMap<>();
        m.put("[[PFAD_1]]", MARKER_PFAD);
        m.put("[[EMAIL_2]]", MARKER_MAIL);
        return PlaceholderMapping.of(m);
    }

    private static Arguments fall(String name, Class<? extends Throwable> erwartet, Executable aktion) {
        return Arguments.of(name, erwartet, aktion);
    }

    /** Eine Fundliste, die der Pseudonymisierer mit einem bestimmten Grund ablehnt. */
    private record Szenario(String name, Reason grund, int index, Executable aktion) {
    }

    /** Je ein Szenario je Wert von {@link Reason}, mit Marker in Text und Funden. */
    private static List<Szenario> ablehnungen() {
        String ueber = MARKER_PFAD;
        String paarText = MARKER_PFAD + "\uD83D\uDE00" + MARKER_MAIL;
        int zweiterStart = MARKER_PFAD.length() + 1;
        String gleich = "D" + MARKER_PFAD.substring(1);
        return List.of(
            new Szenario("P03 Pseudonymizer.pseudonymize NULL_ELEMENT", Reason.NULL_ELEMENT, 1,
                () -> PSEUDONYMIZER.pseudonymize(MARKER_PFAD, Arrays.asList(pfadFund(), null))),
            new Szenario("P04 Pseudonymizer.pseudonymize OUT_OF_RANGE", Reason.OUT_OF_RANGE, 0,
                () -> PSEUDONYMIZER.pseudonymize("x", List.of(pfadFund()))),
            new Szenario("P05 Pseudonymizer.pseudonymize VALUE_MISMATCH", Reason.VALUE_MISMATCH, 0,
                () -> PSEUDONYMIZER.pseudonymize(gleich, List.of(pfadFund()))),
            new Szenario("P06 Pseudonymizer.pseudonymize SPLITS_SURROGATE_PAIR", Reason.SPLITS_SURROGATE_PAIR, 0,
                () -> PSEUDONYMIZER.pseudonymize(paarText, List.of(fundIm(paarText, FindingType.PFAD, zweiterStart,
                    paarText.length())))),
            new Szenario("P07 Pseudonymizer.pseudonymize OVERLAP (teilweise)", Reason.OVERLAP, 1,
                () -> PSEUDONYMIZER.pseudonymize(ueber, List.of(fundIm(ueber, FindingType.PFAD, 0, 10),
                    fundIm(ueber, FindingType.EMAIL, 5, ueber.length())))),
            new Szenario("P08 Pseudonymizer.pseudonymize OVERLAP (gleicher Bereich)", Reason.OVERLAP, 1,
                () -> PSEUDONYMIZER.pseudonymize(MARKER_PFAD, List.of(pfadFund(), pfadFund()))));
    }

    // ------------------------------------------------------------------ Fehlerpfade

    private static Recognizer erkennerMitAusnahme(RuntimeException e) {
        return text -> {
            throw e;
        };
    }

    private static RuntimeException ausnahmeMitMarker() {
        RuntimeException ursache = new IllegalStateException(MARKER_MAIL, new RuntimeException(MARKER_PFAD));
        RuntimeException e = new RuntimeException(MARKER_PFAD, ursache);
        e.addSuppressed(new IllegalArgumentException(MARKER_MAIL));
        return e;
    }

    /** Alle Fehlerpfade der öffentlichen API (AK-32, AK-41, AK-42, AK-47 und die Konstruktoren der Modellklassen). */
    static Stream<Arguments> fehlerpfade() {
        int n = MARKER_PFAD.length();
        Map<String, String> nullSchluessel = new HashMap<>();
        nullSchluessel.put(null, MARKER_PFAD);
        Map<String, String> nullWert = new HashMap<>();
        nullWert.put("[[PFAD_1]]", null);
        String scanText = "abc " + MARKER_MAIL;

        Stream<Arguments> modell = Stream.of(
            fall("F01 Finding type=null", NullPointerException.class,
                () -> new Finding(null, MARKER_PFAD, 0, n, 1.0, "pfad")),
            fall("F02 Finding originalValue=null", NullPointerException.class,
                () -> new Finding(FindingType.PFAD, null, 0, n, 1.0, "pfad")),
            fall("F03 Finding source=null", NullPointerException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.0, null)),
            fall("F04 Finding source=Marker-E-Mail", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.0, MARKER_MAIL)),
            fall("F05 Finding source mit Grossbuchstaben", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.0, "Marker")),
            fall("F06 Finding source leer", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.0, "")),
            fall("F07 Finding source zu lang (Marker-Kleinbuchstaben)", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.0, "marker-mustermann-" + "x".repeat(20))),
            fall("F08 Finding start negativ", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, -1, n - 1, 1.0, "pfad")),
            fall("F09 Finding end gleich start", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 3, 3, 1.0, "pfad")),
            fall("F10 Finding end kleiner start", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 5, 2, 1.0, "pfad")),
            fall("F11 Finding Laenge passt nicht zu start/end", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n + 1, 1.0, "pfad")),
            fall("F12 Finding confidence NaN", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, Double.NaN, "pfad")),
            fall("F13 Finding confidence ueber 1", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, 1.5, "pfad")),
            fall("F14 Finding confidence negativ", IllegalArgumentException.class,
                () -> new Finding(FindingType.PFAD, MARKER_PFAD, 0, n, -0.1, "pfad")),
            fall("F15 Finding.manual type=null", NullPointerException.class,
                () -> Finding.manual(null, MARKER_PFAD, 0, n)),
            fall("F16 Finding.manual end gleich start", IllegalArgumentException.class,
                () -> Finding.manual(FindingType.PFAD, MARKER_PFAD, 4, 4)),
            fall("K01 ConfidenceLevel.of NaN", IllegalArgumentException.class,
                () -> ConfidenceLevel.of(Double.NaN)),
            fall("K02 ConfidenceLevel.of ueber 1", IllegalArgumentException.class,
                () -> ConfidenceLevel.of(1.0001)),
            fall("K03 ConfidenceLevel.of negativ", IllegalArgumentException.class,
                () -> ConfidenceLevel.of(-0.0001)),
            fall("T01 FindingType name=null", NullPointerException.class, () -> new FindingType(null)),
            fall("T02 FindingType name=Marker-E-Mail", IllegalArgumentException.class,
                () -> new FindingType(MARKER_MAIL)),
            fall("T03 FindingType name=Marker-Pfad", IllegalArgumentException.class,
                () -> new FindingType(MARKER_PFAD)),
            fall("T04 FindingType name mit Kleinbuchstaben", IllegalArgumentException.class,
                () -> new FindingType("Marker")),
            fall("T05 FindingType name leer", IllegalArgumentException.class, () -> new FindingType("")),
            fall("T06 FindingType name mit Unterstrich", IllegalArgumentException.class,
                () -> new FindingType("MARKER_PFAD")),
            fall("T07 FindingType name zu lang", IllegalArgumentException.class,
                () -> new FindingType("MARKER".repeat(6))),
            fall("T08 FindingType name beginnt mit Ziffer", IllegalArgumentException.class,
                () -> new FindingType("1MARKER")));

        Stream<Arguments> pseudonymisierung = Stream.concat(Stream.of(
            fall("P01 Pseudonymizer text=null", NullPointerException.class,
                () -> PSEUDONYMIZER.pseudonymize(null, List.of(pfadFund()))),
            fall("P02 Pseudonymizer findings=null", NullPointerException.class,
                () -> PSEUDONYMIZER.pseudonymize(TEXT, null)),
            fall("P09 InvalidFindingException reason=null", NullPointerException.class,
                () -> new InvalidFindingException(null, 0))),
            ablehnungen().stream().map(s -> fall(s.name(), InvalidFindingException.class, s.aktion())));

        Stream<Arguments> rueck = Stream.of(
            fall("R01 Reidentifier.restore text=null", NullPointerException.class,
                () -> REIDENTIFIER.restore(null, mappingMitMarker())),
            fall("R02 Reidentifier.restore mapping=null", NullPointerException.class,
                () -> REIDENTIFIER.restore(TEXT, null)),
            fall("M01 PlaceholderMapping.of map=null", NullPointerException.class,
                () -> PlaceholderMapping.of(null)),
            fall("M02 PlaceholderMapping.of Schluessel=null", NullPointerException.class,
                () -> PlaceholderMapping.of(nullSchluessel)),
            fall("M03 PlaceholderMapping.of Wert=null", NullPointerException.class,
                () -> PlaceholderMapping.of(nullWert)),
            fall("M04 PlaceholderMapping.of Schluessel=Marker-Pfad", IllegalArgumentException.class,
                () -> PlaceholderMapping.of(Map.of(MARKER_PFAD, MARKER_PFAD))),
            fall("M05 PlaceholderMapping.of Schluessel=Marker-E-Mail", IllegalArgumentException.class,
                () -> PlaceholderMapping.of(Map.of(MARKER_MAIL, MARKER_MAIL))),
            fall("M06 PlaceholderMapping.of Nummer mit fuehrender Null", IllegalArgumentException.class,
                () -> PlaceholderMapping.of(Map.of("[[PFAD_01]]", MARKER_PFAD))),
            fall("M07 PlaceholderMapping.of Typ in Kleinbuchstaben", IllegalArgumentException.class,
                () -> PlaceholderMapping.of(Map.of("[[pfad_1]]", MARKER_PFAD))),
            fall("M08 PlaceholderMapping.resolve null", NullPointerException.class,
                () -> mappingMitMarker().resolve(null)),
            fall("S01 PlaceholderSyntax.format type=null", NullPointerException.class,
                () -> PlaceholderSyntax.format(null, 1)),
            fall("S02 PlaceholderSyntax.format n=0", IllegalArgumentException.class,
                () -> PlaceholderSyntax.format(FindingType.PFAD, 0)),
            fall("S03 PlaceholderSyntax.format n negativ", IllegalArgumentException.class,
                () -> PlaceholderSyntax.format(FindingType.PFAD, -7)),
            fall("S04 PlaceholderSyntax.tokenize text=null", NullPointerException.class,
                () -> PlaceholderSyntax.tokenize(null, 0, 0, Set.of())),
            fall("S05 PlaceholderSyntax.tokenize knownTypes=null", NullPointerException.class,
                () -> PlaceholderSyntax.tokenize(MARKER_PFAD, 0, 5, null)),
            fall("S06 PlaceholderSyntax.tokenize from negativ", IndexOutOfBoundsException.class,
                () -> PlaceholderSyntax.tokenize(MARKER_PFAD, -1, 3, Set.of())),
            fall("S07 PlaceholderSyntax.tokenize to hinter Textende", IndexOutOfBoundsException.class,
                () -> PlaceholderSyntax.tokenize(MARKER_PFAD, 0, n + 5, Set.of())),
            fall("S08 PlaceholderSyntax.tokenize from hinter to", IndexOutOfBoundsException.class,
                () -> PlaceholderSyntax.tokenize(MARKER_PFAD, 5, 3, Set.of())),
            fall("S09 Token start negativ", IllegalArgumentException.class,
                () -> new PlaceholderSyntax.Token(-1, 3, PlaceholderSyntax.Token.Kind.PLATZHALTER, "[[PFAD_1]]")),
            fall("S10 Token end gleich start", IllegalArgumentException.class,
                () -> new PlaceholderSyntax.Token(3, 3, PlaceholderSyntax.Token.Kind.PLATZHALTER, "[[PFAD_1]]")),
            fall("S11 Token kind=null", NullPointerException.class,
                () -> new PlaceholderSyntax.Token(0, 5, null, "[[PFAD_1]]")),
            fall("S12 Token Platzhalter ohne canonical", IllegalArgumentException.class,
                () -> new PlaceholderSyntax.Token(0, 5, PlaceholderSyntax.Token.Kind.PLATZHALTER, null)),
            fall("S13 Token beschaedigt mit canonical=Marker-Pfad", IllegalArgumentException.class,
                () -> new PlaceholderSyntax.Token(0, 5, PlaceholderSyntax.Token.Kind.BESCHAEDIGT, MARKER_PFAD)),
            fall("U01 UnresolvedPlaceholder token=null", NullPointerException.class,
                () -> new UnresolvedPlaceholder(null, 0, UnresolvedPlaceholder.Reason.UNBEKANNT)),
            fall("U02 UnresolvedPlaceholder reason=null", NullPointerException.class,
                () -> new UnresolvedPlaceholder(MARKER_PFAD, 0, null)),
            fall("U03 UnresolvedPlaceholder position negativ", IllegalArgumentException.class,
                () -> new UnresolvedPlaceholder(MARKER_PFAD, -1, UnresolvedPlaceholder.Reason.BESCHAEDIGT)),
            fall("E01 PseudonymizationResult text=null", NullPointerException.class,
                () -> new PseudonymizationResult(null, mappingMitMarker())),
            fall("E02 PseudonymizationResult mapping=null", NullPointerException.class,
                () -> new PseudonymizationResult(MARKER_PFAD, null)),
            fall("E03 ReidentificationResult text=null", NullPointerException.class,
                () -> new ReidentificationResult(null, List.of())),
            fall("E04 ReidentificationResult unresolved=null", NullPointerException.class,
                () -> new ReidentificationResult(MARKER_PFAD, null)),
            fall("E05 ReidentificationResult unresolved enthaelt null", NullPointerException.class,
                () -> new ReidentificationResult(MARKER_PFAD, Arrays.asList((UnresolvedPlaceholder) null))));

        Stream<Arguments> scanner = Stream.of(
            fall("X01 TextScanner recognizers=null", NullPointerException.class,
                () -> new TextScanner(null)),
            fall("X02 TextScanner enthaelt null", NullPointerException.class,
                () -> new TextScanner(Arrays.asList((Recognizer) null))),
            fall("X03 TextScanner.scan text=null", NullPointerException.class,
                () -> TextScanner.standard().scan(null)),
            fall("X04 Erkenner liefert null", NullPointerException.class,
                () -> new TextScanner(List.of(text -> null)).scan(scanText)),
            fall("X05 Erkenner liefert null-Element", NullPointerException.class,
                () -> new TextScanner(List.of(text -> Arrays.asList((Finding) null))).scan(scanText)),
            fall("X06 Erkenner liefert Fund an falscher Stelle", IllegalStateException.class,
                () -> new TextScanner(List.of(text -> List.of(mailFund()))).scan(scanText)),
            fall("X07 Erkenner liefert Fund hinter dem Textende", IllegalStateException.class,
                () -> new TextScanner(List.of(text -> List.of(new Finding(FindingType.EMAIL, MARKER_MAIL,
                    scanText.length() - 3, scanText.length() - 3 + MARKER_MAIL.length(), 0.9, "test"))))
                    .scan(scanText)),
            fall("X08 Erkenner wirft RuntimeException mit Marker in Meldung, cause und suppressed",
                IllegalStateException.class,
                () -> new TextScanner(List.of(erkennerMitAusnahme(ausnahmeMitMarker()))).scan(scanText)),
            fall("X09 Erkenner wirft NullPointerException mit Marker", IllegalStateException.class,
                () -> new TextScanner(List.of(erkennerMitAusnahme(new NullPointerException(MARKER_PFAD))))
                    .scan(scanText)),
            fall("X10 zweiter Erkenner wirft IllegalArgumentException mit Marker", IllegalStateException.class,
                () -> new TextScanner(List.of(text -> List.of(), erkennerMitAusnahme(
                    new IllegalArgumentException(MARKER_MAIL, new RuntimeException(MARKER_PFAD))))).scan(scanText)),
            fall("L01 LanrRecognizer.detect text=null", NullPointerException.class,
                () -> new LanrRecognizer().detect(null)),
            fall("L02 IkRecognizer.detect text=null", NullPointerException.class,
                () -> new IkRecognizer().detect(null)),
            fall("C01 ConflictResolver.resolve text=null", NullPointerException.class,
                () -> ConflictResolver.resolve(null, List.of(pfadFund()))),
            fall("C02 ConflictResolver.resolve candidates=null", NullPointerException.class,
                () -> ConflictResolver.resolve(TEXT, null)),
            fall("C03 ConflictResolver.resolve Kandidat=null", NullPointerException.class,
                () -> ConflictResolver.resolve(MARKER_PFAD, Arrays.asList(pfadFund(), null))),
            fall("C04 ConflictResolver.resolve unpassender Kandidat", IllegalArgumentException.class,
                () -> ConflictResolver.resolve("D" + MARKER_PFAD.substring(1), List.of(pfadFund()))),
            fall("C05 ConflictResolver.resolve Kandidat hinter dem Textende", IllegalArgumentException.class,
                () -> ConflictResolver.resolve("x", List.of(mailFund()))),
            fall("C06 ConflictResolver.resolve zweiter Kandidat unpassend", IllegalArgumentException.class,
                () -> ConflictResolver.resolve(MARKER_PFAD,
                    List.of(pfadFund(), new Finding(FindingType.EMAIL, MARKER_MAIL, 2, 2 + MARKER_MAIL.length(),
                        0.9, "test")))));

        return Stream.of(modell, pseudonymisierung, rueck, scanner).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fehlerpfade")
    void fehlerpfad_entstehtUndMeldungCauseSuppressedUndAusgabeSindKlartextfrei(String name,
            Class<? extends Throwable> erwartet, Executable aktion) {
        Throwable[] gefangen = new Throwable[1];
        Ausgabe ausgabe = erfasse(() -> gefangen[0] = assertThrows(erwartet, aktion, name + ": Fehler entsteht nicht"));
        assertNotNull(gefangen[0], name);
        assertKlartextfrei(name, gefangen[0]);
        assertAusgabeKlartextfrei(name, ausgabe);
    }

    @Test
    void fehlerpfade_sindZahlreichUndNamenSindEindeutig() {
        List<String> namen = fehlerpfade().map(a -> (String) a.get()[0]).toList();
        assertEquals(namen.size(), Set.copyOf(namen).size(), "Namen nicht eindeutig");
        assertTrue(namen.size() >= 80, "Fehlerpfade: " + namen.size());
    }

    // ------------------------------------------------------------------ Pseudonymisierung: Gründe (AK-32)

    @Test
    void ak32_jederGrundDerAblehnungWirdErzeugt_mitIndexUndFormatOhneKlartext() {
        Set<Reason> erzeugt = EnumSet.noneOf(Reason.class);
        for (Szenario s : ablehnungen()) {
            InvalidFindingException e = assertThrows(InvalidFindingException.class, s.aktion(), s.name());
            assertEquals(s.grund(), e.reason(), s.name());
            assertEquals(s.index(), e.index(), s.name());
            assertEquals(UE_MELDUNG + s.index() + ": " + s.grund(), e.getMessage(), s.name());
            assertNull(e.getCause(), s.name());
            assertEquals(0, e.getSuppressed().length, s.name());
            assertKlartextfrei(s.name(), e);
            erzeugt.add(e.reason());
        }
        assertEquals(EnumSet.allOf(Reason.class), erzeugt, "nicht jeder Grund erzeugt");
    }

    @Test
    void ak32_ungueltigeFundlisteLiefertKeinErgebnisUndGibtNichtsAus() {
        // Der Fehler entsteht vor jeder Ausgabe: Es gibt kein Ergebnis, nur die Ausnahme.
        Ausgabe a = erfasse(() -> {
            for (Szenario s : ablehnungen()) {
                assertThrows(InvalidFindingException.class, s.aktion(), s.name());
            }
        });
        assertAusgabeKlartextfrei("Gruende", a);
    }

    // ------------------------------------------------------------------ Rückübersetzung (AK-41, AK-42)

    @Test
    void ak41_ak42_unbekannteUndBeschaedigtePlatzhalter_werdenGemeldetOhneKlartext() {
        List<Finding> funde = TextScanner.standard().scan(TEXT);
        PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(TEXT, funde);
        String antwort = pseudo.text() + " | [[PFAD_99]] | [[PFAD_1] | [PFAD_1] | [[PFAD_]] |";
        ReidentificationResult[] ergebnis = new ReidentificationResult[1];
        Ausgabe ausgabe = erfasse(() -> ergebnis[0] = REIDENTIFIER.restore(antwort, pseudo.mapping()));
        ReidentificationResult r = ergebnis[0];

        List<String> tokens = r.unresolved().stream().map(UnresolvedPlaceholder::token).toList();
        assertEquals(List.of("[[PFAD_99]]", "[[PFAD_1]", "[PFAD_1]", "[[PFAD_]]"), tokens);
        assertEquals(List.of(UnresolvedPlaceholder.Reason.UNBEKANNT, UnresolvedPlaceholder.Reason.BESCHAEDIGT,
                UnresolvedPlaceholder.Reason.BESCHAEDIGT, UnresolvedPlaceholder.Reason.BESCHAEDIGT),
                r.unresolved().stream().map(UnresolvedPlaceholder::reason).toList());
        assertTrue(r.text().contains(" | [[PFAD_99]] | [[PFAD_1] | [PFAD_1] | [[PFAD_]] |"),
                "bleibt unveraendert stehen");
        for (UnresolvedPlaceholder u : r.unresolved()) {
            assertToStringKlartextfrei("unresolved " + u.token(), u.token());
            assertToStringKlartextfrei("unresolved.toString " + u.token(), u);
        }
        assertToStringKlartextfrei("unresolved-Liste", r.unresolved());
        assertToStringKlartextfrei("ReidentificationResult.toString", r);
        assertAusgabeKlartextfrei("restore", ausgabe);
        // Das Ergebnis selbst ist wie gefordert wieder der Klartext (Rückübersetzung), nicht aber seine Beschreibung.
        assertTrue(findeMarker(r.text()).isPresent());
    }

    // ------------------------------------------------------------------ Scanner: Kapselung von Erkennerfehlern

    @Test
    void scanner_kapseltErkennerfehlerOhneMeldungUrsacheUndUnterdrueckte() {
        RuntimeException quelle = ausnahmeMitMarker();
        TextScanner scanner = new TextScanner(List.of(text -> List.of(), erkennerMitAusnahme(quelle)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> scanner.scan(TEXT));
        assertEquals("Erkenner an Index 1 fehlgeschlagen: java.lang.RuntimeException", e.getMessage());
        assertNull(e.getCause());
        assertEquals(0, e.getSuppressed().length);
        assertKlartextfrei("scanner", e);
        // Die Ausgangsausnahme enthält den Marker; die Kapsel nicht.
        assertTrue(markerInAusnahme(quelle).isPresent());
    }

    @Test
    void scanner_fundAnFalscherStelle_meldungOhneFundwert() {
        TextScanner scanner = new TextScanner(List.of(text -> List.of(mailFund())));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> scanner.scan("abc " + MARKER_MAIL));
        assertEquals("Erkenner lieferte einen Fund, der nicht zum Text passt", e.getMessage());
        assertKlartextfrei("scanner", e);
    }

    @Test
    void resolver_unpassenderKandidat_meldungNenntNurDenListenindex() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConflictResolver
                .resolve(MARKER_PFAD, List.of(pfadFund(), new Finding(FindingType.EMAIL, MARKER_MAIL, 2,
                    2 + MARKER_MAIL.length(), 0.9, "test"))));
        assertEquals("Kandidat an Listenindex 1 passt nicht zum Text", e.getMessage());
        assertNull(e.getCause());
        assertKlartextfrei("resolver", e);
    }

    // ------------------------------------------------------------------ toString aller Ergebnis- und Modellobjekte

    @Test
    void ak50_toStringAllerErgebnisUndModellobjekte_enthaeltKeinenMarker_undAusgabeBleibtLeerVomMarker() {
        Ausgabe ausgabe = erfasse(() -> {
            List<Finding> funde = TextScanner.standard().scan(TEXT);
            assertTrue(funde.size() >= 2, "Marker wird erkannt: " + funde.size());
            for (Finding f : funde) {
                assertToStringKlartextfrei("Finding.toString", f);
            }
            assertToStringKlartextfrei("Finding.manual",
                    Finding.manual(FindingType.PFAD, MARKER_PFAD, 0, MARKER_PFAD.length()));
            assertToStringKlartextfrei("Finding Email", mailFund());
            assertToStringKlartextfrei("FindingType", FindingType.PFAD);
            for (ConfidenceLevel l : ConfidenceLevel.values()) {
                assertToStringKlartextfrei("ConfidenceLevel", l);
            }

            PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(TEXT, funde);
            assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text");
            assertToStringKlartextfrei("PseudonymizationResult", pseudo);
            assertToStringKlartextfrei("PlaceholderMapping", pseudo.mapping());
            assertToStringKlartextfrei("placeholders()", pseudo.mapping().placeholders());
            assertToStringKlartextfrei("typeNames()", pseudo.mapping().typeNames());
            assertToStringKlartextfrei("leere Zuordnung", PlaceholderMapping.empty());

            // platzhalterähnlicher Originaltext (Literalschutz, Typ TEXT) neben dem Marker
            String literal = "Vorher [[PFAD_7]] " + MARKER_PFAD;
            PseudonymizationResult mitLiteral = PSEUDONYMIZER.pseudonymize(literal, TextScanner.standard()
                    .scan(literal));
            assertToStringKlartextfrei("PseudonymizationResult Literal", mitLiteral);
            assertToStringKlartextfrei("PlaceholderMapping Literal", mitLiteral.mapping());

            ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
            assertEquals(TEXT, zurueck.text());
            assertToStringKlartextfrei("ReidentificationResult", zurueck);
            assertToStringKlartextfrei("unresolved leer", zurueck.unresolved());

            String mitRest = pseudo.text() + " [[PFAD_9]] [[PFAD_";
            List<PlaceholderSyntax.Token> tokens = PlaceholderSyntax.tokenize(mitRest, 0, mitRest.length(),
                    pseudo.mapping().typeNames());
            assertFalse(tokens.isEmpty());
            for (PlaceholderSyntax.Token t : tokens) {
                assertToStringKlartextfrei("Token", t);
            }
            for (Reason r : Reason.values()) {
                assertKlartextfrei("InvalidFindingException " + r, new InvalidFindingException(r, 3));
            }
            for (UnresolvedPlaceholder.Reason r : UnresolvedPlaceholder.Reason.values()) {
                assertToStringKlartextfrei("UnresolvedPlaceholder " + r,
                        new UnresolvedPlaceholder("[[PFAD_9]]", 4, r));
            }
        });
        assertAusgabeKlartextfrei("toString-Test", ausgabe);
    }

    @Test
    void ak50_toStringEinesZusammengelegtenFundes_enthaeltKeinenMarker() {
        String text = MARKER_PFAD + " " + MARKER_MAIL;
        Finding a = fundIm(text, FindingType.PFAD, 0, 20);
        Finding b = fundIm(text, FindingType.EMAIL, 10, text.length());
        List<Finding> ergebnis = ConflictResolver.resolve(text, List.of(a, b));
        assertEquals(1, ergebnis.size());
        Finding zusammen = ergebnis.get(0);
        assertEquals(Finding.SOURCE_MERGED, zusammen.source());
        assertEquals(text, zusammen.originalValue());
        assertToStringKlartextfrei("zusammengelegter Fund", zusammen);
        assertTrue(zusammen.toString().contains("source=zusammengelegt"), zusammen.toString());

        // auch aus dem Scan mit zwei überlappenden Kandidaten zweier Erkenner
        TextScanner scanner = new TextScanner(List.of(t -> List.of(a), t -> List.of(b)));
        List<Finding> gescannt = scanner.scan(text);
        assertEquals(1, gescannt.size());
        assertEquals(Finding.SOURCE_MERGED, gescannt.get(0).source());
        assertToStringKlartextfrei("zusammengelegter Fund (Scan)", gescannt.get(0));
        assertToStringKlartextfrei("Liste der Funde", gescannt);
    }

    // ------------------------------------------------------------------ KV-Nummern (AK-24)

    /** Die drei Quellen je Typ (Pruefziffer gueltig, ungueltig, Laenge abweichend) als Funde der echten Erkenner. */
    private static List<Finding> kvFundeAllerQuellen() {
        String lanr = LANR_MARKER;
        String lanrUngueltig = lanr.substring(0, 6) + (char) ('0' + (lanr.charAt(6) - '0' + 1) % 10)
                + lanr.substring(7);
        String lanrLaenge = lanr.substring(0, 7);
        String ik = IK_MARKER;
        String ikUngueltig = ik.substring(0, 8) + (char) ('0' + (ik.charAt(8) - '0' + 1) % 10);
        String ikLaenge = ik.substring(0, 7);
        List<Finding> funde = new ArrayList<>();
        for (String n : List.of(lanr, lanrUngueltig, lanrLaenge)) {
            funde.addAll(new LanrRecognizer().detect("LANR " + n));
        }
        for (String n : List.of(ik, ikUngueltig, ikLaenge)) {
            funde.addAll(new IkRecognizer().detect("IK " + n));
        }
        return funde;
    }

    @Test
    void ak24_kvFunde_alleDreiQuellenJeTyp_toStringOhneMarkerUndQuellenOhneZiffer() {
        Ausgabe ausgabe = erfasse(() -> {
            List<Finding> funde = kvFundeAllerQuellen();
            assertEquals(6, funde.size(), "je Typ drei Funde");
            assertEquals(Set.of(LanrRecognizer.SOURCE, LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
                    LanrRecognizer.SOURCE_LAENGE_ABWEICHEND, IkRecognizer.SOURCE,
                    IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG, IkRecognizer.SOURCE_LAENGE_ABWEICHEND),
                    funde.stream().map(Finding::source).collect(java.util.stream.Collectors.toSet()),
                    "alle sechs Quellen kommen vor");
            assertEquals(3L, funde.stream().filter(f -> FindingType.LANR.equals(f.type())).count(), "Typ LANR");
            assertEquals(3L, funde.stream().filter(f -> FindingType.IK.equals(f.type())).count(), "Typ IK");
            for (Finding f : funde) {
                assertToStringKlartextfrei("KV-Finding.toString " + f.source(), f);
                assertToStringKlartextfrei("KV-Finding.source " + f.source(), f.source());
                assertToStringKlartextfrei("KV-Finding.type " + f.source(), f.type());
                for (int i = 0; i < f.source().length(); i++) {
                    assertFalse(Character.isDigit(f.source().charAt(i)), "Quelle enthaelt eine Ziffer: " + f.source());
                }
                assertTrue(f.toString().contains("source=" + f.source()), "toString nennt die Quelle");
            }
            assertToStringKlartextfrei("Liste der KV-Funde", funde);
            // Pseudonymisierung und Rueckuebersetzung der Funde einzeln: Ergebnisobjekte ohne Marker
            for (Finding f : funde) {
                String text = "Z " + f.originalValue() + " E";
                Finding im = new Finding(f.type(), f.originalValue(), 2, 2 + f.originalValue().length(),
                        f.confidence(), f.source());
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, List.of(im));
                assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text");
                assertToStringKlartextfrei("PseudonymizationResult KV", pseudo);
                assertToStringKlartextfrei("PlaceholderMapping KV", pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertEquals(text, zurueck.text());
                assertToStringKlartextfrei("ReidentificationResult KV", zurueck);
            }
        });
        assertAusgabeKlartextfrei("KV-toString-Test", ausgabe);
    }

    @Test
    void ak24_kvFunde_manuellGebildeteQuellenSindZifferfreiUndToStringOhneMarker() {
        for (String quelle : List.of(LanrRecognizer.SOURCE, LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
                LanrRecognizer.SOURCE_LAENGE_ABWEICHEND, IkRecognizer.SOURCE, IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
                IkRecognizer.SOURCE_LAENGE_ABWEICHEND)) {
            FindingType typ = quelle.startsWith("lanr") ? FindingType.LANR : FindingType.IK;
            String wert = typ.equals(FindingType.LANR) ? LANR_MARKER : IK_MARKER;
            Finding f = new Finding(typ, wert, 0, wert.length(), 0.5, quelle);
            assertToStringKlartextfrei("KV manuell " + quelle, f);
            assertTrue(quelle.chars().noneMatch(Character::isDigit), "Quelle enthaelt eine Ziffer: " + quelle);
            assertTrue(findeMarker(f.originalValue()).isPresent(), "Suchfunktion findet den KV-Marker");
        }
    }

    @Test
    void ak24_kvMarkerImText_werdenPseudonymisiertUndTauchenInKeinerAusgabeAuf() {
        List<Finding> funde = TextScanner.standard().scan(TEXT);
        assertEquals(4, funde.size(), "Pfad, E-Mail, LANR, IK");
        assertEquals(1L, funde.stream().filter(f -> FindingType.LANR.equals(f.type())).count(), "LANR im Marker-Text");
        assertEquals(1L, funde.stream().filter(f -> FindingType.IK.equals(f.type())).count(), "IK im Marker-Text");
        PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(TEXT, funde);
        assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text");
        assertTrue(pseudo.text().contains("LANR [[LANR_3]], IK [[IK_4]]"), "Platzhalter der KV-Funde");
        assertEquals(TEXT, REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()).text());
    }

    /** Neue Formen aus kv-kontext-luecken (AK-26): gesperrtes Kontextwort, {@code I K}, Komma, Semikolon, T-7a-Form. */
    @Test
    void ak26_kvKontextLuecken_fundeAusGesperrtemKontextwortIKUndKommaSindKlartextfrei() {
        String[] texte = {"L A N R " + LANR_MARKER, "I K " + IK_MARKER, LANR_MARKER + ", LANR", IK_MARKER + ", IK",
            "I.K.: " + IK_MARKER + ".", "L-A-N-R " + LANR_MARKER, LANR_MARKER + "; IK",
            LANR_MARKER + ", L A N R", "IK: Praxis I K Nr " + IK_MARKER};
        Ausgabe ausgabe = erfasse(() -> {
            for (int i = 0; i < texte.length; i++) {
                String text = texte[i];
                List<Finding> funde = TextScanner.standard().scan(text);
                assertEquals(1, funde.size(), "genau ein Fund in Text " + i);
                Finding f = funde.get(0);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                assertToStringKlartextfrei("KV-Finding.toString Text " + i, f);
                assertToStringKlartextfrei("Liste der Funde Text " + i, funde);
                assertToStringKlartextfrei("Finding.source Text " + i, f.source());
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
                assertToStringKlartextfrei("PseudonymizationResult Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Text " + i, zurueck);
            }
        });
        assertAusgabeKlartextfrei("KV-Kontextluecken-Test", ausgabe);
    }

    /**
     * kv-trenner-und-umbruch Teil 1 (AK-28): Funde hinter {@code CR UZ LF} (gesperrtes und normales Kontextwort, beide
     * Arten, auch mit zwei unsichtbaren Zeichen). Die Suchfunktion findet den Marker im Eingabetext; im Fund, in der
     * Fundliste, in {@code source}, im pseudonymisierten Text, im Mapping und im Ergebnis der Rueckuebersetzung
     * (jeweils {@code toString()}) und auf Standardausgabe und Fehlerausgabe steht kein Marker.
     */
    @Test
    void ak28_kvUmbruch_fundeHinterCrUnsichtbaresZeichenLfSindKlartextfrei() {
        String umbruch = "\r" + ZWSP + "\n";
        String[] texte = {"L A N R:" + umbruch + LANR_MARKER, "LANR:" + umbruch + LANR_MARKER,
            "IK:" + umbruch + IK_MARKER, "I K N R:" + umbruch + IK_MARKER,
            "L A N R:\r" + ZWSP + ZWSP + "\n" + LANR_MARKER};
        Ausgabe ausgabe = erfasse(() -> {
            for (int i = 0; i < texte.length; i++) {
                String text = texte[i];
                List<Finding> funde = TextScanner.standard().scan(text);
                assertEquals(1, funde.size(), "genau ein Fund in Text " + i);
                Finding f = funde.get(0);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                assertToStringKlartextfrei("KV-Finding.toString Umbruch-Text " + i, f);
                assertToStringKlartextfrei("Liste der Funde Umbruch-Text " + i, funde);
                assertToStringKlartextfrei("Finding.source Umbruch-Text " + i, f.source());
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
                assertToStringKlartextfrei("PseudonymizationResult Umbruch-Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Umbruch-Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Umbruch-Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Umbruch-Text " + i, zurueck);
            }
        });
        assertAusgabeKlartextfrei("KV-Umbruch-Test", ausgabe);
    }

    /**
     * kv-trenner-und-umbruch Teil 2 (AK-28): Funde aus gesperrt geschriebenen Kontextwoertern mit erweiterten
     * Trennern (Punkt, mehrere Leerzeichen, Tab, nachgestellt in Klammern und mit Komma). Die Suchfunktion findet den
     * Marker im Eingabetext; im Fund, in der Fundliste, in {@code source}, im pseudonymisierten Text, im Mapping und im
     * Ergebnis der Rueckuebersetzung (jeweils {@code toString()}) und auf Standard- und
     * Fehlerausgabe steht kein Marker.
     */
    @Test
    void ak28_kvTrenner_fundeAusErweitertenTrennernSindKlartextfrei() {
        String[] texte = {"L. A. N. R. " + LANR_MARKER, "I  K  N  R " + IK_MARKER, IK_MARKER + " (I. K. N. R.)",
            "L\tA\tN\tR\t" + LANR_MARKER, LANR_MARKER + ", L  A  N  R", "I. K. N. R.\n" + IK_MARKER};
        Ausgabe ausgabe = erfasse(() -> {
            for (int i = 0; i < texte.length; i++) {
                String text = texte[i];
                List<Finding> funde = TextScanner.standard().scan(text);
                assertEquals(1, funde.size(), "genau ein Fund in Text " + i);
                Finding f = funde.get(0);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                assertToStringKlartextfrei("KV-Finding.toString Trenner-Text " + i, f);
                assertToStringKlartextfrei("Liste der Funde Trenner-Text " + i, funde);
                assertToStringKlartextfrei("Finding.source Trenner-Text " + i, f.source());
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
                assertToStringKlartextfrei("PseudonymizationResult Trenner-Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Trenner-Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Trenner-Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Trenner-Text " + i, zurueck);
            }
        });
        assertAusgabeKlartextfrei("KV-Trenner-Test", ausgabe);
    }

    /**
     * geheimnisse-erkennung T-3 (AK-42): Die Typen in {@code GeheimnisTypen} und die Lueckenregel des Scanners. Ein
     * kuenstlicher Erkenner liefert einen Kandidaten vom Typ PASSWORT ueber Marker-Pfad und Marker-E-Mail. Im Fund,
     * in der Fundliste, im pseudonymisierten Text, im Mapping und im Ergebnis der Rueckuebersetzung, in den
     * Ausnahmen (null als Typ, Fund passt nicht zum Text) und auf Standard- und Fehlerausgabe steht kein Marker.
     */
    @Test
    void geheimnisTypen_lueckenregel_keinMarkerInFundenAusnahmenUndAusgabe() {
        int von = "Passwort: ".length();
        int bis = TEXT_GEHEIMNIS.length() - " Ende".length();
        Finding kandidat = new Finding(GeheimnisTypen.PASSWORT, TEXT_GEHEIMNIS.substring(von, bis), von, bis, 0.95,
                "kuenstlich");
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.add(text -> List.of(kandidat));
        Ausgabe ausgabe = erfasse(() -> {
            List<Finding> funde = new TextScanner(liste).scan(TEXT_GEHEIMNIS);
            assertTrue(findeMarker(TEXT_GEHEIMNIS).isPresent(), "Suchfunktion findet den Marker im Eingabetext");
            assertTrue(funde.size() >= 3, "Pfad, E-Mail und Reststueck");
            assertToStringKlartextfrei("Liste der Funde Luecken", funde);
            for (Finding f : funde) {
                assertToStringKlartextfrei("Finding.toString Luecken", f);
                assertToStringKlartextfrei("Finding.source Luecken", f.source());
                assertToStringKlartextfrei("FindingType Luecken", f.type());
            }
            PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(TEXT_GEHEIMNIS, funde);
            assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text");
            assertToStringKlartextfrei("PseudonymizationResult Luecken", pseudo);
            assertToStringKlartextfrei("PlaceholderMapping Luecken", pseudo.mapping());
            ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
            assertTrue(TEXT_GEHEIMNIS.equals(zurueck.text()), "Roundtrip Luecken");
            assertToStringKlartextfrei("ReidentificationResult Luecken", zurueck);

            NullPointerException npe = assertThrows(NullPointerException.class,
                    () -> GeheimnisTypen.istGeheimnistyp(null));
            assertKlartextfrei("istGeheimnistyp(null)", npe);
            Finding falsch = new Finding(GeheimnisTypen.TOKEN, MARKER_PFAD, 0, MARKER_PFAD.length(), 0.95, "x");
            TextScanner fehlerhaft = new TextScanner(List.of(text -> List.of(falsch)));
            IllegalStateException ise = assertThrows(IllegalStateException.class,
                    () -> fehlerhaft.scan("anderer Text ohne Marker"));
            assertKlartextfrei("Fund passt nicht zum Text", ise);
        });
        assertAusgabeKlartextfrei("Luecken-Test", ausgabe);
    }

    private static final String TEXT_GEHEIMNIS = "Passwort: " + MARKER_PFAD + " und " + MARKER_MAIL + " Ende";

    /** Ein Marker-Geheimnis, das den Wert-Test besteht (Buchstaben, Ziffer, Sonderzeichen) und den Marker traegt. */
    private static final String MARKER_GEHEIMNIS = SynthetischeGeheimnisse.MARKER_GEHEIMNIS;

    /**
     * geheimnisse-erkennung T-4 (AK-42, D-15): Der {@code PasswortRecognizer} und seine Bausteine
     * ({@code KontextRegel}, {@code Werttest}, {@code GeheimnisZeichen}) in allen Zweigen der Regel (K1, Umbruch,
     * Zwischenteil, Anfuehrung, Auszeichnung, Klammern am Ende, zu langer Wert). Im Fund, in der Fundliste, im
     * pseudonymisierten Text, im Mapping, im Ergebnis der Rueckuebersetzung, in der Ausnahme bei {@code null} und auf
     * Standard- und Fehlerausgabe steht kein Marker ausser im Eingabetext und in der Rueckuebersetzung des
     * Originals. Der Erkenner wirft ausser der {@code NullPointerException} mit der Meldung {@code text} nichts.
     */
    @Test
    void passwortErkenner_keinMarkerInFundenAusnahmenUndAusgabe() {
        List<String> texte = List.of("Passwort: " + MARKER_GEHEIMNIS, "pw=" + MARKER_GEHEIMNIS + ".",
                "Passwort:\n" + MARKER_GEHEIMNIS, "Passwort f" + (char) 0xFC + "r svc_x: \"" + MARKER_GEHEIMNIS + "\"",
                "**Passwort:** **" + MARKER_GEHEIMNIS + "**", "Passwort: (" + MARKER_GEHEIMNIS + ")",
                "Passwort: " + MARKER_GEHEIMNIS.repeat(400), "Passwort: " + MARKER_PFAD,
                "Passwort: " + MARKER_MAIL, "x Passwort: " + MARKER_GEHEIMNIS + " Passwort: " + MARKER_GEHEIMNIS);
        Ausgabe ausgabe = erfasse(() -> {
            PasswortRecognizer erkenner = new PasswortRecognizer();
            int gefunden = 0;
            for (int i = 0; i < texte.size(); i++) {
                String text = texte.get(i);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                List<Finding> kandidaten = erkenner.detect(text);
                gefunden += kandidaten.size();
                assertToStringKlartextfrei("Kandidaten " + i, kandidaten);
                for (Finding f : kandidaten) {
                    assertToStringKlartextfrei("Finding.toString Passwort-Kandidat " + i, f);
                    assertToStringKlartextfrei("Finding.source Passwort-Kandidat " + i, f.source());
                    assertToStringKlartextfrei("FindingType Passwort-Kandidat " + i, f.type());
                }
                List<Finding> funde = TextScanner.standard().scan(text);
                assertToStringKlartextfrei("Liste der Funde Passwort-Text " + i, funde);
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertToStringKlartextfrei("PseudonymizationResult Passwort-Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Passwort-Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Passwort-Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Passwort-Text " + i, zurueck);
                if (!kandidaten.isEmpty() && i != 7 && i != 8) {
                    assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
                }
            }
            assertTrue(gefunden >= 8, "der Test trifft die Regel zu selten: " + gefunden);
            NullPointerException npe = assertThrows(NullPointerException.class, () -> erkenner.detect(null));
            assertKlartextfrei("PasswortRecognizer.detect(null)", npe);
            assertEquals("text", npe.getMessage());
            NullPointerException scan = assertThrows(NullPointerException.class,
                    () -> TextScanner.standard().scan(null));
            assertKlartextfrei("TextScanner.scan(null) mit Passwort-Erkenner", scan);
        });
        assertAusgabeKlartextfrei("Passwort-Erkenner-Test", ausgabe);
    }

    /**
     * geheimnisse-erkennung T-6 (AK-42): Der {@code TokenRecognizer} und {@code TokenKontext} in allen Zweigen
     * (Kontextwort mit K1 bis K4, {@code API Key}, {@code Authorization} mit Schema, {@code Bearer} mit und ohne
     * Verbinder). Wie beim Passwort steht kein Marker in Funden, Fundlisten, pseudonymisiertem Text, Mapping,
     * Rueckuebersetzung, Ausnahmen und Ausgaben ausser im Eingabetext und in der Rueckuebersetzung des Originals.
     */
    @Test
    void tokenErkenner_keinMarkerInFundenAusnahmenUndAusgabe() {
        // Pfad und E-Mail gehoeren dem Bestandserkenner; der Token-Kandidat ist dort hoechstens ein Reststueck. Die
        // Marker-Pruefung des pseudonymisierten Texts gilt nur fuer Texte, deren Wert der Token-Erkenner besitzt
        // (wie beim Passwort-Test).
        String tokenPfad = "Token: " + MARKER_PFAD;
        String tokenMail = "Token: " + MARKER_MAIL;
        List<String> texte = List.of("Token: " + MARKER_GEHEIMNIS, "access_token=" + MARKER_GEHEIMNIS + ".",
                "Secret:\n" + MARKER_GEHEIMNIS, "API Key f" + (char) 0xFC + "r svc_x: \"" + MARKER_GEHEIMNIS + "\"",
                "Authorization: Bearer " + MARKER_GEHEIMNIS, "Bearer " + MARKER_GEHEIMNIS,
                "Token ist " + MARKER_GEHEIMNIS, "| Secret | " + MARKER_GEHEIMNIS + " |",
                "<token>" + MARKER_GEHEIMNIS + "</token>", "Token: " + MARKER_GEHEIMNIS.repeat(400),
                tokenPfad, tokenMail,
                "x Token: " + MARKER_GEHEIMNIS + " Secret: " + MARKER_GEHEIMNIS);
        Ausgabe ausgabe = erfasse(() -> {
            TokenRecognizer erkenner = new TokenRecognizer();
            int gefunden = 0;
            for (int i = 0; i < texte.size(); i++) {
                String text = texte.get(i);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                List<Finding> kandidaten = erkenner.detect(text);
                gefunden += kandidaten.size();
                assertToStringKlartextfrei("Token-Kandidaten " + i, kandidaten);
                for (Finding f : kandidaten) {
                    assertToStringKlartextfrei("Finding.toString Token-Kandidat " + i, f);
                    assertToStringKlartextfrei("Finding.source Token-Kandidat " + i, f.source());
                    assertToStringKlartextfrei("FindingType Token-Kandidat " + i, f.type());
                }
                List<Finding> funde = TextScanner.standard().scan(text);
                assertToStringKlartextfrei("Liste der Funde Token-Text " + i, funde);
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertToStringKlartextfrei("PseudonymizationResult Token-Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Token-Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Token-Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Token-Text " + i, zurueck);
                if (!kandidaten.isEmpty() && !text.equals(tokenPfad) && !text.equals(tokenMail)) {
                    assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
                }
            }
            assertTrue(gefunden >= 12, "der Test trifft die Regel zu selten: " + gefunden);
            NullPointerException npe = assertThrows(NullPointerException.class, () -> erkenner.detect(null));
            assertKlartextfrei("TokenRecognizer.detect(null)", npe);
            assertEquals("text", npe.getMessage());
        });
        assertAusgabeKlartextfrei("Token-Erkenner-Test", ausgabe);
    }

    /**
     * geheimnisse-erkennung T-7 (AK-42): Die kontextfreien Formen JWT, PEM-Koerper und Praefix-Token in
     * {@code TokenFormen} und die Vereinigung im {@code TokenRecognizer}. Ihre Werte tragen den Marker; er steht in
     * keinem Fund, keiner Fundliste, keinem pseudonymisierten Text, keinem Mapping und keiner Ausnahme ausser im
     * Eingabetext und in der Rueckuebersetzung des Originals.
     */
    @Test
    void tokenFormen_keinMarkerInFundenAusnahmenUndAusgabe() {
        String jwt = "ey" + "J" + "MarkerKopfAbcd" + "." + "ey" + "J" + "MarkerNutzlastEfgh" + "."
                + "MarkerSignaturIjkl";
        String praefixToken = "gh" + "p_" + "AbcdMarkerEfghIjkl7Mnop";
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("RSA PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("RSA PRIVATE KEY");
        List<String> zeilen = List.of("AbcdMarker" + SynthetischeGeheimnisse.base64Zeile(52),
                SynthetischeGeheimnisse.base64Zeile(64), SynthetischeGeheimnisse.base64Zeile(22) + "==");
        String lf = "\n";
        String crlf = "\r\n";
        String pemLf = kopf + lf + String.join(lf, zeilen) + lf + fuss;
        String pemCrlf = kopf + crlf + String.join(crlf, zeilen) + crlf + fuss;
        List<String> texte = List.of(jwt, "Das Token " + jwt + " ist abgelaufen.", "Authorization: Bearer " + jwt,
                "Token: " + jwt, praefixToken, "Der Wert " + praefixToken + " wurde widerrufen.",
                "Token: " + praefixToken, pemLf, pemCrlf, kopf + lf + String.join(lf, zeilen),
                "Schluessel:" + lf + pemLf + lf + "Danach neu starten.", "x " + jwt + " y " + praefixToken + " z",
                "https://example.invalid/x?t=" + jwt);
        Ausgabe ausgabe = erfasse(() -> {
            TokenRecognizer erkenner = new TokenRecognizer();
            int gefunden = 0;
            for (int i = 0; i < texte.size(); i++) {
                String text = texte.get(i);
                assertTrue(findeMarker(text).isPresent(), "Suchfunktion findet den Marker im Eingabetext " + i);
                List<Finding> kandidaten = erkenner.detect(text);
                gefunden += kandidaten.size();
                assertToStringKlartextfrei("Form-Kandidaten " + i, kandidaten);
                for (Finding f : kandidaten) {
                    assertToStringKlartextfrei("Finding.toString Form-Kandidat " + i, f);
                    assertToStringKlartextfrei("Finding.source Form-Kandidat " + i, f.source());
                    assertToStringKlartextfrei("FindingType Form-Kandidat " + i, f.type());
                }
                List<Finding> funde = TextScanner.standard().scan(text);
                assertToStringKlartextfrei("Liste der Funde Form-Text " + i, funde);
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                assertToStringKlartextfrei("PseudonymizationResult Form-Text " + i, pseudo);
                assertToStringKlartextfrei("PlaceholderMapping Form-Text " + i, pseudo.mapping());
                ReidentificationResult zurueck = REIDENTIFIER.restore(pseudo.text(), pseudo.mapping());
                assertTrue(text.equals(zurueck.text()), "Roundtrip Form-Text " + i);
                assertToStringKlartextfrei("ReidentificationResult Form-Text " + i, zurueck);
                assertTrue(findeMarker(pseudo.text()).isEmpty(), "Marker im pseudonymisierten Text " + i);
            }
            assertTrue(gefunden >= 13, "der Test trifft die Regel zu selten: " + gefunden);
            NullPointerException npe = assertThrows(NullPointerException.class, () -> erkenner.detect(null));
            assertKlartextfrei("TokenRecognizer.detect(null) mit Formen", npe);
            assertEquals("text", npe.getMessage());
        });
        assertAusgabeKlartextfrei("Token-Formen-Test", ausgabe);
    }

    @Test
    void selbsttest_kvMarkerUndIhreErstenSechsZiffernWerdenGefunden() {
        assertTrue(findeMarker("x " + LANR_MARKER + " y").isPresent(), "LANR voll");
        assertTrue(findeMarker("x " + IK_MARKER + " y").isPresent(), "IK voll");
        assertTrue(findeMarker("x " + LANR_MARKER.substring(0, 6) + " y").isPresent(), "LANR erste sechs");
        assertTrue(findeMarker("x " + IK_MARKER.substring(0, 6) + " y").isPresent(), "IK erste sechs");
        assertTrue(MARKER_TEILE.contains(LANR_MARKER.substring(0, 6)), "LANR-Anfang in MARKER_TEILE");
        assertTrue(MARKER_TEILE.contains(IK_MARKER.substring(0, 6)), "IK-Anfang in MARKER_TEILE");
    }

    // ------------------------------------------------------------------ Selbsttests der Suchfunktion

    @Test
    void selbsttest_findetJedenBestandteilDesMarkers_auchInGrossUndKleinschreibung() {
        for (String teil : MARKER_TEILE) {
            assertTrue(findeMarker("vor " + teil + " nach").isPresent(), teil);
            assertTrue(findeMarker(teil.toUpperCase(Locale.ROOT)).isPresent(), teil);
            assertTrue(findeMarker(teil.toLowerCase(Locale.ROOT)).isPresent(), teil);
        }
        assertTrue(findeMarker("lokaler Teil: marker.mustermann").isPresent());
        assertEquals(Optional.empty(), findeMarker("Position 5, Typ PFAD, Platzhalter [[PFAD_1]]"));
        assertEquals(Optional.empty(), findeMarker(null));
        assertEquals(Optional.empty(), findeMarker(""));
    }

    @Test
    void selbsttest_findetMarkerInMeldungCauseSuppressedUndToString() {
        assertTrue(markerInAusnahme(new RuntimeException("x " + MARKER_PFAD)).isPresent(), "Meldung");
        assertTrue(markerInAusnahme(new RuntimeException("sauber", new IllegalStateException(MARKER_MAIL)))
                .isPresent(), "cause");
        assertTrue(markerInAusnahme(new RuntimeException("sauber",
                new IllegalStateException("auch sauber", new RuntimeException("mustermann")))).isPresent(),
                "tiefe cause");
        RuntimeException mitUnterdrueckter = new RuntimeException("sauber");
        mitUnterdrueckter.addSuppressed(new IllegalArgumentException("sauber", new RuntimeException("MarkerTool")));
        assertTrue(markerInAusnahme(mitUnterdrueckter).isPresent(), "suppressed (rekursiv, mit cause)");
        RuntimeException ueberschriebeneToString = new RuntimeException("sauber") {
            private static final long serialVersionUID = 1L;

            @Override
            public String toString() {
                return "Fremd " + MARKER_PFAD;
            }
        };
        assertEquals("sauber", ueberschriebeneToString.getMessage());
        assertTrue(markerInAusnahme(ueberschriebeneToString).isPresent(), "toString");
        assertEquals(Optional.empty(), markerInAusnahme(new RuntimeException("Position 4, Typ PFAD [[PFAD_1]]")));
        assertEquals(Optional.empty(), markerInAusnahme(new RuntimeException((String) null)));
    }

    @Test
    void selbsttest_zyklischeUrsachenKetteTerminiert_undFindetMarker() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertEquals(Optional.empty(), markerInAusnahme(a));
        b.addSuppressed(new RuntimeException(MARKER_MAIL));
        assertTrue(markerInAusnahme(a).isPresent());
    }

    @Test
    void selbsttest_findetMarkerInUmgeleitetemSystemOutUndSystemErr_undSetztDieUmleitungZurueck() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        Ausgabe a = erfasse(() -> {
            System.out.println("Hinweis " + MARKER_PFAD);
            System.err.println("Fehler " + MARKER_MAIL);
        });
        assertSame(out, System.out);
        assertSame(err, System.err);
        assertTrue(findeMarker(a.out()).isPresent(), "out");
        assertTrue(findeMarker(a.err()).isPresent(), "err");
        assertEquals(Optional.empty(), findeMarker(erfasse(() -> System.out.println("nichts")).out()));

        // auch bei einer Ausnahme in der Aktion wird zurückgesetzt
        assertThrows(IllegalStateException.class, () -> erfasse(() -> {
            throw new IllegalStateException("x");
        }));
        assertSame(out, System.out);
        assertSame(err, System.err);

        // die Prüfung der Ausgabe schlägt an
        assertThrows(AssertionError.class, () -> assertAusgabeKlartextfrei("selbst", a));
    }

    @Test
    void selbsttest_klartextpruefungSchlaegtBeiMarkerAn() {
        assertThrows(AssertionError.class, () -> assertKlartextfrei("selbst", new RuntimeException(MARKER_PFAD)));
        assertThrows(AssertionError.class,
                () -> assertToStringKlartextfrei("selbst", (Object) ("Finding " + MARKER_MAIL)));
    }
}
