package codes.castled.chess.engine.api.board;

/**
 * Represents one square on the chess board at the given position.
 *
 * @param row Between 1 & 8
 * @param column Between A & H
 */
public record Square(char row, char column) {

  /**
   * Validates the given square values.
   *
   * @throws IllegalArgumentException if invalid values have been used for row or column
   */
  public Square {
    if (row < '1' || row > '8') {
      throw new IllegalArgumentException("Row must be between 1 and 8!");
    }

    column = Character.toUpperCase(column);
    if (column < 'A' || column > 'H') {
      throw new IllegalArgumentException("Column must be between 'A' and 'H'!");
    }
  }

  /**
   * @return the row translated into a zero based index (0-7)
   */
  public int getRowIndex() {
    return Character.getNumericValue(row) - 1;
  }

  /**
   * @return the column translated into a zero based index (0-7)
   */
  public int getColumnIndex() {
    return Character.toUpperCase(column) - 65;
  }
}
