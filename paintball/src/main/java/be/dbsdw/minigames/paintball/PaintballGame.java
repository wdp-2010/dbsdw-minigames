package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.tdm.TdmMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/** The paintball game: its modes, and which running match a player or paintball belongs to. */
public final class PaintballGame implements GameType {
    private final Plugin plugin;
    private final Settings settings;
    private final Messages messages;
    private final ModeSpec tdm;
    private final Map<UUID, TdmMatch> players = new HashMap<>();
    private final Map<UUID, TdmMatch> balls = new HashMap<>();

    PaintballGame(Plugin plugin, Settings settings, Messages messages, String tdmDisplayName) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.tdm = new ModeSpec("paintball", "tdm", tdmDisplayName, 2, settings.minPerTeam(), settings.maxPerTeam(),
                Set.of());
    }

    @Override
    public String id() {
        return "paintball";
    }

    @Override
    public Component displayName() {
        return messages.get("game.name");
    }

    @Override
    public Material icon() {
        return Material.SNOWBALL;
    }

    @Override
    public List<ModeSpec> modes() {
        return List.of(tdm);
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        return new TdmMatch(context, this);
    }

    public Plugin plugin() {
        return plugin;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    /** player id or paintball entity id -> match; maintained by the matches. */
    public Map<UUID, TdmMatch> players() {
        return players;
    }

    public Map<UUID, TdmMatch> balls() {
        return balls;
    }
}
