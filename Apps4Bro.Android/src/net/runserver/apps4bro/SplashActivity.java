package net.runserver.apps4bro;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.splashscreen.SplashScreen;

/**
 * Created by gorbuz on 19.05.16.
 */
public class SplashActivity extends Activity implements AdManager.AdManagerListener
{
    public static final String EXTRA_RESUME_MODE = "resume_mode";

    private static final String TAG = "Splash";
    private String m_mainActivity;
    private int m_timeout = 5;
    private boolean m_adShown;
    private boolean m_advanced;
    private boolean m_resumeMode;

    // Listener-swap: we take the AdManager listener for the splash's lifetime so
    // we can react to onLoaded / onFailed during the wait (and onClosedAd /
    // onFailed(FailedToShow) during the show). Restored on advance / onDestroy.
    private AdManager m_adManager;
    private AdManager.AdManagerListener m_previousListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            splashScreen.setKeepOnScreenCondition(() -> true);

        // Without a content view the activity has nothing to draw on warm launches
        // (where the system splash never fires) — windowBackground flashes briefly
        // and finish() takes over. An explicit content view gives the splash a
        // deterministic, full-screen visual on every launch path. Built in code
        // because Apps4Bro is a res-less library; the empty FrameLayout shows the
        // host activity's windowBackground (e.g. @drawable/back) through.
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        m_resumeMode = getIntent().getBooleanExtra(EXTRA_RESUME_MODE, false);

        try
        {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            m_mainActivity = bundle.getString("main_activity");
            m_timeout = bundle.getInt("ad_timeout", 2) * 1000;

            // Take over the listener now so onLoaded / onFailed can short-circuit
            // the timer below. Whoever it currently is gets restored on advance().
            Application app = getApplication();
            if (app instanceof CardApplication)
            {
                m_adManager = ((CardApplication) app).getAdManager();
                if (m_adManager != null)
                {
                    m_previousListener = m_adManager.getListener();
                    m_adManager.setListener(this);
                }
            }

            Handler handler = new Handler(Looper.getMainLooper());

            // Resume mode + cases we already know the answer for skip the wait entirely.
            // Otherwise the timer is a MAX-wait ceiling; onLoaded / onFailed fire it earlier.
            if (m_resumeMode || isAdManagerTerminal())
                handler.post(this::resumeLaunch);
            else
                handler.postDelayed(this::resumeLaunch, m_timeout);
        }
        catch (Exception ex)
        {
            Log.e(TAG, "Failed to load meta-data, error: " + ex);
            resumeLaunch();
        }
    }

    /**
     * Outcome already known at onCreate — no point waiting on the timer.
     * Deliberately does NOT include NotInited: that's the pre-init transient state,
     * not a failure. Init failures arrive via onFailed(FailedToInit).
     */
    private boolean isAdManagerTerminal()
    {
        if (m_adManager == null) return true;
        AdEnums.AdManagerState s = m_adManager.getState();
        return s == AdEnums.AdManagerState.Ready
            || s == AdEnums.AdManagerState.NoAds;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // Once the ad activity dismisses we land back here; m_adShown tells us this
        // resume is the post-ad return rather than the initial onCreate → onResume.
        if (m_adShown)
            advance();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        // Belt and braces: if no listener callback ran before teardown, restore.
        restoreListener();
    }

    void resumeLaunch()
    {
        // Re-entry guard: the timer may fire after onLoaded already advanced us
        // into show phase. Without this, the timer's call would re-evaluate and
        // potentially advance mid-show.
        if (m_advanced || m_adShown) return;

        if (m_adManager != null && m_adManager.getState() == AdEnums.AdManagerState.Ready)
        {
            m_adShown = true;
            if (m_adManager.showAd(this))
                return; // wait for onClosedAd or onFailed(FailedToShow)
            // showAd returned false synchronously — undo and fall through to advance.
            m_adShown = false;
        }

        advance();
    }

    private void advance()
    {
        if (m_advanced)
            return;
        m_advanced = true;

        restoreListener();

        if (m_resumeMode)
        {
            // The game activity is still in the back stack underneath us; finish()
            // returns to it without firing the launch intent.
            finish();
            return;
        }

        Intent intent = new Intent("ACTION_MAIN");
        intent.setComponent(new ComponentName(this, m_mainActivity));
        startActivity(intent);
        finish();
    }

    private void restoreListener()
    {
        if (m_previousListener != null && m_adManager != null)
        {
            m_adManager.setListener(m_previousListener);
            m_previousListener = null;
        }
    }

    // --- AdManager.AdManagerListener ---

    @Override
    public void onInit(AdManager manager)
    {
        // We took the listener early. The displaced listener normally kicks loadAds
        // on init — delegate so that still happens.
        if (m_previousListener != null)
            m_previousListener.onInit(manager);
    }

    @Override
    public void onLoaded(AdManager manager, String network)
    {
        // Ad just became Ready. Show it now — don't wait for the timer ceiling.
        if (!m_adShown)
            runOnUiThread(this::resumeLaunch);
    }

    @Override
    public boolean onFailed(AdManager manager, String network, AdEnums.AdManagerError code, String error)
    {
        if (m_adShown)
        {
            // Show phase — only FailedToShow lands here in practice.
            if (code == AdEnums.AdManagerError.FailedToShow)
                runOnUiThread(this::advance);
            return false;
        }

        // Wait phase: route by code.
        switch (code)
        {
            case NoFill:
            case FailedToLoad:
            case NoNetwork:
                return true;   // let the cascade keep trying — don't advance yet
            case NoAds:
            case FailedToInit:
            case FailedToRequest:
            case FailedToShow:
            default:
                // No ad is coming. Advance immediately instead of waiting out the timer.
                runOnUiThread(this::advance);
                return false;
        }
    }

    @Override
    public void onClosedAd(AdManager manager, String network, AdEnums.CloseReason reason)
    {
        // Ad ran to completion. Hand the callback back to the previous listener so
        // its loadAds() chain still fires, then let onResume drive advance via m_adShown.
        AdManager.AdManagerListener prev = m_previousListener;
        restoreListener();
        if (prev != null)
            prev.onClosedAd(manager, network, reason);
    }
}
