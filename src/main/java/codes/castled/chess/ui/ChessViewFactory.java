package codes.castled.chess.ui;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.UiConfig;
import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.engine.api.move.MoveCalculator;

/** Constructs the Paper dialog {@link ChessView} for each game. */
public final class ChessViewFactory {

  private final Chess plugin;
  private final UiConfig uiConfig;
  private final MessageConfig messageConfig;
  private final MoveCalculator moveCalculator;

  public ChessViewFactory(
      Chess plugin, UiConfig uiConfig, MessageConfig messageConfig, MoveCalculator moveCalculator) {
    this.plugin = plugin;
    this.uiConfig = uiConfig;
    this.messageConfig = messageConfig;
    this.moveCalculator = moveCalculator;
  }

  /**
   * Creates the view for a game.
   *
   * @param game the game the view will render
   * @return a new dialog view; the caller opens it
   */
  public ChessView create(ChessGameHolder game) {
    DialogSettings settings = uiConfig.getDialogSettings();
    GameStatusEvaluator status = new GameStatusEvaluator(moveCalculator);
    return new DialogChessView(game, plugin, settings, moveCalculator, status, messageConfig);
  }
}
