package codes.castled.chess.ui;

/**
 * The state of a player's draw control, used to drive the draw button's label and the
 * message shown beneath the board. Carried over from the inventory era where each value
 * also selected an item texture; here only the state distinction matters.
 */
public enum DrawItemType {
  /** No active draw offer. */
  NONE,
  /** This player must click again to confirm accepting a draw. */
  CONFIRM,
  /** This player has an incoming draw offer they may accept. */
  ACCEPT,
  /** This player has sent a draw offer and is awaiting a response. */
  SENT
}
