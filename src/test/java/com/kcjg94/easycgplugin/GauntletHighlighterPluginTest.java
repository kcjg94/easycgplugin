package com.kcjg94.easycgplugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GauntletHighlighterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GauntletHighlighterPlugin.class);
		RuneLite.main(args);
	}
}
