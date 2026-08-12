package codes.castled.chess.request;

/** Represents a possible duel request creation result type. */
public enum DuelRequestCreationResultType {
  SUCCESS,
  FIRST_PLAYER_IN_GAME,
  SECOND_PLAYER_IN_GAME,
  BOTH_PLAYERS_IN_GAME,
  SAME_PLAYER,
  ALREADY_REQUESTED_DUEL
}
