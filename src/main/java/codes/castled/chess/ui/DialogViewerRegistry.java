package codes.castled.chess.ui;

import com.dxzell.pocketchess.api.board.Square;
import com.dxzell.pocketchess.api.move.Move;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * In-memory, per-game record of who is currently viewing the chess dialog and the small
 * amount of per-viewer presentation state the dialog needs.
 *
 * <p>This state is cleaned up when a viewer leaves, the game ends or the plugin disables (was in PocketChess
 * persistent player database) and is cleaned up when a viewer leaves, the game ends
 * or the plugin disables.
 */
public final class DialogViewerRegistry {

  /** Mutable per-viewer state. */
  public static final class ViewerState {
    private final boolean spectator;
    private boolean flipped;
    private boolean focused;
    private boolean displaying;
    private long revision;
    private String info = "";
    @Nullable private Square ghostSelected;
    private DrawItemType drawState = DrawItemType.NONE;
    private String drawMessage = "";
    private boolean surrenderConfirming;
    private String surrenderMessage = "";
    @Nullable private Move pendingPromotion;

    private ViewerState(boolean spectator) {
      this.spectator = spectator;
    }

    public boolean isSpectator() {
      return spectator;
    }

    public boolean isFlipped() {
      return flipped;
    }

    public boolean isFocused() {
      return focused;
    }

    @Nullable
    public Square ghostSelected() {
      return ghostSelected;
    }

    public boolean isDisplaying() {
      return displaying;
    }

    public long revision() {
      return revision;
    }

    public String info() {
      return info;
    }

    public DrawItemType drawState() {
      return drawState;
    }

    public String drawMessage() {
      return drawMessage;
    }

    public boolean surrenderConfirming() {
      return surrenderConfirming;
    }

    public String surrenderMessage() {
      return surrenderMessage;
    }

    @Nullable
    public Move pendingPromotion() {
      return pendingPromotion;
    }

    public boolean hasPendingPromotion() {
      return pendingPromotion != null;
    }
  }

  private final Map<UUID, ViewerState> viewers = new ConcurrentHashMap<>();

  /** Registers a game player as a viewer (idempotent). */
  public ViewerState addPlayer(UUID playerId) {
    return viewers.computeIfAbsent(playerId, id -> new ViewerState(false));
  }

  /** Registers a spectator (idempotent). */
  public ViewerState addSpectator(UUID spectatorId) {
    return viewers.computeIfAbsent(spectatorId, id -> new ViewerState(true));
  }

  @Nullable
  public ViewerState get(UUID viewerId) {
    return viewers.get(viewerId);
  }

  public boolean isViewer(UUID viewerId) {
    return viewers.containsKey(viewerId);
  }

  public boolean isSpectator(UUID viewerId) {
    ViewerState state = viewers.get(viewerId);
    return state != null && state.spectator;
  }

  public void remove(UUID viewerId) {
    viewers.remove(viewerId);
  }

  public void clear() {
    viewers.clear();
  }

  /** @return a stable snapshot of the current viewer ids */
  public Collection<UUID> viewerIds() {
    return List.copyOf(viewers.keySet());
  }

  /** Toggles the flip preference for a viewer and returns the new value. */
  public boolean toggleFlip(UUID viewerId) {
    ViewerState state = viewers.get(viewerId);
    if (state == null) {
      return false;
    }
    state.flipped = !state.flipped;
    return state.flipped;
  }

  /** Toggles the focus preference for a viewer and returns the new value. */
  public boolean toggleFocus(UUID viewerId) {
    ViewerState state = viewers.get(viewerId);
    if (state == null) {
      return false;
    }
    state.focused = !state.focused;
    return state.focused;
  }

  /** Advances and returns the viewer's render revision. Called once per render. */
  public long nextRevision(UUID viewerId) {
    ViewerState state = viewers.get(viewerId);
    if (state == null) {
      return -1;
    }
    return ++state.revision;
  }

  /** @return the viewer's current render revision, or -1 if not a viewer */
  public long currentRevision(UUID viewerId) {
    ViewerState state = viewers.get(viewerId);
    return state == null ? -1 : state.revision;
  }

  public void setInfo(UUID viewerId, String message) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      state.info = message == null ? "" : message;
    }
  }

  public void setDraw(UUID viewerId, DrawItemType drawState, String message) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      if (drawState != null) {
        state.drawState = drawState;
      }
      if (message != null) {
        state.drawMessage = message;
      }
    }
  }

  public void setSurrender(UUID viewerId, Boolean confirming, String message) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      if (confirming != null) {
        state.surrenderConfirming = confirming;
      }
      if (message != null) {
        state.surrenderMessage = message;
      }
    }
  }

  public void setPendingPromotion(UUID viewerId, @Nullable Move move) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      state.pendingPromotion = move;
    }
  }

  public void setDisplaying(UUID viewerId, boolean displaying) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      state.displaying = displaying;
    }
  }

  public void setGhostSelected(UUID viewerId, @Nullable Square square) {
    ViewerState state = viewers.get(viewerId);
    if (state != null) {
      state.ghostSelected = square;
    }
  }
}
