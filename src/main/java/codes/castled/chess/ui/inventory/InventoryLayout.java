package codes.castled.chess.ui.inventory;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceColor;

import javax.annotation.Nullable;

/**
 * Translates between chess squares and the inventory slots that display them.
 *
 * <p>The board is drawn across two inventories because 64 squares do not fit in a chest's 54. The
 * six ranks furthest from the viewer occupy the whole 6-row chest, and the two nearest ranks
 * occupy the first two rows of the viewer's own inventory, directly below it — so the two read as
 * one continuous board.
 *
 * <p>Each inventory row is nine wide but a rank is eight, so the ninth column of every row holds no
 * square. In the chest that spare column is where the controls go.
 */
final class InventoryLayout {

  /** Slots in the chest inventory. */
  static final int UPPER_SIZE = 54;

  /** The ninth column of each chest row, reserved for controls rather than squares. */
  static final int CONTROL_COLUMN = 8;

  private InventoryLayout() {}

  /**
   * @param square the square on the board
   * @param color the viewer's colour, which decides the board's orientation
   * @return the slot displaying that square
   */
  static BoardSlot toSlot(Square square, PieceColor color) {
    return switch (color) {
      case WHITE -> {
        InventoryPart part = square.getRowIndex() <= 1 ? InventoryPart.LOWER : InventoryPart.UPPER;
        int slot =
            (part == InventoryPart.LOWER ? 9 : 0)
                + square.getColumnIndex()
                + (((part == InventoryPart.LOWER ? 1 : 7) - square.getRowIndex()) * 9);
        yield new BoardSlot(slot, part);
      }
      case BLACK -> {
        InventoryPart part = square.getRowIndex() >= 6 ? InventoryPart.LOWER : InventoryPart.UPPER;
        int slot =
            (part == InventoryPart.LOWER ? 9 : 0)
                + 7
                - square.getColumnIndex()
                + ((square.getRowIndex() - (part == InventoryPart.LOWER ? 6 : 0)) * 9);
        yield new BoardSlot(slot, part);
      }
    };
  }

  /**
   * @param boardSlot the clicked slot
   * @param color the viewer's colour, which decides the board's orientation
   * @return the square that slot displays, or null if it is not part of the board
   */
  @Nullable
  static Square toSquare(BoardSlot boardSlot, PieceColor color) {
    int slot = boardSlot.slot();
    InventoryPart part = boardSlot.part();

    boolean outsideLower = part == InventoryPart.LOWER && (slot < 9 || slot > 26);
    boolean outsideUpper = part == InventoryPart.UPPER && (slot < 0 || slot >= UPPER_SIZE);

    if (outsideLower || outsideUpper) {
      return null;
    }

    // The ninth column of every row holds a control or nothing, never a square.
    if (slot % 9 == CONTROL_COLUMN) {
      return null;
    }

    int row = slot / 9;
    int column = slot % 9;

    return switch (color) {
      case WHITE -> {
        int rowIndex =
            part == InventoryPart.UPPER ? 7 - row : 1 - (row - 1);
        yield new Square((char) ('1' + rowIndex), (char) ('A' + column));
      }
      case BLACK -> {
        int rowIndex =
            part == InventoryPart.UPPER ? row : 6 + (row - 1);
        yield new Square((char) ('1' + rowIndex), (char) ('A' + (7 - column)));
      }
    };
  }
}
