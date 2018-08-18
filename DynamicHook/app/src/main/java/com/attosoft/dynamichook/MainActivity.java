package com.attosoft.dynamichook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.attosoft.dynamichook.biz.IShopping;
import com.attosoft.dynamichook.biz.ProxyShopping;
import com.attosoft.dynamichook.biz.ShoppingImpl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final static String TAG = "MainActivity";
    private Button mStaticShopping;
    private Button mDynamicShopping;
    private Button mHookActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        mStaticShopping = findViewById(R.id.btn_static_shopping);
        mStaticShopping.setOnClickListener(this);
        mDynamicShopping = findViewById(R.id.btn_dynamic_shopping);
        mDynamicShopping.setOnClickListener(this);
        mHookActivity = findViewById(R.id.btn_hook_activity);
        mHookActivity.setOnClickListener(this);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_static_shopping: {
                IShopping baseShopping = new ShoppingImpl();
                IShopping proxy = new ProxyShopping(baseShopping);
                proxy.buyDrinks();
            }
            break;
            case R.id.btn_dynamic_shopping: {
                final IShopping baseShopping = new ShoppingImpl();
                IShopping dynamicShopping = (IShopping) Proxy.newProxyInstance(IShopping.class.getClassLoader(),
                        baseShopping.getClass().getInterfaces(),
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                                Object retObject = null;
                                if ("buyDrinks".equals(method.getName())) {
                                    retObject = method.invoke(baseShopping, objects);
                                    Log.e(TAG, "dynamic proxy");
                                }
                                return retObject;
                            }
                        });
                dynamicShopping.buyDrinks();
            }
            break;
            case R.id.btn_hook_activity:
                try {
                    hook();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                Intent intent = new Intent();
                intent.setClass(this, NewActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /**
     * 直接hook mInstrumentation
     */
    private void hook() throws NoSuchFieldException, IllegalAccessException {
        //这里不能直接用this.class
        Class activityCls = Activity.class;

        Field instrumentationField = activityCls.getDeclaredField("mInstrumentation");
        instrumentationField.setAccessible(true);
        Instrumentation instrumentation = (Instrumentation) instrumentationField.get(this);

        HookInstrumentation hookInstrumentation = new HookInstrumentation(instrumentation);

        instrumentationField.set(this,hookInstrumentation);
    }

    private class HookInstrumentation extends Instrumentation {
        private static final String TAG = "HookInstrumentation";
        private Instrumentation mSrcInstrumentation;

        public HookInstrumentation(Instrumentation instrumentation) {
            mSrcInstrumentation = instrumentation;
        }

        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, Bundle options) {
            Method execStartActivity = null;
            try {
                execStartActivity = Instrumentation.class.getDeclaredMethod(
                        "execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class,
                        Intent.class, int.class, Bundle.class);
                execStartActivity.setAccessible(true);
                /*替换成新的ComponentName*/
                ComponentName componentName = intent.getComponent();
                intent.setComponent(new ComponentName(componentName.getPackageName(),ProxyNewActivity.class.getName()));
                return (ActivityResult) execStartActivity.invoke(mSrcInstrumentation, who,
                        contextThread, token, target, intent, requestCode, options);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            throw new RuntimeException("hook 失败");
        }
    }

}
