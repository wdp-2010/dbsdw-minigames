package be.dbsdw.minigames.verstoppertje;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.verstoppertje.match.VerstopMatch;
import be.dbsdw.minigames.verstoppertje.rules.Settings;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Verstoppertje: hiders turn into blocks, seekers hunt them; found hiders join the seekers. */
final class VerstopGame implements GameType {
    static final String ID = "verstoppertje";

    private final Plugin plugin;
    private final Settings settings;
    private final List<Material> disguises;
    private final Messages messages;
    private final NamespacedKey itemKey;
    private final ModeSpec mode;
    private final Set<VerstopMatch> active = new CopyOnWriteArraySet<>();
    private final RandomGenerator rnd = RandomGenerator.getDefault();

    VerstopGame(Plugin plugin, Settings settings, List<Material> disguises, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.disguises = List.copyOf(disguises);
        this.messages = messages;
        this.itemKey = new NamespacedKey(plugin, "disguise");
        this.mode = new ModeSpec(ID, "klassiek", messages.raw("mode.klassiek"), 2, 1, settings.maxPerTeam(), Set.of());
    }

    Optional<VerstopMatch> matchOf(Player player) {
        return active.stream().filter(m -> m.has(player)).findFirst();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return messages.get("game.name");
    }

    @Override
    public Material icon() {
        return Material.BARREL;
    }

    @Override
    public List<ModeSpec> modes() {
        return List.of(mode);
    }

    @Override
    public List<String> stats() {
        return List.of("found", "hidden");
    }

    @Override
    public Component statName(String stat) {
        return messages.get("stat." + stat);
    }

    @Override
    public Component teamName(ModeSpec mode, int team) {
        return team == VerstopMatch.HIDERS ? messages.get("team.hiders")
                : team == VerstopMatch.SEEKERS ? messages.get("team.seekers") : null;
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        return new VerstopMatch(plugin, context, settings, disguises, messages, plugin.getLogger(), rnd, itemKey, active);
    }
}
