package dev.saturn;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;

/** Verbindet nach einem Kick/Verbindungsabbruch automatisch neu mit dem letzten Server. */
public final class AutoReconnectModule extends Module {
    private ServerData last;
    private long deadline;
    private int tries;

    public AutoReconnectModule() {
        super("autoreconnect", "Auto-Reconnect", "Verbindet nach Abbruch neu", "Sonstiges", false, 0f, 0f);
    }

    @Override public List<Setting> settings() {
        return List.of(
            Setting.num("delay", "Wartezeit", 1f, 30f, 1f, 5f, " s"),
            Setting.num("max", "Max. Versuche", 1f, 20f, 1f, 10f, ""));
    }

    @Override public void tick(Minecraft mc) {
        if (mc.level != null) {                       // verbunden: Server merken, Zähler zurücksetzen
            ServerData sd = mc.getCurrentServer();
            if (sd != null) last = sd;
            tries = 0; deadline = 0;
            return;
        }
        if (!s.enabled || last == null) return;
        if (!(mc.screen instanceof DisconnectedScreen)) { deadline = 0; return; }
        long now = System.currentTimeMillis();
        if (deadline == 0) { deadline = now + (long) (s.val("delay", 5f) * 1000); return; }
        if (now >= deadline && tries < (int) s.val("max", 10f)) {
            tries++; deadline = 0;
            Compat.reconnect(mc, last);
        }
    }
}
