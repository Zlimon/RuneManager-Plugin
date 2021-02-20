package com.runemanager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import com.google.gson.Gson;
import java.io.IOException;
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

//	public void authenticatePlayer(ChatMessage chatMessage, String message)
//	{
//		if (!userController.ifUserLoggedIn())
//		{
//			plugin.dataSubmittedChatMessage("You have to be logged in to authenticate this account with RuneManager");
//
//			return;
//		}
//
//		String authCode = message.substring(AUTH_COMMAND_STRING.length() + 1);
//		String accountType = client.getAccountType().name().toLowerCase();
//
//		RequestBody formBody = new FormBody.Builder()
//			.add("username", accountUsername)
//			.add("code", authCode)
//			.add("account_type", accountType)
//			.build();
//
//		Request request = new Request.Builder()
//			.url(runeManagerConfig.url() + "/api/authenticate")
//			.addHeader("Authorization", "Bearer " + plugin.userToken)
//			.post(formBody)
//			.build();
//
//		plugin.sendChatMessage("Attempting to authenticate account " + plugin.accountUsername + " with user " + runeManagerConfig.username() + " to RuneManager");
//
//		OkHttpClient httpClient = new OkHttpClient();
//
//		try (Response response = httpClient.newCall(request).execute())
//		{
//			if (!response.isSuccessful())
//			{
//				throw new IOException("Unexpected code " + response);
//			}
//
//			logInAccount();
//
//			plugin.sendChatMessage(response.body().string());
//		}
//		catch (IOException e)
//		{
//			e.printStackTrace();
//		}
//	}

	public void logInAccount()
	{
		if (client.getGameState() != GameState.LOGGED_IN || (plugin.getUserToken() == null || plugin.getUserToken().isEmpty()) || plugin.isAccountLoggedIn())
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
		if ((plugin.getUserToken() == null || plugin.getUserToken().isEmpty()) && plugin.isAccountLoggedIn())
		{
			return;
		}

		String locationString = ""; // TODO

		Request request = new Request.Builder()
			.url(runeManagerConfig.url() + "/api/account/" + plugin.accountUsername + "/logout")
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

			plugin.accountLoggedIn = false;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		plugin.accountLoggedIn = false;
	}
}
