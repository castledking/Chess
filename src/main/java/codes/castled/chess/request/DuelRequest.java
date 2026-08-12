package codes.castled.chess.request;

import com.dxzell.pocketchess.api.game.TimeMode;

import java.util.UUID;

/**
 * Represents a chess duel offer.
 *
 * @param senderId the id of the offer sender
 * @param receiverId the id of the offer receiver
 * @param senderName the name of the offer sender
 * @param receiverName the name of the offer receiver
 * @param timeMode the requested time mode to play
 * @param sentTimestamp the timestamp the offer was sent
 */
public record DuelRequest(
    UUID senderId,
    UUID receiverId,
    String senderName,
    String receiverName,
    TimeMode timeMode,
    long sentTimestamp) {}
