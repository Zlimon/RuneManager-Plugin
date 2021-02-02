package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import javax.inject.Inject;
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

	@Inject
	private AccountController accountController;

	public void logInUser()
	{
		if (plugin.ifUserLoggedIn())
		{
			return;
		}

		if (Strings.isNullOrEmpty(runeManagerConfig.url())
			|| Strings.isNullOrEmpty(runeManagerConfig.username())
			|| Strings.isNullOrEmpty(runeManagerConfig.password()))
		{
			plugin.userToken = "";
			plugin.userLoggedIn = false;

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

		OkHttpClient httpClient = new OkHttpClient();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				plugin.sendChatMessage(response.body().string());

				throw new IOException("Unexpected code " + response);
			}

			Gson gson = new Gson();

			JsonPrimitive authTokenObject = gson.fromJson(response.body().string(), JsonPrimitive.class);

			if (Strings.isNullOrEmpty(authTokenObject.toString()))
			{
				plugin.userToken = "";
				plugin.userLoggedIn = false;

				plugin.sendChatMessage("Could not log in to RuneManager. Did not get user token.");
			}
			else
			{
				plugin.userToken = authTokenObject.toString().replace("\"", "");

				if (!plugin.userToken.isEmpty())
				{
					plugin.userLoggedIn = true;

					if (!plugin.accountUsername.isEmpty())
					{
						if (!plugin.ifAccountLoggedIn())
						{
							accountController.logInAccount();

							if (plugin.ifAccountLoggedIn())
							{
								plugin.sendChatMessage("Successfully logged in to RuneManager with account " + plugin.accountUsername + ".");
							}
							else
							{
								plugin.sendChatMessage("Successfully logged in to RuneManager, but could not connect with account " + plugin.accountUsername + ".");
							}
						}
						else
						{
							plugin.sendChatMessage("1Successfully logged in to RuneManager, but could not connect with account " + plugin.accountUsername + ".");
						}
					} else {
						plugin.sendChatMessage("Successfully logged in to RuneManager.");
					}
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void logOutUser()
	{
		accountController.logOutAccount();

		plugin.userToken = "";
		plugin.userLoggedIn = false;
	}
}
