package codes.castled.chess.engine.common.game;

import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.TimeMode;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.move.Move;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.common.board.CastlingStatus;
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

    whiteTimeLeftMillis = timeMode.getStartTimeMillis();
    blackTimeLeftMillis = timeMode.getStartTimeMillis();
    incrementMillis = timeMode.getIncrementMillis();

    capturedPieces = Map.of(whitePlayerId, new ArrayList<>(), blackPlayerId, new ArrayList<>());
  }

  @Override
  public MoveResult makeMove(Square toSquare, UUID playerId) {
    Square fromSquare = selectedSquares.get(playerId);
    Piece selectedPiece = chessBoard.getPiece(fromSquare);

    List<Square> possibleMoves = new ArrayList<>(moveCalculator.getPossibleMoves(this, fromSquare));

    if (!possibleMoves.contains(toSquare))
      return new MoveResult(MoveResultType.ILLEGAL, false, false, false, false);

    Move playedMove = new Move(selectedPiece, fromSquare, toSquare);
    Piece capturedPiece = chessBoard.movePiece(playedMove);
    MoveResult result = handleMove(fromSquare, toSquare, selectedPiece, capturedPiece);

    if (!result.promotion()) {
      chessBoard.setLastPlayedMove(playedMove);
    }

    if (capturedPiece != null) {
      capturedPieces.get(playerId).add(capturedPiece);
    }

    addIncrement(playerId);

    return result;
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
        : new MoveResult(MoveResultType.SUCCESS, false, false, false, false);
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
  public boolean hasKingMoved(PieceColor color) {
    return castlingStatus.hasKingMoved(color);
  }

  @Override
  public boolean hasKingSideRookMoved(PieceColor color) {
    return castlingStatus.hasKingSideRookMoved(color);
  }

  @Override
  public boolean hasQueenSideRookMoved(PieceColor color) {
    return castlingStatus.hasQueenSideRookMoved(color);
  }

  /**
   * @param pieceColor the piece color
   * @return the id of the player with the given piece color
   */
  public UUID from(PieceColor pieceColor) {
    return pieceColor == PieceColor.WHITE ? whitePlayerId : blackPlayerId;
  }
}
