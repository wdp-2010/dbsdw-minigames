package be.dbsdw.minigames.core.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ArenaView;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.lobby.LobbyService;
import be.dbsdw.minigames.core.pack.PackAssets;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.core.stats.StatsStore;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** One match on one arena: the core side of the game's {@link MatchHandler}. */
final class RunningMatch implements MatchContext {
    private final GameType game;
    private final ModeSpec mode;
    private final ArenaView arena;
    private final MatchState state;
    private final Messages messages;
    private final Logger log;
    private MatchHandler handler;
    private final StatsStore stats;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> deltas = new HashMap<>();
    private long ticks;
    private boolean failed;
    private boolean statsSaved;
    private final Consumer<UUID> onRejoin;

    /** {@code onRejoin} is told about a player taken back in by {@link #rejoin}. */
    RunningMatch(GameType game, ModeSpec mode, ArenaView arena, List<List<UUID>> teams, int resultsTicks,
                 Messages messages, Logger log, StatsStore stats, Consumer<UUID> onRejoin) {
        this.onRejoin = onRejoin;
        this.game = game;
        this.mode = mode;
        this.arena = arena;
        this.state = new MatchState(teams, resultsTicks);
        this.messages = messages;
        this.log = log;
        this.stats = stats;
    }

    GameType game() {
        return game;
    }

    List<UUID> players() {
        return state.everyone();
    }

    boolean failed() {
        return failed;
    }

    /** Teleports everyone to their team spawns, resets them and starts the game's handler. */
    void start() {
        for (int team = 0; team < state.teamCount(); team++) {
            List<Location> spawns = arena.spawns(team);
            List<UUID> members = state.members(team);
            for (int i = 0; i < members.size(); i++) {
                Player player = Bukkit.getPlayer(members.get(i));
                if (player == null) {
                    continue;
                }
                names.put(player.getUniqueId(), player.getName());
                add(player.getUniqueId(), "matches", 1);
                LobbyService.reset(player);
                player.setGameMode(GameMode.ADVENTURE);
                player.teleport(spawns.get(i % spawns.size()));
                player.showTitle(Title.title(text("match.start"),
                        text("match.start-team", Placeholder.component("team", teamName(team)))));
                PackItems.play(player, PackAssets.SOUND_START, 0.9f, 1f);
            }
        }
        log.info("[match] " + mode.key() + " on " + arena.name() + " started: " + state.everyone().size() + " players");
        guarded(() -> {
            handler = game.newMatch(this);
            handler.start();
        });
    }

    /** One server tick. Returns true when the match is over and its arena can be released. */
    boolean tick() {
        switch (state.phase()) {
            case RUNNING -> {
                ticks++;
                guarded(handler::tick);
            }
            case ENDING -> {
                if (state.tickEnding()) {
                    return true;
                }
            }
            case DONE -> {
                return true;
            }
        }
        return state.phase() == MatchState.Phase.DONE;
    }

    /**
     * The player leaves (quit, /leave). Ends the match if only one team is left, unless the game keeps
     * it running; a match nobody is in any more is paused.
     */
    void leave(Player player) {
        OptionalInt team = state.teamOf(player.getUniqueId());
        if (team.isEmpty()) {
            return;
        }
        boolean wasRunning = state.phase() == MatchState.Phase.RUNNING;
        MatchState.LeaveResult result = state.leave(player.getUniqueId());
        if (wasRunning && handler != null) {
            guarded(() -> handler.playerLeft(player, team.getAsInt()));
        }
        boolean keeps = false;
        if (handler != null && !failed) {
            try {
                keeps = handler.keepsRunningWhenAlone();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "[match] keepsRunningWhenAlone failed", e);
            }
        }
        if (keeps) {
            if (state.phase() == MatchState.Phase.RUNNING && state.everyone().isEmpty()) {
                finish(MatchOutcome.paused(List.of()));
            }
            return;
        }
        if (result instanceof MatchState.LeaveResult.LastTeamStanding(int winner)) {
            List<Component> lines = List.of();
            try {
                lines = handler != null ? handler.resultLines() : List.of();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "[match] result lines failed", e);
            }
            finish(MatchOutcome.forfeit(winner, team.getAsInt(), lines));
        }
    }

    /** Stops the match without a result (shutdown, game disabled). */
    void abort(String reason) {
        if (state.phase() == MatchState.Phase.DONE) {
            return;
        }
        boolean wasRunning = state.phase() == MatchState.Phase.RUNNING;
        state.abort();
        if (wasRunning && handler != null) {
            MatchHandler h = handler;
            guarded(h::end);
        }
        saveStats();
        log.info("[match] " + mode.key() + " on " + arena.name() + " ended: aborted (" + reason + ")");
    }

    @Override
    public boolean rejoin(Player player, int team) {
        if (!player.isOnline() || !state.rejoin(player.getUniqueId(), team)) {
            return false;
        }
        names.put(player.getUniqueId(), player.getName());
        onRejoin.accept(player.getUniqueId());
        log.info("[match] " + player.getName() + " rejoined " + mode.key() + " on " + arena.name());
        return true;
    }

    @Override
    public void finish(MatchOutcome outcome) {
        if (!state.finish()) {
            return;
        }
        if (handler != null) {
            guarded(handler::end);
        }
        if (outcome.paused()) {
            paused(outcome);
            return;
        }
        for (UUID id : outcome.practice() ? List.<UUID>of() : state.everyone()) {
            OptionalInt team = state.teamOf(id);
            if (outcome.winner().isEmpty()) {
                add(id, "draws", 1);
            } else if (team.isPresent() && team.getAsInt() == outcome.winner().getAsInt()) {
                add(id, "wins", 1);
            } else {
                add(id, "losses", 1);
            }
        }
        if (outcome.practice()) {
            deltas.clear();
        }
        saveStats();
        Component title = outcome.winner().isPresent()
                ? text("match.win", Placeholder.component("team", teamName(outcome.winner().getAsInt())))
                : text("match.draw");
        // A forfeit names the team that gave up, so a strange score (the winners behind) makes sense.
        TagResolver gaveUp = outcome.gaveUp().isPresent()
                ? Placeholder.component("team", teamName(outcome.gaveUp().getAsInt())) : null;
        for (UUID id : state.everyone()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                OptionalInt team = state.teamOf(id);
                String sub = outcome.winner().isEmpty() ? "match.draw-sub"
                        : team.isPresent() && team.getAsInt() == outcome.winner().getAsInt() ? "match.won-sub" : "match.lost-sub";
                Component subtitle = gaveUp != null ? text("match.forfeit-sub", gaveUp) : text(sub);
                player.showTitle(Title.title(title, subtitle));
                if (gaveUp != null) {
                    player.sendMessage(text("match.forfeit-line", gaveUp));
                }
                outcome.lines().forEach(player::sendMessage);
                personalLine(player);
                String sound = outcome.winner().isEmpty() ? PackAssets.SOUND_DRAW
                        : team.isPresent() && team.getAsInt() == outcome.winner().getAsInt()
                        ? PackAssets.SOUND_WIN : PackAssets.SOUND_LOSE;
                PackItems.play(player, sound, 1f, 1f);
            }
        }
        log.info("[match] " + mode.key() + " on " + arena.name() + " ended: "
                + (outcome.winner().isPresent() ? "winner team " + outcome.winner().getAsInt() : "draw")
                + (outcome.gaveUp().isPresent() ? " (team " + outcome.gaveUp().getAsInt() + " gave up)" : "")
                + (outcome.practice() ? ", practice: nothing counted" : ""));
    }

    /** No result: only the game's own stats count, not even a played match. */
    private void paused(MatchOutcome outcome) {
        deltas.values().forEach(d -> d.remove("matches"));
        deltas.values().removeIf(Map::isEmpty);
        saveStats();
        for (UUID id : state.everyone()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.showTitle(Title.title(text("match.paused"), text("match.paused-sub")));
                outcome.lines().forEach(player::sendMessage);
                personalLine(player);
                PackItems.play(player, PackAssets.SOUND_DRAW, 1f, 1f);
            }
        }
        log.info("[match] " + mode.key() + " on " + arena.name() + " ended: paused, no result");
    }

    private void personalLine(Player player) {
        if (handler == null) {
            return;
        }
        try {
            Component own = handler.personalLine(player.getUniqueId());
            if (own != null) {
                player.sendMessage(own);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "[match] personal line failed", e);
        }
    }

    /** A core text, in the game's own words if it has them. */
    private Component text(String key, TagResolver... resolvers) {
        try {
            Component own = game.coreText(key, resolvers);
            if (own != null) {
                return own;
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "[match] coreText(" + key + ") failed", e);
        }
        return messages.get(key, resolvers);
    }

    @Override
    public void addStat(Player player, String stat, long amount) {
        if (names.containsKey(player.getUniqueId()) && amount != 0) {
            add(player.getUniqueId(), stat, amount);
        }
    }

    private void add(UUID id, String stat, long amount) {
        deltas.computeIfAbsent(id, k -> new HashMap<>()).merge(stat, amount, Long::sum);
    }

    /** Hands this match's stat increments to the store, once. */
    private void saveStats() {
        if (statsSaved) {
            return;
        }
        statsSaved = true;
        if (stats != null && !deltas.isEmpty()) {
            stats.add(game.id(), names, deltas);
        }
    }

    @Override
    public ModeSpec mode() {
        return mode;
    }

    @Override
    public ArenaView arena() {
        return arena;
    }

    @Override
    public int teamCount() {
        return state.teamCount();
    }

    @Override
    public List<Player> team(int team) {
        return state.members(team).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    @Override
    public OptionalInt teamOf(Player player) {
        return state.teamOf(player.getUniqueId());
    }

    @Override
    public Component teamName(int team) {
        Component own = null;
        try {
            own = game.teamName(mode, team);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "[match] team name of " + game.id() + " failed", e);
        }
        return own != null ? own : messages.get("team." + team);
    }

    @Override
    public boolean moveToTeam(Player player, int team) {
        boolean moved = state.move(player.getUniqueId(), team);
        if (moved) {
            log.info("[match] " + player.getName() + " moved to team " + team + " in " + mode.key());
        }
        return moved;
    }

    @Override
    public long ticks() {
        return ticks;
    }

    /**
     * Runs game code. A game bug aborts this match loudly (SEVERE with the stack trace) instead of
     * breaking every other match and the lobby.
     */
    private void guarded(Runnable call) {
        if (failed) {
            return;
        }
        try {
            call.run();
        } catch (RuntimeException e) {
            failed = true;
            log.log(Level.SEVERE, "[match] " + mode.key() + " on " + arena.name() + " aborted: game error", e);
            boolean wasRunning = state.phase() == MatchState.Phase.RUNNING;
            state.abort();
            if (wasRunning && handler != null) {
                try {
                    handler.end();
                } catch (RuntimeException again) {
                    log.log(Level.SEVERE, "[match] " + mode.key() + " end() failed too", again);
                }
            }
        }
    }
}
