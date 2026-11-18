package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** The arena of a running match, as Bukkit locations. */
public interface ArenaView {
    String name();

    World world();

    List<Location> spawns(int team);

    Optional<Location> flag(int team);

    List<Location> powerups();

    BoundingBox bounds();
}
