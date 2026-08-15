package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.common.board.SquareUtils;
import codes.castled.chess.engine.common.move.calculator.type.PieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Calculates possible knight moves. */
public final class KnightMoveCalculator extends PieceMoveCalculator {

  @Override
  public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece knight) {
    List<Square> squares = new ArrayList<>();

    addKnightMoves(squares, pieceSquare);

    return filterOutInvalidSquares(squares, board, knight.color());
  }

  /**
   * Calculates the possible knight squares and adds them to the list of possible moves.
   *
   * @param squares the list of possible moves
   * @param pieceSquare the square of the knight
   */
  private void addKnightMoves(List<Square> squares, Square pieceSquare) {
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 1, 2));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -1, 2));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 1, -2));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -1, -2));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 2, 1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, 2, -1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -2, 1));
    squares.add(SquareUtils.offsetOrNull(pieceSquare, -2, -1));
  }

  /**
   * Filters out every square that is already occupied by a piece of the same color.
   *
   * @param squares the list of possible moves
   * @param board the associated chess board
   * @param knightColor the color of the knight
   * @return the filtered list of possible moves
   */
  private List<Square> filterOutInvalidSquares(
      List<Square> squares, ChessBoard board, PieceColor knightColor) {
    return squares.stream()
        .filter(Objects::nonNull)
        .filter(
            targetSquare -> {
              Piece targetPiece = board.getPiece(targetSquare);
              return targetPiece == null || targetPiece.color() != knightColor;
            })
        .toList();
  }
}
