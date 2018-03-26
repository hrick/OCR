package br.com.oxxynet.ocr;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.io.Closeable;
import java.sql.SQLException;

import br.com.oxxynet.ocr.model.OCR;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper implements Closeable {

    private static final String DATABASE_NAME = "ocr.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {

            TableUtils.createTable(connectionSource, OCR.class);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {


            TableUtils.dropTable(connectionSource, OCR.class, true);

            onCreate(db, connectionSource);
            //}
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}
