package codes.castled.chess.engine.common.move;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.common.board.SquareUtils;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.move.calculator.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provides access to possible moves and targeted squares. */
public final class MoveCalculatorImpl implements MoveCalculator {

  private final MoveValidator moveValidator;
  private final PawnMoveCalculator pawnMoveCalculator;
  private final RookMoveCalculator rookMoveCalculator;
  private final BishopMoveCalculator bishopMoveCalculator;
  private final KnightMoveCalculator knightMoveCalculator;
  private final QueenMoveCalculator queenMoveCalculator;
  private final KingMoveCalculator kingMoveCalculator;

  /** Whether the vertical castling easter egg is enabled. */
  private final boolean verticalCastling;

  public MoveCalculatorImpl(
      MoveValidator moveValidator,
      PawnMoveCalculator pawnMoveCalculator,
      RookMoveCalculator rookMoveCalculator,
      BishopMoveCalculator bishopMoveCalculator,
      KnightMoveCalculator knightMoveCalculator,
      QueenMoveCalculator queenMoveCalculator,
      KingMoveCalculator kingMoveCalculator,
      boolean verticalCastling) {
    this.verticalCastling = verticalCastling;
    this.moveValidator = moveValidator;
    this.pawnMoveCalculator = pawnMoveCalculator;
    this.rookMoveCalculator = rookMoveCalculator;
    this.bishopMoveCalculator = bishopMoveCalculator;
    this.knightMoveCalculator = knightMoveCalculator;
    this.queenMoveCalculator = queenMoveCalculator;
    this.kingMoveCalculator = kingMoveCalculator;
  }

  @Override
  public List<Square> getPossibleMoves(ChessGame chessGame, Square pieceSquare) {
    ChessBoard chessBoard = chessGame.getChessBoard();
    Piece piece = chessBoard.getPiece(pieceSquare);

    if (piece == null) {
      return Collections.emptyList();
    }

    ChessBoardImpl boardImpl = (ChessBoardImpl) chessBoard;

    List<Square> possibleMoves =
        new ArrayList<>(
            getRawMoves(boardImpl, pieceSquare).stream()
                .filter(
                    targetSquare ->
                        !moveValidator.wouldCauseSelfCheck(
                            boardImpl, new Move(piece, pieceSquare, targetSquare), piece.color()))
                .toList());

    if (piece.type() == PieceType.KING) {
      addCastlingMoves(chessGame, possibleMoves, pieceSquare, piece.color());
    }

    return possibleMoves;
  }

  @Override
  public List<Square> getRawMoves(ChessBoard board, Square pieceSquare) {
    Piece piece = board.getPiece(pieceSquare);

    if (piece == null) {
      return List.of();
    }

    return switch (piece.type()) {
      case ROOK -> rookMoveCalculator.getMoves(board, pieceSquare, piece);
      case KNIGHT -> knightMoveCalculator.getMoves(board, pieceSquare, piece);
      case BISHOP -> bishopMoveCalculator.getMoves(board, pieceSquare, piece);
      case QUEEN -> queenMoveCalculator.getMoves(board, pieceSquare, piece);
      case KING -> kingMoveCalculator.getMoves(board, pieceSquare, piece);
      case PAWN -> pawnMoveCalculator.getMoves(board, pieceSquare, piece);
    };
  }

  @Override
  public List<Square> getAllRawMoves(ChessBoard chessBoard, PieceColor color) {
    List<Square> pieceSquares =
        chessBoard.getColoredPieces(
            color,
            PieceType.ROOK,
            PieceType.PAWN,
            PieceType.KNIGHT,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.QUEEN);
    List<Square> rawMoves = new ArrayList<>();
    pieceSquares.forEach(pieceSquare -> rawMoves.addAll(getRawMoves(chessBoard, pieceSquare)));
    return rawMoves;
  }

  /**
   * Checks whether castling moves can be played and adds them to the given list of available moves
   * if so.
   *
   * <p>Each rook that has never moved is considered in turn rather than assuming one rook per
   * side on a fixed file, so a rook on any file works — including one that appeared through
   * promotion, which is what makes vertical castling reachable.
   *
   * @param chessGame the played game
   * @param squares the list of possible moves
   * @param kingSquare the square the king stands on
   * @param color the color of the pieces to check castling
   */
  private void addCastlingMoves(
      ChessGame chessGame, List<Square> squares, Square kingSquare, PieceColor color) {
    if (chessGame.hasKingMoved(color)) {
      return;
    }

    ChessBoard chessBoard = chessGame.getChessBoard();
    List<Square> allRawMoves = getAllRawMoves(chessBoard, PieceColor.getOtherColor(color));

    for (Square rookSquare : chessGame.getUnmovedRookSquares(color)) {
      Square destination =
          castleDestination(chessBoard, allRawMoves, kingSquare, rookSquare, color);

      if (destination != null) {
        squares.add(destination);
      }
    }
  }

  /**
   * Works out where the king would land castling with the rook on the given square, if that
   * castling is legal at all.
   *
   * @param chessBoard the associated chess board
   * @param allRawMoves the list of all raw moves from the other color pieces
   * @param kingSquare the square the king stands on
   * @param rookSquare the square of the rook to castle with
   * @param color the color of the castling side
   * @return the king's destination square, or null if this castling is not available
   */
  @Nullable
  private Square castleDestination(
      ChessBoard chessBoard,
      List<Square> allRawMoves,
      Square kingSquare,
      Square rookSquare,
      PieceColor color) {

    Piece rook = chessBoard.getPiece(rookSquare);
    if (rook == null || rook.type() != PieceType.ROOK || rook.color() != color) {
      return null;
    }

    int rowStep = Integer.signum(rookSquare.getRowIndex() - kingSquare.getRowIndex());
    int columnStep = Integer.signum(rookSquare.getColumnIndex() - kingSquare.getColumnIndex());

    // The rook must share the king's rank (standard castling) or, when the easter egg is on,
    // its file. A rook that shares neither is not reachable in a straight line.
    boolean alongRank = rowStep == 0 && columnStep != 0;
    boolean alongFile = columnStep == 0 && rowStep != 0;

    if (alongFile && !(verticalCastling && rowStep == forwardDirection(color))) {
      return null;
    }

    if (!alongRank && !alongFile) {
      return null;
    }

    if (!isPathClear(chessBoard, kingSquare, rookSquare, rowStep, columnStep)) {
      return null;
    }

    Square crossed = SquareUtils.offsetOrNull(kingSquare, rowStep, columnStep);
    Square destination = SquareUtils.offsetOrNull(kingSquare, rowStep * 2, columnStep * 2);

    if (crossed == null || destination == null) {
      return null;
    }

    // The king may not castle out of, through, or into check.
    if (isAttacked(chessBoard, allRawMoves, kingSquare, color)
        || isAttacked(chessBoard, allRawMoves, crossed, color)
        || isAttacked(chessBoard, allRawMoves, destination, color)) {
      return null;
    }

    return destination;
  }

  /**
   * @param color the color of the castling side
   * @return the row direction that color's pawns advance in, which is the only direction it may
   *     castle vertically — a promoted rook can only ever stand at the far end of that file
   */
  private int forwardDirection(PieceColor color) {
    return color == PieceColor.WHITE ? 1 : -1;
  }

  /**
   * @return whether every square strictly between the king and the rook is empty
   */
  private boolean isPathClear(
      ChessBoard chessBoard, Square kingSquare, Square rookSquare, int rowStep, int columnStep) {
    Square square = SquareUtils.offsetOrNull(kingSquare, rowStep, columnStep);

    while (square != null && !square.equals(rookSquare)) {
      if (chessBoard.isOccupied(square)) {
        return false;
      }

      square = SquareUtils.offsetOrNull(square, rowStep, columnStep);
    }

    return square != null;
  }

  /** Returns whether the given square is currently being attacked by the other color. */
  private boolean isAttacked(
      ChessBoard chessBoard, List<Square> allRawMoves, Square square, PieceColor defendingColor) {
    return allRawMoves.contains(square) || isAttackedByPawn(chessBoard, square, defendingColor);
  }

  /**
   * Checks whether the given square is attacked by an opposite pawn. This is needed because
   * getAllRawMoves() only gets the diagonal attack squares of a pawn if there are pieces on them.
   * But in the case of castling, those diagonal squares are attacked even if there is currently no
   * piece on them. So this method checks and returns whether the castling squares are attacked by a
   * pawn even if there is no piece on those squares. If those squares are attacked, castling is not
   * possible.
   */
  private boolean isAttackedByPawn(
      ChessBoard chessBoard, Square targetSquare, PieceColor defendingColor) {
    PieceColor attackingColor = PieceColor.getOtherColor(defendingColor);
    int attackerRowOffset = attackingColor == PieceColor.WHITE ? -1 : 1;

    Square leftAttackerSquare = SquareUtils.offsetOrNull(targetSquare, attackerRowOffset, -1);
    Square rightAttackerSquare = SquareUtils.offsetOrNull(targetSquare, attackerRowOffset, 1);

    if (leftAttackerSquare != null) {
      Piece piece = chessBoard.getPiece(leftAttackerSquare);
      if (piece != null && piece.type() == PieceType.PAWN && piece.color() == attackingColor) {
        return true;
      }
    }

    if (rightAttackerSquare != null) {
      Piece piece = chessBoard.getPiece(rightAttackerSquare);
      if (piece != null && piece.type() == PieceType.PAWN && piece.color() == attackingColor) {
        return true;
      }
    }

    return false;
  }
}
