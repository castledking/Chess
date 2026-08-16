package codes.castled.chess.ui.inventory;

/**
 * Which of the two inventories a board square lives in.
 *
 * <p>A chess board needs 64 squares and a chest holds 54, so the board spans both the chest and
 * the viewer's own inventory. There is no arrangement that fits a full board on one screen
 * otherwise.
 */
enum InventoryPart {
  /** The 6-row chest, holding the six ranks furthest from the viewer. */
  UPPER,
  /** The viewer's own inventory, holding the two ranks nearest them. */
  LOWER
}
