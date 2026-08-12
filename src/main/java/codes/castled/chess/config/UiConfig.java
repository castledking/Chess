package codes.castled.chess.config;

import codes.castled.chess.Chess;
import codes.castled.chess.ui.DialogSettings;

/** Provides the {@code ui.dialog.*} section of settings.yml as an immutable snapshot. */
public final class UiConfig extends Config {

  public UiConfig(Chess plugin) {
    super(plugin, "settings.yml");
  }

  /** @return the dialog appearance/behaviour settings */
  public DialogSettings getDialogSettings() {
    return new DialogSettings(
        config.getString("ui.dialog.title", "<gold>Chess</gold>"),
        config.getBoolean("ui.dialog.allow-escape-close", true),
        config.getBoolean("ui.dialog.orientation-follows-player", true),
        config.getBoolean("ui.dialog.show-coordinates", true),
        config.getBoolean("ui.dialog.show-legal-moves", true),
        config.getBoolean("ui.dialog.show-last-move", true),
        config.getBoolean("ui.dialog.show-captured-pieces", true),
        config.getBoolean("ui.dialog.show-clock", true),
        Math.max(20, config.getInt("ui.dialog.clock-refresh-ticks", 20)),
        config.getBoolean("ui.dialog.use-glyphs", true),
        Math.max(1, config.getInt("ui.dialog.square-button-width", 20)),
        stripNull(config.getString("ui.dialog.click-sound")),
        stripNull(config.getString("ui.dialog.move-sound")),
        stripNull(config.getString("ui.dialog.check-sound")));
  }

  private String stripNull(String value) {
    return value == null ? "" : value;
  }
}
