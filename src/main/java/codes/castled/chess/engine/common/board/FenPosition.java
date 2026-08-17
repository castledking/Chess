package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;

import javax.annotation.Nullable;

/**
 * A position read out of a FEN, before it is loaded onto a running game.
 *
 * @param board the pieces, indexed as {@code [columnIndex][rowIndex]} to match the board's own
 *     layout
 * @param turn the side to move
 * @param castlingField the raw castling field, e.g. {@code KQkq} or {@code -}; kept as written
 *     because turning it into rook squares needs the board, which the loader has
 * @param enPassantTarget the square a pawn may capture onto, or null
 * @param halfmoveClock plies since the last capture or pawn move
 * @param fullmoveNumber the move number, starting at 1
 */
public record FenPosition(
    Piece[][] board,
    PieceColor turn,
    String castlingField,
    @Nullable Square enPassantTarget,
    int halfmoveClock,
    int fullmoveNumber) {

  /**
   * @param color the colour to check
   * @param kingSide true for the king-side right, false for the queen-side
   * @return whether the castling field grants that right
   */
  public boolean hasCastlingRight(PieceColor color, boolean kingSide) {
    char flag = kingSide ? 'K' : 'Q';
    if (color == PieceColor.BLACK) {
      flag = Character.toLowerCase(flag);
    }
    return castlingField.indexOf(flag) >= 0;
  }
}
