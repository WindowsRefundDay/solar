package com.solar.launcher.deezer;

import com.solar.launcher.net.TlsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Resolve signed CDN URL from media.deezer.com. */
public final class DeezerMedia {
    private static final String GET_URL = "https://media.deezer.com/v1/get_url";

    private final DeezerClient client;

    public DeezerMedia(DeezerClient client) {
        this.client = client;
    }

    public String resolveUrl(String trackToken) throws IOException {
        if (trackToken == null || trackToken.isEmpty()) {
            throw new IOException("No track token");
        }
        JSONObject format = new JSONObject();
        try {
            format.put("cipher", "BF_CBC_STRIPE");
            format.put("format", client.soundFormat());
            JSONArray formats = new JSONArray();
            formats.put(format);
            JSONObject media = new JSONObject();
            media.put("type", "FULL");
            media.put("formats", formats);
            JSONArray mediaArr = new JSONArray();
            mediaArr.put(media);
            JSONArray tokens = new JSONArray();
            tokens.put(trackToken);
            JSONObject body = new JSONObject();
            body.put("license_token", client.licenseToken());
            body.put("media", mediaArr);
            body.put("track_tokens", tokens);

            TlsHelper.ensureSecurityProvider();
            MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
            Request req = new Request.Builder()
                    .url(GET_URL)
                    .post(RequestBody.create(jsonType, body.toString()))
                    .header("User-Agent", DeezerClient.USER_AGENT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
            OkHttpClient http = TlsHelper.client();
            Response resp = http.newCall(req).execute();
            try {
                if (!resp.isSuccessful() || resp.body() == null) {
                    throw new IOException("HTTP " + resp.code());
                }
                JSONObject root = new JSONObject(resp.body().string());
                JSONArray data = root.getJSONArray("data");
                JSONObject item = data.getJSONObject(0);
                if (item.has("errors")) {
                    JSONArray errs = item.getJSONArray("errors");
                    throw new IOException(errs.getJSONObject(0).optString("message", "API error"));
                }
                JSONArray mediaOut = item.getJSONArray("media");
                return mediaOut.getJSONObject(0).getJSONArray("sources")
                        .getJSONObject(0).getString("url");
            } finally {
                if (resp.body() != null) resp.body().close();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
}
