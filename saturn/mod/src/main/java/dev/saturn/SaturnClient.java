package dev.saturn;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class SaturnClient implements ClientModInitializer {
    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();                       // muss vor Hud.* geladen werden
        menuKey = KeyBindingHelper.registerKeyBinding(Compat.createKey());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new MenuScreen());
            }
            for (Module m : Hud.ALL) m.tick(mc);
        });
        Compat.registerHud(Hud::render);
    }
}
