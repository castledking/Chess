package codes.castled.chess.ui.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marks a chest inventory as one of ours and records whose board it is.
 *
 * <p>The click listener has to decide, for every inventory click on the server, whether the click
 * belongs to a chess board. Tagging the inventory with a holder answers that in one instanceof
 * rather than by comparing inventory instances against every running game.
 */
public final class ChessInventoryHolder implements InventoryHolder {

  private final UUID viewerId;
  private Inventory inventory;

  ChessInventoryHolder(UUID viewerId) {
    this.viewerId = viewerId;
  }

  /** @return the viewer this board belongs to */
  public UUID viewerId() {
    return viewerId;
  }

  void setInventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
