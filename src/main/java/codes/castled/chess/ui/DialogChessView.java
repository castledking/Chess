package codes.castled.chess.ui;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.util.Scheduler;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceType;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * The native Paper dialog chess backend. No Bukkit inventory is ever opened.
 *
 * <p>Stateless with respect to the board: on any change it re-reads the authoritative game and
 * re-renders the dialog for the affected viewer(s) via {@code player.showDialog}, which replaces
 * the currently-shown dialog. The fine-grained {@link ChessView} hooks therefore mostly request
 * a re-render.
 *
 * <p>Click staleness is guarded by a per-game move sequence embedded in every clickable button.
 * It is bumped once per authoritative game change ({@link #refresh()}) but not by selection or
 * clock ticks, so an in-progress selection or a clock update never invalidates a legitimate
 * destination click, while a click from a board rendered before an opponent's move is rejected.
 *
 * <p>All game-state mutations are marshalled onto the global region thread; dialog sends are
 * marshalled onto each viewer's own thread, so the backend is Folia-safe.
 */
public final class DialogChessView implements ChessView, BoardClicks {

  private final ChessGameHolder game;
  private final Chess plugin;
  private final DialogViewerRegistry registry = new DialogViewerRegistry();
  private final PaperBoardDialog boardDialog;
  private final PaperPromotionDialog promotionDialog;

  private volatile long moveSequence;

  public DialogChessView(
      ChessGameHolder game,
      Chess plugin,
      DialogSettings settings,
      MoveCalculator moveCalculator,
      GameStatusEvaluator status,
      MessageConfig messages,
      boolean verticalCastling) {
    this.game = game;
    this.plugin = plugin;
    PieceGlyph glyph = new PieceGlyph(settings.useGlyphs());
    // Buttons live for the game's duration and can be clicked repeatedly (the board is
    // re-shown after every click), so uses are unlimited with a generous lifetime.
    ClickCallback.Options options =
        ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();
    this.boardDialog =
        new PaperBoardDialog(
            settings, glyph, moveCalculator, status, messages, options, verticalCastling);
    this.promotionDialog = new PaperPromotionDialog(settings, glyph, messages, options);
  }

  @Override
  public ChessViewType type() {
    return ChessViewType.DIALOG;
  }

  /* Lifecycle -------------------------------------------------------- */

  @Override
  public void openBoard(UUID playerId) {
    registry.addPlayer(playerId);
    renderViewer(playerId, moveSequence);
  }

  @Override
  public boolean addSpectator(UUID spectatorId) {
    registry.addSpectator(spectatorId);
    renderViewer(spectatorId, moveSequence);
    return true;
  }

  @Override
  public boolean hasOpen(UUID viewerId) {
    DialogViewerRegistry.ViewerState state = registry.get(viewerId);
    return state != null && state.isDisplaying();
  }

  @Override
  public boolean isViewer(UUID viewerId) {
    return registry.isViewer(viewerId);
  }

  @Override
  public void removeViewer(UUID viewerId) {
    registry.remove(viewerId);
  }

  @Override
  public void onPlayerLeave(UUID playerId) {
    // The client dialog is gone once the player leaves; stop reopening it, but keep the player
    // tracked so "/chess open" (or reconnect) can reopen the active game.
    registry.setDisplaying(playerId, false);
  }

  @Override
  public void closeAll() {
    registry.clear();
  }

  /* Selection (per viewer) -> re-render that viewer --------------------- */

  @Override
  public void highlightSelected(UUID playerId, Square previousSelection, Square selected) {
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void unhighlightSelected(UUID playerId, Square square) {
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void highlightLegalMoves(UUID playerId, Square from) {
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void unhighlightLegalMoves(UUID playerId) {
    renderViewer(playerId, moveSequence);
  }

  /* Board mutations: no-op (authoritative model is the source) ---------- */

  @Override
  public void applyMove(Move move) {}

  @Override
  public void removePiece(Square square) {}

  @Override
  public void setPiece(Square square, Piece piece) {}

  @Override
  public void unhighlightSelectedForBoth(Square destination) {}

  @Override
  public void unhighlightLegalMovesForBoth() {}

  @Override
  public void highlightLastMove() {}

  @Override
  public void highlightOpponentSelection(ChessGame chessGame) {}

  @Override
  public void updateClock(long millis) {
    for (UUID viewerId : registry.viewerIds()) {
      DialogViewerRegistry.ViewerState state = registry.get(viewerId);
      if (state != null && state.isDisplaying()) {
        renderViewer(viewerId, moveSequence);
      }
    }
  }

  @Override
  public void clearTimeHighlight(UUID currentTurn) {}

  /* Promotion -------------------------------------------------------- */

  @Override
  public void promptPromotion(UUID playerId, Move pendingMove) {
    registry.setPendingPromotion(playerId, pendingMove);
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void clearPromotion(UUID playerId) {
    registry.setPendingPromotion(playerId, null);
  }

  @Override
  public void setGhostSelected(UUID viewerId, Square square) {
    registry.setGhostSelected(viewerId, square);
  }

  @Override
  public Square getGhostSelected(UUID viewerId) {
    DialogViewerRegistry.ViewerState state = registry.get(viewerId);
    return state == null ? null : state.ghostSelected();
  }

  /* Status / info ---------------------------------------------------- */

  @Override
  public void showInfo(UUID playerId, String message) {
    registry.setInfo(playerId, message);
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void resetInfo() {
    for (UUID viewerId : registry.viewerIds()) {
      registry.setInfo(viewerId, "");
    }
  }

  @Override
  public void cancelInfoTasks() {}

  /* Draw / surrender display state ----------------------------------- */

  @Override
  public void setDrawHighlight(DrawItemType state, UUID playerId) {
    registry.setDraw(playerId, state, null);
  }

  @Override
  public void setDrawMessage(String message, UUID playerId) {
    registry.setDraw(playerId, null, message);
    renderViewer(playerId, moveSequence);
  }

  @Override
  public void setSurrenderHighlight(boolean confirming, UUID playerId) {
    registry.setSurrender(playerId, confirming, null);
  }

  @Override
  public void setSurrenderMessage(String message, UUID playerId) {
    registry.setSurrender(playerId, null, message);
    renderViewer(playerId, moveSequence);
  }

  /* Full refresh ----------------------------------------------------- */

  @Override
  public void refresh() {
    long sequence = ++moveSequence;
    for (UUID viewerId : registry.viewerIds()) {
      renderViewer(viewerId, sequence);
    }
  }

  @Override
  public void refreshViewer(UUID viewerId) {
    renderViewer(viewerId, moveSequence);
  }

  /* BoardClicks (button callbacks) ----------------------------------- */

  @Override
  public void onCell(UUID viewerId, Square square, long sequence) {
    Scheduler.global(
        plugin,
        () -> {
          if (sequence != moveSequence) {
            refreshViewer(viewerId);
            return;
          }
          if (registry.isSpectator(viewerId)) {
            return;
          }
          game.handleBoardClick(viewerId, square);
        });
  }

  @Override
  public void onResign(UUID viewerId) {
    Scheduler.global(
        plugin,
        () -> {
          if (!registry.isSpectator(viewerId)) {
            game.handleSurrenderClick(viewerId);
          }
        });
  }

  @Override
  public void onDraw(UUID viewerId) {
    Scheduler.global(
        plugin,
        () -> {
          if (!registry.isSpectator(viewerId)) {
            game.handleDrawClick(viewerId);
          }
        });
  }

  @Override
  public void onCancelPremoves(UUID viewerId) {
    Scheduler.global(
        plugin,
        () -> {
          if (!registry.isSpectator(viewerId)) {
            game.handleCancelPremoves(viewerId);
          }
        });
  }

  @Override
  public void onFlip(UUID viewerId) {
    Scheduler.global(
        plugin,
        () -> {
          registry.toggleFlip(viewerId);
          renderViewer(viewerId, moveSequence);
        });
  }

  @Override
  public void onFocus(UUID viewerId) {
    Scheduler.global(
        plugin,
        () -> {
          registry.toggleFocus(viewerId);
          renderViewer(viewerId, moveSequence);
        });
  }

  @Override
  public void onClose(UUID viewerId, Audience audience) {
    registry.setDisplaying(viewerId, false);
    audience.closeDialog();
    if (registry.isSpectator(viewerId)) {
      registry.remove(viewerId);
    }
  }

  @Override
  public void onPromote(UUID viewerId, PieceType type, long sequence) {
    Scheduler.global(
        plugin,
        () -> {
          if (sequence != moveSequence) {
            refreshViewer(viewerId);
            return;
          }
          game.handlePromotionChoice(viewerId, type);
        });
  }

  /* Rendering -------------------------------------------------------- */

  private void renderViewer(UUID viewerId, long sequence) {
    DialogViewerRegistry.ViewerState state = registry.get(viewerId);
    if (state == null) {
      return;
    }
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      registry.setDisplaying(viewerId, false);
      return;
    }

    Dialog dialog;
    if (!state.isSpectator() && state.hasPendingPromotion()) {
      dialog = promotionDialog.build(viewerId, sequence, game.getColor(viewerId), this);
    } else {
      dialog =
          boardDialog.build(
              game.getChessGame(), viewerId, state, sequence, this, game.getPremoves(viewerId));
    }

    Scheduler.forPlayer(plugin, player, () -> player.showDialog(dialog));
    registry.setDisplaying(viewerId, true);
  }
}
