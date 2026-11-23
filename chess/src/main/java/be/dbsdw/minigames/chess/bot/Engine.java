package be.dbsdw.minigames.chess.bot;

import be.dbsdw.minigames.chess.rules.Move;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.chess.rules.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * A small chess engine for the bot: iterative deepening alpha-beta with a capture search at the
 * leaves, simple move ordering (best move of the last iteration, captures by value, killers) and an
 * evaluation of material and piece-square tables. Built on {@link Position}, so it is slow next to a
 * real engine, but a few plies deep it plays a decent game.
 *
 * <p>One search runs on one thread; an {@code Engine} is not shared between searches.
 */
public final class Engine {
    static final int MATE = 100_000;
    private static final int INF = 1_000_000;
    private static final int MAX_PLY = 64;
    private static final int MAX_QUIESCENCE = 8;
    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
    private static final int[] PHASE = {0, 0, 1, 1, 2, 4, 0};

    /** What the search found: the move, its score for the side to move (centipawns), the depth reached. */
    public record Result(Move move, int score, int depth, long nodes) {}

    /** How the bot searches; see {@link BotLevel}. */
    public record Limits(int maxDepth, long millis, int noise, double randomChance) {}

    private final Map<Long, Integer> history;
    private final long[] path = new long[MAX_PLY + MAX_QUIESCENCE + 2];
    private final Move[][] killers = new Move[MAX_PLY + 1][2];
    private long deadline;
    private long nodes;
    private boolean stopped;

    /**
     * @param gameHashes {@link #hash} of every position of the game so far, the current one included;
     *     coming back to one of them counts as a draw in the search
     */
    public Engine(List<Long> gameHashes) {
        history = new HashMap<>();
        for (long h : gameHashes) {
            history.merge(h, 1, Integer::sum);
        }
    }

    /** The bot's move in this position (which must have a legal move). */
    public Result think(Position root, Limits limits, RandomGenerator rnd) {
        List<Move> legal = root.legalMoves();
        if (legal.isEmpty()) {
            throw new IllegalArgumentException("no legal moves");
        }
        if (legal.size() == 1) {
            return new Result(legal.getFirst(), 0, 0, 0);
        }
        if (limits.randomChance() > 0 && rnd.nextDouble() < limits.randomChance()) {
            return new Result(legal.get(rnd.nextInt(legal.size())), 0, 0, 0);
        }
        deadline = limits.millis() <= 0 ? Long.MAX_VALUE : System.nanoTime() + limits.millis() * 1_000_000;
        nodes = 0;
        stopped = false;
        path[0] = hash(root);
        List<Move> order = ordered(root, legal, 0);
        Move best = order.getFirst();
        int bestScore = 0;
        int reached = 0;
        for (int depth = 1; depth <= limits.maxDepth(); depth++) {
            int[] scores = new int[order.size()];
            int alpha = -INF;
            Move iterBest = null;
            int iterScore = -INF;
            for (int i = 0; i < order.size(); i++) {
                Move m = order.get(i);
                Position next = root.play(m);
                // With noise every root move needs an exact score, so no window narrowing at the root.
                int a = limits.noise() > 0 ? -INF : alpha;
                int score = -search(next, depth - 1, 1, -INF, -a);
                if (stopped) {
                    break;
                }
                if (limits.noise() > 0) {
                    score += rnd.nextInt(-limits.noise(), limits.noise() + 1);
                }
                scores[i] = score;
                if (score > iterScore) {
                    iterScore = score;
                    iterBest = m;
                }
                alpha = Math.max(alpha, score);
            }
            if (stopped) {
                // A partial iteration searched the previous best first, so its best so far is at least as good.
                if (iterBest != null) {
                    best = iterBest;
                    bestScore = iterScore;
                }
                break;
            }
            best = iterBest;
            bestScore = iterScore;
            reached = depth;
            // Best move first in the next iteration, the rest by this iteration's scores.
            List<Move> next = new ArrayList<>(order);
            int[] s = scores;
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < next.size(); i++) {
                idx.add(i);
            }
            idx.sort(Comparator.comparingInt(i -> -s[i]));
            order = idx.stream().map(next::get).toList();
            if (Math.abs(bestScore) > MATE - MAX_PLY) {
                break; // a forced mate was found (for either side): searching deeper changes nothing
            }
        }
        return new Result(best, bestScore, reached, nodes);
    }

    private boolean timeUp() {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            stopped = true;
        }
        return stopped;
    }

    private int search(Position pos, int depth, int ply, int alpha, int beta) {
        if (timeUp()) {
            return 0;
        }
        long h = hash(pos);
        path[ply] = h;
        if (ply > 0 && repeated(h, ply)) {
            return 0;
        }
        if (pos.halfmoveClock() >= 100) {
            return 0;
        }
        boolean check = pos.inCheck();
        if (check && ply < MAX_PLY) {
            depth++; // look one ply further when in check
        }
        if (depth <= 0 || ply >= MAX_PLY) {
            return quiescence(pos, ply, alpha, beta, 0);
        }
        List<Move> legal = pos.legalMoves();
        if (legal.isEmpty()) {
            return check ? -MATE + ply : 0;
        }
        for (Move m : ordered(pos, legal, ply)) {
            int score = -search(pos.play(m), depth - 1, ply + 1, -beta, -alpha);
            if (stopped) {
                return 0;
            }
            if (score >= beta) {
                if (!capture(pos, m)) {
                    Move[] k = killers[Math.min(ply, MAX_PLY)];
                    if (!m.equals(k[0])) {
                        k[1] = k[0];
                        k[0] = m;
                    }
                }
                return beta;
            }
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    private int quiescence(Position pos, int ply, int alpha, int beta, int qply) {
        if (timeUp()) {
            return 0;
        }
        boolean check = pos.inCheck();
        List<Move> legal = pos.legalMoves();
        if (legal.isEmpty()) {
            return check ? -MATE + ply : 0;
        }
        if (!check) {
            int stand = evaluate(pos);
            if (stand >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, stand);
        }
        if (qply >= MAX_QUIESCENCE) {
            return check ? evaluate(pos) : alpha;
        }
        List<Move> moves = new ArrayList<>();
        for (Move m : legal) {
            if (check || capture(pos, m) || m.promotion() == Piece.QUEEN) {
                moves.add(m);
            }
        }
        for (Move m : ordered(pos, moves, ply)) {
            int score = -quiescence(pos.play(m), ply + 1, -beta, -alpha, qply + 1);
            if (stopped) {
                return 0;
            }
            if (score >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    private boolean repeated(long h, int ply) {
        for (int i = ply - 2; i >= 0; i -= 2) {
            if (path[i] == h) {
                return true;
            }
        }
        return history.containsKey(h);
    }

    private List<Move> ordered(Position pos, List<Move> moves, int ply) {
        Move[] k = killers[Math.min(ply, MAX_PLY)];
        List<Move> out = new ArrayList<>(moves);
        out.sort(Comparator.comparingInt(m -> -orderScore(pos, m, k)));
        return out;
    }

    private static int orderScore(Position pos, Move m, Move[] killers) {
        int score = 0;
        int victim = Piece.type(pos.at(m.to()));
        int attacker = Piece.type(pos.at(m.from()));
        if (victim != 0) {
            score += 10_000 + VALUE[victim] * 10 - VALUE[attacker] / 10;
        } else if (attacker == Piece.PAWN && m.from() % 8 != m.to() % 8) {
            score += 10_000 + VALUE[Piece.PAWN] * 10 - VALUE[Piece.PAWN] / 10; // en passant
        }
        if (m.promotion() != 0) {
            score += 9_000 + VALUE[m.promotion()];
        }
        if (score == 0 && (m.equals(killers[0]) || m.equals(killers[1]))) {
            score = 5_000;
        }
        return score;
    }

    private static boolean capture(Position pos, Move m) {
        return pos.at(m.to()) != 0
                || Piece.type(pos.at(m.from())) == Piece.PAWN && m.from() % 8 != m.to() % 8;
    }

    // ---------------------------------------------------------------- evaluation

    /** The position's value for the side to move, in centipawns. */
    static int evaluate(Position pos) {
        int mg = 0;
        int eg = 0;
        int phase = 0;
        int whiteBishops = 0;
        int blackBishops = 0;
        int whiteKing = -1;
        int blackKing = -1;
        int whiteMaterial = 0;
        int blackMaterial = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = pos.at(sq);
            if (p == 0) {
                continue;
            }
            int type = Piece.type(p);
            boolean white = Piece.isWhite(p);
            int idx = white ? (7 - sq / 8) * 8 + sq % 8 : sq;
            int sign = white ? 1 : -1;
            phase += PHASE[type];
            if (type == Piece.KING) {
                mg += sign * Tables.KING_MG[idx];
                eg += sign * Tables.KING_EG[idx];
                if (white) {
                    whiteKing = sq;
                } else {
                    blackKing = sq;
                }
                continue;
            }
            int v = VALUE[type] + Tables.of(type)[idx];
            mg += sign * v;
            eg += sign * v;
            if (white) {
                whiteMaterial += VALUE[type];
            } else {
                blackMaterial += VALUE[type];
            }
            if (type == Piece.BISHOP) {
                if (white) {
                    whiteBishops++;
                } else {
                    blackBishops++;
                }
            }
        }
        int pair = (whiteBishops >= 2 ? 30 : 0) - (blackBishops >= 2 ? 30 : 0);
        mg += pair;
        eg += pair;
        phase = Math.min(phase, 24);
        int score = (mg * phase + eg * (24 - phase)) / 24;
        // Mop-up: the side well ahead in a thin endgame drives the other king to the edge and walks its
        // own king closer, so the bot actually mates instead of shuffling.
        if (phase <= 6 && whiteKing >= 0 && blackKing >= 0) {
            int lead = whiteMaterial - blackMaterial;
            if (Math.abs(lead) >= 300) {
                int loser = lead > 0 ? blackKing : whiteKing;
                int bonus = 10 * centreDistance(loser) + 4 * (14 - kingDistance(whiteKing, blackKing));
                score += lead > 0 ? bonus : -bonus;
            }
        }
        return pos.whiteToMove() ? score : -score;
    }

    private static int centreDistance(int sq) {
        int f = sq % 8;
        int r = sq / 8;
        return Math.max(3 - f, f - 4) + Math.max(3 - r, r - 4);
    }

    private static int kingDistance(int a, int b) {
        return Math.abs(a % 8 - b % 8) + Math.abs(a / 8 - b / 8);
    }

    /**
     * A 64-bit hash of the board and the side to move (castling and en passant rights left out: good
     * enough to spot repetitions in a search).
     */
    public static long hash(Position pos) {
        long h = pos.whiteToMove() ? 0x9E3779B97F4A7C15L : 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = pos.at(sq);
            if (p != 0) {
                h ^= mix(sq * 16L + p + 8);
            }
        }
        return h;
    }

    private static long mix(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
