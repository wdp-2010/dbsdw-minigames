package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.ScopeZoom;
import be.dbsdw.minigames.paintball.rules.Settings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Aiming through the scope, for a match or the practice range: sneaking with the gun in hand zooms
 * in (the client narrows its view when the walk speed drops), slows the steps, tightens the spread
 * and shows the scope overlay. The mouse wheel steps through the zoom levels while aiming.
 */
final class Scope {
    private static final NamespacedKey SLOW = new NamespacedKey("paintball", "scope");

    private final Settings settings;
    private final Messages messages;
    private final Set<UUID> scoped = new HashSet<>();
    /** Zoom level per player, kept between aims. */
    private final Map<UUID, Integer> zoom = new HashMap<>();

    Scope(Settings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    boolean isScoped(UUID id) {
        return scoped.contains(id);
    }

    double spread(UUID id) {
        return scoped.contains(id) ? settings.scopeSpreadDegrees() : settings.spreadDegrees();
    }

    /** Aims while sneaking with the gun in hand, if {@code allowed}; puts the scope away otherwise. */
    void update(Player p, boolean allowed) {
        boolean want = allowed && p.isSneaking() && Gun.isGun(p.getInventory().getItemInMainHand());
        if (want != scoped.contains(p.getUniqueId())) {
            set(p, want);
        }
    }

    void set(Player p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(SLOW,
                        ScopeZoom.slowdown(zoom.getOrDefault(p.getUniqueId(), ScopeZoom.DEFAULT_LEVEL)),
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
        ItemStack head = p.getInventory().getHelmet();
        if (on) {
            scoped.add(p.getUniqueId());
            if (head == null || head.isEmpty()) { // a carried flag keeps the head slot
                p.getInventory().setHelmet(Gun.scope());
            }
        } else {
            scoped.remove(p.getUniqueId());
            if (Gun.isScope(head)) {
                p.getInventory().setHelmet(null);
            }
        }
    }

    /**
     * A hotbar change while aiming: one wheel notch changes the zoom. Returns the feedback to show,
     * or null when the slot change is not for the scope (the caller then lets it through).
     */
    Component scroll(Player p, int from, int to) {
        UUID id = p.getUniqueId();
        if (!scoped.contains(id) || !ScopeZoom.isScroll(from, to)) {
            return null;
        }
        int level = zoom.getOrDefault(id, ScopeZoom.DEFAULT_LEVEL);
        int next = ScopeZoom.levelAfterScroll(level, from, to);
        if (next != level) {
            zoom.put(id, next);
            set(p, true);
            PackItems.play(p, PackAssets.SOUND_DRY_FIRE, 0.4f, 1.4f + 0.15f * next);
        }
        return messages.get("hud.zoom", Placeholder.unparsed("level", Integer.toString(next + 1)),
                Placeholder.unparsed("levels", Integer.toString(ScopeZoom.LEVELS)));
    }

    /** Puts the scope away and forgets the player (left the match or the range). */
    void forget(Player p) {
        set(p, false);
        zoom.remove(p.getUniqueId());
    }
}
