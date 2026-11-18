package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Capture-the-flag state of every team's flag: home, carried by a player, or dropped somewhere. */
public final class Flags {
    public enum State { HOME, CARRIED, DROPPED }

    private final State[] state;
    private final UUID[] carrier;
    private final double[][] dropPos;
    private final long[] droppedAt;
    private final long returnTicks;

    public Flags(int teams, long returnTicks) {
        this.state = new State[teams];
        this.carrier = new UUID[teams];
        this.dropPos = new double[teams][];
        this.droppedAt = new long[teams];
        this.returnTicks = returnTicks;
        java.util.Arrays.fill(state, State.HOME);
    }

    public int teams() {
        return state.length;
    }

    public State state(int flag) {
        return state[flag];
    }

    public Optional<UUID> carrier(int flag) {
        return Optional.ofNullable(carrier[flag]);
    }

    /** Where a dropped flag lies (x, y, z). */
    public double[] dropPos(int flag) {
        return dropPos[flag];
    }

    public long droppedAt(int flag) {
        return droppedAt[flag];
    }

    /** The flag this player carries, if any. */
    public OptionalInt carriedBy(UUID player) {
        for (int f = 0; f < state.length; f++) {
            if (state[f] == State.CARRIED && player.equals(carrier[f])) {
                return OptionalInt.of(f);
            }
        }
        return OptionalInt.empty();
    }

    /** An enemy picks up a flag that is home or dropped; one flag per player. */
    public boolean pickup(int flag, UUID player, int playerTeam) {
        if (flag == playerTeam || state[flag] == State.CARRIED || carriedBy(player).isPresent()) {
            return false;
        }
        state[flag] = State.CARRIED;
        carrier[flag] = player;
        dropPos[flag] = null;
        return true;
    }

    /** A player of the flag's own team touches it while dropped: it goes home. */
    public boolean returnHome(int flag, int playerTeam) {
        if (flag != playerTeam || state[flag] != State.DROPPED) {
            return false;
        }
        home(flag);
        return true;
    }

    /**
     * The player is at their own flag's home: if they carry an enemy flag and their own flag is
     * home, that is a capture. Returns the captured flag (now home again).
     */
    public OptionalInt capture(UUID player, int playerTeam) {
        OptionalInt carried = carriedBy(player);
        if (carried.isEmpty() || state[playerTeam] != State.HOME) {
            return OptionalInt.empty();
        }
        home(carried.getAsInt());
        return carried;
    }

    /** The carrier is out or left: the flag drops where they were. */
    public OptionalInt drop(UUID player, double x, double y, double z, long now) {
        OptionalInt carried = carriedBy(player);
        if (carried.isPresent()) {
            int f = carried.getAsInt();
            state[f] = State.DROPPED;
            carrier[f] = null;
            dropPos[f] = new double[] {x, y, z};
            droppedAt[f] = now;
        }
        return carried;
    }

    /** Dropped flags that lay untouched long enough go home; returns them. */
    public List<Integer> autoReturns(long now) {
        List<Integer> back = new ArrayList<>();
        for (int f = 0; f < state.length; f++) {
            if (state[f] == State.DROPPED && now - droppedAt[f] >= returnTicks) {
                home(f);
                back.add(f);
            }
        }
        return back;
    }

    /** Back home at once (e.g. dropped outside the arena). */
    public void sendHome(int flag) {
        home(flag);
    }

    private void home(int flag) {
        state[flag] = State.HOME;
        carrier[flag] = null;
        dropPos[flag] = null;
    }
}
