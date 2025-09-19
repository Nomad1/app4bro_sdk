#if USE_PUBFINITY2
using System;
using System.Diagnostics;
using Windows.UI.Xaml.Controls;

using Pubfinity.AdsSdk.Uwp;
using Pubfinity.AdsSdk.Uwp.AdUnits;
using Pubfinity.AdsSdk.Uwp.Events;
using ConsentDesk.CmpSdk.Uwp;

namespace Apps4Bro.Networks
{
    internal class PubfinityInterNetwork2 : BaseNetwork
    {
		private static bool s_inited = false;

		private InterstitialAd m_pubfinityAdControl;

        public override string Network
        {
            get { return "PubfinityInter"; }
        }

        public PubfinityInterNetwork2(AdManager manager)
            : base(manager)
        {
            if (s_inited)
                throw new ApplicationException("Only one PubfinityInterNetwork2 wrapper is allowed!");
            s_inited = true;
        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            try
            {
                switch (m_inited)
                {
                    case AdNetworkInitStatus.Unknown:
                        m_inited = AdNetworkInitStatus.Initializing;
                        PubfinityAdsConfig pubfinityInitParams = new PubfinityAdsConfig();
                        pubfinityInitParams.Privacy = new PrivacyConfig();
                        pubfinityInitParams.Privacy.IsCmpPresent = m_allIds.Length >= 3;
                        pubfinityInitParams.Storage = new StorageConfig();

                        if (m_allIds.Length >= 3)
                            ConsentDeskCmp.Initialize(m_allIds[2]);

                        PubfinityAds.OnSdkInitializationCompleted += onPubfinity_SDKStatus;
                        PubfinityAds.Initialize(m_appId, pubfinityInitParams);
                        break;

                    case AdNetworkInitStatus.Inited:
                        m_adManager.RunOnUiThread(() => ShowInterstitial());
                        break;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init Pubfinity advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
                m_pubfinityAdControl = null;
                m_inited = AdNetworkInitStatus.Error;
            }

            return true;
        }

        private void ShowInterstitial()
        {
			try
			{
				if (m_pubfinityAdControl == null)
                {
                    InterstitialAd adControl = new InterstitialAd(m_unitId);

                    adControl.AdClicked += onPubfinity_AdClickEvent;
                    adControl.AdError += onPubfinity_AdErrorEvent;
					adControl.AdLoaded += onPubfinity_AdLoadedEvent;

                    m_pubfinityAdControl = adControl;
                }

				m_pubfinityAdControl.Load();
			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to show Pubfinity advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
				m_pubfinityAdControl = null;
			}
		}

		private void onPubfinity_AdErrorEvent(object sender, BaseAdEventArgs e)
        {
            if (e is AdErrorEventArgs)
				m_adManager.AdError(m_wrapper, ((AdErrorEventArgs)e).ErrorMessage);
            else
			    m_adManager.AdError(m_wrapper, e.ToString());
        }

        private void onPubfinity_AdClickEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        private void onPubfinity_AdLoadedEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        private void onPubfinity_SDKStatus(object sender, SdkInitStatusEventArgs arg)
        {
            PubfinityAds.OnSdkInitializationCompleted -= onPubfinity_SDKStatus;

            if (!arg.Initialized)
            {
				m_inited = AdNetworkInitStatus.Error;

				Debug.WriteLine("Failed to use Pubfinity advertising: " + arg.ErrorMessage);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_ERROR", arg.ErrorMessage);
                Hide();
            }
            else
            {
				m_inited = AdNetworkInitStatus.Inited;
				m_adManager.RunOnUiThread(() => ShowInterstitial());
            }
        }

		public override void Hide()
        {
            m_adManager.RunOnUiThread(() =>
            {
                if (m_pubfinityAdControl != null)
                {                   
                    m_pubfinityAdControl.Reset();
                    m_pubfinityAdControl = null;
                }
            });
        }

        public override void Display()
        {
            m_adManager.RunOnUiThread(() =>
            {
                if (m_pubfinityAdControl != null && m_pubfinityAdControl.IsReadyToShow())
                    m_pubfinityAdControl.Show();
            });
        }
    }
}
#endif