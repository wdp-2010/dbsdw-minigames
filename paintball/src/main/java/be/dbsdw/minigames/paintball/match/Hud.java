package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.TeamScore;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Duration;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Sidebar, boss bar, action bar and titles of one match. The match scoreboard also holds one
 * team per side, which colours name tags and the glow of a flag carrier.
 */
final class Hud {
    private static final String[] LINES = {"score0", "score1", "blank", "time"};
    private static final Title.Times MARKER_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(250));

    private final MatchContext ctx;
    private final Messages messages;
    private final Settings settings;
    private final ModeRules rules;
    private final Scoreboard board;
    private final Objective objective;
    private final Team[] teams = new Team[2];
    private final BossBar bossBar;

    Hud(MatchContext ctx, Messages messages, Settings settings, ModeRules rules, boolean ctf) {
        this.ctx = ctx;
        this.messages = messages;
        this.settings = settings;
        this.rules = rules;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("paintball", Criteria.DUMMY,
                messages.get(ctf ? "hud.title-ctf" : "hud.title-tdm"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        for (int i = 0; i < LINES.length; i++) {
            objective.getScore(LINES[i]).setScore(LINES.length - i);
        }
        objective.getScore("blank").customName(Component.empty());
        for (int t = 0; t < 2; t++) {
            teams[t] = board.registerNewTeam("pb" + t);
            teams[t].color(t == 0 ? NamedTextColor.RED : NamedTextColor.BLUE);
            teams[t].setAllowFriendlyFire(false);
        }
        this.bossBar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
    }

    void show(Player player, int team) {
        teams[team].addEntry(player.getName());
        player.setScoreboard(board);
        player.showBossBar(bossBar);
    }

    void hide(Player player) {
        for (Team t : teams) {
            t.removeEntry(player.getName());
        }
        if (player.getScoreboard() == board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        player.hideBossBar(bossBar);
        player.sendActionBar(Component.empty());
    }

    /** {@code flagStatus} is shown in the boss bar in CTF; null in TDM (time left instead). */
    void refresh(long now, TeamScore score, Component flagStatus) {
        for (int team = 0; team < 2; team++) {
            objective.getScore("score" + team).customName(messages.get("hud.score",
                    Placeholder.component("team", ctx.teamName(team)),
                    Placeholder.unparsed("score", Integer.toString(score.points(team))),
                    Placeholder.unparsed("goal", Integer.toString(score.target()))));
        }
        long left = rules.timeLimitTicks() - now;
        String time = Clock.format(left);
        objective.getScore("time").customName(messages.get("hud.time", Placeholder.unparsed("time", time)));
        bossBar.name(flagStatus != null ? flagStatus : messages.get("hud.bossbar", Placeholder.unparsed("time", time)));
        bossBar.progress((float) Math.clamp((double) left / rules.timeLimitTicks(), 0.0, 1.0));
    }

    /** Hits left as paint drops in the team colour, then grey drops; then shield, power-ups, protection. */
    void actionBar(Player player, int team, int hitsLeft, boolean protectedNow, boolean shield, Component buffs) {
        Component bar = Component.empty()
                .append(drops(hitsLeft, PackAssets.teamColour(team)))
                .append(drops(settings.hitsToOut() - hitsLeft, NamedTextColor.DARK_GRAY));
        if (shield) {
            bar = bar.append(messages.get("hud.shield"));
        }
        bar = bar.append(buffs);
        if (protectedNow) {
            bar = bar.append(messages.get("hud.protected"));
        }
        player.sendActionBar(bar);
    }

    private static Component drops(int count, TextColor colour) {
        return Component.text(PackAssets.DROP.repeat(Math.max(0, count)), colour).font(PackAssets.HUD_FONT);
    }

    /** Hit marker, power-up and similar feedback: a quick subtitle, just under the crosshair. */
    void marker(Player player, Component text) {
        player.showTitle(Title.title(Component.empty(), text, MARKER_TIMES));
    }

    void outTitle(Player player, long secondsLeft) {
        player.showTitle(Title.title(messages.get("out.title"),
                messages.get("out.subtitle", Placeholder.unparsed("seconds", Long.toString(secondsLeft))),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1250), Duration.ofMillis(250))));
    }

    void bigTitle(Player player, Component title, Component subtitle) {
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(400))));
    }
}
