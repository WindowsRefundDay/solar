package com.solar.launcher.net;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/** Validates Let's Encrypt + TLS negotiation on real API 17 Dalvik + Conscrypt JNI. */
@RunWith(AndroidJUnit4.class)
public class TlsHelperDeviceTest {
    @Test
    public void device_letsEncryptIsrgChain() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TlsHelper.init(ctx.getApplicationContext());
        String protocol = TlsHelper.probeProtocol("https://valid-isrgrootx1.letsencrypt.org/");
        if (protocol == null || !protocol.startsWith("TLS")) {
            throw new AssertionError("LE handshake failed, protocol=" + protocol);
        }
    }

    @Test
    public void device_letsEncryptRssFeed() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TlsHelper.init(ctx.getApplicationContext());
        HttpsURLConnection conn = (HttpsURLConnection) new URL(
                "https://feeds.simplecast.com/Y8_HoeNW").openConnection();
        conn.setSSLSocketFactory(TlsHelper.socketFactory());
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("HEAD");
        int code = conn.getResponseCode();
        conn.disconnect();
        if (code < 200 || code >= 400) throw new AssertionError("HTTP " + code);
    }

    /** Logs TLS 1.3 capability; LE must pass, tls13-only host is informational. */
    @Test
    public void device_tls13Probe() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TlsHelper.init(ctx.getApplicationContext());
        String le = TlsHelper.probeProtocol("https://valid-isrgrootx1.letsencrypt.org/");
        String t13 = TlsHelper.probeProtocol("https://tls13.1d.pw/");
        android.util.Log.i("SolarTlsTest", "probe LE=" + le + " tls13-only=" + t13);
        if (le == null || !le.startsWith("TLS")) {
            throw new AssertionError("LE probe failed: " + le);
        }
    }

    /** Deezer uses Gandi/USERTrust chain — often missing on API 17 stock trust store. */
    @Test
    public void device_deezerTlsProbe() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TlsHelper.init(ctx.getApplicationContext());
        String www = TlsHelper.probeProtocol("https://www.deezer.com/");
        String api = TlsHelper.probeProtocol("https://api.deezer.com/");
        org.json.JSONObject d = new org.json.JSONObject();
        d.put("tlsWww", www != null ? www : "fail");
        d.put("tlsApi", api != null ? api : "fail");
        com.solar.launcher.deezer.DeezerDebugLog.log(ctx, "TlsHelperDeviceTest",
                "deezer probe", "A", d);
        android.util.Log.i("SolarTlsTest", "deezer www=" + www + " api=" + api);
        if (www == null || !www.startsWith("TLS")) {
            throw new AssertionError("www.deezer.com TLS failed: " + www);
        }
        if (api == null || !api.startsWith("TLS")) {
            throw new AssertionError("api.deezer.com TLS failed: " + api);
        }
    }
}
