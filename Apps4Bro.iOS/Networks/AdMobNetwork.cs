#if USE_ADMOB && !OLD_ADMOB
using System;
using Foundation;
using Google.MobileAds;

namespace Apps4Bro.Networks
{
    internal class AdMobNetwork : FullScreenContentDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private InterstitialAd m_gadInterstitial;
        private object m_data;
        private bool m_nonPersonalizedAds;

        public string Network
        {
            get { return "AdMob"; }
        }


        public AdMobNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();

            Request request = Request.GetDefaultRequest();
            if (m_nonPersonalizedAds || Apps4BroSDK.UseNonPersonalizedAds)
            {
                var extras = new Extras();
                extras.AdditionalParameters = NSDictionary.FromObjectAndKey(new NSString("1"), new NSString("npa"));
                request.RegisterAdNetworkExtras(extras);

            }
            InterstitialAd.Load(m_id, request, delegate (InterstitialAd interstitialAd, NSError error)
            {
                if (error != null)
                    m_adManager.AdError(m_data, "Error " + error.Code + ": " + error.Description);
                else
                {
                    m_gadInterstitial = interstitialAd;
                    m_gadInterstitial.Delegate = this;

                    m_adManager.AdLoaded(m_data);
                }

            });

            return true;
        }

        public void Hide()
        {
            if (m_gadInterstitial != null)
            {
                m_gadInterstitial.Delegate = null;
                m_gadInterstitial.Dispose();
                m_gadInterstitial = null;
            }
        }

        public void Display()
        {
            m_gadInterstitial.Present(UIKit.UIApplication.SharedApplication.Windows[0].RootViewController);
            // Nomad: I'm changing this one to use topmost view controller to avoid skipping ads with settings window, etc.

            //m_gadInterstitial.Present((UIKit.UIViewController)m_adManager.Context);
        }

        public void SetId(string id)
        {
            string[] split = id.Split(';');
            m_id = split[0];
            m_nonPersonalizedAds = split.Length > 1 && split[1].ToLower() == "true";
        }

        public override void DidPresentFullScreenContent(FullScreenPresentingAd ad)
        {
            m_adManager.AdShown(m_data, ad);
        }

        public override void DidFailToPresentFullScreenContent(FullScreenPresentingAd ad, NSError error)
        {
            m_adManager.AdError(m_data, "Error " + error.Code + ": " + error.Description);
        }

        public override void DidRecordClick(FullScreenPresentingAd ad)
        {
            m_adManager.AdClicked(m_data);
        }
    }
}
#endif