package codes.castled.chess.engine.api.game;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.move.MoveResult;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;

import java.util.List;
import java.util.UUID;

/**
 * Represents a running chess game. Provides access to the current board state & allows legal moves
 * and some more game related operations to be executed.
 */
public interface ChessGame {

  /**
   * Tries to validate and execute a move to the given destination square
   * for the specified player.
   *
   * @param destination the destination square of the move
   * @param playerId the player trying to make the move
   * @return the result of the move execution
   */
  MoveResult makeMove(Square destination, UUID playerId);

  /**
   * Selects the piece on the given square for the specified player.
   *
   * @param square the square of the piece to select
   * @param playerId the player attempting to select the piece
   */
  void selectPiece(Square square, UUID playerId);

  /**
   * Unselects the piece on the given square for the specified player.
   *
   * @param playerId the player attempting to unselect the piece
   */
  void unselectPiece(UUID playerId);

  /**
   * Ends the game. Other player wins.
   *
   * @param surrenderUUID the player who surrendered
   */
  void surrender(UUID surrenderUUID);

  /**
   * Changes the current turn to other color.
   */
  void toggleTurn();

  /**
   * Ends the running game.
   *
   * @param winnerId the id of the winner, or null if a draw was played
   */
  void endGame(UUID winnerId);

  /**
   * @return the board that is being played on
   */
  ChessBoard getChessBoard();

  /**
   * @return the color of the player who has to make a move
   */
  UUID getCurrentTurn();

  /**
   * @param playerUUID the uuid of the player
   * @return the given players color if playing, or null if not playing
   */
  PieceColor getColor(UUID playerUUID);

  /**
   * @return the id of this game instance
   */
  UUID getGameId();

  /**
   * @return the id of the white player
   */
  UUID getWhitePlayerId();

  /**
   * @return the id of the black player
   */
  UUID getBlackPlayerId();

  /**
   * @param playerId the id of the requested player
   * @return the square of the selected piece, or null if no piece has been selected yet
   */
  Square getSelectedPieceSquare(UUID playerId);

  /**
   * @return a list of all captured pieces by the player with the given id
   */
  List<Piece> getCapturedPieces(UUID playerId);

  /**
   * @param playerId the id of the requested player
   * @return the time in milliseconds the player has left
   */
  long getTimeLeftMillis(UUID playerId);

  /**
   * Updates the players time to the specified amount.
   *
   * @param playerId the id of the requested player
   * @param newTimeMillis the new time in milliseconds
   */
  void updateRemainingTime(UUID playerId, long newTimeMillis);

  /**
   * @param color the color of the king
   * @return whether the king with the given color has already moved once
   */
  boolean hasKingMoved(PieceColor color);

  /**
   * @param color the color of the rook
   * @return whether the king side rook with the given color has already moved once
   */
  boolean hasKingSideRookMoved(PieceColor color);

  /**
   * @param color the color of the rook
   * @return whether the queen side rook with the given color has already moved once
   */
  boolean hasQueenSideRookMoved(PieceColor color);
}
