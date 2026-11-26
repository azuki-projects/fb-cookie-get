package com.dansapk.nodejsrunner;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PORT = 38271;
    private final int BG = Color.rgb(8, 11, 9);
    private final int PANEL = Color.rgb(18, 26, 21);
    private final int GREEN = Color.rgb(0, 220, 105);
    private LinearLayout root, page;
    private EditText editor;
    private TextView terminal;
    private File projectsDir, projectDir, currentFile;
    private String currentProject = "MyProject";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        projectsDir = new File(getFilesDir(), "projects");
        projectsDir.mkdirs();
        ensureProject("MyProject");
        startRunnerService();
        showProjects();
    }

    private void startRunnerService() {
        Intent i = new Intent(this, RunnerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void padding(View v, int n) { v.setPadding(dp(n), dp(n), dp(n), dp(n)); }
    private Button action(String title, Runnable run) {
        Button b = new Button(this);
        b.setText(title); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackground(shape(Color.rgb(26, 48, 34), 12));
        b.setOnClickListener(v -> run.run());
        return b;
    }
    private void shell(String title) {
        root = vertical(); root.setBackgroundColor(BG); padding(root, 14);
        setContentView(root);

        LinearLayout top = horizontal();
        TextView brand = label("NODEJS RUNNER", 17, GREEN);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Switch keepAwake = new Switch(this);
        keepAwake.setText("Keep awake");
        keepAwake.setTextSize(11);
        keepAwake.setTextColor(Color.LTGRAY);
        boolean enabled = getSharedPreferences("runner", MODE_PRIVATE).getBoolean("keep_awake", true);
        keepAwake.setChecked(enabled);
        keepAwake.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences("runner", MODE_PRIVATE).edit().putBoolean("keep_awake", checked).apply();
            Intent i = new Intent(this, RunnerService.class);
            i.setAction("SET_WAKE");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            toast(checked ? "Wake lock enabled" : "Wake lock disabled");
        });
        top.addView(keepAwake);
        root.addView(top);

        TextView heading = label(title, 13, Color.LTGRAY);
        heading.setPadding(0, dp(12), 0, dp(7)); root.addView(heading);
        TextView status = label("● Embedded Node.js Mobile · local workspace", 11, GREEN);
        root.addView(status);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(35, 54, 41));
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.setMargins(0, dp(12), 0, dp(12)); root.addView(divider, line);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = vertical(); scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = horizontal();
        nav.setPadding(0, dp(10), 0, 0);
        nav.addView(action("Projects", this::showProjects), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(action("Files", this::showFiles), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(action("Editor", this::showEditor), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(action("Terminal", this::showTerminal), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);
    }

    private void showProjects() {
        stopPolling();
        shell("Projects");
        LinearLayout controls = horizontal();
        controls.addView(action("+ New project", this::newProject), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(action("Open editor", this::showEditor), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(controls);
        File[] dirs = projectsDir.listFiles();
        if (dirs == null || dirs.length == 0) {
            page.addView(label("No projects yet. Create one to get started.", 14, Color.LTGRAY));
            return;
        }
        Arrays.sort(dirs, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : dirs) {
            if (!f.isDirectory()) continue;
            LinearLayout card = vertical();
            card.setBackground(shape(PANEL, 14)); padding(card, 13);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(10); page.addView(card, cp);
            TextView name = label("▣  " + f.getName(), 16, Color.WHITE);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); card.addView(name);
            TextView details = label(countFiles(f) + " files · local storage", 11, Color.GRAY);
            details.setPadding(0, dp(5), 0, dp(10)); card.addView(details);
            LinearLayout buttons = horizontal();
            buttons.addView(action("Open", () -> selectProject(f)), new LinearLayout.LayoutParams(0, -2, 1));
            buttons.addView(action("Delete", () -> deleteProject(f)), new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(buttons);
        }
    }

    private void selectProject(File f) {
        projectDir = f; currentProject = f.getName();
        currentFile = new File(projectDir, "index.js");
        if (!currentFile.exists()) write(currentFile, "console.log('Hello from NodeJS Runner');\n");
        showEditor();
    }

    private void newProject() {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("Project name");
        new AlertDialog.Builder(this).setTitle("Create project").setView(input)
            .setPositiveButton("Create", (d,w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.contains("/") || name.contains("\\\\") || name.equals(".") || name.equals("..")) {
                    toast("Invalid project name"); return;
                }
                File f = new File(projectsDir, name);
                if (f.exists()) { toast("Project already exists"); return; }
                ensureProject(name); selectProject(f);
            }).setNegativeButton("Cancel", null).show();
    }

    private void ensureProject(String name) {
        File dir = new File(projectsDir, name); dir.mkdirs();
        File index = new File(dir, "index.js");
        if (!index.exists()) write(index, "console.log('Hello from NodeJS Runner!');\nconsole.log('Node:', process.version);\n");
        File pkg = new File(dir, "package.json");
        if (!pkg.exists()) write(pkg, "{\n  \"name\": \"" + name.toLowerCase().replaceAll("[^a-z0-9-]", "-") +
            "\",\n  \"version\": \"1.0.0\",\n  \"main\": \"index.js\",\n  \"dependencies\": {}\n}\n");
        if (projectDir == null) { projectDir = dir; currentProject = name; currentFile = index; }
    }

    private void deleteProject(File f) {
        new AlertDialog.Builder(this).setTitle("Delete " + f.getName() + "?")
            .setMessage("This deletes the project and its local files.")
            .setPositiveButton("Delete", (d,w) -> {
                deleteTree(f);
                if (f.equals(projectDir)) { projectDir = null; currentFile = null; }
                showProjects();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showFiles() {
        stopPolling();
        ensureSelection();
        shell("Files · " + currentProject);
        page.addView(action("+ New file", this::newFile));
        listFiles(projectDir);
    }

    private void listFiles(File dir) {
        File[] items = dir.listFiles();
        if (items == null) return;
        Arrays.sort(items, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : items) {
            if (f.isDirectory()) {
                TextView folder = label("▸ " + f.getName() + "/", 13, GREEN);
                folder.setPadding(dp(8), dp(9), 0, dp(4)); page.addView(folder);
                listFiles(f);
            } else {
                LinearLayout row = horizontal();
                row.setBackground(shape(PANEL, 9)); padding(row, 9);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(5); page.addView(row, rp);
                TextView name = label("▤  " + f.getName(), 13, Color.WHITE);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(action("Edit", () -> { currentFile = f; showEditor(); }));
            }
        }
    }

    private void newFile() {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("filename.js");
        new AlertDialog.Builder(this).setTitle("New file").setView(input)
            .setPositiveButton("Create", (d,w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.contains("/") || name.contains("\\\\")) { toast("Invalid filename"); return; }
                File f = new File(projectDir, name);
                if (f.exists()) { toast("File already exists"); return; }
                write(f, ""); currentFile = f; showEditor();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showEditor() {
        stopPolling();
        ensureSelection();
        shell("Editor · " + currentProject);
        LinearLayout controls = horizontal();
        controls.addView(action("Save", this::saveEditor), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(action("Run ▶", this::runFile), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(action("Stop ■", this::stopFile), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(controls);
        TextView fileLabel = label(currentFile.getName(), 12, GREEN);
        fileLabel.setPadding(0, dp(12), 0, dp(6)); page.addView(fileLabel);
        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.LEFT);
        editor.setTextColor(Color.rgb(225, 239, 229));
        editor.setHintTextColor(Color.GRAY);
        editor.setHint("Write JavaScript here...");
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(13);
        editor.setSingleLine(false);
        editor.setMinLines(16);
        editor.setBackground(shape(Color.rgb(13, 19, 15), 10));
        padding(editor, 12);
        editor.setText(read(currentFile));
        page.addView(editor, new LinearLayout.LayoutParams(-1, dp(380)));
        page.addView(action("Browse project files", this::showFiles));
    }

    private void ensureSelection() {
        if (projectDir == null || !projectDir.exists()) {
            ensureProject("MyProject");
        }
        if (currentFile == null || !currentFile.exists()) currentFile = new File(projectDir, "index.js");
        if (!currentFile.exists()) write(currentFile, "console.log('Hello Node.js');\n");
    }

    private void saveEditor() {
        if (editor == null || currentFile == null) return;
        write(currentFile, editor.getText().toString());
        toast("Saved " + currentFile.getName());
    }

    private void runFile() {
        saveEditor();
        if (currentFile == null) return;
        JSONObject data = new JSONObject();
        try { data.put("file", currentFile.getAbsolutePath()); }
        catch (Exception ignored) {}
        requestAsync("/run", data, result -> {
            toast(result.optBoolean("ok", false) ? "Script started" : result.optString("error", "Run failed"));
            showTerminal();
        });
    }

    private void stopFile() {
        requestAsync("/stop", new JSONObject(), result -> toast("Stop command sent"));
    }

    private void showTerminal() {
        shell("Terminal");
        terminal = label("Connecting to embedded Node.js...\n", 12, GREEN);
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setBackground(shape(Color.rgb(3, 7, 4), 10));
        padding(terminal, 12);
        page.addView(terminal, new LinearLayout.LayoutParams(-1, dp(360)));
        LinearLayout controls = horizontal();
        controls.addView(action("Refresh", this::refreshLogs), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(action("Clear", this::clearLogs), new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(action("Stop", this::stopFile), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(controls);
        refreshLogs();
    }

    private void refreshLogs() {
        requestAsync("/logs", new JSONObject(), result -> {
            JSONArray a = result.optJSONArray("logs");
            StringBuilder out = new StringBuilder();
            if (a != null) for (int i = 0; i < a.length(); i++) out.append(a.optString(i)).append('\n');
            if (terminal != null) terminal.setText(out.length() == 0 ? "No output yet." : out.toString());
        });
    }

    private void clearLogs() {
        requestAsync("/clear", new JSONObject(), result -> {
            if (terminal != null) terminal.setText("");
        });
    }

    private void requestAsync(String path, JSONObject body, java.util.function.Consumer<JSONObject> callback) {
        new Thread(() -> {
            JSONObject result;
            try {
                String response = request(path, body);
                result = new JSONObject(response);
            } catch (Exception e) {
                result = new JSONObject();
                try { result.put("error", e.getMessage()); } catch (Exception ignored) {}
            }
            JSONObject finalResult = result;
            runOnUiThread(() -> callback.accept(finalResult));
        }, "Runner-HTTP").start();
    }

    private String request(String path, JSONObject body) throws Exception {
        URL url = new URL("http://127.0.0.1:" + PORT + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(5000);
        if (body != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        } else c.setRequestMethod("GET");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int n;
        while (in != null && (n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
        c.disconnect();
        return bytes.toString("UTF-8");
    }

    private int countFiles(File dir) {
        File[] list = dir.listFiles(); if (list == null) return 0;
        int count = 0;
        for (File f : list) count += f.isDirectory() ? countFiles(f) : 1;
        return count;
    }
    private void deleteTree(File f) {
        File[] list = f.listFiles(); if (list != null) for (File child : list) deleteTree(child);
        f.delete();
    }
    private String read(File f) {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }
    private void write(File f, String value) {
        try {
            File parent = f.getParentFile(); if (parent != null) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(value.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { toast("File error: " + e.getMessage()); }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void stopPolling() { polling = false; handler.removeCallbacksAndMessages(null); }

    @Override public void onDestroy() {
        stopPolling();
        super.onDestroy();
    }
}
