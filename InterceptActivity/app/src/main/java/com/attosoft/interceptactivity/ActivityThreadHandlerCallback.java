package com.attosoft.interceptactivity;

import java.lang.reflect.Field;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;

public class ActivityThreadHandlerCallback implements Handler.Callback {

    Handler mBase;
    private final int LAUNCH_ACTIVITY;

    public ActivityThreadHandlerCallback(Handler base) throws Exception{
        mBase = base;
        //反射获取LAUNCH_ACTIVITY的值
        Class<?> hCls =  Class.forName("android.app.ActivityThread$H");
        Field field = hCls.getDeclaredField("LAUNCH_ACTIVITY");
        LAUNCH_ACTIVITY = (Integer) field.get(null);
    }

    @Override
    public boolean handleMessage(Message msg) {
        //只替换为原来的Intent，其他的不变
        if (msg.what == LAUNCH_ACTIVITY){
            handleLaunchActivity(msg);
        }

        mBase.handleMessage(msg);
        return true;
    }

    private void handleLaunchActivity(Message msg) {
        Object obj = msg.obj;
        try {
            // 把替身恢复成真身
            Field intent = obj.getClass().getDeclaredField("intent");
            intent.setAccessible(true);
            Intent raw = (Intent) intent.get(obj);

            Intent target = raw.getParcelableExtra(HookUtil.EXTRA_RAW_INTENT);
            raw.setComponent(target.getComponent());

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
