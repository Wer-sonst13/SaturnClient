package dev.saturn;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Volle Helligkeit: ein unsichtbarer, nur lokaler Nachtsicht-Effekt (wird nie an den Server gesendet). */
public final class FullbrightModule extends Module {
    private boolean applied;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Alles hell, auch in Höhlen", "Visuell", false, 0f, 0f);
    }

    @Override public void tick(Minecraft mc) {
        if (mc.player == null) { applied = false; return; }
        MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (s.enabled) {
            if (cur == null || cur.getDuration() < 400_000) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false, false));
            }
            applied = true;
        } else if (applied) {
            if (cur != null && cur.getDuration() > 500_000) mc.player.removeEffect(MobEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
