package codes.castled.chess.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.common.board.FenCodec;
import codes.castled.chess.engine.common.move.UciMove;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

/**
 * Covers the built-in bot: that it returns legal moves, that it sees the obvious, and that it is
 * honest about having nothing to play.
 *
 * <p>The bot receives a FEN and answers with UCI notation, so these tests are also an end-to-end
 * check of the interchange layer — a bot that reasoned about a mis-loaded position would fail here
 * rather than in a live game.
 */
class SearchBotTest {

  /** Fixed seed so a blundering level is reproducible rather than flaky. */
  private static final long SEED = 1234;

  @Test
  void theBotAlwaysReturnsALegalMoveFromTheStartingPosition() {
    for (int level = BotDifficulty.MIN; level <= BotDifficulty.MAX; level++) {
      String move = bot(level).chooseMove(FenCodec.START);

      assertNotNull(move, () -> "level must produce a move");
      assertTrue(isLegal(FenCodec.START, move), () -> "level produced an illegal move: " + move);
    }
  }

  @Test
  void aStrongLevelTakesAFreeQueen() {
    // Black queen on d5 is hanging to the pawn on e4; nothing defends it.
    String fen = "rnb1kbnr/ppp1pppp/8/3q4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1";

    String move = bot(BotDifficulty.MAX).chooseMove(fen);

    assertEquals("e4d5", move, "a free queen is the whole point of a search");
  }

  @Test
  void aStrongLevelPlaysMateInOne() {
    // Back-rank mate: the rook drops to a8 and the king has no escape.
    String fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1";

    String move = bot(BotDifficulty.MAX).chooseMove(fen);

    assertEquals("a1a8", move, "mate in one must outrank everything else");
  }

  @Test
  void theBotPromotesToAQueen() {
    // The only sensible move is pushing the pawn home.
    String fen = "8/P6k/8/8/8/8/7K/8 w - - 0 1";

    String move = bot(BotDifficulty.MAX).chooseMove(fen);

    assertEquals("a7a8q", move);
    assertTrue(isLegal(fen, move));
  }

  @Test
  void aFinishedPositionYieldsNoMove() {
    // Black is mated and has nothing to play.
    String fen = "6k1/5ppp/8/8/8/8/8/R5K1 b - - 1 1";
    ChessGame position = new EngineFactory(false).positionFromFen(fen);

    // Sanity: black really does have moves here, so the next case is the meaningful one.
    assertTrue(position.getChessBoard().getColoredPieces(
        codes.castled.chess.engine.api.piece.PieceColor.BLACK).size() > 0);

    // A board with only a king that cannot move: stalemate, so no legal move exists.
    assertNull(bot(BotDifficulty.MAX).chooseMove("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"));
  }

  @Test
  void everyLevelStaysWithinItsThinkingBudget() {
    // The whole scale must stay interactive; a level that blows the budget would stall a game.
    String midgame = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4";

    for (int level = BotDifficulty.MIN; level <= BotDifficulty.MAX; level++) {
      long start = System.currentTimeMillis();
      String move = bot(level).chooseMove(midgame);
      long elapsed = System.currentTimeMillis() - start;

      assertNotNull(move);
      assertTrue(elapsed < 4_000, () -> "a level took " + elapsed + "ms, which a player will feel");
    }
  }

  @Test
  void difficultyRejectsLevelsOutsideTheScale() {
    assertNull(BotDifficulty.parse("0"));
    assertNull(BotDifficulty.parse("11"));
    assertNull(BotDifficulty.parse("hard"));
    assertEquals(7, BotDifficulty.parse("7").level());
    assertEquals(7, BotDifficulty.parse(" 7 ").level());
  }

  @Test
  void weakLevelsErrMoreOftenThanStrongOnes() {
    // Not that level 1 is bad at chess, but that the scale actually varies.
    assertTrue(new BotDifficulty(1).blunderChance() > new BotDifficulty(5).blunderChance());
    assertTrue(new BotDifficulty(5).blunderChance() > new BotDifficulty(9).blunderChance());
    assertEquals(0.0, new BotDifficulty(10).blunderChance(), "the top level plays its best move");
    assertTrue(new BotDifficulty(10).searchDepth() > new BotDifficulty(1).searchDepth());
  }

  /* Helpers ----------------------------------------------------------- */

  private static SearchBot bot(int level) {
    return new SearchBot(
        UUID.randomUUID(), new BotDifficulty(level), new EngineFactory(false), new Random(SEED));
  }

  /** Replays the move onto a fresh copy of the position to prove the engine accepts it. */
  private static boolean isLegal(String fen, String notation) {
    ChessGame position = new EngineFactory(false).positionFromFen(fen);
    UciMove move = UciMove.parse(notation);
    UUID mover = position.getCurrentTurn();

    position.selectPiece(move.from(), mover);
    return position.makeMove(move.to(), mover).type() == MoveResultType.SUCCESS;
  }
}
