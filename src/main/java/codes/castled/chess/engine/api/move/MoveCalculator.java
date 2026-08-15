package codes.castled.chess.engine.api.move;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.piece.PieceColor;

import java.util.List;

/**
 * Provides access to possible moves and targeted squares.
 */
public interface MoveCalculator {

    /**
     * Calculates all possible moves for the piece on the given square without causing self check.
     *
     * @param chessGame the played game
     * @param pieceSquare the square the piece is standing on
     * @return a list of all possible squares the specified piece can move to legally
     */
    List<Square> getPossibleMoves(ChessGame chessGame, Square pieceSquare);

    /**
     * Calculates every square the given piece is targeting.
     *
     * @param board the associated chess board
     * @param pieceSquare the square the piece is standing on
     * @return a list of all squares the piece is targeting, but cannot necessarily move to
     */
    List<Square> getRawMoves(ChessBoard board, Square pieceSquare);

    /**
     * Calculates every targeted square for all pieces of the given color.
     *
     * @param board the associated chess board
     * @param color the color of the pieces
     * @return all squares currently targeted by the pieces of the given color
     */
    List<Square> getAllRawMoves(ChessBoard board, PieceColor color);
}
