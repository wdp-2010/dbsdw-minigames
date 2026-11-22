package be.dbsdw.minigames.core.lobby;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackAssets;
import be.dbsdw.minigames.core.pack.PackItems;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

/** The lobby: its spawn point, the lobby state players are put in, and the lobby items. */
public final class LobbyService {
    public enum LobbyItem {
        GAMES(0, Material.COMPASS, "item.games", PackAssets.LOBBY_GAMES),
        STATS(4, Material.BOOK, "item.stats", PackAssets.LOBBY_STATS),
        /** Private rooms; no pack model yet, the plain item shows. */
        ROOM(6, Material.CAKE, "item.room", null),
        SURVIVAL(8, Material.RED_BED, "item.survival", PackAssets.LOBBY_SURVIVAL);

        final int slot;
        final Material material;
        final String messageKey;
        final String model;

        LobbyItem(int slot, Material material, String messageKey, String model) {
            this.slot = slot;
            this.material = material;
            this.messageKey = messageKey;
            this.model = model;
        }
    }

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("dbsdw", "lobby_item");

    private final Plugin plugin;
    private final Messages messages;

    public LobbyService(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /** The lobby spawn from config; empty when unset or its world is not loaded. */
    public Optional<Location> spawn() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("lobby");
        if (s == null) {
            return Optional.empty();
        }
        World world = plugin.getServer().getWorld(s.getString("world", ""));
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
    }

    public void setSpawn(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", (double) location.getYaw());
        plugin.getConfig().set("lobby.pitch", (double) location.getPitch());
        plugin.saveConfig();
    }

    /** Puts the player in the lobby: spawn, adventure mode, fresh state, lobby items. */
    public void send(Player player) {
        spawn().ifPresent(player::teleport);
        reset(player);
        player.setGameMode(GameMode.ADVENTURE);
        for (LobbyItem item : LobbyItem.values()) {
            player.getInventory().setItem(item.slot, create(item));
        }
        player.getInventory().setHeldItemSlot(LobbyItem.GAMES.slot);
    }

    /** Empty inventory, full health and food, no effects, not burning or glowing. */
    public static void reset(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.setGlowing(false);
        player.setInvisible(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public Optional<LobbyItem> lobbyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LobbyItem.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isLobbyItem(ItemStack stack) {
        return lobbyItem(stack).isPresent();
    }

    private ItemStack create(LobbyItem item) {
        ItemStack stack = item.model == null ? ItemStack.of(item.material) : PackItems.item(item.material, item.model, null);
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get(item.messageKey));
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, item.name());
        stack.setItemMeta(meta);
        return stack;
    }
}
