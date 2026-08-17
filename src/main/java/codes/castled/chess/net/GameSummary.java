package codes.castled.chess.net;

import java.util.UUID;

/**
 * A game as the rest of the network sees it.
 *
 * <p>Carries no board. Watchers elsewhere are shown who is playing, where, and how much time each
 * side has; the position itself belongs to the server running the game.
 *
 * @param gameId the game's id
 * @param white the white player's name
 * @param black the black player's name
 * @param whiteMillis white's remaining time
 * @param blackMillis black's remaining time
 * @param whiteToMove whether white is the side to move, so a viewer knows which clock is running
 * @param fen the position, so a web board can render it and a web player can move in it
 */
public record GameSummary(
    UUID gameId,
    String white,
    String black,
    long whiteMillis,
    long blackMillis,
    boolean whiteToMove,
    String fen) {}
