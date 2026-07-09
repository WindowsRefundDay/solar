package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.deezer.DeezerClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import com.solar.launcher.deezer.DeezerSearch;
import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.deezer.DeezerBackgroundQueue;

public class SolarWebServer extends Thread {
    private ServerSocket serverSocket;
    private boolean running = true;
    private File rootFolder;
    private Context context;

    // 메인 화면에서 폴더 위치와 환경(Context)을 넘겨받음
    public SolarWebServer(Context context, File rootFolder) {
        this.context = context;
        this.rootFolder = rootFolder;
    }

    private String spaHtmlCache = null;

    private String getSpaHtml() {
        if (spaHtmlCache == null) {
            try {
                InputStream is = context.getAssets().open("web/index.html");
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                spaHtmlCache = s.hasNext() ? s.next() : "";
                is.close();
            } catch (Exception e) {
                spaHtmlCache = "<html><body>Error loading index.html</body></html>";
            }
        }
        return spaHtmlCache;
    }

    public void run() {
        com.solar.launcher.net.TlsHelper.init(context);
        try {
            serverSocket = new ServerSocket(8080);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("port", 8080);
                d.put("ip", getLocalIpAddress());
                com.solar.launcher.deezer.DeezerDebugLog.log(context, "SolarWebServer.run",
                        "listening", "E", d);
            } catch (Exception ignored) {}
            // #endregion
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(new RequestHandler(socket)).start();
            }
        } catch (Exception e) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("error", e.getClass().getSimpleName());
                String msg = e.getMessage();
                if (msg != null && msg.length() > 120) msg = msg.substring(0, 120);
                d.put("msg", msg != null ? msg : "");
                com.solar.launcher.deezer.DeezerDebugLog.log(context, "SolarWebServer.run",
                        "bind failed", "E", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch(Exception e){}
    }

    // IP on any active interface (Wi-Fi, mobile/eth0, Ethernet)
    public String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ipAddress = wm.getConnectionInfo().getIpAddress();
                if (ipAddress != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
                }
            }
        } catch (Exception ex) { }
        return "Unknown IP";
    }

    private class RequestHandler implements Runnable {
        private Socket socket;
        public RequestHandler(Socket socket) { this.socket = socket; }

        private String readHeaderLine(InputStream is) throws java.io.IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = is.read()) != -1) {
                if (c == '\r') continue;
                if (c == '\n') break;
                sb.append((char) c);
            }
            return sb.toString();
        }

        public void run() {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                String requestLine = readHeaderLine(is);
                if (requestLine == null || requestLine.isEmpty()) return;

                String[] parts = requestLine.split(" ");
                String method = parts[0];
                String path = parts[1];

                int contentLength = 0;
                String line;
                while (!(line = readHeaderLine(is)).isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    }
                }

                SharedPreferences prefs = context.getSharedPreferences(
                        DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
                DeezerClient client = new DeezerClient(prefs);

                if (method.equals("GET") && path.equals("/api/deezer/status")) {
                    boolean hasArl = DeezerAccount.isUserArlConfigured(prefs);
                    JSONObject res = new JSONObject();
                    res.put("authenticated", hasArl);
                    res.put("quality", DeezerAccount.loadQuality(prefs));
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + res.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/api/deezer/search")) {
                    String q = getQueryParam(path, "q");
                    String type = getQueryParam(path, "type");
                    JSONArray arr = new JSONArray();
                    if (q != null && !q.trim().isEmpty()) {
                        DeezerSearch search = new DeezerSearch(client);
                        if ("artist".equalsIgnoreCase(type)) {
                            List<DeezerSearch.DeezerArtist> artists = search.searchArtists(q);
                            for (DeezerSearch.DeezerArtist a : artists) {
                                JSONObject obj = new JSONObject();
                                obj.put("id", a.id);
                                obj.put("name", a.name);
                                obj.put("pictureUrl", a.pictureUrl);
                                arr.put(obj);
                            }
                        } else if ("album".equalsIgnoreCase(type)) {
                            List<DeezerSearch.DeezerAlbum> albums = search.searchAlbums(q);
                            for (DeezerSearch.DeezerAlbum alb : albums) {
                                JSONObject obj = new JSONObject();
                                obj.put("id", alb.id);
                                obj.put("title", alb.title);
                                obj.put("recordType", alb.recordType);
                                obj.put("trackCount", alb.trackCount);
                                obj.put("coverUrl", alb.coverUrl);
                                arr.put(obj);
                            }
                        } else {
                            List<DeezerResult> tracks = search.searchTracks(q);
                            for (DeezerResult r : tracks) {
                                JSONObject obj = new JSONObject();
                                obj.put("id", r.id);
                                obj.put("title", r.title);
                                obj.put("artist", r.artist);
                                obj.put("album", r.album);
                                obj.put("albumId", r.albumId);
                                obj.put("durationSec", r.durationSec);
                                obj.put("coverUrl", r.coverUrl);
                                arr.put(obj);
                            }
                        }
                    }
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + arr.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/api/deezer/artist/") && path.endsWith("/albums")) {
                    String[] segments = path.split("/");
                    JSONArray arr = new JSONArray();
                    if (segments.length >= 5) {
                        try {
                            long artistId = Long.parseLong(segments[4]);
                            DeezerSearch search = new DeezerSearch(client);
                            List<DeezerSearch.DeezerAlbum> albums = search.listArtistAlbums(artistId);
                            for (DeezerSearch.DeezerAlbum alb : albums) {
                                JSONObject obj = new JSONObject();
                                obj.put("id", alb.id);
                                obj.put("title", alb.title);
                                obj.put("recordType", alb.recordType);
                                obj.put("trackCount", alb.trackCount);
                                obj.put("coverUrl", alb.coverUrl);
                                arr.put(obj);
                            }
                        } catch (Exception ignored) {}
                    }
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + arr.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/api/deezer/album/")) {
                    String[] segments = path.split("/");
                    JSONArray arr = new JSONArray();
                    if (segments.length >= 4) {
                        try {
                            long albumId = Long.parseLong(segments[3]);
                            DeezerSearch search = new DeezerSearch(client);
                            List<DeezerResult> tracks = search.listAlbumTracks(albumId);
                            for (DeezerResult r : tracks) {
                                JSONObject obj = new JSONObject();
                                obj.put("id", r.id);
                                obj.put("title", r.title);
                                obj.put("artist", r.artist);
                                obj.put("album", r.album);
                                obj.put("albumId", r.albumId);
                                obj.put("durationSec", r.durationSec);
                                obj.put("coverUrl", r.coverUrl);
                                arr.put(obj);
                            }
                        } catch (Exception ignored) {}
                    }
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + arr.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("POST") && path.equals("/api/deezer/download")) {
                    byte[] bodyBytes = readBody(is, contentLength);
                    String bodyString = new String(bodyBytes, "UTF-8");
                    JSONObject json = new JSONObject(bodyString);
                    long trackId = json.getLong("id");
                    String title = json.optString("title", "");
                    String artist = json.optString("artist", "");
                    String album = json.optString("album", "");
                    long albumId = json.optLong("albumId", 0);
                    String coverUrl = json.optString("coverUrl", "");
                    String quality = json.optString("quality", null);

                    DeezerResult r = new DeezerResult(trackId, title, artist, album, albumId, 0, "", coverUrl);
                    String qualityFormat = null;
                    String ext = "mp3";
                    if (quality != null && !quality.isEmpty()) {
                        if ("flac".equalsIgnoreCase(quality)) {
                            qualityFormat = "FLAC";
                            ext = "flac";
                        } else if ("320".equals(quality) || "mp3_320".equalsIgnoreCase(quality)) {
                            qualityFormat = "MP3_320";
                            ext = "mp3";
                        } else if ("128".equals(quality) || "mp3_128".equalsIgnoreCase(quality)) {
                            qualityFormat = "MP3_128";
                            ext = "mp3";
                        }
                    }

                    String safeName = r.filenameBase() + "." + ext;
                    File dest = new File(rootFolder, safeName);
                    int n = 1;
                    while (dest.exists()) {
                        dest = new File(rootFolder, r.filenameBase() + " (" + n + ")." + ext);
                        n++;
                    }

                    DeezerBackgroundQueue.Job job = new DeezerBackgroundQueue.Job(r, dest, qualityFormat);
                    ArrayList<DeezerBackgroundQueue.Job> list = new ArrayList<DeezerBackgroundQueue.Job>();
                    list.add(job);

                    MainActivity mainActivity = null;
                    if (context instanceof MainActivity) {
                        mainActivity = (MainActivity) context;
                    }
                    if (mainActivity != null) {
                        mainActivity.getDeezerBackgroundQueue().enqueue(list);
                    }

                    JSONObject res = new JSONObject();
                    res.put("status", "queued");
                    res.put("dest", dest.getName());
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + res.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("POST") && path.equals("/api/deezer/download/album")) {
                    byte[] bodyBytes = readBody(is, contentLength);
                    String bodyString = new String(bodyBytes, "UTF-8");
                    JSONObject json = new JSONObject(bodyString);
                    long albumId = json.getLong("albumId");
                    String quality = json.optString("quality", null);

                    DeezerSearch search = new DeezerSearch(client);
                    List<DeezerResult> tracks = search.listAlbumTracks(albumId);
                    ArrayList<DeezerBackgroundQueue.Job> jobsToEnqueue = new ArrayList<DeezerBackgroundQueue.Job>();

                    String qualityFormat = null;
                    String ext = "mp3";
                    if (quality != null && !quality.isEmpty()) {
                        if ("flac".equalsIgnoreCase(quality)) {
                            qualityFormat = "FLAC";
                            ext = "flac";
                        } else if ("320".equals(quality) || "mp3_320".equalsIgnoreCase(quality)) {
                            qualityFormat = "MP3_320";
                            ext = "mp3";
                        } else if ("128".equals(quality) || "mp3_128".equalsIgnoreCase(quality)) {
                            qualityFormat = "MP3_128";
                            ext = "mp3";
                        }
                    }

                    for (DeezerResult r : tracks) {
                        String safeName = r.filenameBase() + "." + ext;
                        File dest = new File(rootFolder, safeName);
                        int n = 1;
                        while (dest.exists()) {
                            dest = new File(rootFolder, r.filenameBase() + " (" + n + ")." + ext);
                            n++;
                        }
                        jobsToEnqueue.add(new DeezerBackgroundQueue.Job(r, dest, qualityFormat));
                    }

                    MainActivity mainActivity = null;
                    if (context instanceof MainActivity) {
                        mainActivity = (MainActivity) context;
                    }
                    if (mainActivity != null && !jobsToEnqueue.isEmpty()) {
                        mainActivity.getDeezerBackgroundQueue().enqueue(jobsToEnqueue);
                    }

                    JSONObject res = new JSONObject();
                    res.put("status", "queued");
                    res.put("count", jobsToEnqueue.size());
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + res.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.equals("/api/deezer/download/status")) {
                    JSONArray arr = new JSONArray();
                    MainActivity mainActivity = null;
                    if (context instanceof MainActivity) {
                        mainActivity = (MainActivity) context;
                    }
                    if (mainActivity != null) {
                        List<DeezerBackgroundQueue.Job> activeJobs = mainActivity.getDeezerBackgroundQueue().getJobs();
                        for (DeezerBackgroundQueue.Job j : activeJobs) {
                            JSONObject obj = new JSONObject();
                            obj.put("id", j.track.id);
                            obj.put("title", j.track.title);
                            obj.put("artist", j.track.artist);
                            obj.put("status", j.status);
                            obj.put("progress", j.progress);
                            obj.put("error", j.error != null ? j.error : JSONObject.NULL);
                            if ("complete".equals(j.status) && j.dest != null) {
                                try {
                                    String rootCanon = rootFolder.getCanonicalPath();
                                    String destCanon = j.dest.getCanonicalPath();
                                    if (destCanon.startsWith(rootCanon + File.separator)) {
                                        obj.put("path", destCanon.substring(rootCanon.length() + 1));
                                    }
                                } catch (Exception ignored) {}
                            }
                            arr.put(obj);
                        }
                    }
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + arr.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.equals("/api/library")) {
                    JSONArray arr = new JSONArray();
                    try {
                        List<MusicLibraryStore.Track> tracks = MusicLibraryStore.getInstance(context).loadAll();
                        String rootCanon = rootFolder.getCanonicalPath();
                        for (MusicLibraryStore.Track t : tracks) {
                            String rel = t.path;
                            try {
                                String canon = new File(t.path).getCanonicalPath();
                                if (canon.equals(rootCanon)) rel = "";
                                else if (canon.startsWith(rootCanon + File.separator)) rel = canon.substring(rootCanon.length() + 1);
                            } catch (Exception ignored) {}
                            JSONObject obj = new JSONObject();
                            obj.put("path", rel);
                            obj.put("title", t.title != null && !t.title.isEmpty() ? t.title : new File(t.path).getName());
                            obj.put("artist", t.artist != null ? t.artist : "");
                            obj.put("album", t.album != null ? t.album : "");
                            obj.put("durationSec", t.durationSec());
                            arr.put(obj);
                        }
                    } catch (Exception ignored) {}
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + arr.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.equals("/api/now_playing")) {
                    JSONObject np = (context instanceof MainActivity)
                            ? ((MainActivity) context).getNowPlayingJson() : new JSONObject();
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n" + np.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.equals("/api/now_playing/art")) {
                    File artFile = null;
                    if (context instanceof MainActivity) {
                        JSONObject np = ((MainActivity) context).getNowPlayingJson();
                        String album = np.optString("album", "");
                        String artist = np.optString("artist", "");
                        if (np.optBoolean("playing", false) || !np.optString("title", "").isEmpty()) {
                            String key = com.solar.launcher.flow.FlowCoverResolver.albumMatchKey(album, artist);
                            File dir = com.solar.launcher.flow.AlbumArtCache.cacheDir(context);
                            if (com.solar.launcher.flow.AlbumArtCache.has(dir, key)) {
                                artFile = com.solar.launcher.flow.AlbumArtCache.fileForKey(dir, key);
                            }
                        }
                    }
                    if (artFile != null && artFile.exists()) {
                        byte[] bytes = new byte[(int) artFile.length()];
                        InputStream fis = new java.io.FileInputStream(artFile);
                        int off = 0, n;
                        while (off < bytes.length && (n = fis.read(bytes, off, bytes.length - off)) != -1) off += n;
                        fis.close();
                        os.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + bytes.length + "\r\n\r\n").getBytes("UTF-8"));
                        os.write(bytes);
                    } else {
                        os.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nNo art".getBytes("UTF-8"));
                    }
                }
                else if (method.equals("POST") && (path.equals("/api/transport/play_pause") || path.equals("/api/transport/next") || path.equals("/api/transport/prev"))) {
                    if (context instanceof MainActivity) {
                        final MainActivity ma = (MainActivity) context;
                        final String p = path;
                        ma.runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    if (p.endsWith("play_pause")) ma.playOrPauseMusic();
                                    else if (p.endsWith("next")) ma.nextTrack();
                                    else ma.prevTrack();
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n{\"status\":\"ok\"}";
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && (path.equals("/") || path.equals("/deezer_search") || path.equals("/deezer") || path.startsWith("/deezer?"))) {
                    String html = getSpaHtml();
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html;
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/create_folder")) {
                    String q = path.split("\\?")[1];
                    String name = URLDecoder.decode(q.split("=")[1], "UTF-8");
                    File newDir = new File(rootFolder, name);
                    newDir.mkdirs();
                    newDir.setReadable(true, false);
                    newDir.setExecutable(true, false);
                    try { Runtime.getRuntime().exec(new String[]{"chmod", "777", newDir.getAbsolutePath()}); } catch(Exception e){}

                    String response = "HTTP/1.1 200 OK\r\n\r\nOK";
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("POST") && (path.equals("/deezer") || path.startsWith("/deezer?"))) {
                        byte[] body = readBody(is, contentLength);
                        String bodyStr = new String(body, "UTF-8");
                        String arl = formValue(bodyStr, "arl");
                        String quality = formValue(bodyStr, "quality");
                        String msg = null;
                        if (arl == null || arl.trim().length() < 64) {
                            msg = "ARL cookie is too short. Log in at deezer.com, copy the arl cookie from DevTools.";
                        } else {
                            prefs = context.getSharedPreferences(
                                    DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
                            DeezerAccount.saveUserArl(prefs, arl.trim());
                            if (quality != null && !quality.isEmpty()) {
                                prefs.edit().putString(DeezerAccount.PREF_QUALITY, quality).commit();
                            }
                            // #region agent log
                            try {
                                String probeWww = com.solar.launcher.net.TlsHelper.probeProtocol(
                                        "https://www.deezer.com/");
                                String probeApi = com.solar.launcher.net.TlsHelper.probeProtocol(
                                        "https://api.deezer.com/");
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("arlLen", arl.trim().length());
                                d.put("tlsWww", probeWww != null ? probeWww : "fail");
                                d.put("tlsApi", probeApi != null ? probeApi : "fail");
                                com.solar.launcher.deezer.DeezerDebugLog.log(context,
                                        "SolarWebServer.deezer", "pre-test", "A", d);
                            } catch (Exception ignored) {}
                            // #endregion
                            client = new DeezerClient(prefs);
                            boolean ok = false;
                            try {
                                ok = client.initSession();
                                // #region agent log
                                try {
                                    org.json.JSONObject d2 = new org.json.JSONObject();
                                    d2.put("initOk", ok);
                                    d2.put("user", client.userName());
                                    com.solar.launcher.deezer.DeezerDebugLog.log(context,
                                            "SolarWebServer.deezer", "initSession", "B", d2);
                                } catch (Exception ignored) {}
                                // #endregion
                            } catch (java.io.IOException e) {
                                // #region agent log
                                try {
                                    org.json.JSONObject d2 = new org.json.JSONObject();
                                    d2.put("error", e.getClass().getSimpleName());
                                    String errMsg = e.getMessage();
                                    if (errMsg != null && errMsg.length() > 200) errMsg = errMsg.substring(0, 200);
                                    d2.put("msg", errMsg != null ? errMsg : "");
                                    com.solar.launcher.deezer.DeezerDebugLog.log(context,
                                            "SolarWebServer.deezer", "initSession fail", "B", d2);
                                } catch (Exception ignored) {}
                                // #endregion
                            }
                            ConnectivityHelper.setDeezerLoginOk(ok);
                            msg = ok ? "✅ Deezer login verified!" : "❌ Saved ARL but login test failed. Check the cookie.";
                        }
                        String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + msg;
                        os.write(response.getBytes("UTF-8"));
                    }
                else if (method.equals("GET") && path.equals("/scan_stats")) {
                    ScanPerfLog.LastScan last = ScanPerfLog.last();
                    org.json.JSONObject d = new org.json.JSONObject();
                    try {
                        if (last != null) {
                            d.put("timestamp", last.timestamp);
                            d.put("trackCount", last.trackCount);
                            d.put("totalMs", last.totalMs);
                            d.put("phases", new org.json.JSONObject(last.phaseBreakdown));
                            d.put("tracksPerSecond",
                                    last.totalMs > 0 ? (last.trackCount * 1000f / last.totalMs) : 0f);
                        } else {
                            d.put("timestamp", org.json.JSONObject.NULL);
                            d.put("trackCount", 0);
                            d.put("totalMs", 0);
                            d.put("phases", new org.json.JSONObject());
                            d.put("tracksPerSecond", 0f);
                        }
                    } catch (Exception ignored) {}
                    String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + d.toString();
                    os.write(response.getBytes("UTF-8"));
                }
                else if (method.equals("GET") && path.startsWith("/download")) {
                    File f = null;
                    try { f = resolveUnderRoot(getQueryParam(path, "path")); } catch (Exception ignored) {}
                    if (f == null || !f.exists() || !f.isFile()) {
                        os.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nNot found".getBytes("UTF-8"));
                    } else {
                        String headers = "HTTP/1.1 200 OK\r\nContent-Type: " + contentTypeFor(f.getName())
                                + "\r\nContent-Length: " + f.length()
                                + "\r\nContent-Disposition: attachment; filename=\"" + f.getName().replace("\"", "") + "\"\r\n\r\n";
                        os.write(headers.getBytes("UTF-8"));
                        streamFile(f, os);
                    }
                }
                else if (method.equals("GET") && path.startsWith("/stream")) {
                    File f = null;
                    try { f = resolveUnderRoot(getQueryParam(path, "path")); } catch (Exception ignored) {}
                    if (f == null || !f.exists() || !f.isFile()) {
                        os.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nNot found".getBytes("UTF-8"));
                    } else {
                        String headers = "HTTP/1.1 200 OK\r\nContent-Type: " + contentTypeFor(f.getName())
                                + "\r\nContent-Length: " + f.length() + "\r\n\r\n";
                        os.write(headers.getBytes("UTF-8"));
                        streamFile(f, os);
                    }
                }
                else if (method.equals("POST") && path.startsWith("/upload")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String folder = "ROOT", name = "unnamed.file";
                    for (String p : params) {
                        if (p.startsWith("folder=")) folder = URLDecoder.decode(p.substring(7), "UTF-8");
                        if (p.startsWith("name=")) name = URLDecoder.decode(p.substring(5), "UTF-8");
                    }

                    File targetDir = folder.equals("ROOT") ? rootFolder : new File(rootFolder, folder);
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                        targetDir.setReadable(true, false);
                        targetDir.setExecutable(true, false);
                        try { Runtime.getRuntime().exec(new String[]{"chmod", "777", targetDir.getAbsolutePath()}); } catch(Exception e){}
                    }
                    File outFile = new File(targetDir, name);

                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalRead = 0;
                    while (totalRead < contentLength && (bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }

                    fos.flush();
                    try { fos.getFD().sync(); } catch(Exception e){}
                    fos.close();

                    outFile.setReadable(true, false);
                    try { Runtime.getRuntime().exec(new String[]{"chmod", "777", outFile.getAbsolutePath()}); } catch(Exception e){}

                    String response = "HTTP/1.1 200 OK\r\n\r\nOK";
                    os.write(response.getBytes("UTF-8"));
                } else {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("method", method);
                        d.put("path", path);
                        com.solar.launcher.deezer.DeezerDebugLog.log(context,
                                "SolarWebServer.request", "404", "E", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    String response = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nNot found";
                    os.write(response.getBytes("UTF-8"));
                }
                os.flush();
            } catch (Exception e) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("error", e.getClass().getSimpleName());
                    com.solar.launcher.deezer.DeezerDebugLog.log(context,
                            "SolarWebServer.request", "handler error", "E", d);
                } catch (Exception ignored) {}
                // #endregion
            } finally {
                try { socket.close(); } catch (Exception e) {}
            }
        }

        private byte[] readBody(InputStream is, int contentLength) throws java.io.IOException {
            if (contentLength <= 0) return new byte[0];
            byte[] buf = new byte[contentLength];
            int total = 0;
            while (total < contentLength) {
                int n = is.read(buf, total, contentLength - total);
                if (n < 0) break;
                total += n;
            }
            return buf;
        }

        private String formValue(String body, String key) {
            if (body == null || key == null) return "";
            try {
                String[] pairs = body.split("&");
                for (String p : pairs) {
                    int eq = p.indexOf('=');
                    if (eq < 0) continue;
                    String k = URLDecoder.decode(p.substring(0, eq), "UTF-8");
                    if (key.equals(k)) {
                        return URLDecoder.decode(p.substring(eq + 1), "UTF-8");
                    }
                }
            } catch (Exception ignored) {}
            return "";
        }

        /** Resolves a user-supplied relative path under rootFolder, rejecting anything that escapes it. */
        private File resolveUnderRoot(String relPath) throws java.io.IOException {
            if (relPath == null || relPath.trim().isEmpty()) return null;
            File f = new File(rootFolder, relPath);
            String rootCanon = rootFolder.getCanonicalPath();
            String fCanon = f.getCanonicalPath();
            if (!fCanon.equals(rootCanon) && !fCanon.startsWith(rootCanon + File.separator)) return null;
            return f;
        }

        private String contentTypeFor(String fileName) {
            String n = fileName.toLowerCase(Locale.US);
            if (n.endsWith(".mp3")) return "audio/mpeg";
            if (n.endsWith(".flac")) return "audio/flac";
            if (n.endsWith(".m4a")) return "audio/mp4";
            if (n.endsWith(".wav")) return "audio/wav";
            if (n.endsWith(".ogg")) return "audio/ogg";
            return "application/octet-stream";
        }

        private void streamFile(File f, OutputStream os) throws java.io.IOException {
            InputStream fis = new java.io.FileInputStream(f);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) os.write(buffer, 0, n);
            fis.close();
        }

        private String getQueryParam(String path, String key) {
            int qIdx = path.indexOf('?');
            if (qIdx == -1) return null;
            String query = path.substring(qIdx + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int eqIdx = pair.indexOf('=');
                if (eqIdx == -1) continue;
                try {
                    String k = URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
                    if (k.equals(key)) {
                        return URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8");
                    }
                } catch (Exception ignored) {}
            }
            return null;
        }

        private String htmlEscape(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}