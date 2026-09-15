package codes.castled.chess.bot;

import codes.castled.chess.engine.api.piece.PieceColor;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * An engine opponent, standing in for a player.
 *
 * <p>A bot is handed a position as a FEN string and answers with a move in UCI notation. It is
 * never given the live game: {@link #chooseMove} runs off the server threads, where reading the
 * real board would race with the moves being played on it. Working from a snapshot also means a
 * bot can be an external process — which is how a genuinely strong one is reached.
 */
public interface ChessBot {

  /** @return the id this bot plays under, standing in for a player's UUID */
  UUID id();

  /** @return the name shown in messages and on the board */
  String name();

  /**
   * Chooses a move. Called off the server threads, so implementations may think for as long as
   * their difficulty calls for without stalling the server.
   *
   * @param fen the position to move in
   * @return the chosen move in UCI notation such as {@code e2e4} or {@code a7a8q}, or null if no
   *     move could be chosen, which is treated as a resignation
   */
  @Nullable
  String chooseMove(String fen);

  /**
   * Chooses a move in a position its FEN cannot fully describe.
   *
   * <p>The 1500s rules give each king one leap per game, and FEN has no field for whether it has
   * been spent. A bot told only the FEN would believe both leaps available and could choose one
   * the game then refuses — which resigns the bot. Bots that generate their own moves must honour
   * this; the default ignores it, which is right for a bot that plays standard chess.
   *
   * @param fen the position to move in
   * @param kingsLeapSpent the colours whose King's Leap has already been used
   * @return as {@link #chooseMove(String)}
   */
  @Nullable
  default String chooseMove(String fen, Set<PieceColor> kingsLeapSpent) {
    return chooseMove(fen);
  }

  /** Releases anything the bot holds, such as an external engine process. */
  default void close() {}
}
