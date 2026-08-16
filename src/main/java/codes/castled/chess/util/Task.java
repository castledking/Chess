package codes.castled.chess.util;

/**
 * A cancellable scheduled task, independent of which scheduler produced it.
 *
 * <p>Paper's {@code ScheduledTask} and Bukkit's {@code BukkitTask} share no supertype, and naming
 * either one in a field would drag that class into every holder. Callers keep one of these instead.
 */
public interface Task {

  /** Cancels the task if it has not already run or been cancelled. */
  void cancel();
}
