package be.dbsdw.minigames.core.arena;

import be.dbsdw.minigames.core.api.ArenaView;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** An {@link ArenaDefinition} placed in its loaded world. */
public final class BukkitArenaView implements ArenaView {
    private final ArenaDefinition arena;
    private final World world;

    public BukkitArenaView(ArenaDefinition arena, World world) {
        this.arena = arena;
        this.world = world;
    }

    @Override
    public String name() {
        return arena.name();
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public List<Location> spawns(int team) {
        return arena.spawns().getOrDefault(team, List.of()).stream().map(this::location).toList();
    }

    @Override
    public Optional<Location> flag(int team) {
        return Optional.ofNullable(arena.flags().get(team)).map(this::location);
    }

    @Override
    public List<Location> powerups() {
        return arena.powerups().stream().map(this::location).toList();
    }

    @Override
    public BoundingBox bounds() {
        Pos a = arena.corner1();
        Pos b = arena.corner2();
        return new BoundingBox(
                Math.min(a.blockX(), b.blockX()), Math.min(a.blockY(), b.blockY()), Math.min(a.blockZ(), b.blockZ()),
                Math.max(a.blockX(), b.blockX()) + 1, Math.max(a.blockY(), b.blockY()) + 1,
                Math.max(a.blockZ(), b.blockZ()) + 1);
    }

    private Location location(Pos p) {
        return new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }
}
