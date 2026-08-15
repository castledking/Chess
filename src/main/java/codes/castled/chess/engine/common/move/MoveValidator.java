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

  /**
   * Checks whether the given move is a king side rook move.
   *
   * @param selectedPiece the moved piece
   * @param color the color of the moved piece
   * @param fromSquare the start square
   * @return true if the king side rook was moved, false if not
   */
  public boolean isKingSideRookMove(Piece selectedPiece, PieceColor color, Square fromSquare) {
    return isStartRowRookMove(selectedPiece, color, fromSquare, 7);
  }

  /**
   * Checks whether the given move is a queen side rook move.
   *
   * @param selectedPiece the moved piece
   * @param color the color of the moved piece
   * @param fromSquare the start square
   * @return true if the queen side rook was moved, false if not
   */
  public boolean isQueenSideRookMove(Piece selectedPiece, PieceColor color, Square fromSquare) {
    return isStartRowRookMove(selectedPiece, color, fromSquare, 0);
  }

  /**
   * Checks whether the given move is a rook move at the start row.
   *
   * @param selectedPiece the moved piece
   * @param color the color of the moved piece
   * @param fromSquare the start square
   * @param expectedColumnIndex the column index on which the rook is expected to stand. This is
   *     needed to determine whether the king side or queen side rook was moved.
   * @return true if the start row was moved, false if not
   */
  private boolean isStartRowRookMove(
      Piece selectedPiece, PieceColor color, Square fromSquare, int expectedColumnIndex) {
    return selectedPiece.type() == PieceType.ROOK
        && ((color == PieceColor.WHITE && fromSquare.getRowIndex() == 0)
            || (color == PieceColor.BLACK && fromSquare.getRowIndex() == 7))
        && fromSquare.getColumnIndex() == expectedColumnIndex;
  }

  /**
   * Checks whether the given move is a king side rook capture.
   *
   * @param capturedPiece the captured piece
   * @param toSquare the destination square
   * @return true if the king side rook was captured, false if not
   */
  public boolean isKingSideRookCapture(@Nullable Piece capturedPiece, Square toSquare) {
    return isStartRowRookCapture(capturedPiece, toSquare, 7);
  }

  /**
   * Checks whether the given move is a queen side rook capture.
   *
   * @param capturedPiece the captured piece
   * @param toSquare the destination square
   * @return true if the queen side rook was captured, false if not
   */
  public boolean isQueenSideRookCapture(@Nullable Piece capturedPiece, Square toSquare) {
    return isStartRowRookCapture(capturedPiece, toSquare, 0);
  }

  /**
   * Checks whether the given move is a rook capture at the start row.
   *
   * @param capturedPiece the captured piece
   * @param toSquare the destination square
   * @param expectedColumnIndex the column index on which the captured rook is expected to have
   *     stood. This is needed to determine whether the king side or queen side rook was moved.
   * @return true if the start row rook was captured, false if not
   */
  private boolean isStartRowRookCapture(
      @Nullable Piece capturedPiece, Square toSquare, int expectedColumnIndex) {
    return capturedPiece != null
        && capturedPiece.type() == PieceType.ROOK
        && ((capturedPiece.color() == PieceColor.WHITE && toSquare.getRowIndex() == 0)
            || (capturedPiece.color() == PieceColor.BLACK && toSquare.getRowIndex() == 7))
        && toSquare.getColumnIndex() == expectedColumnIndex;
  }
}
