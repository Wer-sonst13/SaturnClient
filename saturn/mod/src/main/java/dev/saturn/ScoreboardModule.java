package dev.saturn;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Verschiebt/skaliert das Vanilla-Scoreboard (per Mixin). Aktiv = eigene Position. */
public final class ScoreboardModule extends HudModule {
    static final int W = 112, H = 88;     // Platzhaltergröße im Editor
    private static boolean pushed;

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard", "Größe und Position der Seitenleiste", false, 0f, 0f);
    }

    @Override protected List<String> lines(Minecraft mc) { return List.of(); }
    @Override public boolean custom() { return true; }

    @Override public float[] bounds(Minecraft mc) {
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        return new float[] { sw + s.x * sw - W * s.scale, sh / 2f + s.y * sh - H * s.scale / 2f, W * s.scale, H * s.scale };
    }

    // Pivot = rechter Rand, vertikale Mitte (dort sitzt das Vanilla-Scoreboard)
    public static void begin(GuiGraphics g) {
        ScoreboardModule m = Hud.scoreboard;
        if (!m.s.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Compat.push(g);
        Compat.translate(g, sw + m.s.x * sw, sh / 2f + m.s.y * sh);
        Compat.scale(g, m.s.scale);
        Compat.translate(g, -sw, -sh / 2f);
        pushed = true;
    }

    public static void end(GuiGraphics g) {
        if (pushed) { Compat.pop(g); pushed = false; }
    }
}
