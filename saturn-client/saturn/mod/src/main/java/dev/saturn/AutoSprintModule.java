package dev.saturn;

import net.minecraft.client.Minecraft;

/** Sprintet automatisch, solange vorwärts gedrückt wird. */
public final class AutoSprintModule extends Module {
    private boolean applied;

    public AutoSprintModule() {
        super("autosprint", "Auto-Sprint", "Sprintet automatisch beim Laufen", "PvP", false, 0f, 0f);
    }

    @Override public void tick(Minecraft mc) {
        if (!s.enabled || mc.player == null) {
            if (applied) { mc.options.keySprint.setDown(false); applied = false; }
            return;
        }
        boolean fwd = mc.options.keyUp.isDown();
        if (fwd || applied) mc.options.keySprint.setDown(fwd);
        applied = fwd;
    }
}
