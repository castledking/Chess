package codes.castled.chess.engine.common.move.calculator.type;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.common.board.SquareUtils;

import java.util.List;

/** Provides shared functionality for piece move calculators. */
public abstract class PieceMoveCalculator {

  /**
   * Calculates the possible moves for the given piece.
   *
   * @param board the associated chess board
   * @param pieceSquare the square of the piece
   * @param piece the piece to calculate the moves from
   * @return the possible moves of the given piece
   */
  public abstract List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece piece);

  /**
   * Adds all possible moves in the line defined by the offsets to the list of possible moves.
   *
   * @param pieceSquare the square of the piece
   * @param board the associated chess board
   * @param squares the list of possible moves
   * @param color the color of the piece
   * @param rowOffset the offset change in row
   * @param columnOffset the offset change in column
   */
  protected void addLineSquares(
      Square pieceSquare,
      ChessBoard board,
      List<Square> squares,
      PieceColor color,
      int rowOffset,
      int columnOffset) {

    Square copySquare = pieceSquare;

    for (int i = 0; i < 7; i++) {
      copySquare = SquareUtils.offsetOrNull(copySquare, rowOffset, columnOffset);

      if (copySquare == null) {
        break;
      }

      Piece currentPiece = board.getPiece(copySquare);

      if (currentPiece == null) {
        squares.add(copySquare);
      } else if (currentPiece.color() != color) {
        squares.add(copySquare);
        break;
      } else {
        break;
      }
    }
  }
}
