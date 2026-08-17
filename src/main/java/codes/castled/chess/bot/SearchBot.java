package codes.castled.chess.bot;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.move.MoveResultType;
import codes.castled.chess.engine.api.piece.Piece;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.api.piece.PieceType;
import codes.castled.chess.engine.common.move.UciMove;
import codes.castled.chess.wiring.EngineFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A bot that searches with this plugin's own engine, so it needs nothing installed.
 *
 * <p>It is deliberately modest. The engine generates roughly 640 full legal-move sets a second,
 * which is about a thousandth of what a real chess engine manages, so each extra ply costs several
 * times the last and useful search ends around two or three plies. That puts this bot in beginner
 * territory; a strong opponent needs an external engine behind the same {@link ChessBot} interface.
 *
 * <p>Positions are advanced by exporting a FEN and reloading it. That is slower than an undo stack
 * but it is exactly right, because a FEN carries castling rights and the en passant target — a
 * hand-rolled undo would have to reproduce both and would quietly mis-evaluate any line involving
 * them.
 *
 * <p>Search is iteratively deepened under a wall-clock budget, so a move always exists even when
 * the hardware is slow, and the bot never thinks for longer than a player will wait.
 */
public final class SearchBot implements ChessBot {

  /** Longest the bot will think, however deep its difficulty asks it to look. */
  private static final long BUDGET_MILLIS = 1_500;

  private static final int CHECKMATE_SCORE = 1_000_000;

  private final UUID id;
  private final String name;
  private final BotDifficulty difficulty;
  private final EngineFactory engine;
  private final Random random;

  public SearchBot(UUID id, BotDifficulty difficulty, EngineFactory engine, Random random) {
    this.id = id;
    this.name = "CPU (level " + difficulty.level() + ")";
    this.difficulty = difficulty;
    this.engine = engine;
    this.random = random;
  }

  @Override
  public UUID id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Nullable
  @Override
  public String chooseMove(String fen) {
    ChessGame position = engine.positionFromFen(fen);
    PieceColor side = sideToMove(position);

    List<ScoredMove> rootMoves = rootMoves(position, side);
    if (rootMoves.isEmpty()) {
      return null;
    }

    long deadline = System.currentTimeMillis() + BUDGET_MILLIS;

    // Iterative deepening: keep the best move from the deepest ply that finished in budget.
    for (int depth = 1; depth <= difficulty.searchDepth(); depth++) {
      boolean completed = scoreMoves(rootMoves, position, side, depth, deadline);
      if (!completed) {
        break;
      }
    }

    rootMoves.sort(Comparator.comparingInt((ScoredMove scored) -> scored.score).reversed());
    return pick(rootMoves).notation();
  }

  /**
   * Chooses between the scored moves, playing a worse one as often as the difficulty says.
   *
   * <p>Without this, low levels play strangely rather than weakly: a one-ply search still takes
   * every hanging piece it sees, then makes an alien-looking move elsewhere. Erring on purpose
   * reads much more like a beginner.
   */
  private ScoredMove pick(List<ScoredMove> ranked) {
    if (ranked.size() == 1 || random.nextDouble() >= difficulty.blunderChance()) {
      return ranked.get(0);
    }

    // Somewhere in the worse half, so the mistake is a real one rather than a near-miss.
    int from = Math.max(1, ranked.size() / 2);
    return ranked.get(from + random.nextInt(ranked.size() - from));
  }

  /**
   * @return whether the whole ply was searched; false when the budget ran out, in which case the
   *     scores from the previous, shallower pass are the ones to trust
   */
  private boolean scoreMoves(
      List<ScoredMove> moves, ChessGame position, PieceColor side, int depth, long deadline) {

    String fen = position.toFen();
    for (ScoredMove move : moves) {
      if (System.currentTimeMillis() > deadline) {
        return false;
      }

      ChessGame after = play(fen, move);
      if (after == null) {
        move.score = Integer.MIN_VALUE;
        continue;
      }

      move.score =
          -negamax(after, PieceColor.getOtherColor(side), depth - 1, -CHECKMATE_SCORE, CHECKMATE_SCORE, deadline);
    }
    return true;
  }

  private int negamax(
      ChessGame position, PieceColor side, int depth, int alpha, int beta, long deadline) {

    if (depth == 0 || System.currentTimeMillis() > deadline) {
      return evaluate(position.getChessBoard(), side);
    }

    List<ScoredMove> moves = rootMoves(position, side);
    if (moves.isEmpty()) {
      // No legal move: either mate against this side, or stalemate. Preferring a later mate over
      // an immediate one keeps the bot from walking into the fastest loss.
      return -CHECKMATE_SCORE + (10 - depth);
    }

    String fen = position.toFen();
    int best = -CHECKMATE_SCORE;

    for (ScoredMove move : moves) {
      ChessGame after = play(fen, move);
      if (after == null) {
        continue;
      }

      int score =
          -negamax(after, PieceColor.getOtherColor(side), depth - 1, -beta, -alpha, deadline);
      best = Math.max(best, score);
      alpha = Math.max(alpha, score);

      if (alpha >= beta) {
        break;
      }
    }

    return best;
  }

  /**
   * @return the position after the move, or null if it turned out not to be playable
   */
  @Nullable
  private ChessGame play(String fen, ScoredMove move) {
    ChessGame position = engine.positionFromFen(fen);
    UUID mover = position.getCurrentTurn();

    position.selectPiece(move.from, mover);
    MoveResultType result = position.makeMove(move.to, mover).type();
    position.unselectPiece(mover);

    if (result != MoveResultType.SUCCESS) {
      return null;
    }

    // A pawn reaching the last rank leaves the move pending until a piece is chosen. Bots always
    // take the queen, which is right in all but a handful of studies.
    if (position.getChessBoard().getPiece(move.to) == null) {
      position.applyPromotion(move.from, move.to, PieceType.QUEEN);
    }

    position.toggleTurn();
    return position;
  }

  private List<ScoredMove> rootMoves(ChessGame position, PieceColor side) {
    List<ScoredMove> moves = new ArrayList<>();
    MoveCalculator calculator = engine.moveCalculator();

    for (Square from : position.getChessBoard().getColoredPieces(side)) {
      for (Square to : calculator.getPossibleMoves(position, from)) {
        moves.add(new ScoredMove(from, to, promotionFor(position.getChessBoard(), from, to)));
      }
    }
    return moves;
  }

  @Nullable
  private PieceType promotionFor(ChessBoard board, Square from, Square to) {
    Piece piece = board.getPiece(from);
    if (piece == null || piece.type() != PieceType.PAWN) {
      return null;
    }
    return to.getRowIndex() == 0 || to.getRowIndex() == 7 ? PieceType.QUEEN : null;
  }

  /**
   * Scores a position from one side's point of view, in centipawns.
   *
   * <p>Material plus a small nudge towards the centre. Crude, but at two or three plies the search
   * is the limiting factor by a wide margin, so a more elaborate evaluation would not show.
   */
  private int evaluate(ChessBoard board, PieceColor side) {
    int score = 0;

    for (PieceColor color : PieceColor.values()) {
      int sign = color == side ? 1 : -1;

      for (Square square : board.getColoredPieces(color)) {
        Piece piece = board.getPiece(square);
        if (piece == null) {
          continue;
        }
        score += sign * (value(piece.type()) + centreBonus(square, piece.type()));
      }
    }

    return score;
  }

  private int value(PieceType type) {
    return switch (type) {
      case PAWN -> 100;
      case KNIGHT, BISHOP -> 320;
      case ROOK -> 500;
      case QUEEN -> 900;
      case KING -> 20_000;
    };
  }

  /** Central squares are worth a little, except to a king, which is safer at the edge. */
  private int centreBonus(Square square, PieceType type) {
    if (type == PieceType.KING) {
      return 0;
    }
    int fileDistance = Math.abs(square.getColumnIndex() * 2 - 7);
    int rankDistance = Math.abs(square.getRowIndex() * 2 - 7);
    return (14 - fileDistance - rankDistance) * 2;
  }

  private PieceColor sideToMove(ChessGame position) {
    return position.getCurrentTurn().equals(position.getWhitePlayerId())
        ? PieceColor.WHITE
        : PieceColor.BLACK;
  }

  /** A candidate move and the score the search gave it. */
  private static final class ScoredMove {
    private final Square from;
    private final Square to;
    private final PieceType promotion;
    private int score;

    private ScoredMove(Square from, Square to, @Nullable PieceType promotion) {
      this.from = from;
      this.to = to;
      this.promotion = promotion;
    }

    private String notation() {
      return new UciMove(from, to, promotion).notation();
    }
  }
}
