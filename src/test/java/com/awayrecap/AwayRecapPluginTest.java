package com.awayrecap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AwayRecapPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AwayRecapPlugin.class);
		RuneLite.main(args);
	}
}
