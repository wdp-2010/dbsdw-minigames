package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.pack.PackAssets;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.core.room.RoomBook.Outcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/** Private rooms on the Bukkit side: commands, menus, messages and countdowns around a {@link RoomBook}. */
public final class RoomService {
    private static final int SIZE = 54;
    /** Row 1: one icon per game (slots 0-5), then the map and the team rule. */
    private static final int MAP_SLOT = 6;
    private static final int RULE_SLOT = 8;
    /** Row 2: the modes of the chosen game. */
    private static final int MODE_ROW = 9;
    /** Header slot of each team row; the member heads follow it. Row 5 (slot 36) is "no team". */
    private static final int[] TEAM_ROWS = {18, 27};
    private static final int NONE_SLOT = 36;
    private static final int NONE_LAST = 44;
    private static final int INVITE_SLOT = 45;
    private static final int LEAVE_SLOT = 49;
    private static final int START_SLOT = 53;
    private static final int INVITE_BACK_SLOT = 49;
    private static final Material[] TEAM_WOOL = {Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL,
            Material.YELLOW_WOOL};
    private static final int COUNTDOWN_SOUND_FROM = 5;
    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200));

    private final Plugin plugin;
    private final RoomBook book;
    private final ArenaPool pool;
    private final Messages messages;
    private final RoomHooks hooks;
    private final Logger log;
    private final RoomMenuListener listener = new RoomMenuListener();
    private long ticks;

    public RoomService(Plugin plugin, RoomBook book, ArenaPool pool, Messages messages, RoomHooks hooks) {
        this.plugin = plugin;
        this.book = book;
        this.pool = pool;
        this.messages = messages;
        this.hooks = hooks;
        this.log = plugin.getLogger();
    }

    public Listener listener() {
        return listener;
    }

    public boolean inRoom(UUID player) {
        return book.inRoom(player);
    }

    /** The lobby item and plain {@code /room}: opens your room, making one first if needed. */
    public void openOrCreate(Player player) {
        if (hooks.inMatch(player.getUniqueId())) {
            return;
        }
        if (!book.inRoom(player.getUniqueId())) {
            List<ModeSpec> modes = hooks.modes();
            if (modes.isEmpty()) {
                player.sendMessage(messages.get("unavailable"));
                return;
            }
            hooks.leaveQueue(player.getUniqueId());
            book.create(player.getUniqueId(), modes.getFirst());
            player.sendMessage(messages.get("room.created"));
            log.info("[room] " + player.getName() + " made a room");
        }
        openRoom(player);
    }

    public void invite(Player host, Player target) {
        Outcome out = book.invite(host.getUniqueId(), target.getUniqueId());
        if (out != Outcome.OK) {
            error(host, out);
            return;
        }
        host.sendMessage(messages.get("room.invite-sent", name("player", target)));
        Component button = messages.get("room.join-button")
                .clickEvent(ClickEvent.runCommand("/room join " + host.getName()))
                .hoverEvent(HoverEvent.showText(messages.get("room.join-hover")));
        target.sendMessage(messages.get("room.invited", name("player", host), Placeholder.component("join", button)));
        log.info("[room] " + host.getName() + " invited " + target.getName());
    }

    /** Joins the room that {@code member} (usually the host who invited) is in. */
    public void join(Player player, Player member) {
        if (hooks.inMatch(player.getUniqueId())) {
            return;
        }
        Outcome out = book.join(player.getUniqueId(), member.getUniqueId());
        if (out != Outcome.OK) {
            error(player, out);
            return;
        }
        hooks.leaveQueue(player.getUniqueId());
        RoomView room = book.roomOf(player.getUniqueId()).orElseThrow();
        tellOthers(room, player.getUniqueId(), messages.get("room.member-joined", name("player", player)));
        player.sendMessage(messages.get("room.joined", Placeholder.unparsed("host", nameOf(room.host()))));
        if (room.playing()) {
            player.sendMessage(messages.get("room.wait-match"));
        }
        log.info("[room] " + player.getName() + " joined the room of " + nameOf(room.host()));
        openRoom(player);
        refresh();
    }

    /** {@code /room leave}, the leave button, {@code /leave} in the lobby. */
    public void leave(Player player) {
        leave(player, true);
    }

    /** Logging out: out of the room without a message to the player. */
    public void quit(Player player) {
        leave(player, false);
    }

    private void leave(Player player, boolean tell) {
        Optional<RoomBook.Left> left = book.leave(player.getUniqueId());
        if (left.isEmpty()) {
            if (tell) {
                error(player, Outcome.NO_ROOM);
            }
            return;
        }
        if (tell) {
            player.sendMessage(messages.get("room.left"));
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RoomMenu) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            }
        }
        afterLeave(left.get(), player.getName());
        log.info("[room] " + player.getName() + " left a room" + (tell ? "" : " (quit)"));
    }

    private void afterLeave(RoomBook.Left left, String who) {
        book.room(left.roomId()).ifPresent(room -> {
            tellAll(room, messages.get("room.member-left", Placeholder.unparsed("player", who)));
            left.newHost().ifPresent(host -> tellAll(room, messages.get("room.new-host",
                    Placeholder.unparsed("player", nameOf(host)))));
        });
        refresh();
    }

    public void kick(Player host, Player target) {
        Outcome out = book.kick(host.getUniqueId(), target.getUniqueId());
        if (out != Outcome.OK) {
            error(host, out);
            return;
        }
        target.sendMessage(messages.get("room.kicked"));
        if (target.getOpenInventory().getTopInventory().getHolder(false) instanceof RoomMenu) {
            Bukkit.getScheduler().runTask(plugin, () -> target.closeInventory());
        }
        book.roomOf(host.getUniqueId()).ifPresent(room ->
                tellAll(room, messages.get("room.member-left", name("player", target))));
        log.info("[room] " + host.getName() + " kicked " + target.getName());
        refresh();
    }

    /** The start button and {@code /room start}: starts, or cancels a running countdown. */
    public void start(Player host) {
        Optional<RoomView> room = book.roomOf(host.getUniqueId());
        Outcome out = room.isPresent() && room.get().counting()
                ? book.cancel(host.getUniqueId())
                : book.start(host.getUniqueId());
        if (out != Outcome.OK) {
            error(host, out);
        } else if (room.isPresent()) {
            boolean cancelled = room.get().counting();
            log.info("[room] " + host.getName() + (cancelled ? " cancelled the start" : " pressed start"));
            if (!cancelled && RoomBook.shortHanded(room.get().members().size(), room.get().mode())) {
                tellAll(room.get(), messages.get("room.short-warning"));
            }
        }
        refresh();
    }

    public void tick() {
        for (RoomEvent event : book.tick()) {
            switch (event) {
                case RoomEvent.CountdownTick(int id, List<UUID> members, int seconds) -> countdown(members, seconds);
                case RoomEvent.CountdownCancelled(int id, List<UUID> members) ->
                        tell(members, messages.get("room.cancelled"));
                case RoomEvent.Start(int id, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams) -> {
                    log.info("[room] room " + id + " starts " + mode.key() + " on " + arena.name());
                    if (!hooks.launch(id, mode, arena, teams)) {
                        book.matchEnded(id);
                        book.room(id).ifPresent(r -> tellAll(r, messages.get("room.start-failed")));
                    }
                }
            }
        }
        if (++ticks % 20 == 0) {
            refresh();
        }
    }

    /** The room's match is over and its players are back in the lobby. */
    public void matchEnded(int roomId) {
        book.matchEnded(roomId);
        book.room(roomId).ifPresent(room -> tellAll(room, messages.get("room.back",
                Placeholder.unparsed("host", nameOf(room.host())))));
    }

    // Command helpers

    public List<String> memberNames(Player player) {
        return book.roomOf(player.getUniqueId()).map(r -> r.members().stream()
                .filter(id -> !id.equals(player.getUniqueId())).map(RoomService::nameOf).toList()).orElse(List.of());
    }

    public List<String> inviterNames(Player player) {
        return book.inviters(player.getUniqueId()).stream().map(RoomService::nameOf).toList();
    }

    public List<String> invitableNames(Player player) {
        return invitable(player).stream().map(Player::getName).toList();
    }

    // Menus

    private void openRoom(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> book.roomOf(player.getUniqueId()).ifPresent(room -> {
            RoomMenu menu = new RoomMenu(RoomMenu.Kind.ROOM);
            Inventory inventory = Bukkit.createInventory(menu, SIZE,
                    messages.get("room.menu.title", Placeholder.unparsed("host", nameOf(room.host()))));
            menu.inventory(inventory);
            renderRoom(menu, player, room);
            player.openInventory(inventory);
        }));
    }

    private void openInvite(Player host) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            RoomMenu menu = new RoomMenu(RoomMenu.Kind.INVITE);
            Inventory inventory = Bukkit.createInventory(menu, SIZE, messages.get("room.menu.invite-title"));
            menu.inventory(inventory);
            renderInvite(menu, host);
            host.openInventory(inventory);
        });
    }

    /** Redraws every open room menu; closes it for players no longer in a room. */
    private void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof RoomMenu menu)) {
                continue;
            }
            Optional<RoomView> room = book.roomOf(player.getUniqueId());
            if (room.isEmpty() || hooks.inMatch(player.getUniqueId())) {
                // Often called from a click in that very menu: close it after the event.
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            } else if (menu.kind == RoomMenu.Kind.ROOM) {
                renderRoom(menu, player, room.get());
            } else {
                renderInvite(menu, player);
            }
        }
    }

    private void renderRoom(RoomMenu menu, Player viewer, RoomView room) {
        Inventory inv = menu.getInventory();
        inv.clear();
        menu.clearActions();
        boolean host = room.host().equals(viewer.getUniqueId());

        // Row 1 picks the game, row 2 the mode of that game.
        List<ModeSpec> modes = hooks.modes();
        String chosenGame = room.mode().gameId();
        List<String> games = modes.stream().map(ModeSpec::gameId).distinct().toList();
        for (int i = 0; i < games.size() && i < MAP_SLOT; i++) {
            String game = games.get(i);
            boolean selected = game.equals(chosenGame);
            ItemStack icon = hooks.gameIcon(game);
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(messages.get(selected ? "room.menu.game-selected" : host ? "room.menu.game-click"
                    : "room.menu.start-host-only")));
            meta.setEnchantmentGlintOverride(selected);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
            if (!selected) {
                ModeSpec first = modes.stream().filter(m -> m.gameId().equals(game)).findFirst().orElseThrow();
                menu.action(i, p -> act(p, book.setMode(p.getUniqueId(), first)));
            }
        }
        List<ModeSpec> gameModes = modes.stream().filter(m -> m.gameId().equals(chosenGame)).toList();
        for (int i = 0; i < gameModes.size() && i < 9; i++) {
            ModeSpec mode = gameModes.get(i);
            boolean selected = mode.key().equals(room.mode().key());
            ItemStack icon = hooks.modeIcon(mode);
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(MiniMessage.miniMessage().deserialize(mode.displayName()));
            meta.lore(List.of(messages.get(selected ? "room.menu.mode-selected" : host ? "room.menu.mode-click"
                    : "room.menu.start-host-only")));
            meta.setEnchantmentGlintOverride(selected);
            icon.setItemMeta(meta);
            inv.setItem(MODE_ROW + i, icon);
            menu.action(MODE_ROW + i, p -> act(p, book.setMode(p.getUniqueId(), mode)));
        }

        Component map = room.arena().<Component>map(Component::text).orElseGet(() -> messages.get("room.menu.map-random"));
        inv.setItem(MAP_SLOT, item(Material.FILLED_MAP, messages.get("room.menu.map", Placeholder.component("map", map)),
                host ? List.of(messages.get("room.menu.host-click")) : List.of()));
        menu.action(MAP_SLOT, p -> act(p, book.setArena(p.getUniqueId(), nextArena(room))));

        String ruleKey = "room.menu.rule-" + room.rule().name().toLowerCase(Locale.ROOT);
        inv.setItem(RULE_SLOT, item(Material.COMPARATOR,
                messages.get("room.menu.rule", Placeholder.component("rule", messages.get(ruleKey))),
                host ? List.of(messages.get("room.menu.host-click")) : List.of()));
        menu.action(RULE_SLOT, p -> act(p, book.cycleRule(p.getUniqueId())));

        boolean random = room.rule() == RoomTeams.Rule.RANDOM;
        int teams = Math.min(room.mode().teams(), TEAM_ROWS.length);
        for (int team = 0; team < teams; team++) {
            int header = TEAM_ROWS[team];
            int t = team;
            List<UUID> members = random ? List.of()
                    : room.members().stream().filter(m -> room.teamOf(m).equals(OptionalInt.of(t))).toList();
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get("room.menu.team-count", Placeholder.unparsed("count", String.valueOf(members.size())),
                    Placeholder.unparsed("max", String.valueOf(room.mode().maxPerTeam()))));
            lore.add(messages.get(switch (room.rule()) {
                case PICK -> "room.menu.team-click";
                case HOST -> "room.menu.team-host";
                case RANDOM -> "room.menu.team-random";
            }));
            inv.setItem(header, item(TEAM_WOOL[team], messages.get("team." + team), lore));
            menu.action(header, p -> act(p, book.pick(p.getUniqueId(), OptionalInt.of(t))));
            heads(menu, inv, room, viewer, members, header + 1, header + 8);
        }

        List<UUID> none = random ? room.members()
                : room.members().stream().filter(m -> room.teamOf(m).isEmpty()).toList();
        List<Component> noneLore = new ArrayList<>(List.of(messages.get("room.menu.none-lore")));
        if (room.rule() == RoomTeams.Rule.PICK) {
            noneLore.add(messages.get("room.menu.none-click"));
        }
        inv.setItem(NONE_SLOT, item(Material.LIGHT_GRAY_WOOL, messages.get("room.menu.none"), noneLore));
        menu.action(NONE_SLOT, p -> act(p, book.pick(p.getUniqueId(), OptionalInt.empty())));
        heads(menu, inv, room, viewer, none, NONE_SLOT + 1, NONE_LAST);

        if (host) {
            inv.setItem(INVITE_SLOT, item(Material.WRITABLE_BOOK, messages.get("room.menu.invite"),
                    List.of(messages.get("room.menu.invite-lore"))));
            menu.action(INVITE_SLOT, this::openInvite);
        }
        inv.setItem(LEAVE_SLOT, item(Material.BARRIER, messages.get("room.menu.leave"), List.of()));
        menu.action(LEAVE_SLOT, this::leave);

        List<Component> startLore = new ArrayList<>();
        Component startName;
        Material startIcon = Material.LIME_CONCRETE;
        if (room.playing()) {
            startName = messages.get("room.menu.playing");
            startIcon = Material.CLOCK;
        } else if (room.counting()) {
            startName = host ? messages.get("room.menu.cancel") : messages.get("room.menu.starting",
                    Placeholder.unparsed("seconds", String.valueOf(room.secondsLeft().getAsInt())));
            startIcon = host ? Material.RED_CONCRETE : Material.CLOCK;
            if (host) {
                startLore.add(messages.get("room.menu.starting",
                        Placeholder.unparsed("seconds", String.valueOf(room.secondsLeft().getAsInt()))));
            }
        } else {
            startName = messages.get("room.menu.start");
            if (!host) {
                startLore.add(messages.get("room.menu.start-host-only"));
                startIcon = Material.GRAY_CONCRETE;
            } else if (RoomBook.shortHanded(room.members().size(), room.mode())) {
                startLore.add(messages.get("room.menu.start-short"));
                startIcon = Material.YELLOW_CONCRETE;
            }
        }
        inv.setItem(START_SLOT, item(startIcon, startName, startLore));
        menu.action(START_SLOT, this::start);
    }

    private void heads(RoomMenu menu, Inventory inv, RoomView room, Player viewer, List<UUID> members, int from, int to) {
        boolean hostMoves = room.rule() == RoomTeams.Rule.HOST && room.host().equals(viewer.getUniqueId());
        int slot = from;
        int capacity = to - from + 1;
        for (UUID member : members) {
            if (slot > to) {
                break;
            }
            if (slot == to && members.size() > capacity) {
                // more members than slots: the last slot counts the rest
                inv.setItem(slot, item(Material.PAPER, messages.get("room.menu.more",
                        Placeholder.unparsed("count", String.valueOf(members.size() - capacity + 1))), List.of()));
                break;
            }
            List<Component> lore = new ArrayList<>();
            if (member.equals(room.host())) {
                lore.add(messages.get("room.menu.member-host"));
            }
            if (hostMoves) {
                lore.add(messages.get("room.menu.member-move"));
                menu.action(slot, p -> act(p, book.move(p.getUniqueId(), member)));
            }
            inv.setItem(slot, head(member, lore));
            slot++;
        }
    }

    private void renderInvite(RoomMenu menu, Player host) {
        Inventory inv = menu.getInventory();
        inv.clear();
        menu.clearActions();
        List<Player> candidates = invitable(host);
        if (candidates.isEmpty()) {
            inv.setItem(22, item(Material.PAPER, messages.get("room.menu.invite-empty"), List.of()));
        }
        for (int slot = 0; slot < candidates.size() && slot < 45; slot++) {
            Player target = candidates.get(slot);
            inv.setItem(slot, head(target.getUniqueId(), List.of(messages.get("room.menu.invite-lore"))));
            menu.action(slot, p -> {
                invite(p, target);
                openRoom(p);
            });
        }
        inv.setItem(INVITE_BACK_SLOT, item(Material.ARROW, messages.get("room.menu.back"), List.of()));
        menu.action(INVITE_BACK_SLOT, this::openRoom);
    }

    /** Online players the host could invite: not in a match and not in the host's room. */
    private List<Player> invitable(Player host) {
        List<UUID> members = book.roomOf(host.getUniqueId()).map(RoomView::members).orElse(List.of());
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(host) && !members.contains(p.getUniqueId()) && !hooks.inMatch(p.getUniqueId()))
                .map(p -> (Player) p).sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
    }

    /** The map after the room's current choice: random, then each fitting arena, then random again. */
    private Optional<String> nextArena(RoomView room) {
        List<String> names = pool.all().stream().filter(a -> a.fits(room.mode())).map(ArenaDefinition::name)
                .sorted().toList();
        if (room.arena().isEmpty()) {
            return names.isEmpty() ? Optional.empty() : Optional.of(names.getFirst());
        }
        int i = names.indexOf(room.arena().get());
        return i < 0 || i + 1 >= names.size() ? Optional.empty() : Optional.of(names.get(i + 1));
    }

    /** A menu click's outcome: an error is told, anything else redraws the menus. */
    private void act(Player player, Outcome out) {
        if (out != Outcome.OK) {
            error(player, out);
        }
        refresh();
    }

    // Messages

    private void countdown(List<UUID> members, int seconds) {
        Title title = Title.title(messages.get("queue.countdown.title", Placeholder.unparsed("seconds", String.valueOf(seconds))),
                messages.get("queue.countdown.subtitle"), COUNTDOWN_TIMES);
        players(members).forEach(p -> {
            p.showTitle(title);
            if (seconds <= COUNTDOWN_SOUND_FROM) {
                PackItems.play(p, PackAssets.SOUND_COUNTDOWN, 0.8f, 1f);
            }
        });
    }

    private void error(Player player, Outcome out) {
        player.sendMessage(messages.get("room.error." + out.name().toLowerCase(Locale.ROOT).replace('_', '-')));
    }

    private void tellAll(RoomView room, Component message) {
        tell(room.members(), message);
    }

    private void tellOthers(RoomView room, UUID except, Component message) {
        tell(room.members().stream().filter(id -> !id.equals(except)).toList(), message);
    }

    private static void tell(Collection<UUID> ids, Component message) {
        players(ids).forEach(p -> p.sendMessage(message));
    }

    private static List<Player> players(Collection<UUID> ids) {
        return ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private static TagResolver name(String tag, Player player) {
        return Placeholder.unparsed(tag, player.getName());
    }

    static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? "?" : offline.getName();
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack head(UUID id, List<Component> lore) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            meta.setPlayerProfile(online.getPlayerProfile());
        }
        meta.itemName(Component.text(nameOf(id)));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
