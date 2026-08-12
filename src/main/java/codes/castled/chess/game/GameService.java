package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.ui.ChessViewFactory;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.game.ChessGameService;
import com.dxzell.pocketchess.api.game.GameCreationResult;
import com.dxzell.pocketchess.api.game.GameCreationResultType;
import com.dxzell.pocketchess.api.game.TimeMode;
import com.dxzell.pocketchess.api.move.MoveCalculator;
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

  public GameService(
      Chess plugin,
      ChessGameService chessService,
      MoveCalculator moveCalculator,
      MessageConfig messageConfig,
      SettingsConfig settingsConfig,
      SoundPlayer soundPlayer,
      ChessViewFactory viewFactory) {
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
              viewFactory));
    }
    return result;
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
