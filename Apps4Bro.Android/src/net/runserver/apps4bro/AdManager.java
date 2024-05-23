package net.runserver.apps4bro;

import android.app.Activity;
import android.content.Context;
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

    private final static Map<String, AdNetworkHandler> s_adNetworks = new HashMap<String, AdNetworkHandler>();

    static
    {
        //region Network registration

        registerAdNetwork(new AdMobNetwork(false));
        registerAdNetwork(new AdMobNetwork(true));

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
    public AdEnums.AdManagerState getState() { return m_state; }

    public Context getApplicationContext()
    {
        return m_context;
    }

    public void setListener(AdManagerListener listener)
    {
        m_listener = listener;
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
        if (m_state == AdEnums.AdManagerState.NotInited)
        {
            Log.e(TAG, "loadAds called before initialization!");
            return;
        }

        if (m_state == AdEnums.AdManagerState.Ready || m_state == AdEnums.AdManagerState.Showing)
        {
            Log.w(TAG, "loadAds called when ad is already loaded or showing");
            return;
        }

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            switch (m_adWrappers[i].getRequestState())
            {
                case Loading: // this request is now loading something
                    m_state = AdEnums.AdManagerState.Loading;
                    return;
                case Ready:  // we already have something loaded
                    m_state = AdEnums.AdManagerState.Ready;
                    return;
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

        m_adWrappers = loadApps4BroData(m_appId, m_keys);

        Log.d(TAG, "Initializing complete. Networks registered: " + m_adWrappers.length);

        m_state = m_adWrappers.length == 0 ? AdEnums.AdManagerState.NotInited : AdEnums.AdManagerState.Initialized;

        // push the result to listener

        if (m_listener != null)
            runOnUiThread(() ->
            {
                if (m_adWrappers.length == 0)
                    m_listener.onError(this, AdEnums.AdManagerError.FailedToInit);
                else
                    m_listener.onInit(this);
            });
    }

    private static AdWrapper[] loadApps4BroData(String appId, Map<String, String> keys)
    {
        if (appId != null)
        {
            // first try to load data from network
            try
            {
                URLConnection conn = new URL(Apps4BroSDK.getRequestUrl(appId)).openConnection();
                conn.setConnectTimeout(NetworkTimeout);
                conn.setReadTimeout(NetworkTimeout);

                String data = Apps4BroSDK.loadStreamText(conn.getInputStream());
                if (data != null && data.length() > 0)
                {
                    Log.d(TAG, "Got response: " + data);

                    AdWrapper [] wrappers = parseAdData(data);

                    if (wrappers.length > 0)
                        return wrappers;
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
        for (Map.Entry<String, String> pair : keys.entrySet())
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
            for (int i = 0; i < m_adWrappers.length; i++)
            {
                final AdWrapper wrapper = m_adWrappers[i];
                if (wrapper.getRequestState() == AdEnums.AdState.None)
                {
                    runOnUiThread(() -> {
                        Log.w(TAG, "loadAds spawned new request for " + wrapper.getName());

                        wrapper.request(AdManager.this);
                    });
                    break;
                }
            }
        }
        catch (Exception e) // just in case
        {
            e.printStackTrace();

            if (m_listener != null)
                runOnUiThread(() -> m_listener.onError(AdManager.this, AdEnums.AdManagerError.FailedToRequest));
        }
    }

    //region Callback region

    /*internal*/ void adError(Object data, String error)
    {
        final String networkName;
        AdWrapper wrapper = (AdWrapper) data;

        if (wrapper != null)
        {
            networkName = wrapper.getName();
            wrapper.LastError = error;
            wrapper.FailCount++;
            Log.w(TAG, String.format("Failed to load ad from %s [%d/%d/%d/%d], error %s", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
            //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} [{1}/{2}/{3}/{4}] '{5}'", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
        } else
        {
            networkName = "Unknown network";
            Log.w(TAG, "Failed to load ad from " + networkName + ", error " + error);

            //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} '{1}'", networkName, error));
        }

        if (m_state == AdEnums.AdManagerState.Loading)
            runOnBackgroundThread(this::requestAds);
        else
            Log.w(TAG, "Not loading next ad since state is " + m_state);

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onFailedAd(this, networkName));
    }

    /*internal*/ void adReady(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        Log.w(TAG, String.format("Ad ready for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Ready;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onReadyAd(this, networkName));
    }

    /*internal*/ void adClosed(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        Log.w(TAG, String.format("Ad closed for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Finished;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onClosedAd(this, networkName));
    }

    /*internal*/ void adShown(Object data, Object view)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        wrapper.SuccessCount++;
        Log.w(TAG, String.format("Displayed ad from %s [%d/%d/%d/%d]", wrapper.getName(), wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Showing;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onShownAd(this, networkName));
    }

    /*internal*/ void adClick(Object data)
    {
        AdWrapper wrapper = (AdWrapper) data;
        final String networkName = wrapper.getName();

        wrapper.ClickCount++;
        Log.w(TAG, String.format("Clicked ad from %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

        m_state = AdEnums.AdManagerState.Finished;

        if (m_listener != null)
            runOnUiThread(() -> m_listener.onClickedAd(this, networkName));
    }

    //endregion

    public interface AdManagerListener
    {
        void onClickedAd(AdManager manager, String network);

        void onClosedAd(AdManager manager, String network);

        void onReadyAd(AdManager manager, String network);

        void onShownAd(AdManager manager, String network);

        void onFailedAd(AdManager manager, String network);

        void onInit(AdManager manager);

        void onError(AdManager manager, AdEnums.AdManagerError error);
    }
}
