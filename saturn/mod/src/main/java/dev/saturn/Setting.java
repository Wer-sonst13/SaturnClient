package dev.saturn;

/** Eine einstellbare Option eines Moduls (Schalter oder Zahlen-Regler). */
public record Setting(String key, String label, boolean bool, float min, float max, float step, float def, String unit) {
    public static Setting toggle(String key, String label, boolean def) {
        return new Setting(key, label, true, 0f, 1f, 1f, def ? 1f : 0f, "");
    }
    public static Setting num(String key, String label, float min, float max, float step, float def, String unit) {
        return new Setting(key, label, false, min, max, step, def, unit);
    }
}
