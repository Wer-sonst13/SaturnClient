package dev.saturn;

import java.util.List;
import net.minecraft.client.Minecraft;

/** Basis für alles, was man im Menü ein- und ausschalten kann. */
public abstract class Module {
    public final String id, name, desc, cat;
    public final Config.State s;

    protected Module(String id, String name, String desc, String cat, boolean defOn, float x, float y) {
        this.id = id; this.name = name; this.desc = desc; this.cat = cat;
        this.s = Config.get(id, defOn, x, y);
    }

    /** true = hat ein HUD-Element, das sich verschieben lässt. */
    public boolean hud() { return false; }
    public List<Setting> settings() { return List.of(); }
    /** Wird jeden Client-Tick aufgerufen (auch wenn aus, damit Aufräumen möglich ist). */
    public void tick(Minecraft mc) {}
}
