package com.mcfht.realisticfluids;

import java.util.ArrayList;

import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
public class FluidModInfo {

    public static final String MODID = "realisticfluids";
    public static final String VERSION = "0.2";
    public static final String AUTHOR = "FHT";
	
	public static void get(ModMetadata meta)
	{
		meta.modId = MODID;
		meta.name = MODID;
		meta.version = VERSION;
		meta.credits = AUTHOR;
		meta.authorList.add(AUTHOR);
		meta.description= "";
		meta.url = "";
		meta.updateUrl= "";
		meta.screenshots= new String[0];
		meta.logoFile = "";
		meta.dependants = new ArrayList<ArtifactVersion>(){{
			add(new DefaultArtifactVersion("COFHCore", true));
			add(new DefaultArtifactVersion("CodeChickenCore", true));
			}};
	}
	
}
