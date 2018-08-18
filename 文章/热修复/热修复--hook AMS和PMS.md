## AMS获取流程
先来看两种startActivity的方式
1. Context.startActivity
2. Activity.startActivity

//4.4源码
Context.startActivity 的实现在ContextImpl中，需要带上Intent.FLAG_ACTIVITY_NEW_TASK
```
@Override
    public void startActivity(Intent intent, Bundle options) {
        warnIfCallingFromSystemProcess();
        if ((intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
            throw new AndroidRuntimeException(
                    "Calling startActivity() from outside of an Activity "
                    + " context requires the FLAG_ACTIVITY_NEW_TASK flag."
                    + " Is this really what you want?");
        }
        mMainThread.getInstrumentation().execStartActivity(
            getOuterContext(), mMainThread.getApplicationThread(), null,
            (Activity)null, intent, -1, options);
    }
```
Activity.startActivity

```
public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (mParent == null) {
            Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                    this, mMainThread.getApplicationThread(), mToken, this,
                    intent, requestCode, options);
            if (ar != null) {
                mMainThread.sendActivityResult(
                    mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                    ar.getResultData());
            }
            if (requestCode >= 0) {
                // If this start is requesting a result, we can avoid making
                // the activity visible until the result is received.  Setting
                // this code during onCreate(Bundle savedInstanceState) or onResume() will keep the
                // activity hidden during this time, to avoid flickering.
                // This can only be done when a result is requested because
                // that guarantees we will get information back when the
                // activity is finished, no matter what happens to it.
                mStartedActivity = true;
            }

            final View decor = mWindow != null ? mWindow.peekDecorView() : null;
            if (decor != null) {
                decor.cancelPendingInputEvents();
            }
            // TODO Consider clearing/flushing other event sources and events for child windows.
        } else {
            if (options != null) {
                mParent.startActivityFromChild(this, intent, requestCode, options);
            } else {
                // Note we want to go through this method for compatibility with
                // existing applications that may have overridden it.
                mParent.startActivityFromChild(this, intent, requestCode);
            }
        }
    }
```

两种方式一样都是由mInstrumentation来实现，最终调用的是ActivityManagerNative.getDefault()

```
public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        IApplicationThread whoThread = (IApplicationThread) contextThread;
        if (mActivityMonitors != null) {
            synchronized (mSync) {
                final int N = mActivityMonitors.size();
                for (int i=0; i<N; i++) {
                    final ActivityMonitor am = mActivityMonitors.get(i);
                    if (am.match(who, null, intent)) {
                        am.mHits++;
                        if (am.isBlocking()) {
                            return requestCode >= 0 ? am.getResult() : null;
                        }
                        break;
                    }
                }
            }
        }
        try {
            intent.migrateExtraStreamToClipData();
            intent.prepareToLeaveProcess();
            int result = ActivityManagerNative.getDefault()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, null, options);
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
        }
        return null;
    }
    
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
```
单例，要hook就它了；接下来的事情也就简单了


```
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
                        Log.e(TAG,"method:" + method.getName());
                        return method.invoke(rawIActivityManager,args);
                    }
                });
        instanceField.set(gDefaultObject,proxy);
    }
```

## PMS的获取流程

ContextImpl.java	frameworks\base\core\java\android\app	
```
@Override
    public PackageManager getPackageManager() {
        if (mPackageManager != null) {
            return mPackageManager;
        }

        IPackageManager pm = ActivityThread.getPackageManager();
        if (pm != null) {
            // Doesn't matter if we make more than one instance.
            return (mPackageManager = new ApplicationPackageManager(this, pm));
        }

        return null;
    }
```
ActivityThread.java	frameworks\base\core\java\android\app
```
    public static IPackageManager getPackageManager() {
        if (sPackageManager != null) {
            //Slog.v("PackageManager", "returning cur default = " + sPackageManager);
            return sPackageManager;
        }
        IBinder b = ServiceManager.getService("package");
        //Slog.v("PackageManager", "default service binder = " + b);
        sPackageManager = IPackageManager.Stub.asInterface(b);
        //Slog.v("PackageManager", "default service = " + sPackageManager);
        return sPackageManager;
    }
    
    static IPackageManager sPackageManager;
```
对于下面这一段应该很熟悉了
ServiceManager.java	frameworks\base\core\java\android\os
```
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
    private static HashMap<String, IBinder> sCache = new HashMap<String, IBinder>();
```
从上面这两段我们可以看到有两个地方可以hook掉，sPackageManager或者sCache

这里引出一个问题，如果已经有引用指向了已经创建了的对象，这里直接替换掉是否得做一些其他的处理，是否得知道有多少个变量引用了旧的值，我们得去更新；或者直接就把hook提前。


```
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
```
demo地址 https://github.com/attosoft/project/tree/master/HookAMS_PMS