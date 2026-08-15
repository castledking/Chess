package codes.castled.chess.engine.common.move;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.ChessBoardImpl;

import javax.annotation.Nullable;
import java.util.List;

public final class MoveValidator {

  private final MoveCalculator moveCalculator;

  public MoveValidator(MoveCalculator moveCalculator) {
    this.moveCalculator = moveCalculator;
  }

  /**
   * Tests whether the given move would cause check.
   *
   * @param board the chess board that is being played on
   * @param move the move that would be played
   * @param color the color to test the check
   * @return true if it would cause check, false if not
   */
  public boolean wouldCauseSelfCheck(ChessBoardImpl board, Move move, PieceColor color) {
    Piece movingPiece = board.getPiece(move.from());
    Piece capturedPiece = board.getPiece(move.to());

    if (movingPiece == null) {
      return false;
    }

    board.setPiece(move.from(), null);
    board.setPiece(move.to(), movingPiece);

    boolean wouldCauseSelfCheck = isInCheck(board, color);

    board.setPiece(move.from(), movingPiece);
    board.setPiece(move.to(), capturedPiece);

    return wouldCauseSelfCheck;
  }

  /**
   * Tests whether the specified color currently is in check.
   *
   * @param board the chess board that is being played on
   * @param color the color to test the check
   * @return true if in check, false if not
   */
  public boolean isInCheck(ChessBoard board, PieceColor color) {
    List<Square> pieceSquares =
        board.getColoredPieces(
            PieceColor.getOtherColor(color),
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KNIGHT,
            PieceType.PAWN);
    Square kingSquare = board.getColoredPieces(color, PieceType.KING).get(0);
    for (Square pieceSquare : pieceSquares) {
      if (moveCalculator.getRawMoves(board, pieceSquare).stream()
          .anyMatch(targetSquare -> targetSquare.equals(kingSquare))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether the given move is an en passant move.
   *
   * @param fromSquare the square of the selected piece
   * @param toSquare the destination square of the piece
   * @param selectedPiece the piece to move
   * @param capturedPiece the piece to capture, or null if no piece will be captured
   * @return true if the move is en passant, false if not
   */
  public boolean isEnPassantMove(
      Square fromSquare, Square toSquare, Piece selectedPiece, @Nullable Piece capturedPiece) {
    return selectedPiece.type() == PieceType.PAWN
        && Math.abs((toSquare.getColumnIndex() - fromSquare.getColumnIndex())) == 1
        && capturedPiece == null;
  }
}
