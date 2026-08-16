package codes.castled.chess.ui.inventory;

/**
 * A slot index paired with the inventory it belongs to.
 *
 * @param slot the index within that inventory
 * @param part which inventory the index refers to
 */
record BoardSlot(int slot, InventoryPart part) {}
