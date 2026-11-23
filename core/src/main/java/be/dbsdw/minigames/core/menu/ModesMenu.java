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
        for (ModeSpec mode : game.modes()) {
            if (slot >= SIZE) {
                break;
            }
            ItemStack icon = game.modeIcon(mode);
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(MiniMessage.miniMessage().deserialize(mode.displayName()));
            List<Component> lore = new ArrayList<>();
            if (!model.anyArena(mode)) {
                lore.add(messages.get("menu.mode.no-arena"));
            } else {
                lore.add(messages.get("menu.mode.queued",
                        Placeholder.unparsed("queued", String.valueOf(model.queued(mode.key()))),
                        Placeholder.unparsed("needed", String.valueOf(mode.startMinimum()))));
                OptionalInt seconds = model.secondsLeft(mode.key());
                if (seconds.isPresent()) {
                    lore.add(messages.get("menu.mode.countdown",
                            Placeholder.unparsed("seconds", String.valueOf(seconds.getAsInt()))));
                }
                lore.add(messages.get(model.isQueued(viewer.getUniqueId(), mode.key())
                        ? "menu.mode.click-leave" : "menu.mode.click-join"));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(model.isQueued(viewer.getUniqueId(), mode.key()));
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            modeBySlot.put(slot, mode.key());
            slot += 2;
        }
        entry = game.menuEntry(viewer).orElse(null);
        inventory.setItem(ENTRY_SLOT, entry == null ? null : entry.icon());
    }

    /** The game's extra button, if this slot holds it. */
    Optional<GameType.MenuEntry> entryAt(int slot) {
        return slot == ENTRY_SLOT ? Optional.ofNullable(entry) : Optional.empty();
    }

    Optional<String> modeAt(int slot) {
        return Optional.ofNullable(modeBySlot.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
