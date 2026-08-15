package codes.castled.chess.engine.common.move;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.board.SquareUtils;
import codes.castled.chess.engine.common.game.ChessGameImpl;

import javax.annotation.Nullable;

/** Handles special moves. */
public final class SpecialMoveHandler {

  private final ChessGameImpl chessGame;
  private final ChessBoardImpl chessBoard;
  private final MoveValidator moveValidator;

  public SpecialMoveHandler(
      ChessGameImpl chessGame, ChessBoardImpl chessBoard, MoveValidator moveValidator) {
    this.chessGame = chessGame;
    this.chessBoard = chessBoard;
    this.moveValidator = moveValidator;
  }

  /**
   * Checks whether the given move is en passant and returns a suitable move result.
   *
   * @param fromSquare the start square
   * @param toSquare the destination square
   * @param selectedPiece the selected chess piece
   * @param capturedPiece the captured chess piece
   * @return a suitable move result, or null if the move was not en passant
   */
  @Nullable
  public MoveResult handleEnPassantMove(
      Square fromSquare, Square toSquare, Piece selectedPiece, @Nullable Piece capturedPiece) {
    if (moveValidator.isEnPassantMove(fromSquare, toSquare, selectedPiece, capturedPiece)) {
      chessBoard.setPiece(
          SquareUtils.offsetOrNull(toSquare, selectedPiece.color() == PieceColor.WHITE ? -1 : 1, 0),
          null);
      return new MoveResult(MoveResultType.SUCCESS, false, true, false, false);
    }
    return null;
  }

  /**
   * Checks whether the given move is a castling move and returns a suitable move result.
   *
   * @param fromSquare the start square
   * @param toSquare the destination square
   * @param selectedPiece the selected chess piece
   * @return a suitable move result, or null if it was not a castling move
   */
  @Nullable
  public MoveResult handleCastlingMove(Square fromSquare, Square toSquare, Piece selectedPiece) {
    if (selectedPiece.type() == PieceType.KING
        && Math.abs(fromSquare.getColumnIndex() - toSquare.getColumnIndex()) == 2) {

      Square rookFrom;
      Square rookTo;

      if (toSquare.getColumnIndex() == 6) { // King side castling
        rookFrom = new Square(toSquare.row(), 'H');
        rookTo = new Square(toSquare.row(), 'F');
      } else { // Queen side castling
        rookFrom = new Square(toSquare.row(), 'A');
        rookTo = new Square(toSquare.row(), 'D');
      }

      Piece rook = chessBoard.getPiece(rookFrom);
      if (rook != null) {
        chessBoard.movePiece(new Move(rook, rookFrom, rookTo));
      }

      return new MoveResult(MoveResultType.SUCCESS, true, false, false, false);
    }
    return null;
  }

  /**
   * Checks whether the moved piece was a pawn and whether it reached the promotion row. In that
   * case it sets the piece back to the start square and returns a suitable move result.
   *
   * @param capturedPiece the captured chess piece
   * @param selectedPiece the selected chess piece
   * @param fromSquare the start square
   * @param toSquare the destination square
   * @return a suitable move result, or null if it was not a promotion move
   */
  public MoveResult handlePromotionMove(
      Piece capturedPiece, Piece selectedPiece, Square fromSquare, Square toSquare) {
    if (selectedPiece.type() == PieceType.PAWN
        && (toSquare.getRowIndex() == 7 || toSquare.getRowIndex() == 0)) {
      chessBoard.setPiece(toSquare, capturedPiece);
      chessBoard.setPiece(fromSquare, selectedPiece);
      return new MoveResult(MoveResultType.SUCCESS, false, false, true, false);
    }
    return null;
  }

  /**
   * Updates the castling status after the given move was played.
   *
   * @param fromSquare the start square
   * @param toSquare the destination square
   * @param selectedPiece the selected chess piece
   * @param capturedPiece the captured chess piece
   * @param currentTurn the color of the player who made the move
   */
  public void updateCastlingStatus(
      Square fromSquare,
      Square toSquare,
      Piece selectedPiece,
      @Nullable Piece capturedPiece,
      PieceColor currentTurn) {

    handleRookMove(selectedPiece, fromSquare, currentTurn);
    handleRookCapture(capturedPiece, toSquare);
  }

  /**
   * Updates the castling status if a rook was moved.
   *
   * @param selectedPiece the selected chess piece
   * @param fromSquare the start square
   * @param currentTurn the color of the player who made the move
   */
  private void handleRookMove(Piece selectedPiece, Square fromSquare, PieceColor currentTurn) {
    if (selectedPiece.type() == PieceType.KING) {
      chessGame.getCastlingStatus().markKingMoved(currentTurn);
    } else if (moveValidator.isQueenSideRookMove(selectedPiece, currentTurn, fromSquare)) {
      chessGame.getCastlingStatus().markQueenSideRookMoved(currentTurn);
    } else if (moveValidator.isKingSideRookMove(selectedPiece, currentTurn, fromSquare)) {
      chessGame.getCastlingStatus().markKingSideRookMoved(currentTurn);
    }
  }

  /**
   * Updates the castling status if a rook was captured.
   *
   * @param capturedPiece the captured piece
   * @param toSquare the destination square
   */
  private void handleRookCapture(@Nullable Piece capturedPiece, Square toSquare) {
    if (moveValidator.isQueenSideRookCapture(capturedPiece, toSquare)) {
      chessGame.getCastlingStatus().markQueenSideRookMoved(capturedPiece.color());
    } else if (moveValidator.isKingSideRookCapture(capturedPiece, toSquare)) {
      chessGame.getCastlingStatus().markKingSideRookMoved(capturedPiece.color());
    }
  }
}
