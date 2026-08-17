package codes.castled.chess.net;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The network as it behaves when cross-server play is switched off, or when this server cannot
 * safely take part.
 *
 * <p>Having a do-nothing implementation rather than a null network means every caller can use the
 * network unconditionally: tab completion offers no remote players, and nothing else has to ask
 * whether the feature is on.
 */
public final class OfflineNetwork implements ChessNetwork {

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public Collection<RemotePlayer> remotePlayers() {
    return List.of();
  }

  @Nullable
  @Override
  public RemotePlayer findRemotePlayer(String name) {
    return null;
  }

  @Override
  public void setWebChallengeListener(WebChallengeListener listener) {}

  @Override
  public void setWebMoveListener(WebMoveListener listener) {}

  @Override
  public void publishGame(GameSummary game) {}

  @Override
  public void publishGameEnded(UUID gameId) {}

  @Override
  public void publishPresence(Collection<RemotePlayer> joined, Collection<UUID> left) {}
}
