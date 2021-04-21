package com.runemanager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import net.runelite.http.api.loottracker.LootRecord;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Controller
{
	private static final MediaType MEDIA_TYPE_MARKDOWN
		= MediaType.parse("application/json; charset=utf-8");
	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();

	@Inject
	private RuneManagerConfig runeManagerConfig;

	@Inject
	private RuneManagerPlugin plugin;

	public AvailableCollections[] getBossOverview()
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/collection/all")
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Unexpected code " + response);
			}

			// Headers responseHeaders = response.headers();
			// for (int i = 0; i < responseHeaders.size(); i++) {
			// System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
			// }

			String collectionData = response.body().string();

			JsonArray collectionOverview = gson.fromJson(collectionData, JsonArray.class);

			return gson.fromJson(collectionOverview, AvailableCollections[].class);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public String[] getRecentNotifications()
	{
		Request request = new Request.Builder()
				.url(runeManagerConfig.url() + "/api/broadcast/recent/announcement")
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Unexpected code " + response);
			}

			String collectionData = response.body().string();

			System.out.println(collectionData);

			JsonArray collectionOverview = gson.fromJson(collectionData, JsonArray.class);

			System.out.println(collectionOverview);

			return gson.fromJson(collectionOverview, String[].class);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public String postLootStack(String name, LootRecord loot)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/loot/" + name;

		String lootString = gson.toJson(loot);

		return sendPutRequest(endPoint, lootString);
	}

//	public String postLootCrate(String name, LinkedHashMap<String, String> loot)
//	{
//		String player = "Jern Zlimon"; // WIP
//
//		String endPoint = "/api/account/" + player + "/lootcrate";
//
//		String lootString = gson.toJson(loot);
//
//		Integer responseCode = sendPutRequest(endPoint, lootString);
//
//		if (responseCode == 200) {
//			return "Successfully submitted loot crate loot for " + name + " to RuneManager!";
//		}
//
//		return "Something went wrong";
//	}

	public String postCollectionLog(String categoryTitle, List<CollectionLogItem> collectionLogItems, Integer obtainedCount, Integer killCount)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/collection/" + categoryTitle;

		JsonArray collectionLogItemsJsonArray = gson.toJsonTree(collectionLogItems).getAsJsonArray();

		JsonObject collectionLogJsonObject = new JsonObject();
		collectionLogJsonObject.add("collectionLogItems", collectionLogItemsJsonArray);

		collectionLogJsonObject.addProperty("obtained", Integer.toString(obtainedCount));
		collectionLogJsonObject.addProperty("kill_count", Integer.toString(killCount));

		String collectionLogString = gson.toJson(collectionLogJsonObject);

		return sendPostRequest(endPoint, collectionLogString);
	}

	public String postLevelUp(HashMap<String, String> levelUpData)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/skill/" + levelUpData.get("name");

		String levelUpString = gson.toJson(levelUpData);

		return sendPostRequest(endPoint, levelUpString);
	}

	public String postEquipment(JsonArray equipment)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/equipment";

		String equipmentString = gson.toJson(equipment);

		return sendPostRequest(endPoint, equipmentString);
	}

	public String postBank(JsonArray bank)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/bank";

		String bankString = gson.toJson(bank);

		return sendPostRequest(endPoint, bankString);
	}

	public String postQuests(JsonArray quests)
	{
		String endPoint = "/api/account/" + plugin.getAccountUsername() + "/quests";

		String postString = gson.toJson(quests);

		return sendPostRequest(endPoint, postString);
	}

	public String sendPostRequest(String endPoint, String json)
	{
		if (!plugin.ifUserToken() || !plugin.ifAccountLoggedIn())
		{
			return plugin.accountUsername + " is not logged in to RuneManager. Restart the plugin or RuneLite";
		}

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + endPoint)
			.addHeader("Authorization", "Bearer " + plugin.getUserToken())
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, json))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				return "Error: " + response.code() + " - " + response.body().string();
			}

			return response.body().string();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong!";
	}

	public String sendPutRequest(String endPoint, String json)
	{
		if (!plugin.ifUserToken() || !plugin.ifAccountLoggedIn())
		{
			return plugin.accountUsername + " is not logged in to RuneManager. Restart the plugin or RuneLite";
		}

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + endPoint)
			.addHeader("Authorization", "Bearer " + plugin.getUserToken())
			.put(RequestBody.create(MEDIA_TYPE_MARKDOWN, json))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				return "Error: " + response.code() + " - " + response.body().string();
			}

			return response.body().string();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong!";
	}
}