# Saturn

Fabric-Client-Mod (HUD + Menü) und ein schlanker Launcher, der Saturn bei deinen Freunden installiert und aktuell hält.

- **Mod** (`mod/`): Rechts-Shift öffnet das Menü. Reiter **HUD** (FPS, Koordinaten, Ping, Uhrzeit, Spotify), **PvP** (Keystrokes, CPS, Effekte, Auto-Sprint), **Visuell** (Scoreboard verschieben/skalieren/ausblenden, Fullbright, Keine Partikel), **Sonstiges** (Auto-Reconnect). Im Menü: Elemente mit der Maus ziehen, Mausrad = Größe.
- **Launcher** (`launcher/`): eigenständiger Minecraft-Launcher (Tauri, wenige MB). Eigene Instanzen (Fabric, 1.21.1 bis neueste), Minecraft/Java/Assets werden selbst geladen, Saturn und Fabric API werden bei jedem Start automatisch aktualisiert, Mods über Modrinth. Daten liegen in `%APPDATA%\Saturn`.
  Konto: vorerst Offline-Name. Der Microsoft-Login wird später ergänzt (Azure-Client-ID nötig).

## Einrichten
1. Neues GitHub-Repo anlegen, diesen Ordner hochladen (`.github/` muss im Root liegen).
2. Release erstellen: `git tag v1.0.0 && git push origin v1.0.0`.
3. Der Workflow baut alle Versionen aus `mc-versions.json` plus `Saturn-Launcher.exe` und den Installer (`Saturn_*_x64-setup.exe`) und hängt alles an das Release.
4. Freunde laden `Saturn-Launcher.exe` aus dem Release, wählen eine Version, klicken „Installieren / Aktualisieren“.

## Updates
- Saturn: neuen Tag pushen → neues Release. Beim Start prüft der Launcher alle installierten Versionen und aktualisiert sie automatisch auf `releases/latest`.
- Fabric API / Loader: werden beim Build und im Launcher live von Fabric-Maven bzw. Modrinth geholt.
- Neue Minecraft-Version: `check-versions.yml` öffnet montags ein Issue. Version in `mc-versions.json` eintragen.

## Lokal bauen
```
cd mod && gradle build -Pmc=1.21.4      # Jar: mod/build/libs/saturn-1.21.4.jar
cd launcher && npx @tauri-apps/cli@2 dev
```

## Versionsabdeckung
`mod/src/compat/` enthält pro API-Generation eine kleine `Compat.java`:
`legacy` = 1.21.1–1.21.5, `gui2` = 1.21.6–1.21.8, `gui3` = 1.21.9–1.21.10, `gui4` = 1.21.11.
Minecraft 26.x (unobfuskiert, Java 25, Loom-Plugin `net.fabricmc.fabric-loom`) braucht eine eigene Schicht und ist noch nicht enthalten.
Falls der Loom-Build mit „plugin not found“ abbricht: Version in `mod/build.gradle` (`1.15-SNAPSHOT`) anpassen.
