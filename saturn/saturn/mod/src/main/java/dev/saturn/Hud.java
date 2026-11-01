package dev.saturn;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Hud {
    private Hud() {}

    public static final String[] CATS = { "HUD", "PvP", "Visuell", "Sonstiges" };

    private static final List<String> NONE = List.of();
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    public static final ScoreboardModule scoreboard = new ScoreboardModule();
    public static final FeatureModule noParticles =
        new FeatureModule("noparticles", "Keine Partikel", "Blendet alle Partikel aus", "Visuell", false);

    public static final List<Module> ALL = List.of(
        // --- HUD
        new SimpleModule("fps", "FPS", "Bilder pro Sekunde", "HUD", true, 0.01f, 0.02f, List.of(),
            (mc, st) -> List.of(mc.getFps() + " FPS")),
        new SimpleModule("coords", "Koordinaten", "Position und Blickrichtung", "HUD", false, 0.01f, 0.09f,
            List.of(Setting.toggle("dir", "Blickrichtung zeigen", true)),
            (mc, st) -> {
                if (mc.player == null) return NONE;
                String xyz = String.format(Locale.ROOT, "XYZ %.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                return st.on("dir", true) ? List.of(xyz, mc.player.getDirection().getName()) : List.of(xyz);
            }),
        new SimpleModule("ping", "Ping", "Latenz zum Server", "HUD", false, 0.01f, 0.17f, List.of(),
            (mc, st) -> {
                if (mc.player == null || mc.getConnection() == null) return NONE;
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                return info == null ? NONE : List.of(info.getLatency() + " ms");
            }),
        new SimpleModule("clock", "Uhrzeit", "Lokale Uhrzeit", "HUD", false, 0.01f, 0.23f,
            List.of(Setting.toggle("h24", "24-Stunden-Format", true)),
            (mc, st) -> List.of(LocalTime.now().format(st.on("h24", true) ? H24 : H12))),
        new SimpleModule("spotify", "Spotify", "Aktueller Song der Spotify-App", "HUD", false, 0.01f, 0.29f, List.of(),
            (mc, st) -> {
                String t = Spotify.track();
                return t.isEmpty() ? NONE : List.of("\u266A " + t);
            }),
        // --- PvP
        new KeystrokesModule(),
        new SimpleModule("cps", "CPS", "Klicks pro Sekunde", "PvP", false, 0.01f, 0.45f,
            List.of(Setting.toggle("right", "Rechtsklick anzeigen", true)),
            (mc, st) -> {
                Cps.sample(mc);
                return List.of(st.on("right", true) ? "CPS " + Cps.left() + " | " + Cps.right() : "CPS " + Cps.left());
            }),
        new EffectsModule(),
        new AutoSprintModule(),
        // --- Visuell
        scoreboard,
        new FullbrightModule(),
        noParticles,
        // --- Sonstiges
        new AutoReconnectModule()
    );

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        for (Module m : ALL) {
            if (m instanceof HudModule h && h.s.enabled && !h.custom()) h.render(g, mc);
        }
    }
}
