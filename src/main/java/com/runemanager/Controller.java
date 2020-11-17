package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.runelite.api.Client;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class Controller
{
	@Inject
	private RuneManagerConfig runeManagerConfig;

	private String authToken;
	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();
	private static final MediaType MEDIA_TYPE_MARKDOWN
		= MediaType.parse("application/json; charset=utf-8");

	public boolean logIn()
	{
		if (Strings.isNullOrEmpty(runeManagerConfig.url())
			|| Strings.isNullOrEmpty(runeManagerConfig.username())
			|| Strings.isNullOrEmpty(runeManagerConfig.password()))
		{
			return false;
		}

		RequestBody formBody = new FormBody.Builder()
			.add("name", runeManagerConfig.username())
			.add("password", runeManagerConfig.password())
			.build();

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/user/login")
			.post(formBody)
			.build();

		OkHttpClient httpClient = new OkHttpClient();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				Gson gson = new Gson();

				JsonPrimitive authTokenObject = gson.fromJson(response.body().string(), JsonPrimitive.class);

				if (Strings.isNullOrEmpty(authTokenObject.toString()))
				{
					authToken = null;

					return false;

				}
				else
				{
					authToken = authTokenObject.toString().replace("\"", "");

					return true;
				}
			}
			else
			{
				authToken = null;

				return false;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		authToken = null;

		return false;
	}

	public void logOut()
	{
		authToken = null;
	}

	public String getAuthToken()
	{
		return authToken;
	}

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

	public String postLootStack(String player, String collectionName, LinkedHashMap<String, Integer> loot)
	{
		String collectionJson = gson.toJson(loot);

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + player + "/loot/" + collectionName)
			.addHeader("Authorization", "Bearer " + authToken)
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
			.addHeader("Authorization", "Bearer " + authToken)
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
			.addHeader("Authorization", "Bearer " + authToken)
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
}