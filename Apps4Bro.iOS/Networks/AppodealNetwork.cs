#if USE_APPODEAL
using System;
using AppodealBinding;

namespace Apps4Bro.Networks
{
    internal class AppodealNetwork : AppodealInterstitialDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private object m_data;

        public string Network
        {
            get { return "Appodeal"; }
        }

        public AppodealNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Appodeal.InitializeWithApiKey(m_id, AppodealAdType.Interstitial);
            Appodeal.SetInterstitialDelegate(this);
            Appodeal.SetAutocache(false, AppodealAdType.Interstitial);
            //Appodeal.SetDebugEnabled(false);
            //Appodeal.DisableNetworkForAdType(AppodealAdType.All, NetworkNames.kAppodealAdMobNetworkName);
            //Appodeal.DisableNetworkForAdType(AppodealAdType.All, "chartboost");
            //Appodeal.DisableNetworkForAdType(AppodealAdType.All, "unity_ads");
            Appodeal.CacheAd(AppodealAdType.Interstitial);
            
            return true;
        }
        
        public void Hide()
        {
            Appodeal.HideBanner();
        }

        public void SetId(string id)
        {
            m_id = id;
        }

        //public override void InterstitialDidLoadAd()
        //{
        //    Console.WriteLine("Appodeal received interstitial banner");   
           
        //}
        public override void InterstitialDidFailToLoadAd()
        {
            m_adManager.AdError(m_data, "AdError");
        }
        public override void InterstitialWillPresent()
        {
            m_adManager.AdLoaded(m_data);
        }
        
        public override void InterstitialDidDismiss()
        {
            
        }
        
        public override void InterstitialDidClick()
        {
            m_adManager.AdClicked(m_data);
        }

        public void Display()
        {
            Appodeal.ShowAd(AppodealShowStyle.Interstitial, (UIKit.UIViewController)m_adManager.Context);
        }
    }
}
#endif