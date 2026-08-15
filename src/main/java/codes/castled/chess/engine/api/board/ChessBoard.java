package codes.castled.chess.engine.api.board;

import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;

import java.util.List;

/**
 * Represents the chess board of a running game. Allows read-only access to the current state of the
 * board.
 */
public interface ChessBoard {

  /**
   * @param square the position on the board
   * @return the piece on that square, or null if there is no piece
   */
  Piece getPiece(Square square);

  /**
   * @return the last move that was played
   */
  Move getLastPlayedMove();

  /**
   * @param square the position on the board
   * @return whether there is a piece on that square, or not
   */
  boolean isOccupied(Square square);

  /**
   * Gets all the squares from the specified piece types with the specified color.
   *
   * @param color the color of the specified piece types
   * @param types the type of the needed pieces
   * @return the squares of all pieces with the specified type and color
   */
  List<Square> getColoredPieces(PieceColor color, PieceType... types);

  /**
   * Gets all the squares from every piece with the specified color.
   *
   * @param color the color of the requested pieces
   * @return the squares of all pieces with the specified color
   */
  List<Square> getColoredPieces(PieceColor color);
}
