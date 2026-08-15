package codes.castled.chess.game;

import com.dxzell.pocketchess.api.board.ChessBoard;
import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;
import com.dxzell.pocketchess.common.board.SquareUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates premove destinations using relaxed, geometry-only validation matching the
 * chessground/lichess standard. Unlike normal move generation, premove validation:
 *
 * <ul>
 *   <li>Allows pawn diagonal captures even when no enemy piece is on the target square
 *       (the opponent might move a piece there before the premove fires).
 *   <li>Does not block sliding pieces (bishop, rook, queen) on opponent pieces — only
 *       own pieces block the path, since opponent pieces may move away.
 *   <li>Does not validate check, castling, or en passant — those are enforced at play
 *       time by the engine's strict move validation.
 * </ul>
 */
public final class PremoveMoveCalculator {

  private PremoveMoveCalculator() {}

  public static List<Square> getPremoveMoves(ChessGame game, Square from) {
    ChessBoard board = game.getChessBoard();
    Piece piece = board.getPiece(from);
    if (piece == null) {
      return List.of();
    }

    PieceColor color = piece.color();
    List<Square> moves = new ArrayList<>();

    switch (piece.type()) {
      case KNIGHT -> addKnightMoves(board, from, color, moves);
      case BISHOP -> addSlidingMoves(board, from, color, moves, true, false);
      case ROOK -> addSlidingMoves(board, from, color, moves, false, true);
      case QUEEN -> addSlidingMoves(board, from, color, moves, true, true);
      case KING -> addKingMoves(board, from, color, moves);
      case PAWN -> addPawnMoves(game, from, color, moves);
    }

    return moves;
  }

  /** Knight: all 8 L-shaped jumps, blocked only by own pieces. */
  private static void addKnightMoves(
      ChessBoard board, Square from, PieceColor color, List<Square> moves) {
    int[][] offsets = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    for (int[] off : offsets) {
      Square dest = SquareUtils.offsetOrNull(from, off[0], off[1]);
      if (dest == null) continue;
      Piece occupant = board.getPiece(dest);
      if (occupant != null && occupant.color() == color) continue;
      moves.add(dest);
    }
  }

  /**
   * Sliding pieces: slide in each direction, stopping at own pieces. Opponent pieces
   * are transparent (they might move away before the premove fires).
   */
  private static void addSlidingMoves(
      ChessBoard board,
      Square from,
      PieceColor color,
      List<Square> moves,
      boolean diagonal,
      boolean straight) {
    int[][] dirs = new int[0][];
    if (diagonal && straight) {
      dirs = new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    } else if (diagonal) {
      dirs = new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    } else {
      dirs = new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    }
    for (int[] dir : dirs) {
      Square current = from;
      while (true) {
        Square next = SquareUtils.offsetOrNull(current, dir[0], dir[1]);
        if (next == null) break;
        Piece occupant = board.getPiece(next);
        if (occupant != null && occupant.color() == color) break;
        moves.add(next);
        current = next;
      }
    }
  }

  /** King: all 8 adjacent squares, blocked only by own pieces. No castling. */
  private static void addKingMoves(
      ChessBoard board, Square from, PieceColor color, List<Square> moves) {
    int[][] offsets = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    for (int[] off : offsets) {
      Square dest = SquareUtils.offsetOrNull(from, off[0], off[1]);
      if (dest == null) continue;
      Piece occupant = board.getPiece(dest);
      if (occupant != null && occupant.color() == color) continue;
      moves.add(dest);
    }
  }

  /**
   * Pawn: forward 1 (must be empty), forward 2 from starting rank (both squares must be
   * empty), diagonal 1 (always allowed — the opponent might place a piece there). Promotion
   * squares are excluded (ambiguous for premoves).
   */
  private static void addPawnMoves(
      ChessGame game, Square from, PieceColor color, List<Square> moves) {
    ChessBoard board = game.getChessBoard();
    boolean white = color == PieceColor.WHITE;
    int forward = white ? 1 : -1;
    int startRow = white ? 1 : 6; // 0-indexed: row index 1 = rank '2', 6 = rank '7'
    int promotionRow = white ? 7 : 0; // 0-indexed: row index 7 = rank '8', 0 = rank '1'

    // Forward 1
    Square fwd1 = SquareUtils.offsetOrNull(from, forward, 0);
    if (fwd1 != null && board.getPiece(fwd1) == null) {
      if (fwd1.getRowIndex() != promotionRow) {
        moves.add(fwd1);
      }
      // Forward 2 from starting rank (both squares must be empty)
      if (from.getRowIndex() == startRow) {
        Square fwd2 = SquareUtils.offsetOrNull(from, forward * 2, 0);
        if (fwd2 != null && board.getPiece(fwd2) == null) {
          moves.add(fwd2);
        }
      }
    }

    // Diagonal captures — always allowed for premoves, even without a target piece.
    // Promotion squares are excluded.
    for (int colOff : new int[] {-1, 1}) {
      Square diag = SquareUtils.offsetOrNull(from, forward, colOff);
      if (diag != null && diag.getRowIndex() != promotionRow) {
        moves.add(diag);
      }
    }
  }
}
