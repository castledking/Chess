package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.common.move.calculator.type.LineBasedPieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;

/** Calculates possible queen moves. */
public final class QueenMoveCalculator extends LineBasedPieceMoveCalculator {

  @Override
  public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece queen) {
    List<Square> squares = new ArrayList<>();

    addVerticalLineSquares(board, squares, pieceSquare, queen.color());
    addHorizontalLineSquares(board, squares, pieceSquare, queen.color());
    addDiagonalLineSquares(board, squares, pieceSquare, queen.color());

    return squares;
  }
}
