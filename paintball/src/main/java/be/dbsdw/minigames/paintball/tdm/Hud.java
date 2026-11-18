package be.dbsdw.minigames.paintball.tdm;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.TeamScore;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Duration;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** Sidebar, boss bar, action bar and titles of one match. */
final class Hud {
    private static final String[] LINES = {"score0", "score1", "blank", "time"};

    private final MatchContext ctx;
    private final Messages messages;
    private final Settings settings;
    private final Scoreboard board;
    private final Objective objective;
    private final BossBar bossBar;

    Hud(MatchContext ctx, Messages messages, Settings settings) {
        this.ctx = ctx;
        this.messages = messages;
        this.settings = settings;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("paintball", Criteria.DUMMY, messages.get("hud.title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        for (int i = 0; i < LINES.length; i++) {
            objective.getScore(LINES[i]).setScore(LINES.length - i);
        }
        objective.getScore("blank").customName(Component.empty());
        this.bossBar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
    }

    void show(Player player) {
        player.setScoreboard(board);
        player.showBossBar(bossBar);
    }

    void hide(Player player) {
        if (player.getScoreboard() == board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        player.hideBossBar(bossBar);
        player.sendActionBar(Component.empty());
    }

    void refresh(long now, TeamScore score) {
        for (int team = 0; team < 2; team++) {
            objective.getScore("score" + team).customName(messages.get("hud.score",
                    Placeholder.component("team", ctx.teamName(team)),
                    Placeholder.unparsed("score", Integer.toString(score.points(team)))));
        }
        long left = settings.timeLimitTicks() - now;
        String time = Clock.format(left);
        objective.getScore("time").customName(messages.get("hud.time", Placeholder.unparsed("time", time)));
        bossBar.name(messages.get("hud.bossbar", Placeholder.unparsed("time", time)));
        bossBar.progress((float) Math.clamp((double) left / settings.timeLimitTicks(), 0.0, 1.0));
    }

    /** Hits left as filled dots in the team colour, then grey dots; plus marker text and protection. */
    void actionBar(Player player, int team, int hitsLeft, boolean protectedNow, Component prefix) {
        NamedTextColor colour = team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE;
        Component bar = prefix
                .append(Component.text("●".repeat(Math.max(0, hitsLeft)), colour))
                .append(Component.text("●".repeat(Math.max(0, settings.hitsToOut() - hitsLeft)), NamedTextColor.DARK_GRAY));
        if (protectedNow) {
            bar = bar.append(messages.get("hud.protected"));
        }
        player.sendActionBar(bar);
    }

    void outTitle(Player player, long secondsLeft) {
        player.showTitle(Title.title(messages.get("out.title"),
                messages.get("out.subtitle", Placeholder.unparsed("seconds", Long.toString(secondsLeft))),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1250), Duration.ofMillis(250))));
    }
}
