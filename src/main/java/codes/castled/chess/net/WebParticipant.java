package codes.castled.chess.net;

import java.util.UUID;

/**
 * Someone playing from the castled.codes dashboard rather than from a server.
 *
 * <p>Stands in for a player exactly as an engine opponent does: it holds a colour, has a clock,
 * and can be checkmated. The only difference is where its moves come from — the hub rather than a
 * search — which is why the same participant plumbing serves both.
 *
 * @param id the id this participant plays under
 * @param name the name shown in game and on the board, chosen on the dashboard
 */
public record WebParticipant(UUID id, String name) {}
