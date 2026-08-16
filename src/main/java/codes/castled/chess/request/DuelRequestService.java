package codes.castled.chess.request;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.game.GameService;
import codes.castled.chess.util.Scheduler;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.util.Task;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Manages all outstanding chess duel requests. */
public final class DuelRequestService {

  private final Chess plugin;
  private final GameService gameService;
  private final MessageConfig messageConfig;
  private final List<DuelRequest> ongoingRequests = new ArrayList<>();
  private final long expireTimeMillis;
  private Task requestTask;

  public DuelRequestService(
      Chess plugin,
      GameService gameService,
      MessageConfig messageConfig,
      SettingsConfig settingsConfig) {
    this.plugin = plugin;
    this.gameService = gameService;
    this.messageConfig = messageConfig;
    this.expireTimeMillis = settingsConfig.getDuelRequestExpiresInMillis();
  }

  /**
   * Tries sending out a duel request.
   *
   * @param senderId the id of the duel request sender
   * @param receiverId the id of the duel request receiver
   * @param senderName the name of the duel request sender
   * @param receiverName the name of the duel request receiver
   * @param timeMode the requested time mode to play
   */
  public void sendRequest(
      UUID senderId, UUID receiverId, String senderName, String receiverName, TimeMode timeMode) {
    expireOldRequests();
    if (ongoingRequests.isEmpty()) {
      stopRequestTask();
    }
    DuelRequestCreationResult creationResult = checkPlayerStatus(senderId, receiverId);
    if (creationResult.type() == DuelRequestCreationResultType.SUCCESS) {
      ongoingRequests.add(
          new DuelRequest(
              senderId, receiverId, senderName, receiverName, timeMode, System.currentTimeMillis()));
      startRequestTask();
      sendMessage(
          senderId,
          messageConfig.getSuccessfullySentDuelRequest(receiverName, timeMode.getDisplayName()));
      sendMessage(
          receiverId, messageConfig.getDuelRequestReceived(senderName, timeMode.getDisplayName()));
    } else {
      sendMessage(senderId, creationResult.message());
    }
  }

  /**
   * Tries to accept the duel request and create a chess game.
   *
   * @param receiverId the id of the duel request receiver
   * @param senderId the id of the duel request sender
   */
  public void acceptRequest(UUID receiverId, UUID senderId) {
    DuelRequest duelRequest = getDuelRequest(senderId, receiverId);
    if (duelRequest != null) {
      GameCreationResult creationResult =
          gameService.createGame(
              Bukkit.getPlayer(senderId), Bukkit.getPlayer(receiverId), duelRequest.timeMode());
      if (creationResult.type() == GameCreationResultType.SUCCESS) {
        ongoingRequests.remove(duelRequest);
      } else {
        String message =
            switch (creationResult.type()) {
              case SUCCESS -> "";
              case FIRST_PLAYER_IN_GAME -> messageConfig.getRequestSenderPlaying();
              case SECOND_PLAYER_IN_GAME -> messageConfig.getYouAlreadyInGame();
              case BOTH_PLAYERS_IN_GAME -> messageConfig.getRequestSenderReceiverPlaying();
            };
        sendMessage(receiverId, message);
      }
    } else {
      sendMessage(receiverId, messageConfig.getNoOngoingRequest());
    }
  }

  /**
   * Tries to decline the duel request.
   *
   * @param receiverId the id of the duel request receiver
   * @param senderId the id of the duel request sender
   */
  public void declineRequest(UUID receiverId, UUID senderId) {
    DuelRequest duelRequest = getDuelRequest(senderId, receiverId);
    if (duelRequest != null) {
      sendMessage(senderId, messageConfig.getOpponentDeclinedRequest(duelRequest.receiverName()));
      sendMessage(receiverId, messageConfig.getYouDeclinedRequest(duelRequest.senderName()));
      ongoingRequests.remove(duelRequest);
    } else {
      sendMessage(receiverId, messageConfig.getNoOngoingRequest());
    }
  }

  private DuelRequestCreationResult checkPlayerStatus(UUID senderId, UUID receiverId) {
    if (senderId.equals(receiverId)) {
      return new DuelRequestCreationResult(
          DuelRequestCreationResultType.SAME_PLAYER, messageConfig.getCannotDuelYourself());
    }
    if (alreadyRequested(senderId, receiverId)) {
      return new DuelRequestCreationResult(
          DuelRequestCreationResultType.ALREADY_REQUESTED_DUEL,
          messageConfig.getAlreadySentDuelRequest());
    }
    if (gameService.isPlaying(senderId) && gameService.isPlaying(receiverId)) {
      return new DuelRequestCreationResult(
          DuelRequestCreationResultType.BOTH_PLAYERS_IN_GAME, messageConfig.getBothAlreadyInGame());
    }
    if (gameService.isPlaying(senderId)) {
      return new DuelRequestCreationResult(
          DuelRequestCreationResultType.FIRST_PLAYER_IN_GAME, messageConfig.getYouAlreadyInGame());
    }
    if (gameService.isPlaying(receiverId)) {
      return new DuelRequestCreationResult(
          DuelRequestCreationResultType.SECOND_PLAYER_IN_GAME,
          messageConfig.getOpponentAlreadyInGame());
    }
    return new DuelRequestCreationResult(DuelRequestCreationResultType.SUCCESS, "");
  }

  private void startRequestTask() {
    if (requestTask == null) {
      requestTask =
          Scheduler.globalRepeating(
              plugin,
              () -> {
                expireOldRequests();
                if (ongoingRequests.isEmpty()) {
                  stopRequestTask();
                }
              },
              20L,
              20L);
    }
  }

  private void stopRequestTask() {
    if (requestTask == null) {
      return;
    }
    requestTask.cancel();
    requestTask = null;
  }

  private void expireOldRequests() {
    long now = System.currentTimeMillis();
    List<DuelRequest> expiredRequests =
        ongoingRequests.stream().filter(request -> isExpired(request, now)).toList();
    ongoingRequests.removeAll(expiredRequests);
    for (DuelRequest request : expiredRequests) {
      sendMessage(request.senderId(), messageConfig.getDuelRequestToExpired(request.receiverName()));
      sendMessage(
          request.receiverId(), messageConfig.getDuelRequestFromExpired(request.senderName()));
    }
  }

  private boolean isExpired(DuelRequest duelRequest, long now) {
    return now >= duelRequest.sentTimestamp() + expireTimeMillis;
  }

  private boolean alreadyRequested(UUID senderId, UUID receiverId) {
    for (DuelRequest request : ongoingRequests) {
      if (request.senderId().equals(senderId) && request.receiverId().equals(receiverId)) {
        return true;
      }
    }
    return false;
  }

  private DuelRequest getDuelRequest(UUID senderId, UUID receiverId) {
    for (DuelRequest duelRequest : ongoingRequests) {
      if (duelRequest.senderId().equals(senderId) && duelRequest.receiverId().equals(receiverId)) {
        return duelRequest;
      }
    }
    return null;
  }

  private void sendMessage(UUID playerId, String message) {
    Player player = Bukkit.getPlayer(playerId);
    if (player != null) {
      player.sendMessage(message);
    }
  }
}
