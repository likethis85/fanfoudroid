package com.ch_linghu.fanfoudroid.data.db;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.ch_linghu.fanfoudroid.TwitterApplication;
import com.ch_linghu.fanfoudroid.data.Dm;
import com.ch_linghu.fanfoudroid.data.Tweet;
import com.ch_linghu.fanfoudroid.helper.Preferences;
import com.ch_linghu.fanfoudroid.helper.Utils;

/**
 * A Database which contains all statuses and direct-messages, use
 * getInstane(Context) to get a new instance
 * 
 */
public class TwitterDatabase {

    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "status_db";
    private static final int DATABASE_VERSION = 1;

    private static TwitterDatabase instance = null;
    private DatabaseHelper mOpenHelper = null;
    private Context mContext = null;

    /**
     * SQLiteOpenHelper
     * 
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        // Construct
        public DatabaseHelper(Context context, String name,
                CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        public DatabaseHelper(Context context, String name) {
            this(context, name, DATABASE_VERSION);
        }

        public DatabaseHelper(Context context) {
            this(context, DATABASE_NAME, DATABASE_VERSION);
        }

        public DatabaseHelper(Context context, int version) {
            this(context, DATABASE_NAME, null, version);
        }

        public DatabaseHelper(Context context, String name, int version) {
            this(context, name, null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.i(TAG, "Create Database.");
            // Log.i(TAG, StatusTable.STATUS_TABLE_CREATE);
            db.execSQL(StatusTable.CREATE_TABLE);
            db.execSQL(MessageTable.CREATE_TABLE);
            db.execSQL(FollowTable.CREATE_TABLE);
            
            //2011.03.01 add beta
            db.execSQL(UserInfoTable.CREATE_TABLE);
        }

        @Override
        public synchronized void close() {
            Log.i(TAG, "Close Database.");
            super.close();
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            Log.i(TAG, "Open Database.");
            super.onOpen(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "Upgrade Database.");
            dropAllTables(db);
        }

        private void dropAllTables(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + StatusTable.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + MessageTable.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + FollowTable.TABLE_NAME);
            
            //2011.03.01 add
            db.execSQL("DROP TABLE IF EXISTS "+UserInfoTable.TABLE_NAME);
        }
    }

    private TwitterDatabase(Context context) {
        mContext = context;
        mOpenHelper = new DatabaseHelper(context);
    }

    public static synchronized TwitterDatabase getInstance(Context context) {
        if (null == instance) {
            return new TwitterDatabase(context);
        }
        return instance;
    }

    // 测试用
    public SQLiteOpenHelper getSQLiteOpenHelper() {
        return mOpenHelper;
    }

    public void close() {
        if (null != instance) {
            mOpenHelper.close();
            instance = null;
        }
    }

    /**
     * 清空所有表中数据, 谨慎使用
     * 
     */
    public void clearData() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        db.execSQL("DELETE FROM " + StatusTable.TABLE_NAME);
        db.execSQL("DELETE FROM " + MessageTable.TABLE_NAME);
        db.execSQL("DELETE FROM " + FollowTable.TABLE_NAME);
        
        //2011.03.01 add
        db.execSQL("DELETE FROM "+UserInfoTable.TABLE_NAME);
    }

    /**
     * 直接删除数据库文件, 调试用
     * 
     * @return true if this file was deleted, false otherwise.
     * @deprecated
     */
    private boolean deleteDatabase() {
        File dbFile = mContext.getDatabasePath(DATABASE_NAME);
        return dbFile.delete();
    }

    /**
     * 取出某类型的一条消息
     * 
     * @param tweetId
     * @param type of status
     *              <li>StatusTable.TYPE_HOME</li>
     *              <li>StatusTable.TYPE_MENTION</li>
     *              <li>StatusTable.TYPE_USER</li>
     *              <li>StatusTable.TYPE_FAVORITE</li>
     *              <li>-1 means all types</li>
     * @return 将Cursor转换过的Tweet对象
     */
    public Tweet queryTweet(String tweetId, int type) {
        SQLiteDatabase Db = mOpenHelper.getWritableDatabase();

        String selection = StatusTable._ID + "=? ";
        if (-1 != type) {
            selection += " AND " + StatusTable.FIELD_STATUS_TYPE + "=" + type;
        }

        Cursor cursor = Db.query(StatusTable.TABLE_NAME,
                StatusTable.TABLE_COLUMNS, selection, new String[] { tweetId },
                null, null, null);

        Tweet tweet = null;

        if (cursor != null) {
            cursor.moveToFirst();
            if (cursor.getCount() > 0) {
                tweet = StatusTable.parseCursor(cursor);
            }
        }

        cursor.close();
        return tweet;
    }

    /**
     * 快速检查某条消息是否存在(指定类型)
     * 
     * @param tweetId
     * @param type
     *            <li>StatusTable.TYPE_HOME</li>
     *            <li>StatusTable.TYPE_MENTION</li>
     *            <li>StatusTable.TYPE_USER</li>
     *            <li>StatusTable.TYPE_FAVORITE</li>
     * @return is exists
     */
    public boolean isExists(String tweetId, String owner, int type) {
        SQLiteDatabase Db = mOpenHelper.getWritableDatabase();
        boolean result = false;

        Cursor cursor = Db.query(StatusTable.TABLE_NAME,
                new String[] { StatusTable._ID }, StatusTable._ID + " =? AND "
                    + StatusTable.FIELD_OWNER_ID + "=? AND "    
                	+ StatusTable.FIELD_STATUS_TYPE + " = " + type,
                new String[] { tweetId, owner }, null, null, null);

        if (cursor != null && cursor.getCount() > 0) {
            result = true;
        }

        cursor.close();
        return result;
    }

    /**
     * 删除一条消息
     * 
     * @param tweetId
     * @param type -1 means all types
     * @return the number of rows affected if a whereClause is passed in, 0
     *         otherwise. To remove all rows and get a count pass "1" as the
     *         whereClause.
     */
    public int deleteTweet(String tweetId, String owner, int type) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
        String where = StatusTable._ID + " =? "; 
        if (!Utils.isEmpty(owner)){
        	where += " AND " + StatusTable.FIELD_OWNER_ID + " = '" + owner + "' ";
        }
        if (-1 != type) {
           where  += " AND " + StatusTable.FIELD_STATUS_TYPE + " = " + type;
        }

        return db.delete(StatusTable.TABLE_NAME, where, new String[] { tweetId });
    }

    /**
     * 删除超过MAX_ROW_NUM垃圾数据
     * 
     * @param type
     *            <li>StatusTable.TYPE_HOME</li>
     *            <li>StatusTable.TYPE_MENTION</li>
     *            <li>StatusTable.TYPE_USER</li>
     *            <li>StatusTable.TYPE_FAVORITE</li>
     *            <li>-1 means all types</li>
     */
    public void gc(String owner, int type) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        String sql = "DELETE FROM " + StatusTable.TABLE_NAME
                + " WHERE " + StatusTable._ID + " NOT IN " 
                + " (SELECT " + StatusTable._ID // 子句
                + " FROM " + StatusTable.TABLE_NAME;
        boolean first = true;
        if (!Utils.isEmpty(owner)){
        	sql += " WHERE " + StatusTable.FIELD_OWNER_ID + " = '" + owner + "' ";
        	first = false;
        }
        if (type != -1){
        	if (first){
        		sql += " WHERE ";
        	}else{
        		sql += " AND ";
        	}
        	sql += StatusTable.FIELD_STATUS_TYPE + " = " + type + " ";
        }
                sql += " ORDER BY " + StatusTable.FIELD_CREATED_AT + " DESC LIMIT "
                + StatusTable.MAX_ROW_NUM + ")";

        if (!Utils.isEmpty(owner)){
        	sql += " AND " + StatusTable.FIELD_OWNER_ID + " = '" + owner + "' ";
        }
        if (type != -1) {
            sql += " AND " + StatusTable.FIELD_STATUS_TYPE + " = " + type + " ";
        }

        Log.d(TAG, sql);
        mDb.execSQL(sql);
    }

    public final static DateFormat DB_DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);

    /**
     * 向Status表中写入一行数据, 此方法为私有方法, 外部插入数据请使用 putTweets()
     * 
     * @param tweet
     *            需要写入的单条消息
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertTweet(Tweet tweet, String owner, int type, boolean isUnread) {
        SQLiteDatabase Db = mOpenHelper.getWritableDatabase();

        if (isExists(tweet.id, owner, type)) {
            Log.i(TAG, tweet.id + "is exists.");
            return -1;
        }

        ContentValues initialValues = makeTweetValues(tweet, owner, type, isUnread);
        long id = Db.insert(StatusTable.TABLE_NAME, null, initialValues);

        if (-1 == id) {
            Log.e(TAG, "cann't insert the tweet : " + tweet.toString());
        } else {
            Log.i(TAG, "Insert a status into datebase : " + tweet.toString());
        }

        return id;
    }

    /**
     * 更新一条消息
     * 
     * @param tweetId
     * @param values
     *            ContentValues 需要更新字段的键值对
     * @return the number of rows affected
     */
    public int updateTweet(String tweetId, ContentValues values) {
        Log.i(TAG, "Update Tweet  : " + tweetId + " " + values.toString());

        SQLiteDatabase Db = mOpenHelper.getWritableDatabase();

        return Db.update(StatusTable.TABLE_NAME, values,
                StatusTable._ID + "=?", new String[] { tweetId });
    }
    
    private ContentValues makeTweetValues(Tweet tweet, String owner, int type, boolean isUnread) {
        // 插入一条新消息
        ContentValues initialValues = new ContentValues();
        initialValues.put(StatusTable.FIELD_OWNER_ID, owner);
        initialValues.put(StatusTable.FIELD_STATUS_TYPE, type);
        initialValues.put(StatusTable._ID, tweet.id);
        initialValues.put(StatusTable.FIELD_TEXT, tweet.text);
        initialValues.put(StatusTable.FIELD_USER_ID, tweet.userId);
        initialValues.put(StatusTable.FIELD_USER_SCREEN_NAME, tweet.screenName);
        initialValues.put(StatusTable.FIELD_PROFILE_IMAGE_URL,
                tweet.profileImageUrl);
        initialValues.put(StatusTable.FIELD_FAVORITED, tweet.favorited);
        initialValues.put(StatusTable.FIELD_IN_REPLY_TO_STATUS_ID,
                tweet.inReplyToStatusId);
        initialValues.put(StatusTable.FIELD_IN_REPLY_TO_USER_ID,
                tweet.inReplyToUserId);
        initialValues.put(StatusTable.FIELD_IN_REPLY_TO_SCREEN_NAME,
                tweet.inReplyToScreenName);
        // initialValues.put(FIELD_IS_REPLY, tweet.isReply());
        initialValues.put(StatusTable.FIELD_CREATED_AT,
                DB_DATE_FORMATTER.format(tweet.createdAt));
        initialValues.put(StatusTable.FIELD_SOURCE, tweet.source);
        initialValues.put(StatusTable.FIELD_IS_UNREAD, isUnread);
        initialValues.put(StatusTable.FIELD_TRUNCATED, tweet.truncated);
        // TODO: truncated
        
        return initialValues;
    }

    /**
     * 写入N条消息
     * 
     * @param tweets
     *            需要写入的消息List
     * @return
     */
    public void putTweets(List<Tweet> tweets, String owner, int type, boolean isUnread) {
        //long start = System.currentTimeMillis();
        if (null == tweets || 0 == tweets.size())
            return;

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        try {
            db.beginTransaction();

            for (int i = tweets.size() - 1; i >= 0; i--) {
                Tweet tweet = tweets.get(i);
                
                ContentValues initialValues = makeTweetValues(tweet, owner, type, isUnread);
                long id = db.insert(StatusTable.TABLE_NAME, null, initialValues);

                if (-1 == id) {
                    Log.e(TAG, "cann't insert the tweet : " + tweet.toString());
                } else {
                    Log.i(TAG, String.format("Insert a status into database[%s] : %s", owner, tweet.toString()));
                }
            }

            // gc(type); // 保持总量
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        //long end = System.currentTimeMillis();
        //Log.d("LDS", "putTweets : " + (end-start));
    }

    /**
     * 取出指定用户的某一类型的所有消息
     * 
     * @param userId
     * @param tableName
     * @return a cursor
     */
    public Cursor fetchAllTweets(String owner, int type) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        return mDb.query(StatusTable.TABLE_NAME, StatusTable.TABLE_COLUMNS,
                StatusTable.FIELD_OWNER_ID + " = ? AND " + StatusTable.FIELD_STATUS_TYPE + " = " + type,  
                new String[]{owner}, null, null,
                StatusTable.FIELD_CREATED_AT + " DESC ");
        //LIMIT " + StatusTable.MAX_ROW_NUM);
    }

    /**
     * 取出自己的某一类型的所有消息
     * 
     * @param tableName
     * @return a cursor
     */
    public Cursor fetchAllTweets(int type) {
		// 获取登录用户id
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String myself = preferences.getString(Preferences.CURRENT_USER_ID,
				TwitterApplication.mApi.getUserId());
		
		return fetchAllTweets(myself, type);
    	
    }
    /**
     * 清空某类型的所有信息
     * 
     * @param tableName
     * @return the number of rows affected if a whereClause is passed in, 0
     *         otherwise. To remove all rows and get a count pass "1" as the
     *         whereClause.
     */
    public int dropAllTweets(int type) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        return mDb.delete(StatusTable.TABLE_NAME, StatusTable.FIELD_STATUS_TYPE
                + " = " + type, null);
    }

    /**
     * 取出本地某类型最新消息ID
     * 
     * @param type
     * @return The newest Status Id
     */
    public String fetchMaxTweetId(String owner, int type) {
        return fetchMaxOrMinTweetId(owner, type, true);
    }

    /**
     * 取出本地某类型最旧消息ID
     * 
     * @param tableName
     * @return The oldest Status Id
     */
    public String fetchMinTweetId(String owner, int type) {
        return fetchMaxOrMinTweetId(owner, type, false);
    }

    private String fetchMaxOrMinTweetId(String owner, int type, boolean isMax) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        String sql = "SELECT " + StatusTable._ID + " FROM "
                + StatusTable.TABLE_NAME + " WHERE "
                + StatusTable.FIELD_STATUS_TYPE + " = " + type + " AND "
                + StatusTable.FIELD_OWNER_ID + " = '" + owner + "' "
                + " ORDER BY "
                + StatusTable.FIELD_CREATED_AT;
        if (isMax)
            sql += " DESC ";

        Cursor mCursor = mDb.rawQuery(sql + " LIMIT 1", null);

        String result = null;

        if (mCursor == null) {
            return result;
        }

        mCursor.moveToFirst();
        if (mCursor.getCount() == 0) {
            result = null;
        } else {
            result = mCursor.getString(0);
        }
        mCursor.close();

        return result;
    }

    /**
     * Count unread tweet
     * 
     * @param tableName
     * @return
     */
    public int fetchUnreadCount(String owner, int type) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        Cursor mCursor = mDb.rawQuery("SELECT COUNT(" + StatusTable._ID + ")"
                + " FROM " + StatusTable.TABLE_NAME + " WHERE "
                + StatusTable.FIELD_STATUS_TYPE + " = " + type + " AND "
                + StatusTable.FIELD_OWNER_ID + " = '" + owner + "' AND "
                + StatusTable.FIELD_IS_UNREAD + " = 1 ",
               // "LIMIT " + StatusTable.MAX_ROW_NUM,
                null);

        int result = 0;

        if (mCursor == null) {
            return result;
        }

        mCursor.moveToFirst();
        result = mCursor.getInt(0);
        mCursor.close();

        return result;
    }

    public int addNewTweetsAndCountUnread(List<Tweet> tweets, String owner, int type) {
        putTweets(tweets, owner, type, true);

        return fetchUnreadCount(owner, type);
    }

    /**
     * Set isFavorited
     * 
     * @param tweetId
     * @param isFavorited
     * @return Is Succeed
     */
    public boolean setFavorited(String tweetId, String isFavorited) {
        ContentValues values = new ContentValues();
        values.put(StatusTable.FIELD_FAVORITED, isFavorited);
        int i = updateTweet(tweetId, values);

        return (i > 0) ? true : false;
    }

    // DM & Follower

    /**
     * 写入一条私信
     * 
     * @param dm
     * @param isUnread
     * @return the row ID of the newly inserted row, or -1 if an error occurred,
     *         因为主键的原因,此处返回的不是 _ID 的值, 而是一个自增长的 row_id
     */
    public long createDm(Dm dm, boolean isUnread) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        ContentValues initialValues = new ContentValues();
        initialValues.put(MessageTable._ID, dm.id);
        initialValues.put(MessageTable.FIELD_USER_SCREEN_NAME, dm.screenName);
        initialValues.put(MessageTable.FIELD_TEXT, dm.text);
        initialValues.put(MessageTable.FIELD_PROFILE_IMAGE_URL,
                dm.profileImageUrl);
        initialValues.put(MessageTable.FIELD_IS_UNREAD, isUnread);
        initialValues.put(MessageTable.FIELD_IS_SENT, dm.isSent);
        initialValues.put(MessageTable.FIELD_CREATED_AT,
                DB_DATE_FORMATTER.format(dm.createdAt));
        initialValues.put(MessageTable.FIELD_USER_ID, dm.userId);

        return mDb.insert(MessageTable.TABLE_NAME, null, initialValues);
    }

    //

    /**
     * Create a follower
     * 
     * @param userId
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long createFollower(String userId) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        ContentValues initialValues = new ContentValues();
        initialValues.put(FollowTable._ID, userId);
        long rowId = mDb.insert(FollowTable.TABLE_NAME, null, initialValues);
        if (-1 == rowId) {
            Log.e(TAG, "Cann't create Follower : " + userId);
        } else {
            Log.i(TAG, "create create follower : " + userId);
        }
        return rowId;
    }

    /**
     * 清空Followers表并添加新内容
     * 
     * @param followers
     */
    public void syncFollowers(List<String> followers) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        try {
            mDb.beginTransaction();

            deleteAllFollowers();

            for (String userId : followers) {
                createFollower(userId);
            }

            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    /**
     * @param type
     *            <li>MessageTable.TYPE_SENT</li>
     *            <li>MessageTable.TYPE_GET</li>
     *            <li>其他任何值都认为取出所有类型</li>
     * @return
     */
    public Cursor fetchAllDms(int type) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        String selection = null;

        if (MessageTable.TYPE_SENT == type) {
            selection = MessageTable.FIELD_IS_SENT + " = "
                    + MessageTable.TYPE_SENT;
        } else if (MessageTable.TYPE_GET == type) {
            selection = MessageTable.FIELD_IS_SENT + " = "
                    + MessageTable.TYPE_GET;
        }

        return mDb.query(MessageTable.TABLE_NAME, MessageTable.TABLE_COLUMNS,
                selection, null, null, null, MessageTable.FIELD_CREATED_AT
                        + " DESC");
    }

    public Cursor fetchInboxDms() {
        return fetchAllDms(MessageTable.TYPE_GET);
    }

    public Cursor fetchSendboxDms() {
        return fetchAllDms(MessageTable.TYPE_SENT);
    }

    public Cursor fetchAllFollowers() {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        return mDb.query(FollowTable.TABLE_NAME, FollowTable.TABLE_COLUMNS,
                null, null, null, null, null);
    }

    /**
     * FIXME:
     * @param filter
     * @return
     */
    public Cursor getFollowerUsernames(String filter) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        String likeFilter = '%' + filter + '%';

        // FIXME: 此方法的作用应该是在于在写私信时自动完成联系人的功能,
        // 改造数据库后,因为本地tweets表中的数据有限, 所以几乎使得该功能没有实际价值(因为能从数据库中读到的联系人很少)
        // 在完成关注者/被关注者两个界面后, 看能不能使得本地有一份 
        // [互相关注] 的 id/name 缓存(即getFriendsIds和getFollowersIds的交集, 因为客户端只能给他们发私信, 如果按现在
        // 只提示followers的列表则很容易造成服务器返回"只能给互相关注的人发私信"的错误信息, 这会造成用户无法理解, 因为此联系人是我们提供给他们选择的,
        // 并且将目前的自动完成功能的基础上加一个[选择联系人]按钮, 用于启动一个新的联系人列表页面以显示所有可发送私信的联系人对象, 类似手机写短信时的选择联系人功能
        return null;
        
        // FIXME: clean this up. 新数据库中失效, 表名, 列名
//        return mDb.rawQuery(
//                        "SELECT user_id AS _id, user"
//                         + " FROM (SELECT user_id, user FROM tweets"
//                         + " INNER JOIN followers on tweets.user_id = followers._id UNION"
//                         + " SELECT user_id, user FROM dms INNER JOIN followers"
//                         + " on dms.user_id = followers._id)"
//                         + " WHERE user LIKE ?"
//                         + " ORDER BY user COLLATE NOCASE",
//                        new String[] { likeFilter });
    }

    /**
     * @param userId
     *            该用户是否follow Me
     * @deprecated 未使用
     * @return
     */
    public boolean isFollower(String userId) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        Cursor cursor = mDb.query(FollowTable.TABLE_NAME,
                FollowTable.TABLE_COLUMNS, FollowTable._ID + "= ?",
                new String[] { userId }, null, null, null);

        boolean result = false;

        if (cursor != null && cursor.moveToFirst()) {
            result = true;
        }

        cursor.close();

        return result;
    }

    public boolean deleteAllFollowers() {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        return mDb.delete(FollowTable.TABLE_NAME, null, null) > 0;
    }

    public boolean deleteDm(String id) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        return mDb.delete(MessageTable.TABLE_NAME,
                String.format("%s = '%s'", MessageTable._ID, id), null) > 0;

    }

    /**
     * @param tableName
     * @return the number of rows affected
     */
    public int markAllTweetsRead(String owner, int type) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(StatusTable.FIELD_IS_UNREAD, 0);

        return mDb.update(StatusTable.TABLE_NAME, values,
                StatusTable.FIELD_STATUS_TYPE + "=" + type, null);
    }

    public boolean deleteAllDms() {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        return mDb.delete(MessageTable.TABLE_NAME, null, null) > 0;
    }

    public int markAllDmsRead() {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(MessageTable.FIELD_IS_UNREAD, 0);

        return mDb.update(MessageTable.TABLE_NAME, values, null, null);
    }

    public String fetchMaxDmId(boolean isSent) {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        Cursor mCursor = mDb.rawQuery("SELECT " + MessageTable._ID + " FROM "
                + MessageTable.TABLE_NAME + " WHERE "
                + MessageTable.FIELD_IS_SENT + " = ? " + " ORDER BY "
                + MessageTable.FIELD_CREATED_AT + " DESC   LIMIT 1",
                new String[] { isSent ? "1" : "0" });

        String result = null;

        if (mCursor == null) {
            return result;
        }

        mCursor.moveToFirst();
        if (mCursor.getCount() == 0) {
            result = null;
        } else {
            result = mCursor.getString(0);
        }
        mCursor.close();

        return result;
    }

    public int addNewDmsAndCountUnread(List<Dm> dms) {
        addDms(dms, true);

        return fetchUnreadDmCount();
    }

    public int fetchDmCount() {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        Cursor mCursor = mDb.rawQuery("SELECT COUNT(" + MessageTable._ID
                + ") FROM " + MessageTable.TABLE_NAME, null);

        int result = 0;

        if (mCursor == null) {
            return result;
        }

        mCursor.moveToFirst();
        result = mCursor.getInt(0);
        mCursor.close();

        return result;
    }

    private int fetchUnreadDmCount() {
        SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();

        Cursor mCursor = mDb.rawQuery("SELECT COUNT(" + MessageTable._ID
                + ") FROM " + MessageTable.TABLE_NAME + " WHERE "
                + MessageTable.FIELD_IS_UNREAD + " = 1", null);

        int result = 0;

        if (mCursor == null) {
            return result;
        }

        mCursor.moveToFirst();
        result = mCursor.getInt(0);
        mCursor.close();

        return result;
    }

    public void addDms(List<Dm> dms, boolean isUnread) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        try {
            mDb.beginTransaction();

            for (Dm dm : dms) {
                createDm(dm, isUnread);
            }

            // limitRows(TABLE_DIRECTMESSAGE, TwitterApi.RETRIEVE_LIMIT);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }
    
    //2011.03.01 add 
    //UserInfo操作
    
    public Cursor getAllUserInfo(){
    	SQLiteDatabase mDb=mOpenHelper.getReadableDatabase();
    	return mDb.query(UserInfoTable.TABLE_NAME,UserInfoTable.TABLE_COLUMNS, null, null, null, null, null);
    }
    /**
     * 根据id列表获取user数据
     * @param userIds
     * @return
     */
    public Cursor getUserInfoByIds(String[] userIds){
    	SQLiteDatabase mDb=mOpenHelper.getReadableDatabase();
    	String userIdStr="";
    	for(String id:userIds){
    		userIdStr+="'"+id+"',";
    	}
    	if(userIds.length==0){
    		userIdStr="'',";
    	}
    	userIdStr=userIdStr.substring(0, userIdStr.lastIndexOf(","));//删除最后的逗号
    	return mDb.query(UserInfoTable.TABLE_NAME, UserInfoTable.TABLE_COLUMNS, UserInfoTable._ID+" in ("+userIdStr+")", null, null, null, null);
    	
    }
    
    /**
     * 新建用户
     * 
     * @param user
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long createUserInfo(com.ch_linghu.fanfoudroid.data.User user) {
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();

        ContentValues initialValues = new ContentValues();
        initialValues.put(UserInfoTable._ID, user.id);
        initialValues.put(UserInfoTable.FIELD_USER_NAME, user.name);
        initialValues.put(UserInfoTable.FIELD_USER_SCREEN_NAME, user.screenName);
        initialValues.put(UserInfoTable.FIELD_LOCALTION, user.location);
        initialValues.put(UserInfoTable.FIELD_DESCRIPTION, user.description);
        initialValues.put(UserInfoTable.FIELD_PROFILE_IMAGE_URL, user.profileImageUrl);
        initialValues.put(UserInfoTable.FIELD_URL, user.url);
        initialValues.put(UserInfoTable.FIELD_PROTECTED, user.isProtected);
        initialValues.put(UserInfoTable.FIELD_FOLLOWERS_COUNT, user.followersCount);
        initialValues.put(UserInfoTable.FIELD_LAST_STATUS, user.lastStatus);
        initialValues.put(UserInfoTable.FIELD_FRIENDS_COUNT, user.friendsCount);
        initialValues.put(UserInfoTable.FIELD_FAVORITES_COUNT, user.favoritesCount);
        initialValues.put(UserInfoTable.FIELD_STATUSES_COUNT, user.statusesCount);
        initialValues.put(UserInfoTable.FIELD_FOLLOWING, user.isFollowing);
        
        
        long rowId = mDb.insert(UserInfoTable.TABLE_NAME, null, initialValues);
        if (-1 == rowId) {
            Log.e(TAG, "Cann't create Follower : " + user.id);
        } else {
            Log.i(TAG, "create create follower : " + user.id);
        }
        return rowId;
    }
    
    public long createWeiboUserInfo(com.ch_linghu.fanfoudroid.weibo.User user){
        SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();
    	ContentValues args = new ContentValues();

		args.put(UserInfoTable._ID, user.getName());
		
		args.put(UserInfoTable.FIELD_USER_NAME, user.getName());
		
		args.put(UserInfoTable.FIELD_USER_SCREEN_NAME,
				user.getScreenName());
		
		String location = user.getLocation();
		args.put(UserInfoTable.FIELD_LOCALTION, location);
		
		String description = user.getDescription();
		args.put(UserInfoTable.FIELD_DESCRIPTION, description);
		
		args.put(UserInfoTable.FIELD_PROFILE_IMAGE_URL,
				user.getProfileBackgroundImageUrl());

		if (user.getURL() != null) {
			args.put(UserInfoTable.FIELD_URL, user.getURL().toString());
		}

		args.put(UserInfoTable.FIELD_PROTECTED, user.isProtected());
		
		args.put(UserInfoTable.FIELD_FOLLOWERS_COUNT,
				user.getFollowersCount());
		
		args.put(UserInfoTable.FIELD_LAST_STATUS, user.getStatusSource());
		
		args.put(UserInfoTable.FIELD_FRIENDS_COUNT,
				user.getFriendsCount());
		
		args.put(UserInfoTable.FIELD_FAVORITES_COUNT,
				user.getFavouritesCount());
		
		args.put(UserInfoTable.FIELD_STATUSES_COUNT,
				user.getStatusesCount());
		
		args.put(UserInfoTable.FIELD_FOLLOWING, user.isFollowing());
		
	    long rowId = mDb.insert(UserInfoTable.TABLE_NAME, null, args);
        if (-1 == rowId) {
            Log.e(TAG, "Cann't create Follower : " + user.getId());
        } else {
            Log.i(TAG, "create create follower : " + user.getId());
        }
        return rowId;
    }
    
    
    /**
     * 查看数据是否已保存用户数据
     * @param userId
     * @return
     */
    public boolean existsUser(String userId) {
        SQLiteDatabase Db = mOpenHelper.getReadableDatabase();
        boolean result = false;

        Cursor cursor = Db.query(UserInfoTable.TABLE_NAME,
                new String[] { UserInfoTable._ID }, UserInfoTable._ID +"='"+userId+"'",
                null, null, null, null);
        Log.i("testesetesteste", String.valueOf(cursor.getCount()));
        if (cursor != null && cursor.getCount() > 0) {
            result = true;
        }

        cursor.close();
        return result;
    }
    
    /**
     * 根据userid提取信息
     * @param userId
     * @return
     */
    public Cursor getUserInfoById(String userId){
    	  SQLiteDatabase Db = mOpenHelper.getReadableDatabase();

          Cursor cursor = Db.query(UserInfoTable.TABLE_NAME,
                  UserInfoTable.TABLE_COLUMNS, UserInfoTable._ID + " = '" +userId+"'",
                  null, null, null, null);

        return cursor;
    }
    /**
     * 更新用户
     * @param uid
     * @param args
     * @return
     */
    public boolean updateUser(String uid,ContentValues args){
    	SQLiteDatabase Db=mOpenHelper.getWritableDatabase();
    	return Db.update(UserInfoTable.TABLE_NAME, args, UserInfoTable._ID+"='"+uid+"'", null)>0;
    }

    
    /**
     * 更新用户信息
     */
    public boolean updateUser(com.ch_linghu.fanfoudroid.data.User user){
    	
    	SQLiteDatabase Db=mOpenHelper.getWritableDatabase();
    	ContentValues args=new ContentValues();
    	args.put(UserInfoTable._ID, user.id);
		
		args.put(UserInfoTable.FIELD_USER_NAME, user.name);
		
		args.put(UserInfoTable.FIELD_USER_SCREEN_NAME, user.screenName);
		
		args.put(UserInfoTable.FIELD_LOCALTION, user.location);
		
		args.put(UserInfoTable.FIELD_DESCRIPTION, user.description);
		
		args.put(UserInfoTable.FIELD_PROFILE_IMAGE_URL, user.profileImageUrl);

		args.put(UserInfoTable.FIELD_URL, user.url);

		args.put(UserInfoTable.FIELD_PROTECTED, user.isProtected);
		
		args.put(UserInfoTable.FIELD_FOLLOWERS_COUNT, user.followersCount);
		
		args.put(UserInfoTable.FIELD_LAST_STATUS, user.lastStatus);
		
		args.put(UserInfoTable.FIELD_FRIENDS_COUNT, user.friendsCount);
		
		args.put(UserInfoTable.FIELD_FAVORITES_COUNT, user.favoritesCount);
		
		args.put(UserInfoTable.FIELD_STATUSES_COUNT, user.statusesCount);
		
		args.put(UserInfoTable.FIELD_FOLLOWING, user.isFollowing);
		
    	return Db.update(UserInfoTable.TABLE_NAME, args, UserInfoTable._ID+"='"+user.id+"'", null)>0;
    }
    /**
     * 减少转换的开销
     * @param user
     * @return
     */
    public boolean updateWeiboUser(com.ch_linghu.fanfoudroid.weibo.User user){
    	
    	SQLiteDatabase Db=mOpenHelper.getWritableDatabase();
    	ContentValues args = new ContentValues();

		args.put(UserInfoTable._ID, user.getName());
		
		args.put(UserInfoTable.FIELD_USER_NAME, user.getName());
		
		args.put(UserInfoTable.FIELD_USER_SCREEN_NAME,
				user.getScreenName());
		
		String location = user.getLocation();
		args.put(UserInfoTable.FIELD_LOCALTION, location);
		
		String description = user.getDescription();
		args.put(UserInfoTable.FIELD_DESCRIPTION, description);
		
		args.put(UserInfoTable.FIELD_PROFILE_IMAGE_URL,
				user.getProfileBackgroundImageUrl());

		if (user.getURL() != null) {
			args.put(UserInfoTable.FIELD_URL, user.getURL().toString());
		}

		args.put(UserInfoTable.FIELD_PROTECTED, user.isProtected());
		
		args.put(UserInfoTable.FIELD_FOLLOWERS_COUNT,
				user.getFollowersCount());
		
		args.put(UserInfoTable.FIELD_LAST_STATUS, user.getStatusSource());
		
		args.put(UserInfoTable.FIELD_FRIENDS_COUNT,
				user.getFriendsCount());
		
		args.put(UserInfoTable.FIELD_FAVORITES_COUNT,
				user.getFavouritesCount());
		
		args.put(UserInfoTable.FIELD_STATUSES_COUNT,
				user.getStatusesCount());
		
		args.put(UserInfoTable.FIELD_FOLLOWING, user.isFollowing());
		
		return Db.update(UserInfoTable.TABLE_NAME, args, UserInfoTable._ID+"='"+user.getId()+"'", null)>0;
		
    }
    /**
     * 同步用户,更新已存在的用户,插入未存在的用户
     */
    public void syncUsers(List<com.ch_linghu.fanfoudroid.data.User> users){
    	SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();
    	try{
    		mDb.beginTransaction();
    	for(com.ch_linghu.fanfoudroid.data.User u:users){
    		if(existsUser(u.id)){
    			updateUser(u);
    		}else{
    			createUserInfo(u);
    		}
    	}
    	mDb.setTransactionSuccessful();
    	} finally {
            mDb.endTransaction();
        }
    	
    }
    
    public void syncWeiboUsers(List<com.ch_linghu.fanfoudroid.weibo.User> users){
    	SQLiteDatabase mDb = mOpenHelper.getWritableDatabase();
    	
    	try{
    		 	mDb.beginTransaction();
    	for(com.ch_linghu.fanfoudroid.weibo.User u:users){
    		if(existsUser(u.getId())){
    			updateWeiboUser(u);
    		}else{
    			createWeiboUserInfo(u);
    		}
    	}
    	mDb.setTransactionSuccessful();
    	} finally {
            mDb.endTransaction();
        }
   
    }

    

}