package com.runemanager;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("runemanager")
public interface RuneManagerConfig extends Config
{
	@ConfigItem(
			keyName = "url",
			name = "URL",
			description = "RuneManager URL",
			position = 0
	)
	default String url() { return ""; };

	@ConfigItem(
			keyName = "username",
			name = "Username",
			description = "RuneManager Username",
			position = 1
	)
	default String username() { return ""; };

	@ConfigItem(
			keyName = "password",
			name = "Password",
			description = "RuneManager Password",
			secret = true,
			position = 2
	)
	default String password() { return ""; };






	@ConfigItem(
		keyName = "saveLoot",
		name = "Submit loot tracker data",
		description = "Submit loot tracker data"
	)
	default boolean saveLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ignoredEvents",
		name = "",
		description = ""
	)
	void setIgnoredEvents(String key);

	@ConfigItem(
		keyName = "npcKillChatMessage",
		name = "Show chat message for NPC kills",
		description = "Adds a chat message with monster name and kill value when receiving loot from an NPC kill."
	)
	default boolean npcKillChatMessage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pvpKillChatMessage",
		name = "Show chat message for PVP kills",
		description = "Adds a chat message with player name and kill value when receiving loot from a player kill."
	)
	default boolean pvpKillChatMessage()
	{
		return false;
	}

	@ConfigItem(
			keyName = "submitBank",
			name = "Submit bank data",
			description = "Submit data from bank."
	)
	default boolean submitBank()
	{
		return false;
	}
}
