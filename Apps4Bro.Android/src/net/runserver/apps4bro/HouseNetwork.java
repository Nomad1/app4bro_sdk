package net.runserver.apps4bro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.net.URLEncoder;
import java.util.Locale;

class HouseNetwork implements AdNetworkHandler
{
    private final static String TAG = "House";

    public String getNetwork()
    {
        return TAG;
    }

    public int getType()
    {
        return AdEnums.NetworkType.Interstitial;
    }

    public HouseNetwork()
    {
    }

    public AdObject request(final AdManager manager, final String id, final Object data)
    {
        Log.d(TAG, "Running network " + getNetwork() + "[" + id + "]");

        HouseBannerView bannerView = new HouseBannerView(manager.getApplicationContext(), id, Apps4BroSDK.HouseAdTimeout);

        HouseAd result = new HouseAd(manager, id, bannerView, data);

        bannerView.setListener(result);

        bannerView.load();

        return result;
    }

    class HouseAd implements AdObject, IBannerViewListener
    {
        private final String m_id;
        private final HouseBannerView m_interstitial;
        private final Object m_data;
        private AdEnums.AdState m_state;
        private final AdManager m_manager;

        public String getId()
        {
            return m_id;
        }

        public AdEnums.AdState getState()
        {
            return m_state;
        }

        public HouseAd(final AdManager manager, String id, HouseBannerView interstitial, Object data)
        {
            m_id = id;
            m_interstitial = interstitial;
            m_data = data;
            m_manager = manager;
            m_state = AdEnums.AdState.Loading;
        }

        public boolean show(Activity activity)
        {
            try
            {
                /*if (!m_interstitial.isReady)
                {
					m_state = AdState.Failed;
					return false;
				}*/

                m_state = AdEnums.AdState.Shown; // consume ad

                m_interstitial.show(activity);
                m_manager.adShown(m_data, null);

                return true;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                m_state = AdEnums.AdState.Failed;
                return false;
            }
        }

        public void hide()
        {
            m_state = AdEnums.AdState.Closed;

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
            m_state = AdEnums.AdState.Failed;
            m_manager.adError(m_data, AdEnums.AdManagerError.FailedToLoad, "House: " + error);
            hide();
        }

        @Override
        public void onLoad()
        {
            m_state = AdEnums.AdState.Ready;

            m_manager.adReady(m_data);
        }

        public void onDisplay()
        {
            m_state = AdEnums.AdState.Shown;

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
//            Context context = getContext();

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
                Context appContext = getContext();
                WindowManager windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
                int w = windowManager.getDefaultDisplay().getWidth();
                int h = windowManager.getDefaultDisplay().getHeight();

                TelephonyManager manager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = manager.getNetworkOperatorName();

                String appVer = "";
                try {
                    appVer = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0).versionName;
                } catch (Exception e) {
                    Log.e("App4Bro", "appver lookup failed: " + e.getMessage());
                }
                String url = String.format(Apps4BroSDK.HouseAdUrl, m_zoneId, appContext.getPackageName(), URLEncoder.encode(Build.MANUFACTURER), URLEncoder.encode(Build.MODEL), URLEncoder.encode(carrierName), w, h, Locale.getDefault().getLanguage(), Apps4BroSDK.Version, Apps4BroSDK.getPlatform(), Build.VERSION.RELEASE, appVer == null ? "" : appVer, Apps4BroSDK.getAdvertisingId());

                Log.d(TAG, "process url: " + url);

                super.loadUrl(url);
            }
            catch (Exception e)
            {
                Log.e(TAG, "error: " + e.getMessage(), e);
            }

        }

        @SuppressWarnings("deprecation")
        public void show(final Activity activity)
        {
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
                            Thread.sleep(30000);
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
