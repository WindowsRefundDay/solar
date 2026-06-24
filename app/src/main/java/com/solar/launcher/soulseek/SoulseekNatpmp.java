package com.solar.launcher.soulseek;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.solar.launcher.ConnectivityHelper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** NAT-PMP TCP port map (RFC 6886) with gateway + local-IP fallbacks for home routers. */
public final class SoulseekNatpmp {
  private static final int NATPMP_PORT = 5351;
  /** Long lease — renewed at half-life by {@link SoulseekClient}. */
  private static final int LEASE_SEC = 7200;
  private static final int ATTEMPTS_PER_GATEWAY = 4;
  private static final int INIT_TIMEOUT_MS = 400;

  public static final class Result {
    public final int privatePort;
    public final int publicPort;
    public final String externalIp;
    public final String gatewayIp;
    public final String status;

    public Result(int privatePort, int publicPort, String externalIp, String gatewayIp, String status) {
      this.privatePort = privatePort;
      this.publicPort = publicPort;
      this.externalIp = externalIp;
      this.gatewayIp = gatewayIp;
      this.status = status;
    }

    public boolean mapped() {
      return "ok".equals(status);
    }
  }

  private SoulseekNatpmp() {}

  static Result tryMapTcpPort(Context ctx, int privatePort) {
    if (privatePort <= 0 || privatePort > 65535) {
      return new Result(privatePort, privatePort, null, null, "bad_port");
    }
    try {
      InetAddress local = localIp(ctx);
      if (local == null) return new Result(privatePort, privatePort, null, null, "no_local_ip");

      List<InetAddress> gateways = gatewayCandidates(ctx, local);
      if (gateways.isEmpty()) {
        return new Result(privatePort, privatePort, null, null, "no_gateway");
      }

      String externalIp = null;
      String usedGateway = null;
      for (InetAddress gateway : gateways) {
        externalIp = requestExternalIp(local, gateway);
        int mapped = requestTcpMap(local, gateway, privatePort);
        if (mapped > 0) {
          return new Result(privatePort, mapped, externalIp, gateway.getHostAddress(), "ok");
        }
        usedGateway = gateway.getHostAddress();
      }
      return new Result(privatePort, privatePort, externalIp, usedGateway, "map_failed");
    } catch (Exception e) {
      return new Result(privatePort, privatePort, null, null, "error");
    }
  }

  static List<InetAddress> gatewayCandidates(Context ctx, InetAddress local) {
    Set<String> seen = new LinkedHashSet<String>();
    List<InetAddress> out = new ArrayList<InetAddress>();
    addGateway(out, seen, gatewayFromProcRoute());
    addGateway(out, seen, gatewayFromDhcp(ctx));
    addGateway(out, seen, subnetGateway(local, 1));
    addGateway(out, seen, subnetGateway(local, 254));
    return out;
  }

  private static void addGateway(List<InetAddress> out, Set<String> seen, InetAddress gw) {
    if (gw == null) return;
    String host = gw.getHostAddress();
    if (host == null || host.isEmpty() || seen.contains(host)) return;
    seen.add(host);
    out.add(gw);
  }

  private static String requestExternalIp(InetAddress local, InetAddress gateway) {
    byte[] req = new byte[] {0, 0};
    byte[] resp = udpExchange(local, gateway, req, 12);
    if (resp == null || resp.length < 12 || resp[1] != (byte) 128 || readU16(resp, 2) != 0) return null;
    return String.format(Locale.US,
        "%d.%d.%d.%d", resp[8] & 0xff, resp[9] & 0xff, resp[10] & 0xff, resp[11] & 0xff);
  }

  private static int requestTcpMap(InetAddress local, InetAddress gateway, int privatePort) {
    byte[] req = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        .put((byte) 0)
        .put((byte) 2)
        .putShort((short) 0)
        .putShort((short) privatePort)
        .putShort((short) privatePort)
        .putInt(LEASE_SEC)
        .array();
    byte[] resp = udpExchange(local, gateway, req, 16);
    if (resp == null || resp.length < 12) return -1;
    if (resp[1] != (byte) (2 + 128) || readU16(resp, 2) != 0) return -1;
    return readU16(resp, 10);
  }

  private static byte[] udpExchange(InetAddress local, InetAddress gateway, byte[] req, int expectLen) {
    DatagramSocket sock = null;
    try {
      sock = new DatagramSocket(0, local);
      int timeout = INIT_TIMEOUT_MS;
      for (int i = 0; i < ATTEMPTS_PER_GATEWAY; i++) {
        sock.setSoTimeout(timeout);
        sock.send(new DatagramPacket(req, req.length, gateway, NATPMP_PORT));
        byte[] buf = new byte[Math.max(expectLen, 16)];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        try {
          sock.receive(resp);
          byte[] out = new byte[resp.getLength()];
          System.arraycopy(buf, 0, out, 0, resp.getLength());
          return out;
        } catch (java.net.SocketTimeoutException ignored) {
          timeout = Math.min(timeout * 2, 2000);
        }
      }
    } catch (Exception ignored) {
    } finally {
      if (sock != null) sock.close();
    }
    return null;
  }

  private static int readU16(byte[] b, int off) {
    return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
  }

  static InetAddress localIp(Context ctx) {
    InetAddress wifi = localIpFromWifi(ctx);
    if (wifi != null) return wifi;
    try {
      String ip = ConnectivityHelper.localIpv4(ctx);
      if (ip != null) return InetAddress.getByName(ip);
    } catch (Exception ignored) {}
    return null;
  }

  private static InetAddress localIpFromWifi(Context ctx) {
    if (ctx == null) return null;
    try {
      WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
      if (info == null) return null;
      int ip = info.getIpAddress();
      if (ip == 0) return null;
      return intToInet(ip);
    } catch (Exception ignored) {
      return null;
    }
  }

  static InetAddress gatewayFromDhcp(Context ctx) {
    if (ctx == null) return null;
    try {
      WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      DhcpInfo dhcp = wm != null ? wm.getDhcpInfo() : null;
      if (dhcp == null || dhcp.gateway == 0) return null;
      return intToInet(dhcp.gateway);
    } catch (Exception ignored) {
      return null;
    }
  }

  static InetAddress subnetGateway(InetAddress local, int lastOctet) {
    if (local == null || lastOctet < 1 || lastOctet > 254) return null;
    byte[] addr = local.getAddress();
    if (addr == null || addr.length != 4) return null;
    if ((addr[0] & 0xff) == 10) {
      // 10.x.x.1 is common on carrier-grade NAT hotspots
    } else if ((addr[0] & 0xff) == 172 && (addr[1] & 0xff) >= 16 && (addr[1] & 0xff) <= 31) {
      // 172.16+
    } else if ((addr[0] & 0xff) == 192 && (addr[1] & 0xff) == 168) {
      // typical home LAN
    } else if ((addr[0] & 0xff) == 169 && (addr[1] & 0xff) == 254) {
      return null;
    }
    try {
      return InetAddress.getByAddress(new byte[] {addr[0], addr[1], addr[2], (byte) lastOctet});
    } catch (Exception ignored) {
      return null;
    }
  }

  static InetAddress gatewayFromProcRoute() {
    try {
      java.io.BufferedReader br = new java.io.BufferedReader(
          new java.io.FileReader("/proc/net/route"));
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 3) continue;
        if (!"00000000".equals(p[1])) continue;
        int hex = (int) Long.parseLong(p[2], 16);
        br.close();
        return intToInet(hex);
      }
      br.close();
    } catch (Exception ignored) {}
    return null;
  }

  private static InetAddress intToInet(int ip) throws java.net.UnknownHostException {
    return InetAddress.getByAddress(new byte[] {
        (byte) (ip & 0xff), (byte) ((ip >> 8) & 0xff),
        (byte) ((ip >> 16) & 0xff), (byte) ((ip >> 24) & 0xff)
    });
  }
}
