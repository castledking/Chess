package codes.castled.chess.engine.common.board;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Represents the chess board state and provides access to the pieces. */
public final class ChessBoardImpl implements ChessBoard {

  @Getter @Setter private Move lastPlayedMove;

  private final Piece[][] chessBoard;

  public ChessBoardImpl() {
    this.chessBoard = new Piece[8][8];

    ChessBoardInitializer.initialize(chessBoard);
  }

  /**
   * Creates a board holding an arbitrary position, for analysis rather than a new game.
   *
   * @param pieces the pieces, indexed as {@code [columnIndex][rowIndex]}; copied, so the caller
   *     may keep using its array
   */
  public ChessBoardImpl(Piece[][] pieces) {
    this.chessBoard = new Piece[8][8];
    for (int column = 0; column < 8; column++) {
      System.arraycopy(pieces[column], 0, chessBoard[column], 0, 8);
    }
  }

  /**
   * Runs the move logic on the chess board.
   *
   * @param move the move that was played
   * @return the captured piece, or null if the target square was empty
   * @throws IllegalStateException if there is no piece on the start square
   */
  @Nullable
  public Piece movePiece(Move move) {
    Square fromSquare = move.from();
    Square toSquare = move.to();

    Piece movingPiece = getPiece(fromSquare);
    Piece capturedPiece = getPiece(toSquare);

    if (movingPiece == null) {
      throw new IllegalStateException(
          "Cannot execute move because there is no piece on the start square.");
    }

    chessBoard[fromSquare.getColumnIndex()][fromSquare.getRowIndex()] = null;
    chessBoard[toSquare.getColumnIndex()][toSquare.getRowIndex()] = movingPiece;

    return capturedPiece;
  }

  @Override
  public List<Square> getColoredPieces(PieceColor color, PieceType... types) {
    List<Square> neededSquares = new ArrayList<>();
    List<Square> allOccupiedSquares = getAllOccupiedSquares();
    List<PieceType> typesList = Arrays.stream(types).toList();

    allOccupiedSquares.forEach(
        square -> {
          Piece piece = getPiece(square);
          if (piece != null && piece.color() == color && typesList.contains(piece.type())) {
            neededSquares.add(square);
          }
        });

    return neededSquares;
  }

  @Override
  public List<Square> getColoredPieces(PieceColor color) {
    return getColoredPieces(
        color,
        PieceType.KING,
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.KNIGHT,
        PieceType.QUEEN,
        PieceType.PAWN);
  }

  @Override
  public boolean isOccupied(Square square) {
    return getPiece(square) != null;
  }

  @Nullable
  @Override
  public Piece getPiece(Square square) {
    if (square == null) {
      return null;
    }

    return chessBoard[square.getColumnIndex()][square.getRowIndex()];
  }

  /**
   * Allows changes to the board state for move validation purposes.
   *
   * @param square the square on the chess board
   * @param piece the piece to set on the given square, or null
   */
  public void setPiece(Square square, Piece piece) {
    chessBoard[square.getColumnIndex()][square.getRowIndex()] = piece;
  }

  /**
   * @return all squares that hold pieces (list can be empty)
   */
  private List<Square> getAllOccupiedSquares() {
    List<Square> squares = new ArrayList<>();
    for (char column = 'A'; column <= 'H'; column++) {
      for (char row = '1'; row <= '8'; row++) {
        Square square = new Square(row, column);

        if (isOccupied(square)) {
          squares.add(square);
        }
      }
    }
    return squares;
  }
}
