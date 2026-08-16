package codes.castled.chess.ui.inventory;

import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameService;
import codes.castled.chess.ui.ChessView;
import codes.castled.chess.ui.ChessViewType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Turns inventory interaction into board clicks, and keeps the board's items from being taken.
 *
 * <p>Every interaction with a chess board is cancelled before it can move an item. The board is a
 * display, not storage, and it occupies the player's own inventory — an uncancelled shift-click or
 * drag would let a player pull chess pieces into the world and drop their stored items.
 */
public final class InventoryBoardListener implements Listener {

  private final GameService gameService;

  public InventoryBoardListener(GameService gameService) {
    this.gameService = gameService;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    InventoryChessView view = viewFor(player.getUniqueId(), event.getView().getTopInventory());
    if (view == null) {
      return;
    }

    // Nothing on either inventory may be picked up while a board is open.
    event.setCancelled(true);

    Inventory clicked = event.getClickedInventory();
    if (clicked == null) {
      return;
    }

    InventoryPart part =
        clicked.equals(event.getView().getTopInventory())
            ? InventoryPart.UPPER
            : InventoryPart.LOWER;

    view.onClick(player.getUniqueId(), part, event.getSlot());
  }

  /** Dragging spans several slots at once, so it is refused outright rather than interpreted. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (viewFor(player.getUniqueId(), event.getView().getTopInventory()) != null) {
      event.setCancelled(true);
    }
  }

  /**
   * Hands the player's own items back when they close the board. The game keeps running, so
   * reopening the board takes them again.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }

    InventoryChessView view = viewFor(player.getUniqueId(), event.getInventory());
    if (view == null) {
      return;
    }

    InventoryBoard board = view.boardOf(player.getUniqueId());
    if (board != null) {
      board.restoreItems();
    }
  }

  /**
   * @param playerId the interacting player
   * @param topInventory the top inventory of their open view
   * @return the inventory board view they are interacting with, or null if this is not one of ours
   */
  @Nullable
  private InventoryChessView viewFor(UUID playerId, Inventory topInventory) {
    InventoryHolder holder = topInventory.getHolder();
    if (!(holder instanceof ChessInventoryHolder chessHolder)
        || !chessHolder.viewerId().equals(playerId)) {
      return null;
    }

    ChessGameHolder game = gameService.getGameByPlayer(playerId);
    if (game == null) {
      return null;
    }

    ChessView view = game.getView();
    if (view.type() != ChessViewType.INVENTORY) {
      return null;
    }
    return (InventoryChessView) view;
  }
}
