package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceColor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which pieces are still eligible to castle.
 *
 * <p>Rooks are tracked as a set of squares rather than king-side/queen-side flags. A rook is
 * eligible while its square is in the set; moving or losing it removes the square. This models
 * three things the flag pair could not: rooks that start on arbitrary files (Chess960), rooks
 * that appear mid-game through promotion, and the fact that eligibility belongs to a specific
 * rook rather than to a side of the board.
 */
public final class CastlingStatus {

  private final Map<PieceColor, Boolean> kingMoved =
      new HashMap<>(Map.of(PieceColor.WHITE, false, PieceColor.BLACK, false));

  private final Map<PieceColor, Set<Square>> unmovedRooks =
      new HashMap<>(
          Map.of(PieceColor.WHITE, new HashSet<>(), PieceColor.BLACK, new HashSet<>()));

  public void markKingMoved(PieceColor color) {
    kingMoved.put(color, true);
  }

  public boolean hasKingMoved(PieceColor color) {
    return kingMoved.get(color);
  }

  /**
   * Records a rook that has never moved, either at board setup or when one appears through
   * promotion.
   *
   * @param color the colour of the rook
   * @param square the square the rook stands on
   */
  public void markRookUnmoved(PieceColor color, Square square) {
    unmovedRooks.get(color).add(square);
  }

  /**
   * Withdraws a rook's castling eligibility, because it moved or was captured.
   *
   * @param color the colour of the rook
   * @param square the square the rook stood on
   */
  public void markRookMoved(PieceColor color, Square square) {
    unmovedRooks.get(color).remove(square);
  }

  /**
   * Withdraws every rook right for a colour, so a loaded position can grant exactly the rights
   * its FEN declares rather than inheriting whatever the board happens to look like.
   *
   * @param color the colour to clear
   */
  public void withdrawAllRookRights(PieceColor color) {
    unmovedRooks.get(color).clear();
  }

  /**
   * @param color the colour of the rooks
   * @return the squares of that colour's rooks that have never moved
   */
  public Set<Square> getUnmovedRookSquares(PieceColor color) {
    return Set.copyOf(unmovedRooks.get(color));
  }
}
