package codes.castled.chess.engine.common.move;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveResult;
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
      return MoveResult.enPassantCapture();
    }
    return null;
  }

  /**
   * Checks whether the given move is a castling move, moves the rook if it is, and returns a
   * suitable move result.
   *
   * <p>A king only ever travels two squares by castling, so a two-square king move along a rank
   * (standard castling) or a file (vertical castling) identifies one. The rook always lands on
   * the square the king crossed, and is always the first piece beyond the king's destination in
   * the direction of travel — the move generator only offers the move when that path is clear.
   * Deriving both from the geometry keeps this correct for any rook file, which fixed king-side
   * and queen-side squares would not.
   *
   * @param fromSquare the start square
   * @param toSquare the destination square
   * @param selectedPiece the selected chess piece
   * @return a suitable move result, or null if it was not a castling move
   */
  @Nullable
  public MoveResult handleCastlingMove(Square fromSquare, Square toSquare, Piece selectedPiece) {
    if (selectedPiece.type() != PieceType.KING) {
      return null;
    }

    int rowDelta = toSquare.getRowIndex() - fromSquare.getRowIndex();
    int columnDelta = toSquare.getColumnIndex() - fromSquare.getColumnIndex();

    boolean alongRank = rowDelta == 0 && Math.abs(columnDelta) == 2;
    boolean alongFile = columnDelta == 0 && Math.abs(rowDelta) == 2;

    if (!alongRank && !alongFile) {
      return null;
    }

    int rowStep = Integer.signum(rowDelta);
    int columnStep = Integer.signum(columnDelta);

    Square rookFrom = findRookBeyond(toSquare, rowStep, columnStep, selectedPiece.color());
    if (rookFrom == null) {
      return null;
    }

    Square rookTo = SquareUtils.offsetOrNull(fromSquare, rowStep, columnStep);
    Piece rook = chessBoard.getPiece(rookFrom);
    Move rookMove = new Move(rook, rookFrom, rookTo);

    chessBoard.movePiece(rookMove);
    chessGame.getCastlingStatus().markRookMoved(selectedPiece.color(), rookFrom);

    return MoveResult.castlingWith(rookMove);
  }

  /**
   * Finds the castling rook by scanning outwards from the king's destination, which is where the
   * king now stands.
   *
   * @param fromSquare the square to start scanning beyond
   * @param rowStep the vertical direction of travel
   * @param columnStep the horizontal direction of travel
   * @param color the colour of the castling side
   * @return the rook's square, or null if the first piece found is not that side's rook
   */
  @Nullable
  private Square findRookBeyond(Square fromSquare, int rowStep, int columnStep, PieceColor color) {
    Square square = SquareUtils.offsetOrNull(fromSquare, rowStep, columnStep);

    while (square != null) {
      Piece piece = chessBoard.getPiece(square);

      if (piece != null) {
        return piece.type() == PieceType.ROOK && piece.color() == color ? square : null;
      }

      square = SquareUtils.offsetOrNull(square, rowStep, columnStep);
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
      return MoveResult.pendingPromotion();
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

    if (selectedPiece.type() == PieceType.KING) {
      chessGame.getCastlingStatus().markKingMoved(currentTurn);
    } else if (selectedPiece.type() == PieceType.ROOK) {
      chessGame.getCastlingStatus().markRookMoved(currentTurn, fromSquare);
    }

    if (capturedPiece != null && capturedPiece.type() == PieceType.ROOK) {
      chessGame.getCastlingStatus().markRookMoved(capturedPiece.color(), toSquare);
    }
  }
}
