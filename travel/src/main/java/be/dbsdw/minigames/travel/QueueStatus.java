package be.dbsdw.minigames.travel;

import java.util.ArrayList;
import java.util.List;

/**
 * One public queue of the minigame server with players waiting in it, as the survival server reads it.
 * The minigame server serves the list as text, one queue per line, fields separated by tabs.
 *
 * @param key    queue key, e.g. {@code paintball:tdm:2v2}
 * @param name   MiniMessage name, e.g. {@code Paintball TDM 2v2}
 * @param queued players waiting now
 * @param needed players needed for the countdown to start
 * @param max    players a match of this queue takes
 */
public record QueueStatus(String key, String name, int queued, int needed, int max) {
    public QueueStatus {
        if (key.isEmpty() || key.contains("\t") || key.contains("\n")) {
            throw new IllegalArgumentException("bad queue key: " + key);
        }
        name = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Whether someone else could still join this queue's next match. */
    public boolean open() {
        return queued > 0 && queued < max;
    }

    public static String encode(List<QueueStatus> queues) {
        StringBuilder out = new StringBuilder();
        for (QueueStatus q : queues) {
            out.append(q.key).append('\t').append(q.queued).append('\t').append(q.needed).append('\t')
                    .append(q.max).append('\t').append(q.name).append('\n');
        }
        return out.toString();
    }

    /** The queues in {@code text}; lines that do not parse are skipped. */
    public static List<QueueStatus> decode(String text) {
        List<QueueStatus> queues = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", 5);
            if (f.length != 5) {
                continue;
            }
            try {
                queues.add(new QueueStatus(f[0], f[4], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3])));
            } catch (IllegalArgumentException e) {
                // skip the line
            }
        }
        return queues;
    }
}
