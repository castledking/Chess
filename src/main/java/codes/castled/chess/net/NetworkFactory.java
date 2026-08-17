package codes.castled.chess.net;

import codes.castled.chess.Chess;
import org.bukkit.Bukkit;

/**
 * Decides whether this server may take part in cross-server play, and builds the link if so.
 *
 * <p>Refusing is a normal outcome rather than an error: the feature is off by default, and a
 * server that cannot take part safely gets the do-nothing network instead, so nothing above has to
 * handle the feature being absent.
 */
public final class NetworkFactory {

  private NetworkFactory() {}

  /**
   * @param plugin the plugin, for logging and scheduling
   * @param settings the configured hub link
   * @return the network to use, which is {@link OfflineNetwork} when this server should not join
   */
  public static ChessNetwork create(Chess plugin, HubNetwork.NetworkSettings settings) {
    if (!settings.enabled()) {
      return new OfflineNetwork();
    }

    if (!settings.usable()) {
      plugin
          .getLogger()
          .warning(
              "Chess network is enabled but not configured: set network.url, network.token and a "
                  + "unique network.server-id in settings.yml. Cross-server play stays off.");
      return new OfflineNetwork();
    }

    // Players are matched across servers by Mojang UUID. An offline-mode server derives UUIDs
    // from names instead, so two of them can mint the same UUID for different people — which
    // across a shared network is an impersonation hole rather than a mere inconvenience.
    if (!Bukkit.getOnlineMode()) {
      plugin
          .getLogger()
          .severe(
              "Chess network refuses to start: this server is in offline mode, where player UUIDs "
                  + "are derived from names and can collide across servers. Cross-server play "
                  + "stays off.");
      return new OfflineNetwork();
    }

    if (!settings.hubUrl().startsWith("wss://")) {
      plugin
          .getLogger()
          .warning(
              "Chess network: network.url is not wss://, so the hub token and all traffic are "
                  + "sent in cleartext.");
    }

    return new HubNetwork(plugin, settings);
  }
}
