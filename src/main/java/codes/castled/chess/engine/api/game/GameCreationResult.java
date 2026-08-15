package codes.castled.chess.engine.api.game;

/**
 * Represents a possible game creation result.
 *
 * @param type the type of the game creation result
 * @param game the game if creation was successful, otherwise null
 */
public record GameCreationResult(GameCreationResultType type, ChessGame game) {}
