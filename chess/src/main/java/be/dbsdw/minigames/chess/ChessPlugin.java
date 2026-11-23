package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.save.SaveStore;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.MatchStarter;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChessPlugin extends JavaPlugin {
    private static final Pattern MODE_ID = Pattern.compile("[a-z0-9_-]+");
    private static final long HOUR_TICKS = 20L * 60 * 60;

    private ExecutorService io;
    private ChessGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        ChessSettings settings;
        try {
            settings = load(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("[chess] config.yml: " + e.getMessage() + " - Chess is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        MatchStarter starter = getServer().getServicesManager().load(MatchStarter.class);
        if (registry == null || starter == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry/MatchStarter is not available");
        }
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Chess-save");
            t.setDaemon(true);
            return t;
        });
        SaveStore store = new SaveStore(getDataFolder().toPath().resolve("games"), io, getLogger());
        try {
            store.load();
        } catch (IOException e) {
            getLogger().severe("[chess] could not read saved games: " + e.getMessage() + " - Chess is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Messages messages = Messages.load(this);
        game = new ChessGame(this, settings, messages, store, starter);
        registry.register(this, game);
        getServer().getPluginManager().registerEvents(new ChessListener(game), this);
        getServer().getScheduler().runTaskTimer(this, game::tick, 1, 1);
        getServer().getScheduler().runTaskTimer(this, () -> {
            int expired = store.expire(System.currentTimeMillis(), settings.expireMillis()).size();
            if (expired > 0) {
                getLogger().info("[chess] removed " + expired + " saved game(s) not played for "
                        + settings.expireMillis() / 86_400_000 + " days");
            }
        }, 20, HOUR_TICKS);
        ChessCommand command = new ChessCommand(game);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                {
                    event.registrar().register("schaak", "Schaken: je onafgemaakte partijen", List.of("chess"), command);
                    event.registrar().register("schaakbot", "Schaken tegen de computer", List.of("chessbot"),
                            new BotCommand(game));
                });
        getLogger().info("[chess] enabled: " + settings.modes().size() + " modes, " + store.size() + " saved games");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("[chess] saving games took too long; some saves may be missing");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static ChessSettings load(FileConfiguration c) {
        ConfigurationSection modes = c.getConfigurationSection("modes");
        if (modes == null || modes.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("modes: at least one mode is needed");
        }
        List<ChessSettings.TimeControl> controls = new ArrayList<>();
        for (String id : modes.getKeys(false)) {
            if (!MODE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("modes: " + id + " must match [a-z0-9_-]+");
            }
            double minutes = modes.getDouble(id + ".minutes");
            double increment = modes.getDouble(id + ".increment-seconds");
            if (minutes < 0 || increment < 0) {
                throw new IllegalArgumentException("modes." + id + ": times must not be negative");
            }
            controls.add(new ChessSettings.TimeControl(id, modes.getString(id + ".name", id),
                    Math.round(minutes * 60_000), Math.round(increment * 1000)));
        }
        double scale = c.getDouble("pieces.scale");
        if (scale <= 0 || scale > 2) {
            throw new IllegalArgumentException("pieces.scale must be above 0 and at most 2");
        }
        int max = c.getInt("unfinished.max-per-player");
        if (max < 1) {
            throw new IllegalArgumentException("unfinished.max-per-player must be at least 1");
        }
        return new ChessSettings(controls, Math.round(c.getDouble("away-grace-seconds") * 20), max,
                Math.round(c.getDouble("unfinished.expire-days") * 86_400_000L),
                Math.round(c.getDouble("invite-seconds") * 20), scale, c.getInt("pieces.move-ticks"),
                c.getDouble("reach"), c.getBoolean("debug"));
    }
}
