package be.dbsdw.minigames.core.menu;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest menu with one entry per mode of a game; click joins or leaves that queue. */
public final class ModesMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int FIRST_SLOT = 11;
    /** The game's own extra button (GameType#menuEntry), bottom middle. */
    private static final int ENTRY_SLOT = 22;

    private final GameType game;
    private final MenuModel model;
    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> modeBySlot = new HashMap<>();
    /** Modes with a queue per team size: a click opens their sizes menu. */
    private final Map<Integer, ModeSpec> sizedBySlot = new HashMap<>();
    private GameType.MenuEntry entry;

    private ModesMenu(GameType game, MenuModel model, Messages messages) {
        this.game = game;
        this.model = model;
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, SIZE,
                messages.get("menu.modes.title", Placeholder.component("game", game.displayName())));
    }

    public static void open(Player player, GameType game, MenuModel model, Messages messages) {
        ModesMenu menu = new ModesMenu(game, model, messages);
        menu.render(player);
        player.openInventory(menu.inventory);
    }

    /** Redraws the mode entries for this viewer. */
    void render(Player viewer) {
        int slot = FIRST_SLOT;
        modeBySlot.clear();
        sizedBySlot.clear();
        for (ModeSpec mode : game.modes()) {
            if (slot >= SIZE) {
                break;
            }
            List<String> keys = model.queueKeys(mode);
            boolean roomsOnly = keys.isEmpty();
            boolean sized = !roomsOnly && !keys.equals(List.of(mode.key()));
            ItemStack icon = game.modeIcon(mode);
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(MiniMessage.miniMessage().deserialize(mode.displayName()));
            List<Component> lore = new ArrayList<>();
            Component locked = model.lockLore(viewer, mode);
            boolean queued = keys.stream().anyMatch(k -> model.isQueued(viewer.getUniqueId(), k));
            if (locked != null) {
                lore.add(locked);
            } else if (roomsOnly) {
                lore.add(messages.get("menu.mode.rooms-only"));
            } else if (!model.anyArena(mode)) {
                lore.add(messages.get("menu.mode.no-arena"));
            } else if (sized) {
                lore.add(messages.get("menu.mode.sizes", Placeholder.component("sizes", sizes(keys))));
                lore.add(messages.get("menu.mode.click-sizes"));
            } else {
                lore.add(messages.get("menu.mode.queued",
                        Placeholder.unparsed("queued", String.valueOf(model.queued(mode.key()))),
                        Placeholder.unparsed("needed", String.valueOf(mode.startMinimum()))));
                OptionalInt seconds = model.secondsLeft(mode.key());
                if (seconds.isPresent()) {
                    lore.add(messages.get("menu.mode.countdown",
                            Placeholder.unparsed("seconds", String.valueOf(seconds.getAsInt()))));
                }
                lore.add(messages.get(queued ? "menu.mode.click-leave" : "menu.mode.click-join"));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(locked == null && queued);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            if (sized) {
                sizedBySlot.put(slot, mode);
            } else if (!roomsOnly) {
                modeBySlot.put(slot, mode.key());
            }
            slot += 2;
        }
        entry = game.menuEntry(viewer).orElse(null);
        inventory.setItem(ENTRY_SLOT, entry == null ? null : entry.icon());
    }

    /** The game's extra button, if this slot holds it. */
    Optional<GameType.MenuEntry> entryAt(int slot) {
        return slot == ENTRY_SLOT ? Optional.ofNullable(entry) : Optional.empty();
    }

    /** "1v1 (1) · 2v2 (0)": each size's name after the mode and how many wait in it. */
    private Component sizes(List<String> keys) {
        Component line = Component.empty();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            line = line.append(Component.text((i == 0 ? "" : " · ") + key.substring(key.lastIndexOf(':') + 1)
                    + " (" + model.queued(key) + ")"));
        }
        return line;
    }

    /** The mode whose sizes menu this slot opens, if it holds one. */
    Optional<ModeSpec> sizedAt(int slot) {
        return Optional.ofNullable(sizedBySlot.get(slot));
    }

    GameType game() {
        return game;
    }

    Optional<String> modeAt(int slot) {
        return Optional.ofNullable(modeBySlot.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
