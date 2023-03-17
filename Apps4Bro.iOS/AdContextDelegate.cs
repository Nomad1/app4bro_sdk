namespace Apps4Bro
{
    /// <summary>
    /// Delegate with callbacks for ad manager interaction
    /// </summary>
    public interface AdContextDelegate
    {
        /// <summary>
        /// Callback function signalling that Apps4Bro system is ready to work. Call AdManager.LoadAd after this callback
        /// </summary>
        /// <param name="manager">Ad Manager</param>
        void OnInited(AdManager manager);

        /// <summary>
        /// Callback raised when ad failed to load
        /// </summary>
        /// <param name="manager">Ad Manager</param>
        /// <param name="network">Network name</param>
        /// <param name="error">Error text</param>
        bool OnFailedAd(AdManager manager, string network, string error);

        /// <summary>
        /// Callback raised when ad is loaded
        /// </summary>
        /// <param name="manager">Ad Manager</param>
        /// <param name="network">Network name</param>
        void OnLoadedAd(AdManager manager, string network);

        /// <summary>
        /// Callback raised when ad is shown
        /// </summary>
        /// <param name="manager">Ad Manager</param>
        /// <param name="network">Network name</param>
        void OnShownAd(AdManager manager, string network, object view);

        /// <summary>
        /// Callback raised when user clicks on ad
        /// </summary>
        /// <param name="manager">Ad Manager</param>
        /// <param name="network">Network name</param>
        void OnClickedAd(AdManager manager, string network);
    }
}

