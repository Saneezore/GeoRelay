package app.georelay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

// Recording starts only after MainActivity grants location and notification permissions.
@android.annotation.SuppressLint("MissingPermission")
public final class RecordingService extends Service implements LocationListener {
    static final String STOP = "app.georelay.STOP";
    static final String CHANNEL = "recording";
    static final int NOTICE = 1;
    static volatile boolean running;
    static volatile String status = "Stopped";
    private LocationManager manager;
    private FixStore store;
    private long lastElapsed;
    private long lastReceived;
    private long lastRequest;
    private final Handler healthHandler = new Handler(Looper.getMainLooper());
    private final Runnable healthCheck = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            try {
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    status = "GPS is off · enable Location in system settings";
                } else if (now - lastReceived >= 90_000) {
                    status = "No recent GPS fix · waiting for satellite reception";
                    if (now - lastRequest >= 120_000) {
                        manager.removeUpdates(RecordingService.this);
                        requestUpdates();
                    }
                }
                refresh();
                healthHandler.postDelayed(this, 30_000);
            } catch (SecurityException | IllegalArgumentException e) {
                status = "Recording stopped · check precise location permission";
                running = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    };
    private void requestUpdates() {
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL, 0, this, Looper.getMainLooper());
        lastRequest = SystemClock.elapsedRealtime();
    }
    static final long INTERVAL = 15_000;

    @Override public void onCreate() {
        super.onCreate();
        store = new FixStore(this);
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "GPS recording", NotificationManager.IMPORTANCE_LOW));
    }
    static Notification notification(Context c, boolean active) {
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent action = active
            ? PendingIntent.getService(c, 1, new Intent(c, RecordingService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
            : PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class).setAction("app.georelay.START"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, CHANNEL).setSmallIcon(app.georelay.R.drawable.ic_record)
            .setContentTitle(active ? "GeoRelay · Recording" : "GeoRelay · Stopped")
            .setContentText(active ? status : "Tap Start to record GPS fixes")
            .setContentIntent(content).setOngoing(active).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, active ? "Stop" : "Start", action).build()).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            running = false; status = "Stopped";
            healthHandler.removeCallbacks(healthCheck);
            manager.removeUpdates(this);
            stopForeground(STOP_FOREGROUND_DETACH);
            getSystemService(NotificationManager.class).notify(NOTICE, notification(this, false));
            stopSelf(); return START_NOT_STICKY;
        }
        status = "Waiting for GPS · requested every 15 seconds";
        if (running) return START_STICKY;
        try {
            startForeground(NOTICE, notification(this, true));
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Precise location permission required");
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                status = "GPS is off · enable Location in system settings";
            }
            requestUpdates();
            running = true;
            lastReceived = SystemClock.elapsedRealtime();
            healthHandler.postDelayed(healthCheck, 30_000);
        } catch (SecurityException | IllegalArgumentException e) {
            status = "Unable to record · check precise location and GPS";
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
        return running ? START_STICKY : START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location fix) {
        if (!running || fix.isFromMockProvider() || !fix.hasAccuracy() || fix.getTime() <= 0) return;
        long elapsed = fix.getElapsedRealtimeNanos() / 1_000_000;
        if (lastElapsed != 0 && elapsed - lastElapsed < INTERVAL) return;
        if (SystemClock.elapsedRealtime() - elapsed > 30_000) return;
        lastElapsed = elapsed;
        lastReceived = SystemClock.elapsedRealtime();
        store.add(fix);
        status = "GPS fixes saved · every 15 seconds when available";
        getSystemService(NotificationManager.class).notify(NOTICE, notification(this, true));
    }
    @Override public void onProviderDisabled(String provider) { status = "GPS is off · waiting for Location"; refresh(); }
    @Override public void onProviderEnabled(String provider) { status = "Waiting for GPS"; refresh(); }
    private void refresh() { if (running) getSystemService(NotificationManager.class).notify(NOTICE, notification(this, true)); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onDestroy() {
        healthHandler.removeCallbacks(healthCheck);
        running = false; manager.removeUpdates(this); store.close(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
