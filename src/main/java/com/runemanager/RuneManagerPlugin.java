/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.runemanager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.http.api.loottracker.GameItem;
import net.runelite.http.api.loottracker.LootAggregate;
import net.runelite.http.api.loottracker.LootRecord;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.http.api.loottracker.LootTrackerClient;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "1RuneManager",
	description = "Official RuneManager plugin"
)
@Slf4j
public class RuneManagerPlugin extends Plugin
{
	// Activity/Event loot handling
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed [0-9]+ ([a-z]+) Treasure Trails?\\.");
	private static final int THEATRE_OF_BLOOD_REGION = 12867;

	// Hespori loot handling
	private static final String HESPORI_LOOTED_MESSAGE = "You have successfully cleared this patch for new crops.";
	private static final String HESPORI_EVENT = "Hespori";
	private static final int HESPORI_REGION = 5021;

	// Chest loot handling
	private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
	private static final Pattern LARRAN_LOOTED_PATTERN = Pattern.compile("You have opened Larran's (big|small) chest .*");
	// Used by Stone Chest, Isle of Souls chest, Dark Chest
	private static final String OTHER_CHEST_LOOTED_MESSAGE = "You steal some loot from the chest.";
	private static final String DORGESH_KAAN_CHEST_LOOTED_MESSAGE = "You find treasure inside!";
	private static final String GRUBBY_CHEST_LOOTED_MESSAGE = "You have opened the Grubby Chest";
	private static final Pattern HAM_CHEST_LOOTED_PATTERN = Pattern.compile("Your (?<key>[a-z]+) key breaks in the lock.*");
	private static final int HAM_STOREROOM_REGION = 10321;
	private static final Map<Integer, String> CHEST_EVENT_TYPES = new ImmutableMap.Builder<Integer, String>().
		put(5179, "Brimstone Chest").
		put(11573, "Crystal Chest").
		put(12093, "Larran's big chest").
		put(12127, "The Gauntlet").
		put(13113, "Larran's small chest").
		put(13151, "Elven Crystal Chest").
		put(5277, "Stone chest").
		put(10835, "Dorgesh-Kaan Chest").
		put(10834, "Dorgesh-Kaan Chest").
		put(7323, "Grubby Chest").
		put(8593, "Isle of Souls Chest").
		put(7827, "Dark Chest").
		build();

	// Shade chest loot handling
	private static final Pattern SHADE_CHEST_NO_KEY_PATTERN = Pattern.compile("You need a [a-z]+ key with a [a-z]+ trim to open this chest .*");
	private static final Map<Integer, String> SHADE_CHEST_OBJECTS = new ImmutableMap.Builder<Integer, String>().
		put(ObjectID.BRONZE_CHEST, "Bronze key red").
		put(ObjectID.BRONZE_CHEST_4112, "Bronze key brown").
		put(ObjectID.BRONZE_CHEST_4113, "Bronze key crimson").
		put(ObjectID.BRONZE_CHEST_4114, "Bronze key black").
		put(ObjectID.BRONZE_CHEST_4115, "Bronze key purple").
		put(ObjectID.STEEL_CHEST, "Steel key red").
		put(ObjectID.STEEL_CHEST_4117, "Steel key brown").
		put(ObjectID.STEEL_CHEST_4118, "Steel key crimson").
		put(ObjectID.STEEL_CHEST_4119, "Steel key black").
		put(ObjectID.STEEL_CHEST_4120, "Steel key purple").
		put(ObjectID.BLACK_CHEST, "Black key red").
		put(ObjectID.BLACK_CHEST_4122, "Black key brown").
		put(ObjectID.BLACK_CHEST_4123, "Black key crimson").
		put(ObjectID.BLACK_CHEST_4124, "Black key black").
		put(ObjectID.BLACK_CHEST_4125, "Black key purple").
		put(ObjectID.SILVER_CHEST, "Silver key red").
		put(ObjectID.SILVER_CHEST_4127, "Silver key brown").
		put(ObjectID.SILVER_CHEST_4128, "Silver key crimson").
		put(ObjectID.SILVER_CHEST_4129, "Silver key black").
		put(ObjectID.SILVER_CHEST_4130, "Silver key purple").
//		put(ObjectID.GOLD_CHEST, "Gold key red").
//		put(ObjectID.GOLD_CHEST_41213, "Gold key brown").
//		put(ObjectID.GOLD_CHEST_41214, "Gold key crimson").
//		put(ObjectID.GOLD_CHEST_41215, "Gold key black").
//		put(ObjectID.GOLD_CHEST_41216, "Gold key purple").
	build();

	// Hallow Sepulchre Coffin handling
	private static final String COFFIN_LOOTED_MESSAGE = "You push the coffin lid aside.";
	private static final String HALLOWED_SEPULCHRE_COFFIN_EVENT = "Coffin (Hallowed Sepulchre)";
	private static final Set<Integer> HALLOWED_SEPULCHRE_MAP_REGIONS = ImmutableSet.of(8797, 10077, 9308, 10074, 9050); // one map region per floor

	// Last man standing map regions
	private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(13658, 13659, 13914, 13915, 13916);

	private static final String CASKET_EVENT = "Casket";

	// Soul Wars
	private static final String SPOILS_OF_WAR_EVENT = "Spoils of war";
	private static final Set<Integer> SOUL_WARS_REGIONS = ImmutableSet.of(8493, 8749, 9005);

	private static final Set<Character> VOWELS = ImmutableSet.of('a', 'e', 'i', 'o', 'u');

	private final Map<String, List<CollectionLogItem>> obtainedItems = new HashMap<>();

	private static final Pattern ITEM_NAME_PATTERN = Pattern.compile("<col=(.+?)>(.+?)</col>");

	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final int COLLECTION_LOG_CONTAINER = 1;
	private static final int COLLECTION_LOG_CATEGORY_HEAD = 19;
	private static final int COLLECTION_LOG_CATEGORY_ITEMS = 35;
	private static final int COLLECTION_LOG_DRAW_LIST_SCRIPT_ID = 2730;
	private static final int COLLECTION_LOG_CATEGORY_VARBIT_INDEX = 2049;

	private boolean wintertodtLootWidgetOpen;
	private boolean levelUpWidgetOpen;
	private boolean bankWidgetOpen;
	private boolean questLogMenuOpen;

	private static final Pattern WINTERTODT_LOOT_PATTERN = Pattern.compile("You have earned: (.+?).");
	private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");

	private JsonArray oldBank = new JsonArray();
	private JsonArray newBank = new JsonArray();

	@Getter
	public String userToken;
	public boolean ifUserToken() { return (getUserToken() != null && !getUserToken().isEmpty()); }

	@Getter
	public String accountUsername;
	@Getter
	public boolean accountLoggedIn = false;
	public boolean ifAccountLoggedIn() { return ((getAccountUsername() != null || !getAccountUsername().isEmpty()) && accountLoggedIn); }



	@Inject
	private UserController userController;

	@Inject
	private AccountController accountController;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RuneManagerConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private LootManager lootManager;

	@Inject
	private Controller controller;

	@Inject
	private ChatCommandManager chatCommandManager;

	@VisibleForTesting
	String eventType;
	@VisibleForTesting
	LootRecordType lootRecordType;
	private Object metadata;
	private boolean chestLooted;

	private final List<String> ignoredItems = new ArrayList<>();
	private final List<String> ignoredEvents = new ArrayList<>();

	private Multiset<Integer> inventorySnapshot;

	@Getter(AccessLevel.PACKAGE)
	@Inject
	private LootTrackerClient lootTrackerClient;
	private final List<LootRecord> queuedLoots = new ArrayList<>();

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

	@Provides
	LootTrackerClient provideLootTrackerClient(OkHttpClient okHttpClient)
	{
		return new LootTrackerClient(okHttpClient);
	}

	@Provides
	RuneManagerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneManagerConfig.class);
	}

	@Subscribe
	public void onSessionOpen(SessionOpen sessionOpen)
	{
		AccountSession accountSession = sessionManager.getAccountSession();
		if (accountSession.getUuid() != null)
		{
			lootTrackerClient.setUuid(accountSession.getUuid());
		}
		else
		{
			lootTrackerClient.setUuid(null);
		}
	}

	@Subscribe
	public void onSessionClose(SessionClose sessionClose)
	{
//		submitLoot();
		lootTrackerClient.setUuid(null);
	}

	@Override
	protected void startUp() throws Exception
	{
		if (getUserToken() == null)
		{
			userController.logInUser();
		}

		chatCommandManager.registerCommandAsync(AccountController.AUTH_COMMAND_STRING, accountController::authenticatePlayer);

//		ignoredEvents = Text.fromCSV(config.getIgnoredEvents());
	}

	@Override
	protected void shutDown()
	{
		userToken = null;

		chatCommandManager.unregisterCommand(AccountController.AUTH_COMMAND_STRING);

//		submitLoot();

		lootTrackerClient.setUuid(null);
		chestLooted = false;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
//		Future<Void> future = submitLoot();
//		if (future != null)
//		{
//			event.waitFor(future);
//		}
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING && !client.isInInstancedRegion())
		{
			chestLooted = false;
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			accountController.logOutAccount();
		}
	}

	void addLoot(@NonNull String name, int combatLevel, LootRecordType type, Object metadata, Collection<ItemStack> items)
	{
		final LootTrackerItem[] entries = buildEntries(stack(items));

		if (config.saveLoot())
		{
			LootRecord lootRecord = new LootRecord(name, type, entries, toGameItems(items), Instant.now());

			dataSubmittedChatMessage(controller.postLootStack(name, lootRecord));

			synchronized (queuedLoots)
			{
				queuedLoots.add(lootRecord);
			}
		}

		eventBus.post(new LootReceived(name, combatLevel, type, items));
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
	{
		final NPC npc = npcLootReceived.getNpc();
		final Collection<ItemStack> items = npcLootReceived.getItems();
		final String name = npc.getName();
		final int combat = npc.getCombatLevel();

		addLoot(name, combat, LootRecordType.NPC, npc.getId(), items);
	}

	@Subscribe
	public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived)
	{
		// Ignore Last Man Standing and Soul Wars player loots
		if (isPlayerWithinMapRegion(LAST_MAN_STANDING_REGIONS) || isPlayerWithinMapRegion(SOUL_WARS_REGIONS))
		{
			return;
		}

		final Player player = playerLootReceived.getPlayer();
		final Collection<ItemStack> items = playerLootReceived.getItems();
		final String name = player.getName();
		final int combat = player.getCombatLevel();

		addLoot(name, combat, LootRecordType.PLAYER, null, items);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		final ItemContainer container;

		switch (widgetLoaded.getGroupId())
		{
			case (WidgetID.BARROWS_REWARD_GROUP_ID):
				setEvent(LootRecordType.EVENT, "Barrows");
				container = client.getItemContainer(InventoryID.BARROWS_REWARD);
				break;
			case (WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID):
				if (chestLooted)
				{
					return;
				}
				setEvent(LootRecordType.EVENT, "Chambers of Xeric");
				container = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);
				chestLooted = true;
				break;
			case (WidgetID.THEATRE_OF_BLOOD_GROUP_ID):
				if (chestLooted)
				{
					return;
				}
				int region = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
				if (region != THEATRE_OF_BLOOD_REGION)
				{
					return;
				}
				setEvent(LootRecordType.EVENT, "Theatre of Blood");
				container = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);
				chestLooted = true;
				break;
			case (WidgetID.CLUE_SCROLL_REWARD_GROUP_ID):
				// event type should be set via ChatMessage for clue scrolls.
				// Clue Scrolls use same InventoryID as Barrows
				container = client.getItemContainer(InventoryID.BARROWS_REWARD);

				if (eventType == null)
				{
					log.debug("Clue scroll reward interface with no event!");
					return;
				}
				break;
			case (WidgetID.KINGDOM_GROUP_ID):
				setEvent(LootRecordType.EVENT, "Kingdom of Miscellania");
				container = client.getItemContainer(InventoryID.KINGDOM_OF_MISCELLANIA);
				break;
			case (WidgetID.FISHING_TRAWLER_REWARD_GROUP_ID):
				setEvent(LootRecordType.EVENT, "Fishing Trawler", client.getBoostedSkillLevel(Skill.FISHING));
				container = client.getItemContainer(InventoryID.FISHING_TRAWLER_REWARD);
				break;
			case (WidgetID.DRIFT_NET_FISHING_REWARD_GROUP_ID):
				setEvent(LootRecordType.EVENT, "Drift Net", client.getBoostedSkillLevel(Skill.FISHING));
				container = client.getItemContainer(InventoryID.DRIFT_NET_FISHING_REWARD);
				break;
			case (COLLECTION_LOG_GROUP_ID):
				getCategory();
				container = null;
				break;
			case 84:
				getCurrentEquipment();
				container = null;
				break;
			case 229:
			{
				wintertodtLootWidgetOpen = true;
				setEvent(LootRecordType.EVENT, "Wintertodt", client.getBoostedSkillLevel(Skill.FIREMAKING));
				container = null;
				break;
			}
			case (WidgetID.LEVEL_UP_GROUP_ID):
			{
				levelUpWidgetOpen = true;
				container = null;
				break;
			}
			case (WidgetID.BANK_GROUP_ID):
			{
				bankWidgetOpen = true;
				container = null;
				break;
			}
			case (WidgetID.QUESTLIST_GROUP_ID):
			{
				questLogMenuOpen = true;
				container = null;
				break;
			}
			default:
				return;
		}

		if (container == null)
		{
			return;
		}

		// Convert container items to array of ItemStack
		final Collection<ItemStack> items = Arrays.stream(container.getItems())
			.filter(item -> item.getId() > 0)
			.map(item -> new ItemStack(item.getId(), item.getQuantity(), client.getLocalPlayer().getLocalLocation()))
			.collect(Collectors.toList());

		if (items.isEmpty())
		{
			log.debug("No items to find for Event: {} | Container: {}", eventType, container);
			return;
		}

		addLoot(eventType, -1, lootRecordType, metadata, items);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		final String message = event.getMessage();

		if (message.equals(CHEST_LOOTED_MESSAGE) || message.equals(OTHER_CHEST_LOOTED_MESSAGE)
			|| message.equals(DORGESH_KAAN_CHEST_LOOTED_MESSAGE) || message.startsWith(GRUBBY_CHEST_LOOTED_MESSAGE)
			|| LARRAN_LOOTED_PATTERN.matcher(message).matches())
		{
			final int regionID = client.getLocalPlayer().getWorldLocation().getRegionID();
			if (!CHEST_EVENT_TYPES.containsKey(regionID))
			{
				return;
			}

			setEvent(LootRecordType.EVENT, CHEST_EVENT_TYPES.get(regionID));
			takeInventorySnapshot();

			return;
		}

		if (message.equals(COFFIN_LOOTED_MESSAGE) &&
			isPlayerWithinMapRegion(HALLOWED_SEPULCHRE_MAP_REGIONS))
		{
			setEvent(LootRecordType.EVENT, HALLOWED_SEPULCHRE_COFFIN_EVENT);
			takeInventorySnapshot();
			return;
		}

		final int regionID = client.getLocalPlayer().getWorldLocation().getRegionID();
		if (HESPORI_REGION == regionID && message.equals(HESPORI_LOOTED_MESSAGE))
		{
			setEvent(LootRecordType.EVENT, HESPORI_EVENT);
			takeInventorySnapshot();
			return;
		}

		final Matcher hamStoreroomMatcher = HAM_CHEST_LOOTED_PATTERN.matcher(message);
		if (hamStoreroomMatcher.matches() && regionID == HAM_STOREROOM_REGION)
		{
			String keyType = hamStoreroomMatcher.group("key");
			setEvent(LootRecordType.EVENT, String.format("H.A.M. chest (%s)", keyType));
			takeInventorySnapshot();
			return;
		}

		// Check if message is for a clue scroll reward
		final Matcher m = CLUE_SCROLL_PATTERN.matcher(Text.removeTags(message));
		if (m.find())
		{
			final String type = m.group(1).toLowerCase();
			switch (type)
			{
				case "beginner":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Beginner)");
					return;
				case "easy":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Easy)");
					return;
				case "medium":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Medium)");
					return;
				case "hard":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Hard)");
					return;
				case "elite":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Elite)");
					return;
				case "master":
					setEvent(LootRecordType.EVENT, "Clue Scroll (Master)");
					return;
			}
		}

		if (SHADE_CHEST_NO_KEY_PATTERN.matcher(message).matches())
		{
			// Player didn't have the key they needed.
			resetEvent();
			return;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId()
			|| eventType == null)
		{
			return;
		}

		if (CHEST_EVENT_TYPES.containsValue(eventType)
			|| SHADE_CHEST_OBJECTS.containsValue(eventType)
			|| HALLOWED_SEPULCHRE_COFFIN_EVENT.equals(eventType)
			|| HESPORI_EVENT.equals(eventType)
			|| CASKET_EVENT.equals(eventType)
			|| SPOILS_OF_WAR_EVENT.equals(eventType)
			|| eventType.endsWith("Bird House")
			|| eventType.startsWith("H.A.M. chest")
			|| lootRecordType == LootRecordType.PICKPOCKET)
		{
			WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
			Collection<ItemStack> groundItems = lootManager.getItemSpawns(playerLocation);

			processInventoryLoot(eventType, lootRecordType, metadata, event.getItemContainer(), groundItems);
			resetEvent();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuOption().equals("Open") && SHADE_CHEST_OBJECTS.containsKey(event.getId()))
		{
			setEvent(LootRecordType.EVENT, SHADE_CHEST_OBJECTS.get(event.getId()));
			takeInventorySnapshot();
		}

		if (event.getMenuOption().equals("Open") && event.getId() == ItemID.CASKET)
		{
			setEvent(LootRecordType.EVENT, CASKET_EVENT);
			takeInventorySnapshot();
		}

		if (event.getMenuOption().equals("Open") && event.getId() == ItemID.SPOILS_OF_WAR)
		{
			setEvent(LootRecordType.EVENT, SPOILS_OF_WAR_EVENT);
			takeInventorySnapshot();
		}
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

						final ItemComposition itemComposition = itemManager.getItemComposition(child.getItemId());
						final int gePrice = itemManager.getItemPrice(child.getItemId());
						final int haPrice = itemComposition.getHaPrice();

						itemObject.addProperty("gePrice", gePrice);
						itemObject.addProperty("haPrice", haPrice);

						oldBank.add(itemObject);
						newBank.add(itemObject);
					}
				}
			}
		}
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (getAccountUsername() == null || getAccountUsername().isEmpty()) {
			accountUsername = client.getLocalPlayer().getName();

			accountController.logInAccount();

			dataSubmittedChatMessage(ifAccountLoggedIn() ? "You are now using RuneManager with " + accountUsername : "Could not verify this account to your RuneManager user");
		}

		if (!ifUserToken() || !ifAccountLoggedIn())
		{
			return;
		}

		if (wintertodtLootWidgetOpen)
		{
			wintertodtLootWidgetOpen = false;

			final Widget wintertodtLootWidget = client.getWidget(229, 1);
			if (wintertodtLootWidget == null)
			{
				return;
			}

			Item[] container = parseWintertodtLootWidget(wintertodtLootWidget.getText());
			if (container == null)
			{
				return;
			}

			// Convert container items to array of ItemStack
			final Collection<ItemStack> items = Arrays.stream(container)
				.filter(item -> item.getId() > 0)
				.map(item -> new ItemStack(item.getId(), item.getQuantity(), client.getLocalPlayer().getLocalLocation()))
				.collect(Collectors.toList());

			if (items.isEmpty())
			{
				log.debug("No items to find for Event: {} | Container: {}", eventType, container);
				return;
			}

			addLoot(eventType, -1, lootRecordType, metadata, items);
		}

		if (levelUpWidgetOpen)
		{
			levelUpWidgetOpen = false;

			HashMap<String, String> levelUpData = new HashMap<String, String>();

			// If level up, parse level up data from level up widget
			if (client.getWidget(WidgetInfo.LEVEL_UP_LEVEL) != null)
			{
				levelUpData = parseLevelUpWidget(WidgetInfo.LEVEL_UP_LEVEL);
			}

			if (levelUpData.isEmpty())
			{
				log.debug("Could not find any level up message");
				return;
			}

			dataSubmittedChatMessage(controller.postLevelUp(levelUpData));
		}

		if (bankWidgetOpen)
		{
			bankWidgetOpen = false;

			if (oldBank == newBank)
			{
				newBank = new JsonArray();

				return;
			}

			dataSubmittedChatMessage(controller.postBank(newBank));

			oldBank = newBank;
		}

		if (questLogMenuOpen)
		{
			questLogMenuOpen = false;

			getQuestJournal();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (varbitChanged.getIndex() == COLLECTION_LOG_CATEGORY_VARBIT_INDEX)
		{
			clientThread.invokeLater(this::getCategory);
		}
	}

	@Schedule(
		period = 5,
		unit = ChronoUnit.MINUTES,
		asynchronous = true
	)
	public void submitLootTask()
	{
		submitLoot();
	}

	@Nullable
	private CompletableFuture<Void> submitLoot()
	{
		List<LootRecord> copy;
		synchronized (queuedLoots)
		{
			if (queuedLoots.isEmpty())
			{
				return null;
			}

			copy = new ArrayList<>(queuedLoots);
			queuedLoots.clear();
		}

		if (!config.saveLoot())
		{
			return null;
		}

		log.debug("Submitting {} loot records", copy.size());

		return lootTrackerClient.submit(copy);
	}

	private void setEvent(LootRecordType lootRecordType, String eventType, Object metadata)
	{
		this.lootRecordType = lootRecordType;
		this.eventType = eventType;
		this.metadata = metadata;
	}

	private void setEvent(LootRecordType lootRecordType, String eventType)
	{
		setEvent(lootRecordType, eventType, null);
	}

	private void resetEvent()
	{
		lootRecordType = null;
		eventType = null;
		metadata = null;
	}

	private void takeInventorySnapshot()
	{
		final ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
		if (itemContainer != null)
		{
			inventorySnapshot = HashMultiset.create();
			Arrays.stream(itemContainer.getItems())
				.forEach(item -> inventorySnapshot.add(item.getId(), item.getQuantity()));
		}
	}

	private void processInventoryLoot(String event, LootRecordType lootRecordType, Object metadata, ItemContainer inventoryContainer, Collection<ItemStack> groundItems)
	{
		if (inventorySnapshot != null)
		{
			Multiset<Integer> currentInventory = HashMultiset.create();
			Arrays.stream(inventoryContainer.getItems())
				.forEach(item -> currentInventory.add(item.getId(), item.getQuantity()));

			groundItems.stream()
				.forEach(item -> currentInventory.add(item.getId(), item.getQuantity()));

			final Multiset<Integer> diff = Multisets.difference(currentInventory, inventorySnapshot);

			List<ItemStack> items = diff.entrySet().stream()
				.map(e -> new ItemStack(e.getElement(), e.getCount(), client.getLocalPlayer().getLocalLocation()))
				.collect(Collectors.toList());

			addLoot(event, -1, lootRecordType, metadata, items);

			inventorySnapshot = null;
		}
	}

	boolean isIgnored(String name)
	{
		return ignoredItems.contains(name);
	}

	void toggleEvent(String name, boolean ignore)
	{
		final Set<String> ignoredSet = new LinkedHashSet<>(ignoredEvents);

		if (ignore)
		{
			ignoredSet.add(name);
		}
		else
		{
			ignoredSet.remove(name);
		}

		config.setIgnoredEvents(Text.toCSV(ignoredSet));
		// the config changed will update the panel
	}

	boolean isEventIgnored(String name)
	{
		return ignoredEvents.contains(name);
	}

	private LootTrackerItem buildLootTrackerItem(int itemId, int quantity)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int gePrice = itemManager.getItemPrice(itemId);
		final int haPrice = itemComposition.getHaPrice();
		final boolean ignored = ignoredItems.contains(itemComposition.getName());

		return new LootTrackerItem(
			itemId,
			itemComposition.getName().replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase(),
			quantity,
			gePrice,
			haPrice,
			ignored);
	}

	private LootTrackerItem[] buildEntries(final Collection<ItemStack> itemStacks)
	{
		return itemStacks.stream()
			.map(itemStack -> buildLootTrackerItem(itemStack.getId(), itemStack.getQuantity()))
			.toArray(LootTrackerItem[]::new);
	}

	private static Collection<GameItem> toGameItems(Collection<ItemStack> items)
	{
		return items.stream()
			.map(item -> new GameItem(item.getId(), item.getQuantity()))
			.collect(Collectors.toList());
	}

	private Collection<LootTrackerRecord> convertToLootTrackerRecord(final Collection<LootAggregate> records)
	{
		return records.stream()
			.sorted(Comparator.comparing(LootAggregate::getLast_time))
			.map(record ->
			{
				LootTrackerItem[] drops = record.getDrops().stream().map(itemStack ->
					buildLootTrackerItem(itemStack.getId(), itemStack.getQty())
				).toArray(LootTrackerItem[]::new);

				return new LootTrackerRecord(record.getEventId(), "", record.getType(), drops, record.getAmount());
			})
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Is player currently within the provided map regions
	 */
	private boolean isPlayerWithinMapRegion(Set<Integer> definedMapRegions)
	{
		final int[] mapRegions = client.getMapRegions();

		for (int region : mapRegions)
		{
			if (definedMapRegions.contains(region))
			{
				return true;
			}
		}

		return false;
	}

	private long getTotalPrice(Collection<ItemStack> items)
	{
		long totalPrice = 0;

		for (final ItemStack itemStack : items)
		{
			totalPrice += (long) itemManager.getItemPrice(itemStack.getId()) * itemStack.getQuantity();
		}

		return totalPrice;
	}

	private Integer getObtainedCount(Widget categoryHead)
	{
		Widget[] children = categoryHead.getDynamicChildren();
		if (children.length < 3)
		{
			return 0;
		}

		Pattern UNIQUES_OBTAINED_PATTERN = Pattern.compile("Obtained: <col=(.+?)>([0-9]+)/([0-9]+)</col>");

		String obtainedCount = categoryHead.getDynamicChildren()[1].getText();
		Matcher uniquesObtainedMatcher = UNIQUES_OBTAINED_PATTERN.matcher(obtainedCount);
		if (uniquesObtainedMatcher.find())
		{
			obtainedCount = uniquesObtainedMatcher.group(2);
		}

		return Integer.parseInt(obtainedCount);
	}

	private Integer getKillCount(Widget categoryHead)
	{
		Widget[] children = categoryHead.getDynamicChildren();
		if (children.length < 3)
		{
			return 0;
		}

		String killCount = categoryHead.getDynamicChildren()[2].getText();
		killCount = killCount.split(": ")[1]
			.split(">")[1]
			.split("<")[0]
			.replace(",", "");

		return Integer.parseInt(killCount);
	}

	private void getCategory()
	{
		Widget categoryHead = client.getWidget(COLLECTION_LOG_GROUP_ID, COLLECTION_LOG_CATEGORY_HEAD);
		if (categoryHead == null)
		{
			return;
		}

		String categoryTitle = categoryHead.getDynamicChildren()[0].getText();

		final Widget[] header = categoryHead.getDynamicChildren();
		if (header == null)
		{
			return;
		}

		Integer obtainedCount = getObtainedCount(categoryHead);
		Integer killCount = getKillCount(categoryHead);

		getItems(categoryTitle, obtainedCount, killCount);
	}

	private void getItems(String categoryTitle, Integer obtainedCount, Integer killCount)
	{
		final Widget itemsContainer = client.getWidget(COLLECTION_LOG_GROUP_ID, COLLECTION_LOG_CATEGORY_ITEMS);
		if (itemsContainer == null)
		{
			return;
		}

		final Widget[] items = itemsContainer.getDynamicChildren();
		if (items == null)
		{
			return;
		}

		List<CollectionLogItem> collectionLogItems = new ArrayList<>();
		for (Widget item : items)
		{
			collectionLogItems.add(new CollectionLogItem(item));
		}

		dataSubmittedChatMessage(controller.postCollectionLog(categoryTitle, collectionLogItems, obtainedCount, killCount));
	}

	private Item[] parseWintertodtLootWidget(String wintertodtLootString)
	{
		Matcher m = WINTERTODT_LOOT_PATTERN.matcher(wintertodtLootString);
		if (!m.matches())
		{
			return null;
		}

		String[] lootStrings = m.group(1).replace("<br>", " ").split(", ");

		final List<Item> wintertodtLootItems = new ArrayList<>();

		for (String lootString : lootStrings)
		{
			String[] itemAndQuantity = lootString.split(" x ");

			String itemName = itemAndQuantity[0].replaceFirst(" ", ""); // For some reason there is a redundant zero
			List<ItemPrice> itemIdSearch = itemManager.search(itemName);

			int itemId = 0;
			if (itemIdSearch.size() > 0)
			{
				itemId = itemIdSearch.get(0).getId();
			}

			Item item = new Item(itemId, Integer.parseInt(itemAndQuantity[1]));

			wintertodtLootItems.add(item);
		}

		return wintertodtLootItems.toArray(new Item[0]);
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

		return levelUpData;
	}

	private void getCurrentEquipment()
	{
		JsonArray equipment = new JsonArray();

		for (int i = 10; i < 21; i++)
		{
			final Widget equipmentSlot = client.getWidget(84, i);
			final Widget[] equipmentData = equipmentSlot.getDynamicChildren();

			JsonObject equipmentObject = new JsonObject();
			equipmentObject.addProperty("id", Integer.toString(equipmentData[1].getItemId()));

			String itemName = equipmentSlot.getName();

			Matcher itemNameMatcher = ITEM_NAME_PATTERN.matcher(itemName);
			if (itemNameMatcher.find())
			{
				itemName = itemNameMatcher.group(2);
			}

			equipmentObject.addProperty("name", itemName);

			equipmentObject.addProperty("quantity", Integer.toString(equipmentData[1].getItemQuantity()));

			equipment.add(equipmentObject);
		}

		dataSubmittedChatMessage(controller.postEquipment(equipment));
	}

	private void getQuestJournal()
	{
		final Widget allQuests = client.getWidget(WidgetID.QUESTLIST_GROUP_ID, 5);
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
			final Widget questWidget = client.getWidget(questChild.getId());
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
				}
				else if (statusCode.equals(16776960))
				{
					status = "in_progress";
				}
				else if (statusCode.equals(16711680))
				{
					status = "not_started";
				}

				questObject.addProperty("status", status);

				quests.add(questObject);
			}

			questCategory.add(quests);
		}

		dataSubmittedChatMessage(controller.postQuests(questCategory));
	}

	public void dataSubmittedChatMessage(String chatMessage)
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
