package dev.saturn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
    private Config() {}

    public static final class State {
        public boolean enabled;
        public float x, y;            // Anteil der Bildschirmgröße (Scoreboard: Versatz)
        public float scale = 1f;
        public boolean background = true;
        public boolean shadow = true;
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("saturn.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, State> map = new HashMap<>();

    public static State get(String id, boolean enabled, float x, float y) {
        return map.computeIfAbsent(id, k -> {
            State s = new State();
            s.enabled = enabled; s.x = x; s.y = y;
            return s;
        });
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Type t = new TypeToken<Map<String, State>>() {}.getType();
            Map<String, State> m = GSON.fromJson(Files.readString(FILE), t);
            if (m != null) map = m;
        } catch (Exception ignored) { }
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(map)); } catch (IOException ignored) { }
    }
}
