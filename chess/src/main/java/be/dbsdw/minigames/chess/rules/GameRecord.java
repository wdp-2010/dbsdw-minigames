package be.dbsdw.minigames.chess.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A game from the start position: the moves played, the current position and whether the game is
 * over by the board (mate, stalemate, repetition, 50 moves, too little material). Draws by repetition
 * and the 50-move rule are automatic, so nobody has to claim them.
 */
public final class GameRecord {
    public enum Status {
        ONGOING(false),
        CHECKMATE(false),
        STALEMATE(true),
        THREEFOLD_REPETITION(true),
        FIFTY_MOVES(true),
        INSUFFICIENT_MATERIAL(true);

        private final boolean draw;

        Status(boolean draw) {
            this.draw = draw;
        }

        public boolean draw() {
            return draw;
        }
    }

    private final List<Move> moves = new ArrayList<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private Position position;
    private Status status;
    private List<Move> legal;

    public GameRecord() {
        this.position = Position.start();
        afterMove();
    }

    /** Replays saved moves. @throws IllegalArgumentException if a move is not legal */
    public static GameRecord replay(List<String> uciMoves) {
        GameRecord game = new GameRecord();
        for (String uci : uciMoves) {
            Move m = Move.parse(uci);
            if (!game.play(m)) {
                throw new IllegalArgumentException("illegal move " + uci + " after " + game.moves.size() + " moves");
            }
        }
        return game;
    }

    public Position position() {
        return position;
    }

    public Status status() {
        return status;
    }

    public boolean over() {
        return status != Status.ONGOING;
    }

    public List<Move> moves() {
        return List.copyOf(moves);
    }

    public List<String> uciMoves() {
        return moves.stream().map(Move::uci).toList();
    }

    public Optional<Move> lastMove() {
        return moves.isEmpty() ? Optional.empty() : Optional.of(moves.getLast());
    }

    public List<Move> legalMoves() {
        return legal;
    }

    public List<Move> legalMovesFrom(int square) {
        return legal.stream().filter(m -> m.from() == square).toList();
    }

    /** Whether some legal move goes from → to (any promotion). */
    public boolean isPromotion(int from, int to) {
        return legal.stream().anyMatch(m -> m.from() == from && m.to() == to && m.promotion() != 0);
    }

    /** Plays the move if it is legal and the game is not over; returns whether it was played. */
    public boolean play(Move m) {
        if (over() || !legal.contains(m)) {
            return false;
        }
        position = position.play(m);
        moves.add(m);
        afterMove();
        return true;
    }

    private void afterMove() {
        legal = position.legalMoves();
        int count = seen.merge(position.repetitionKey(), 1, Integer::sum);
        if (legal.isEmpty()) {
            status = position.inCheck() ? Status.CHECKMATE : Status.STALEMATE;
        } else if (position.insufficientMaterial()) {
            status = Status.INSUFFICIENT_MATERIAL;
        } else if (count >= 3) {
            status = Status.THREEFOLD_REPETITION;
        } else if (position.halfmoveClock() >= 100) {
            status = Status.FIFTY_MOVES;
        } else {
            status = Status.ONGOING;
        }
    }
}
