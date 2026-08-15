package codes.castled.chess.ui;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceColor;

/**
 * Converts between chess board squares and the row/column indices of a rendered
 * 8x8 dialog grid.
 *
 * <p>The renderer lays out 64 cells in reading order: render row 0 is the top row,
 * render column 0 is the left column. Orientation decides which board square each
 * rendered cell maps to:
 *
 * <ul>
 *   <li>{@link PieceColor#WHITE}: rank 8 on top, file A on the left, so the top-left
 *       cell is {@code a8} and the bottom-right cell is {@code h1}.
 *   <li>{@link PieceColor#BLACK}: the board is flipped 180 degrees, so the top-left
 *       cell is {@code h1} and the bottom-right cell is {@code a8}.
 * </ul>
 *
 * <p>This class is intentionally free of any Bukkit or dialog-API dependency so the
 * orientation maths can be unit tested in isolation.
 */
public enum BoardOrientation {
  WHITE,
  BLACK;

  /** Number of ranks/files on a chess board. */
  public static final int SIZE = 8;

  /**
   * Chooses the orientation a viewer should see. Players see the board from their
   * own colour's perspective; anyone without a colour (a spectator) gets the
   * deterministic {@link #WHITE} orientation.
   *
   * @param viewerColor the viewer's colour, or {@code null} for a spectator
   * @param flipped whether the viewer has toggled a flipped board
   * @return the orientation to render with
   */
  public static BoardOrientation forViewer(PieceColor viewerColor, boolean flipped) {
    BoardOrientation base = viewerColor == PieceColor.BLACK ? BLACK : WHITE;
    if (!flipped) {
      return base;
    }
    return base == WHITE ? BLACK : WHITE;
  }

  /**
   * Maps a rendered grid cell to the board square it represents under this
   * orientation.
   *
   * @param renderRow render row, 0 (top) .. 7 (bottom)
   * @param renderColumn render column, 0 (left) .. 7 (right)
   * @return the corresponding board square
   * @throws IllegalArgumentException if either index is outside {@code 0..7}
   */
  public Square toSquare(int renderRow, int renderColumn) {
    checkBounds(renderRow, "renderRow");
    checkBounds(renderColumn, "renderColumn");

    int rankIndex; // 0 == rank 1, 7 == rank 8
    int fileIndex; // 0 == file A, 7 == file H
    if (this == WHITE) {
      rankIndex = (SIZE - 1) - renderRow;
      fileIndex = renderColumn;
    } else {
      rankIndex = renderRow;
      fileIndex = (SIZE - 1) - renderColumn;
    }

    char row = (char) ('1' + rankIndex);
    char column = (char) ('A' + fileIndex);
    return new Square(row, column);
  }

  /**
   * Maps a board square to the render row it occupies under this orientation.
   *
   * @param square the board square
   * @return render row, 0 (top) .. 7 (bottom)
   */
  public int toRenderRow(Square square) {
    int rankIndex = square.getRowIndex();
    return this == WHITE ? (SIZE - 1) - rankIndex : rankIndex;
  }

  /**
   * Maps a board square to the render column it occupies under this orientation.
   *
   * @param square the board square
   * @return render column, 0 (left) .. 7 (right)
   */
  public int toRenderColumn(Square square) {
    int fileIndex = square.getColumnIndex();
    return this == WHITE ? fileIndex : (SIZE - 1) - fileIndex;
  }

  private static void checkBounds(int value, String name) {
    if (value < 0 || value >= SIZE) {
      throw new IllegalArgumentException(name + " must be between 0 and " + (SIZE - 1));
    }
  }
}
