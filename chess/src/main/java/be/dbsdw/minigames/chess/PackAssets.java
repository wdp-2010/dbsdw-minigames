package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.rules.Piece;

/**
 * Ids of the resource pack models and sounds chess uses. Every id is written out, so tools/test_pack_keys.py
 * checks each one exists in the pack.
 */
public final class PackAssets {
    /** Index: piece type (1 pawn … 6 king). */
    private static final String[] WHITE = {null, "dbsdw:chess_white_pawn", "dbsdw:chess_white_knight",
            "dbsdw:chess_white_bishop", "dbsdw:chess_white_rook", "dbsdw:chess_white_queen", "dbsdw:chess_white_king"};
    private static final String[] BLACK = {null, "dbsdw:chess_black_pawn", "dbsdw:chess_black_knight",
            "dbsdw:chess_black_bishop", "dbsdw:chess_black_rook", "dbsdw:chess_black_queen", "dbsdw:chess_black_king"};

    /** A piece moves to an empty square. */
    public static final String SOUND_MOVE = "dbsdw:chess.move";
    /** A piece takes another. */
    public static final String SOUND_CAPTURE = "dbsdw:chess.capture";
    /** A king is in check. */
    public static final String SOUND_CHECK = "dbsdw:chess.check";

    private PackAssets() {}

    public static String piece(int piece) {
        return (Piece.isWhite(piece) ? WHITE : BLACK)[Piece.type(piece)];
    }
}
