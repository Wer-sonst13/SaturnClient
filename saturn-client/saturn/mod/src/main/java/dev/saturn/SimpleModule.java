package dev.saturn;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;

public final class SimpleModule extends HudModule {
    private final Function<Minecraft, List<String>> src;

    public SimpleModule(String id, String name, String desc, boolean on, float x, float y,
                        Function<Minecraft, List<String>> src) {
        super(id, name, desc, on, x, y);
        this.src = src;
    }

    @Override protected List<String> lines(Minecraft mc) { return src.apply(mc); }
}
