package codes.castled.chess.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.EasterEggRules;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.common.move.UciMove;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Pins that a CPU opponent under the 1500s rules knows when its King's Leap is spent.
 *
 * <p>A bot works from a FEN snapshot, and FEN has no field for the leap. Told only the FEN, a bot
 * that had already leapt would believe it could leap again, choose a move the live game refuses,
 * and be resigned for it — losing a game on its own with nobody having beaten it.
 */
class KingsLeapBotTest {

  /** White to move. The undefended black queen on f3 is a knight's move from the king on e1. */
  private static final String QUEEN_HANGS_TO_A_LEAP = "k7/8/8/8/8/5q2/8/4K3 w - - 0 1";

  private static final String LEAP_CAPTURE = "e1f3";

  private final EngineFactory engine = new EngineFactory(EasterEggRules.STANDARD.with1500sRules());

  @Test
  void anUnspentLeapIsPlayedWhenItWinsTheQueen() {
    // Proves the bot does reach for the leap here, so the spent case below is not vacuous.
    assertEquals(
        LEAP_CAPTURE, strongestBot().chooseMove(QUEEN_HANGS_TO_A_LEAP, EnumSet.noneOf(PieceColor.class)));
  }

  @Test
  void aSpentLeapIsNeverChosenEvenWhenItWouldWinTheQueen() {
    Set<PieceColor> spent = EnumSet.of(PieceColor.WHITE);

    String move = strongestBot().chooseMove(QUEEN_HANGS_TO_A_LEAP, spent);

    assertNotNull(move, "the king can still step to d2, so there is a move to find");
    assertNotEquals(LEAP_CAPTURE, move, "the leap is spent and the live game would refuse it");

    // And the live game, which does know the leap is spent, accepts what the bot chose.
    ChessGame live = engine.positionFromFen(QUEEN_HANGS_TO_A_LEAP);
    live.markKingsLeapUsed(PieceColor.WHITE);
    UciMove parsed = UciMove.parse(move);
    UUID mover = live.getCurrentTurn();
    live.selectPiece(parsed.from(), mover);
    assertEquals(
        MoveResultType.SUCCESS,
        live.makeMove(parsed.to(), mover).type(),
        () -> "the live game refused " + move + ", which would resign the bot");
  }

  /** Level 10 never errs on purpose, so the best move is the one returned. */
  private SearchBot strongestBot() {
    return new SearchBot(UUID.randomUUID(), new BotDifficulty(BotDifficulty.MAX), engine, new Random(1));
  }
}
