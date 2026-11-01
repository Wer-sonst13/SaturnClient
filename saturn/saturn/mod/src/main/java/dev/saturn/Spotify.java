package dev.saturn;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Liest den laufenden Song aus der Spotify-Desktop-App. Kein Login, keine API-Keys. */
final class Spotify {
    private Spotify() {}

    private static volatile String track = "";
    private static volatile long lastAsk;
    private static volatile boolean running;

    static String track() {
        lastAsk = System.currentTimeMillis();
        if (!running) {
            running = true;
            Thread t = new Thread(Spotify::loop, "saturn-spotify");
            t.setDaemon(true);
            t.start();
        }
        return track;
    }

    // Läuft nur, solange das HUD den Wert auch wirklich abfragt.
    private static void loop() {
        try {
            while (System.currentTimeMillis() - lastAsk < 15_000) {
                track = read();
                Thread.sleep(4000);
            }
        } catch (InterruptedException ignored) {
        } finally {
            running = false;
            track = "";
        }
    }

    private static String read() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] cmd;
            if (os.contains("win")) {
                cmd = new String[] { "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                    + "(Get-Process Spotify -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowTitle} | Select-Object -First 1).MainWindowTitle" };
            } else if (os.contains("mac")) {
                cmd = new String[] { "osascript", "-e",
                    "if application \"Spotify\" is running then tell application \"Spotify\" to if player state is playing then return (artist of current track) & \" - \" & (name of current track)" };
            } else {
                cmd = new String[] { "playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}" };
            }
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
            // Pausiert/Leerlauf: Fenstertitel ist nur "Spotify", "Spotify Free" oder "Spotify Premium"
            if (out.isEmpty() || out.equals("Spotify") || out.startsWith("Spotify ")) return "";
            return out.length() > 34 ? out.substring(0, 33) + "\u2026" : out;
        } catch (Exception e) {
            return "";
        }
    }
}
