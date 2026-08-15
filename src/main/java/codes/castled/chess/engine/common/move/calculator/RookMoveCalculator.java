package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.common.move.calculator.type.LineBasedPieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates possible rook moves.
 */
public final class RookMoveCalculator extends LineBasedPieceMoveCalculator {

  @Override
  public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece rook) {
    List<Square> squares = new ArrayList<>();

    addVerticalLineSquares(board, squares, pieceSquare, rook.color());
    addHorizontalLineSquares(board, squares, pieceSquare, rook.color());

    return squares;
  }
}
