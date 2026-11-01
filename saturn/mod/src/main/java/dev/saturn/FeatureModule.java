package dev.saturn;

/** Einfacher Schalter ohne eigene Logik (die Wirkung steckt in einem Mixin). */
public final class FeatureModule extends Module {
    public FeatureModule(String id, String name, String desc, String cat, boolean on) {
        super(id, name, desc, cat, on, 0f, 0f);
    }
}
