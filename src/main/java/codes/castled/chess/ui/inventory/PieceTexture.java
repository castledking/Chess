package codes.castled.chess.ui.inventory;

import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;

/**
 * The {@code custom_model_data} values the resource pack maps to each piece texture.
 *
 * <p>Highlighted variants are derived by appending a digit rather than being listed: a selected
 * piece is the base value with {@code 1} appended and an available one with {@code 2}, matching
 * the thresholds declared in {@code assets/chess/items/chess.json}. So a white pawn is 1001,
 * selected 10011, and available 10012.
 */
enum PieceTexture {
  WHITE_PAWN(1001),
  WHITE_ROOK(1002),
  WHITE_KNIGHT(1003),
  WHITE_BISHOP(1004),
  WHITE_QUEEN(1005),
  WHITE_KING(1006),
  BLACK_PAWN(1007),
  BLACK_ROOK(1008),
  BLACK_KNIGHT(1009),
  BLACK_BISHOP(1010),
  BLACK_QUEEN(1011),
  BLACK_KING(1012),
  WHITE_QUEEN_PROMOTION(1013),
  BLACK_QUEEN_PROMOTION(1014),
  WHITE_BISHOP_PROMOTION(1015),
  BLACK_BISHOP_PROMOTION(1016),
  WHITE_KNIGHT_PROMOTION(1017),
  BLACK_KNIGHT_PROMOTION(1018),
  WHITE_ROOK_PROMOTION(1019),
  BLACK_ROOK_PROMOTION(1020),

  /** An empty square carrying the selected wash. */
  EMPTY_SELECTED(1),
  /** An empty square carrying the legal-move wash. */
  EMPTY_AVAILABLE(2);

  private final int modelData;

  PieceTexture(int modelData) {
    this.modelData = modelData;
  }

  int modelData() {
    return modelData;
  }

  /**
   * @param piece the piece to draw
   * @return its plain texture
   */
  static PieceTexture forPiece(Piece piece) {
    boolean white = piece.color() == PieceColor.WHITE;
    return switch (piece.type()) {
      case PAWN -> white ? WHITE_PAWN : BLACK_PAWN;
      case ROOK -> white ? WHITE_ROOK : BLACK_ROOK;
      case KNIGHT -> white ? WHITE_KNIGHT : BLACK_KNIGHT;
      case BISHOP -> white ? WHITE_BISHOP : BLACK_BISHOP;
      case QUEEN -> white ? WHITE_QUEEN : BLACK_QUEEN;
      case KING -> white ? WHITE_KING : BLACK_KING;
    };
  }

  /**
   * @param type the piece a pawn is promoting to
   * @param color the promoting colour
   * @return the texture for that promotion button
   */
  static PieceTexture forPromotion(PieceType type, PieceColor color) {
    boolean white = color == PieceColor.WHITE;
    return switch (type) {
      case QUEEN -> white ? WHITE_QUEEN_PROMOTION : BLACK_QUEEN_PROMOTION;
      case BISHOP -> white ? WHITE_BISHOP_PROMOTION : BLACK_BISHOP_PROMOTION;
      case KNIGHT -> white ? WHITE_KNIGHT_PROMOTION : BLACK_KNIGHT_PROMOTION;
      case ROOK -> white ? WHITE_ROOK_PROMOTION : BLACK_ROOK_PROMOTION;
      default -> throw new IllegalArgumentException("Cannot promote to " + type);
    };
  }

  /** @return the model data for this texture drawn as the viewer's selected square */
  int selected() {
    return withSuffix(1);
  }

  /** @return the model data for this texture drawn as a legal destination */
  int available() {
    return withSuffix(2);
  }

  private int withSuffix(int suffix) {
    return modelData * 10 + suffix;
  }
}
