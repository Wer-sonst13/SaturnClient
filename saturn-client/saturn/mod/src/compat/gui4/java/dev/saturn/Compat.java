package dev.saturn;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** 1.21.11: wie gui3, aber ResourceLocation heißt jetzt Identifier. */
public final class Compat {
    private Compat() {}

    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("saturn", "main"));

    public static KeyMapping createKey() {
        return new KeyMapping("key.saturn.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY);
    }
    public static void push(GuiGraphics g) { g.pose().pushMatrix(); }
    public static void translate(GuiGraphics g, float x, float y) { g.pose().translate(x, y); }
    public static void scale(GuiGraphics g, float s) { g.pose().scale(s, s); }
    public static void pop(GuiGraphics g) { g.pose().popMatrix(); }
    public static void registerHud(Consumer<GuiGraphics> r) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("saturn", "hud"), (g, dt) -> r.accept(g));
    }
}
