package be.dbsdw.minigames.verstoppertje.rules;

import java.util.List;
import java.util.Set;

/**
 * Which blocks players may place and turn into. By material name, so it is testable without a server.
 * Left out: blocks that run machines or commands, unbreakable or invisible blocks, blocks that fall,
 * and blocks drawn by a block-entity renderer (chests, signs, heads...), which a block display shows
 * as nothing.
 */
public final class BlockRules {
    private static final Set<String> DENIED = Set.of("TNT", "REDSTONE_BLOCK", "OBSERVER", "PISTON", "STICKY_PISTON",
            "DISPENSER", "DROPPER", "CRAFTER", "HOPPER", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "BARRIER", "LIGHT",
            "BEDROCK", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "SCULK_SHRIEKER",
            "SCULK_CATALYST", "BELL", "DECORATED_POT", "CONDUIT", "BEACON", "RESPAWN_ANCHOR", "SLIME_BLOCK",
            "HONEY_BLOCK", "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "TARGET", "DAYLIGHT_DETECTOR");
    private static final List<String> DENIED_PARTS = List.of("COMMAND_BLOCK", "CHEST", "SHULKER_BOX", "_BED", "SIGN",
            "BANNER", "_HEAD", "SKULL", "COPPER_GOLEM", "PRESSURE_PLATE", "_BUTTON", "LEVER", "REDSTONE");

    private BlockRules() {
    }

    /** Whether a player may place this block and become it. */
    public static boolean allowed(String name, boolean block, boolean solid, boolean gravity) {
        if (!block || !solid || gravity || name.endsWith("AIR") || DENIED.contains(name)) {
            return false;
        }
        for (String part : DENIED_PARTS) {
            if (name.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /** How many more a player may place or break, never below zero. */
    public static int left(int limit, int used) {
        return Math.max(0, limit - used);
    }
}
