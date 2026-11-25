package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P1_OHNE_AUSRUF;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P6;
import static de.skillshield.core.SynthetischeGeheimnisse.P7;
import static de.skillshield.core.SynthetischeGeheimnisse.P_DOLLAR;
import static de.skillshield.core.SynthetischeGeheimnisse.P_GEHEIM;
import static de.skillshield.core.SynthetischeGeheimnisse.P_HUNTER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_LEERZEICHEN_OHNE;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_LEERZEICHEN;
import static de.skillshield.core.SynthetischeGeheimnisse.P_SEMIKOLON;
import static de.skillshield.core.SynthetischeGeheimnisse.T;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Positivkorpus (Plan geheimnisse-erkennung, T-2; Anforderung AK-37): synthetische Faelle mit den erwarteten
 * Geheimnis-Funden (Typ, Bereich, Konfidenz, Stufe, Quelle). Alle Werte stammen aus {@link SynthetischeGeheimnisse}
 * und werden nur zur Laufzeit zu Texten zusammengesetzt; JWT, PEM-Koepfe und Praefix-Token entstehen dort aus Teilen.
 *
 * <p><b>Erwartung je Fall.</b> Der Scanner liefert genau die Funde von {@code ALT} (dem Scanner vor dem Feature,
 * unveraendert und feldgleich) und dazu die hier genannten Geheimnis-Funde, sortiert nach Beginn. Jeder Fall nennt
 * ausserdem die <i>geschuetzten Bereiche</i>: die Wertpositionen, die nach dem Feature abgedeckt sein muessen.
 * Ein Fall ist ein <b>Konfliktfall nach T-1 Regel 3</b>, wenn {@code ALT} eine geschuetzte Position schon abdeckt
 * (der Wert steht in einer Pfad-, URL- oder E-Mail-Form); dann gilt die Lueckenregel (Plan Kapitel 1.1): der Fund von
 * {@code ALT} bleibt unveraendert, und die Geheimnis-Funde sind genau die Reststuecke der geschuetzten Bereiche
 * ausserhalb aller Funde von {@code ALT} (keine, wenn der Bestandsfund den Bereich ganz abdeckt). Die
 * Strukturpruefungen in {@code GeheimnisPositivKorpusTest} leiten das aus {@code ALT} ab und halten es fest.
 *
 * <p><b>Kategorien</b> (die Reihenfolge des Plans): {@link #KATEGORIEN}. Eine Kategorie wird erst freigeschaltet
 * ({@code GeheimnisPositivKorpusTest.AKTIVE_KATEGORIEN}), wenn der Task, der sie liefert, abgenommen ist. Faelle mit
 * einem Wert des Typs TOKEN stehen deshalb nie in einer Passwort-Kategorie und umgekehrt.
 *
 * <p>Eingefroren ab Abnahme T-2 (Plan Kapitel 6.3).
 */
final class GeheimnisPositivKorpus {

    static final String K1 = "K1";
    static final String ZWISCHENTEIL = "ZWISCHENTEIL";
    static final String UMBRUCH = "UMBRUCH";
    static final String ANFUEHRUNG = "ANFUEHRUNG";
    static final String K2 = "K2";
    static final String K3 = "K3";
    static final String K4 = "K4";
    static final String TOKEN_KONTEXT = "TOKEN_KONTEXT";
    static final String SCHEMA = "SCHEMA";
    static final String JWT = "JWT";
    static final String PEM = "PEM";
    static final String PRAEFIX = "PRAEFIX";
    static final String ODBC = "ODBC";
    static final String ORACLE = "ORACLE";
    static final String KOMMANDOZEILE = "KOMMANDOZEILE";
    static final String MEHRFACH = "MEHRFACH";

    /** Die Kategorien des Plans (Kapitel 9, T-2). */
    static final List<String> KATEGORIEN = List.of(K1, ZWISCHENTEIL, UMBRUCH, ANFUEHRUNG, K2, K3, K4,
            TOKEN_KONTEXT, SCHEMA, JWT, PEM, PRAEFIX, ODBC, ORACLE, KOMMANDOZEILE, MEHRFACH);

    static final FindingType PASSWORT = new FindingType("PASSWORT");
    static final FindingType TOKEN = new FindingType("TOKEN");

    static final String QUELLE_PASSWORT = "passwort-kontext";
    static final String QUELLE_VERBINDUNG = "passwort-verbindung";
    static final String QUELLE_TOKEN = "token-kontext";
    static final String QUELLE_JWT = "token-jwt";
    static final String QUELLE_PEM = "token-pem";
    static final String QUELLE_PRAEFIX = "token-praefix";

    private static final String AT = "@";
    private static final String AE = String.valueOf((char) 0xE4);
    private static final String UE = String.valueOf((char) 0xFC);
    private static final String NBSP = String.valueOf((char) 0xA0);
    private static final String DUENN = String.valueOf((char) 0x2009);
    private static final String LF = "\n";
    private static final String CRLF = "\r\n";

    /** Ein erwarteter Geheimnis-Fund. */
    record Erwartung(FindingType typ, int start, int end, double konfidenz, String quelle) {

        Finding finding(String text) {
            return new Finding(typ, text.substring(start, end), start, end, konfidenz, quelle);
        }

        ConfidenceLevel stufe() {
            return ConfidenceLevel.of(konfidenz);
        }
    }

    /**
     * Ein Fall des Positivkorpus.
     *
     * @param id          Fall-ID (Kategorie und laufende Nummer), die einzige Angabe in Testmeldungen
     * @param kategorie   eine der {@link #KATEGORIEN}
     * @param text        der Text (enthaelt Testwerte, steht nie in einer Meldung)
     * @param funde       die erwarteten Geheimnis-Funde, aufsteigend, ueberlappungsfrei
     * @param geschuetzt  die Wertbereiche {@code [start, end)}, die nach dem Feature abgedeckt sein muessen
     * @param konflikt    ob der Fall ein Konfliktfall nach T-1 Regel 3 ist ({@code ALT} deckt einen Wertbereich ab)
     */
    record Fall(String id, String kategorie, String text, List<Erwartung> funde, List<int[]> geschuetzt,
                boolean konflikt) {

        /** Nur die ID, nie Text oder Werte (Plan Kapitel 6.1). */
        @Override
        public String toString() {
            return "Fall[" + id + "]";
        }

        /** Die Funde der Erwartung als {@link Finding}. */
        List<Finding> geheimnisFunde() {
            List<Finding> liste = new ArrayList<>();
            for (Erwartung e : funde) {
                liste.add(e.finding(text));
            }
            return liste;
        }
    }

    private static final List<Fall> FAELLE = baue();

    private GeheimnisPositivKorpus() {
    }

    /** Alle Faelle in fester Reihenfolge (unveraenderlich). */
    static List<Fall> faelle() {
        return FAELLE;
    }

    // ------------------------------------------------------------------ Baustein

    /** Baut einen Fall aus Textteilen und Fund-Teilen; Positionen ergeben sich aus der Reihenfolge. */
    private static final class B {
        private final String id;
        private final String kategorie;
        private final StringBuilder sb = new StringBuilder();
        private final List<Erwartung> funde = new ArrayList<>();
        private final List<int[]> geschuetzt = new ArrayList<>();
        private boolean konflikt;

        B(String id, String kategorie) {
            this.id = id;
            this.kategorie = kategorie;
        }

        B t(String text) {
            sb.append(text);
            return this;
        }

        private B fund(FindingType typ, String wert, double konfidenz, String quelle) {
            int start = sb.length();
            sb.append(wert);
            funde.add(new Erwartung(typ, start, sb.length(), konfidenz, quelle));
            geschuetzt.add(new int[] {start, sb.length()});
            return this;
        }

        /** PASSWORT nach K1 in einer Zeile: 0,95 ab acht Zeichen, sonst 0,80. */
        B pw(String wert) {
            return fund(PASSWORT, wert, wert.length() >= 8 ? 0.95 : 0.80, QUELLE_PASSWORT);
        }

        B pw(String wert, double konfidenz) {
            return fund(PASSWORT, wert, konfidenz, QUELLE_PASSWORT);
        }

        /** PASSWORT aus Verbindungszeichenfolge oder Kommandozeile: 0,95. */
        B pv(String wert) {
            return fund(PASSWORT, wert, 0.95, QUELLE_VERBINDUNG);
        }

        B tk(String wert, double konfidenz, String quelle) {
            return fund(TOKEN, wert, konfidenz, quelle);
        }

        /** Ein Wert, den ein Bestandsfund von ALT ganz abdeckt: kein Geheimnis-Fund, Konfliktfall. */
        B frei(String wert) {
            int start = sb.length();
            sb.append(wert);
            geschuetzt.add(new int[] {start, sb.length()});
            konflikt = true;
            return this;
        }

        /**
         * Ein Wert, den ein Bestandsfund von ALT bis auf ein Reststueck am Ende abdeckt: der abgedeckte Teil
         * {@code bestand}, danach das Reststueck als Geheimnis-Fund. Konfliktfall.
         */
        B teil(String bestand, FindingType typ, String rest, double konfidenz, String quelle) {
            int start = sb.length();
            sb.append(bestand);
            int restStart = sb.length();
            sb.append(rest);
            funde.add(new Erwartung(typ, restStart, sb.length(), konfidenz, quelle));
            geschuetzt.add(new int[] {start, sb.length()});
            konflikt = true;
            return this;
        }

        Fall bauen() {
            return new Fall(id, kategorie, sb.toString(), List.copyOf(funde), List.copyOf(geschuetzt), konflikt);
        }
    }

    private static final class Liste {
        private final List<Fall> faelle = new ArrayList<>();
        private final Map<String, Integer> zaehler = new LinkedHashMap<>();

        B neu(String kategorie) {
            int n = zaehler.merge(kategorie, 1, Integer::sum);
            return new B(kategorie + "-" + (n < 10 ? "0" + n : String.valueOf(n)), kategorie);
        }

        void add(B b) {
            faelle.add(b.bauen());
        }
    }

    // ------------------------------------------------------------------ Faelle

    private static List<Fall> baue() {
        Liste l = new Liste();
        k1(l);
        zwischenteil(l);
        umbruch(l);
        anfuehrung(l);
        k2(l);
        k3k4(l);
        tokenKontext(l);
        schema(l);
        jwt(l);
        pem(l);
        praefix(l);
        odbc(l);
        oracle(l);
        kommandozeile(l);
        mehrfach(l);
        return List.copyOf(l.faelle);
    }

    private static void k1(Liste l) {
        // AK-2: alle Kontextwoerter mit Doppelpunkt
        for (String wort : List.of("Passwort", "passwort", "KENNWORT", "Kennwort", "password", "Password", "passwd",
                "Passphrase", "Initialpasswort", "Einmalkennwort", "Adminpasswort", "Root-Passwort", "db-passwort",
                "PW", "pw", "Pwd")) {
            l.add(l.neu(K1).t(wort + ": ").pw(P1));
        }
        // AK-2: Kontextwoerter mit Gleichheitszeichen ohne Leerzeichen; AK-15
        for (String wort : List.of("DB_PASSWORD", "dbPassword", "pwd", "PW")) {
            l.add(l.neu(K1).t(wort + "=").pw(P1));
        }
        // AK-4: Verbinder K1 mit Leerraum
        l.add(l.neu(K1).t("Passwort = ").pw(P1));
        l.add(l.neu(K1).t("Passwort : ").pw(P1));
        l.add(l.neu(K1).t("Passwort:").pw(P1));
        // AK-7: Ende des Werts
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t("."));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t(","));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t(";"));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t(":"));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t("?"));
        l.add(l.neu(K1).t("(Passwort: ").pw(P1).t(")"));
        l.add(l.neu(K1).t("[Passwort: ").pw(P1).t("]"));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t(".)"));
        l.add(l.neu(K1).t("Passwort: ").pw(P1_OHNE_AUSRUF).t("."));
        l.add(l.neu(K1).t("Passwort: ").pw(P1 + "!"));
        l.add(l.neu(K1).t("Passwort: ").pw(P1).t(" bitte " + AE + "ndern"));
        // AK-10: Laengen
        l.add(l.neu(K1).t("Passwort: ").pw(P6));
        l.add(l.neu(K1).t("Passwort: ").pw(P7));
        l.add(l.neu(K1).t("Passwort: ").pw(SynthetischeGeheimnisse.wert4096()));
        // AK-11 und AK-16: Zeichenmischung
        l.add(l.neu(K1).t("Passwort: ").pw(P1_OHNE_AUSRUF));
        l.add(l.neu(K1).t("Passwort: ").pw(P_DOLLAR));
        l.add(l.neu(K1).t("Passwort: ").pw(P3));
        l.add(l.neu(K1).t("Passwort: ").pw(P2));
        l.add(l.neu(K1).t("Passwort: ").pw(P_HUNTER));
        // AK-45: geschuetztes Leerzeichen und schmales Leerzeichen wie ein Leerzeichen
        l.add(l.neu(K1).t("Passwort:" + NBSP).pw(P1));
        l.add(l.neu(K1).t("Passwort:" + DUENN).pw(P1));
        // AK-13, AK-29: Wert in Pfad-, URL- oder E-Mail-Form; der Bestandsfund bleibt, kein PASSWORT-Fund
        l.add(l.neu(K1).t("Passwortdatei: ").frei("/etc/x"));
        l.add(l.neu(K1).t("Passwort: ").frei("/etc/shadow"));
        l.add(l.neu(K1).t("Passwort: ").frei("C:\\Tresor\\pw.kdbx"));
        l.add(l.neu(K1).t("Passwort: ").frei("https://tresor.kvbsp.local/eintrag"));
        l.add(l.neu(K1).t("Passwort: ").frei("erika.beispiel" + AT + "kv-beispiel.example"));
        l.add(l.neu(K1).t("C:\\x\\pw=").frei(P1 + "\\y"));
        l.add(l.neu(K1).t("pw=").frei(P1 + AT + "example.invalid"));
        // AK-28: Zugangsdaten in einer URL; der URL-Fund bleibt (Nachtrag: Reststueck bei Endzeichen des URL-Erkenners)
        l.add(l.neu(K1).t("https://svc_x:").frei(P1).t(AT + "host.example/pfad"));
        l.add(l.neu(K1).t("ftp://u:").frei(P1).t(AT + "host.example"));
        String jdbc = "jdbc:postgresql://host.example/db?user=u&password=";
        l.add(l.neu(K1).t(jdbc).teil(P1_OHNE_AUSRUF, PASSWORT, "!", 0.95, QUELLE_PASSWORT));
        l.add(l.neu(K1).t(jdbc).teil(P1_OHNE_AUSRUF, PASSWORT, "!!", 0.95, QUELLE_PASSWORT).t(" im Wiki"));
        for (String ende : List.of(".", ",", ";", ":", "?", "'", "\"", ")", "]")) {
            l.add(l.neu(K1).t(jdbc).teil(P1_OHNE_AUSRUF, PASSWORT, "!", 0.95, QUELLE_PASSWORT).t(ende));
        }
    }

    private static void zwischenteil(Liste l) {
        l.add(l.neu(ZWISCHENTEIL).t("Tempor" + AE + "res Passwort f" + UE + "r svc_abr_export: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort f" + UE + "r svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort des Kontos KVBSP\\svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("password for root: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort f" + UE + "r den Benutzer svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Kennwort zum Dienstkonto svc_x: ").pw(P2));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort vom Dienstkonto svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort der Datenbank: ").pw(P2));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort zu svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Kennwort zur Anwendung: ").pw(P3));
        l.add(l.neu(ZWISCHENTEIL).t("password of root: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("password to svc_x: ").pw(P1));
        l.add(l.neu(ZWISCHENTEIL).t("Passwort des Kontos: ").pw(P1));
    }

    private static void umbruch(Liste l) {
        l.add(l.neu(UMBRUCH).t("Passwort:" + LF).pw(P1, 0.80));
        l.add(l.neu(UMBRUCH).t("Passwort:" + CRLF + "    ").pw(P1, 0.80));
        l.add(l.neu(UMBRUCH).t("Passwort =" + LF).pw(P1, 0.80));
        l.add(l.neu(UMBRUCH).t("Passwort:\r").pw(P1, 0.80));
        l.add(l.neu(UMBRUCH).t("Kennwort:" + LF).pw(P2, 0.80).t(LF + "Weitere Angaben folgen."));
    }

    private static void anfuehrung(Liste l) {
        // AK-4
        l.add(l.neu(ANFUEHRUNG).t("\"password\": \"").pw(P1).t("\""));
        l.add(l.neu(ANFUEHRUNG).t("password: '").pw(P1).t("'"));
        l.add(l.neu(ANFUEHRUNG).t("password = \"").pw(P1).t("\""));
        // AK-8
        l.add(l.neu(ANFUEHRUNG).t("Passwort: \"").pw(P_MIT_LEERZEICHEN).t("\""));
        l.add(l.neu(ANFUEHRUNG).t("Passwort: " + (char) 0x201E).pw(P_MIT_LEERZEICHEN).t(String.valueOf((char) 0x201C)));
        l.add(l.neu(ANFUEHRUNG).t("Passwort: `").pw(P_MIT_LEERZEICHEN).t("`"));
        l.add(l.neu(ANFUEHRUNG).t("Passwort: \"").pw(P1));
        l.add(l.neu(ANFUEHRUNG).t("Passwort: \"").pw(P2).t("\" gilt bis Freitag."));
    }

    private static void k2(Liste l) {
        // AK-4
        l.add(l.neu(K2).t("Passwort ist ").pw(P1, 0.80));
        l.add(l.neu(K2).t("Passwort lautet ").pw(P1, 0.80));
        l.add(l.neu(K2).t("password is ").pw(P1, 0.80));
        // AK-5, AK-16
        l.add(l.neu(K2).t("Das Passwort f" + UE + "r den Benutzer svc_x lautet ").pw(P1, 0.80));
        l.add(l.neu(K2).t("Passwort von SRV-APP07 ist ").pw(P1, 0.80));
        l.add(l.neu(K2).t("Passwort ist ").pw(P1_OHNE_AUSRUF, 0.80));
        l.add(l.neu(K2).t("Das Initialpasswort lautet ").pw(P2, 0.80).t(" und gilt einmalig."));
    }

    private static void k3k4(Liste l) {
        l.add(l.neu(K3).t("Passwort\t").pw(P1, 0.80));
        l.add(l.neu(K3).t("| Passwort | ").pw(P1, 0.80).t(" |"));
        l.add(l.neu(K3).t("Kennwort\t").pw(P2, 0.80));
        l.add(l.neu(K3).t("Adminpasswort\t").pw(P3, 0.80));
        l.add(l.neu(K3).t("| Kennwort | ").pw(P2, 0.80));
        l.add(l.neu(K4).t("<Kennwort>").pw(P3, 0.80).t("</Kennwort>"));
        l.add(l.neu(K4).t("<password>").pw(P1, 0.80).t("</password>"));
        l.add(l.neu(K4).t("<Passwort>").pw(P2, 0.80).t("</Passwort>"));
    }

    private static void tokenKontext(Liste l) {
        // AK-17
        for (String wort : List.of("Token", "access_token", "accessToken", "refresh_token", "idToken", "API-Key",
                "apikey", "api_key", "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret")) {
            l.add(l.neu(TOKEN_KONTEXT).t(wort + ": ").tk(T, 0.95, QUELLE_TOKEN));
        }
        l.add(l.neu(TOKEN_KONTEXT).t("client_secret=").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(TOKEN_KONTEXT).t("\"token\": \"").tk(T, 0.95, QUELLE_TOKEN).t("\""));
        // AK-19: eine UUID ist mit Kontextwort ein Fund
        l.add(l.neu(TOKEN_KONTEXT).t("apikey: ").tk(SynthetischeGeheimnisse.uuid(), 0.95, QUELLE_TOKEN));
        // AK-9, AK-30: Passwort und Token in einer Zeile; KV-Nummer daneben
        l.add(l.neu(TOKEN_KONTEXT).t("Benutzer: svc_x, Passwort: ").pw(P1).t(", Token: ")
                .tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(TOKEN_KONTEXT).t("LANR " + SynthetischeKvNummern.LANR_GUELTIG + ", Token: ")
                .tk(T, 0.95, QUELLE_TOKEN));
    }

    private static void schema(Liste l) {
        l.add(l.neu(SCHEMA).t("Authorization: Bearer ").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(SCHEMA).t("Authorization: Basic ").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(SCHEMA).t("Authorization: Token ").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(SCHEMA).t("Bearer ").tk(T, 0.80, QUELLE_TOKEN));
    }

    private static void jwt(Liste l) {
        String jwt = SynthetischeGeheimnisse.jwt();
        l.add(l.neu(JWT).tk(jwt, 0.95, QUELLE_JWT));
        l.add(l.neu(JWT).t("Das Token ").tk(jwt, 0.95, QUELLE_JWT).t(" ist abgelaufen."));
        l.add(l.neu(JWT).t("Authorization: Bearer ").tk(jwt, 0.95, QUELLE_JWT));
        l.add(l.neu(JWT).t("https://example.invalid/x?t=").frei(jwt));
    }

    private static void pem(Liste l) {
        List<String> koerper = SynthetischeGeheimnisse.pemKoerperZeilen();
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of(LF, CRLF)) {
                l.add(l.neu(PEM).t(SynthetischeGeheimnisse.pemKopfzeile(art) + ze)
                        .tk(String.join(ze, koerper), 0.95, QUELLE_PEM)
                        .t(ze + SynthetischeGeheimnisse.pemFusszeile(art)));
            }
            l.add(l.neu(PEM).t(SynthetischeGeheimnisse.pemKopfzeile(art) + LF)
                    .tk(String.join(LF, koerper), 0.95, QUELLE_PEM));
        }
        String art = "RSA PRIVATE KEY";
        l.add(l.neu(PEM).t("Schl" + UE + "ssel im Anhang:" + LF + SynthetischeGeheimnisse.pemKopfzeile(art) + LF)
                .tk(String.join(LF, koerper), 0.95, QUELLE_PEM)
                .t(LF + SynthetischeGeheimnisse.pemFusszeile(art) + LF + "Danach neu starten."));
        List<String> mitKopf = new ArrayList<>(List.of("Proc-Type: 4,ENCRYPTED",
                "DEK-Info: AES-128-CBC," + SynthetischeGeheimnisse.hex32(), ""));
        mitKopf.addAll(koerper);
        l.add(l.neu(PEM).t(SynthetischeGeheimnisse.pemKopfzeile(art) + LF)
                .tk(String.join(LF, mitKopf), 0.95, QUELLE_PEM).t(LF + SynthetischeGeheimnisse.pemFusszeile(art)));
        // Koerper mit Schraegstrich in der ersten Zeile: ein Unix-Pfad-Fund von ALT deckt die Zeile ab (G-18)
        List<String> mitStrich = SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich();
        for (String ze : List.of(LF, CRLF)) {
            l.add(l.neu(PEM).t(SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY") + ze)
                    .teil(mitStrich.get(0), TOKEN, ze + mitStrich.get(1) + ze + mitStrich.get(2), 0.95, QUELLE_PEM)
                    .t(ze + SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY")));
        }
    }

    private static void praefix(Liste l) {
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            l.add(l.neu(PRAEFIX).tk(SynthetischeGeheimnisse.praefixToken(praefix), 0.95, QUELLE_PRAEFIX));
        }
        List<String> praefixe = SynthetischeGeheimnisse.praefixe();
        l.add(l.neu(PRAEFIX).t("Fund im Log: ")
                .tk(SynthetischeGeheimnisse.praefixToken(praefixe.get(0)), 0.95, QUELLE_PRAEFIX));
        l.add(l.neu(PRAEFIX).t("Der Wert ").tk(SynthetischeGeheimnisse.praefixToken(praefixe.get(5)), 0.95,
                QUELLE_PRAEFIX).t(" wurde widerrufen."));
        l.add(l.neu(PRAEFIX).t("(").tk(SynthetischeGeheimnisse.praefixToken(praefixe.get(7)), 0.95, QUELLE_PRAEFIX)
                .t(")"));
    }

    private static void odbc(Liste l) {
        l.add(l.neu(ODBC).t("Driver={X};Server=Beispiel;Database=Demo;Uid=u;Pwd=").pv(P1).t(";"));
        l.add(l.neu(ODBC).t("Server=Beispiel;Password=").pv(P3).t(";Database=Demo"));
        l.add(l.neu(ODBC).t("Server=Beispiel;Pwd=").pv(P_GEHEIM).t(";"));
        l.add(l.neu(ODBC).t("Server=Beispiel;Pwd=").pv(P_LEERZEICHEN_OHNE).t(";"));
        l.add(l.neu(ODBC).t("Server=Beispiel;Pwd='").pv(P_SEMIKOLON).t("'"));
        l.add(l.neu(ODBC).t("Server=Beispiel;PWD={").pv(P_SEMIKOLON).t("}"));
        l.add(l.neu(ODBC).t("Server=Beispiel;Uid=u;Passwd=").pv(P2));
    }

    private static void oracle(Liste l) {
        l.add(l.neu(ORACLE).t("jdbc:oracle:thin:scott/").pv(P3).t(AT + "dbsrv-ora01:1521/dienst"));
        l.add(l.neu(ORACLE).t("sqlplus scott/").pv(P3).t(AT + "ORCL"));
        l.add(l.neu(ORACLE).t("sqlplus -S scott/").pv(P3).t(AT + "ORCL"));
        l.add(l.neu(ORACLE).t("CONNECT scott/").pv(P3).t(AT + "ORCL"));
        // G-6: Punkt im Ziel, ALT liefert einen E-Mail-Fund ueber die ganze Zeichenfolge (Konfliktfall)
        l.add(l.neu(ORACLE).t("jdbc:oracle:thin:scott/").frei(P3).t(AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD"));
        l.add(l.neu(ORACLE).t("sqlplus scott/").frei(P3).t(AT + "orcl.kvbsp.local"));
        l.add(l.neu(ORACLE).t("sqlplus scott/").frei(P3).t(AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD"));
    }

    private static void kommandozeile(Liste l) {
        l.add(l.neu(KOMMANDOZEILE).t("--password=").pv(P1));
        l.add(l.neu(KOMMANDOZEILE).t("--password ").pv(P1));
        l.add(l.neu(KOMMANDOZEILE).t("--passwd=").pv(P1));
        l.add(l.neu(KOMMANDOZEILE).t("--pwd=").pv(P1));
        l.add(l.neu(KOMMANDOZEILE).t("--pass ").pv(P1));
        l.add(l.neu(KOMMANDOZEILE).t("tool --user svc_x --password=").pv(P1).t(" --verbose"));
        l.add(l.neu(KOMMANDOZEILE).t("--client-secret=").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("--token ").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("--api-key=").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("--secret=").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("--access-token ").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("--apikey=").tk(T, 0.95, QUELLE_TOKEN));
        l.add(l.neu(KOMMANDOZEILE).t("curl --api-key ").tk(T, 0.95, QUELLE_TOKEN).t(" https://example.invalid/x"));
    }

    private static void mehrfach(Liste l) {
        // AK-9: mehrere Werte in einer Zeile sind je ein eigener Fund
        l.add(l.neu(MEHRFACH).t("Passwort: ").pw(P1).t(" Passwort: ").pw(P2));
        l.add(l.neu(MEHRFACH).t("Passwort: ").pw(P1).t(", Kennwort: ").pw(P2));
        l.add(l.neu(MEHRFACH).t("pw=").pw(P1).t("; pwd=").pw(P2));
        l.add(l.neu(MEHRFACH).t("Passwort: ").pw(P1).t(" und Passwort: ").pw(P1));
        l.add(l.neu(MEHRFACH).t("db-passwort=").pw(P1).t(" pw=").pw(P2).t(" Kennwort: ").pw(P3));
        l.add(l.neu(MEHRFACH).t("Passwort: ").pw(P1).t(LF + "Kennwort: ").pw(P2));
        // AK-30: KV-Nummer neben einem Passwort; der KV-Fund steht in ALT
        l.add(l.neu(MEHRFACH).t("BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + ", Passwort: ").pw(P1));
    }
}
