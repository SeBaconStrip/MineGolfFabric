package net.colin.minegolf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.colin.minegolf.round.RoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** {@code /minegolf join <round>}, {@code /minegolf leave}, {@code /minegolf pickup}. No OP needed. */
public final class MineGolfCommand {
	private MineGolfCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("minegolf")
				.then(Commands.literal("join")
						.then(Commands.argument("round", IntegerArgumentType.integer(1))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									RoundManager.get(ctx.getSource().getServer()).join(player, IntegerArgumentType.getInteger(ctx, "round"));
									return 1;
								})))
				.then(Commands.literal("leave")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							RoundManager.get(ctx.getSource().getServer()).leave(player);
							return 1;
						}))
				.then(Commands.literal("pickup")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							RoundManager.get(ctx.getSource().getServer()).pickup(player);
							return 1;
						})));
	}
}
