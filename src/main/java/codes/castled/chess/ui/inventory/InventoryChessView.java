package codes.castled.chess.ui.inventory;

import codes.castled.chess.Chess;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.ui.ChessView;
import codes.castled.chess.ui.ChessViewType;
import codes.castled.chess.ui.DialogSettings;
import codes.castled.chess.ui.DrawItemType;
import codes.castled.chess.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the board as two Bukkit inventories, for servers without Paper's Dialog API.
 *
 * <p>Uses only Bukkit APIs, so it runs on Spigot, Paper and Folia alike. Like the dialog board it
 * is stateless with respect to the position: every hook below re-renders from the authoritative
 * game model rather than mutating individual squares, so a redraw cannot drift from the real
 * board.
 *
 * <p>This board covers core chess only. Premoves, spectating and focus mode are dialog-board
 * features and are deliberate no-ops here — the methods are documented individually. Nothing calls
 * them expecting a return value except {@link #addSpectator(UUID)} and {@link #getGhostSelected},
 * which report honestly that the feature is unavailable.
 */
public final class InventoryChessView implements ChessView {

  private final Chess plugin;
  private final ChessGameHolder holder;
  private final ChessGame game;
  private final MoveCalculator moveCalculator;
  private final DialogSettings settings;
  private final Map<UUID, InventoryBoard> boards = new HashMap<>();

  public InventoryChessView(
      Chess plugin,
      ChessGameHolder holder,
      ChessGame game,
      MoveCalculator moveCalculator,
      DialogSettings settings) {
    this.plugin = plugin;
    this.holder = holder;
    this.game = game;
    this.moveCalculator = moveCalculator;
    this.settings = settings;

    boards.put(game.getWhitePlayerId(), new InventoryBoard(game.getWhitePlayerId(), PieceColor.WHITE));
    boards.put(game.getBlackPlayerId(), new InventoryBoard(game.getBlackPlayerId(), PieceColor.BLACK));
  }

  @Override
  public ChessViewType type() {
    return ChessViewType.INVENTORY;
  }

  /* Lifecycle -------------------------------------------------------- */

  @Override
  public void openBoard(UUID playerId) {
    InventoryBoard board = boards.get(playerId);
    if (board == null) {
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null) {
      return;
    }
    Scheduler.forPlayer(
        plugin,
        player,
        () -> {
          board.open();
          renderBoard(board);
        });
  }

  /**
   * Spectating needs a third board, and the board occupies the viewer's own inventory — a
   * spectator would have their items taken for a game they are not playing. Not offered here.
   *
   * @return always false, so the caller tells the player spectating is unavailable
   */
  @Override
  public boolean addSpectator(UUID spectatorId) {
    return false;
  }

  @Override
  public boolean hasOpen(UUID viewerId) {
    InventoryBoard board = boards.get(viewerId);
    return board != null && board.isOpen();
  }

  @Override
  public boolean isViewer(UUID viewerId) {
    return boards.containsKey(viewerId);
  }

  @Override
  public void removeViewer(UUID viewerId) {
    InventoryBoard board = boards.get(viewerId);
    if (board != null) {
      board.restoreItems();
    }
  }

  @Override
  public void onPlayerLeave(UUID playerId) {
    // Their items are still in storage; hand them back before they are gone. If they are already
    // offline this is a no-op and the items are restored when the game ends.
    removeViewer(playerId);
  }

  @Override
  public void closeAll() {
    for (InventoryBoard board : boards.values()) {
      Player player = Bukkit.getPlayer(board.viewerId());
      board.restoreItems();
      if (player != null && board.isOpen()) {
        player.closeInventory();
      }
    }
  }

  /* Rendering -------------------------------------------------------- */

  @Override
  public void refresh() {
    for (InventoryBoard board : boards.values()) {
      renderBoard(board);
    }
  }

  @Override
  public void refreshViewer(UUID viewerId) {
    InventoryBoard board = boards.get(viewerId);
    if (board != null) {
      renderBoard(board);
    }
  }

  private void renderBoard(InventoryBoard board) {
    Square selected = game.getSelectedPieceSquare(board.viewerId());
    List<Square> legal =
        selected == null ? List.of() : moveCalculator.getPossibleMoves(game, selected);
    board.render(game, selected, legal, settings.showLegalMoves());
  }

  /* Highlighting — each simply re-renders from the model ------------- */

  @Override
  public void highlightSelected(UUID playerId, Square previousSelection, Square selected) {
    refreshViewer(playerId);
  }

  @Override
  public void unhighlightSelected(UUID playerId, Square square) {
    refreshViewer(playerId);
  }

  @Override
  public void highlightLegalMoves(UUID playerId, Square from) {
    refreshViewer(playerId);
  }

  @Override
  public void unhighlightLegalMoves(UUID playerId) {
    refreshViewer(playerId);
  }

  @Override
  public void applyMove(Move move) {
    refresh();
  }

  @Override
  public void removePiece(Square square) {
    refresh();
  }

  @Override
  public void setPiece(Square square, Piece piece) {
    refresh();
  }

  @Override
  public void unhighlightSelectedForBoth(Square destination) {
    refresh();
  }

  @Override
  public void unhighlightLegalMovesForBoth() {
    refresh();
  }

  /** The pack has no last-move texture for the inventory board, so nothing is drawn. */
  @Override
  public void highlightLastMove() {}

  /** The opponent's selection is private on this board; only your own selection is drawn. */
  @Override
  public void highlightOpponentSelection(ChessGame chessGame) {}

  /**
   * The clock is not drawn on the inventory board — there is no room in the control column for the
   * six digit items the dialog board renders, and re-sending the whole board every second to
   * animate it would fight the viewer's cursor.
   */
  @Override
  public void updateClock(long millis) {}

  @Override
  public void clearTimeHighlight(UUID currentTurn) {}

  /* Promotion -------------------------------------------------------- */

  @Override
  public void promptPromotion(UUID playerId, Move pendingMove) {
    InventoryBoard board = boards.get(playerId);
    if (board == null) {
      return;
    }
    board.setPromotionChoices(
        new PieceType[] {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT});
    renderBoard(board);
  }

  @Override
  public void clearPromotion(UUID playerId) {
    InventoryBoard board = boards.get(playerId);
    if (board == null) {
      return;
    }
    board.setPromotionChoices(null);
    renderBoard(board);
  }

  /* Premoves — dialog board only ------------------------------------- */

  /** Premoves are not offered on the inventory board, so there is no ghost to select. */
  @Override
  public void setGhostSelected(UUID viewerId, Square square) {}

  /** @return always null; the inventory board has no premove ghosts */
  @Nullable
  @Override
  public Square getGhostSelected(UUID viewerId) {
    return null;
  }

  /* Status text ------------------------------------------------------ */

  @Override
  public void showInfo(UUID playerId, String message) {
    Player player = Bukkit.getPlayer(playerId);
    if (player != null) {
      player.sendMessage(message);
    }
  }

  @Override
  public void resetInfo() {}

  @Override
  public void cancelInfoTasks() {}

  @Override
  public void setDrawHighlight(DrawItemType state, UUID playerId) {
    InventoryBoard board = boards.get(playerId);
    if (board != null) {
      board.setDrawState(state);
      renderBoard(board);
    }
  }

  @Override
  public void setDrawMessage(String message, UUID playerId) {
    showInfo(playerId, message);
  }

  @Override
  public void setSurrenderHighlight(boolean confirming, UUID playerId) {
    InventoryBoard board = boards.get(playerId);
    if (board != null) {
      board.setResignConfirming(confirming);
      renderBoard(board);
    }
  }

  @Override
  public void setSurrenderMessage(String message, UUID playerId) {
    showInfo(playerId, message);
  }

  /* Click routing ---------------------------------------------------- */

  /**
   * Routes a click on this board into the game.
   *
   * @param viewerId the clicking player
   * @param part which inventory was clicked
   * @param slot the slot within it
   */
  void onClick(UUID viewerId, InventoryPart part, int slot) {
    InventoryBoard board = boards.get(viewerId);
    if (board == null) {
      return;
    }

    if (board.promotionChoices() != null) {
      if (part == InventoryPart.LOWER) {
        PieceType chosen = board.promotionAt(slot);
        if (chosen != null) {
          holder.handlePromotionChoice(viewerId, chosen);
        }
      }
      return;
    }

    if (part == InventoryPart.UPPER) {
      InventoryBoard.Control control = board.controlAt(slot);
      if (control == InventoryBoard.Control.RESIGN) {
        holder.handleSurrenderClick(viewerId);
        return;
      }
      if (control == InventoryBoard.Control.DRAW) {
        holder.handleDrawClick(viewerId);
        return;
      }
    }

    Square square = InventoryLayout.toSquare(new BoardSlot(slot, part), board.color());
    if (square != null) {
      holder.handleBoardClick(viewerId, square);
    }
  }

  /** @return the board belonging to a viewer, or null if they are not in this game */
  @Nullable
  InventoryBoard boardOf(UUID viewerId) {
    return boards.get(viewerId);
  }
}
