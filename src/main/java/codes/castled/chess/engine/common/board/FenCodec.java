package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;

import javax.annotation.Nullable;

/**
 * Reads and writes positions in Forsyth-Edwards Notation.
 *
 * <p>FEN is the interchange format every chess engine speaks, so this is what lets the game be
 * handed to a UCI engine, mirrored to another server, or written down in a test. The six fields
 * are piece placement, side to move, castling availability, the en passant target, the halfmove
 * clock, and the move number.
 */
public final class FenCodec {

  /** The standard starting position. */
  public static final String START =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  private FenCodec() {}

  /**
   * @param game the game to describe
   * @return the full six-field FEN for the current position
   */
  public static String export(ChessGame game) {
    return placement(game.getChessBoard())
        + ' '
        + activeColor(game)
        + ' '
        + castling(game)
        + ' '
        + enPassantTarget(game.getChessBoard())
        + ' '
        + game.getHalfmoveClock()
        + ' '
        + game.getFullmoveNumber();
  }

  /**
   * Returns the part of the FEN that decides whether two positions count as the same for
   * repetition: everything except the two counters.
   *
   * <p>The counters are excluded deliberately. Two positions with the same pieces, side to move,
   * castling rights and en passant target <em>are</em> the same position for the threefold rule
   * even though the move number differs.
   *
   * @param game the game to describe
   * @param sideToMove who moves in the resulting position. Passed in rather than read from the
   *     game because the turn is toggled by the caller after a move, so the game's own idea of
   *     the current turn is briefly the side that just moved.
   * @return the repetition-relevant prefix of the FEN
   */
  public static String repetitionKey(ChessGame game, PieceColor sideToMove) {
    return placement(game.getChessBoard())
        + ' '
        + (sideToMove == PieceColor.WHITE ? "w" : "b")
        + ' '
        + castling(game)
        + ' '
        + enPassantTarget(game.getChessBoard());
  }

  /**
   * Parses a FEN into a position that can be loaded onto a board.
   *
   * @param fen the FEN to read
   * @return the parsed position
   * @throws IllegalArgumentException if the FEN is not six fields, or a field is malformed
   */
  public static FenPosition parse(String fen) {
    String[] fields = fen.trim().split("\\s+");
    if (fields.length != 6) {
      throw new IllegalArgumentException(
          "A FEN has six fields, this one has " + fields.length + ": " + fen);
    }

    Piece[][] board = parsePlacement(fields[0]);
    PieceColor turn = parseActiveColor(fields[1]);
    int halfmoveClock = parseCount(fields[4], "halfmove clock");
    int fullmoveNumber = parseCount(fields[5], "move number");

    return new FenPosition(
        board, turn, fields[2], parseSquare(fields[3]), halfmoveClock, fullmoveNumber);
  }

  /* Writing ---------------------------------------------------------- */

  private static String placement(ChessBoard board) {
    StringBuilder placement = new StringBuilder(64);

    for (int rowIndex = 7; rowIndex >= 0; rowIndex--) {
      int emptyRun = 0;

      for (int columnIndex = 0; columnIndex <= 7; columnIndex++) {
        Piece piece =
            board.getPiece(new Square((char) ('1' + rowIndex), (char) ('A' + columnIndex)));

        if (piece == null) {
          emptyRun++;
          continue;
        }

        if (emptyRun > 0) {
          placement.append(emptyRun);
          emptyRun = 0;
        }
        placement.append(symbol(piece));
      }

      if (emptyRun > 0) {
        placement.append(emptyRun);
      }
      if (rowIndex > 0) {
        placement.append('/');
      }
    }

    return placement.toString();
  }

  private static String activeColor(ChessGame game) {
    return game.getCurrentTurn().equals(game.getWhitePlayerId()) ? "w" : "b";
  }

  /**
   * Writes the castling field.
   *
   * <p>The engine tracks rights as the set of rooks that have never moved, which carries more
   * information than FEN's four flags: a promoted rook on the king's file is eligible for vertical
   * castling but has no letter to be written as. Such a rook is simply omitted — standard FEN
   * cannot express it, and every consumer of this output (engines, remote boards) plays standard
   * chess anyway.
   */
  private static String castling(ChessGame game) {
    StringBuilder rights = new StringBuilder(4);

    appendRights(game, PieceColor.WHITE, '1', 'K', 'Q', rights);
    appendRights(game, PieceColor.BLACK, '8', 'k', 'q', rights);

    return rights.isEmpty() ? "-" : rights.toString();
  }

  private static void appendRights(
      ChessGame game,
      PieceColor color,
      char homeRow,
      char kingSide,
      char queenSide,
      StringBuilder rights) {

    if (game.hasKingMoved(color)) {
      return;
    }

    // Asked in order rather than by iterating the rook set, because that set is unordered and
    // FEN requires exactly KQkq. Iterating it produced KQqk about half the time.
    if (hasUnmovedRookOn(game, color, homeRow, 'H')) {
      rights.append(kingSide);
    }
    if (hasUnmovedRookOn(game, color, homeRow, 'A')) {
      rights.append(queenSide);
    }
  }

  private static boolean hasUnmovedRookOn(
      ChessGame game, PieceColor color, char row, char column) {
    return game.getUnmovedRookSquares(color).contains(new Square(row, column));
  }

  /**
   * @return the square a pawn could capture onto en passant, or {@code -}
   *     <p>Written whenever a pawn has just advanced two squares, which is what FEN records —
   *     regardless of whether a capture is actually available.
   */
  private static String enPassantTarget(ChessBoard board) {
    Move last = board.getLastPlayedMove();
    if (last == null || last.piece() == null || last.piece().type() != PieceType.PAWN) {
      return "-";
    }

    int rowDelta = last.to().getRowIndex() - last.from().getRowIndex();
    if (Math.abs(rowDelta) != 2) {
      return "-";
    }

    char skippedRow = (char) ('1' + last.from().getRowIndex() + Integer.signum(rowDelta));
    return "" + Character.toLowerCase(last.to().column()) + skippedRow;
  }

  private static char symbol(Piece piece) {
    char letter =
        switch (piece.type()) {
          case PAWN -> 'p';
          case ROOK -> 'r';
          case KNIGHT -> 'n';
          case BISHOP -> 'b';
          case QUEEN -> 'q';
          case KING -> 'k';
        };
    return piece.color() == PieceColor.WHITE ? Character.toUpperCase(letter) : letter;
  }

  /* Reading ---------------------------------------------------------- */

  private static Piece[][] parsePlacement(String placement) {
    Piece[][] board = new Piece[8][8];
    String[] ranks = placement.split("/");

    if (ranks.length != 8) {
      throw new IllegalArgumentException(
          "A FEN placement has eight ranks, this one has " + ranks.length);
    }

    for (int rank = 0; rank < 8; rank++) {
      int rowIndex = 7 - rank;
      int columnIndex = 0;

      for (char symbol : ranks[rank].toCharArray()) {
        if (Character.isDigit(symbol)) {
          columnIndex += symbol - '0';
          continue;
        }

        if (columnIndex > 7) {
          throw new IllegalArgumentException("Rank " + (rowIndex + 1) + " is too long: " + ranks[rank]);
        }

        board[columnIndex][rowIndex] = piece(symbol);
        columnIndex++;
      }

      if (columnIndex != 8) {
        throw new IllegalArgumentException(
            "Rank " + (rowIndex + 1) + " covers " + columnIndex + " files, not 8");
      }
    }

    return board;
  }

  private static Piece piece(char symbol) {
    PieceColor color = Character.isUpperCase(symbol) ? PieceColor.WHITE : PieceColor.BLACK;
    PieceType type =
        switch (Character.toLowerCase(symbol)) {
          case 'p' -> PieceType.PAWN;
          case 'r' -> PieceType.ROOK;
          case 'n' -> PieceType.KNIGHT;
          case 'b' -> PieceType.BISHOP;
          case 'q' -> PieceType.QUEEN;
          case 'k' -> PieceType.KING;
          default -> throw new IllegalArgumentException("Unknown piece symbol: " + symbol);
        };
    return new Piece(type, color);
  }

  private static PieceColor parseActiveColor(String field) {
    return switch (field) {
      case "w" -> PieceColor.WHITE;
      case "b" -> PieceColor.BLACK;
      default -> throw new IllegalArgumentException("Side to move must be w or b, not: " + field);
    };
  }

  @Nullable
  private static Square parseSquare(String field) {
    if (field.equals("-")) {
      return null;
    }
    if (field.length() != 2) {
      throw new IllegalArgumentException("Not a square: " + field);
    }
    return new Square(field.charAt(1), Character.toUpperCase(field.charAt(0)));
  }

  private static int parseCount(String field, String name) {
    try {
      int value = Integer.parseInt(field);
      if (value < 0) {
        throw new IllegalArgumentException("The " + name + " cannot be negative: " + field);
      }
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("The " + name + " is not a number: " + field);
    }
  }
}
