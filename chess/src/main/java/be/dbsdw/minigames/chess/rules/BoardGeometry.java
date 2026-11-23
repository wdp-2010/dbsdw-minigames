package be.dbsdw.minigames.chess.rules;

/**
 * Where the 64 squares lie in the world. The arena marks the centre of a1 and of h8 on the board's
 * top surface; white's spawn tells which axis runs from white to black. The board lies flat (one y).
 *
 * <p>{@link #square} turns a look ray into the square it hits, which is how players pick pieces from
 * their stand above the board.
 */
public final class BoardGeometry {
    private final double a1x;
    private final double a1z;
    private final double topY;
    private final double size;
    /** World step per file (a→h) and per rank (1→8), each a unit vector along x or z. */
    private final int fileX;
    private final int fileZ;
    private final int rankX;
    private final int rankZ;
    private final boolean mirrored;

    private BoardGeometry(double a1x, double a1z, double topY, double size, int fileX, int fileZ, int rankX, int rankZ,
                          boolean mirrored) {
        this.a1x = a1x;
        this.a1z = a1z;
        this.topY = topY;
        this.size = size;
        this.fileX = fileX;
        this.fileZ = fileZ;
        this.rankX = rankX;
        this.rankZ = rankZ;
        this.mirrored = mirrored;
    }

    /**
     * @throws IllegalArgumentException if a1 and h8 are not opposite corners of a square board, or
     *     white's spawn is not on the rank-1 side
     */
    public static BoardGeometry of(double a1x, double a1z, double h8x, double h8z, double topY,
                                   double whiteX, double whiteZ) {
        double dx = h8x - a1x;
        double dz = h8z - a1z;
        if (Math.abs(dx) < 0.5 || Math.abs(Math.abs(dx) - Math.abs(dz)) > 0.25) {
            throw new IllegalArgumentException("a1 and h8 must be opposite corners of a square board");
        }
        double size = Math.abs(dx) / 7;
        double cx = (a1x + h8x) / 2;
        double cz = (a1z + h8z) / 2;
        // White stands off the board along the rank axis: the axis where it is farthest out.
        boolean ranksAlongX = Math.abs(whiteX - cx) - Math.abs(dx) / 2 > Math.abs(whiteZ - cz) - Math.abs(dz) / 2;
        int sx = (int) Math.signum(dx);
        int sz = (int) Math.signum(dz);
        int rankX = ranksAlongX ? sx : 0;
        int rankZ = ranksAlongX ? 0 : sz;
        int fileX = ranksAlongX ? 0 : sx;
        int fileZ = ranksAlongX ? sz : 0;
        double whiteAlongRanks = (whiteX - cx) * rankX + (whiteZ - cz) * rankZ;
        if (whiteAlongRanks >= 0) {
            throw new IllegalArgumentException("white's spawn must be on the rank-1 side (near a1)");
        }
        // Seen from white (facing +rank), files must run left to right: right = (-forwardZ, forwardX).
        boolean mirrored = fileX != -rankZ || fileZ != rankX;
        return new BoardGeometry(a1x, a1z, topY, size, fileX, fileZ, rankX, rankZ, mirrored);
    }

    public double squareSize() {
        return size;
    }

    public double topY() {
        return topY;
    }

    /** True when h1 is on white's left: the arena's a1/h8 marks are swapped across. Still playable. */
    public boolean mirrored() {
        return mirrored;
    }

    public double centreX(int square) {
        return a1x + (square % 8) * size * fileX + (square / 8) * size * rankX;
    }

    public double centreZ(int square) {
        return a1z + (square % 8) * size * fileZ + (square / 8) * size * rankZ;
    }

    /** Yaw (degrees, Minecraft convention) of something facing from white's side towards black. */
    public float whiteForwardYaw() {
        return (float) Math.toDegrees(Math.atan2(-rankX, rankZ));
    }

    /** The square under this world point, or -1 off the board. */
    public int squareAt(double x, double z) {
        double ox = x - a1x;
        double oz = z - a1z;
        double f = (ox * fileX + oz * fileZ) / size + 0.5;
        double r = (ox * rankX + oz * rankZ) / size + 0.5;
        if (f < 0 || r < 0 || f >= 8 || r >= 8) {
            return -1;
        }
        return (int) Math.floor(r) * 8 + (int) Math.floor(f);
    }

    /**
     * The square a look ray hits on the board's top surface, or -1 if it misses or points up.
     *
     * @param range how far the ray reaches, in blocks
     */
    public int square(double eyeX, double eyeY, double eyeZ, double dirX, double dirY, double dirZ, double range) {
        if (dirY >= -1e-6 || eyeY <= topY) {
            return -1;
        }
        double t = (topY - eyeY) / dirY;
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (t * len > range) {
            return -1;
        }
        return squareAt(eyeX + dirX * t, eyeZ + dirZ * t);
    }
}
