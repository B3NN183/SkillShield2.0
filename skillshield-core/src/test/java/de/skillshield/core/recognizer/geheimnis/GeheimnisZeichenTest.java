package de.skillshield.core.recognizer.geheimnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Zeichenklassen und Vergleiche der Geheimnis-Erkenner (Plan geheimnisse-erkennung, Kapitel 1.7, OF-10; AK-45):
 * Umlaute und Eszett, {@code I mit Punkt} und {@code i ohne Punkt}, geschuetztes und schmales Leerzeichen,
 * Surrogate, Zeilenumbrueche, kombinierende Zeichen, unsichtbare Formatzeichen und die Anfuehrungszeichen-Paare.
 */
class GeheimnisZeichenTest {

    private static final int AE = 0xE4;
    private static final int ESZETT = 0xDF;
    private static final int UE_KLEIN = 0xFC;
    private static final int UE_GROSS = 0xDC;
    private static final int I_PUNKT = 0x130;
    private static final int I_OHNE_PUNKT = 0x131;
    private static final int KELVIN = 0x212A;
    private static final int NBSP = 0xA0;
    private static final int DUENN = 0x2009;
    private static final int SCHMAL_FEST = 0x202F;
    private static final int ZWSP = 0x200B;
    private static final int EMOJI = 0x1F600;

    private static String s(int... codepoints) {
        return new String(codepoints, 0, codepoints.length);
    }

    // ------------------------------------------------------------------ Buchstaben, Ziffern, Wortkoerper

    @Test
    void umlauteUndEszettSindBuchstabenUndKleinbuchstaben() {
        for (int cp : new int[] {AE, ESZETT, UE_KLEIN, 0xF6}) {
            assertTrue(GeheimnisZeichen.istBuchstabe(cp), "Buchstabe " + cp);
            assertTrue(Character.isLowerCase(cp), "klein " + cp);
            assertTrue(GeheimnisZeichen.istWortkoerperZeichen(cp), "Wortkoerper " + cp);
        }
        assertTrue(Character.isUpperCase(UE_GROSS));
        assertTrue(GeheimnisZeichen.istBuchstabe(UE_GROSS));
    }

    @Test
    void ziffernUndWortkoerperzeichen() {
        assertTrue(GeheimnisZeichen.istZiffer('7'));
        assertTrue(GeheimnisZeichen.istAsciiZiffer('7'));
        assertFalse(GeheimnisZeichen.istAsciiZiffer(0x0663), "arabisch-indische Ziffer ist keine ASCII-Ziffer");
        assertTrue(GeheimnisZeichen.istZiffer(0x0663), "aber eine Ziffer");
        assertTrue(GeheimnisZeichen.istWortkoerperZeichen('_'));
        assertTrue(GeheimnisZeichen.istWortkoerperZeichen('-'));
        for (char c : new char[] {':', '=', ' ', '.', ',', '"', '\'', '/', '\\', '!', '*', '`', '(', ')'}) {
            assertFalse(GeheimnisZeichen.istWortkoerperZeichen(c), "kein Wortkoerperzeichen: " + (int) c);
        }
    }

    @Test
    void kombinierendeZeichenGehoerenZumWortkoerper_unsichtbareFormatzeichenNicht() {
        assertTrue(GeheimnisZeichen.istKombinierend(0x0301), "Akut, Mn");
        assertTrue(GeheimnisZeichen.istWortkoerperZeichen(0x0301));
        assertFalse(GeheimnisZeichen.istKombinierend('a'));
        assertFalse(GeheimnisZeichen.istWortkoerperZeichen(ZWSP), "Nullbreitenleerzeichen trennt");
        assertFalse(GeheimnisZeichen.istWortkoerperZeichen(0x2060), "Wortverbinder trennt");
        assertFalse(GeheimnisZeichen.istWortkoerperZeichen(0xFEFF), "BOM trennt");
    }

    @Test
    void surrogatpaarIstEinCodepoint_einEmojiIstKeinBuchstabeUndKeinWortkoerperzeichen() {
        String emoji = s(EMOJI);
        assertEquals(2, emoji.length());
        assertEquals(EMOJI, emoji.codePointAt(0));
        assertFalse(GeheimnisZeichen.istBuchstabe(EMOJI));
        assertFalse(GeheimnisZeichen.istWortkoerperZeichen(EMOJI));
        assertFalse(GeheimnisZeichen.istLeerraum(EMOJI));
        assertFalse(GeheimnisZeichen.istLeerraum(emoji.charAt(0)), "hohes Surrogat");
        assertFalse(GeheimnisZeichen.istLeerraum(emoji.charAt(1)), "tiefes Surrogat");
    }

    // ------------------------------------------------------------------ Leerraum und Zeilenumbruch

    @Test
    void leerraum_dazuGehoerenGeschuetztesUndSchmalesLeerzeichenUndTabulator() {
        for (int cp : new int[] {' ', '\t', NBSP, DUENN, SCHMAL_FEST, 0x3000, 0x0B, 0x0C}) {
            assertTrue(GeheimnisZeichen.istLeerraum(cp), "Leerraum " + cp);
            assertFalse(GeheimnisZeichen.istZeilenumbruch(cp), "kein Umbruch " + cp);
        }
        assertFalse(GeheimnisZeichen.istLeerraum('a'));
        assertFalse(GeheimnisZeichen.istLeerraum(ZWSP), "Nullbreitenleerzeichen ist kein Leerraum");
    }

    @Test
    void zeilenumbruch_lfCrNelUndAbsatztrenner_sindKeinLeerraum() {
        for (int cp : new int[] {'\n', '\r', 0x85, 0x2028, 0x2029}) {
            assertTrue(GeheimnisZeichen.istZeilenumbruch(cp), "Umbruch " + cp);
            assertFalse(GeheimnisZeichen.istLeerraum(cp), "Umbruch ist kein Leerraum " + cp);
        }
        assertFalse(GeheimnisZeichen.istZeilenumbruch(' '));
    }

    @Test
    void zeilenumbruchLaenge_crLfIstEinUmbruch() {
        assertEquals(2, GeheimnisZeichen.zeilenumbruchLaenge("a\r\nb", 1));
        assertEquals(1, GeheimnisZeichen.zeilenumbruchLaenge("a\rb", 1));
        assertEquals(1, GeheimnisZeichen.zeilenumbruchLaenge("a\nb", 1));
        assertEquals(1, GeheimnisZeichen.zeilenumbruchLaenge("a" + s(0x2028), 1));
        assertEquals(0, GeheimnisZeichen.zeilenumbruchLaenge("ab", 1));
        assertEquals(1, GeheimnisZeichen.zeilenumbruchLaenge("a\r", 1), "CR am Textende");
        assertEquals(0, GeheimnisZeichen.zeilenumbruchLaenge("a", 1), "Position am Textende");
        assertEquals(0, GeheimnisZeichen.zeilenumbruchLaenge("a", 5), "Position hinter dem Text");
    }

    // ------------------------------------------------------------------ ASCII-Vergleich

    @Test
    void endetMit_grossUndKleinGleich_nurAscii() {
        assertTrue(GeheimnisZeichen.endetMit("DB_PASSWORD", 11, "password"));
        assertTrue(GeheimnisZeichen.endetMit("dbPassword", 10, "password"));
        assertTrue(GeheimnisZeichen.endetMit("Initialpasswort", 15, "passwort"));
        assertFalse(GeheimnisZeichen.endetMit("Passwort", 8, "passwoerter"), "Stamm laenger als das Wort");
        assertFalse(GeheimnisZeichen.endetMit("Passwort", 8, "kennwort"));
        assertTrue(GeheimnisZeichen.endetMit("xxPasswortyy", 10, "passwort"), "Ende liegt vor dem Textende");
    }

    @Test
    void kelvinzeichenUndIMitPunktPassenNie_keineLocaleUndKeinToLowerCase() {
        // U+212A (Kelvin-Zeichen) wird von toLowerCase zu k; hier darf es nie zu k werden
        String kennwort = "Kennwort".replace('K', (char) KELVIN);
        assertFalse(GeheimnisZeichen.endetMit(kennwort, kennwort.length(), "kennwort"), "Kelvin-Zeichen");
        // I mit Punkt und i ohne Punkt (Tuerkisch): kein Treffer auf windows11
        String punkt = "W" + s(I_PUNKT) + "ndows11";
        String ohnePunkt = "W" + s(I_OHNE_PUNKT) + "ndows11";
        assertFalse(GeheimnisZeichen.gleichKlein(punkt, 0, punkt.length(), "windows11"), "I mit Punkt");
        assertFalse(GeheimnisZeichen.gleichKlein(ohnePunkt, 0, ohnePunkt.length(), "windows11"), "i ohne Punkt");
        assertTrue(GeheimnisZeichen.gleichKlein("WINDOWS11", 0, 9, "windows11"));
        assertTrue(GeheimnisZeichen.gleichKlein("Windows11", 0, 9, "windows11"));
        assertFalse(GeheimnisZeichen.gleichKlein("Windows12", 0, 9, "windows11"));
        assertFalse(GeheimnisZeichen.gleichKlein("Windows11", 0, 8, "windows11"), "Laenge verschieden");
    }

    @Test
    void gleichKlein_derUmlautUeGiltInBeidenSchreibweisen_sonstKeinNichtAscii() {
        String fuer = "f" + s(UE_KLEIN) + "r";
        assertTrue(GeheimnisZeichen.gleichKlein(fuer, 0, 3, fuer));
        assertTrue(GeheimnisZeichen.gleichKlein("F" + s(UE_GROSS) + "R", 0, 3, fuer));
        assertFalse(GeheimnisZeichen.gleichKlein("F" + s(AE) + "R", 0, 3, fuer));
        assertFalse(GeheimnisZeichen.gleichKlein("fur", 0, 3, fuer));
        assertTrue(GeheimnisZeichen.gleichKlein("xxforxx", 2, 5, "for"), "Teilbereich");
    }

    @Test
    void inListe_vergleichtDenGanzenWert() {
        List<String> liste = List.of("sha-256", "md5");
        assertTrue(GeheimnisZeichen.inListe("SHA-256", liste));
        assertTrue(GeheimnisZeichen.inListe("Md5", liste));
        assertFalse(GeheimnisZeichen.inListe("sha-2566", liste));
        assertFalse(GeheimnisZeichen.inListe("", liste));
    }

    @Test
    void asciiKlein_veraendertNurAsciiGrossbuchstaben() {
        assertEquals('a', GeheimnisZeichen.asciiKlein('A'));
        assertEquals('z', GeheimnisZeichen.asciiKlein('Z'));
        assertEquals('a', GeheimnisZeichen.asciiKlein('a'));
        assertEquals((char) UE_GROSS, GeheimnisZeichen.asciiKlein((char) UE_GROSS));
        assertEquals('1', GeheimnisZeichen.asciiKlein('1'));
    }

    // ------------------------------------------------------------------ Anfuehrungszeichen

    @Test
    void anfuehrungszeichenPaare_geradeUndTypografisch() {
        int[][] paare = {{'"', '"'}, {'\'', '\''}, {'`', '`'}, {0x201E, 0x201C}, {0x201E, 0x201D}, {0x201C, 0x201D},
            {0x201A, 0x2018}, {0x201A, 0x2019}, {0x2018, 0x2019}, {0xBB, 0xAB}, {0xAB, 0xBB},
            {0x203A, 0x2039}, {0x2039, 0x203A}};
        for (int i = 0; i < paare.length; i++) {
            assertTrue(GeheimnisZeichen.istAnfuehrungOeffnend(paare[i][0]), "oeffnend, Paar " + i);
            assertTrue(GeheimnisZeichen.schliesst(paare[i][0], paare[i][1]), "schliessend, Paar " + i);
        }
        assertFalse(GeheimnisZeichen.schliesst('"', '\''));
        assertFalse(GeheimnisZeichen.schliesst(0x201C, 0x201C), "links oben schliesst sich nicht selbst");
        assertFalse(GeheimnisZeichen.schliesst(0xBB, 0xBB));
        assertFalse(GeheimnisZeichen.schliesst('a', 'a'));
        assertFalse(GeheimnisZeichen.istAnfuehrungOeffnend('a'));
        assertFalse(GeheimnisZeichen.istAnfuehrungOeffnend('('));
    }

    @Test
    void auszeichnung_sternUnterstrichGravis_undFolgenlaenge() {
        assertTrue(GeheimnisZeichen.istAuszeichnung('*'));
        assertTrue(GeheimnisZeichen.istAuszeichnung('_'));
        assertTrue(GeheimnisZeichen.istAuszeichnung('`'));
        assertFalse(GeheimnisZeichen.istAuszeichnung('-'));
        assertEquals(2, GeheimnisZeichen.folgenLaenge("a**b", 1, 4));
        assertEquals(4, GeheimnisZeichen.folgenLaenge("*****", 0, 4), "auf die Grenze gekappt");
        assertEquals(0, GeheimnisZeichen.folgenLaenge("a", 1, 4), "Textende");
        assertEquals(1, GeheimnisZeichen.folgenLaenge("ab", 0, 4));
    }

    @Test
    void asciiHilfen() {
        assertTrue(GeheimnisZeichen.istAsciiGross('A'));
        assertFalse(GeheimnisZeichen.istAsciiGross(UE_GROSS));
        assertTrue(GeheimnisZeichen.istAsciiBuchstabeOderZiffer('z'));
        assertTrue(GeheimnisZeichen.istAsciiBuchstabeOderZiffer('0'));
        assertFalse(GeheimnisZeichen.istAsciiBuchstabeOderZiffer('-'));
        assertFalse(GeheimnisZeichen.istAsciiBuchstabeOderZiffer(AE));
    }
}
