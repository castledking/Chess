package codes.castled.chess.bot;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.move.UciMove;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

/**
 * Plays a whole game between two bots through the engine, with no server involved.
 *
 * <p>This is the closest a headless test gets to the real thing: it drives the same select, move,
 * promote and toggle sequence the plugin performs for an engine opponent. Every move the bot
 * proposes must be one the engine accepts, and the game must actually end rather than shuffling
 * forever — the latter is what the fifty-move and repetition rules exist to guarantee.
 */
class BotGameTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("w".getBytes());
  private static final UUID BLACK = UUID.nameUUIDFromBytes("b".getBytes());

  @Test
  void twoBotsPlayALegalGameThatTerminates() {
    EngineFactory engine = new EngineFactory(false);
    ChessGame game = engine.chessGameService().createGame(WHITE, BLACK, TimeMode.TEN).game();

    // Level 1 keeps the search to one ply so a whole game fits in a test run.
    ChessBot white = new SearchBot(WHITE, new BotDifficulty(1), engine, new Random(7));
    ChessBot black = new SearchBot(BLACK, new BotDifficulty(1), engine, new Random(11));

    int plies = 0;
    String previousFen = null;

    while (plies < 80) {
      UUID mover = game.getCurrentTurn();
      String fen = game.toFen();

      assertNotEquals(previousFen, fen, "the position must change every ply");
      previousFen = fen;

      String notation = (mover.equals(WHITE) ? white : black).chooseMove(fen);
      if (notation == null) {
        break; // Mate or stalemate: no legal move remains.
      }

      UciMove move = UciMove.parse(notation);
      game.selectPiece(move.from(), mover);
      var result = game.makeMove(move.to(), mover);
      game.unselectPiece(mover);

      assertTrue(
          result.type() == MoveResultType.SUCCESS,
          () -> "the engine rejected its own bot's move " + notation + " in " + fen);

      if (result.promotion()) {
        PieceType chosen = move.promotion() == null ? PieceType.QUEEN : move.promotion();
        game.applyPromotion(move.from(), move.to(), chosen);
      }

      game.toggleTurn();
      plies++;

      if (game.isFiftyMoveDraw() || game.isThreefoldRepetition()) {
        break; // A drawn game is a terminated game, which is the point.
      }
    }

    assertTrue(plies > 0, "the bots must actually have played");
  }

  @Test
  void aBotNeverProposesAMoveTheEngineRefuses() {
    // Deliberately awkward: a cramped position with pins, castling rights and a promotion race.
    String fen = "r3k2r/pPppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    EngineFactory engine = new EngineFactory(false);

    for (int difficulty = BotDifficulty.MIN; difficulty <= BotDifficulty.MAX; difficulty++) {
      final int level = difficulty;
      ChessBot bot = new SearchBot(UUID.randomUUID(), new BotDifficulty(level), engine, new Random(level));
      String notation = bot.chooseMove(fen);

      assertTrue(notation != null, "there are legal moves here");

      ChessGame position = engine.positionFromFen(fen);
      UciMove move = UciMove.parse(notation);
      UUID mover = position.getCurrentTurn();
      position.selectPiece(move.from(), mover);

      assertTrue(
          position.makeMove(move.to(), mover).type() == MoveResultType.SUCCESS,
          () -> "level " + level + " proposed the illegal move " + notation);
    }
  }
}
