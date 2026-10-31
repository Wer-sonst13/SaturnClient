package dev.saturn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Saturn-Menü (Rechts-Shift). Eingaben werden bewusst per Polling in render() gelesen,
 * damit der Code die Signaturänderungen der Maus-/Tastatur-Events (1.21.9+) nicht mitmachen muss.
 */
public class MenuScreen extends Screen {
    private static final int PW = 360, PH = 236, ROW = 22;

    private final Minecraft mc = Minecraft.getInstance();
    private HudModule sel = Hud.ALL.get(0), drag;
    private boolean down, sliding;
    private int lastX, lastY;

    public MenuScreen() { super(Component.literal("Saturn")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        Config.save();
        super.onClose();
    }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }
    private boolean inPanel(double x, double y) { return Ui.hit(x, y, px(), py(), PW, PH); }

    // ---------------------------------------------------------------- Zeichnen
    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x55000000);

        for (HudModule m : Hud.ALL) {
            if (!m.s.enabled) continue;
            float[] b = m.bounds(mc);
            boolean active = m == sel || m == drag;
            if (m.custom()) {
                Ui.rect(g, b[0], b[1], b[2], b[3], 0x30FFFFFF);
                Ui.text(g, "Scoreboard", Math.round(b[0]) + 4, Math.round(b[1]) + 4, Ui.MUTED);
            }
            Ui.outline(g, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, active ? Ui.ACCENT : 0x55FFFFFF);
        }

        handleMouse(mx, my);
        drawPanel(g, mx, my);
    }

    private void drawPanel(GuiGraphics g, int mx, int my) {
        int x = px(), y = py();
        Ui.rrect(g, x - 1, y - 1, PW + 2, PH + 2, Ui.BORDER);
        Ui.rrect(g, x, y, PW, PH, Ui.BG);

        Ui.text(g, "S A T U R N", x + 12, y + 12, Ui.ACCENT);
        Ui.text(g, "x", x + PW - 18, y + 12, Ui.MUTED);
        g.fill(x + 12, y + 30, x + PW - 12, y + 31, Ui.BORDER);

        // linke Spalte: Module
        int lx = x + 12, ly = y + 40;
        for (int i = 0; i < Hud.ALL.size(); i++) {
            HudModule m = Hud.ALL.get(i);
            int ry = ly + i * ROW;
            boolean hov = Ui.hit(mx, my, lx, ry, 150, ROW - 2);
            if (m == sel) Ui.rrect(g, lx, ry, 150, ROW - 2, 0xFF20232A);
            else if (hov) Ui.rrect(g, lx, ry, 150, ROW - 2, Ui.HOVER);
            Ui.text(g, m.name, lx + 8, ry + 6, m == sel ? Ui.TEXT : 0xFFB9BCC4);
            Ui.toggle(g, lx + 150 - 32, ry + 4, m.s.enabled);
        }

        // rechte Spalte: Einstellungen des gewählten Moduls
        int rx = x + 186, rw = PW - 186 - 12, ry = y + 40;
        g.fill(rx - 12, y + 40, rx - 11, y + PH - 30, Ui.BORDER);
        Ui.text(g, sel.name, rx, ry, Ui.TEXT);
        Ui.text(g, sel.desc, rx, ry + 12, Ui.MUTED);

        int sy = ry + 38;
        Ui.text(g, "Größe", rx, sy - 14, 0xFFB9BCC4);
        String sv = String.format(java.util.Locale.ROOT, "%.2fx", sel.s.scale);
        Ui.text(g, sv, rx + rw - Ui.width(sv), sy - 14, Ui.MUTED);
        Ui.rrect(g, rx, sy, rw, 4, Ui.OFF);
        float t = (sel.s.scale - 0.5f) / 2.5f;
        Ui.rrect(g, rx, sy, Math.round(rw * t), 4, Ui.ACCENT);
        Ui.rrect(g, rx + Math.round((rw - 6) * t), sy - 3, 6, 10, Ui.TEXT);

        int by = sy + 22;
        if (!sel.custom()) {
            Ui.text(g, "Hintergrund", rx, by + 2, 0xFFB9BCC4);
            Ui.toggle(g, rx + rw - 26, by, sel.s.background);
            Ui.text(g, "Textschatten", rx, by + 24, 0xFFB9BCC4);
            Ui.toggle(g, rx + rw - 26, by + 22, sel.s.shadow);
            by += 44;
        } else {
            Ui.text(g, "Aktiv = eigene Position & Größe.", rx, by, Ui.MUTED);
            Ui.text(g, "Aus = Vanilla-Verhalten.", rx, by + 11, Ui.MUTED);
            by += 34;
        }
        boolean hr = Ui.hit(mx, my, rx, by, rw, 18);
        Ui.rrect(g, rx, by, rw, 18, hr ? 0xFF2A2D35 : 0xFF20232A);
        String rt = "Position zurücksetzen";
        Ui.text(g, rt, rx + (rw - Ui.width(rt)) / 2, by + 5, Ui.TEXT);

        Ui.text(g, "Ziehen: verschieben   Mausrad: Größe   Esc: schließen", x + 12, y + PH - 18, Ui.MUTED);
    }

    // ---------------------------------------------------------------- Eingabe
    private void handleMouse(int mx, int my) {
        boolean now = mc.mouseHandler.isLeftPressed();
        if (now && !down) press(mx, my);
        else if (now && down) dragTo(mx, my);
        else if (!now && down) { drag = null; sliding = false; }
        down = now;
        lastX = mx; lastY = my;
    }

    private void press(int mx, int my) {
        if (inPanel(mx, my)) { panelPress(mx, my); return; }
        for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
            HudModule m = Hud.ALL.get(i);
            if (!m.s.enabled) continue;
            float[] b = m.bounds(mc);
            if (Ui.hit(mx, my, b[0], b[1], b[2], b[3])) { sel = m; drag = m; return; }
        }
    }

    private void panelPress(int mx, int my) {
        int x = px(), y = py();
        if (Ui.hit(mx, my, x + PW - 24, y + 6, 18, 18)) { onClose(); return; }

        int lx = x + 12, ly = y + 40;
        for (int i = 0; i < Hud.ALL.size(); i++) {
            HudModule m = Hud.ALL.get(i);
            int ry = ly + i * ROW;
            if (!Ui.hit(mx, my, lx, ry, 150, ROW - 2)) continue;
            if (Ui.hit(mx, my, lx + 150 - 32, ry + 4, 26, 12)) m.s.enabled = !m.s.enabled;
            else sel = m;
            return;
        }

        int rx = x + 186, rw = PW - 186 - 12, sy = y + 40 + 38;
        if (Ui.hit(mx, my, rx, sy - 5, rw, 14)) { sliding = true; setScale(mx); return; }
        int by = sy + 22;
        if (!sel.custom()) {
            if (Ui.hit(mx, my, rx, by - 2, rw, 18)) { sel.s.background = !sel.s.background; return; }
            if (Ui.hit(mx, my, rx, by + 20, rw, 18)) { sel.s.shadow = !sel.s.shadow; return; }
            by += 44;
        } else by += 34;
        if (Ui.hit(mx, my, rx, by, rw, 18)) sel.resetPos();
    }

    private void setScale(int mx) {
        int rx = px() + 186, rw = PW - 186 - 12;
        float t = Math.max(0f, Math.min(1f, (mx - rx) / (float) rw));
        sel.s.scale = Math.round((0.5f + t * 2.5f) * 20f) / 20f;
    }

    private void dragTo(int mx, int my) {
        if (sliding) { setScale(mx); return; }
        if (drag == null) return;
        float nx = drag.s.x + (mx - lastX) / (float) width;
        float ny = drag.s.y + (my - lastY) / (float) height;
        if (drag.custom()) { drag.s.x = clamp(nx, -0.6f, 0.4f); drag.s.y = clamp(ny, -0.5f, 0.5f); }
        else { drag.s.x = clamp(nx, 0f, 0.98f); drag.s.y = clamp(ny, 0f, 0.98f); }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!inPanel(mx, my)) {
            for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
                HudModule m = Hud.ALL.get(i);
                if (!m.s.enabled) continue;
                float[] b = m.bounds(mc);
                if (Ui.hit(mx, my, b[0], b[1], b[2], b[3])) {
                    sel = m;
                    m.s.scale = clamp(Math.round((m.s.scale + (sy > 0 ? 0.1f : -0.1f)) * 20f) / 20f, 0.5f, 3f);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
