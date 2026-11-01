package dev.saturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Saturn-Menü (Rechts-Shift). Eingaben werden bewusst per Polling in render() gelesen,
 * damit der Code die Signaturänderungen der Maus-/Tastatur-Events (1.21.9+) nicht mitmachen muss.
 */
public class MenuScreen extends Screen {
    private static final int PW = 440, PH = 312, ROW = 24;
    private static final int SCALE = 0, BG = 1, SHADOW = 2, BOOL = 3, NUM = 4, RESET = 5;

    private record Row(int kind, String key, String label, float min, float max, float step, float def, String unit, int y, int h) {}

    private final Minecraft mc = Minecraft.getInstance();
    private int tab = 0;
    private Module sel;
    private Module drag;
    private Row slide;
    private boolean down;
    private int lastX, lastY;

    public MenuScreen() {
        super(Component.literal("Saturn"));
        sel = inTab().get(0);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        Config.save();
        super.onClose();
    }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }
    private boolean inPanel(double x, double y) { return Ui.hit(x, y, px(), py(), PW, PH); }

    private List<Module> inTab() {
        List<Module> l = new ArrayList<>();
        for (Module m : Hud.ALL) if (m.cat.equals(Hud.CATS[tab])) l.add(m);
        return l;
    }

    // ---------------------------------------------------------------- Einstellungszeilen
    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        int y = py() + 100;
        if (sel instanceof HudModule h) {
            out.add(new Row(SCALE, "", "Größe", 0.5f, 3f, 0.05f, 1f, "x", y, 34)); y += 34;
            if (h.hasBg()) { out.add(new Row(BG, "", "Hintergrund", 0, 1, 1, 1, "", y, 22)); y += 22; }
            out.add(new Row(SHADOW, "", "Textschatten", 0, 1, 1, 1, "", y, 22)); y += 22;
        }
        for (Setting st : sel.settings()) {
            if (st.bool()) { out.add(new Row(BOOL, st.key(), st.label(), 0, 1, 1, st.def(), "", y, 22)); y += 22; }
            else { out.add(new Row(NUM, st.key(), st.label(), st.min(), st.max(), st.step(), st.def(), st.unit(), y, 34)); y += 34; }
        }
        if (sel instanceof HudModule) out.add(new Row(RESET, "", "Position zurücksetzen", 0, 0, 0, 0, "", y + 4, 22));
        return out;
    }

    private float get(Row r) {
        return switch (r.kind) {
            case SCALE -> sel.s.scale;
            case BG -> sel.s.background ? 1f : 0f;
            case SHADOW -> sel.s.shadow ? 1f : 0f;
            default -> sel.s.val(r.key, r.def);
        };
    }

    private void set(Row r, float v) {
        switch (r.kind) {
            case SCALE -> sel.s.scale = v;
            case BG -> sel.s.background = v >= 0.5f;
            case SHADOW -> sel.s.shadow = v >= 0.5f;
            default -> sel.s.put(r.key, v);
        }
    }

    // ---------------------------------------------------------------- Zeichnen
    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x55000000);

        for (Module m : Hud.ALL) {
            if (!(m instanceof HudModule h) || !h.s.enabled) continue;
            float[] b = h.bounds(mc);
            boolean active = m == sel || m == drag;
            if (h.custom()) {
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

        Ui.text(g, "S A T U R N", x + 14, y + 12, Ui.ACCENT);
        Ui.text(g, "x", x + PW - 18, y + 12, Ui.MUTED);
        g.fill(x + 14, y + 30, x + PW - 14, y + 31, Ui.BORDER);

        // Reiter
        int tx = x + 14;
        for (int i = 0; i < Hud.CATS.length; i++) {
            int tw = Ui.width(Hud.CATS[i]) + 20;
            boolean on = i == tab, hov = Ui.hit(mx, my, tx, y + 38, tw, 20);
            if (on) Ui.rrect(g, tx, y + 38, tw, 20, 0xFF20232A);
            else if (hov) Ui.rrect(g, tx, y + 38, tw, 20, Ui.HOVER);
            Ui.text(g, Hud.CATS[i], tx + 10, y + 44, on ? Ui.ACCENT : 0xFFB9BCC4);
            tx += tw + 4;
        }

        // linke Spalte: Module dieses Reiters
        int lx = x + 14, ly = y + 68;
        List<Module> mods = inTab();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int ry = ly + i * ROW;
            boolean hov = Ui.hit(mx, my, lx, ry, 152, ROW - 2);
            if (m == sel) Ui.rrect(g, lx, ry, 152, ROW - 2, 0xFF20232A);
            else if (hov) Ui.rrect(g, lx, ry, 152, ROW - 2, Ui.HOVER);
            Ui.text(g, m.name, lx + 8, ry + 7, m == sel ? Ui.TEXT : 0xFFB9BCC4);
            Ui.toggle(g, lx + 152 - 32, ry + 5, m.s.enabled);
        }

        // rechte Spalte
        int rx = x + 192, rw = PW - 192 - 14;
        g.fill(rx - 12, y + 68, rx - 11, y + PH - 30, Ui.BORDER);
        Ui.text(g, sel.name, rx, y + 70, Ui.TEXT);
        Ui.text(g, mc.font.plainSubstrByWidth(sel.desc, rw), rx, y + 82, Ui.MUTED);

        for (Row r : rows()) {
            if (r.kind == RESET) {
                boolean hr = Ui.hit(mx, my, rx, r.y, rw, r.h);
                Ui.rrect(g, rx, r.y, rw, r.h, hr ? 0xFF2A2D35 : 0xFF20232A);
                Ui.text(g, r.label, rx + (rw - Ui.width(r.label)) / 2, r.y + 7, Ui.TEXT);
            } else if (r.kind == SCALE || r.kind == NUM) {
                float v = get(r);
                String vs = r.kind == SCALE
                    ? String.format(Locale.ROOT, "%.2fx", v)
                    : (r.step >= 1f ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v)) + r.unit;
                Ui.text(g, r.label, rx, r.y + 2, 0xFFB9BCC4);
                Ui.text(g, vs, rx + rw - Ui.width(vs), r.y + 2, Ui.MUTED);
                float t = (v - r.min) / (r.max - r.min);
                Ui.rrect(g, rx, r.y + 18, rw, 4, Ui.OFF);
                Ui.rrect(g, rx, r.y + 18, Math.round(rw * t), 4, Ui.ACCENT);
                Ui.rrect(g, rx + Math.round((rw - 6) * t), r.y + 15, 6, 10, Ui.TEXT);
            } else {
                Ui.text(g, r.label, rx, r.y + 4, 0xFFB9BCC4);
                Ui.toggle(g, rx + rw - 26, r.y + 2, get(r) >= 0.5f);
            }
        }

        Ui.text(g, "Ziehen: verschieben   Mausrad: Größe   Esc: schließen", x + 14, y + PH - 18, Ui.MUTED);
    }

    // ---------------------------------------------------------------- Eingabe
    private void handleMouse(int mx, int my) {
        boolean now = mc.mouseHandler.isLeftPressed();
        if (now && !down) press(mx, my);
        else if (now && down) dragTo(mx, my);
        else if (!now && down) { drag = null; slide = null; }
        down = now;
        lastX = mx; lastY = my;
    }

    private void press(int mx, int my) {
        if (inPanel(mx, my)) { panelPress(mx, my); return; }
        for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
            if (!(Hud.ALL.get(i) instanceof HudModule h) || !h.s.enabled) continue;
            float[] b = h.bounds(mc);
            if (Ui.hit(mx, my, b[0], b[1], b[2], b[3])) { select(h); drag = h; return; }
        }
    }

    /** Wählt ein Modul und springt zu dessen Reiter. */
    private void select(Module m) {
        sel = m;
        for (int i = 0; i < Hud.CATS.length; i++) if (Hud.CATS[i].equals(m.cat)) tab = i;
    }

    private void panelPress(int mx, int my) {
        int x = px(), y = py();
        if (Ui.hit(mx, my, x + PW - 24, y + 6, 18, 18)) { onClose(); return; }

        int tx = x + 14;
        for (int i = 0; i < Hud.CATS.length; i++) {
            int tw = Ui.width(Hud.CATS[i]) + 20;
            if (Ui.hit(mx, my, tx, y + 38, tw, 20)) { tab = i; sel = inTab().get(0); return; }
            tx += tw + 4;
        }

        int lx = x + 14, ly = y + 68;
        List<Module> mods = inTab();
        for (int i = 0; i < mods.size(); i++) {
            int ry = ly + i * ROW;
            if (!Ui.hit(mx, my, lx, ry, 152, ROW - 2)) continue;
            Module m = mods.get(i);
            if (Ui.hit(mx, my, lx + 152 - 32, ry + 5, 26, 12)) m.s.enabled = !m.s.enabled;
            else sel = m;
            return;
        }

        int rx = x + 192, rw = PW - 192 - 14;
        for (Row r : rows()) {
            if (r.kind == RESET) {
                if (Ui.hit(mx, my, rx, r.y, rw, r.h) && sel instanceof HudModule h) h.resetPos();
            } else if (r.kind == SCALE || r.kind == NUM) {
                if (Ui.hit(mx, my, rx, r.y + 10, rw, 18)) { slide = r; slideTo(mx); return; }
            } else if (Ui.hit(mx, my, rx, r.y, rw, r.h)) {
                set(r, get(r) >= 0.5f ? 0f : 1f);
                return;
            }
        }
    }

    private void slideTo(int mx) {
        int rx = px() + 192, rw = PW - 192 - 14;
        float t = Math.max(0f, Math.min(1f, (mx - rx) / (float) rw));
        float v = slide.min + t * (slide.max - slide.min);
        v = Math.round(v / slide.step) * slide.step;
        set(slide, Math.max(slide.min, Math.min(slide.max, v)));
    }

    private void dragTo(int mx, int my) {
        if (slide != null) { slideTo(mx); return; }
        if (!(drag instanceof HudModule h)) return;
        float nx = h.s.x + (mx - lastX) / (float) width;
        float ny = h.s.y + (my - lastY) / (float) height;
        if (h.custom()) { h.s.x = clamp(nx, -0.6f, 0.4f); h.s.y = clamp(ny, -0.5f, 0.5f); }
        else { h.s.x = clamp(nx, 0f, 0.98f); h.s.y = clamp(ny, 0f, 0.98f); }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!inPanel(mx, my)) {
            for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
                if (!(Hud.ALL.get(i) instanceof HudModule h) || !h.s.enabled) continue;
                float[] b = h.bounds(mc);
                if (Ui.hit(mx, my, b[0], b[1], b[2], b[3])) {
                    select(h);
                    h.s.scale = clamp(Math.round((h.s.scale + (sy > 0 ? 0.1f : -0.1f)) * 20f) / 20f, 0.5f, 3f);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
