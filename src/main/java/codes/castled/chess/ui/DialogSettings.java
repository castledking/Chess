package codes.castled.chess.ui;

/**
 * Immutable snapshot of the {@code ui.dialog.*} configuration used while rendering
 * the chess dialog.
 *
 * @param title the dialog title (MiniMessage/legacy)
 * @param allowEscapeClose whether pressing ESC closes the view (never resigns)
 * @param orientationFollowsPlayer if true each player sees the board from their own
 *     colour; if false a fixed white orientation is used
 * @param showCoordinates whether to render rank/file coordinate labels
 * @param showLegalMoves whether to highlight legal destinations after selection
 * @param showLastMove whether to highlight the last played move
 * @param showCapturedPieces whether to show captured pieces in the body
 * @param showClock whether to show the clock
 * @param clockRefreshTicks minimum ticks between clock-driven refreshes (>= 20)
 * @param useGlyphs whether to render resource-pack piece glyphs (vs Unicode fallback)
 * @param squareButtonWidth the width applied to each of the 64 board buttons
 * @param clickSound sound key played on a board click, or blank for none
 * @param moveSound sound key played on a successful move, or blank for none
 * @param checkSound sound key played when a move gives check, or blank for none
 */
public record DialogSettings(
    String title,
    boolean allowEscapeClose,
    boolean orientationFollowsPlayer,
    boolean showCoordinates,
    boolean showLegalMoves,
    boolean showLastMove,
    boolean showCapturedPieces,
    boolean showClock,
    int clockRefreshTicks,
    boolean useGlyphs,
    int squareButtonWidth,
    String clickSound,
    String moveSound,
    String checkSound) {}
