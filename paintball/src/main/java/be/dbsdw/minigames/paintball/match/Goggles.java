package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Paint on the goggles when hit: a camera overlay in the shooter's colour, worn on the head for a
 * moment. A camera overlay always covers the whole screen (a title glyph has a fixed size and left
 * the edges clear on big screens). The head slot is shared: a carried flag stays visible under the
 * paint, and afterwards the slot gets back what it should hold then (flag, scope or nothing).
 */
final class Goggles {
    /** Ticks at full strength, then ticks fading. */
    static final long FULL_TICKS = 8;
    static final long FADE_TICKS = 6;

    private static final class Splash {
        final int team;
        final ItemStack base;
        final long fadeAt;
        final long endAt;
        boolean faded;

        Splash(int team, ItemStack base, long now) {
            this.team = team;
            this.base = base;
            this.fadeAt = now + FULL_TICKS;
            this.endAt = now + FULL_TICKS + FADE_TICKS;
        }
    }

    private final Map<UUID, Splash> active = new HashMap<>();

    void splash(Player p, int team, long now) {
        ItemStack head = p.getInventory().getHelmet();
        Splash old = active.get(p.getUniqueId());
        ItemStack base;
        if (Gun.isSplash(head) && old != null) {
            base = old.base;
        } else if (head == null || head.isEmpty() || Gun.isScope(head)) {
            base = null;
        } else {
            base = head.clone();
        }
        active.put(p.getUniqueId(), new Splash(team, base, now));
        p.getInventory().setHelmet(Gun.splash(base, PackAssets.splash(team, false)));
    }

    /** Fades and ends the splashes; {@code withoutPaint} says what the head slot should hold after. */
    void tick(long now, Function<Player, ItemStack> withoutPaint) {
        Iterator<Map.Entry<UUID, Splash>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Splash> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Splash s = e.getValue();
            if (p == null || !Gun.isSplash(p.getInventory().getHelmet())) {
                it.remove(); // gone, or the match put something else on the head (flag taken or dropped)
                continue;
            }
            if (now >= s.endAt) {
                p.getInventory().setHelmet(withoutPaint.apply(p));
                it.remove();
            } else if (now >= s.fadeAt && !s.faded) {
                s.faded = true;
                p.getInventory().setHelmet(Gun.splash(s.base, PackAssets.splash(s.team, true)));
            }
        }
    }

    void forget(Player p) {
        active.remove(p.getUniqueId());
    }
}
