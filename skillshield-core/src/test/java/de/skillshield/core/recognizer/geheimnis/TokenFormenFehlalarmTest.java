package de.skillshield.core.recognizer.geheimnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeGeheimnisse;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Eigene Fehlalarm-Stichprobe fuer die kontextfreien Formen (Plan geheimnisse-erkennung, T-7): mindestens 150
 * synthetische Zeilen ohne Geheimnis in sieben Klassen (Dokumentation ueber JWT, Dokumentation ueber PEM und
 * oeffentliche Schluessel, Erklaertexte ueber mehrere Absaetze mit BEGIN und END weit auseinander, Hashes und
 * Kennungen in Logzeilen, praefixaehnliche Bezeichner in Erklaertexten, Versions-, Build- und Dateiangaben,
 * gewoehnliche Ticketsaetze). Jede Zeile liefert mit {@code standard()} keinen PASSWORT- und keinen TOKEN-Fund; die
 * Zahl der Zeilen und der Treffer je Klasse steht fest (Treffer: 0). Die Stichprobe
 * ist ergaenzend zum eingefrorenen Pruefstand; ein Treffer wuerde die Regel aendern, nicht die Stichprobe. Formen, die
 * ein Geheimnis-Scanner meldet, entstehen zur Laufzeit aus Teilen (AK-50). Meldungen nennen Klasse und Index.
 */
class TokenFormenFehlalarmTest {

    private static final TextScanner STANDARD = TextScanner.standard();

    private static String praefix(int index) {
        return SynthetischeGeheimnisse.praefixe().get(index);
    }

    private static String kopfPrivat() {
        return SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
    }

    private static String fussPrivat() {
        return SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
    }

    private static List<String> jwtDokumentation() {
        char punkte = (char) 0x2026;
        String jwt = SynthetischeGeheimnisse.jwt();
        String kopf = jwt.substring(0, 12);
        return List.of(
                "Ein JWT besteht aus drei Teilen: Header, Payload und Signatur, getrennt durch Punkte.",
                "Der Header eines JWT beginnt mit ey" + "J, weil er Base64 von einem JSON-Objekt ist.",
                "Format: ey" + "J<header>.ey" + "J<payload>.<signature>",
                "Authorization: Bearer <JWT>",
                "Authorization: Bearer <token>",
                "Authorization: Bearer " + kopf + punkte,
                "Beispiel (gekuerzt): " + kopf + punkte + "." + kopf + punkte + "." + punkte,
                "Platzhalter fuer das JWT: <JWT>",
                "Das JWT wird im Header Authorization uebergeben und ist 15 Minuten gueltig.",
                "Token-Typ: JWT (RS256), Lebensdauer 900 Sekunden",
                "Fehler 401: das JWT ist abgelaufen oder die Signatur ist ungueltig.",
                "Zum Pruefen ein JWT lokal mit base64 -d dekodieren, nie in ein Online-Werkzeug kopieren.",
                "jwt.decode(token, key, algorithms=[\"RS256\"])",
                "Die Claims iss, sub, aud und exp stehen in der Payload.",
                "Variable JWT_SECRET ist im Tresor hinterlegt.",
                "Der Dienst gibt ein JWT zurueck, das der Client im naechsten Aufruf mitschickt.",
                "ey" + "J ist kein Geheimnis, sondern der Anfang jedes JSON-Objekts in Base64.",
                "key" + "Json-Datei liegt unter C:" + (char) 0x5C + "Temp",
                "Das Feld heisst key" + "JwtAlgorithmus und enthaelt den Wert HS256.",
                "Bearer-Authentifizierung wird fuer die Schnittstelle nicht verwendet.");
    }

    private static List<String> pemDokumentation() {
        String oeffentlich = SynthetischeGeheimnisse.pemBlock("PUBLIC KEY", "\n", true);
        String zertifikat = SynthetischeGeheimnisse.pemBlock("CERTIFICATE", "\r\n", true);
        String csr = SynthetischeGeheimnisse.pemBlock("CERTIFICATE REQUEST", "\n", true);
        String pgpOeffentlich = SynthetischeGeheimnisse.pemBlock("PGP PUBLIC KEY BLOCK", "\n", true);
        List<String> zeilen = new ArrayList<>(List.of(
                oeffentlich, zertifikat, csr, pgpOeffentlich,
                "Der private Schluessel liegt in der Datei server.key und beginnt mit der Zeile " + kopfPrivat() + ".",
                "Die Datei endet mit " + fussPrivat() + " und darf nicht weitergegeben werden.",
                "Datei id_rsa.pub enthaelt den oeffentlichen Schluessel, id_rsa den privaten.",
                "ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -C dienstkonto",
                "openssl genrsa -out server.key 4096",
                "openssl req -new -key server.key -out server.csr",
                "Der Schluessel wird in einem PEM-Container gespeichert (Base64 zwischen zwei Markierungszeilen).",
                "Zertifikat server.pem, Kette chain.pem und Schluessel key.pem liegen unter /etc/ssl.",
                kopfPrivat() + "\n...\n" + fussPrivat(),
                kopfPrivat() + "\n(Inhalt der Datei hier einfuegen)\n" + fussPrivat(),
                kopfPrivat() + "\n<Schluessel>\n" + fussPrivat(),
                kopfPrivat() + "\n[gekuerzt]\n" + fussPrivat(),
                kopfPrivat() + "\n" + fussPrivat(),
                kopfPrivat() + "\n\n" + fussPrivat(),
                "Beginn: " + kopfPrivat() + " Ende: " + fussPrivat(),
                "Das Zertifikat hat den Fingerabdruck " + SynthetischeGeheimnisse.fingerprint(),
                "SSH-Fingerprint: SHA256:" + SynthetischeGeheimnisse.base64Zeile(43)));
        zeilen.addAll(pemMarkierungenImText());
        return zeilen;
    }

    /**
     * Zeilen aus Dokumentation, die BEGIN- und END-Markierungen erwaehnen (T-7, Durchlauf 2, Form zwischen
     * Markierungen): mit Platzhalter, Beschreibung oder Kurzwoertern dazwischen, in Zitaten, Kommentaren, Tabellen und
     * maskiert, mit oeffentlichen Arten, nur mit BEGIN oder nur mit END. Zwischen den Markierungen steht nie eine
     * Folge von 16 Base64-Zeichen (siehe die Grenze fuer lange Woerter in {@code TokenGrenzenTest}). Genau 36 Zeilen.
     */
    private static List<String> pemMarkierungenImText() {
        String k = kopfPrivat();
        String f = fussPrivat();
        List<String> z = new ArrayList<>();
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            z.add("Die Datei beginnt mit " + SynthetischeGeheimnisse.pemKopfzeile(art) + " und endet mit "
                    + SynthetischeGeheimnisse.pemFusszeile(art) + ".");
        }
        for (String art : SynthetischeGeheimnisse.pemOeffentlicheArten()) {
            String kopf = SynthetischeGeheimnisse.pemKopfzeile(art);
            String fuss = SynthetischeGeheimnisse.pemFusszeile(art);
            z.add("Oeffentlich: " + kopf + " ... " + fuss);
            z.add(kopf + " " + SynthetischeGeheimnisse.base64Zeile(64) + " " + fuss);
            z.add("> " + kopf + "\\n" + SynthetischeGeheimnisse.base64Zeile(64) + "\\n" + fuss);
        }
        z.addAll(List.of(
                "Zwischen " + k + " und " + f + " steht der Base64-kodierte Inhalt.",
                "# " + k + " ... " + f,
                "> " + k + " [gekuerzt] " + f,
                "KEY=\"" + k + " <hier einfuegen> " + f + "\"",
                "| private_key | " + k + " (siehe Tresor) " + f + " |",
                "Beispiel: " + k + "\\n...\\n" + f + "\\n",
                "Das Skript haengt " + k + " und " + f + " um den Inhalt der Variable.",
                "Markierungen: " + k + " / " + f,
                "Zeile 1: " + k + ", Zeile 40: " + f,
                "Die Zeile " + k + " fehlt in der Datei, der Import schlaegt fehl.",
                "Fehler: erwartet wurde " + f + ", gefunden wurde Dateiende.",
                "grep -n BEGIN server.key liefert " + k,
                "`" + k + "` ... `" + f + "`",
                "Die Datei muss mit " + k + " beginnen und mit " + f + " enden, sonst lehnt der Dienst sie ab.",
                "Inhalt zwischen " + k + " und " + f + ": 27 Zeilen Base64, nicht kopieren.",
                k + " xxxxxxxx " + f,
                k + " *** " + f,
                k + " REDACTED " + f,
                k + "\\n<REDACTED>\\n" + f,
                "Ablauf: 1. " + k + " einfuegen 2. Inhalt pruefen 3. " + f + " einfuegen",
                "Reihenfolge: " + f + " steht vor " + k + " (falsch herum)."));
        return z;
    }

    private static final List<String> SAETZE = List.of(
            "Der Dienst startet nach dem Neustart wieder und die Meldung im Log ist unauffaellig.",
            "Bitte pruefen, ob das Zertifikat noch gueltig ist und wer es erneuert hat.",
            "Wir haben den Wert im Tresor abgelegt und das Team darueber informiert.",
            "Die Datei liegt auf dem Server im Ordner Betrieb und wird nachts gesichert.",
            "Ein neuer Zugang wurde beantragt und von der Leitung freigegeben.",
            "Der Fehler tritt nur sporadisch auf und verschwindet nach einem Neustart.",
            "Im Wiki steht die Anleitung, falls sich der Ablauf aendert.",
            "Die Rueckmeldung des Betriebsteams steht noch aus.");

    private static final List<String> LANGE_WOERTER = List.of("/var/log/application/betrieb-dienst-2026-03-12.log",
            "https://wiki.example.invalid/betrieb/zertifikate", "Schluesselverwaltungsinhalt",
            "Zertifikatserneuerungsprozess", "C:" + (char) 0x5C + "Programme" + (char) 0x5C + "Anwendung" + (char) 0x5C
                    + "config.ini", "Zugangsdatenverwaltungskonzept");

    /**
     * Ein Erklaertext aus eigenen Saetzen, der die BEGIN-Markierung eines privaten Schluessels im ersten Absatz und die
     * END-Markierung {@code absaetze} Absaetze spaeter nennt (T-7, Durchlauf 3). Ab {@code variante} 1 (ohne die
     * Vielfachen von 4) steht je Absatz ein langes Wort, ein Pfad oder eine URL dazwischen.
     */
    static String erklaertext(int absaetze, int variante) {
        String art = SynthetischeGeheimnisse.pemPrivateArten().get(variante % 6);
        int n = SAETZE.size();
        StringBuilder sb = new StringBuilder("Die Datei beginnt mit " + SynthetischeGeheimnisse.pemKopfzeile(art)
                + " und " + SAETZE.get(variante % n));
        for (int a = 0; a < absaetze; a++) {
            sb.append("\n\n").append(SAETZE.get((variante + a) % n)).append(' ')
                    .append(SAETZE.get((variante + a + 3) % n));
            if (variante % 4 != 0) {
                sb.append(' ').append(LANGE_WOERTER.get((variante + a) % LANGE_WOERTER.size()));
            }
        }
        sb.append("\n\n").append(SAETZE.get((variante + 5) % n)).append(" Sie endet mit ")
                .append(SynthetischeGeheimnisse.pemFusszeile(art)).append(" und ")
                .append(SAETZE.get((variante + 1) % n));
        return sb.toString();
    }

    /** Genau 24 Texte: je 1, 3 und 10 Absaetze in acht Varianten (T-7, Durchlauf 3). */
    private static List<String> mehrAbsatzErklaertexte() {
        List<String> texte = new ArrayList<>();
        for (int absaetze : List.of(1, 3, 10)) {
            for (int variante = 0; variante < 8; variante++) {
                texte.add(erklaertext(absaetze, variante));
            }
        }
        return texte;
    }

    private static List<String> hashesUndKennungen() {
        String hex32 = SynthetischeGeheimnisse.hex32();
        String hex40 = SynthetischeGeheimnisse.hex40();
        String hex64 = SynthetischeGeheimnisse.hex64();
        String uuid = SynthetischeGeheimnisse.uuid();
        return List.of(
                "commit " + hex40, "Merge " + SynthetischeGeheimnisse.hex7() + " in main",
                "git checkout " + hex40, "Image sha256:" + hex64,
                "docker pull registry.example.invalid/app@sha256:" + hex64,
                "Digest: sha256:" + hex64, "MD5 (datei.iso) = " + hex32, "SHA1: " + hex40, "SHA256: " + hex64,
                "Pruefsumme " + hex64 + " stimmt ueberein.",
                "Zertifikat-Fingerprint " + SynthetischeGeheimnisse.fingerprint(),
                "Thumbprint: " + hex40.toUpperCase(java.util.Locale.ROOT),
                "Request-ID: " + uuid, "correlation-id=" + uuid, "Objekt " + uuid + " wurde angelegt.",
                "trace_id " + hex32 + " span_id " + SynthetischeGeheimnisse.hex(16),
                "Session " + SynthetischeGeheimnisse.hex(24) + " beendet",
                "Container " + SynthetischeGeheimnisse.hex(12) + " gestartet",
                "Build-ID: " + SynthetischeGeheimnisse.hex(20), "ETag: \"" + SynthetischeGeheimnisse.hex32() + "\"",
                "Base64-Zeile: " + SynthetischeGeheimnisse.base64Zeile(60),
                "Inhalt (Base64): " + SynthetischeGeheimnisse.base64Zeile(44) + "==",
                "Passwort-Hash: " + hex64, "Hash des Kennworts " + hex32 + " in der Tabelle USERS",
                "Seriennummer " + SynthetischeGeheimnisse.hex(14), "Lizenzschluessel-ID " + uuid,
                "Backup-Satz " + uuid + " Groesse 12 GB", "MAC aa:bb:cc:dd:ee:ff erkannt",
                "Schluessel-ID " + SynthetischeGeheimnisse.hex(16) + " im Schluesselbund",
                "kid: " + SynthetischeGeheimnisse.hex(16),
                "Dienst " + uuid + " Status OK, Antwortzeit 12 ms");
    }

    private static List<String> praefixAehnlich() {
        String ohneZiffer = "x".repeat(28);
        return List.of(
                "Das Praefix " + praefix(0) + " kennzeichnet Personal Access Tokens von GitHub.",
                "Tokens mit " + praefix(3) + " sind Installations-Tokens und laufen nach einer Stunde ab.",
                "Praefix " + praefix(6) + " steht fuer GitLab Personal Access Tokens.",
                "Slack-Tokens beginnen mit " + praefix(7) + " oder " + praefix(8) + " und werden im Tresor abgelegt.",
                "Neue Token beginnen mit " + praefix(5) + " (fein granuliert).",
                praefix(0) + "<TOKEN>", praefix(5) + "<TOKEN>", praefix(6) + "XXXX", praefix(7) + "<geheim>",
                "export GITHUB_TOKEN=" + praefix(0) + ohneZiffer,
                "Platzhalter " + praefix(0) + ohneZiffer + " nur zur Doku",
                "Das Skript prueft, ob der Wert mit " + praefix(0) + " beginnt.",
                "Variable " + praefix(1) + "token_pfad zeigt auf die Datei.",
                "Funktion " + praefix(3) + "refresh_aufruf_2 ist veraltet.",
                "Parameter " + praefix(4) + "cache_laenge=5",
                "Tabelle " + praefix(2) + "status_historie_v2 wird geleert.",
                "Verzeichnis /opt/ghr_runner_2/work",
                "Pipeline-Variable glpat-" + "token" + " ist nicht gesetzt.",
                "Nutzer " + praefix(7) + "bot wurde angelegt.",
                "Sicherheitsregel: Tokens mit dem Praefix " + praefix(0) + " nie in Tickets einfuegen.",
                "Secret-Scanner meldet Muster " + praefix(0) + "[A-Za-z0-9]{36}",
                "Regex fuer " + praefix(6) + "[A-Za-z0-9_-]{20}",
                "Beispielwert " + praefix(0) + "abc",
                "Beispielwert " + praefix(8) + "1-2-3",
                "Der Anbieter hat das Format der " + praefix(5) + "-Tokens geaendert.",
                "Abkuerzung ghs bedeutet GitHub Server",
                "Funktion " + praefix(4) + "refresh_aufruf_2 ist veraltet.",
                "Bezeichner " + praefix(0) + "name-mit-bindestrich-3 existiert nicht.");
    }

    private static List<String> versionenBuildsDateien() {
        return List.of(
                "Version 4.2.1 wurde ausgerollt", "Build 2026.03.1-rc2 ist freigegeben", "app-2.4.1-rc1.jar",
                "Release 12.0.3 (Build 4711)", "Hotfix 3.1.4-2 fuer SRV-APP07", "CHG0012345 genehmigt",
                "INC0004711 geschlossen", "JIRA-1234 Rueckmeldung offen",
                "Datei de.skillshield.core.recognizer.geheimnis.TokenFormen.java geaendert",
                "Klasse org.example.security.JwtTokenService.class geladen",
                "Paket com.example.auth.jwt.JwtDecoder aktualisiert",
                "Zeitstempel 2026-03-12T10:15:00Z", "Log 2026-03-12 10:15:00,123 INFO Dienst gestartet",
                "Host dbsrv-ora01.kvbsp.local antwortet", "Pfad C:" + (char) 0x5C + "Programme" + (char) 0x5C + "App"
                        + (char) 0x5C + "config.ini",
                "Datei /var/log/app/app-2026-03-12.log rotiert", "Archiv backup_2026_03_12_full.zip erstellt",
                "ISO image windows_server_2022_x64.iso", "Treiber-Paket nvidia_driver_536_67.exe installiert",
                "Zertifikat server_2026.pem erneuert", "Schluessel server_2026.key liegt im Tresor",
                "Java 21.0.3+9-LTS", "Python 3.12.1", "Treiberversion 31.0.101.4502",
                "TLS1.3 aktiv, Cipher AES256-GCM-SHA384");
    }

    private static List<String> ticketSaetze() {
        return List.of(
                "Kunde meldet Fehler beim Login, Passwort wurde zurueckgesetzt.",
                "Bitte pruefen, ob das Token im Cache noch gueltig ist.",
                "Der Schluessel wurde rotiert, die Anwendung ueber den neuen Schluessel informiert.",
                "Zertifikat laeuft in 30 Tagen ab, Erneuerung beantragt.",
                "Dienstkonto svc_x hat keine Berechtigung auf den Tresor.",
                "Fehlermeldung: invalid_token, Fehlercode 401, Wiederholung nach 5 Minuten.",
                "Anmeldung ueber Single Sign-on schlaegt fehl, Ursache vermutlich abgelaufenes Zertifikat.",
                "Der API-Aufruf liefert HTTP 403, Zugriff fuer die Gruppe fehlt.",
                "Das Zertifikat wurde im Tresor abgelegt, der Eintrag heisst Dienst-Zertifikat.",
                "Wir haben den Schluessel nicht im Klartext gespeichert, nur den Verweis.",
                "Der Client authentifiziert sich mit einem Zertifikat statt mit einem Token.",
                "Zugriffstoken werden nach 3600 Sekunden erneuert.",
                "Die Anleitung steht im Wiki unter Betrieb/Zertifikate.",
                "Bitte den Fingerabdruck des Zertifikats mit dem Eintrag im Tresor vergleichen.",
                "Der Dienst startet nach dem Neustart wieder, Meldung im Log unauffaellig.",
                "Backup-Job lief ohne Fehler, Dauer 42 Minuten, Groesse 12 GB.",
                "Datenbank-Verbindung ueber den Pool, maximal 20 Verbindungen.",
                "Ticket an das Betriebsteam uebergeben, Rueckmeldung bis Freitag.",
                "Fuer die Integration wird ein neuer API-Zugang beantragt.",
                "Der Zugang wurde vom Sicherheitsteam freigegeben, Details im Ticket.",
                "Das Skript liest den Schluessel aus der Umgebungsvariable, nicht aus der Datei.",
                "Zugriff mit ssh und Schluesseldatei funktioniert, mit Passwort nicht.",
                "Rotation der Schluessel ist fuer das naechste Wartungsfenster geplant.",
                "Die Signatur der Nachricht konnte nicht geprueft werden.",
                "Fehler im Header: Authorization fehlt.",
                "Der Webserver liefert den Header WWW-Authenticate: Bearer realm=\"api\".",
                "Anfrage mit Header X-Request-Id und Zeitstempel protokolliert.",
                "Neustart des Dienstes durch den Watchdog, siehe Log.",
                "Konfiguration geprueft, keine Abweichung vom Standard.",
                "Wiederherstellung erfolgreich, Daten stimmen mit der Sicherung ueberein.");
    }

    private static Map<String, List<String>> stichprobe() {
        Map<String, List<String>> klassen = new LinkedHashMap<>();
        klassen.put("JWT-Dokumentation", jwtDokumentation());
        klassen.put("PEM-Dokumentation", pemDokumentation());
        klassen.put("Erklaertexte ueber mehrere Absaetze", mehrAbsatzErklaertexte());
        klassen.put("Hashes und Kennungen", hashesUndKennungen());
        klassen.put("Praefixaehnliche Bezeichner", praefixAehnlich());
        klassen.put("Versionen, Builds, Dateien", versionenBuildsDateien());
        klassen.put("Ticketsaetze", ticketSaetze());
        return klassen;
    }

    @Test
    void stichprobe_mindestens150Zeilen_keinPasswortUndKeinTokenFund_jeKlasseNull() {
        Map<String, List<String>> klassen = stichprobe();
        int zeilen = 0;
        Map<String, Integer> treffer = new LinkedHashMap<>();
        Map<String, List<Integer>> orte = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : klassen.entrySet()) {
            int n = 0;
            for (int i = 0; i < e.getValue().size(); i++) {
                zeilen++;
                List<Finding> funde = STANDARD.scan(e.getValue().get(i)).stream()
                        .filter(f -> GeheimnisTypen.istGeheimnistyp(f.type())).toList();
                if (!funde.isEmpty()) {
                    n++;
                    orte.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(i);
                }
            }
            treffer.put(e.getKey(), n);
        }
        assertTrue(zeilen >= 150, "zu wenige Zeilen: " + zeilen);
        Map<String, Integer> erwartet = new LinkedHashMap<>();
        for (String name : klassen.keySet()) {
            erwartet.put(name, 0);
        }
        assertEquals(erwartet, treffer, "Treffer je Klasse, Orte " + orte);
        assertEquals(ZEILEN, zeilen, "Zahl der Zeilen");
        Map<String, Integer> groessen = new LinkedHashMap<>();
        klassen.forEach((name, liste) -> groessen.put(name, liste.size()));
        assertEquals(Map.of("JWT-Dokumentation", 20, "PEM-Dokumentation", 57, "Erklaertexte ueber mehrere Absaetze", 24,
                "Hashes und Kennungen", 31, "Praefixaehnliche Bezeichner", 28, "Versionen, Builds, Dateien", 25,
                "Ticketsaetze", 30), groessen, "Zeilen je Klasse");
    }

    /**
     * 155 Zeilen bis T-7, Durchlauf 1; 36 Zeilen mit Markierungen in Durchlauf 2 (PEM-Dokumentation 21 auf 57); 24
     * Erklaertexte ueber mehrere Absaetze in Durchlauf 3.
     */
    private static final int ZEILEN = 215;

    @Test
    void gegenprobe_dieStichprobeIstScharf_einEchterFundWirdGemeldet() {
        String jwt = SynthetischeGeheimnisse.jwt();
        String token = SynthetischeGeheimnisse.praefixToken(praefix(0));
        List<String> echt = List.of("Das Token " + jwt + " ist abgelaufen.", "Der Wert " + token + " wurde gesetzt.",
                SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\n", true),
                "# " + SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\n# ", true),
                "x " + SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\\n", true));
        for (String zeile : echt) {
            assertTrue(STANDARD.scan(zeile).stream().anyMatch(f -> GeheimnisTypen.istGeheimnistyp(f.type())),
                    "echter Fund wird erkannt");
        }
    }
}
