package net.runserver.apps4bro;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class AdManager
{
    private final static String TAG = "AdManager";
    private final static int ChainThreshold = 4;
    private final static int AdShowDelay = 4 * 60 * 1000; // 5 minutes

    private final Map<String, String> m_keys;
    private final Map<String, AdNetworkHandler> m_adNetworks;
    private final String m_appId;

    private AdManagerListener m_listener;
    private Context m_context;

    private AdWrapper[] m_adWrappers;

    private boolean m_noAds;
    private boolean m_adShown;
    private boolean m_inited;
    private boolean m_timedOut;

    public boolean isAdShown()
    {
        return m_adShown;
    }

    public boolean isNoAds()
    {
        return m_noAds;
    }

    public boolean isInited()
    {
        return m_inited;
    }

    public boolean isChain()
    {
        return m_adWrappers.length >= ChainThreshold;
    }

    public boolean isTimedOut()
    {
        return m_timedOut;
    }

    public Context getContext()
    {
        return m_context;
    }

    public void setListener(AdManagerListener listener)
    {
        m_listener = listener;
    }

    public void registerAdNetwork(AdNetworkHandler handler)
    {
        m_adNetworks.put(handler.getNetwork().toLowerCase(), handler);
    }

    public AdManager(String[] keys)
    {
        m_keys = new HashMap<String, String>();

        for (int i = 0; i < keys.length / 2; i++)
            m_keys.put(keys[i * 2].toLowerCase(), keys[i * 2 + 1]);

        m_adNetworks = new HashMap<String, AdNetworkHandler>();

        m_appId = m_keys.get(Apps4BroSDK.App4BroTag);
        if (m_appId == null)
        {
            Log.e(TAG, "No App4Bro key specified for AdManager!");
            return;
        }

        ////////

        registerAdNetwork(new AdMobNetwork(this, false));
        registerAdNetwork(new AdMobNetwork(this, true));

        //registerAdNetwork(new AppodealNetwork(this));
        // registerAdNetwork(new AppodealVideoNetwork(this));
        //registerAdNetwork(new AppLovinNetwork(this));
        //registerAdNetwork(new AdToAppNetwork(this));
        //registerAdNetwork(new AdCashNetwork(this));
        //registerAdNetwork(new MoPubNetwork(this));
        //registerAdNetwork(new MobFoxNetwork(this));
        //registerAdNetwork(new FlyMobNetwork(this));
        //registerAdNetwork(new MyTargetNetwork(this));

        registerAdNetwork(new HouseNetwork(this));
    }

    //region Timeout region

    public boolean isAdTimeoutCompleted(Context context, boolean set)
    {
        long now = new Date().getTime();
        long next = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).getLong("next_ad", -1);

        if (next <= now)
        {
            if (set)
                setAdTimeout(context);
            //if (next == -1)
            //  return false;
            return true;
        }
        return false;
    }

    private void setAdTimeout(Context context)
    {
        Editor editor = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
        editor.putLong("next_ad", new Date().getTime() + AdShowDelay);
        editor.commit();
    }

    //endregion

    // region Init region

    public void init(Context context)
    {
        m_context = context;

        if (m_inited)
        {
            Log.e(TAG, "Called AdManager init twice!");
            //if (m_listener != null)
            //  m_listener.onInited(this);
            return;
        }

        Apps4BroSDK.init(context.getApplicationContext());

        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                initAsync();
            }
        };
        thread.start();
    }

    private void initAsync()
    {
        m_inited = true;

        try
        {
            URLConnection conn = new URL(Apps4BroSDK.getRequestUrl(m_appId)).openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            String data = Apps4BroSDK.loadStreamText(conn.getInputStream());
            if (data != null && data.length() > 0)
                parseAdData(data);
            else
                Log.w(TAG, "Got empty response");

        }
        catch (Exception ex)
        {
            Log.w(TAG, "Network error: " + ex);
            ex.printStackTrace();
        }

        if (m_adWrappers == null)
        {
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

            parseAdData(builder.toString());

            if (m_adWrappers == null || m_adWrappers.length == 0)
            {
                Log.w(TAG, "No ad networks registered!");
                {
                    ((Activity) m_context).runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            m_listener.onTimeout(AdManager.this);
                        }
                    });
                }
                return;
            }
        }

        if (m_listener != null)
            ((Activity) m_context).runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    m_listener.onInit(AdManager.this);
                }
            });
    }

    private void parseAdData(String data)
    {
        try
        {
            String[] adData = data.split("\\|");

            if (adData.length > 1)
            {
                List<AdWrapper> wrappers = new ArrayList<AdWrapper>(adData.length / 2);

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

                    AdNetworkHandler networkHandler = m_adNetworks.get(network);

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
                    m_adWrappers = wrappers.toArray(new AdWrapper[wrappers.size()]);
                    Log.d(TAG, "Registered " + m_adWrappers.length + " ad networks");
                }
            }
        }
        catch (Exception ex)
        {
            Log.e(TAG, "Error parsing data string: " + data);
            ex.printStackTrace();
        }
    }

    //endregion

    public void loadAds(final int timeout)
    {
        if (m_adNetworks == null)
        {
            Log.w(TAG, "requestAd called before initialization!");
            return;
        }

        Log.w(TAG, "We are starting loadAds with timeout " + timeout + ", chain mode: " + isChain());

        m_timedOut = false;
        m_noAds = false;
        boolean hasLoading = false;

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            AdNetworkHandler.AdObject request = m_adWrappers[i].AdRequest;
            if (request != null)
            {
                if (request.getState() != AdNetworkHandler.AdState.Loading && request.getState() != AdNetworkHandler.AdState.Ready) // clean old stuff
                {
                    m_adWrappers[i].AdRequest = null;
                } else if (request.getState() == AdNetworkHandler.AdState.Ready)  // we have ad ready, why not show it?
                {
                    if (request.show()) // if that is no a joke - show it!
                    {
                        Log.w(TAG, "Showing ad for " + m_adWrappers[i].getName());
                        m_adShown = true;
                        return;
                    } else
                    {
                        m_adWrappers[i].AdRequest = null;
                    }
                } else // loading
                    hasLoading = true;
            }
        }

        if (hasLoading && isChain())
            return; // in chain mode only one request is allowed per time and we already have something loading

        { // requests thread
            Thread thread = new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        for (int i = 0; i < m_adWrappers.length; i++)
                        {
                            final AdWrapper wrapper = m_adWrappers[i];
                            AdNetworkHandler.AdObject request = wrapper.AdRequest;
                            if (request == null)
                            {
                                ((Activity) m_context).runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {

                                        Log.w(TAG, "loadAds spawned new request for " + wrapper.getName());

                                        wrapper.CallCount++;
                                        wrapper.AdRequest = wrapper.getNetworkHandler().request(wrapper.getId(), wrapper);
                                    }
                                });
                                if (isChain()) // in non-chain mode we are sending requests for all possible networks. In chain - only for first one
                                    break;
                            }
                        }
                    }
                    catch (Exception e) // just in case
                    {
                        e.printStackTrace();
                        if (m_listener != null)
                        {
                            ((Activity) m_context).runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    m_listener.onTimeout(AdManager.this);
                                }
                            });
                        }
                    }
                }
            };
            thread.start();
        }

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
                            if (isAdShown() || isNoAds())
                            {
                                Log.w(TAG, "Throttling finished with ad show or no ads");
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

                    if (m_listener != null)
                    {
                        ((Activity) m_context).runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                m_listener.onTimeout(AdManager.this);
                            }
                        });
                    }
                }
            };
            thread.start();
        }
    }

    private void updateAdStatus()
    {
        if (isAdShown()) // we already have showing ad, no need for this
            return;

        if (isTimedOut()) // no time for new ads
            return;

        Log.w(TAG, "Called updateStatus");

        boolean hasLoading = false;

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            AdNetworkHandler.AdObject request = m_adWrappers[i].AdRequest;
            if (request == null)
            {
                if (isChain()) // in non-chain mode all requests are already sent, so no eed to resend
                {
                    Log.w(TAG, "updateStatus in Chain mode spawned new request for " + m_adWrappers[i].getName());

                    m_adWrappers[i].CallCount++;
                    m_adWrappers[i].AdRequest = m_adWrappers[i].getNetworkHandler().request(m_adWrappers[i].getId(), m_adWrappers[i]);
                    return;
                }
            } else
            {
                if (request.getState() == AdNetworkHandler.AdState.Ready)  // we have ad ready, why not show it?
                {
                    if (request.show())
                    {
                        Log.w(TAG, "Showing ad for " + m_adWrappers[i].getName());
                        m_adShown = true;
                        return;
                    }
                } else if (request.getState() == AdNetworkHandler.AdState.Loading)
                {
                    if (isChain()) // that should never happen, but if so, we'll wait some more
                    {
                        Log.w(TAG, "Chain mode found loading ad for " + m_adWrappers[i].getName());
                        return;
                    }

                    hasLoading = true; // indicates that we still have some work to do
                }
            }

        }

        if (!hasLoading)
        {
            Log.w(TAG, "updateStatus found no active or loading ads");

            m_noAds = true;

            if (m_listener != null)
                m_listener.onNoAds(AdManager.this);
        }
    }

    /*
    private boolean requestAd()
    {
        if (m_adNetworks == null)
        {
            Log.w(TAG, "requestAd called before initialization!");
            return false;
        }

        int i = 0;

        for (i = 0; i < m_adWrappers.length; i++)
        {
            AdNetworkHandler.AdObject request = m_adWrappers[i].AdRequest;
            if (request != null)
            {
                if (request.getState() == AdNetworkHandler.AdState.Ready && request.show())
                {
                    Log.w(TAG, "Showed ad for " + m_adWrappers[i].getName());
                    m_adShown = true;
                    // ideal situation: ad was loaded and shown
                    return true;
                }
                if (request.getState() == AdNetworkHandler.AdState.Loading)
                {
                    // ad is loading, move to next one
                    if (isChain())
                        break; // or wait for the result when in chain mode

                    continue;
                }
            }

            if (request == null)// || request.getState() == AdNetworkHandler.AdState.Failed || request.getState() == AdNetworkHandler.AdState.Closed || request.getState() == AdNetworkHandler.AdState.Used)
            {
                m_adWrappers[i].CallCount++;
                m_adWrappers[i].AdRequest = m_adWrappers[i].getNetworkHandler().request(m_adWrappers[i].getId(), m_adWrappers[i]);
            }

            if (isChain())
                break;
        }

        if (i == m_adWrappers.length)
            m_noAds = true;

        return false;
    }*/

    public void hideAd()
    {
        if (m_adNetworks == null)
        {
            Log.w(TAG, "hideAd called before initialization!");
            return;
        }

        m_adShown = false;

        for (int i = 0; i < m_adWrappers.length; i++)
        {
            AdNetworkHandler.AdObject request = m_adWrappers[i].AdRequest;
            if (request != null && request.getState() == AdNetworkHandler.AdState.Used)
            {
                request.hide();
                Log.d(TAG, "Called hide ad for network " + m_adWrappers[i].getNetworkHandler().getNetwork());
            }
        }
    }

    //region Callback region

    /*internal*/ void adError(Object data, String error)
    {
        String networkName = "Unknown network";
        try
        {
            AdWrapper wrapper = (AdWrapper) data;

            if (wrapper != null)
            {
                networkName = wrapper.getName();
                wrapper.LastError = error;
                wrapper.FailCount++;
                Log.w(TAG, String.format("Failed to load ad from %s [%d/%d/%d/%d], error %s", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
                //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} [{1}/{2}/{3}/{4}] '{5}'", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
                return;
            } else
            {
                //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} '{1}'", networkName, error));
            }

        }
        finally
        {
            updateAdStatus();

            if (m_listener != null)
                m_listener.onFailedAd(this, networkName);
        }
        Log.w(TAG, "Failed to load ad from " + networkName + ", error " + error);
    }

    /*internal*/ void adReady(Object data)
    {
        String networkName = "Unknown network";
        AdWrapper wrapper = (AdWrapper) data;

        try
        {
            if (wrapper != null)
            {
                networkName = wrapper.getName();
                Log.w(TAG, String.format("Ad ready for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
                return;
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            updateAdStatus();

            if (m_listener != null)
                m_listener.onReadyAd(this, networkName);
        }
        Log.w(TAG, "Ad ready for " + networkName);
    }

    /*internal*/ void adClosed(Object data)
    {
        String networkName = "Unknown network";
        AdWrapper wrapper = (AdWrapper) data;

        try
        {
            if (wrapper != null)
            {
                networkName = wrapper.getName();
                Log.w(TAG, String.format("Ad closed for %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
                return;
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if (m_listener != null)
                m_listener.onClosedAd(this, networkName);
        }
        Log.w(TAG, "Ad ready for " + networkName);
    }

    /*internal*/ void adShown(Object data, Object view)
    {
        String networkName = "Unknown network";

        try
        {
            setAdTimeout(m_context);

            AdWrapper wrapper = (AdWrapper) data;

            m_adShown = true;

            if (wrapper != null)
            {
                networkName = wrapper.getName();
                wrapper.SuccessCount++;
                Log.w(TAG, String.format("Displayed ad from %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));

                return;
//	            m_reportManager.ReportEvent("AD_SHOW", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
            } else
            {
//	            m_reportManager.ReportEvent("AD_SHOW", "");
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if (m_listener != null)
                m_listener.onShownAd(this, networkName);
        }
        Log.w(TAG, "Displayed ad from " + networkName);
        //m_context.OnShownAd(this, networkName, view);
    }

    /*internal*/ void adClick(Object data)
    {
        String networkName = "Unknown network";

        try
        {
            AdWrapper wrapper = (AdWrapper) data;

            m_adShown = false;

            if (wrapper != null)
            {
                networkName = wrapper.getName();
                wrapper.ClickCount++;
                Log.w(TAG, String.format("Clicked ad from %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
                return;
//                m_reportManager.ReportEvent("AD_CLICK", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
            } else
            {
//	            m_reportManager.ReportEvent("AD_SHOW", "");
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if (m_listener != null)
                m_listener.onClickedAd(this, networkName);
        }
        Log.w(TAG, "Clicked ad from " + networkName);
//            m_context.OnClickedAd(this, networkName);
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

        void onTimeout(AdManager manager);

        void onNoAds(AdManager manager);
    }
}
