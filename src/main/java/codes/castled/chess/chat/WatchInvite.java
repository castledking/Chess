package codes.castled.chess.chat;

/**
 * The clickable "watch this game" chat broadcast, described independently of any chat library.
 *
 * <p>Rendered as {@code [Watch Game] White vs Black}, where the whole line runs {@code command} on
 * click and shows {@code hoverText} on hover.
 *
 * @param label the bracketed call to action, e.g. {@code Watch Game}
 * @param whiteName the white player's name
 * @param blackName the black player's name
 * @param versus the separator shown between the two names
 * @param command the command suggested when the message is clicked
 * @param hoverText the tooltip shown on hover
 */
public record WatchInvite(
    String label,
    String whiteName,
    String blackName,
    String versus,
    String command,
    String hoverText) {}
