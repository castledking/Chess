package codes.castled.chess.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.UUID;

/**
 * Renders the watch invite with Adventure, the chat library Paper bundles. Loaded only when
 * Adventure is present.
 */
final class AdventureInviteBroadcaster implements InviteBroadcaster {

  @Override
  public void broadcast(WatchInvite invite, UUID whiteId, UUID blackId) {
    Component message =
        Component.text("")
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(Component.text(invite.label(), NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.GRAY))
            .append(Component.text(invite.whiteName(), NamedTextColor.WHITE))
            .append(Component.text(invite.versus(), NamedTextColor.GRAY))
            .append(Component.text(invite.blackName(), NamedTextColor.WHITE))
            .clickEvent(ClickEvent.suggestCommand(invite.command()))
            .hoverEvent(
                HoverEvent.showText(Component.text(invite.hoverText(), NamedTextColor.GREEN)));

    InviteBroadcaster.toSpectators(whiteId, blackId, player -> player.sendMessage(message));
  }
}
