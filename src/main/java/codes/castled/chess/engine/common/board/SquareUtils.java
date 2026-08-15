package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.board.Square;

import javax.annotation.Nullable;

/** Provides helper methods to run calculations on squares. */
public final class SquareUtils {

  /**
   * Returns a new square moved by the given offsets, or null if the resulting position is outside chess the board.
   *
   * @param square the base square
   * @param rowOffset the vertical offset
   * @param columnOffset the horizontal offset
   * @return the offset square, or null if the resulting position is invalid
   */
  @Nullable
  public static Square offsetOrNull(Square square, int rowOffset, int columnOffset) {
    try {
      return offset(square, rowOffset, columnOffset);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  /**
   * Returns a new square moved by the given offsets.
   *
   * @param square the base square
   * @param rowOffset the vertical offset
   * @param columnOffset the horizontal offset
   * @return the offset square
   * @throws IllegalArgumentException if the resulting position is outside the chess board
   */
  private static Square offset(Square square, int rowOffset, int columnOffset) {
    return new Square((char) (square.row() + rowOffset), (char) (square.column() + columnOffset));
  }
}
