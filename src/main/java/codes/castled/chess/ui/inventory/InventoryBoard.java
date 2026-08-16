package codes.castled.chess.ui.inventory;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.ui.DrawItemType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * One viewer's board: the chest holding the six far ranks, plus the two rows of their own
 * inventory holding the two near ranks.
 *
 * <p>Because the board occupies the viewer's inventory, their items are taken into storage while
 * the board is open and put back when it closes. {@link #restoreItems()} is idempotent so the
 * several paths that can end a game — closing, resigning, disconnecting, the plugin disabling —
 * cannot double-restore or drop the items.
 *
 * <p>Like the dialog board, this renders from the authoritative game model every time rather than
 * mutating squares incrementally, so a redraw cannot drift from the real position.
 */
final class InventoryBoard {

  /** The control column, top to bottom: info, resign, draw. */
  private static final int INFO_SLOT = InventoryLayout.CONTROL_COLUMN;
  private static final int RESIGN_SLOT = InventoryLayout.CONTROL_COLUMN + 9;
  private static final int DRAW_SLOT = InventoryLayout.CONTROL_COLUMN + 18;

  private static final int INFO_MODEL = 12;
  private static final int RESIGN_MODEL = 13;
  private static final int DRAW_MODEL = 14;

  /** The GUI background glyph, declared by the pack in {@code minecraft:default}. */
  private static final String TITLE = "§f月日";

  private final UUID viewerId;
  private final PieceColor color;
  private final Inventory chest;

  private ItemStack[] storedItems;
  private boolean confirmingResign;
  private DrawItemType drawState = DrawItemType.NONE;
  private PieceType[] promotionChoices;

  InventoryBoard(UUID viewerId, PieceColor color) {
    this.viewerId = viewerId;
    this.color = color;

    ChessInventoryHolder holder = new ChessInventoryHolder(viewerId);
    this.chest = Bukkit.createInventory(holder, InventoryLayout.UPPER_SIZE, TITLE);
    holder.setInventory(chest);
  }

  UUID viewerId() {
    return viewerId;
  }

  PieceColor color() {
    return color;
  }

  Inventory chest() {
    return chest;
  }

  /** @return whether this viewer currently has the board open */
  boolean isOpen() {
    Player player = Bukkit.getPlayer(viewerId);
    return player != null && chest.equals(player.getOpenInventory().getTopInventory());
  }

  /**
   * Takes the viewer's items into storage and shows the board. Does nothing if they are offline.
   */
  void open() {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }

    if (storedItems == null) {
      storedItems = player.getInventory().getContents();
      player.getInventory().clear();
    }

    player.openInventory(chest);
  }

  /**
   * Puts the viewer's own items back. Safe to call more than once and when they are offline; in
   * that case the items stay in storage until they are restored on rejoin.
   */
  void restoreItems() {
    if (storedItems == null) {
      return;
    }

    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }

    ItemStack[] items = storedItems;
    storedItems = null;
    player.getInventory().setContents(items);
    player.updateInventory();
  }

  /** @return whether this viewer's items are currently held in storage */
  boolean hasStoredItems() {
    return storedItems != null;
  }

  void setResignConfirming(boolean confirming) {
    this.confirmingResign = confirming;
  }

  void setDrawState(DrawItemType state) {
    this.drawState = state;
  }

  /**
   * Puts the board into promotion mode, replacing the near rank with the four choices.
   *
   * @param choices the promotion pieces offered, in display order
   */
  void setPromotionChoices(@Nullable PieceType[] choices) {
    this.promotionChoices = choices;
  }

  @Nullable
  PieceType[] promotionChoices() {
    return promotionChoices;
  }

  /**
   * Redraws every square and control from the game model.
   *
   * @param game the authoritative game
   * @param selected the viewer's selected square, or null
   * @param legalMoves destinations to mark, empty when nothing is selected
   * @param showLegalMoves whether the legal-move wash is enabled in config
   */
  void render(ChessGame game, @Nullable Square selected, List<Square> legalMoves, boolean showLegalMoves) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }

    ChessBoard board = game.getChessBoard();
    ItemStack[] lower = new ItemStack[36];

    for (char row = '1'; row <= '8'; row++) {
      for (char column = 'A'; column <= 'H'; column++) {
        Square square = new Square(row, column);
        Piece piece = board.getPiece(square);

        ChessItems.Highlight highlight = ChessItems.Highlight.NONE;
        if (square.equals(selected)) {
          highlight = ChessItems.Highlight.SELECTED;
        } else if (showLegalMoves && legalMoves.contains(square)) {
          highlight = ChessItems.Highlight.LEGAL;
        }

        BoardSlot slot = InventoryLayout.toSlot(square, color);
        ItemStack item = ChessItems.square(piece, highlight);

        if (slot.part() == InventoryPart.UPPER) {
          chest.setItem(slot.slot(), item);
        } else {
          lower[slot.slot()] = item;
        }
      }
    }

    renderControls();

    if (promotionChoices != null) {
      overlayPromotion(lower);
    }

    player.getInventory().setContents(lower);
    player.updateInventory();
  }

  private void renderControls() {
    chest.setItem(INFO_SLOT, ChessItems.control(INFO_MODEL));
    chest.setItem(
        RESIGN_SLOT,
        ChessItems.control(confirmingResign ? RESIGN_MODEL * 10 + 1 : RESIGN_MODEL));
    chest.setItem(DRAW_SLOT, ChessItems.control(drawModel()));

    // The rest of the control column stays empty so the background reads cleanly.
    // (see drawModel for why the draw suffixes are spelled out rather than derived)
    for (int slot = DRAW_SLOT + 9; slot < InventoryLayout.UPPER_SIZE; slot += 9) {
      chest.setItem(slot, ChessItems.empty());
    }
  }

  /**
   * @return the draw control's texture for the current state
   *     <p>The suffixes are written out rather than taken from the enum's ordinal: they are
   *     thresholds in the resource pack, so reordering {@link DrawItemType} for any unrelated
   *     reason would silently repaint this control.
   */
  private int drawModel() {
    return switch (drawState) {
      case NONE -> DRAW_MODEL;
      case CONFIRM -> DRAW_MODEL * 10 + 1;
      case ACCEPT -> DRAW_MODEL * 10 + 2;
      case SENT -> DRAW_MODEL * 10 + 3;
    };
  }

  /**
   * Draws the promotion choices over the viewer's own rows, where they are closest to hand. The
   * board underneath is unreachable until a choice is made, which matches the engine refusing any
   * other move while a promotion is pending.
   */
  private void overlayPromotion(ItemStack[] lower) {
    java.util.Arrays.fill(lower, ChessItems.empty());
    for (int index = 0; index < promotionChoices.length; index++) {
      lower[PROMOTION_SLOTS[index]] = ChessItems.promotion(promotionChoices[index], color);
    }
  }

  /** Centred across the viewer's first visible row. */
  static final int[] PROMOTION_SLOTS = {11, 12, 13, 14};

  /**
   * @param slot a slot in the viewer's own inventory
   * @return the promotion piece that slot offers, or null if it offers none
   */
  @Nullable
  PieceType promotionAt(int slot) {
    if (promotionChoices == null) {
      return null;
    }
    for (int index = 0; index < promotionChoices.length && index < PROMOTION_SLOTS.length; index++) {
      if (PROMOTION_SLOTS[index] == slot) {
        return promotionChoices[index];
      }
    }
    return null;
  }

  /**
   * @param slot a slot in the chest
   * @return which control was clicked, or null for none
   */
  @Nullable
  Control controlAt(int slot) {
    if (slot == RESIGN_SLOT) {
      return Control.RESIGN;
    }
    if (slot == DRAW_SLOT) {
      return Control.DRAW;
    }
    return null;
  }

  /** The clickable controls in the chest's spare column. */
  enum Control {
    RESIGN,
    DRAW
  }
}
