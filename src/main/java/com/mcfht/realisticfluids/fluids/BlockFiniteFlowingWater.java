package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import com.mcfht.realisticfluids.RealisticFluids;

import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import cofh.asmhooks.block.BlockTickingWater;

/**
 * Different classes are needed for the block registry aliasing to work.
 * This block replaces itself with a BlockFiniteFluid as soon as possible.
 * 
 * @author Canisaur
 */

public class BlockFiniteFlowingWater extends BlockTickingWater {
	
	/** Tendency of this liquid to flow */
	public final int	viscosity;
	/** Rate at which this liquid flows */
	public int			flowRate;
	/** Amount of fluid needed to break things */
	public final int	flowBreak	= RealisticFluids.MAX_FLUID >> 3;

	public BlockFiniteFlowingWater(Material mat) {
		super(mat);
		viscosity = RealisticFluids.waterVisc;
		flowRate = RealisticFluids.WATER_UPDATE;
	}
	
	@Override
	public void updateTick(final World w, final int x, final int y, final int z, final Random rand)
	{
		RealisticFluids.setBlock(w, x, y, z, new BlockFiniteFluid(super.blockMaterial, viscosity, flowRate), 0, 3);
	}

}
