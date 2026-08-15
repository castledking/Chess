package codes.castled.chess.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.game.GameStatusEvaluator;
import codes.castled.chess.wiring.EngineFactory;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.GameCreationResult;
import codes.castled.chess.engine.api.game.GameCreationResultType;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives a real engine game through every phase the board can be in and reports how much body
 * content the dialog accumulates, then enforces a line budget.
 *
 * <p>The dialog has no scrollbar — re-rendering it snaps the viewer back to the top — so every
 * added body line pushes the board further out of view. Body content is therefore treated as a
 * sacred, budgeted resource: this test exists so transient status text (an "in check!" banner,
 * "it is not your turn", etc.) can never silently creep back in. It prints the measured growth
 * so the budget can be reviewed against the actual dialog.
 */
class DialogContentGrowthTest {

  private static final UUID WHITE = UUID.nameUUIDFromBytes("white".getBytes(StandardCharsets.UTF_8));
  private static final UUID BLACK = UUID.nameUUIDFromBytes("black".getBytes(StandardCharsets.UTF_8));
  private static final UUID SPECTATOR = UUID.nameUUIDFromBytes("spectator".getBytes(StandardCharsets.UTF_8));

  /** The board body always shows the players line plus the two captured-piece lines. */
  private static final int BOARD_BASELINE_LINES = 3;
  /**
   * The baseline plus at most ONE transient status line (info / draw / resign). Tighten this to
   * {@link #BOARD_BASELINE_LINES} to forbid every transient line once the info/draw/resign text
   * is removed from the dialog.
   */
  private static final int BOARD_MAX_LINES = BOARD_BASELINE_LINES + 1;
  /** The promotion dialog never carries more than its single prompt line. */
  private static final int PROMOTION_LINES = 1;

  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private final EngineFactory engine = new EngineFactory(false);
  private final GameStatusEvaluator status = new GameStatusEvaluator(engine.moveCalculator());
  private final DialogSettings settings = defaultSettings();
  private final PaperBoardDialog board =
      new PaperBoardDialog(
          settings, new PieceGlyph(false), engine.moveCalculator(), status, fakeLabels(), clickOptions());
  private final PaperPromotionDialog promotion =
      new PaperPromotionDialog(settings, new PieceGlyph(false), fakeLabels(), clickOptions());

  @Test
  void boardDialogStaysWithinSacredBudgetAcrossEveryPhase() {
    List<Measure> report = new ArrayList<>();

    // ---- Board phases driven by a real engine game -------------------
    ChessGame game = newGame();
    DialogViewerRegistry registry = new DialogViewerRegistry();
    DialogViewerRegistry.ViewerState white = registry.addPlayer(WHITE);

    // Phase: brand-new game.
    report.add(measure("game start", game, white));

    // 1. e4 e5 2. Qh5 Nc6 3. Qxf7+ — leaves the black king in check.
    move(game, WHITE, '2', 'E', '4', 'E');
    move(game, BLACK, '7', 'E', '5', 'E');
    move(game, WHITE, '1', 'D', '5', 'H');
    move(game, BLACK, '8', 'B', '6', 'C');
    move(game, WHITE, '5', 'H', '7', 'F');
    assertTrue(status.isInCheck(game, BLACK), "Qxf7+ must leave black in check");

    // Phase: opponent in check — must NOT grow the body with a banner line.
    report.add(measure("opponent in check", game, white));

    // 3... Kxf7 — black recaptures the queen; both captured lists grow.
    move(game, BLACK, '8', 'E', '7', 'F');
    assertFalse(game.getCapturedPieces(WHITE).isEmpty(), "white must have captured the f-pawn");
    assertFalse(game.getCapturedPieces(BLACK).isEmpty(), "black must have captured the white queen");

    // Phase: mid-game after captures on both sides.
    report.add(measure("after captures", game, white));

    // Free the players, then open a single clean game for the per-viewer status phases below
    // (the service tracks one active game per player, so the phases must share one game).
    game.endGame(null);
    ChessGame statusGame = newGame();

    // ---- Per-viewer status phases ------------------------------------
    report.add(statePhase("spectator", statusGame, spectatorState()));
    report.add(
        statePhase(
            "draw offer sent",
            statusGame,
            drawState(DrawItemType.SENT, "&7You have sent a draw offer to your opponent.")));
    report.add(
        statePhase(
            "draw offer received",
            statusGame,
            drawState(
                DrawItemType.ACCEPT,
                "&7Your opponent has offered a draw. &6Click Draw &7to &aaccept&7.")));
    report.add(
        statePhase(
            "draw confirm",
            statusGame,
            drawState(DrawItemType.CONFIRM, "&6Click Draw again &7to &aconfirm &7the draw.")));
    report.add(statePhase("resign confirm", statusGame, resignState()));
    report.add(
        statePhase(
            "transient info line",
            statusGame,
            infoState("&cYour selected piece cannot move to that square.")));

    // ---- Promotion phase (a separate, smaller dialog) ----------------
    List<String> promoLines = plainLines(promotion.bodyContent());
    report.add(
        new Measure(
            "promotion dialog",
            promoLines.size(),
            promoLines.stream().mapToInt(String::length).sum()));

    printReport(report);

    // ---- Budget enforcement -------------------------------------------
    for (Measure m : report) {
      if (m.phase.equals("promotion dialog")) {
        assertEquals(PROMOTION_LINES, m.lines, "promotion dialog must stay a single prompt line");
        continue;
      }
      assertTrue(
          m.lines <= BOARD_MAX_LINES,
          () ->
              m.phase
                  + " grew to "
                  + m.lines
                  + " body lines, exceeding the sacred budget of "
                  + BOARD_MAX_LINES);
      if (isBoardBaselinePhase(m.phase)) {
        assertEquals(
            BOARD_BASELINE_LINES,
            m.lines,
            () -> m.phase + " must not add any transient status line");
      }
    }
  }

  private static boolean isBoardBaselinePhase(String phase) {
    return phase.equals("game start")
        || phase.equals("opponent in check")
        || phase.equals("after captures");
  }

  /* Phase builders --------------------------------------------------- */

  private Measure measure(String name, ChessGame game, DialogViewerRegistry.ViewerState state) {
    List<String> lines = plainLines(board.bodyContent(game, state, WHITE, BLACK, "White", "Black"));
    return new Measure(name, lines.size(), lines.stream().mapToInt(String::length).sum());
  }

  private Measure statePhase(
      String name, ChessGame game, DialogViewerRegistry.ViewerState state) {
    return measure(name, game, state);
  }

  private DialogViewerRegistry.ViewerState spectatorState() {
    return new DialogViewerRegistry().addSpectator(SPECTATOR);
  }

  private DialogViewerRegistry.ViewerState drawState(DrawItemType type, String message) {
    DialogViewerRegistry registry = new DialogViewerRegistry();
    DialogViewerRegistry.ViewerState state = registry.addPlayer(WHITE);
    registry.setDraw(WHITE, type, message);
    return state;
  }

  private DialogViewerRegistry.ViewerState resignState() {
    DialogViewerRegistry registry = new DialogViewerRegistry();
    DialogViewerRegistry.ViewerState state = registry.addPlayer(WHITE);
    registry.setSurrender(WHITE, true, "&6Click Resign again &7to &aconfirm&7.");
    return state;
  }

  private DialogViewerRegistry.ViewerState infoState(String message) {
    DialogViewerRegistry registry = new DialogViewerRegistry();
    DialogViewerRegistry.ViewerState state = registry.addPlayer(WHITE);
    registry.setInfo(WHITE, message);
    return state;
  }

  /* Engine helpers --------------------------------------------------- */

  private ChessGame newGame() {
    GameCreationResult result = engine.chessGameService().createGame(WHITE, BLACK, TimeMode.TEN);
    assertEquals(GameCreationResultType.SUCCESS, result.type());
    return result.game();
  }

  private static void move(
      ChessGame game, UUID player, char fromRow, char fromCol, char toRow, char toCol) {
    game.selectPiece(new Square(fromRow, fromCol), player);
    MoveResult result = game.makeMove(new Square(toRow, toCol), player);
    assertEquals(
        MoveResultType.SUCCESS,
        result.type(),
        () -> "move " + fromRow + fromCol + "-" + toRow + toCol + " must be legal");
    game.toggleTurn();
  }

  /* Report ----------------------------------------------------------- */

  private static void printReport(List<Measure> report) {
    StringBuilder out = new StringBuilder();
    out.append('\n');
    out.append("Dialog body growth across every game phase\n");
    out.append("===========================================\n");
    out.append("Body lines are counted ABOVE the board. Every board dialog additionally shows a\n");
    out.append("constant clock + Resign/Draw/Flip line and the 64 board buttons. Each body line\n");
    out.append("adds one row of dialog height; the dialog has no scrollbar, so height is sacred.\n");
    out.append('\n');
    String header = String.format("%-26s %6s %10s%n", "phase", "lines", "text chars");
    out.append(header);
    out.append("-".repeat(header.length()));
    for (Measure m : report) {
      out.append(String.format("%-26s %6d %10d%n", m.phase, m.lines, m.chars));
    }
    out.append('\n');
    out.append("Budgets: board body <= ")
        .append(BOARD_MAX_LINES)
        .append(" lines (baseline ")
        .append(BOARD_BASELINE_LINES)
        .append("), promotion dialog <= ")
        .append(PROMOTION_LINES)
        .append(" line.\n");
    System.out.print(out);
  }

  private static List<String> plainLines(List<Component> body) {
    return body.stream().map(PLAIN::serialize).toList();
  }

  /* Fixtures --------------------------------------------------------- */

  private static DialogSettings defaultSettings() {
    return new DialogSettings(
        "Chess", // title
        true, // allowEscapeClose
        true, // orientationFollowsPlayer
        false, // showCoordinates
        true, // showLegalMoves
        true, // showLastMove
        true, // showCapturedPieces
        true, // showClock
        20, // clockRefreshTicks
        false, // useGlyphs (stable single-char captured glyphs for the report)
        20, // squareButtonWidth
        "", // clickSound
        "", // moveSound
        ""); // checkSound
  }

  private static ClickCallback.Options clickOptions() {
    return ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(30))
        .build();
  }

  private static DialogLabels fakeLabels() {
    Map<String, String> labels =
        Map.ofEntries(
            Map.entry("players", "<white>[white]</white> <gray>vs</gray> <white>[black]</white>"),
            Map.entry("spectating", "<gray><italic>Spectating (read-only)</italic></gray>"),
            Map.entry("captured-white", "<gray>White captured:</gray> [pieces]"),
            Map.entry("captured-black", "<gray>Black captured:</gray> [pieces]"),
            Map.entry("captured-none", "<dark_gray>—</dark_gray>"),
            Map.entry("promotion-prompt", "<gray>Select the piece to promote your pawn to.</gray>"));
    return new DialogLabels() {
      @Override
      public String getDialogLabel(String key) {
        return labels.getOrDefault(key, "<red>?" + key + "?</red>");
      }

      @Override
      public String getDialogLabel(String key, Map<String, String> placeholders) {
        String value = getDialogLabel(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
          value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
      }
    };
  }

  private record Measure(String phase, int lines, int chars) {}
}