package codes.castled.chess.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.FenCodec;
import codes.castled.chess.engine.common.board.FenPosition;
import codes.castled.chess.wiring.EngineFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Covers the FEN codec, the fifty-move clock, and threefold repetition.
 *
 * <p>FEN is the format a UCI engine and a remote board both consume, so an error here is not a
 * cosmetic one: it would hand a chess engine the wrong position and get back a move for a game
 * nobody is playing.
 */
class FenCodecTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));

  /* Writing ---------------------------------------------------------- */

  @Test
  void aFreshGameIsTheStandardStartingPosition() {
    assertEquals(FenCodec.START, newGame().toFen());
  }

  @Test
  void aPawnAdvanceRecordsTheEnPassantTargetAndResetsTheClock() {
    ChessGame game = newGame();

    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));

    // Target is the square the pawn skipped, and it is black to move on move 1.
    assertEquals(
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", game.toFen());
  }

  @Test
  void theMoveNumberAdvancesAfterBlackMovesAndTheClockCountsQuietMoves() {
    ChessGame game = newGame();

    move(game, WHITE, new Square('1', 'G'), new Square('3', 'F'));
    assertEquals(1, game.getFullmoveNumber());
    assertEquals(1, game.getHalfmoveClock(), "a knight move is quiet");

    move(game, BLACK, new Square('8', 'G'), new Square('6', 'F'));
    assertEquals(2, game.getFullmoveNumber(), "the move number advances after black");
    assertEquals(2, game.getHalfmoveClock());

    assertTrue(game.toFen().endsWith(" w KQkq - 2 2"));
  }

  @Test
  void losingCastlingRightsIsReflectedInTheCastlingField() {
    ChessGame game = newGame();

    // Clear the king side, castle, and the whole white side of the field goes.
    move(game, WHITE, new Square('1', 'G'), new Square('3', 'F'));
    move(game, BLACK, new Square('8', 'G'), new Square('6', 'F'));
    move(game, WHITE, new Square('2', 'E'), new Square('3', 'E'));
    move(game, BLACK, new Square('7', 'E'), new Square('6', 'E'));
    move(game, WHITE, new Square('1', 'F'), new Square('5', 'B'));
    move(game, BLACK, new Square('8', 'F'), new Square('4', 'B'));
    move(game, WHITE, new Square('1', 'E'), new Square('1', 'G'));

    String castlingField = game.toFen().split(" ")[2];
    assertEquals("kq", castlingField, "white has castled, black still has both rights");
  }

  /* Reading ---------------------------------------------------------- */

  @Test
  void theStartingPositionRoundTrips() {
    FenPosition parsed = FenCodec.parse(FenCodec.START);

    assertEquals(PieceColor.WHITE, parsed.turn());
    assertEquals(0, parsed.halfmoveClock());
    assertEquals(1, parsed.fullmoveNumber());
    assertNull(parsed.enPassantTarget());

    assertTrue(parsed.hasCastlingRight(PieceColor.WHITE, true));
    assertTrue(parsed.hasCastlingRight(PieceColor.BLACK, false));

    // [column][row], so e1 is column 4, row 0.
    assertEquals(PieceType.KING, parsed.board()[4][0].type());
    assertEquals(PieceColor.WHITE, parsed.board()[4][0].color());
    assertEquals(PieceType.ROOK, parsed.board()[0][7].type());
    assertEquals(PieceColor.BLACK, parsed.board()[0][7].color());
    assertNull(parsed.board()[4][4], "the middle of the board is empty");
  }

  @Test
  void everyPositionThisEngineProducesParsesBack() {
    ChessGame game = newGame();

    move(game, WHITE, new Square('2', 'E'), new Square('4', 'E'));
    move(game, BLACK, new Square('7', 'C'), new Square('5', 'C'));
    move(game, WHITE, new Square('1', 'G'), new Square('3', 'F'));

    String fen = game.toFen();
    FenPosition parsed = FenCodec.parse(fen);

    assertEquals(PieceColor.BLACK, parsed.turn());
    // Nf3 came after c7-c5, so the en passant chance has already lapsed.
    assertNull(parsed.enPassantTarget());
    assertEquals(1, parsed.halfmoveClock(), "the knight move was quiet");
    assertEquals(2, parsed.fullmoveNumber());
  }

  @Test
  void malformedFensAreRejectedRatherThanSilentlyMisread() {
    assertThrows(IllegalArgumentException.class, () -> FenCodec.parse("too few fields"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FenCodec.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1"),
        "seven ranks is not a board");
    assertThrows(
        IllegalArgumentException.class,
        () -> FenCodec.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1"),
        "x is not a side to move");
    assertThrows(
        IllegalArgumentException.class,
        () -> FenCodec.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1"),
        "the clock must be a number");
    assertThrows(
        IllegalArgumentException.class,
        () -> FenCodec.parse("rnbqkbnr/pppppppp/8/8/8/9/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
        "a rank must cover exactly eight files");
  }

  /* Draw rules ------------------------------------------------------- */

  @Test
  void shufflingKnightsBackAndForthRepeatsThePositionThreeTimes() {
    ChessGame game = newGame();
    Square g1 = new Square('1', 'G');
    Square f3 = new Square('3', 'F');
    Square g8 = new Square('8', 'G');
    Square f6 = new Square('6', 'F');

    // The position after this pair has now been seen once.
    for (int repetition = 0; repetition < 2; repetition++) {
      move(game, WHITE, g1, f3);
      move(game, BLACK, g8, f6);
      move(game, WHITE, f3, g1);
      move(game, BLACK, f6, g8);
    }

    assertTrue(game.isThreefoldRepetition(), "the start position has now occurred three times");
    assertEquals(8, game.getHalfmoveClock(), "none of those moves was a capture or a pawn move");
  }

  @Test
  void aPawnMoveClearsTheRepetitionHistory() {
    ChessGame game = newGame();
    Square g1 = new Square('1', 'G');
    Square f3 = new Square('3', 'F');
    Square g8 = new Square('8', 'G');
    Square f6 = new Square('6', 'F');

    move(game, WHITE, g1, f3);
    move(game, BLACK, g8, f6);
    move(game, WHITE, f3, g1);
    move(game, BLACK, f6, g8);

    // A pawn move is irreversible, so nothing before it can ever recur.
    move(game, WHITE, new Square('2', 'A'), new Square('3', 'A'));
    assertEquals(0, game.getHalfmoveClock());

    move(game, BLACK, g8, f6);
    move(game, WHITE, g1, f3);
    move(game, BLACK, f6, g8);
    move(game, WHITE, f3, g1);

    assertTrue(!game.isThreefoldRepetition(), "the count restarted after the pawn move");
  }

  @Test
  void theFiftyMoveDrawNeedsAHundredQuietPlies() {
    ChessGame game = newGame();

    assertTrue(!game.isFiftyMoveDraw());
    assertEquals(0, game.getHalfmoveClock());

    move(game, WHITE, new Square('1', 'G'), new Square('3', 'F'));
    assertEquals(1, game.getHalfmoveClock());
    assertTrue(!game.isFiftyMoveDraw(), "one ply is not fifty moves");
  }

  /* Helpers ---------------------------------------------------------- */

  private static ChessGame newGame() {
    return new EngineFactory(false)
        .chessGameService()
        .createGame(WHITE, BLACK, TimeMode.TEN)
        .game();
  }

  private static void move(ChessGame game, UUID player, Square from, Square to) {
    game.selectPiece(from, player);
    assertEquals(
        MoveResultType.SUCCESS,
        game.makeMove(to, player).type(),
        () -> "move " + from + "-" + to + " must be legal");
    game.unselectPiece(player);
    game.toggleTurn();
  }
}
