package codes.castled.chess;

import codes.castled.chess.command.ChessCommand;
import codes.castled.chess.command.ChessCommandHandler;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.config.UiConfig;
import codes.castled.chess.game.GameService;
import codes.castled.chess.game.SoundPlayer;
import codes.castled.chess.listener.ChessGameListener;
import codes.castled.chess.listener.NetworkPresenceListener;
import codes.castled.chess.net.ChessNetwork;
import codes.castled.chess.net.NetworkFactory;
import codes.castled.chess.net.WebParticipant;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.pack.ResourcePackService;
import codes.castled.chess.request.DuelRequestService;
import codes.castled.chess.ui.ChessViewFactory;
import codes.castled.chess.ui.inventory.InventoryBoardListener;
import codes.castled.chess.util.Platform;
import codes.castled.chess.wiring.EngineFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point: a dialog chess board on Paper, an inventory board on Spigot. RSPM- and Folia-aware. */
public final class Chess extends JavaPlugin {

  private GameService gameService;
  private ChessNetwork network;

  @Override
  public void onEnable() {
    // Manual wiring (no DI framework, so no Guava/Guice in the jar).
    MessageConfig messages = new MessageConfig(this);
    SettingsConfig settings = new SettingsConfig(this);
    UiConfig ui = new UiConfig(this);

    network = NetworkFactory.create(this, settings.getNetworkSettings());
    network.start();

    EngineFactory engine = new EngineFactory(settings.isVerticalCastlingEnabled());
    SoundPlayer sound = new SoundPlayer(this, settings);
    ChessViewFactory viewFactory = new ChessViewFactory(
            this, ui, messages, engine.moveCalculator(), settings.isVerticalCastlingEnabled());
    gameService =
        new GameService(
            this,
            engine.chessGameService(),
            engine.moveCalculator(),
            messages,
            settings,
            sound,
            viewFactory,
            engine,
            network);
    DuelRequestService duelRequestService = new DuelRequestService(this, gameService, messages, settings);
    ResourcePackService resourcePackService = new ResourcePackService(this, settings);

    ChessCommand chessCommand =
        new ChessCommand(this, network, new ChessCommandHandler(messages, gameService, duelRequestService, network));
    getCommand("chess").setExecutor(chessCommand);
    getCommand("chess").setTabCompleter(chessCommand);

    Bukkit.getPluginManager()
        .registerEvents(new ChessGameListener(gameService, resourcePackService), this);
    // Routes clicks on the inventory board. Harmless on Paper, where no board tags its
    // inventory with our holder, so every event falls straight through.
    Bukkit.getPluginManager().registerEvents(new InventoryBoardListener(gameService), this);
    Bukkit.getPluginManager().registerEvents(new NetworkPresenceListener(network), this);

    // A dashboard challenge becomes an ordinary duel request. The challenger is a participant
    // with no Player behind it, exactly like an engine opponent, so accepting it starts a real
    // game that the dashboard then plays through the hub.
    network.setWebChallengeListener(
        (target, challenger, timeMode) -> {
          org.bukkit.entity.Player player = Bukkit.getPlayer(target);
          if (player == null) {
            return;
          }

          WebParticipant web =
              new WebParticipant(java.util.UUID.randomUUID(), challenger);
          gameService.registerWebParticipant(web);

          duelRequestService.sendRequest(
              web.id(),
              target,
              web.name(),
              player.getName(),
              TimeMode.containsMode(timeMode) ? TimeMode.fromDisplayName(timeMode) : TimeMode.TEN);
        });

    network.setWebMoveListener(
        (gameId, notation) -> {
          var holder = gameService.holderFor(gameId);
          if (holder != null) {
            holder.applyWebMove(notation);
          }
        });

    // Extract the bundled pack and hand it to ResourcePackManager when installed.
    resourcePackService.setup();

    getLogger()
        .info(
            "Chess enabled on "
                + Platform.describe()
                + " ("
                + (Platform.hasDialogApi() ? "dialog board available" : "inventory board")
                + ", ui.mode="
                + ui.getViewMode()
                + ").");
  }

  @Override
  public void onDisable() {
    if (gameService != null) {
      gameService.endAllGames();
    }
    if (network != null) {
      network.stop();
    }
  }
}
