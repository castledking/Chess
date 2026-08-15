package codes.castled.chess.engine.api.piece;

/** Represents a piece color. */
public enum PieceColor {
  BLACK,
  WHITE;

  public static PieceColor getOtherColor(PieceColor color) {
    return color == WHITE ? BLACK : WHITE;
  }
}
