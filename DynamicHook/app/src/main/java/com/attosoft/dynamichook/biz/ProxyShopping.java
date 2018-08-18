package com.attosoft.dynamichook.biz;

import android.util.Log;

public class ProxyShopping implements IShopping{
    private final static String TAG = "ProxyShopping";

    private IShopping mSrc;
    public ProxyShopping(IShopping src){
            mSrc = src;
    }
    @Override
    public void buyDrinks() {
        if (mSrc == null){
            return;
        }
        mSrc.buyDrinks();
        Log.e(TAG,"代理费用，100");
    }
}
