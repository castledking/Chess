package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.util.Scheduler;
import com.dxzell.pocketchess.api.game.ChessGame;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.UUID;

/**
 * Drives the per-game time control. Runs on the global region thread (so it never races the
 * click handlers, which also mutate game state there) and re-renders the clock roughly once
 * per second. Elapsed time is measured from wall-clock timestamps, so the display cadence
 * does not affect timing accuracy — only how often the board is redrawn.
 */
public final class GameClock {

  /** One redraw per second: fine for a visible clock and gentle on dialog re-sends. */
  private static final long PERIOD_TICKS = 20L;

  private final Chess plugin;
  private final ChessGameHolder holder;
  private final ChessGame chessGame;
  private ScheduledTask task;
  private long lastUpdateMillis;

  public GameClock(Chess plugin, ChessGameHolder holder) {
    this.plugin = plugin;
    this.holder = holder;
    this.chessGame = holder.getChessGame();
  }

  /** Creates and starts the repeating time task. */
  public void start() {
    if (task != null) {
      return;
    }
    lastUpdateMillis = System.currentTimeMillis();
    task =
        Scheduler.globalRepeating(
            plugin,
            () -> {
              UUID currentTurn = chessGame.getCurrentTurn();

              long now = System.currentTimeMillis();
              long elapsed = now - lastUpdateMillis;
              lastUpdateMillis = now;

              long newTime = chessGame.getTimeLeftMillis(currentTurn) - elapsed;

              if (newTime <= 0) {
                holder.endGame(holder.getOtherPlayerId(chessGame.getCurrentTurn()));
                return;
              }
              chessGame.updateRemainingTime(currentTurn, newTime);
              holder.getView().updateClock(newTime);

              holder.getDrawHandler().updateTimestamps();
              holder.getSurrenderHandler().updateTimestamps();
            },
            PERIOD_TICKS,
            PERIOD_TICKS);
  }

  /** Stops the time task if running. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }
}
