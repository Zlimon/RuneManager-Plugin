package com.runemanager;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import okhttp3.*;

import javax.inject.Inject;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.widgets.WidgetID.*;

@PluginDescriptor(
	name = "1RuneManager",
	description = "Official RuneManager plugin"
)

@Slf4j
public class RuneManagerPlugin extends Plugin
{
	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private RuneManagerConfig runeManagerConfig;

	@Inject
	private UserController userController;

	@Inject
	private Controller controller;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private WorldService worldService;

	private boolean onLeagueWorld;
	private boolean loggedIn = false;
	private static final String AUTH_COMMAND_STRING = "!auth";
	private AvailableCollections[] collections = null;
	private boolean collectionLogOpen;
	private int previousCollectionLogValue;
	private boolean levelUp;
	private static final Pattern UNIQUES_OBTAINED_PATTERN = Pattern.compile("Obtained: <col=(.+?)>([0-9]+)/([0-9]+)</col>");
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile("(.+?): <col=(.+?)>([0-9]+)</col>");
	private static final Pattern ITEM_NAME_PATTERN = Pattern.compile("<col=(.+?)>(.+?)</col>");
	private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");

	@Override
	protected void startUp()
	{
		loggedIn = userController.logIn();

		chatCommandManager.registerCommandAsync(AUTH_COMMAND_STRING, this::authenticatePlayer);

		if (collections == null && !Strings.isNullOrEmpty(runeManagerConfig.url()))
		{
			collections = controller.getBossOverview();
		}

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				if (loggedIn)
				{
					sendChatMessage("You are logged in to RuneManager");
				}
				else
				{
					sendChatMessage("You are NOT logged in to RuneManager");
				}
			}
		});
	}

	@Override
	protected void shutDown()
	{
		userController.authToken = null;

		chatCommandManager.unregisterCommand(AUTH_COMMAND_STRING);

		System.out.println("Successfully logged out");
	}

	@Provides
	RuneManagerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneManagerConfig.class);
	}

	private void authenticatePlayer(ChatMessage chatMessage, String message)
	{
		if (!loggedIn)
		{
			sendChatMessage("You have to be logged in to authenticate this account with RuneManager");

			return;
		}

		System.out.println(userController.authToken);

		String authCode = message.substring(AUTH_COMMAND_STRING.length() + 1);
		String accountType = client.getAccountType().name().toLowerCase();

		RequestBody formBody = new FormBody.Builder()
			.add("username", client.getLocalPlayer().getName())
			.add("code", authCode)
			.add("account_type", accountType)
			.build();

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/authenticate")
			.addHeader("Authorization", "Bearer " + userController.authToken)
			.post(formBody)
			.build();

		sendChatMessage("Attempting to authenticate account " + client.getLocalPlayer().getName() + " with user " + runeManagerConfig.username() + " to RuneManager");

		OkHttpClient httpClient = new OkHttpClient();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Unexpected code " + response);
			}

			sendChatMessage(response.body().string());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			onLeagueWorld = isLeagueWorld(client.getWorld());
		}
	}

	private boolean isLeagueWorld(int worldNumber)
	{
		WorldResult worlds = worldService.getWorlds();
		if (worlds == null)
		{
			return false;
		}

		World world = worlds.findWorld(worldNumber);
		return world != null && world.getTypes().contains(WorldType.LEAGUE);
	}

	/**
	 * Checks if killed NPC is available for loot tracking, and creates a loot stack ready for submission
	 *
	 * @param npcLootReceived Loot received from NPC
	 */
	@Subscribe
	private void onNpcLootReceived(final NpcLootReceived npcLootReceived) throws IOException
	{
		if (!loggedIn)
		{
			sendChatMessage("You have to be logged in to submit to RuneManager");

			return;
		}

		if (onLeagueWorld)
		{
			sendChatMessage("You have to be logged in to a normal world to submit to RuneManager");

			return;
		}

		final NPC npc = npcLootReceived.getNpc();

		// Find if NPC is available for loot tracking
		for (AvailableCollections availableCollections : collections)
		{
			if (availableCollections.getName().equals(npc.getName().toLowerCase()))
			{
				final String name = npc.getName();
				final Collection<ItemStack> items = npcLootReceived.getItems(); // Received items

				createLootStack(name, items);

				break;
			}
		}
	}

	/**
	 * Loops through loot stack and renames item names compliant with RuneManager API, and then submits loot to RuneManager
	 *
	 * @param collectionName NPC name
	 * @param items          Loot received from NPC
	 */
	private void createLootStack(String collectionName, final Collection<ItemStack> items) throws IOException
	{
		// itemName, itemQuantity
		LinkedHashMap<String, Integer> loot = new LinkedHashMap<String, Integer>();

		final LootItem[] entries = buildEntries(stack(items));

		// Rename item names
		for (LootItem item : entries)
		{
			String itemName = item.getName();
			int itemQuantity = item.getQuantity();

			itemName = itemName.replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase();

			loot.put(itemName, itemQuantity);
		}

		sendChatMessage(controller.postLootStack(client.getLocalPlayer().getName(), collectionName, loot, userController.authToken));
	}

	private LootItem[] buildEntries(final Collection<ItemStack> itemStacks)
	{
		return itemStacks.stream()
			.map(itemStack -> buildLootItem(itemStack.getId(), itemStack.getQuantity()))
			.toArray(LootItem[]::new);
	}

	private LootItem buildLootItem(int itemId, int quantity)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);

		return new LootItem(
			itemId,
			itemComposition.getName(),
			quantity);
	}

	private static Collection<ItemStack> stack(Collection<ItemStack> items)
	{
		final List<ItemStack> list = new ArrayList<>();

		for (final ItemStack item : items)
		{
			int quantity = 0;
			for (final ItemStack i : list)
			{
				if (i.getId() == item.getId())
				{
					quantity = i.getQuantity();
					list.remove(i);
					break;
				}
			}
			if (quantity > 0)
			{
				list.add(new ItemStack(item.getId(), item.getQuantity() + quantity, item.getLocation()));
			}
			else
			{
				list.add(item);
			}
		}

		return list;
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{
		if (loggedIn && !onLeagueWorld)
		{
			int groupId = event.getGroupId();

			switch (groupId)
			{
				case 621:
				{
					collectionLogOpen = true;
					return;
				}
				case LEVEL_UP_GROUP_ID:
				{
					levelUp = true;
					return;
				}
				default:
					return;
			}
		}
	}

	@Subscribe
	private void onGameTick(GameTick event) throws IOException
	{
		if (!levelUp)
		{
			return;
		}

		levelUp = false;

		HashMap<String, String> levelUpData = new HashMap<String, String>();

		// If level up, parse level up data from level up widget
		if (client.getWidget(WidgetInfo.LEVEL_UP_LEVEL) != null)
		{
			System.out.println("test");
			levelUpData = parseLevelUpWidget(WidgetInfo.LEVEL_UP_LEVEL);
			//controller.postLevelUp(client.getLocalPlayer().getName(), skill, userController.authToken);
		}

		// Submit level up data
		if (!levelUpData.isEmpty())
		{
			System.out.println(levelUpData.get("level"));
			sendChatMessage(controller.postLevelUp(client.getLocalPlayer().getName(), levelUpData, userController.authToken));
		}
		//controller.postLevelUp(levelUpData, client.getLocalPlayer().getName(), userController.authToken);
	}

	private HashMap<String, String> parseLevelUpWidget(WidgetInfo levelUpLevel)
	{
		Widget levelChild = client.getWidget(levelUpLevel);
		if (levelChild == null)
		{
			return null;
		}

		Matcher m = LEVEL_UP_PATTERN.matcher(levelChild.getText());
		if (!m.matches())
		{
			return null;
		}

		HashMap<String, String> levelUpData = new HashMap<String, String>();

		System.out.println(m.group(1).toLowerCase());

		levelUpData.put("name", m.group(1).toLowerCase());
		levelUpData.put("level", m.group(2));
		//levelUpData.put("xp", "123");
		return levelUpData;
	}

	@Subscribe
	private void onVarbitChanged(VarbitChanged event)
	{
		if (!collectionLogOpen)
		{
			return;
		}

		int collectionLogValue = client.getVarbitValue(6906);
		if (collectionLogValue != previousCollectionLogValue)
		{
			getCurrentCollectionLogHeaderData();

			previousCollectionLogValue = collectionLogValue;
		}
	}

	private void getCurrentCollectionLogHeaderData()
	{
		clientThread.invokeLater(() ->
		{
			final Widget collectionLogHeader = client.getWidget(621, 19); // Right widget header panel
			if (collectionLogHeader == null)
			{
				return;
			}

			final Widget[] header = collectionLogHeader.getDynamicChildren(); // 0 - Collection name, 1 - Uniques obtained, 2 - Killcount
			if (header == null)
			{
				return;
			}

			String collectionName = header[0].getText().replaceAll("[+.^:,']", "");

			int uniquesObtained = 0;
			Matcher uniquesObtainedMatcher = UNIQUES_OBTAINED_PATTERN.matcher(header[1].getText());
			if (uniquesObtainedMatcher.find())
			{
				uniquesObtained = Integer.parseInt(uniquesObtainedMatcher.group(2));
			}

			int killCount = 0;
			Matcher killCountMatcher = KILL_COUNT_PATTERN.matcher(header[2].getText());
			if (killCountMatcher.find())
			{
				killCount = Integer.parseInt(killCountMatcher.group(3));
			}

			try
			{
				getCollectionLogContentData(collectionName, uniquesObtained, killCount);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		});
	}

	private void getCollectionLogContentData(String collectionName, int uniquesObtained, int killCount) throws IOException
	{
		//final Widget collectionLog = client.getWidget(WidgetInfo.COLLECTION_LOG_LOOT);
		final Widget collectionLog = client.getWidget(621, 35); // Right widget loot panel
		if (collectionLog == null)
		{
			return;
		}

		final Widget[] log = collectionLog.getDynamicChildren();
		if (log == null)
		{
			return;
		}

		LinkedHashMap<String, Integer> items = new LinkedHashMap<String, Integer>();

		items.put("obtained", uniquesObtained);
		items.put("kill_count", killCount);

		for (Widget item : log)
		{
			String itemName = "";
			int itemQuantity = 0;

			Matcher itemNameMatcher = ITEM_NAME_PATTERN.matcher(item.getName());
			if (itemNameMatcher.find())
			{
				itemName = itemNameMatcher.group(2);
			}

			itemName = itemName.replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase();

			if (item.getOpacity() == 0)
			{
				itemQuantity = item.getItemQuantity();
			}

			items.put(itemName, itemQuantity);
		}

		sendChatMessage(controller.postCollectionLog(client.getLocalPlayer().getName(), collectionName, items, userController.authToken));
	}

	private void sendChatMessage(String chatMessage)
	{
		final String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(chatMessage)
			.build();

		chatMessageManager.queue(
			QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message)
				.build());
	}
}
