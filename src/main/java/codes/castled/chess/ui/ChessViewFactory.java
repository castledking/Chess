package codes.castled.chess.ui;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.UiConfig;
import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.ui.inventory.InventoryChessView;
import codes.castled.chess.util.Platform;
import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.engine.api.move.MoveCalculator;

/** Constructs the Paper dialog {@link ChessView} for each game. */
public final class ChessViewFactory {

  private final Chess plugin;
  private final UiConfig uiConfig;
  private final MessageConfig messageConfig;
  private final MoveCalculator moveCalculator;

  /** Whether the vertical castling easter egg is enabled, needed to highlight its premove. */
  private final boolean verticalCastling;

  public ChessViewFactory(
      Chess plugin,
      UiConfig uiConfig,
      MessageConfig messageConfig,
      MoveCalculator moveCalculator,
      boolean verticalCastling) {
    this.plugin = plugin;
    this.uiConfig = uiConfig;
    this.messageConfig = messageConfig;
    this.moveCalculator = moveCalculator;
    this.verticalCastling = verticalCastling;
  }

  /**
   * Creates the view for a game.
   *
   * @param game the game the view will render
   * @return a new dialog view; the caller opens it
   */
  public ChessView create(ChessGameHolder game) {
    DialogSettings settings = uiConfig.getDialogSettings();

    if (useInventoryBoard()) {
      return new InventoryChessView(
          plugin, game, game.getChessGame(), moveCalculator, settings);
    }

    GameStatusEvaluator status = new GameStatusEvaluator(moveCalculator);
    return new DialogChessView(
        game, plugin, settings, moveCalculator, status, messageConfig, verticalCastling);
  }

  /**
   * @return whether to render the inventory board rather than the dialog board
   *     <p>{@code auto} follows the server: the dialog board needs Paper's Dialog API, which
   *     Spigot does not have. An explicit {@code dialog} on a server without that API is refused
   *     rather than honoured, because honouring it would leave players with no board at all.
   */
  private boolean useInventoryBoard() {
    String mode = uiConfig.getViewMode();

    if (mode.equals("inventory")) {
      return true;
    }

    if (mode.equals("dialog") && !Platform.hasDialogApi()) {
      plugin
          .getLogger()
          .warning(
              "ui.mode is 'dialog' but this server has no Dialog API; using the inventory board.");
      return true;
    }

    return !Platform.hasDialogApi();
  }
}
