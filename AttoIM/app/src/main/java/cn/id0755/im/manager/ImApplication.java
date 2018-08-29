package cn.id0755.im.manager;

import android.app.Application;

public class ImApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        // 确保MobileIMSDK被初始化哦（整个APP生生命周期中只需调用一次哦）
        // 提示：在不退出APP的情况下退出登陆后再重新登陆时，请确保调用本方法一次，不然会报code=203错误哦！
        IMClientManager.getInstance(this).initMobileIMSDK();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    private static ImApplication mInstance = null;
    public static Application getInstance(){
        ImApplication mInstance = ImApplication.mInstance;
        return mInstance;
    }
}
