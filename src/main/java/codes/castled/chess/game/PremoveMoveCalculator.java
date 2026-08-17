package codes.castled.chess.game;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.SquareUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Generates premove destinations using relaxed, geometry-only validation matching the
 * chessground/lichess standard. Unlike normal move generation, premove validation:
 *
 * <ul>
 *   <li>Allows premoving onto your own pieces (self-capture), since the opponent's move can
 *       displace them.
 *   <li>Allows pawn diagonal captures even when no enemy piece is on the target square
 *       (the opponent might move a piece there before the premove fires).
 *   <li>Does not block sliding pieces (bishop, rook, queen) on any pieces — only board
 *       edges stop the slide, since pieces may move away.
 *   <li>Offers castling on rights alone, without checking that the path is clear or that the
 *       king would pass through check — the opponent's single move can change either.
 *   <li>Does not validate check or en passant — those are enforced at play time by the
 *       engine's strict move validation.
 * </ul>
 */
public final class PremoveMoveCalculator {

  private PremoveMoveCalculator() {}

  public static List<Square> getPremoveMoves(
      ChessGame game, Square from, boolean verticalCastling) {
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
      case KING -> {
        addKingMoves(board, from, color, moves);
        addCastlingMoves(game, verticalCastling, from, color, board::getPiece, moves);
      }
      case PAWN -> addPawnMoves(game, from, color, moves);
    }

    return moves;
  }

  /**
   * Adds the castling destinations a king may premove to.
   *
   * <p>Deliberately checks neither that the path is clear nor that the king would castle out of,
   * through, or into check, because the opponent's move can change both: it can capture the piece
   * blocking the path, or move the piece giving check. Castling <em>rights</em> are checked,
   * because unlike those, they are monotonic — once the king or rook has moved the right is gone
   * for good and no opponent move brings it back. Play-time validation applies the real rules.
   *
   * @param game the authoritative game, which owns castling rights
   * @param verticalCastling whether the vertical castling easter egg is enabled
   * @param kingSquare the king's square in the projected position
   * @param color the premover's colour
   * @param projected looks a square up in the projected position, which for a stacked premove
   *     chain is not the authoritative board
   * @param moves the list of premove destinations to add to
   */
  static void addCastlingMoves(
      ChessGame game,
      boolean verticalCastling,
      Square kingSquare,
      PieceColor color,
      Function<Square, Piece> projected,
      List<Square> moves) {

    if (game.hasKingMoved(color)) {
      return;
    }

    // A king that has already been premoved elsewhere cannot castle from where it now appears.
    // Its rights say it has not moved, so the authoritative board still holds it at home; if the
    // projected square is not that square, this is a projected king and castling does not apply.
    Piece authoritativeKing = game.getChessBoard().getPiece(kingSquare);
    if (authoritativeKing == null
        || authoritativeKing.type() != PieceType.KING
        || authoritativeKing.color() != color) {
      return;
    }

    for (Square rookSquare : game.getUnmovedRookSquares(color)) {
      // The rook may have been premoved away or captured earlier in the chain.
      Piece rook = projected.apply(rookSquare);
      if (rook == null || rook.type() != PieceType.ROOK || rook.color() != color) {
        continue;
      }

      int rowStep = Integer.signum(rookSquare.getRowIndex() - kingSquare.getRowIndex());
      int columnStep = Integer.signum(rookSquare.getColumnIndex() - kingSquare.getColumnIndex());

      boolean alongRank = rowStep == 0 && columnStep != 0;
      boolean alongFile = columnStep == 0 && rowStep != 0;

      int forward = color == PieceColor.WHITE ? 1 : -1;
      if (alongFile && !(verticalCastling && rowStep == forward)) {
        continue;
      }

      if (!alongRank && !alongFile) {
        continue;
      }

      Square destination =
          SquareUtils.offsetOrNull(kingSquare, rowStep * 2, columnStep * 2);
      if (destination != null) {
        moves.add(destination);
      }
    }
  }

  /** Knight: all 8 L-shaped jumps. No blocking — own pieces may be captured. */
  private static void addKnightMoves(
      ChessBoard board, Square from, PieceColor color, List<Square> moves) {
    int[][] offsets = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    for (int[] off : offsets) {
      Square dest = SquareUtils.offsetOrNull(from, off[0], off[1]);
      if (dest == null) continue;
      moves.add(dest);
    }
  }

  /**
   * Sliding pieces: slide in each direction, stopping only at board edges.
   * Own pieces may be captured — the opponent's move can displace them.
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
        moves.add(next);
        current = next;
      }
    }
  }

  /** King: all 8 adjacent squares. No blocking — own pieces may be captured. Castling is added separately. */
  private static void addKingMoves(
      ChessBoard board, Square from, PieceColor color, List<Square> moves) {
    int[][] offsets = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    for (int[] off : offsets) {
      Square dest = SquareUtils.offsetOrNull(from, off[0], off[1]);
      if (dest == null) continue;
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
