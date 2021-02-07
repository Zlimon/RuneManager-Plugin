package com.runemanager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class Controller
{
	@Inject
	private RuneManagerConfig runeManagerConfig;

	@Inject
	private RuneManagerPlugin plugin;

	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();
	private static final MediaType MEDIA_TYPE_MARKDOWN
		= MediaType.parse("application/json; charset=utf-8");

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

	public String postLootStack(String player, String collectionName, LinkedHashMap<String, String> loot)
	{
		String collectionJson = gson.toJson(loot);

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/loot/" + collectionName)
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.put(RequestBody.create(MEDIA_TYPE_MARKDOWN, collectionJson))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted kill for " + collectionName + " to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postCollectionLog(String player, String collectionName, LinkedHashMap<String, Integer> loot)
	{
		String collectionJson = gson.toJson(loot);

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/collection/" + collectionName)
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, collectionJson))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted collection log for " + collectionName + " to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postLevelUp(String player, HashMap<String, String> levelUpData)
	{
		RequestBody formBody = new FormBody.Builder()
			.add("level", levelUpData.get("level"))
			.build();

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/skill/" + levelUpData.get("name"))
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(formBody)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted level up for " + levelUpData.get("name") + " to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postEquipment(String player, JsonArray equipment)
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/equipment")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, String.valueOf(equipment)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted equipment to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postLootCrate(String player, LinkedHashMap<String, String> loot)
	{
		String collectionJson = gson.toJson(loot);

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/lootcrate")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, collectionJson))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted loot crate loot for  to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postBank(String player, JsonArray bank)
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/bank")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, String.valueOf(bank)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted bank to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}

	public String postQuests(String player, JsonArray quests)
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/quests")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, String.valueOf(quests)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return "Successfully submitted quests to RuneManager!";
			}
			else
			{
				return response.body().string();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong";
	}
}