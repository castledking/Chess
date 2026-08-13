package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;

/**
 * Maps chess pieces and empty-square states to the glyph string rendered inside a
 * dialog button label.
 *
 * <p>Two rendering modes are supported and chosen per game by configuration:
 *
 * <ul>
 *   <li>{@code glyphs = true}: private-use-area codepoints resolved by the
 *       PocketChess resource pack's bitmap font. Each codepoint points at the very
 *       same {@code minecraft:item/<name>.png} texture the inventory item models
 *       already use, so the real artwork is reused with no new drawing. Highlight
 *       states (selected/legal) are encoded by pre-composited texture variants,
 *       not by text colour.
 *   <li>{@code glyphs = false}: standard Unicode chess symbols (U+2654..U+265F),
 *       which the bundled unifont renders on every client, so there are no tofu
 *       boxes when the pack is absent.
 * </ul>
 *
 * <p>Codepoint scheme (also declared in the pack's {@code pocketchess:board} font):
 * {@code U+E001..U+E00C} are the plain pieces (white P,R,N,B,Q,K then black
 * P,R,N,B,Q,K), used off-board where there is no square behind them. Board squares
 * use composites that bake the checkerboard tile in behind the piece, one plane per
 * (square colour, highlight) pair: {@code base + 0x300/0x400} plain light/dark,
 * {@code + 0x500/0x600} selected, {@code + 0x700/0x800} legal. The empty tiles
 * follow the same six states at {@code U+E010}, {@code U+E011} and
 * {@code U+E014..U+E017}.
 */
public final class PieceGlyph {

  /** Visual state a square can be rendered in. */
  public enum Highlight {
    /** Normal square. */
    NONE,
    /** The viewer's currently selected source square. */
    SELECTED,
    /** A square the selected piece may legally move to (or capture on). */
    LEGAL,
    /**
     * The origin or destination square of the last played move. Shown to every viewer
     * (both players and spectators) so the opponent can see what was just played, the
     * way most chess sites highlight the previous move.
     */
    LAST_MOVE
  }

  private static final char WHITE_PAWN = '';
  private static final char WHITE_ROOK = '';
  private static final char WHITE_KNIGHT = '';
  private static final char WHITE_BISHOP = '';
  private static final char WHITE_QUEEN = '';
  private static final char WHITE_KING = '';
  private static final char BLACK_PAWN = '';
  private static final char BLACK_ROOK = '';
  private static final char BLACK_KNIGHT = '';
  private static final char BLACK_BISHOP = '';
  private static final char BLACK_QUEEN = '';
  private static final char BLACK_KING = '';

  private static final char EMPTY_LIGHT = '';
  private static final char EMPTY_DARK = '';

  // Composite planes: a piece baked onto its square's checkerboard tile so an occupied
  // square shows the light/dark colour behind the piece instead of the bare dialog-button
  // grey. Normal squares add 0x300 (light) / 0x400 (dark); the highlighted planes below
  // are the same tiles with the selected/legal wash baked in between square and piece, so
  // a highlighted square looks like a normal one that is merely tinted.
  private static final int LIGHT_SQUARE_OFFSET = 0x300;
  private static final int DARK_SQUARE_OFFSET = 0x400;
  private static final int LIGHT_SQUARE_SELECTED_OFFSET = 0x500;
  private static final int DARK_SQUARE_SELECTED_OFFSET = 0x600;
  private static final int LIGHT_SQUARE_LEGAL_OFFSET = 0x700;
  private static final int DARK_SQUARE_LEGAL_OFFSET = 0x800;

  // Empty tiles with the highlight wash baked onto the checkerboard, so an empty
  // highlighted square keeps its square colour too.
  private static final char EMPTY_LIGHT_SELECTED = '';
  private static final char EMPTY_DARK_SELECTED = '';
  private static final char EMPTY_LIGHT_LEGAL = '';
  private static final char EMPTY_DARK_LEGAL = '';

  /**
   * MiniMessage font tag wrapping every glyph we emit. Our codepoints live in our own
   * {@code assets/pocketchess/font/board.json} rather than {@code minecraft:default},
   * because that font is shared: ResourcePackManager merges the providers array of every
   * installed pack into it, and the last provider to claim a codepoint wins. Nexo puts
   * its {@code required/} glyphs in the same low private-use range, so U+E008 was
   * resolving to its exit icon instead of the black rook. A private font namespace has no
   * such contention — no other pack declares {@code pocketchess:board}.
   */
  private static final String FONT_OPEN = "<font:pocketchess:board>";

  private static final String FONT_CLOSE = "</font>";

  // Minecraft gives each 20px action button a 2px label margin, leaving 16px
  // before DrawnTextConsumer enables its horizontal marquee. A fully opaque
  // 22px bitmap has a 23px advance. These invisible advances make the label
  // measure 16px while leaving the bitmap itself 22px wide, so it overdraws
  // the button and the grid's 2px gap without scrolling.
  private static final char BOARD_TILE_LEFT_SPACER = '\uE0FD';
  private static final char BOARD_TILE_RIGHT_SPACER = '\uE0FE';

  private final boolean glyphs;

  public PieceGlyph(boolean glyphs) {
    this.glyphs = glyphs;
  }

  private static String inFont(char codepoint) {
    return FONT_OPEN + codepoint + FONT_CLOSE;
  }

  private static String boardTile(char codepoint) {
    return FONT_OPEN + BOARD_TILE_LEFT_SPACER + codepoint + BOARD_TILE_RIGHT_SPACER + FONT_CLOSE;
  }

  /**
   * Returns the glyph string for a piece on a square.
   *
   * @param piece the piece
   * @param highlight the visual state of the square
   * @param light whether the underlying square is a light square
   * @return a one-glyph string
   */
  public String forPiece(Piece piece, Highlight highlight, boolean light) {
    if (!glyphs) {
      return unicodeFor(piece);
    }
    char base = plainCodepoint(piece);
    // Every state uses a composite that has the checkerboard baked in, so a square never
    // loses its colour just because it became highlighted.
    int codepoint =
        switch (highlight) {
          case NONE -> base + (light ? LIGHT_SQUARE_OFFSET : DARK_SQUARE_OFFSET);
          // Last move reuses the selected (orange) wash so the destination piece is
          // clearly marked for both players.
          case SELECTED, LAST_MOVE ->
              base + (light ? LIGHT_SQUARE_SELECTED_OFFSET : DARK_SQUARE_SELECTED_OFFSET);
          case LEGAL -> base + (light ? LIGHT_SQUARE_LEGAL_OFFSET : DARK_SQUARE_LEGAL_OFFSET);
        };
    return boardTile((char) codepoint);
  }

  /**
   * Returns the plain piece glyph with no square background baked in. Used where a
   * piece is shown outside the board grid (e.g. the captured-pieces list), so it
   * renders transparently over the dialog body rather than on a checkerboard tile.
   *
   * @param piece the piece
   * @return a one-glyph string
   */
  public String forPlainPiece(Piece piece) {
    if (!glyphs) {
      return unicodeFor(piece);
    }
    return inFont(plainCodepoint(piece));
  }

  /**
   * Returns the glyph string for an empty square.
   *
   * @param light whether the square is a light square
   * @param highlight the visual state of the square
   * @return a one-glyph string, or a space when glyphs are disabled
   */
  public String forEmpty(boolean light, Highlight highlight) {
    if (!glyphs) {
      return switch (highlight) {
        case NONE -> " ";
        case SELECTED, LAST_MOVE -> "☐"; // ballot box, distinct without colour
        case LEGAL -> "•"; // bullet marks a legal destination
      };
    }
    return switch (highlight) {
      case NONE -> boardTile(light ? EMPTY_LIGHT : EMPTY_DARK);
      case SELECTED, LAST_MOVE -> boardTile(light ? EMPTY_LIGHT_SELECTED : EMPTY_DARK_SELECTED);
      case LEGAL -> boardTile(light ? EMPTY_LIGHT_LEGAL : EMPTY_DARK_LEGAL);
    };
  }

  private static char plainCodepoint(Piece piece) {
    boolean white = piece.color() == PieceColor.WHITE;
    return switch (piece.type()) {
      case PAWN -> white ? WHITE_PAWN : BLACK_PAWN;
      case ROOK -> white ? WHITE_ROOK : BLACK_ROOK;
      case KNIGHT -> white ? WHITE_KNIGHT : BLACK_KNIGHT;
      case BISHOP -> white ? WHITE_BISHOP : BLACK_BISHOP;
      case QUEEN -> white ? WHITE_QUEEN : BLACK_QUEEN;
      case KING -> white ? WHITE_KING : BLACK_KING;
    };
  }

  /** Standard Unicode chess symbols, used when the resource pack is unavailable. */
  public static String unicodeFor(Piece piece) {
    boolean white = piece.color() == PieceColor.WHITE;
    return switch (piece.type()) {
      case KING -> white ? "♔" : "♚";
      case QUEEN -> white ? "♕" : "♛";
      case ROOK -> white ? "♖" : "♜";
      case BISHOP -> white ? "♗" : "♝";
      case KNIGHT -> white ? "♘" : "♞";
      case PAWN -> white ? "♙" : "♟";
    };
  }

  /** @return whether resource-pack glyph rendering is enabled */
  public boolean usesGlyphs() {
    return glyphs;
  }
}
