package codes.castled.chess.engine.api.game;

import java.util.ArrayList;
import java.util.List;

/** Represents a possible chess game time mode. */
public enum TimeMode {
  ONE(1, 0, "1"),
  ONE_ONE(1, 1, "1|1"),
  TWO_ONE(2, 1, "2|1"),
  THREE(3, 0, "3"),
  THREE_TWO(3, 2, "3|2"),
  FIVE(5, 0, "5"),
  FIVE_FIVE(5, 5, "5|5"),
  FIVE_TWO(5, 2, "5|2"),
  TEN(10, 0, "10"),
  FIFTEEN_TEN(15, 10, "15|10"),
  THIRTY(30, 0, "30"),
  TEN_FIVE(10, 5, "10|5"),
  HOUR(60, 0, "60"),
  TWO_HOURS(120, 0, "120"),
  THREE_HOURS(180, 0, "180"),
  FOUR_HOURS(240, 0, "240"),
  FIVE_HOURS(300, 0, "300");

  private final int minutes;
  private final int incrementSeconds;
  private final String displayName;

  TimeMode(int minutes, int incrementSeconds, String displayName) {
    this.minutes = minutes;
    this.incrementSeconds = incrementSeconds;
    this.displayName = displayName;
  }

  /**
   * @param timeModeString the time mode as a string
   * @return the time mode enum object, or null if invalid
   */
  public static TimeMode fromDisplayName(String timeModeString) {
    for (TimeMode mode : values()) {
      if (mode.getDisplayName().equals(timeModeString)) {
        return mode;
      }
    }
    return null;
  }

  /**
   * @return a list of all time mode display names
   */
  public static List<String> getAllDisplayNames() {
    List<String> displayNames = new ArrayList<>();
    for (TimeMode mode : values()) {
      displayNames.add(mode.getDisplayName());
    }
    return displayNames;
  }

  /**
   * @param timeMode the time mode
   * @return whether the given time mode exists
   */
  public static boolean containsMode(String timeMode) {
    return getAllDisplayNames().contains(timeMode);
  }

  /**
   * @return the start time in milliseconds
   */
  public long getStartTimeMillis() {
    return minutes * 60L * 1000L;
  }

  /**
   * @return in increment in milliseconds
   */
  public long getIncrementMillis() {
    return incrementSeconds * 1000L;
  }

  public int getMinutes() {
    return minutes;
  }

  public int getIncrementSeconds() {
    return incrementSeconds;
  }

  public String getDisplayName() {
    return displayName;
  }
}
