using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net;
using System.IO;
using System.Text;
using Apps4Bro.Networks;

namespace Apps4Bro
{
    public class AdManager
    {
        private readonly static string App4BroTag = "app4bro";

        private readonly Dictionary<string, AdNetworkHandler> m_adNetworks;
        private readonly Dictionary<string, string> m_keys;

        private readonly ReportManager m_reportManager;

        private AdWrapper[] m_adWrappers;
        private AdContextDelegate m_context;
        private int m_currentWrapper;
        private bool m_adShown;
        private bool m_adLoaded;
        private string m_appId;
        private object m_data;

        public bool IsAdShown
        {
            get { return m_adShown; }
        }

        public bool IsAdLoaded
        {
            get { return m_adLoaded; }
        }

        public bool IsInited
        {
            get { return m_adNetworks != null && m_adWrappers != null; }
        }
        public string AppId
        {
            get { return m_appId; }
        }

        public AdContextDelegate Context
        {
            get { return m_context; }
        }

        public ReportManager ReportManager
        {
            get { return m_reportManager; }
        }

        public AdManager(params string[] keys)
        {
            m_keys = new Dictionary<string, string>();

            for (int i = 0; i < keys.Length; i += 2)
                m_keys.Add(keys[i].ToLower(), keys[i + 1]);

            if (!m_keys.TryGetValue(App4BroTag, out m_appId))
                throw new ApplicationException("No App4Bro key specified for AdManager!");

            m_reportManager = new ReportManager(m_appId);

#if VERBOSE_LOG
            m_reportManager.ReportEvent("AD_START", Apps4BroSDK.Version.ToString());
#endif
            m_adNetworks = new Dictionary<string, AdNetworkHandler>();

            // Registering built-in SDKs
#if __IOS__

            // Interstitials
#if USE_ADMOB
            RegisterAdNetwork(new AdMobNetwork(this));
#endif
#if USE_ADCASH
            RegisterAdNetwork(new AdCashNetwork(this));
#endif
#if USE_IAD
            RegisterAdNetwork(new iAdNetwork(this));
#endif

            if (UIKit.UIDevice.CurrentDevice.CheckSystemVersion(7, 0))
            {
#if USE_APPODEAL
                RegisterAdNetwork(new AppodealNetwork(this));
#endif
#if USE_INNERACTIVE
                RegisterAdNetwork(new InneractiveNetwork(this));
#endif
            }

            // Banners
#if USE_ADMOB
            RegisterAdNetwork(new AdMobBannerNetwork(this));
#endif
#if USE_IAD
            RegisterAdNetwork(new iAdBannerNetwork(this));
#endif


            if (UIKit.UIDevice.CurrentDevice.CheckSystemVersion(7, 0))
            {
#if USE_INNERACTIVE
                RegisterAdNetwork(new InneractiveBanner(this));
#endif
#if USE_ADTOAPP
                RegisterAdNetwork(new AdToAppNetwork(this));
#endif
            }

#elif __ANDROID__
            RegisterAdNetwork(new AdMobNetwork(this));
#if USE_ADCASH
            RegisterAdNetwork(new AdCashNetwork(this));
#endif
#endif
            RegisterAdNetwork(new DummyNetwork(this));
            RegisterAdNetwork(new HouseNetwork(this));
        }

        public void RegisterAdNetwork(AdNetworkHandler handler)
        {
            m_adNetworks[handler.Network.ToLower()] = handler;
        }

        private string FormatRequest()
        {
            return string.Format(Apps4BroSDK.AdManagerUrl,
                Apps4BroSDK.AdvertisingId,
                m_appId
            );
        }

        public void Init(AdContextDelegate context, object data)
        {
            m_data = data;

            if (IsInited)
            {
                LoadAd();
                return;
            }

            m_context = context;
            WebRequest request = WebRequest.Create(FormatRequest());
            request.Headers[HttpRequestHeader.UserAgent] = "WinHTTP";
            request.ContentType = "application/text";
            request.Method = "GET";
#if !NETFX_CORE
            request.Timeout = 5000;
#endif

            request.BeginGetResponse(InitAsync, request);
        }

        private void InitAsync(IAsyncResult asynchronousResult)
        {
            try
            {
                string data = null;

                WebRequest request = (WebRequest)asynchronousResult.AsyncState;
                HttpWebResponse response = request.EndGetResponse(asynchronousResult) as HttpWebResponse;
                if (response == null || response.StatusCode != HttpStatusCode.OK)
                {
                    Debug.WriteLine("Error fetching data. Server returned status code: {0}", response == null ? HttpStatusCode.Unused : response.StatusCode);
                }
                else
                {
                    using (StreamReader reader = new StreamReader(response.GetResponseStream()))
                        data = reader.ReadToEnd();
                }

                if (!string.IsNullOrWhiteSpace(data))
                {
                    ParseAdData(data, true);
                }
                else
                    Debug.WriteLine("Got empty response");

            }
            catch (Exception ex)
            {
                Debug.WriteLine("Network error: " + ex);
            }

            if (m_adWrappers == null) // try cached data
            {
#if __IOS__

                string data = Foundation.NSUserDefaults.StandardUserDefaults.StringForKey(App4BroTag + "_" + m_appId);
                if (data != null)
                {
                    Debug.WriteLine("Registering cached ads");
                    ParseAdData(data, false);
                }

#endif
            }
           

            if (m_adWrappers == null)
            {
                Debug.WriteLine("Registering fallback ads");

                StringBuilder builder = new StringBuilder();

                int count = 0;
                foreach (KeyValuePair<string, string> pair in m_keys)
                {
                    count++;
                    builder.AppendFormat("{0}:{0}def{1}|{2}|", pair.Key, count, pair.Value);
                }

                ParseAdData(builder.ToString(), false);

                if (m_adWrappers == null)
                {
                    Debug.WriteLine("No ad networks registered!");
                    return;
                }
            }

            m_currentWrapper = -1;

            m_context.OnInited(this);
        }

        private void ParseAdData(string data, bool save)
        {
            try
            {
                string[] adData = data.Split(new char[] { '|' }, StringSplitOptions.RemoveEmptyEntries);

                if (adData.Length > 1)
                {
                    List<AdWrapper> wrappers = new List<AdWrapper>(adData.Length / 2);

                    for (int i = 0; i < adData.Length; i += 2)
                    {
                        string network = adData[i].ToLower();
                        string networkId;

                        if (network.Contains(":"))
                        {
                            string[] nsplit = network.Split(':');
                            network = nsplit[0];
                            networkId = nsplit[1];
                        }
                        else
                            networkId = network + " " + (wrappers.Count + 1);

                        AdNetworkHandler networkHandler = null;

                        if (m_adNetworks.TryGetValue(network, out networkHandler))
                        {
                            wrappers.Add(new AdWrapper(networkHandler, adData[i + 1], networkId));
                            Debug.WriteLine("Registered ad network " + network + "[" + adData[i + 1] + "]");
                        }
                        else
                            Debug.WriteLine("Failed to register ad network " + network);
                    }

                    if (wrappers.Count == 0)
                    {
                        Debug.WriteLine("No ad networks registered!");
                    }
                    else
                    {
                        m_adWrappers = wrappers.ToArray();
                        Debug.WriteLine("Registered " + m_adWrappers.Length + " ad networks");
                    }

                    if (save)
                    {
#if __IOS__
                        Foundation.NSUserDefaults.StandardUserDefaults.SetString(data, App4BroTag + "_" + m_appId);
#endif
                    }


                    m_reportManager.ReportEvent("AD_INIT", m_adWrappers.Length.ToString());
                }
            }
            catch (Exception)
            {
                Debug.WriteLine("Error parsing data string " + data);
            }
        }

        public void HideAd()
        {
            if (!IsInited)
            {
                Debug.WriteLine("HideAd called before initialization!");
                return;
            }

            if (!m_adShown)
            {
                Debug.WriteLine("HideAd called with no shown ads!");
                return;
            }

            try
            {
                m_adShown = false;
                AdWrapper wrapper = m_adWrappers[m_currentWrapper];
                wrapper.AdNetworkHandler.Hide();
                Debug.WriteLine("Called hide ad for network " + wrapper.AdNetworkHandler.Network);
            }
            catch (Exception ex)
            {
                Debug.WriteLine("HideAd Exception: " + ex);
            }
        }

        public void LoadAd()
        {
            if (!IsInited)
            {
                throw new ApplicationException("LoadAd called before initialization!");
            }

            m_currentWrapper++;
            m_adLoaded = false;

            Debug.WriteLine("Going to use wrapper #" + m_currentWrapper);

            if (m_currentWrapper >= m_adWrappers.Length)
            {
                Debug.WriteLine("No ad networks left for LoadAd()!");
                m_currentWrapper = -1;
                return;
            }

            AdWrapper wrapper = null;
            try
            {
                wrapper = m_adWrappers[m_currentWrapper];
                wrapper.CallCount++;

                wrapper.AdNetworkHandler.SetId(wrapper.Id);

                if (!wrapper.AdNetworkHandler.Show(wrapper, m_data))
                    throw new ApplicationException("Ad wrapper " + wrapper.Name + " returned false");

            }
            catch (Exception ex)
            {
                Debug.WriteLine("LoadAd Exception: " + ex);

                AdError(wrapper, ex.Message);
            }
        }

        public void DisplayAd()
        {
            if (!IsInited)
            {
                throw new ApplicationException("LoadAd called before initialization!");
            }

            if (!m_adLoaded)
            {
                Debug.WriteLine("DisplayAd called when no ad is loaded!");
                return;
            }

            if (m_currentWrapper == -1)
                LoadAd();

            if (m_currentWrapper == -1)
                return;

            AdWrapper wrapper = null;
            try
            {
                wrapper = m_adWrappers[m_currentWrapper];
                wrapper.AdNetworkHandler.Display();
            }
            catch (Exception ex)
            {
                Debug.WriteLine("DisplayAd Exception: " + ex);

                AdError(wrapper, ex.Message);
            }
        }

        internal void AdLoaded(AdWrapper wrapper)
        {
            string networkName = "Unknown network";
            try
            {
                m_adLoaded = true;

                if (wrapper != null)
                {
                    networkName = wrapper.Name;
                    wrapper.SuccessCount++;
                    Debug.WriteLine("Loaded ad from {0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount);
#if VERBOSE_LOG
                    m_reportManager.ReportEvent("AD_LOAD", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
#endif
                }
                else
                {
                    Debug.WriteLine("Loaded ad from " + networkName);
#if VERBOSE_LOG
                    m_reportManager.ReportEvent("AD_LOAD", "");
#endif
                }

            }
            finally
            {
                m_context.OnLoadedAd(this, networkName);
            }
        }

        internal void AdError(AdWrapper wrapper, string error)
        {
            string networkName = "Unknown network";
            try
            {
                m_adShown = false;
                m_adLoaded = false;

                if (wrapper != null)
                {
                    networkName = wrapper.Name;
                    wrapper.LastError = error;
                    wrapper.FailCount++;
                    Debug.WriteLine("Failed to load ad from {0} [{1}/{2}/{3}/{4}], error {5}", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error);
                    m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} [{1}/{2}/{3}/{4}] '{5}'", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
                }
                else
                {
                    Debug.WriteLine("Failed to load ad from " + networkName + ", error " + error);
                    m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} '{1}'", networkName, error));
                }

            }
            finally
            {
                if (m_context.OnFailedAd(this, networkName, error))
                    LoadAd();
            }
        }

        internal void AdClicked(AdWrapper wrapper)
        {
            string networkName = "Unknown network";
            try
            {
                if (wrapper != null)
                {
                    networkName = wrapper.Name;
                    wrapper.ClickCount++;
                    Debug.WriteLine("Clicked ad from {0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount);
                    m_reportManager.ReportEvent("AD_CLICK", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
                }
                else
                {
                    Debug.WriteLine("Clicked ad from " + networkName);
                    m_reportManager.ReportEvent("AD_CLICK", networkName);
                }
            }
            finally
            {
                m_context.OnClickedAd(this, networkName);
            }
        }

        internal void AdShown(AdWrapper wrapper, object view)
        {
            string networkName = "Unknown network";
            try
            {
                m_adShown = true;

                if (wrapper != null)
                {
                    networkName = wrapper.Name;
                    wrapper.ShowCount++;
                    Debug.WriteLine("Shown ad from {0} [{1}/{2}/{3}/{4}/{5}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, wrapper.ShowCount);
#if VERBOSE_LOG
                    m_reportManager.ReportEvent("AD_SHOW", string.Format("{0} [{1}/{2}/{3}/{4}/{5}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, wrapper.ShowCount));
#endif                
                }
                else
                {
                    Debug.WriteLine("Shown ad from " + networkName);
#if VERBOSE_LOG
                    m_reportManager.ReportEvent("AD_SHOW", networkName);
#endif
                }
            }
            finally
            {
                m_context.OnShownAd(this, networkName, view);
            }
        }
    }
}

