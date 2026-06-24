package com.solar.launcher.soulseek;

import android.content.Context;

import com.solar.launcher.BuildConfig;
import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.net.SolarHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HTTP client for the Reach user directory (Cloudflare Worker). */
public final class ReachDirectoryClient {
    private static final String UA = DeviceFeatures.reachClientName() + "/Solar";

    public interface UsersCallback {
        void onUsers(List<ReachDirectoryUser> users);
        void onError(String reason);
    }

    public interface LookupCallback {
        void onResult(boolean found, ReachDirectoryUser user);
        void onError(String reason);
    }

    private ReachDirectoryClient() {}

    public static boolean isConfigured() {
        String url = baseUrl();
        return url != null && !url.isEmpty();
    }

    public static void registerAsync(final Context context, final String username) {
        if (!isConfigured() || username == null || username.trim().isEmpty()) return;
        final String user = username.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("username", user);
                    body.put("device", DeviceFeatures.deviceModelLabel());
                    SolarHttp.postJson(registerUrl(), body.toString(), UA, registryToken());
                } catch (Exception ignored) {}
            }
        }, "ReachRegister").start();
    }

    public static void fetchUsersAsync(final String excludeUsername, final UsersCallback callback) {
        if (!isConfigured()) {
            if (callback != null) callback.onError("not_configured");
            return;
        }
        final String exclude = excludeUsername != null ? excludeUsername.trim() : "";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = usersUrl();
                    if (!exclude.isEmpty()) {
                        url += "?exclude=" + java.net.URLEncoder.encode(exclude, "UTF-8");
                    }
                    String raw = new String(
                            SolarHttp.getBytes(url, "application/json", UA, registryToken()), "UTF-8");
                    JSONObject root = new JSONObject(raw);
                    JSONArray arr = root.optJSONArray("users");
                    List<ReachDirectoryUser> out = new ArrayList<ReachDirectoryUser>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            ReachDirectoryUser u = parseUser(arr.optJSONObject(i));
                            if (u != null) out.add(u);
                        }
                    }
                    if (callback != null) callback.onUsers(out);
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "fetch_failed");
                    }
                }
            }
        }, "ReachList").start();
    }

    public static void lookupAsync(final String username, final LookupCallback callback) {
        if (!isConfigured() || username == null || username.trim().isEmpty()) {
            if (callback != null) callback.onResult(false, null);
            return;
        }
        final String user = username.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = baseUrl() + "/v1/users/"
                            + java.net.URLEncoder.encode(user, "UTF-8");
                    String raw = new String(
                            SolarHttp.getBytes(url, "application/json", UA, registryToken()), "UTF-8");
                    JSONObject root = new JSONObject(raw);
                    if (!root.optBoolean("found", false)) {
                        if (callback != null) callback.onResult(false, null);
                        return;
                    }
                    ReachDirectoryUser u = parseUser(root.optJSONObject("user"));
                    if (callback != null) callback.onResult(u != null, u);
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "lookup_failed");
                    }
                }
            }
        }, "ReachLookup").start();
    }

    static ReachDirectoryUser parseUser(JSONObject o) {
        if (o == null) return null;
        String name = o.optString("username", "");
        if (name.isEmpty()) return null;
        return new ReachDirectoryUser(name, o.optString("device", "Y1"), o.optLong("lastSeen", 0L),
                o.optLong("registeredAt", 0L));
    }

    private static String baseUrl() {
        String url = BuildConfig.REACH_DIRECTORY_URL;
        if (url == null) return "";
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static String registerUrl() {
        return baseUrl() + "/v1/register";
    }

    private static String usersUrl() {
        return baseUrl() + "/v1/users";
    }

    private static String registryToken() {
        String t = BuildConfig.REACH_DIRECTORY_TOKEN;
        return t != null ? t.trim() : "";
    }
}
