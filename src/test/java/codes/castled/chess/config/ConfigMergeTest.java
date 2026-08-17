package codes.castled.chess.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins the rule that an existing settings.yml picks up keys added in a later version.
 *
 * <p>Without this, every new setting silently does nothing on an upgraded server: the code reads a
 * key its config has never heard of and quietly takes the default, so a feature appears not to
 * work and there is nothing in the file to suggest why.
 *
 * <p>The merge itself lives in {@link Config}, which needs a running server to construct, so the
 * same algorithm is exercised here against the real bundled settings.yml.
 */
class ConfigMergeTest {

  private static final Path BUNDLED = Path.of("src/main/resources/settings.yml");

  /** The merge {@code Config.updateConfig} performs: add every key the on-disk file lacks. */
  private static YamlConfiguration merge(YamlConfiguration onDisk, YamlConfiguration defaults) {
    for (String key : defaults.getKeys(true)) {
      if (!onDisk.contains(key)) {
        onDisk.set(key, defaults.get(key));
      }
    }
    return onDisk;
  }

  private static YamlConfiguration bundledDefaults() throws IOException {
    return YamlConfiguration.loadConfiguration(
        new StringReader(Files.readString(BUNDLED, StandardCharsets.UTF_8)));
  }

  @Test
  void aConfigFromBeforeCrossServerGainsTheNetworkSection() throws IOException {
    // A settings.yml as it looked before the network section existed.
    YamlConfiguration old =
        YamlConfiguration.loadConfiguration(
            new StringReader(
                """
                duel-request:
                  duel-request-expires-in-seconds: 60
                ui:
                  dialog:
                    show-legal-moves: true
                """));

    assertTrue(!old.contains("network"), "the old config has no network section to begin with");

    YamlConfiguration merged = merge(old, bundledDefaults());

    assertTrue(merged.contains("network"), "the network section must be added");
    assertTrue(merged.contains("network.enabled"));
    assertTrue(merged.contains("network.url"));
    assertTrue(merged.contains("network.server-key"));
    assertTrue(merged.contains("network.server-name"));
  }

  @Test
  void mergingNeverOverwritesWhatTheOperatorAlreadySet() throws IOException {
    YamlConfiguration old =
        YamlConfiguration.loadConfiguration(
            new StringReader(
                """
                network:
                  enabled: false
                  url: 'wss://example.test'
                """));

    YamlConfiguration merged = merge(old, bundledDefaults());

    assertEquals(false, merged.getBoolean("network.enabled"), "an explicit false must survive");
    assertEquals("wss://example.test", merged.getString("network.url"));
    assertTrue(merged.contains("network.server-key"), "missing keys are still filled in");
  }

  @Test
  void everySettingTheCodeReadsExistsInTheBundledDefault() throws IOException {
    // A key the code reads but the file never mentions is invisible: it silently takes the
    // default forever, and no operator can discover it exists.
    YamlConfiguration defaults = bundledDefaults();

    for (String key :
        new String[] {
          "network.enabled",
          "network.url",
          "network.server-name",
          "network.server-key",
          "easter-egg.enable-vertical-castling",
          "ui.mode",
          "ui.dialog.show-legal-moves",
          "resource-pack.use-resourcepack",
        }) {
      assertTrue(defaults.contains(key), () -> key + " is read by the code but not in settings.yml");
    }
  }
}
