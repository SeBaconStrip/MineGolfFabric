package net.colin.minegolf.client;

import net.colin.minegolf.network.ModPayloads;
import org.jspecify.annotations.Nullable;

/** Last round state the server sent; null when not in a round. */
public final class ClientRoundState {
	private static ModPayloads.@Nullable RoundState current;

	private ClientRoundState() {
	}

	public static ModPayloads.@Nullable RoundState get() {
		return current;
	}

	public static void set(ModPayloads.@Nullable RoundState state) {
		current = state;
	}
}
