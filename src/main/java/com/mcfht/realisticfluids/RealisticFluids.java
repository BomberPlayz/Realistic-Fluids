package com.mcfht.realisticfluids;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.EventBus;
import com.mcfht.realisticfluids.FluidData.ChunkCache;
import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.fluids.BlockFiniteFlowingWater;
import com.mcfht.realisticfluids.fluids.BlockFiniteWater;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.registry.ExistingSubstitutionException;
import cpw.mods.fml.common.registry.GameRegistry;

/* ~~~~~~~~~~~~~~~~~~General Notes on Code~~~~~~~~~~~~~~~~~~~~
 * 
 * I have a very particular naming scheme when I code.
 * 
 * abc0 refers to the FIRST or ORIGIN or whatever
 * abc1 refers to the TARGET or NEXT
 * abcn refers to the Nth target
 * 
 * For example, if we are changing fluid level; l0 is the current level, 
 * l1 is the next level, b0 is the target block, etcetera
 * 
 * If you see "i" or "j" or "k", or a seriously shortened word (aka "dist" = "distance")
 * then it is a counter, or temp variable. The full word is regarded as a parameter or local
 * 
 * I also like to use specific letters for a range of specific things. For example,
 * world = w, Block = b, Meta = m, Random = r, Level = l, and so on.
 * 
 * Parameters which I deem to be "unclear" typically get a full name, since it
 * just helps, you know.
 * 
 * 
 * That's about all that needs to be said on this matter, so enjoy perusing. 
 * 
 * - FHT
 * 
 */
@Mod(modid = FluidModInfo.MODID, version = FluidModInfo.VERSION)
public class RealisticFluids extends DummyModContainer
{
	// /////////////////////// GENERAL SETTINGS //////////////////////
	/** Max update quota per tick. TODO NOT MAX */
	public static int		MAX_UPDATES			= 1024;
	/** Force this much update quota TODO NOT MAX */
	public static int		FAR_UPDATES			= 2048;
	/** Number of ticks between update sweeps */
	public static int		GLOBAL_RATE			= 5;
	/** Max number of ticks between update sweeps */
	public static int		GLOBAL_RATE_MAX		= 10;
	public static int		GLOBAL_RATE_AIM		= 5;

	public static final int	CORES				= Runtime.getRuntime().availableProcessors();

	// //////////////////DISTANCE BASED PRIORITIZATION ///////////////////////
	/** Priority distance */
	public static int		UPDATE_RANGE		= 4 * 4;
	/** "Trivial" distance */
	public static int		UPDATE_RANGE_FAR	= 16 * 16;

	// /////////////////// EQUALIZATION SETTINGS //////////////////////
	/** Arbitrary limits on NEAR equalization */
	public static int		EQUALIZE_NEAR		= 2;
	public static int		EQUALIZE_NEAR_R		= 64;
	/** Aribtrary limits on DISTANT equalization */
	public static int		EQUALIZE_FAR		= 16;
	public static int		EQUALIZE_FAR_R		= 64;

	public static int		EQUALIZE_GLOBAL		= 32;

	// //////////////// FLUID SETTINGS //////////////////////
	/** The number of fluid levels for each cell */
	public final static int	MAX_FLUID			= 1 << 20;
	// WATER
	/** Relative update rate of water */
	public static int		WATER_UPDATE		= 1;
	/** Runniness of water */
	public static final int	waterVisc			= 4;
	// LAVA

	/** update rate of lava in the overworld */
	public static final int	LAVA_UPDATE			= 5;
	/** update rate of lava in the nether) */
	public static final int	LAVA_NETHER			= 3;
	/** Runniness of lava */
	public static final int	lavaVisc			= 3;

	public static Logger log;
	
	public static final BlockFiniteWater BFW_INST = new BlockFiniteWater(Material.water);
	public static final BlockFiniteFlowingWater BFFW_INST = new BlockFiniteFlowingWater(Material.water);

	public RealisticFluids()
	{
		super(new ModMetadata());
		FluidModInfo.get(this.getMetadata());
	}

	@Override
	public boolean registerBus(final EventBus bus, final LoadController controller)
	{
		bus.register(this);
		return true;
	}

	@EventHandler
	public void preInit(final FMLPreInitializationEvent event)
	{
		log = event.getModLog();
		FluidConfig.handleConfigs(new Configuration(event.getSuggestedConfigurationFile()));
	}

	@EventHandler
	public void init(final FMLInitializationEvent event)
	{
		log.info("Initializing RealisticFluids...");
		// Register event handlers
		FMLCommonHandler.instance().bus().register(this);
		MinecraftForge.EVENT_BUS.register(this);
		
		//for (Object o : Block.blockRegistry.getKeys()) {
		//	log.info(o.toString());
		//}
		
		//Item.itemRegistry.getKeys()
		
		try {
			//GameRegistry.registerBlock(new BlockFiniteWater(Material.water), "finitewater");
			//GameRegistry.registerBlock(new BlockFiniteFlowingWater(Material.water), "finiteflowingwater");
			//Item waterItem = GameRegistry.findItem("minecraft", "water");
			//log.info(waterItem.getClass());
			GameRegistry.addSubstitutionAlias("minecraft:water", GameRegistry.Type.BLOCK, BFW_INST);
			GameRegistry.addSubstitutionAlias("minecraft:water", GameRegistry.Type.ITEM, new ItemBlock(BFW_INST));
			GameRegistry.addSubstitutionAlias("minecraft:flowing_water", GameRegistry.Type.BLOCK, BFFW_INST);
			GameRegistry.addSubstitutionAlias("minecraft:flowing_water", GameRegistry.Type.ITEM, new ItemBlock(BFFW_INST));
			
			log.info("Successfully injected my water block!");
		} catch (ExistingSubstitutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.info("Done!");
	}

	/** Hidden internal tick counter */
	private static int	_tickCounter	= 0;
	/** Returns the current tick-time of this instance */
	public static int tickCounter()
	{
		return _tickCounter;
	}
	protected long	lastTime	= 0L;

	// ///////////////////////////////// BLOCK SETTING
	// ///////////////////////////////////////////////////
	/*
	 * Vanilla world.setBlock calls are not necessarily thread reliable. This
	 * implementation allows us to schedule updates for the server thread, AND
	 * allows us to set blocks a little more reliably in the EBS directly.
	 * 
	 * ONLY FLAG IMMEDIACY FROM AN ENVIRONMENT WHERE WE ARE DEFINITELY THREAD
	 * SAFE AND DO NOT CARE ABOUT THINGS LIKE HEIGHTMAPS AND LIGHTING.
	 */

	/**
	 * Marks block for update in world coordinates. Assumes block is fluid!
	 * Thread Safe. WARNING: THIS STATIC METHOD IS SLOW-ER. Use
	 * {@link ChunkData#markUpdate} where possible.
	 * 
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markBlockForUpdate(final World w, final int x, final int y, final int z)
	{
		// First ensure the target chunk is mapped
		Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (!c.isChunkLoaded)
			c = w.getChunkProvider().provideChunk(x >> 4, z >> 4);

		// System.out.println("***********START MARK***********");
		// System.out.println(" -" +Util.intStr(x, y, z));
		FluidData.getChunkData(c).markUpdate(x & 0xF, y, z & 0xF);
	}

	/**
	 * Full version of my heavily optimized and stripped set block method.
	 * Utterly ignores all kinds of updates which only take up out precious
	 * clocks, and typically do not matter at all when dealing with fluids
	 * themselves.
	 * 
	 * @param w
	 * @param c
	 * @param ebs
	 * @param x
	 * @param y
	 * @param z
	 * @param b
	 * @param m
	 * @param flag
	 */
	public static void setBlock(final World w, final Chunk c, ExtendedBlockStorage ebs, int x, int y, int z, final Block b, final int m,
			final int flag)
	{
		// EXTREME HAX
		if (ebs == null)
			ebs = c.getBlockStorageArray()[y >> 4] = new ExtendedBlockStorage(y & 0xFFFFFFF0, !c.worldObj.provider.hasNoSky);

		final int _flag = (flag >> 31) & 0x1;
		// At CPU level, this costs many less clocks than > or <, since we are
		// targetting specific conditions
		// if ((flag & 0x2) == (_flag))
		w.markBlockForUpdate(x, y, z); // Never called without rerender so...
		if ((flag & 0x1) != (_flag))
			w.notifyBlockChange(x, y, z, ebs.getBlockByExtId(x & 0xF, y & 0xF, z & 0xF));

		x &= 0xF;
		y &= 0xF;
		z &= 0xF;

		// Warning will not flag changes very far through the system!
		ebs.setExtBlockMetadata(x, y, z, m);
		ebs.func_150818_a(x, y, z, b); // If there was a block

		// Allow skipping relights

		// if ((flag & 0x8000000) == 0)
		c.updateSkylightColumns[x + (z << 4)] = true;
	}
	/**
	 * Same as above, but metadata optimized
	 * 
	 * @param w
	 * @param c
	 * @param ebs
	 * @param x
	 * @param y
	 * @param z
	 * @param m
	 * @param flag
	 */
	public static void setMetadata(final World w, final Chunk c, ExtendedBlockStorage ebs, int x, int y, int z, final int m, final int flag)
	{
		// EXTREME HAX
		if (ebs == null)
			// 2 bitshifts where one & is enough (2x more overhead lol)...
			// You're slipping Mojang /clinically insane optimizer
			ebs = c.getBlockStorageArray()[y >> 4] = new ExtendedBlockStorage(y & 0xFFFFFF0, !c.worldObj.provider.hasNoSky);

		final int _flag = (flag >> 31) & 0x1;
		// At CPU level, this costs many less clocks than > or <, since we are
		// targetting specific conditions
		// if ((flag & 0x2) == (_flag))
		w.markBlockForUpdate(x, y, z);
		if ((flag & 0x1) != (_flag))
			w.notifyBlockChange(x, y, z, ebs.getBlockByExtId(x & 0xF, y & 0xF, z & 0xF));
		x &= 0xF;
		y &= 0xF;
		z &= 0xF;
		// Warning will not flag changes very far through the system. Care when
		// using with other systems!
		ebs.setExtBlockMetadata(x, y, z, m);
		// Allow skipping relights
		// if ((flag & 0x8000000) == 0)
		c.updateSkylightColumns[x + (z << 4)] = true;
	}

	/**
	 * Guaranteed thread safe block setting method, directly manipulates EBS,
	 * skips some redundant world.setBlock calls, allows skipping of light
	 * recalculations, and allows unimportant block updates to deferred to the
	 * server tick at a later time.
	 * 
	 * <p>
	 * Only supports flags 2 and 3, however negative versions will skip lighting
	 * recalculations.
	 * 
	 * <p>
	 * Flag immediacy for fluid updates. Immediacy forces all kinds of hacky
	 * things, like skipping relights and heightmap updates, and hence should
	 * ~only~ be used with fluids! Not flagging immediacy will ship the
	 * operation out to the server tick, where it will eventually be performed
	 * (much more thread safe).
	 * 
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param m
	 * @param flag
	 *            : 0 = no update or light, 2 = render update, 3 = block update,
	 *            negative prevents light recalc
	 * @param immediate
	 *            : whether we should do this now, or defer it to the server
	 *            tick event.
	 */
	public static void setBlock(final World w, final int x, final int y, final int z, final Block b, final int m, final int flag,
			final boolean immediate)
	{
		Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (c == null || !c.isChunkLoaded)
			c = w.getChunkProvider().provideChunk(x >> 4, z >> 4);
		final ExtendedBlockStorage ebs = c.getBlockStorageArray()[y >> 4];
		if (!immediate)
		{
			BlockTask.blockTasks.add(new BlockTask(w, c, ebs, x, y, z, b, m, flag));
			return;
		}
		if (b == null)
			setMetadata(w, c, ebs, x, y, z, m, flag);
		else
			setBlock(w, c, ebs, x, y, z, b, m, flag);
	}

	public static void setBlockMetadata(final World world, final int x, final int y, final int z, final int meta, final int flag)
	{
		setBlock(world, x, y, z, null, meta, flag, true);
	}

	public static void setBlock(final World world, final int x, final int y, final int z, final Block block, final int meta, final int flag)
	{
		setBlock(world, x, y, z, block, meta, flag, true);
	}
	/**
	 * Simple queue implementation to prevent duplicate entries. TODO Benchmark
	 * 
	 * @author FHT
	 * @param <E>
	 */
	static class QueueSet<E> extends ConcurrentLinkedQueue<E>
	{
		@Override
		public boolean add(final E e)
		{
			if (super.contains(e))
				return false;
			return super.add(e);
		}
	}

	/**
	 * Block Task Object for multiple thread access stuffs
	 * 
	 * @author FHT
	 * 
	 */
	private static class BlockTask
	{
		public static QueueSet<BlockTask>	blockTasks	= new QueueSet<BlockTask>();

		World								w;
		Chunk								c;
		ExtendedBlockStorage				ebs;
		int									x;
		int									y;
		int									z;
		Block								b;
		int									m;
		int									f;

		/** Block Task object to be constructed for thread safe block setting! */
		BlockTask(final World world, final Chunk c, final ExtendedBlockStorage ebs, final int x, final int y, final int z, final Block b,
				final int m, final int flag)
		{
			this.w = world;
			this.c = c;
			this.ebs = ebs;
			this.x = x;
			this.y = y;
			this.z = z;
			this.b = b;
			this.m = m;
			this.f = flag;
		}

		/** Perform this block task. Thread Safe. */
		public boolean set()
		{
			setBlock(this.w, this.c, this.ebs, this.x, this.y, this.z, this.b, this.m, this.f);
			return true;
		}
	}

	/**
	 * Clean up after ourselves when a chunk is unloaded.
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public void chunkUnload(final ChunkEvent.Unload event)
	{
		if (FluidData.worldCache.get(event.world) != null)
			FluidData.worldCache.get(event.world).chunks.remove(event.getChunk());
	}
	/**
	 * Clean up after ourselves when a world is unloaded
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public void worldUnload(final WorldEvent.Unload event)
	{
		if (FluidData.worldCache.get(event.world) != null)
			for (final ChunkData c : FluidData.worldCache.get(event.world).chunks.values())
				FluidData.worldCache.get(event.world).chunks.values().remove(c);
	}

	@SubscribeEvent
	public void serverTick(final ServerTickEvent event)
	{
		_tickCounter += 1;
		FluidEqualizer.WORKER.run();

		if (event.phase == Phase.START)
		{
			final long timeCost = System.currentTimeMillis() - this.lastTime;
			if (this.lastTime > 0)
				if (timeCost > 500)
					GLOBAL_RATE = Math.min(++GLOBAL_RATE, GLOBAL_RATE_MAX);
				else if (timeCost < 40 && (_tickCounter % GLOBAL_RATE) == 1)
					GLOBAL_RATE = Math.max(--GLOBAL_RATE, GLOBAL_RATE_AIM);
			this.lastTime = System.currentTimeMillis();
		}

		// System.out.println("Doing tick");
		if (event.phase == Phase.END && (tickCounter() % GLOBAL_RATE) == 0)
		{
			// FIND CHUNKS
			for (final World w : MinecraftServer.getServer().worldServers)
			{
				if (w.playerEntities == null || w.playerEntities.size() == 0)
					continue;
				for (final Object p : w.playerEntities)
				{
					final EntityPlayer player = (EntityPlayer) p;
					final ChunkCache map = FluidData.worldCache.get(w);
					if (map == null)
						continue;
					// iterate over all flagged chunks
					for (final Chunk c : map.chunks.keySet())
					{
						if (!c.isChunkLoaded)
							continue;// Just to be safe;
						final int x = c.xPosition - (((int) player.posX) >> 4);
						final int z = c.zPosition - (((int) player.posZ) >> 4);
						final int dist = x * x + z * z;
						if (dist <= UPDATE_RANGE)
							map.priority.add(c);
						else if (dist <= UPDATE_RANGE_FAR)
							// System.out.println("Found distant chunk: " +
							// map.distant.size());
							if (map.distant.size() < 256)
								// System.out.println("Added eeet");
								map.distant.add(c);
					}
				}
			}

			FluidManager.delegator.myStartTick = tickCounter();
			FluidManager.delegator.worlds = MinecraftServer.getServer().worldServers.clone();
			System.out.println("Performing Tasks...");
			FluidManager.delegator.performTasks();

			/*
			 * FluidManager.PWorker.quota = tickQuota;
			 * FluidManager.PWorker.myStartTime = tickCounter();
			 * FluidManager.PWorker.worlds =
			 * MinecraftServer.getServer().worldServers.clone(); // Running task
			 * like this is fine, since OS will just try to catch // up threads
			 * at some point // In the long run I will switch to using thread
			 * pools probably FluidManager.PRIORITY.run();
			 * 
			 * FluidManager.TWorker.quota = tickQuota;
			 * FluidManager.TWorker.myStartTime = tickCounter();
			 * FluidManager.TWorker.worlds =
			 * MinecraftServer.getServer().worldServers.clone();
			 * FluidManager.TRIVIAL.run();
			 */
		}

		// Set blocks for a little bit on the server thread
		// This is triggered from using the setBlock call WITHOUT Immediacy
		// NOTE: This is 100% utterly thread safe.
		int toPerform = BlockTask.blockTasks.size() / 16;
		toPerform = toPerform > 32 ? 32 : toPerform;

		// Prevent lagging the system by allocating a max amount of time
		while (System.currentTimeMillis() - this.lastTime < 10 && BlockTask.blockTasks.size() > 0)
			for (int i = 0; i < Math.min(toPerform, BlockTask.blockTasks.size()); i++)
				BlockTask.blockTasks.remove().set();
	}

}
