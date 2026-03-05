package com.grandexchangepanelplus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GrandExchangePanelPlusPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GrandExchangePanelPlusPlugin.class);
		RuneLite.main(args);
	}
}
