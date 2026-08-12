package codes.castled.chess.wiring;

import com.dxzell.pocketchess.api.board.ChessBoard;
import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.game.ChessGame;
import com.dxzell.pocketchess.api.game.ChessGameService;
import com.dxzell.pocketchess.api.move.MoveCalculator;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.common.game.ChessGameServiceImpl;
import com.dxzell.pocketchess.common.move.MoveCalculatorImpl;
import com.dxzell.pocketchess.common.move.MoveValidator;
import com.dxzell.pocketchess.common.move.calculator.BishopMoveCalculator;
import com.dxzell.pocketchess.common.move.calculator.KingMoveCalculator;
import com.dxzell.pocketchess.common.move.calculator.KnightMoveCalculator;
import com.dxzell.pocketchess.common.move.calculator.PawnMoveCalculator;
import com.dxzell.pocketchess.common.move.calculator.QueenMoveCalculator;
import com.dxzell.pocketchess.common.move.calculator.RookMoveCalculator;

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

  public EngineFactory() {
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
            new KingMoveCalculator());
    lazy.delegate = calculator;

    this.moveCalculator = calculator;
    this.chessGameService = new ChessGameServiceImpl(calculator, validator);
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
