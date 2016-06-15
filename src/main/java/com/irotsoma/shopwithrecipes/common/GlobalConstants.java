package com.irotsoma.shopwithrecipes.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GlobalConstants {
    public static final long MILLSECS_PER_DAY = 1000 * 60 * 60 * 24;
	public static final long DELETE_SYNC_DATA_AFTER_DAYS = 180;
    public static final String ASSETS_DB_FILE_NAME = "shopwithrecipes.db";
	public static final String V1_DB_FILE_NAME = "shopwithrecipes.db";
    public static final String V2_DB_FILE_NAME = "shopwithrecipes2.db";
    public static final String V3_DB_FILE_NAME = "shopwithrecipes3.db";
    public static final String TEMP_DB_FILE_NAME = "temp.db";
    public static final String EXPORT_SDCARD_DIRECTORY = "shopwithrecipes";

    public static final int MAX_ROWS_PER_LIST = 1000;
    public static final String CONTENT_DATABASE_UUID = "add01491-41bc-49a4-add7-c9484534647d";
    public static final String DATA_PROVIDER_PREFERENCE_PREFIX = "dataProvider_";
    public static final String DATA_PROVIDER_PREFERENCE_SUFFIX = "_installed";
    public static final String PRIMARY_SHARED_PREFERENCES_NAME = "PRIMARY_SHARED_PREFERENCES";
    public static final Map<String, String[]> DATA_PROVIDERS;


    //list of data providers on google play store format is
    //m.put(<package name>,new String[] {<source UUID>, <description>});
    static {  
      Map<String, String[]> m = new HashMap<>();
      m.put("USDAIngredients", new String[] {"dff8da36-1abe-498b-8b58-9daa4ade7486", "Ingredients from USDA"});    
       
      DATA_PROVIDERS = Collections.unmodifiableMap(m);  
    }


}
