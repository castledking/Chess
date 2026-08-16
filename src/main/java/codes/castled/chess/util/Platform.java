package codes.castled.chess.util;

/**
 * Detects which server APIs are available, so the plugin can run on Spigot as well as Paper and
 * Folia.
 *
 * <p>Every check is a one-off class lookup evaluated at load. Nothing here touches a Paper class
 * directly: naming one in a field or signature would make this class fail to load on Spigot, which
 * is exactly what it exists to prevent. Callers use these flags to choose between implementations
 * that <em>do</em> name Paper classes, and the JVM only loads the branch that is actually taken.
 */
public final class Platform {

  private static final boolean DIALOG_API = classPresent("io.papermc.paper.dialog.Dialog");

  private static final boolean REGION_SCHEDULERS =
      classPresent("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");

  private static final boolean ADVENTURE = classPresent("net.kyori.adventure.text.Component");

  private Platform() {}

  /**
   * @return whether the server can show Paper dialogs, which the dialog board is built on. False on
   *     Spigot, where the inventory board is used instead. The dialogs themselves are a vanilla
   *     1.21.6+ client feature; it is the server-side API that Spigot lacks.
   */
  public static boolean hasDialogApi() {
    return DIALOG_API;
  }

  /**
   * @return whether the server has Paper's region-aware schedulers. False on Spigot, where the
   *     legacy Bukkit scheduler is used.
   */
  public static boolean hasRegionSchedulers() {
    return REGION_SCHEDULERS;
  }

  /**
   * @return whether the server bundles Adventure. False on Spigot, where messages fall back to
   *     legacy strings and the BungeeCord chat components Spigot does bundle.
   */
  public static boolean hasAdventure() {
    return ADVENTURE;
  }

  /** @return a short description of the detected platform, for the startup log line */
  public static String describe() {
    if (REGION_SCHEDULERS && DIALOG_API) {
      return "Paper";
    }
    return "Spigot";
  }

  private static boolean classPresent(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException | LinkageError ignored) {
      return false;
    }
  }
}
