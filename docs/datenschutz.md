# Datenschutzerklärung – Fernhilfe

Stand: November 2026

Die App „Fernhilfe“ (`de.controlme.app`) ist eine private, nicht-kommerzielle App, mit der Familienmitglieder sich gegenseitig am Smartphone helfen können. Sie enthält keine Werbung und kein Tracking.

## Welche Daten verarbeitet werden

**Kopplung und Verbindungsaufbau (Google Firebase, Standort Frankfurt):**
- eine zufällige, anonyme Kennung je Gerät (Firebase Anonymous Authentication), es werden kein Name, keine E-Mail-Adresse und keine Telefonnummer abgefragt
- Gerätename und Modell (z. B. „Pixel 8“) sowie der Zeitpunkt der letzten Erreichbarkeit
- der selbst gewählte Anzeigename des Helfers
- technische Verbindungsdaten für den Aufbau der Direktverbindung (WebRTC-Sitzungsbeschreibungen und Netzwerk-Kandidaten, die IP-Adressen enthalten)

**Bildschirmübertragung und Fernsteuerung:**
Bildschirminhalt und Steuerbefehle (Tippen, Wischen, Text) werden **direkt und Ende-zu-Ende verschlüsselt** (WebRTC/DTLS-SRTP) zwischen den beiden gekoppelten Geräten übertragen. Sie werden nicht auf einem Server gespeichert und nicht aufgezeichnet. Ist kein direkter Weg möglich, kann ein Relay-Server (TURN) die verschlüsselten Daten durchleiten, ohne sie lesen zu können.

## Bedienungshilfe (AccessibilityService)

Die App nutzt die Android-Bedienungshilfen-Schnittstelle ausschließlich, um während einer vom Nutzer freigegebenen Fernhilfe-Sitzung die Tipp-, Wisch- und Texteingaben des gekoppelten Helfers auszuführen sowie den System-Dialog zur Bildschirmfreigabe zu bestätigen. Über diese Schnittstelle werden **keine Daten erhoben, gespeichert oder weitergegeben**.

## Speicherdauer und Löschung

Daten in Firebase bleiben bestehen, solange die Geräte gekoppelt sind. Mit „App zurücksetzen“ oder durch Deinstallation wird die Kopplung beendet. Auf Wunsch werden alle Daten gelöscht – Kontakt siehe unten.

## Weitergabe

Es erfolgt keine Weitergabe an Dritte. Auftragsverarbeiter ist Google (Firebase) gemäß den Google-Cloud-Datenschutzbedingungen.

## Kontakt

Betreiber: der Herausgeber dieser App im Google Play Store (Kontaktdaten siehe Store-Eintrag).
