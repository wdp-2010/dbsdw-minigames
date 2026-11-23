package be.dbsdw.minigames.chess.rules;

/**
 * A move from one square to another. Squares are 0 to 63, a1 = 0, h1 = 7, a8 = 56. {@code promotion}
 * is the piece type a pawn becomes on the last rank ({@link Piece#QUEEN} …), otherwise 0. Castling is
 * the king's two-square move.
 */
public record Move(int from, int to, int promotion) {
    public Move {
        if (from < 0 || from > 63 || to < 0 || to > 63 || from == to) {
            throw new IllegalArgumentException("bad squares " + from + " -> " + to);
        }
        if (promotion != 0 && (promotion < Piece.KNIGHT || promotion > Piece.QUEEN)) {
            throw new IllegalArgumentException("bad promotion " + promotion);
        }
    }

    public Move(int from, int to) {
        this(from, to, 0);
    }

    /** UCI notation: e2e4, e7e8q. */
    public String uci() {
        String s = square(from) + square(to);
        return promotion == 0 ? s : s + Character.toLowerCase(Piece.letter(promotion));
    }

    /** @throws IllegalArgumentException if the text is not a UCI move */
    public static Move parse(String uci) {
        if (uci.length() != 4 && uci.length() != 5) {
            throw new IllegalArgumentException("not a move: " + uci);
        }
        int promotion = 0;
        if (uci.length() == 5) {
            promotion = Piece.type(Piece.fromLetter(uci.charAt(4)));
            if (promotion == 0) {
                throw new IllegalArgumentException("not a move: " + uci);
            }
        }
        return new Move(square(uci.substring(0, 2)), square(uci.substring(2, 4)), promotion);
    }

    public static String square(int sq) {
        return "" + (char) ('a' + sq % 8) + (char) ('1' + sq / 8);
    }

    /** @throws IllegalArgumentException if the text is not a square like e4 */
    public static int square(String name) {
        if (name.length() != 2) {
            throw new IllegalArgumentException("not a square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("not a square: " + name);
        }
        return rank * 8 + file;
    }

    @Override
    public String toString() {
        return uci();
    }
}
