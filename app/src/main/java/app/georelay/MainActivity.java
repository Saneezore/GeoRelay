package app.georelay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView state, latest;
    private Button start, stop;
    private LinearLayout root, body;
    private ListView history;
    private FixStore store;
    private boolean showingHistory;
    private final Runnable tick = new Runnable() { public void run() { render(); handler.postDelayed(this, 1500); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store = new FixStore(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 16, 24, 16);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.systemBars()) : null;
            if (bars != null) v.setPadding(24 + bars.left, 16 + bars.top, 24 + bars.right, 16 + bars.bottom);
            else v.setPadding(24, 16 + insets.getSystemWindowInsetTop(), 24, 16 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = text("GeoRelay", 28); root.addView(title);
        LinearLayout tabs = new LinearLayout(this);
        Button recorder = button("Recorder", v -> show(false)); Button fixes = button("Coordinates", v -> show(true));
        tabs.addView(recorder, new LinearLayout.LayoutParams(0, -2, 1)); tabs.addView(fixes, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(tabs);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); show(false); handleStartIntent(getIntent());
    }
    private TextView text(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, 16, 0, 16); return v; }
    private Button button(String label, View.OnClickListener listener) { Button b = new Button(this); b.setText(label); b.setStateListAnimator(null); b.setOnClickListener(listener); return b; }
    private void show(boolean list) {
        showingHistory = list; body.removeAllViews();
        if (list) {
            body.addView(text("Recorded coordinates · newest first", 16)); history = new ListView(this); body.addView(history);
        } else {
            state = text("", 20); body.addView(state);
            LinearLayout actions = new LinearLayout(this);
            start = button("Start", v -> begin()); stop = button("Stop", v -> { startService(new Intent(this, RecordingService.class).setAction(RecordingService.STOP)); });
            actions.addView(start, new LinearLayout.LayoutParams(0, -2, 1)); actions.addView(stop, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(actions);
            latest = text("", 17); latest.setTextIsSelectable(true); body.addView(latest);
            body.addView(text("GPS interval: 15 seconds\nShare a JPEG to GeoRelay to add its recorded location and pass it on.", 14));
        }
        render();
    }
    private String describe(Cursor c) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(c.getLong(0)))
            + String.format(Locale.US, "\n%.6f, %.6f  ·  ±%.0f m", c.getDouble(1), c.getDouble(2), c.getFloat(3));
    }
    private void render() {
        try (Cursor c = store.latest(showingHistory ? -1 : 1)) {
            if (showingHistory) {
                ArrayList<String> items = new ArrayList<>(); while (c.moveToNext()) items.add(describe(c));
                if (items.isEmpty()) items.add("No coordinates recorded yet.");
                // Avoid resetting the list while the user scrolls.
                if (history.getTag() == null || !history.getTag().equals(items)) {
                    int first = history.getFirstVisiblePosition(); View row = history.getChildAt(0); int top = row == null ? 0 : row.getTop();
                    history.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items)); history.setTag(items); history.setSelectionFromTop(first, top);
                }
            } else {
                state.setText((RecordingService.running ? "● Recording\n" : "○ ") + RecordingService.status);
                state.setTextColor(RecordingService.running ? 0xff76ba96 : 0xffcccccc);
                start.setEnabled(!RecordingService.running); stop.setEnabled(RecordingService.running);
                latest.setText("Latest: " + (c.moveToFirst() ? describe(c) : "No fix yet"));
            }
        }
    }
    private void begin() {
        ArrayList<String> needed = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!needed.isEmpty()) { requestPermissions(needed.toArray(new String[0]), 7); return; }
        startForegroundService(new Intent(this, RecordingService.class));
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        boolean notifications = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && notifications) startForegroundService(new Intent(this, RecordingService.class));
        else new AlertDialog.Builder(this).setMessage("Precise location and notifications are needed for GPS recording and its Start/Stop notification. Enable them in app settings.")
            .setPositiveButton("Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName())))).setNegativeButton("Cancel", null).show();
    }
    private void handleStartIntent(Intent intent) { if ("app.georelay.START".equals(intent.getAction())) { intent.setAction(null); begin(); } }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleStartIntent(intent); }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
}
