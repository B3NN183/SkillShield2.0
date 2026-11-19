package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests mit festem Startwert für den Literalschutz (T-9): Roundtrip zeichengleich ohne Meldung,
 * Platzhalter-Invariante mit Positionen, kein Originalwert an der Stelle eines Fundes, Determinismus
 * (Prüfung in {@link LiteralschutzPruefung}). Insgesamt über 100 000 Läufe.
 */
class LiteralschutzEigenschaftTest {

    private static final long SEED = 20261119L;

    /** Bausteine: Klammern, Unterstrich, Leerraum, Ziffern, Typnamen, vollständige und beschädigte Platzhalter. */
    private static final String[] STUECKE = {"[", "]", "[[", "]]", "_", " ", "  ", "1", "0", "9", "12", "x", "A",
        "EMAIL", "email", "Email", "PFAD", "pfad", "TEXT", "text", "IP", "PERSON", "EMAIL_", "EMAIL_1", "TEXT_1",
        "[[EMAIL_1]]", "[[email_1]]", "[[ EMAIL_1 ]]", "[[TEXT_1]]", "[[TEXT_2]]", "[[IP_1]]", "[[A_1]]",
        "[EMAIL_1]", "[[EMAIL_", "[[EMAIL_1]", "[[TEXT_", "[IP_", "[[PERSON_]]", "[[X_9]]", "[[[[X_1]]]]",
        "\uFF3B", "\uFF3D", "\u200B", "\u00AD", "\u200F", "\uFEFF", "e\u0301", "\uD83D\uDE00", "$1", "\\1", "\n"};

    private static final String[] WERTE = {"192.0.2.1", "x", "ab", "[[IP_1]]", "[[", "]]", "EMAIL_1", "_", "1",
        "erika.beispiel@example.invalid", "[[TEXT_1]]", "[", "]", "[[EMAIL_1]]", "EMAIL"};

    private static final FindingType[] TYPEN = {FindingType.EMAIL, FindingType.IP, FindingType.PFAD, FindingType.TEXT,
        FindingType.URL, new FindingType("PERSON"), new FindingType("A"), new FindingType("X")};

    private static String zufallstext(Random rnd, int maxStuecke) {
        StringBuilder sb = new StringBuilder();
        int n = rnd.nextInt(maxStuecke + 1);
        for (int i = 0; i < n; i++) {
            sb.append(STUECKE[rnd.nextInt(STUECKE.length)]);
        }
        return sb.toString();
    }

    private static boolean trenntSurrogatpaar(String text, int pos) {
        return pos > 0 && pos < text.length() && Character.isHighSurrogate(text.charAt(pos - 1))
                && Character.isLowSurrogate(text.charAt(pos));
    }

    @Test
    void zufallstextOhneFundeBleibtOderWirdGeschuetzt() {
        Random rnd = new Random(SEED);
        for (int lauf = 0; lauf < 30_000; lauf++) {
            String text = zufallstext(rnd, 14);
            PseudonymizationResult r = LiteralschutzPruefung.pruefe(text, List.of(), rnd, "Lauf " + lauf);
            // Ohne Funde entsteht nur TEXT-Schutz; ohne Token bleibt der Text identisch (AK-30)
            if (r.mapping().size() == 0) {
                assertEquals(text, r.text(), "Lauf " + lauf);
            }
        }
    }

    @Test
    void zufallstextMitZufaelligenFundbereichen() {
        Random rnd = new Random(SEED + 1);
        for (int lauf = 0; lauf < 40_000; lauf++) {
            String text = zufallstext(rnd, 14);
            List<Finding> funde = new ArrayList<>();
            int pos = 0;
            while (pos < text.length()) {
                int start = pos + rnd.nextInt(4);
                if (start >= text.length()) {
                    break;
                }
                int end = Math.min(text.length(), start + 1 + rnd.nextInt(14));
                if (trenntSurrogatpaar(text, start) || trenntSurrogatpaar(text, end)) {
                    pos = start + 1;
                    continue;
                }
                funde.add(Finding.manual(TYPEN[rnd.nextInt(TYPEN.length)], text.substring(start, end), start, end));
                pos = end;
            }
            LiteralschutzPruefung.pruefe(text, funde, rnd, "Lauf " + lauf);
        }
    }

    @Test
    void zufallstextMitFundenAngrenzendAnKlammernUndPlatzhalter() {
        Random rnd = new Random(SEED + 2);
        for (int lauf = 0; lauf < 40_000; lauf++) {
            StringBuilder sb = new StringBuilder();
            List<Finding> funde = new ArrayList<>();
            int anzahl = rnd.nextInt(10);
            for (int i = 0; i <= anzahl; i++) {
                sb.append(zufallstext(rnd, 3));
                if (i == anzahl) {
                    break;
                }
                String wert = WERTE[rnd.nextInt(WERTE.length)];
                int s = sb.length();
                sb.append(wert);
                funde.add(Finding.manual(TYPEN[rnd.nextInt(TYPEN.length)], wert, s, sb.length()));
            }
            LiteralschutzPruefung.pruefe(sb.toString(), funde, rnd, "Lauf " + lauf);
        }
    }
}
