package codes.castled.chess.engine.common.move.calculator.type;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceColor;

import java.util.List;

/** Provides shared functionality for line based piece move calculators. */
public abstract class LineBasedPieceMoveCalculator extends PieceMoveCalculator {

    /**
     * Calculates the possible vertical squares and adds them to the list of possible moves.
     *
     * @param board the associated chess board
     * @param squares the list of possible moves
     * @param pieceSquare the square of the piece
     * @param pieceColor the color of the piece
     */
    protected void addVerticalLineSquares(
            ChessBoard board, List<Square> squares, Square pieceSquare, PieceColor pieceColor) {
        addLineSquares(pieceSquare, board, squares, pieceColor, 1, 0);
        addLineSquares(pieceSquare, board, squares, pieceColor, -1, 0);
    }

    /**
     * Calculates the possible horizontal squares and adds them to the list of possible moves.
     *
     * @param board the associated chess board
     * @param squares the list of possible moves
     * @param pieceSquare the square of the piece
     * @param pieceColor the color of the piece
     */
    protected void addHorizontalLineSquares(
            ChessBoard board, List<Square> squares, Square pieceSquare, PieceColor pieceColor) {
        addLineSquares(pieceSquare, board, squares, pieceColor, 0, 1);
        addLineSquares(pieceSquare, board, squares, pieceColor, 0, -1);
    }

    /**
     * Calculates the possible diagonal squares and adds them to the list of possible moves.
     *
     * @param board the associated chess board
     * @param squares the list of possible moves
     * @param pieceSquare the square of the piece
     * @param pieceColor the color of the piece
     */
    protected void addDiagonalLineSquares(ChessBoard board, List<Square> squares, Square pieceSquare, PieceColor pieceColor) {
        addLineSquares(pieceSquare, board, squares, pieceColor, 1, 1);
        addLineSquares(pieceSquare, board, squares, pieceColor, -1, 1);
        addLineSquares(pieceSquare, board, squares, pieceColor, 1, -1);
        addLineSquares(pieceSquare, board, squares, pieceColor, -1, -1);
    }
}
