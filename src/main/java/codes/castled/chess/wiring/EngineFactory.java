package codes.castled.chess.wiring;

import codes.castled.chess.engine.api.board.ChessBoard;
import codes.castled.chess.engine.api.board.Square;
import codes.castled.chess.engine.api.game.ChessGame;
import codes.castled.chess.engine.api.game.ChessGameService;
import codes.castled.chess.engine.api.move.MoveCalculator;
import codes.castled.chess.engine.api.piece.PieceColor;
import codes.castled.chess.engine.common.board.FenCodec;
import codes.castled.chess.engine.common.game.ChessGameImpl;
import codes.castled.chess.engine.common.game.ChessGameServiceImpl;
import codes.castled.chess.engine.common.move.MoveCalculatorImpl;
import codes.castled.chess.engine.common.move.MoveValidator;
import codes.castled.chess.engine.common.move.calculator.BishopMoveCalculator;
import codes.castled.chess.engine.common.move.calculator.KingMoveCalculator;
import codes.castled.chess.engine.common.move.calculator.KnightMoveCalculator;
import codes.castled.chess.engine.common.move.calculator.PawnMoveCalculator;
import codes.castled.chess.engine.common.move.calculator.QueenMoveCalculator;
import codes.castled.chess.engine.common.move.calculator.RookMoveCalculator;

import java.util.List;

/**
 * Builds the engine object graph by hand, so the plugin needs no dependency-injection framework
 * (dropping Guice removes ~8&nbsp;MB of Guava/Guice from the jar). This replaces the engine's own
 * {@code ChessModule}.
 *
 * <p>{@link MoveValidator} needs a {@link MoveCalculator} and {@link MoveCalculatorImpl} needs the
 * {@link MoveValidator} — a constructor cycle Guice previously broke with an interface proxy. It
 * is broken here with a one-shot delegating holder wired to the real calculator immediately after
 * construction, before any move is ever calculated.
 */
public final class EngineFactory {

  private final MoveCalculator moveCalculator;
  private final ChessGameService chessGameService;
  private final MoveValidator validator;

  /**
   * @param verticalCastling whether the vertical castling easter egg is enabled
   */
  public EngineFactory(boolean verticalCastling) {
    Lazy lazy = new Lazy();
    MoveValidator validator = new MoveValidator(lazy);
    MoveCalculatorImpl calculator =
        new MoveCalculatorImpl(
            validator,
            new PawnMoveCalculator(),
            new RookMoveCalculator(),
            new BishopMoveCalculator(),
            new KnightMoveCalculator(),
            new QueenMoveCalculator(),
            new KingMoveCalculator(),
            verticalCastling);
    lazy.delegate = calculator;

    this.moveCalculator = calculator;
    this.validator = validator;
    this.chessGameService = new ChessGameServiceImpl(calculator, validator);
  }

  /**
   * Loads an arbitrary position for analysis, without registering a game.
   *
   * <p>Used to hand a bot an immutable snapshot of a position so it can reason off the server
   * threads while the real board carries on being mutated.
   *
   * @param fen the position to load
   * @return a game holding that position
   */
  public ChessGame positionFromFen(String fen) {
    return ChessGameImpl.fromPosition(FenCodec.parse(fen), moveCalculator, validator);
  }

  public MoveCalculator moveCalculator() {
    return moveCalculator;
  }

  public ChessGameService chessGameService() {
    return chessGameService;
  }

  /** Delegating {@link MoveCalculator} that breaks the validator/calculator construction cycle. */
  private static final class Lazy implements MoveCalculator {
    private MoveCalculator delegate;

    @Override
    public List<Square> getPossibleMoves(ChessGame game, Square square) {
      return delegate.getPossibleMoves(game, square);
    }

    @Override
    public List<Square> getRawMoves(ChessBoard board, Square square) {
      return delegate.getRawMoves(board, square);
    }

    @Override
    public List<Square> getAllRawMoves(ChessBoard board, PieceColor color) {
      return delegate.getAllRawMoves(board, color);
    }
  }
}
