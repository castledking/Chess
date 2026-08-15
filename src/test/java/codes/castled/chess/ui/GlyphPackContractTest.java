package codes.castled.chess.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pins the contract between {@link PieceGlyph} and the resource pack's board font: every
 * codepoint the glyph mapper can emit must be declared in {@code board.json}.
 *
 * <p>A codepoint the pack does not declare renders client-side as a tofu box, which is invisible
 * to every other test — the board still builds, the dialog still opens, and only a screenshot
 * shows the damage. This test enumerates the full product of piece, highlight, and square colour
 * so an incomplete plane cannot be reached unnoticed.
 *
 * <p>It caught the check plane being king-only: premoving a capture onto a checked king rendered
 * the capturing ghost piece against the check wash, asking for a glyph that does not exist.
 */
class GlyphPackContractTest {

  private static final Path BOARD_FONT =
      Path.of("src/main/resources/resourcepack/assets/chess/font/board.json");

  @Test
  void everyGlyphTheMapperCanEmitIsDeclaredByThePack() throws IOException {
    Set<Integer> declared = declaredCodepoints();
    PieceGlyph glyph = new PieceGlyph(true);
    List<String> missing = new ArrayList<>();

    for (PieceColor color : PieceColor.values()) {
      for (PieceType type : PieceType.values()) {
        Piece piece = new Piece(type, color);
        for (PieceGlyph.Highlight highlight : PieceGlyph.Highlight.values()) {
          for (boolean light : new boolean[] {true, false}) {
            for (int codepoint : privateUseCodepoints(glyph.forPiece(piece, highlight, light))) {
              if (!declared.contains(codepoint)) {
                missing.add(
                    "%s %s %s on %s square -> U+%04X"
                        .formatted(color, type, highlight, light ? "light" : "dark", codepoint));
              }
            }
          }
        }
      }
    }

    for (PieceGlyph.Highlight highlight : PieceGlyph.Highlight.values()) {
      for (boolean light : new boolean[] {true, false}) {
        for (int codepoint : privateUseCodepoints(glyph.forEmpty(light, highlight))) {
          if (!declared.contains(codepoint)) {
            missing.add(
                "empty %s on %s square -> U+%04X"
                    .formatted(highlight, light ? "light" : "dark", codepoint));
          }
        }
      }
    }

    assertTrue(missing.isEmpty(), () -> "codepoints missing from board.json:\n  " + String.join("\n  ", missing));
  }

  @Test
  void theCheckPlaneIsBakedForKingsOnly() throws IOException {
    Set<Integer> declared = declaredCodepoints();

    // Guard the assumption the fallback rests on: if the pack ever gains the full check plane,
    // the fallback in forPiece becomes unnecessary and should be removed rather than left to
    // silently downgrade a wash the pack could now render.
    for (boolean light : new boolean[] {true, false}) {
      int planeBase = light ? 0xEB00 : 0xEC00;
      for (int piece = 0x01; piece <= 0x0C; piece++) {
        boolean isKing = piece == 0x06 || piece == 0x0C;
        int codepoint = planeBase + piece;
        assertEquals(
            isKing, declared.contains(codepoint), () -> "U+%04X".formatted(codepoint));
      }
    }
  }

  @Test
  void aNonKingOnACheckSquareFallsBackInsteadOfEmittingAMissingGlyph() {
    PieceGlyph glyph = new PieceGlyph(true);
    Piece rook = new Piece(PieceType.ROOK, PieceColor.BLACK);

    // What premoving a capture onto a checked king used to ask for.
    String checkWash = glyph.forPiece(rook, PieceGlyph.Highlight.CHECK, true);

    assertEquals(glyph.forPiece(rook, PieceGlyph.Highlight.NONE, true), checkWash);
    // 0xEB08 is the light check plane's black rook, which board.json does not declare.
    assertFalse(
        checkWash.codePoints().anyMatch(codepoint -> codepoint == 0xEB08),
        "must not emit the undeclared check-plane rook");
  }

  private static Set<Integer> declaredCodepoints() throws IOException {
    String json = Files.readString(BOARD_FONT, StandardCharsets.UTF_8);
    Set<Integer> declared = new HashSet<>();

    // board.json writes its chars as JSON escapes; read them without pulling in a JSON parser.
    // Both the pattern and this comment build the escape prefix by concatenation, because javac
    // resolves that two-character sequence as a unicode escape even inside a comment.
    Matcher matcher = Pattern.compile("\\\\" + "u([0-9a-fA-F]{4})").matcher(json);
    while (matcher.find()) {
      declared.add(Integer.parseInt(matcher.group(1), 16));
    }

    // Any chars written literally rather than escaped still count as declared.
    json.codePoints()
        .filter(codepoint -> codepoint >= 0xE000 && codepoint <= 0xF8FF)
        .forEach(declared::add);

    assertFalse(declared.isEmpty(), "board.json declared no codepoints — is the path still right?");
    return declared;
  }

  /** @return the private-use-area codepoints in a glyph string, ignoring spacers and markup */
  private static List<Integer> privateUseCodepoints(String glyph) {
    List<Integer> codepoints = new ArrayList<>();
    glyph
        .codePoints()
        .filter(codepoint -> codepoint >= 0xE000 && codepoint <= 0xF8FF)
        // The invisible tile spacers are advances declared in the space provider, not bitmaps.
        .filter(codepoint -> codepoint != 0xE0FD && codepoint != 0xE0FE)
        .forEach(codepoints::add);
    return codepoints;
  }
}
