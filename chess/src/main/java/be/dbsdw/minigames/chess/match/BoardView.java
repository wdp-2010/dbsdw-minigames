package be.dbsdw.minigames.chess.match;

import be.dbsdw.minigames.chess.PackAssets;
import be.dbsdw.minigames.chess.rules.BoardGeometry;
import be.dbsdw.minigames.chess.rules.Move;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.chess.rules.Position;
import be.dbsdw.minigames.core.pack.PackItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * The pieces and highlights on one board, as display entities. Pieces are pack models standing on
 * their squares; a move glides the piece over (teleport interpolation), a captured piece spins away.
 * Move markers and the hover square are shown to the player to move only; the last move and a king
 * in check are shown to everyone.
 */
final class BoardView {
    /** Lift of a selected piece, in blocks. */
    private static final float LIFT = 0.7f;
    private static final int CAPTURE_TICKS = 10;
    private static final float PLATE = 0.04f;

    private final Plugin plugin;
    private final World world;
    private final BoardGeometry geo;
    private final float scale;
    private final int moveTicks;
    private final ItemDisplay[] pieces = new ItemDisplay[64];
    private final List<Entity> markers = new ArrayList<>();
    private final List<Dying> dying = new ArrayList<>();
    private final BlockDisplay[] lastMove = new BlockDisplay[2];
    private BlockDisplay check;
    private BlockDisplay hover;
    private int hoverSquare = -1;
    private Player hoverViewer;
    private int lifted = -1;
    private long ticks;

    private record Dying(Entity entity, long removeAt) {}

    BoardView(Plugin plugin, World world, BoardGeometry geo, double pieceScale, int moveTicks) {
        this.plugin = plugin;
        this.world = world;
        this.geo = geo;
        this.scale = (float) (geo.squareSize() * pieceScale);
        this.moveTicks = Math.max(1, Math.min(59, moveTicks));
    }

    /** Puts every piece of the position on the board (replacing what was there). */
    void setup(Position position) {
        for (int sq = 0; sq < 64; sq++) {
            if (pieces[sq] != null) {
                pieces[sq].remove();
                pieces[sq] = null;
            }
            int p = position.at(sq);
            if (p != 0) {
                pieces[sq] = spawnPiece(sq, p);
            }
        }
    }

    private Location centre(int sq, double up) {
        return new Location(world, geo.centreX(sq), geo.topY() + up, geo.centreZ(sq));
    }

    private float yaw(int piece) {
        float forward = geo.whiteForwardYaw();
        return Piece.isWhite(piece) ? forward : forward + 180f;
    }

    private ItemDisplay spawnPiece(int sq, int piece) {
        Location at = centre(sq, 0);
        at.setYaw(yaw(piece));
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(PackItems.item(Material.PAPER, PackAssets.piece(piece), null));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(moveTicks);
            d.setInterpolationDuration(4);
            d.setTransformation(pose(0));
            d.setViewRange(2f);
        });
    }

    /** The model's base on the board top; NONE shows an item model centred on the entity. */
    private Transformation pose(float lift) {
        return new Transformation(new Vector3f(0, scale / 2 + lift, 0), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    /** Lifts and outlines the piece and shows where it can go, to the viewer only. */
    void select(int sq, List<Move> moves, Position position, Player viewer) {
        deselect();
        ItemDisplay piece = pieces[sq];
        if (piece != null) {
            piece.setInterpolationDelay(0);
            piece.setTransformation(pose(LIFT));
            piece.setGlowColorOverride(Color.fromRGB(0xFFD23F));
            piece.setGlowing(true);
            lifted = sq;
        }
        List<Integer> done = new ArrayList<>();
        for (Move m : moves) {
            if (done.contains(m.to())) {
                continue;
            }
            done.add(m.to());
            boolean capture = position.effect(m).capturedSquare() >= 0;
            markers.add(plate(m.to(), capture ? Material.RED_STAINED_GLASS : Material.LIME_STAINED_GLASS,
                    capture ? 0.9f : 0.4f, 3 * PLATE, viewer));
        }
    }

    void deselect() {
        if (lifted >= 0 && pieces[lifted] != null) {
            pieces[lifted].setInterpolationDelay(0);
            pieces[lifted].setTransformation(pose(0));
            pieces[lifted].setGlowing(false);
        }
        lifted = -1;
        markers.forEach(Entity::remove);
        markers.clear();
    }

    /** The square the viewer is looking at, outlined for them; -1 hides it. */
    void hover(int sq, Player viewer) {
        if (sq == hoverSquare && viewer == hoverViewer && hover != null && hover.isValid()) {
            return;
        }
        if (hover != null) {
            hover.remove();
            hover = null;
        }
        hoverSquare = sq;
        hoverViewer = viewer;
        if (sq >= 0 && viewer != null) {
            hover = plate(sq, Material.WHITE_STAINED_GLASS, 0.96f, 2 * PLATE, viewer);
        }
    }

    /** Plays a move that was just made: piece glides, a captured piece spins away, the rook castles. */
    void move(Move m, Position.MoveEffect effect, Position after) {
        deselect();
        hover(-1, null);
        if (effect.capturedSquare() >= 0 && pieces[effect.capturedSquare()] != null) {
            capture(effect.capturedSquare());
        }
        slide(m.from(), m.to());
        if (effect.rookFrom() >= 0) {
            slide(effect.rookFrom(), effect.rookTo());
        }
        if (m.promotion() != 0 && pieces[m.to()] != null) {
            pieces[m.to()].setItemStack(PackItems.item(Material.PAPER, PackAssets.piece(after.at(m.to())), null));
        }
        lastMove(m);
        showCheck(after.inCheck() ? after.king(after.whiteToMove()) : -1);
    }

    private void slide(int from, int to) {
        ItemDisplay piece = pieces[from];
        pieces[from] = null;
        if (piece == null) {
            return;
        }
        Location target = centre(to, 0);
        target.setYaw(piece.getYaw());
        piece.teleport(target);
        pieces[to] = piece;
    }

    private void capture(int sq) {
        ItemDisplay piece = pieces[sq];
        pieces[sq] = null;
        piece.setInterpolationDuration(CAPTURE_TICKS);
        piece.setInterpolationDelay(0);
        piece.setTransformation(new Transformation(new Vector3f(0, scale, 0), new AxisAngle4f((float) Math.PI, 0, 1, 0),
                new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
        world.spawnParticle(Particle.POOF, centre(sq, scale / 2), 20, scale / 4, scale / 4, scale / 4, 0.02);
        dying.add(new Dying(piece, ticks + CAPTURE_TICKS + 2));
    }

    private void lastMove(Move m) {
        for (int i = 0; i < 2; i++) {
            if (lastMove[i] != null) {
                lastMove[i].remove();
            }
            lastMove[i] = plate(i == 0 ? m.from() : m.to(), Material.YELLOW_STAINED_GLASS, 0.96f, PLATE, null);
        }
    }

    /** Marks the king in check (square), or clears it with -1. */
    void showCheck(int kingSquare) {
        if (check != null) {
            check.remove();
            check = null;
        }
        if (kingSquare >= 0) {
            check = plate(kingSquare, Material.RED_STAINED_GLASS, 0.96f, 1.5f * PLATE, null);
        }
    }

    /** Restores the last-move and check marks after the board is rebuilt (a resumed game). */
    void marks(Move last, Position position) {
        if (last != null) {
            lastMove(last);
        }
        showCheck(position.inCheck() ? position.king(position.whiteToMove()) : -1);
    }

    /**
     * A flat glass square on the board, {@code width} of a square wide; seen by {@code viewer} only, or
     * by everyone for null.
     */
    private BlockDisplay plate(int sq, Material glass, float width, float up, Player viewer) {
        float w = (float) (geo.squareSize() * width);
        BlockDisplay plate = world.spawn(centre(sq, 0), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(glass.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(-w / 2, up, -w / 2), new AxisAngle4f(),
                    new Vector3f(w, PLATE, w), new AxisAngle4f()));
            if (viewer != null) {
                d.setVisibleByDefault(false);
            }
        });
        if (viewer != null) {
            viewer.showEntity(plugin, plate);
        }
        return plate;
    }

    void tick() {
        ticks++;
        for (Iterator<Dying> it = dying.iterator(); it.hasNext(); ) {
            Dying d = it.next();
            if (ticks >= d.removeAt()) {
                d.entity().remove();
                it.remove();
            }
        }
    }

    /** Removes every entity of this board. */
    void remove() {
        deselect();
        hover(-1, null);
        for (int sq = 0; sq < 64; sq++) {
            if (pieces[sq] != null) {
                pieces[sq].remove();
                pieces[sq] = null;
            }
        }
        dying.forEach(d -> d.entity().remove());
        dying.clear();
        for (int i = 0; i < 2; i++) {
            if (lastMove[i] != null) {
                lastMove[i].remove();
                lastMove[i] = null;
            }
        }
        showCheck(-1);
    }
}
