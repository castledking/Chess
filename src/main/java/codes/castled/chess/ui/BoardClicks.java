package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.piece.PieceType;
import net.kyori.adventure.audience.Audience;

import java.util.UUID;

/**
 * The set of interactions a rendered chess dialog can raise. Implemented by
 * {@link DialogChessView}; the dialog builders ({@link PaperBoardDialog},
 * {@link PaperPromotionDialog}) attach these as button click callbacks, capturing the viewer
 * and the render sequence so a click from a stale board can be rejected.
 */
public interface BoardClicks {

  /** A board square was clicked (select / move). */
  void onCell(UUID viewerId, Square square, long sequence);

  /** The resign control was clicked. */
  void onResign(UUID viewerId);

  /** The draw control was clicked. */
  void onDraw(UUID viewerId);

  /** The flip control was clicked. */
  void onFlip(UUID viewerId);

  /** The close control was clicked; {@code audience} is the clicking player. */
  void onClose(UUID viewerId, Audience audience);

  /** The cancel-premoves control was clicked. */
  void onCancelPremoves(UUID viewerId);

  /** The focus/unfocus control was clicked. */
  void onFocus(UUID viewerId);

  /** A promotion piece was chosen. */
  void onPromote(UUID viewerId, PieceType type, long sequence);
}
