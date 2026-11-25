package be.dbsdw.minigames.core.admin;

import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.ArenaStore;
import be.dbsdw.minigames.core.arena.ArenaValidator;
import be.dbsdw.minigames.core.arena.Pos;
import be.dbsdw.minigames.core.lobby.LobbyService;
import be.dbsdw.minigames.core.progress.ProgressService;
import be.dbsdw.minigames.core.stats.Leaderboards;
import be.dbsdw.minigames.core.stats.StatNames;
import be.dbsdw.minigames.core.match.GameRegistryImpl;
import be.dbsdw.minigames.core.npc.GameNpc;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /mg}: lobby, NPC and arena setup for admins ({@code minigames.admin}). */
public final class MgCommand implements BasicCommand {
    public static final String PERMISSION = "minigames.admin";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");
    private static final List<String> ARENA_SUB =
            List.of("create", "modes", "setspawn", "setflag", "addpowerup", "setbounds", "copies", "save", "list",
                    "delete");

    private final GameRegistryImpl games;
    private final ArenaPool pool;
    private final ArenaStore store;
    private final LobbyService lobby;
    private final GameNpc npc;
    private final Leaderboards leaderboards;
    private final ProgressService progress;
    private final Logger log;
    private final Map<UUID, ArenaDefinition> drafts = new HashMap<>();

    public MgCommand(GameRegistryImpl games, ArenaPool pool, ArenaStore store, LobbyService lobby, GameNpc npc,
                     Leaderboards leaderboards, ProgressService progress, Logger log) {
        this.games = games;
        this.pool = pool;
        this.store = store;
        this.lobby = lobby;
        this.npc = npc;
        this.leaderboards = leaderboards;
        this.progress = progress;
        this.log = log;
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            usage(sender);
            return;
        }
        switch (args[0]) {
            case "setlobby" -> withPlayer(sender, admin -> {
                lobby.setSpawn(admin.getLocation());
                ok(sender, "Lobby spawn set here.");
            });
            case "npc" -> npcCommand(sender, args);
            case "arena" -> arenaCommand(sender, args);
            case "leaderboard" -> leaderboardCommand(sender, args);
            case "level" -> levelCommand(sender, args);
            default -> usage(sender);
        }
    }

    /** {@code /mg level set <player> <game|account|all> <level>}: gives a player exactly the XP of that level. */
    private void levelCommand(CommandSender sender, String[] args) {
        int max = progress.curve().maxLevel();
        String usage = "Usage: /mg level set <player> <game|account|all> <level 1-" + max + ">";
        if (args.length != 5 || !args[1].equals("set")) {
            error(sender, usage);
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            level = 0;
        }
        if (level < 1 || level > max) {
            error(sender, usage);
            return;
        }
        List<String> targets;
        if (args[3].equals("all")) {
            targets = Stream.concat(Stream.of(ProgressService.ACCOUNT), games.games().stream().map(GameType::id)).toList();
        } else if (args[3].equals(ProgressService.ACCOUNT) || games.game(args[3]).isPresent()) {
            targets = List.of(args[3]);
        } else {
            error(sender, "Unknown game " + args[3] + ". Use a game id, account or all.");
            return;
        }
        int chosen = level;
        progress.setLevel(args[2], targets, chosen, name -> {
            if (name == null) {
                error(sender, "No player " + args[2] + " (they must have played before), or the XP could not be saved; see the server log.");
                return;
            }
            log.info("[progress] " + sender.getName() + " set " + name + " to level " + chosen + " in " + targets);
            ok(sender, name + " is now level " + chosen + " in " + String.join(", ", targets) + ".");
        });
    }

    private void leaderboardCommand(CommandSender sender, String[] args) {
        if (args.length >= 4 && args[1].equals("add")) {
            withPlayer(sender, admin -> {
                Optional<GameType> game = games.game(args[2]);
                if (game.isEmpty() || !StatNames.of(game.get()).contains(args[3])) {
                    error(sender, "Unknown game or stat. Stats of " + args[2] + ": "
                            + game.map(g -> String.join(", ", StatNames.of(g))).orElse("-"));
                    return;
                }
                leaderboards.add(args[2], args[3], admin.getLocation().add(0, 2, 0));
                ok(sender, "Leaderboard for " + args[2] + " " + args[3] + " placed (2 blocks above you).");
            });
        } else if (args.length == 2 && args[1].equals("remove")) {
            withPlayer(sender, admin -> ok(sender, "Removed " + leaderboards.removeNear(admin.getLocation()) + " leaderboard(s)."));
        } else {
            error(sender, "Usage: /mg leaderboard add <game> <stat> | /mg leaderboard remove");
        }
    }

    private void npcCommand(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equals("set")) {
            withPlayer(sender, admin -> {
                if (npc.spawn(admin, args[2])) {
                    ok(sender, "NPC for " + args[2] + " placed.");
                } else {
                    error(sender, "Unknown game " + args[2] + ".");
                }
            });
        } else if (args.length == 2 && args[1].equals("remove")) {
            withPlayer(sender, admin -> ok(sender, "Removed " + npc.removeNear(admin) + " NPC(s)."));
        } else {
            error(sender, "Usage: /mg npc set <game> | /mg npc remove");
        }
    }

    private void arenaCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /mg arena " + String.join("|", ARENA_SUB));
            return;
        }
        switch (args[1]) {
            case "list" -> list(sender);
            case "delete" -> delete(sender, args);
            case "create" -> withPlayer(sender, admin -> create(admin, args));
            default -> withPlayer(sender, admin -> edit(admin, args));
        }
    }

    private void create(Player admin, String[] args) {
        if (args.length != 3 || !NAME.matcher(args[2]).matches()) {
            error(admin, "Usage: /mg arena create <name>  (name: a-z, 0-9, _ or -)");
            return;
        }
        Optional<ArenaDefinition> saved = pool.get(args[2]);
        ArenaDefinition draft = saved.orElseGet(() -> ArenaDefinition.empty(args[2], admin.getWorld().getName()));
        drafts.put(admin.getUniqueId(), draft);
        ok(admin, (saved.isPresent() ? "Editing saved arena " : "Created draft arena ") + draft.name()
                + " in world " + draft.world() + ". Finish with /mg arena save.");
    }

    private void edit(Player admin, String[] args) {
        ArenaDefinition draft = drafts.get(admin.getUniqueId());
        if (draft == null) {
            error(admin, "Start with /mg arena create <name>.");
            return;
        }
        if (!admin.getWorld().getName().equals(draft.world())) {
            error(admin, "Arena " + draft.name() + " is in world " + draft.world() + "; go there first.");
            return;
        }
        Pos here = pos(admin.getLocation());
        ArenaDefinition next;
        switch (args[1]) {
            case "modes" -> {
                Set<String> modes = new LinkedHashSet<>();
                Arrays.stream(args, 2, args.length).flatMap(a -> Arrays.stream(a.split(",")))
                        .filter(s -> !s.isBlank()).forEach(modes::add);
                if (modes.isEmpty()) {
                    error(admin, "Usage: /mg arena modes <game:mode>[,<game:mode>…]");
                    return;
                }
                next = draft.withModes(modes);
            }
            case "setspawn" -> {
                Optional<Integer> team = team(admin, args);
                if (team.isEmpty()) {
                    return;
                }
                next = draft.withSpawn(team.get(), here);
            }
            case "setflag" -> {
                Optional<Integer> team = team(admin, args);
                if (team.isEmpty()) {
                    return;
                }
                next = draft.withFlag(team.get(), here);
            }
            case "addpowerup" -> next = draft.withPowerup(here);
            case "setbounds" -> {
                if (args.length != 3 || !(args[2].equals("1") || args[2].equals("2"))) {
                    error(admin, "Usage: /mg arena setbounds <1|2>");
                    return;
                }
                Location l = admin.getLocation();
                next = draft.withCorner(Integer.parseInt(args[2]), new Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ(), 0, 0));
            }
            case "copies" -> {
                int count;
                try {
                    count = args.length == 3 ? Integer.parseInt(args[2]) : -1;
                } catch (NumberFormatException e) {
                    count = -1;
                }
                if (count < 0 || count > ArenaDefinition.MAX_COPIES) {
                    error(admin, "Usage: /mg arena copies <0-" + ArenaDefinition.MAX_COPIES
                            + ">  (0: play on the arena itself; more: a fresh copy per match, built east of it)");
                    return;
                }
                next = draft.withCopies(count);
            }
            case "save" -> {
                save(admin, draft);
                return;
            }
            default -> {
                error(admin, "Usage: /mg arena " + String.join("|", ARENA_SUB));
                return;
            }
        }
        drafts.put(admin.getUniqueId(), next);
        ok(admin, "Arena " + draft.name() + ": " + args[1] + " done.");
    }

    private void save(Player admin, ArenaDefinition draft) {
        ArenaValidator.Validation v = ArenaValidator.validate(draft, games::mode, pool.copySpacing());
        v.warnings().forEach(w -> admin.sendMessage(Component.text(w, NamedTextColor.YELLOW)));
        if (!v.errors().isEmpty()) {
            v.errors().forEach(e -> error(admin, e));
            error(admin, "Not saved.");
            return;
        }
        try {
            store.save(draft);
        } catch (IOException e) {
            log.severe("[arena] could not save " + draft.name() + ": " + e.getMessage());
            error(admin, "Could not write the arena file; see the server log.");
            return;
        }
        pool.put(draft);
        drafts.remove(admin.getUniqueId());
        log.info("[arena] " + admin.getName() + " saved arena " + draft.name());
        ok(admin, "Arena " + draft.name() + " saved.");
    }

    private void list(CommandSender sender) {
        Collection<ArenaDefinition> all = pool.all();
        if (all.isEmpty()) {
            ok(sender, "No arenas.");
        }
        for (ArenaDefinition a : all) {
            String use = a.isTemplate()
                    ? pool.busyCount(a.name()) + "/" + a.copies() + " copies in use"
                    : pool.isBusy(a.name()) ? "busy" : "free";
            ok(sender, a.name() + " (" + a.world() + ") " + String.join(",", a.modes().stream().sorted().toList())
                    + " — " + use);
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length != 3) {
            error(sender, "Usage: /mg arena delete <name>");
            return;
        }
        String name = args[2];
        if (pool.get(name).isEmpty()) {
            error(sender, "No arena " + name + ".");
            return;
        }
        if (pool.isBusy(name)) {
            error(sender, "Arena " + name + " is in use; try again after the match.");
            return;
        }
        try {
            store.delete(name);
        } catch (IOException e) {
            log.severe("[arena] could not delete " + name + ": " + e.getMessage());
            error(sender, "Could not delete the arena file; see the server log.");
            return;
        }
        pool.remove(name);
        log.info("[arena] " + sender.getName() + " deleted arena " + name);
        ok(sender, "Arena " + name + " deleted.");
    }

    private Optional<Integer> team(Player admin, String[] args) {
        try {
            if (args.length == 3) {
                int team = Integer.parseInt(args[2]);
                if (team >= 0 && team < 4) {
                    return Optional.of(team);
                }
            }
        } catch (NumberFormatException e) {
            // fall through to the usage message
        }
        error(admin, "Usage: /mg arena " + args[1] + " <team 0-3>");
        return Optional.empty();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String last = args.length == 0 ? "" : args[args.length - 1];
        List<String> options = switch (args.length) {
            case 0, 1 -> List.of("setlobby", "npc", "arena", "leaderboard", "level");
            case 2 -> switch (args[0]) {
                case "npc" -> List.of("set", "remove");
                case "arena" -> ARENA_SUB;
                case "leaderboard" -> List.of("add", "remove");
                case "level" -> List.of("set");
                default -> List.of();
            };
            case 3 -> switch (args[0] + " " + args[1]) {
                case "npc set", "leaderboard add" -> games.games().stream().map(GameType::id).toList();
                case "arena modes" -> games.games().stream().flatMap(g -> g.modes().stream()).map(ModeSpec::key).toList();
                case "arena delete", "arena create" -> pool.all().stream().map(ArenaDefinition::name).toList();
                case "arena setspawn", "arena setflag" -> List.of("0", "1", "2", "3");
                case "arena setbounds" -> List.of("1", "2");
                case "level set" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            case 4 -> args[0].equals("leaderboard") && args[1].equals("add")
                    ? games.game(args[2]).map(StatNames::of).orElse(List.of())
                    : args[0].equals("level") && args[1].equals("set")
                    ? Stream.concat(Stream.of("all", ProgressService.ACCOUNT), games.games().stream().map(GameType::id)).toList()
                    : List.of();
            default -> List.of();
        };
        return options.stream().filter(o -> o.startsWith(last)).toList();
    }

    private void usage(CommandSender sender) {
        Stream.of("/mg setlobby", "/mg npc set <game> | /mg npc remove",
                "/mg arena create <name> | modes <game:mode,…> | setspawn <team> | setflag <team>",
                "/mg arena addpowerup | setbounds <1|2> | save | list | delete <name>",
                "/mg leaderboard add <game> <stat> | /mg leaderboard remove",
                "/mg level set <player> <game|account|all> <level>")
                .forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GRAY)));
    }

    private void withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            error(sender, "This needs a position; run it in-game.");
        }
    }

    private static Pos pos(Location l) {
        return new Pos(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
