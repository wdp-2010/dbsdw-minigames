package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.rules.Piece;

/**
 * Ids of the resource pack models chess uses. Every id is written out, so tools/test_pack_keys.py
 * checks each one exists in the pack.
 */
public final class PackAssets {
    /** Index: piece type (1 pawn … 6 king). */
    private static final String[] WHITE = {null, "dbsdw:chess_white_pawn", "dbsdw:chess_white_knight",
            "dbsdw:chess_white_bishop", "dbsdw:chess_white_rook", "dbsdw:chess_white_queen", "dbsdw:chess_white_king"};
    private static final String[] BLACK = {null, "dbsdw:chess_black_pawn", "dbsdw:chess_black_knight",
            "dbsdw:chess_black_bishop", "dbsdw:chess_black_rook", "dbsdw:chess_black_queen", "dbsdw:chess_black_king"};

    private PackAssets() {}

    public static String piece(int piece) {
        return (Piece.isWhite(piece) ? WHITE : BLACK)[Piece.type(piece)];
    }
}
