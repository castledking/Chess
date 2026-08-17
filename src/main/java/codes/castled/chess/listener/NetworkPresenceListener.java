package codes.castled.chess.listener;

import codes.castled.chess.net.ChessNetwork;
import codes.castled.chess.net.RemotePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Tells the network who is on this server, so other servers can see and challenge them.
 *
 * <p>Sends deltas as players come and go. The hub answers with the whole roster rather than
 * applying deltas of its own, so a lost message costs one stale completion rather than a roster
 * that drifts further out of step with every event.
 */
public final class NetworkPresenceListener implements Listener {

  private final ChessNetwork network;

  public NetworkPresenceListener(ChessNetwork network) {
    this.network = network;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    network.publishPresence(
        List.of(
            new RemotePlayer(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(), "")),
        List.of());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    network.publishPresence(List.of(), List.of(event.getPlayer().getUniqueId()));
  }
}
