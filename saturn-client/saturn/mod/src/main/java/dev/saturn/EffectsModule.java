package dev.saturn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

/** Liste der aktiven Tränke/Effekte mit Restzeit. */
public final class EffectsModule extends HudModule {
    public EffectsModule() {
        super("effects", "Effekte", "Aktive Tränke mit Restzeit", "PvP", false, 0.82f, 0.02f);
    }

    @Override protected List<String> lines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (e.getDuration() > 500_000) continue;            // Fullbright-Effekt nicht anzeigen
            String name = e.getEffect().value().getDisplayName().getString();
            String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            int sec = Math.max(0, e.getDuration() / 20);
            out.add(name + lvl + "  " + (e.getDuration() < 0 ? "**:**" : String.format("%d:%02d", sec / 60, sec % 60)));
        }
        return out;
    }
}
