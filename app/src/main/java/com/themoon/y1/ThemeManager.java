package com.themoon.y1;

import android.graphics.Color;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    // 💡 개별 테마의 색상과 [폴더 경로], [폰트] 정보를 담는 그릇
    public static class ThemeData {
        public String folderPath;
        public String name;
        public android.graphics.Typeface customFont; // 🚀 [추가] 폰트 데이터를 담을 공간
        public int textPrimary;
        public int textSecondary;
        public int bgOverlay;
        public int statusBarBg;
        public int btnNormal;
        public int btnFocused;
        public int btnFocusedText;

        public ThemeData(String folderPath, String name, android.graphics.Typeface customFont, int textPrimary, int textSecondary, int bgOverlay, int statusBarBg, int btnNormal, int btnFocused, int btnFocusedText) {
            this.folderPath = folderPath;
            this.name = name;
            this.customFont = customFont; // 🚀 추가됨
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.bgOverlay = bgOverlay;
            this.statusBarBg = statusBarBg;
            this.btnNormal = btnNormal;
            this.btnFocused = btnFocused;
            this.btnFocusedText = btnFocusedText;
        }
    }
    // 🚀 [추가] 현재 테마의 폰트를 가져오는 마법의 함수
    public static android.graphics.Typeface getCustomFont() {
        if (availableThemes.isEmpty()) return android.graphics.Typeface.DEFAULT;
        return availableThemes.get(currentThemeIndex).customFont;
    }
    public static List<ThemeData> availableThemes = new ArrayList<>();
    private static int currentThemeIndex = 0;

    public static int getStatusBarBackgroundColor() {
        if (availableThemes.isEmpty()) return 0x66000000;
        return availableThemes.get(currentThemeIndex).statusBarBg;
    }

    // 🚀 [핵심] Y1_Themes 폴더 안의 '서브 폴더'들을 뒤져서 config.json을 찾습니다!
    public static void loadThemesFromStorage(File themeFolder) {
        availableThemes.clear();

        // 1. 기본 다크 테마 (얘는 실제 폴더가 없으므로 경로는 "default"로 지정)
        // 🚀 [수정] 3번째 자리에 기본 폰트(Typeface.DEFAULT)를 추가해서 갯수를 딱 맞춰줍니다!
        availableThemes.add(new ThemeData("default", "Dark (Default)", android.graphics.Typeface.DEFAULT,
                0xFFFFFFFF, 0xFF888888, 0x88000000, 0x88000000, 0x15FFFFFF, 0xDDFFFFFF, 0xFF000000));
        // 2. 최상위 테마 폴더가 없으면 만들고 샘플 폴더를 생성해 줍니다.
        if (!themeFolder.exists()) {
            themeFolder.mkdirs();
            createSampleThemeFolder(themeFolder); // 🚀 함수 이름 변경됨
        }

        // 3. Y1_Themes 안의 항목들을 하나씩 검사합니다.
        File[] folders = themeFolder.listFiles();
        if (folders != null) {
            for (File subFolder : folders) {
                // 🚀 [수정] 단순 파일이 아니라 '폴더'일 경우에만 안으로 들어갑니다!
                if (subFolder.isDirectory()) {
                    // 폴더 안에서 config.json 파일을 찾습니다.
                    File configFile = new File(subFolder, "config.json");

                    if (configFile.exists() && configFile.isFile()) {
                        try {
                            FileInputStream fis = new FileInputStream(configFile);
                            byte[] data = new byte[(int) configFile.length()];
                            fis.read(data);
                            fis.close();

                            String jsonStr = new String(data, "UTF-8");
                            JSONObject json = new JSONObject(jsonStr);

                            int parsedOverlayBg = Color.parseColor(json.getString("bgOverlay"));
                            int parsedStatusBarBg = json.has("statusBarBg") ?
                                    Color.parseColor(json.getString("statusBarBg")) : parsedOverlayBg;

                            // 🚀 [추가] json에 "font" 설정이 있는지 확인하고, 있으면 파일을 찾아 폰트로 변환합니다!
                            android.graphics.Typeface parsedFont = android.graphics.Typeface.DEFAULT;
                            if (json.has("font")) {
                                File fontFile = new File(subFolder, json.getString("font"));
                                if (fontFile.exists() && fontFile.isFile()) {
                                    try {
                                        parsedFont = android.graphics.Typeface.createFromFile(fontFile);
                                    } catch (Exception e) {}
                                }
                            }

                            ThemeData theme = new ThemeData(
                                    subFolder.getAbsolutePath(),
                                    json.getString("name"),
                                    parsedFont, // 🚀 폰트 데이터를 장착!
                                    Color.parseColor(json.getString("textPrimary")),
                                    Color.parseColor(json.getString("textSecondary")),
                                    parsedOverlayBg,
                                    parsedStatusBarBg,
                                    Color.parseColor(json.getString("btnNormal")),
                                    Color.parseColor(json.getString("btnFocused")),
                                    Color.parseColor(json.getString("btnFocusedText"))
                            );

                            availableThemes.add(theme);
                        } catch (Exception e) {
                            // 형식이 잘못된 json은 조용히 무시합니다.
                        }
                    }
                }
            }
        }
    }

    // 💡 샘플 테마 '폴더'와 그 안의 config.json을 생성하는 함수
    private static void createSampleThemeFolder(File rootFolder) {
        try {
            // 🚀 1. Ocean_Blue 라는 서브 폴더를 먼저 만듭니다.
            File sampleFolder = new File(rootFolder, "Ocean_Blue");
            if (!sampleFolder.exists()) sampleFolder.mkdirs();

            // 🚀 2. 그 폴더 안에 config.json 이라는 이름으로 파일을 저장합니다.
            File configFile = new File(sampleFolder, "config.json");
            String json = "{\n" +
                    "  \"name\": \"Ocean Blue\",\n" +
                    "  \"font\": \"font.ttf\",\n" +
                    "  \"textPrimary\": \"#FFFFFF\",\n" +
                    "  \"textSecondary\": \"#88AADD\",\n" +
                    "  \"bgOverlay\": \"#DD0F172A\",\n" +
                    "  \"statusBarBg\": \"#99002255\",\n" +
                    "  \"btnNormal\": \"#221E40AF\",\n" +
                    "  \"btnFocused\": \"#DD3B82F6\",\n" +
                    "  \"btnFocusedText\": \"#000000\"\n" +
                    "}";
            FileOutputStream fos = new FileOutputStream(configFile);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {}
    }

    public static void setThemeIndex(int index) {
        if (index >= 0 && index < availableThemes.size()) currentThemeIndex = index;
        else currentThemeIndex = 0;
    }

    public static int getCurrentThemeIndex() { return currentThemeIndex; }
    public static ThemeData getCurrentTheme() { return availableThemes.get(currentThemeIndex); }

    public static int getTextColorPrimary() { return getCurrentTheme().textPrimary; }
    public static int getTextColorSecondary() { return getCurrentTheme().textSecondary; }
    public static int getOverlayBackgroundColor() { return getCurrentTheme().bgOverlay; }
    public static int getListButtonNormalBg() { return getCurrentTheme().btnNormal; }
    public static int getListButtonFocusedBg() { return getCurrentTheme().btnFocused; }
    public static int getListButtonFocusedTextColor() { return getCurrentTheme().btnFocusedText; }
}