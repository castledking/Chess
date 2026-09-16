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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
 *
 * <p>A server whose settings.yml was copied from another shares that server's key. The hub keeps
 * whichever connected first and refuses the other with {@link HubProtocol#DUPLICATE_KEY}; the
 * refused server then mints a key of its own and rejoins as a separate server.
 */
public final class HubNetwork implements ChessNetwork {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final long MIN_RETRY_SECONDS = 5;
  private static final long MAX_RETRY_SECONDS = 300;

  /**
   * How many fresh keys one run will mint. One is normally enough; the cap only matters if
   * something keeps refusing every new key, which would otherwise rewrite settings.yml forever.
   */
  static final int MAX_KEY_RENEWALS = 3;

  private final Chess plugin;
  private final NetworkSettings settings;
  private final Supplier<String> keyRenewer;

  /** Every remote player, keyed by uuid. Written by the network thread, read by server threads. */
  private final Map<UUID, RemotePlayer> roster = new ConcurrentHashMap<>();

  private final AtomicBoolean running = new AtomicBoolean();
  /**
   * The link in use, or null. Callbacks from a socket that is no longer this one are ignored, so a
   * close arriving late from an old connection cannot tear down or double-schedule the new one.
   */
  private final AtomicReference<WebSocket> socket = new AtomicReference<>();

  private final AtomicInteger keyRenewals = new AtomicInteger();
  private volatile String serverKey;
  private volatile long retrySeconds = MIN_RETRY_SECONDS;
  private volatile WebChallengeListener challengeListener;
  private volatile WebMoveListener moveListener;

  /** Frames can arrive split across several callbacks, so text is accumulated until complete. */
  private final StringBuilder incoming = new StringBuilder();

  /**
   * @param keyRenewer replaces this server's saved key with a new one and returns it. Called on the
   *     global region thread, since it writes settings.yml.
   */
  public HubNetwork(Chess plugin, NetworkSettings settings, Supplier<String> keyRenewer) {
    this.plugin = plugin;
    this.settings = settings;
    this.keyRenewer = keyRenewer;
    this.serverKey = settings.serverKey();
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
    WebSocket open = socket.getAndSet(null);
    if (open != null) {
      open.sendClose(WebSocket.NORMAL_CLOSURE, "shutting down");
    }
    roster.clear();
  }

  @Override
  public boolean isConnected() {
    WebSocket open = socket.get();
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
  public void setWebMoveListener(WebMoveListener listener) {
    this.moveListener = listener;
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
    frame.addProperty("fen", game.fen());
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

    URI uri = URI.create(settings.hubUrl() + HubProtocol.PATH + "?key=" + serverKey);

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

              // The listener recorded the socket as it opened, before this callback could run.
              retrySeconds = MIN_RETRY_SECONDS;
              plugin.getLogger().info("Chess network: connected as '" + settings.label() + "'.");

              // This callback runs on the HTTP client's thread, and building the hello reads the
              // online players, so it hops to the thread that owns them first. Reading Bukkit off
              // a server thread is unsupported and on Folia it is a genuine race.
              codes.castled.chess.util.Scheduler.global(plugin, this::sendHello);
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
   *     <p>Must be called on a server thread: {@link Bukkit#getOnlinePlayers()} is not safe to
   *     read from elsewhere. Both callers arrive there — presence changes come from an event, and
   *     the hello is scheduled onto the global region after the socket opens.
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
    WebSocket open = socket.get();
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
      case HubProtocol.WEB_MOVE -> handleWebMove(frame);
      case HubProtocol.REJECTED -> {
        String reason = frame.has("reason") ? frame.get("reason").getAsString() : "no reason given";
        if (shouldRenewKey(frame, keyRenewals.get())) {
          keyRenewals.incrementAndGet();
          plugin
              .getLogger()
              .warning(
                  "Chess network: another server is connected with this server's key, most likely "
                      + "because settings.yml was copied from it. Generating a new key and rejoining "
                      + "as a separate server.");
          // The hub closes the link straight after refusing, and that close schedules the
          // reconnect several seconds out, so the new key is in place well before it is used.
          codes.castled.chess.util.Scheduler.global(plugin, () -> serverKey = keyRenewer.get());
          return;
        }
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

  /** Hands a web move to the listener, on the thread that owns the board. */
  private void handleWebMove(JsonObject frame) {
    WebMoveListener listener = moveListener;
    if (listener == null || !frame.has("gameId") || !frame.has("move")) {
      return;
    }

    UUID gameId;
    try {
      gameId = UUID.fromString(frame.get("gameId").getAsString());
    } catch (IllegalArgumentException exception) {
      return;
    }

    String notation = frame.get("move").getAsString();
    codes.castled.chess.util.Scheduler.global(plugin, () -> listener.onWebMove(gameId, notation));
  }

  /** Replaces the roster wholesale, which is why a missed frame cannot leave it stale. */
  private void applyRoster(JsonObject frame) {
    Map<UUID, RemotePlayer> replacement = new ConcurrentHashMap<>();

    if (frame.has("players")) {
      for (var element : frame.getAsJsonArray("players")) {
        JsonObject entry = element.getAsJsonObject();
        // The hub leaves this server's own players out of the roster it sends here, and names
        // servers by a hash rather than their key, so there is nothing of our own to skip.
        String serverId = entry.get("serverId").getAsString();

        UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
        String label = entry.has("server") ? entry.get("server").getAsString() : serverId;
        replacement.put(uuid, new RemotePlayer(uuid, entry.get("name").getAsString(), label));
      }
    }

    roster.clear();
    roster.putAll(replacement);
  }

  /**
   * @param frame a REJECTED frame from the hub
   * @param renewalsSoFar how many keys this run has already minted
   * @return whether the refusal is one a fresh key fixes, and there are renewals left to try it
   */
  static boolean shouldRenewKey(JsonObject frame, int renewalsSoFar) {
    return frame.has("code")
        && HubProtocol.DUPLICATE_KEY.equals(frame.get("code").getAsString())
        && renewalsSoFar < MAX_KEY_RENEWALS;
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
      socket.set(webSocket);
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
      if (!socket.compareAndSet(webSocket, null)) {
        return null; // An old connection, or one onError already dealt with.
      }
      roster.clear();
      if (running.get()) {
        plugin.getLogger().info("Chess network: hub closed the link (" + reason + "); reconnecting.");
        scheduleReconnect();
      }
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      if (!socket.compareAndSet(webSocket, null)) {
        return;
      }
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
