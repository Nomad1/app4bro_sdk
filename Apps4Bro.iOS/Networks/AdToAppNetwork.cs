#if USE_ADTOAPP
using System;
using AdToAppBinding;
using Foundation;

namespace Apps4Bro.Networks
{
    internal class AdToAppNetwork : AdToAppSDKDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private object m_data;

        public string Network
        {
            get { return "AdToApp"; }
        }

        public AdToAppNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;
            
            AdToAppSDK.SetDelegate (this);

            NSString[] modules = {
                //new NSString (AdToAppContentType.IMAGE),
                //new NSString (AdToAppContentType.VIDEO), 
                new NSString (AdToAppContentType.INTERSTITIAL), 
                //new NSString (AdToAppContentType.BANNER),
                //new NSString (AdToAppContentType.REWARDED)
            };
#if DEBUG
            AdToAppSDK.EnableDebugLogs ();
#endif
           // AdToAppSDK.EnableTestMode ();

            AdToAppSDK.StartWithAppId (m_id, modules);

            AdToAppSDK.ShowInterstitial (AdToAppContentType.INTERSTITIAL);
            
            return true;
        }

        public void Hide()
        {
            AdToAppSDK.HideInterstitial();
        }

        public void SetId(string id)
        {
            m_id = id;
        }
        
        public override void OnAdDidDisappear (string adType)
        {
            Console.WriteLine ("OnAdDidDisappear:" + adType);
        }

        public override void OnAdWillAppear (string adType)
        {
            Console.WriteLine ("OnAdWillAppear:" + adType);
            m_adManager.AdLoaded(m_data);
        }

        public override void OnFirstAdLoaded (string adType)
        {
            Console.WriteLine ("OnFirstAdLoaded:" + adType);
        }

        public override void OnReward (int reward, string gameCurrency)
        {
            Console.WriteLine ("OnReward:" + reward + " " + gameCurrency);
        }

        public override void OnAdClicked (string adType)
        {
            Console.WriteLine ("OnAdClicked:" + adType);
            m_adManager.AdClick(m_data);
        }

        public override bool ShouldShowAd (string adType)
        {
            Console.WriteLine ("ShouldShowAd:" + adType);

            return true;
        }

        public override void OnAdFailedToAppear (string adType)
        {
            Console.WriteLine ("OnAdFailedToAppear:" + adType);
            m_adManager.AdError(m_data, "AdError for " + adType);
        }
    }
}
#endif