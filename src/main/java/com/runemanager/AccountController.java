package com.runemanager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.FormBody;
import okhttp3.MediaType;
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

	@Inject
	private UserController userController;

	private static final MediaType MEDIA_TYPE_MARKDOWN
		= MediaType.parse("application/json; charset=utf-8");
	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();

	public static final String AUTH_COMMAND_STRING = "!auth";

	public void authenticatePlayer(ChatMessage chatMessage, String message)
	{
		if (!plugin.ifUserToken())
		{
			plugin.dataSubmittedChatMessage("You have to be logged in to authenticate this account with RuneManager");

			return;
		}

		clientThread.invoke(() ->
		{
			String authCode = message.substring(AUTH_COMMAND_STRING.length() + 1);
			String accountType = client.getAccountType().name().toLowerCase();


			plugin.dataSubmittedChatMessage("Attempting to authenticate account " + plugin.accountUsername + " with user " + runeManagerConfig.username() + " to RuneManager");

			RequestBody formBody = new FormBody.Builder()
				.add("username", plugin.getAccountUsername())
				.add("code", authCode)
				.add("account_type", accountType)
				.build();

			Request request = new Request.Builder()
				.url(runeManagerConfig.url() + "/api/authenticate")
				.addHeader("Authorization", "Bearer " + plugin.getUserToken())
				.post(formBody)
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					plugin.accountUsername = null;
					plugin.accountLoggedIn = false;

					plugin.dataSubmittedChatMessage("Error: " + response.code() + " - " + response.body().string());
				} else
				{
					plugin.dataSubmittedChatMessage(response.body().string());

					logInAccount();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		});
	}

	public void logInAccount()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !plugin.ifUserToken() || plugin.isAccountLoggedIn())
		{
			return;
		}

		plugin.accountUsername = client.getLocalPlayer().getName();

		String locationString = gson.toJson(client.getLocalPlayer().getLocalLocation());

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + plugin.accountUsername + "/login")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.put(RequestBody.create(MEDIA_TYPE_MARKDOWN, locationString))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				plugin.accountLoggedIn = false;

				return;
			}

			plugin.accountLoggedIn = true;
		}
		catch (IOException e)
		{
			e.printStackTrace();

			plugin.accountLoggedIn = false;
		}
	}

	public void logOutAccount()
	{
		if (!plugin.ifUserToken())
		{
			return;
		}

		String locationString = "";

		if (client.getGameState() == GameState.LOGGED_IN) {
			locationString = gson.toJson(client.getLocalPlayer().getLocalLocation());
		}

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + plugin.accountUsername + "/logout")
			.addHeader("Authorization", "Bearer " + plugin.userToken)
			.put(RequestBody.create(MEDIA_TYPE_MARKDOWN, locationString))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			plugin.accountUsername = null;
			plugin.accountLoggedIn = false;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		plugin.accountUsername = null;
		plugin.accountLoggedIn = false;
	}
}
