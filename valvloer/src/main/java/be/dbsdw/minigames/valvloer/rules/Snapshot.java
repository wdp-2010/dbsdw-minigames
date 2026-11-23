package be.dbsdw.minigames.valvloer.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The floor blocks of an arena as they were at the start of a match: world name plus position and
 * block data of each block. Written to disk so a crash mid-match can still put the floor back.
 *
 * @param blocks block key → block data string (e.g. {@code minecraft:oak_planks})
 */
public record Snapshot(String world, Map<Long, String> blocks) {
    public Snapshot {
        blocks = new LinkedHashMap<>(blocks);
    }

    public int lowestY() {
        return blocks.keySet().stream().mapToInt(BlockKey::y).min().orElse(Integer.MIN_VALUE);
    }

    public List<String> toLines() {
        List<String> lines = new ArrayList<>(blocks.size() + 1);
        lines.add("world " + world);
        blocks.forEach((k, data) -> lines.add(BlockKey.x(k) + " " + BlockKey.y(k) + " " + BlockKey.z(k) + " " + data));
        return lines;
    }

    /** @throws IllegalArgumentException on a malformed file */
    public static Snapshot parse(List<String> lines) {
        if (lines.isEmpty() || !lines.getFirst().startsWith("world ")) {
            throw new IllegalArgumentException("first line must be 'world <name>'");
        }
        Map<Long, String> blocks = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" ", 4);
            if (p.length != 4) {
                throw new IllegalArgumentException("line " + (i + 1) + ": need x y z data");
            }
            try {
                blocks.put(BlockKey.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), p[3]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Snapshot(lines.getFirst().substring(6).strip(), blocks);
    }
}
