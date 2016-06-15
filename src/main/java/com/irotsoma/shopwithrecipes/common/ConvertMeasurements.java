package com.irotsoma.shopwithrecipes.common;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;

import com.irotsoma.shopwithrecipes.common.databaseinterface.DataBaseAdapter;

public class ConvertMeasurements {
	
	private long[] fromID;
	private long[] fromIngredientID;
	private long[] toID;
	private long[] toIngredientID;
	private String[] fromIDGroup;
	private String[] toIDGroup;
	private double[] measurementConversion;

	private boolean[] visitedIndexes;

	
	public ConvertMeasurements(Context context){

        synchronized (DataBaseAdapter.primaryDataBaseLock) {
            DataBaseAdapter readableDatabase = new DataBaseAdapter(context);
            readableDatabase.open();
            Cursor readCursor;
            String sqlQuery = "SELECT ingredient_measurement_conversion.id_ingredient_measurement_from AS id_ingredient_measurement_from, ingredient_measurement_conversion.id_ingredient_from AS id_ingredient_from, ingredient_measurement_conversion.id_ingredient_measurement_to AS id_ingredient_measurement_to, ingredient_measurement_conversion.id_ingredient_to AS id_ingredient_to, ingredient_measurement_conversion.ingredient_measurement_conversion_multiplier AS ingredient_measurement_conversion_multiplier, imFrom.ingredient_measurement_group AS groupFrom, imTo.ingredient_measurement_group AS groupTo FROM ingredient_measurement_conversion JOIN ingredient_measurement AS imFrom ON (imFrom._id = id_ingredient_measurement_from) JOIN ingredient_measurement AS imTo ON (imTo._id = id_ingredient_measurement_to)";
            try {
                readCursor = readableDatabase.primaryDataBase.rawQuery(sqlQuery, null);
            } catch (SQLException e) {
                throw new Error("Error querying database: " + e.getMessage());
            }

            int xCounter = 0;
            fromID = new long[readCursor.getCount()];
            toID = new long[readCursor.getCount()];
            measurementConversion = new double[readCursor.getCount()];
            fromIDGroup = new String[readCursor.getCount()];
            toIDGroup = new String[readCursor.getCount()];
            fromIngredientID = new long[readCursor.getCount()];
            toIngredientID = new long[readCursor.getCount()];
            readCursor.moveToFirst();
            while (!readCursor.isAfterLast()) {

                fromID[xCounter] = readCursor.getLong(readCursor.getColumnIndexOrThrow("id_ingredient_measurement_from"));
                fromIngredientID[xCounter] = readCursor.getLong(readCursor.getColumnIndexOrThrow("id_ingredient_from"));
                toID[xCounter] = readCursor.getLong(readCursor.getColumnIndexOrThrow("id_ingredient_measurement_to"));
                toIngredientID[xCounter] = readCursor.getLong(readCursor.getColumnIndexOrThrow("id_ingredient_to"));
                measurementConversion[xCounter] = readCursor.getDouble(readCursor.getColumnIndexOrThrow("ingredient_measurement_conversion_multiplier"));
                fromIDGroup[xCounter] = readCursor.getString(readCursor.getColumnIndexOrThrow("groupFrom"));
                toIDGroup[xCounter] = readCursor.getString(readCursor.getColumnIndexOrThrow("groupTo"));
                xCounter++;
                readCursor.moveToNext();
            }
            readCursor.close();
            readableDatabase.close();
        }
	}

	
	//returns 0 if there was an error
	public double ConversionFactor(long convertFromMeasurementId, long convertToMeasurementId, long convertIngredientID){
	
		if (convertFromMeasurementId == convertToMeasurementId)
		{
			return 1.0;
		}
		else if ((convertFromMeasurementId == 0) || (convertToMeasurementId == 0))
		{
			return 1.0;
		}
		visitedIndexes = new boolean[fromID.length];
		
		
		//use recursive calls to the same methods passing in the fromID, toID and ingredientID each time until it reaches a dead end and returns to that nested path
		double returnValue = 0.0;
		int[] fromIDStartingIndexes = ConversionUtilities.searchUnsortedLongArray(fromID, convertFromMeasurementId);
		if (fromIDStartingIndexes != null)	{	
			for (int fromIDIndex : fromIDStartingIndexes){
				if ((fromIngredientID[fromIDIndex] == convertIngredientID) || (fromIngredientID[fromIDIndex] == -1)){
					returnValue = CheckPathForward(fromIDIndex, convertToMeasurementId, convertIngredientID);
					if (returnValue != 0.0)
					{
						break;
					}
				}
			}
		}
		
		
		if (returnValue == 0.0)
		{
			int[] toIDStartingIndexes = ConversionUtilities.searchUnsortedLongArray(toID, convertFromMeasurementId);
			if (toIDStartingIndexes != null){
				for (int toIDIndex : toIDStartingIndexes){
					if ((toIngredientID[toIDIndex] == convertIngredientID) || (toIngredientID[toIDIndex] == -1)){
						returnValue = CheckPathBackward(toIDIndex, convertToMeasurementId, convertIngredientID);
						if (returnValue != 0.0)
						{
							break;
						}
					}
				}
			}
		}		
		return returnValue;
	}

	private double CheckPathBackward(int toIDIndex, long convertToMeasurementId, long convertIngredientID) {
		
		visitedIndexes[toIDIndex] = true;
		if (fromID[toIDIndex] == convertToMeasurementId)
		{
			return 1/measurementConversion[toIDIndex];
		}
		else
		{
			double returnValue = 0.0;
			int[] currentFromIDIndexes = ConversionUtilities.searchUnsortedLongArray(fromID, fromID[toIDIndex]);
			if (currentFromIDIndexes != null){
				for(int currentFromIDIndex : currentFromIDIndexes){
					if (!visitedIndexes[currentFromIDIndex] && ((fromIngredientID[currentFromIDIndex] == convertIngredientID) || (fromIngredientID[currentFromIDIndex] == -1))){
						returnValue = CheckPathForward(currentFromIDIndex, convertToMeasurementId, convertIngredientID);
						if (returnValue != 0.0)
						{
							break;
						}
					}
				}
			}
			else 
			{
				return 0.0;
			}
			if (returnValue == 0.0){
				int[] currentToIDIndexes = ConversionUtilities.searchUnsortedLongArray(toID, fromID[toIDIndex]);
				if (currentToIDIndexes != null){
					for (int currentToIDIndex : currentToIDIndexes){
						if (!visitedIndexes[currentToIDIndex] && ((toIngredientID[currentToIDIndex] == convertIngredientID) || (toIngredientID[currentToIDIndex] == -1))){
							returnValue = CheckPathBackward(currentToIDIndex, convertToMeasurementId, convertIngredientID);
							if (returnValue != 0.0)
							{
								break;
							}
						}
					}
				}
				else 
				{
					return 0.0;
				}
			}
			return returnValue * (1/measurementConversion[toIDIndex]);
		}
		
	}
	private double CheckPathForward(int fromIDIndex, long convertToMeasurementId, long convertIngredientID) {

		visitedIndexes[fromIDIndex] = true;
		if (toID[fromIDIndex] == convertToMeasurementId)
		{
			return measurementConversion[fromIDIndex];
		}
		else
		{
			double returnValue = 0.0;
			int[] currentFromIDIndexes = ConversionUtilities.searchUnsortedLongArray(fromID, toID[fromIDIndex]);
			if (currentFromIDIndexes != null)
			{
				for(int currentFromIDIndex : currentFromIDIndexes){
					if (!visitedIndexes[currentFromIDIndex] && ((fromIngredientID[currentFromIDIndex] == convertIngredientID) || (fromIngredientID[currentFromIDIndex] == -1))){
						returnValue = CheckPathForward(currentFromIDIndex, convertToMeasurementId, convertIngredientID);
						if (returnValue != 0.0)
						{
							break;
						}
					}
				}
			}
			else 
			{
				return 0.0;
			}
			if (returnValue == 0.0){
				int[] currentToIDIndexes = ConversionUtilities.searchUnsortedLongArray(toID, toID[fromIDIndex]);
				if (currentToIDIndexes != null){
					for (int currentToIDIndex : currentToIDIndexes){
						if (!visitedIndexes[currentToIDIndex] && ((toIngredientID[currentToIDIndex] == convertIngredientID) || (toIngredientID[currentToIDIndex] == -1))){
							returnValue = CheckPathBackward(currentToIDIndex, convertToMeasurementId, convertIngredientID);
							if (returnValue != 0.0)
							{
								break;
							}
						}
					}	
				}
				else 
				{
					return 0.0;
				}
			}
			return returnValue * measurementConversion[fromIDIndex];
		}
	}

}
