package be.dbsdw.minigames.core.solo;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The {@code /solo} menu: the top row lists the games that can be played alone (against a bot), the
 * second row the modes of the chosen game. Clicking a mode closes the menu and hands the player and
 * mode to {@code start}, which makes a private room and presses start for them; the game then takes
 * over (e.g. chess asks for the bot's difficulty and your colour).
 */
public final class SoloMenu implements Listener {
    private static final int SIZE = 18;
    private static final int MODE_ROW = 9;

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<List<ModeSpec>> soloModes;
    private final Function<String, ItemStack> gameIcon;
    private final Function<ModeSpec, ItemStack> modeIcon;
    private final BiConsumer<Player, ModeSpec> start;

    /**
     * @param soloModes the modes of the games that support solo play, in menu order
     * @param start     makes a private room with this mode for the player and starts it alone
     */
    public SoloMenu(Plugin plugin, Messages messages, Supplier<List<ModeSpec>> soloModes,
                    Function<String, ItemStack> gameIcon, Function<ModeSpec, ItemStack> modeIcon,
                    BiConsumer<Player, ModeSpec> start) {
        this.plugin = plugin;
        this.messages = messages;
        this.soloModes = soloModes;
        this.gameIcon = gameIcon;
        this.modeIcon = modeIcon;
        this.start = start;
    }

    public void open(Player player) {
        Map<String, List<ModeSpec>> games = SoloChoices.byGame(soloModes.get());
        if (games.isEmpty()) {
            player.sendMessage(messages.get("solo.none"));
            return;
        }
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, messages.get("solo.title"));
        render(holder, games, Optional.empty());
        player.openInventory(holder.inventory);
    }

    private void render(Holder holder, Map<String, List<ModeSpec>> games, Optional<String> chosen) {
        Inventory inv = holder.inventory;
        inv.clear();
        holder.actions.clear();
        int slot = 0;
        for (Map.Entry<String, List<ModeSpec>> game : games.entrySet()) {
            if (slot >= MODE_ROW) {
                break;
            }
            boolean selected = chosen.isPresent() && chosen.get().equals(game.getKey());
            ItemStack icon = gameIcon.apply(game.getKey());
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(messages.get(selected ? "solo.game-selected" : "solo.game-click")));
            meta.setEnchantmentGlintOverride(selected);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            List<ModeSpec> modes = game.getValue();
            String gameId = game.getKey();
            // A game with a single mode starts right away; otherwise its modes appear below.
            holder.actions.put(slot, modes.size() == 1
                    ? p -> begin(p, modes.getFirst())
                    : p -> render(holder, games, Optional.of(gameId)));
            slot++;
        }
        if (chosen.isEmpty()) {
            return;
        }
        List<ModeSpec> modes = games.getOrDefault(chosen.get(), List.of());
        for (int i = 0; i < modes.size() && i < 9; i++) {
            ModeSpec mode = modes.get(i);
            ItemStack icon = modeIcon.apply(mode);
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(MiniMessage.miniMessage().deserialize(mode.displayName()));
            meta.lore(List.of(messages.get("solo.mode-click")));
            icon.setItemMeta(meta);
            inv.setItem(MODE_ROW + i, icon);
            holder.actions.put(MODE_ROW + i, p -> begin(p, mode));
        }
    }

    private void begin(Player player, ModeSpec mode) {
        // Called from a click in this menu: close it and start after the event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (player.isOnline()) {
                start.accept(player, mode);
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player
                && event.getClickedInventory() == event.getView().getTopInventory()) {
            Consumer<Player> action = holder.actions.get(event.getSlot());
            if (action != null) {
                action.accept(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
