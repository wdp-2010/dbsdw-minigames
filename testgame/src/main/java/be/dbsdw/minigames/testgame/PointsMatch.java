package be.dbsdw.minigames.testgame;

import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** One test-game match: /testgame score adds a point, first to 3 wins, 60 s limit. */
final class PointsMatch implements MatchHandler {
    static final int TARGET = 3;
    static final long TIME_LIMIT_TICKS = 60 * 20;

    private final MatchContext ctx;
    private final Map<UUID, PointsMatch> byPlayer;
    private final PointsRace race;

    PointsMatch(MatchContext ctx, Map<UUID, PointsMatch> byPlayer) {
        this.ctx = ctx;
        this.byPlayer = byPlayer;
        this.race = new PointsRace(ctx.teamCount(), TARGET);
    }

    @Override
    public void start() {
        for (int team = 0; team < ctx.teamCount(); team++) {
            ctx.team(team).forEach(p -> byPlayer.put(p.getUniqueId(), this));
        }
    }

    @Override
    public void tick() {
        if (ctx.ticks() >= TIME_LIMIT_TICKS) {
            OptionalInt leader = race.leader();
            ctx.finish(leader.isPresent()
                    ? MatchOutcome.win(leader.getAsInt(), List.of(scoreLine()))
                    : MatchOutcome.draw(List.of(scoreLine())));
        }
    }

    @Override
    public void playerLeft(Player player, int team) {
        byPlayer.remove(player.getUniqueId());
    }

    @Override
    public void end() {
        byPlayer.values().removeIf(match -> match == this);
    }

    void score(Player player) {
        OptionalInt team = ctx.teamOf(player);
        if (team.isEmpty()) {
            return;
        }
        OptionalInt winner = race.score(team.getAsInt());
        Component line = scoreLine();
        for (int t = 0; t < ctx.teamCount(); t++) {
            ctx.team(t).forEach(p -> p.sendActionBar(line));
        }
        if (winner.isPresent()) {
            ctx.finish(MatchOutcome.win(winner.getAsInt(), List.of(line)));
        }
    }

    private Component scoreLine() {
        Component line = Component.empty();
        for (int t = 0; t < ctx.teamCount(); t++) {
            if (t > 0) {
                line = line.append(Component.text(" - "));
            }
            line = line.append(ctx.teamName(t)).append(Component.text(" " + race.points(t)));
        }
        return line;
    }
}
