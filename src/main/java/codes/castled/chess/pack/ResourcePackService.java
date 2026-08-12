package codes.castled.chess.pack;

import codes.castled.chess.Chess;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Owns how the chess resource pack reaches players. The pack supplies the board/piece glyphs the
 * dialog renders with, so without it every square shows a missing-glyph box.
 *
 * <p>Behaviour is gated by {@code resource-pack.use-resourcepack} (default true):
 *
 * <ul>
 *   <li><b>false</b> — the plugin does nothing: it neither sends the pack directly nor registers
 *       it with ResourcePackManager (so RSPM stops merging it and an admin can remove it there
 *       while keeping the plugin). The pack is still extracted to disk so it can be managed
 *       manually.
 *   <li><b>true + ResourcePackManager installed</b> — the pack is registered with RSPM, which
 *       merges it into the server's combined pack. Essential alongside Nexo/ItemsAdder/etc., where
 *       a direct send would fight their pack.
 *   <li><b>true + no ResourcePackManager</b> — the pack is pushed to each joining player with
 *       {@code setResourcePack} from {@code resource-pack.url}. The SHA-1 is computed from the
 *       extracted {@code resourcepack.zip}, so the admin only needs to host that exact file and
 *       point the URL at it.
 * </ul>
 *
 * <p>All RSPM API calls are isolated in {@link ResourcePackManagerBootstrap} (reflection only),
 * so this class stays safe to load when RSPM is absent.
 */
public final class ResourcePackService {

  /** Name of the pack inside the plugin jar and inside the plugin's data folder. */
  public static final String PACK_FILE_NAME = "resourcepack.zip";

  private static final String RSPM_PLUGIN_NAME = "ResourcePackManager";
  private static final long FIRST_ATTEMPT_DELAY_TICKS = 1L;
  private static final long MAX_ATTEMPT_DELAY_TICKS = 200L;

  private final Chess plugin;
  private final boolean useResourcePack;
  private final String packUrl;

  /** True while RSPM owns pack distribution, so we must not push a pack ourselves. */
  private volatile boolean managedByResourcePackManager;

  /** SHA-1 of the extracted pack, for the direct-send path; null until computed. */
  private byte[] packSha1;

  public ResourcePackService(Chess plugin, SettingsConfig settingsConfig) {
    this.plugin = plugin;
    this.useResourcePack = settingsConfig.getUseResourcePack();
    this.packUrl = settingsConfig.getResourcePackUrl();
  }

  /** Extracts the pack and, per config, registers it with RSPM or arms the direct-send path. */
  public void setup() {
    Path packFile = exportBundledPack();

    if (!useResourcePack) {
      plugin
          .getLogger()
          .info(
              "resource-pack.use-resourcepack is false; not sending or merging the chess pack. "
                  + "Manage it yourself (the file is at plugins/"
                  + plugin.getDataFolder().getName()
                  + "/"
                  + PACK_FILE_NAME
                  + ").");
      return;
    }

    if (Bukkit.getPluginManager().getPlugin(RSPM_PLUGIN_NAME) != null && packFile != null) {
      // Claim RSPM ownership up front so a player joining during the retry window is not sent a
      // competing pack. Undone only if we give up entirely.
      managedByResourcePackManager = true;
      scheduleRegistration(FIRST_ATTEMPT_DELAY_TICKS);
      return;
    }

    // Direct-send path: no RSPM (or no pack file to hand it).
    if (packUrl.isBlank()) {
      plugin
          .getLogger()
          .warning(
              "No ResourcePackManager installed and resource-pack.url is blank, so players will "
                  + "not receive the chess pack (the board will show missing-glyph boxes). Host "
                  + "plugins/"
                  + plugin.getDataFolder().getName()
                  + "/"
                  + PACK_FILE_NAME
                  + " somewhere and set resource-pack.url to it, or install ResourcePackManager.");
      return;
    }
    if (packFile != null) {
      packSha1 = sha1Of(packFile);
    }
    if (packSha1 != null) {
      plugin.getLogger().info("Sending the chess pack directly from " + packUrl + " on join.");
    }
  }

  /**
   * Pushes the pack to a joining player when the direct-send path is armed. No-op when RSPM owns
   * distribution, when disabled, or when no URL/hash is available.
   *
   * @param player the joining player
   */
  public void sendPackTo(Player player) {
    if (!useResourcePack || managedByResourcePackManager || packSha1 == null || packUrl.isBlank()) {
      return;
    }
    Scheduler.forPlayer(plugin, player, () -> player.setResourcePack(packUrl, packSha1));
  }

  private void scheduleRegistration(long delayTicks) {
    Scheduler.globalLater(
        plugin,
        () -> {
          if (Bukkit.getPluginManager().isPluginEnabled(RSPM_PLUGIN_NAME)) {
            String localPath = plugin.getDataFolder().getName() + "/" + PACK_FILE_NAME;
            if (ResourcePackManagerBootstrap.register(plugin, localPath)) {
              return;
            }
          }
          long nextDelay = delayTicks * 2;
          if (nextDelay > MAX_ATTEMPT_DELAY_TICKS) {
            managedByResourcePackManager = false;
            plugin
                .getLogger()
                .warning(
                    "Gave up registering the chess pack with ResourcePackManager; "
                        + "set resource-pack.url for the direct-send path instead.");
            return;
          }
          scheduleRegistration(nextDelay);
        },
        delayTicks);
  }

  private Path exportBundledPack() {
    Path target = plugin.getDataFolder().toPath().resolve(PACK_FILE_NAME);

    try (InputStream bundled = plugin.getResource(PACK_FILE_NAME)) {
      if (bundled == null) {
        return Files.exists(target) ? target : null;
      }
      Files.createDirectories(target.getParent());
      Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException exception) {
      plugin.getLogger().warning("Could not write " + target + ": " + exception.getMessage());
      return Files.exists(target) ? target : null;
    }
  }

  private byte[] sha1Of(Path file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return digest.digest(Files.readAllBytes(file));
    } catch (NoSuchAlgorithmException | IOException exception) {
      plugin.getLogger().warning("Could not hash the chess pack: " + exception.getMessage());
      return null;
    }
  }
}
