package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.bot.BotLevel;
import be.dbsdw.minigames.paintball.match.PaintballMatch;
import be.dbsdw.minigames.paintball.rules.Settings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private final Map<UUID, BotSetup> botSetups = new HashMap<>();
    private final Map<UUID, BotMenu.Choice> botChoices = new HashMap<>();

    /** Bots for the next match of a player who started alone: their level, team size and the player's team. */
    public record BotSetup(BotLevel level, int perTeam, int team) {}

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
    public ItemStack iconItem() {
        return PackItems.item(Material.PAPER, PackAssets.GUN, PackAssets.RED);
    }

    /** Team deathmatch shows the gun, capture the flag a flag. */
    @Override
    public ItemStack modeIcon(ModeSpec mode) {
        return mode.id().equals("ctf") ? PackItems.item(Material.PAPER, PackAssets.FLAG, PackAssets.BLUE) : iconItem();
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

    /** Paintball alone means against bots: listed in {@code /solo}. */
    @Override
    public boolean supportsSolo() {
        return true;
    }

    /** A room's host pressed start alone: pick the bots, then the room starts with the host on a random team. */
    @Override
    public boolean startAlone(Player host, ModeSpec mode, IntConsumer startOnTeam) {
        BotMenu.open(host, this, mode, choice -> {
            int team = ThreadLocalRandom.current().nextInt(2);
            botSetups.put(host.getUniqueId(), new BotSetup(choice.level(), choice.perTeam(), team));
            startOnTeam.accept(team);
        });
        return true;
    }

    /** A room's host pressed start with others in the room: offer bots to fill the teams, or start without. */
    @Override
    public boolean startWithBots(Player host, ModeSpec mode, Runnable start) {
        BotMenu.openForRoom(host, this, mode, choice -> {
            botSetups.put(host.getUniqueId(), new BotSetup(choice.level(), choice.perTeam(), -1));
            start.run();
        }, () -> {
            botSetups.remove(host.getUniqueId());
            start.run();
        });
        return true;
    }

    /** The bots a player chose for their next match, once; null when they did not. */
    public BotSetup takeBots(UUID player) {
        return botSetups.remove(player);
    }

    public BotMenu.Choice botChoice(UUID player) {
        return botChoices.getOrDefault(player, new BotMenu.Choice(BotLevel.NORMAAL, 1));
    }

    public void rememberBotChoice(UUID player, BotMenu.Choice choice) {
        botChoices.put(player, choice);
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
