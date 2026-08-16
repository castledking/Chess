package codes.castled.chess.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Schedules the plugin's work on whichever scheduler the server provides.
 *
 * <p>On Paper and Folia this is the region-aware scheduler set, which is required on Folia: world
 * state there is split across region threads, so {@code Bukkit.getScheduler()} is unusable. On
 * Spigot there are no regions and the legacy Bukkit scheduler is used instead.
 *
 * <p>Convention in this plugin, unchanged by which backend is active: all authoritative chess-game
 * state (the board, clocks, draw and surrender timers) is mutated only on the <b>global region</b>
 * via {@link #globalRepeating} / {@link #global}, which on Spigot is simply the main thread.
 * Anything that touches a single player (opening a board, playing a sound) runs on <b>that
 * player's</b> thread via {@link #forPlayer}, which on Spigot is also the main thread.
 *
 * <p>No Paper type appears in this class's signatures or fields. The Paper backend is instantiated
 * only when the region schedulers are present, so on Spigot its class is never loaded.
 */
public final class Scheduler {

  private static final Backend BACKEND =
      Platform.hasRegionSchedulers() ? new RegionBackend() : new LegacyBackend();

  private Scheduler() {}

  /**
   * Runs a repeating task on the global region thread.
   *
   * @param plugin the owning plugin
   * @param task the work to run each period
   * @param initialDelayTicks ticks before the first run (clamped to at least 1)
   * @param periodTicks ticks between runs (clamped to at least 1)
   * @return the scheduled task, so callers can cancel it
   */
  public static Task globalRepeating(
      Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
    return BACKEND.globalRepeating(
        plugin, task, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
  }

  /**
   * Runs a one-shot task on the global region thread after a delay.
   *
   * @param plugin the owning plugin
   * @param task the work to run
   * @param delayTicks ticks to wait (clamped to at least 1)
   * @return the scheduled task
   */
  public static Task globalLater(Plugin plugin, Runnable task, long delayTicks) {
    return BACKEND.globalLater(plugin, task, Math.max(1L, delayTicks));
  }

  /** Runs a task on the global region thread as soon as possible. */
  public static void global(Plugin plugin, Runnable task) {
    BACKEND.global(plugin, task);
  }

  /**
   * Runs a task on the given player's region thread. No-op if the player has been removed by the
   * time it would run.
   *
   * @param plugin the owning plugin
   * @param player the player whose thread the task belongs to
   * @param task the work to run
   */
  public static void forPlayer(Plugin plugin, Player player, Runnable task) {
    BACKEND.forPlayer(plugin, player, task);
  }

  /** Runs a task off the server threads. */
  public static void async(Plugin plugin, Runnable task) {
    BACKEND.async(plugin, task);
  }

  /** The operations each server family implements differently. */
  private interface Backend {
    Task globalRepeating(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks);

    Task globalLater(Plugin plugin, Runnable task, long delayTicks);

    void global(Plugin plugin, Runnable task);

    void forPlayer(Plugin plugin, Player player, Runnable task);

    void async(Plugin plugin, Runnable task);
  }

  /**
   * Paper and Folia. Loaded only when {@link Platform#hasRegionSchedulers()}, because every method
   * here resolves a Paper class.
   */
  private static final class RegionBackend implements Backend {

    @Override
    public Task globalRepeating(
        Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
      io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
          org.bukkit.Bukkit.getGlobalRegionScheduler()
              .runAtFixedRate(plugin, ignored -> task.run(), initialDelayTicks, periodTicks);
      return scheduled::cancel;
    }

    @Override
    public Task globalLater(Plugin plugin, Runnable task, long delayTicks) {
      io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
          org.bukkit.Bukkit.getGlobalRegionScheduler()
              .runDelayed(plugin, ignored -> task.run(), delayTicks);
      return scheduled::cancel;
    }

    @Override
    public void global(Plugin plugin, Runnable task) {
      org.bukkit.Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    @Override
    public void forPlayer(Plugin plugin, Player player, Runnable task) {
      player.getScheduler().run(plugin, ignored -> task.run(), null);
    }

    @Override
    public void async(Plugin plugin, Runnable task) {
      org.bukkit.Bukkit.getAsyncScheduler()
          .runNow(plugin, ignored -> task.run());
    }
  }

  /**
   * Spigot. There is one main thread and no regions, so the global region and a player's thread are
   * both simply the main thread.
   */
  private static final class LegacyBackend implements Backend {

    @Override
    public Task globalRepeating(
        Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
      org.bukkit.scheduler.BukkitTask scheduled =
          org.bukkit.Bukkit.getScheduler()
              .runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
      return scheduled::cancel;
    }

    @Override
    public Task globalLater(Plugin plugin, Runnable task, long delayTicks) {
      org.bukkit.scheduler.BukkitTask scheduled =
          org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
      return scheduled::cancel;
    }

    @Override
    public void global(Plugin plugin, Runnable task) {
      org.bukkit.Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void forPlayer(Plugin plugin, Player player, Runnable task) {
      // The player's thread is the main thread here. The Paper backend drops the task when the
      // player has been removed, so match that rather than acting on a stale player.
      org.bukkit.Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                if (player.isOnline()) {
                  task.run();
                }
              });
    }

    @Override
    public void async(Plugin plugin, Runnable task) {
      org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
  }
}
