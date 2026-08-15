package codes.castled.chess.game;

import codes.castled.chess.Chess;
import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.config.SettingsConfig;
import codes.castled.chess.ui.ChessView;
import codes.castled.chess.ui.ChessViewFactory;
import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.board.SquareUtils;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final MoveCalculator moveCalculator;
  private final ChessView view;
  private final MoveHandler moveHandler;
  private final GameClock gameClock;
  private final DrawHandler drawHandler;
  private final SurrenderHandler surrenderHandler;

  /**
   * The player's stacked premove queue, keyed by the premover's id. Entries are stored only
   * while it is not that player's turn; the head is played (and the rest of the queue kept for
   * later turns) the moment their turn arrives. Only ever touched on the global region thread.
   */
  private final Map<UUID, Deque<Move>> premoves = new HashMap<>();

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
    this.moveCalculator = moveCalculator;
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
    broadcastWatchGame(whitePlayerId, blackPlayerId);
  }

  private void broadcastWatchGame(UUID whiteId, UUID blackId) {
    String whiteName = Bukkit.getOfflinePlayer(whiteId).getName();
    String blackName = Bukkit.getOfflinePlayer(blackId).getName();
    if (whiteName == null) whiteName = "White";
    if (blackName == null) blackName = "Black";

    Component msg =
        Component.text("")
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(Component.text("Watch Game", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.GRAY))
            .append(Component.text(whiteName, NamedTextColor.WHITE))
            .append(Component.text(" vs ", NamedTextColor.GRAY))
            .append(Component.text(blackName, NamedTextColor.WHITE))
            .clickEvent(ClickEvent.suggestCommand("/chess watch " + whiteName))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to spectate this game", NamedTextColor.GREEN)));

    for (Player online : Bukkit.getOnlinePlayers()) {
      if (!online.getUniqueId().equals(whiteId) && !online.getUniqueId().equals(blackId)) {
        online.sendMessage(msg);
      }
    }
  }

  /**
   * Backend-independent board interaction. Selects one of the player's own pieces or, when a
   * piece is already selected, attempts a move. Out of turn, a piece-and-destination click
   * sequence appends to the player's premove queue instead of moving; any other
   * non-premove click cancels the whole queue. Illegal interactions do nothing and never
   * mutate the game.
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
    boolean myTurn = chessGame.getCurrentTurn().equals(playerId);
    Square ghostSel = view.getGhostSelected(playerId);

    // Clicking a premove ghost square (destination or intermediate source): simulate
    // the premove chain to find the piece that would be there, then mark it as a
    // ghost selection so legal moves are computed from the simulated position.
    if (!myTurn && selectedSquare == null && ghostSel == null) {
      List<Move> queue = getPremoves(playerId);
      for (Move m : queue) {
        if (m.to().equals(square) || m.from().equals(square)) {
          Map<Square, Piece> sim = simulatePremoves(chessBoard, queue);
          Piece ghostPiece = sim.get(square);
          if (ghostPiece != null && ghostPiece.color() == getColor(playerId)) {
            view.setGhostSelected(playerId, square);
            view.refreshViewer(playerId);
          }
          return;
        }
      }
    }

    // Ghost is selected and user clicked a destination: queue premove from ghost
    if (!myTurn && ghostSel != null && !square.equals(ghostSel)) {
      view.setGhostSelected(playerId, null);
      if (!queuePremove(playerId, ghostSel, square)) {
        cancelPremoves(playerId);
      }
      view.refreshViewer(playerId);
      return;
    }

    // Ghost is selected and user clicked the same ghost square: deselect
    if (!myTurn && ghostSel != null && square.equals(ghostSel)) {
      view.setGhostSelected(playerId, null);
      view.refreshViewer(playerId);
      return;
    }

    // Any non-ghost click clears the ghost selection
    view.setGhostSelected(playerId, null);

    if (piece != null && piece.color() == getColor(playerId)) {
      if (myTurn) {
        handleSelectPiece(square, playerId);
      } else {
        handleOffTurnPieceClick(square, playerId);
      }
    } else if (selectedSquare != null && myTurn) {
      handleMove(square, playerId);
    } else if (selectedSquare != null) {
      // Out of turn, a selected own piece plus any destination appends to the premove queue.
      // A destination the engine rejects is a non-premove click and cancels the whole queue.
      if (!queuePremove(playerId, selectedSquare, square)) {
        cancelPremoves(playerId);
        view.refreshViewer(playerId);
      }
    }
    // Empty squares and out-of-turn destination clicks without a selection are ignored;
    // premoves are cancelled only via the dedicated cancel button.
  }

  /**
   * Handles a click on one of the player's own pieces while it is not their turn. Selecting a
   * piece is allowed so a destination click can queue a premove; the existing premove queue
   * is preserved so clicking a different piece and choosing a destination appends to it.
   */
  private void handleOffTurnPieceClick(Square clickedSquare, UUID playerId) {
    handleSelectPiece(clickedSquare, playerId);
    view.clearPromotion(playerId);
  }

  /**
   * Appends a premove for a player whose turn has not yet arrived. Only the queue's first
   * entry is validated against the current position — later entries are planned against
   * positions that do not exist yet and are validated at play time instead.
   *
   * @return whether the premove was queued; {@code false} when the first entry is illegal
   */
  private boolean queuePremove(UUID playerId, Square from, Square to) {
    if (!hasQueuedPremoves(playerId)) {
      List<Square> possible =
          PremoveMoveCalculator.getPremoveMoves(
              chessGame, from, settingsConfig.isVerticalCastlingEnabled());
      if (possible.isEmpty() || !possible.contains(to)) {
        return false;
      }
    }
    premoves.computeIfAbsent(playerId, unused -> new ArrayDeque<>())
        .addLast(new Move(null, from, to));
    chessGame.unselectPiece(playerId);
    view.refreshViewer(playerId);
    return true;
  }

  private void cancelPremoves(UUID playerId) {
    premoves.remove(playerId);
  }

  private boolean hasQueuedPremoves(UUID playerId) {
    Deque<Move> queue = premoves.get(playerId);
    return queue != null && !queue.isEmpty();
  }

  /**
   * Plays the head of the current turn's player premove queue, if one is queued. Called by
   * {@link MoveHandler} right after the turn toggles, so a premove fires the moment its owner
   * may move. Remaining entries stay queued for the player's later turns; they fire one per
   * turn, each re-validated against the position it meets. A head that no longer fits the
   * position discards the whole queue — later entries were planned against positions that no
   * longer exist and must not be skipped past.
   *
   * @return whether the final state is fully rendered for every viewer (a played premove runs
   *     through finishMove, which renders it); {@code false} when nothing fired, the queue was
   *     discarded, or a promotion is pending — the caller must then refresh
   */
  public boolean playPremoveIfQueued() {
    UUID currentTurn = chessGame.getCurrentTurn();
    Deque<Move> queue = premoves.get(currentTurn);
    if (queue == null || queue.isEmpty()) {
      return false;
    }
    Move premove = queue.peekFirst();
    Piece fromPiece = chessBoard.getPiece(premove.from());
    if (fromPiece == null
        || fromPiece.color() != getColor(currentTurn)
        || !moveCalculator.getPossibleMoves(chessGame, premove.from()).contains(premove.to())) {
      // Discarded silently; the caller's refresh re-renders both viewers.
      premoves.remove(currentTurn);
      return false;
    }
    queue.pollFirst();
    chessGame.selectPiece(premove.from(), currentTurn);
    MoveResult moveResult = chessGame.makeMove(premove.to(), currentTurn);
    chessGame.unselectPiece(currentTurn);
    if (moveResult.type() == MoveResultType.SUCCESS) {
      return moveHandler.handleSuccessfulMove(moveResult, premove, currentTurn, messageConfig);
    }
    premoves.remove(currentTurn);
    return false;
  }

  /** Handles a draw control interaction. */
  public void handleDrawClick(UUID playerId) {
    drawHandler.handleDrawClick(playerId);
  }

  /** Cancels all queued premoves for the player and refreshes their board. */
  public void handleCancelPremoves(UUID playerId) {
    cancelPremoves(playerId);
    view.refreshViewer(playerId);
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

  /** @return the head of the player's queued premoves, or {@code null} when none are queued */
  public Move getPremove(UUID playerId) {
    Deque<Move> queue = premoves.get(playerId);
    return queue == null ? null : queue.peekFirst();
  }

  /** @return an unmodifiable view of the player's full premove queue */
  public List<Move> getPremoves(UUID playerId) {
    Deque<Move> queue = premoves.get(playerId);
    return queue == null ? List.of() : List.copyOf(queue);
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

  /**
   * Simulates applying the full premove chain on a copy of the board and returns the resulting
   * piece positions. The real board is never mutated.
   */
  public static Map<Square, Piece> simulatePremoves(ChessBoard board, List<Move> queue) {
    Map<Square, Piece> sim = new HashMap<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        Square sq = new Square((char) ('1' + r), (char) ('a' + c));
        Piece p = board.getPiece(sq);
        if (p != null) {
          sim.put(sq, p);
        }
      }
    }
    for (Move m : queue) {
      Piece p = sim.remove(m.from());
      if (p != null) {
        sim.put(m.to(), p);
      }
    }
    return sim;
  }

  /**
   * Computes pseudo-legal destination squares for a piece at {@code from} based on the
   * simulated board after the premove chain. Own pieces block; opponent pieces are
   * capturable; no check or en-passant validation. Castling is offered on rights alone,
   * looked up against the simulated position so a rook already premoved away stops offering
   * it.
   */
  public static List<Square> pseudoLegalMoves(
      ChessGame game,
      boolean verticalCastling,
      Map<Square, Piece> sim,
      Square from,
      PieceColor color) {
    Piece piece = sim.get(from);
    if (piece == null) {
      return List.of();
    }
    List<Square> moves = new ArrayList<>();
    switch (piece.type()) {
      case KNIGHT -> {
        int[][] offsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] o : offsets) {
          Square d = SquareUtils.offsetOrNull(from, o[0], o[1]);
          if (d != null) {
            Piece occ = sim.get(d);
            if (occ == null || occ.color() != color) moves.add(d);
          }
        }
      }
      case BISHOP -> addSimSliding(sim, from, color, moves, true, false);
      case ROOK -> addSimSliding(sim, from, color, moves, false, true);
      case QUEEN -> addSimSliding(sim, from, color, moves, true, true);
      case KING -> {
        int[][] offsets = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] o : offsets) {
          Square d = SquareUtils.offsetOrNull(from, o[0], o[1]);
          if (d != null) {
            Piece occ = sim.get(d);
            if (occ == null || occ.color() != color) moves.add(d);
          }
        }
        PremoveMoveCalculator.addCastlingMoves(
            game, verticalCastling, from, color, sim::get, moves);
      }
      case PAWN -> {
        int dir = color == PieceColor.WHITE ? 1 : -1;
        int startRow = color == PieceColor.WHITE ? 1 : 6;
        int promRow = color == PieceColor.WHITE ? 7 : 0;
        int r = from.getRowIndex();
        int c = from.getColumnIndex();
        // Forward one — only own pieces block (opponent pieces may move away before premove fires)
        Square fwd1 = SquareUtils.offsetOrNull(from, dir, 0);
        if (fwd1 != null) {
          Piece fwd1Piece = sim.get(fwd1);
          if ((fwd1Piece == null || fwd1Piece.color() != color) && fwd1.getRowIndex() != promRow) {
            moves.add(fwd1);
            // Forward two from start
            if (r == startRow) {
              Square fwd2 = SquareUtils.offsetOrNull(from, dir * 2, 0);
              if (fwd2 != null) {
                Piece fwd2Piece = sim.get(fwd2);
                if (fwd2Piece == null || fwd2Piece.color() != color) moves.add(fwd2);
              }
            }
          }
        }
        // Diagonals (always allowed for premove — opponent might move a piece there)
        for (int dc : new int[]{-1, 1}) {
          Square diag = SquareUtils.offsetOrNull(from, dir, dc);
          if (diag != null && diag.getRowIndex() != promRow) {
            moves.add(diag);
          }
        }
      }
    }
    return moves;
  }

  private static void addSimSliding(
      Map<Square, Piece> sim, Square from, PieceColor color,
      List<Square> moves, boolean diagonal, boolean straight) {
    int[][] dirs = new int[0][];
    if (diagonal && straight) {
      dirs = new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}};
    } else if (diagonal) {
      dirs = new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}};
    } else {
      dirs = new int[][]{{-1,0},{1,0},{0,-1},{0,1}};
    }
    for (int[] d : dirs) {
      for (int i = 1; i < 8; i++) {
        Square sq = SquareUtils.offsetOrNull(from, d[0] * i, d[1] * i);
        if (sq == null) break;
        Piece occ = sim.get(sq);
        if (occ == null) {
          moves.add(sq);
        } else {
          if (occ.color() != color) moves.add(sq);
          break;
        }
      }
    }
  }
}
