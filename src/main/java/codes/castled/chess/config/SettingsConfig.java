package codes.castled.chess.config;

import codes.castled.chess.Chess;
import codes.castled.chess.game.ChessGameEvent;
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
