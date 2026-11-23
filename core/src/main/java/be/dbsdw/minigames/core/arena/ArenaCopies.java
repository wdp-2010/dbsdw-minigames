package be.dbsdw.minigames.core.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the copies of a template arena go: in a row east (+x) of the template, in the same world, each
 * one {@link #step} blocks further. The step is the spacing (default {@link #DEFAULT_SPACING}, far
 * enough that players on one copy do not see the next one) but at least the arena's width plus
 * {@link #GAP}, rounded up to a multiple of 16 so a copy sits at the same place inside its chunks as
 * the template.
 */
public final class ArenaCopies {
    /** Least free blocks between the template and its first copy, and between copies. */
    public static final int GAP = 32;
    /** Blocks from one copy to the next unless configured otherwise (view distance 8 chunks = 128). */
    public static final int DEFAULT_SPACING = 512;
    private static final String SEPARATOR = "#";

    private ArenaCopies() {}

    /** Blocks between the template and copy 1, and between neighbouring copies. */
    public static int step(ArenaDefinition template, int spacing) {
        int width = Math.abs(template.corner2().blockX() - template.corner1().blockX()) + 1;
        return (Math.max(width + GAP, spacing) + 15) / 16 * 16;
    }

    /** The template's copies, named {@code <template>#1} …; empty for a plain arena or one without bounds. */
    public static List<ArenaDefinition> of(ArenaDefinition template, int spacing) {
        List<ArenaDefinition> out = new ArrayList<>();
        if (!template.isTemplate() || !template.hasBounds()) {
            return out;
        }
        int step = step(template, spacing);
        for (int k = 1; k <= template.copies(); k++) {
            out.add(template.shifted(template.name() + SEPARATOR + k, k * step, 0));
        }
        return out;
    }

    /** The template name of a copy name ({@code woud#2} → {@code woud}), or the name itself. */
    public static String templateName(String name) {
        int i = name.indexOf(SEPARATOR);
        return i < 0 ? name : name.substring(0, i);
    }

    public static boolean isCopyName(String name) {
        return name.contains(SEPARATOR);
    }

    /** The x offset of a copy from its template. */
    public static int offsetX(ArenaDefinition template, ArenaDefinition copy) {
        return copy.corner1().blockX() - template.corner1().blockX();
    }
}
