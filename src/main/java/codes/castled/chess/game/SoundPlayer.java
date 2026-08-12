package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Plays configured sounds for chess game events. */
public final class SoundPlayer {

  private final Chess plugin;
  private final SettingsConfig settingsConfig;

  public SoundPlayer(Chess plugin, SettingsConfig settingsConfig) {
    this.plugin = plugin;
    this.settingsConfig = settingsConfig;
  }

  /** Plays a check sound to the checked player and the player who made the move. */
  public void playCheckMoveSound(UUID checkedPlayerId, UUID movedPlayerId) {
    playSound(ChessGameEvent.CHECK, checkedPlayerId);
    playSound(ChessGameEvent.CHECK, movedPlayerId);
  }

  /** Plays a move sound to both players. */
  public void playMoveSound(UUID firstPlayerId, UUID secondPlayerId) {
    playSound(ChessGameEvent.MOVE, firstPlayerId);
    playSound(ChessGameEvent.MOVE, secondPlayerId);
  }

  /** Plays a win sound to the winner and a loss sound to the loser. */
  public void playWinLoseSounds(UUID winnerId, UUID loserId) {
    playSound(ChessGameEvent.WIN, winnerId);
    playSound(ChessGameEvent.LOSS, loserId);
  }

  /** Plays a draw sound to both players. */
  public void playDrawSound(UUID firstPlayerId, UUID secondPlayerId) {
    playSound(ChessGameEvent.DRAW, firstPlayerId);
    playSound(ChessGameEvent.DRAW, secondPlayerId);
  }

  private void playSound(ChessGameEvent gameEvent, UUID playerId) {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null) {
      return;
    }
    Sound sound = settingsConfig.getSound(gameEvent);
    if (sound == null) {
      return;
    }
    // Hop to the player's own region thread so this is Folia-safe when called from the
    // global game thread.
    Scheduler.forPlayer(plugin, player, () -> player.playSound(player.getLocation(), sound, 1.0f, 1.0f));
  }
}
