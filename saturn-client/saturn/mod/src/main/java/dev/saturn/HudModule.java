package dev.saturn;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudModule {
    public final String id, name, desc;
    public final float defX, defY;
    public final Config.State s;

    protected HudModule(String id, String name, String desc, boolean defOn, float defX, float defY) {
        this.id = id; this.name = name; this.desc = desc; this.defX = defX; this.defY = defY;
        this.s = Config.get(id, defOn, defX, defY);
    }

    protected abstract List<String> lines(Minecraft mc);

    /** true = kein eigenes Rendern, sondern Transformation eines Vanilla-Elements. */
    public boolean custom() { return false; }

    public void resetPos() { s.x = defX; s.y = defY; s.scale = 1f; }

    protected int[] size(List<String> l) {
        int w = 0;
        for (String t : l) w = Math.max(w, Ui.width(t));
        return new int[] { w + 10, l.size() * 10 + 4 };
    }

    /** {x, y, breite, höhe} in GUI-Pixeln, inkl. Skalierung. */
    public float[] bounds(Minecraft mc) {
        int[] sz = size(lines(mc));
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        return new float[] { s.x * sw, s.y * sh, sz[0] * s.scale, sz[1] * s.scale };
    }

    public void render(GuiGraphics g, Minecraft mc) {
        List<String> l = lines(mc);
        if (l.isEmpty()) return;
        int[] sz = size(l);
        float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Compat.push(g);
        Compat.translate(g, s.x * sw, s.y * sh);
        Compat.scale(g, s.scale);
        if (s.background) {
            Ui.rrect(g, 0, 0, sz[0], sz[1], 0xA0111216);
            Ui.rect(g, 0, 1, 2, sz[1] - 2, Ui.ACCENT);
        }
        for (int i = 0; i < l.size(); i++) Ui.text(g, l.get(i), 6, 3 + i * 10, 0xFFFFFFFF, s.shadow);
        Compat.pop(g);
    }
}
