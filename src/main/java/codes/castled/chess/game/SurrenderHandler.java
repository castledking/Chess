package codes.castled.chess.game;

import codes.castled.chess.ui.ChessView;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Handles surrender requests. UI-neutral state logic; display is delegated to the view. */
public final class SurrenderHandler {

  private final ChessGameHolder spigotChessGame;
  private final ChessView view;
  private final SettingsConfig settingsConfig;
  private final MessageConfig messageConfig;
  private final SurrenderData whiteSurrenderData;
  private final SurrenderData blackSurrenderData;

  public SurrenderHandler(ChessGameHolder spigotChessGame) {
    this.spigotChessGame = spigotChessGame;
    view = spigotChessGame.getView();
    settingsConfig = spigotChessGame.getSettingsConfig();
    messageConfig = spigotChessGame.getMessageConfig();
    whiteSurrenderData = new SurrenderData(spigotChessGame.getChessGame().getWhitePlayerId());
    blackSurrenderData = new SurrenderData(spigotChessGame.getChessGame().getBlackPlayerId());
  }

  /**
   * Handles the surrender click by checking the current surrender state and validating which action
   * should be performed.
   *
   * @param playerId the id of the player who clicked the surrender item
   */
  public void handleSurrenderClick(UUID playerId) {
    SurrenderData surrenderData = getPlayersSurrenderData(playerId);

    if (surrenderData.isWaitingForConfirmation()) {
      spigotChessGame.endGame(spigotChessGame.getOtherPlayerId(playerId));
    } else {
      surrenderData.setSurrenderRequestTimestamp(System.currentTimeMillis());
      setConfirmationState(surrenderData);
    }
  }

  /**
   * Sets the players surrender data into a confirmation state. The player now has to click again to
   * surrender.
   *
   * @param playersSurrenderData the players surrender data
   */
  private void setConfirmationState(SurrenderData playersSurrenderData) {
    UUID playerId = playersSurrenderData.getPlayerId();
    playersSurrenderData.setWaitingForConfirmation(true);
    view.setSurrenderHighlight(true, playerId);
    playersSurrenderData.addLittleTimeToRequest();
    setSurrenderItemMessage(messageConfig.getConfirmSurrender(), playerId);
  }

  /** Updates the surrender request timestamps for both players. */
  public void updateTimestamps() {
    whiteSurrenderData.updateSurrenderRequestTimestamp();
    blackSurrenderData.updateSurrenderRequestTimestamp();
  }

  /**
   * Displays the given message in the surrender item lore.
   *
   * @param message the message to display
   * @param playerId the id of the player to display the message to
   */
  private void setSurrenderItemMessage(String message, UUID playerId) {
    view.setSurrenderMessage(message, playerId);
  }

  /**
   * @param playerId the id of the player
   * @return the given players surrender data
   */
  private SurrenderData getPlayersSurrenderData(UUID playerId) {
    return playerId.equals(whiteSurrenderData.getPlayerId())
        ? whiteSurrenderData
        : blackSurrenderData;
  }

  /** Holds all needed data for the surrender validation. */
  @Getter
  @Setter
  private final class SurrenderData {

    private final UUID playerId;
    private Long surrenderRequestTimestamp;
    private boolean waitingForConfirmation = false;

    public SurrenderData(UUID playerId) {
      this.playerId = playerId;
    }

    /** Updates the surrender request timestamp and resets it if the timestamp has been reached. */
    public void updateSurrenderRequestTimestamp() {
      if (surrenderRequestTimestamp == null) {
        return;
      }

      long expiresInTimestamp =
          surrenderRequestTimestamp + settingsConfig.getSurrenderConfirmationExpiresInMillis();
      if (System.currentTimeMillis() >= expiresInTimestamp) {
        resetSurrenderRequestTimestamp();
      }
    }

    /** Resets the surrender request timestamp and removes the highlight & item message. */
    private void resetSurrenderRequestTimestamp() {
      waitingForConfirmation = false;
      surrenderRequestTimestamp = null;
      view.setSurrenderHighlight(false, playerId);
      setSurrenderItemMessage(" ", playerId);
    }

    /**
     * Adds a little hardcoded amount of time. When the player clicks the item the first time and
     * needs to click again to confirm this method will allow the player to get a little bit more
     * time so the item doesn't reset instantly.
     */
    private void addLittleTimeToRequest() {
      surrenderRequestTimestamp += 3000L;
    }
  }
}
