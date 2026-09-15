package codes.castled.chess.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.EasterEggRules;
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
import codes.castled.chess.engine.common.board.CastlingStatus;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.game.ChessGameImpl;
import codes.castled.chess.game.PremoveMoveCalculator;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Covers the 1500s rules easter egg: no castling at all (the vertical castling egg included),
 * pawns that advance a single square only, no en passant, and one King's Leap per game — a
 * single knight move each king may make.
 *
 * <p>Positions are built by clearing the standard start position and placing only the pieces the
 * case needs, because the engine has no FEN parser.
 */
class Rules1500sTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));

  private static final Square E1 = new Square('1', 'E');
  private static final Square E2 = new Square('2', 'E');
  private static final Square E3 = new Square('3', 'E');
  private static final Square E4 = new Square('4', 'E');
  private static final Square E5 = new Square('5', 'E');
  private static final Square E8 = new Square('8', 'E');
  private static final Square D5 = new Square('5', 'D');
  private static final Square D6 = new Square('6', 'D');
  private static final Square D7 = new Square('7', 'D');
  private static final Square H1 = new Square('1', 'H');
  private static final Square F3 = new Square('3', 'F');

  /* Castling ------------------------------------------------------------ */

  @Test
  void castlingIsNotOfferedEvenWhenVerticalCastlingIsAlsoEnabled() {
    // Both eggs on: the 1500s rules win and neither form of castling survives.
    Fixture fixture = emptyBoard(new EasterEggRules(true, true));
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.placeUnmovedRook(E8, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    List<Square> moves = fixture.movesFrom(E1);

    assertFalse(moves.contains(new Square('1', 'G')), "standard castling must be gone");
    assertFalse(moves.contains(E3), "vertical castling must be gone with it");
  }

  /* Pawns --------------------------------------------------------------- */

  @Test
  void pawnsAdvanceASingleSquareOnly() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E2, PieceType.PAWN, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);

    List<Square> moves = fixture.movesFrom(E2);

    assertTrue(moves.contains(E3), "the single step stays");
    assertFalse(moves.contains(E4), "the double step is gone");
  }

  @Test
  void enPassantIsNeverAvailable() {
    Fixture withEgg = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    assertEnPassantAvailability(withEgg, false);

    Fixture withoutEgg = emptyBoard(EasterEggRules.STANDARD);
    assertEnPassantAvailability(withoutEgg, true);
  }

  /**
   * Sets up a white pawn on e5 beside a black pawn that has just arrived on d5 "by double
   * step", then checks whether d6 is offered.
   *
   * <p>The double advance cannot actually be played under the 1500s rules, so the position is
   * impossible there — which is rather the point: the capture is refused even against a move
   * the engine did generate itself.
   */
  private void assertEnPassantAvailability(Fixture fixture, boolean expectAvailable) {
    fixture.place(E5, PieceType.PAWN, PieceColor.WHITE);
    fixture.place(D5, PieceType.PAWN, PieceColor.BLACK);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.playedLastMove(
        new Move(new Piece(PieceType.PAWN, PieceColor.BLACK), D7, D5));

    assertEquals(expectAvailable, fixture.movesFrom(E5).contains(D6));
  }

  /* The King's Leap ------------------------------------------------------ */

  @Test
  void theKingsLeapOffersKnightSquaresAndCaptures() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(F3, PieceType.KNIGHT, PieceColor.BLACK);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    List<Square> moves = fixture.movesFrom(E1);

    assertTrue(moves.contains(F3), "a leap may capture");
    assertTrue(moves.contains(new Square('2', 'G')), "every free knight square is offered");
    assertTrue(moves.contains(new Square('1', 'F')), "ordinary steps remain");
  }

  @Test
  void theKingsLeapMayNotLandOnYourOwnPiecesOrNextToTheEnemyKing() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(new Square('2', 'C'), PieceType.PAWN, PieceColor.WHITE);
    fixture.place(new Square('3', 'H'), PieceType.KING, PieceColor.BLACK);

    List<Square> moves = fixture.movesFrom(E1);

    assertFalse(moves.contains(new Square('2', 'C')), "own pieces may not be leapt onto");
    assertFalse(
        moves.contains(new Square('2', 'G')),
        "g2 stands next to the black king on h3, so a leap there is refused");
  }

  @Test
  void theKingsLeapIsHeldToTheSelfCheckRule() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    // The rook on e8 checks along the file; the one on f8 covers the f3 escape, so the leap
    // would land straight into an attack and is refused like any other king move.
    fixture.place(E8, PieceType.ROOK, PieceColor.BLACK);
    fixture.place(new Square('8', 'F'), PieceType.ROOK, PieceColor.BLACK);
    fixture.place(new Square('7', 'A'), PieceType.KING, PieceColor.BLACK);

    List<Square> moves = fixture.movesFrom(E1);

    assertFalse(moves.contains(F3), "the leap may not land on an attacked square");
    assertTrue(moves.contains(new Square('1', 'D')), "stepping off the file still escapes");
  }

  @Test
  void theKingsLeapMayBeMadeOncePerGame() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    assertTrue(fixture.movesFrom(E1).contains(F3));

    fixture.move(WHITE, E1, F3);
    fixture.move(BLACK, E8, new Square('7', 'E'));

    List<Square> moves = fixture.movesFrom(F3);

    assertFalse(moves.contains(new Square('4', 'H')), "the leap is spent");
    assertFalse(moves.contains(new Square('5', 'E')));
    assertFalse(moves.contains(new Square('2', 'D')));
    assertTrue(moves.contains(new Square('4', 'F')), "ordinary steps remain");
  }

  /* Premovability -------------------------------------------------------- */

  @Test
  void castlingIsNeverPremovableButTheLeapTakesItsPlaceWhileUnspent() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    List<Square> premoves = fixture.premovesFrom(E1);

    assertFalse(premoves.contains(new Square('1', 'G')), "castling has no premove here");
    assertTrue(premoves.contains(F3), "the unspent leap is premovable");
  }

  @Test
  void theLeapStopsBeingPremovableOnceSpent() {
    Fixture fixture = emptyBoard(EasterEggRules.STANDARD.with1500sRules());
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    fixture.move(WHITE, E1, F3);
    fixture.move(BLACK, E8, new Square('7', 'E'));

    assertFalse(fixture.premovesFrom(F3).contains(new Square('4', 'H')));
  }

  /* Fixture -------------------------------------------------------------- */

  private Fixture emptyBoard(EasterEggRules rules) {
    EngineFactory engine = new EngineFactory(rules);
    GameCreationResult result =
        engine.chessGameService().createGame(WHITE, BLACK, TimeMode.TEN);
    assertEquals(GameCreationResultType.SUCCESS, result.type());

    Fixture fixture = new Fixture(result.game(), engine.moveCalculator());
    fixture.clear();
    return fixture;
  }

  /** A game whose board has been emptied so a specific position can be placed on it. */
  private static final class Fixture {

    private final ChessGame game;
    private final MoveCalculator calculator;
    private final ChessBoardImpl board;

    private Fixture(ChessGame game, MoveCalculator calculator) {
      this.game = game;
      this.calculator = calculator;
      this.board = (ChessBoardImpl) game.getChessBoard();
    }

    private void clear() {
      for (char column = 'A'; column <= 'H'; column++) {
        for (char row = '1'; row <= '8'; row++) {
          board.setPiece(new Square(row, column), null);
        }
      }
      // Emptying the board leaves the start position's castling rights behind; drop the rooks
      // that no longer exist so each case starts from exactly what it places.
      for (PieceColor color : PieceColor.values()) {
        for (Square square : game.getUnmovedRookSquares(color)) {
          castlingStatus().markRookMoved(color, square);
        }
      }
    }

    /** Places a piece without granting it any castling rights. */
    private void place(Square square, PieceType type, PieceColor color) {
      board.setPiece(square, new Piece(type, color));
    }

    /** Places a rook that counts as never having moved, so it may be castled with. */
    private void placeUnmovedRook(Square square, PieceColor color) {
      place(square, PieceType.ROOK, color);
      castlingStatus().markRookUnmoved(color, square);
    }

    private CastlingStatus castlingStatus() {
      return ((ChessGameImpl) game).getCastlingStatus();
    }

    /** Records the move that has "just" been played, as the board sees it. */
    private void playedLastMove(Move move) {
      board.setLastPlayedMove(move);
    }

    private List<Square> movesFrom(Square square) {
      return calculator.getPossibleMoves(game, square);
    }

    private List<Square> premovesFrom(Square square) {
      return PremoveMoveCalculator.getPremoveMoves(game, square, EasterEggRules.STANDARD.with1500sRules());
    }

    private MoveResult move(UUID player, Square from, Square to) {
      game.selectPiece(from, player);
      MoveResult result = game.makeMove(to, player);
      assertNotNull(result);
      assertEquals(
          MoveResultType.SUCCESS,
          result.type(),
          () -> "move " + from + "-" + to + " must be legal");
      game.unselectPiece(player);
      game.toggleTurn();
      return result;
    }
  }
}
