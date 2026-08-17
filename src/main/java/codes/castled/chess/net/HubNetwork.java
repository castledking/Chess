package codes.castled.chess.net;

import codes.castled.chess.Chess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Links this server to the others through a central hub.
 *
 * <p>The server dials out over a WebSocket, so it needs no inbound port and no proxy: servers that
 * share nothing but the hub's address can see each other. The client is {@link java.net.http},
 * which is part of the JDK, so this costs the jar nothing.
 *
 * <p>The roster is held locally and answered from memory, because tab completion asks for it on
 * the main thread and must never wait on a network. The hub sends it whole rather than as deltas,
 * so a server that misses a frame recovers on the next one instead of drifting.
 *
 * <p>Reconnection is automatic and backs off, since a hub restart should not require restarting
 * every server attached to it.
 */
public final class HubNetwork implements ChessNetwork {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final long MIN_RETRY_SECONDS = 5;
  private static final long MAX_RETRY_SECONDS = 300;

  private final Chess plugin;
  private final NetworkSettings settings;

  /** Every remote player, keyed by uuid. Written by the network thread, read by server threads. */
  private final Map<UUID, RemotePlayer> roster = new ConcurrentHashMap<>();

  private final AtomicBoolean running = new AtomicBoolean();
  private volatile WebSocket socket;
  private volatile long retrySeconds = MIN_RETRY_SECONDS;
  private volatile WebChallengeListener challengeListener;

  /** Frames can arrive split across several callbacks, so text is accumulated until complete. */
  private final StringBuilder incoming = new StringBuilder();

  public HubNetwork(Chess plugin, NetworkSettings settings) {
    this.plugin = plugin;
    this.settings = settings;
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    connect();
  }

  @Override
  public void stop() {
    running.set(false);
    WebSocket open = socket;
    socket = null;
    if (open != null) {
      open.sendClose(WebSocket.NORMAL_CLOSURE, "shutting down");
    }
    roster.clear();
  }

  @Override
  public boolean isConnected() {
    WebSocket open = socket;
    return open != null && !open.isOutputClosed();
  }

  @Override
  public Collection<RemotePlayer> remotePlayers() {
    return List.copyOf(roster.values());
  }

  @Nullable
  @Override
  public RemotePlayer findRemotePlayer(String name) {
    for (RemotePlayer player : roster.values()) {
      if (player.name().equalsIgnoreCase(name)) {
        return player;
      }
    }
    return null;
  }

  @Override
  public void setWebChallengeListener(WebChallengeListener listener) {
    this.challengeListener = listener;
  }

  @Override
  public void publishGame(GameSummary game) {
    if (!isConnected()) {
      return;
    }

    JsonObject frame = new JsonObject();
    frame.addProperty(HubProtocol.TYPE, HubProtocol.GAME);
    frame.addProperty("gameId", game.gameId().toString());
    frame.addProperty("white", game.white());
    frame.addProperty("black", game.black());
    frame.addProperty("whiteMillis", game.whiteMillis());
    frame.addProperty("blackMillis", game.blackMillis());
    frame.addProperty("turn", game.whiteToMove() ? "white" : "black");
    send(frame);
  }

  @Override
  public void publishGameEnded(UUID gameId) {
    if (!isConnected()) {
      return;
    }

    JsonObject frame = new JsonObject();
    frame.addProperty(HubProtocol.TYPE, HubProtocol.GAME_ENDED);
    frame.addProperty("gameId", gameId.toString());
    send(frame);
  }

  @Override
  public void publishPresence(Collection<RemotePlayer> joined, Collection<UUID> left) {
    if (!isConnected()) {
      return;
    }

    JsonObject frame = new JsonObject();
    frame.addProperty(HubProtocol.TYPE, HubProtocol.PRESENCE);
    frame.add("joined", playersArray(joined));

    JsonArray leftArray = new JsonArray();
    left.forEach(uuid -> leftArray.add(uuid.toString()));
    frame.add("left", leftArray);

    send(frame);
  }

  /* Connection -------------------------------------------------------- */

  private void connect() {
    if (!running.get()) {
      return;
    }

    URI uri = URI.create(settings.hubUrl() + HubProtocol.PATH + "?key=" + settings.serverKey());

    HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()
        .newWebSocketBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .buildAsync(uri, new Listener())
        .whenComplete(
            (opened, failure) -> {
              if (failure != null) {
                plugin
                    .getLogger()
                    .warning(
                        "Chess network: could not reach the hub ("
                            + rootCause(failure)
                            + "); retrying in "
                            + retrySeconds
                            + "s.");
                scheduleReconnect();
                return;
              }

              socket = opened;
              retrySeconds = MIN_RETRY_SECONDS;
              sendHello();
              plugin.getLogger().info("Chess network: connected as '" + settings.label() + "'.");
            });
  }

  private void scheduleReconnect() {
    if (!running.get()) {
      return;
    }

    long delay = retrySeconds;
    // Back off so a hub that is down does not get hammered by every server attached to it.
    retrySeconds = Math.min(MAX_RETRY_SECONDS, retrySeconds * 2);

    codes.castled.chess.util.Scheduler.globalLater(plugin, this::connect, delay * 20L);
  }

  private void sendHello() {
    JsonObject frame = new JsonObject();
    frame.addProperty(HubProtocol.TYPE, HubProtocol.HELLO);
    frame.addProperty("label", settings.label());
    frame.add("players", playersArray(localPlayers()));
    send(frame);
  }

  /**
   * @return everyone on this server right now
   *     <p>Read on whichever thread calls it; {@link Bukkit#getOnlinePlayers()} is safe to read
   *     from the main thread, which is where presence changes and the initial hello originate.
   */
  private Collection<RemotePlayer> localPlayers() {
    List<RemotePlayer> players = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      players.add(new RemotePlayer(player.getUniqueId(), player.getName(), settings.label()));
    }
    return players;
  }

  private JsonArray playersArray(Collection<RemotePlayer> players) {
    JsonArray array = new JsonArray();
    for (RemotePlayer player : players) {
      JsonObject entry = new JsonObject();
      entry.addProperty("uuid", player.uuid().toString());
      entry.addProperty("name", player.name());
      array.add(entry);
    }
    return array;
  }

  private void send(JsonObject frame) {
    WebSocket open = socket;
    if (open != null) {
      open.sendText(frame.toString(), true);
    }
  }

  /* Incoming ---------------------------------------------------------- */

  private void handle(String text) {
    JsonObject frame;
    try {
      frame = JsonParser.parseString(text).getAsJsonObject();
    } catch (RuntimeException exception) {
      plugin.getLogger().warning("Chess network: unreadable frame from the hub, ignoring it.");
      return;
    }

    String type = frame.has(HubProtocol.TYPE) ? frame.get(HubProtocol.TYPE).getAsString() : "";

    switch (type) {
      case HubProtocol.ROSTER -> applyRoster(frame);
      case HubProtocol.WEB_CHALLENGE -> handleWebChallenge(frame);
      case HubProtocol.REJECTED -> {
        String reason = frame.has("reason") ? frame.get("reason").getAsString() : "no reason given";
        plugin.getLogger().warning("Chess network: the hub refused this server (" + reason + ").");
        running.set(false);
      }
      default -> plugin.getLogger().fine("Chess network: ignoring unknown frame '" + type + "'.");
    }
  }

  /**
   * Hands a web challenge to the listener, on the thread that owns the game state.
   *
   * <p>Frames arrive on the HTTP client's threads, so this hops to the global region before
   * touching anything the server owns.
   */
  private void handleWebChallenge(JsonObject frame) {
    WebChallengeListener listener = challengeListener;
    if (listener == null || !frame.has("uuid")) {
      return;
    }

    UUID target;
    try {
      target = UUID.fromString(frame.get("uuid").getAsString());
    } catch (IllegalArgumentException exception) {
      return;
    }

    String challenger = frame.has("challenger") ? frame.get("challenger").getAsString() : "castled.codes";
    String timeMode = frame.has("timeMode") ? frame.get("timeMode").getAsString() : "TEN";

    codes.castled.chess.util.Scheduler.global(
        plugin, () -> listener.onWebChallenge(target, challenger, timeMode));
  }

  /** Replaces the roster wholesale, which is why a missed frame cannot leave it stale. */
  private void applyRoster(JsonObject frame) {
    Map<UUID, RemotePlayer> replacement = new ConcurrentHashMap<>();

    if (frame.has("players")) {
      for (var element : frame.getAsJsonArray("players")) {
        JsonObject entry = element.getAsJsonObject();
        String serverId = entry.get("serverId").getAsString();

        // The hub echoes every server; skip our own so local players are never listed as remote.
        if (serverId.equalsIgnoreCase(settings.serverKey())) {
          continue;
        }

        UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
        String label = entry.has("server") ? entry.get("server").getAsString() : serverId;
        replacement.put(uuid, new RemotePlayer(uuid, entry.get("name").getAsString(), label));
      }
    }

    roster.clear();
    roster.putAll(replacement);
  }

  private String rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getClass().getSimpleName()
        + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
  }

  /** Receives frames from the hub. Runs on the HTTP client's own threads, never a server thread. */
  private final class Listener implements WebSocket.Listener {

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      incoming.append(data);
      if (last) {
        String frame = incoming.toString();
        incoming.setLength(0);
        try {
          handle(frame);
        } catch (RuntimeException exception) {
          plugin.getLogger().warning("Chess network: failed to handle a frame: " + exception);
        }
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      socket = null;
      roster.clear();
      if (running.get()) {
        plugin.getLogger().info("Chess network: hub closed the link (" + reason + "); reconnecting.");
        scheduleReconnect();
      }
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      socket = null;
      roster.clear();
      if (running.get()) {
        plugin.getLogger().warning("Chess network: link failed (" + rootCause(error) + ").");
        scheduleReconnect();
      }
    }
  }

  /**
   * Settings the hub link needs.
   *
   * @param enabled whether cross-server play is switched on
   * @param hubUrl the hub's base URL
   * @param serverKey this server's identity, generated on first start and kept in the plugin's
   *     data folder. It is a secret: whoever holds it is this server as far as the hub is
   *     concerned.
   * @param label a human-readable server name, shown to players and on the dashboard. Never used
   *     for identity, so two servers sharing a label is untidy rather than unsafe.
   */
  public record NetworkSettings(boolean enabled, String hubUrl, String serverKey, String label) {

    /** @return whether the link can be attempted */
    public boolean usable() {
      return enabled && !hubUrl.isBlank() && !serverKey.isBlank();
    }
  }
}
