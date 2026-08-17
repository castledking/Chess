package codes.castled.chess.net;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Links this server to the others running Chess, so players can see and challenge each other
 * across them.
 *
 * <p>Deliberately says nothing about how servers reach one another. The hub implementation dials
 * out to a central service, which works between servers that share no proxy and no network; a
 * proxy-based implementation could sit behind the same interface without anything above it
 * changing.
 *
 * <p>Everything here is called from the server threads and must not block on them. Implementations
 * answer roster questions from a local cache and send messages without waiting for a reply.
 */
public interface ChessNetwork {

  /** Opens the link. Safe to call when the network is disabled, in which case it does nothing. */
  void start();

  /** Closes the link and releases anything it holds. */
  void stop();

  /** @return whether this server is currently linked to the network */
  boolean isConnected();

  /**
   * @return every player known to be online elsewhere on the network, from a local cache so this
   *     is safe to call while completing a command
   */
  Collection<RemotePlayer> remotePlayers();

  /**
   * @param name the name to look for, case-insensitively
   * @return that player if they are online elsewhere on the network, or null
   */
  @Nullable
  RemotePlayer findRemotePlayer(String name);

  /**
   * Sets who is told when the web challenges a player here. Replaces any previous listener.
   *
   * @param listener the listener, called on the global region thread
   */
  void setWebChallengeListener(WebChallengeListener listener);

  /**
   * Tells the network about a game running here, so it can be shown elsewhere.
   *
   * <p>Sent when a game starts and after every move, not on every clock tick. The frame carries
   * both clocks and whose turn it is, which is enough for a viewer to count the moving side down
   * itself — publishing every second per game would be far more traffic for the same picture.
   *
   * @param game the game to describe
   */
  void publishGame(GameSummary game);

  /**
   * Tells the network a game has finished.
   *
   * @param gameId the game that ended
   */
  void publishGameEnded(UUID gameId);

  /**
   * Tells the network who is now on this server.
   *
   * @param joined players who have just joined
   * @param left players who have just left
   */
  void publishPresence(Collection<RemotePlayer> joined, Collection<UUID> left);
}
