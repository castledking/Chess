package codes.castled.chess.engine.api.game;

import java.util.List;
import java.util.UUID;

/** Manages all running chess games. */
public interface ChessGameService {

  /**
   * Creates a new chess game if all required checks pass.
   *
   * @param whitePlayerId the player who starts with white
   * @param blackPlayerId the player who starts with black
   * @param timeMode the time mode for the game (time + increment)
   * @return the result of the game creation attempt
   */
  GameCreationResult createGame(UUID whitePlayerId, UUID blackPlayerId, TimeMode timeMode);

  /**
   * Ends a running and unfinished game. There will be no winner or loser.
   *
   * @param gameId the id of the requested game
   * @return true if the game was ended, or false if the game wasn't found
   */
  boolean endGameById(UUID gameId);

  /**
   * Ends a running and unfinished game. There will be no winner or loser.
   *
   * @param playerId a player id from the requested game (either black or white)
   * @return true if the game was ended, or false if no game was played by this player
   */
  boolean endGameByPlayer(UUID playerId);

  /**
   * @param playerId a player id from the requested game (either black or white)
   * @return the played game, or null if not existing
   */
  ChessGame getGameByPlayer(UUID playerId);

  /**
   * @param gameId the id of the requested game
   * @return the played game, or null if not existing
   */
  ChessGame getGameById(UUID gameId);

  /**
   * @return a list of all currently running games
   */
  List<ChessGame> getGames();

  /**
   * @param playerId the id of the player to check
   * @return true if playing, or false if not playing
   */
  boolean isPlaying(UUID playerId);
}
