package codes.castled.chess.ui.inventory;

import org.bukkit.NamespacedKey;

/**
 * The {@code minecraft:item_model} component key every chess inventory item carries.
 *
 * <p>The piece, clock and menu textures could live on vanilla {@code minecraft:paper} selected by
 * {@code custom_model_data}, but that item definition is shared: ResourcePackManager merges the
 * {@code range_dispatch} entries of every installed pack into it, and any other pack putting
 * textures on paper then fights ours for the same integers, rendering chess pieces over their
 * items and vice versa.
 *
 * <p>Pointing our items at this private key makes the client read the thresholds from our own
 * {@code assets/chess/items/chess.json}, which no other pack declares — the same namespace
 * isolation the dialog board's glyph font uses.
 */
final class ChessItemModel {

  /** Resolves to {@code assets/chess/items/chess.json} on the client. */
  static final NamespacedKey KEY = new NamespacedKey("chess", "chess");

  private ChessItemModel() {}
}
