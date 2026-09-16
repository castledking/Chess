package codes.castled.chess.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Covers when a refusal from the hub makes this server mint a new key.
 *
 * <p>Getting it wrong either way is visible: renewing on every refusal would rewrite settings.yml
 * over problems a new key cannot fix, and never renewing leaves a copied server off the network
 * for good.
 */
class KeyRenewalTest {

  private static JsonObject rejected(String code) {
    JsonObject frame = new JsonObject();
    frame.addProperty(HubProtocol.TYPE, HubProtocol.REJECTED);
    frame.addProperty("reason", "refused");
    if (code != null) {
      frame.addProperty("code", code);
    }
    return frame;
  }

  @Test
  void aSharedKeyIsRenewed() {
    assertTrue(HubNetwork.shouldRenewKey(rejected(HubProtocol.DUPLICATE_KEY), 0));
  }

  @Test
  void otherRefusalsAreNot() {
    assertFalse(HubNetwork.shouldRenewKey(rejected(null), 0), "a refusal without a code");
    assertFalse(HubNetwork.shouldRenewKey(rejected("BAD_KEY"), 0), "a refusal a new key cannot fix");
  }

  @Test
  void renewalStopsAtTheCap() {
    assertTrue(
        HubNetwork.shouldRenewKey(rejected(HubProtocol.DUPLICATE_KEY), HubNetwork.MAX_KEY_RENEWALS - 1));
    assertFalse(
        HubNetwork.shouldRenewKey(rejected(HubProtocol.DUPLICATE_KEY), HubNetwork.MAX_KEY_RENEWALS),
        "a key refused every time must not rewrite settings.yml forever");
  }
}
