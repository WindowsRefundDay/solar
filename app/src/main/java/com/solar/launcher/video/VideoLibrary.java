package com.solar.launcher.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Local video files on MicroSD: /storage/sdcard0/Videos and browsable parents. */
public final class VideoLibrary {
    public static final File DEVICE_ROOT = new File(com.solar.launcher.DeviceFeatures.getPrimaryStorageRoot().getAbsolutePath());
    public static final File ROOT = new File(com.solar.launcher.DeviceFeatures.getPrimaryStorageRoot(), "Videos");

    private static final String[] VIDEO_EXT = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".webm", ".ts", ".m4v"
    };

    /**
     * Cap recursion when probing folders for videos. Deep/wide trees can overflow Dalvik's
     * JNI local-reference table inside {@link File#listFiles()} (default limit 512 refs).
     */
    private static final int MAX_SCAN_DEPTH = 5;

    /** Prefer the bundled {@code find} binary; fallback paths are tried if this is missing. */
    private static final String[] FIND_PATHS = {"/system/xbin/find", "/system/bin/find"};

    /** ponytail: unit tests only — null restores live scan root. */
    static File rootOverride = null;
    /** ponytail: unit tests only — null restores DEVICE_ROOT. */
    static File deviceRootOverride = null;

    private VideoLibrary() {}

    static File scanRoot() {
        return rootOverride != null ? rootOverride : ROOT;
    }

    static File deviceRoot() {
        return deviceRootOverride != null ? deviceRootOverride : DEVICE_ROOT;
    }

    public static boolean isVideoFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String ext : VIDEO_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /** True when {@code dir} is device root or a folder under it. */
    public static boolean isBrowsablePath(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        String path = dir.getAbsolutePath();
        for (File root : com.solar.launcher.DeviceFeatures.getStorageRoots()) {
            String rootPath = root.getAbsolutePath();
            if (path.equals(rootPath) || path.startsWith(rootPath + File.separator)) {
                return true;
            }
        }
        return false;
    }

    /** Parent folder when still browsable; null at device root top. */
    public static File browseParent(File dir) {
        if (dir == null) return null;
        File parent = dir.getParentFile();
        if (parent != null && isBrowsablePath(parent)) return parent;
        return null;
    }

    /** All video files under {@link #ROOT}, recursive, sorted by path. */
    public static List<File> scanAll() {
        List<File> out = new ArrayList<File>();
        if (rootOverride != null) {
            if (rootOverride.isDirectory()) collectVideos(rootOverride, out);
        } else {
            for (File root : com.solar.launcher.DeviceFeatures.getVideoRoots()) {
                if (root.isDirectory()) collectVideos(root, out);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath());
            }
        });
        return out;
    }

    /** Video files in one folder (non-recursive), sorted by name. */
    public static List<File> listInFolder(File dir) {
        List<File> out = new ArrayList<File>();
        if (dir == null) return out;

        List<File> scanDirs = new ArrayList<File>();
        scanDirs.add(dir);

        String dirPath = dir.getAbsolutePath();
        for (File root : com.solar.launcher.DeviceFeatures.getVideoRoots()) {
            String rootPath = root.getAbsolutePath();
            if (dirPath.startsWith(rootPath + File.separator)) {
                String subPath = dirPath.substring(rootPath.length());
                for (File otherRoot : com.solar.launcher.DeviceFeatures.getVideoRoots()) {
                    if (otherRoot.equals(root)) continue;
                    File matchingDir = new File(otherRoot, subPath);
                    if (matchingDir.isDirectory()) {
                        scanDirs.add(matchingDir);
                    }
                }
                break;
            }
        }

        java.util.Set<String> seenNames = new java.util.HashSet<String>();
        for (File d : scanDirs) {
            File[] files = d.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && f.length() > 0 && isVideoFileName(f.getName())) {
                    if (seenNames.add(f.getName().toLowerCase(java.util.Locale.US))) {
                        out.add(f);
                    }
                }
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    /** Immediate child folders under root that contain at least one video file. */
    public static List<File> listFoldersWithVideos() {
        return listChildFoldersWithVideos(scanRoot());
    }

    /** Immediate child folders under {@code parent} that contain any video file. */
    public static List<File> listChildFoldersWithVideos(File parent) {
        List<File> out = new ArrayList<File>();
        if (parent == null) return out;

        List<File> scanDirs = new ArrayList<File>();
        if (parent.equals(ROOT)) {
            scanDirs.addAll(com.solar.launcher.DeviceFeatures.getVideoRoots());
        } else {
            scanDirs.add(parent);
        }

        java.util.Set<String> seenNames = new java.util.HashSet<String>();
        for (File dir : scanDirs) {
            if (!dir.isDirectory()) continue;
            File[] dirs = dir.listFiles();
            if (dirs == null) continue;
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                if (seenNames.add(d.getName().toLowerCase(java.util.Locale.US))) {
                    if (!listInFolder(d).isEmpty() || hasVideosRecursive(d)) out.add(d);
                }
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    private static void collectVideos(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isFile() && f.length() > 0 && isVideoFileName(f.getName())) {
                out.add(f);
            } else if (f.isDirectory()) {
                collectVideos(f, out);
            }
        }
    }

    private static boolean hasVideosRecursive(File dir) {
        return hasVideosRecursive(dir, MAX_SCAN_DEPTH);
    }

    private static boolean hasVideosRecursive(File dir, int depth) {
        if (depth <= 0 || dir == null || !dir.isDirectory()) return false;

        // Fast, safe path: let the system's find(1) do the recursion. This keeps the scan out of
        // the Dalvik VM's JNI local-reference table, which overflows when File.listFiles() hits a
        // directory with more than ~512 entries.
        Boolean found = hasVideosViaFind(dir, depth);
        if (found != null) return found;

        // Fallback for devices without find: stream directory entries via ls(1), which also avoids
        // the all-at-once allocation in File.listFiles().
        return hasVideosRecursiveFallback(dir, depth);
    }

    /** Null = find(1) unavailable/failed; caller should fall back. */
    private static Boolean hasVideosViaFind(File dir, int depth) {
        String findBin = null;
        for (String path : FIND_PATHS) {
            if (new File(path).exists()) {
                findBin = path;
                break;
            }
        }
        if (findBin == null) return null;

        List<String> cmd = new ArrayList<String>();
        cmd.add(findBin);
        cmd.add(dir.getAbsolutePath());
        cmd.add("-maxdepth");
        cmd.add(String.valueOf(depth));
        cmd.add("-type");
        cmd.add("f");
        cmd.add("-size");
        cmd.add("+0");
        cmd.add("(");
        for (int i = 0; i < VIDEO_EXT.length; i++) {
            cmd.add("-iname");
            cmd.add("*" + VIDEO_EXT[i]);
            if (i < VIDEO_EXT.length - 1) cmd.add("-o");
        }
        cmd.add(")");
        cmd.add("-print");
        cmd.add("-quit");

        Process p = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            return reader.readLine() != null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (p != null) p.destroy();
        }
    }

    private static boolean hasVideosRecursiveFallback(File dir, int depth) {
        if (depth <= 0 || dir == null) return false;
        String[] names = listDirNamesViaShell(dir);
        if (names == null) {
            // Last resort: File.listFiles() can abort Dalvik on huge directories.
            try {
                File[] children = dir.listFiles();
                if (children != null) {
                    for (File f : children) {
                        if (f.isFile() && f.length() > 0 && isVideoFileName(f.getName())) return true;
                        if (f.isDirectory() && hasVideosRecursiveFallback(f, depth - 1)) return true;
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        }
        for (String name : names) {
            if (isVideoFileName(name)) {
                File f = new File(dir, name);
                if (f.isFile() && f.length() > 0) return true;
            }
        }
        for (String name : names) {
            File f = new File(dir, name);
            if (f.isDirectory() && hasVideosRecursiveFallback(f, depth - 1)) return true;
        }
        return false;
    }

    private static String[] listDirNamesViaShell(File dir) {
        Process p = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"/system/bin/ls", "-1", dir.getAbsolutePath()});
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            List<String> names = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                names.add(line);
            }
            return names.toArray(new String[names.size()]);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (p != null) p.destroy();
        }
    }

    static void selfCheck() {
        if (!isVideoFileName("clip.MP4")) throw new AssertionError("mp4");
        if (!isVideoFileName("movie.mkv")) throw new AssertionError("mkv");
        if (isVideoFileName("notes.txt")) throw new AssertionError("txt");
        if (isVideoFileName(null)) throw new AssertionError("null");
    }
}
