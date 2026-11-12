package com.example.galaxyimageautoclick;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class AutomationConfig {
    static final String PREF = "galaxy_click_settings";
    public static final class Step {
        public String file = "";
        public String name = "";
        public int clicks = 1;
        public int gapMs = 400;
        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject(); obj.put("file", file); obj.put("name", name);
            obj.put("clicks", clicks); obj.put("gapMs", gapMs); return obj;
        }
        public static Step fromJson(JSONObject obj) {
            Step s = new Step(); s.file = obj.optString("file"); s.name = obj.optString("name");
            s.clicks = Math.max(1, Math.min(100, obj.optInt("clicks", 1)));
            s.gapMs = Math.max(100, Math.min(5000, obj.optInt("gapMs", 400))); return s;
        }
    }
    public List<Step> steps = new ArrayList<>();
    public int randomPercent = 15; // each axis +/-15% of image width/height
    public int waitSeconds = 30;
    public int cycleCount = 0; // 0 = infinite
    public int scanMs = 650;
    public boolean alert = true;
    public boolean skipMissing = false;
    public boolean miniMonitor = true;

    public static AutomationConfig load(Context ctx) {
        AutomationConfig c = new AutomationConfig();
        try {
            String json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("config", "{}");
            JSONObject root = new JSONObject(json);
            c.randomPercent = Math.max(0, Math.min(45, root.optInt("random", 15)));
            c.waitSeconds = Math.max(3, Math.min(600, root.optInt("wait", 30)));
            c.cycleCount = Math.max(0, Math.min(10000, root.optInt("cycles", 0)));
            c.scanMs = Math.max(300, Math.min(5000, root.optInt("scan", 650)));
            c.alert = root.optBoolean("alert", true);
            c.skipMissing = root.optBoolean("skip", false);
            c.miniMonitor = root.optBoolean("mini", true);
            JSONArray a = root.optJSONArray("steps");
            if (a != null) for (int i=0; i<a.length(); i++) c.steps.add(Step.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return c;
    }
    public void save(Context ctx) {
        try {
            JSONObject root = new JSONObject(); root.put("random",randomPercent); root.put("wait",waitSeconds);
            root.put("cycles",cycleCount); root.put("scan",scanMs); root.put("alert",alert);
            root.put("skip",skipMissing); root.put("mini",miniMonitor);
            JSONArray a = new JSONArray(); for (Step step:steps) a.put(step.toJson()); root.put("steps",a);
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("config",root.toString()).apply();
        } catch (Exception ignored) {}
    }
    public static void status(Context ctx, String state, String log, int step, int success) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
        editor.putString("state", state).putInt("step", step).putInt("success", success);
        if(log != null) {
            String past = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("logs", "");
            String now = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date());
            String logs = now + "  " + log + "\n" + past;
            editor.putString("logs", logs.length() > 8000 ? logs.substring(0, 8000) : logs);
        }
        editor.apply();
    }
}
