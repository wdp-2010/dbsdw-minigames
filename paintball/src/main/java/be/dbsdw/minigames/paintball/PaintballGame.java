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
import be.dbsdw.minigames.paintball.rules.BombSettings;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.ShopCash;
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
    static final List<String> STATS = List.of("eliminations", "outs", "hits", "shots", "captures", "returns",
            "rounds", "plants", "defuses");

    private final Plugin plugin;
    private final Settings settings;
    private final Messages messages;
    private final ModeSpec tdm;
    private final ModeSpec ctf;
    private final ModeSpec tdmShop;
    private final ModeSpec ctfShop;
    private final ModeSpec bomb;
    private final int shopLevel;
    private final BombSettings bombSettings;
    private final ShopCash shopCash;
    private final Map<UUID, PaintballMatch> players = new HashMap<>();
    private final Map<UUID, PaintballMatch> balls = new HashMap<>();
    private final Map<UUID, BotSetup> botSetups = new HashMap<>();
    private final Map<UUID, BotMenu.Choice> botChoices = new HashMap<>();

    /** Bots for a player's next match: their level and how many bots join each team. */
    public record BotSetup(BotLevel level, List<Integer> perTeam) {
        public BotSetup {
            perTeam = List.copyOf(perTeam);
        }

        public int in(int team) {
            return team >= 0 && team < perTeam.size() ? perTeam.get(team) : 0;
        }
    }

    /** XP per stat: a hit 1, an elimination 3, a flag capture 15, a flag return 5, a round won 10, bomb planted or defused 5. */
    private static final Map<String, Integer> XP = Map.of("hits", 1, "eliminations", 3, "captures", 15, "returns", 5,
            "rounds", 10, "plants", 5, "defuses", 5);

    /**
     * {@code shopLevel}: the paintball level that unlocks the two shop modes; the bomb mode unlocks at
     * {@code bomb.unlockLevel()}.
     */
    PaintballGame(Plugin plugin, Settings settings, Messages messages, String tdmDisplayName, String ctfDisplayName,
                  String tdmShopDisplayName, String ctfShopDisplayName, int shopLevel, String bombDisplayName,
                  BombSettings bomb, ShopCash shopCash) {
        this.shopLevel = shopLevel;
        this.bombSettings = bomb;
        this.shopCash = shopCash;
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.tdm = new ModeSpec("paintball", "tdm", tdmDisplayName, 2, settings.tdm().minPerTeam(),
                settings.tdm().maxPerTeam(), Set.of());
        this.ctf = new ModeSpec("paintball", "ctf", ctfDisplayName, 2, settings.ctf().minPerTeam(),
                settings.ctf().maxPerTeam(), Set.of(ArenaFeature.FLAGS));
        // The shop modes play like the plain ones (same rules, same arenas) with a shop on top.
        this.tdmShop = new ModeSpec("paintball", "tdm_shop", tdmShopDisplayName, 2, settings.tdm().minPerTeam(),
                settings.tdm().maxPerTeam(), Set.of());
        this.ctfShop = new ModeSpec("paintball", "ctf_shop", ctfShopDisplayName, 2, settings.ctf().minPerTeam(),
                settings.ctf().maxPerTeam(), Set.of(ArenaFeature.FLAGS));
        // Rounds of attack and defence; the bomb site is the defending team's flag spot.
        this.bomb = new ModeSpec("paintball", "bomb", bombDisplayName, 2, bomb.minPerTeam(), bomb.maxPerTeam(),
                Set.of(ArenaFeature.FLAGS));
    }

    /** Whether the mode has a shop: the two shop variants and the bomb mode. */
    public static boolean isShop(ModeSpec mode) {
        return mode.id().endsWith("_shop") || isBomb(mode);
    }

    public static boolean isBomb(ModeSpec mode) {
        return mode.id().equals("bomb");
    }

    /** Whether the mode is a capture the flag mode, with or without shop. */
    public static boolean isCtf(ModeSpec mode) {
        return mode.id().startsWith("ctf");
    }

    @Override
    public int xpPerStat(String stat) {
        return XP.getOrDefault(stat, 0);
    }

    @Override
    public int requiredLevel(ModeSpec mode) {
        if (isBomb(mode)) {
            return bombSettings.unlockLevel();
        }
        return isShop(mode) ? shopLevel : 0;
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
        if (isBomb(mode)) {
            return PackItems.item(Material.PAPER, PackAssets.BOMB, null);
        }
        return isCtf(mode) ? PackItems.item(Material.PAPER, PackAssets.FLAG, PackAssets.BLUE) : iconItem();
    }

    @Override
    public List<ModeSpec> modes() {
        return List.of(tdm, ctf, tdmShop, ctfShop, bomb);
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
            List<Integer> perTeam = team == 0 ? List.of(choice.perTeam() - 1, choice.perTeam())
                    : List.of(choice.perTeam(), choice.perTeam() - 1);
            botSetups.put(host.getUniqueId(), new BotSetup(choice.level(), perTeam));
            startOnTeam.accept(team);
        });
        return true;
    }

    /** A room's host can add bots to the teams, at any level. */
    @Override
    public List<Component> roomBotLevels(ModeSpec mode) {
        return java.util.Arrays.stream(BotLevel.values()).map(l -> messages.get("bot.level." + l.id())).toList();
    }

    /** The bots a room's host added, for the match that starts now; all zeros forgets them. */
    @Override
    public void roomBots(List<UUID> players, ModeSpec mode, List<Integer> perTeam, int level) {
        players.forEach(botSetups::remove);
        if (players.isEmpty() || perTeam.stream().allMatch(n -> n <= 0)) {
            return;
        }
        BotLevel[] levels = BotLevel.values();
        botSetups.put(players.getFirst(), new BotSetup(levels[Math.floorMod(level, levels.length)], perTeam));
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
        PaintballMatch.Mode mode = isBomb(context.mode()) ? PaintballMatch.Mode.BOMB
                : isCtf(context.mode()) ? PaintballMatch.Mode.CTF : PaintballMatch.Mode.TDM;
        return new PaintballMatch(context, this, mode);
    }

    public Plugin plugin() {
        return plugin;
    }

    public Settings settings() {
        return settings;
    }

    public BombSettings bombSettings() {
        return bombSettings;
    }

    public ShopCash shopCash() {
        return shopCash;
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
