package codes.castled.chess.ui;

import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.game.GameStatusEvaluator;
import com.dxzell.pocketchess.api.board.ChessBoard;
import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.move.Move;
import com.dxzell.pocketchess.api.move.MoveCalculator;
import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the native Paper {@link Dialog} that renders the chess board for a single viewer.
 *
 * <p>Stateless: every call reads exclusively from the authoritative {@link ChessGame} model
 * plus the viewer's presentation state, so both players and spectators see the same position.
 * It produces 64 real, individually clickable board buttons in an 8-column {@code multiAction}
 * grid; side controls are appended after the grid and the close button is the dialog's exit
 * action. Every clickable button carries an in-process callback that captures the target and
 * the render sequence, so a click from a board rendered before the opponent moved is rejected.
 */
public final class PaperBoardDialog {

  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private final DialogSettings settings;
  private final PieceGlyph glyph;
  private final MoveCalculator moveCalculator;
  private final GameStatusEvaluator status;
  private final MessageConfig messages;
  private final ClickCallback.Options clickOptions;

  public PaperBoardDialog(
      DialogSettings settings,
      PieceGlyph glyph,
      MoveCalculator moveCalculator,
      GameStatusEvaluator status,
      MessageConfig messages,
      ClickCallback.Options clickOptions) {
    this.settings = settings;
    this.glyph = glyph;
    this.moveCalculator = moveCalculator;
    this.status = status;
    this.messages = messages;
    this.clickOptions = clickOptions;
  }

  /**
   * Builds the board dialog for a viewer.
   *
   * @param chessGame the authoritative game
   * @param viewerId the viewer
   * @param state the viewer's presentation state
   * @param sequence the render sequence embedded in every clickable action
   * @param clicks the sink that receives button clicks
   * @return the dialog ready for {@code player.showDialog}
   */
  public Dialog build(
      ChessGame chessGame,
      UUID viewerId,
      DialogViewerRegistry.ViewerState state,
      long sequence,
      BoardClicks clicks) {
    ChessBoard board = chessGame.getChessBoard();
    UUID whiteId = chessGame.getWhitePlayerId();
    UUID blackId = chessGame.getBlackPlayerId();

    PieceColor viewerColor = state.isSpectator() ? null : chessGame.getColor(viewerId);
    BoardOrientation orientation = orientationFor(viewerColor, state.isFlipped());

    Square selected = state.isSpectator() ? null : chessGame.getSelectedPieceSquare(viewerId);
    List<Square> legal =
        (selected != null && settings.showLegalMoves())
            ? moveCalculator.getPossibleMoves(chessGame, selected)
            : List.of();
    Move lastMove = settings.showLastMove() ? board.getLastPlayedMove() : null;

    List<DialogBody> body = buildBody(chessGame, state, viewerColor, whiteId, blackId);
    body.add(statusLine(chessGame, state, viewerId, whiteId, blackId, clicks));
    // No side controls are appended to the action grid: the client lays out a multi-action grid
    // with one width per column (taken from the widest button in that column), so a 120px control
    // in the 8-column grid would widen the columns it shares with the 20px board cells and blow
    // the uniform board apart. Resign/Draw/Flip are clickable body text instead.
    List<ActionButton> buttons =
        buildButtons(chessGame, viewerId, sequence, clicks, orientation, board, selected, legal, lastMove);

    ActionButton close =
        ActionButton.create(
            mini(messages.getDialogLabel("control-close")),
            null,
            120,
            DialogAction.customClick(
                (response, audience) -> clicks.onClose(viewerId, audience), clickOptions));

    DialogBase base =
        DialogBase.builder(mini(settings.title()))
            .canCloseWithEscape(settings.allowEscapeClose())
            // Non-pausing: a paused dialog would require an after-action that unpauses, which is
            // incompatible with NONE. The board is a live view, so it must not pause the game.
            .pause(false)
            // NONE keeps the dialog open after a click; we re-show the updated board ourselves.
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .body(body)
            .build();

    DialogType type =
        DialogType.multiAction(buttons).columns(BoardOrientation.SIZE).exitAction(close).build();

    return Dialog.create(
        factory -> factory.empty().base(base).type(type));
  }

  private BoardOrientation orientationFor(PieceColor viewerColor, boolean flipped) {
    BoardOrientation base =
        settings.orientationFollowsPlayer()
            ? (viewerColor == PieceColor.BLACK ? BoardOrientation.BLACK : BoardOrientation.WHITE)
            : BoardOrientation.WHITE;
    if (!flipped) {
      return base;
    }
    return base == BoardOrientation.WHITE ? BoardOrientation.BLACK : BoardOrientation.WHITE;
  }

  /* Body -------------------------------------------------------------- */

  private List<DialogBody> buildBody(
      ChessGame chessGame,
      DialogViewerRegistry.ViewerState state,
      PieceColor viewerColor,
      UUID whiteId,
      UUID blackId) {
    String whiteName = nameOf(whiteId, "White");
    String blackName = nameOf(blackId, "Black");
    UUID turn = chessGame.getCurrentTurn();
    boolean whiteTurn = turn.equals(whiteId);

    List<DialogBody> body = new ArrayList<>();
    body.add(line(messages.getDialogLabel("players", Map.of("[white]", whiteName, "[black]", blackName))));

    if (state.isSpectator()) {
      body.add(line(messages.getDialogLabel("spectating")));
    }

    if (status.isInCheck(chessGame, turn)) {
      body.add(line(messages.getDialogLabel("in-check", Map.of("[player]", whiteTurn ? whiteName : blackName))));
    }

    if (settings.showCapturedPieces()) {
      body.add(
          line(
              messages.getDialogLabel(
                  "captured-white",
                  Map.of("[pieces]", capturedGlyphs(chessGame.getCapturedPieces(whiteId))))));
      body.add(
          line(
              messages.getDialogLabel(
                  "captured-black",
                  Map.of("[pieces]", capturedGlyphs(chessGame.getCapturedPieces(blackId))))));
    }

    // Info / draw / surrender messages come from MessageConfig (legacy '§' codes), not the
    // MiniMessage dialog labels, so they must be parsed with the legacy serializer.
    if (!state.info().isEmpty()) {
      body.add(legacyLine(state.info()));
    }

    if (!state.isSpectator()) {
      String drawMessage = drawStatusText(state);
      if (!drawMessage.isEmpty()) {
        body.add(legacyLine(drawMessage));
      }
    }

    return body;
  }

  private String drawStatusText(DialogViewerRegistry.ViewerState state) {
    if (!state.surrenderMessage().isBlank()) {
      return state.surrenderMessage();
    }
    if (state.drawState() != DrawItemType.NONE && !state.drawMessage().isBlank()) {
      return state.drawMessage();
    }
    return "";
  }

  /* Board buttons ----------------------------------------------------- */

  private List<ActionButton> buildButtons(
      ChessGame chessGame,
      UUID viewerId,
      long sequence,
      BoardClicks clicks,
      BoardOrientation orientation,
      ChessBoard board,
      Square selected,
      List<Square> legal,
      Move lastMove) {
    UUID whiteId = chessGame.getWhitePlayerId();
    UUID blackId = chessGame.getBlackPlayerId();
    Square whiteCheckKing = checkedKingSquare(chessGame, whiteId, board);
    Square blackCheckKing = checkedKingSquare(chessGame, blackId, board);

    List<ActionButton> buttons = new ArrayList<>(BoardOrientation.SIZE * BoardOrientation.SIZE);
    for (int row = 0; row < BoardOrientation.SIZE; row++) {
      for (int col = 0; col < BoardOrientation.SIZE; col++) {
        Square square = orientation.toSquare(row, col);
        Piece piece = board.getPiece(square);

        PieceGlyph.Highlight highlight = PieceGlyph.Highlight.NONE;
        if (square.equals(selected)) {
          highlight = PieceGlyph.Highlight.SELECTED;
        } else if (legal.contains(square)) {
          highlight = PieceGlyph.Highlight.LEGAL;
        } else if (isLastMoveSquare(lastMove, square)) {
          highlight = PieceGlyph.Highlight.LAST_MOVE;
        }

        boolean light = ((square.getColumnIndex() + square.getRowIndex()) % 2) == 1;
        String text =
            piece != null ? glyph.forPiece(piece, highlight, light) : glyph.forEmpty(light, highlight);

        Component tooltip =
            tooltip(
                square,
                piece,
                legal,
                lastMove,
                square.equals(whiteCheckKing) || square.equals(blackCheckKing));

        Square target = square;
        buttons.add(
            ActionButton.create(
                mini(text),
                tooltip,
                settings.squareButtonWidth(),
                DialogAction.customClick(
                    (response, audience) -> clicks.onCell(viewerId, target, sequence), clickOptions)));
      }
    }
    return buttons;
  }

  /* Bottom status line (clock + side controls, one body element) -------------------- */

  /**
   * Renders the clock and the Resign/Draw/Flip controls joined by a single line break into one
   * body element. Keeping them in one element removes the dialog's per-body-element spacing, so
   * the clock sits directly above the controls with no gap.
   */
  private DialogBody statusLine(
      ChessGame chessGame,
      DialogViewerRegistry.ViewerState state,
      UUID viewerId,
      UUID whiteId,
      UUID blackId,
      BoardClicks clicks) {
    List<Component> pieces = new ArrayList<>(2);
    if (settings.showClock()) {
      pieces.add(
          mini(
              messages.getDialogLabel(
                  "clock",
                  Map.of(
                      "[white]", formatMillis(chessGame.getTimeLeftMillis(whiteId)),
                      "[black]", formatMillis(chessGame.getTimeLeftMillis(blackId))))));
    }
    pieces.add(controlsComponent(viewerId, state, clicks));

    Component line = pieces.get(0);
    for (int i = 1; i < pieces.size(); i++) {
      line = line.append(Component.newline()).append(pieces.get(i));
    }
    return DialogBody.plainMessage(line, 200);
  }

  /** Resign/Draw/Flip as one clickable line so the 8x8 action grid stays uniform. */
  private Component controlsComponent(
      UUID viewerId, DialogViewerRegistry.ViewerState state, BoardClicks clicks) {
    List<Component> items = new ArrayList<>(3);
    if (!state.isSpectator()) {
      items.add(
          controlText(
              messages.getDialogLabel("control-resign"), b -> clicks.onResign(viewerId)));
      items.add(
          controlText(
              messages.getDialogLabel(
                  state.drawState() == DrawItemType.ACCEPT ? "control-draw-accept" : "control-draw"),
              b -> clicks.onDraw(viewerId)));
    }
    items.add(controlText(messages.getDialogLabel("control-flip"), b -> clicks.onFlip(viewerId)));

    Component line = items.get(0);
    for (int i = 1; i < items.size(); i++) {
      line = line.append(Component.text("   ")).append(items.get(i));
    }
    return line;
  }

  private Component controlText(String label, java.util.function.Consumer<Void> onClick) {
    return mini(label).clickEvent(ClickEvent.callback(audience -> onClick.accept(null), clickOptions));
  }

  private static boolean isLastMoveSquare(Move lastMove, Square square) {
    return lastMove != null && (square.equals(lastMove.from()) || square.equals(lastMove.to()));
  }

  private Square checkedKingSquare(ChessGame chessGame, UUID playerId, ChessBoard board) {
    if (!status.isInCheck(chessGame, playerId)) {
      return null;
    }
    PieceColor color = chessGame.getColor(playerId);
    List<Square> kings = board.getColoredPieces(color, PieceType.KING);
    return kings.isEmpty() ? null : kings.get(0);
  }

  private Component tooltip(
      Square square, Piece piece, List<Square> legal, Move lastMove, boolean inCheck) {
    List<String> lines = new ArrayList<>();
    if (settings.showCoordinates()) {
      lines.add(messages.getDialogLabel("tooltip-coord", Map.of("[coord]", SquareNotation.toAlgebraic(square))));
    }
    if (piece != null) {
      lines.add(
          messages.getDialogLabel(
              "tooltip-piece",
              Map.of(
                  "[color]", messages.getDialogLabel(piece.color() == PieceColor.WHITE ? "white" : "black"),
                  "[piece]", pieceName(piece))));
    }
    if (legal.contains(square)) {
      lines.add(messages.getDialogLabel(piece != null ? "tooltip-capture" : "tooltip-legal"));
    }
    if (lastMove != null && (square.equals(lastMove.from()) || square.equals(lastMove.to()))) {
      lines.add(messages.getDialogLabel("tooltip-last-move"));
    }
    if (inCheck) {
      lines.add(messages.getDialogLabel("tooltip-check"));
    }
    if (lines.isEmpty()) {
      return null;
    }
    Component tip = null;
    for (String l : lines) {
      Component c = mini(l);
      tip = tip == null ? c : tip.append(Component.newline()).append(c);
    }
    return tip;
  }

  /* Helpers ----------------------------------------------------------- */

  private DialogBody line(String mini) {
    return DialogBody.plainMessage(mini(mini));
  }

  /** For MessageConfig-sourced text carrying legacy '§' colour codes (info/draw/surrender). */
  private DialogBody legacyLine(String legacy) {
    return DialogBody.plainMessage(LEGACY.deserialize(legacy));
  }

  private static Component mini(String value) {
    return MINI.deserialize(value);
  }

  private String capturedGlyphs(List<Piece> pieces) {
    if (pieces == null || pieces.isEmpty()) {
      return messages.getDialogLabel("captured-none");
    }
    StringBuilder builder = new StringBuilder();
    for (Piece piece : pieces) {
      builder.append(glyph.forPlainPiece(piece));
    }
    return builder.toString();
  }

  private String pieceName(Piece piece) {
    return messages.getDialogLabel("piece-" + piece.type().name().toLowerCase());
  }

  private static String nameOf(UUID playerId, String fallback) {
    String name = Bukkit.getOfflinePlayer(playerId).getName();
    return name != null ? name : fallback;
  }

  private static String formatMillis(long millis) {
    if (millis < 0) {
      millis = 0;
    }
    long totalSeconds = millis / 1000;
    return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
  }
}
