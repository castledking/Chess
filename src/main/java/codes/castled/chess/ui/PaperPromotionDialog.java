package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.piece.Piece;
import com.dxzell.pocketchess.api.piece.PieceColor;
import com.dxzell.pocketchess.api.piece.PieceType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.UUID;

/**
 * Builds the promotion-choice dialog shown when a pawn reaches the final rank. ESC is disabled
 * so the promotion cannot be left pending; the four choices carry the render sequence so a stale
 * response (from an outdated dialog) is rejected.
 */
public final class PaperPromotionDialog {

  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final PieceType[] CHOICES = {
    PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
  };

  private final DialogSettings settings;
  private final PieceGlyph glyph;
  private final DialogLabels labels;
  private final ClickCallback.Options clickOptions;

  public PaperPromotionDialog(
      DialogSettings settings,
      PieceGlyph glyph,
      DialogLabels labels,
      ClickCallback.Options clickOptions) {
    this.settings = settings;
    this.glyph = glyph;
    this.labels = labels;
    this.clickOptions = clickOptions;
  }

  /**
   * The promotion dialog's body content — a single prompt line. Exposed for the dialog-growth
   * test; the dialog itself never accumulates any other body content.
   */
  List<Component> bodyContent() {
    return List.of(MINI.deserialize(labels.getDialogLabel("promotion-prompt")));
  }

  private static DialogBody toBody(Component component) {
    return DialogBody.plainMessage(component);
  }

  /**
   * @param viewerId the promoting player
   * @param sequence the render sequence the choice must match to be accepted
   * @param color the promoting player's colour (chooses which artwork to show)
   * @param clicks the sink that receives the chosen piece
   * @return the promotion dialog
   */
  public Dialog build(UUID viewerId, long sequence, PieceColor color, BoardClicks clicks) {
    DialogBase base =
        DialogBase.builder(MINI.deserialize(labels.getDialogLabel("promotion-title")))
            .canCloseWithEscape(false)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .body(bodyContent().stream().map(PaperPromotionDialog::toBody).toList())
            .build();

    List<ActionButton> buttons = new java.util.ArrayList<>(CHOICES.length);
    for (PieceType type : CHOICES) {
      Component label = MINI.deserialize(glyph.forPlainPiece(new Piece(type, color)));
      Component tooltip = MINI.deserialize(labels.getDialogLabel("piece-" + type.name().toLowerCase()));
      PieceType chosen = type;
      buttons.add(
          ActionButton.create(
              label,
              tooltip,
              Math.max(settings.squareButtonWidth(), 40),
              DialogAction.customClick(
                  (response, audience) -> clicks.onPromote(viewerId, chosen, sequence), clickOptions)));
    }

    DialogType type = DialogType.multiAction(buttons).columns(CHOICES.length).build();
    return Dialog.create(factory -> factory.empty().base(base).type(type));
  }
}
