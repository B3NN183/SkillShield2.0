@echo off
rem SkillShield starten: Doppelklick auf diese Datei. Voraussetzung: Java 21 (Temurin) oder neuer.
rem Die Datei muss im selben Ordner wie skillshield.jar liegen. Sie legt selbst keine Dateien an
rem und greift nicht auf das Netz zu.
setlocal DisableDelayedExpansion
rem Programme nie im aktuellen Ordner suchen (nur im PATH und in festen Systempfaden).
set "NoDefaultCurrentDirectoryInExePath=1"
rem JVM-Umgebungsvariablen leeren: _JAVA_OPTIONS wird nach der Kommandozeile ausgewertet und hebt deren
rem Optionen auf, die anderen koennten Agenten oder Speicherabbilder hinzufuegen.
set "_JAVA_OPTIONS="
set "JAVA_TOOL_OPTIONS="
set "JDK_JAVA_OPTIONS="

if not exist "%~dp0skillshield.jar" goto keinejar

rem Alle javaw.exe im PATH in PATH-Reihenfolge pruefen; die erste mit Java 21 oder neuer wird gestartet.
rem "$PATH:" durchsucht nur den Suchpfad, nie den aktuellen Ordner; 2^>nul blendet die Fehlermeldung von
rem where.exe aus (die Umleitung muss mit ^ geschuetzt sein, damit sie fuer where.exe gilt, nicht fuer for).
rem Der Pfad wandert ueber eine Variable in die Unterroutine, nicht als Argument von call (call wuerde
rem Prozentzeichen und Zirkumflex im Pfad ein zweites Mal auswerten).
set "JAVAW="
set "JAVAVERSION="
set "KANDIDAT="
for /f "delims=" %%p in ('"%SystemRoot%\System32\where.exe" $PATH:javaw.exe 2^>nul') do if not defined JAVAW set "KANDIDAT=%%p" & call :pruefe

if defined JAVAW goto starten
if defined JAVAVERSION goto zualt
goto keinjava

:starten
start "" "%JAVAW%" -XX:-UsePerfData -XX:+DisableAttachMechanism -XX:ErrorFile=NUL -XX:-CreateCoredumpOnCrash -jar "%~dp0skillshield.jar"
exit /b 0

rem Unterroutine: prueft %KANDIDAT% (volle Pfadangabe einer javaw.exe). Bei Java 21 oder neuer wird JAVAW
rem gesetzt, sonst bleibt JAVAW leer; JAVAVERSION merkt sich die zuletzt gefundene Version fuer die Meldung.
:pruefe
rem Die Versionspruefung laeuft mit der java.exe aus demselben Ordner wie die javaw.exe.
for %%f in ("%KANDIDAT%") do set "JAVAEXE=%%~df%%~pfjava.exe"
if not exist "%JAVAEXE%" goto :eof
set "KVERSION="
rem Die Form '""%%JAVAEXE%%" -version" 2^>^&1' ist noetig: Die aeusseren Anfuehrungszeichen umschliessen den
rem ganzen Befehl fuer das Kind-cmd, das for /f startet. %%JAVAEXE%% wird erst dort aufgeloest, damit der
rem Pfad nie ungeschuetzt in dieser Zeile steht. 2^>^&1 leitet im Kind-cmd die Fehlerausgabe (java -version
rem schreibt dorthin) in die Standardausgabe um. tokens=2,3 trifft die Zeile  ... version "21.0.1" ...  :
rem %%a ist das Wort version, %%b die Versionszahl; %%~b entfernt deren Anfuehrungszeichen.
for /f "tokens=2,3" %%a in ('""%%JAVAEXE%%" -version" 2^>^&1') do if /i "%%a"=="version" set "KVERSION=%%~b"
if not defined KVERSION goto :eof
rem delims=.-+_ tokens=1 schneidet die Hauptversion ab (aus 21.0.1, 21-ea, 21+35 wird 21; Java 8: 1.8.0_x gibt 1).
set "KMAJOR="
for /f "delims=.-+_ tokens=1" %%m in ("%KVERSION%") do set "KMAJOR=%%m"
if not defined KMAJOR goto :eof
rem Nur Ziffern zulassen, sonst ist die Ausgabe unbrauchbar und der Kandidat wird uebersprungen.
for /f "delims=0123456789" %%x in ("%KMAJOR%") do goto :eof
set "JAVAVERSION=%KVERSION%"
if %KMAJOR% LSS 21 goto :eof
set "JAVAW=%KANDIDAT%"
goto :eof

:keinejar
echo SkillShield kann nicht gestartet werden: Die Datei skillshield.jar fehlt.
echo Sie muss im selben Ordner liegen wie diese Datei (SkillShield.cmd).
echo.
pause
exit /b 1

:zualt
echo SkillShield kann nicht gestartet werden: Die gefundene Java-Version ist zu alt.
echo Gefunden wurde Java %JAVAVERSION%, gebraucht wird Java 21 oder neuer (Eclipse Temurin 21).
goto installieren

:keinjava
echo SkillShield kann nicht gestartet werden: Java 21 wurde nicht gefunden.
echo SkillShield braucht Java 21 oder neuer (Eclipse Temurin 21).

:installieren
echo Installieren Sie es mit dem folgenden Befehl und starten Sie danach diese Datei erneut:
echo.
echo     winget install EclipseAdoptium.Temurin.21.JRE
echo.
pause
exit /b 1
