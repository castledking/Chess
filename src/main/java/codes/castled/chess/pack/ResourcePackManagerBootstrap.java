package codes.castled.chess.pack;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * The single class that touches ResourcePackManager, entirely via reflection so the plugin
 * compiles and runs with no RSPM dependency of any kind. RSPM is an optional soft dependency;
 * these calls only succeed when it is installed.
 */
final class ResourcePackManagerBootstrap {

  private static final String API_CLASS = "com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI";

  private ResourcePackManagerBootstrap() {}

  /**
   * Hands the chess pack to ResourcePackManager so it is merged into the server's combined pack
   * instead of competing with it.
   *
   * @param plugin the plugin instance
   * @param localPath the pack path relative to the plugins directory
   * @return whether RSPM accepted the registration
   */
  static boolean register(Plugin plugin, String localPath) {
    try {
      Class<?> api = Class.forName(API_CLASS);
      // registerLocalResourcePack(name, localPath, encrypts, distributes, zips, reloadCommand)
      Method register =
          api.getMethod(
              "registerLocalResourcePack",
              String.class,
              String.class,
              boolean.class,
              boolean.class,
              boolean.class,
              String.class);
      register.invoke(null, plugin.getName(), localPath, false, true, true, null);
      plugin
          .getLogger()
          .info("Registered the chess resource pack with ResourcePackManager (" + localPath + ").");
      return true;
    } catch (Throwable throwable) {
      // RSPM initializes asynchronously, so an early attempt can fail; the caller retries.
      plugin.getLogger().fine("ResourcePackManager registration not ready yet: " + throwable);
      return false;
    }
  }
}
