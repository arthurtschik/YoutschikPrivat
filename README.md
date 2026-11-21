# Youtschik

YouTube-Link teilen → DOWNLOAD → MP3 (Fallback: m4a/webm, automatisch).

## Einrichtung (einmalig)
1. GitHub → Settings → Developer settings → Fine-grained token: nur dieses Repo, Berechtigung *Contents: Read-only*.
2. Repo → Settings → Secrets and variables → Actions → Secret `UPDATE_TOKEN` mit dem Token anlegen.
3. Jeder Push auf `main` baut die APK und veröffentlicht sie als Release `build-N`. Die App erkennt neuere Releases selbst.

Die Release-Signatur (`app/release.jks`) muss gleich bleiben, sonst lassen sich Updates nicht installieren.

Update-Test: dieser Build dient nur dem Test der Update-Funktion.
