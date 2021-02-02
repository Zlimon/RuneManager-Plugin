package com.runemanager;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
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

public class AccountController
{
	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private RuneManagerPlugin plugin;

	@Inject
	private RuneManagerConfig runeManagerConfig;

	public static final String AUTH_COMMAND_STRING = "!auth";

	public void authenticatePlayer(ChatMessage chatMessage, String message)
	{
		if (!plugin.ifUserLoggedIn())
		{
			plugin.sendChatMessage("You have to be logged in to authenticate this account with RuneManager");

			return;
		}

		String authCode = message.substring(AUTH_COMMAND_STRING.length() + 1);
		String accountType = client.getAccountType().name().toLowerCase();

		RequestBody formBody = new FormBody.Builder()
			.add("username", plugin.accountUsername)
			.add("code", authCode)
			.add("account_type", accountType)
			.build();

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/authenticate")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.post(formBody)
			.build();

		plugin.sendChatMessage("Attempting to authenticate account " + plugin.accountUsername + " with user " + runeManagerConfig.username() + " to RuneManager");

		OkHttpClient httpClient = new OkHttpClient();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Unexpected code " + response);
			}

			logInAccount();

			plugin.sendChatMessage(response.body().string());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void logInAccount()
	{
		if (plugin.ifUserLoggedIn())
		{
			clientThread.invoke(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					if (!plugin.ifAccountLoggedIn())
					{
						plugin.accountUsername = client.getLocalPlayer().getName();

						RequestBody formBody = new FormBody.Builder()
							.build();

						Request request = new Request.Builder()
							.url(runeManagerConfig.url() + "/api/account/" + plugin.accountUsername + "/login")
							.addHeader("Authorization", "Bearer " + plugin.userToken)
							.put(formBody)
							.build();

						OkHttpClient httpClient = new OkHttpClient();

						try (Response response = httpClient.newCall(request).execute())
						{
							if (!response.isSuccessful())
							{
								throw new IOException("Unexpected code " + response);
							}

							plugin.accountLoggedIn = true;
						}
						catch (IOException e)
						{
							e.printStackTrace();
						}
					}
				}
			});
		}
	}

	public void logOutAccount()
	{
		if (plugin.ifUserLoggedIn() && plugin.ifAccountLoggedIn())
		{
			RequestBody formBody = new FormBody.Builder()
				.build();

			Request request = new Request.Builder()
				.url(runeManagerConfig.url() + "/api/account/" + plugin.accountUsername + "/logout")
				.addHeader("Authorization", "Bearer " + plugin.userToken)
				.put(formBody)
				.build();

			OkHttpClient httpClient = new OkHttpClient();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					throw new IOException("Unexpected code " + response);
				}

				plugin.accountUsername = "";
				plugin.accountLoggedIn = false;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
