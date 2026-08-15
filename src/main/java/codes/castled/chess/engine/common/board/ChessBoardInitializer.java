package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;

import java.util.List;

/** Initializes the chess board. */
public final class ChessBoardInitializer {

  private ChessBoardInitializer() {}

  /**
   * Puts the chess board in a starting state.
   *
   * @param chessBoard the chess board represented as an array
   */
  public static void initialize(Piece[][] chessBoard) {
    setWhitePieces(chessBoard);
    setBlackPieces(chessBoard);
  }

  /**
   * Puts all the white pieces on their right position inside the chess board array.
   *
   * @param chessBoard the chess board represented as an array
   */
  private static void setWhitePieces(Piece[][] chessBoard) {
    setPawns(chessBoard, PieceColor.WHITE);
    setRemainingPieces(chessBoard, PieceColor.WHITE);
  }

  /**
   * Puts all the black pieces on their right position inside the chess board array.
   *
   * @param chessBoard the chess board represented as an array
   */
  private static void setBlackPieces(Piece[][] chessBoard) {
    setPawns(chessBoard, PieceColor.BLACK);
    setRemainingPieces(chessBoard, PieceColor.BLACK);
  }

  /**
   * Puts the pawns on their right position inside the chess board array.
   *
   * @param chessBoard the chess board represented as an array
   * @param color the color of the pawns
   */
  private static void setPawns(Piece[][] chessBoard, PieceColor color) {
    int rowIndex = color == PieceColor.WHITE ? 1 : 6;
    for (int col = 0; col < 8; col++) {
      chessBoard[col][rowIndex] = new Piece(PieceType.PAWN, color);
    }
  }

  /**
   * Puts the remaining pieces on their right positions inside the chess board array.
   *
   * @param chessBoard the chess board represented as an array
   * @param color the color of the remaining pieces
   */
  private static void setRemainingPieces(Piece[][] chessBoard, PieceColor color) {
    int rowIndex = color == PieceColor.WHITE ? 0 : 7;
    List<PieceType> remainingPieceTypes =
        List.of(
            PieceType.ROOK,
            PieceType.KNIGHT,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.ROOK);

    for (int columnIndex = 0; columnIndex <= 7; columnIndex++) {
      chessBoard[columnIndex][rowIndex] = new Piece(remainingPieceTypes.get(columnIndex), color);
    }
  }
}
