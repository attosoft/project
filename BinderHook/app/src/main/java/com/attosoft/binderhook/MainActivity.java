package com.attosoft.binderhook;

import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            hook();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void hook() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        final String CLIPBOARD_SERVICE = "clipboard";
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);

        /* 获取到Binder对象 */
        IBinder rawBinder = (IBinder) getService.invoke(null,CLIPBOARD_SERVICE);

        IBinder hookedBinder = (IBinder) Proxy.newProxyInstance(serviceManager.getClassLoader(),
                new Class<?>[] { IBinder.class },
                new BinderProxyHookHandler(rawBinder));

        // 把这个hook过的Binder代理对象放进ServiceManager的cache里面
        // 以后查询的时候 会优先查询缓存里面的Binder, 这样就会使用被我们修改过的Binder了
        Field cacheField = serviceManager.getDeclaredField("sCache");
        cacheField.setAccessible(true);
        Map<String, IBinder> cache = (Map) cacheField.get(null);
        cache.put(CLIPBOARD_SERVICE, hookedBinder);
    }

    private class BinderProxyHookHandler implements InvocationHandler{
        private IBinder mBaseBinder;

        Class<?> stub;

        Class<?> iinterface;
        public BinderProxyHookHandler(IBinder binder){
            this.mBaseBinder = binder;
            try {
                this.stub = Class.forName("android.content.IClipboard$Stub");
                this.iinterface = Class.forName("android.content.IClipboard");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("queryLocalInterface".equals(method.getName())) {
                return Proxy.newProxyInstance(proxy.getClass().getClassLoader(),
                        new Class[] { this.iinterface },
                        new BinderHookHandler(mBaseBinder, stub));
            }
            return method.invoke(mBaseBinder, args);
        }
    }
}
