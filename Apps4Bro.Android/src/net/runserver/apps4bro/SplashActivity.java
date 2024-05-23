package net.runserver.apps4bro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.splashscreen.SplashScreen;

/**
 * Created by gorbuz on 19.05.16.
 */
public class SplashActivity extends Activity
{
    private static final String TAG = "Splash";
    private String m_mainActivity;
    private int m_timeout = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

            super.onCreate(savedInstanceState);
            splashScreen.setKeepOnScreenCondition(() -> true);
        }
//        else
//        {
//            super.onCreate(savedInstanceState);
//        }

        try
        {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            m_mainActivity = bundle.getString("main_activity");
            m_timeout = bundle.getInt("ad_timeout", 2) * 1000;

            Handler handler = new Handler(Looper.getMainLooper());

            handler.postDelayed(this::resumeLaunch, m_timeout);
        }
        catch (Exception ex)
        {
            Log.e(TAG, "Failed to load meta-data, error: " + ex);
            resumeLaunch();
        }
    }

    void resumeLaunch()
    {
        Intent intent = new Intent("ACTION_MAIN");
        intent.setComponent(new ComponentName(this, m_mainActivity));
        startActivity(intent);
        finish();
    }
}