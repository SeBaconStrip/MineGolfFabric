package net.colin.minegolf.course;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.colin.minegolf.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** One hole: where it is teed off, where the cup is, and its par. */
public record Hole(ResourceKey<Level> dimension, BlockPos tee, BlockPos cup, int par) {
	public static final int MIN_PAR = 2;
	public static final int MAX_PAR = 6;
	public static final int DEFAULT_PAR = 3;

	public static final Codec<Hole> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Hole::dimension),
			BlockPos.CODEC.fieldOf("tee").forGetter(Hole::tee),
			BlockPos.CODEC.fieldOf("cup").forGetter(Hole::cup),
			Codec.INT.fieldOf("par").forGetter(Hole::par)
	).apply(i, Hole::new));

	public Hole withPar(int newPar) {
		return new Hole(dimension, tee, cup, Math.clamp(newPar, MIN_PAR, MAX_PAR));
	}

	/** Horizontal distance from tee to cup in whole blocks. */
	public int length() {
		double dx = cup.getX() - tee.getX();
		double dz = cup.getZ() - tee.getZ();
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	/** Both marker blocks are still in place (unloaded chunks count as fine). */
	public boolean isIntact(MinecraftServer server) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) return false;
		if (!level.isLoaded(tee) || !level.isLoaded(cup)) return true;
		return level.getBlockState(tee).is(ModBlocks.TEE_BOX) && level.getBlockState(cup).is(ModBlocks.CUP);
	}
}
