package com.solar.launcher.soulseek;

import java.util.List;

/** Outbound browse of a remote user's Soulseek shares. */
public final class SoulseekBrowseSession {

    public interface Callback {
        void onFolders(List<SoulseekWire.BrowseFolder> folders);
        void onError(String reason);
    }

    private SoulseekBrowseSession() {}

    public static void fetchShares(final SoulseekClient client, final String peerUser,
                                   final Callback callback) {
        if (client == null || peerUser == null || peerUser.trim().isEmpty()) {
            if (callback != null) callback.onError("No user");
            return;
        }
        client.runPeerOperation(peerUser.trim(), new SoulseekClient.PeerOperation() {
            @Override
            public void run(java.net.Socket peer) throws Exception {
                peer.getOutputStream().write(
                        SoulseekWire.peerMessage(SoulseekWire.PEER_SHARES_REQUEST, new byte[0]));
                peer.getOutputStream().flush();
                SoulseekWire.PeerFrame frame = SoulseekWire.readPeerFrame(peer.getInputStream());
                if (frame.code != SoulseekWire.PEER_SHARES_REPLY) {
                    throw new java.io.IOException("Unexpected browse response");
                }
                final List<SoulseekWire.BrowseFolder> folders =
                        SoulseekWire.parseShareList(frame.body);
                // #region agent log
                int fileCount = 0;
                for (SoulseekWire.BrowseFolder f : folders) {
                    if (f.files != null) fileCount += f.files.size();
                }
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("peerUser", peerUser);
                    d.put("folderCount", folders.size());
                    d.put("fileCount", fileCount);
                    d.put("bodyLen", frame.body != null ? frame.body.length : 0);
                    ReachDebugLog.log(null, "SoulseekBrowseSession.fetchShares",
                            "browse result", "H3-H5", d);
                } catch (Exception ignored) {}
                // #endregion
                client.notifyBrowseResult(callback, folders);
            }

            @Override
            public void onError(String reason) {
                client.notifyBrowseError(callback, reason);
            }
        });
    }

    public static void fetchFolder(final SoulseekClient client, final String peerUser,
                                   final String folder, final int token,
                                   final Callback callback) {
        if (client == null || peerUser == null || peerUser.trim().isEmpty()) {
            if (callback != null) callback.onError("No user");
            return;
        }
        client.runPeerOperation(peerUser.trim(), new SoulseekClient.PeerOperation() {
            @Override
            public void run(java.net.Socket peer) throws Exception {
                byte[] body = packFolderRequest(token, folder);
                peer.getOutputStream().write(
                        SoulseekWire.peerMessage(SoulseekWire.PEER_FOLDER_CONTENTS_REQUEST, body));
                peer.getOutputStream().flush();
                SoulseekWire.PeerFrame frame = SoulseekWire.readPeerFrame(peer.getInputStream());
                if (frame.code != SoulseekWire.PEER_FOLDER_CONTENTS_RESPONSE) {
                    throw new java.io.IOException("Unexpected folder response");
                }
                final List<SoulseekWire.BrowseFolder> folders =
                        SoulseekWire.parseFolderContents(frame.body);
                client.notifyBrowseResult(callback, folders);
            }

            @Override
            public void onError(String reason) {
                client.notifyBrowseError(callback, reason);
            }
        });
    }

    public static String parentFolder(String virtualPath) {
        return SoulseekWire.parentFolder(virtualPath);
    }

    private static byte[] packFolderRequest(int token, String folder) throws java.io.IOException {
        byte[] a = SoulseekWire.packUInt32(token);
        byte[] b = SoulseekWire.packString(folder != null ? folder : "");
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
