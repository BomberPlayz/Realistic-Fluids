package com.mcfht.realisticfluids;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.FluidData.ChunkCache;
import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.fluids.BlockFiniteWater;

/**
 * Handles virtually all fluid calculations. Manages worker threads.
 * 
 * @author FHT
 * 
 */
public class FluidManager
{

	public static Delegator			delegator	= new Delegator();

	public static WorkerPriority	PWorker		= new WorkerPriority();
	public static Thread			PRIORITY	= new Thread(PWorker);

	public static WorkerTrivial		TWorker		= new WorkerTrivial();
	public static Thread			TRIVIAL		= new Thread(TWorker);

	/**
	 * Delegates tasks to different threads in a not yet existing thread pool
	 * 
	 * @author FHT
	 * 
	 */
	public static class Delegator implements Runnable
	{
		public AtomicInteger	sweepCost	= new AtomicInteger(0);

		@Override
		public void run()
		{
			// TODO Auto-generated method stub

		}

	}

	public static class Worker implements Runnable
	{
		public boolean		forceQuit	= false;
		public boolean		isHighPriority;
		public int			myStartTick;
		public int			cost;
		public ChunkData	data;

		@Override
		public void run()
		{
			this.cost += doTask(this.data, this.isHighPriority, this.myStartTick);
			delegator.sweepCost.addAndGet(this.cost);
		}

	}
	/**
	 * Thread object to perform high priority updates
	 * 
	 * @author 4HT
	 * 
	 */
	public static class WorkerPriority implements Runnable
	{

		public int		myStartTime;
		public int		quota;
		public World[]	worlds;

		@Override
		public void run()
		{
			for (final World world : this.worlds)
			{
				// There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0)
					continue;

				final ChunkCache map = FluidData.worldCache.get(world);
				if (map == null)
					continue;
				if (map.priority.size() <= 0)
					continue;

				// Thread no. 1
				// Start with priority chunks!
				for (final Chunk c : map.priority)
				{
					final ChunkData data = map.chunks.get(c);
					if (data == null || !c.isChunkLoaded)
					{
						System.out.println("Map was null");
						continue;
					}
					doTask(data, true, this.myStartTime);
				}
				map.priority.clear();
			}
		}
	}

	/**
	 * Thread object to perform Trivial (aka distant) updates
	 * 
	 * @author 4HT
	 * 
	 */
	public static class WorkerTrivial implements Runnable
	{
		public int		myStartTime;
		public int		quota;
		public World[]	worlds;

		@Override
		public void run()
		{
			// System.err.println("Running trivial updater!");
			for (final World world : this.worlds)
			{
				// There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0)
					continue;

				final ChunkCache map = FluidData.worldCache.get(world);

				if (map == null)
					// System.err.println("Map es Null!");
					continue;
				if (map.distant.size() <= 0)
					// System.err.println("Nwo distant updwates!");
					continue;

				int ticksLeft = RealisticFluids.FAR_UPDATES; // Give ourselves a
																// tick quota
				// System.out.println("Ticks Left : " + ticksLeft);
				while (map.distant.size() > 0 && (ticksLeft > 0))
				{
					// Select a random distant chunk
					// int i = world.rand.nextInt(map.distant.size());
					final Chunk c = map.distant.poll(); // can we just do 0?
					// map.distant.remove(c);

					final ChunkData data = map.chunks.get(c);
					if (data == null || !c.isChunkLoaded)
						continue;
					// System.out.println("Doing trivial stuff");
					ticksLeft -= doTask(data, false, this.myStartTime);
				}
			}
		}
	}

	/**
	 * Performs updates within a chunk (or more precisely, a ChunkCache object
	 * 
	 * @param w
	 * @param c
	 * @param data
	 * @param flag
	 *            Do heavy equalization?
	 * @return
	 */
	public static int doTask(final ChunkData data, final boolean isHighPriority, final int startTime)
	{
		final int interval = (startTime % RealisticFluids.GLOBAL_RATE);
		int cost = 0;
		int x, y, z;

		// Iterate over each
		for (int i = 0; i < 16; i++)
		{

			// Don't bother with empty spaces
			if (data.c.getBlockStorageArray()[i] == null)
				continue;

			// First of all, let's perform our own random ticks (maor control)
			// do evaporation, seeping, refilling in rain, and so on.
			doRandomTicks(data, i, 3, isHighPriority);
			// No updates, exit
			if (!data.updateCounter[i] || data.updateFlags[i] == null)
				continue;

			data.workingUpdate[i] = new boolean[4096];
			System.arraycopy(data.updateFlags[i], 0, data.workingUpdate[i], 0, 4096);
			data.updateFlags[i] = new boolean[4096];

			// cost += Math.max(16, t.updateCounter[i] >> 6); //Moved this to
			// the end

			// Reset the cube flag
			data.updateCounter[i] = false;

			// ///////////////////////////////////////////////////////////////////////////////////
			for (int j = 0; j < 4096; j++)
				if (data.workingUpdate[i][j])
				{
					cost++;
					// Un-flag this block
					data.workingUpdate[i][j] = false;

					// Rebuild the coordinates from the array position
					x = (data.c.xPosition << 4) + (j & 0xF);
					y = (i << 4) + ((j >> 8) & 0xF);
					z = (data.c.zPosition << 4) + ((j >> 4) & 0xF);

					final Block b = data.c.getBlock(x & 0xF, y, z & 0xF);
					if (b instanceof BlockFiniteFluid)
						// Tick the water block
						((BlockFiniteFluid) b).doUpdate(data, x, y, z, data.w.rand, interval);

				}
		}
		// TODO: Make distant chunks re-render
		return cost;
	}

	/**
	 * Perform a specified number of random ticks in the 16x16x16 part of the
	 * world.
	 * 
	 * @param w
	 * @param c
	 * @param data
	 * @param ebsY
	 * @param number
	 * @param isHighPriority
	 */
	public static void doRandomTicks(final ChunkData data, final int ebsY, final int number, final boolean isHighPriority)
	{

		int equalizationQuota = isHighPriority ? RealisticFluids.EQUALIZE_NEAR : RealisticFluids.EQUALIZE_FAR;
		for (int i = 0; i < number; i++)
		{

			final int x = data.w.rand.nextInt(16);
			int y = data.w.rand.nextInt(16) + (ebsY << 4);
			final int z = data.w.rand.nextInt(16);

			final Block b = data.c.getBlock(x, y, z);
			// w.markBlockRangeForRenderUpdate(p_147458_1_, p_147458_2_,
			// p_147458_3_, p_147458_4_, p_147458_5_, p_147458_6_);
			// Do rainfall and evaporation
			// First, try to move up a few blocks (aka to the top of stuff)
			/*
			 * if (c.heightMap != null && c.heightMap[x + (z << 4)] < y + 16 &&
			 * c.heightMap[x + (z << 4)] < 255) { Block b1 = c.getBlock(x,
			 * c.heightMap[x + (z << 4)] + 1, y); if (b ==
			 * RealisticFluids.finiteWater || b == Blocks.air) doWaterFun(w, c,
			 * x, c.heightMap[x + (z << 4)] + 1, z, b); }
			 */

			// doWaterFun(data, b, x, y, z);
			// Only bother doing the next part with fluids
			if (b instanceof BlockFiniteFluid && FluidEqualizer.tasks.size() < RealisticFluids.EQUALIZE_GLOBAL)
			{
				// Make sure we don't overstep the equalization quota, Trivial
				// unless QUOTAS are set low
				if (equalizationQuota-- <= 0)
					continue;

				// Benefit large bodies of water by trying to find surface
				// blocks
				for (int j = 0; y < 255 && j < 8 && data.w.getBlock(x, y + 1, z) instanceof BlockFiniteFluid; j++)
					y++;

				if (data.w.getBlock(x, y + 1, z) != Blocks.air)
					continue;

				final int level = data.getLevel(x, y, z);
				// Prevent spamming on flat ocean areas
				if (level < RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 16))
					if (!isHighPriority && data.w.rand.nextInt(5) == 0)
						// System.out.println("Smoothing...");
						FluidEqualizer.addSmoothTask(data.w, (data.c.xPosition << 4) + x, y, (data.c.zPosition << 4) + z,
								(BlockFiniteFluid) b, RealisticFluids.MAX_FLUID >> 1, 8);
					else
						FluidEqualizer.addLinearTask(data.w, (data.c.xPosition << 4) + x, y, (data.c.zPosition << 4) + z,
								(BlockFiniteFluid) b, isHighPriority ? RealisticFluids.EQUALIZE_NEAR_R : RealisticFluids.EQUALIZE_FAR_R, 3);
			}
		}
	}

	public static void doWaterFun(final ChunkData data, final Block b, final int x, final int y, final int z)
	{
		if (data.c.canBlockSeeTheSky(x, y, z))
		{
			final int yRain = data.w.isRaining() ? 62 : data.w.isThundering() ? 63 : 0;
			if (yRain > 0 && data.c.getHeightValue(x, z) <= yRain)
			{
				final int wx = x + (data.c.xPosition << 4);
				final int wz = x + (data.c.zPosition << 4);
				final BiomeGenBase biome = data.c.getBiomeGenForWorldCoords(x, z, data.w.provider.worldChunkMgr);

				if (biome == BiomeGenBase.ocean || biome == BiomeGenBase.deepOcean || biome == BiomeGenBase.river)
					if (b == Blocks.air || b instanceof BlockFiniteWater)
					{
						System.out.println("Rain is falling!");
						FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.water, wx, y, wz, (RealisticFluids.MAX_FLUID >> 3)
								+ FluidData.getLevel(data, (BlockFiniteFluid) Blocks.water, x & 0xF, y + 1, z & 0xF), true);
					}
			}
		}

	}

	/**
	 * Handles all the fun things that can happen when playing with water, like
	 * evaporation and unicorns.
	 * 
	 * @param w
	 * @param c
	 * @param x
	 * @param y
	 * @param z
	 * @param b
	 */
	/*
	 * public static void doWaterFun(World w, Chunk c, int x, int y, int z,
	 * Block b) { boolean isWater = b != Blocks.air; int xx = x +
	 * (c.xPosition<<4), zz = z + (c.zPosition<<4); BiomeGenBase biome =
	 * c.getBiomeGenForWorldCoords(x, z, w.getWorldChunkManager()); if (y <= 64
	 * && (w.isRaining() || w.isThundering()) && biome.rainfall > 0F &&
	 * w.canBlockSeeTheSky(x, y, z)) { System.out.println("Rain Increasing...");
	 * if (isWater) { BlockFiniteFluid f = ((BlockFiniteFluid)b); int l0 =
	 * f.getLevel(w, xx, y, zz); l0 += y < 64 ? RealisticFluids.MAX_FLUID/6 :
	 * RealisticFluids.MAX_FLUID; f.setLevel(w, xx, y, zz, l0, true); if (l0 >
	 * RealisticFluids.MAX_FLUID) f.setLevel(w, xx, y, zz, l0 -
	 * RealisticFluids.MAX_FLUID, true); } else { w.setBlock(xx, y, zz,
	 * Blocks.water); BlockFiniteFluid f = (BlockFiniteFluid) c.getBlock(x, y,
	 * z); f.setLevel(w, xx, y, zz, f.viscosity, true); }
	 * 
	 * }
	 * 
	 * //Make water evaporate in deserts? if (b.getMaterial() == Material.water
	 * && biome.temperature > 1F) { System.out.println("Evaporating..");
	 * BlockFiniteFluid f = (BlockFiniteFluid) b; int l0 = f.getLevel(w, xx, y,
	 * zz); f.setLevel(w, xx, y, z, l0 - RealisticFluids.MAX_FLUID/2, true);
	 * //ChunkDataMap.setWaterLevel(w, c, x, y, z, ChunkDataMap.getWaterLevel(w,
	 * c, x, y, z) - (FiniteWater.MAX_FLUID/3)); } }
	 */
}
