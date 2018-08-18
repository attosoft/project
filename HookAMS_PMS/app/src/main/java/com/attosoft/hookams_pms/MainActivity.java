package com.attosoft.hookams_pms;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MainActivity extends AppCompatActivity {
private final static String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            hookAMS();
            hookPMS();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        try {
            getPackageManager().getActivityLogo(new ComponentName(this,MainActivity.class));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void hookAMS() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> activityManagerNativeCls = Class.forName("android.app.ActivityManagerNative");
        Field gDefaultField = activityManagerNativeCls.getDeclaredField("gDefault");
        gDefaultField.setAccessible(true);
        Object gDefaultObject = gDefaultField.get(null);

        Class<?> singletonCls = Class.forName("android.util.Singleton");
        Field instanceField = singletonCls.getDeclaredField("mInstance");
        instanceField.setAccessible(true);
        final Object rawIActivityManager = instanceField.get(gDefaultObject);

        //创建代理对象替换
        Class<?> iActivityManagerInterface = Class.forName("android.app.IActivityManager");
        Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{iActivityManagerInterface}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Log.e(TAG,"hookAMS || method:" + method.getName());
                        return method.invoke(rawIActivityManager,args);
                    }
                });
        instanceField.set(gDefaultObject,proxy);
    }

    private void hookPMS() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Class<?> activityThreadCls = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod = activityThreadCls.getDeclaredMethod("currentActivityThread");
        Object currentActivityThread = currentActivityThreadMethod.invoke(null);

        Field sPackageManagerField = activityThreadCls.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        final Object sPackageManager = sPackageManagerField.get(null);

        Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
        Object proxy = Proxy.newProxyInstance(currentActivityThread.getClass().getClassLoader(),
                new Class[]{iPackageManagerInterface}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Log.e(TAG,"hookPMS || method:"+method.getName());
                        return method.invoke(sPackageManager,args);
                    }
                });
        sPackageManagerField.set(currentActivityThread, proxy);

        //继续替换掉ApplicationPackageManager中的mPM
        PackageManager pm = this.getPackageManager();
        Field mPMField = pm.getClass().getDeclaredField("mPM");
        mPMField.setAccessible(true);
        mPMField.set(pm,proxy);
    }
}
