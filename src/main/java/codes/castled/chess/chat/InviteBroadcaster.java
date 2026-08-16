package codes.castled.chess.chat;

import codes.castled.chess.util.Platform;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Sends the clickable watch invite using whichever chat component library the server has.
 *
 * <p>Paper bundles Adventure; Spigot does not, and bundles the BungeeCord chat API instead. Both
 * can express a click and hover event, so neither platform loses the feature. Only this package
 * names either library, so no core class drags one in.
 *
 * <p>Paper also still ships the BungeeCord API, so a single implementation would compile — but it
 * is deprecated there and slated for removal, so Paper keeps using Adventure.
 */
public interface InviteBroadcaster {

  /**
   * Sends the invite to every online player except the two playing.
   *
   * @param invite the message to render
   * @param whiteId the white player, who is not sent the invite
   * @param blackId the black player, who is not sent the invite
   */
  void broadcast(WatchInvite invite, UUID whiteId, UUID blackId);

  /** @return the implementation matching this server */
  static InviteBroadcaster forPlatform() {
    return Platform.hasAdventure() ? new AdventureInviteBroadcaster() : new SpigotInviteBroadcaster();
  }

  /**
   * Runs {@code send} for every online player who is not one of the two playing.
   *
   * @param whiteId the white player, skipped
   * @param blackId the black player, skipped
   * @param send what to do with each remaining player
   */
  static void toSpectators(UUID whiteId, UUID blackId, java.util.function.Consumer<Player> send) {
    for (Player online : Bukkit.getOnlinePlayers()) {
      UUID id = online.getUniqueId();
      if (!id.equals(whiteId) && !id.equals(blackId)) {
        send.accept(online);
      }
    }
  }
}
