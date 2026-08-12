package codes.castled.chess.request;

/**
 * Represents a possible duel request creation result.
 *
 * @param type the duel request creation type
 * @param message the message that corresponds to the creation type
 */
public record DuelRequestCreationResult(DuelRequestCreationResultType type, String message) {}
