package dev.saturn;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Hud {
    private Hud() {}

    private static final List<String> NONE = List.of();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public static final ScoreboardModule scoreboard = new ScoreboardModule();

    public static final List<HudModule> ALL = List.of(
        new SimpleModule("fps", "FPS", "Bilder pro Sekunde", true, 0.01f, 0.02f,
            mc -> List.of(mc.getFps() + " FPS")),
        new SimpleModule("coords", "Koordinaten", "Position und Blickrichtung", false, 0.01f, 0.09f,
            mc -> mc.player == null ? NONE : List.of(
                String.format(Locale.ROOT, "XYZ %.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                mc.player.getDirection().getName())),
        new SimpleModule("ping", "Ping", "Latenz zum Server", false, 0.01f, 0.17f, mc -> {
            if (mc.player == null || mc.getConnection() == null) return NONE;
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? NONE : List.of(info.getLatency() + " ms");
        }),
        new SimpleModule("clock", "Uhrzeit", "Lokale Uhrzeit", false, 0.01f, 0.23f,
            mc -> List.of(LocalTime.now().format(CLOCK))),
        new SimpleModule("spotify", "Spotify", "Aktueller Song der Spotify-App", false, 0.01f, 0.29f, mc -> {
            String t = Spotify.track();
            return t.isEmpty() ? NONE : List.of("\u266A " + t);
        }),
        scoreboard
    );

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        for (HudModule m : ALL) if (m.s.enabled && !m.custom()) m.render(g, mc);
    }
}
