package codes.castled.chess.chat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.UUID;

/**
 * Renders the watch invite with the BungeeCord chat API, which Spigot bundles in place of
 * Adventure. Loaded only when Adventure is absent.
 */
final class SpigotInviteBroadcaster implements InviteBroadcaster {

  @Override
  public void broadcast(WatchInvite invite, UUID whiteId, UUID blackId) {
    BaseComponent[] message =
        new ComponentBuilder("[")
            .color(ChatColor.GRAY)
            .append(invite.label())
            .color(ChatColor.GREEN)
            .bold(true)
            .append("] ")
            .color(ChatColor.GRAY)
            .bold(false)
            .append(invite.whiteName())
            .color(ChatColor.WHITE)
            .append(invite.versus())
            .color(ChatColor.GRAY)
            .append(invite.blackName())
            .color(ChatColor.WHITE)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, invite.command()))
            .event(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new Text(ChatColor.GREEN + invite.hoverText())))
            .create();

    // The builder's click and hover events apply to the part being built, so they are attached
    // last and then copied over the earlier parts to cover the whole line, matching Adventure's
    // behaviour of styling the parent component.
    for (BaseComponent part : message) {
      part.setClickEvent(message[message.length - 1].getClickEvent());
      part.setHoverEvent(message[message.length - 1].getHoverEvent());
    }

    InviteBroadcaster.toSpectators(
        whiteId, blackId, player -> player.spigot().sendMessage(message));
  }
}
