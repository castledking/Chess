package codes.castled.chess.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.wiring.EngineFactory;
import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Covers the premove feature: the blue premove glyph codepoints (the contract between
 * {@link PieceGlyph} and the resource pack font, board.json) and the play-through mechanics
 * {@code ChessGameHolder} executes for a queued premove. The holder itself cannot be
 * instantiated without a server, so the engine steps it performs are driven directly.
 */
class PremoveTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));

  /* Glyph codepoint contract ------------------------------------------- */

  @Test
  void premoveGlyphsResolveToTheBlueSquarePlanes() {
    PieceGlyph glyph = new PieceGlyph(true);
    Piece whitePawn = new Piece(PieceType.PAWN, PieceColor.WHITE);
    Piece blackKnight = new Piece(PieceType.KNIGHT, PieceColor.BLACK);

    // base + 0x900 = light premove plane, base + 0xA00 = dark premove plane.
    assertTrue(glyph.forPiece(whitePawn, PieceGlyph.Highlight.PREMOVE, true).contains("\uE901"));
    assertTrue(glyph.forPiece(whitePawn, PieceGlyph.Highlight.PREMOVE, false).contains("\uEA01"));
    assertTrue(glyph.forPiece(blackKnight, PieceGlyph.Highlight.PREMOVE, true).contains("\uE909"));
    assertTrue(glyph.forPiece(blackKnight, PieceGlyph.Highlight.PREMOVE, false).contains("\uEA09"));
    // Sanity: the untouched planes still resolve where they always did.
    assertTrue(glyph.forPiece(whitePawn, PieceGlyph.Highlight.NONE, true).contains("\uE301"));
    assertTrue(glyph.forPiece(whitePawn, PieceGlyph.Highlight.SELECTED, true).contains("\uE501"));
    assertTrue(glyph.forPiece(whitePawn, PieceGlyph.Highlight.LEGAL, true).contains("\uE701"));

    // Empty premove tiles live next to the other empty highlight tiles.
    assertTrue(glyph.forEmpty(true, PieceGlyph.Highlight.PREMOVE).contains("\uE018"));
    assertTrue(glyph.forEmpty(false, PieceGlyph.Highlight.PREMOVE).contains("\uE019"));
  }

  @Test
  void premoveGlyphsFallBackWithoutTheResourcePack() {
    PieceGlyph glyph = new PieceGlyph(false);
    Piece whitePawn = new Piece(PieceType.PAWN, PieceColor.WHITE);
    assertEquals("♙", glyph.forPiece(whitePawn, PieceGlyph.Highlight.PREMOVE, true));
    assertEquals("☐", glyph.forEmpty(true, PieceGlyph.Highlight.PREMOVE));
  }

  /* Play-through mechanics --------------------------------------------- */

  @Test
  void queuedPremoveIsPlayedOnceItsPlayersTurnArrives() {
    EngineFactory engine = new EngineFactory(false);
    MoveCalculator calculator = engine.moveCalculator();
    ChessGame game = newGame(engine);

    // 1. e4 — white moves; it becomes black's turn.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    ChessBoard board = game.getChessBoard();

    // Black queues a premove Nf6 (g8 -> f6) while it is NOT their turn. Queueing only reads
    // the move calculator and stores the from/to pair; the board must not change yet.
    Square g8 = new Square('8', 'G');
    Square f6 = new Square('6', 'F');
    assertTrue(calculator.getPossibleMoves(game, g8).contains(f6));
    assertEquals(PieceType.KNIGHT, board.getPiece(g8).type());
    assertEquals(null, board.getPiece(f6));

    // 2. d4 — white moves; the turn flips back to black, so the premove fires.
    move(game, WHITE, new Square('2', 'D'), new Square('4', 'D'));

    // What the plugin's playPremoveIfQueued then executes: select, makeMove, unselect, and
    // finishMove's toggleTurn.
    playPremove(game, BLACK, g8, f6);

    assertEquals(PieceType.KNIGHT, board.getPiece(f6).type());
    assertEquals(PieceColor.BLACK, board.getPiece(f6).color());
    assertEquals(null, board.getPiece(g8));
  }

  @Test
  void capturePremovePlaysOnceTheTurnArrives() {
    EngineFactory engine = new EngineFactory(false);
    MoveCalculator calculator = engine.moveCalculator();
    ChessGame game = newGame(engine);

    // 1. e4 e5 2. Nf3 — the white knight now attacks the e5 pawn.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'E'), new Square('5', 'E'));
    move(game, WHITE, new Square('1', 'G'), new Square('3', 'F'));
    assertEquals(PieceColor.BLACK, game.getColor(BLACK));

    // White queues a capture premove Nf3xe5 while it is black's turn.
    Square f3 = new Square('3', 'F');
    Square e5 = new Square('5', 'E');
    assertTrue(calculator.getPossibleMoves(game, f3).contains(e5));

    // 2... Nc6 — black moves; white's premove then captures the e5 pawn.
    move(game, BLACK, new Square('8', 'B'), new Square('6', 'C'));
    playPremove(game, WHITE, f3, e5);

    assertEquals(PieceType.KNIGHT, game.getChessBoard().getPiece(e5).type());
    assertEquals(PieceColor.WHITE, game.getChessBoard().getPiece(e5).color());
    assertEquals(null, game.getChessBoard().getPiece(f3));
  }

  @Test
  void premoveThatBecomesIllegalIsDiscarded() {
    EngineFactory engine = new EngineFactory(false);
    MoveCalculator calculator = engine.moveCalculator();
    ChessGame game = newGame(engine);

    // 1. e4 e5 2. Qh5 — the queen lands on h5, black to move.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'E'), new Square('5', 'E'));
    move(game, WHITE, new Square('1', 'D'), new Square('5', 'H'));
    assertEquals(PieceColor.BLACK, game.getColor(BLACK));

    // White queues a capture premove Qh5xf7 while it is black's turn.
    Square h5 = new Square('5', 'H');
    Square f7 = new Square('7', 'F');
    assertTrue(calculator.getPossibleMoves(game, h5).contains(f7));

    // 2... g6 kicks the queen: the g6 pawn blocks the h5-f7 diagonal, so when the turn
    // returns to white the premove is no longer legal and the plugin discards it.
    move(game, BLACK, new Square('7', 'G'), new Square('6', 'G'));
    assertFalse(
        calculator.getPossibleMoves(game, h5).contains(f7),
        "Qh5xf7 must be blocked by the g6 pawn");

    // What the plugin's playPremoveIfQueued then executes: the engine rejects the move, so the
    // premove is silently dropped and the position is untouched.
    game.selectPiece(h5, WHITE);
    assertEquals(MoveResultType.ILLEGAL, game.makeMove(f7, WHITE).type());
    game.unselectPiece(WHITE);
    assertEquals(PieceColor.WHITE, game.getChessBoard().getPiece(h5).color());
    assertEquals(PieceType.PAWN, game.getChessBoard().getPiece(f7).type());
  }

  @Test
  void eightPawnPremoveStormFillsTheFourthAndFifthRanks() {
    EngineFactory engine = new EngineFactory(false);
    ChessGame game = newGame(engine);
    ChessBoard board = game.getChessBoard();

    // 1. e4 is played normally (the game starts on white's turn, so it cannot be a premove);
    // a5 answers. White then stacks the other seven double-pushes as premoves in one go,
    // and black answers each one by double-pushing another pawn, so no file interferes.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'A'), new Square('5', 'A'));

    PremoveQueue queue = new PremoveQueue(game, engine.moveCalculator(), WHITE);
    char[] files = {'D', 'C', 'B', 'A', 'F', 'G', 'H'};
    for (char file : files) {
      queue.queue(new Square('2', file), new Square('4', file));
    }

    char[] blackFiles = {'B', 'C', 'D', 'E', 'F', 'G', 'H'};
    for (char blackFile : blackFiles) {
      move(game, BLACK, new Square('7', blackFile), new Square('5', blackFile));
      queue.playIfQueued(); // one stacked premove fires per turn
    }

    // Ranks 4 and 5 are each fully occupied by pawns of one color, all 16 still on the board.
    for (char file = 'A'; file <= 'H'; file++) {
      Piece whitePawn = board.getPiece(new Square('4', file));
      assertEquals(PieceType.PAWN, whitePawn.type(), "white pawn missing on " + file + "4");
      assertEquals(PieceColor.WHITE, whitePawn.color());
      Piece blackPawn = board.getPiece(new Square('5', file));
      assertEquals(PieceType.PAWN, blackPawn.type(), "black pawn missing on " + file + "5");
      assertEquals(PieceColor.BLACK, blackPawn.color());
      assertEquals(null, board.getPiece(new Square('2', file)));
      assertEquals(null, board.getPiece(new Square('7', file)));
    }
    assertFalse(queue.hasQueued());
  }

  @Test
  void scholarsMateByPremove() {
    EngineFactory engine = new EngineFactory(false);
    MoveCalculator calculator = engine.moveCalculator();
    ChessGame game = newGame(engine);
    ChessBoard board = game.getChessBoard();

    // 1. e4 is played normally (white cannot premove on the opening turn); a6 answers. White
    // then stacks the whole mating sequence — Qf3, Bc4, Qxf7# — in one queue. Only Qf3 is
    // legal in the current position; the other two are speculative and validated at fire
    // time. Black single-steps the a-pawn a6, a5, a4, a3, so black does reach a3 before the
    // final premove delivers checkmate.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'A'), new Square('6', 'A'));

    PremoveQueue queue = new PremoveQueue(game, calculator, WHITE);
    queue.queue(new Square('1', 'D'), new Square('3', 'F')); // Qf3 — legal at queue time
    queue.queue(new Square('1', 'F'), new Square('4', 'C')); // Bc4 — speculative
    queue.queue(new Square('3', 'F'), new Square('7', 'F')); // Qxf7# — speculative

    move(game, BLACK, new Square('6', 'A'), new Square('5', 'A')); // ...a5, then 2. Qf3
    queue.playIfQueued();
    move(game, BLACK, new Square('5', 'A'), new Square('4', 'A')); // ...a4, then 3. Bc4
    queue.playIfQueued();
    move(game, BLACK, new Square('4', 'A'), new Square('3', 'A')); // ...a3, then 4. Qxf7#
    queue.playIfQueued();

    // The engine stub never populates MoveResult.checkmate, so mate is detected the way the
    // plugin detects it: GameStatusEvaluator on the final position.
    GameStatusEvaluator status = new GameStatusEvaluator(calculator);
    assertTrue(status.isCheckmate(game, BLACK), "Qxf7# must be checkmate");
    assertFalse(status.hasPossibleMoves(game, BLACK));

    assertEquals(PieceType.KING, board.getPiece(new Square('1', 'E')).type());
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('1', 'E')).color());
    assertEquals(PieceType.QUEEN, board.getPiece(new Square('7', 'F')).type());
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('7', 'F')).color());
    assertEquals(PieceType.BISHOP, board.getPiece(new Square('4', 'C')).type());
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('4', 'C')).color());
    assertEquals(PieceType.KING, board.getPiece(new Square('8', 'E')).type());
    assertEquals(PieceColor.BLACK, board.getPiece(new Square('8', 'E')).color());
    // Black's last played move landed the a-pawn on a3.
    assertEquals(PieceType.PAWN, board.getPiece(new Square('3', 'A')).type());
    assertEquals(PieceColor.BLACK, board.getPiece(new Square('3', 'A')).color());
    assertFalse(queue.hasQueued());
  }

  @Test
  void premoveStackDiscardsEverythingWhenTheHeadBecomesIllegal() {
    EngineFactory engine = new EngineFactory(false);
    ChessGame game = newGame(engine);
    ChessBoard board = game.getChessBoard();

    // 1. e4 a6, then white stacks d4 and d4-d5. Black's ...d5 lands a pawn on d5, so the
    // stack's head (d4) still fires, but the follow-up d4-d5 now pushes into an occupied
    // square: the whole queue is discarded, not skipped past.
    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'A'), new Square('6', 'A'));

    PremoveQueue queue = new PremoveQueue(game, engine.moveCalculator(), WHITE);
    queue.queue(new Square('2', 'D'), new Square('4', 'D'));
    queue.queue(new Square('4', 'D'), new Square('5', 'D'));

    move(game, BLACK, new Square('7', 'D'), new Square('5', 'D')); // ...d5, then 2. d4 fires
    queue.playIfQueued();
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('4', 'D')).color());
    assertTrue(queue.hasQueued());

    move(game, BLACK, new Square('6', 'A'), new Square('5', 'A')); // ...a5, then d4-d5 is dead
    queue.playIfQueued();
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('4', 'D')).color());
    assertEquals(PieceColor.BLACK, board.getPiece(new Square('5', 'D')).color());
    assertFalse(queue.hasQueued(), "an illegal head must discard the whole queue");

    // Nothing is left to fire on later turns.
    move(game, BLACK, new Square('5', 'A'), new Square('4', 'A'));
    queue.playIfQueued();
    assertEquals(PieceColor.WHITE, board.getPiece(new Square('4', 'D')).color());
  }

  /* Helpers ------------------------------------------------------------ */

  /**
   * Test replica of {@code ChessGameHolder}'s stacked premove queue: the first entry is
   * validated against the current position when queued, later entries are speculative, and
   * only the head fires per turn — re-validated against the position it actually meets. An
   * illegal head discards the whole queue rather than skipping to the next entry.
   */
  private static final class PremoveQueue {
    private final ChessGame game;
    private final MoveCalculator calculator;
    private final UUID player;
    private final java.util.ArrayDeque<Move> queue = new java.util.ArrayDeque<>();

    PremoveQueue(ChessGame game, MoveCalculator calculator, UUID player) {
      this.game = game;
      this.calculator = calculator;
      this.player = player;
    }

    void queue(Square from, Square to) {
      if (queue.isEmpty()) {
        assertTrue(
            calculator.getPossibleMoves(game, from).contains(to),
            () -> "first premove " + from + "-" + to + " must be legal when queued");
      }
      queue.addLast(new Move(null, from, to));
    }

    void playIfQueued() {
      if (queue.isEmpty()) {
        return;
      }
      Move head = queue.peekFirst();
      Piece fromPiece = game.getChessBoard().getPiece(head.from());
      if (fromPiece == null
          || fromPiece.color() != game.getColor(player)
          || !calculator.getPossibleMoves(game, head.from()).contains(head.to())) {
        queue.clear();
        return;
      }
      queue.pollFirst();
      playPremove(game, player, head.from(), head.to());
    }

    boolean hasQueued() {
      return !queue.isEmpty();
    }
  }

  private static ChessGame newGame(EngineFactory engine) {
    GameCreationResult result = engine.chessGameService().createGame(WHITE, BLACK, TimeMode.TEN);
    assertEquals(GameCreationResultType.SUCCESS, result.type());
    return result.game();
  }

  private static void move(ChessGame game, UUID player, Square from, Square to) {
    game.selectPiece(from, player);
    MoveResult result = game.makeMove(to, player);
    assertEquals(
        MoveResultType.SUCCESS,
        result.type(),
        () -> "move " + from + "-" + to + " must be legal");
    game.toggleTurn();
  }

  /** The exact sequence {@code ChessGameHolder.playPremoveIfQueued} runs for a legal premove. */
  private static void playPremove(ChessGame game, UUID player, Square from, Square to) {
    game.selectPiece(from, player);
    assertEquals(MoveResultType.SUCCESS, game.makeMove(to, player).type());
    game.unselectPiece(player);
    game.toggleTurn();
  }
}