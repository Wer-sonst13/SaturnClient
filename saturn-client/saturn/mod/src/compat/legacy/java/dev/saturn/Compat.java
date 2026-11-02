package dev.saturn;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** 1.21.1 - 1.21.5: PoseStack-basiertes GUI, String-Kategorie, HudRenderCallback. */
public final class Compat {
    private Compat() {}

    public static KeyMapping createKey() {
        return new KeyMapping("key.saturn.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.saturn");
    }
    public static void push(GuiGraphics g) { g.pose().pushPose(); }
    public static void translate(GuiGraphics g, float x, float y) { g.pose().translate(x, y, 0f); }
    public static void scale(GuiGraphics g, float s) { g.pose().scale(s, s, 1f); }
    public static void pop(GuiGraphics g) { g.pose().popPose(); }
    public static void registerHud(Consumer<GuiGraphics> r) { HudRenderCallback.EVENT.register((g, dt) -> r.accept(g)); }

    public static void reconnect(Minecraft mc, ServerData sd) {
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
            ServerAddress.parseString(sd.ip), sd, false, null);
    }
}
