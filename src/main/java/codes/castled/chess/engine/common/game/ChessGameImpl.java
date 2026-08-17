package codes.castled.chess.engine.common.game;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.common.board.CastlingStatus;
import codes.castled.chess.engine.common.board.FenCodec;
import codes.castled.chess.engine.common.board.ChessBoardImpl;
import codes.castled.chess.engine.common.move.MoveValidator;
import codes.castled.chess.engine.common.move.SpecialMoveHandler;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a running chess game. Provides access to the current board state & allows legal moves
 * and some more game related operations to be executed.
 */
@Getter
public final class ChessGameImpl implements ChessGame {

  private final ChessGameServiceImpl chessService;
  private final MoveCalculator moveCalculator;
  private final SpecialMoveHandler specialMoveHandler;
  private final Map<UUID, List<Piece>> capturedPieces;
  private final Map<UUID, Square> selectedSquares = new HashMap<>();
  private final ChessBoardImpl chessBoard;
  private final UUID gameId = UUID.randomUUID();
  private final UUID whitePlayerId;
  private final UUID blackPlayerId;
  private final CastlingStatus castlingStatus = new CastlingStatus();
  private final long incrementMillis;
  private long whiteTimeLeftMillis;
  private long blackTimeLeftMillis;
  private PieceColor currentTurn = PieceColor.WHITE;

  /** Plies since the last capture or pawn move; at 100 the fifty-move rule applies. */
  private int halfmoveClock;

  /** Starts at 1 and increments after each of black's moves, as in FEN. */
  private int fullmoveNumber = 1;

  /**
   * How many times each position has been reached, keyed by the repetition-relevant part of its
   * FEN. A third occurrence is a draw by repetition.
   */
  private final Map<String, Integer> positionCounts = new HashMap<>();

  /**
   * The key of the position most recently arrived at. Held rather than recomputed on demand so
   * that asking about repetition does not depend on whether the caller has toggled the turn yet.
   */
  private String lastPositionKey;

  public ChessGameImpl(
      ChessGameServiceImpl chessService,
      TimeMode timeMode,
      ChessBoardImpl chessBoard,
      UUID whitePlayerId,
      UUID blackPlayerId,
      MoveCalculator moveCalculator,
      MoveValidator moveValidator) {
    this.chessService = chessService;
    this.moveCalculator = moveCalculator;
    this.chessBoard = chessBoard;
    this.whitePlayerId = whitePlayerId;
    this.blackPlayerId = blackPlayerId;

    specialMoveHandler = new SpecialMoveHandler(this, chessBoard, moveValidator);

    // Seed castling eligibility from wherever the rooks actually start, so an alternative
    // starting arrangement needs no changes here.
    for (PieceColor color : PieceColor.values()) {
      chessBoard
          .getColoredPieces(color, PieceType.ROOK)
          .forEach(square -> castlingStatus.markRookUnmoved(color, square));
    }

    whiteTimeLeftMillis = timeMode.getStartTimeMillis();
    blackTimeLeftMillis = timeMode.getStartTimeMillis();
    incrementMillis = timeMode.getIncrementMillis();

    capturedPieces = Map.of(whitePlayerId, new ArrayList<>(), blackPlayerId, new ArrayList<>());

    recordPosition(currentTurn);
  }

  @Override
  public MoveResult makeMove(Square toSquare, UUID playerId) {
    Square fromSquare = selectedSquares.get(playerId);
    Piece selectedPiece = chessBoard.getPiece(fromSquare);

    List<Square> possibleMoves = new ArrayList<>(moveCalculator.getPossibleMoves(this, fromSquare));

    if (!possibleMoves.contains(toSquare))
      return MoveResult.illegal();

    Move playedMove = new Move(selectedPiece, fromSquare, toSquare);
    Piece capturedPiece = chessBoard.movePiece(playedMove);
    MoveResult result = handleMove(fromSquare, toSquare, selectedPiece, capturedPiece);

    if (!result.promotion()) {
      chessBoard.setLastPlayedMove(playedMove);
    }

    advanceCounters(selectedPiece, capturedPiece, result);

    if (capturedPiece != null) {
      capturedPieces.get(playerId).add(capturedPiece);
    }

    addIncrement(playerId);

    return result;
  }

  /**
   * Advances the fifty-move clock, the move number, and the repetition history after a move.
   *
   * <p>A pending promotion is skipped: the move is rolled back until a piece is chosen, so the
   * position on the board is not one that was actually reached. {@link #applyPromotion} finishes
   * the bookkeeping once the piece is known.
   *
   * @param movedPiece the piece that moved
   * @param capturedPiece the piece captured, or null
   * @param result the result of the move
   */
  private void advanceCounters(
      Piece movedPiece, @Nullable Piece capturedPiece, MoveResult result) {
    if (result.promotion()) {
      return;
    }

    // A capture or a pawn move is irreversible, so it resets the clock and makes every earlier
    // position unreachable — which is why the repetition history is cleared with it.
    if (capturedPiece != null || movedPiece.type() == PieceType.PAWN) {
      halfmoveClock = 0;
      positionCounts.clear();
    } else {
      halfmoveClock++;
    }

    if (currentTurn == PieceColor.BLACK) {
      fullmoveNumber++;
    }

    // The turn has not been toggled yet, so the side to move in the resulting position is the
    // opponent of whoever just moved.
    recordPosition(PieceColor.getOtherColor(currentTurn));
  }

  /**
   * Counts the position that has just been arrived at.
   *
   * @param sideToMove who is to move in that position
   */
  private void recordPosition(PieceColor sideToMove) {
    lastPositionKey = FenCodec.repetitionKey(this, sideToMove);
    positionCounts.merge(lastPositionKey, 1, Integer::sum);
  }

  private void addIncrement(UUID playerId) {
    if (playerId.equals(whitePlayerId)) {
      whiteTimeLeftMillis += incrementMillis;
    } else {
      blackTimeLeftMillis += incrementMillis;
    }
  }

  /**
   * Handles the move by checking whether it is a special move or not and creates a suitable move
   * result.
   *
   * @param fromSquare the starter square
   * @param toSquare the destination square
   * @param selectedPiece the moved piece
   * @param capturedPiece the captured piece, or null if no piece was captured
   * @return the move result of the played move
   */
  private MoveResult handleMove(
      Square fromSquare, Square toSquare, Piece selectedPiece, @Nullable Piece capturedPiece) {

    specialMoveHandler.updateCastlingStatus(
        fromSquare, toSquare, selectedPiece, capturedPiece, currentTurn);

    MoveResult result =
        firstNotNull(
            specialMoveHandler.handleEnPassantMove(
                fromSquare, toSquare, selectedPiece, capturedPiece),
            specialMoveHandler.handleCastlingMove(fromSquare, toSquare, selectedPiece),
            specialMoveHandler.handlePromotionMove(
                capturedPiece, selectedPiece, fromSquare, toSquare));

    return result != null
        ? result
        : MoveResult.success();
  }

  /**
   * @param results the different possible move results
   * @return the first move result that isn't null
   */
  private MoveResult firstNotNull(MoveResult... results) {
    for (MoveResult result : results) {
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public void selectPiece(Square square, UUID playerId) {
    selectedSquares.put(playerId, square);
  }

  @Override
  public void unselectPiece(UUID playerId) {
    selectedSquares.remove(playerId);
  }

  @Override
  public void endGame(UUID winnerId) {
    chessService.removeGameById(gameId);
  }

  @Override
  public void surrender(UUID surrenderUUID) {
    endGame(surrenderUUID.equals(whitePlayerId) ? blackPlayerId : whitePlayerId);
  }

  @Override
  public void toggleTurn() {
    currentTurn = currentTurn == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
  }

  @Override
  public UUID getCurrentTurn() {
    return currentTurn == PieceColor.WHITE ? whitePlayerId : blackPlayerId;
  }

  @Override
  public PieceColor getColor(UUID playerUUID) {
    if (whitePlayerId.equals(playerUUID)) {
      return PieceColor.WHITE;
    }

    if (blackPlayerId.equals(playerUUID)) {
      return PieceColor.BLACK;
    }

    return null;
  }

  @Nullable
  @Override
  public Square getSelectedPieceSquare(UUID playerId) {
    return selectedSquares.get(playerId);
  }

  @Override
  public List<Piece> getCapturedPieces(UUID playerId) {
    return capturedPieces.get(playerId);
  }

  @Override
  public long getTimeLeftMillis(UUID playerId) {
    if (playerId.equals(whitePlayerId)) {
      return whiteTimeLeftMillis;
    } else {
      return blackTimeLeftMillis;
    }
  }

  @Override
  public void updateRemainingTime(UUID playerId, long newTimeMillis) {
    if (playerId.equals(whitePlayerId)) {
      whiteTimeLeftMillis = newTimeMillis;
    } else {
      blackTimeLeftMillis = newTimeMillis;
    }
  }

  @Override
  public int getHalfmoveClock() {
    return halfmoveClock;
  }

  @Override
  public int getFullmoveNumber() {
    return fullmoveNumber;
  }

  @Override
  public boolean isFiftyMoveDraw() {
    return halfmoveClock >= 100;
  }

  @Override
  public boolean isThreefoldRepetition() {
    return positionCounts.getOrDefault(lastPositionKey, 0) >= 3;
  }

  @Override
  public String toFen() {
    return FenCodec.export(this);
  }

  @Override
  public boolean hasKingMoved(PieceColor color) {
    return castlingStatus.hasKingMoved(color);
  }

  @Override
  public Set<Square> getUnmovedRookSquares(PieceColor color) {
    return castlingStatus.getUnmovedRookSquares(color);
  }

  @Override
  public void applyPromotion(Square from, Square to, PieceType type) {
    Piece pawn = chessBoard.getPiece(from);
    if (pawn == null) {
      return;
    }

    Piece promoted = new Piece(type, pawn.color());
    chessBoard.setPiece(from, null);
    chessBoard.setPiece(to, promoted);
    chessBoard.setLastPlayedMove(new Move(promoted, from, to));

    // A promoted rook has never moved, so it can castle — which is only reachable on the
    // king's file, and therefore only matters when vertical castling is enabled.
    if (type == PieceType.ROOK) {
      castlingStatus.markRookUnmoved(pawn.color(), to);
    }

    // A promotion is a pawn move, so it always resets the clock and the repetition history.
    // makeMove deferred this because the position was rolled back until a piece was chosen.
    halfmoveClock = 0;
    positionCounts.clear();
    if (currentTurn == PieceColor.BLACK) {
      fullmoveNumber++;
    }
    recordPosition(PieceColor.getOtherColor(currentTurn));
  }

  /**
   * @param pieceColor the piece color
   * @return the id of the player with the given piece color
   */
  public UUID from(PieceColor pieceColor) {
    return pieceColor == PieceColor.WHITE ? whitePlayerId : blackPlayerId;
  }
}
