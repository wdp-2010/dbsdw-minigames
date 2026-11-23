package be.dbsdw.minigames.chess.rules;

/**
 * Piece codes on a {@link Position} board: 0 is an empty square, 1 to 6 are the white pawn, knight,
 * bishop, rook, queen and king, and the same numbers negated are the black pieces.
 */
public final class Piece {
    public static final int EMPTY = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    private static final String LETTERS = ".pnbrqk";

    private Piece() {}

    public static int type(int piece) {
        return Math.abs(piece);
    }

    public static boolean isWhite(int piece) {
        return piece > 0;
    }

    public static int of(int type, boolean white) {
        return white ? type : -type;
    }

    /** FEN letter: upper case for white, lower case for black. */
    public static char letter(int piece) {
        char c = LETTERS.charAt(type(piece));
        return piece > 0 ? Character.toUpperCase(c) : c;
    }

    /** The piece for a FEN letter, or 0 for anything else. */
    public static int fromLetter(char c) {
        int type = LETTERS.indexOf(Character.toLowerCase(c));
        if (type <= 0) {
            return EMPTY;
        }
        return Character.isUpperCase(c) ? type : -type;
    }

    /** Lower-case name used in pack model ids and messages: pawn, knight, … */
    public static String name(int type) {
        return switch (type) {
            case PAWN -> "pawn";
            case KNIGHT -> "knight";
            case BISHOP -> "bishop";
            case ROOK -> "rook";
            case QUEEN -> "queen";
            case KING -> "king";
            default -> throw new IllegalArgumentException("no piece type " + type);
        };
    }
}
