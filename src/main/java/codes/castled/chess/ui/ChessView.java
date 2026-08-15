package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.move.Move;
import com.dxzell.pocketchess.api.piece.Piece;

import java.util.UUID;

/**
 * Rendering abstraction that decouples the chess game/rules from the UI. A view is owned by
 * a single {@code ChessGameHolder} and always renders from the authoritative game model — it
 * never stores its own copy of the board.
 *
 * <p>The only implementation is the stateless Paper {@code DialogChessView}: it re-reads the
 * game model and rebuilds the dialog on {@link #refresh()} / {@link #refreshViewer(UUID)}, so
 * the fine-grained highlight/mutation hooks below mostly just request a re-render.
 *
 * <p>Game-state methods are called on the global region thread; implementations must not
 * assume a viewer is online and no-op when a player is absent.
 */
public interface ChessView {

  /** @return which backend this view is */
  ChessViewType type();

  /* Lifecycle -------------------------------------------------------- */

  /** Opens (or re-opens/replaces) the board for a player who belongs to the game. */
  void openBoard(UUID playerId);

  /**
   * Registers a spectator and opens a read-only board for them.
   *
   * @return {@code true} if the spectator was added, {@code false} if unsupported
   */
  boolean addSpectator(UUID spectatorId);

  /** @return whether the board is currently displayed to the given viewer */
  boolean hasOpen(UUID viewerId);

  /** @return whether the given id is a tracked viewer (player or spectator) */
  boolean isViewer(UUID viewerId);

  /** Stops tracking a viewer and closes their view (disconnect, manual close, quit). */
  void removeViewer(UUID viewerId);

  /**
   * Hook for the leave-type events the plugin listens to (quit, world change, close, lethal
   * damage). Stops reopening the dialog for the viewer; never resigns the game.
   */
  void onPlayerLeave(UUID playerId);

  /** Closes and clears every viewer's board (game end / plugin disable). */
  void closeAll();

  /* Selection (per viewer) ------------------------------------------- */

  void highlightSelected(UUID playerId, Square previousSelection, Square selected);

  void unhighlightSelected(UUID playerId, Square square);

  void highlightLegalMoves(UUID playerId, Square from);

  void unhighlightLegalMoves(UUID playerId);

  /* Board mutations / move completion -------------------------------- */

  void applyMove(Move move);

  void removePiece(Square square);

  void setPiece(Square square, Piece piece);

  void unhighlightSelectedForBoth(Square destination);

  void unhighlightLegalMovesForBoth();

  void highlightLastMove();

  void highlightOpponentSelection(ChessGame game);

  void updateClock(long millis);

  void clearTimeHighlight(UUID currentTurn);

  /* Promotion -------------------------------------------------------- */

  /**
   * Prompts the given player to choose a promotion piece for a pending move.
   *
   * @param playerId the promoting player
   * @param pendingMove the from/to squares of the pawn's promoting move
   */
  void promptPromotion(UUID playerId, Move pendingMove);

  /** Removes any active promotion prompt for the player. */
  void clearPromotion(UUID playerId);

  /** Marks a ghost square (premove destination) as selected for the viewer. */
  void setGhostSelected(UUID viewerId, Square square);

  /** @return the ghost-selected square for the viewer, or {@code null} */
  Square getGhostSelected(UUID viewerId);

  /* Status / info line ----------------------------------------------- */

  void showInfo(UUID playerId, String message);

  void resetInfo();

  void cancelInfoTasks();

  /* Draw / surrender display state ----------------------------------- */

  void setDrawHighlight(DrawItemType state, UUID playerId);

  void setDrawMessage(String message, UUID playerId);

  void setSurrenderHighlight(boolean confirming, UUID playerId);

  void setSurrenderMessage(String message, UUID playerId);

  /* Full refresh (rebuilds and re-displays) -------------------------- */

  /** Rebuilds and re-displays the board for every current viewer. */
  void refresh();

  /** Rebuilds and re-displays the board for a single viewer. */
  void refreshViewer(UUID viewerId);
}
