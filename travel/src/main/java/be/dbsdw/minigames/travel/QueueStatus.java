package be.dbsdw.minigames.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One public queue of the minigame server with players waiting in it, as the survival server reads it.
 * The minigame server serves the list as text, one queue per line, fields separated by tabs.
 *
 * @param key    queue key, e.g. {@code paintball:tdm:2v2}
 * @param name   MiniMessage name, e.g. {@code Paintball TDM 2v2}
 * @param queued players waiting now
 * @param needed players needed for the countdown to start
 * @param max    players a match of this queue takes
 * @param game   id of the queue's game, e.g. {@code paintball}
 * @param level  the level in that game a player needs to join (1 or less: none)
 */
public record QueueStatus(String key, String name, int queued, int needed, int max, String game, int level) {
    public QueueStatus {
        if (key.isEmpty() || key.contains("\t") || key.contains("\n")) {
            throw new IllegalArgumentException("bad queue key: " + key);
        }
        name = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        game = game.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** A queue anyone may join; the game is the first part of the key. */
    public QueueStatus(String key, String name, int queued, int needed, int max) {
        this(key, name, queued, needed, max, key.contains(":") ? key.substring(0, key.indexOf(':')) : key, 0);
    }

    /** Whether someone else could still join this queue's next match. */
    public boolean open() {
        return queued > 0 && queued < max;
    }

    /** Whether joining takes a level. */
    public boolean leveled() {
        return level > 1;
    }

    /** Whether a player on {@code playerLevel} in this queue's game may join it. */
    public boolean allows(int playerLevel) {
        return !leveled() || playerLevel >= level;
    }

    /** One line per queue: key, queued, needed, max, level, game, name. */
    public static String encode(List<QueueStatus> queues) {
        StringBuilder out = new StringBuilder();
        for (QueueStatus q : queues) {
            out.append(q.key).append('\t').append(q.queued).append('\t').append(q.needed).append('\t')
                    .append(q.max).append('\t').append(q.level).append('\t').append(q.game).append('\t')
                    .append(q.name).append('\n');
        }
        return out.toString();
    }

    /**
     * The queues in {@code text}; lines that do not parse are skipped. Also reads the older five-field
     * lines (no level or game) of a minigame server that was not updated yet.
     */
    public static List<QueueStatus> decode(String text) {
        List<QueueStatus> queues = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            try {
                if (f.length == 7) {
                    queues.add(new QueueStatus(f[0], f[6], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                            Integer.parseInt(f[3]), f[5], Integer.parseInt(f[4])));
                } else if (f.length == 5) {
                    queues.add(new QueueStatus(f[0], f[4], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                            Integer.parseInt(f[3])));
                }
            } catch (IllegalArgumentException e) {
                // skip the line
            }
        }
        return queues;
    }

    /** Players' levels in one game, one {@code uuid<TAB>level} line each (the minigame server's /levels). */
    public static String encodeLevels(Map<UUID, Integer> levels) {
        StringBuilder out = new StringBuilder();
        levels.forEach((id, level) -> out.append(id).append('\t').append(level).append('\n'));
        return out.toString();
    }

    /** The levels in {@code text}; lines that do not parse are skipped. */
    public static Map<UUID, Integer> decodeLevels(String text) {
        Map<UUID, Integer> levels = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 2) {
                continue;
            }
            try {
                levels.put(UUID.fromString(f[0]), Integer.parseInt(f[1]));
            } catch (IllegalArgumentException e) {
                // skip the line
            }
        }
        return levels;
    }
}
