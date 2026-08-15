package codes.castled.chess.ui;

import java.util.Map;

/**
 * Minimal label lookup used when rendering the Paper dialogs, so the dialog builders depend
 * on the labels they need rather than on the whole {@code MessageConfig} (and its plugin
 * backing). The single production implementation is {@code MessageConfig}.
 */
public interface DialogLabels {

  /** @return the raw dialog label for the given key under {@code messages.chess-game.dialog} */
  String getDialogLabel(String key);

  /** @return the dialog label with its {@code [placeholder]} tokens replaced */
  String getDialogLabel(String key, Map<String, String> placeholders);
}