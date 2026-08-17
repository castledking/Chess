package codes.castled.chess.engine.common.move;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceType;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Long algebraic notation, the move format the UCI protocol speaks.
 *
 * <p>A move is the origin square followed by the destination, and for a promotion the chosen piece
 * as a lower-case letter: {@code e2e4}, {@code e7e8q}. Castling is written as the king's own move
 * ({@code e1g1}), which matches how this engine represents it — the rook's half is derived rather
 * than given.
 *
 * @param from the origin square
 * @param to the destination square
 * @param promotion the piece a pawn promotes to, or null when the move is not a promotion
 */
public record UciMove(Square from, Square to, @Nullable PieceType promotion) {

  /**
   * @param notation a move such as {@code e2e4} or {@code e7e8q}
   * @return the parsed move
   * @throws IllegalArgumentException if the notation is not a well-formed move
   */
  public static UciMove parse(String notation) {
    String move = notation.trim().toLowerCase(Locale.ROOT);

    if (move.length() < 4 || move.length() > 5) {
      throw new IllegalArgumentException("Not a UCI move: " + notation);
    }

    Square from = square(move.substring(0, 2), notation);
    Square to = square(move.substring(2, 4), notation);
    PieceType promotion = move.length() == 5 ? promotionPiece(move.charAt(4), notation) : null;

    return new UciMove(from, to, promotion);
  }

  /** @return this move in UCI notation */
  public String notation() {
    String base =
        ""
            + Character.toLowerCase(from.column())
            + from.row()
            + Character.toLowerCase(to.column())
            + to.row();
    return promotion == null ? base : base + promotionLetter(promotion);
  }

  private static Square square(String text, String notation) {
    char column = Character.toUpperCase(text.charAt(0));
    char row = text.charAt(1);

    if (column < 'A' || column > 'H' || row < '1' || row > '8') {
      throw new IllegalArgumentException("Not a square in " + notation + ": " + text);
    }
    return new Square(row, column);
  }

  private static PieceType promotionPiece(char letter, String notation) {
    return switch (letter) {
      case 'q' -> PieceType.QUEEN;
      case 'r' -> PieceType.ROOK;
      case 'b' -> PieceType.BISHOP;
      case 'n' -> PieceType.KNIGHT;
      default -> throw new IllegalArgumentException("Not a promotion piece in " + notation + ": " + letter);
    };
  }

  private static char promotionLetter(PieceType type) {
    return switch (type) {
      case QUEEN -> 'q';
      case ROOK -> 'r';
      case BISHOP -> 'b';
      case KNIGHT -> 'n';
      default -> throw new IllegalArgumentException("Cannot promote to " + type);
    };
  }
}
