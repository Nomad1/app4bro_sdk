#if USE_VUNGLE

#define VUNGLE_DEBUG

using System;
using System.Diagnostics;
using VungleSDK;

namespace Apps4Bro.Networks
{
    internal class VungleInterNetwork : BaseNetwork
    {
		private VungleAd m_sdkInstance;
		private bool m_showing;

		public override string Network
        {
            get { return "VungleInter"; }
        }

        public VungleInterNetwork(AdManager manager)
            : base(manager)
        {
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

						m_sdkInstance = AdFactory.GetInstance(m_appId);
						m_sdkInstance.OnInitCompleted += SdkInstance_OnInitCompleted;
						m_sdkInstance.OnAdPlayableChanged += SdkInstance_OnAdPlayableChanged;
						m_sdkInstance.OnAdEnd += SdkInstance_OnAdEnd;
						m_sdkInstance.OnAdStart += SdkInstance_OnAdStart;
#if VUNGLE_DEBUG
						m_sdkInstance.Diagnostic += SdkInstance_Diagnostic;
#endif
						break;

					case AdNetworkInitStatus.Inited:
						m_adManager.RunOnUiThread(() => m_sdkInstance.LoadAd(m_unitId));
						break;
				}
			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to init Vungle advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("VUNGLE_EXCEPTION", ex.ToString());
				m_sdkInstance = null;
				m_inited = AdNetworkInitStatus.Error;
			}

			return true;
        }

		private void SdkInstance_Diagnostic(object sender, DiagnosticLogEvent e)
		{
			Debug.WriteLine("Vungle diag: " + e.Message);
		}

		private void SdkInstance_OnAdStart(object sender, AdEventArgs e)
		{
			m_adManager.RunOnUiThread(() => m_adManager.AdShown(m_wrapper, null));
		}

		private void SdkInstance_OnAdEnd(object sender, AdEndEventArgs e)
		{
			Debug.WriteLine("OnVideoEnd(" + e.Id + "): "
					+ "\n\tPlacement: " + e.Placement
					+ "\n\tIsCompletedView: " + e.IsCompletedView
					+ "\n\tCallToActionClicked: " + e.CallToActionClicked
					+ "\n\tWatchedDuration: " + e.WatchedDuration);
			m_adManager.RunOnUiThread(()=>m_adManager.AdClicked(m_wrapper));
		}

		private void SdkInstance_OnInitCompleted(object sender, ConfigEventArgs arg)
		{
			m_sdkInstance.OnInitCompleted -= SdkInstance_OnInitCompleted;

			if (m_sdkInstance != null && arg.Initialized && arg.Placements.Length > 0)
			{
				string id = arg.Placements[0].ReferenceId;
				m_inited = AdNetworkInitStatus.Inited;
				if (arg.Placements[0].IsHeaderBidding)
					m_sdkInstance.LoadMediatedAd(id, ""); // wont work but causes an error so other networks could continue
				else
					m_sdkInstance.LoadAd(id);
			}
			else
			{
				m_inited = AdNetworkInitStatus.Error;

				Debug.WriteLine("Failed to use Vungle advertising: " + arg.ErrorMessage);
				m_adManager.ReportManager.ReportEvent("VUNGLE_ERROR", arg.ErrorMessage);

				Hide();
			}
		}

		private void SdkInstance_OnAdPlayableChanged(object sender, AdPlayableEventArgs e)
		{
			if (e.AdPlayable)
			{
				m_adManager.RunOnUiThread(ShowInterstitial);
				m_adManager.AdLoaded(m_wrapper);
				m_showing = true;
			}
			else
			if (!m_showing)
				m_adManager.AdError(m_wrapper, "No playable ads");
		}

		private async void ShowInterstitial()
		{
			try
			{
				if (m_sdkInstance != null)
				{
					AdConfig adConfig = new AdConfig();

					adConfig.Orientation = DisplayOrientations.Landscape;
					adConfig.SoundEnabled = false;
					await m_sdkInstance.PlayAdAsync(adConfig, m_unitId);
				}
			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to show Vungle advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("VUNGLE_ERROR", ex.ToString());
				m_sdkInstance = null;
				m_inited = AdNetworkInitStatus.Error;
			}
		}


        public override void Hide()
        {
			m_showing = false;
		}

        public override void Display()
        {
           
        }
    }
}
#endif