package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.board.Square;

/**
 * Converts board squares to and from lower-case algebraic notation (e.g. {@code e2}).
 *
 * <p>The dialog backend drives clicks through in-process callbacks that capture the target
 * {@link Square} directly, so no string action encoding is needed — only human-readable
 * coordinates for tooltips and status lines.
 */
public final class SquareNotation {

  private SquareNotation() {}

  /** Formats a square as lower-case algebraic notation, e.g. {@code e2}. */
  public static String toAlgebraic(Square square) {
    return "" + Character.toLowerCase(square.column()) + square.row();
  }

  /**
   * Parses lower- or upper-case algebraic notation into a square.
   *
   * @return the square, or {@code null} if the text is not a valid square
   */
  public static Square parseAlgebraic(String text) {
    if (text == null || text.length() != 2) {
      return null;
    }
    char file = Character.toUpperCase(text.charAt(0));
    char rank = text.charAt(1);
    if (file < 'A' || file > 'H' || rank < '1' || rank > '8') {
      return null;
    }
    try {
      return new Square(rank, file);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
