package dev.saturn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Kleine Zeichenhelfer: flach, ein Akzent, keine Verläufe. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xF0111216, BORDER = 0xFF262930, TEXT = 0xFFE9E7E2,
        MUTED = 0xFF8A8E98, ACCENT = 0xFFE3B04B, OFF = 0xFF2C2F36, HOVER = 0xFF1B1D23;

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int c) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), c);
    }

    /** Rechteck mit abgeschnittenen Ecken (1 px). */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    public static void outline(GuiGraphics g, float x, float y, float w, float h, int c) {
        rect(g, x, y, w, 1, c); rect(g, x, y + h - 1, w, 1, c);
        rect(g, x, y, 1, h, c); rect(g, x + w - 1, y, 1, h, c);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int c, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, s, x, y, c, shadow);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int c) { text(g, s, x, y, c, false); }

    public static int width(String s) { return Minecraft.getInstance().font.width(s); }

    public static void toggle(GuiGraphics g, int x, int y, boolean on) {
        rrect(g, x, y, 26, 12, on ? ACCENT : OFF);
        rrect(g, on ? x + 16 : x + 2, y + 2, 8, 8, on ? 0xFF15161A : 0xFF9A9EA8);
    }

    public static boolean hit(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
