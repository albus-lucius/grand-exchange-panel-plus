package com.grandexchangepanelplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("grandexchangepanelplus")
public interface GrandExchangePanelPlusConfig extends Config
{
	@ConfigItem(
		keyName = "showSlotStatus",
		name = "Show Slot Status",
		description = "Display offer status text on Grand Exchange slots"
	)
	default boolean showSlotStatus()
	{
		return true;
	}
}
