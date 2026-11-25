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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest menu with one entry per team size of a mode (1v1, 2v2, ...); click joins or leaves that queue. */
public final class SizesMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int[] SLOTS = {10, 12, 14, 16, 11, 13, 15};
    private static final int BACK_SLOT = 22;

    private final GameType game;
    private final ModeSpec mode;
    private final MenuModel model;
    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> queueBySlot = new HashMap<>();

    private SizesMenu(GameType game, ModeSpec mode, MenuModel model, Messages messages) {
        this.game = game;
        this.mode = mode;
        this.model = model;
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.sizes.title",
                Placeholder.component("mode", MiniMessage.miniMessage().deserialize(mode.displayName()))));
    }

    public static void open(Player player, GameType game, ModeSpec mode, MenuModel model, Messages messages) {
        SizesMenu menu = new SizesMenu(game, mode, model, messages);
        menu.render(player);
        player.openInventory(menu.inventory);
    }

    /** Redraws the size entries for this viewer. */
    void render(Player viewer) {
        List<String> keys = model.queueKeys(mode);
        Component locked = model.lockLore(viewer, mode);
        queueBySlot.clear();
        for (int i = 0; i < keys.size() && i < SLOTS.length; i++) {
            String key = keys.get(i);
            ItemStack icon = game.modeIcon(mode);
            icon.setAmount(Math.max(1, Math.min(64, model.needed(key) / Math.max(1, mode.teams()))));
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(model.queueName(key));
            List<Component> lore = new ArrayList<>();
            boolean queued = model.isQueued(viewer.getUniqueId(), key);
            if (locked != null) {
                lore.add(locked);
            } else {
                lore.add(messages.get("menu.mode.queued",
                        Placeholder.unparsed("queued", String.valueOf(model.queued(key))),
                        Placeholder.unparsed("needed", String.valueOf(model.needed(key)))));
                OptionalInt seconds = model.secondsLeft(key);
                if (seconds.isPresent()) {
                    lore.add(messages.get("menu.mode.countdown",
                            Placeholder.unparsed("seconds", String.valueOf(seconds.getAsInt()))));
                }
                lore.add(messages.get(queued ? "menu.mode.click-leave" : "menu.mode.click-join"));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(locked == null && queued);
            icon.setItemMeta(meta);
            inventory.setItem(SLOTS[i], icon);
            queueBySlot.put(SLOTS[i], key);
        }
        ItemStack back = ItemStack.of(Material.ARROW);
        back.editMeta(m -> m.itemName(messages.get("menu.sizes.back")));
        inventory.setItem(BACK_SLOT, back);
    }

    boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    GameType game() {
        return game;
    }

    Optional<String> queueAt(int slot) {
        return Optional.ofNullable(queueBySlot.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
