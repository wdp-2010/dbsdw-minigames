package be.dbsdw.minigames.chess.save;

import java.util.List;
import java.util.UUID;

/**
 * An unfinished game as saved on disk: who plays white and black, the mode (time control), every
 * move so far in UCI, both clocks and when it was started and last played (epoch milliseconds).
 */
public record SavedGame(String id, UUID white, String whiteName, UUID black, String blackName, String mode,
                        List<String> moves, long initialMillis, long incrementMillis, long whiteMillis,
                        long blackMillis, long createdAt, long updatedAt) {
    public SavedGame {
        moves = List.copyOf(moves);
    }

    public boolean involves(UUID player) {
        return white.equals(player) || black.equals(player);
    }

    public UUID opponentOf(UUID player) {
        return white.equals(player) ? black : white;
    }

    public String opponentName(UUID player) {
        return white.equals(player) ? blackName : whiteName;
    }

    /** White moves when an even number of moves was played. */
    public boolean whiteToMove() {
        return moves.size() % 2 == 0;
    }

    public SavedGame withProgress(List<String> newMoves, long newWhiteMillis, long newBlackMillis, long now) {
        return new SavedGame(id, white, whiteName, black, blackName, mode, newMoves, initialMillis, incrementMillis,
                newWhiteMillis, newBlackMillis, createdAt, now);
    }
}
