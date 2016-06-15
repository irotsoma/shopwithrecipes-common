package com.irotsoma.shopwithrecipes.common;

import android.content.Context;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;

public class ConversionUtilities {

    public final static String TOO_LARGE_VALUE = "too big";
    public final static String TOO_SMALL_VALUE = "too small";
    private static final int LIMIT = 50;
    private static final int MIN_GOODNESS = 100;
    private static final int ERROR_THRESHOLD = 10;

    public static int[] searchUnsortedLongArray(long[] longArray, long valueToFind){

        int longArrayLength = longArray.length;
        int[] integerValues = new int[longArrayLength];
        int returnCount = 0;
        for (int x = 0; x < longArrayLength; x++)
        {
            if (longArray[x] == valueToFind)
            {
                integerValues[returnCount] = x;
                returnCount++;
            }
        }
        if (returnCount == 0)
        {
            return null;
        }
        else
        {
            //shrink array to content and return
            int [] returnValues = new int[returnCount];
            System.arraycopy(integerValues, 0, returnValues, 0, returnCount);
            //noinspection UnusedAssignment
            integerValues = null;
            return returnValues;
        }
    }
    public static InputFilter quantityTextFilter = new InputFilter() {

        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            String destTxt = dest.toString();
            String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
            if (fractionToDouble(resultingTxt).equals(Double.NaN)) {

                if (source instanceof Spanned) {
                    return new SpannableString("");
                } else {
                    return "";
                }
            }
            return null;
        }
    };
    public static Double fractionToDouble(String inputFraction){

        String wholeNumber;
        String fraction = inputFraction;
        fraction = fraction.replace("~", "");
        fraction = fraction.trim();
        int spaceIndex = fraction.indexOf(' ', 0);
        int slashIndex = fraction.indexOf('/', 0);
        Double returnValue;
        if (spaceIndex == -1)
        {
            if (slashIndex == -1)
            {
                wholeNumber = fraction;
                fraction = "";
            }
            else
            {
                wholeNumber = "0.0";
            }
        }
        else
        {
            wholeNumber = fraction.substring(0, fraction.indexOf(' ', 0)).trim();
            fraction = fraction.substring(fraction.lastIndexOf(' ')+1).trim();
        }

        try
        {
            returnValue = Double.valueOf(wholeNumber);
        }
        catch (Exception e)
        {
            return Double.NaN;
        }


        if (!fraction.equals(""))
        {
            int numerator = 0;
            int denominator = 1;
            try
            {
                numerator = Integer.valueOf(fraction.substring(0,fraction.indexOf('/')));
            }
            catch (Exception ignored){
            }
            try
            {
                denominator = Integer.valueOf(fraction.substring(fraction.indexOf('/')+1));
            }
            catch (Exception ignored){
            }
            try
            {
                returnValue += ((double)numerator)/((double)denominator);
            }
            catch (Exception e){
                return Double.NaN;
            }
        }

        return returnValue;
    }
    public static String doubleToFraction(double inputValue){
        return doubleToFraction(inputValue,LIMIT,MIN_GOODNESS, ERROR_THRESHOLD);
    }
    public static String doubleToFraction(double inputValue, int limit, int minGoodness, int errorThreshold){

        if (inputValue == 0.0)
        {
            return "";
        }
        else if (inputValue >= 2147483647)
        {
            return TOO_LARGE_VALUE;
        }

        double decimal;

        int denominators[] = new int[limit + 1];
        int numerator = 1;
        int denominator =1;
        int temp;

        decimal = inputValue;
        int i = 0;
        while (i < limit + 1) {
            denominators[i] = (int)decimal;
            decimal = 1.0 / (decimal - denominators[i]);
            i = i + 1;
        }
        int last = 0;
        boolean foundAcceptableValue = false;
        while (last < limit) {

            // Initialize variables used in computation
            numerator = 1;
            denominator = 1;
            temp = 0;

            // Do the computation
            int current = last;
            while (current >= 0) {
                //if (denominators[current] !=0)
                //{
                denominator = numerator;
                numerator = (numerator * denominators[current]) + temp;
                temp = denominator;
                //}
                current = current - 1;
            }
            last = last + 1;

            double value = 0;
            if (denominator != 0){
                value = (double)numerator/denominator;
            }
            int goodness = denominators[last];
            double error = 100 * Math.abs(value - inputValue) / inputValue;

            // Exit early if we have reached our goodness criterion
            if ((Math.abs(goodness) > minGoodness) && (error < errorThreshold))
            {
                foundAcceptableValue = true;
                break;
            }
        }
        if (!foundAcceptableValue)
        {
            //if conversion to fraction is not possible with the current limit, goodness and error settings then just return the decimal
            return "~ "+String.valueOf(inputValue);
        }
        else if (denominator == 0)
        {
            return TOO_SMALL_VALUE;
        }
        else if (numerator == 0)
        {
            return TOO_SMALL_VALUE;
        }
        else if (numerator/denominator >= 2147483647)
        {
            return TOO_LARGE_VALUE;
        }
        else if (denominator == 1){
            return Integer.toString(numerator);
        }
        else if (numerator == denominator){
            return "1";
        }
        else if (numerator < denominator){

            return Integer.toString(numerator) + "/" + Integer.toString(denominator);
        }
        else
        {
            int wholeNumber = numerator/denominator;
            int remainder = numerator % denominator;
            return Integer.toString(wholeNumber) + "  " + Integer.toString(remainder)+"/"+Integer.toString(denominator);
        }
    }

    public static String contextAwareDoubleToFraction(Context context, double inputValue){
        String resultFraction = doubleToFraction(inputValue,LIMIT,MIN_GOODNESS, ERROR_THRESHOLD);
        if (resultFraction.equals(ConversionUtilities.TOO_LARGE_VALUE)){
            resultFraction = context.getString(R.string.common_number_too_large_value);
        } else if (resultFraction.equals(ConversionUtilities.TOO_SMALL_VALUE)){
            resultFraction = context.getString(R.string.common_number_too_small_value);
        }
        return resultFraction;
    }
}
