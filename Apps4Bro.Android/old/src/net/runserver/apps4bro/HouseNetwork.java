package net.runserver.apps4bro;

import java.net.URLEncoder;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

class HouseNetwork implements AdNetworkHandler
{
    private final static String TAG = "House";

    private final AdManager m_manager;

    public String getNetwork()
    {
        return TAG;
    }

    public HouseNetwork(AdManager manager)
    {
        m_manager = manager;
    }

    public AdObject request(final String id, final Object data)
    {
        Log.d(TAG, "Running network " + getNetwork() + "[" + id + "]");

        HouseBannerView bannerView = new HouseBannerView(m_manager.getContext(), id, Apps4BroSDK.HouseAdTimeout);

        HouseAd result = new HouseAd(id, bannerView, data);

        bannerView.setListener(result);

        bannerView.load();

        return result;
    }

    class HouseAd implements AdObject, IBannerViewListener
    {
        private final String m_id;
        private final HouseBannerView m_interstitial;
        private final Object m_data;
        private AdState m_state;

        public String getId()
        {
            return m_id;
        }

        public AdState getState()
        {
            return m_state;
        }

        public HouseAd(String id, HouseBannerView interstitial, Object data)
        {
            m_id = id;
            m_interstitial = interstitial;
            m_data = data;
            m_state = AdState.Loading;
        }

        public boolean show()
        {
            try
            {
                /*if (!m_interstitial.isReady)
                {
					m_state = AdState.Failed;
					return false;
				}*/

                if (m_manager.isTimedOut())
                {
                    Log.e(TAG, "Ad loading timed out!");
                    return false;
                }

                m_state = AdState.Used; // consume ad

                m_interstitial.show();
                m_manager.adShown(m_data, null);

                return true;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                m_state = AdState.Failed;
                return false;
            }
        }

        public void hide()
        {
            m_state = AdState.Closed;

            m_interstitial.hide();
            m_interstitial.destroy();
        }

        @Override
        public void onClick()
        {
            m_manager.adClick(m_data);
            hide();
        }

        @Override
        public void onClosed()
        {
            hide();

            m_manager.adClosed(m_data);
        }

        @Override
        public void onError(String error)
        {
            m_state = AdState.Failed;
            m_manager.adError(m_data, "Error " + error);
            hide();
        }

        @Override
        public void onLoad()
        {
            m_state = AdState.Ready;

            m_manager.adReady(m_data);
        }

        public void onDisplay()
        {
            m_state = AdState.Used;

            m_manager.adShown(m_data, m_interstitial);
        }
    }

    public interface IBannerViewListener
    {
        public void onLoad();

        public void onError(String error);

        public void onClick();

        public void onClosed();

        public void onDisplay();
    }

    public class HouseBannerView extends WebView
    {
        private final String m_zoneId;
        private final int m_timeout;
        private IBannerViewListener m_listener;

        public void setListener(IBannerViewListener listener)
        {
            m_listener = listener;
        }

        public HouseBannerView(Context cnt, String zone, int timeout)
        {
            super(cnt, null);
            m_zoneId = zone;
            m_timeout = timeout;
        }

        public void hide()
        {
            if (this.getParent() != null)
            {
                ((ViewGroup) this.getParent()).removeView(this);
                setVisibility(View.GONE);
            }
        }

        @SuppressWarnings("deprecation")
        public void load()
        {
            Activity activity = (Activity) getContext();

            getSettings().setUseWideViewPort(true);
            //s.setBuiltInZoomControls(true);
            getSettings().setLayoutAlgorithm(LayoutAlgorithm.NARROW_COLUMNS);
            getSettings().setLoadWithOverviewMode(true);
            getSettings().setSaveFormData(true);

            getSettings().setJavaScriptEnabled(true);
            //getSettings().setPluginsEnabled(true);

            //addJavascriptInterface(new AdvClientAPI(this), "AdvClientAPI");
            setVerticalScrollBarEnabled(false);
            setHorizontalScrollBarEnabled(false);
            setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);

            try
            {
                setWebChromeClient(new WebChromeClient());
            }
            catch (Exception e)
            {
                Log.e(TAG, e.getMessage(), e);
            }

            setWebViewClient(new WebViewClient()
            {
                private boolean m_shouldClose;

                public void onReceivedError(android.webkit.WebView view, int errorCode, java.lang.String description, java.lang.String failingUrl)
                {
                    Log.d(TAG, errorCode + " , " + description);
                    m_shouldClose = true;
                    if (m_listener != null)
                        m_listener.onError("FailedToReceiveAd: " + errorCode);
                }

                public boolean shouldOverrideUrlLoading(WebView view, String url)
                {
                    try
                    {
                        //Log.d(TAG, "Override click: " + url);
                        Log.d(TAG, "Should override url call : " + url);

                        Uri uri = Uri.parse(url);

                        if (uri.getScheme().equals("app4bro"))
                        {
                            m_shouldClose = true;
                            if (!uri.getHost().equals("200"))
                            {
                                if (m_listener != null)
                                    m_listener.onError("FailedToReceiveAd: " + uri.getHost());
                            } else
                            {
                                if (m_listener != null)
                                    m_listener.onClosed();
                            }
                            return false;
                        }

                        if (uri.getScheme().equals("mopub"))
                        {
                            m_shouldClose = true;
                            if (!uri.getHost().equals("success"))
                            {
                                if (m_listener != null)
                                    m_listener.onError("FailedToReceiveAd: " + uri.getHost());
                            } else
                            {
                                if (m_listener != null)
                                    m_listener.onClosed();
                            }
                            return false;
                        }

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        ((Activity) getContext()).startActivity(intent);
                        if (m_listener != null)
                            m_listener.onClick();

                        return true;
                    }
                    catch (Exception e)
                    {
                        Log.e(TAG, e.getMessage(), e);
                        //e.printStackTrace();
                    }
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url)
                {
                    Log.d(TAG, "onPageFinished: " + url);

                    if (m_shouldClose)
                        return; // no need to display

                    if (url.length() > 16)
                    {
                        if (m_listener != null)
                            m_listener.onLoad();

                    } else if (m_listener != null)
                        m_listener.onError("Invalid url loaded: " + url);
                }
            });

            try
            {
                int w = activity.getWindowManager().getDefaultDisplay().getWidth();
                int h = activity.getWindowManager().getDefaultDisplay().getHeight();

                TelephonyManager manager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = manager.getNetworkOperatorName();

                String url = String.format(Apps4BroSDK.HouseAdUrl, m_zoneId, activity.getPackageName(), URLEncoder.encode(Build.MANUFACTURER), URLEncoder.encode(Build.MODEL), URLEncoder.encode(carrierName), w, h, Locale.getDefault().getLanguage(), Apps4BroSDK.Version, Apps4BroSDK.getPlatform(), Apps4BroSDK.getAdvertisingId());

                Log.d(TAG, "process url: " + url);

                super.loadUrl(url);
            }
            catch (Exception e)
            {
                Log.e(TAG, "error: " + e.getMessage(), e);
            }

        }

        @SuppressWarnings("deprecation")
        public void show()
        {
            final Activity activity = (Activity) getContext();
            try
            {
                ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
                root.addView(this);

                int w = activity.getWindowManager().getDefaultDisplay().getWidth();
                int h = activity.getWindowManager().getDefaultDisplay().getHeight();

                Log.d(TAG, "Show Banner: " + w + "x" + h);
                FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.FILL_PARENT);
                p.leftMargin = 0;
                p.rightMargin = 0;
                setLayoutParams(p);
                setVisibility(View.VISIBLE);
                requestFocus();

                if (m_listener != null)
                    m_listener.onDisplay();

                Thread t = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            Thread.sleep(40000);
                        }
                        catch (InterruptedException e)
                        {
                        }

                        if (getVisibility() == View.VISIBLE)
                        {
                            activity.runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    if (m_listener != null)
                                        m_listener.onClosed();
                                    else
                                        hide();
                                }
                            });
                        }
                    }
                });
                t.start();

            }
            catch (Exception e)
            {
                Log.d(TAG, "Sync prob: " + e.getMessage(), e);
            }
        }
    }
}
