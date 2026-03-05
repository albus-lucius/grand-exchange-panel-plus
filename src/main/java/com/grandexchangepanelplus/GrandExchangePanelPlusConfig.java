package com.grandexchangepanelplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("grandexchangepanelplus")
public interface GrandExchangePanelPlusConfig extends Config
{
	@ConfigSection(
		name = "Collect",
		description = "Collect button on progress bars",
		position = 0
	)
	String collectSection = "collect";

	@ConfigItem(
		keyName = "collectDisplay",
		name = "Show",
		description = "Show the Collect button on progress bars",
		section = "collect",
		position = 0
	)
	default boolean collectDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collectEnable",
		name = "Click",
		description = "Allow clicking progress bars to collect items",
		section = "collect",
		position = 1
	)
	default boolean collectEnable()
	{
		return true;
	}

	@ConfigSection(
		name = "Modify",
		description = "Modify button on offer title bars",
		position = 1
	)
	String modifySection = "modify";

	@ConfigItem(
		keyName = "modifyDisplay",
		name = "Show",
		description = "Show the Modify button on title bars",
		section = "modify",
		position = 0
	)
	default boolean modifyDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "modifyEnable",
		name = "Click",
		description = "Allow clicking the Modify button",
		section = "modify",
		position = 1
	)
	default boolean modifyEnable()
	{
		return true;
	}

	@ConfigSection(
		name = "Abort",
		description = "Abort button on offer title bars",
		position = 2
	)
	String abortSection = "abort";

	@ConfigItem(
		keyName = "abortDisplay",
		name = "Show",
		description = "Show the Abort button on title bars",
		section = "abort",
		position = 0
	)
	default boolean abortDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "abortEnable",
		name = "Click",
		description = "Allow clicking the Abort button",
		section = "abort",
		position = 1
	)
	default boolean abortEnable()
	{
		return true;
	}
}
