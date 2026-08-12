package codes.castled.chess.config;

import codes.castled.chess.Chess;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/** Shared functionality for the plugin's YAML-backed config classes. */
public abstract class Config {

  protected final Chess plugin;
  protected File file;
  protected YamlConfiguration config;

  protected Config(Chess plugin, String configName) {
    this.plugin = plugin;
    this.load(configName);
  }

  /** Saves changes made to the YAML file values. */
  protected void save() {
    try {
      config.save(file);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void load(String configName) {
    file = new File(plugin.getDataFolder(), configName);

    if (!file.exists()) {
      plugin.saveResource(configName, true);
    }

    config = new YamlConfiguration();
    config.options().parseComments(true);

    try {
      config.load(file);
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    updateConfig(configName);
  }

  /** Adds any keys present in the bundled default that the on-disk file is missing. */
  private void updateConfig(String configName) {
    java.io.InputStream defaults = plugin.getResource(configName);
    if (defaults == null) {
      return;
    }
    try (Reader defConfigStream = new InputStreamReader(defaults)) {
      YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);

      boolean changed = false;
      for (String key : defConfig.getKeys(true)) {
        if (!config.contains(key)) {
          config.set(key, defConfig.get(key));
          changed = true;
        }
      }

      if (changed) {
        save();
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  protected String getColoredString(String path) {
    String text = config.getString(path);
    return ChatColor.translateAlternateColorCodes(
        '&', text != null ? text : ChatColor.RED + "Path " + path + " not found.");
  }
}
