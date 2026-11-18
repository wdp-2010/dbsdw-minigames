package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.paintball.match.PaintballMatch;
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
    /** Stats paintball reports (the core adds matches, wins, losses, draws). */
    static final List<String> STATS = List.of("eliminations", "outs", "hits", "shots", "captures", "returns");

    private final Plugin plugin;
    private final Settings settings;
    private final Messages messages;
    private final ModeSpec tdm;
    private final ModeSpec ctf;
    private final Map<UUID, PaintballMatch> players = new HashMap<>();
    private final Map<UUID, PaintballMatch> balls = new HashMap<>();

    PaintballGame(Plugin plugin, Settings settings, Messages messages, String tdmDisplayName, String ctfDisplayName) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.tdm = new ModeSpec("paintball", "tdm", tdmDisplayName, 2, settings.tdm().minPerTeam(),
                settings.tdm().maxPerTeam(), Set.of());
        this.ctf = new ModeSpec("paintball", "ctf", ctfDisplayName, 2, settings.ctf().minPerTeam(),
                settings.ctf().maxPerTeam(), Set.of(ArenaFeature.FLAGS));
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
        return List.of(tdm, ctf);
    }

    @Override
    public List<String> stats() {
        return STATS;
    }

    @Override
    public Component statName(String stat) {
        return messages.get("stat." + stat);
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        return new PaintballMatch(context, this,
                context.mode().id().equals("ctf") ? PaintballMatch.Mode.CTF : PaintballMatch.Mode.TDM);
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
    public Map<UUID, PaintballMatch> players() {
        return players;
    }

    public Map<UUID, PaintballMatch> balls() {
        return balls;
    }
}
