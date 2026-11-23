package be.dbsdw.minigames.paintball.bot;

import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Paths over the arena floor for bots: the walkable cells (see
 * {@link be.dbsdw.minigames.paintball.rules.WalkableArea}) with steps to the eight neighbouring
 * columns, up one block or down three, and A* between two cells. Diagonal steps only on flat ground
 * with both side cells free, so a bot never cuts a wall corner.
 */
public final class BotNav {
    private static final int[][] STRAIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] STEPS = {0, 1, -1, -2, -3};
    private static final double SQRT2 = Math.sqrt(2);

    private final Set<Cell> cells;
    private final List<Cell> list;
    /** Column (x, z) to the floor heights in it. */
    private final Map<Long, List<Integer>> columns = new HashMap<>();

    public BotNav(Collection<Cell> cells) {
        this.cells = new HashSet<>(cells);
        this.list = List.copyOf(this.cells);
        for (Cell c : this.cells) {
            columns.computeIfAbsent(key(c.x(), c.z()), k -> new ArrayList<>()).add(c.y());
        }
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public boolean contains(Cell c) {
        return cells.contains(c);
    }

    /** The walkable cell closest to a position, searching columns up to 4 blocks away. */
    public Optional<Cell> nearest(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        Cell best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    List<Integer> ys = columns.get(key(bx + dx, bz + dz));
                    if (ys == null) {
                        continue;
                    }
                    for (int cy : ys) {
                        Cell c = new Cell(bx + dx, cy, bz + dz);
                        double d = c.distanceSquared(x, y, z) + 4 * Math.abs(cy - y); // prefer the same floor
                        if (d < bestD) {
                            bestD = d;
                            best = c;
                        }
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    /** A random walkable cell within {@code radius} columns of {@code around}, or empty. */
    public Optional<Cell> randomNear(Cell around, int radius, RandomGenerator rnd) {
        for (int tries = 0; tries < 12; tries++) {
            int x = around.x() + rnd.nextInt(-radius, radius + 1);
            int z = around.z() + rnd.nextInt(-radius, radius + 1);
            List<Integer> ys = columns.get(key(x, z));
            if (ys != null) {
                return Optional.of(new Cell(x, ys.get(rnd.nextInt(ys.size())), z));
            }
        }
        return Optional.empty();
    }

    /** Any walkable cell. */
    public Optional<Cell> random(RandomGenerator rnd) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(rnd.nextInt(list.size())));
    }

    /** The cells one step away. */
    public List<Cell> neighbours(Cell c) {
        List<Cell> out = new ArrayList<>(8);
        for (int[] d : STRAIGHT) {
            for (int dy : STEPS) {
                Cell n = new Cell(c.x() + d[0], c.y() + dy, c.z() + d[1]);
                if (cells.contains(n)) {
                    out.add(n);
                    break;
                }
            }
        }
        for (int[] d : DIAGONAL) {
            Cell n = new Cell(c.x() + d[0], c.y(), c.z() + d[1]);
            if (cells.contains(n) && cells.contains(new Cell(c.x() + d[0], c.y(), c.z()))
                    && cells.contains(new Cell(c.x(), c.y(), c.z() + d[1]))) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * The cells from {@code from} (excluded) to {@code to} (included), or the path to the closest
     * cell reached when {@code to} is unreachable or the search gives up after {@code maxExpanded}
     * cells. Empty when already there.
     */
    public List<Cell> path(Cell from, Cell to, int maxExpanded) {
        if (from.equals(to) || !cells.contains(from)) {
            return List.of();
        }
        Map<Cell, Cell> came = new HashMap<>();
        Map<Cell, Double> cost = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        cost.put(from, 0.0);
        open.add(new Node(from, guess(from, to)));
        Cell closest = from;
        double closestGuess = guess(from, to);
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxExpanded) {
            Node node = open.poll();
            Cell c = node.cell;
            double g = cost.get(c);
            if (node.f > g + guess(c, to) + 1e-9) {
                continue; // stale entry
            }
            if (c.equals(to)) {
                closest = c;
                break;
            }
            expanded++;
            double h = guess(c, to);
            if (h < closestGuess) {
                closestGuess = h;
                closest = c;
            }
            for (Cell n : neighbours(c)) {
                double step = (n.x() != c.x() && n.z() != c.z() ? SQRT2 : 1) + (n.y() > c.y() ? 0.5 : 0);
                double ng = g + step;
                Double old = cost.get(n);
                if (old == null || ng < old - 1e-9) {
                    cost.put(n, ng);
                    came.put(n, c);
                    open.add(new Node(n, ng + guess(n, to)));
                }
            }
        }
        List<Cell> out = new ArrayList<>();
        for (Cell c = closest; c != null && !c.equals(from); c = came.get(c)) {
            out.add(c);
        }
        Collections.reverse(out);
        return out;
    }

    private static double guess(Cell a, Cell b) {
        int dx = Math.abs(a.x() - b.x());
        int dz = Math.abs(a.z() - b.z());
        return Math.max(dx, dz) + (SQRT2 - 1) * Math.min(dx, dz) + 0.5 * Math.abs(a.y() - b.y());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record Node(Cell cell, double f) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }
}
