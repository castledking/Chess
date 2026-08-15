package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.piece.PieceColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the castling status.
 */
public final class CastlingStatus {

  private final Map<PieceColor, Boolean> kingMoved =
      new HashMap<>(Map.of(PieceColor.WHITE, false, PieceColor.BLACK, false));

  private final Map<PieceColor, Boolean> kingSideRookMoved =
      new HashMap<>(Map.of(PieceColor.WHITE, false, PieceColor.BLACK, false));

  private final Map<PieceColor, Boolean> queenSideRookMoved =
      new HashMap<>(Map.of(PieceColor.WHITE, false, PieceColor.BLACK, false));

  public void markKingMoved(PieceColor color) {
    kingMoved.put(color, true);
  }

  public boolean hasKingMoved(PieceColor color) {
    return kingMoved.get(color);
  }

  public void markKingSideRookMoved(PieceColor color) {
    kingSideRookMoved.put(color, true);
  }

  public boolean hasKingSideRookMoved(PieceColor color) {
    return kingSideRookMoved.get(color);
  }

  public void markQueenSideRookMoved(PieceColor color) {
    queenSideRookMoved.put(color, true);
  }

  public boolean hasQueenSideRookMoved(PieceColor color) {
    return queenSideRookMoved.get(color);
  }
}
