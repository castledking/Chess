package codes.castled.chess.engine.api.game;

/**
 * The variant-rule toggles the easter eggs turn on, carried as one value so call sites never
 * pass a pair of bare booleans whose order nobody remembers.
 *
 * @param verticalCastling whether a king may castle vertically with an unmoved rook on its own
 *     file (the Pam-Krabbé castling), which in practice means a rook created by promoting a pawn
 *     on the king's file
 * @param rules1500s whether the 1500s rules are played: no castling at all — which also turns
 *     vertical castling off should both be enabled — pawns that advance a single square only,
 *     no en passant, and one King's Leap per game, a single knight move the king may make
 */
public record EasterEggRules(boolean verticalCastling, boolean rules1500s) {

  /** No easter eggs: standard chess. */
  public static final EasterEggRules STANDARD = new EasterEggRules(false, false);

  /**
   * @return these rules with the 1500s rules switched on, keeping whatever the vertical castling
   *     toggle was — which no longer matters, since those rules forbid castling outright
   */
  public EasterEggRules with1500sRules() {
    return new EasterEggRules(verticalCastling, true);
  }
}
