package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P5;
import static de.skillshield.core.SynthetischeGeheimnisse.P6;
import static de.skillshield.core.SynthetischeGeheimnisse.P_HUNTER;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Fehlalarm-Pruefstand fuer die Geheimnis-Erkennung (Plan geheimnisse-erkennung, T-2; Anforderung AK-33 bis AK-36).
 * Er steht vor der Erkennerlogik und ist ab Abnahme von T-2 eingefroren (Plan Kapitel 6.3): Ein Treffer aendert die
 * Regel, nie den Korpus (AK-35).
 *
 * <ul>
 * <li>Der Negativkorpus ({@code negativkorpus.txt}): mindestens 300 Saetze, zwoelf Kategorien (die acht der
 * Anforderung mit je mindestens 20 Saetzen, dazu Tabellen/XML/JSON, Versionen/Daten/Nummern, Hashes/UUIDs/Base64,
 * Pfade/URLs), enthaelt wortlich alle Saetze aus AK-33 und AK-34. {@code TextScanner.standard()} und jeder Erkenner
 * im Paket {@code de.skillshield.core.recognizer.geheimnis} liefern darauf 0 PASSWORT- und TOKEN-Funde.</li>
 * <li>Der Negativ-Generator ({@link GeheimnisNegativGenerator}, AK-36): 100 000 Saetze, 0 Funde.</li>
 * <li>Laufzeit-Grenzfaelle: Saetze aus den Akzeptanzkriterien, die einen Wert enthalten und trotzdem kein Fund sind
 * (AK-3, AK-5, AK-6, AK-10, AK-16, AK-17, AK-19 bis AK-24, AK-25, AK-27).</li>
 * <li>Gegenproben (Regel 12 der Vorgaenger): Ein kuenstlicher Erkenner ohne Wert-Test trifft den Korpus und den
 * Generator zu mindestens der Haelfte, damit ist belegt, dass der Prufstand wirklich prueft.</li>
 * <li>{@code SIEGEL_*}: die ersten acht Hexziffern des SHA-256 ueber Korpus, Wortliste und die ersten 1 000 Saetze
 * des Generators; eine Aenderung faellt auf.</li>
 * </ul>
 * Meldungen nennen nur Kategorie, Index und Zahlen, nie einen Satz (Plan Kapitel 6.1); die Saetze des Korpus
 * enthalten keine Geheimnisse, die Grenzfaelle schon.
 */
class GeheimnisFehlalarmTest {

    // ------------------------------------------------------------------ Siegel und Istwerte (eingefroren)

    /** Siegel der Datei {@code negativkorpus.txt} (LF-normalisiert, UTF-8). */
    static final String SIEGEL_NEGATIVKORPUS = "c71e144d";
    /** Siegel der Datei {@code wortliste.txt}. */
    static final String SIEGEL_WORTLISTE = "129c2efa";
    /** Siegel der ersten 1 000 Saetze des Negativ-Generators. */
    static final String SIEGEL_NEGATIV_GENERATOR = "6c361a46";

    /** Zahl der Saetze des Negativkorpus (Istwert). */
    static final int KORPUS_SAETZE_IST = 372;
    /** Zahl der Laufzeit-Grenzfaelle (Istwert). */
    static final int GRENZFAELLE_IST = 66;
    /** Siegel der Laufzeit-Grenzfaelle (SHA-256 ueber alle Faelle, getrennt durch das Zeichen 1). */
    static final String SIEGEL_GRENZFAELLE = "8f6b7e66";
    /** Zahl der Woerter der Wortliste (Istwert). */
    static final int WORTLISTE_IST = 267;
    /**
     * Zahl der Erkenner in {@code TextScanner.standard()}, deren Klasse im Paket
     * {@code de.skillshield.core.recognizer.geheimnis} liegt (Istwert; in T-2 gibt es keinen, ab T-4 den
     * {@code PasswortRecognizer}, ab T-6 auch den {@code TokenRecognizer}).
     */
    // Umgestellt durch geheimnisse-erkennung T-6: alt 1 (nur PasswortRecognizer), neu 2 (dazu TokenRecognizer).
    static final int GEHEIMNIS_ERKENNER_IST = 2;

    /** Saetze mit K1, K2, K3, K4 im Korpus, Istwerte (Gegenprobe). */
    static final int KORPUS_K1_IST = 153;
    static final int KORPUS_K2_IST = 32;
    static final int KORPUS_K3_IST = 14;
    static final int KORPUS_K4_IST = 8;

    /** Treffer der Gegenproben (Istwerte). */
    static final int GEGENPROBE_KORPUS_K1_TREFFER_IST = 152;
    static final int GEGENPROBE_KORPUS_ALLE_TREFFER_IST = 205;
    static final int GEGENPROBE_GENERATOR_ALLE_TREFFER_IST = 1882;

    /** Zahl der Treffer in den Texten 0 bis 1 999 des Generators fuer die Gegenprobe. */
    private static final int GEGENPROBE_GENERATOR_TEXTE = 2000;

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String GEHEIMNIS_PAKET = "de.skillshield.core.recognizer.geheimnis";
    private static final List<String> ACHT_KATEGORIEN = List.of("ZUSTAND", "AUFFORDERUNG", "VERWEIS", "RICHTLINIE",
            "ZAHL_DAUER", "ENGLISCH", "TOKEN_SAETZE", "KONFIGURATION");
    private static final Pattern KATEGORIE_ZEILE = Pattern.compile("# Kategorie: ([A-Z0-9_]+)");

    // ------------------------------------------------------------------ Hilfen

    /** Ersetzt {ue}, {ae}, {oe} durch die Umlaute (kein rohes Nicht-ASCII im Quelltext). */
    private static String u(String s) {
        return s.replace("{ue}", String.valueOf((char) 0xFC)).replace("{ae}", String.valueOf((char) 0xE4))
                .replace("{oe}", String.valueOf((char) 0xF6));
    }

    /** Die Saetze des Negativkorpus je Kategorie in Dateireihenfolge. */
    static Map<String, List<String>> korpus() {
        Map<String, List<String>> kategorien = new LinkedHashMap<>();
        String aktuell = null;
        for (String zeile : SynthetischeGeheimnisse.korpusText("negativkorpus.txt").split("\n", -1)) {
            if (zeile.isEmpty()) {
                continue;
            }
            if (zeile.charAt(0) == '#') {
                Matcher m = KATEGORIE_ZEILE.matcher(zeile);
                if (m.matches()) {
                    aktuell = m.group(1);
                    kategorien.put(aktuell, new ArrayList<>());
                }
                continue;
            }
            assertTrue(aktuell != null, "Satz vor der ersten Kategoriezeile");
            kategorien.get(aktuell).add(zeile.replace("\\n", "\n").replace("\\t", "\t"));
        }
        return kategorien;
    }

    private static List<String> alleSaetze(Map<String, List<String>> korpus) {
        List<String> alle = new ArrayList<>();
        korpus.values().forEach(alle::addAll);
        return alle;
    }

    private static int geheimnisFunde(TextScanner scanner, String text) {
        int n = 0;
        for (Finding f : scanner.scan(text)) {
            String typ = f.type().name();
            if (typ.equals("PASSWORT") || typ.equals("TOKEN")) {
                n++;
            }
        }
        return n;
    }

    /** Die Erkenner von {@code standard()}, deren Klasse im Paket der Geheimnis-Erkenner liegt. */
    private static List<Recognizer> geheimnisErkenner() {
        List<Recognizer> liste = new ArrayList<>();
        for (Recognizer r : STANDARD.recognizers()) {
            if (r.getClass().getName().startsWith(GEHEIMNIS_PAKET + ".")) {
                liste.add(r);
            }
        }
        return liste;
    }

    /** Die Scanner, gegen die geprueft wird: {@code standard()} und je ein Scanner je Geheimnis-Erkenner. */
    static List<TextScanner> pruefScanner() {
        List<TextScanner> scanner = new ArrayList<>();
        scanner.add(STANDARD);
        for (Recognizer r : geheimnisErkenner()) {
            scanner.add(new TextScanner(List.of(r)));
        }
        return scanner;
    }

    // ------------------------------------------------------------------ Negativkorpus: Aufbau

    private static final List<String> AK33 = List.of("Passwort zur{ue}ckgesetzt", "Passwort ist abgelaufen",
            "bitte neues Passwort vergeben", "Passwort: siehe Tresor", "Token abgelaufen", "Kennwort {ae}ndern",
            "password policy", "Passwort wurde per SMS versendet");

    private static final List<String> AK34 = List.of("Das Passwort muss mindestens 12 Zeichen lang sein.",
            "Passwort: 12 Zeichen", "Die Passwortl{ae}nge wurde auf 14 erh{oe}ht.", "Kennwort ist 8-stellig.",
            "Passwort f{ue}r svc_abr_export wurde zur{ue}ckgesetzt.",
            "Das Passwort f{ue}r den Benutzer ist abgelaufen.", "Bitte Passwort {ae}ndern: Anleitung im Wiki.",
            "Token: ung{ue}ltig (HTTP 401)", "Der Token l{ae}uft nach 3600 Sekunden ab.",
            "Passwort-Richtlinie: 90 Tage g{ue}ltig", "password: required", "The password has been reset.",
            "Secret: nicht vorhanden", "Kennwort: wird per Post zugestellt", "Passwort: siehe KeePass",
            "Passwort (siehe Tresor): Eintrag SRV-APP07", "Passwort:\nBitte neues Passwort vergeben.", "pw: ****",
            "Das Token ist nicht mehr g{ue}ltig, siehe CHG0012345.", "Passwort f{ue}r SRV-APP07 laut Tresor");

    @Test
    void korpus_aufbau_mindestzahlenUndKategorien() {
        Map<String, List<String>> korpus = korpus();
        List<String> alle = alleSaetze(korpus);
        assertTrue(alle.size() >= 300, "weniger als 300 Saetze: " + alle.size());
        assertEquals(KORPUS_SAETZE_IST, alle.size(), "Istwert der Satzzahl");
        for (String kategorie : ACHT_KATEGORIEN) {
            assertTrue(korpus.containsKey(kategorie), "Kategorie fehlt: " + kategorie);
            assertTrue(korpus.get(kategorie).size() >= 20,
                    "weniger als 20 Saetze in " + kategorie + ": " + korpus.get(kategorie).size());
        }
        for (Map.Entry<String, List<String>> e : korpus.entrySet()) {
            assertTrue(e.getValue().size() >= 20, "weniger als 20 Saetze in " + e.getKey());
        }
        assertEquals(12, korpus.size(), "Zahl der Kategorien");
    }

    @Test
    void korpus_saetzeSindEinmalig_nichtLeer_ohneFuehrendenOderNachgestelltenLeerraum() {
        List<String> alle = alleSaetze(korpus());
        Set<String> einmalig = new LinkedHashSet<>(alle);
        assertEquals(alle.size(), einmalig.size(), "doppelte Saetze im Korpus");
        for (int i = 0; i < alle.size(); i++) {
            String satz = alle.get(i);
            assertFalse(satz.isBlank(), "leerer Satz, Index " + i);
            assertEquals(satz.stripLeading().length(), satz.length(), "fuehrender Leerraum, Index " + i);
            assertEquals(satz.stripTrailing().length(), satz.length(), "nachgestellter Leerraum, Index " + i);
        }
    }

    @Test
    void korpus_enthaeltWoertlichAlleSaetzeAusAk33UndAk34() {
        List<String> alle = alleSaetze(korpus());
        for (String satz : AK33) {
            assertTrue(alle.contains(u(satz)), "Satz aus AK-33 fehlt, Index " + AK33.indexOf(satz));
        }
        for (String satz : AK34) {
            assertTrue(alle.contains(u(satz)), "Satz aus AK-34 fehlt, Index " + AK34.indexOf(satz));
        }
        assertEquals(8, AK33.size());
        assertEquals(20, AK34.size());
    }

    // ------------------------------------------------------------------ Negativkorpus: kein Fund

    @Test
    void korpus_keinPasswortUndKeinTokenFund_standardUndJederGeheimnisErkenner() {
        assertEquals(GEHEIMNIS_ERKENNER_IST, geheimnisErkenner().size(), "Zahl der Geheimnis-Erkenner");
        List<TextScanner> scanner = pruefScanner();
        for (Map.Entry<String, List<String>> e : korpus().entrySet()) {
            List<String> saetze = e.getValue();
            for (int i = 0; i < saetze.size(); i++) {
                for (int s = 0; s < scanner.size(); s++) {
                    assertEquals(0, geheimnisFunde(scanner.get(s), saetze.get(i)),
                            "Fund im Negativkorpus: Kategorie " + e.getKey() + ", Index " + i + ", Scanner " + s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Wortliste

    @Test
    void wortliste_mindestens200Woerter_nurBuchstaben_keinKontextOderVerbindungswort() {
        List<String> woerter = GeheimnisNegativGenerator.woerter();
        assertTrue(woerter.size() >= 200, "weniger als 200 Woerter: " + woerter.size());
        assertEquals(WORTLISTE_IST, woerter.size(), "Istwert der Wortzahl");
        long zeilen = SynthetischeGeheimnisse.korpusText("wortliste.txt").lines()
                .filter(z -> !z.isEmpty() && z.charAt(0) != '#').count();
        assertEquals(zeilen, woerter.size(), "doppelte Woerter in der Wortliste");
        Set<String> verboten = Set.of("pw", "pwd", "bearer", "basic", "authorization", "ist", "is", "lautet",
                u("f{ue}r"), "for", "von", "vom", "des", "der", "zu", "zum", "zur", "of", "to", "api", "key");
        List<String> staemme = List.of("passwort", "kennwort", "password", "passwd", "passphrase", "token",
                "apikey", "secret", "secretkey", "accesskey", "privatekey");
        for (int i = 0; i < woerter.size(); i++) {
            String wort = woerter.get(i);
            String klein = wort.toLowerCase(Locale.ROOT);
            assertTrue(wort.chars().allMatch(Character::isLetter), "Wort mit Nicht-Buchstaben, Index " + i);
            assertFalse(verboten.contains(klein), "Kontext- oder Verbindungswort in der Wortliste, Index " + i);
            for (String stamm : staemme) {
                assertFalse(klein.endsWith(stamm), "Wort endet auf einen Kontextstamm, Index " + i);
            }
        }
    }

    // ------------------------------------------------------------------ Negativ-Generator

    private static GeheimnisNegativGenerator.Lauf vollerLauf;

    /** Der volle Lauf des Negativ-Generators, einmal berechnet. */
    private static synchronized GeheimnisNegativGenerator.Lauf vollerLauf() {
        if (vollerLauf == null) {
            vollerLauf = GeheimnisNegativGenerator.lauf();
        }
        return vollerLauf;
    }

    @Test
    void generator_kennzahlen_100000Saetze_jedesKontextwortUndJederVerbinderUndJedeWertklasse() {
        GeheimnisNegativGenerator.Lauf lauf = vollerLauf();
        assertEquals(GeheimnisNegativGenerator.ANZAHL, lauf.texte().size());
        assertEquals(100_000, lauf.texte().size());
        for (int i = 0; i < lauf.jeKontext().length; i++) {
            assertTrue(lauf.jeKontext()[i] >= 50, "Kontextwort selten, Index " + i + ": " + lauf.jeKontext()[i]);
        }
        for (int i = 0; i < lauf.jeStil().length; i++) {
            assertTrue(lauf.jeStil()[i] >= 500, "Verbinder selten, Index " + i + ": " + lauf.jeStil()[i]);
        }
        for (int i = 0; i < lauf.jeKlasse().length; i++) {
            assertTrue(lauf.jeKlasse()[i] >= 10_000, "Wertklasse selten, Index " + i + ": " + lauf.jeKlasse()[i]);
        }
        assertEquals(100_000, Arrays.stream(lauf.jeKlasse()).sum());
    }

    @Test
    void generator_istDeterministisch_gleicheErgebnisseBeiGleichemSeed_ersteTexteSindPraefix() {
        GeheimnisNegativGenerator.Lauf a = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED, 3000);
        GeheimnisNegativGenerator.Lauf b = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED, 3000);
        assertTrue(a.texte().equals(b.texte()), "gleicher Seed, gleiche Saetze, 3000 Saetze");
        GeheimnisNegativGenerator.Lauf c = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED + 1, 3000);
        assertFalse(a.texte().equals(c.texte()), "anderer Seed muss andere Texte liefern");
        assertTrue(a.texte().equals(vollerLauf().texte().subList(0, 3000)), "Praefix des vollen Laufs, 3000 Saetze");
    }

    @Test
    void generator_hunderttausendSaetze_keinPasswortUndKeinTokenFund() {
        GeheimnisNegativGenerator.Lauf lauf = vollerLauf();
        List<TextScanner> scanner = pruefScanner();
        int funde = 0;
        int erster = -1;
        for (int i = 0; i < lauf.texte().size(); i++) {
            for (TextScanner s : scanner) {
                int n = geheimnisFunde(s, lauf.texte().get(i));
                if (n > 0 && erster < 0) {
                    erster = i;
                }
                funde += n;
            }
        }
        assertEquals(0, funde, "Funde im Negativ-Generator, erster Text: " + erster);
    }

    // ------------------------------------------------------------------ Laufzeit-Grenzfaelle aus den AK

    private static List<String> grenzfaelle() {
        String at = "@";
        List<String> f = new ArrayList<>();
        // AK-3: Abgrenzung der Kontextwoerter
        for (String wort : List.of("Passwortrichtlinie", "Passwort-Reset", "Passw{oe}rter", "Kennwortes", "Pass", "PIN",
                "Zugangscode", "Schl{ue}ssel", "Zugangsdaten", "xpw")) {
            f.add(u(wort) + ": " + P1);
        }
        f.addAll(List.of("pw ist " + P1, "pwd ist " + P1, "Passwort:", "Passwort", "Das Passwort:"));
        // AK-5, AK-6, AK-10, AK-16: Zwischenteil, Zeilenumbruch, Laenge, Zeichenklassen
        f.add(u("Passwort f{ue}r den alten Benutzer svc_x: " + P1));
        f.add(u("Passwort f{ue}r den\nBenutzer svc_x: " + P1));
        f.add("Passwort:\n\n" + P1);
        f.add("Passwort ist\n" + P1);
        f.add("Passwort: " + P5);
        f.add("Passwort: " + SynthetischeGeheimnisse.wert4097());
        f.add("Passwort ist " + P_HUNTER);
        f.add("Passwort ist " + P6);
        // AK-17, AK-19, AK-20: Tokens ohne Kontext
        f.add(u("Tokenl{ae}nge: ") + T);
        f.add("Token-Ablauf: " + T);
        f.add("Secretary: " + T);
        f.add("Request-ID: " + SynthetischeGeheimnisse.uuid());
        f.add("Objekt " + SynthetischeGeheimnisse.uuid());
        f.add(SynthetischeGeheimnisse.uuid());
        f.add(SynthetischeGeheimnisse.hex32());
        f.add(SynthetischeGeheimnisse.hex40());
        f.add(SynthetischeGeheimnisse.hex64());
        f.add("sha256:" + SynthetischeGeheimnisse.hex64());
        f.add("Commit " + SynthetischeGeheimnisse.hex7());
        f.add("Commit " + SynthetischeGeheimnisse.hex40());
        f.add(SynthetischeGeheimnisse.fingerprint());
        f.add(SynthetischeGeheimnisse.base64Zeile60());
        f.add("Passwort-Hash: " + SynthetischeGeheimnisse.hex64());
        f.add("Ausgabe von pwd: /opt/app");
        f.add("Passwort: [[PASSWORT_1]]");
        // AK-18: Schema-Wert unter 16 Zeichen (15 Zeichen, gemischt)
        f.add("Bearer " + SynthetischeGeheimnisse.mischwert(15));
        f.add("Authorization: Bearer " + SynthetischeGeheimnisse.mischwert(15));
        // AK-21: JWT-Formen, die keine sind
        String[] teile = SynthetischeGeheimnisse.jwt().split("\\.");
        f.add(teile[0] + "." + teile[1]);
        f.add(teile[0] + "." + teile[1] + ".abc");
        f.add(teile[0].substring(3) + "." + teile[1] + "." + teile[2]);
        f.add("a" + SynthetischeGeheimnisse.jwt());
        f.add("." + SynthetischeGeheimnisse.jwt());
        // AK-22: oeffentliche PEM-Arten und leerer Koerper
        for (String art : SynthetischeGeheimnisse.pemOeffentlicheArten()) {
            f.add(SynthetischeGeheimnisse.pemBlock(art, "\n", true));
        }
        f.add(SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", List.of(), "\n", true));
        // AK-23: Praefix-Token, die keine sind
        String praefix = SynthetischeGeheimnisse.praefixe().get(0);
        f.add(praefix + "Abcd" + "Efgh1");
        f.add(praefix + "AbcdTestwertEfghIjklMnop");
        f.add("x" + SynthetischeGeheimnisse.praefixToken(praefix));
        f.add("gh" + "x_" + SynthetischeGeheimnisse.PRAEFIX_REST);
        // AK-24, AK-25, AK-27: Verbindungszeichenfolgen und Kommandozeile
        f.add("Server=Beispiel;Pwd=;");
        f.add("Server=Beispiel;Pwd=${PW};");
        f.add("Server=Beispiel;Pwd=xxxxxx;");
        f.add("Password=" + SynthetischeGeheimnisse.P_GEHEIM);
        f.add("scott/" + P3 + at + "ORCL");
        f.add("jdbc:oracle:thin:" + at + "dbsrv-ora01.kvbsp.local:1521/ABRPROD");
        f.add("--password-file /etc/x");
        f.add("-p " + P1);
        f.add("--password=${PW}");
        f.add("--password");
        f.add("--password -v");
        return f;
    }

    @Test
    void grenzfaelle_ausDenAkzeptanzkriterien_keinPasswortUndKeinTokenFund() {
        List<String> faelle = grenzfaelle();
        assertTrue(faelle.size() >= 60, "zu wenige Grenzfaelle: " + faelle.size());
        assertEquals(GRENZFAELLE_IST, faelle.size(), "Istwert der Grenzfallzahl");
        List<TextScanner> scanner = pruefScanner();
        for (int i = 0; i < faelle.size(); i++) {
            for (int s = 0; s < scanner.size(); s++) {
                assertEquals(0, geheimnisFunde(scanner.get(s), faelle.get(i)),
                        "Fund bei Grenzfall " + i + ", Scanner " + s);
            }
        }
    }

    // ------------------------------------------------------------------ Gegenproben

    private static int treffer(Recognizer erkenner, List<String> saetze) {
        TextScanner scanner = new TextScanner(List.of(erkenner));
        int n = 0;
        for (String satz : saetze) {
            if (geheimnisFunde(scanner, satz) > 0) {
                n++;
            }
        }
        return n;
    }

    private static int zaehle(Pattern muster, List<String> saetze) {
        int n = 0;
        for (String satz : saetze) {
            if (muster.matcher(satz).find()) {
                n++;
            }
        }
        return n;
    }

    @Test
    void gegenprobe_kuenstlicherErkennerOhneWertTest_trifftMindestensDieHaelfteDerSaetzeMitVerbinderImKorpus() {
        List<String> alle = alleSaetze(korpus());
        int mitK1 = zaehle(GeheimnisNegativGenerator.HAT_K1, alle);
        int mitK2 = zaehle(GeheimnisNegativGenerator.HAT_K2, alle);
        int mitK3 = zaehle(GeheimnisNegativGenerator.HAT_K3, alle);
        int mitK4 = zaehle(GeheimnisNegativGenerator.HAT_K4, alle);
        assertEquals(KORPUS_K1_IST, mitK1, "Saetze mit K1");
        assertEquals(KORPUS_K2_IST, mitK2, "Saetze mit K2");
        assertEquals(KORPUS_K3_IST, mitK3, "Saetze mit K3");
        assertEquals(KORPUS_K4_IST, mitK4, "Saetze mit K4");
        int k1Treffer = treffer(GeheimnisNegativGenerator.ersterWortNachK1(), alle);
        int alleTreffer = treffer(GeheimnisNegativGenerator.ersterWortNachJedemVerbinder(), alle);
        assertEquals(GEGENPROBE_KORPUS_K1_TREFFER_IST, k1Treffer, "Treffer des K1-Erkenners im Korpus");
        assertEquals(GEGENPROBE_KORPUS_ALLE_TREFFER_IST, alleTreffer, "Treffer aller Verbinder im Korpus");
        assertTrue(2 * k1Treffer >= mitK1, "K1-Erkenner trifft weniger als die Haelfte: " + k1Treffer + "/" + mitK1);
        assertTrue(2 * alleTreffer >= mitK1 + mitK2 + mitK3 + mitK4,
                "Erkenner trifft weniger als die Haelfte: " + alleTreffer);
    }

    @Test
    void gegenprobe_kuenstlicherErkennerOhneWertTest_trifftDenGeneratorSatzFuerSatz() {
        List<String> texte = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED, GEGENPROBE_GENERATOR_TEXTE)
                .texte();
        int alleTreffer = treffer(GeheimnisNegativGenerator.ersterWortNachJedemVerbinder(), texte);
        assertEquals(GEGENPROBE_GENERATOR_ALLE_TREFFER_IST, alleTreffer, "Treffer im Generator");
        assertTrue(10 * alleTreffer >= 9 * texte.size(), "Erkenner trifft weniger als 90 Prozent: " + alleTreffer);
    }

    @Test
    void gegenprobe_dieGrenzfaelleSindFuerEinenErkennerOhneWertTestNichtLeer() {
        // Die Grenzfaelle enthalten Werte; ein Erkenner ohne Wert-Test muss auf einem Teil davon anschlagen.
        int treffer = treffer(GeheimnisNegativGenerator.ersterWortNachJedemVerbinder(), grenzfaelle());
        assertTrue(treffer >= 10, "Gegenprobe der Grenzfaelle zu schwach: " + treffer);
    }

    // ------------------------------------------------------------------ Siegel

    @Test
    void siegel_negativkorpusWortlisteUndGeneratorSindEingefroren() {
        assertEquals(SIEGEL_NEGATIVKORPUS,
                GeheimnisNegativGenerator.siegel(SynthetischeGeheimnisse.korpusText("negativkorpus.txt")),
                "Siegel des Negativkorpus");
        assertEquals(SIEGEL_WORTLISTE,
                GeheimnisNegativGenerator.siegel(SynthetischeGeheimnisse.korpusText("wortliste.txt")),
                "Siegel der Wortliste");
        List<String> grenzfaelle = grenzfaelle();
        assertEquals(SIEGEL_GRENZFAELLE, GeheimnisNegativGenerator.siegelUeberTexte(grenzfaelle, grenzfaelle.size()),
                "Siegel der Grenzfaelle");
        List<String> texte = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED,
                GeheimnisNegativGenerator.SIEGEL_ANZAHL).texte();
        assertEquals(SIEGEL_NEGATIV_GENERATOR,
                GeheimnisNegativGenerator.siegelUeberTexte(texte, GeheimnisNegativGenerator.SIEGEL_ANZAHL),
                "Siegel der ersten 1000 Saetze des Generators");
    }

    @Test
    void siegel_selbsttest_eineAenderungFaelltAuf() {
        String text = SynthetischeGeheimnisse.korpusText("negativkorpus.txt");
        assertEquals(8, GeheimnisNegativGenerator.siegel(text).length());
        assertFalse(GeheimnisNegativGenerator.siegel(text).equals(GeheimnisNegativGenerator.siegel(text + "x")),
                "Siegel aendert sich bei Aenderung");
        assertFalse(GeheimnisNegativGenerator.siegel(text).equals(GeheimnisNegativGenerator.siegel(
                text.replaceFirst("Passwort", "Kennwort"))), "Siegel aendert sich bei Ersetzung");
    }
}
