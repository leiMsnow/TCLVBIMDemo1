package com.tencent.qcloud.xiaozhibo;

import android.app.Application;
import android.util.Log;

import com.tencent.bugly.crashreport.CrashReport;
import com.tencent.qcloud.xiaozhibo.base.TCHttpEngine;
import com.tencent.qcloud.xiaozhibo.base.TCLog;
import com.tencent.qcloud.xiaozhibo.logic.TCIMInitMgr;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLivePusher;

import java.util.Locale;

/**
 * 小直播应用类，用于全局的操作，如
 * sdk初始化,全局提示框
 */
public class TCApplication extends Application {

//    private RefWatcher mRefWatcher;

    private static final String BUGLY_APPID = "1400012894";

    private static TCApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        initSDK();

//        mRefWatcher =
//        LeakCanary.install(this);
    }

    public static TCApplication getApplication() {
        return instance;
    }

//    public static RefWatcher getRefWatcher(Context context) {
//        TCApplication application = (TCApplication) context.getApplicationContext();
//        return application.mRefWatcher;
//    }

    /**
     * 初始化SDK，包括Bugly，IMSDK，RTMPSDK等
     */
    public void initSDK() {


        //注册crash上报 bugly组件
        int[] sdkVer = TXLivePusher.getSDKVersion(); //这里调用TXLivePlayer.getSDKVersion()也是可以的
        if (sdkVer != null && sdkVer.length >= 3) {
            if (sdkVer[0] > 0 && sdkVer[1] > 0) {
                //启动bugly组件，bugly组件为腾讯提供的用于crash上报和分析的开放组件，如果您不需要该组件，可以自行移除
                CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(getApplicationContext());
                strategy.setAppVersion(String.format(Locale.US, "%d.%d.%d",sdkVer[0],sdkVer[1],sdkVer[2]));
                CrashReport.initCrashReport(getApplicationContext(), BUGLY_APPID, true, strategy);
            }
        }

        TCIMInitMgr.init(getApplicationContext());

        //设置rtmpsdk log回调，将log保存到文件
        TXLiveBase.getInstance().listener = new TCLog(getApplicationContext());

        //初始化httpengine
        TCHttpEngine.getInstance().initContext(getApplicationContext());

        Log.w("TCLog","app init sdk");
    }

}
