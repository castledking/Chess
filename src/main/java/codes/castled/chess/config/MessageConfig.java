package codes.castled.chess.config;

import codes.castled.chess.Chess;
import codes.castled.chess.ui.DialogLabels;

import java.util.Map;

/** Provides access to all plugin-sent messages and the dialog board labels. */
public final class MessageConfig extends Config implements DialogLabels {

  public MessageConfig(Chess plugin) {
    super(plugin, "messages.yml");
  }

  public String getNoPieceSelected() {
    return getColoredString(getInfoPath() + "no-piece-selected");
  }

  public String getInvalidSquare() {
    return getColoredString(getInfoPath() + "invalid-square");
  }

  public String getPickPromotion() {
    return getColoredString(getInfoPath() + "pick-promotion-piece");
  }

  public String getDrawOfferCooldown(String cooldownString) {
    return getColoredString(getDrawPath() + "cooldown").replace("[cooldown]", cooldownString);
  }

  public String getDrawOfferReceived() {
    return getColoredString(getDrawPath() + "offer-received");
  }

  public String getDrawOfferSent() {
    return getColoredString(getDrawPath() + "offer-sent");
  }

  public String getConfirmDraw() {
    return getColoredString(getDrawPath() + "confirm-offer");
  }

  public String getConfirmSurrender() {
    return getColoredString(getSurrenderPath() + "confirm");
  }

  public String getOpponentMoved() {
    return getColoredString(getChessChatPath() + "opponent-moved");
  }

  public String getYouAlreadyInGame() {
    return getColoredString(getChessCommandMessagePath() + "you-already-in-game");
  }

  public String getOpponentAlreadyInGame() {
    return getColoredString(getChessCommandMessagePath() + "opponent-already-in-game");
  }

  public String getBothAlreadyInGame() {
    return getColoredString(getChessCommandMessagePath() + "both-in-game");
  }

  public String getCannotDuelYourself() {
    return getColoredString(getChessCommandMessagePath() + "cannot-duel-yourself");
  }

  public String getNotPlaying() {
    return getColoredString(getChessCommandMessagePath() + "not-playing");
  }

  public String getTargetNotInGame() {
    return getColoredString(getChessCommandMessagePath() + "target-not-in-game");
  }

  public String getOpponentNotOnline() {
    return getColoredString(getChessCommandMessagePath() + "opponent-not-online");
  }

  public String getInvalidTimeMode() {
    return getColoredString(getChessCommandMessagePath() + "invalid-time-mode");
  }

  public String getAlreadySentDuelRequest() {
    return getColoredString(getChessCommandMessagePath() + "already-requested-duel");
  }

  public String getDuelRequestToExpired(String opponentName) {
    return getColoredString(getChessCommandMessagePath() + "duel-request-to-expired")
        .replace("[opponent]", opponentName);
  }

  public String getDuelRequestFromExpired(String opponentName) {
    return getColoredString(getChessCommandMessagePath() + "duel-request-from-expired")
        .replace("[opponent]", opponentName);
  }

  public String getDuelRequestReceived(String opponentName, String timeMode) {
    return getColoredString(getChessCommandMessagePath() + "duel-request-received")
        .replace("[opponent]", opponentName)
        .replace("[mode]", timeMode);
  }

  public String getSuccessfullySentDuelRequest(String opponentName, String timeMode) {
    return getColoredString(getChessCommandMessagePath() + "successfully-sent-duel-request")
        .replace("[opponent]", opponentName)
        .replace("[mode]", timeMode);
  }

  public String getDuelRequestSenderOffline() {
    return getColoredString(getChessCommandMessagePath() + "request-sender-offline");
  }

  public String getRequestSenderPlaying() {
    return getColoredString(getChessCommandMessagePath() + "request-sender-playing");
  }

  public String getRequestSenderReceiverPlaying() {
    return getColoredString(getChessCommandMessagePath() + "request-sender-receiver-playing");
  }

  public String getNoOngoingRequest() {
    return getColoredString(getChessCommandMessagePath() + "no-ongoing-request");
  }

  public String getGameStarted() {
    return getColoredString(getChessChatPath() + "started");
  }

  public String getYouDeclinedRequest(String senderName) {
    return getColoredString(getChessCommandMessagePath() + "you-declined-request")
        .replace("[opponent]", senderName);
  }

  public String getOpponentDeclinedRequest(String receiverName) {
    return getColoredString(getChessCommandMessagePath() + "opponent-declined-request")
        .replace("[opponent]", receiverName);
  }

  public String getInvalidArgsCmd() {
    return getColoredString(getChessCommandMessagePath() + "invalid-args-command");
  }

  /** Shown when a player must resolve a pending promotion before doing anything else. */
  public String getPromotionRequired() {
    return getColoredString("messages.ui.promotion-required");
  }

  /**
   * Raw (untranslated) dialog label rendered inside the Paper dialog itself. Labels use
   * MiniMessage tags and are parsed by the dialog renderer, so they are returned verbatim.
   *
   * @param key the label key under {@code messages.chess-game.dialog}
   * @return the configured label, or a visible placeholder if missing
   */
  public String getDialogLabel(String key) {
    String value = config.getString(getDialogPath() + key);
    return value != null ? value : "<red>?" + key + "?</red>";
  }

  public String getDialogLabel(String key, Map<String, String> placeholders) {
    String value = getDialogLabel(key);
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      value = value.replace(entry.getKey(), entry.getValue());
    }
    return value;
  }

  private String getDialogPath() {
    return getChessGameMessagePath() + "dialog.";
  }

  private String getChessCommandMessagePath() {
    return "messages.chess-command.";
  }

  private String getChessGameMessagePath() {
    return "messages.chess-game.";
  }

  private String getInfoPath() {
    return getChessGameMessagePath() + "info.";
  }

  private String getDrawPath() {
    return getChessGameMessagePath() + "draw.";
  }

  private String getSurrenderPath() {
    return getChessGameMessagePath() + "surrender.";
  }

  private String getChessChatPath() {
    return getChessGameMessagePath() + "chat.";
  }
}
