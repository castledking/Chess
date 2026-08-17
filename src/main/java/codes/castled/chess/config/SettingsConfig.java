package codes.castled.chess.config;

import codes.castled.chess.Chess;
import codes.castled.chess.game.ChessGameEvent;
import codes.castled.chess.net.HubNetwork;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import javax.annotation.Nullable;

/** Provides access to general plugin settings from settings.yml. */
public final class SettingsConfig extends Config {

  public SettingsConfig(Chess plugin) {
    super(plugin, "settings.yml");
  }

  @Nullable
  public Sound getSound(ChessGameEvent gameEvent) {
    String path = "chess-game.sounds." + gameEvent.name().toLowerCase();
    String soundName = config.getString(path);

    if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("no.sound")) {
      return null;
    }

    NamespacedKey key = NamespacedKey.fromString(soundName);
    if (key == null) {
      return null;
    }
    return Registry.SOUNDS.get(key);
  }

  public long getDrawOfferCooldownMillis() {
    return config.getInt(getDrawSettingsPath() + "offer-cooldown-in-seconds") * 1000L;
  }

  public long getDrawOfferExpiresInMillis() {
    return config.getInt(getDrawSettingsPath() + "offer-expires-in-seconds") * 1000L;
  }

  public long getSurrenderConfirmationExpiresInMillis() {
    return config.getInt(getSurrenderSettingsPath() + "offer-expires-in-seconds") * 1000L;
  }

  public long getDuelRequestExpiresInMillis() {
    return config.getInt("duel-request.duel-request-expires-in-seconds") * 1000L;
  }

  /**
   * @return whether a king may castle vertically with an unmoved rook on its own file, which in
   *     practice means a rook created by promoting a pawn on the king's file
   */
  public boolean isVerticalCastlingEnabled() {
    return config.getBoolean("easter-egg.enable-vertical-castling", false);
  }

  /**
   * @return how this server links to the cross-server hub
   *     <p>There is nothing to fill in. The server's identity is a key generated on first use and
   *     written back to settings.yml, so an operator only ever chooses whether the feature is on.
   */
  public HubNetwork.NetworkSettings getNetworkSettings() {
    return new HubNetwork.NetworkSettings(
        config.getBoolean("network.enabled", true),
        config.getString("network.url", "wss://castled.codes"),
        getOrCreateServerKey(),
        getServerLabel());
  }

  /**
   * @return this server's identity on the network, generating and saving one the first time
   *     <p>The key is this server as far as the hub is concerned, so it is generated locally and
   *     never leaves except to the hub. Persisting it means a restart rejoins as the same server
   *     rather than appearing as a new one.
   */
  private String getOrCreateServerKey() {
    String existing = config.getString("network.server-key", "");
    if (existing != null && !existing.isBlank()) {
      return existing;
    }

    String generated = java.util.UUID.randomUUID().toString();
    config.set("network.server-key", generated);
    save();
    return generated;
  }

  /** @return the name shown for this server, falling back to something recognisable */
  private String getServerLabel() {
    String configured = config.getString("network.server-name", "");
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    String motd = org.bukkit.Bukkit.getMotd();
    return motd == null || motd.isBlank() ? "Server" : motd.replaceAll("§.", "").trim();
  }

  /** @return whether the plugin should manage (send/merge) its resource pack at all */
  public boolean getUseResourcePack() {
    return config.getBoolean("resource-pack.use-resourcepack", true);
  }

  /** @return the hosted pack URL for the direct-send path, or empty when unset */
  public String getResourcePackUrl() {
    return config.getString("resource-pack.url", "");
  }

  private String getDrawSettingsPath() {
    return "chess-game.draw.";
  }

  private String getSurrenderSettingsPath() {
    return "chess-game.surrender.";
  }
}
