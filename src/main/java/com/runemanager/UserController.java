package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserController
{
	@Inject
	private RuneManagerPlugin plugin;

	@Inject
	private RuneManagerConfig runeManagerConfig;

	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();

	public void logInUser()
	{
		if (Strings.isNullOrEmpty(runeManagerConfig.url())
			|| Strings.isNullOrEmpty(runeManagerConfig.username())
			|| Strings.isNullOrEmpty(runeManagerConfig.password()))
		{
			plugin.userToken = null;

			return;
		}

		RequestBody formBody = new FormBody.Builder()
			.add("name", runeManagerConfig.username())
			.add("password", runeManagerConfig.password())
			.build();

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/user/login")
			.post(formBody)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
//				return "Error: " + response.code() + " - " + response.body().string();
				plugin.userToken = null;

				return;
			}

			JsonPrimitive authTokenObject = gson.fromJson(response.body().string(), JsonPrimitive.class);

			if (Strings.isNullOrEmpty(authTokenObject.toString()))
			{
				plugin.userToken = null;

//				plugin.dataSubmittedChatMessage("Could not log in to RuneManager. Did not get user token.");
				return;
			}

			plugin.userToken = authTokenObject.toString().replace("\"", "");
		}
		catch (IOException e)
		{
			e.printStackTrace();

			plugin.userToken = null;
		}
	}
}
