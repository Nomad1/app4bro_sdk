#if USE_VUNGLE
using System;
using System.Diagnostics;
using VungleSDK;
using VungleSDK.UI;
using Windows.System.Threading;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

namespace Apps4Bro.Networks
{
    internal class VungleNetwork : BaseNetwork
    {
        private VungleAdControl m_vungleAdControl;

        public override string Network
        {
            get { return "Vungle"; }
        }

        public VungleNetwork(AdManager manager)
            : base(manager)
        {
        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            try
            {
                Panel bannerGrid = data as Panel;

                if (bannerGrid == null)
                    return false;

                VungleAdControl adControl = new VungleAdControl();
                adControl.IsBannerAd = true;
                adControl.AppID = m_appId;
                adControl.Placement = m_unitId;
                adControl.Width = 728;
                adControl.Height = 90;
                adControl.SoundEnabled = false;

#if DEBUG
                adControl.Diagnostic += AdControl_Diagnostic;
                adControl.OnAdPlayableChanged += AdControl_OnAdPlayableChanged;
#endif

                adControl.OnAdStart += AdControl_OnAdStart;
                adControl.OnAdEnd += AdControl_OnAdEnd;
            

                //bannerGrid.Visibility = Visibility.Visible;
                //bannerGrid.Opacity = 1;
                bannerGrid.Children.Add(adControl);

                m_vungleAdControl = adControl;

                adControl.AutoRun = true;

                RunDelayed(AdControl_Check, 20);

                // calls loadbannerad
                //m_vungleAdControl.LoadBannerAd(m_unitId, VungleBannerSizes.BannerLeaderboard_728x90);

            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init Vungle advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("VUNGLE_ERROR", ex.ToString());
                m_vungleAdControl = null;
            }

            return true;
        }

        private void AdControl_Check()
        {
            if (m_vungleAdControl == null)
                return;

            if (!m_vungleAdControl.IsAdPlayable())
                RunDelayed(() => m_adManager.AdError(m_wrapper, "Ad timeout"));
            else
                RunDelayed(() => m_adManager.AdLoaded(m_wrapper));
        }

        private void AdControl_OnAdEnd(object sender, AdEndEventArgs e)
        {
            RunDelayed(() => m_adManager.AdClicked(m_wrapper));
        }

        private void AdControl_OnAdStart(object sender, AdEventArgs e)
        {
            RunDelayed(() => m_adManager.AdShown(m_wrapper, m_vungleAdControl));
        }

        private void AdControl_OnAdPlayableChanged(object sender, AdPlayableEventArgs e)
        {
            // if (!e.AdPlayable)
            //   RunDelayed(() => m_adManager.AdError(m_wrapper, "Ad not playable"));
            //else
            //  RunDelayed(() => m_adManager.AdLoaded(m_wrapper));
        }

        private void AdControl_Diagnostic(object sender, DiagnosticLogEvent e)
        {
            Debug.WriteLine("Vungle diag: " + e.Message);
        }

        private void RunDelayed(Action action, int seconds = 0)
        {
            Panel bannerGrid = m_data as Panel;

            if (bannerGrid == null)
                return;


            ThreadPoolTimer DelayTimer = ThreadPoolTimer.CreateTimer(
               async (source) =>
               {
                   await bannerGrid.Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () => action());

               }, TimeSpan.FromMilliseconds(seconds * 1000 + 1));
        }

        public override void Hide()
        {
            if (m_vungleAdControl != null)
            {
                m_vungleAdControl.StopBannerAd();
                m_vungleAdControl = null;
            }

            Panel bannerGrid = m_data as Panel;
            if (bannerGrid != null)
                bannerGrid.Children.Clear();
        }

        public override void Display()
        {
            if (m_vungleAdControl != null)
            {
               // m_vungleAdControl.PlayBannerAd(m_unitId, VungleBannerSizes.BannerLeaderboard_728x90);

                m_vungleAdControl.Visibility = Visibility.Visible;
                m_vungleAdControl.Opacity = 1;
            }
        }
    }
}
#endif