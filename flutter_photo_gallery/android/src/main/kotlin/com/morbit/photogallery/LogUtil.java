package com.morbit.photogallery;

import android.util.Log;

public class LogUtil {
    public  static boolean isOpenLog = false;
    public static void i(String message){
        if(isOpenLog){
            Log.i("nb", message);
        }
    }
    public static void i(String tag,String message){
        if(isOpenLog){
            Log.i(tag, message);
        }
    }
}
