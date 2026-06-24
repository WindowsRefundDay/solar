package com.solar.launcher.soulseek;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Embedded 3–5 letter English words for Reach auto usernames. */
public final class SoulseekWordDictionary {
    private static final String ASSET = "reach-dictionary.txt";
    private static List<String> words;

    private SoulseekWordDictionary() {}

    public static synchronized void ensureLoaded(Context context) {
        if (words != null && !words.isEmpty()) return;
        List<String> loaded = new ArrayList<String>();
        if (context != null) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(
                        context.getAssets().open(ASSET), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null) {
                    String w = line.trim().toLowerCase();
                    if (isValidWord(w)) loaded.add(w);
                }
            } catch (Exception ignored) {
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
            }
        }
        if (loaded.isEmpty()) {
            loaded.addAll(fallbackWords());
        }
        words = loaded;
    }

    public static String pickWord(Context context, byte[] hash, int wordIndex) {
        ensureLoaded(context);
        if (words == null || words.isEmpty()) return "sun";
        int h = 0;
        if (hash != null) {
            for (int i = 0; i < 4; i++) {
                int idx = (wordIndex * 4 + i) % hash.length;
                h = (h << 8) | (hash[idx] & 0xff);
            }
        }
        int pick = Math.abs(h) % words.size();
        return words.get(pick);
    }

    static boolean isValidWord(String w) {
        if (w == null) return false;
        int len = w.length();
        if (len < 3 || len > 5) return false;
        for (int i = 0; i < len; i++) {
            char c = w.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private static List<String> fallbackWords() {
        String[] arr = {
                "ace", "act", "add", "age", "ago", "aid", "aim", "air", "all", "and",
                "ant", "any", "ape", "apt", "arc", "ark", "arm", "art", "ash", "ask",
                "bad", "bag", "ban", "bar", "bat", "bay", "bed", "bee", "beg", "bet",
                "big", "bin", "bit", "bog", "bow", "box", "boy", "bud", "bug", "bun",
                "bus", "but", "buy", "cab", "can", "cap", "car", "cat", "cod", "cog",
                "cop", "cot", "cow", "cub", "cup", "cut", "dab", "dad", "dam", "day",
                "den", "dew", "did", "die", "dig", "dim", "din", "dip", "dog", "dot",
                "dry", "dub", "dud", "due", "dug", "dun", "duo", "dye", "ear", "eat",
                "ebb", "egg", "ego", "elf", "elk", "elm", "emu", "end", "era", "eve",
                "eye", "fad", "fan", "far", "fat", "fed", "fee", "fen", "few", "fib",
                "fig", "fin", "fir", "fit", "fix", "flu", "fly", "fob", "foe", "fog",
                "for", "fox", "fry", "fun", "fur", "gag", "gal", "gap", "gas", "gel",
                "gem", "get", "gig", "gin", "gnu", "god", "got", "gum", "gun", "gut",
                "guy", "gym", "had", "hag", "ham", "has", "hat", "hay", "hem", "hen",
                "her", "hew", "hex", "hey", "hid", "him", "hip", "his", "hit", "hob",
                "hog", "hop", "hot", "how", "hub", "hue", "hug", "hum", "hut", "ice",
                "icy", "ill", "imp", "ink", "inn", "ion", "ire", "irk", "ivy", "jab",
                "jag", "jam", "jar", "jaw", "jay", "jet", "jig", "job", "jog", "jot",
                "joy", "jug", "jut", "keg", "ken", "key", "kid", "kin", "kit", "lab",
                "lad", "lag", "lam", "lap", "law", "lax", "lay", "led", "leg", "let",
                "lid", "lie", "lip", "lit", "log", "lot", "low", "lug", "lux", "lye",
                "mad", "man", "map", "mar", "mat", "maw", "max", "may", "men", "met",
                "mid", "mix", "mob", "mod", "mop", "mud", "mug", "mum", "nab", "nag",
                "nap", "net", "new", "nib", "nil", "nip", "nit", "nod", "nor", "not",
                "now", "nun", "nut", "oak", "oar", "oat", "odd", "off", "oft", "oil",
                "old", "one", "opt", "orb", "ore", "our", "out", "ova", "owe", "owl",
                "own", "pad", "pal", "pan", "par", "pat", "paw", "pay", "pea", "peg",
                "pen", "pep", "per", "pet", "pew", "pie", "pig", "pin", "pit", "ply",
                "pod", "pop", "pot", "pry", "pub", "pun", "pup", "put", "rag", "ram",
                "ran", "rap", "rat", "raw", "ray", "red", "ref", "rib", "rid", "rig",
                "rim", "rip", "rob", "rod", "rot", "row", "rub", "rug", "rum", "run",
                "rut", "rye", "sac", "sad", "sag", "sap", "sat", "saw", "sax", "say",
                "sea", "set", "sew", "she", "shy", "sin", "sip", "sir", "sit", "six",
                "ski", "sky", "sly", "sob", "sod", "son", "sop", "sow", "soy", "spa",
                "spy", "sty", "sub", "sue", "sum", "sun", "sup", "tab", "tad", "tag",
                "tan", "tap", "tar", "tax", "tea", "ten", "the", "thy", "tic", "tie",
                "tin", "tip", "toe", "ton", "too", "top", "tot", "tow", "toy", "try",
                "tub", "tug", "two", "urn", "use", "van", "vat", "vet", "vie", "vim",
                "vow", "wad", "wag", "war", "was", "wax", "way", "web", "wed", "wee",
                "wet", "who", "why", "wig", "win", "wit", "woe", "wok", "won", "woo",
                "wow", "wry", "yak", "yam", "yap", "yaw", "yea", "yen", "yes", "yet",
                "yew", "you", "zap", "zen", "zip", "zoo", "plum", "wave", "sage", "mint",
                "fern", "moss", "reef", "tide", "glow", "dusk", "haze", "mist", "calm",
                "bold", "keen", "warm", "cool", "dark", "pale", "rich", "soft", "loud",
                "fast", "slow", "high", "deep", "wide", "thin", "flat", "neat", "trim"
        };
        List<String> list = new ArrayList<String>(arr.length);
        for (String w : arr) list.add(w);
        return list;
    }
}
