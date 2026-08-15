package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.SquareUtils;
import codes.castled.chess.engine.common.move.calculator.type.PieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;

/** Calculates possible pawn moves. */
public final class PawnMoveCalculator extends PieceMoveCalculator {

  @Override
  public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece pawn) {
    List<Square> squares = new ArrayList<>();
    int direction = pawn.color() == PieceColor.WHITE ? 1 : -1;
    boolean onStartRow =
        (pawn.color() == PieceColor.WHITE && pieceSquare.getRowIndex() == 1)
            || (pawn.color() == PieceColor.BLACK && pieceSquare.getRowIndex() == 6);

    addVerticalSquares(board, squares, pieceSquare, direction, onStartRow);

    addDiagonalSquares(board, squares, pieceSquare, direction, pawn.color());

    addEnPassantSquares(board, squares, pieceSquare, direction, pawn.color());

    return squares;
  }

  /**
   * Calculates the possible vertical squares and adds them to the list of possible moves.
   *
   * @param board the associated chess board
   * @param squares the list of possible moves
   * @param pieceSquare the square of the pawn
   * @param direction the forward direction of the pawn
   * @param onStartRow true if the pawn is on the start row, false if not
   */
  private void addVerticalSquares(
      ChessBoard board,
      List<Square> squares,
      Square pieceSquare,
      int direction,
      boolean onStartRow) {
    Square verticalSquare = SquareUtils.offsetOrNull(pieceSquare, direction, 0);
    if (verticalSquare != null && board.getPiece(verticalSquare) == null) {
      squares.add(verticalSquare);

      Square doubleVerticalSquare = SquareUtils.offsetOrNull(pieceSquare, direction * 2, 0);
      if (onStartRow
          && doubleVerticalSquare != null
          && board.getPiece(doubleVerticalSquare) == null) {
        squares.add(doubleVerticalSquare);
      }
    }
  }

  /**
   * Calculates the possible diagonal squares and adds them to the list of possible moves.
   *
   * @param board the associated chess board
   * @param squares the list of possible moves
   * @param pieceSquare the square of the pawn
   * @param direction the forward direction of the pawn
   * @param pawnColor the color of the pawn
   */
  private void addDiagonalSquares(
      ChessBoard board,
      List<Square> squares,
      Square pieceSquare,
      int direction,
      PieceColor pawnColor) {
    Square diagonalRightSquare = SquareUtils.offsetOrNull(pieceSquare, direction, 1);
    Piece otherPiece = board.getPiece(diagonalRightSquare);

    if (otherPiece != null && otherPiece.color() != pawnColor) {
      squares.add(diagonalRightSquare);
    }

    Square diagonalLeftSquare = SquareUtils.offsetOrNull(pieceSquare, direction, -1);
    otherPiece = board.getPiece(diagonalLeftSquare);

    if (otherPiece != null && otherPiece.color() != pawnColor) {
      squares.add(diagonalLeftSquare);
    }
  }

  /**
   * Calculates the possible en passant squares and adds them to the list of possible moves.
   *
   * @param board the associated chess board
   * @param squares the list of possible moves
   * @param pieceSquare the square of the pawn
   * @param direction the forward direction of the pawn
   * @param pawnColor the color of the pawn
   */
  private void addEnPassantSquares(
      ChessBoard board,
      List<Square> squares,
      Square pieceSquare,
      int direction,
      PieceColor pawnColor) {
    Move lastPlayedMove = board.getLastPlayedMove();
    if (lastPlayedMove != null) {
      Piece lastMovedPiece = lastPlayedMove.piece();

      if (lastMovedPiece != null
          && lastMovedPiece.type() == PieceType.PAWN
          && lastMovedPiece.color() != pawnColor) {

        if (Math.abs(lastPlayedMove.from().getRowIndex() - lastPlayedMove.to().getRowIndex()) == 2
            && lastPlayedMove.to().getRowIndex() == pieceSquare.getRowIndex()
            && Math.abs(lastPlayedMove.to().getColumnIndex() - pieceSquare.getColumnIndex()) == 1) {
          Square enPassantSquare = SquareUtils.offsetOrNull(lastPlayedMove.to(), direction, 0);
          if (enPassantSquare != null) {
            squares.add(enPassantSquare);
          }
        }
      }
    }
  }
}
