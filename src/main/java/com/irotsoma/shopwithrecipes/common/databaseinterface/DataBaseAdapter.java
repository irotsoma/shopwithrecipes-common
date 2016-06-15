package com.irotsoma.shopwithrecipes.common.databaseinterface;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.widget.Toast;

import com.irotsoma.shopwithrecipes.common.GlobalConstants;
import com.irotsoma.shopwithrecipes.common.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

//TODO: reduce size of content image
public class DataBaseAdapter {
	 
	public static final int DB_VERSION = 16;

    // Object for intrinsic lock
    public static final Object primaryDataBaseLock = new Object();

    private DataBaseHelper primaryDbHelper;
    public SQLiteDatabase primaryDataBase;

    private DataBaseHelper tempDbHelper;
    private SQLiteDatabase tempDataBase;
	
    private boolean noSync = false;

    private String primaryDbFileName;
    public String getPrimaryDbFileName() {
        return primaryDbFileName;
    }

    public boolean hasBeenSynced(){
		return primaryDbHelper.hasBeenSynced();
		
	}
    private void insertNewUUID(SQLiteDatabase db){
    	db.delete("sync_database", "_id = 0", null);
		UUID newUUID = UUID.randomUUID();
		ContentValues insertValues = new ContentValues();
		insertValues.put("_id", 0);
		insertValues.put("sync_database_UUID", newUUID.toString());
		db.insert("sync_database", null, insertValues);
	}
    public boolean isOpen(){
        return primaryDataBase != null && primaryDataBase.isOpen();
    }
 
    private final Context myContext;
    public DataBaseAdapter(Context context) 
    {
        try {
            String currentVersionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            switch (currentVersionName.substring(0,1)) {
                case "1":   primaryDbFileName = GlobalConstants.V1_DB_FILE_NAME;
                    break;
                case "2":   primaryDbFileName = GlobalConstants.V2_DB_FILE_NAME;
                    break;
                case "3":   primaryDbFileName = GlobalConstants.V3_DB_FILE_NAME;
                    break;
                default:    throw new Error("Can not determine application version.");
            }
        }
        catch (PackageManager.NameNotFoundException e){
            throw new Error(e);
        }
        this.myContext = context;
    }
    @Override
    protected void finalize(){
    	if (this.isOpen())
    	{
    		primaryDataBase.close();
    	}
    	if (tempDataBase != null)
    	{
	    	if (tempDataBase.isOpen())
	    	{
	    		tempDataBase.close();
	    	}
    	}
    	try {
			super.finalize();
		} catch (Throwable e) {
			e.printStackTrace();
		}
    }
    private boolean isExternalStorageAvail() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
     }
    public String RestoreFromSDCard() throws SQLException {
    	if (!isExternalStorageAvail())
    	{
    		return myContext.getString(R.string.common_external_storage_unavailable_message);
    	}
    	if (primaryDataBase != null)
    	{
            if (primaryDataBase.isOpen())
	    	{
	    		primaryDataBase.close();
	    	}
    	}
    	File exportDir = new File(Environment.getExternalStorageDirectory(), GlobalConstants.EXPORT_SDCARD_DIRECTORY);
    	if (!exportDir.exists())
    	{
    		return myContext.getString(R.string.common_backup_file_does_not_exist_message);
    	}
    	File inputFile = new File(exportDir, getPrimaryDbFileName());
    	if (!inputFile.exists())
    	{
    		return myContext.getString(R.string.common_backup_file_does_not_exist_message);
    	}
        String dbPath;
        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            this.open();
            dbPath = primaryDataBase.getPath();
            this.close();
        }
    	try {
			CopyDataBaseFromSD(dbPath);
		} catch (IOException e) {
			return myContext.getString(R.string.common_error_copying_file_label)+e.getMessage();
		}
    	return null;
    }
    public String BackupToSDCard() throws SQLException {
    	return BackupToSDCard(null, null);
    }
    
    public String BackupToSDCard(File sdFileDirectory, String sdFileName) throws SQLException {
    	/*if (!isExternalStorageAvail())
    	{
    		return "External storage is not available, unable to backup data.";
    	}*/
    	if (primaryDataBase != null)
    	{
	    	/*if (primaryDataBase.isDbLockedByOtherThreads())
	    	{
	    		return "Database is in use and can not be backed up.";
	    	}
	    	else*/
            if (primaryDataBase.isOpen())
	    	{
	    		primaryDataBase.close();
	    	}
    	}
        String dbPath;
        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            this.open();
            dbPath = primaryDataBase.getPath();
            this.close();
        }
    	try {
			CopyDataBaseToSD(dbPath, sdFileDirectory, sdFileName);
		} catch (IOException e) {
			return myContext.getString(R.string.common_error_copying_file_label)+e.getMessage();
		}
    	return null;
    }
    public String BackupTempDbToSDCard(File sdFileDirectory, String sdFileName) throws IOException {
    	/*if (!isExternalStorageAvail())
    	{
    		return "External storage is not available, unable to backup data.";
    	}*/
    	if (tempDataBase != null)
    	{
	    	/*if (tempDataBase.isDbLockedByOtherThreads())
	    	{
	    		return "Database is in use and can not be backed up.";
	    	}
	    	else*/
            if (tempDataBase.isOpen())
	    	{
	    		tempDataBase.close();
	    	}
    	}
    	tempDbHelper = new DataBaseHelper(myContext, GlobalConstants.TEMP_DB_FILE_NAME);
		SQLiteDatabase checkDB = tempDbHelper.getReadableDatabase();
  		String dbPath = checkDB != null ? checkDB.getPath() : null;
        assert checkDB != null;
        checkDB.close();
  		InputStream tempDatabaseStream = new FileInputStream(dbPath);
  		sdFileDirectory.mkdirs();
  		File outputFile = new File(sdFileDirectory, sdFileName);
		OutputStream myOutput = new FileOutputStream(outputFile);

    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = tempDatabaseStream.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
     	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	tempDatabaseStream.close();
    	return null;
    }
    private void CopyDataBaseFromSD(String dbPath) throws IOException {
    	OutputStream myOutput = new FileOutputStream(dbPath);
    	File exportDir = new File(Environment.getExternalStorageDirectory(), GlobalConstants.EXPORT_SDCARD_DIRECTORY);
        File inputFile = new File(exportDir, getPrimaryDbFileName());
    	InputStream myInput = new FileInputStream(inputFile);
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInput.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
 
    	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	myInput.close();
    }
    private void CopyDataBaseToSD(String dbPath, File targetDirectory, String targetFileName) throws IOException {
    	InputStream myInput = new FileInputStream(dbPath);
    	File exportDir;
    	
    	if (targetDirectory == null)
		{
    		exportDir = new File(Environment.getExternalStorageDirectory(), GlobalConstants.EXPORT_SDCARD_DIRECTORY);
		}
    	else
    	{
    		exportDir = targetDirectory;
    	}
    	exportDir.mkdirs();
        File outputFile;
        if ((targetFileName == null) || (targetFileName.equals("")))
        {
        	outputFile = new File(exportDir, getPrimaryDbFileName());
        }
        else
        {
        	outputFile = new File(exportDir, targetFileName);
        }
       	outputFile.createNewFile();
        
    	OutputStream myOutput = new FileOutputStream(outputFile);
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInput.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
 
    	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	myInput.close();
    }
    //TODO: performance enhancements around open
    public DataBaseAdapter open() throws SQLException {
    	String dbPath;
    	primaryDbHelper = new DataBaseHelper(myContext);
    	try{
    		primaryDataBase = primaryDbHelper.getWritableDatabase();
	    	dbPath = primaryDataBase != null ? primaryDataBase.getPath() : null;
	  		//int test = checkDB.getVersion();
	  		
    	}
    	catch (Exception e)
    	{
    		throw new Error(myContext.getString(R.string.common_error_opening_database_label)+e.getMessage());
    	}
    	
  		if (primaryDbHelper.newDataBaseFlag){
            this.close();
            copyDataBaseFromAssets(dbPath);
            try{
                primaryDataBase = primaryDbHelper.getWritableDatabase();
            }
            catch (Exception e)
            {
                throw new Error(myContext.getString(R.string.common_error_opening_database_label)+e.getMessage());
            }
  		}
      
        //primaryDataBase = primaryDbHelper.getWritableDatabase();
        return this;
    }
    private void copyDataBaseFromAssets(String pathToDatabase) {
		try
		{
	    	InputStream myInput = myContext.getAssets().open(GlobalConstants.ASSETS_DB_FILE_NAME);

	    	//Open the empty db as the output stream
	    	OutputStream myOutput = new FileOutputStream(pathToDatabase);

	    	//transfer bytes from the inputfile to the outputfile
	    	byte[] buffer = new byte[1024];
	    	int length;
	    	while ((length = myInput.read(buffer))>0){
	    		myOutput.write(buffer, 0, length);
	    	}

	    	//Close the streams
	    	myOutput.flush();
	    	myOutput.close();
	    	myInput.close();
		}
		catch (Exception e)
		{
			throw new Error(myContext.getString(R.string.common_error_copying_file_label)+ e.getMessage());
		}
        primaryDbHelper = new DataBaseHelper(myContext);
		SQLiteDatabase newDB = primaryDbHelper.getWritableDatabase();
		insertNewUUID(newDB);
        assert newDB != null;
        newDB.setVersion(DB_VERSION);
		newDB.close();
		primaryDbHelper.newDataBaseFlag = false;
    }
    private void copyAndOpenTempDataBase(InputStream externalDatabaseStream) {
    	noSync = true;
    	try
		{
    		
    		tempDbHelper = new DataBaseHelper(myContext, GlobalConstants.TEMP_DB_FILE_NAME);
    		
    		SQLiteDatabase checkDB = tempDbHelper.getReadableDatabase();
      		String dbPath = checkDB != null ? checkDB.getPath() : null;
      		tempDbHelper.close();
      		tempDbHelper = null;
      		
      		
    		OutputStream myOutput = new FileOutputStream(dbPath);

        	byte[] buffer = new byte[1024];
        	int length;
        	while ((length = externalDatabaseStream.read(buffer))>0){
        		myOutput.write(buffer, 0, length);
        	}
     
        	//Close the streams
        	myOutput.flush();
        	myOutput.close();
        	externalDatabaseStream.close();
		}
		catch (Exception e)
		{
			throw new Error(myContext.getString(R.string.common_error_copying_file_label) + e.getMessage());
		}
    	tempDbHelper = new DataBaseHelper(myContext, GlobalConstants.TEMP_DB_FILE_NAME);
		tempDataBase = tempDbHelper.getWritableDatabase();
        assert tempDataBase != null;
        tempDataBase.setVersion(DB_VERSION);
		noSync = false;
    }
    private void copyTempDatabaseTo(File outputFile) throws IOException {
    	
    	OutputStream myOutputStream = new FileOutputStream(outputFile);
    	tempDbHelper = new DataBaseHelper(myContext, GlobalConstants.TEMP_DB_FILE_NAME);
    	tempDbHelper.close();
    	SQLiteDatabase checkDB = tempDbHelper.getReadableDatabase();
  		String tempDbPath = checkDB != null ? checkDB.getPath() : null;
        assert checkDB != null;
        checkDB.close();
    	InputStream myInputStream = new FileInputStream(tempDbPath);
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInputStream.read(buffer))>0){
    		myOutputStream.write(buffer, 0, length);
    	}
 
    	//Close the streams
    	myOutputStream.flush();
    	myOutputStream.close();
    	myInputStream.close();
    }
    
    private void copyAndOpenTempDataBase(File externalDatabaseFile) throws IOException {
    	copyAndOpenTempDataBase(new FileInputStream(externalDatabaseFile));
    }
    public void resetDatabaseToDefault()
    {
        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            primaryDbHelper.newDataBaseFlag = true;
            this.open();
            this.close();
        }
    }
    
    public boolean ImportDatabase(File inputDBFile, boolean syncContent, boolean inputWins, int startDbVersion)
    {
    	try
    	{
    		copyAndOpenTempDataBase(inputDBFile);
    	}
    	catch (IOException e)
    	{
    		e.printStackTrace();
    		return false;
    	}
    	if (primaryDbHelper == null)
		{
			primaryDbHelper = new DataBaseHelper(myContext);
		}
    	if (!this.isOpen())
		{
            boolean returnValue;
            synchronized (DataBaseAdapter.primaryDataBaseLock) {
                synchronized (DataBaseAdapter.primaryDataBaseLock) {
                    this.open();
                    returnValue = SyncDatabases(tempDbHelper, primaryDataBase, true, inputWins, startDbVersion);
                    this.close();
                }
            }
			return returnValue;
		}
    	
    	else
    	{
    		return SyncDatabases(tempDbHelper, primaryDataBase, syncContent, inputWins, startDbVersion);
    	}

    }public boolean ImportDatabase(InputStream inputDBStream, boolean syncContent, boolean inputWins, int startDbVersion)
    {
   		copyAndOpenTempDataBase(inputDBStream);

    	if (primaryDbHelper == null)
		{
			primaryDbHelper = new DataBaseHelper(myContext);
		}
    	if (!this.isOpen())
		{
            boolean returnValue;
            synchronized (DataBaseAdapter.primaryDataBaseLock) {
                synchronized (DataBaseAdapter.primaryDataBaseLock) {
                    this.open();
                    returnValue = SyncDatabases(tempDbHelper, primaryDataBase, true, inputWins, startDbVersion);
                    this.close();
                }
            }
    		return returnValue;
		}
    	else
    	{
    		return SyncDatabases(tempDbHelper, primaryDataBase, syncContent, inputWins, startDbVersion);
    	}

    }
    public boolean ExportDatabase(File targetDBFile, boolean syncContent, boolean inputWins, int startDbVersion)
    {

    	try
    	{
    		copyAndOpenTempDataBase(targetDBFile);
    	}
    	catch (IOException e)
    	{
    		e.printStackTrace();
    		return false;
    	}
        boolean returnValue;
        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            if (primaryDbHelper == null) {
                primaryDbHelper = new DataBaseHelper(myContext);
            }

            returnValue = SyncDatabases(primaryDbHelper, tempDataBase, syncContent, inputWins, startDbVersion);
            tempDataBase.close();
        }
    	if (returnValue)
    	{
	    	try {
				copyTempDatabaseTo(targetDBFile);
			} catch (IOException e) {
				
				e.printStackTrace();
				return false;
			}
    	}
    	return returnValue;
    }
    public boolean ExportDatabase(InputStream targetDBFile, boolean syncContent, boolean inputWins, int startDbVersion)
    {
   		copyAndOpenTempDataBase(targetDBFile);
        boolean returnValue;
        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            if (primaryDbHelper == null) {
                primaryDbHelper = new DataBaseHelper(myContext);
            }
            returnValue = SyncDatabases(primaryDbHelper, tempDataBase, syncContent, inputWins, startDbVersion);
            tempDataBase.close();
        }
    	return returnValue;
    }
    public boolean SyncWithContent(SQLiteDatabase currentDb, boolean contentWins, int startDbVersion)
    {
    	if (noSync) {
    		return false;
    	}
    	
    	try {
			copyAndOpenTempDataBase(myContext.getAssets().open(getPrimaryDbFileName()));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		if (currentDb == null)
		{
            boolean returnValue;
            synchronized (DataBaseAdapter.primaryDataBaseLock) {
                this.open();
                returnValue = SyncDatabases(tempDbHelper, primaryDataBase, true, contentWins, startDbVersion);
                this.close();
            }
			return returnValue;
		}
		else if (!currentDb.isOpen())
		{
            boolean returnValue;
            synchronized (DataBaseAdapter.primaryDataBaseLock) {
                this.open();
                //currentDb = primaryDataBase;
                returnValue = SyncDatabases(tempDbHelper, primaryDataBase, true, contentWins, startDbVersion);
                this.close();
            }
            return returnValue;
		}
		else
		{
			return SyncDatabases(tempDbHelper, currentDb, true, contentWins, startDbVersion);
		}
    }
    
    private int DeleteRows(SQLiteDatabase inputDb, SQLiteDatabase targetDb, Date now)
    {
    	int deletedRowCount = 0;
    	//delete using delete rows and update last synced date if the delete actually occurred in the target database.
    	Cursor inputCursor;
    	if (now == null){
    		now = new Date();
    	}
		DateFormat nowFormatted = DateFormat.getDateTimeInstance();    	
		try
		{
    		inputCursor = inputDb.query("deleted_row", new String[] { "_id", "deleted_row_table_name", "deleted_row_UUID", }, null, null, null, null, null);
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		inputCursor.moveToFirst();
		while (!inputCursor.isAfterLast())
		{
			String tableName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("deleted_row_table_name"));
			int returnValue = targetDb.delete(tableName, tableName + "_UUID = '"+inputCursor.getString(inputCursor.getColumnIndexOrThrow("deleted_row_UUID"))+"'", null);
			if (returnValue > 0)
			{
				ContentValues deleteDateTimeValue = new ContentValues();
				deleteDateTimeValue.put("deleted_row_synced_date_time", nowFormatted.format(now));
				inputDb.update("deleted_row", deleteDateTimeValue, "_id = "+inputCursor.getString(inputCursor.getColumnIndexOrThrow("_id")), null);
				deletedRowCount++;
			}
			inputCursor.moveToNext();
		}
		inputCursor.close();
		return deletedRowCount;
    }
    
    private boolean SyncDatabases(DataBaseHelper inputDbHelper, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int syncContentDbVersionAndUp)
    {
    	//close existing connections and reopen the databases
//    	if (inputDbHelper != null){
//    		inputDbHelper.close();
//    	}
//    	if (targetDbHelper != null){
//    		targetDbHelper.close();
//    	}
    	if (noSync){
    		return false;
    	}
    	
    	
    	if (targetDb == null)
    	{
    		return false;
    	}
    	if (inputDbHelper == null)
    	{
    		return false;
    	}
    	SQLiteDatabase inputDb = inputDbHelper.getWritableDatabase();
    	
    	if ((!targetDb.isOpen()) /*|| (targetDb.isDbLockedByOtherThreads())*/)
    	{
    		return false;
    	}
    	//SQLiteDatabase targetDb = targetDbHelper.getWritableDatabase();
    	
//    	boolean returnValue = InsertContentIDZeroRecordsIfMissing(inputDb, targetDb);
//    	if (!returnValue)
//    	{
//    		return false;
//    	}
    	
    	//delete pending delete rows then reopen the input db as read-only
    	Date now = new Date();
    	DateFormat df = DateFormat.getDateTimeInstance();

 		@SuppressWarnings("UnusedAssignment")
        int rowsDeletedCount = DeleteRows(inputDb, targetDb, now);
        assert inputDb != null;
        inputDb.close();
    	inputDb = inputDbHelper.getReadableDatabase();
    	
    	//sync sources
    	HashMap<Long, Long> sourceCrosswalk = SyncSources(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp);
    	//sync ingredient groups
    	HashMap<Long, Long> ingredientGroupCrosswalk = SyncIngredientGroups(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, sourceCrosswalk);
    	//sync ingredient measurements
    	HashMap<Long, Long> ingredientMeasurementCrosswalk = SyncIngredientMeasurements(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp);    	
    	//sync ingredients
    	HashMap<Long, Long> ingredientCrosswalk = SyncIngredients(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, ingredientMeasurementCrosswalk, ingredientGroupCrosswalk, sourceCrosswalk);
    	//sync ingredient measurement conversions
        @SuppressWarnings("UnusedAssignment")
		HashMap<Long, Long> ingredientMeasurementConversionCrosswalk = SyncIngredientMeasurementConversions(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, ingredientCrosswalk, ingredientMeasurementCrosswalk);
    	//sync ingredient package sizes
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> ingredientPackageCrosswalk = SyncIngredientPackages(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, ingredientCrosswalk, ingredientMeasurementCrosswalk);
    	//sync ingredient to ingredient measurement links
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> ingredientToCustomIngredientMeasurementLinkCrosswalk = SyncIngredientToIngredientMeasurementLinks(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, ingredientCrosswalk, ingredientMeasurementCrosswalk);
    	//sync recipes
    	HashMap<Long, Long> recipeCrosswalk = SyncRecipes(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp);
    	//sync recipe categories
    	HashMap<Long, Long> recipeCategoryCrosswalk = SyncRecipeCategories(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp);
    	//sync recipe ingredients
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> recipeIngredientCrosswalk = SyncRecipeIngredients(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, recipeCrosswalk, ingredientCrosswalk, ingredientMeasurementCrosswalk);
    	//sync recipe to recipe category links
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> recipeToRecipeCategoryCrosswalk = SyncRecipeToRecipeCategoryLinks(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, recipeCrosswalk, recipeCategoryCrosswalk);
    	//sync shopping lists
    	HashMap<Long, Long> shoppingListCrosswalk = SyncShoppingLists(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp);
    	//sync shopping list ingredients
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> shoppingListIngredientCrosswalk = SyncShoppingListIngredients(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, shoppingListCrosswalk, ingredientCrosswalk, ingredientMeasurementCrosswalk);
    	//sync sub recipes
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> subRecipeCrosswalk = SyncSubRecipes(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, recipeCrosswalk);
    	//sync recipe images
        @SuppressWarnings("UnusedAssignment")
    	HashMap<Long, Long> recipeImageCrosswalk = SyncRecipeImage(inputDb, targetDb, syncContent, inputWins, syncContentDbVersionAndUp, recipeCrosswalk);
    	
    	
    	Cursor inputCursor;
    	if ((inputDbHelper.getUUID() != null) && (!inputDbHelper.getUUID().equals(GlobalConstants.CONTENT_DATABASE_UUID))) {
    		

			try
			{
	    		inputCursor = targetDb.query("sync_database", new String[] { "_id", "sync_database_date_time" }, "sync_database_UUID = ?", new String[] { inputDbHelper.getUUID() }, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
			}
			if (inputCursor.moveToFirst()){
				
				ContentValues updateValues = new ContentValues();
				updateValues.put("sync_database_date_time", df.format(now));
				targetDb.update("sync_database", updateValues, "sync_database_UUID = ?", new String[] { inputDbHelper.getUUID() });
			}
			else{
				ContentValues insertValues = new ContentValues();
				insertValues.put("sync_database_date_time", df.format(now));
				insertValues.put("sync_database_UUID", inputDbHelper.getUUID());
				targetDb.insert("sync_database", null, insertValues);
			}
			inputCursor.close();
		}
    	//if the input database has never synced with another database other than the target then clear the delete table as it is now fully synced
    	Cursor otherSyncsCursor;
    	try
		{
    		otherSyncsCursor = targetDb.query("sync_database", new String[] { "_id", "sync_database_date_time" }, "sync_database_UUID <> ? AND _id <> 0", new String[] { inputDbHelper.getUUID() }, null, null, null);
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	
    	if (!otherSyncsCursor.moveToNext()){
            assert inputDb != null;
            inputDb.delete("deleted_row", null, null);
    	}
    	else
    	{
	    	//otherwise clean up deleted row table based on the time since the data was last synced to save some space on data that hasn't been used in a while
	    	try
			{
                assert inputDb != null;
                inputCursor = inputDb.query("deleted_row", new String[] { "_id", "deleted_row_synced_date_time" }, "NOT deleted_row_synced_date_time IS NULL", null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
			}
			//inputCursor.moveToFirst();
			while(inputCursor.moveToNext())
			{
				
				Date syncDate;
				try {
					syncDate = df.parse(inputCursor.getString(inputCursor.getColumnIndexOrThrow("deleted_row_synced_date_time")));
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					inputCursor.close();
					return false;
				} catch (ParseException e) {
					e.printStackTrace();
					inputCursor.close();
					return false;
				}
				long daysDiff = (now.getTime() - syncDate.getTime()) / GlobalConstants.MILLSECS_PER_DAY;
				if (daysDiff > GlobalConstants.DELETE_SYNC_DATA_AFTER_DAYS)
				{
					inputDb.delete("deleted_row", "_id = "+ inputCursor.getString(inputCursor.getColumnIndexOrThrow("_id")), null);
				}

			}
			inputCursor.close();
			
			//and get rid of any databases that haven't been synced recently as well
			do {
				Date syncDate;
				try {
					syncDate = df.parse(otherSyncsCursor.getString(otherSyncsCursor.getColumnIndexOrThrow("sync_database_date_time")));
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					inputCursor.close();
					return false;
				} catch (ParseException e) {
					e.printStackTrace();
					inputCursor.close();
					return false;
				}
				long daysDiff = (now.getTime() - syncDate.getTime()) / GlobalConstants.MILLSECS_PER_DAY;
				if (daysDiff > GlobalConstants.DELETE_SYNC_DATA_AFTER_DAYS)
				{
					inputDb.delete("deleted_row", "_id = "+ otherSyncsCursor.getString(otherSyncsCursor.getColumnIndexOrThrow("_id")), null);
				}
			} while (otherSyncsCursor.moveToNext());
			otherSyncsCursor.close();
			
    	}
    	
		
		
		
		
   		inputDb.close();
		return true;
    }
    private HashMap<Long, Long> SyncSources(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(source_is_content = 0) OR (source_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(source_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("source", new String[] { "_id", "source_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("source_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("source", new String[] { "_id", "source_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("source_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> sourceCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			sourceCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any ingredient groups in crosswalk 
    	for (Long currentKey : sourceCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("source", new String[] { "_id", "source_name", "source_is_content", "source_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("source_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("source_name")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_is_content")) == 1)
        		{
        			updatedValues.put("source_is_content", 1);
        			updatedValues.put("source_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_content_database_version")));
        		}
        	}
        	else
        	{

        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_is_content")) == 1)
        		{
        			updatedValues.put("source_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("source_name")));
        			updatedValues.put("source_is_content", 1);
        			updatedValues.put("source_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_content_database_version")));
        		}

        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("source", updatedValues, "_id = "+sourceCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any ingredient groups left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("source", new String[] { "_id", "source_name", "source_is_content", "source_content_database_version", "source_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
    		long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("source_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_is_content")));
    		insertValues.put("source_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("source_content_database_version")));
    		insertValues.put("source_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("source_UUID")));
    		
    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String sourceName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("source_name"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("source_name", sourceName + nameSuffix);
    			targetID = targetDb.insert("source", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			sourceCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return sourceCrosswalk;
	}
    
	private HashMap<Long, Long> SyncIngredientGroups(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> sourceCrosswalk) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(ingredient_group_is_content = 0) OR (ingredient_group_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_group_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("ingredient_group", new String[] { "_id", "ingredient_group_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_group_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("ingredient_group", new String[] { "_id", "ingredient_group_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientGroupCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			ingredientGroupCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any ingredient groups in crosswalk 
    	for (Long currentKey : ingredientGroupCrosswalk.keySet())
    	{

    		try
    		{
        		inputCursor = inputDb.query("ingredient_group", new String[] { "_id", "ingredient_group_description", "ingredient_group_is_content", "ingredient_group_content_database_version", "id_source", "ingredient_group_source_id"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	long inputSourceID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_source"));
        	long sourceID = (sourceCrosswalk.get(inputSourceID) == null) ? inputSourceID : sourceCrosswalk.get(inputSourceID);
 
        	    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("ingredient_group_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_description")));
        		updatedValues.put("id_source", sourceID);
        		updatedValues.put("ingredient_group_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_source_id")));

        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_group_is_content", 1);
        			updatedValues.put("ingredient_group_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_content_database_version")));
        		}
        	}
        	else
        	{

        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_group_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_description")));
        			updatedValues.put("ingredient_group_is_content", 1);
        			updatedValues.put("ingredient_group_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_content_database_version")));
            		updatedValues.put("id_source", sourceID);
            		updatedValues.put("ingredient_group_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_source_id")));
        		}
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient_group", updatedValues, "_id = "+ingredientGroupCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any ingredient groups left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient_group", new String[] { "_id", "ingredient_group_description", "ingredient_group_is_content", "ingredient_group_content_database_version", "ingredient_group_UUID", "id_source", "ingredient_group_source_id" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	long inputSourceID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_source"));
        	long sourceID = (sourceCrosswalk.get(inputSourceID) == null) ? inputSourceID : sourceCrosswalk.get(inputSourceID);
 
        	
    		long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("ingredient_group_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_is_content")));
    		insertValues.put("ingredient_group_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_group_content_database_version")));
    		insertValues.put("ingredient_group_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_UUID")));
    		insertValues.put("id_source", sourceID);
    		insertValues.put("ingredient_group_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_source_id")));

    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputIngredientGroupDescription = inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_group_description"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("ingredient_group_description", inputIngredientGroupDescription + nameSuffix);
    			targetID = targetDb.insert("ingredient_group", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			ingredientGroupCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return ingredientGroupCrosswalk;
	}
	
	
	
    
    private HashMap<Long, Long> SyncRecipeImage(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> recipeCrosswalk) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(recipe_image_is_content = 0) OR (recipe_image_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(recipe_image_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("recipe_image", new String[] { "_id", "recipe_image_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_image_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("recipe_image", new String[] { "_id", "recipe_image_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> recipeImageCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			recipeImageCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	
    	
    	//update any sub recipes in crosswalk 
    	for (Long currentKey : recipeImageCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("recipe_image", new String[] { "_id", "id_recipe", "recipe_image_name", "recipe_image_image", "recipe_image_order", "recipe_image_is_content", "recipe_image_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    	
        	//crosswalk recipes and sub-recipes
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);

        	
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_recipe", recipeID);
        		updatedValues.put("recipe_image_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_name")));
        		updatedValues.put("recipe_image_image", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_image_image")));
        		updatedValues.put("recipe_image_order", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_order")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_is_content")) == 1)
        		{
        			updatedValues.put("recipe_image_is_content", 1);
        			updatedValues.put("recipe_image_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("recipe_image", new String[] { "_id", "recipe_image_name", "recipe_image_image", "recipe_image_order"}, "_id = "+recipeImageCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_image_name")).trim().equals("")) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_is_content")) == 0))
        		{
        			updatedValues.put("recipe_image_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_name")));
        		}
        		if ((targetCursor.getBlob(targetCursor.getColumnIndexOrThrow("recipe_image_name")) == null) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_is_content")) == 0))
        		{
        			updatedValues.put("recipe_image_image", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_image_image")));
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_is_content")) == 1)
        		{
        			updatedValues.put("id_recipe", recipeID);
        			updatedValues.put("recipe_image_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_name")));
        			updatedValues.put("recipe_image_image", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_image_image")));
        			updatedValues.put("recipe_image_order", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_order")));
        			updatedValues.put("recipe_image_is_content", 1);
        			updatedValues.put("recipe_image_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("recipe_image", updatedValues, "_id = "+recipeImageCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any sub recipes left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("recipe_image", new String[] { "_id", "id_recipe", "recipe_image_name", "recipe_image_image", "recipe_image_order", "recipe_image_is_content", "recipe_image_content_database_version", "recipe_image_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk recipes and sub-recipes
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
       	

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_recipe", recipeID);
    		insertValues.put("recipe_image_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_name")));
    		insertValues.put("recipe_image_image", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_image_image")));
    		insertValues.put("recipe_image_order", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_order")));
    		insertValues.put("recipe_image_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_is_content")));
    		insertValues.put("recipe_image_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_image_content_database_version")));
    		insertValues.put("recipe_image_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_image_UUID")));

    		long targetID = targetDb.insert("recipe_image", "_id", insertValues);
    		if (targetID != -1)
    		{
    			recipeImageCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	
    	//fix order if it got distorted by sync
    	try
		{
    		targetCursor = targetDb.query("recipe_image", new String[] { "_id", "id_recipe", "recipe_image_order" }, null, null, null, null, "id_recipe, recipe_image_order");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	targetCursor.moveToFirst();
    	
    	ContentValues updateOrderValues = new ContentValues();
    	Long previousRecipeID = targetCursor.getLong(targetCursor.getColumnIndexOrThrow("id_recipe"));
    	int currentCounter = 0;
    	while (!targetCursor.isAfterLast())
    	{
    		if (previousRecipeID == targetCursor.getLong(targetCursor.getColumnIndexOrThrow("id_recipe")))
    		{
    			currentCounter++;
    		}
    		else
    		{
    			currentCounter = 1;
    		}
    		updateOrderValues.put("recipe_image_order", currentCounter);
    		targetDb.update("recipe_image", updateOrderValues, "_id = "+targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")), null);
    		
    		previousRecipeID = targetCursor.getLong(targetCursor.getColumnIndexOrThrow("id_recipe"));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	return recipeImageCrosswalk;
	}
    

	private HashMap<Long, Long> SyncSubRecipes(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> recipeCrosswalk) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(sub_recipe_is_content = 0) OR (sub_recipe_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(sub_recipe_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("sub_recipe", new String[] { "_id", "sub_recipe_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("sub_recipe_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("sub_recipe", new String[] { "_id", "sub_recipe_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("sub_recipe_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> subRecipeCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			subRecipeCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	//update any sub recipes in crosswalk 
    	for (Long currentKey : subRecipeCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("sub_recipe", new String[] { "_id", "id_recipe", "id_sub_recipe", "sub_recipe_quantity", "sub_recipe_is_content", "sub_recipe_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    	
        	//crosswalk recipes and sub-recipes
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputSubRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_sub_recipe"));
        	long subRecipeID = (recipeCrosswalk.get(inputSubRecipeID) == null) ? inputSubRecipeID : recipeCrosswalk.get(inputSubRecipeID);
        	
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_recipe", recipeID);
        		updatedValues.put("id_sub_recipe", subRecipeID);
        		updatedValues.put("sub_recipe_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("sub_recipe_quantity")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_is_content")) == 1)
        		{
        			updatedValues.put("sub_recipe_is_content", 1);
        			updatedValues.put("sub_recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("sub_recipe", new String[] { "_id", "sub_recipe_quantity"}, "_id = "+subRecipeCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getDouble(targetCursor.getColumnIndexOrThrow("sub_recipe_quantity")) == 0) && (inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("sub_recipe_quantity")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_is_content")) == 0))
        		{
        			updatedValues.put("sub_recipe_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("sub_recipe_quantity")));
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_is_content")) == 1)
        		{
        			updatedValues.put("id_recipe", recipeID);
            		updatedValues.put("id_sub_recipe", subRecipeID);
            		updatedValues.put("sub_recipe_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("sub_recipe_quantity")));
        			updatedValues.put("sub_recipe_is_content", 1);
        			updatedValues.put("sub_recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("sub_recipe", updatedValues, "_id = "+subRecipeCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any sub recipes left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("sub_recipe", new String[] { "_id", "id_recipe", "id_sub_recipe", "sub_recipe_quantity", "sub_recipe_is_content", "sub_recipe_content_database_version", "sub_recipe_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk recipes and sub-recipes
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputSubRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_sub_recipe"));
        	long subRecipeID = (recipeCrosswalk.get(inputSubRecipeID) == null) ? inputSubRecipeID : recipeCrosswalk.get(inputSubRecipeID);
        	

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_recipe", recipeID);
    		insertValues.put("id_sub_recipe", subRecipeID);
    		insertValues.put("sub_recipe_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("sub_recipe_quantity")));
    		insertValues.put("sub_recipe_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_is_content")));
    		insertValues.put("sub_recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("sub_recipe_content_database_version")));
    		insertValues.put("sub_recipe_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("sub_recipe_UUID")));

    		long targetID = targetDb.insert("sub_recipe", "_id", insertValues);
    		if (targetID != -1)
    		{
    			subRecipeCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return subRecipeCrosswalk;
    	
    	
    	
    	
	}
	private HashMap<Long, Long> SyncShoppingListIngredients(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> shoppingListCrosswalk, HashMap<Long, Long> ingredientCrosswalk, HashMap<Long, Long> ingredientMeasurementCrosswalk) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(shopping_list_ingredient_is_content = 0) OR (shopping_list_ingredient_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(shopping_list_ingredient_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("shopping_list_ingredient", new String[] { "_id", "shopping_list_ingredient_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("shopping_list_ingredient_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("shopping_list_ingredient", new String[] { "_id", "shopping_list_ingredient_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> shoppingListIngredientCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			shoppingListIngredientCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any shopping list ingredients in crosswalk 
    	for (Long currentKey : shoppingListIngredientCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("shopping_list_ingredient", new String[] { "_id", "id_shopping_list", "id_ingredient", "ingredient_quantity", "id_ingredient_measurement", "shopping_list_ingredient_is_content", "shopping_list_ingredient_content_database_version", "shopping_list_ingredient_is_checked"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    	
        	//crosswalk shopping lists, ingredients and ingredient measurements
        	long inputShoppingListID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_shopping_list"));
        	long shoppingListID = (shoppingListCrosswalk.get(inputShoppingListID) == null) ? inputShoppingListID : shoppingListCrosswalk.get(inputShoppingListID);
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);
        	
        	
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_shopping_list", shoppingListID);
        		updatedValues.put("id_ingredient", ingredientID);
        		updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        		updatedValues.put("shopping_list_ingredient_is_checked", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_checked")));
        		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_content")) == 1)
        		{
        			updatedValues.put("shopping_list_ingredient_is_content", 1);
        			updatedValues.put("shopping_list_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("shopping_list_ingredient", new String[] { "_id", "ingredient_quantity", "id_ingredient_measurement" }, "_id = "+shoppingListIngredientCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getInt(targetCursor.getColumnIndexOrThrow("id_ingredient_measurement")) == 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_content")) == 0))
        		{
        			updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_content")) == 1)
        		{
        			updatedValues.put("id_shopping_list", shoppingListID);
            		updatedValues.put("id_ingredient", ingredientID);
            		updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
            		updatedValues.put("shopping_list_ingredient_is_checked", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_checked")));
        			updatedValues.put("shopping_list_ingredient_is_content", 1);
        			updatedValues.put("shopping_list_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("shopping_list_ingredient", updatedValues, "_id = "+shoppingListIngredientCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any shopping list ingredients left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("shopping_list_ingredient", new String[] { "_id", "id_shopping_list", "id_ingredient", "ingredient_quantity", "id_ingredient_measurement", "shopping_list_ingredient_is_content", "shopping_list_ingredient_content_database_version", "shopping_list_ingredient_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk recipes, ingredients and ingredient measurements
        	long inputShoppingListID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_shopping_list"));
        	long shoppingListID = (shoppingListCrosswalk.get(inputShoppingListID) == null) ? inputShoppingListID : shoppingListCrosswalk.get(inputShoppingListID);
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);
        	

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_shopping_list", shoppingListID);
    		insertValues.put("id_ingredient", ingredientID);
    		insertValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
    		insertValues.put("id_ingredient_measurement", ingredientMeasurementID);
    		insertValues.put("shopping_list_ingredient_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_is_content")));
    		insertValues.put("shopping_list_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_content_database_version")));
    		insertValues.put("shopping_list_ingredient_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_ingredient_UUID")));

    		long targetID = targetDb.insert("shopping_list_ingredient", "_id", insertValues);
    		if (targetID != -1)
    		{
    			shoppingListIngredientCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return shoppingListIngredientCrosswalk;
	}
	private HashMap<Long, Long> SyncRecipeToRecipeCategoryLinks(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> recipeCrosswalk, HashMap<Long, Long> recipeCategoryCrosswalk) {
		Cursor targetCursor;
		Cursor inputCursor;
		
		String whereQuery;
		if (syncContent)
    	{
    		whereQuery = "(recipe_to_recipe_category_link_is_content = 0) OR (recipe_to_recipe_category_link_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(recipe_to_recipe_category_link_is_content = 0)";
    	}
    	
    	try
		{
    		targetCursor = targetDb.query("recipe_to_recipe_category_link", new String[] { "_id", "recipe_to_recipe_category_link_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	try
		{
    		inputCursor = inputDb.query("recipe_to_recipe_category_link", new String[] { "_id", "recipe_to_recipe_category_link_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> recipeToRecipeCategoryLinkCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			recipeToRecipeCategoryLinkCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any links in crosswalk 
    	for (Long currentKey : recipeToRecipeCategoryLinkCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("recipe_to_recipe_category_link", new String[] { "_id", "id_recipe", "id_recipe_category", "recipe_to_recipe_category_link_is_content", "recipe_to_recipe_category_link_content_database_version" }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	//crosswalk recipes and recipe categories
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputRecipeCategoryID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe_category"));
        	long recipeCategoryID = (recipeCategoryCrosswalk.get(inputRecipeCategoryID) == null) ? inputRecipeCategoryID : recipeCategoryCrosswalk.get(inputRecipeCategoryID);

        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_recipe", recipeID);
        		updatedValues.put("id_recipe_category", recipeCategoryID);
    		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_is_content")) == 1)
        		{
        			updatedValues.put("recipe_to_recipe_category_link_is_content", 1);
        			updatedValues.put("recipe_to_recipe_category_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_content_database_version")));
        		}
        	}
        	else
        	{
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_is_content")) == 1)
        		{
        			updatedValues.put("id_recipe", recipeID);
            		updatedValues.put("id_recipe_category", recipeCategoryID);
            		updatedValues.put("recipe_to_recipe_category_link_is_content", 1);
        			updatedValues.put("recipe_to_recipe_category_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_content_database_version")));
                }

        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("recipe_to_recipe_category_link", updatedValues, "_id = "+recipeToRecipeCategoryLinkCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any links left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("recipe_to_recipe_category_link", new String[] { "_id", "id_recipe", "id_recipe_category", "recipe_to_recipe_category_link_is_content", "recipe_to_recipe_category_link_content_database_version", "recipe_to_recipe_category_link_UUID"  }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk recipes and recipe categories
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputRecipeCategoryID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe_category"));
        	long recipeCategoryID = (recipeCategoryCrosswalk.get(inputRecipeCategoryID) == null) ? inputRecipeCategoryID : recipeCategoryCrosswalk.get(inputRecipeCategoryID);

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_recipe", recipeID);
    		insertValues.put("id_recipe_category", recipeCategoryID);
    		insertValues.put("recipe_to_recipe_category_link_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_is_content")));
    		insertValues.put("recipe_to_recipe_category_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_content_database_version")));
    		insertValues.put("recipe_to_recipe_category_link_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_UUID")));
    		
  			long targetID = targetDb.insert("recipe_to_recipe_category_link", "_id", insertValues);
    		if (targetID != -1)
    		{
    			recipeToRecipeCategoryLinkCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	return recipeToRecipeCategoryLinkCrosswalk;

	}
	private HashMap<Long, Long> SyncShoppingLists(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion) {
		
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(shopping_list_is_content = 0) OR (shopping_list_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(shopping_list_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("shopping_list", new String[] { "_id", "shopping_list_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("shopping_list_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("shopping_list", new String[] { "_id", "shopping_list_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> shoppingListCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			shoppingListCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any shopping lists in crosswalk 
    	for (Long currentKey : shoppingListCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("shopping_list", new String[] { "_id", "shopping_list_description", "shopping_list_category", "shopping_list_is_content", "shopping_list_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("shopping_list_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_description")));
        		updatedValues.put("shopping_list_category", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_category")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_is_content")) == 1)
        		{
        			updatedValues.put("shopping_list_is_content", 1);
        			updatedValues.put("shopping_list_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("shopping_list", new String[] { "_id", "shopping_list_category", "shopping_list_is_content" }, "_id = "+shoppingListCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		String category = targetCursor.getString(targetCursor.getColumnIndexOrThrow("shopping_list_category"));
                if (category == null){
                    category = "";
                }
        		if ((category.trim().equals("")) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_is_content")) == 0))
        		{
        			updatedValues.put("shopping_list_category", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_category")));
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_is_content")) == 1)
        		{
        			updatedValues.put("shopping_list_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_description")));
            		updatedValues.put("shopping_list_category", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_category")));
        			updatedValues.put("shopping_list_is_content", 1);
        			updatedValues.put("shopping_list_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("shopping_list", updatedValues, "_id = "+shoppingListCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any shopping lists left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("shopping_list", new String[] { "_id", "shopping_list_description", "shopping_list_category", "shopping_list_is_content", "shopping_list_content_database_version", "shopping_list_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
    		long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("shopping_list_category", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_category")));
    		insertValues.put("shopping_list_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_is_content")));
    		insertValues.put("shopping_list_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("shopping_list_content_database_version")));
    		insertValues.put("shopping_list_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_UUID")));
    		
    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputShoppingListName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("shopping_list_description"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("shopping_list_description", inputShoppingListName+nameSuffix);
    			targetID = targetDb.insert("shopping_list", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			shoppingListCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
		return shoppingListCrosswalk;
	}
	private HashMap<Long, Long> SyncRecipeIngredients(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> recipeCrosswalk, HashMap<Long, Long> ingredientCrosswalk, HashMap<Long, Long> ingredientMeasurementCrosswalk) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(recipe_ingredient_is_content = 0) OR (recipe_ingredient_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(recipe_ingredient_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("recipe_ingredient", new String[] { "_id", "recipe_ingredient_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_ingredient_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("recipe_ingredient", new String[] { "_id", "recipe_ingredient_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_ingredient_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> recipeIngredientCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			recipeIngredientCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any recipe ingredient in crosswalk 
    	for (Long currentKey : recipeIngredientCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("recipe_ingredient", new String[] { "_id", "id_recipe", "id_ingredient", "ingredient_quantity", "id_ingredient_measurement", "recipe_ingredient_is_content", "recipe_ingredient_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    	
        	//crosswalk recipes, ingredients and ingredient measurements
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);
        	
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_recipe", recipeID);
        		updatedValues.put("id_ingredient", ingredientID);
        		updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_is_content")) == 1)
        		{
        			updatedValues.put("recipe_ingredient_is_content", 1);
        			updatedValues.put("recipe_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("recipe_ingredient", new String[] { "_id", "ingredient_quantity", "id_ingredient_measurement" }, "_id = "+recipeIngredientCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getInt(targetCursor.getColumnIndexOrThrow("id_ingredient_measurement")) == 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_is_content")) == 0))
        		{
        			updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		}

        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_is_content")) == 1)
        		{
        			updatedValues.put("id_recipe", recipeID);
            		updatedValues.put("id_ingredient", ingredientID);
            		updatedValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        			updatedValues.put("recipe_ingredient_is_content", 1);
        			updatedValues.put("recipe_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("recipe_ingredient", updatedValues, "_id = "+recipeIngredientCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any recipe ingredients left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("recipe_ingredient", new String[] { "_id", "id_recipe", "id_ingredient", "ingredient_quantity", "id_ingredient_measurement", "recipe_ingredient_is_content", "recipe_ingredient_content_database_version", "recipe_ingredient_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk recipes, ingredients and ingredient measurements
        	long inputRecipeID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_recipe"));
        	long recipeID = (recipeCrosswalk.get(inputRecipeID) == null) ? inputRecipeID : recipeCrosswalk.get(inputRecipeID);
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_recipe", recipeID);
    		insertValues.put("id_ingredient", ingredientID);
    		insertValues.put("ingredient_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_quantity")));
    		insertValues.put("id_ingredient_measurement", ingredientMeasurementID);
    		insertValues.put("recipe_ingredient_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_is_content")));
    		insertValues.put("recipe_ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_ingredient_content_database_version")));
    		insertValues.put("recipe_ingredient_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_ingredient_UUID")));

    		long targetID = targetDb.insert("recipe_ingredient", "_id", insertValues);
    		if (targetID != -1)
    		{
    			recipeIngredientCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return recipeIngredientCrosswalk;
	}
	private HashMap<Long, Long> SyncRecipeCategories(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion) {
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(recipe_category_is_content = 0) OR (recipe_category_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(recipe_category_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("recipe_category", new String[] { "_id", "recipe_category_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_category_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("recipe_category", new String[] { "_id", "recipe_category_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_category_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> recipeCategoryCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			recipeCategoryCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any recipe categories in crosswalk 
    	for (Long currentKey : recipeCategoryCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("recipe_category", new String[] { "_id", "recipe_category_name", "recipe_category_is_content", "recipe_category_content_database_version"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("recipe_category_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_category_name")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_is_content")) == 1)
        		{
        			updatedValues.put("recipe_category_is_content", 1);
        			updatedValues.put("recipe_category_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_content_database_version")));
        		}
        	}
        	else
        	{

        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_is_content")) == 1)
        		{
        			updatedValues.put("recipe_category_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_category_name")));
        			updatedValues.put("recipe_category_is_content", 1);
        			updatedValues.put("recipe_category_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_content_database_version")));
        		}
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("recipe_category", updatedValues, "_id = "+recipeCategoryCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any recipe categories left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("recipe_category", new String[] { "_id", "recipe_category_name", "recipe_category_is_content", "recipe_category_content_database_version", "recipe_category_UUID" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
    		long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("recipe_category_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_is_content")));
    		insertValues.put("recipe_category_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_category_content_database_version")));
    		insertValues.put("recipe_category_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_category_UUID")));
    		
    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputRecipeCategoryName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_category_name"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("recipe_category_name", inputRecipeCategoryName+nameSuffix);
    			targetID = targetDb.insert("recipe_category", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			recipeCategoryCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return recipeCategoryCrosswalk;
	}
	private HashMap<Long, Long> SyncRecipes(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion) {
		
		String whereQuery;
    	if (syncContent)
    	{
    		whereQuery = "(recipe_is_content = 0) OR (recipe_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(recipe_is_content = 0)";
    	}
    	Cursor targetCursor;
    	try
		{
    		targetCursor = targetDb.query("recipe", new String[] { "_id", "recipe_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	Cursor inputCursor;
    	try
		{
    		inputCursor = inputDb.query("recipe", new String[] { "_id", "recipe_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> recipeCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			recipeCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any recipes in crosswalk 
    	for (Long currentKey : recipeCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("recipe", new String[] { "_id", "recipe_name", "recipe_description", "recipe_details", "recipe_is_content", "recipe_content_database_version", "recipe_thumbnail"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("recipe_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_name")));
        		updatedValues.put("recipe_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_description")));
        		updatedValues.put("recipe_details", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_details")));
        		updatedValues.put("recipe_thumbnail", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_thumbnail")));
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")) == 1)
        		{
        			updatedValues.put("recipe_is_content", 1);
        			updatedValues.put("recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("recipe", new String[] { "_id", "recipe_name", "recipe_description", "recipe_details", "recipe_thumbnail" }, "_id = "+recipeCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_description")).trim().equals("")) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")) == 0))
        		{
        			updatedValues.put("recipe_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_description")));
        		}
        		if ((targetCursor.getString(targetCursor.getColumnIndexOrThrow("recipe_details")).trim().equals("")) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")) == 0))
        		{
        			updatedValues.put("recipe_details", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_details")));
        		}
        		if ((targetCursor.getBlob(targetCursor.getColumnIndexOrThrow("recipe_thumbnail")) == null) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")) == 0))
        		{
        			updatedValues.put("recipe_thumbnail", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_thumbnail")));
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")) == 1)
        		{
        			updatedValues.put("recipe_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_name")));
            		updatedValues.put("recipe_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_description")));
            		updatedValues.put("recipe_details", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_details")));
        			updatedValues.put("recipe_is_content", 1);
        			updatedValues.put("recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_content_database_version")));
        			updatedValues.put("recipe_thumbnail", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_thumbnail")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("recipe", updatedValues, "_id = "+recipeCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any recipes left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("recipe", new String[] { "_id", "recipe_name", "recipe_description", "recipe_details", "recipe_is_content", "recipe_content_database_version", "recipe_UUID", "recipe_thumbnail" }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
    		long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("recipe_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_description")));
    		insertValues.put("recipe_details", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_details")));
    		insertValues.put("recipe_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_is_content")));
    		insertValues.put("recipe_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("recipe_content_database_version")));
    		insertValues.put("recipe_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_UUID")));
    		insertValues.put("recipe_thumbnail", inputCursor.getBlob(inputCursor.getColumnIndexOrThrow("recipe_thumbnail")));
    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputRecipeName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("recipe_name"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("recipe_name", inputRecipeName+nameSuffix);
    			targetID = targetDb.insert("recipe", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			recipeCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
		return recipeCrosswalk;
	}
	private HashMap<Long, Long> SyncIngredientToIngredientMeasurementLinks(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> ingredientCrosswalk, HashMap<Long, Long> ingredientMeasurementCrosswalk) {
		Cursor targetCursor;
		Cursor inputCursor;
		
		String whereQuery;
		if (syncContent)
    	{
    		whereQuery = "(ingredient_to_custom_ingredient_measurement_link_is_content = 0) OR (ingredient_to_custom_ingredient_measurement_link_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_to_custom_ingredient_measurement_link_is_content = 0)";
    	}
    	
    	try
		{
    		targetCursor = targetDb.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "_id", "ingredient_to_custom_ingredient_measurement_link_UUID", }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	try
		{
    		inputCursor = inputDb.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "_id", "ingredient_to_custom_ingredient_measurement_link_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientToCustomIngredientMeasurementLinkCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			ingredientToCustomIngredientMeasurementLinkCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any links in crosswalk 
    	for (Long currentKey : ingredientToCustomIngredientMeasurementLinkCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "_id", "id_ingredient", "id_ingredient_measurement", "ingredient_to_custom_ingredient_measurement_link_is_content", "ingredient_to_custom_ingredient_measurement_link_content_database_version" }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);

        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		updatedValues.put("id_ingredient", ingredientID);
    		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_to_custom_ingredient_measurement_link_is_content", 1);
        			updatedValues.put("ingredient_to_custom_ingredient_measurement_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_content_database_version")));
        		}
        	}
        	else
        	{
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_is_content")) == 1)
        		{
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
            		updatedValues.put("id_ingredient", ingredientID);
            		updatedValues.put("ingredient_to_custom_ingredient_measurement_link_is_content", 1);
        			updatedValues.put("ingredient_to_custom_ingredient_measurement_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_content_database_version")));
        		}

        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient_to_custom_ingredient_measurement_link", updatedValues, "_id = "+ingredientToCustomIngredientMeasurementLinkCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any links left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "_id", "id_ingredient", "id_ingredient_measurement", "ingredient_to_custom_ingredient_measurement_link_is_content", "ingredient_to_custom_ingredient_measurement_link_content_database_version", "ingredient_to_custom_ingredient_measurement_link_UUID"  }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_ingredient_measurement", ingredientMeasurementID);
    		insertValues.put("id_ingredient", ingredientID);
    		insertValues.put("ingredient_to_custom_ingredient_measurement_link_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_is_content")));
    		insertValues.put("ingredient_to_custom_ingredient_measurement_link_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_content_database_version")));
    		insertValues.put("ingredient_to_custom_ingredient_measurement_link_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_UUID")));
    		
  			long targetID = targetDb.insert("ingredient_to_custom_ingredient_measurement_link", "_id", insertValues);
    		if (targetID != -1)
    		{
    			ingredientToCustomIngredientMeasurementLinkCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	return ingredientToCustomIngredientMeasurementLinkCrosswalk;
    	
    	
	}
	private HashMap<Long, Long> SyncIngredientPackages(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> ingredientCrosswalk, HashMap<Long, Long> ingredientMeasurementCrosswalk) {
		Cursor targetCursor;
		Cursor inputCursor;
		
		String whereQuery;
		if (syncContent)
    	{
    		whereQuery = "(ingredient_package_is_content = 0) OR (ingredient_package_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_package_is_content = 0)";
    	}
    	
    	try
		{
    		targetCursor = targetDb.query("ingredient_package", new String[] { "_id", "ingredient_package_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_package_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
		
    	try
		{
    		inputCursor = inputDb.query("ingredient_package", new String[] { "_id", "ingredient_package_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
		
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientPackageCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			ingredientPackageCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
		
    	
    	//update any packages in crosswalk 
    	for (Long currentKey : ingredientPackageCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("ingredient_package", new String[] { "_id", "id_ingredient", "ingredient_package_description", "ingredient_package_quantity", "id_ingredient_measurement", "ingredient_package_is_content", "ingredient_package_content_database_version" }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);

        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		updatedValues.put("id_ingredient", ingredientID);
        		updatedValues.put("ingredient_package_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_description")));
        		updatedValues.put("ingredient_package_quantity", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_quantity")));
        		
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_package_is_content", 1);
        			updatedValues.put("ingredient_package_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("ingredient_package", new String[] { "_id", "ingredient_package_quantity", "id_ingredient_measurement", "ingredient_package_is_content" }, "_id = "+ingredientPackageCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getInt(targetCursor.getColumnIndexOrThrow("id_ingredient_measurement")) == 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_is_content")) == 0))
        		{
        			updatedValues.put("ingredient_package_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_package_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		}
        		else if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_is_content")) == 1)
        		{
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
            		updatedValues.put("id_ingredient", ingredientID);
            		updatedValues.put("ingredient_package_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_description")));
            		updatedValues.put("ingredient_package_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_package_quantity")));
            		updatedValues.put("ingredient_package_is_content", 1);
        			updatedValues.put("ingredient_package_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient_package", updatedValues, "_id = "+ingredientPackageCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any packages left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient_package", new String[] { "_id", "id_ingredient", "ingredient_package_description", "ingredient_package_quantity", "id_ingredient_measurement", "ingredient_package_is_content", "ingredient_package_content_database_version", "ingredient_package_UUID"  }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient"));
        	long ingredientID = (ingredientCrosswalk.get(inputIngredientID) == null) ? inputIngredientID : ingredientCrosswalk.get(inputIngredientID);
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_ingredient_measurement", ingredientMeasurementID);
    		insertValues.put("id_ingredient", ingredientID);
    		insertValues.put("ingredient_package_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_description")));
    		insertValues.put("ingredient_package_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_package_quantity")));
    		insertValues.put("ingredient_package_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_is_content")));
    		insertValues.put("ingredient_package_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_package_content_database_version")));
    		insertValues.put("ingredient_package_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_package_UUID")));
    		
  			long targetID = targetDb.insert("ingredient_package", "_id", insertValues);
    		if (targetID != -1)
    		{
    			ingredientPackageCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
    	return ingredientPackageCrosswalk;
	}
	private HashMap<Long, Long> SyncIngredientMeasurementConversions(SQLiteDatabase inputDb, SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> ingredientCrosswalk, HashMap<Long, Long> ingredientMeasurementCrosswalk) 
	{
		Cursor targetCursor;
		Cursor inputCursor;
		
		String whereQuery;
		if (syncContent)
    	{
    		whereQuery = "(ingredient_measurement_conversion_is_content = 0) OR (ingredient_measurement_conversion_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_measurement_conversion_is_content = 0)";
    	}
    	
    	try
		{
    		targetCursor = targetDb.query("ingredient_measurement_conversion", new String[] { "_id", "ingredient_measurement_conversion_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
		
    	try
		{
    		inputCursor = inputDb.query("ingredient_measurement_conversion", new String[] { "_id", "ingredient_measurement_conversion_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
		
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientMeasurementConversionCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			ingredientMeasurementConversionCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any ingredient measurement conversions in crosswalk 
    	for (Long currentKey : ingredientMeasurementConversionCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("ingredient_measurement_conversion", new String[] { "_id", "id_ingredient_measurement_from", "id_ingredient_from", "id_ingredient_measurement_to", "id_ingredient_to", "ingredient_measurement_conversion_multiplier", "ingredient_measurement_conversion_is_content", "ingredient_measurement_conversion_content_database_version" }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientFromID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_from"));
        	long ingredientFromID = (ingredientCrosswalk.get(inputIngredientFromID) == null) ? inputIngredientFromID : ingredientCrosswalk.get(inputIngredientFromID);
        	long inputIngredientToID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_to"));
        	long ingredientToID = (ingredientCrosswalk.get(inputIngredientToID) == null) ? inputIngredientToID : ingredientCrosswalk.get(inputIngredientToID);
        	long inputIngredientMeasurementToID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement_to"));
        	long ingredientMeasurementToID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementToID) == null) ? inputIngredientMeasurementToID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementToID);
        	long inputIngredientMeasurementFromID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement_from"));
        	long ingredientMeasurementFromID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementFromID) == null) ? inputIngredientMeasurementFromID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementFromID);

        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("id_ingredient_measurement_from", ingredientMeasurementFromID);
        		updatedValues.put("id_ingredient_from", ingredientFromID);
        		updatedValues.put("id_ingredient_measurement_to", ingredientMeasurementToID);
        		updatedValues.put("id_ingredient_to", ingredientToID);
        		updatedValues.put("ingredient_measurement_conversion_multiplier", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_multiplier")));
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_measurement_conversion_is_content", 1);
        			updatedValues.put("ingredient_measurement_conversion_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_content_database_version")));
        		}
        	}
        	else
        	{
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_is_content")) == 1)
        		{
        			updatedValues.put("id_ingredient_measurement_from", ingredientMeasurementFromID);
            		updatedValues.put("id_ingredient_from", ingredientFromID);
            		updatedValues.put("id_ingredient_measurement_to", ingredientMeasurementToID);
            		updatedValues.put("id_ingredient_to", ingredientToID);
            		updatedValues.put("ingredient_measurement_conversion_multiplier", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_multiplier")));
            		updatedValues.put("ingredient_measurement_conversion_is_content", 1);
        			updatedValues.put("ingredient_measurement_conversion_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_content_database_version")));
        		}
        		//targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient_measurement_conversion", updatedValues, "_id = "+ingredientMeasurementConversionCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	//insert any ingredient measurement conversions left in the inputMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient_measurement_conversion", new String[] { "_id", "id_ingredient_measurement_from", "id_ingredient_from", "id_ingredient_measurement_to", "id_ingredient_to", "ingredient_measurement_conversion_multiplier", "ingredient_measurement_conversion_is_content", "ingredient_measurement_conversion_content_database_version", "ingredient_measurement_conversion_UUID"  }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	//crosswalk ingredients and ingredient measurements
        	long inputIngredientFromID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_from"));
        	long ingredientFromID = (ingredientCrosswalk.get(inputIngredientFromID) == null) ? inputIngredientFromID : ingredientCrosswalk.get(inputIngredientFromID);
        	long inputIngredientToID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_to"));
        	long ingredientToID = (ingredientCrosswalk.get(inputIngredientToID) == null) ? inputIngredientToID : ingredientCrosswalk.get(inputIngredientToID);
        	long inputIngredientMeasurementToID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement_to"));
        	long ingredientMeasurementToID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementToID) == null) ? inputIngredientMeasurementToID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementToID);
        	long inputIngredientMeasurementFromID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement_from"));
        	long ingredientMeasurementFromID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementFromID) == null) ? inputIngredientMeasurementFromID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementFromID);

    		ContentValues insertValues = new ContentValues();
    		insertValues.put("id_ingredient_measurement_from", ingredientMeasurementFromID);
    		insertValues.put("id_ingredient_from", ingredientFromID);
    		insertValues.put("id_ingredient_measurement_to", ingredientMeasurementToID);
    		insertValues.put("id_ingredient_to", ingredientToID);
    		insertValues.put("ingredient_measurement_conversion_multiplier", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_multiplier")));
    		insertValues.put("ingredient_measurement_conversion_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_is_content")));
    		insertValues.put("ingredient_measurement_conversion_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_content_database_version")));

    		insertValues.put("ingredient_measurement_conversion_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")));
    		
  			long targetID = targetDb.insert("ingredient_measurement_conversion", "_id", insertValues);
    		if (targetID != -1)
    		{
    			ingredientMeasurementConversionCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
    	
		return ingredientMeasurementConversionCrosswalk;

	}
	private HashMap<Long, Long> SyncIngredientMeasurements(SQLiteDatabase inputDb, SQLiteDatabase targetDb,	boolean syncContent, boolean inputWins,	int startDbVersion) {
		Cursor targetCursor;
		Cursor inputCursor;
	
		String whereQuery;
		if (syncContent)
    	{
    		whereQuery = "(ingredient_measurement_is_content = 0) OR (ingredient_measurement_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_measurement_is_content = 0)";
    	}
    	
    	try
		{
    		targetCursor = targetDb.query("ingredient_measurement", new String[] { "_id", "ingredient_measurement_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
		HashMap<String, Long> targetMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_measurement_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
		
    	try
		{
    		inputCursor = inputDb.query("ingredient_measurement", new String[] { "_id", "ingredient_measurement_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
		
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientMeasurementCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetMap.containsKey(currentKey))
    		{
    			ingredientMeasurementCrosswalk.put(inputMap.get(currentKey), targetMap.get(currentKey));
    			inputMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;

    	//update any ingredient measurements in crosswalk 
    	for (Long currentKey : ingredientMeasurementCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("ingredient_measurement", new String[] { "_id", "ingredient_measurement_description", "ingredient_measurement_abbreviation", "ingredient_measurement_group", "ingredient_measurement_is_content", "ingredient_measurement_content_database_version" }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	
    		
        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("ingredient_measurement_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_description")));
        		updatedValues.put("ingredient_measurement_abbreviation", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_abbreviation")));
        		updatedValues.put("ingredient_measurement_group", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_group")));
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_measurement_is_content", 1);
        			updatedValues.put("ingredient_measurement_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("ingredient_measurement", new String[] { "_id", "ingredient_measurement_group" }, "_id = "+ingredientMeasurementCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		if ((inputCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_measurement_group")).equals("")) &&(inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_is_content")) == 0))
        		{
        			updatedValues.put("ingredient_measurement_group", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_group")));
        		}
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_measurement_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_description")));
            		updatedValues.put("ingredient_measurement_abbreviation", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_abbreviation")));
            		updatedValues.put("ingredient_measurement_group", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_group")));
        			updatedValues.put("ingredient_measurement_is_content", 1);
        			updatedValues.put("ingredient_measurement_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_content_database_version")));
        		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient_measurement", updatedValues, "_id = "+ingredientMeasurementCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
		
    	//insert any ingredient measurements left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient_measurement", new String[] { "_id", "ingredient_measurement_description", "ingredient_measurement_abbreviation", "ingredient_measurement_group", "ingredient_measurement_is_content", "ingredient_measurement_content_database_version", "ingredient_measurement_UUID"  }, "_id = "+inputMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
    		
    		ContentValues insertValues = new ContentValues();
    		insertValues.put("ingredient_measurement_description", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_description")));
    		insertValues.put("ingredient_measurement_abbreviation", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_abbreviation")));
    		insertValues.put("ingredient_measurement_group", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_group")));
    		insertValues.put("ingredient_measurement_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_is_content")));
    		insertValues.put("ingredient_measurement_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_measurement_content_database_version")));
    		insertValues.put("ingredient_measurement_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_UUID")));
    		
    		//if the description or abbreviation turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end of the description and just a number to the end of the abbreviation.  This will try numbers up to 99 before giving up with this record.
    		long targetID = -1;
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputIngredientMeasurementDescription = inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_description"));
    		String inputIngredientMeasurementAbbreviation = inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_measurement_abbreviation"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("ingredient_measurement_description", inputIngredientMeasurementDescription+nameSuffix);
        		insertValues.put("ingredient_measurement_abbreviation", inputIngredientMeasurementAbbreviation + nameSuffix.replace("(", "").replace(")",""));
    			targetID = targetDb.insert("ingredient_measurement", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			ingredientMeasurementCrosswalk.put(inputMap.get(newUUID), targetID);
    		}
    		
    		inputCursor.close();
    	}
    	
		return ingredientMeasurementCrosswalk;
	}
	private HashMap<Long, Long> SyncIngredients(SQLiteDatabase inputDb,	SQLiteDatabase targetDb, boolean syncContent, boolean inputWins, int startDbVersion, HashMap<Long, Long> ingredientMeasurementCrosswalk, HashMap<Long, Long> ingredientGroupCrosswalk, HashMap<Long, Long> sourceCrosswalk) {
		Cursor targetCursor;
		Cursor inputCursor;
		String whereQuery;
    	
    	
    	try
		{
    		targetCursor = targetDb.query("ingredient", new String[] { "_id", "ingredient_UUID", }, null, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
		targetCursor.moveToFirst();
    	HashMap<String, Long> targetIngredientMap = new HashMap<>();
    	while (!targetCursor.isAfterLast())
    	{
    		targetIngredientMap.put(targetCursor.getString(targetCursor.getColumnIndexOrThrow("ingredient_UUID")), targetCursor.getLong(targetCursor.getColumnIndexOrThrow("_id")));
    		targetCursor.moveToNext();
    	}
    	targetCursor.close();
    	
    	if (syncContent)
    	{
    		whereQuery = "(ingredient_is_content = 0) OR (ingredient_content_database_version >= "+startDbVersion+")";
    	}
    	else
    	{
    		whereQuery = "(ingredient_is_content = 0)";
    	}
    	try
		{
    		inputCursor = inputDb.query("ingredient", new String[] { "_id", "ingredient_UUID" }, whereQuery, null, null, null, "_id");
		}
		catch (SQLException e)
		{
			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
		}
    	inputCursor.moveToFirst();
    	HashMap<String, Long> inputIngredientMap = new HashMap<>();
    	while (!inputCursor.isAfterLast())
    	{
    		inputIngredientMap.put(inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_UUID")), inputCursor.getLong(inputCursor.getColumnIndexOrThrow("_id")));
    		inputCursor.moveToNext();
    	}
    	inputCursor.close();
    	
    	 
    	//make a crosswalk of IDs from above maps for duplicates and remove them from the inputIngredientMap
    	//use a copy Set of keys so that iteration can happen even as items are removed from the Map
    	HashMap<Long, Long> ingredientCrosswalk = new HashMap<>();
    	HashSet<String> tempSet = new HashSet<>();
    	tempSet.addAll(inputIngredientMap.keySet());
    	for(String currentKey : tempSet)
    	{
    		if (targetIngredientMap.containsKey(currentKey))
    		{
    			ingredientCrosswalk.put(inputIngredientMap.get(currentKey), targetIngredientMap.get(currentKey));
    			inputIngredientMap.remove(currentKey);
    		}
    	}
    	//done with temp copy so set to null to allow garbage collection
        //noinspection UnusedAssignment
        tempSet = null;
    	
    	//update any ingredients in crosswalk 
    	for (Long currentKey : ingredientCrosswalk.keySet())
    	{
    		
    		try
    		{
        		inputCursor = inputDb.query("ingredient", new String[] { "_id", "ingredient_name", "ingredient_default_quantity", "id_ingredient_measurement", "id_ingredient_group", "ingredient_default_to_shopping_list", "ingredient_is_content", "ingredient_content_database_version", "id_ingredient_group", "id_source", "ingredient_source_id"  }, "_id = "+currentKey, null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);
        	long inputIngredientGroupID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_group"));
        	long ingredientGroupID = (ingredientGroupCrosswalk.get(inputIngredientGroupID) == null) ? inputIngredientGroupID : ingredientGroupCrosswalk.get(inputIngredientGroupID);
        	long inputSourceID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_source"));
        	long sourceID = (sourceCrosswalk.get(inputSourceID) == null) ? inputSourceID : sourceCrosswalk.get(inputSourceID);

        	ContentValues updatedValues = new ContentValues();
        	if (inputWins)
        	{
        		updatedValues.put("ingredient_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_name")));
        		updatedValues.put("ingredient_default_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_default_quantity")));
        		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		updatedValues.put("id_ingredient_group", ingredientGroupID);
        		updatedValues.put("ingredient_default_to_shopping_list", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_default_to_shopping_list")));
        		updatedValues.put("id_source", sourceID);
        		updatedValues.put("ingredient_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_source_id")));
        		
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_is_content", 1);
        			updatedValues.put("ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_content_database_version")));
        		}
        	}
        	else
        	{
        		try
        		{
            		targetCursor = targetDb.query("ingredient", new String[] { "_id", "ingredient_default_quantity", "id_ingredient_measurement", "id_ingredient_group", "ingredient_default_to_shopping_list", "ingredient_is_content" }, "_id = "+ingredientCrosswalk.get(currentKey), null, null, null, "_id");
        		}
        		catch (SQLException e)
        		{
        			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
        		}
        		targetCursor.moveToFirst();
        		
        		if ((targetCursor.getInt(targetCursor.getColumnIndexOrThrow("id_ingredient_measurement")) == 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_is_content")) == 0))
        		{
        			updatedValues.put("ingredient_default_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_default_quantity")));
        			updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
        		}
        		if ((targetCursor.getInt(targetCursor.getColumnIndexOrThrow("id_ingredient_group")) == 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("id_ingredient_group")) != 0) && (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_is_content")) == 0))
        		{
        			updatedValues.put("id_ingredient_group", ingredientGroupID);
        		}
        		if (inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_is_content")) == 1)
        		{
        			updatedValues.put("ingredient_name", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_name")));
            		updatedValues.put("ingredient_default_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_default_quantity")));
            		updatedValues.put("id_ingredient_measurement", ingredientMeasurementID);
            		updatedValues.put("id_ingredient_group", ingredientGroupID);
            		updatedValues.put("ingredient_default_to_shopping_list", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_default_to_shopping_list")));
        			updatedValues.put("ingredient_is_content", 1);
        			updatedValues.put("ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_content_database_version")));
            		updatedValues.put("id_source", sourceID);
            		updatedValues.put("ingredient_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_source_id")));
             		}
        		targetCursor.close();
        	}
        	if (updatedValues.size() > 0)
        	{
        		targetDb.update("ingredient", updatedValues, "_id = "+ingredientCrosswalk.get(currentKey), null);
        	}
        	inputCursor.close();
    	}
    	
    	//insert any ingredients left in the inputIngredientMap and add the new _id to the crosswalk
    	for (String newUUID : inputIngredientMap.keySet())
    	{
    		try
    		{
        		inputCursor = inputDb.query("ingredient", new String[] { "_id", "ingredient_name", "ingredient_default_quantity", "id_ingredient_measurement", "id_ingredient_group", "ingredient_default_to_shopping_list", "ingredient_is_content", "ingredient_content_database_version", "ingredient_UUID", "id_source", "ingredient_source_id"  }, "_id = "+inputIngredientMap.get(newUUID), null, null, null, "_id");
    		}
    		catch (SQLException e)
    		{
    			throw new Error(myContext.getString(R.string.common_database_sync_error_label) +e.getMessage());
    		}
        	inputCursor.moveToFirst();
        	long inputIngredientMeasurementID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_measurement"));
        	long ingredientMeasurementID = (ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID) == null) ? inputIngredientMeasurementID : ingredientMeasurementCrosswalk.get(inputIngredientMeasurementID);
        	long inputIngredientGroupID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_ingredient_group"));
        	long ingredientGroupID = (ingredientGroupCrosswalk.get(inputIngredientGroupID) == null) ? inputIngredientGroupID : ingredientGroupCrosswalk.get(inputIngredientGroupID);
        	long inputSourceID = inputCursor.getLong(inputCursor.getColumnIndexOrThrow("id_source"));
        	long sourceID = (sourceCrosswalk.get(inputSourceID) == null) ? inputSourceID : sourceCrosswalk.get(inputSourceID);
        	
        	long targetID = -1;
    		ContentValues insertValues = new ContentValues();

    		insertValues.put("ingredient_default_quantity", inputCursor.getDouble(inputCursor.getColumnIndexOrThrow("ingredient_default_quantity")));
    		insertValues.put("id_ingredient_measurement", ingredientMeasurementID);
    		insertValues.put("id_ingredient_group", ingredientGroupID);  		
    		insertValues.put("ingredient_default_to_shopping_list", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_default_to_shopping_list")));
    		insertValues.put("ingredient_is_content", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_is_content")));
    		insertValues.put("ingredient_content_database_version", inputCursor.getInt(inputCursor.getColumnIndexOrThrow("ingredient_content_database_version")));
    		insertValues.put("ingredient_UUID", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_UUID")));
    		insertValues.put("id_source", sourceID);
    		insertValues.put("ingredient_source_id", inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_source_id")));

    		//if the name turns out to be a duplicate even though the UUID was different, try adding a number in parentheses to the end.  This will try numbers up to 99 before giving up with this record.
    		String nameSuffix = "";
    		int nameSuffixInt = 0;
    		String inputIngredientName = inputCursor.getString(inputCursor.getColumnIndexOrThrow("ingredient_name"));
    		while ((targetID == -1) && (nameSuffixInt < 99))
    		{
    			insertValues.put("ingredient_name", inputIngredientName+nameSuffix);
    			targetID = targetDb.insert("ingredient", "_id", insertValues);
    			nameSuffixInt++;
    			nameSuffix = " ("+nameSuffixInt+")";
    		}
    		if (targetID != -1)
    		{
    			ingredientCrosswalk.put(inputIngredientMap.get(newUUID), targetID);
    		}
    		inputCursor.close();
    	}
		return ingredientCrosswalk;
	}
	
	
	public void close() 
    {
    	if (this.isOpen())
    		primaryDataBase.close();
    }
	public class DataBaseHelper extends SQLiteOpenHelper
	{
		private boolean newDataBaseFlag;
		private String databaseUUID;
		private boolean synced;
		DataBaseHelper(Context context, String dbName)
		{
			super(context, dbName, null, DB_VERSION);
			if (dbName.equals(getPrimaryDbFileName()))
			{
				throw new Error("Database overwrite attempt!");
			}
		}
		
		public boolean hasBeenSynced(){
			return synced;
		}
		
		public String getUUID(){
			return databaseUUID;
		}

        DataBaseHelper(Context context) {
            super(context, getPrimaryDbFileName(), null, DB_VERSION);
        }
		@Override
		public void onCreate(SQLiteDatabase db) {
			newDataBaseFlag = true;
		
		}
		
		@Override
		public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);

			int dbVersion = 0;
			if (!newDataBaseFlag) {
				Cursor readCursor;
				try
	    		{
	    			readCursor = db.query("versioning", new String[] { "version_number" }, "version_type = 'DBVersion'", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error checking database version number: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		
				if (!readCursor.isAfterLast()) {
					dbVersion = readCursor.getInt(readCursor.getColumnIndexOrThrow("version_number"));
				}
				readCursor.close();
			}
			if ((!newDataBaseFlag) && (dbVersion > 14)){
				databaseUUID = GetDatabaseUUID(db);
				synced = CheckSynced(db);
			}
		}
		private boolean CheckSynced(SQLiteDatabase db){
			Cursor readCursor;
			try{
				readCursor = db.query("sync_database", new String[] {"sync_database_UUID"}, "_id != 0", null, null, null, null);
			}
			catch (Exception e){
				throw new Error("Error getting sync data: " +e.getMessage());
			}
			if (readCursor.moveToFirst()){
				
				readCursor.close();
				return true;
			}
			else
			{
				readCursor.close();
				return false;
			}
		}

		private String GetDatabaseUUID(SQLiteDatabase db){
			
			Cursor readCursor;
			try{
				readCursor = db.query("sync_database", new String[] {"sync_database_UUID"}, "_id = 0", null, null, null, null);
			}
			catch (Exception e){
				throw new Error("Error getting unique database ID: " +e.getMessage());
			}
			if (readCursor.moveToFirst()){
				String currentUUID = readCursor.getString(readCursor.getColumnIndexOrThrow("sync_database_UUID"));
				readCursor.close();
				return currentUUID;
			}
			else
			{
				readCursor.close();
				return null;
			}
	    }
		private void InsertUUID(SQLiteDatabase db){

			UUID newUUID = UUID.randomUUID();
			ContentValues insertValues = new ContentValues();
			insertValues.put("_id", 0);
			insertValues.put("sync_database_UUID", newUUID.toString());
			db.insert("sync_database", null, insertValues);

		}
		
		
		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
    		try
			{
				Toast toast = Toast.makeText(myContext, "Existing database is not compatible with this version.  Please upgrade this app to the latest version before continuing." , Toast.LENGTH_LONG);
				toast.show();
			}
			catch (Exception ignored){}
			throw new Error("Error upgrading database: Original database version is newer than this software.  Please upgrade the app to the latest version to read this database.");
		}
		
		@SuppressWarnings("ConstantConditions")
        @Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
			//if we are just opening a new database for the first time to set the version then no need to upgrade
			if (newDataBaseFlag)
			{
				return;
			}
			
			if (db.isReadOnly())
			{
				throw new Error("Attempting to upgrade read-only database!");
			}
			
			
			int previousVersion;
			/*
			try
			{
				Toast toast = Toast.makeText(myContext, "Upgrading Database" , Toast.LENGTH_SHORT);
				toast.show();
			}
			catch (Exception e){}
			*/
			
			//double check version number from database due to bugs in Galaxy Tab et. al. not setting version number
			Cursor versionCursor;
			try
    		{
				versionCursor = db.query("versioning", new String[] { "version_number" }, "version_type = 'DBVersion'", null, null, null, "_id");
				versionCursor.moveToFirst();
	    		if (versionCursor.getCount() == 0)
	    		{
	    			ContentValues insertValues = new ContentValues();
		    		insertValues.put("version_type", "DBVersion");
		    		insertValues.put("version_number", DB_VERSION);
		    		db.insert("versioning", null, insertValues);
	    			throw new Error("Error selecting version: versioning table returned 0 rows");
	    		}
	    		else
	    		{
	    			previousVersion = versionCursor.getInt(versionCursor.getColumnIndexOrThrow("version_number"));
	    		}
                versionCursor.close();
    		}
    		catch (Exception e)
    		{
    			if (e.getMessage().contains("no such table"))
    			{
    				try
    	    		{
    	    			db.execSQL("CREATE TABLE versioning ( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, version_type TEXT NOT NULL UNIQUE DEFAULT '', version_number INTEGER NOT NULL DEFAULT 0)");
    	    		}
    	    		catch (Exception ex)
    	    		{
    	    			throw new Error("Error inserting version table: " +ex.getMessage());
    	    		}
    	    		ContentValues insertValues = new ContentValues();
    	    		insertValues.put("version_type", "DBVersion");
    	    		insertValues.put("version_number", DB_VERSION);
    	    		db.insert("versioning", null, insertValues);
    	    		previousVersion = 10;
    			}
    			else
    			{
    				throw new Error("Error selecting version: " +e.getMessage());
    			}
    		}
            //noinspection UnusedAssignment
            versionCursor = null;
			
			//go through each version one at a time
			if (previousVersion < 1)
	    	{
				throw new Error("Error upgrading database: Original database version is invalid.");
	    	}

	    	if (previousVersion > DB_VERSION)
	    	{
	    		try
				{
					Toast toast = Toast.makeText(myContext, "Existing database is not compatible with this version.  Please upgrade this app to the latest version before continuing." , Toast.LENGTH_LONG);
					toast.show();
				}
				catch (Exception ignored){}
				throw new Error("Error upgrading database: Original database version is newer than this software.  Please upgrade the app to the latest version to read this database.");
	    	}
			
	    	if (previousVersion < 11)
	    	{
	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_measurement_conversion");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		
	    		try
	    		{
	    			db.execSQL("CREATE TABLE ingredient_measurement_temp ( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ingredient_measurement_description TEXT NOT NULL UNIQUE DEFAULT '', ingredient_measurement_abbreviation TEXT NOT NULL UNIQUE DEFAULT '', ingredient_measurement_group TEXT NOT NULL DEFAULT '', ingredient_measurement_is_content INTEGER NOT NULL DEFAULT 0, ingredient_measurement_content_database_version INTEGER NOT NULL DEFAULT -1, ingredient_measurement_UUID TEXT UNIQUE NOT NULL DEFAULT '' )");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		
	    		ContentValues updatedValues = new ContentValues();
	    		updatedValues.put("_id", 0);
	    		updatedValues.put("ingredient_measurement_description", "");
	    		updatedValues.put("ingredient_measurement_abbreviation", "");
	    		updatedValues.put("ingredient_measurement_group", "");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "90551e48-7693-4403-b0d3-d3cc4571e1f6");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		Cursor readCursor;
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'pinch(es)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "pinch(es)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "pinch");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "30dc7483-51e5-4a9c-b3ab-1ff51461d3b0");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'dash(es)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "dash(es)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "dash");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "ab8093fb-5415-4683-a69b-2b2d56bd9784");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'teaspoon(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "teaspoon(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "tsp");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "cdff6bec-0296-4a48-afbe-5fd69202835f");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'tablespoon(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "tablespoon(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "Tbsp");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "1c267f81-9a8b-4e0d-9c5b-308a10ee9144");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'fluid ounce(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "fluid ounce(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "fl oz");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "674fc233-a3a4-4bee-bead-b5a0cd0ad8dd");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'cup(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "cup(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "cup");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "aa13a09d-8575-4cfc-81a5-f0ae4491a2be");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'pint(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "pint(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "pt");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "79230512-a1d4-4d3e-80fd-61eb5576ba60");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'quart(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "quart(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "qt");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "81d44f58-bf9e-4188-a4f5-37851c019c41");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'gallon(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "gallon(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "gal");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "a769d3a9-49be-4392-990d-067c9c7c8343");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'liter(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "liter(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "L");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "f9c70ef2-4165-48b9-8177-ac683e3b524f");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'milliliter(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "milliliter(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "mL");
	    		updatedValues.put("ingredient_measurement_group", "volume");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "804af3af-9d8c-4836-a66d-44934d117304");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'ounce(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "ounce(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "oz");
	    		updatedValues.put("ingredient_measurement_group", "weight");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "4c8fad9c-a3a4-4722-bf5c-8d0caf5c698c");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'pound(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "pound(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "lb");
	    		updatedValues.put("ingredient_measurement_group", "weight");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "4bbc5b9e-481d-4ebd-a470-4b9b7e8fa981");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'gram(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "gram(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "g");
	    		updatedValues.put("ingredient_measurement_group", "weight");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "7021dd37-dcc9-447a-8f38-ce522221af4f");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'kilogram(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "kilogram(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "kg");
	    		updatedValues.put("ingredient_measurement_group", "weight");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "1bc1df04-9922-4c69-abf6-5de08729cc6a");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'count(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "count(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "ct");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "4c172e1f-1a88-4257-85c7-20c28a1f7c52");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'slice(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "slice(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "slice");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "7cfb6421-3e82-4046-a84c-9fb7b0de2e14");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'package(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "package(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "pkg");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "c1d2bf1b-6c3f-4c26-b92c-a835fd36b992");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'stick(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "stick(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "stick");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "fa2e7512-6a2f-432b-8023-3d0b9a6644f9");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'clove(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "clove(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "clove");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 1);
	    		updatedValues.put("ingredient_measurement_UUID", "d1bc3093-1671-435d-8f40-6ddad6fe4ad0");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'cube(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "cube(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "cube");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 8);
	    		updatedValues.put("ingredient_measurement_UUID", "3dd387bc-5c7e-4fce-a4a5-ee8f93de81ae");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient_measurement", new String[] { "_id" }, "ingredient_measurement_description = 'bulb(s)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		updatedValues = new ContentValues();
	    		updatedValues.put("_id", readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
	    		updatedValues.put("ingredient_measurement_description", "bulb(s)");
	    		updatedValues.put("ingredient_measurement_abbreviation", "bulb");
	    		updatedValues.put("ingredient_measurement_group", "custom");
	    		updatedValues.put("ingredient_measurement_is_content", 1);
	    		updatedValues.put("ingredient_measurement_content_database_version", 8);
	    		updatedValues.put("ingredient_measurement_UUID", "d0f8f2e1-8740-4b79-86cf-c0dcde2f4261");
	    		db.insert("ingredient_measurement_temp", "_id", updatedValues);
	    		
	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_measurement");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_package_size");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_package");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("CREATE TABLE ingredient_measurement ( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ingredient_measurement_description TEXT NOT NULL UNIQUE DEFAULT '', ingredient_measurement_abbreviation TEXT NOT NULL UNIQUE DEFAULT '', ingredient_measurement_group TEXT NOT NULL DEFAULT '', ingredient_measurement_is_content INTEGER NOT NULL DEFAULT 0, ingredient_measurement_content_database_version INTEGER NOT NULL DEFAULT -1, ingredient_measurement_UUID TEXT NOT NULL DEFAULT '' )");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		
	    		try
	    		{
	    			db.execSQL("INSERT INTO ingredient_measurement SELECT * FROM ingredient_measurement_temp");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}

	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_measurement_temp");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("CREATE TABLE ingredient_measurement_conversion( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, id_ingredient_measurement_from INTEGER NOT NULL, id_ingredient_from INTEGER NOT NULL DEFAULT -1, id_ingredient_measurement_to INTEGER NOT NULL, id_ingredient_to INTEGER NOT NULL DEFAULT -1, ingredient_measurement_conversion_multiplier REAL NOT NULL, ingredient_measurement_conversion_is_content INTEGER NOT NULL DEFAULT 0, ingredient_measurement_conversion_content_database_version INTEGER NOT NULL DEFAULT -1, ingredient_measurement_conversion_UUID TEXT NOT NULL DEFAULT '')");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		
	    		try
	    		{
	    			db.execSQL("DROP TABLE ingredient_to_custom_ingredient_measurement_link");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("CREATE TABLE ingredient_to_custom_ingredient_measurement_link ( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, id_ingredient INTEGER NOT NULL, id_ingredient_measurement INTEGER NOT NULL, ingredient_to_custom_ingredient_measurement_link_is_content INTEGER NOT NULL DEFAULT 0, ingredient_to_custom_ingredient_measurement_link_content_database_version INTEGER NOT NULL DEFAULT(-1), ingredient_to_custom_ingredient_measurement_link_UUID TEXT NOT NULL DEFAULT '')");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("CREATE TABLE ingredient_package (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, id_ingredient INTEGER NOT NULL, ingredient_package_description TEXT, ingredient_package_quantity REAL NOT NULL, id_ingredient_measurement INTEGER NOT NULL, ingredient_package_is_content INTEGER NOT NULL DEFAULT(0), ingredient_package_content_database_version INTEGER NOT NULL DEFAULT(-1), ingredient_package_UUID TEXT NOT NULL DEFAULT(''));");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		
	    		
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'milk'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "2a1bf974-43f6-43b3-b602-631f101edb89");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'salt'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long saltID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			saltID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "c8610d8e-05c6-4523-872e-45792dcb9f4e");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'sugar'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long sugarID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			sugarID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "760084a4-dc58-4dfe-b644-2c6f7f0d45ba");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'egg'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "5046dd0e-af0d-4649-a3fa-9c34fb43991e");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'baking powder'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "588bc181-d955-4280-8a4e-fa66400827d6");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'baking soda'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "5595e9f1-0d2c-4d18-9b6f-b5649037c773");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'pepperoni'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long pepperoniID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			pepperoniID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "33391afd-732f-4e9d-96d3-d7c9657b34db");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'water'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long waterID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			waterID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "a129eb25-9c49-4f9a-81ad-ff4c617e4060");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'ground beef'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "9da4994d-816c-46a4-ab59-4ac048b37396");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'beef sirloin'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "23b39655-afc1-4ceb-a926-59374f726ae6");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'basil, dry'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long basilID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			basilID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "002fa5cf-cd13-44c3-a57d-f5aa2b8d9ada");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'oregano, dry'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long oreganoID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			oreganoID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "28e3bdfc-7cc4-475b-acf5-cc24d28105a9");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'tomato sauce'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long tomatoSauceID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			tomatoSauceID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "0b947b3e-e9fe-4aa7-963f-20bc1f342b8a");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'black pepper'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "d2777b3e-95d5-44f8-931a-5fa4960383dd");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'red pepper flakes, dry'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long redPepperFlakesID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			redPepperFlakesID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "1c0b8a6b-4f12-4020-8894-7793244068c8");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'flour'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long flourID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			flourID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "627b3477-c1eb-4c58-8704-d5cb8c09c529");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'dry yeast'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long dryYeastID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			dryYeastID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "c05af595-2005-4917-a648-9e0da2565a32");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'oil'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long oilID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			oilID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "b09aa696-7838-4d06-ae36-af3b31175554");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'red wine'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long redWineID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			redWineID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "3aef4781-4c52-4310-b193-f4ba3579aa2b");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'cloves, whole'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long clovesID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			clovesID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "b9aa1f5a-e15b-4ee7-a907-d889c2831d45");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'cinnamon stick'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long cinnamonID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			cinnamonID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "b48192f4-39d8-46c1-91a7-a1f12dbaa33b");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'allspice, whole'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long allspiceID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			allspiceID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "709699ae-2fc2-49c6-bd7c-f90c8147ab66");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'nutmeg, whole'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long nutmegID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			nutmegID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "f9f24c58-7b76-49d8-9347-6f79e15ef277");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'orange, sliced'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long orangeSlicedID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			orangeSlicedID = readCursor.getLong(readCursor.getColumnIndex("_id"));
	    			updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "4acb1758-b140-410d-ad03-a8c2622245e9");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'orange juice'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long orangeJuiceID = null;
	    		
	    		if (readCursor.getCount() == 1)
	    		{
	    			orangeJuiceID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "32cea10e-a1a6-4bec-b204-817021f22205");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'Kirschwasser'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long kirschwasserID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			kirschwasserID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "e8f3ad5f-0f2c-4d98-9f1a-66e015f4d34b");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'honey'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long honeyID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			honeyID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "55d2ccdd-1706-42cf-9785-0f50218df77c");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'cardamom pods'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long cardamomPodsID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			cardamomPodsID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "0c9e36f9-a2fe-40bf-8522-44e7559e55c5");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'tomato paste'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long tomatoPasteID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			tomatoPasteID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "3b883a82-c236-46e8-91ac-eb7bffc75b62");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'garlic'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long garlicID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			garlicID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "c19c11cd-b587-422e-addd-c20c062b246c");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'olive oil'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long oliveOilID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			oliveOilID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "0d707d94-f2a8-4735-9972-260247442bd3");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'romano cheese'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long romanoCheeseID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			romanoCheeseID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "d61a3cb6-970c-4be0-a3e8-8ee06ebbf50c");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'mozzarella cheese'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long mozzarellaCheeseID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			mozzarellaCheeseID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "da792868-a09f-4aca-80b5-06ca687022ef");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_name = 'butter'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("ingredient_UUID", "94acca20-2e30-4375-9eb7-5c1fa0d8e7ac");
		    		db.update("ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe", new String[] { "_id" }, "recipe_name = 'mulled wine (i)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long mulledWineID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			mulledWineID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_UUID", "d0486709-fa3a-4326-b990-14ae06b830bc");
		    		db.update("recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe", new String[] { "_id" }, "recipe_name = 'pizza dough (i)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long pizzaDoughID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			pizzaDoughID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_UUID", "eaa13bb4-dc89-495c-9556-42a37b2950e2");
		    		db.update("recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe", new String[] { "_id" }, "recipe_name = 'pizza sauce (i)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long pizzaSauceID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			pizzaSauceID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_UUID", "296f4f82-7bcb-4835-a53f-db2280430611");
		    		db.update("recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe", new String[] { "_id" }, "recipe_name = 'pizza (i)'", null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		Long pizzaID = null;
	    		if (readCursor.getCount() == 1)
	    		{
	    			pizzaID = readCursor.getLong(readCursor.getColumnIndex("_id"));
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_UUID", "cb821d60-8fc5-4f23-b10b-44c70e24276b");
		    		db.update("recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ redWineID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "2d2fea90-7547-43b4-93a0-00414c49f26b");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ clovesID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "93f6854f-1675-405d-8603-79c5aa6e2902");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ cinnamonID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "68e7b19f-aee0-43f5-9935-760bc04d2caa");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ allspiceID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "824329e9-ac0a-4aef-a025-db431d9fcb44");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ nutmegID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "be743fda-2014-40c9-a839-53804a9b87d1");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ orangeJuiceID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "830610f6-797a-443e-8137-9bafb6b32467");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ kirschwasserID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "ee22a80e-4054-4e4f-8fb9-1ab4bc150311");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ honeyID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "ff37483e-441a-4d8b-9fb7-ed46678fcc3a");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ cardamomPodsID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "068d9b18-5608-48e0-bdd4-f791c3241e7c");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ mulledWineID +" AND id_ingredient = "+ orangeSlicedID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "4eaa561b-255a-4199-b7fc-daf3fb795d12");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ flourID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "5cc8ccac-6794-45c3-bd03-4a7545be2122");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ dryYeastID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "e92114bc-eeec-40bb-8bfd-dcd68b586030");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ saltID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "d7bda717-a497-4e83-8421-0d4a1f094812");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ sugarID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "baef664c-e3aa-4883-9ff9-bfb068d20f36");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ oilID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "f5ec0086-480d-4609-a540-9d89c5307b26");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaDoughID +" AND id_ingredient = "+ waterID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "34350704-f29d-4976-bd4e-3a579aed92a2");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ tomatoSauceID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "99f00e88-f279-437e-91d5-744f6bfcc83e");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ tomatoPasteID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "1f80df5d-c7ff-4535-9cb6-c081266a7466");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ garlicID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "39131303-608e-40d2-8522-d6fcf43d7ec6");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ basilID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "a6c29e11-bb88-483c-b01c-1218383162f2");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ redPepperFlakesID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "77584e41-01f0-41c6-9019-f9f8d47fdf9f");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ oliveOilID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "ffb5087e-4830-4343-84b2-11e61b8e950e");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ saltID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "5922f7d6-645b-4e00-b968-b64db0c74012");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ sugarID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "f13e5a84-c968-43d0-a524-ac254e572be7");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaSauceID +" AND id_ingredient = "+ waterID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "a0b802c6-c88c-4bf7-9c77-b2813bbd3ac1");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ oliveOilID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "fcee4444-67b5-47ae-87fd-bd65ec4f40fe");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ romanoCheeseID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "49f3dcf2-9644-4de2-bfdf-9597ef463a31");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ oreganoID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "2c7437d3-ad51-47cc-8084-8c6718309b0a");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ basilID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "ff6052ff-9b78-4504-91bd-3e00003dda81");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ mozzarellaCheeseID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "920119dd-4a11-4c62-a9a3-73b8db9992ea");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_ingredient = "+ pepperoniID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("recipe_ingredient_UUID", "18e59480-ecc2-4da6-8835-93a5116d09ae");
		    		db.update("recipe_ingredient", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		
	    		try
	    		{
	    			readCursor = db.query("sub_recipe", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_sub_recipe = "+ pizzaDoughID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("sub_recipe_UUID", "86166e5f-7f82-41b0-abc5-b7bfe376c24c");
		    		db.update("sub_recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			readCursor = db.query("sub_recipe", new String[] { "_id" }, "id_recipe = "+ pizzaID +" AND id_sub_recipe = "+ pizzaSauceID , null, null, null, "_id");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		if (readCursor.getCount() == 1)
	    		{
		    		updatedValues = new ContentValues();
		    		updatedValues.put("sub_recipe_UUID", "7f6320a7-c55d-4332-a3f4-8e1290000a66");
		    		db.update("sub_recipe", updatedValues, "_id = " + readCursor.getLong(readCursor.getColumnIndex("_id")), null);
	    		}
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		//add UUIDs to user ingredients
	    		try
	    		{
	    			readCursor = db.query("ingredient", new String[] { "_id" }, "ingredient_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("ingredient_UUID",newUUID.toString());
	    			db.update("ingredient", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		//add UUIDs to user recipes
	    		try
	    		{
	    			readCursor = db.query("recipe", new String[] { "_id" }, "recipe_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("recipe_UUID",newUUID.toString());
	    			db.update("recipe", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		//add UUIDs to user recipe ingredients
	    		try
	    		{
	    			readCursor = db.query("recipe_ingredient", new String[] { "_id" }, "recipe_ingredient_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("recipe_ingredient_UUID",newUUID.toString());
	    			db.update("recipe_ingredient", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		//add UUIDs to user sub-recipes
	    		try
	    		{
	    			readCursor = db.query("sub_recipe", new String[] { "_id" }, "sub_recipe_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("sub_recipe_UUID",newUUID.toString());
	    			db.update("sub_recipe", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		//add UUIDs to user shopping lists
	    		try
	    		{
	    			readCursor = db.query("shopping_list", new String[] { "_id" }, "shopping_list_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("shopping_list_UUID",newUUID.toString());
	    			db.update("shopping_list", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		//add UUIDs to user shopping list ingredients
	    		try
	    		{
	    			readCursor = db.query("shopping_list_ingredient", new String[] { "_id" }, "shopping_list_ingredient_UUID = ''", null, null, null, null);
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.moveToFirst();
	    		while (!readCursor.isAfterLast())
	    		{
	    			UUID newUUID = UUID.randomUUID();
	    			updatedValues = new ContentValues();
	    			updatedValues.put("shopping_list_ingredient_UUID",newUUID.toString());
	    			db.update("shopping_list_ingredient", updatedValues, "_id = "+readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")), null);
	    			readCursor.moveToNext();
	    		}
	    		
	    		
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    		readCursor.close();
		    	//sync all content regardless of version
	    		//boolean returnValue = SyncWithContent(db, true, -1);
	    		
	    		//if (!returnValue)
	    		//{
	    		//	throw new Error("Error upgrading database to version 11:  content data sync failed");
	    		//}

	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
	    		}
	    	}
	    	if (previousVersion < 12){
	    		
	    		//add thumbnail column to recipe
		    	try
	    		{
	    			db.execSQL("ALTER TABLE recipe ADD COLUMN recipe_thumbnail BLOB DEFAULT NULL");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}
		    	//add recipe image table
		    	try
	    		{
	    			db.execSQL("CREATE TABLE recipe_image ( _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, id_recipe INTEGER NOT NULL, recipe_image_name TEXT NOT NULL DEFAULT '', recipe_image_image BLOB DEFAULT NULL, recipe_image_order INTEGER NOT NULL DEFAULT 1, recipe_image_is_content INTEGER NOT NULL DEFAULT 0, recipe_image_content_database_version INTEGER NOT NULL DEFAULT -1, recipe_image_UUID TEXT UNIQUE NOT NULL DEFAULT '')");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}
	    		
	    		//clean up excess rows that were left behind by defect related to entries not being added to the delete table for sync
		    	try
	    		{
		    		db.execSQL("DELETE FROM recipe_ingredient WHERE id_recipe NOT IN (SELECT _id FROM recipe)");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}
		    	try
	    		{
		    		db.execSQL("DELETE FROM shopping_list_ingredient WHERE id_shopping_list NOT IN (SELECT _id FROM shopping_list)");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}
		    	
    	
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}

	    		//boolean returnValue = SyncWithContent(db, false, 12);
	    		//if (!returnValue)
	    		//{
	    		//	throw new Error("Error upgrading database to version 12:  content data sync failed");
	    		//}
	    		
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 12: " +e.getMessage());
	    		}

	    		
			}
	    	if (previousVersion < 13) {
	    		
	    		try
	    		{
	    			db.execSQL("ALTER TABLE shopping_list_ingredient ADD COLUMN shopping_list_ingredient_is_checked INTEGER NOT NULL DEFAULT 0");
	    		}
	    		catch (Exception e)
	    		{
	    			//throw new Error("Error upgrading database to version 13: " +e.getMessage());
	    		}
	    			    		
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 13: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
    			{
    				throw new Error("Error upgrading database to version 13: " +e.getMessage());
    			}

	    	}
	    	
	    	if (previousVersion < 14) {
	    		
	    		try
	    		{
	    			db.execSQL("CREATE TABLE source (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source_name TEXT UNIQUE DEFAULT '' NOT NULL, source_is_content INTEGER DEFAULT 0 NOT NULL, source_content_database_version INTEGER DEFAULT -1 NOT NULL, source_UUID TEXT UNIQUE DEFAULT '' NOT NULL)");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    		
	    		try
	    		{
	    			db.execSQL("CREATE TABLE [ingredient_group] ([_id] INTEGER  PRIMARY KEY AUTOINCREMENT NOT NULL,[ingredient_group_description] TEXT DEFAULT '' NOT NULL,[ingredient_group_is_content] INTEGER DEFAULT 0 NOT NULL,[ingredient_group_content_database_version] INTEGER DEFAULT -1 NOT NULL,[ingredient_group_UUID] TEXT UNIQUE DEFAULT '' NOT NULL,[id_source] INTEGER DEFAULT 0 NOT NULL,[ingredient_group_source_id] TEXT)");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("ALTER TABLE ingredient ADD COLUMN id_ingredient_group INTEGER DEFAULT 0 NOT NULL");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("ALTER TABLE ingredient ADD COLUMN id_source INTEGER DEFAULT 0 NOT NULL");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    		try
	    		{
	    			db.execSQL("ALTER TABLE ingredient ADD COLUMN ingredient_source_id TEXT DEFAULT ''");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    			
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 14: " +e.getMessage());
	    		}
	    		
	    		
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
    			{
    				throw new Error("Error upgrading database to version 14: " +e.getMessage());
    			}
	    		

	    	}
	    	
	    	if (previousVersion < 15) {
	    		
	    		try
	    		{
	    			db.execSQL("CREATE TABLE sync_database (_id INTEGER  PRIMARY KEY AUTOINCREMENT NOT NULL, sync_database_UUID TEXT NOT NULL UNIQUE DEFAULT ( '' ), sync_database_date_time TEXT DEFAULT NULL)");
	    		}
	    		catch (Exception e)
	    		{
	    			throw new Error("Error upgrading database to version 15: " +e.getMessage());
	    		}
	    		InsertUUID(db);
			
	    		try
	    		{
	    			db.execSQL("COMMIT TRANSACTION");
	    		}
	    		catch (SQLException e)
	    		{
	    			throw new Error("Error upgrading database to version 15: " +e.getMessage());
	    		}
	    		
	    		try
	    		{
	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
	    		}
	    		catch (SQLException e)
    			{
    				throw new Error("Error upgrading database to version 15: " +e.getMessage());
    			}
	    		
				databaseUUID = GetDatabaseUUID(db);
				synced = CheckSynced(db);
				
			}
            if (previousVersion < 16) {

                try {
                    db.execSQL("UPDATE recipe_ingredient\n" +
                            "SET id_ingredient_measurement = 0\n" +
                            "WHERE id_recipe = 1 AND id_ingredient_measurement = 16");
                } catch (Exception e) {
                    throw new Error("Error upgrading database to version 16: " + e.getMessage());
                }

                try {
                    db.execSQL("COMMIT TRANSACTION");
                } catch (SQLException e) {
                    throw new Error("Error upgrading database to version 16: " + e.getMessage());
                }

                try {
                    db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
                } catch (SQLException e) {
                    throw new Error("Error upgrading database to version 16: " + e.getMessage());
                }
            }
	    	//continue with if statements upgrading each version one at a time and include sync
	    	//Example:
	    	//	    	if (previousVersion < 11){
	    	//			
	    	//				DO STUFF HERE
	    	//
	    	//
	    	//				try
			//	    		{
			//	    			db.execSQL("COMMIT TRANSACTION");
			//	    		}
			//	    		catch (SQLException e)
			//	    		{
			//	    			throw new Error("Error upgrading database to version 11: " +e.getMessage());
			//	    		}
	    	//	    		boolean returnValue = SyncWithContent(db, false, 11);
	    	//	    		if (!returnValue)
	    	//	    		{
	    	//	    			throw new Error("Error upgrading database to version 11:  content data sync failed");
	    	//	    		}
			//	    		try
			//	    		{
			//	    			db.execSQL("BEGIN EXCLUSIVE TRANSACTION");
			//	    		}
			//	    		catch (SQLException e)
			//    			{
			//    				throw new Error("Error upgrading database to version 11: " +e.getMessage());
			//    			}
	    	//	    	}

                SyncWithContent(db, false, previousVersion);
	    	
	    	
	    	//UPDATE Versioning Table
	    	ContentValues updatedValues = new ContentValues();
    		updatedValues.put("version_number",DB_VERSION);
    		db.update("versioning", updatedValues, "version_type = 'DBVersion'", null);
		}
		
	}
	

}
