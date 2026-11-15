package app.georelay;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;

final class FixStore extends SQLiteOpenHelper {
    FixStore(Context context) { super(context, "fixes.db", null, 1); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE fixes (time INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, accuracy REAL NOT NULL)");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    void add(Location location) {
        ContentValues v = new ContentValues();
        v.put("time", location.getTime()); v.put("lat", location.getLatitude());
        v.put("lon", location.getLongitude()); v.put("accuracy", location.getAccuracy());
        getWritableDatabase().insertWithOnConflict("fixes", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    Cursor latest(int limit) { return getReadableDatabase().rawQuery("SELECT time,lat,lon,accuracy FROM fixes ORDER BY time DESC LIMIT ?", new String[]{String.valueOf(limit)}); }
    Fix after(long time) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT time,lat,lon,accuracy FROM fixes WHERE time > ? ORDER BY time ASC LIMIT 1", new String[]{String.valueOf(time)})) {
            return c.moveToFirst() ? new Fix(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3)) : null;
        }
    }
    record Fix(long time, double lat, double lon, float accuracy) { }
}
