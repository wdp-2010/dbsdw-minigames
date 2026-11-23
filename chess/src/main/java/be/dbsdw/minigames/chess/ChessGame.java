package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.bot.BotLevel;
import be.dbsdw.minigames.chess.match.ChessMatch;
import be.dbsdw.minigames.chess.menu.BotMenu;
import be.dbsdw.minigames.chess.menu.GamesMenu;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.chess.save.SaveStore;
import be.dbsdw.minigames.chess.save.SavedGame;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchStarter;
import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.pack.PackItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Chess for MinigameCore: one mode per time control, 1 against 1. Unfinished games are saved and can
 * be continued from the chess menu ({@code /schaak}): the other player gets an invite and both are put
 * on a fresh board.
 */
public final class ChessGame implements GameType {
    private final Plugin plugin;
    private final ChessSettings settings;
    private final Messages messages;
    private final SaveStore store;
    private final MatchStarter starter;
    private final List<ModeSpec> modes = new ArrayList<>();
    private final Map<String, ChessSettings.TimeControl> controls = new HashMap<>();
    private final Map<UUID, ChessMatch> matchOf = new HashMap<>();
    /** Saved games about to be continued, by "white black". */
    private final Map<String, SavedGame> resumes = new HashMap<>();
    /** Open invites to continue a game: game id → who asked, and until when (server tick). */
    private final Map<String, Invite> invites = new HashMap<>();
    /** Games against the bot about to start, by the player's id. */
    private final Map<UUID, BotGame> bots = new HashMap<>();
    /** The last choices in the bot menu, per player. */
    private final Map<UUID, BotMenu.Choice> botChoices = new HashMap<>();
    private ExecutorService botThreads;
    private long ticks;

    private record Invite(UUID from, long until) {}

    /** A game against the bot: its level (the player's colour is their team). */
    public record BotGame(BotLevel level) {}

    public ChessGame(Plugin plugin, ChessSettings settings, Messages messages, SaveStore store, MatchStarter starter) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.store = store;
        this.starter = starter;
        for (ChessSettings.TimeControl c : settings.modes()) {
            modes.add(new ModeSpec(id(), c.id(), c.name(), 2, 1, 1, Set.of(ArenaFeature.FLAGS)));
            controls.put(c.id(), c);
        }
    }

    public Plugin plugin() {
        return plugin;
    }

    public ChessSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public SaveStore store() {
        return store;
    }

    /** The time control of a mode; an unknown mode (removed from config) plays without a clock. */
    public ChessSettings.TimeControl control(String modeId) {
        ChessSettings.TimeControl c = controls.get(modeId);
        return c != null ? c : new ChessSettings.TimeControl(modeId, modeId, 0, 0);
    }

    @Override
    public String id() {
        return "chess";
    }

    @Override
    public Component displayName() {
        return messages.get("game-name");
    }

    @Override
    public Material icon() {
        return Material.PAPER;
    }

    @Override
    public ItemStack iconItem() {
        return PackItems.item(Material.PAPER, PackAssets.piece(Piece.KNIGHT), null);
    }

    @Override
    public ItemStack modeIcon(ModeSpec mode) {
        ChessSettings.TimeControl c = control(mode.id());
        int piece = c.initialMillis() == 0 ? Piece.ROOK : c.initialMillis() <= 5 * 60_000 ? Piece.KNIGHT : Piece.QUEEN;
        return PackItems.item(Material.PAPER, PackAssets.piece(piece), null);
    }

    @Override
    public List<ModeSpec> modes() {
        return List.copyOf(modes);
    }

    @Override
    public List<String> stats() {
        return List.of("checkmates", "captures");
    }

    @Override
    public Component statName(String stat) {
        return messages.get("stat." + stat);
    }

    @Override
    public Component teamName(ModeSpec mode, int team) {
        return messages.get(team == 0 ? "team.white" : "team.black");
    }

    @Override
    public Component coreText(String key, TagResolver... resolvers) {
        String own = "core." + key;
        String raw = messages.raw(own);
        return raw.equals(own) ? null : messages.get(own, resolvers);
    }

    @Override
    public Component queueBlock(Player player, ModeSpec mode) {
        int count = store.count(player.getUniqueId());
        if (count < settings.maxUnfinished()) {
            return null;
        }
        return messages.get("limit", Placeholder.unparsed("max", String.valueOf(settings.maxUnfinished())));
    }

    @Override
    public Optional<MenuEntry> menuEntry(Player viewer) {
        int count = store.count(viewer.getUniqueId());
        ItemStack icon = PackItems.item(Material.PAPER, PackAssets.piece(-Piece.KING), null);
        ItemMeta meta = icon.getItemMeta();
        meta.itemName(messages.get("menu.entry", Placeholder.unparsed("count", String.valueOf(count))));
        meta.lore(List.of(messages.get(count == 0 ? "menu.entry-none" : "menu.entry-lore")));
        icon.setItemMeta(meta);
        return Optional.of(new MenuEntry(icon, this::openGames));
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        return new ChessMatch(context, this);
    }

    // ---------------------------------------------------------------- running matches

    public void track(ChessMatch match, UUID white, UUID black) {
        matchOf.put(white, match);
        matchOf.put(black, match);
    }

    public void untrack(ChessMatch match) {
        matchOf.values().removeIf(m -> m == match);
    }

    /** The chess match this player plays in (also while they are away from it). */
    public Optional<ChessMatch> matchOf(UUID player) {
        return Optional.ofNullable(matchOf.get(player));
    }

    // ---------------------------------------------------------------- the bot

    /** Threads the bot thinks on (never the server's), started on first use. */
    public synchronized ExecutorService botThreads() {
        if (botThreads == null) {
            AtomicInteger n = new AtomicInteger();
            botThreads = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Chess-bot-" + n.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return botThreads;
    }

    synchronized void shutdown() {
        if (botThreads != null) {
            botThreads.shutdownNow();
        }
    }

    /** The bot game this player is about to start, once. */
    public BotGame takeBot(UUID player) {
        return bots.remove(player);
    }

    public BotMenu.Choice botChoice(UUID player) {
        return botChoices.getOrDefault(player, new BotMenu.Choice(BotLevel.NORMAAL, BotMenu.Colour.WHITE, modes.getFirst().id()));
    }

    public void rememberBotChoice(UUID player, BotMenu.Choice choice) {
        botChoices.put(player, choice);
    }

    /** {@code /schaakbot}: the bot menu, to start a game against the bot from the lobby. */
    public void openBotMenu(Player player) {
        BotMenu.open(player, this, null, null);
    }

    /** Started from the bot menu outside a room: straight onto a board. */
    public void startBot(Player player, BotLevel level, boolean white, String modeId) {
        Optional<ModeSpec> mode = modes.stream().filter(m -> m.id().equals(modeId)).findFirst();
        if (mode.isEmpty()) {
            player.sendMessage(messages.get("resume.failed-unknown-mode"));
            return;
        }
        if (!starter.available(player.getUniqueId())) {
            player.sendMessage(messages.get("bot.busy"));
            return;
        }
        UUID id = player.getUniqueId();
        bots.put(id, new BotGame(level));
        List<List<UUID>> teams = white ? List.of(List.of(id), List.of()) : List.of(List.of(), List.of(id));
        MatchStarter.Result result = starter.start(mode.get(), teams);
        if (result != MatchStarter.Result.STARTED) {
            bots.remove(id);
            player.sendMessage(messages.get("resume.failed-" + result.name().toLowerCase().replace('_', '-')));
        }
    }

    /** Pressing start alone in a room opens the bot menu; its start button starts the room's countdown. */
    @Override
    public boolean startAlone(Player host, ModeSpec mode, IntConsumer startOnTeam) {
        BotMenu.open(host, this, mode, (level, white) -> {
            bots.put(host.getUniqueId(), new BotGame(level));
            startOnTeam.accept(white ? 0 : 1);
        });
        return true;
    }

    /** The saved game to load for a match between these two, once. */
    public SavedGame takeResume(UUID white, UUID black) {
        return resumes.remove(white + " " + black);
    }

    void tick() {
        ticks++;
        invites.values().removeIf(i -> i.until() < ticks);
    }

    // ---------------------------------------------------------------- continuing saved games

    public void openGames(Player player) {
        GamesMenu.open(player, this);
    }

    /**
     * The player wants to continue this saved game: invite the opponent, or start right away when the
     * opponent already invited them.
     */
    public void requestResume(Player player, String gameId) {
        Optional<SavedGame> found = store.get(gameId).filter(g -> g.involves(player.getUniqueId()));
        if (found.isEmpty()) {
            player.sendMessage(messages.get("resume.gone"));
            return;
        }
        SavedGame g = found.get();
        UUID other = g.opponentOf(player.getUniqueId());
        Invite open = invites.get(gameId);
        if (open != null && open.from().equals(other)) {
            invites.remove(gameId);
            begin(g, player);
            return;
        }
        if (!starter.available(player.getUniqueId())) {
            player.sendMessage(messages.get("resume.you-busy"));
            return;
        }
        Player opponent = Bukkit.getPlayer(other);
        if (opponent == null) {
            player.sendMessage(messages.get("resume.offline", Placeholder.unparsed("player", g.opponentName(player.getUniqueId()))));
            return;
        }
        if (!starter.available(other)) {
            player.sendMessage(messages.get("resume.busy", Placeholder.unparsed("player", opponent.getName())));
            return;
        }
        invites.put(gameId, new Invite(player.getUniqueId(), ticks + settings.inviteTicks()));
        player.sendMessage(messages.get("resume.sent", Placeholder.unparsed("player", opponent.getName())));
        opponent.sendMessage(messages.get("resume.invite", Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("moves", String.valueOf(g.moves().size())),
                Placeholder.unparsed("id", gameId)));
    }

    private void begin(SavedGame g, Player accepting) {
        Optional<ModeSpec> mode = modes.stream().filter(m -> m.id().equals(g.mode())).findFirst();
        ModeSpec spec = mode.orElse(modes.getFirst());
        String key = g.white() + " " + g.black();
        resumes.put(key, g);
        MatchStarter.Result result = starter.start(spec, List.of(List.of(g.white()), List.of(g.black())));
        if (result != MatchStarter.Result.STARTED) {
            resumes.remove(key);
            Component why = messages.get("resume.failed-" + result.name().toLowerCase().replace('_', '-'));
            accepting.sendMessage(why);
            Player other = Bukkit.getPlayer(g.opponentOf(accepting.getUniqueId()));
            if (other != null) {
                other.sendMessage(why);
            }
        }
    }

    /** Deletes a saved game for both players, without a result. */
    public void delete(Player player, String gameId) {
        Optional<SavedGame> g = store.get(gameId).filter(s -> s.involves(player.getUniqueId()));
        if (g.isEmpty()) {
            return;
        }
        if (matchOf.values().stream().anyMatch(m -> m.id().equals(gameId))) {
            player.sendMessage(messages.get("menu.delete-running"));
            return;
        }
        store.delete(gameId);
        invites.remove(gameId);
        player.sendMessage(messages.get("menu.deleted", Placeholder.unparsed("player", g.get().opponentName(player.getUniqueId()))));
        plugin.getLogger().info("[chess] " + player.getName() + " deleted saved game " + gameId);
    }

    /** On join: back into an away game, or a reminder of unfinished games. */
    void joined(Player player) {
        Optional<ChessMatch> match = matchOf(player.getUniqueId());
        if (match.isPresent() && match.get().isAway(player.getUniqueId()) && match.get().rejoin(player)) {
            return;
        }
        int count = store.count(player.getUniqueId());
        if (count > 0) {
            player.sendMessage(messages.get("join-reminder", Placeholder.unparsed("count", String.valueOf(count))));
        }
    }
}
