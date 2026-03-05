package com.grandexchangepanelplus;

import com.devtools.DevToolsPlugin;
import com.devtools.WidgetSequenceRecorder;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GrandExchangePanelPlusPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// Point logback to dev-tools project root before RuneLite initializes logging
		System.setProperty("devtools.root", WidgetSequenceRecorder.getProjectRoot().getAbsolutePath());

		ExternalPluginManager.loadBuiltin(
			GrandExchangePanelPlusPlugin.class,
			DevToolsPlugin.class
		);
		RuneLite.main(args);
	}
}
