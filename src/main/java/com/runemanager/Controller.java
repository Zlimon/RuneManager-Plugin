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

	public String postLootStack(String name, LootRecord loot)
	{
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/loot/" + name.toLowerCase();

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
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/collection/" + categoryTitle.toLowerCase();

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
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/skill/" + levelUpData.get("name");

		String levelUpString = gson.toJson(levelUpData);

		return sendPostRequest(endPoint, levelUpString);
	}

	public String postEquipment(JsonArray equipment)
	{
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/equipment";

		String equipmentString = gson.toJson(equipment);

		return sendPostRequest(endPoint, equipmentString);
	}

	public String postBank(JsonArray bank)
	{
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/bank";

		String bankString = gson.toJson(bank);

		return sendPostRequest(endPoint, bankString);
	}

	public String postQuests(JsonArray quests)
	{
		String player = "Jern Zlimon"; // WIP

		String endPoint = "/api/account/" + player + "/quests";

		String postString = gson.toJson(quests);

		return sendPostRequest(endPoint, postString);
	}

	public String sendPostRequest(String endPoint, String json)
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + endPoint)
			.addHeader("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIiwianRpIjoiMWZiZDAxNjBiNDY0ZDYwM2U4YWZhOGNlYmNjODM3YzUzMmExOTU5NDU5Njg4MzZhN2QxNmIzODE5MGVlMDIxNzNhMTNjOTYyYzViZTMxYzIiLCJpYXQiOiIxNjEzNjgxNTM0Ljk0MDMzNiIsIm5iZiI6IjE2MTM2ODE1MzQuOTQwMzM5IiwiZXhwIjoiMTY0NTIxNzUzNC44Nzk4NDMiLCJzdWIiOiIxIiwic2NvcGVzIjpbXX0.A2oJPTB9AsQrSKK8UZqToEeGoRyFx5-F0zAzJWj9-Hb3cD_ooIrYPT4SIp9Q1lmmYTlsxA4FjrJnWW7WqO8HoRyjoD7oiSPUKsINOn80pDKebdE2TJR8zl4giNaRN7tCozbAqKkvJ0V4FwCDdPyUZRFpBCI9lQQT1Zhcy_Ts_k2Y1Cgu9AOp2F20LXmgEM1pKGF0D_PeSaQoyP0npzJnCqUIdwmLAKGYSXRaTTXRAmnMed21QNGKS3QdD2FKngRWsXF0k4mey9CJKKK_Kd1djP0421eDD4VX-SHDJLTWj0VUhH_MMatPJij0MEU5UqyWW-k5mJmKkaO7z9z-8LwHeZV_gUJgHXAQqnaVBJNN3pw1DwffDU3GkQFGLImDh-AZKK2o-LpLKIe85g6mWTi8K7Dq0WbzjJhIg2xzLWbYbKkcVsh4pi7yRj-oJ-_l36O-BVAMrHk4uR4zq702IHC5Uz7iFsS1Y3WxJUf-iai283JfoSaapRnj2D0qP7QLZeiWjE1WbqB6PFosWrSMur2qFQYF4U7aullR9dCqGCWc6of6eCkh_7mapB8lfPTnzbWtM_PExQu3mmkmFIK2MnWeFxIOvodgGWe_D78KqpJBnhQM_8ZdjvMyW2jyMuztyBKiZIIOfzP2eDjQnf96gGZEKBk7f1MF-u0J3tGUFKCp8OQ")
			.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, json))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200 || response.code() == 201)
			{
				return response.body().string();
			}

			return "Error: " + response.code() + " - " + response.body().string();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong!";
	}

	public String sendPutRequest(String endPoint, String json)
	{
		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + endPoint)
			.addHeader("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIiwianRpIjoiMWZiZDAxNjBiNDY0ZDYwM2U4YWZhOGNlYmNjODM3YzUzMmExOTU5NDU5Njg4MzZhN2QxNmIzODE5MGVlMDIxNzNhMTNjOTYyYzViZTMxYzIiLCJpYXQiOiIxNjEzNjgxNTM0Ljk0MDMzNiIsIm5iZiI6IjE2MTM2ODE1MzQuOTQwMzM5IiwiZXhwIjoiMTY0NTIxNzUzNC44Nzk4NDMiLCJzdWIiOiIxIiwic2NvcGVzIjpbXX0.A2oJPTB9AsQrSKK8UZqToEeGoRyFx5-F0zAzJWj9-Hb3cD_ooIrYPT4SIp9Q1lmmYTlsxA4FjrJnWW7WqO8HoRyjoD7oiSPUKsINOn80pDKebdE2TJR8zl4giNaRN7tCozbAqKkvJ0V4FwCDdPyUZRFpBCI9lQQT1Zhcy_Ts_k2Y1Cgu9AOp2F20LXmgEM1pKGF0D_PeSaQoyP0npzJnCqUIdwmLAKGYSXRaTTXRAmnMed21QNGKS3QdD2FKngRWsXF0k4mey9CJKKK_Kd1djP0421eDD4VX-SHDJLTWj0VUhH_MMatPJij0MEU5UqyWW-k5mJmKkaO7z9z-8LwHeZV_gUJgHXAQqnaVBJNN3pw1DwffDU3GkQFGLImDh-AZKK2o-LpLKIe85g6mWTi8K7Dq0WbzjJhIg2xzLWbYbKkcVsh4pi7yRj-oJ-_l36O-BVAMrHk4uR4zq702IHC5Uz7iFsS1Y3WxJUf-iai283JfoSaapRnj2D0qP7QLZeiWjE1WbqB6PFosWrSMur2qFQYF4U7aullR9dCqGCWc6of6eCkh_7mapB8lfPTnzbWtM_PExQu3mmkmFIK2MnWeFxIOvodgGWe_D78KqpJBnhQM_8ZdjvMyW2jyMuztyBKiZIIOfzP2eDjQnf96gGZEKBk7f1MF-u0J3tGUFKCp8OQ")
			.put(RequestBody.create(MEDIA_TYPE_MARKDOWN, json))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 200)
			{
				return response.body().string();
			}

			return "Error: " + response.code() + " - " + response.body().string();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return "Something went wrong!";
	}
}