package codes.castled.chess.ui;

import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.game.PremoveMoveCalculator;
import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.Piece;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
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

import javax.annotation.Nullable;

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
  private final DialogLabels labels;
  private final ClickCallback.Options clickOptions;

  public PaperBoardDialog(
      DialogSettings settings,
      PieceGlyph glyph,
      MoveCalculator moveCalculator,
      GameStatusEvaluator status,
      DialogLabels labels,
      ClickCallback.Options clickOptions) {
    this.settings = settings;
    this.glyph = glyph;
    this.moveCalculator = moveCalculator;
    this.status = status;
    this.labels = labels;
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
   * @param premove the viewer's queued premoves, or empty
   * @return the dialog ready for {@code player.showDialog}
   */
  public Dialog build(
      ChessGame chessGame,
      UUID viewerId,
      DialogViewerRegistry.ViewerState state,
      long sequence,
      BoardClicks clicks,
      List<Move> premoves) {
    ChessBoard board = chessGame.getChessBoard();
    UUID whiteId = chessGame.getWhitePlayerId();
    UUID blackId = chessGame.getBlackPlayerId();

    PieceColor viewerColor = state.isSpectator() ? null : chessGame.getColor(viewerId);
    BoardOrientation orientation = orientationFor(viewerColor, state.isFlipped());

    Square selected = state.isSpectator() ? null : chessGame.getSelectedPieceSquare(viewerId);
    Square ghostSel = state.isSpectator() ? null : state.ghostSelected();
    boolean viewerOnTurn = !state.isSpectator() && chessGame.getCurrentTurn().equals(viewerId);
    List<Square> legal;
    if (ghostSel != null && settings.showLegalMoves()) {
      // Ghost square selected — compute moves from the simulated premove position
      Map<Square, Piece> sim = ChessGameHolder.simulatePremoves(board, premoves);
      Piece ghostPiece = sim.get(ghostSel);
      legal = ghostPiece != null
          ? ChessGameHolder.pseudoLegalMoves(sim, ghostSel, ghostPiece.color())
          : List.of();
      selected = ghostSel;
    } else if (selected != null && settings.showLegalMoves() && !state.isSpectator()) {
      if (viewerOnTurn) {
        legal = moveCalculator.getPossibleMoves(chessGame, selected);
      } else {
        legal = PremoveMoveCalculator.getPremoveMoves(chessGame, selected);
      }
    } else {
      legal = List.of();
    }
    Move lastMove = settings.showLastMove() ? board.getLastPlayedMove() : null;

    List<DialogBody> body = buildBody(chessGame, state, whiteId, blackId);
    body.add(statusLine(chessGame, state, viewerId, whiteId, blackId, clicks, premoves));
    // No side controls are appended to the action grid: the client lays out a multi-action grid
    // with one width per column (taken from the widest button in that column), so a 120px control
    // in the 8-column grid would widen the columns it shares with the 20px board cells and blow
    // the uniform board apart. Resign/Draw/Flip are clickable body text instead.
    List<ActionButton> buttons =
        buildButtons(
            chessGame, viewerId, sequence, clicks, orientation, board, selected, legal, lastMove, premoves);

    ActionButton close =
        ActionButton.create(
            mini(labels.getDialogLabel("control-close")),
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
      ChessGame chessGame, DialogViewerRegistry.ViewerState state, UUID whiteId, UUID blackId) {
    // Mutable: build() appends the clock/control status line to the returned list.
    return bodyContent(
            chessGame,
            state,
            whiteId,
            blackId,
            nameOf(whiteId, "White"),
            nameOf(blackId, "Black"))
        .stream()
        .map(PaperBoardDialog::toBody)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  /**
   * Computes the dialog's body content as plain text components — one component per body line,
   * in the exact order the dialog renders them. Kept component-level (rather than building
   * {@link DialogBody} directly) so tests can measure how much content the dialog accumulates
   * without a running Paper server.
   */
  List<Component> bodyContent(
      ChessGame chessGame,
      DialogViewerRegistry.ViewerState state,
      UUID whiteId,
      UUID blackId,
      String whiteName,
      String blackName) {
    List<Component> body = new ArrayList<>();

    if (!state.isFocused()) {
      body.add(mini(labels.getDialogLabel("players", Map.of("[white]", whiteName, "[black]", blackName))));
    }

    if (state.isSpectator() && !state.isFocused()) {
      body.add(mini(labels.getDialogLabel("spectating")));
    }

    if (settings.showCapturedPieces() && !state.isFocused()) {
      body.add(
          mini(
              labels.getDialogLabel(
                  "captured-white",
                  Map.of("[pieces]", capturedGlyphs(chessGame.getCapturedPieces(whiteId))))));
      body.add(
          mini(
              labels.getDialogLabel(
                  "captured-black",
                  Map.of("[pieces]", capturedGlyphs(chessGame.getCapturedPieces(blackId))))));
    }

    // Info / draw / surrender messages come from MessageConfig (legacy '§' codes), not the
    // MiniMessage dialog labels, so they must be parsed with the legacy serializer.
    if (!state.info().isEmpty()) {
      body.add(LEGACY.deserialize(state.info()));
    }

    if (!state.isSpectator()) {
      String drawMessage = drawStatusText(state);
      if (!drawMessage.isEmpty()) {
        body.add(LEGACY.deserialize(drawMessage));
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
      Move lastMove,
      List<Move> premoves) {
    UUID whiteId = chessGame.getWhitePlayerId();
    UUID blackId = chessGame.getBlackPlayerId();
    Square whiteCheckKing = checkedKingSquare(chessGame, whiteId, board);
    Square blackCheckKing = checkedKingSquare(chessGame, blackId, board);

    // Collect all premove source/destination squares and ghost pieces across the full queue.
    // Intermediate squares (destination of one premove, source of the next) keep the blue
    // highlight but no ghost — only leaf destinations show the ghost piece.
    Set<Square> premoveFroms = new HashSet<>();
    Set<Square> premoveTos = new HashSet<>();
    for (Move m : premoves) {
      premoveFroms.add(m.from());
      premoveTos.add(m.to());
    }
    // Show ghost pieces on ALL premove destinations from the simulated position.
    // Intermediate squares (both source and destination) show the piece that arrives there.
    Map<Square, Piece> sim = ChessGameHolder.simulatePremoves(board, premoves);
    Map<Square, Piece> ghosts = new HashMap<>();
    for (Move m : premoves) {
      Piece simPiece = sim.get(m.to());
      if (simPiece != null) {
        ghosts.putIfAbsent(m.to(), simPiece);
      }
    }

    List<ActionButton> buttons = new ArrayList<>(BoardOrientation.SIZE * BoardOrientation.SIZE);
    for (int row = 0; row < BoardOrientation.SIZE; row++) {
      for (int col = 0; col < BoardOrientation.SIZE; col++) {
        Square square = orientation.toSquare(row, col);
        Piece piece = board.getPiece(square);
        boolean isPremoveSquare = premoveFroms.contains(square) || premoveTos.contains(square);

        PieceGlyph.Highlight highlight = PieceGlyph.Highlight.NONE;
        boolean inCheck = square.equals(whiteCheckKing) || square.equals(blackCheckKing);
        if (inCheck) {
          highlight = PieceGlyph.Highlight.CHECK;
        } else if (square.equals(selected)) {
          highlight = PieceGlyph.Highlight.SELECTED;
        } else if (legal.contains(square)) {
          highlight = PieceGlyph.Highlight.LEGAL;
        } else if (isPremoveSquare) {
          highlight = PieceGlyph.Highlight.PREMOVE;
        } else if (isLastMoveSquare(lastMove, square)) {
          highlight = PieceGlyph.Highlight.LAST_MOVE;
        }

        boolean light = ((square.getColumnIndex() + square.getRowIndex()) % 2) == 1;
        // Premove leaving squares show an empty blue tile; destination squares show a ghost.
        Piece glyphPiece;
        if (premoveFroms.contains(square)) {
          glyphPiece = null;
        } else if (ghosts.containsKey(square)) {
          glyphPiece = ghosts.get(square);
        } else {
          glyphPiece = piece;
        }
        String text =
            glyphPiece != null ? glyph.forPiece(glyphPiece, highlight, light) : glyph.forEmpty(light, highlight);

        Component tooltip =
            tooltip(
                square,
                piece,
                legal,
                lastMove,
                isPremoveSquare,
                inCheck);

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
      BoardClicks clicks,
      List<Move> premoves) {
    List<Component> pieces = new ArrayList<>(2);
    if (settings.showClock()) {
      pieces.add(
          mini(
              labels.getDialogLabel(
                  "clock",
                  Map.of(
                      "[white]", formatMillis(chessGame.getTimeLeftMillis(whiteId)),
                      "[black]", formatMillis(chessGame.getTimeLeftMillis(blackId))))));
    }
    pieces.add(controlsComponent(viewerId, state, clicks, premoves));

    Component line = pieces.get(0);
    for (int i = 1; i < pieces.size(); i++) {
      line = line.append(Component.newline()).append(pieces.get(i));
    }
    return DialogBody.plainMessage(line, 200);
  }

  /** Resign/Draw/Flip/Focus as one clickable line so the 8x8 action grid stays uniform. */
  private Component controlsComponent(
      UUID viewerId, DialogViewerRegistry.ViewerState state, BoardClicks clicks, List<Move> premoves) {
    List<Component> items = new ArrayList<>(4);
    if (!state.isSpectator()) {
      if (!premoves.isEmpty()) {
        items.add(
            controlText(
                labels.getDialogLabel("control-cancel-premoves"), b -> clicks.onCancelPremoves(viewerId)));
      } else {
        items.add(
            controlText(
                labels.getDialogLabel("control-resign"), b -> clicks.onResign(viewerId)));
      }
      items.add(
          controlText(
              labels.getDialogLabel(
                  state.drawState() == DrawItemType.ACCEPT ? "control-draw-accept" : "control-draw"),
              b -> clicks.onDraw(viewerId)));
    }
    items.add(controlText(labels.getDialogLabel("control-flip"), b -> clicks.onFlip(viewerId)));
    items.add(controlText(
        labels.getDialogLabel(state.isFocused() ? "control-unfocus" : "control-focus"),
        b -> clicks.onFocus(viewerId)));

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
      Square square, Piece piece, List<Square> legal, Move lastMove, boolean premove, boolean inCheck) {
    List<String> lines = new ArrayList<>();
    if (settings.showCoordinates()) {
      lines.add(labels.getDialogLabel("tooltip-coord", Map.of("[coord]", SquareNotation.toAlgebraic(square))));
    }
    if (piece != null) {
      lines.add(
          labels.getDialogLabel(
              "tooltip-piece",
              Map.of(
                  "[color]", labels.getDialogLabel(piece.color() == PieceColor.WHITE ? "white" : "black"),
                  "[piece]", pieceName(piece))));
    }
    if (legal.contains(square)) {
      lines.add(labels.getDialogLabel(piece != null ? "tooltip-capture" : "tooltip-legal"));
    }
    if (lastMove != null && (square.equals(lastMove.from()) || square.equals(lastMove.to()))) {
      lines.add(labels.getDialogLabel("tooltip-last-move"));
    }
    if (premove) {
      lines.add(labels.getDialogLabel("tooltip-premove"));
    }
    if (inCheck) {
      lines.add(labels.getDialogLabel("tooltip-check"));
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

  private static DialogBody toBody(Component component) {
    return DialogBody.plainMessage(component);
  }

  private static Component mini(String value) {
    return MINI.deserialize(value);
  }

  private String capturedGlyphs(List<Piece> pieces) {
    if (pieces == null || pieces.isEmpty()) {
      return labels.getDialogLabel("captured-none");
    }
    StringBuilder builder = new StringBuilder();
    for (Piece piece : pieces) {
      builder.append(glyph.forPlainPiece(piece));
    }
    return builder.toString();
  }

  private String pieceName(Piece piece) {
    return labels.getDialogLabel("piece-" + piece.type().name().toLowerCase());
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
