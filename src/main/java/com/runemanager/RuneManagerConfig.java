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
}
