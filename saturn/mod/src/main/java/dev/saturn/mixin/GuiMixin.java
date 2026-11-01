package dev.saturn.mixin;

import dev.saturn.ScoreboardModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void saturn$begin(GuiGraphics g, Objective o, CallbackInfo ci) {
        if (ScoreboardModule.begin(g)) ci.cancel();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void saturn$end(GuiGraphics g, Objective o, CallbackInfo ci) { ScoreboardModule.end(g); }
}
