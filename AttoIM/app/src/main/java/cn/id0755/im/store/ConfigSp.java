package cn.id0755.im.store;

import android.content.Context;
import android.content.SharedPreferences;

import cn.id0755.im.manager.ImApplication;

public class ConfigSp {

    private final static String AUTO_LOGIN = "auto_login";
    private final static String REMEMBER_PSW = "remember_psw";

    private SharedPreferences mSharedPreferences = null;
    private static ConfigSp mInstance = null;

    public ConfigSp(SharedPreferences sharedPreferences){
        mSharedPreferences = sharedPreferences;
    }

    public static synchronized ConfigSp getConfigSp(){
        if (mInstance == null){
            mInstance = new ConfigSp(ImApplication.getInstance().getSharedPreferences("APP", Context.MODE_PRIVATE));
        }
        return mInstance;
    }

    public void setAutoLogin(boolean autoLogin){
        mSharedPreferences.edit().putBoolean(AUTO_LOGIN,autoLogin).apply();
    }

    public boolean getAutoLogin(){
        return mSharedPreferences.getBoolean(AUTO_LOGIN,false);
    }

    public void setRememberPsw(boolean rememberPsw){
        mSharedPreferences.edit().putBoolean(REMEMBER_PSW,rememberPsw).apply();
    }

    public boolean getRememberPsw(){
        return mSharedPreferences.getBoolean(REMEMBER_PSW,false);
    }
}
