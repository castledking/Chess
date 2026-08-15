package codes.castled.chess.engine.api.move;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.Piece;

/**
 * Represents a played move.
 *
 * @param piece the piece that was moved
 * @param from the old position
 * @param to the new position
 */
public record Move(Piece piece, Square from, Square to) {}
