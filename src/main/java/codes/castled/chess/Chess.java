package codes.castled.chess;

import codes.castled.chess.command.ChessCommand;
import codes.castled.chess.command.ChessCommandHandler;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.config.UiConfig;
import codes.castled.chess.game.GameService;
import codes.castled.chess.game.SoundPlayer;
import codes.castled.chess.listener.ChessGameListener;
import codes.castled.chess.pack.ResourcePackService;
import codes.castled.chess.request.DuelRequestService;
import codes.castled.chess.ui.ChessViewFactory;
import codes.castled.chess.wiring.EngineFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point: a native Paper dialog chess board, RSPM- and Folia-aware. */
public final class Chess extends JavaPlugin {

  private GameService gameService;

  @Override
  public void onEnable() {
    // Manual wiring (no DI framework, so no Guava/Guice in the jar).
    MessageConfig messages = new MessageConfig(this);
    SettingsConfig settings = new SettingsConfig(this);
    UiConfig ui = new UiConfig(this);

    EngineFactory engine = new EngineFactory(settings.isVerticalCastlingEnabled());
    SoundPlayer sound = new SoundPlayer(this, settings);
    ChessViewFactory viewFactory = new ChessViewFactory(
            this, ui, messages, engine.moveCalculator(), settings.isVerticalCastlingEnabled());
    gameService =
        new GameService(
            this, engine.chessGameService(), engine.moveCalculator(), messages, settings, sound, viewFactory);
    DuelRequestService duelRequestService = new DuelRequestService(this, gameService, messages, settings);
    ResourcePackService resourcePackService = new ResourcePackService(this, settings);

    ChessCommand chessCommand =
        new ChessCommand(this, new ChessCommandHandler(messages, gameService, duelRequestService));
    getCommand("chess").setExecutor(chessCommand);
    getCommand("chess").setTabCompleter(chessCommand);

    Bukkit.getPluginManager()
        .registerEvents(new ChessGameListener(gameService, resourcePackService), this);

    // Extract the bundled pack and hand it to ResourcePackManager when installed.
    resourcePackService.setup();

    getLogger().info("Chess enabled: native Paper dialog board.");
  }

  @Override
  public void onDisable() {
    if (gameService != null) {
      gameService.endAllGames();
    }
  }
}
