package com.morlunk.mumbleclient.app.db;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;

public class DbAdapter {

	public static final String DATABASE_NAME = "mumble.db";
	
	public static final String TABLE_SERVER = "server";
	public static final String SERVER_ID = "_id";
	public static final String SERVER_NAME = "name";
	public static final String SERVER_HOST = "host";
	public static final String SERVER_PORT = "port";
	public static final String SERVER_USERNAME = "username";
	public static final String SERVER_PASSWORD = "password";
	public static final String TABLE_SERVER_CREATE_SQL = "CREATE TABLE `"+TABLE_SERVER+"` ("
			   + "`"+SERVER_ID+"` INTEGER PRIMARY KEY AUTOINCREMENT,"
			   + "`"+SERVER_NAME+"` TEXT NOT NULL,"
			   + "`"+SERVER_HOST+"` TEXT NOT NULL,"
			   + "`"+SERVER_PORT+"` INTEGER,"
			   + "`"+SERVER_USERNAME+"` TEXT NOT NULL,"
			   + "`"+SERVER_PASSWORD+"` TEXT"
			   + ");";
	
	public static final String TABLE_FAVOURITES = "favourites";
	public static final String FAVOURITES_ID = "_id";
	public static final String FAVOURITES_CHANNEL = "channel";
	public static final String FAVOURITES_SERVER = "server";
	public static final String TABLE_FAVOURITES_CREATE_SQL = "CREATE TABLE `"+TABLE_FAVOURITES+"` ("
			   +"`"+FAVOURITES_ID+"` INTEGER PRIMARY KEY AUTOINCREMENT,"
			   +"`"+FAVOURITES_CHANNEL+"` TEXT NOT NULL,"
			   +"`"+FAVOURITES_SERVER+"` INTEGER NOT NULL"
			   +");";
	
	public static final String TABLE_TOKENS = "tokens";
	public static final String TOKENS_ID = "_id";
	public static final String TOKENS_VALUE = "value";
	public static final String TOKENS_SERVER = "server";
	public static final String TABLE_TOKENS_CREATE_SQL = "CREATE TABLE `"+TABLE_TOKENS+"` ("
			   +"`"+TOKENS_ID+"` INTEGER PRIMARY KEY AUTOINCREMENT,"
			   +"`"+TOKENS_VALUE+"` TEXT NOT NULL,"
			   +"`"+TOKENS_SERVER+"` INTEGER NOT NULL"
			   +");";
	
	public static final String TABLE_COMMENTS = "comments";
	public static final String COMMENTS_WHO = "who";
	public static final String COMMENTS_COMMENT = "comment";
	public static final String COMMENTS_SEEN = "seen";
	public static final String TABLE_COMMENTS_CREATE_SQL = "CREATE TABLE `"+TABLE_COMMENTS+"` ("
			   +"`"+COMMENTS_WHO+"` TEXT NOT NULL,"
			   +"`"+COMMENTS_COMMENT+"` TEXT NOT NULL,"
			   +"`"+COMMENTS_SEEN+"` DATE NOT NULL"
			   +");";
	
	public static final Integer PRE_FAVOURITES_DB_VERSION = 2;
	public static final Integer PRE_TOKENS_DB_VERSION = 3;
	public static final Integer PRE_COMMENTS_DB_VERSION = 4;
	public static final Integer CURRENT_DB_VERSION = 5;
	
	private static class DatabaseHelper extends SQLiteOpenHelper {
		
		public DatabaseHelper(final Context context) {
			super(context, DATABASE_NAME, null, CURRENT_DB_VERSION);
		}

		@Override
		public void onCreate(final SQLiteDatabase db) {
			db.execSQL(TABLE_SERVER_CREATE_SQL);
			db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
			db.execSQL(TABLE_TOKENS_CREATE_SQL);
			db.execSQL(TABLE_COMMENTS_CREATE_SQL);
		}

		@Override
		public void onUpgrade(
			final SQLiteDatabase db,
			final int oldVersion,
			final int newVersion) {
			Log.w(Globals.LOG_TAG, "Database upgrade from " + oldVersion +
								  " to " + newVersion);
			if(oldVersion <= PRE_FAVOURITES_DB_VERSION) {
				db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
			}
			
			if(oldVersion <= PRE_TOKENS_DB_VERSION) {
				db.execSQL(TABLE_TOKENS_CREATE_SQL);
			}
			
			if(oldVersion <= PRE_COMMENTS_DB_VERSION) {
				db.execSQL(TABLE_COMMENTS_CREATE_SQL);
			}
		}
	}

	private final Context context;
	private SQLiteDatabase db;
	private DatabaseHelper dbHelper;
	
	public DbAdapter(final Context context_) {
		context = context_;
	}

	public final void close() {
		if(db.isOpen())
			dbHelper.close();
	}

	public final long createServer(
		final String name,
		final String host,
		final int port,
		final String username,
		final String password) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_NAME, name);
		values.put(SERVER_HOST, host);
		values.put(SERVER_PORT, port);
		values.put(SERVER_USERNAME, username);
		values.put(SERVER_PASSWORD, password);
		return db.insert(TABLE_SERVER, null, values);
	}

	public final boolean deleteServer(final long serverId) {
		return db.delete(TABLE_SERVER, SERVER_ID + " = " + serverId, null) > 0;
	}

	public final List<Server> fetchAllServers() {
		final Cursor c = db.query(
			TABLE_SERVER,
			new String[] { SERVER_ID, SERVER_NAME, SERVER_HOST,
					SERVER_PORT, SERVER_USERNAME, SERVER_PASSWORD },
			null,
			null,
			null,
			null,
			null);
		
		List<Server> servers = new ArrayList<Server>();
		servers = parseServers(c);
		
		c.close();
		
		return servers;
	}
	
	/**
	 * Parses all server objects from the passed cursor.
	 * @param cursor
	 * @return
	 */
	private List<Server> parseServers(Cursor c) {		
		List<Server> servers = new ArrayList<Server>();

		c.moveToFirst();
		while(!c.isAfterLast()) {
			Server server = new Server(c.getInt(c.getColumnIndex(SERVER_ID)),
					c.getString(c.getColumnIndex(SERVER_NAME)), 
					c.getString(c.getColumnIndex(SERVER_HOST)), 
					c.getInt(c.getColumnIndex(SERVER_PORT)), 
					c.getString(c.getColumnIndex(SERVER_USERNAME)),
					c.getString(c.getColumnIndex(SERVER_PASSWORD)));
			servers.add(server);
			c.moveToNext();
		}
		
		return servers;
	}

	public final Server fetchServer(final long serverId) {
		final Cursor c = db.query(
			TABLE_SERVER,
			new String[] { SERVER_ID, SERVER_NAME, SERVER_HOST,
					SERVER_PORT, SERVER_USERNAME, SERVER_PASSWORD },
			SERVER_ID + " = " + serverId,
			null,
			null,
			null,
			null);
		
		Server server = parseServers(c).get(0);
		
		c.close();

		return server;
	}

	public final DbAdapter open() {
		dbHelper = new DatabaseHelper(context);
		db = dbHelper.getWritableDatabase();
		return this;
	}

	public final void updateServer(
		final long id,
		final String name,
		final String host,
		final int port,
		final String username,
		final String password) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_NAME, name);
		values.put(SERVER_HOST, host);
		values.put(SERVER_PORT, port);
		values.put(SERVER_USERNAME, username);
		values.put(SERVER_PASSWORD, password);
		db.update(
			TABLE_SERVER,
			values,
			SERVER_ID + "=?",
			new String[] { Long.toString(id) });
	}
	
	public long createToken(long serverId, String token) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(TOKENS_VALUE, token);
		contentValues.put(TOKENS_SERVER, serverId);
		return db.insert(TABLE_TOKENS, null, contentValues);
	}
	
	public List<String> fetchAllTokens(long serverId) {
		Cursor cursor = db.query(TABLE_TOKENS, 
				new String[] { TOKENS_VALUE }, 
				TOKENS_SERVER+"=?", 
				new String[] { String.valueOf(serverId) }, 
				null, 
				null, 
				null);
		cursor.moveToFirst();
		
		List<String> tokens = new ArrayList<String>();
		
		while(!cursor.isAfterLast()) {
			String token = cursor.getString(cursor.getColumnIndex(TOKENS_VALUE));
			tokens.add(token);
			cursor.moveToNext();
		}
		
		return tokens;
	}
	
	public boolean deleteToken(String token, long serverId) {
		return db.delete(TABLE_TOKENS, TOKENS_VALUE + " = ? AND " + TOKENS_SERVER + " = ?", new String[] { token, String.valueOf(serverId) }) > 0;
	}
	
	public long createFavourite(long serverId, long channelId) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(FAVOURITES_CHANNEL, channelId);
		contentValues.put(FAVOURITES_SERVER, serverId);
		return db.insert(TABLE_FAVOURITES, null, contentValues);
	}
	
	private List<Favourite> parseFavourites(Cursor c) {
		List<Favourite> favourites = new ArrayList<Favourite>();

		c.moveToFirst();
		while(!c.isAfterLast()) {
			Favourite favourite = new Favourite(c.getInt(c.getColumnIndex(FAVOURITES_ID)),
					c.getInt(c.getColumnIndex(FAVOURITES_SERVER)), 
					c.getInt(c.getColumnIndex(FAVOURITES_CHANNEL)));
			favourites.add(favourite);
			c.moveToNext();
		}
		
		return favourites;
	}
	
	public List<Favourite> fetchAllFavourites(long serverId) {

		final Cursor c = db.query(
			TABLE_FAVOURITES,
			new String[] { FAVOURITES_ID, FAVOURITES_CHANNEL, FAVOURITES_SERVER},
			FAVOURITES_SERVER + "=?",
			new String[] { String.valueOf(serverId) },
			null,
			null,
			null);
		
		List<Favourite> favourites = new ArrayList<Favourite>();
		favourites = parseFavourites(c);
		
		c.close();
		
		return favourites;
	}
	
	public boolean deleteFavourite(final long favouriteId) {
		return db.delete(TABLE_FAVOURITES, FAVOURITES_ID + " = " + favouriteId, null) > 0;
	}
	
	public boolean isCommentSeen(String who, String commentHash) {
		Cursor cursor = db.query(TABLE_COMMENTS, new String[] { COMMENTS_WHO, COMMENTS_COMMENT, COMMENTS_SEEN }, COMMENTS_WHO+"=? AND "+COMMENTS_COMMENT+"=?", new String[] { who, commentHash }, null, null, null);
		return cursor.moveToNext();
	}
	
	public void setCommentSeen(String who, String commentHash) {
		// First, remove the existence of any other comments by the user.
		db.delete(TABLE_COMMENTS, COMMENTS_WHO+"=?", new String[] { who });
		
		ContentValues values = new ContentValues();
		values.put(COMMENTS_WHO, who);
		values.put(COMMENTS_COMMENT, commentHash);
		values.put(COMMENTS_SEEN, "datetime('now')");
		db.insert(TABLE_COMMENTS, null, values);
	}
}
