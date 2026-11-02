package dev.saturn;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class KeystrokesModule extends HudModule {
    private static final int K = 22, G = 2, SP = 14;

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Zeigt gedrückte Tasten an", "PvP", false, 0.01f, 0.62f);
    }

    @Override public boolean hasBg() { return false; }
    @Override protected List<String> lines(Minecraft mc) { return List.of(); }

    @Override public List<Setting> settings() {
        return List.of(
            Setting.toggle("mouse", "Maustasten anzeigen", true),
            Setting.toggle("cps", "CPS in Maustasten", true),
            Setting.toggle("space", "Leertaste anzeigen", true));
    }

    private int[] dims() {
        int w = 3 * K + 2 * G, h = 2 * K + G;
        if (s.on("mouse", true)) h += K + G;
        if (s.on("space", true)) h += SP + G;
        return new int[] { w, h };
    }

    @Override public float[] bounds(Minecraft mc) {
        int[] d = dims();
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        return new float[] { s.x * sw, s.y * sh, d[0] * s.scale, d[1] * s.scale };
    }

    @Override public void render(GuiGraphics g, Minecraft mc) {
        Cps.sample(mc);
        int[] d = dims();
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Compat.push(g);
        Compat.translate(g, s.x * sw, s.y * sh);
        Compat.scale(g, s.scale);

        var o = mc.options;
        key(g, K + G, 0, K, K, "W", o.keyUp.isDown());
        key(g, 0, K + G, K, K, "A", o.keyLeft.isDown());
        key(g, K + G, K + G, K, K, "S", o.keyDown.isDown());
        key(g, 2 * (K + G), K + G, K, K, "D", o.keyRight.isDown());
        int y = 2 * (K + G);
        if (s.on("mouse", true)) {
            int half = (d[0] - G) / 2;
            boolean cps = s.on("cps", true);
            key(g, 0, y, half, K, cps && Cps.left() > 0 ? String.valueOf(Cps.left()) : "LMB", o.keyAttack.isDown());
            key(g, half + G, y, half, K, cps && Cps.right() > 0 ? String.valueOf(Cps.right()) : "RMB", o.keyUse.isDown());
            y += K + G;
        }
        if (s.on("space", true)) key(g, 0, y, d[0], SP, "SPACE", o.keyJump.isDown());
        Compat.pop(g);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean down) {
        Ui.rrect(g, x, y, w, h, down ? 0xF0E3B04B : 0xA0111216);
        Ui.text(g, label, x + (w - Ui.width(label)) / 2, y + (h - 8) / 2, down ? 0xFF17130A : 0xFFFFFFFF, s.shadow && !down);
    }
}
