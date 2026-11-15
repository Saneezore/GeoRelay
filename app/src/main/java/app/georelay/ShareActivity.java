package app.georelay;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.time.ZoneId;
import java.util.*;

public final class ShareActivity extends Activity {
    private TextView message;
    private File output;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 80, 32, 32);
        message = new TextView(this); message.setTextSize(18); message.setText("Preparing image…"); root.addView(message);
        Button cancel = new Button(this); cancel.setText("Close"); cancel.setStateListAnimator(null); cancel.setOnClickListener(v -> finish()); root.addView(cancel); setContentView(root);
        if (!Intent.ACTION_SEND.equals(getIntent().getAction())) { message.setText("Share a JPEG image to GeoRelay from another app."); return; }
        Uri uri = Build.VERSION.SDK_INT >= 33 ? getIntent().getParcelableExtra(Intent.EXTRA_STREAM, Uri.class) : getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null || !"content".equals(uri.getScheme())) { message.setText("The source app did not provide a readable image."); return; }
        new Thread(() -> {
            try {
                Prepared ready = prepare(uri);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    message.setText(ready.description);
                    Button share = new Button(this); share.setText("Share image"); share.setStateListAnimator(null);
                    share.setOnClickListener(v -> share(ready.file)); root.addView(share);
                    share(ready.file);
                });
            } catch (Exception e) {
                if (output != null) output.delete();
                String detail = e instanceof IllegalArgumentException ? e.getMessage() : "Unable to read or update this JPEG. The source app may have removed access or supplied an unsupported file.";
                runOnUiThread(() -> { if (!isDestroyed()) message.setText(detail); });
            }
        }, "image-relay").start();
    }
    private record Prepared(File file, String description) { }
    private Prepared prepare(Uri uri) throws IOException {
        File folder = new File(getCacheDir(), "relay");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create cache");
        File[] old = folder.listFiles();
        if (old != null) for (File f : old) if (System.currentTimeMillis() - f.lastModified() > 7L * 86400000) f.delete();
        output = File.createTempFile("GeoRelay-", ".jpg", folder);
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(output)) {
            if (in == null) throw new IOException("Unreadable stream");
            byte[] buffer = new byte[65536]; long size = 0; int n;
            while ((n = in.read(buffer)) != -1) { size += n; if (size > 100L * 1024 * 1024) throw new IllegalArgumentException("This image exceeds the 100 MB limit."); out.write(buffer, 0, n); }
        }
        try (InputStream in = new FileInputStream(output)) {
            if (in.read() != 0xff || in.read() != 0xd8) throw new IllegalArgumentException("GeoRelay currently supports JPEG images only. The image was not changed.");
        }
        ExifInterface exif = new ExifInterface(output);
        String offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
        long captured = CaptureTime.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL), offset, ZoneId.systemDefault());
        FixStore.Fix fix;
        try (FixStore store = new FixStore(this)) { fix = store.after(captured); }
        if (fix == null) throw new IllegalArgumentException("No GPS fix exists after this image’s capture time. Keep recording until a fix is saved, then share the original image again.");
        exif.setLatLong(fix.lat(), fix.lon());
        // Replace stale GPS time and measurements instead of retaining unrelated GPS data.
        for (String tag : new String[]{ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_TRACK,
                ExifInterface.TAG_GPS_TRACK_REF, ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
                ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_DEST_DISTANCE_REF}) exif.setAttribute(tag, null);
        java.time.ZonedDateTime gps = java.time.Instant.ofEpochMilli(fix.time()).atZone(java.time.ZoneOffset.UTC);
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gps.format(java.time.format.DateTimeFormatter.ofPattern("uuuu:MM:dd", Locale.ROOT)));
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, String.format(Locale.US, "%02d:%02d:%02d", gps.getHour(), gps.getMinute(), gps.getSecond()));
        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS");
        exif.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, "WGS-84");
        exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, String.valueOf(fix.accuracy()));
        exif.saveAttributes();
        return new Prepared(output, String.format(Locale.US, "Ready · %.6f, %.6f\nGPS fix: %s\n%.3f seconds after capture · accuracy ±%.0f m%s\nOriginal image retained. No pixel recompression.", fix.lat(), fix.lon(), java.time.Instant.ofEpochMilli(fix.time()), (fix.time() - captured) / 1000.0, fix.accuracy(), offset == null ? "\nCapture timezone assumed: " + ZoneId.systemDefault() : ""));
    }
    private void share(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
             .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("Geotagged image", uri));
        Intent chooser = Intent.createChooser(send, "Share geotagged image");
        if (Build.VERSION.SDK_INT >= 24) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{new ComponentName(this, ShareActivity.class)});
        try { startActivity(chooser); } catch (ActivityNotFoundException e) { message.setText("No app is available to receive this image."); }
    }
}
