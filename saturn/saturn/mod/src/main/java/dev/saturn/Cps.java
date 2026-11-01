package dev.saturn;

import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;

/** Zählt Klicks pro Sekunde, indem jedes Frame der Tastenzustand abgefragt wird (keine Mixins nötig). */
public final class Cps {
    private Cps() {}

    private static final ArrayDeque<Long> L = new ArrayDeque<>(), R = new ArrayDeque<>();
    private static boolean pl, pr;
    private static long last;

    public static void sample(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now == last) return;
        last = now;
        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !pl) L.addLast(now);
        if (r && !pr) R.addLast(now);
        pl = l; pr = r;
        while (!L.isEmpty() && now - L.peekFirst() > 1000) L.pollFirst();
        while (!R.isEmpty() && now - R.peekFirst() > 1000) R.pollFirst();
    }

    public static int left() { return L.size(); }
    public static int right() { return R.size(); }
}
