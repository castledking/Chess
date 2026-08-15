package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.SquareUtils;
import codes.castled.chess.engine.common.move.calculator.type.PieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Calculates possible king moves. */
public final class KingMoveCalculator extends PieceMoveCalculator {

  @Override
  public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece king) {
    List<Square> squares = new ArrayList<>();

    addKingMoves(squares, pieceSquare);

    return filterOutInvalidSquares(squares, board, king.color());
  }

  /**
   * Calculates the possible king squares and adds them to the list of possible moves.
   *
   * @param squares the list of possible moves
   * @param pieceSquare the square of the king
   */
  private void addKingMoves(List<Square> squares, Square pieceSquare) {
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 0, 1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 1, 0));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 1, 1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -1, 1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 1, -1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -1, -1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 0, -1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -1, 0));
  }

  /**
   * Filters out every square that the king is not allowed to move to.
   *
   * @param squares the list of possible moves
   * @param board the associated chess board
   * @param kingColor the color of the king
   * @return the filtered list of possible moves
   */
  private List<Square> filterOutInvalidSquares(
          List<Square> squares, ChessBoard board, PieceColor kingColor) {
    return squares.stream()
            .filter(Objects::nonNull)
            .filter(targetSquare -> !isOccupiedByOwnPiece(board, targetSquare, kingColor))
            .filter(targetSquare -> !isOccupiedByEnemyKing(board, targetSquare, kingColor))
            .filter(targetSquare -> !isNextToEnemyKing(board, targetSquare, kingColor))
            .toList();
  }

  /**
   * @param board the associated chess board
   * @param square the square to check
   * @param kingColor the color of the king
   * @return whether the given square is occupied by an own piece
   */
  private boolean isOccupiedByOwnPiece(ChessBoard board, Square square, PieceColor kingColor) {
    Piece piece = board.getPiece(square);

    return piece != null && piece.color() == kingColor;
  }

  /**
   * @param board the associated chess board
   * @param square the square to check
   * @param kingColor the color of the king
   * @return whether the given square is occupied by an enemy king
   */
  private boolean isOccupiedByEnemyKing(ChessBoard board, Square square, PieceColor kingColor) {
    Piece piece = board.getPiece(square);

    return piece != null
            && piece.color() != kingColor
            && piece.type() == PieceType.KING;
  }

  /**
   * @param board the associated chess board
   * @param square the square to check
   * @param kingColor the color of the king
   * @return whether the given square is next to the enemy king
   */
  private boolean isNextToEnemyKing(ChessBoard board, Square square, PieceColor kingColor) {
    PieceColor enemyColor = PieceColor.getOtherColor(kingColor);

    for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
      for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
        if (rowOffset == 0 && columnOffset == 0) {
          continue;
        }

        Square offsetSquare = SquareUtils.offsetOrNull(square, rowOffset, columnOffset);

        if (offsetSquare == null) {
          continue;
        }

        Piece adjacentPiece = board.getPiece(offsetSquare);

        if (adjacentPiece != null
                && adjacentPiece.color() == enemyColor
                && adjacentPiece.type() == PieceType.KING) {
          return true;
        }
      }
    }

    return false;
  }
}
