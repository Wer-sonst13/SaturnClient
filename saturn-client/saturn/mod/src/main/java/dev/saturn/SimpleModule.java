package dev.saturn;

import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;

public final class SimpleModule extends HudModule {
    private final List<Setting> settings;
    private final BiFunction<Minecraft, Config.State, List<String>> src;

    public SimpleModule(String id, String name, String desc, String cat, boolean on, float x, float y,
                        List<Setting> settings, BiFunction<Minecraft, Config.State, List<String>> src) {
        super(id, name, desc, cat, on, x, y);
        this.settings = settings;
        this.src = src;
    }

    @Override public List<Setting> settings() { return settings; }
    @Override protected List<String> lines(Minecraft mc) { return src.apply(mc, s); }
}
