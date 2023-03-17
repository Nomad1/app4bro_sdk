package net.runserver.apps4bro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by gorbuz on 19.05.16.
 */
public class SplashActivity extends Activity implements AdManager.AdManagerListener
{
    private static final String TAG = "Splash";
    private String m_mainActivity;
    private String m_adKeys;
    private AdManager m_adManager;
    private boolean m_launched;
    private boolean m_shouldResume;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        m_launched = false;

        try
        {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            m_mainActivity = bundle.getString("main_activity");
            m_adKeys = bundle.getString("ad_keys");
        }
        catch (Exception ex)
        {
            Log.e(TAG, "Failed to load meta-data, error: " + ex);
            finish();
            return;
        }

        if (isNetworkAvailable() && m_adKeys != null)
        {
            if (m_adManager == null)
                m_adManager = new AdManager(m_adKeys.split("\\|"));

            m_adManager.setListener(this);

            m_adManager.init(this);

            return;
        }
        resumeLaunch();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (m_shouldResume)
            resumeLaunch();
    }

    private boolean isNetworkAvailable()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onInit(AdManager manager)
    {
        if (!m_adManager.isAdShown() && m_adManager.isAdTimeoutCompleted(this, false))
        {
            m_adManager.loadAds(10 * 1000);// maximum time for loading screen is 10 seconds
        } else
        {
            Log.w(TAG, "Skipping add due to " + (m_adManager.isAdShown() ? "show" : "timeout"));
            resumeLaunch();
        }
    }

    @Override
    public void onTimeout(AdManager manager)
    {
        Log.e(TAG, "Ad manager reported timeout");
        resumeLaunch();
    }

    @Override
    public void onNoAds(AdManager manager)
    {
        Log.e(TAG, "Ad manager reported no ads");
        resumeLaunch();
    }

    private void resumeLaunch()
    {
        if (m_launched)
            return;

        m_launched = true;
        Intent intent = new Intent("ACTION_MAIN");
        intent.setComponent(new ComponentName(this, m_mainActivity));
        startActivity(intent);
        finish();

        try
        {
            m_adManager.hideAd();
        }
        catch(Exception ex)
        {
            Log.e(TAG, "Ad manager failed to hide ad"  + ex);
        }
    }

    @Override
    public void onClickedAd(AdManager manager, String network)
    {

    }

    @Override
    public void onClosedAd(AdManager manager, String network)
    {
        resumeLaunch();
    }

    @Override
    public void onReadyAd(AdManager manager, String network)
    {

    }

    @Override
    public void onShownAd(AdManager manager, String network)
    {
        m_shouldResume = true;
    }

    @Override
    public void onFailedAd(AdManager manager, String network)
    {
        if (m_shouldResume)
            resumeLaunch(); // fail was after ad started displaying
    }
}