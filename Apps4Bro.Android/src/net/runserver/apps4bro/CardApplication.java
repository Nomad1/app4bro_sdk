package net.runserver.apps4bro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
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
    private static final long COLD_START_DELAY = 60 * 60 * 1000; // 1 hour

    private AdManager m_adManager;
    private Activity m_currentActivity;
    private List<String> m_allowedActivities;
    private boolean m_tvMode;
    private volatile long m_nextColdStart;

    public Activity getCurrentActivity()
    {
        return m_currentActivity;
    }

    public AdManager getAdManager()
    {
        return m_adManager;
    }

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
    public void onStop(@NonNull LifecycleOwner owner)
    {
        DefaultLifecycleObserver.super.onStop(owner);
        m_nextColdStart = System.currentTimeMillis() + COLD_START_DELAY;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner)
    {
        DefaultLifecycleObserver.super.onStart(owner);

        // Warm resume after being backgrounded long enough to feel like a cold start:
        // launch SplashActivity in resume_mode so the colored splash background covers
        // the game while the ad shows. Cold launch goes through SplashActivity natively,
        // so the m_currentActivity guard skips that path (current activity is splash itself
        // or null at that point).
        if (System.currentTimeMillis() < m_nextColdStart) return;
        if (m_adManager == null || m_allowedActivities == null) return;
        // Resume uses the Interstitial cascade. discardStaleAd evicts a Ready-but-expired
        // ad and re-loads with the same type; the follow-up loadAds(Interstitial) handles
        // the type-mismatch case (e.g. a Ready AppOpen ad left over from cold start).
        m_adManager.discardStaleAd(AdEnums.NetworkType.Interstitial);
        m_adManager.loadAds(AdEnums.NetworkType.Interstitial);
        if (m_adManager.getState() != AdEnums.AdManagerState.Ready) return;
        if (m_currentActivity == null) return;
        if (m_currentActivity instanceof SplashActivity) return;
        if (!m_allowedActivities.contains(m_currentActivity.getLocalClassName())) return;

        Intent i = new Intent(m_currentActivity, SplashActivity.class);
        i.putExtra(SplashActivity.EXTRA_RESUME_MODE, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        m_currentActivity.startActivity(i);
    }
    @Override
    public void onInit(AdManager manager)
    {
        // Cold start: only AppOpen networks. Interstitials are loaded later by the resume gate.
        m_adManager.loadAds(AdEnums.NetworkType.AppOpen);
    }

    @Override
    public void onLoaded(AdManager manager, String network)
    {
        // ad warm and ready for the next splash show
    }

    @Override
    public boolean onFailed(AdManager manager, String network, AdEnums.AdManagerError code, String error)
    {
        Log.d(TAG, "Ad failed: network=" + network + " code=" + code + " error=" + error);
        switch (code)
        {
            case NoFill:
            case FailedToLoad:
            case NoNetwork:
                return true;  // load-phase: try the next network in the cascade
            case FailedToShow:
            case FailedToInit:
            case FailedToRequest:
            case NoAds:
            default:
                return false; // show is over / manager unusable / cascade exhausted — stop
        }
    }

    @Override
    public void onClosedAd(AdManager manager, String network, AdEnums.CloseReason reason)
    {
        if (m_adManager == null || m_allowedActivities == null) // initialization failed before
            return;

        // Ads are loaded only on startup and resume — no preload on close.
        // if (m_adManager.getState() == AdEnums.AdManagerState.Initialized || m_adManager.getState() == AdEnums.AdManagerState.Finished)
        //     m_adManager.loadAds();
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

        // No auto-show on activity create — ads are surfaced only by SplashActivity.
        // Preload disabled: the next splash is at least COLD_START_DELAY (60 min) away,
        // longer than AdInvalidationTime (50 min), so any ad loaded here is guaranteed
        // stale by the time it could be shown. Resume gate loads on demand instead.
        // m_adManager.loadAds();
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
