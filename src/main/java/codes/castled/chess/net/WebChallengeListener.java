package codes.castled.chess.net;

import java.util.UUID;

/** Told when someone on the web challenges a player on this server. */
@FunctionalInterface
public interface WebChallengeListener {

  /**
   * @param target the player being challenged, who is online here
   * @param challenger the name to show as the challenger, chosen on the dashboard
   * @param timeMode the requested time control
   */
  void onWebChallenge(UUID target, String challenger, String timeMode);
}
