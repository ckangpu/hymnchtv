/*
 * hymnchtv: COG hymns' lyrics viewer and player client
 * Copyright 2020 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cog.hymnchtv.persistance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.cog.hymnchtv.*;
import org.cog.hymnchtv.mediaconfig.MediaConfig;
import org.cog.hymnchtv.mediaconfig.MediaRecord;
import org.cog.hymnchtv.persistance.migrations.Migrations;
import org.cog.hymnchtv.persistance.migrations.MigrationsHelper;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static org.cog.hymnchtv.MainActivity.HYMN_BB;
import static org.cog.hymnchtv.MainActivity.HYMN_DB;
import static org.cog.hymnchtv.MainActivity.HYMN_ER;
import static org.cog.hymnchtv.MainActivity.HYMN_XB;

/**
 * The <tt>DatabaseBackend</tt> uses SQLite to store all the aTalk application data in the database "dbRecords.db"
 *
 * @author Eng Chong Meng
 */
public class DatabaseBackend extends SQLiteOpenHelper
{
    /**
     * Name of the database and its version number
     * Increment DATABASE_VERSION when there is a change in database records
     */
    public static final String DATABASE_NAME = "dbHymnApp.db";
    private static final int DATABASE_VERSION = 1;

    private static DatabaseBackend instance = null;
    private final Context mContext;

    private DatabaseBackend(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    /**
     * Get an instance of the DataBaseBackend and create one if new
     *
     * @param context context
     * @return DatabaseBackend instance
     */
    public static synchronized DatabaseBackend getInstance(Context context)
    {
        if (instance == null) {
            instance = new DatabaseBackend(context);
        }
        return instance;
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion)
    {
        Timber.i("Upgrading database from version %s to version %s", oldVersion, newVersion);

        db.beginTransaction();
        try {
            RealMigrationsHelper migrationsHelper = new RealMigrationsHelper();
            Migrations.upgradeDatabase(db, migrationsHelper);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Timber.e("Exception while upgrading database. Resetting the DB to original: %s", e.getMessage());
            db.setVersion(oldVersion);

            if (BuildConfig.DEBUG) {
                db.endTransaction();
                throw new Error("Database upgrade failed! Exception: ", e);
            }
        } finally {
            db.endTransaction();
        }
    }

    // HymnContent info table creation statement
    public static String HYMN_CONTENT_STATEMENT = "CREATE TABLE %s("
            + MediaConfig.HYMN_NO + " INTEGER, "
            + MediaConfig.HYMN_FU + " BOOL, "
            + MediaConfig.MEDIA_TYPE + " TEXT, "
            + MediaConfig.MEDIA_URI + " TEXT, "
            + MediaConfig.MEDIA_FILE_PATH + " TEXT,  UNIQUE("
            + MediaConfig.HYMN_NO + ", " + MediaConfig.HYMN_FU + ", " + MediaConfig.MEDIA_TYPE
            + ") ON CONFLICT REPLACE);";

    /**
     * Create all the required virgin database tables and perform initial data migration:
     * a. System properties
     * b. HymnContent Tablee per HYMN_XXX
     *
     * # Initialize and initial data migration
     *
     * @param db SQLite database
     */
    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format("PRAGMA foreign_keys =%s", "ON");
        db.execSQL(query);

        db.execSQL(HYMN_CONTENT_STATEMENT.replace("%s", HYMN_DB));
        db.execSQL(HYMN_CONTENT_STATEMENT.replace("%s", HYMN_BB));
        db.execSQL(HYMN_CONTENT_STATEMENT.replace("%s", HYMN_XB));
        db.execSQL(HYMN_CONTENT_STATEMENT.replace("%s", HYMN_ER));

        // Perform the first data migration to SQLite database
        initDatabase(db);
    }

    /**
     * Initialize, migrate and fill the database from old data implementation
     */
    private void initDatabase(SQLiteDatabase db)
    {
        Timber.i("### Starting Database migration! ###");
        db.beginTransaction();
        try {
            db.setTransactionSuccessful();
            Timber.i("### Completed SQLite DataBase migration successfully! ###");
        } finally {
            db.endTransaction();
        }
    }

    public void storeMediaRecord(MediaRecord mRecord)
    {
        SQLiteDatabase db = getWritableDatabase();
        // String[] selectionArgs = {mRecord.getHymnNo(), mRecord.isFu() ? "true" : "false", mRecord.getMediaType()};

        ContentValues values = new ContentValues();
        values.put(MediaConfig.HYMN_NO, mRecord.getHymnNo());
        values.put(MediaConfig.HYMN_FU, mRecord.isFu());
        values.put(MediaConfig.MEDIA_TYPE, mRecord.getMediaType().toString());
        values.put(MediaConfig.MEDIA_URI, mRecord.getMediaUri());
        values.put(MediaConfig.MEDIA_FILE_PATH, mRecord.getMediaFilePath());

        long row = db.insert(mRecord.getHymnType(), null, values);
        if (row == -1) {
            Timber.e("### Error in creating media record for table:hymNo: %s:%s", mRecord.getHymnType(), mRecord.getHymnNo());
        }
    }

    /**
     * Check if mRecord exist in DB and update with the DB result if update if true
     *
     * @param mRecord Media record to check for
     * @param update Update mRecord if true, else just return the status; i.e just to check if exist in DB
     *
     * @return mRecord present status, and mRecord is updated if update is true;
     */
    public boolean getMediaRecord(MediaRecord mRecord, boolean update)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        boolean hasRecord = false;

        String[] columns = {MediaConfig.MEDIA_URI, MediaConfig.MEDIA_FILE_PATH};
        String[] args = {Integer.toString(mRecord.getHymnNo()), mRecord.isFu() ? "1" : "0", mRecord.getMediaType().toString()};

        Cursor cursor = db.query(mRecord.getHymnType(), columns,
                MediaConfig.HYMN_NO + "=? AND " + MediaConfig.HYMN_FU + "=? AND " + MediaConfig.MEDIA_TYPE + "=?",
                args, null, null, null);

        while (cursor.moveToNext()) {
            if (update) {
                mRecord.setMediaUri(cursor.getString(0));
                mRecord.setFilePath(cursor.getString(1));
            }
            hasRecord = true;
        }
        cursor.close();
        return hasRecord;
    }

    public int deleteMediaRecord(MediaRecord mRecord)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {Integer.toString(mRecord.getHymnNo()), mRecord.isFu() ? "1" : "0", mRecord.getMediaType().toString()};

        return db.delete(mRecord.getHymnType(), MediaConfig.HYMN_NO + "=? AND "
                + MediaConfig.HYMN_FU + "=? AND " + MediaConfig.MEDIA_TYPE + "=?", args);
    }

    public List<MediaRecord> getMediaRecords(String hymnType)
    {
        SQLiteDatabase db = this.getReadableDatabase();
        List<MediaRecord> mediaRecords = new ArrayList<>();
        String ORDER_ASC = MediaConfig.HYMN_NO + " ASC";

        Cursor cursor = db.query(hymnType, null, null, null, null, null, ORDER_ASC);
        while (cursor.moveToNext()) {
            MediaRecord mediaRecord = new MediaRecord(hymnType,
                    cursor.getInt(cursor.getColumnIndex(MediaConfig.HYMN_NO)),
                    cursor.getInt(cursor.getColumnIndex(MediaConfig.HYMN_FU)) > 0,
                    Enum.valueOf(MediaType.class, cursor.getString(cursor.getColumnIndex(MediaConfig.MEDIA_TYPE))),
                    cursor.getString(cursor.getColumnIndex(MediaConfig.MEDIA_URI)),
                    cursor.getString(cursor.getColumnIndex(MediaConfig.MEDIA_FILE_PATH)));
            mediaRecords.add(mediaRecord);
        }
        cursor.close();
        return mediaRecords;
    }

    @Override
    public SQLiteDatabase getWritableDatabase()
    {
        SQLiteDatabase db = super.getWritableDatabase();
        // db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format("PRAGMA foreign_keys =%s", "ON");
        db.execSQL(query);
        return db;
    }

    private static class RealMigrationsHelper implements MigrationsHelper
    {
        public RealMigrationsHelper()
        {
        }

        @Override
        public Context getContext()
        {
            return HymnsApp.getGlobalContext();
        }

        //        @Override
        //        public String serializeFlags(List<Flag> flags) {
        //            return LocalStore.serializeFlags(flags);
        //        }
    }
}
