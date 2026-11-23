package be.dbsdw.minigames.chess.match;

import be.dbsdw.minigames.chess.ChessGame;
import be.dbsdw.minigames.chess.ChessSettings;
import be.dbsdw.minigames.chess.menu.PromotionMenu;
import be.dbsdw.minigames.chess.rules.BoardGeometry;
import be.dbsdw.minigames.chess.rules.ChessClock;
import be.dbsdw.minigames.chess.rules.GameRecord;
import be.dbsdw.minigames.chess.rules.Move;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.chess.rules.Position;
import be.dbsdw.minigames.chess.save.SavedGame;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * One chess game on one board copy. Team 0 plays white, team 1 black. Every move is saved at once, so
 * a game that stops without a result (paused, a player gone, a server restart) can be continued.
 *
 * <p>When a player leaves, the board waits {@code away-grace-seconds} with both clocks stopped. They
 * can come back in that time. After it, the player who stayed claims the win or keeps the game for
 * later.
 */
public final class ChessMatch implements MatchHandler {
    private enum Phase { PLAYING, AWAY, CLAIM }

    /** Two clicks closer together than this are one click (the client may send a block and an air use). */
    private static final long CLICK_GAP_TICKS = 3;
    private static final long CONFIRM_TICKS = 60;
    private static final long REQUEST_TICKS = 30 * 20;

    private final MatchContext ctx;
    private final ChessGame game;
    private final ChessSettings settings;
    private final Messages messages;
    private final ChessSettings.TimeControl control;
    private UUID white;
    private UUID black;
    private String whiteName;
    private String blackName;
    private GameRecord record;
    private ChessClock clock;
    private SavedGame saved;
    private BoardGeometry geo;
    private BoardView view;
    private BossBar bar;
    private Phase phase = Phase.PLAYING;
    private UUID away;
    private long awayUntil;
    private int selected = -1;
    private long lastClick = -100;
    private long lastNanos;
    private UUID drawOffer;
    private UUID pauseRequest;
    private long pauseUntil;
    private UUID resignArmed;
    private long resignUntil;
    /** A result was given (mate, draw, resign, time, claim): the save goes. */
    private boolean decided;
    private boolean ended;

    public ChessMatch(MatchContext ctx, ChessGame game) {
        this.ctx = ctx;
        this.game = game;
        this.settings = game.settings();
        this.messages = game.messages();
        this.control = game.control(ctx.mode().id());
    }

    @Override
    public void start() {
        Player w = ctx.team(0).getFirst();
        Player b = ctx.team(1).getFirst();
        white = w.getUniqueId();
        black = b.getUniqueId();
        whiteName = w.getName();
        blackName = b.getName();
        SavedGame resume = game.takeResume(white, black);
        if (resume != null) {
            record = GameRecord.replay(resume.moves());
            clock = new ChessClock(resume.initialMillis(), resume.incrementMillis(), resume.whiteMillis(),
                    resume.blackMillis());
            saved = resume;
        } else {
            record = new GameRecord();
            clock = new ChessClock(control.initialMillis(), control.incrementMillis());
            long now = System.currentTimeMillis();
            saved = new SavedGame(game.store().newId(), white, whiteName, black, blackName, ctx.mode().id(),
                    List.of(), control.initialMillis(), control.incrementMillis(), control.initialMillis(),
                    control.initialMillis(), now, now);
        }
        Location a1 = ctx.arena().flag(0).orElseThrow(() -> new IllegalStateException("arena has no a1 mark (flag 0)"));
        Location h8 = ctx.arena().flag(1).orElseThrow(() -> new IllegalStateException("arena has no h8 mark (flag 1)"));
        Location stand = ctx.arena().spawns(0).getFirst();
        geo = BoardGeometry.of(a1.getX(), a1.getZ(), h8.getX(), h8.getZ(), a1.getY(), stand.getX(), stand.getZ());
        if (geo.mirrored()) {
            game.plugin().getLogger().warning("[chess] arena " + ctx.arena().name()
                    + ": h1 is on white's left; swap the a1/h8 marks (flags 0 and 1)");
        }
        view = new BoardView(game.plugin(), ctx.arena().world(), geo, settings.pieceScale(), settings.moveTicks());
        view.setup(record.position());
        view.marks(record.lastMove().orElse(null), record.position());
        bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        for (Player p : players()) {
            equip(p);
        }
        if (resume != null) {
            tellBoth(messages.get("resumed", Placeholder.unparsed("moves", String.valueOf(record.moves().size()))));
        }
        game.track(this, white, black);
        lastNanos = System.nanoTime();
        updateBar();
        game.plugin().getLogger().info("[chess] game " + saved.id() + (resume != null ? " resumed: " : " started: ")
                + whiteName + " (white) vs " + blackName + " (black), " + ctx.mode().id());
    }

    private void equip(Player p) {
        Hotbar.give(p, messages, phase == Phase.CLAIM);
        p.showBossBar(bar);
        p.sendMessage(messages.get(p.getUniqueId().equals(white) ? "you-are-white" : "you-are-black"));
    }

    private List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (UUID id : new UUID[] {white, black}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && ctx.teamOf(p).isPresent()) {
                out.add(p);
            }
        }
        return out;
    }

    private void tellBoth(Component message) {
        players().forEach(p -> p.sendMessage(message));
    }

    private Player mover() {
        Player p = Bukkit.getPlayer(record.position().whiteToMove() ? white : black);
        return p != null && ctx.teamOf(p).isPresent() ? p : null;
    }

    public String id() {
        return saved.id();
    }

    public boolean has(UUID player) {
        return player.equals(white) || player.equals(black);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        long now = System.nanoTime();
        long elapsed = (now - lastNanos) / 1_000_000;
        lastNanos = now;
        view.tick();
        if (ended) {
            return;
        }
        switch (phase) {
            case PLAYING -> {
                // The clock runs from the moment both sides have made their first move.
                if (record.moves().size() >= 2) {
                    boolean whiteThinking = record.position().whiteToMove();
                    clock.run(whiteThinking, elapsed);
                    if (clock.flagged(whiteThinking)) {
                        flag(whiteThinking);
                        return;
                    }
                }
                Player mover = mover();
                if (mover != null && ctx.ticks() % 2 == 0) {
                    view.hover(look(mover), mover);
                }
            }
            case AWAY -> {
                view.hover(-1, null);
                if (ctx.ticks() >= awayUntil) {
                    phase = Phase.CLAIM;
                    for (Player p : players()) {
                        Hotbar.give(p, messages, true);
                        p.sendMessage(messages.get("away.choose"));
                    }
                }
            }
            case CLAIM -> {}
        }
        if (pauseRequest != null && ctx.ticks() > pauseUntil) {
            pauseRequest = null;
        }
        if (resignArmed != null && ctx.ticks() > resignUntil) {
            resignArmed = null;
        }
        if (ctx.ticks() % (clock.millis(record.position().whiteToMove()) < 10_000 ? 2 : 10) == 0) {
            updateBar();
        }
    }

    private void updateBar() {
        boolean whiteToMove = record.position().whiteToMove();
        Component text;
        if (phase != Phase.PLAYING) {
            long left = Math.max(0, awayUntil - ctx.ticks()) / 20;
            text = messages.get(phase == Phase.AWAY ? "bar.away" : "bar.claim",
                    Placeholder.unparsed("player", Objects.equals(away, white) ? whiteName : blackName),
                    Placeholder.unparsed("time", ChessClock.format(left * 1000)));
        } else if (clock.unlimited()) {
            text = messages.get(whiteToMove ? "bar.free-white" : "bar.free-black",
                    Placeholder.unparsed("white", whiteName), Placeholder.unparsed("black", blackName),
                    Placeholder.unparsed("move", String.valueOf(record.position().fullmove())));
        } else {
            text = messages.get(whiteToMove ? "bar.clock-white" : "bar.clock-black",
                    Placeholder.unparsed("white", whiteName), Placeholder.unparsed("black", blackName),
                    Placeholder.unparsed("white_time", ChessClock.format(clock.millis(true))),
                    Placeholder.unparsed("black_time", ChessClock.format(clock.millis(false))));
        }
        bar.name(text);
        bar.color(whiteToMove ? BossBar.Color.WHITE : BossBar.Color.PURPLE);
        float progress = clock.unlimited() ? 1f
                : (float) Math.min(1, (double) clock.millis(whiteToMove) / Math.max(1, clock.initialMillis()));
        bar.progress(Math.max(0f, progress));
    }

    /** The square the player looks at, or -1. */
    private int look(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        return geo.square(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(), settings.reach());
    }

    // ---------------------------------------------------------------- input

    /** A click (either button) with the select item or an empty hand. */
    public void click(Player p) {
        if (ended || ctx.ticks() - lastClick < CLICK_GAP_TICKS) {
            return;
        }
        lastClick = ctx.ticks();
        if (phase != Phase.PLAYING) {
            p.sendActionBar(messages.get("away.no-moves"));
            return;
        }
        boolean isWhite = p.getUniqueId().equals(white);
        if (isWhite != record.position().whiteToMove()) {
            p.sendActionBar(messages.get("not-your-turn"));
            return;
        }
        int sq = look(p);
        if (settings.debug()) {
            Location eye = p.getEyeLocation();
            game.plugin().getLogger().info(String.format(java.util.Locale.ROOT,
                    "[chess] click %s eye %.2f %.2f %.2f yaw %.1f pitch %.1f -> %s, selected %s",
                    p.getName(), eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                    sq < 0 ? "off the board" : Move.square(sq), selected < 0 ? "-" : Move.square(selected)));
        }
        if (sq < 0) {
            deselect();
            return;
        }
        if (selected >= 0) {
            List<Move> to = record.legalMovesFrom(selected).stream().filter(m -> m.to() == sq).toList();
            if (!to.isEmpty()) {
                if (to.getFirst().promotion() != 0) {
                    int from = selected;
                    PromotionMenu.open(p, messages, isWhite, type -> {
                        p.closeInventory();
                        if (selected == from && !ended && phase == Phase.PLAYING) {
                            play(new Move(from, sq, type));
                        }
                    });
                } else {
                    play(to.getFirst());
                }
                return;
            }
        }
        int piece = record.position().at(sq);
        if (piece != 0 && Piece.isWhite(piece) == isWhite && sq != selected) {
            List<Move> moves = record.legalMovesFrom(sq);
            if (moves.isEmpty()) {
                deselect();
                p.sendActionBar(messages.get("no-moves"));
                sound(p, "minecraft:block.note_block.bass", 0.6f, 0.8f);
                return;
            }
            selected = sq;
            view.select(sq, moves, record.position(), p);
            sound(p, "minecraft:block.wood.hit", 0.8f, 1.4f);
        } else {
            deselect();
        }
    }

    private void deselect() {
        selected = -1;
        view.deselect();
    }

    private void play(Move m) {
        Position before = record.position();
        Position.MoveEffect effect = before.effect(m);
        if (!record.play(m)) {
            return;
        }
        boolean whiteMoved = before.whiteToMove();
        if (record.moves().size() > 2) {
            clock.moved(whiteMoved);
        }
        selected = -1;
        drawOffer = null;
        Position after = record.position();
        view.move(m, effect, after);
        Player moverPlayer = Bukkit.getPlayer(whiteMoved ? white : black);
        if (effect.capturedSquare() >= 0 && moverPlayer != null) {
            ctx.addStat(moverPlayer, "captures", 1);
        }
        Location at = new Location(ctx.arena().world(), geo.centreX(m.to()), geo.topY(), geo.centreZ(m.to()));
        at.getWorld().playSound(Sound.sound(Key.key(effect.capturedSquare() >= 0
                ? "minecraft:entity.zombie.break_wooden_door" : "minecraft:block.wood.place"),
                Sound.Source.PLAYER, effect.capturedSquare() >= 0 ? 0.5f : 1f, 1f), at.getX(), at.getY(), at.getZ());
        if (after.inCheck() && !record.over()) {
            for (Player p : players()) {
                p.sendActionBar(messages.get("check"));
                sound(p, "minecraft:block.note_block.bell", 1f, 1.2f);
            }
        }
        save();
        updateBar();
        if (record.over()) {
            finishByBoard();
        }
    }

    private static void sound(Player p, String key, float volume, float pitch) {
        p.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch));
    }

    /** A hotbar button. */
    public void action(Player p, Hotbar.Action action) {
        if (ended) {
            return;
        }
        UUID id = p.getUniqueId();
        switch (action) {
            case SELECT -> click(p);
            case DRAW -> draw(p);
            case PAUSE -> pause(p);
            case RESIGN -> {
                if (phase != Phase.PLAYING) {
                    return;
                }
                if (id.equals(resignArmed)) {
                    resign(id);
                } else {
                    resignArmed = id;
                    resignUntil = ctx.ticks() + CONFIRM_TICKS;
                    p.sendActionBar(messages.get("resign.confirm"));
                }
            }
            case CLAIM -> claim(p);
            case KEEP -> keep(p);
        }
    }

    /** Offer a draw, or accept the opponent's offer. */
    public void draw(Player p) {
        if (ended || phase != Phase.PLAYING) {
            return;
        }
        UUID id = p.getUniqueId();
        if (drawOffer != null && !drawOffer.equals(id)) {
            decided = true;
            game.store().delete(saved.id());
            ctx.finish(MatchOutcome.draw(List.of(messages.get("result.agreed-draw"))));
            return;
        }
        if (id.equals(drawOffer)) {
            p.sendActionBar(messages.get("draw.already"));
            return;
        }
        drawOffer = id;
        p.sendMessage(messages.get("draw.offered"));
        opponent(id).ifPresent(o -> o.sendMessage(messages.get("draw.offer", Placeholder.unparsed("player", p.getName()))));
    }

    /** Ask to pause, or agree to the opponent's request. */
    public void pause(Player p) {
        if (ended || phase != Phase.PLAYING) {
            return;
        }
        UUID id = p.getUniqueId();
        if (pauseRequest != null && !pauseRequest.equals(id)) {
            pausedEnd(messages.get("result.paused-agreed"));
            return;
        }
        pauseRequest = id;
        pauseUntil = ctx.ticks() + REQUEST_TICKS;
        p.sendMessage(messages.get("pause.asked"));
        opponent(id).ifPresent(o -> o.sendMessage(messages.get("pause.request", Placeholder.unparsed("player", p.getName()))));
    }

    /** The player who stayed takes the win once the grace time is over. */
    public void claim(Player p) {
        if (ended || phase != Phase.CLAIM || p.getUniqueId().equals(away)) {
            return;
        }
        decided = true;
        game.store().delete(saved.id());
        int stayer = team(p.getUniqueId());
        ctx.finish(MatchOutcome.forfeit(stayer, 1 - stayer, List.of(messages.get("result.claimed",
                Placeholder.unparsed("player", nameOf(away))))));
    }

    /** The player who stayed keeps the game for later. */
    public void keep(Player p) {
        if (ended || phase != Phase.CLAIM || p.getUniqueId().equals(away)) {
            return;
        }
        pausedEnd(messages.get("result.kept", Placeholder.unparsed("player", nameOf(away))));
    }

    private java.util.Optional<Player> opponent(UUID id) {
        Player o = Bukkit.getPlayer(id.equals(white) ? black : white);
        return o != null && ctx.teamOf(o).isPresent() ? java.util.Optional.of(o) : java.util.Optional.empty();
    }

    private int team(UUID id) {
        return id.equals(white) ? 0 : 1;
    }

    private String nameOf(UUID id) {
        return Objects.equals(id, white) ? whiteName : blackName;
    }

    // ---------------------------------------------------------------- leaving and coming back

    @Override
    public boolean keepsRunningWhenAlone() {
        return true;
    }

    @Override
    public void playerLeft(Player player, int team) {
        if (ended) {
            return;
        }
        save();
        if (selected >= 0 && player.getUniqueId().equals(record.position().whiteToMove() ? white : black)) {
            deselect();
        }
        view.hover(-1, null);
        player.hideBossBar(bar);
        if (phase == Phase.PLAYING) {
            phase = Phase.AWAY;
            away = player.getUniqueId();
            awayUntil = ctx.ticks() + settings.awayGraceTicks();
            pauseRequest = null;
            drawOffer = null;
            resignArmed = null;
            opponent(away).ifPresent(o -> o.sendMessage(messages.get("away.left",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("seconds", String.valueOf(settings.awayGraceTicks() / 20)))));
            updateBar();
        } else {
            // Both gone: keep the game. (The core would pause it too.)
            pausedEnd(null);
        }
    }

    /** The player who left is back on the server: put them on their stand again. */
    public boolean rejoin(Player p) {
        if (ended || phase == Phase.PLAYING || !p.getUniqueId().equals(away)) {
            return false;
        }
        int team = team(p.getUniqueId());
        if (!ctx.rejoin(p, team)) {
            return false;
        }
        phase = Phase.PLAYING;
        away = null;
        lastNanos = System.nanoTime();
        p.teleport(ctx.arena().spawns(team).getFirst());
        for (Player each : players()) {
            if (each.equals(p)) {
                equip(each);
            } else {
                Hotbar.give(each, messages, false);
            }
            each.sendMessage(messages.get("away.back", Placeholder.unparsed("player", p.getName())));
        }
        updateBar();
        return true;
    }

    public boolean isAway(UUID player) {
        return !ended && phase != Phase.PLAYING && player.equals(away);
    }

    // ---------------------------------------------------------------- endings

    private void finishByBoard() {
        GameRecord.Status status = record.status();
        decided = true;
        game.store().delete(saved.id());
        int moves = record.moves().size();
        if (status == GameRecord.Status.CHECKMATE) {
            boolean whiteWon = !record.position().whiteToMove();
            Player winner = Bukkit.getPlayer(whiteWon ? white : black);
            if (winner != null) {
                ctx.addStat(winner, "checkmates", 1);
            }
            ctx.finish(MatchOutcome.win(whiteWon ? 0 : 1, List.of(messages.get("result.checkmate",
                    Placeholder.unparsed("moves", String.valueOf((moves + 1) / 2))))));
        } else {
            ctx.finish(MatchOutcome.draw(List.of(messages.get("result." + status.name().toLowerCase().replace('_', '-')))));
        }
    }

    private void flag(boolean whiteFlagged) {
        decided = true;
        game.store().delete(saved.id());
        updateBar();
        String loser = whiteFlagged ? whiteName : blackName;
        if (record.position().cannotMate(!whiteFlagged)) {
            ctx.finish(MatchOutcome.draw(List.of(messages.get("result.time-draw", Placeholder.unparsed("player", loser)))));
        } else {
            ctx.finish(MatchOutcome.win(whiteFlagged ? 1 : 0, List.of(messages.get("result.time",
                    Placeholder.unparsed("player", loser)))));
        }
    }

    private void resign(UUID who) {
        decided = true;
        game.store().delete(saved.id());
        ctx.finish(MatchOutcome.win(1 - team(who), List.of(messages.get("result.resigned",
                Placeholder.unparsed("player", nameOf(who))))));
    }

    /** Ends without a result; the game stays saved (if a move was made). */
    private void pausedEnd(Component line) {
        save();
        List<Component> lines = new ArrayList<>();
        if (line != null) {
            lines.add(line);
        }
        lines.add(record.moves().isEmpty() ? messages.get("result.nothing-saved") : messages.get("result.saved"));
        ctx.finish(MatchOutcome.paused(lines));
    }

    /** Writes the game to disk; a game without moves is not worth keeping. */
    private void save() {
        if (decided || record.moves().isEmpty()) {
            return;
        }
        saved = saved.withProgress(record.uciMoves(), clock.millis(true), clock.millis(false), System.currentTimeMillis());
        game.store().put(saved);
    }

    @Override
    public void end() {
        if (ended) {
            return;
        }
        save();
        ended = true;
        game.untrack(this);
        view.remove();
        for (UUID id : new UUID[] {white, black}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
                p.closeInventory();
            }
        }
        game.plugin().getLogger().info("[chess] game " + saved.id() + " ended after " + record.moves().size()
                + " moves: " + (decided ? record.status() : "saved"));
    }

    @Override
    public List<Component> resultLines() {
        return List.of();
    }
}
