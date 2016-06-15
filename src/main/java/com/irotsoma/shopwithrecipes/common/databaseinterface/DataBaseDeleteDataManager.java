package com.irotsoma.shopwithrecipes.common.databaseinterface;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;

//TODO: add bulk delete functions for removing external content as it is currently crazy slow
public final class DataBaseDeleteDataManager {

	public static void RemoveMeasurementFromIngredient(DataBaseAdapter dbAdapter, Long ingredientID, Long measurementID){
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		Cursor readCursor;
		try
		{
			readCursor = dbAdapter.primaryDataBase.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "ingredient_to_custom_ingredient_measurement_link_UUID" }, "id_ingredient_measurement = " + measurementID + " AND id_ingredient = "+ingredientID, null, null, null, null);
		}
		catch (SQLException e)
		{
			throw new Error("Error querying database: "+e.getMessage());
		}
	    readCursor.moveToFirst();
	    if (readCursor.getCount() > 0)
	    {
			ContentValues deletedRowValues = new ContentValues();
		    deletedRowValues.put("deleted_row_table_name", "ingredient_to_custom_ingredient_measurement_link");
		    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_UUID")));
		    deletedRowValues.putNull("deleted_row_synced_date_time");
		    //Date now = new Date();
		    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
		    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
	    }
	    dbAdapter.primaryDataBase.delete("ingredient_to_custom_ingredient_measurement_link", "id_ingredient_measurement = " + measurementID + " AND id_ingredient = "+ingredientID, null);
		readCursor.close();
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient_measurement_conversion", new String[] { "ingredient_measurement_conversion_UUID" }, "id_ingredient_measurement_from = " + measurementID + " AND id_ingredient_from = "+ingredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    while (!readCursor.isAfterLast())
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient_measurement_conversion");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //Date now = new Date();
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
                readCursor.moveToNext();
		    }
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("ingredient_measurement_conversion", "id_ingredient_measurement_from = " + measurementID + " AND id_ingredient_from = "+ingredientID, null);
			
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	public static void RemoveMeasurementConversionFromIngredient(DataBaseAdapter dbAdapter, Long ingredientMeasurementConversionID){
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		Cursor readCursor;
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient_measurement_conversion", new String[] { "ingredient_measurement_conversion_UUID" }, "_id = " + ingredientMeasurementConversionID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    while (!readCursor.isAfterLast())
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient_measurement_conversion");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //Date now = new Date();
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
                readCursor.moveToNext();
		    }
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("ingredient_measurement_conversion", "_id = " + ingredientMeasurementConversionID, null);
			
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	public static String DeleteIngredient(DataBaseAdapter dbAdapter, Long ingredientID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		Cursor readCursor;
		try
		{
			readCursor = dbAdapter.primaryDataBase.query("recipe_ingredient", new String[] { "_id" }, "id_ingredient = " + ingredientID, null, null, null, null);
		}
		catch (SQLException e)
		{
			throw new Error("Error querying database: "+e.getMessage());
		}
	    readCursor.moveToFirst();
		if (readCursor.getCount() > 0)
		{
			if (!leaveDBOpen){
				dbAdapter.close();
			}
			return "This ingredient is in use in at least one recipe and can not be deleted.";
		}
		
		
		try
		{
			readCursor = dbAdapter.primaryDataBase.query("shopping_list_ingredient", new String[] { "_id" }, "id_ingredient = " + ingredientID, null, null, null, null);
		}
		catch (SQLException e)
		{
			throw new Error("Error querying database: "+e.getMessage());
		}
	    readCursor.moveToFirst();
		if (readCursor.getCount() > 0)
		{
			if (!leaveDBOpen){
				dbAdapter.close();
			}
			return "This ingredient is in use in at least one shopping list and can not be deleted.";
		}
		readCursor.close();
		//Date now = new Date();
		//delete ingredient_to_custom_ingredient_measurement_link
		ContentValues deletedRowValues;
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "ingredient_to_custom_ingredient_measurement_link_UUID" }, "id_ingredient = " + ingredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    
		    if (readCursor.getCount() > 0)
		    {
		    	deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient_to_custom_ingredient_measurement_link");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_to_custom_ingredient_measurement_link_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			}
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("ingredient_to_custom_ingredient_measurement_link", "id_ingredient = "+ingredientID, null);
	    
		
		//delete ingredient_measurement_conversion
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient_measurement_conversion", new String[] { "ingredient_measurement_conversion_UUID" }, "id_ingredient_from = " + ingredientID +" OR id_ingredient_to = "+ ingredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient_measurement_conversion");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			} 
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("ingredient_measurement_conversion", "id_ingredient_from = "+ingredientID+" OR id_ingredient_to = "+ingredientID, null);
	    
		//delete ingredient
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient", new String[] { "ingredient_UUID" }, "_id = " + ingredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			}
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("ingredient", "_id = " + ingredientID, null);
	    

		

		if (!leaveDBOpen){
			dbAdapter.close();
		}
		return null;
	}
	
	public static void RemoveIngredientFromRecipe(DataBaseAdapter dbAdapter, Long recipeIngredientID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		if  (dbAdapter.hasBeenSynced()) {
			Cursor readCursor;
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe_ingredient", new String[] { "recipe_ingredient_UUID" }, "_id = " + recipeIngredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe_ingredient");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_ingredient_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //Date now = new Date();
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
		    
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("recipe_ingredient", "_id = "+recipeIngredientID, null);

		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	public static void DeleteSubRecipe(DataBaseAdapter dbAdapter, Long subRecipeID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		if  (dbAdapter.hasBeenSynced()) {
			Cursor readCursor;
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("sub_recipe", new String[] { "sub_recipe_UUID" }, "_id = " + subRecipeID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "sub_recipe");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("sub_recipe_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //Date now = new Date();
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("sub_recipe", "_id = "+subRecipeID, null);
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	public static void DeleteRecipeImage(DataBaseAdapter dbAdapter, Long recipeImageID)
	{
		boolean leaveDBOpen = true;
		Long recipeID = (long)-1;
		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		Cursor readCursor;
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe_image", new String[] { "id_recipe", "recipe_image_UUID" }, "_id = " + recipeImageID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (!readCursor.isAfterLast())
		    {
		    	recipeID = readCursor.getLong(readCursor.getColumnIndexOrThrow("id_recipe"));
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe_image");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_image_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //Date now = new Date();
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
		    readCursor.close();
		}
		dbAdapter.primaryDataBase.delete("recipe_image", "_id = "+recipeImageID, null);
		
		//update order of all images
		try
		{
			readCursor = dbAdapter.primaryDataBase.query("recipe_image", new String[] { "_id" }, "id_recipe = " + recipeID, null, null, null, "recipe_image_order ASC");
		}
		catch (SQLException e)
		{
			throw new Error("Error querying database: "+e.getMessage());
		}
	    readCursor.moveToFirst();
	    
	    int imageCounter = 1;
	    while (!readCursor.isAfterLast())
	    {
	    	ContentValues updateValues = new ContentValues();
	    	updateValues.put("recipe_image_order", imageCounter);
	    	dbAdapter.primaryDataBase.update("recipe_image", updateValues, "_id = " + readCursor.getInt(readCursor.getColumnIndexOrThrow("_id")), null);
	    	imageCounter++;
	    	readCursor.moveToNext();
	    }
		
		dbAdapter.primaryDataBase.rawQuery("VACUUM", null);
		readCursor.close();
		
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	public static void DeleteRecipe(DataBaseAdapter dbAdapter, Long recipeID)
	{

		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		Cursor readCursor;
	    //Date now = new Date();
	    ContentValues deletedRowValues;
	    //delete sub_recipe
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("sub_recipe", new String[] { "sub_recipe_UUID" }, "id_recipe = " + recipeID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
	    
		    while (!readCursor.isAfterLast())
		    {
		    	
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "sub_recipe");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("sub_recipe_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			    readCursor.moveToNext();
		    }
		    readCursor.close();
	    }
	    dbAdapter.primaryDataBase.delete("sub_recipe", "id_recipe = " + recipeID, null);
	    
	    //delete recipe ingredients
	    if  (dbAdapter.hasBeenSynced()) {
		    try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe_ingredient", new String[] { "recipe_ingredient_UUID" }, "id_recipe = " + recipeID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
	    
		    while (!readCursor.isAfterLast())
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe_ingredient");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_ingredient_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			    readCursor.moveToNext();
		    }
		    readCursor.close();
	    }dbAdapter.primaryDataBase.delete("recipe_ingredient", "id_recipe = " + recipeID, null);
	    //delete recipe images
	    if  (dbAdapter.hasBeenSynced()) {
		    try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe_image", new String[] { "recipe_image_UUID" }, "id_recipe = " + recipeID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
	    
		    while (!readCursor.isAfterLast())
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe_image");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_image_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
			    readCursor.moveToNext();
		    }
		    readCursor.close();
	    }
    	dbAdapter.primaryDataBase.delete("recipe_image", "id_recipe = " + recipeID, null);
        //delete recipe category links
        try
        {
            readCursor = dbAdapter.primaryDataBase.query("recipe_to_recipe_category_link", new String[] { "_id" }, "id_recipe = " + recipeID, null, null, null, null);
        }
        catch (SQLException e)
        {
            throw new Error("Error querying database: "+e.getMessage());
        }
        while (readCursor.moveToNext()){
            DeleteRecipeToRecipeCategoryLink(dbAdapter, readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
        }
        //delete subrecipe entries for this recipe
        try
        {
            readCursor = dbAdapter.primaryDataBase.query("sub_recipe", new String[] { "_id" }, "id_sub_recipe = " + recipeID, null, null, null, null);
        }
        catch (SQLException e)
        {
            throw new Error("Error querying database: "+e.getMessage());
        }
        while (readCursor.moveToNext()){
            DeleteSubRecipe(dbAdapter, readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
        }
	    //delete recipe
	    if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe", new String[] { "recipe_UUID" }, "_id = " + recipeID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
		    readCursor.close();
	    }
	    dbAdapter.primaryDataBase.delete("recipe", "_id = " + recipeID, null);
	    try
		{
	    	dbAdapter.primaryDataBase.execSQL("VACUUM");
		}
		catch (SQLException e)
		{
			throw new Error("Error cleaning up database" +e.getMessage());
		}
	
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	public static void DeleteShoppingList(DataBaseAdapter dbAdapter, Long shoppingListID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		Cursor readCursor;
		ContentValues deletedRowValues;
	    //Date now = new Date();
	    //delete shopping_list_ingredient
		if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("shopping_list_ingredient", new String[] { "shopping_list_ingredient_UUID" }, "id_shopping_list = " + shoppingListID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    deletedRowValues = new ContentValues();
		    while(!readCursor.isAfterLast())
		    {
			    deletedRowValues.put("deleted_row_table_name", "shopping_list_ingredient");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("shopping_list_ingredient_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);	    
			    readCursor.moveToNext();
		    }
		    readCursor.close();
		}
	    dbAdapter.primaryDataBase.delete("shopping_list_ingredient", "id_shopping_list = " + shoppingListID, null);

	    //delete shopping_list
	    if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("shopping_list", new String[] { "shopping_list_UUID" }, "_id = " + shoppingListID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
			    deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "shopping_list");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("shopping_list_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
		    readCursor.close();
	    }
	    dbAdapter.primaryDataBase.delete("shopping_list", "_id = " + shoppingListID, null);

		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	public static void DeleteShoppingListIngredient(DataBaseAdapter dbAdapter, Long shoppingListIngredientID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		//Date now = new Date();
		Cursor readCursor;
	    if  (dbAdapter.hasBeenSynced()) {
			try
			{
				readCursor = dbAdapter.primaryDataBase.query("shopping_list_ingredient", new String[] { "shopping_list_ingredient_UUID" }, "_id = " + shoppingListIngredientID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "shopping_list_ingredient");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("shopping_list_ingredient_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			    //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
			readCursor.close();
	    }
		dbAdapter.primaryDataBase.delete("shopping_list_ingredient", "_id = " + shoppingListIngredientID, null);
		
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	
	public static void DeleteIngredientGroup(DataBaseAdapter dbAdapter, Long ingredientGroupID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		//Date now = new Date();
		Cursor readCursor;
	    if  (dbAdapter.hasBeenSynced()) {

			try
			{
				readCursor = dbAdapter.primaryDataBase.query("ingredient_group", new String[] { "ingredient_group_UUID" }, "_id = " + ingredientGroupID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "ingredient_group");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_group_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			   // deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
			readCursor.close();
	    }
		dbAdapter.primaryDataBase.delete("ingredient_group", "_id = " + ingredientGroupID, null);
		
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	
	
	public static void DeleteRecipeToRecipeCategoryLink(DataBaseAdapter dbAdapter, Long recipeToRecipeCategoryLinkID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		Cursor readCursor;
	    if  (dbAdapter.hasBeenSynced()) {

			try
			{
				readCursor = dbAdapter.primaryDataBase.query("recipe_to_recipe_category_link", new String[] { "recipe_to_recipe_category_link_UUID" }, "_id = " + recipeToRecipeCategoryLinkID, null, null, null, null);
			}
			catch (SQLException e)
			{
				throw new Error("Error querying database: "+e.getMessage());
			}
		    readCursor.moveToFirst();
		    if (readCursor.getCount() > 0)
		    {
				ContentValues deletedRowValues = new ContentValues();
			    deletedRowValues.put("deleted_row_table_name", "recipe_to_recipe_category_link");
			    deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("recipe_to_recipe_category_link_UUID")));
			    deletedRowValues.putNull("deleted_row_synced_date_time");
			   // deletedRowValues.put("deleted_row_synced_date_time", now.toString());
			    dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
		    }
			readCursor.close();
	    }
		dbAdapter.primaryDataBase.delete("recipe_to_recipe_category_link", "_id = " + recipeToRecipeCategoryLinkID, null);
		

		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}


    public static void DeleteMeasurement(DataBaseAdapter dbAdapter, Long ingredientMeasurementID)
    {
        boolean leaveDBOpen = true;

        if (!dbAdapter.isOpen())
        {
            dbAdapter.open();
            leaveDBOpen = false;
        }
        Cursor readCursor;

        //remove conversion entries for the measurement
        try
        {
            readCursor = dbAdapter.primaryDataBase.query("ingredient_measurement_conversion", new String[] { "_id" }, "id_ingredient_measurement_from = " + ingredientMeasurementID + " OR id_ingredient_measurement_to = " + ingredientMeasurementID, null, null, null, null);
        }
        catch (SQLException e)
        {
            throw new Error("Error querying database: "+e.getMessage());
        }
        while (readCursor.moveToNext()) {
            RemoveMeasurementConversionFromIngredient(dbAdapter, readCursor.getLong(readCursor.getColumnIndexOrThrow("_id")));
        }

        //remove links to ingredients
        try
        {
            readCursor = dbAdapter.primaryDataBase.query("ingredient_to_custom_ingredient_measurement_link", new String[] { "id_ingredient" }, "id_ingredient_measurement = " + ingredientMeasurementID, null, null, null, null);
        }
        catch (SQLException e)
        {
            throw new Error("Error querying database: "+e.getMessage());
        }
        while (readCursor.moveToNext()) {
            RemoveMeasurementFromIngredient(dbAdapter,readCursor.getLong(readCursor.getColumnIndexOrThrow("id_ingredient")),ingredientMeasurementID);
        }


        if  (dbAdapter.hasBeenSynced()) {

            try
            {
                readCursor = dbAdapter.primaryDataBase.query("ingredient_measurement", new String[] { "ingredient_measurement_UUID" }, "_id = " + ingredientMeasurementID, null, null, null, null);
            }
            catch (SQLException e)
            {
                throw new Error("Error querying database: "+e.getMessage());
            }

            if (readCursor.moveToFirst())
            {
                ContentValues deletedRowValues = new ContentValues();
                deletedRowValues.put("deleted_row_table_name", "ingredient_measurement");
                deletedRowValues.put("deleted_row_UUID", readCursor.getString(readCursor.getColumnIndexOrThrow("ingredient_measurement_UUID")));
                deletedRowValues.putNull("deleted_row_synced_date_time");
                //Date now = new Date();
                //deletedRowValues.put("deleted_row_synced_date_time", now.toString());
                dbAdapter.primaryDataBase.insert("deleted_row", "_id", deletedRowValues);
            }
            readCursor.close();
        }
        dbAdapter.primaryDataBase.delete("ingredient_measurement", "_id = "+ingredientMeasurementID, null);

        if (!leaveDBOpen){
            dbAdapter.close();
        }
    }
	
	/*
	public final static void DeleteTemplate(DataBaseAdapter dbAdapter, Long ID)
	{
		boolean leaveDBOpen = true;

		if (!dbAdapter.isOpen())
		{
			dbAdapter.open();
			leaveDBOpen = false;
		}
		
		
		if (!leaveDBOpen){
			dbAdapter.close();
		}
	}
	*/
}
