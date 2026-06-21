package net.runserver.apps4bro;

public interface AdEnums
{
    enum AdManagerState
    {
        NotInited, // not yet initialized. init() is needed
        Initialized, // initialized and ready to call loadAds()
        Loading, // loadAds() called. Results in Ready or NoAds
        Ready, // ads are loaded and ready to display
        NoAds, // cascade exhausted, nothing to show
        Showing, // ad is ready to be shown
        Finished; // ads were shown and then closed or clicked. Need to load new ones

        public static final AdManagerState values[] = values();
    }

    enum AdManagerError
    {
        None,
        FailedToInit,    // App4Bro server down / no config / init exception
        FailedToRequest, // exception thrown during requestAds orchestration
        FailedToLoad,    // network returned a generic load error
        NoFill,          // network has no ad to serve
        NoNetwork,       // no internet connectivity
        FailedToShow,    // show() was called but the ad never displayed
        NoAds,           // cascade exhausted, no ad available this session
    }

    enum CloseReason
    {
        Dismissed, // user closed the ad
        Clicked,   // user clicked through (app went to background)
    }

    enum AdState
    {
        None, // not inited
        Loading, // in progress
        Failed, // error loading or showing ad
        Ready, // ad is ready to be shown
        Closed, // was shown but now closed by click or close button
        Shown // showing right now
    }

    /**
     * Bitmask of ad network categories. A network handler declares one of these
     * via {@link AdNetworkHandler#getType()}; {@link AdManager#loadAds(int)}
     * filters the cascade so only matching networks are tried.
     */
    interface NetworkType
    {
        int Banner       = 1;
        int Interstitial = 2;
        int AppOpen      = 4;
        int All          = Banner | Interstitial | AppOpen;
    }
}
