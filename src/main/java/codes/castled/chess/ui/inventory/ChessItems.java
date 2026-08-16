package codes.castled.chess.ui.inventory;

import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;

/**
 * Builds the items the inventory board is drawn from.
 *
 * <p>Every item is a {@link Material#PAPER} carrying the private chess item model and a
 * {@code custom_model_data} value the pack maps to a texture, so the board is entirely a
 * resource-pack render. Tooltips are hidden: a board square is not a thing to inspect, and the
 * vanilla "Paper" name would show through on hover.
 *
 * <p>Only Bukkit APIs are used, so this works unchanged on Spigot. {@code setItemModel},
 * {@code setHideTooltip} and {@code setCustomModelData} are all upstream Bukkit.
 */
final class ChessItems {

  private ChessItems() {}

  /**
   * @param piece the piece on the square, or null for an empty square
   * @param highlight how the square should be washed
   * @return the item to place in that square's slot
   */
  static ItemStack square(@Nullable Piece piece, Highlight highlight) {
    if (piece == null) {
      return switch (highlight) {
        case NONE -> empty();
        case SELECTED -> model(PieceTexture.EMPTY_SELECTED.modelData());
        case LEGAL -> model(PieceTexture.EMPTY_AVAILABLE.modelData());
      };
    }

    PieceTexture texture = PieceTexture.forPiece(piece);
    return switch (highlight) {
      case NONE -> model(texture.modelData());
      case SELECTED -> model(texture.selected());
      case LEGAL -> model(texture.available());
    };
  }

  /**
   * @param type the piece being promoted to
   * @param color the promoting colour
   * @return the clickable promotion button
   */
  static ItemStack promotion(PieceType type, PieceColor color) {
    return model(PieceTexture.forPromotion(type, color).modelData());
  }

  /**
   * @param modelData the texture to draw
   * @return a control item carrying that texture
   */
  static ItemStack control(int modelData) {
    return model(modelData);
  }

  /** @return an empty square, drawn as nothing so the GUI background shows through */
  static ItemStack empty() {
    return new ItemStack(Material.AIR);
  }

  private static ItemStack model(int modelData) {
    ItemStack item = new ItemStack(Material.PAPER);
    ItemMeta meta = item.getItemMeta();
    meta.setItemModel(ChessItemModel.KEY);
    meta.setCustomModelData(modelData);
    meta.setHideTooltip(true);
    meta.addItemFlags(ItemFlag.values());
    item.setItemMeta(meta);
    return item;
  }

  /** How a board square is washed. Premove and check washes are dialog-board only. */
  enum Highlight {
    /** Drawn plain. */
    NONE,
    /** The viewer's selected source square. */
    SELECTED,
    /** A square the selected piece may move to. */
    LEGAL
  }
}
