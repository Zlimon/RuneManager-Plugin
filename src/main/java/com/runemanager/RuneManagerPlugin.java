package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
	private UserController userController;

	@Inject
	private AccountController accountController;

	@Inject
	private Controller controller;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private RuneManagerConfig runeManagerConfig;

	@Inject
	private WorldService worldService;

	@Inject
	private ItemManager itemManager;


	private AvailableCollections[] collections = null;
	private boolean onNormalWorld = true;
	private boolean collectionLogOpen;
	private boolean levelUp;
	private boolean wintertodtLootWidget;
	private boolean bankOpen;
	private boolean questLogOpen;
	private static final Pattern UNIQUES_OBTAINED_PATTERN = Pattern.compile("Obtained: <col=(.+?)>([0-9]+)/([0-9]+)</col>");
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile("(.+?): <col=(.+?)>([0-9]+)</col>");
	private static final Pattern ITEM_NAME_PATTERN = Pattern.compile("<col=(.+?)>(.+?)</col>");
	private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");
	private static final Pattern WINTERTODT_LOOT_PATTERN = Pattern.compile("You have earned: (.+?).");
	private int previousCollectionLogValue;
	private JsonArray oldBank = new JsonArray();
	private JsonArray newBank = new JsonArray();

	public String userToken = "";
	public boolean userLoggedIn = false;

	public boolean ifUserLoggedIn()
	{
		return !userToken.isEmpty() && userLoggedIn;
	}

	public String accountUsername = "";
	public boolean accountLoggedIn = false;

	public boolean ifAccountLoggedIn()
	{
		return !accountUsername.isEmpty() && accountLoggedIn;
	}

	public boolean ifUserAndAccountLoggedIn()
	{
		return ifUserLoggedIn() && ifAccountLoggedIn();
	}

	@Override
	protected void startUp()
	{
		userController.logInUser();

		chatCommandManager.registerCommandAsync(AccountController.AUTH_COMMAND_STRING, accountController::authenticatePlayer);

		if (collections == null && !Strings.isNullOrEmpty(runeManagerConfig.url()))
		{
			collections = controller.getBossOverview();
		}
	}

	@Override
	protected void shutDown()
	{
		userController.logOutUser();

		chatCommandManager.unregisterCommand(AccountController.AUTH_COMMAND_STRING);

		sendChatMessage("Successfully logged out of RuneManager.");

		System.out.println("Successfully logged out");
	}

	@Provides
	RuneManagerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneManagerConfig.class);
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
//		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
//		{
//			onNormalWorld = isNormalWorld(client.getWorld());
//		}

		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			accountController.logOutAccount();
		}
	}

	private boolean isNormalWorld(int worldNumber)
	{
		WorldResult worlds = worldService.getWorlds();
		if (worlds == null)
		{
			return false;
		}

		World world = worlds.findWorld(worldNumber);

		if (world != null)
		{
			if (world.getTypes().contains(WorldType.DEADMAN))
			{
				return false;
			}
			else if (world.getTypes().contains(WorldType.DEADMAN_TOURNAMENT))
			{
				return false;
			}
			else if (world.getTypes().contains(WorldType.LAST_MAN_STANDING))
			{
				return false;
			}
			else if (world.getTypes().contains(WorldType.LEAGUE))
			{
				return false;
			}
			else if (world.getTypes().contains(WorldType.TOURNAMENT))
			{
				return false;
			}
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}
	}

	/**
	 * Checks if killed NPC is available for loot tracking, and creates a loot stack ready for submission
	 *
	 * @param npcLootReceived Loot received from NPC
	 */
	@Subscribe
	private void onNpcLootReceived(final NpcLootReceived npcLootReceived) throws IOException
	{
		final NPC npc = npcLootReceived.getNpc();

		// Find if NPC is available for loot tracking
		for (AvailableCollections availableCollections : collections)
		{
			if (availableCollections.getName().equals(npc.getName().toLowerCase()))
			{
				if (!ifUserAndAccountLoggedIn())
				{
					sendChatMessage("You have to be logged in to submit to RuneManager");

					return;
				}

//				if (!onNormalWorld)
//				{
//					sendChatMessage("You have to be logged in to a normal world to submit to RuneManager");
//
//					return;
//				}

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
		LinkedHashMap<String, String> loot = new LinkedHashMap<String, String>();

		final LootItem[] entries = buildEntries(stack(items));

		// Rename item names
		for (LootItem item : entries)
		{
			String itemName = item.getName();
			String itemQuantity = Integer.toString(item.getQuantity());

			itemName = itemName.replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase();

			loot.put(itemName, itemQuantity);
		}

		sendChatMessage(controller.postLootStack(accountUsername, collectionName, loot));
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
		if (ifUserAndAccountLoggedIn() && event.getGroupId() == 84)
		{
			getCurrentEquipment();
		}

		if (ifUserAndAccountLoggedIn())
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
				case 229:
				{
					wintertodtLootWidget = true;
					return;
				}
				case BANK_GROUP_ID:
				{
					bankOpen = true;
					return;
				}
				case QUESTLIST_GROUP_ID:
				{
					questLogOpen = true;
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
		if (accountUsername.isEmpty())
		{
			accountUsername = client.getLocalPlayer().getName();

			accountController.logInAccount();

			if (accountLoggedIn)
			{
				sendChatMessage("You are now using RuneManager with " + accountUsername);
			}
			else
			{
				sendChatMessage("Could not verify this account to your RuneManager user");
			}
		}
		else if (!accountUsername.equals(client.getLocalPlayer().getName()))
		{
			sendChatMessage("Detected another account! Relogging this account to RuneManager...");

			accountController.logOutAccount();

			accountUsername = client.getLocalPlayer().getName();

			accountController.logInAccount();

			if (accountLoggedIn)
			{
				sendChatMessage("You are now using RuneManager with " + accountUsername);
			}
			else
			{
				sendChatMessage("Could not verify this account to your RuneManager user");
			}
		}

		if (!ifUserAndAccountLoggedIn())
		{
			return;
		}

		if (wintertodtLootWidget)
		{
			wintertodtLootWidget = false;

			LinkedHashMap<String, String> wintertodtLootData = new LinkedHashMap<String, String>();

			final Widget wintertodtLootWidget = client.getWidget(229, 1);
			if (wintertodtLootWidget == null)
			{
				return;
			}

			wintertodtLootData = parseWintertodtLootWidget(wintertodtLootWidget.getText());

			if (!wintertodtLootData.isEmpty())
			{
				sendChatMessage(controller.postLootCrate(accountUsername, wintertodtLootData));
			}
		}

		if (levelUp)
		{
			levelUp = false;

			HashMap<String, String> levelUpData = new HashMap<String, String>();

			// If level up, parse level up data from level up widget
			if (client.getWidget(WidgetInfo.LEVEL_UP_LEVEL) != null)
			{
				levelUpData = parseLevelUpWidget(WidgetInfo.LEVEL_UP_LEVEL);
			}

			// Submit level up data
			if (!levelUpData.isEmpty())
			{
				sendChatMessage(controller.postLevelUp(accountUsername, levelUpData));
			}
		}

		if (bankOpen)
		{
			bankOpen = false;

			if (oldBank == newBank)
			{
				newBank = new JsonArray();

				return;
			}

			sendChatMessage(controller.postBank(accountUsername, newBank));

			oldBank = newBank;
		}

		if (questLogOpen)
		{
			questLogOpen = false;

			getQuestJournal();
		}
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

		levelUpData.put("name", m.group(1).toLowerCase());
		levelUpData.put("level", m.group(2));
		//levelUpData.put("xp", "123");
		return levelUpData;
	}

	private LinkedHashMap<String, String> parseWintertodtLootWidget(String wintertodtLoot)
	{
		Matcher m = WINTERTODT_LOOT_PATTERN.matcher(wintertodtLoot);
		if (!m.matches())
		{
			return null;
		}

		String[] items = m.group(1).replace("<br>", " ").split(", ");

		LinkedHashMap<String, String> wintertodtLootData = new LinkedHashMap<>();

		wintertodtLootData.put("icon_id", "20703");
		wintertodtLootData.put("crate_type", "Wintertodt supply crate");
		wintertodtLootData.put("total_value", "0");

		boolean unique = false;

		String[] uniques = new String[]{"tome_of_fire_(empty)", "burnt_page", "pyromancer_garb", "pyromancer_hood", "pyromancer_robe", "pyromancer_boots", "warm_gloves", "bruma_torch", "dragon_axe"};
		List<String> uniqueslist = Arrays.asList(uniques);

		for (String item : items)
		{
			String[] itemAndQuantity = item.split(" x ");

			String itemName = itemAndQuantity[0].replaceFirst(" ", "").replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase();

			if (uniqueslist.contains(itemName))
			{
				unique = true;
			}

			wintertodtLootData.put(itemName, itemAndQuantity[1]);
		}

		if (unique)
		{
			controller.postLootStack(accountUsername, "wintertodt", wintertodtLootData);
		}

		return wintertodtLootData;
	}

	@Subscribe
	private void onVarbitChanged(VarbitChanged event)
	{
		if (!ifUserAndAccountLoggedIn() || !collectionLogOpen)
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

		sendChatMessage(controller.postCollectionLog(accountUsername, collectionName, items));
	}

	private void getCurrentEquipment()
	{
		clientThread.invokeLater(() ->
		{
			final Widget head = client.getWidget(84, 10);
			final Widget[] headData = head.getDynamicChildren();
			final Widget cape = client.getWidget(84, 11);
			final Widget[] capeData = cape.getDynamicChildren();
			final Widget amulet = client.getWidget(84, 12);
			final Widget[] amuletData = amulet.getDynamicChildren();
			final Widget weapon = client.getWidget(84, 13);
			final Widget[] weaponData = weapon.getDynamicChildren();
			final Widget platebody = client.getWidget(84, 14);
			final Widget[] platebodyData = platebody.getDynamicChildren();
			final Widget shield = client.getWidget(84, 15);
			final Widget[] shieldData = shield.getDynamicChildren();
			final Widget platelegs = client.getWidget(84, 16);
			final Widget[] platelegsData = platelegs.getDynamicChildren();
			final Widget gloves = client.getWidget(84, 17);
			final Widget[] glovesData = gloves.getDynamicChildren();
			final Widget footwear = client.getWidget(84, 18);
			final Widget[] footwearData = footwear.getDynamicChildren();
			final Widget ring = client.getWidget(84, 19);
			final Widget[] ringData = ring.getDynamicChildren();
			final Widget ammunition = client.getWidget(84, 20);
			final Widget[] ammunitionData = ammunition.getDynamicChildren(); // 1 - ItemId & ItemQuantity

			JsonArray equipment = new JsonArray();

			JsonObject headObject = new JsonObject();
			headObject.addProperty("id", Integer.toString(headData[1].getItemId()));
			headObject.addProperty("name", head.getName());
			headObject.addProperty("quantity", Integer.toString(headData[1].getItemQuantity()));

			equipment.add(headObject);

			JsonObject capeObject = new JsonObject();
			capeObject.addProperty("id", Integer.toString(capeData[1].getItemId()));
			capeObject.addProperty("name", cape.getName());
			capeObject.addProperty("quantity", Integer.toString(capeData[1].getItemQuantity()));

			equipment.add(capeObject);

			JsonObject amuletObject = new JsonObject();
			amuletObject.addProperty("id", Integer.toString(amuletData[1].getItemId()));
			amuletObject.addProperty("name", amulet.getName());
			amuletObject.addProperty("quantity", Integer.toString(amuletData[1].getItemQuantity()));

			equipment.add(amuletObject);

			JsonObject weaponObject = new JsonObject();
			weaponObject.addProperty("id", Integer.toString(weaponData[1].getItemId()));
			weaponObject.addProperty("name", weapon.getName());
			weaponObject.addProperty("quantity", Integer.toString(weaponData[1].getItemQuantity()));

			equipment.add(weaponObject);

			JsonObject platebodyObject = new JsonObject();
			platebodyObject.addProperty("id", Integer.toString(platebodyData[1].getItemId()));
			platebodyObject.addProperty("name", platebody.getName());
			platebodyObject.addProperty("quantity", Integer.toString(platebodyData[1].getItemQuantity()));

			equipment.add(platebodyObject);

			JsonObject shieldObject = new JsonObject();
			shieldObject.addProperty("id", Integer.toString(shieldData[1].getItemId()));
			shieldObject.addProperty("name", shield.getName());
			shieldObject.addProperty("quantity", Integer.toString(shieldData[1].getItemQuantity()));

			equipment.add(shieldObject);

			JsonObject platelegsObject = new JsonObject();
			platelegsObject.addProperty("id", Integer.toString(platelegsData[1].getItemId()));
			platelegsObject.addProperty("name", platelegs.getName());
			platelegsObject.addProperty("quantity", Integer.toString(platelegsData[1].getItemQuantity()));

			equipment.add(platelegsObject);

			JsonObject glovesObject = new JsonObject();
			glovesObject.addProperty("id", Integer.toString(glovesData[1].getItemId()));
			glovesObject.addProperty("name", gloves.getName());
			glovesObject.addProperty("quantity", Integer.toString(glovesData[1].getItemQuantity()));

			equipment.add(glovesObject);

			JsonObject footwearObject = new JsonObject();
			footwearObject.addProperty("id", Integer.toString(footwearData[1].getItemId()));
			footwearObject.addProperty("name", footwear.getName());
			footwearObject.addProperty("quantity", Integer.toString(footwearData[1].getItemQuantity()));

			equipment.add(footwearObject);

			JsonObject ringObject = new JsonObject();
			ringObject.addProperty("id", Integer.toString(ringData[1].getItemId()));
			ringObject.addProperty("name", ring.getName());
			ringObject.addProperty("quantity", Integer.toString(ringData[1].getItemQuantity()));

			equipment.add(ringObject);

			JsonObject ammunitionObject = new JsonObject();
			ammunitionObject.addProperty("id", Integer.toString(ammunitionData[1].getItemId()));
			ammunitionObject.addProperty("name", ammunition.getName());
			ammunitionObject.addProperty("quantity", Integer.toString(ammunitionData[1].getItemQuantity()));

			equipment.add(ammunitionObject);

			sendChatMessage(controller.postEquipment(accountUsername, equipment));
		});
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD && newBank.size() == 0)
		{
			// Compute bank prices using only the shown items so that we can show bank value during searches
			final Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
			final ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
			final Widget[] children = bankItemContainer.getChildren();

			if (bankContainer != null && children != null)
			{
				log.debug("Computing bank price of {} items", bankContainer.size());

				// The first components are the bank items, followed by tabs etc. There are always 816 components regardless
				// of bank size, but we only need to check up to the bank size.
				for (int i = 0; i < bankContainer.size(); ++i)
				{
					Widget child = children[i];
					if (child != null && !child.isSelfHidden() && child.getItemId() > -1)
					{
						JsonObject itemObject = new JsonObject();
						itemObject.addProperty("id", child.getItemId());
						itemObject.addProperty("quantity", child.getItemQuantity());

						Matcher itemNameMatcher = ITEM_NAME_PATTERN.matcher(child.getName());
						if (itemNameMatcher.find())
						{
							itemObject.addProperty("name", itemNameMatcher.group(2));
						}

						oldBank.add(itemObject);
						newBank.add(itemObject);
					}
				}
			}
		}
	}

	private void getQuestJournal() {
		final Widget allQuests = client.getWidget(QUESTLIST_GROUP_ID, 5); // Right widget loot panel
		if (allQuests == null)
		{
			return;
		}

		final Widget[] allQuestsChildren = allQuests.getStaticChildren();
		if (allQuestsChildren == null)
		{
			return;
		}

		JsonArray questCategory = new JsonArray();

		for (Widget questChild : allQuestsChildren)
		{
			final Widget questWidget = client.getWidget(questChild.getId()); // Right widget loot panel
			if (questWidget == null)
			{
				return;
			}

			final Widget[] questChildChildren = questWidget.getDynamicChildren();
			if (questChildChildren == null)
			{
				return;
			}

			JsonArray quests = new JsonArray();

			for (Widget quest : questChildChildren)
			{
				JsonObject questObject = new JsonObject();
				questObject.addProperty("quest", quest.getText());

				Integer statusCode = quest.getTextColor();

				String status = "";

				if (statusCode.equals(901389))
				{
					status = "completed";
				} else if (statusCode.equals(16776960))
				{
					status = "in_progress";
				} else if (statusCode.equals(16711680))
				{
					status = "not_started";
				}

				questObject.addProperty("status", status);

				quests.add(questObject);
			}

			questCategory.add(quests);
		}

		sendChatMessage(controller.postQuests(accountUsername, questCategory));
	}

	public void sendChatMessage(String chatMessage)
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
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
		});
	}
}