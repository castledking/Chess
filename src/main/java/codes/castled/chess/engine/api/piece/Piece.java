package codes.castled.chess.engine.api.piece;

/**
 * Represents a chess piece.
 *
 * @param type the piece type (f.e. Pawn)
 * @param color the piece color which is either white or black
 */
public record Piece(PieceType type, PieceColor color) {
}
