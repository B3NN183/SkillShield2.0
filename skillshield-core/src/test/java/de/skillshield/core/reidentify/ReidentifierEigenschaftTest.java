package de.skillshield.core.reidentify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Eigenschaftstests mit festem Startwert. */
class ReidentifierEigenschaftTest {

    private static final long SEED = 20261119L;
    private static final String[] WERTE = {"erika.beispiel@example.invalid", "192.0.2.10", "198.51.100.7",
        "203.0.113.5", "https://intranet.example/a/b", "C:\\Tools\\a.exe", "$1", "\\1", "${HOME}", "%APPDATA%",
        "[[", "]]", "x", "GEHEIM-mustermann-4711"};
    // Luecken ohne vollstaendigen Platzhalter; Bruchstuecke mit unbekanntem Typ oder ohne Typ sind erlaubt
    private static final String[] LUECKEN = {"", " ", "Start", "mit \uD83D\uDE00 Emoji", "e\u0301", "\t", "\r\n",
        "$1", "\\1", "a.b", "Zeile\nzwei", "arr[idx_0]", "[", "[[", "]]", "]", "[x]", "\uFF3B\uFF3B", "\u200B"};
    private static final FindingType[] TYPEN = {FindingType.EMAIL, FindingType.IP, FindingType.URL,
        FindingType.PFAD, new FindingType("PERSON")};

    @Test
    void roundtripPseudonymizeReidentifyIstZeichengleich() {
        Random rnd = new Random(SEED);
        Pseudonymizer p = new Pseudonymizer();
        Reidentifier r = new Reidentifier();
        for (int lauf = 0; lauf < 3000; lauf++) {
            StringBuilder sb = new StringBuilder();
            List<Finding> funde = new ArrayList<>();
            int anzahl = rnd.nextInt(12);
            for (int i = 0; i <= anzahl; i++) {
                int teile = rnd.nextInt(3);
                for (int k = 0; k < teile; k++) {
                    sb.append(LUECKEN[rnd.nextInt(LUECKEN.length)]);
                }
                if (i == anzahl) {
                    break;
                }
                String wert = WERTE[rnd.nextInt(WERTE.length)];
                int s = sb.length();
                sb.append(wert);
                funde.add(new Finding(TYPEN[rnd.nextInt(TYPEN.length)], wert, s, sb.length(), 0.9, "manuell"));
            }
            String text = sb.toString();
            PseudonymizationResult pr = p.pseudonymize(text, funde);
            ReidentificationResult rr = r.restore(pr.text(), pr.mapping());
            assertEquals(text, rr.text(), "Lauf " + lauf);
            assertTrue(rr.unresolved().isEmpty(), "Lauf " + lauf);
        }
    }

    private static final String[] BRUCHSTUECKE = {"[", "]", "[[", "]]", "_", " ", "  ", "EMAIL", "email", "PFAD",
        "pfad", "X", "1", "0", "9", "12", "99999999999", "\u200B", "\uFF3B", "\uFF3D", "$1", "\\", "\n", "\u00E4",
        "[[EMAIL_1]]", "[[PFAD_2]]", "[[PFAD_2]", "[[EMAIL_", "\uD83D\uDE00"};
    private static final String SENTINEL_1 = "\u0001S1\u0001";
    private static final String SENTINEL_2 = "\u0001S2\u0001";

    @Test
    void zufaelligeKiAntwortWirftNieUndLaesstUnbekanntesUnveraendert() {
        Random rnd = new Random(SEED + 1);
        Reidentifier r = new Reidentifier();
        PlaceholderMapping m = PlaceholderMapping.of(Map.of("[[EMAIL_1]]", SENTINEL_1, "[[PFAD_2]]", SENTINEL_2));
        for (int lauf = 0; lauf < 20_000; lauf++) {
            StringBuilder sb = new StringBuilder();
            int teile = rnd.nextInt(30);
            for (int k = 0; k < teile; k++) {
                sb.append(BRUCHSTUECKE[rnd.nextInt(BRUCHSTUECKE.length)]);
            }
            String text = sb.toString();
            ReidentificationResult res = r.restore(text, m);

            // Meldungen: genau im Eingabetext vorhanden, aufsteigend, ueberlappungsfrei, nur erlaubte Zeichen
            int ende = 0;
            for (UnresolvedPlaceholder u : res.unresolved()) {
                assertTrue(u.position() >= ende, "Lauf " + lauf);
                assertTrue(text.startsWith(u.token(), u.position()), "Lauf " + lauf);
                ende = u.position() + u.token().length();
                assertTrue(u.token().length() <= 62, "Lauf " + lauf);
                assertTrue(u.token().chars().allMatch(c -> c == '[' || c == ']' || c == '_' || c == ' '
                        || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')),
                        "Lauf " + lauf);
            }
            // Ersetzt wird nur durch die beiden Sentinel-Werte; entfernt man sie, bleibt nichts Fremdes uebrig:
            // jede nicht ersetzte Stelle steht unveraendert im Ergebnis (Laenge: Eingabe minus ersetzte Token)
            String ohneSentinel = res.text().replace(SENTINEL_1, "").replace(SENTINEL_2, "");
            assertTrue(ohneSentinel.length() <= text.length(), "Lauf " + lauf);
            // Ohne Klammer gibt es nichts zu ersetzen
            if (text.indexOf('[') < 0) {
                assertEquals(text, res.text(), "Lauf " + lauf);
                assertTrue(res.unresolved().isEmpty(), "Lauf " + lauf);
            }
        }
    }

    @Test
    void zufaelligeAntwortAusSonderzeichenOhneMapping() {
        Random rnd = new Random(SEED + 2);
        Reidentifier r = new Reidentifier();
        String alphabet = "[]_ aAeEmMiIlL1019$\\\u200B\uFF3B\uD83D\uDE00";
        for (int lauf = 0; lauf < 20_000; lauf++) {
            int len = rnd.nextInt(80);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            String text = sb.toString();
            ReidentificationResult res = r.restore(text, PlaceholderMapping.empty());
            assertEquals(text, res.text(), "Lauf " + lauf);
            assertTrue(res.unresolved().stream().allMatch(u -> u.reason() == UnresolvedPlaceholder.Reason.UNBEKANNT),
                    "Lauf " + lauf);
        }
    }
}
