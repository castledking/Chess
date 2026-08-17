package codes.castled.chess.net;

import java.util.UUID;

/** Told when someone playing from the web makes a move. */
@FunctionalInterface
public interface WebMoveListener {

  /**
   * @param gameId the game the move belongs to
   * @param notation the move in UCI notation, which is not trusted to be legal
   */
  void onWebMove(UUID gameId, String notation);
}
