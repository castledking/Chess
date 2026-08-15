package codes.castled.chess.engine.common.move.calculator;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.common.move.calculator.type.LineBasedPieceMoveCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates possible bishop moves.
 */
public final class BishopMoveCalculator extends LineBasedPieceMoveCalculator {

    @Override
    public List<Square> getMoves(ChessBoard board, Square pieceSquare, Piece bishop) {
        List<Square> squares = new ArrayList<>();

        addDiagonalLineSquares(board, squares, pieceSquare, bishop.color());

        return squares;
    }
}
