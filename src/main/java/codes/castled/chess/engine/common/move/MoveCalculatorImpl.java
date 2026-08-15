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

  public MoveCalculatorImpl(
      MoveValidator moveValidator,
      PawnMoveCalculator pawnMoveCalculator,
      RookMoveCalculator rookMoveCalculator,
      BishopMoveCalculator bishopMoveCalculator,
      KnightMoveCalculator knightMoveCalculator,
      QueenMoveCalculator queenMoveCalculator,
      KingMoveCalculator kingMoveCalculator) {
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
      addCastlingMoves(chessGame, possibleMoves, piece.color());
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
   * @param chessGame the played game
   * @param squares the list of possible moves
   * @param color the color of the pieces to check castling
   */
  private void addCastlingMoves(ChessGame chessGame, List<Square> squares, PieceColor color) {
    ChessBoard chessBoard = chessGame.getChessBoard();
    if (!chessGame.hasKingMoved(color)) {
      char row = color == PieceColor.WHITE ? '1' : '8';

      List<Square> allRawMoves = getAllRawMoves(chessBoard, PieceColor.getOtherColor(color));

      addKingSideCastleSquare(chessGame, squares, allRawMoves, color, row);

      addQueenSideCastleSquare(chessGame, squares, allRawMoves, color, row);
    }
  }

  /**
   * @param chessGame the played game
   * @param squares the list of possible moves
   * @param allRawMoves the list of all raw moves from the other color pieces
   * @param kingColor the color of the king
   * @param row the row of the castling line
   */
  private void addKingSideCastleSquare(
      ChessGame chessGame,
      List<Square> squares,
      List<Square> allRawMoves,
      PieceColor kingColor,
      char row) {
    ChessBoard chessBoard = chessGame.getChessBoard();
    if (!chessGame.hasKingSideRookMoved(kingColor)
        && !chessBoard.isOccupied(new Square(row, 'F'))
        && !chessBoard.isOccupied(new Square(row, 'G'))) {

      boolean kingSideAttacked =
          isAttacked(chessBoard, allRawMoves, new Square(row, 'E'), kingColor)
              || isAttacked(chessBoard, allRawMoves, new Square(row, 'F'), kingColor)
              || isAttacked(chessBoard, allRawMoves, new Square(row, 'G'), kingColor);

      if (!kingSideAttacked) {
        squares.add(new Square(row, 'G'));
      }
    }
  }

  /**
   * @param chessGame the played game
   * @param squares the list of possible moves
   * @param allRawMoves the list of all raw moves from the other color pieces
   * @param kingColor the color of the king
   * @param row the row of the castling line
   */
  private void addQueenSideCastleSquare(
      ChessGame chessGame,
      List<Square> squares,
      List<Square> allRawMoves,
      PieceColor kingColor,
      char row) {
    ChessBoard chessBoard = chessGame.getChessBoard();
    if (!chessGame.hasQueenSideRookMoved(kingColor)
        && !chessBoard.isOccupied(new Square(row, 'D'))
        && !chessBoard.isOccupied(new Square(row, 'C'))
        && !chessBoard.isOccupied(new Square(row, 'B'))) {

      boolean queenSideAttacked =
          isAttacked(chessBoard, allRawMoves, new Square(row, 'E'), kingColor)
              || isAttacked(chessBoard, allRawMoves, new Square(row, 'D'), kingColor)
              || isAttacked(chessBoard, allRawMoves, new Square(row, 'C'), kingColor);

      if (!queenSideAttacked) {
        squares.add(new Square(row, 'C'));
      }
    }
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
