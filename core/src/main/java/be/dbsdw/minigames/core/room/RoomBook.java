package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.voice.VoiceRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Private rooms: a host gathers invited players, picks mode, arena, team rule and voice rule, and starts a match.
 * A host alone may start too (a solo test match; bots will fill such rooms later). A player is in at most one room. Call {@link #tick()} once per server tick.
 */
public final class RoomBook {
    public enum Outcome {
        OK, NO_ROOM, NOT_HOST, IN_ROOM, NO_INVITE, NOT_MEMBER, FULL, TEAM_FULL, TOO_FEW, EMPTY_TEAM, BUSY,
        NO_ARENA, PLAYING, COUNTING, NOT_ALLOWED
    }

    /** A player left a room: whether that removed it, and the new host if the host left. */
    public record Left(int roomId, boolean removed, Optional<UUID> newHost) {}

    public static final int COUNTDOWN_SECONDS = 5;
    public static final int INVITE_TICKS = 60 * 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_MEMBERS = 1;

    private final ArenaPool pool;
    private final RandomGenerator rnd;
    private final VoiceRule defaultVoice;
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, Room> roomByMember = new HashMap<>();
    /** Invites per invited player: room id to the tick it expires. */
    private final Map<UUID, Map<Integer, Long>> invites = new HashMap<>();
    private final List<RoomEvent> pending = new ArrayList<>();
    private int nextId = 1;
    private long now;

    public RoomBook(ArenaPool pool, RandomGenerator rnd) {
        this(pool, rnd, VoiceRule.TEAM);
    }

    /** New rooms start with {@code defaultVoice}; the host may change it. */
    public RoomBook(ArenaPool pool, RandomGenerator rnd, VoiceRule defaultVoice) {
        this.pool = pool;
        this.rnd = rnd;
        this.defaultVoice = defaultVoice;
    }

    public Outcome create(UUID host, ModeSpec mode) {
        if (roomByMember.containsKey(host)) {
            return Outcome.IN_ROOM;
        }
        Room room = new Room(nextId++, host, mode);
        rooms.put(room.id, room);
        roomByMember.put(host, room);
        return Outcome.OK;
    }

    public Outcome invite(UUID host, UUID target) {
        Room room = roomByMember.get(host);
        if (room == null) {
            return Outcome.NO_ROOM;
        }
        if (!room.host.equals(host)) {
            return Outcome.NOT_HOST;
        }
        if (room.members.contains(target)) {
            return Outcome.IN_ROOM;
        }
        invites.computeIfAbsent(target, k -> new HashMap<>()).put(room.id, now + INVITE_TICKS);
        return Outcome.OK;
    }

    /** Whether the player has a live invite to the room that {@code member} is in. */
    public boolean invited(UUID player, UUID member) {
        Room room = roomByMember.get(member);
        return room != null && invites.getOrDefault(player, Map.of()).containsKey(room.id);
    }

    /** The members whose rooms invited the player: each room's host. */
    public List<UUID> inviters(UUID player) {
        return invites.getOrDefault(player, Map.of()).keySet().stream()
                .map(rooms::get).filter(r -> r != null).map(r -> r.host).toList();
    }

    /** Joins the room {@code member} is in, using an invite to it. */
    public Outcome join(UUID player, UUID member) {
        if (roomByMember.containsKey(player)) {
            return Outcome.IN_ROOM;
        }
        Room room = roomByMember.get(member);
        if (room == null) {
            return Outcome.NO_ROOM;
        }
        Map<Integer, Long> own = invites.get(player);
        if (own == null || !own.containsKey(room.id)) {
            return Outcome.NO_INVITE;
        }
        if (room.members.size() >= room.mode.maximum()) {
            return Outcome.FULL;
        }
        own.remove(room.id);
        room.members.add(player);
        roomByMember.put(player, room);
        return Outcome.OK;
    }

    /** Takes the player out of their room; empty when they were in none. */
    public Optional<Left> leave(UUID player) {
        Room room = roomByMember.remove(player);
        if (room == null) {
            return Optional.empty();
        }
        room.members.remove(player);
        room.picks.remove(player);
        if (room.members.isEmpty()) {
            room.stopCountdown();
            rooms.remove(room.id);
            invites.values().forEach(m -> m.remove(room.id));
            return Optional.of(new Left(room.id, true, Optional.empty()));
        }
        if (room.host.equals(player)) {
            room.host = room.members.getFirst();
            return Optional.of(new Left(room.id, false, Optional.of(room.host)));
        }
        return Optional.of(new Left(room.id, false, Optional.empty()));
    }

    public Outcome kick(UUID host, UUID target) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        if (target.equals(host) || !room.members.contains(target)) {
            return Outcome.NOT_MEMBER;
        }
        leave(target);
        return Outcome.OK;
    }

    public Outcome setMode(UUID host, ModeSpec mode) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        if (room.members.size() > mode.maximum()) {
            return Outcome.FULL;
        }
        room.mode = mode;
        room.picks.values().removeIf(team -> team >= mode.teams());
        if (room.arena.isPresent() && pool.get(room.arena.get()).filter(a -> a.fits(mode)).isEmpty()) {
            room.arena = Optional.empty();
        }
        return Outcome.OK;
    }

    /** Chooses the arena; empty means a random free one at start. */
    public Outcome setArena(UUID host, Optional<String> arena) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        if (arena.isPresent() && pool.get(arena.get()).filter(a -> a.fits(room.mode)).isEmpty()) {
            return Outcome.NO_ARENA;
        }
        room.arena = arena;
        return Outcome.OK;
    }

    public Outcome cycleRule(UUID host) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        room.rule = room.rule.next();
        return Outcome.OK;
    }

    /** The host switches the voice rule: per team, everyone together, nearby only. */
    public Outcome cycleVoice(UUID host) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        room.voice = room.voice.next();
        return Outcome.OK;
    }

    /** The player picks a team (empty: no team). Only under {@link RoomTeams.Rule#PICK}. */
    public Outcome pick(UUID player, OptionalInt team) {
        Room room = roomByMember.get(player);
        if (room == null) {
            return Outcome.NO_ROOM;
        }
        Outcome state = room.stateCheck();
        if (state != Outcome.OK) {
            return state;
        }
        if (room.rule != RoomTeams.Rule.PICK) {
            return Outcome.NOT_ALLOWED;
        }
        return room.assign(player, team);
    }

    /** The host moves a member to the next team: 0, 1, ..., no team, 0. Only under {@link RoomTeams.Rule#HOST}. */
    public Outcome move(UUID host, UUID target) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        if (room.rule != RoomTeams.Rule.HOST) {
            return Outcome.NOT_ALLOWED;
        }
        if (!room.members.contains(target)) {
            return Outcome.NOT_MEMBER;
        }
        Integer current = room.picks.get(target);
        int next = current == null ? 0 : current + 1;
        // Skip teams that are full; past the last team the member has no team.
        while (next < room.mode.teams() && room.teamSize(next) >= room.mode.maxPerTeam()) {
            next++;
        }
        return room.assign(target, next < room.mode.teams() ? OptionalInt.of(next) : OptionalInt.empty());
    }

    /** Checks the room can start, reserves its arena and starts the countdown. */
    public Outcome start(UUID host) {
        return start(host, OptionalInt.empty());
    }

    /**
     * As {@link #start(UUID)}; with a team, the host is alone in the room and plays on that team (the
     * game chose it, e.g. the colour against a chess bot) and the other teams stay empty.
     */
    public Outcome start(UUID host, OptionalInt soloTeam) {
        Outcome check = hostCheck(host);
        if (check != Outcome.OK) {
            return check;
        }
        Room room = roomByMember.get(host);
        Outcome ready = room.readiness();
        if (ready != Outcome.OK) {
            return ready;
        }
        if (soloTeam.isPresent() && (room.members.size() != 1 || soloTeam.getAsInt() < 0
                || soloTeam.getAsInt() >= room.mode.teams())) {
            return Outcome.NOT_ALLOWED;
        }
        Optional<ArenaDefinition> reserved = room.arena.isPresent()
                ? pool.reserve(room.arena.get(), room.mode)
                : pool.reserve(room.mode, rnd);
        if (reserved.isEmpty()) {
            return room.arena.isPresent() ? Outcome.BUSY : Outcome.NO_ARENA;
        }
        room.reserved = reserved.get();
        room.soloTeam = soloTeam;
        room.startedWith = room.members.size();
        room.ticksLeft = COUNTDOWN_SECONDS * TICKS_PER_SECOND;
        pending.add(new RoomEvent.CountdownTick(room.id, List.copyOf(room.members), COUNTDOWN_SECONDS));
        return Outcome.OK;
    }

    public Outcome cancel(UUID host) {
        Room room = roomByMember.get(host);
        if (room == null) {
            return Outcome.NO_ROOM;
        }
        if (!room.host.equals(host)) {
            return Outcome.NOT_HOST;
        }
        if (room.reserved == null) {
            return Outcome.OK;
        }
        room.stopCountdown();
        pending.add(new RoomEvent.CountdownCancelled(room.id, List.copyOf(room.members)));
        return Outcome.OK;
    }

    /** The room's match is over; the host may start again. */
    public void matchEnded(int roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.playing = false;
        }
    }

    public List<RoomEvent> tick() {
        now++;
        List<RoomEvent> events = new ArrayList<>(pending);
        pending.clear();
        for (Map<Integer, Long> own : invites.values()) {
            own.values().removeIf(expires -> expires <= now);
        }
        invites.values().removeIf(Map::isEmpty);
        for (Room room : rooms.values()) {
            if (room.reserved == null) {
                continue;
            }
            // Leavers that make the room short-handed stop the countdown, so nobody gets a solo match unwarned.
            boolean nowShort = shortHanded(room.members.size(), room.mode) && !shortHanded(room.startedWith, room.mode);
            boolean soloBroken = room.soloTeam.isPresent() && room.members.size() != 1;
            if (room.readiness() != Outcome.OK || nowShort || soloBroken) {
                room.stopCountdown();
                events.add(new RoomEvent.CountdownCancelled(room.id, List.copyOf(room.members)));
                continue;
            }
            room.ticksLeft--;
            if (room.ticksLeft == 0) {
                List<List<UUID>> teams = room.soloTeam.isPresent()
                        ? soloTeams(room.host, room.soloTeam.getAsInt(), room.mode.teams())
                        : RoomTeams.build(List.copyOf(room.members), room.picks, room.rule, room.mode.teams(), rnd);
                room.soloTeam = OptionalInt.empty();
                events.add(new RoomEvent.Start(room.id, room.mode, room.reserved, teams, room.voice));
                room.reserved = null;
                room.playing = true;
            } else if (room.ticksLeft % TICKS_PER_SECOND == 0) {
                events.add(new RoomEvent.CountdownTick(room.id, List.copyOf(room.members),
                        room.ticksLeft / TICKS_PER_SECOND));
            }
        }
        return events;
    }

    public Optional<RoomView> roomOf(UUID player) {
        return Optional.ofNullable(roomByMember.get(player)).map(Room::view);
    }

    public Optional<RoomView> room(int id) {
        return Optional.ofNullable(rooms.get(id)).map(Room::view);
    }

    /** Whether a room with this many members plays without a full set of opponents. */
    private static List<List<UUID>> soloTeams(UUID host, int team, int teams) {
        List<List<UUID>> out = new ArrayList<>();
        for (int t = 0; t < teams; t++) {
            out.add(t == team ? List.of(host) : List.of());
        }
        return out;
    }

    public static boolean shortHanded(int members, ModeSpec mode) {
        return members < Math.max(2, mode.teams());
    }

    public boolean inRoom(UUID player) {
        return roomByMember.containsKey(player);
    }

    /** The host's room for a settings change, or why not. */
    private Outcome hostCheck(UUID host) {
        Room room = roomByMember.get(host);
        if (room == null) {
            return Outcome.NO_ROOM;
        }
        if (!room.host.equals(host)) {
            return Outcome.NOT_HOST;
        }
        return room.stateCheck();
    }

    private final class Room {
        final int id;
        UUID host;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Integer> picks = new HashMap<>();
        ModeSpec mode;
        Optional<String> arena = Optional.empty();
        RoomTeams.Rule rule = RoomTeams.Rule.PICK;
        VoiceRule voice = defaultVoice;
        ArenaDefinition reserved;
        int ticksLeft;
        /** Members when the countdown began. */
        int startedWith;
        boolean playing;
        /** Set for a countdown the host started alone on a team the game chose. */
        OptionalInt soloTeam = OptionalInt.empty();

        Room(int id, UUID host, ModeSpec mode) {
            this.id = id;
            this.host = host;
            this.mode = mode;
            members.add(host);
        }

        Outcome stateCheck() {
            if (playing) {
                return Outcome.PLAYING;
            }
            return reserved != null ? Outcome.COUNTING : Outcome.OK;
        }

        /** Whether the members, as picked, make a match (playing/counting aside). */
        Outcome readiness() {
            if (members.size() < MIN_MEMBERS) {
                return Outcome.TOO_FEW;
            }
            // Too few members to fill every team: some stay empty (later filled by bots).
            if (rule == RoomTeams.Rule.RANDOM || members.size() < mode.teams()) {
                return Outcome.OK;
            }
            long unpicked = members.stream().filter(m -> !picks.containsKey(m)).count();
            long emptyTeams = 0;
            for (int team = 0; team < mode.teams(); team++) {
                if (teamSize(team) == 0) {
                    emptyTeams++;
                }
            }
            // Members without a team fill the smallest (first the empty) teams.
            return unpicked >= emptyTeams ? Outcome.OK : Outcome.EMPTY_TEAM;
        }

        int teamSize(int team) {
            return (int) picks.values().stream().filter(t -> t == team).count();
        }

        Outcome assign(UUID member, OptionalInt team) {
            if (team.isEmpty()) {
                picks.remove(member);
                return Outcome.OK;
            }
            int t = team.getAsInt();
            if (t < 0 || t >= mode.teams()) {
                return Outcome.NOT_ALLOWED;
            }
            if (!Integer.valueOf(t).equals(picks.get(member)) && teamSize(t) >= mode.maxPerTeam()) {
                return Outcome.TEAM_FULL;
            }
            picks.put(member, t);
            return Outcome.OK;
        }

        void stopCountdown() {
            if (reserved != null) {
                pool.release(reserved.name());
                reserved = null;
            }
            soloTeam = OptionalInt.empty();
        }

        RoomView view() {
            OptionalInt seconds = reserved == null ? OptionalInt.empty()
                    : OptionalInt.of((ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
            return new RoomView(id, host, List.copyOf(members), mode, arena, rule, picks, seconds, playing, voice);
        }
    }
}
