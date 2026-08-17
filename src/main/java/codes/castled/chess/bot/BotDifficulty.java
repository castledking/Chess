package codes.castled.chess.bot;

/**
 * The requested strength of an engine opponent, from 1 to 10.
 *
 * <p>Levels are a player-facing scale rather than a search depth. How a bot honours them is its
 * own business: a search-based bot varies depth and how often it deliberately plays a worse move,
 * while an external engine maps them onto its own strength limiter.
 *
 * @param level the requested level, 1 to 10
 */
public record BotDifficulty(int level) {

  public static final int MIN = 1;
  public static final int MAX = 10;

  public BotDifficulty {
    if (level < MIN || level > MAX) {
      throw new IllegalArgumentException("Difficulty must be between " + MIN + " and " + MAX);
    }
  }

  /**
   * @param text the level as typed by a player
   * @return the parsed difficulty, or null if it is not a level
   */
  public static BotDifficulty parse(String text) {
    try {
      int level = Integer.parseInt(text.trim());
      return level < MIN || level > MAX ? null : new BotDifficulty(level);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /**
   * @return how far the bot should look ahead, for bots that search
   *     <p>Kept shallow deliberately. This engine generates roughly 640 full legal-move sets a
   *     second, so each extra ply costs several times the last; beyond this the bot would think
   *     for longer than a player will wait.
   */
  public int searchDepth() {
    return switch (level) {
      case 1, 2 -> 1;
      case 3, 4 -> 2;
      case 5, 6, 7 -> 3;
      default -> 4;
    };
  }

  /**
   * @return how often the bot should pick a move it knows is not the best, from 0 to 1
   *     <p>Weak levels need this to feel weak rather than merely strange. A shallow search alone
   *     plays alien-looking moves; a stronger search that sometimes errs reads as a beginner.
   */
  public double blunderChance() {
    return switch (level) {
      case 1 -> 0.60;
      case 2 -> 0.45;
      case 3 -> 0.32;
      case 4 -> 0.22;
      case 5 -> 0.15;
      case 6 -> 0.10;
      case 7 -> 0.06;
      case 8 -> 0.03;
      case 9 -> 0.01;
      default -> 0.0;
    };
  }
}
