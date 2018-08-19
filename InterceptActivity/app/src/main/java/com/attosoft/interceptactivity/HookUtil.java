package com.attosoft.interceptactivity;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class HookUtil {
    private final static String TAG = "HookUtil";

    public final static String EXTRA_RAW_INTENT = "extra_raw_intent";

    public static void hookAMS() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
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
                        if ("startActivity".equals(method.getName())) {
                            // 找到参数里面的第一个Intent 对象
                            Intent raw;
                            int index = 0;

                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof Intent) {
                                    index = i;
                                    break;
                                }
                            }
                            raw = (Intent) args[index];

                            Intent newIntent = new Intent();
                            //替换为占位的Activity
                            String stubPackage = "com.attosoft.interceptactivity";
                            ComponentName componentName = new ComponentName(stubPackage, StubActivity.class.getName());
                            newIntent.setComponent(componentName);

                            // 保存一下原始的intent，等会好恢复用
                            newIntent.putExtra(EXTRA_RAW_INTENT, raw);

                            // 替换掉Intent
                            args[index] = newIntent;
                        }
                        return method.invoke(rawIActivityManager,args);
                    }
                });
        instanceField.set(gDefaultObject,proxy);
    }

    public static void hookActivityThreadHandler() throws Exception {
        // 先获取到当前的ActivityThread对象
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field currentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
        currentActivityThreadField.setAccessible(true);
        Object currentActivityThread = currentActivityThreadField.get(null);

        // 由于ActivityThread一个进程只有一个,我们获取这个对象的mH
        Field mHField = activityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Handler mH = (Handler) mHField.get(currentActivityThread);

        Field mCallBackField = Handler.class.getDeclaredField("mCallback");
        mCallBackField.setAccessible(true);

        mCallBackField.set(mH, new ActivityThreadHandlerCallback(mH));
    }
}
