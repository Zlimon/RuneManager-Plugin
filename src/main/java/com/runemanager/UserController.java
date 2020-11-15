package com.runemanager;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;

public class UserController {
    @Inject
    private RuneManagerConfig runeManagerConfig;

    public String authToken;

    public boolean logIn() {
        if (!Strings.isNullOrEmpty(authToken)
                || Strings.isNullOrEmpty(runeManagerConfig.url())
                || Strings.isNullOrEmpty(runeManagerConfig.username())
                || Strings.isNullOrEmpty(runeManagerConfig.password())) {
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

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() != 200) {
                authToken = null;

                return false;
            }

            Gson gson = new Gson();

            JsonPrimitive authTokenObject = gson.fromJson(response.body().string(), JsonPrimitive.class);

            if (Strings.isNullOrEmpty(authTokenObject.toString())) {
                authToken = null;

                return false;

            } else {
                authToken = authTokenObject.toString().replace("\"", "");

                System.out.println("Successfully logged in");

                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();

            authToken = null;

            return false;
        }
    }
}
