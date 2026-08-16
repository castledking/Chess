package codes.castled.chess.ui.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.piece.PieceColor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Pins the mapping between chess squares and the inventory slots that display them.
 *
 * <p>This is the one piece of the inventory board that is pure logic and entirely invisible until
 * a player clicks the wrong square: an off-by-one here silently moves the wrong piece rather than
 * throwing. It is also the part most easily broken by a change to the layout, because the board
 * spans two inventories with different origins and a spare ninth column in every row.
 */
class InventoryLayoutTest {

  @Test
  void everySquareRoundTripsThroughItsSlotForBothColours() {
    for (PieceColor color : PieceColor.values()) {
      for (char row = '1'; row <= '8'; row++) {
        for (char column = 'A'; column <= 'H'; column++) {
          Square square = new Square(row, column);
          BoardSlot slot = InventoryLayout.toSlot(square, color);

          assertEquals(
              square,
              InventoryLayout.toSquare(slot, color),
              () -> "round trip failed for " + square + " as " + color);
        }
      }
    }
  }

  @Test
  void everySquareGetsItsOwnSlotForBothColours() {
    for (PieceColor color : PieceColor.values()) {
      Map<BoardSlot, Square> occupants = new HashMap<>();

      for (char row = '1'; row <= '8'; row++) {
        for (char column = 'A'; column <= 'H'; column++) {
          Square square = new Square(row, column);
          BoardSlot slot = InventoryLayout.toSlot(square, color);
          Square clash = occupants.put(slot, square);

          assertNull(clash, () -> square + " and " + clash + " share slot " + slot + " as " + color);
        }
      }

      assertEquals(64, occupants.size(), () -> "all 64 squares must be placed for " + color);
    }
  }

  @Test
  void theBoardFillsTheChestAndTwoRowsOfTheViewersInventory() {
    for (PieceColor color : PieceColor.values()) {
      long upper = 0;
      long lower = 0;

      for (char row = '1'; row <= '8'; row++) {
        for (char column = 'A'; column <= 'H'; column++) {
          BoardSlot slot = InventoryLayout.toSlot(new Square(row, column), color);
          if (slot.part() == InventoryPart.UPPER) {
            upper++;
            assertTrue(
                slot.slot() >= 0 && slot.slot() < InventoryLayout.UPPER_SIZE,
                () -> "chest slot out of range: " + slot);
          } else {
            lower++;
            // The two rows directly under the chest, never the hotbar.
            assertTrue(slot.slot() >= 9 && slot.slot() <= 26, () -> "row out of range: " + slot);
          }
        }
      }

      assertEquals(48, upper, "six ranks belong in the chest");
      assertEquals(16, lower, "two ranks belong in the viewer's own inventory");
    }
  }

  @Test
  void theSpareNinthColumnIsNotPartOfTheBoard() {
    for (PieceColor color : PieceColor.values()) {
      for (int slot = 0; slot < InventoryLayout.UPPER_SIZE; slot++) {
        if (slot % 9 == InventoryLayout.CONTROL_COLUMN) {
          assertNull(
              InventoryLayout.toSquare(new BoardSlot(slot, InventoryPart.UPPER), color),
              "the control column holds no square");
        }
      }
    }
  }

  @Test
  void clicksOutsideTheBoardMapToNothing() {
    for (PieceColor color : PieceColor.values()) {
      // The hotbar and the bottom row of the viewer's inventory are not part of the board.
      for (int slot : new int[] {0, 8, 27, 35}) {
        assertNull(InventoryLayout.toSquare(new BoardSlot(slot, InventoryPart.LOWER), color));
      }
      assertNull(InventoryLayout.toSquare(new BoardSlot(-1, InventoryPart.UPPER), color));
      assertNull(
          InventoryLayout.toSquare(
              new BoardSlot(InventoryLayout.UPPER_SIZE, InventoryPart.UPPER), color));
    }
  }

  @Test
  void eachColourSeesItsOwnBackRankNearest() {
    // White's first rank sits in white's own inventory; black's eighth rank sits in black's.
    BoardSlot whiteHome = InventoryLayout.toSlot(new Square('1', 'E'), PieceColor.WHITE);
    BoardSlot blackHome = InventoryLayout.toSlot(new Square('8', 'E'), PieceColor.BLACK);

    assertEquals(InventoryPart.LOWER, whiteHome.part());
    assertEquals(InventoryPart.LOWER, blackHome.part());

    // And each sees the opponent's back rank at the top of the chest.
    assertEquals(
        InventoryPart.UPPER, InventoryLayout.toSlot(new Square('8', 'E'), PieceColor.WHITE).part());
    assertNotNull(InventoryLayout.toSquare(new BoardSlot(0, InventoryPart.UPPER), PieceColor.WHITE));
    assertEquals(
        new Square('8', 'A'),
        InventoryLayout.toSquare(new BoardSlot(0, InventoryPart.UPPER), PieceColor.WHITE));
    assertEquals(
        new Square('1', 'H'),
        InventoryLayout.toSquare(new BoardSlot(0, InventoryPart.UPPER), PieceColor.BLACK));
  }
}
