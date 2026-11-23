package be.dbsdw.minigames.chess.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One chess position: pieces, side to move, castling rights, en passant square and move counters.
 * Immutable; {@link #play} returns the next position. Squares: a1 = 0 … h8 = 63.
 */
public final class Position {
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    static final int WHITE_KING_SIDE = 1;
    static final int WHITE_QUEEN_SIDE = 2;
    static final int BLACK_KING_SIDE = 4;
    static final int BLACK_QUEEN_SIDE = 8;

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] PROMOTIONS = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};

    private final int[] board;
    private final boolean whiteToMove;
    private final int castling;
    private final int enPassant;
    private final int halfmoveClock;
    private final int fullmove;

    private Position(int[] board, boolean whiteToMove, int castling, int enPassant, int halfmoveClock, int fullmove) {
        this.board = board;
        this.whiteToMove = whiteToMove;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmove = fullmove;
    }

    public static Position start() {
        return fen(START_FEN);
    }

    /** @throws IllegalArgumentException if the text is not a valid FEN position */
    public static Position fen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        int[] board = new int[64];
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN needs 8 ranks: " + fen);
        }
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    int piece = Piece.fromLetter(c);
                    if (piece == 0 || file > 7) {
                        throw new IllegalArgumentException("bad FEN rank " + ranks[i]);
                    }
                    board[rank * 8 + file++] = piece;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("bad FEN rank " + ranks[i]);
            }
        }
        boolean white = switch (parts[1]) {
            case "w" -> true;
            case "b" -> false;
            default -> throw new IllegalArgumentException("bad side to move " + parts[1]);
        };
        int castling = 0;
        if (!parts[2].equals("-")) {
            for (char c : parts[2].toCharArray()) {
                castling |= switch (c) {
                    case 'K' -> WHITE_KING_SIDE;
                    case 'Q' -> WHITE_QUEEN_SIDE;
                    case 'k' -> BLACK_KING_SIDE;
                    case 'q' -> BLACK_QUEEN_SIDE;
                    default -> throw new IllegalArgumentException("bad castling " + parts[2]);
                };
            }
        }
        int ep = parts[3].equals("-") ? -1 : Move.square(parts[3]);
        int half = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int full = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        if (Arrays.stream(board).filter(p -> p == Piece.KING).count() != 1
                || Arrays.stream(board).filter(p -> p == -Piece.KING).count() != 1) {
            throw new IllegalArgumentException("each side needs exactly one king: " + fen);
        }
        return new Position(board, white, castling, ep, half, full);
    }

    public String fen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int p = board[rank * 8 + file];
                if (p == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.letter(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(whiteToMove ? " w " : " b ").append(castlingText());
        sb.append(' ').append(enPassant < 0 ? "-" : Move.square(enPassant));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmove);
        return sb.toString();
    }

    private String castlingText() {
        String s = ((castling & WHITE_KING_SIDE) != 0 ? "K" : "") + ((castling & WHITE_QUEEN_SIDE) != 0 ? "Q" : "")
                + ((castling & BLACK_KING_SIDE) != 0 ? "k" : "") + ((castling & BLACK_QUEEN_SIDE) != 0 ? "q" : "");
        return s.isEmpty() ? "-" : s;
    }

    /**
     * What makes two positions the same for repetition: pieces, side to move, castling rights and an
     * en passant square only when an en passant capture is actually possible.
     */
    public String repetitionKey() {
        String fen = fen();
        String[] parts = fen.split(" ");
        String ep = "-";
        if (enPassant >= 0) {
            for (Move m : legalMoves()) {
                if (m.to() == enPassant && Piece.type(board[m.from()]) == Piece.PAWN) {
                    ep = parts[3];
                    break;
                }
            }
        }
        return parts[0] + " " + parts[1] + " " + parts[2] + " " + ep;
    }

    public int at(int square) {
        return board[square];
    }

    public boolean whiteToMove() {
        return whiteToMove;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmove() {
        return fullmove;
    }

    public int king(boolean white) {
        int k = Piece.of(Piece.KING, white);
        for (int sq = 0; sq < 64; sq++) {
            if (board[sq] == k) {
                return sq;
            }
        }
        throw new IllegalStateException("no king");
    }

    public boolean inCheck() {
        return attacked(king(whiteToMove), !whiteToMove);
    }

    /** Whether a piece of the given side attacks the square. */
    public boolean attacked(int sq, boolean byWhite) {
        int f = sq % 8;
        int r = sq / 8;
        int pawnRank = byWhite ? r - 1 : r + 1;
        for (int df : new int[] {-1, 1}) {
            if (on(f + df, pawnRank) && board[pawnRank * 8 + f + df] == Piece.of(Piece.PAWN, byWhite)) {
                return true;
            }
        }
        if (steps(f, r, KNIGHT_STEPS, Piece.of(Piece.KNIGHT, byWhite))
                || steps(f, r, KING_STEPS, Piece.of(Piece.KING, byWhite))) {
            return true;
        }
        return slides(f, r, ROOK_DIRS, Piece.of(Piece.ROOK, byWhite), Piece.of(Piece.QUEEN, byWhite))
                || slides(f, r, BISHOP_DIRS, Piece.of(Piece.BISHOP, byWhite), Piece.of(Piece.QUEEN, byWhite));
    }

    private boolean steps(int f, int r, int[][] steps, int piece) {
        for (int[] s : steps) {
            if (on(f + s[0], r + s[1]) && board[(r + s[1]) * 8 + f + s[0]] == piece) {
                return true;
            }
        }
        return false;
    }

    private boolean slides(int f, int r, int[][] dirs, int a, int b) {
        for (int[] d : dirs) {
            int nf = f + d[0];
            int nr = r + d[1];
            while (on(nf, nr)) {
                int p = board[nr * 8 + nf];
                if (p != 0) {
                    if (p == a || p == b) {
                        return true;
                    }
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        return false;
    }

    private static boolean on(int f, int r) {
        return f >= 0 && f < 8 && r >= 0 && r < 8;
    }

    /** Every legal move of the side to move. */
    public List<Move> legalMoves() {
        List<Move> legal = new ArrayList<>();
        for (Move m : pseudoMoves()) {
            Position next = play(m);
            if (!next.attacked(next.king(whiteToMove), !whiteToMove)) {
                legal.add(m);
            }
        }
        return legal;
    }

    /** Legal moves of the piece on this square (empty for an empty square or the other side's piece). */
    public List<Move> legalMovesFrom(int square) {
        return legalMoves().stream().filter(m -> m.from() == square).toList();
    }

    private List<Move> pseudoMoves() {
        List<Move> out = new ArrayList<>();
        for (int sq = 0; sq < 64; sq++) {
            int p = board[sq];
            if (p == 0 || Piece.isWhite(p) != whiteToMove) {
                continue;
            }
            int f = sq % 8;
            int r = sq / 8;
            switch (Piece.type(p)) {
                case Piece.PAWN -> pawnMoves(sq, f, r, out);
                case Piece.KNIGHT -> stepMoves(sq, f, r, KNIGHT_STEPS, out);
                case Piece.BISHOP -> slideMoves(sq, f, r, BISHOP_DIRS, out);
                case Piece.ROOK -> slideMoves(sq, f, r, ROOK_DIRS, out);
                case Piece.QUEEN -> {
                    slideMoves(sq, f, r, ROOK_DIRS, out);
                    slideMoves(sq, f, r, BISHOP_DIRS, out);
                }
                case Piece.KING -> {
                    stepMoves(sq, f, r, KING_STEPS, out);
                    castleMoves(sq, out);
                }
                default -> throw new IllegalStateException("bad piece " + p);
            }
        }
        return out;
    }

    private boolean enemy(int p) {
        return p != 0 && Piece.isWhite(p) != whiteToMove;
    }

    private void pawnMoves(int sq, int f, int r, List<Move> out) {
        int dir = whiteToMove ? 1 : -1;
        int startRank = whiteToMove ? 1 : 6;
        int nr = r + dir;
        if (!on(f, nr)) {
            return;
        }
        if (board[nr * 8 + f] == 0) {
            addPawn(sq, nr * 8 + f, out);
            int two = r + 2 * dir;
            if (r == startRank && board[two * 8 + f] == 0) {
                out.add(new Move(sq, two * 8 + f));
            }
        }
        for (int df : new int[] {-1, 1}) {
            int nf = f + df;
            if (!on(nf, nr)) {
                continue;
            }
            int t = nr * 8 + nf;
            if (enemy(board[t])) {
                addPawn(sq, t, out);
            } else if (t == enPassant && board[t] == 0) {
                out.add(new Move(sq, t));
            }
        }
    }

    private void addPawn(int from, int to, List<Move> out) {
        int rank = to / 8;
        if (rank == 0 || rank == 7) {
            for (int promo : PROMOTIONS) {
                out.add(new Move(from, to, promo));
            }
        } else {
            out.add(new Move(from, to));
        }
    }

    private void stepMoves(int sq, int f, int r, int[][] steps, List<Move> out) {
        for (int[] s : steps) {
            int nf = f + s[0];
            int nr = r + s[1];
            if (on(nf, nr)) {
                int t = nr * 8 + nf;
                if (board[t] == 0 || enemy(board[t])) {
                    out.add(new Move(sq, t));
                }
            }
        }
    }

    private void slideMoves(int sq, int f, int r, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nf = f + d[0];
            int nr = r + d[1];
            while (on(nf, nr)) {
                int t = nr * 8 + nf;
                if (board[t] == 0) {
                    out.add(new Move(sq, t));
                } else {
                    if (enemy(board[t])) {
                        out.add(new Move(sq, t));
                    }
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
    }

    private void castleMoves(int sq, List<Move> out) {
        int home = whiteToMove ? 4 : 60;
        if (sq != home || attacked(home, !whiteToMove)) {
            return;
        }
        int rook = Piece.of(Piece.ROOK, whiteToMove);
        int kingSide = whiteToMove ? WHITE_KING_SIDE : BLACK_KING_SIDE;
        int queenSide = whiteToMove ? WHITE_QUEEN_SIDE : BLACK_QUEEN_SIDE;
        if ((castling & kingSide) != 0 && board[home + 3] == rook && board[home + 1] == 0 && board[home + 2] == 0
                && !attacked(home + 1, !whiteToMove) && !attacked(home + 2, !whiteToMove)) {
            out.add(new Move(home, home + 2));
        }
        if ((castling & queenSide) != 0 && board[home - 4] == rook && board[home - 1] == 0 && board[home - 2] == 0
                && board[home - 3] == 0 && !attacked(home - 1, !whiteToMove) && !attacked(home - 2, !whiteToMove)) {
            out.add(new Move(home, home - 2));
        }
    }

    /** The position after this move. The move must be legal here (not checked). */
    public Position play(Move m) {
        int[] next = board.clone();
        int p = next[m.from()];
        int type = Piece.type(p);
        int captured = next[m.to()];
        boolean enPassantCapture = type == Piece.PAWN && m.to() == enPassant && captured == 0
                && m.from() % 8 != m.to() % 8;
        if (enPassantCapture) {
            next[m.to() + (whiteToMove ? -8 : 8)] = 0;
        }
        next[m.from()] = 0;
        next[m.to()] = m.promotion() != 0 ? Piece.of(m.promotion(), whiteToMove) : p;
        if (type == Piece.KING && Math.abs(m.to() - m.from()) == 2) {
            boolean kingSide = m.to() > m.from();
            int rookFrom = kingSide ? m.from() + 3 : m.from() - 4;
            int rookTo = kingSide ? m.from() + 1 : m.from() - 1;
            next[rookTo] = next[rookFrom];
            next[rookFrom] = 0;
        }
        int rights = castling & rightsKept(m.from()) & rightsKept(m.to());
        int ep = type == Piece.PAWN && Math.abs(m.to() - m.from()) == 16 ? (m.from() + m.to()) / 2 : -1;
        int half = type == Piece.PAWN || captured != 0 || enPassantCapture ? 0 : halfmoveClock + 1;
        int full = whiteToMove ? fullmove : fullmove + 1;
        return new Position(next, !whiteToMove, rights, ep, half, full);
    }

    private static int rightsKept(int sq) {
        return switch (sq) {
            case 0 -> ~WHITE_QUEEN_SIDE;
            case 7 -> ~WHITE_KING_SIDE;
            case 4 -> ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
            case 56 -> ~BLACK_QUEEN_SIDE;
            case 63 -> ~BLACK_KING_SIDE;
            case 60 -> ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
            default -> ~0;
        };
    }

    /** What a move does, for animation and sounds. */
    public MoveEffect effect(Move m) {
        int p = board[m.from()];
        int type = Piece.type(p);
        int capturedSquare = -1;
        if (board[m.to()] != 0) {
            capturedSquare = m.to();
        } else if (type == Piece.PAWN && m.to() == enPassant && m.from() % 8 != m.to() % 8) {
            capturedSquare = m.to() + (whiteToMove ? -8 : 8);
        }
        int rookFrom = -1;
        int rookTo = -1;
        if (type == Piece.KING && Math.abs(m.to() - m.from()) == 2) {
            boolean kingSide = m.to() > m.from();
            rookFrom = kingSide ? m.from() + 3 : m.from() - 4;
            rookTo = kingSide ? m.from() + 1 : m.from() - 1;
        }
        return new MoveEffect(capturedSquare, rookFrom, rookTo);
    }

    /**
     * Side effects of a move beside the moving piece: the square of a captured piece (differs from
     * {@code to} for en passant) and the castling rook's move; -1 when there is none.
     */
    public record MoveEffect(int capturedSquare, int rookFrom, int rookTo) {}

    /**
     * Whether this side has too little material to ever checkmate: a bare king, or a king with only
     * one bishop or one knight. Used for draws on time.
     */
    public boolean cannotMate(boolean white) {
        int minors = 0;
        for (int p : board) {
            if (p == 0 || Piece.isWhite(p) != white) {
                continue;
            }
            switch (Piece.type(p)) {
                case Piece.KING -> {}
                case Piece.KNIGHT, Piece.BISHOP -> minors++;
                default -> {
                    return false;
                }
            }
        }
        return minors <= 1;
    }

    /**
     * Neither side can checkmate: king against king, king and one minor piece against king, or kings
     * with bishops all on squares of one colour.
     */
    public boolean insufficientMaterial() {
        int knights = 0;
        int lightBishops = 0;
        int darkBishops = 0;
        for (int sq = 0; sq < 64; sq++) {
            int type = Piece.type(board[sq]);
            switch (type) {
                case Piece.EMPTY, Piece.KING -> {}
                case Piece.KNIGHT -> knights++;
                case Piece.BISHOP -> {
                    if ((sq % 8 + sq / 8) % 2 == 0) {
                        darkBishops++;
                    } else {
                        lightBishops++;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        int bishops = lightBishops + darkBishops;
        if (knights == 0) {
            return bishops <= 1 || lightBishops == 0 || darkBishops == 0;
        }
        return knights == 1 && bishops == 0;
    }
}
