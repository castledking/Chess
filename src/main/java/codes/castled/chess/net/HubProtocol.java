package codes.castled.chess.net;

/**
 * The message names exchanged with the cross-server hub.
 *
 * <p>Every frame is a JSON object with a {@code type} field naming one of these. Keeping the names
 * in one place stops the plugin and the hub drifting apart over a typo, which is otherwise a
 * failure that shows up only at runtime and only between two machines.
 *
 * <p>Sent by a server:
 *
 * <ul>
 *   <li>{@link #HELLO} — on connect, declaring the server's id and everyone currently on it
 *   <li>{@link #PRESENCE} — a delta as players join and leave
 * </ul>
 *
 * <p>Sent by the hub:
 *
 * <ul>
 *   <li>{@link #ROSTER} — the full set of players on every other server, resent whenever it
 *       changes. Sent whole rather than as deltas so a server that missed a message cannot drift
 *       out of step indefinitely.
 *   <li>{@link #REJECTED} — the connection was refused, with a reason worth logging
 * </ul>
 */
public final class HubProtocol {

  /** The path a server connects to, appended to the configured hub URL. */
  public static final String PATH = "/api/chess/ws";

  public static final String HELLO = "HELLO";
  public static final String PRESENCE = "PRESENCE";
  public static final String GAME = "GAME";
  public static final String GAME_ENDED = "GAME_ENDED";
  public static final String ROSTER = "ROSTER";
  public static final String WEB_CHALLENGE = "WEB_CHALLENGE";
  public static final String REJECTED = "REJECTED";

  /** Field naming the message type on every frame. */
  public static final String TYPE = "type";

  private HubProtocol() {}
}
