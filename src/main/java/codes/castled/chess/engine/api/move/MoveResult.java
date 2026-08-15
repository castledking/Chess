package codes.castled.chess.engine.api.move;

/**
 * Represents a move result. A move can be normal or special (Castling, EnPassant, Promotion).
 *
 * @param type whether the move was successful or illegal
 * @param castling whether a castling move was played
 * @param enPassant whether an en passant move was played
 * @param promotion whether a promotion move was played
 * @param checkmate whether checkmate was caused
 */
public record MoveResult(
    MoveResultType type,
    boolean castling,
    boolean enPassant,
    boolean promotion,
    boolean checkmate) {}
