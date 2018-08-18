
关于hook，对于应用程序我们只能hook应用程序自己的进程，系统进程是hook不到的

应用程序和系统服务间是通过binder来通信的，其中ServiceManager管理着这些binder


下面看系统服务的获取过程
android 4.4源码

```
ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
```

Activity.java	frameworks\base\core\java\android\app
```
@Override
    public Object getSystemService(String name) {
        if (getBaseContext() == null) {
            throw new IllegalStateException(
                    "System services not available to Activities before onCreate()");
        }

        if (WINDOW_SERVICE.equals(name)) {
            return mWindowManager;
        } else if (SEARCH_SERVICE.equals(name)) {
            ensureSearchManager();
            return mSearchManager;
        }
        return super.getSystemService(name);
    }
```
ContextThemeWrapper.java	frameworks\base\core\java\android\view	

```
@Override public Object getSystemService(String name) {
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mInflater == null) {
                mInflater = LayoutInflater.from(mBase).cloneInContext(this);
            }
            return mInflater;
        }
        return mBase.getSystemService(name);
    }
```
最终实现在
ContextImpl.java	frameworks\base\core\java\android\app	
```
@Override
    public Object getSystemService(String name) {
        ServiceFetcher fetcher = SYSTEM_SERVICE_MAP.get(name);
        return fetcher == null ? null : fetcher.getService(this);
    }
    
private static final HashMap<String, ServiceFetcher> SYSTEM_SERVICE_MAP =
            new HashMap<String, ServiceFetcher>();
```
SYSTEM_SERVICE_MAP 是一个静态变量，这可以成为一个hook点

看看初始化的地方
ContextImpl.java	frameworks\base\core\java\android\app
```
private static void registerService(String serviceName, ServiceFetcher fetcher) {
        if (!(fetcher instanceof StaticServiceFetcher)) {
            fetcher.mContextCacheIndex = sNextPerContextServiceCacheIndex++;
        }
        SYSTEM_SERVICE_MAP.put(serviceName, fetcher);
    }
    
    registerService(ACTIVITY_SERVICE, new ServiceFetcher() {
                public Object createService(ContextImpl ctx) {
                    return new ActivityManager(ctx.getOuterContext(), ctx.mMainThread.getHandler());
                }});
    ...其他的系统服务对象注册省略
```
ActivityManager的核心操作还是由ActivityManagerNative.getDefault()
完成，最终是一个binder引用，或者说是ServiceManager在应用进程中的一个代理服务对象

```
/**
     * Retrieve the system's default/global activity manager.
     */
    static public IActivityManager getDefault() {
        return gDefault.get();
    }

private static final Singleton<IActivityManager> gDefault = new Singleton<IActivityManager>() {
        protected IActivityManager create() {
            IBinder b = ServiceManager.getService("activity");
            if (false) {
                Log.v("ActivityManager", "default service binder = " + b);
            }
            IActivityManager am = asInterface(b);
            if (false) {
                Log.v("ActivityManager", "default service = " + am);
            }
            return am;
        }
    };
    
    static public IActivityManager asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        IActivityManager in =
            (IActivityManager)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }

        return new ActivityManagerProxy(obj);
    }
```
终结一下：
1. IBinder b = ServiceManager.getService("activity");
2. IActivityManager am = asInterface(b);
3. 其他系统服务的也一样

想想我们能干点什么坏事

ServiceManager中缓存了系统服务的Ibinder

ServiceManager.java	frameworks\base\core\java\android\os
```
private static HashMap<String, IBinder> sCache = new HashMap<String, IBinder>();

public static IBinder getService(String name) {
        try {
            IBinder service = sCache.get(name);
            if (service != null) {
                return service;
            } else {
                return getIServiceManager().getService(name);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "error in getService", e);
        }
        return null;
    }
```

接下去要做的就是获取到已存在的系统对象，然后替换成我们的代理对象


```
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

public class BinderHookHandler implements InvocationHandler {

    private static final String TAG = "BinderHookHandler";

    // 原始的Service对象 (IInterface)
    Object base;

    public BinderHookHandler(IBinder base, Class<?> stubClass) {
        try {
            Method asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            // IClipboard.Stub.asInterface(base);
            //静态方法，第一个参数为空
            this.base = asInterfaceMethod.invoke(null, base);
        } catch (Exception e) {
            throw new RuntimeException("hooked failed!");
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("getPrimaryClip".equals(method.getName())) {
            return ClipData.newPlainText(null, "hooked");
        }

        // 欺骗系统,使之认为剪切版上一直有内容
        if ("hasPrimaryClip".equals(method.getName())) {
            return true;
        }

        return method.invoke(base, args);
    }
}
```
现在粘贴板一直返回的都是hooked

demo地址
https://github.com/attosoft/project/tree/master/[BinderHook](http://note.youdao.com/)