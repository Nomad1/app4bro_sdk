package net.runserver.apps4bro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public abstract class CardApplication extends Application implements AdManager.AdManagerListener,
        Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver
{
    private static final String TAG = "Application";

    private AdManager m_adManager;
    private Activity m_currentActivity;
    private List<String> m_allowedActivities;
    private boolean m_tvMode;

    @Override
    public void onCreate() {
        super.onCreate();
        this.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        String adKeys;
        try
        {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            adKeys = bundle.getString("ad_keys");
            String mainActivity = bundle.getString("main_activity");
            String[] activities = bundle.getString("ad_activities", mainActivity).split(",");
            m_tvMode = bundle.getBoolean("tv", false);

            if (m_tvMode)
                return; // right now I don't know how to handle TV mode apps with DPAD

            m_allowedActivities = Arrays.asList(activities);

            Log.d(TAG, "Starting app");
        }
        catch (Exception ex)
        {
            Log.e(TAG, "Failed to load meta-data, error: " + ex);

            return;
        }

        if (isNetworkAvailable() && adKeys != null && adKeys.length() > 0)
        {
            Log.d(TAG, "Initializing AdManager");
            m_adManager = new AdManager(adKeys.split("\\|"), this.getApplicationContext());
            m_adManager.setListener(this);
        } else
            Log.d(TAG, "Skipping AdManager");
    }

    private boolean isNetworkAvailable()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isAvailable();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner)
    {
        DefaultLifecycleObserver.super.onStart(owner);

        if (m_adManager == null || m_allowedActivities == null) // initialization failed before
            return;

        // Show the ad (if available) when the app moves to foreground.
        if (m_adManager.getState() == AdEnums.AdManagerState.Ready && m_currentActivity != null)
        {
            Log.d(TAG, "Moving to foreground, going to show ad");
            m_adManager.showAd(m_currentActivity);
        }
    }
    @Override
    public void onClickedAd(AdManager manager, String network)
    {
        if (m_adManager == null || m_allowedActivities == null) // initialization failed before
            return;

        if (m_adManager.getState() == AdEnums.AdManagerState.Initialized || m_adManager.getState() == AdEnums.AdManagerState.Finished)
            m_adManager.loadAds();
    }

    @Override
    public void onClosedAd(AdManager manager, String network)
    {
        if (m_adManager == null || m_allowedActivities == null) // initialization failed before
            return;

        if (m_adManager.getState() == AdEnums.AdManagerState.Initialized || m_adManager.getState() == AdEnums.AdManagerState.Finished)
            m_adManager.loadAds();
    }

    @Override
    public void onReadyAd(AdManager manager, String network)
    {

    }

    @Override
    public void onShownAd(AdManager manager, String network)
    {

    }

    @Override
    public void onFailedAd(AdManager manager, String network)
    {
    }

    @Override
    public void onInit(AdManager manager)
    {
        m_adManager.loadAds();
    }

    @Override
    public void onError(AdManager manager, AdEnums.AdManagerError error)
    {

    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle)
    {
        if (m_adManager == null || m_allowedActivities == null) // initialization failed before
            return;

        if (m_adManager.getState() == AdEnums.AdManagerState.Showing)
            return;

        if (activity == m_currentActivity)
            return;

        if (m_allowedActivities.size() != 0 && !m_allowedActivities.contains(activity.getLocalClassName()))
            return;

        Log.d(TAG, "New main activity:" + activity);
        m_currentActivity = activity;

        if (m_adManager.getState() == AdEnums.AdManagerState.Ready)
        {
            Log.d(TAG, "Creating activity, going to show ad");
            m_adManager.showAd(m_currentActivity);
        } else
            m_adManager.loadAds();
    }
    @Override
    public void onActivityStarted(@NonNull Activity activity)
    {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity)
    {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity)
    {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity)
    {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle)
    {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity)
    {

    }
}
