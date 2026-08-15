package codes.castled.chess.engine.api.move;

import javax.annotation.Nullable;

/**
 * Represents the outcome of a played move.
 *
 * @param type whether the move was legal
 * @param enPassant whether the move captured en passant
 * @param promotion whether the move reached the promotion row and awaits a piece choice
 * @param checkmate whether the move ended the game
 * @param rookMove the rook's half of a castling move, or null when the move was not castling.
 *     Carrying it here is what lets callers apply castling without re-deriving the rook's
 *     squares from the king's destination — a derivation that only holds for standard chess.
 */
public record MoveResult(
    MoveResultType type,
    boolean enPassant,
    boolean promotion,
    boolean checkmate,
    @Nullable Move rookMove) {

  /** @return whether the move was a castling move */
  public boolean castling() {
    return rookMove != null;
  }

  /** @return a result for a move that was not legal */
  public static MoveResult illegal() {
    return new MoveResult(MoveResultType.ILLEGAL, false, false, false, null);
  }

  /** @return a result for an ordinary legal move */
  public static MoveResult success() {
    return new MoveResult(MoveResultType.SUCCESS, false, false, false, null);
  }

  /** @return a result for a legal en passant capture */
  public static MoveResult enPassantCapture() {
    return new MoveResult(MoveResultType.SUCCESS, true, false, false, null);
  }

  /** @return a result for a move that reached the promotion row */
  public static MoveResult pendingPromotion() {
    return new MoveResult(MoveResultType.SUCCESS, false, true, false, null);
  }

  /**
   * @param rookMove the rook's half of the castling move
   * @return a result for a legal castling move
   */
  public static MoveResult castlingWith(Move rookMove) {
    return new MoveResult(MoveResultType.SUCCESS, false, false, false, rookMove);
  }
}
