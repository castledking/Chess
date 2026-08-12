package codes.castled.chess.game;

import codes.castled.chess.ui.DrawItemType;
import codes.castled.chess.ui.ChessView;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Handles draw requests. UI-neutral state logic; display is delegated to the view. */
public final class DrawHandler {

  private final ChessGameHolder spigotChessGame;
  private final ChessView view;
  private final SettingsConfig settingsConfig;
  private final MessageConfig messageConfig;
  private final DrawData whiteDrawData;
  private final DrawData blackDrawData;

  public DrawHandler(ChessGameHolder spigotChessGame) {
    this.spigotChessGame = spigotChessGame;
    view = spigotChessGame.getView();
    settingsConfig = spigotChessGame.getSettingsConfig();
    messageConfig = spigotChessGame.getMessageConfig();
    whiteDrawData = new DrawData(spigotChessGame.getChessGame().getWhitePlayerId());
    blackDrawData = new DrawData(spigotChessGame.getChessGame().getBlackPlayerId());
  }

  /**
   * Handles the draw click by checking the current draw state and validating which action should be
   * performed.
   *
   * @param playerId the id of the player who clicked the draw item
   */
  public void handleDrawClick(UUID playerId) {
    DrawData playersDrawData = getPlayersDrawData(playerId);

    if (playersDrawData.hasIncomingDrawOffer()) {
      handleIncomingDrawOfferClick(playersDrawData);
      return;
    }

    if (playersDrawData.hasCooldown()) {
      handleCooldownClick(playersDrawData);
      return;
    }

    if (playersDrawData.hasOutgoingDrawOffer()) {
      return;
    }

    offerDraw(playerId);
  }

  /**
   * Handles the draw offer click for when the player has an incoming draw offer.
   *
   * @param playersDrawData the draw data of the player
   */
  private void handleIncomingDrawOfferClick(DrawData playersDrawData) {
    if (playersDrawData.isWaitingForConfirmation()) {
      spigotChessGame.endGame(null);
    } else {
      setConfirmationState(playersDrawData);
    }
  }

  /**
   * Handles the draw offer click for when the player has an ongoing cooldown.
   *
   * @param playersDrawData the draw data of the player
   */
  private void handleCooldownClick(DrawData playersDrawData) {
    setDrawItemMessage(
        messageConfig.getDrawOfferCooldown(
            getMillisAsString(
                playersDrawData.getCooldownEndTimestamp() - System.currentTimeMillis())),
        playersDrawData.getPlayerId());
  }

  /**
   * Offers a draw to the other player.
   *
   * @param senderId the id of the player sending the draw offer
   */
  private void offerDraw(UUID senderId) {
    updateDrawData(senderId);
    sendDrawOfferInventoryUpdates(senderId);
  }

  /**
   * Updates the draw data objects for both players.
   *
   * @param senderId the id of the draw offer sender
   */
  private void updateDrawData(UUID senderId) {
    DrawData senderDrawData = getPlayersDrawData(senderId);
    senderDrawData.setCooldownEndTimestamp(
        System.currentTimeMillis() + settingsConfig.getDrawOfferCooldownMillis());
    senderDrawData.setOutgoingDrawOfferTimestamp(System.currentTimeMillis());
    getOtherPlayersDrawData(senderId).setIncomingDrawOfferTimestamp(System.currentTimeMillis());
  }

  /**
   * Updates the inventories of both draw offer sender and receiver. The updates contain
   * highlighting and a message display.
   *
   * @param senderId the id of the draw offer sender
   */
  private void sendDrawOfferInventoryUpdates(UUID senderId) {
    updateDrawOfferSendersInventory(senderId);
    updateDrawOfferReceiverInventory(spigotChessGame.getOtherPlayerId(senderId));
  }

  /**
   * Updates the inventory of the draw offer sender.
   *
   * @param senderId the id of the draw offer sender
   */
  private void updateDrawOfferSendersInventory(UUID senderId) {
    view.setDrawHighlight(DrawItemType.SENT, senderId);
    setDrawItemMessage(messageConfig.getDrawOfferSent(), senderId);
  }

  /**
   * Updates the inventory of the draw offer receiver.
   *
   * @param receiverId the id of the draw offer receiver
   */
  private void updateDrawOfferReceiverInventory(UUID receiverId) {
    view.setDrawHighlight(DrawItemType.ACCEPT, receiverId);
    setDrawItemMessage(messageConfig.getDrawOfferReceived(), receiverId);
  }

  /**
   * Sets the players draw data into a confirmation state. The player now has to click again to
   * draw.
   *
   * @param playersDrawData the players draw data
   */
  private void setConfirmationState(DrawData playersDrawData) {
    UUID playerId = playersDrawData.getPlayerId();
    playersDrawData.setWaitingForConfirmation(true);
    view.setDrawHighlight(DrawItemType.CONFIRM, playerId);
    playersDrawData.addLittleTimeToIncoming();
    getOtherPlayersDrawData(playerId).addLittleTimeToOutgoing();
    setDrawItemMessage(messageConfig.getConfirmDraw(), playerId);
  }

  /** Updates the draw outgoing and incoming timestamps for both players. */
  public void updateTimestamps() {
    updateWhiteTimestamps();
    updateBlackTimestamps();
  }

  /** Resets the draw timestamps for both players. */
  public void resetTimestamps() {
    whiteDrawData.resetTimestamps();
    blackDrawData.resetTimestamps();
  }

  /** Updates the white players draw timestamps. */
  private void updateWhiteTimestamps() {
    whiteDrawData.updateIncomingDrawOfferTimestamp();
    whiteDrawData.updateOutgoingDrawOfferTimestamp();
  }

  /** Updates the black players draw timestamps. */
  private void updateBlackTimestamps() {
    blackDrawData.updateIncomingDrawOfferTimestamp();
    blackDrawData.updateOutgoingDrawOfferTimestamp();
  }

  /**
   * Displays the given message in the draw item lore.
   *
   * @param message the message to display
   * @param playerId the id of the player to display the message to
   */
  private void setDrawItemMessage(String message, UUID playerId) {
    view.setDrawMessage(message, playerId);
  }

  /**
   * @param millis the time in milliseconds
   * @return translates the given time into a string displaying it as hours, minutes and seconds
   */
  private String getMillisAsString(long millis) {
    int hours = (int) (millis / 3600000);
    long restMillis = millis % 3600000;

    int minutes = (int) (restMillis / 60000);
    restMillis = restMillis % 60000;

    int seconds = (int) (restMillis / 1000);

    String timeString =
        (hours > 0 ? hours + "h " : "")
            + (minutes > 0 ? minutes + "m " : "")
            + (seconds > 0 ? seconds + "s" : "");

    return !timeString.isEmpty() ? timeString : "1s";
  }

  /**
   * @param playerId the id of the player
   * @return the draw data of the given player
   */
  private DrawData getPlayersDrawData(UUID playerId) {
    return playerId.equals(whiteDrawData.getPlayerId()) ? whiteDrawData : blackDrawData;
  }

  /**
   * @param playerId the id of the player
   * @return the draw data of the other player of the chess game
   */
  private DrawData getOtherPlayersDrawData(UUID playerId) {
    return playerId.equals(whiteDrawData.getPlayerId()) ? blackDrawData : whiteDrawData;
  }

  /** Holds all needed data for the draw validation. */
  @Getter
  @Setter
  private final class DrawData {

    private final UUID playerId;
    private Long outgoingDrawOfferTimestamp;
    private Long incomingDrawOfferTimestamp;
    private boolean waitingForConfirmation = false;
    private Long cooldownEndTimestamp;

    public DrawData(UUID playerId) {
      this.playerId = playerId;
    }

    /** Updates the incoming draw timestamps and resets it if the timestamp has been reached. */
    public void updateIncomingDrawOfferTimestamp() {
      if (incomingDrawOfferTimestamp == null) {
        return;
      }

      long expiresInTimestamp =
          incomingDrawOfferTimestamp + settingsConfig.getDrawOfferExpiresInMillis();
      if (System.currentTimeMillis() >= expiresInTimestamp) {
        resetIncomingTimestamp();
      }
    }

    /** Updates the outgoing draw timestamps and resets it if the timestamp has been reached. */
    public void updateOutgoingDrawOfferTimestamp() {
      if (outgoingDrawOfferTimestamp == null) {
        return;
      }

      long expiresInTimestamp =
          outgoingDrawOfferTimestamp + settingsConfig.getDrawOfferExpiresInMillis();
      if (System.currentTimeMillis() >= expiresInTimestamp) {
        resetOutgoingTimestamp();
      }
    }

    /** Resets both incoming and outgoing draw timestamps. */
    public void resetTimestamps() {
      resetOutgoingTimestamp();
      resetIncomingTimestamp();
    }

    /** Resets the outgoing draw timestamp and removes the highlight & item message. */
    private void resetOutgoingTimestamp() {
      outgoingDrawOfferTimestamp = null;
      view.setDrawHighlight(DrawItemType.NONE, playerId);
      setDrawItemMessage(" ", playerId);
    }

    /** Resets the incoming draw timestamp and removes the highlight & item message. */
    private void resetIncomingTimestamp() {
      waitingForConfirmation = false;
      incomingDrawOfferTimestamp = null;
      view.setDrawHighlight(DrawItemType.NONE, playerId);
      setDrawItemMessage(" ", playerId);
    }

    /**
     * @return whether the player currently has a draw sending cooldown
     */
    public boolean hasCooldown() {
      return cooldownEndTimestamp != null
          && (cooldownEndTimestamp - System.currentTimeMillis() > 0);
    }

    /**
     * Adds a little hardcoded amount of time. When the player clicks the item the first time and
     * needs to click again to confirm this method will allow the player to get a little bit more
     * time so the item doesn't reset instantly.
     */
    public void addLittleTimeToIncoming() {
      incomingDrawOfferTimestamp += 3000L;
    }

    /**
     * Adds a little hardcoded amount of time. When the player clicks the item the first time and
     * needs to click again to confirm this method will allow the player to get a little bit more
     * time so the item doesn't reset instantly.
     */
    public void addLittleTimeToOutgoing() {
      outgoingDrawOfferTimestamp += 3000L;
    }

    /**
     * @return whether the player currently has an incoming draw offer
     */
    public boolean hasIncomingDrawOffer() {
      return incomingDrawOfferTimestamp != null;
    }

    /**
     * @return whether the player currently has an outgoing draw offer
     */
    public boolean hasOutgoingDrawOffer() {
      return outgoingDrawOfferTimestamp != null;
    }
  }
}
