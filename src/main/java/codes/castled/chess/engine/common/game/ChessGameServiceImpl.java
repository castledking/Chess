package codes.castled.chess.engine.common.game;

import codes.castled.chess.engine.api.game.ChessGameService;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.move.MoveValidator;

import javax.annotation.Nullable;
import java.util.*;

public final class ChessGameServiceImpl implements ChessGameService {

  private final Map<UUID, ChessGame> games = new HashMap<>();
  private final MoveCalculator moveCalculator;
  private final MoveValidator moveValidator;

  public ChessGameServiceImpl(MoveCalculator moveCalculator, MoveValidator moveValidator) {
    this.moveCalculator = moveCalculator;
    this.moveValidator = moveValidator;
  }

  @Override
  public GameCreationResult createGame(UUID whitePlayerId, UUID blackPlayerId, TimeMode timeMode) {
    GameCreationResult result = checkPlayerStatus(whitePlayerId, blackPlayerId);

    if (result != null) {
      return result;
    }

    ChessGame game =
        new ChessGameImpl(
            this,
            timeMode,
            new ChessBoardImpl(),
            whitePlayerId,
            blackPlayerId,
            moveCalculator,
            moveValidator);
    games.put(game.getGameId(), game);

    return new GameCreationResult(GameCreationResultType.SUCCESS, game);
  }

  private GameCreationResult checkPlayerStatus(UUID whitePlayerId, UUID blackPlayerId) {
    if (isPlaying(whitePlayerId) && isPlaying(blackPlayerId)) {
      return new GameCreationResult(GameCreationResultType.BOTH_PLAYERS_IN_GAME, null);
    }

    if (isPlaying(whitePlayerId)) {
      return new GameCreationResult(GameCreationResultType.FIRST_PLAYER_IN_GAME, null);
    }

    if (isPlaying(blackPlayerId)) {
      return new GameCreationResult(GameCreationResultType.SECOND_PLAYER_IN_GAME, null);
    }

    return null;
  }

  @Override
  public boolean endGameById(UUID gameId) {
    ChessGame game = getGameById(gameId);

    if (game == null) {
      return false;
    }

    games.remove(gameId);
    game.endGame(null);
    return true;
  }

  @Override
  public boolean endGameByPlayer(UUID playerId) {
    ChessGame game = getGameByPlayer(playerId);

    if (game == null) {
      return false;
    }

    games.remove(game.getGameId());
    game.endGame(null);
    return true;
  }

  @Nullable
  @Override
  public ChessGame getGameByPlayer(UUID playerId) {
    for (ChessGame game : games.values()) {
      if (game.getWhitePlayerId().equals(playerId) || game.getBlackPlayerId().equals(playerId)) {
        return game;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public ChessGame getGameById(UUID gameId) {
    return games.get(gameId);
  }

  @Override
  public List<ChessGame> getGames() {
    return new ArrayList<>(games.values());
  }

  @Override
  public boolean isPlaying(UUID playerId) {
    for (ChessGame game : games.values()) {
      if (game.getWhitePlayerId().equals(playerId) || game.getBlackPlayerId().equals(playerId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes the game with the given game id.
   *
   * @param gameId the id of the game to remove
   */
  public void removeGameById(UUID gameId) {
    games.remove(gameId);
  }
}
