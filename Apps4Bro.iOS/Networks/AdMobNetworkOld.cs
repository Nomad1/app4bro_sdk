#if USE_ADMOB && OLD_ADMOB
using Foundation;
using Google.MobileAds;

namespace Apps4Bro.Networks
{
    internal class AdMobNetwork : InterstitialDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private Interstitial m_gadInterstitial;
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

            m_gadInterstitial = new Interstitial(m_id);
            m_gadInterstitial.Delegate = this;
            Request request = Request.GetDefaultRequest();
            if (m_nonPersonalizedAds || Apps4BroSDK.UseNonPersonalizedAds)
            {
                var extras = new Extras();
                extras.AdditionalParameters = NSDictionary.FromObjectAndKey(new NSString("1"), new NSString("npa"));
                request.RegisterAdNetworkExtras(extras);

            }
            m_gadInterstitial.LoadRequest(request);
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

        public override void DidFailToReceiveAd(Interstitial sender, RequestError error)
        {
            if (error.Code == 9) // no fill
                m_adManager.AdError(m_data, "Error " + error.Code);
            else
                m_adManager.AdError(m_data, "Error " + error.Code + ": " + error.Description);
        }

        public override void DidReceiveAd(Interstitial ad)
        {
            //Console.WriteLine("AdMob received interstitial banner");
            m_adManager.AdLoaded(m_data);
        }

        public override void WillPresentScreen(Interstitial ad)
        {
            m_adManager.AdShown(m_data, ad);
        }

        public override void WillLeaveApplication(Interstitial ad)
        {
            m_adManager.AdClicked(m_data);
        }
    }
}
#endif