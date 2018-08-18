package com.attosoft.dynamichook.biz;

import android.util.Log;

public class ShoppingImpl implements IShopping {
    private final static String TAG = "ShoppingImpl";
    @Override
    public void buyDrinks() {
        Log.e(TAG,"买点饮料！");
    }
}
