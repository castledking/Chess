package codes.castled.chess.listener;

import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameService;
import codes.castled.chess.pack.ResourcePackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Sends the pack on join (direct-send path) and drops viewers on leave-type events. */
public final class ChessGameListener implements Listener {

  private final GameService gameService;
  private final ResourcePackService resourcePackService;

  public ChessGameListener(GameService gameService, ResourcePackService resourcePackService) {
    this.gameService = gameService;
    this.resourcePackService = resourcePackService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    resourcePackService.sendPackTo(e.getPlayer());
  }

  @EventHandler
  public void onDamageTaken(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player damagedPlayer
        && damagedPlayer.getHealth() - e.getFinalDamage() <= 0) {
      onPlayerLeave(damagedPlayer.getUniqueId());
    }
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent e) {
    onPlayerLeave(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    onPlayerLeave(playerId);
    // Drop the player from any game they were spectating so refreshes stop.
    for (ChessGameHolder game : gameService.getGames()) {
      if (!game.isParticipant(playerId) && game.getView().isViewer(playerId)) {
        game.getView().removeViewer(playerId);
      }
    }
  }

  /**
   * Tells a participant's view the player is leaving. Never resigns the game; it only stops the
   * dialog backend from reopening the board.
   */
  private void onPlayerLeave(UUID playerId) {
    ChessGameHolder game = gameService.getGameByPlayer(playerId);
    if (game != null) {
      game.getView().onPlayerLeave(playerId);
    }
  }
}
