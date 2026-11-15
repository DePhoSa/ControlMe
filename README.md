# Fernhilfe (ControlMe)

Eine kleine, kostenlose Fernwartungs-App für Android 14+ (Pixel 8 und neuer), gedacht für die Familie (1 Helfer, bis zu 3 Handys). Keine Werbung, kein Konto, kein Abo.

- **Handy der Mutter:** App installieren → „Ich möchte Hilfe bekommen“ → 3 Freigaben antippen → fertig. Kein Konto, keine Registrierung, kein Passwort.
- **Dein Handy:** App installieren → „Ich helfe anderen“ → Namen eingeben → 6-stellige Zahl vom anderen Handy eintippen → ab jetzt jederzeit **„Verbinden“**.

## So funktioniert es

| Teil | Technik | Kosten |
|---|---|---|
| Anmeldung | Firebase **anonyme** Anmeldung (automatisch, unsichtbar) | gratis |
| Kopplung & Verbindungsaufbau | Cloud Firestore (Spark-Tarif) | gratis |
| Bildübertragung | WebRTC, direkt von Handy zu Handy, Ende-zu-Ende verschlüsselt | gratis |
| Tippen/Wischen/Text | Android-Bedienungshilfe (AccessibilityService) | – |
| Bauen & Verteilen der APK | GitHub Actions + GitHub Releases | gratis |

Ablauf einer Sitzung: Du tippst auf „Verbinden“. Das Handy deiner Mutter bekommt die Anfrage über Firestore (die App wartet dort dauerhaft im Hintergrund) und öffnet selbst den Android-Dialog „Bildschirm teilen“. Den tippt die App nach Möglichkeit auch selbst an (siehe unten). Danach siehst du den Bildschirm und kannst steuern: tippen, lange drücken, wischen, Zurück/Home/Apps/Benachrichtigungen und Text eingeben.

Deine Mutter sieht während der Hilfe eine Benachrichtigung „Sascha sieht deinen Bildschirm“ mit **Beenden**-Knopf. Außerdem zeigt Android den Chip für die Bildschirmfreigabe in der Statusleiste.

## Einmalige Einrichtung (ca. 15 Minuten, alles kostenlos)

### 1. Firebase-Projekt
1. <https://console.firebase.google.com> → **Projekt hinzufügen** (Google Analytics kannst du ausschalten). Der Spark-Tarif reicht, eine Kreditkarte ist nicht nötig.
2. **Authentication** → Jetzt starten → Anbieter **Anonym** aktivieren.
3. **Firestore Database** → Datenbank erstellen (Standort z. B. `europe-west3` Frankfurt, Produktionsmodus).
4. Firestore → **Regeln**: den Inhalt von [`firebase/firestore.rules`](firebase/firestore.rules) einfügen → **Veröffentlichen**.
5. Projektübersicht → **Android-App hinzufügen** mit Paketname `de.controlme.app` (SHA-1 ist nicht nötig) → `google-services.json` herunterladen.

### 2. Signierschlüssel (damit spätere Updates sich über die alte Version installieren lassen)
```bash
keytool -genkeypair -v -keystore release.jks -alias fernhilfe -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.jks > release.jks.b64
```
Die Datei `release.jks` gut aufheben, aber **nicht** ins Repository legen.

### 3. GitHub-Secrets
Repository → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Inhalt |
|---|---|
| `GOOGLE_SERVICES_JSON` | kompletter Inhalt der `google-services.json` |
| `KEYSTORE_BASE64` | Inhalt von `release.jks.b64` |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | `fernhilfe` |
| `KEY_PASSWORD` | Passwort des Schlüssels |
| `TURN_URL` / `TURN_USER` / `TURN_PASS` | *optional*, siehe unten |

### 4. APK bauen
Jeder Push baut die App (Reiter **Actions** → Artefakt „Fernhilfe-APK“). Bei einem Push auf `main` wird die APK zusätzlich als **Release** veröffentlicht. Den Link kannst du deiner Mutter schicken oder die APK selbst auf ihrem Handy installieren.

### 5. Installation auf dem Handy der Mutter
1. APK öffnen → Installation aus dieser Quelle erlauben → Installieren.
2. App öffnen → **„Ich möchte Hilfe bekommen“**.
3. Die drei blauen Knöpfe nacheinander antippen:
   - **Benachrichtigungen** → Erlauben
   - **Fernbedienung** → „Fernhilfe“ → Schalter ein.
     *Ist der Schalter grau?* Android sperrt das bei Apps, die nicht aus dem Play Store kommen. Dann: App-Info → ⋮ oben rechts → **„Eingeschränkte Einstellungen zulassen“** und den Schalter danach nochmal einschalten.
   - **Immer erreichbar** → Zulassen
4. Die App zeigt eine 6-stellige Zahl. Du tippst sie in deiner App unter **„Handy hinzufügen“** ein. Fertig.

Am besten erledigst du das einmal selbst bei einem Besuch. Danach muss deine Mutter nie wieder etwas einrichten. Nach Neustarts und App-Updates startet die Bereitschaft automatisch.

## Was Android nicht erlaubt (ehrlich gesagt)

- **Dialog „Bildschirm teilen“:** Android verlangt ihn bei jeder Sitzung, das gilt für alle Apps ohne Systemrechte. Die App versucht, ihn über die Bedienungshilfe **automatisch zu bestätigen** (Einstellung „Freigabe automatisch bestätigen“, standardmäßig an). Falls das nach einem Android-Update nicht mehr klappt, muss deine Mutter einmal auf „Starten“ bzw. „Bildschirm teilen“ tippen.
- **Gesperrtes Handy:** Ist das Handy gesperrt (PIN/Fingerabdruck), muss es erst entsperrt werden.
- **Geschützte Inhalte:** Banking-Apps oder Passwortfelder erscheinen oft schwarz. Ab Android 15 blendet Android beim Teilen außerdem Benachrichtigungen und Einmalcodes aus.

## Optional: TURN-Server

In den allermeisten Fällen klappt die direkte Verbindung über die öffentlichen STUN-Server von Google und Cloudflare. Manche Mobilfunknetze (CGNAT) blockieren direkte Verbindungen. Dann meldet die App „Verbindung fehlgeschlagen“. Abhilfe schafft ein kostenloser TURN-Server, z. B. Cloudflare Realtime TURN (großzügiges Gratis-Kontingent) oder Metered Open Relay. Trage dafür URL(s) (kommagetrennt), Benutzer und Passwort als Secrets `TURN_URL`, `TURN_USER` und `TURN_PASS` ein und baue neu.

## Projektstruktur

```
app/src/main/java/de/controlme/app/
  core/    Firebase, WebRTC, Einstellungen, Benachrichtigungen
  client/  Handy, das Hilfe bekommt: Bereitschaftsdienst, Bildschirmübertragung,
           Bedienungshilfe (Gesten), Einrichtungs-Bildschirm
  helper/  Helfer: Geräteliste, Kopplung, Fernsteuerungs-Bildschirm
  ui/      Material-You-Theme, Rollenauswahl
firebase/firestore.rules   Sicherheitsregeln
```

Technik: Kotlin, Jetpack Compose, Material 3 mit dynamischen Farben (Material You), Edge-to-Edge, `minSdk 34` (Android 14), `targetSdk 36` (Android 16).
