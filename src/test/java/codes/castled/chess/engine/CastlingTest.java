package codes.castled.chess.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.CastlingStatus;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.game.ChessGameImpl;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Covers castling generation and execution: the standard king-side and queen-side moves, and the
 * vertical castling easter egg reached by promoting a pawn to a rook on the king's own file.
 *
 * <p>Positions are built by clearing the standard start position and placing only the pieces the
 * case needs, because the engine has no FEN parser.
 */
class CastlingTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));

  private static final Square E1 = new Square('1', 'E');
  private static final Square E2 = new Square('2', 'E');
  private static final Square E3 = new Square('3', 'E');
  private static final Square E8 = new Square('8', 'E');
  private static final Square A1 = new Square('1', 'A');
  private static final Square H1 = new Square('1', 'H');

  /* Standard castling --------------------------------------------------- */

  @Test
  void kingSideCastlingMovesTheKingTwoFilesAndTheRookBesideIt() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    Square g1 = new Square('1', 'G');
    assertTrue(fixture.movesFrom(E1).contains(g1), "king side castling must be offered");

    MoveResult result = fixture.move(WHITE, E1, g1);

    assertTrue(result.castling());
    assertEquals(PieceType.KING, fixture.pieceAt(g1).type());
    assertEquals(PieceType.ROOK, fixture.pieceAt(new Square('1', 'F')).type());
    assertNull(fixture.pieceAt(H1));
    assertEquals(H1, result.rookMove().from());
    assertEquals(new Square('1', 'F'), result.rookMove().to());
  }

  @Test
  void queenSideCastlingMovesTheKingTwoFilesAndTheRookBesideIt() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(A1, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    Square c1 = new Square('1', 'C');
    assertTrue(fixture.movesFrom(E1).contains(c1), "queen side castling must be offered");

    MoveResult result = fixture.move(WHITE, E1, c1);

    assertTrue(result.castling());
    assertEquals(PieceType.KING, fixture.pieceAt(c1).type());
    assertEquals(PieceType.ROOK, fixture.pieceAt(new Square('1', 'D')).type());
    assertNull(fixture.pieceAt(A1));
  }

  @Test
  void castlingIsNotOfferedThroughAnOccupiedSquare() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.place(new Square('1', 'F'), PieceType.BISHOP, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    assertFalse(fixture.movesFrom(E1).contains(new Square('1', 'G')));
  }

  @Test
  void castlingIsNotOfferedOnceTheRookHasMoved() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);

    // Rook out and straight back: the position repeats but the right is gone.
    fixture.move(WHITE, H1, new Square('2', 'H'));
    fixture.move(BLACK, E8, new Square('7', 'E'));
    fixture.move(WHITE, new Square('2', 'H'), H1);

    assertFalse(fixture.movesFrom(E1).contains(new Square('1', 'G')));
  }

  @Test
  void castlingIsNotOfferedThroughAnAttackedSquare() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(H1, PieceColor.WHITE);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);
    // A black rook on f8 attacks f1, the square the king would cross.
    fixture.place(new Square('8', 'F'), PieceType.ROOK, PieceColor.BLACK);

    assertFalse(fixture.movesFrom(E1).contains(new Square('1', 'G')));
  }

  /* Vertical castling --------------------------------------------------- */

  @Test
  void verticalCastlingIsNotOfferedWhenTheEasterEggIsDisabled() {
    Fixture fixture = emptyBoard(false);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(E8, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    assertFalse(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void verticalCastlingMovesTheKingTwoRanksAndTheRookBelowIt() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(E8, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    // Both squares up the file light up: e2 as an ordinary king step, e3 as the castle.
    assertTrue(fixture.movesFrom(E1).contains(E2), "e2 is an ordinary king move");
    assertTrue(fixture.movesFrom(E1).contains(E3), "e3 is the vertical castle");

    MoveResult result = fixture.move(WHITE, E1, E3);

    assertTrue(result.castling());
    assertEquals(PieceType.KING, fixture.pieceAt(E3).type());
    assertEquals(PieceType.ROOK, fixture.pieceAt(E2).type());
    assertNull(fixture.pieceAt(E8));
    assertNull(fixture.pieceAt(E1));
    assertEquals(E8, result.rookMove().from());
    assertEquals(E2, result.rookMove().to());
  }

  @Test
  void verticalCastlingIsNotOfferedWhenTheFileIsBlocked() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(E8, PieceType.ROOK, PieceColor.WHITE);
    fixture.place(new Square('5', 'E'), PieceType.PAWN, PieceColor.BLACK);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);
    fixture.markUnmovedRook(PieceColor.WHITE, E8);

    assertFalse(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void verticalCastlingIsNotOfferedOutOfThroughOrIntoCheck() {
    // Out of check: a black rook on a1 checks the king along the first rank.
    Fixture outOf = verticalFixture();
    outOf.place(A1, PieceType.ROOK, PieceColor.BLACK);
    assertFalse(outOf.movesFrom(E1).contains(E3), "may not castle out of check");

    // Through check: a black rook on a2 attacks e2, the square the king crosses.
    Fixture through = verticalFixture();
    through.place(new Square('2', 'A'), PieceType.ROOK, PieceColor.BLACK);
    assertFalse(through.movesFrom(E1).contains(E3), "may not castle through check");

    // Into check: a black rook on a3 attacks e3, the king's destination.
    Fixture into = verticalFixture();
    into.place(new Square('3', 'A'), PieceType.ROOK, PieceColor.BLACK);
    assertFalse(into.movesFrom(E1).contains(E3), "may not castle into check");
  }

  @Test
  void verticalCastlingIsNotOfferedOnceTheKingHasMoved() {
    Fixture fixture = verticalFixture();

    fixture.move(WHITE, E1, new Square('1', 'D'));
    fixture.move(BLACK, new Square('8', 'A'), new Square('7', 'A'));
    fixture.move(WHITE, new Square('1', 'D'), E1);

    assertFalse(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void verticalCastlingIsNotOfferedWithARookThatHasMoved() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(E8, PieceType.ROOK, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);
    // Deliberately not registered as unmoved: a rook that walked to the e-file cannot castle.

    assertFalse(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void promotingToARookOnTheKingsFileEnablesVerticalCastling() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(new Square('7', 'E'), PieceType.PAWN, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    Square e7 = new Square('7', 'E');
    MoveResult promotionResult = fixture.move(WHITE, e7, E8);
    assertTrue(promotionResult.promotion(), "reaching the last rank must await a piece choice");

    fixture.game.applyPromotion(e7, E8, PieceType.ROOK);
    assertEquals(PieceType.ROOK, fixture.pieceAt(E8).type());

    // The promoted rook has never moved, so the king may now castle straight up the file.
    assertTrue(fixture.game.getUnmovedRookSquares(PieceColor.WHITE).contains(E8));
    assertTrue(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void promotingToAQueenDoesNotEnableVerticalCastling() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.place(new Square('7', 'E'), PieceType.PAWN, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    fixture.move(WHITE, new Square('7', 'E'), E8);
    fixture.game.applyPromotion(new Square('7', 'E'), E8, PieceType.QUEEN);

    assertFalse(fixture.game.getUnmovedRookSquares(PieceColor.WHITE).contains(E8));
    assertFalse(fixture.movesFrom(E1).contains(E3));
  }

  @Test
  void blackCastlesVerticallyDownTheBoard() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E8, PieceType.KING, PieceColor.BLACK);
    fixture.placeUnmovedRook(E1, PieceColor.BLACK);
    fixture.place(new Square('1', 'A'), PieceType.KING, PieceColor.WHITE);
    fixture.game.toggleTurn();

    Square e6 = new Square('6', 'E');
    assertTrue(fixture.movesFrom(E8).contains(e6));

    MoveResult result = fixture.move(BLACK, E8, e6);

    assertTrue(result.castling());
    assertEquals(PieceType.KING, fixture.pieceAt(e6).type());
    assertEquals(PieceType.ROOK, fixture.pieceAt(new Square('7', 'E')).type());
  }

  @Test
  void verticalCastlingIsNotOfferedBackwards() {
    // A white rook that is somehow unmoved behind the king must not let it castle downwards,
    // and in any case there is no rank below the first.
    Fixture fixture = emptyBoard(true);
    fixture.place(new Square('8', 'E'), PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(E1, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);

    assertFalse(fixture.movesFrom(new Square('8', 'E')).contains(new Square('6', 'E')));
  }

  /* Fixture ------------------------------------------------------------- */

  private Fixture verticalFixture() {
    Fixture fixture = emptyBoard(true);
    fixture.place(E1, PieceType.KING, PieceColor.WHITE);
    fixture.placeUnmovedRook(E8, PieceColor.WHITE);
    fixture.place(new Square('8', 'A'), PieceType.KING, PieceColor.BLACK);
    return fixture;
  }

  private Fixture emptyBoard(boolean verticalCastling) {
    EngineFactory engine = new EngineFactory(verticalCastling);
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
      markUnmovedRook(color, square);
    }

    private void markUnmovedRook(PieceColor color, Square square) {
      castlingStatus().markRookUnmoved(color, square);
    }

    private CastlingStatus castlingStatus() {
      return ((ChessGameImpl) game).getCastlingStatus();
    }

    private List<Square> movesFrom(Square square) {
      return calculator.getPossibleMoves(game, square);
    }

    private Piece pieceAt(Square square) {
      return board.getPiece(square);
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
