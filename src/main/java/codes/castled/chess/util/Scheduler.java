package codes.castled.chess.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Thin wrapper over Paper's region-aware schedulers so the plugin runs unchanged on both
 * Paper and Folia. Folia has no single "main thread": world state is split across region
 * threads, so {@code Bukkit.getScheduler()} is unusable there. The global-region, async and
 * per-entity schedulers used here exist on modern Paper too, so one code path serves the
 * whole 1.21.11+ range.
 *
 * <p>Convention in this plugin: all authoritative chess-game state (the board, clocks, draw
 * and surrender timers) is mutated only on the <b>global region</b> thread via
 * {@link #globalRepeating} / {@link #global}. Anything that touches a single player (showing
 * a dialog, playing a sound) is run on <b>that player's</b> thread via {@link #forPlayer}.
 */
public final class Scheduler {

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
  public static ScheduledTask globalRepeating(
      Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
    return Bukkit.getGlobalRegionScheduler()
        .runAtFixedRate(
            plugin, scheduled -> task.run(), Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
  }

  /**
   * Runs a one-shot task on the global region thread after a delay.
   *
   * @param plugin the owning plugin
   * @param task the work to run
   * @param delayTicks ticks to wait (clamped to at least 1)
   * @return the scheduled task
   */
  public static ScheduledTask globalLater(Plugin plugin, Runnable task, long delayTicks) {
    return Bukkit.getGlobalRegionScheduler()
        .runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayTicks));
  }

  /** Runs a task on the global region thread as soon as possible. */
  public static void global(Plugin plugin, Runnable task) {
    Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
  }

  /**
   * Runs a task on the given player's region thread. No-op if the player has been removed by
   * the time it would run.
   *
   * @param plugin the owning plugin
   * @param player the player whose thread to run on
   * @param task the work to run
   */
  public static void forPlayer(Plugin plugin, Player player, Runnable task) {
    player.getScheduler().run(plugin, scheduled -> task.run(), null);
  }

  /** Runs a task off the server threads entirely (e.g. blocking I/O). */
  public static void async(Plugin plugin, Runnable task) {
    Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
  }
}
