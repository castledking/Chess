package codes.castled.chess.net;

import java.util.UUID;

/**
 * A player on another server taking part in the network.
 *
 * @param uuid their Mojang UUID, which is what identifies them across servers
 * @param name their name, shown in tab completion and messages
 * @param serverId the server they are on
 */
public record RemotePlayer(UUID uuid, String name, String serverId) {}
