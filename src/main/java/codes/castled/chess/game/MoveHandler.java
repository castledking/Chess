package codes.castled.chess.game;

import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.ui.ChessView;
import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.move.Move;
import com.dxzell.pocketchess.api.move.MoveCalculator;
import com.dxzell.pocketchess.api.move.MoveResult;
import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;
import com.dxzell.pocketchess.common.board.ChessBoardImpl;
import com.dxzell.pocketchess.common.board.SquareUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Applies successful moves to the game model and drives the {@link ChessView} to reflect the
 * new state. All rules/status logic is delegated to {@link GameStatusEvaluator}, so no chess
 * rules are duplicated here.
 */
public final class MoveHandler {

  private final ChessGameHolder holder;
  private final ChessGame chessGame;
  private final ChessView view;
  private final GameStatusEvaluator status;
  private Move whitePromotionMove;
  private Move blackPromotionMove;

  public MoveHandler(
      ChessGameHolder holder,
      ChessGame chessGame,
      MoveCalculator moveCalculator,
      ChessView view,
      GameStatusEvaluator status) {
    this.holder = holder;
    this.chessGame = chessGame;
    this.view = view;
    this.status = status;
  }

  /**
   * Updates the view and game state after a successful move.
   *
   * @param moveResult the result of the played move
   * @param move the played move
   * @param playerId the id of the player who made the move
   * @param messageConfig to get custom messages
   * @return whether the final game state is fully rendered for every viewer; {@code false} only
   *     when a promotion is pending (just the promoter got their promotion dialog)
   */
  public boolean handleSuccessfulMove(
      MoveResult moveResult, Move move, UUID playerId, MessageConfig messageConfig) {
    if (moveResult.checkmate()) {
      holder.endGame(playerId);
      return true;
    } else if (moveResult.promotion()) {
      handlePromotion(move.from(), move.to(), playerId);
      view.showInfo(playerId, messageConfig.getPickPromotion());
      return false;
    } else {
      view.applyMove(move);
      handleCastling(moveResult, move.to());
      handleEnPassant(moveResult, move.to(), playerId);
      finishMove(move.to());
      return true;
    }
  }

  private void handleCastling(MoveResult moveResult, Square destination) {
    if (moveResult.castling()) {
      Square rookFrom;
      Square rookTo;
      if (destination.getColumnIndex() == 6) { // King side castling
        rookFrom = new Square(destination.row(), 'H');
        rookTo = new Square(destination.row(), 'F');
      } else { // Queen side castling
        rookFrom = new Square(destination.row(), 'A');
        rookTo = new Square(destination.row(), 'D');
      }
      view.applyMove(new Move(null, rookFrom, rookTo));
    }
  }

  private void handleEnPassant(MoveResult moveResult, Square toSquare, UUID playerId) {
    if (moveResult.enPassant()) {
      view.removePiece(
          SquareUtils.offsetOrNull(
              toSquare, chessGame.getColor(playerId) == PieceColor.WHITE ? -1 : 1, 0));
    }
  }

  private void handlePromotion(Square fromSquare, Square toSquare, UUID playerId) {
    Move move = new Move(null, fromSquare, toSquare);
    if (chessGame.getColor(playerId) == PieceColor.WHITE) {
      whitePromotionMove = move;
    } else {
      blackPromotionMove = move;
    }
    view.promptPromotion(playerId, move);
  }

  /**
   * Applies a chosen promotion through the engine's board and finishes the move.
   *
   * @param color the promoting colour
   * @param type the chosen promotion piece type
   */
  public void applyPromotion(PieceColor color, PieceType type) {
    Move pending = color == PieceColor.WHITE ? whitePromotionMove : blackPromotionMove;
    if (pending == null) {
      return;
    }
    Square fromSquare = pending.from();
    Square toSquare = pending.to();
    Piece promotionPiece = new Piece(type, color);

    ChessBoardImpl board = (ChessBoardImpl) chessGame.getChessBoard();
    board.setPiece(fromSquare, null);
    board.setPiece(toSquare, promotionPiece);

    UUID promotingPlayerId =
        color == PieceColor.WHITE ? chessGame.getWhitePlayerId() : chessGame.getBlackPlayerId();
    view.removePiece(fromSquare);
    view.setPiece(toSquare, promotionPiece);

    board.setLastPlayedMove(new Move(null, fromSquare, toSquare));

    view.clearPromotion(promotingPlayerId);
    if (color == PieceColor.WHITE) {
      whitePromotionMove = null;
    } else {
      blackPromotionMove = null;
    }

    finishMove(toSquare);
  }

  /**
   * @param playerId the id of a player
   * @return whether that player has an unresolved promotion
   */
  public boolean hasPendingPromotion(UUID playerId) {
    return hasPendingPromotion(chessGame.getColor(playerId));
  }

  /**
   * @param color a colour
   * @return whether that colour has an unresolved promotion
   */
  public boolean hasPendingPromotion(PieceColor color) {
    return (color == PieceColor.WHITE ? whitePromotionMove : blackPromotionMove) != null;
  }

  /**
   * Puts the game in a fresh state, which allows the other player to make a move.
   *
   * @param destination the square the piece was moved to
   */
  private void finishMove(Square destination) {
    UUID opponentId = holder.getOtherPlayerId(chessGame.getCurrentTurn());

    if (status.isInsufficientMaterialDraw(chessGame.getChessBoard())
        || status.isStalemate(chessGame, opponentId)) {
      holder.endGame(null);
      return;
    }

    if (status.isCheckmate(chessGame, opponentId)) {
      holder.endGame(chessGame.getCurrentTurn());
      return;
    }

    UUID currentTurn = chessGame.getCurrentTurn();
    UUID otherPlayer = holder.getOtherPlayerId(currentTurn);

    if (status.isInCheck(chessGame, otherPlayer)) {
      holder.getSoundPlayer().playCheckMoveSound(otherPlayer, currentTurn);
    } else {
      holder.getSoundPlayer().playMoveSound(currentTurn, otherPlayer);
    }

    if (!view.hasOpen(otherPlayer)) {
      sendMessage(otherPlayer, holder.getMessageConfig().getOpponentMoved());
    }

    view.unhighlightSelectedForBoth(destination);
    view.unhighlightLegalMovesForBoth();
    view.highlightLastMove();
    view.highlightOpponentSelection(chessGame);
    // No clock render here: it would send both viewers a dialog whose sequence is already
    // obsolete by the time the final refresh below bumps it, and clicks racing that send
    // (typically the mover starting a premove right after their own move) would be rejected
    // as stale. The final refresh and the GameClock's 1-second re-render both show the clock.

    chessGame.toggleTurn();

    // The turn just passed to the premover: fire their queued premove now, before the final
    // re-render, so the board never briefly shows a stale position. A failed (no longer
    // legal) premove is silently discarded. When a premove fired, its own finishMove already
    // refreshed both boards with the final state — refresh again here would only bump the
    // sequence and open a window that silently rejects the premover's next clicks.
    boolean premoveRenderedFinalState = holder.playPremoveIfQueued();

    view.clearTimeHighlight(chessGame.getCurrentTurn());
    view.cancelInfoTasks();
    view.resetInfo();

    holder.getDrawHandler().resetTimestamps();

    if (!premoveRenderedFinalState) {
      // Re-render both boards from the now-authoritative state.
      view.refresh();
    }
  }

  private void sendMessage(UUID playerId, String message) {
    Player player = Bukkit.getPlayer(playerId);
    if (player != null) {
      player.sendMessage(message);
    }
  }
}
