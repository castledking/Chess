package codes.castled.chess.game;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UI-independent evaluation of chess game status (check, checkmate, stalemate,
 * insufficient-material draw) built entirely on top of the authoritative {@link ChessGame}
 * model and {@link MoveCalculator}, so no chess rules are duplicated here.
 */
public final class GameStatusEvaluator {

  private final MoveCalculator moveCalculator;

  public GameStatusEvaluator(MoveCalculator moveCalculator) {
    this.moveCalculator = moveCalculator;
  }

  /**
   * @param game the game
   * @param playerId the player whose king to test
   * @return whether the given player's king is currently attacked
   */
  public boolean isInCheck(ChessGame game, UUID playerId) {
    PieceColor playerColor = game.getColor(playerId);
    PieceColor enemyColor = PieceColor.getOtherColor(playerColor);

    ChessBoard board = game.getChessBoard();
    List<Square> kingSquares = board.getColoredPieces(playerColor, PieceType.KING);
    if (kingSquares.isEmpty()) {
      return false;
    }
    Square kingSquare = kingSquares.get(0);

    for (Square pieceSquare : board.getColoredPieces(enemyColor, PieceType.values())) {
      for (Square targetSquare : moveCalculator.getRawMoves(board, pieceSquare)) {
        if (targetSquare.equals(kingSquare)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @param game the game
   * @param playerId the player to test
   * @return whether the player has been checkmated
   */
  public boolean isCheckmate(ChessGame game, UUID playerId) {
    return !hasPossibleMoves(game, playerId) && isInCheck(game, playerId);
  }

  /**
   * @param game the game
   * @param playerId the player to test
   * @return whether the player is stalemated (no legal moves and not in check)
   */
  public boolean isStalemate(ChessGame game, UUID playerId) {
    return !hasPossibleMoves(game, playerId) && !isInCheck(game, playerId);
  }

  /**
   * @param game the game
   * @param playerId the player to test
   * @return whether the player has at least one legal move
   */
  public boolean hasPossibleMoves(ChessGame game, UUID playerId) {
    ChessBoard board = game.getChessBoard();
    for (Square square : board.getColoredPieces(game.getColor(playerId), PieceType.values())) {
      if (!moveCalculator.getPossibleMoves(game, square).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param board the board
   * @return whether the position is an insufficient-material draw
   */
  public boolean isInsufficientMaterialDraw(ChessBoard board) {
    return isDraw(board, List.of(PieceType.KING), List.of(PieceType.KING))
        || isDraw(board, List.of(PieceType.KING, PieceType.BISHOP), List.of(PieceType.KING))
        || isDraw(board, List.of(PieceType.KING), List.of(PieceType.KING, PieceType.BISHOP))
        || isDraw(board, List.of(PieceType.KING, PieceType.KNIGHT), List.of(PieceType.KING))
        || isDraw(board, List.of(PieceType.KING), List.of(PieceType.KING, PieceType.KNIGHT));
  }

  private boolean isDraw(ChessBoard board, List<PieceType> whiteTypes, List<PieceType> blackTypes) {
    return hasExactlyPieces(board, PieceColor.WHITE, whiteTypes)
        && hasExactlyPieces(board, PieceColor.BLACK, blackTypes);
  }

  private boolean hasExactlyPieces(
      ChessBoard board, PieceColor color, List<PieceType> expectedPieceTypes) {
    List<Square> pieceSquares = board.getColoredPieces(color);
    List<PieceType> remaining = new ArrayList<>(expectedPieceTypes);
    for (Square pieceSquare : pieceSquares) {
      Piece piece = board.getPiece(pieceSquare);
      if (piece == null) {
        continue;
      }
      if (!remaining.remove(piece.type())) {
        return false;
      }
    }
    return remaining.isEmpty();
  }
}
