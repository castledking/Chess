package codes.castled.chess.ui;

/** Which rendering backend a {@link ChessView} is. */
public enum ChessViewType {
  /** Paper's native dialog board. */
  DIALOG,
  /** The Bukkit inventory board, used where the Dialog API is unavailable. */
  INVENTORY
}
