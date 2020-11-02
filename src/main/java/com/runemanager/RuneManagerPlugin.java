package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.*;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

import net.runelite.client.plugins.chatcommands.ChatCommandsConfig;
import net.runelite.client.plugins.chatcommands.ChatKeyboardListener;
import okhttp3.*;

import javax.inject.Inject;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
		name = "1RuneManager",
		description = "Official RuneManager plugin"
)

@Slf4j
public class RuneManagerPlugin extends Plugin {
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

	private static final String AUTH_COMMAND_STRING = "!auth";
	private AvailableCollections[] collections = null;
	private static final Pattern UNIQUES_OBTAINED_PATTERN = Pattern.compile("Obtained: <col=(.+?)>([0-9]+)/([0-9]+)</col>");
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile("(.+?): <col=(.+?)>([0-9]+)</col>");
	private static final Pattern ITEM_NAME_PATTERN = Pattern.compile("<col=(.+?)>(.+?)</col>");

	@Override
	protected void startUp() {
		if (!Strings.isNullOrEmpty(runeManagerConfig.url())) {
			if (userController.logIn()) {
				chatCommandManager.registerCommandAsync(AUTH_COMMAND_STRING, this::authenticatePlayer);

				if (collections == null) {
					System.out.println("HENTER BOSS OVERSIKT");

					collections = controller.getBossOverview();

					for (AvailableCollections availableCollections : collections) {
						System.out.println(availableCollections.getName());
					}
				}
			} else {
				System.out.println("Could not log in to RuneManager");
			}
		}
	}

	@Override
	public void shutDown() {
		userController.authToken = null;

		chatCommandManager.unregisterCommand(AUTH_COMMAND_STRING);
	}

	@Provides
	RuneManagerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneManagerConfig.class);
	}

	private void authenticatePlayer(ChatMessage chatMessage, String message)
	{
		if (!Strings.isNullOrEmpty(userController.authToken)) {
			if (!Strings.isNullOrEmpty(runeManagerConfig.url())
				&& !Strings.isNullOrEmpty(runeManagerConfig.username())
				&& !Strings.isNullOrEmpty(runeManagerConfig.password())) {
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

				sendChatMessage("Attempting to authenticate account " + client.getLocalPlayer().getName() + " with user " + runeManagerConfig.username());

				OkHttpClient httpClient = new OkHttpClient();

				try (Response response = httpClient.newCall(request).execute()) {
					if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

					sendChatMessage(response.body().string());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			sendChatMessage("You are not logged in to RuneManager");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			if (!Strings.isNullOrEmpty(userController.authToken)) {
				sendChatMessage("You are logged in to RuneManager");
			} else {
				sendChatMessage("You are NOT logged in to RuneManager");
			}
		}
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived) throws IOException {
		if (!Strings.isNullOrEmpty(userController.authToken)) {
			final NPC npc = npcLootReceived.getNpc();

			// Find if NPC is available for loot tracking
			for (AvailableCollections availableCollections : collections) {
				if (availableCollections.getName().equals(npc.getName().toLowerCase())) {
					final String name = npc.getName();
					final Collection<ItemStack> items = npcLootReceived.getItems();

					createLootStack(name, items);
				}
			}
		}
	}

	private boolean collectionLogOpen = false;
	private int previousCollectionLogValue;
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (!Strings.isNullOrEmpty(userController.authToken)) {
			if (event.getGroupId() != 621) {
				collectionLogOpen = false;
				return;
			}

			collectionLogOpen = true;
		}
	}
	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (collectionLogOpen) {
			int collectionLogValue = client.getVarbitValue(6906);
			if (collectionLogValue != previousCollectionLogValue) {
				getCurrentCollectionLogHeaderData();

				previousCollectionLogValue = collectionLogValue;
			}
		}
	}

	public void getCurrentCollectionLogHeaderData() {
		clientThread.invokeLater(() ->
		{
			final Widget collectionLogHeader = client.getWidget(621, 19); // Right widget header panel
			if (collectionLogHeader == null) {
				return;
			}

			final Widget[] header = collectionLogHeader.getDynamicChildren(); // 0 - Collection name, 1 - Uniques obtained, 2 - Killcount
			if (header == null) {
				return;
			}

			String collectionName = header[0].getText().replaceAll("[+.^:,']", "");

			int uniquesObtained = 0;
			Matcher uniquesObtainedMatcher = UNIQUES_OBTAINED_PATTERN.matcher(header[1].getText());
			if (uniquesObtainedMatcher.find()) {
				uniquesObtained = Integer.parseInt(uniquesObtainedMatcher.group(2));
			}

			int killCount = 0;
			Matcher killCountMatcher = KILL_COUNT_PATTERN.matcher(header[2].getText());
			if (killCountMatcher.find()) {
				killCount = Integer.parseInt(killCountMatcher.group(3));
			}

			try {
				getCollectionLogContentData(collectionName, uniquesObtained, killCount);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void getCollectionLogContentData(String collectionName, int uniquesObtained, int killCount) throws IOException {
		//final Widget collectionLog = client.getWidget(WidgetInfo.COLLECTION_LOG_LOOT);
		final Widget collectionLog = client.getWidget(621, 35); // Right widget loot panel
		if (collectionLog == null) {
			return;
		}

		final Widget[] log = collectionLog.getDynamicChildren();
		if (log == null) {
			return;
		}

		LinkedHashMap<String,Integer> items = new LinkedHashMap<String, Integer>();

		items.put("obtained", uniquesObtained);
		items.put("kill_count", killCount);

		for (Widget item : log) {
			String itemName = "";
			int itemQuantity = 0;

			Matcher itemNameMatcher = ITEM_NAME_PATTERN.matcher(item.getName());
			if (itemNameMatcher.find())
			{
				itemName = itemNameMatcher.group(2);
			}

			System.out.println("GAMMELT NAVN " + itemName);

			itemName = itemName.replace(" ", "_").replaceAll("[+.^:,']","").toLowerCase();

			System.out.println("NYTT NAVN " + itemName);

			if (item.getOpacity() == 0) {
				itemQuantity = item.getItemQuantity();
			}

			items.put(itemName, itemQuantity);
		}

		sendChatMessage(controller.postCollectionLog(client.getLocalPlayer().getName(), collectionName, items, userController.authToken));
	}

	public void createLootStack(String collectionName, final Collection<ItemStack> items) throws IOException {
		LinkedHashMap<String,Integer> loot = new LinkedHashMap<String, Integer>();

		final LootItem[] entries = buildEntries(stack(items));

		for (LootItem item : entries) {
			String itemName = item.getName();
			int itemQuantity = item.getQuantity();

			System.out.println("GAMMELT NAVN " + itemName);

			itemName = itemName.replace(" ", "_").replaceAll("[+.^:,']","").toLowerCase();

			System.out.println("NYTT NAVN " + itemName);

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

	private void makeModel(String bossName, LinkedHashMap<String,Integer> loot) throws IOException {
		String fileName = RuneLite.RUNELITE_DIR + "\\model\\" + bossName.replace(" ", "") + ".php";
		purgeList(fileName);

		FileWriter writer = new FileWriter(fileName, true);

		writer.write("<?php\n");
		writer.write("\r\n");
		writer.write("namespace App\\Boss;\r\n");
		writer.write("\r\n");
		writer.write("use Illuminate\\Database\\Eloquent\\Model;\r\n");
		writer.write("\r\n");
		writer.write("class " + (bossName.substring(0, 1).toUpperCase() + bossName.substring(1)).replace(" ", "") + " extends Model\r\n");
		writer.write("{\r\n");
		writer.write("    protected $table = '" + bossName.toLowerCase().replaceAll(" ", "_") + "';\r\n");
		writer.write("\r\n");
		writer.write("    protected $fillable = [\r\n");
		//writer.write("        'obtained',\r\n");
		//writer.write("        'kill_count',\r\n");
		for (HashMap.Entry me : loot.entrySet()) {
			System.out.println("Key: "+me.getKey() + " & Value: " + me.getValue());
			String key = me.getKey().toString();
			writer.write("        '" + key + "',\r\n");
		}
		writer.write("    ];\r\n");
		writer.write("\r\n");
		writer.write("    protected $hidden = ['user_id'];\n");
		writer.write("}\r\n");

		writer.close();

		makeMigration(bossName.replace(" ", ""), loot);
	}

	private void makeMigration(String bossName, LinkedHashMap<String,Integer> loot) throws IOException {
		String fileName = RuneLite.RUNELITE_DIR + "\\migration\\" + bossName + " migration.php";
		purgeList(fileName);

		FileWriter writer = new FileWriter(fileName, true);

		writer.write("$table->id();\r\n");
		writer.write("$table->integer('user_id')->unsigned()->unique();\r\n");
		//writer.write("$table->integer('obtained')->default(0)->unsigned();\r\n");
		//writer.write("$table->integer('kill_count')->default(0)->unsigned();\r\n");
		for (HashMap.Entry me : loot.entrySet()) {
			System.out.println("Key: "+me.getKey() + " & Value: " + me.getValue());
			String key = me.getKey().toString();
			writer.write("$table->integer('"+key+"')->default(0)->unsigned();" + "\r\n");
		}
		writer.write("$table->timestamps();");

		writer.close();
	}

	private void purgeList(String fileName) {
		File purge = new File(fileName);
		purge.delete();
	}
}
