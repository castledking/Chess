package codes.castled.chess.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.FenCodec;
import codes.castled.chess.engine.common.move.UciMove;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Covers loading a position back out of a FEN, and the UCI move notation a chess engine speaks.
 *
 * <p>These are the two halves of handing a position to something outside this JVM and getting a
 * move back. A fault here does not throw — it silently reasons about a different position than
 * the one being played, so the round trip is asserted rather than assumed.
 */
class FenRoundTripTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));

  /* Position loading -------------------------------------------------- */

  @Test
  void aLoadedPositionWritesBackTheFenItCameFrom() {
    String[] positions = {
      FenCodec.START,
      // Kasparov-Topalov after 24...Rd8, an ordinary middlegame.
      "1r2k2r/p1q1nppp/2p1p3/3pP3/3P4/2PB1N2/P1Q2PPP/R3K2R w KQk - 4 18",
      // No castling rights left at all.
      "8/5k2/8/8/3K4/8/8/8 b - - 12 45",
      // Black to move with an en passant target standing.
      "rnbqkbnr/pp1ppppp/8/8/2pPP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 3",
      // Only the queen side survives for white.
      "r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 20",
    };

    for (String fen : positions) {
      assertEquals(fen, load(fen).toFen(), () -> "round trip failed for " + fen);
    }
  }

  @Test
  void aLoadedPositionOffersTheSameMovesAsOneReachedByPlaying() {
    // Reach a position by playing, then reload its FEN and compare legal moves.
    ChessGame played = newGame();
    move(played, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(played, BLACK, new Square('7', 'E'), new Square('5', 'E'));
    move(played, WHITE, new Square('1', 'G'), new Square('3', 'F'));

    ChessGame loaded = load(played.toFen());
    MoveCalculator calculator = new EngineFactory(false).moveCalculator();

    for (char row = '1'; row <= '8'; row++) {
      for (char column = 'A'; column <= 'H'; column++) {
        Square square = new Square(row, column);
        assertEquals(
            calculator.getPossibleMoves(played, square),
            calculator.getPossibleMoves(loaded, square),
            () -> "moves differ from " + square);
      }
    }
  }

  @Test
  void aLoadedEnPassantTargetIsActuallyCapturable() {
    // White has just played d2-d4; black's c4 pawn may take en passant on d3.
    ChessGame game = load("rnbqkbnr/pp1ppppp/8/8/2pPP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 3");
    MoveCalculator calculator = new EngineFactory(false).moveCalculator();

    assertTrue(
        calculator.getPossibleMoves(game, new Square('4', 'C')).contains(new Square('3', 'D')),
        "the en passant capture must survive the round trip");
  }

  @Test
  void castlingRightsComeFromTheFenNotFromWhereTheRooksHappenToBe() {
    // Both rooks are home, but the FEN grants white only the queen side.
    ChessGame game = load("r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 20");
    MoveCalculator calculator = new EngineFactory(false).moveCalculator();

    var kingMoves = calculator.getPossibleMoves(game, new Square('1', 'E'));

    assertTrue(kingMoves.contains(new Square('1', 'C')), "queen side is granted");
    assertTrue(!kingMoves.contains(new Square('1', 'G')), "king side was not granted");
  }

  /* UCI notation ------------------------------------------------------ */

  @Test
  void uciMovesRoundTrip() {
    for (String notation : new String[] {"e2e4", "a7a8q", "h1h8", "b7c8n", "e1g1"}) {
      assertEquals(notation, UciMove.parse(notation).notation());
    }
  }

  @Test
  void uciPromotionsCarryTheChosenPiece() {
    assertEquals(PieceType.QUEEN, UciMove.parse("a7a8q").promotion());
    assertEquals(PieceType.KNIGHT, UciMove.parse("b7c8n").promotion());
    assertEquals(new Square('7', 'A'), UciMove.parse("a7a8q").from());
    assertEquals(new Square('8', 'A'), UciMove.parse("a7a8q").to());
    assertEquals(null, UciMove.parse("e2e4").promotion());
  }

  @Test
  void malformedUciMovesAreRejected() {
    for (String bad : new String[] {"e2", "e2e4qq", "z2e4", "e9e4", "e2e4x", ""}) {
      assertThrows(
          IllegalArgumentException.class, () -> UciMove.parse(bad), () -> "should reject " + bad);
    }
  }

  /* Helpers ----------------------------------------------------------- */

  private static ChessGame load(String fen) {
    return new EngineFactory(false).positionFromFen(fen);
  }

  private static ChessGame newGame() {
    return new EngineFactory(false)
        .chessGameService()
        .createGame(WHITE, BLACK, TimeMode.TEN)
        .game();
  }

  private static void move(ChessGame game, UUID player, Square from, Square to) {
    game.selectPiece(from, player);
    assertEquals(MoveResultType.SUCCESS, game.makeMove(to, player).type());
    game.unselectPiece(player);
    game.toggleTurn();
  }
}
