package net.runserver.apps4bro;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdManager
{
    private final static String TAG = "AdManager";
    private final static int NetworkTimeout = 3000; // 3 seconds
    private final static int AdShowDelay = /*4 **/ 60 * 1000; // 1 minutes
    private final static long AdInvalidationTime = 50 * 60 * 1000; // 50 minutes
    private final static String CachePrefs = "app4bro_cache";
    private final static String CacheKeyPrefix = "cascade_";

    private final static Map<String, AdNetworkHandler> s_adNetworks = new HashMap<String, AdNetworkHandler>();

    static
    {
        //region Network registration

        registerAdNetwork(new AdMobNetwork(false));
        registerAdNetwork(new AdMobNetwork(true));
        registerAdNetwork(new AdMobStartupNetwork(false));
        registerAdNetwork(new AdMobStartupNetwork(true));

        //registerAdNetwork(new AppodealNetwork());
        //registerAdNetwork(new AppodealVideoNetwork());
        //registerAdNetwork(new AppLovinNetwork());
        //registerAdNetwork(new AdToAppNetwork());
        //registerAdNetwork(new AdCashNetwork());
        //registerAdNetwork(new MoPubNetwork());
        //registerAdNetwork(new MobFoxNetwork());
        //registerAdNetwork(new FlyMobNetwork());
        //registerAdNetwork(new MyTargetNetwork());
        //registerAdNetwork(new FbAudienceNetwork());

        registerAdNetwork(new DummyNetwork());
        registerAdNetwork(new HouseNetwork());

        //endregion
    }

    private final Map<String, String> m_keys;
    private final String m_appId;

    private final Handler m_uiHandler;
    private final Handler m_backgroundHandler;
    private final HandlerThread m_backgroundThread;

    private AdManagerListener m_listener;
    private final Context m_context;

    private AdWrapper[] m_adWrappers;

    private volatile AdEnums.AdManagerState m_state;
    private volatile long m_adInvalidationTime;
    private volatile int m_requestedTypes = AdEnums.NetworkType.All;
    public AdEnums.AdManagerState getState() { return m_state; }

    /**
     * Discards the currently-loaded ad if it has been Ready longer than
     * {@link #AdInvalidationTime} (i.e. likely expired by the underlying SDK).
     * Triggers a fresh {@code loadAds(types)} so the next opportunity has a warm ad.
     * Cheap no-op when not stale.
     */
    public void discardStaleAd()
    {
        discardStaleAd(AdEnums.NetworkType.All);
    }

    public void discardStaleAd(int types)
    {
        if (m_state == AdEnums.AdManagerState.Ready
                && System.currentTimeMillis() >= m_adInvalidationTime)
        {
            Log.w(TAG, "Discarding stale ad");
            for (int i = 0; i < m_adWrappers.length; i++)
                m_adWrappers[i].clear();
            m_state = AdEnums.AdManagerState.Finished;
            loadAds(types);
        }
    }

    public Context getApplicationContext()
    {
        return m_context;
    }

    public void setListener(AdManagerListener listener)
    {
        m_listener = listener;
    }

    public AdManagerListener getListener()
    {
        return m_listener;
    }

    public static void registerAdNetwork(AdNetworkHandler handler)
    {
        s_adNetworks.put(handler.getNetwork().toLowerCase(), handler);
    }

    public AdManager(String[] keys, Context applicationContext)
    {
        m_context = applicationContext; // we're saving this context to have at least app context. Will be upgraded to Activity later
        // SDK init
        Apps4BroSDK.init(applicationContext);

        // threads
        m_uiHandler = new Handler(Looper.getMainLooper());
        m_backgroundThread = new HandlerThread("AdManager.BackgroundThread");
        m_backgroundThread.start();
        m_backgroundHandler = new Handler(m_backgroundThread.getLooper());

        // variables
        m_state = AdEnums.AdManagerState.NotInited;

        m_keys = new HashMap<String, String>();

        for (int i = 0; i < keys.length / 2; i++)
            m_keys.put(keys[i * 2].toLowerCase(), keys[i * 2 + 1]);

        m_appId = m_keys.get(Apps4BroSDK.App4BroTag);

        if (m_appId == null)
            Log.e(TAG, "No App4Bro key specified for AdManager!");
        else
            Log.d(TAG, "AdManager inited with id " + m_appId);

        runOnBackgroundThread(this::initAsync);
    }

    private void runOnUiThread(Runnable runnable)
    {
        m_uiHandler.post(runnable);
    }

    private void runOnBackgroundThread(Runnable runnable)
    {
        m_backgroundHandler.post(runnable);
    }

    //region Timeout region
/*
    public boolean isAdTimeoutCompleted(Context context, boolean setNewTime)
    {
        if (Debug.isDebuggerConnected())
            return true;

        long now = new Date().getTime();
        long next = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).getLong("next_ad", -1);

        if (next <= now)
        {
            if (setNewTime)
                setNextAdTime(context);

            return true;
        }
        return false;
    }

    private void setNextAdTime(Context context)
    {
        Editor editor = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
        editor.putLong("next_ad", new Date().getTime() + AdShowDelay);
        editor.commit();
    }*/

    // endregion

    // region Public methods

    public void loadAds()
    {
        loadAds(AdEnums.NetworkType.All);
    }

    /**
     * @param types bitmask of {@link AdEnums.NetworkType} values. Only handlers
     *              whose declared type matches at least one bit in {@code types}
     *              will be used. An existing Ready ad of a non-matching type
     *              is evicted and the cascade restarts.
     */
    public void loadAds(int types)
    {
        if (m_state == AdEnums.AdManagerState.NotInited)
        {
            Log.e(TAG, "loadAds called before initialization!");
            return;
        }

        if (m_state == AdEnums.AdManagerState.Showing)
        {
            Log.w(TAG, "loadAds called while showing");
            return;
        }

        m_requestedTypes = types;

        // Existing Ready ad: reuse if its type matches, otherwise evict and
        // fall through to a fresh cascade walk.
        if (m_state == AdEnums.AdManagerState.Ready)
        {
            for (int i = 0; i < m_adWrappers.length; i++)
            {
                if (m_adWrappers[i].getRequestState() == AdEnums.AdState.Ready)
                {
                    if ((m_adWrappers[i].getType() & types) != 0)
                    {
                        Log.w(TAG, "loadAds: Ready ad matches requested types, reusing");
                        return;
                    }
                    Log.w(TAG, "loadAds: Ready ad type doesn't match, evicting");
                    break;
                }
            }
            for (int i = 0; i < m_adWrappers.length; i++)
                m_adWrappers[i].clear();
            m_state = AdEnums.AdManagerState.Finished;
        }

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            switch (m_adWrappers[i].getRequestState())
            {
                case Loading: // this request is now loading something
                    if ((m_adWrappers[i].getType() & types) != 0)
                    {
                        m_state = AdEnums.AdManagerState.Loading;
                        return;
                    }
                    // in-flight load is the wrong type — let it finish on its own;
                    // caller can retry once it settles.
                    Log.w(TAG, "loadAds: in-flight load is wrong type, bailing");
                    return;
                case Ready:  // defensive — we already handled state==Ready above
                    if ((m_adWrappers[i].getType() & types) != 0)
                    {
                        m_state = AdEnums.AdManagerState.Ready;
                        return;
                    }
                    m_adWrappers[i].clear();
                    break;
                default: // clean old stuff
                    m_adWrappers[i].clear();
                    break;
            }
        }

        Log.d(TAG, "We are starting requestAds");

        runOnBackgroundThread(this::requestAds);

        /*
        { // wait thread
            Thread thread = new Thread()
            {
                @Override
                public void run()
                {
                    int count = timeout / 200;
                    try
                    {
                        for (int i = 0; i < count; i++)
                        {
                            if (AdManager.this.getState() != Enums.AdManagerState.Loading) // still loading
                            {
                                Log.w(TAG, "Throttling finished with manager state " + AdManager.this.getState());
                                return;
                            }
                            Thread.sleep(200);
                        }
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    Log.w(TAG, "Throttling finished with timeout");
                    m_timedOut = true;

                    if (m_listener != null && AdManager.this.getState() == Enums.AdManagerState.Loading)
                    {
                        ((Activity) m_context).runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                m_listener.onError(AdManager.this, Enums.AdManagerError.Timeout);
                            }
                        });
                    }
                }
            };
            thread.start();
        }*/
    }

    public boolean showAd(Activity activityContext)
    {
        if (m_state != AdEnums.AdManagerState.Ready)
        {
            Log.d(TAG, "No ads ready for showAd()");
            return false;
        }

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            switch (m_adWrappers[i].getRequestState())
            {
                case Ready:  // we already have something loaded
                    if (m_adWrappers[i].show(activityContext))
                    {
                        Log.d(TAG, "showAd called wrapper " + m_adWrappers[i]);
                        return true;
                    }

                    // ad was not actually loaded or was broken or outdated
                    m_adWrappers[i].clear();
                    break;
            }
        }

        Log.d(TAG, "showAd wasn't able to find adequate ad. Calling requestAds again");

        runOnBackgroundThread(this::requestAds);

        return false;
    }

    public void hideAd()
    {
        if (m_state == AdEnums.AdManagerState.NoAds || m_state == AdEnums.AdManagerState.NotInited || m_state == AdEnums.AdManagerState.Finished)
        {
            Log.w(TAG, "hideAd called before initialization!");
            return;
        }
        m_state = AdEnums.AdManagerState.Finished;

        for (int i = 0; i < m_adWrappers.length; i++)
            m_adWrappers[i].clear();
    }

    // endregion

    // region Init helpers

    private void initAsync()
    {
        if (m_state != AdEnums.AdManagerState.NotInited) // already initialized, skip
            return;

        // Try cached config first for an instant init. Fresh config is fetched in
        // the background and persisted for the next launch. This means launches
        // after the first one don't pay the up-to-NetworkTimeout server round-trip.
        boolean fromCache = false;
        String cached = readCache();
        if (cached != null)
        {
            m_adWrappers = parseAdData(cached);
            fromCache = m_adWrappers.length > 0;
        }

        if (!fromCache)
            m_adWrappers = loadApps4BroData();

        Log.d(TAG, "Initializing complete. Networks registered: " + m_adWrappers.length + (fromCache ? " (from cache)" : ""));

        m_state = m_adWrappers.length == 0 ? AdEnums.AdManagerState.NotInited : AdEnums.AdManagerState.Initialized;

        // push the result to listener

        if (m_listener != null)
            runOnUiThread(() ->
            {
                if (m_adWrappers.length == 0)
                    m_listener.onFailed(this, null, AdEnums.AdManagerError.FailedToInit, "No ad networks registered");
                else
                    m_listener.onInit(this);
            });

        if (fromCache)
            runOnBackgroundThread(this::refreshCache);
    }

    private AdWrapper[] loadApps4BroData()
    {
        if (m_appId != null)
        {
            // first try to load data from network
            try
            {
                URLConnection conn = new URL(Apps4BroSDK.getRequestUrl(m_appId)).openConnection();
                conn.setConnectTimeout(NetworkTimeout);
                conn.setReadTimeout(NetworkTimeout);

                String data = Apps4BroSDK.loadStreamText(conn.getInputStream());
                if (data != null && data.length() > 0)
                {
                    Log.d(TAG, "Got response: " + data);

                    AdWrapper[] wrappers = parseAdData(data);

                    if (wrappers.length > 0)
                    {
                        writeCache(data);
                        return wrappers;
                    }
                } else
                    Log.w(TAG, "Got empty response");
            }
            catch (Exception ex)
            {
                Log.w(TAG, "Network error: " + ex);
                ex.printStackTrace();
            }
        }

        // use default data if nothing was loaded

        Log.d(TAG, "Registering fallback ads");

        StringBuilder builder = new StringBuilder();

        int count = 0;
        for (Map.Entry<String, String> pair : m_keys.entrySet())
        {
            count++;
            builder.append(pair.getKey());
            builder.append(':');
            builder.append(pair.getKey());
            builder.append("def");
            builder.append(count);
            builder.append('|');
            builder.append(pair.getValue());
            builder.append('|');
        }

        return parseAdData(builder.toString());
    }

    private void refreshCache()
    {
        if (m_appId == null)
            return;
        try
        {
            URLConnection conn = new URL(Apps4BroSDK.getRequestUrl(m_appId)).openConnection();
            conn.setConnectTimeout(NetworkTimeout);
            conn.setReadTimeout(NetworkTimeout);

            String data = Apps4BroSDK.loadStreamText(conn.getInputStream());
            if (data != null && data.length() > 0 && parseAdData(data).length > 0)
            {
                writeCache(data);
                Log.d(TAG, "Cache refreshed for next launch");
            }
        }
        catch (Exception ex)
        {
            Log.w(TAG, "Cache refresh failed: " + ex);
        }
    }

    private String readCache()
    {
        if (m_appId == null)
            return null;
        SharedPreferences prefs = m_context.getSharedPreferences(CachePrefs, Context.MODE_PRIVATE);
        return prefs.getString(CacheKeyPrefix + m_appId, null);
    }

    private void writeCache(String data)
    {
        if (m_appId == null)
            return;
        m_context.getSharedPreferences(CachePrefs, Context.MODE_PRIVATE)
                .edit()
                .putString(CacheKeyPrefix + m_appId, data)
                .apply();
    }

    private static AdWrapper[] parseAdData(String data)
    {
        try
        {
            String[] adData = data.split("\\|");

            if (adData.length > 1)
            {
                List<AdWrapper> wrappers = new ArrayList<AdWrapper>(adData.length / 2);

                Log.d(TAG, "Got " + adData.length + " items from server");

                for (int i = 0; i < adData.length; i += 2)
                {
                    String network = adData[i].toLowerCase();
                    String networkId;

                    if (network.contains(":"))
                    {
                        String[] nsplit = network.split(":");
                        network = nsplit[0];
                        networkId = nsplit[1];
                    } else
                        networkId = network + " " + (wrappers.size() + 1);

                    AdNetworkHandler networkHandler = s_adNetworks.get(network);

                    if (networkHandler != null)
                    {
                        wrappers.add(new AdWrapper(networkHandler, adData[i + 1], networkId));
                        Log.d(TAG, "Registered ad network " + network + "[" + adData[i + 1] + "]");
                    } else
                        Log.w(TAG, "Failed to register ad network " + network);
                }


                if (wrappers.size() == 0)
                {
                    Log.w(TAG, "No ad networks registered!");
                } else
                {
                    Log.d(TAG, "Registered " + wrappers.size() + " ad networks");
                    return wrappers.toArray(new AdWrapper[wrappers.size()]);
                }
            } else
                Log.w(TAG, "No ad networks in array!");

        }
        catch (Exception ex)
        {
            Log.e(TAG, "Error parsing data string: " + data);
            ex.printStackTrace();
        }

        return new AdWrapper[0];
    }

    //endregion

    private void requestAds()
    {
        m_state = AdEnums.AdManagerState.Loading;

        // here we are rolling all ad wrappers one by one until we find the next one that is ready
        // note that call to loadAds restarts this process
        try
        {
            boolean found = false;
            for (int i = 0; i < m_adWrappers.length; i++)
            {
                final AdWrapper wrapper = m_adWrappers[i];
                if (wrapper.getRequestState() == AdEnums.AdState.None
                        && (wrapper.getType() & m_requestedTypes) != 0)
                {
                    found = true;
                    runOnUiThread(() -> {
                        Log.w(TAG, "loadAds spawned new request for " + wrapper.getName());

                        wrapper.request(AdManager.this);
                    });
                    break;
                }
            }

            if (!found)
            {
                m_state = AdEnums.AdManagerState.NoAds;
                Log.w(TAG, "Cascade exhausted, no ads available");
                if (m_listener != null)
                    runOnUiThread(() -> m_listener.onFailed(this, null, AdEnums.AdManagerError.NoAds, "Cascade exhausted"));
            }
        }
        catch (Exception e) // just in case
        {
            e.printStackTrace();

            if (m_listener != null)
                runOnUiThread(() -> m_listener.onFailed(AdManager.this, null, AdEnums.AdManagerError.FailedToRequest, e.toString()));
        }
    }

    //region Callback region

    /*internal*/ void adError(Object data, AdEnums.AdManagerError code, String error)
    {
        final String networkName;
        AdWrapper wrapper = (AdWrapper) data;

        if (wrapper != null)
        {
            networkName = wrapper.getName();
            wrapper.LastError = error;
            wrapper.FailCount++;
            Log.w(TAG, String.format("Failed to load ad from %s [%d/%d/%d/%d], code %s, error %s", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, code, error));
        } else
        {
            networkName = null;
            Log.w(TAG, "Ad failure code " + code + ", error " + error);
        }

        // Listener call and cascade decision both happen on UI thread so the listener
        // can manipulate UI (close container, hide overlay, etc.) before we move on.
        runOnUiThread(() -> {
            boolean proceed = true;
            if (m_listener != null)
                proceed = m_listener.onFailed(this, networkName, code, error);

            if (proceed && m_state == AdEnums.AdManagerState.Loading)
                runOnBackgroundThread(this::requestAds);
            else
                Log.w(TAG, "Not loading next ad (proceed=" + proceed + ", state=" + m_state + ")");
        });
    }

    /*internal*/ void adFailedToShow(Object data, String error)
    {
        final String networkName;
        AdWrapper wrapper = (AdWrapper) data;

        if (wrapper != null)
        {
            networkName = wrapper.getName();
            wrapper.LastError = error;
            Log.w(TAG, String.format("Failed to show ad from %s, error %s", networkName, error));
        } else
        {
            networkName = null;
            Log.w(TAG, "Failed to show ad, error " + error);
        }

        // Show attempt is over; the caller (e.g. SplashActivity) needs to resume its
        // flow. No cascade — we already committed to this ad.
        m_state = AdEnums.AdManagerState.Finished;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onFailed(this, networkName, AdEnums.AdManagerError.FailedToShow, error));
    }

    /*internal*/ void adReady(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        Log.w(TAG, String.format("Ad ready for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Ready;
        m_adInvalidationTime = System.currentTimeMillis() + AdInvalidationTime;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onLoaded(this, networkName));
    }

    /*internal*/ void adClosed(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        Log.w(TAG, String.format("Ad closed (dismissed) for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Finished;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onClosedAd(this, networkName, AdEnums.CloseReason.Dismissed));
    }

    /*internal*/ void adClick(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        wrapper.ClickCount++;
        Log.w(TAG, String.format("Ad clicked for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Finished;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onClosedAd(this, networkName, AdEnums.CloseReason.Clicked));
    }

    /*internal*/ void adShown(Object data, Object view)
    {
        AdWrapper wrapper = (AdWrapper) data;
        wrapper.SuccessCount++;
        Log.w(TAG, String.format("Displayed ad from %s [%d/%d/%d/%d]", wrapper.getName(), wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Showing;
        // No listener notification — the public interface only surfaces load/closed/failure events.
    }

    //endregion

    /**
     * Listener for ad manager events. All callbacks are invoked on the UI thread,
     * so implementations can safely manipulate views.
     */
    public interface AdManagerListener
    {
        /** Manager is initialized and ready; the listener should call {@link AdManager#loadAds()}. */
        void onInit(AdManager manager);

        /** A network's load succeeded — an ad is ready to show. */
        void onLoaded(AdManager manager, String network);

        /**
         * Any failure path: load error, no fill, no network, manager init failure, show-time failure, or cascade exhaustion.
         *
         * @param network network name, or {@code null} for manager-level errors
         * @param code typed reason — caller can switch on this
         * @param error freeform message for logging / telemetry
         * @return {@code true} to let the manager try the next network in the cascade (load-phase failures only);
         *         {@code false} to stop. Return is ignored for {@link AdEnums.AdManagerError#FailedToShow},
         *         {@link AdEnums.AdManagerError#FailedToInit}, {@link AdEnums.AdManagerError#FailedToRequest}
         *         and {@link AdEnums.AdManagerError#NoAds} where there is no cascade to continue.
         */
        boolean onFailed(AdManager manager, String network, AdEnums.AdManagerError code, String error);

        /** Ad was shown and is now done. Reason explains how it ended. */
        void onClosedAd(AdManager manager, String network, AdEnums.CloseReason reason);
    }
}
