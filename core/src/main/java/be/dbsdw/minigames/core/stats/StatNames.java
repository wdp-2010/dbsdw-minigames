package be.dbsdw.minigames.core.stats;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Stat names: the core's match stats first, then each game's own stats. */
public final class StatNames {
    /** Kept by the core for every game. */
    public static final List<String> CORE = List.of("matches", "wins", "losses", "draws");

    private StatNames() {
    }

    public static List<String> of(GameType game) {
        List<String> all = new ArrayList<>(CORE);
        game.stats().stream().filter(s -> !all.contains(s)).forEach(all::add);
        return all;
    }

    public static Component display(GameType game, String stat, Messages messages) {
        return CORE.contains(stat) ? messages.get("stats.name." + stat) : game.statName(stat);
    }
}
