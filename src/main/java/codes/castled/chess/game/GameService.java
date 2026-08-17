package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.bot.BotDifficulty;
import codes.castled.chess.bot.ChessBot;
import codes.castled.chess.bot.SearchBot;
import codes.castled.chess.net.ChessNetwork;
import codes.castled.chess.net.WebParticipant;
import codes.castled.chess.ui.ChessViewFactory;
import codes.castled.chess.wiring.EngineFactory;

import javax.annotation.Nullable;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.ChessGameService;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveCalculator;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the plugin-side chess games ({@link ChessGameHolder}s) that wrap the engine's
 * authoritative games with a view, clock and handlers. Delegates rules/model creation to the
 * engine's {@link ChessGameService}.
 */
public final class GameService {

  private final Map<UUID, ChessGameHolder> games = new ConcurrentHashMap<>();
  private final Chess plugin;
  private final ChessGameService chessService;
  private final MoveCalculator moveCalculator;
  private final MessageConfig messageConfig;
  private final SettingsConfig settingsConfig;
  private final SoundPlayer soundPlayer;
  private final ChessViewFactory viewFactory;
  private final EngineFactory engineFactory;
  private final ChessNetwork network;

  /**
   * The engine opponents currently playing, keyed by the id each plays under. A bot stands in for
   * a player everywhere else in the plugin, so this is the only place that knows the difference.
   */
  private final Map<UUID, ChessBot> bots = new ConcurrentHashMap<>();

  /**
   * People playing from the dashboard, keyed by the id each plays under. Held for the same reason
   * as bots: everywhere else treats them as a participant who happens never to be online.
   */
  private final Map<UUID, WebParticipant> webPlayers = new ConcurrentHashMap<>();

  public GameService(
      Chess plugin,
      ChessGameService chessService,
      MoveCalculator moveCalculator,
      MessageConfig messageConfig,
      SettingsConfig settingsConfig,
      SoundPlayer soundPlayer,
      ChessViewFactory viewFactory,
      EngineFactory engineFactory,
      ChessNetwork network) {
    this.engineFactory = engineFactory;
    this.network = network;
    this.plugin = plugin;
    this.chessService = chessService;
    this.moveCalculator = moveCalculator;
    this.messageConfig = messageConfig;
    this.settingsConfig = settingsConfig;
    this.soundPlayer = soundPlayer;
    this.viewFactory = viewFactory;
  }

  /**
   * Creates a chess game and its plugin-side holder.
   *
   * @param whitePlayer the player who starts with white
   * @param blackPlayer the player who starts with black
   * @param timeMode the time control
   * @return the result of the game creation attempt
   */
  public GameCreationResult createGame(Player whitePlayer, Player blackPlayer, TimeMode timeMode) {
    if (whitePlayer == null || blackPlayer == null) {
      throw new IllegalArgumentException("Player objects must not be null.");
    }

    GameCreationResult result =
        chessService.createGame(whitePlayer.getUniqueId(), blackPlayer.getUniqueId(), timeMode);
    if (result.type() == GameCreationResultType.SUCCESS) {
      ChessGame game = result.game();
      games.put(
          game.getGameId(),
          new ChessGameHolder(
              plugin,
              this,
              game,
              whitePlayer.getUniqueId(),
              blackPlayer.getUniqueId(),
              moveCalculator,
              messageConfig,
              settingsConfig,
              soundPlayer,
              viewFactory,
              network));
    }
    return result;
  }

  /**
   * Starts a game against an engine opponent.
   *
   * <p>The bot is given its own id and stands in for a player from then on: it holds a colour,
   * has a clock, and can be checkmated. Everything downstream treats it as a participant who
   * simply never happens to be online, which the views and messaging already handle because they
   * were written to no-op for absent players.
   *
   * @param player the human, who plays white so they move first
   * @param difficulty how strong the opponent should be
   * @param timeMode the time control
   * @return the result of the game creation attempt
   */
  public GameCreationResult createCpuGame(
      Player player, BotDifficulty difficulty, TimeMode timeMode) {

    ChessBot bot =
        new SearchBot(UUID.randomUUID(), difficulty, engineFactory, new java.util.Random());

    GameCreationResult result =
        chessService.createGame(player.getUniqueId(), bot.id(), timeMode);

    if (result.type() != GameCreationResultType.SUCCESS) {
      return result;
    }

    bots.put(bot.id(), bot);
    ChessGame game = result.game();
    games.put(
        game.getGameId(),
        new ChessGameHolder(
            plugin,
            this,
            game,
            player.getUniqueId(),
            bot.id(),
            moveCalculator,
            messageConfig,
            settingsConfig,
            soundPlayer,
            viewFactory,
            network));
    return result;
  }

  /**
   * Starts a game between someone on the dashboard and someone in game.
   *
   * <p>The web player takes white, since they issued the challenge and so move first.
   *
   * @param web the dashboard player
   * @param player the in-game player
   * @param timeMode the time control
   * @return the result of the game creation attempt
   */
  public GameCreationResult createWebGame(
      WebParticipant web, Player player, TimeMode timeMode) {

    GameCreationResult result =
        chessService.createGame(web.id(), player.getUniqueId(), timeMode);

    if (result.type() != GameCreationResultType.SUCCESS) {
      return result;
    }

    webPlayers.put(web.id(), web);
    ChessGame game = result.game();
    games.put(
        game.getGameId(),
        new ChessGameHolder(
            plugin,
            this,
            game,
            web.id(),
            player.getUniqueId(),
            moveCalculator,
            messageConfig,
            settingsConfig,
            soundPlayer,
            viewFactory,
            network));
    return result;
  }

  /** Registers a pending dashboard challenger so a duel request can name them. */
  public void registerWebParticipant(WebParticipant web) {
    webPlayers.put(web.id(), web);
  }

  /**
   * @param playerId an id holding a colour in some game
   * @return the dashboard player under that id, or null if it is not one
   */
  @Nullable
  public WebParticipant webParticipantFor(UUID playerId) {
    return webPlayers.get(playerId);
  }

  /**
   * @param gameId a game id
   * @return the game with that id, or null
   */
  @Nullable
  public ChessGameHolder holderFor(UUID gameId) {
    return games.get(gameId);
  }

  /**
   * @param playerId an id holding a colour in some game
   * @return the engine opponent playing under that id, or null if it belongs to a real player
   */
  @Nullable
  public ChessBot botFor(UUID playerId) {
    return bots.get(playerId);
  }

  /** Forgets the bot that played under the given id, releasing anything it held. */
  void releaseBot(UUID playerId) {
    ChessBot bot = bots.remove(playerId);
    if (bot != null) {
      bot.close();
    }
    webPlayers.remove(playerId);
  }

  /**
   * Ends a running game with no winner.
   *
   * @param gameId the id of the game
   * @return whether a game was ended
   */
  public boolean endGameById(UUID gameId) {
    ChessGameHolder holder = games.get(gameId);
    if (holder != null && chessService.endGameById(gameId)) {
      holder.endGame(null);
      games.remove(gameId);
      return true;
    }
    return false;
  }

  /**
   * Ends the running game a player is in, with no winner.
   *
   * @param playerId a player in the game
   * @return whether a game was ended
   */
  public boolean endGameByPlayerId(UUID playerId) {
    ChessGameHolder holder = getGameByPlayer(playerId);
    if (holder != null && chessService.endGameByPlayer(playerId)) {
      holder.endGame(null);
      games.remove(holder.getChessGame().getGameId());
      return true;
    }
    return false;
  }

  public void removeGame(UUID gameId) {
    ChessGameHolder holder = games.get(gameId);
    if (holder != null) {
      releaseBot(holder.getChessGame().getWhitePlayerId());
      releaseBot(holder.getChessGame().getBlackPlayerId());
    }
    games.remove(gameId);
  }

  /** @return the holder for the given game id, or {@code null} */
  public ChessGameHolder getGameById(UUID gameId) {
    return games.get(gameId);
  }

  public List<ChessGameHolder> getGames() {
    return List.copyOf(games.values());
  }

  /** Ends all currently running games. */
  public void endAllGames() {
    for (ChessGameHolder holder : List.copyOf(games.values())) {
      holder.endGame(null);
    }
    games.clear();
  }

  /** @return the holder for the game the player is in, or {@code null} */
  public ChessGameHolder getGameByPlayer(UUID playerId) {
    for (ChessGameHolder holder : games.values()) {
      if (holder.getChessGame().getBlackPlayerId().equals(playerId)
          || holder.getChessGame().getWhitePlayerId().equals(playerId)) {
        return holder;
      }
    }
    return null;
  }

  /** @return whether the player is currently in a chess game */
  public boolean isPlaying(UUID playerId) {
    return chessService.isPlaying(playerId);
  }
}
