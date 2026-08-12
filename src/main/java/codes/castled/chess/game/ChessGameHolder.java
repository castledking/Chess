package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.ui.ChessView;
import codes.castled.chess.ui.ChessViewFactory;
import com.dxzell.pocketchess.api.board.ChessBoard;
import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.move.Move;
import com.dxzell.pocketchess.api.move.MoveCalculator;
import com.dxzell.pocketchess.api.move.MoveResult;
import com.dxzell.pocketchess.api.move.MoveResultType;
import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Holds the plugin-side state for one chess game and routes player input into the
 * authoritative chess model. Rendering is delegated to a {@link ChessView}, so the game logic
 * is independent of how the board is shown.
 */
@Getter
public final class ChessGameHolder {

  private final Chess plugin;
  private final GameService gameService;
  private final ChessGame chessGame;
  private final ChessBoard chessBoard;
  private final MessageConfig messageConfig;
  private final SettingsConfig settingsConfig;
  private final SoundPlayer soundPlayer;
  private final GameStatusEvaluator statusEvaluator;
  private final ChessView view;
  private final MoveHandler moveHandler;
  private final GameClock gameClock;
  private final DrawHandler drawHandler;
  private final SurrenderHandler surrenderHandler;

  public ChessGameHolder(
      Chess plugin,
      GameService gameService,
      ChessGame chessGame,
      UUID whitePlayerId,
      UUID blackPlayerId,
      MoveCalculator moveCalculator,
      MessageConfig messageConfig,
      SettingsConfig settingsConfig,
      SoundPlayer soundPlayer,
      ChessViewFactory viewFactory) {
    this.plugin = plugin;
    this.gameService = gameService;
    this.chessGame = chessGame;
    this.chessBoard = chessGame.getChessBoard();
    this.messageConfig = messageConfig;
    this.settingsConfig = settingsConfig;
    this.soundPlayer = soundPlayer;

    this.statusEvaluator = new GameStatusEvaluator(moveCalculator);
    this.view = viewFactory.create(this);
    this.moveHandler = new MoveHandler(this, chessGame, moveCalculator, view, statusEvaluator);
    this.gameClock = new GameClock(plugin, this);
    this.drawHandler = new DrawHandler(this);
    this.surrenderHandler = new SurrenderHandler(this);

    // Open the dialog board for both players (no Bukkit inventory is ever opened).
    view.openBoard(whitePlayerId);
    view.openBoard(blackPlayerId);

    gameClock.start();
    sendGameMessage(messageConfig.getGameStarted());
  }

  /**
   * Backend-independent board interaction. Selects one of the player's own pieces or, when a
   * piece is already selected, attempts a move. Illegal interactions only show an info message
   * and never mutate the game.
   *
   * @param playerId the id of the clicking player
   * @param square the clicked board square
   */
  public void handleBoardClick(UUID playerId, Square square) {
    if (moveHandler.hasPendingPromotion(playerId)) {
      view.showInfo(playerId, messageConfig.getPromotionRequired());
      return;
    }

    Piece piece = chessBoard.getPiece(square);
    Square selectedSquare = chessGame.getSelectedPieceSquare(playerId);

    if (piece != null && piece.color() == getColor(playerId)) {
      handleSelectPiece(square, playerId);
    } else if (selectedSquare != null) {
      if (chessGame.getCurrentTurn().equals(playerId)) {
        handleMove(square, playerId);
      } else {
        view.showInfo(playerId, messageConfig.getNotYourTurn());
      }
    }
    // Removed "no piece selected" message - clicking an empty/unoccupied square does nothing.
  }

  /** Handles a draw control interaction. */
  public void handleDrawClick(UUID playerId) {
    drawHandler.handleDrawClick(playerId);
  }

  /** Handles a surrender/resign control interaction. */
  public void handleSurrenderClick(UUID playerId) {
    surrenderHandler.handleSurrenderClick(playerId);
  }

  /**
   * Applies a promotion choice made through the dialog UI.
   *
   * @param playerId the promoting player
   * @param chosen the chosen promotion piece type
   */
  public void handlePromotionChoice(UUID playerId, PieceType chosen) {
    PieceColor color = getColor(playerId);
    if (!moveHandler.hasPendingPromotion(color)) {
      view.refreshViewer(playerId);
      return;
    }
    moveHandler.applyPromotion(color, chosen);
  }

  private void handleMove(Square destination, UUID playerId) {
    MoveResult moveResult = chessGame.makeMove(destination, playerId);
    Move move = new Move(null, chessGame.getSelectedPieceSquare(playerId), destination);

    if (moveResult.type() == MoveResultType.SUCCESS) {
      // makeMove does not clear the selection; do it now so the mover's source square stops
      // rendering as SELECTED. The last-move highlight then marks from/to for both players.
      chessGame.unselectPiece(playerId);
      moveHandler.handleSuccessfulMove(moveResult, move, playerId, messageConfig);
    } else {
      view.showInfo(playerId, messageConfig.getInvalidSquare());
    }
  }

  private void handleSelectPiece(Square clickedSquare, UUID playerId) {
    Square beforeSelectedSquare = chessGame.getSelectedPieceSquare(playerId);

    if (beforeSelectedSquare != null && beforeSelectedSquare.equals(clickedSquare)) {
      chessGame.unselectPiece(playerId);
      view.unhighlightSelected(playerId, clickedSquare);
      view.unhighlightLegalMoves(playerId);
    } else {
      chessGame.selectPiece(clickedSquare, playerId);
      view.highlightSelected(playerId, beforeSelectedSquare, clickedSquare);
      view.highlightLegalMoves(playerId, clickedSquare);
    }
    view.clearPromotion(playerId);
  }

  /** Ends the game and cleans up the view and clock. */
  public void endGame(UUID winnerId) {
    chessGame.endGame(winnerId);
    gameService.removeGame(chessGame.getGameId());
    // Show the final position, then stop tracking viewers.
    view.refresh();
    view.closeAll();
    gameClock.stop();

    if (winnerId != null) {
      sendGameMessage(
          ChatColor.GREEN
              + "The winner of the game is: "
              + ChatColor.GOLD
              + Bukkit.getOfflinePlayer(winnerId).getName());
      soundPlayer.playWinLoseSounds(winnerId, getOtherPlayerId(winnerId));
    } else {
      sendGameMessage(ChatColor.GRAY + "The game ended in a draw.");
      soundPlayer.playDrawSound(chessGame.getWhitePlayerId(), chessGame.getBlackPlayerId());
    }
  }

  /** @return the player's colour */
  public PieceColor getColor(UUID playerId) {
    return chessGame.getColor(playerId);
  }

  /** @return the id of the other player */
  public UUID getOtherPlayerId(UUID playerId) {
    return playerId.equals(chessGame.getWhitePlayerId())
        ? chessGame.getBlackPlayerId()
        : chessGame.getWhitePlayerId();
  }

  /** @return whether the given id belongs to this game */
  public boolean isParticipant(UUID playerId) {
    return playerId.equals(chessGame.getWhitePlayerId())
        || playerId.equals(chessGame.getBlackPlayerId());
  }

  /** Sends the given message to both players of the game. */
  public void sendGameMessage(String message) {
    Player whitePlayer = Bukkit.getPlayer(chessGame.getWhitePlayerId());
    if (whitePlayer != null) {
      whitePlayer.sendMessage(message);
    }
    Player blackPlayer = Bukkit.getPlayer(chessGame.getBlackPlayerId());
    if (blackPlayer != null) {
      blackPlayer.sendMessage(message);
    }
  }
}
