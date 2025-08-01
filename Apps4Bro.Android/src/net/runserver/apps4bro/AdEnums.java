package net.runserver.apps4bro;

public interface AdEnums
{
    enum AdManagerState
    {
        NotInited, // not yet initialized. init() is needed
        Initialized, // initialized and ready to call loadAds()
        Loading, // loadAds() called. Results in Ready or NoAds
        Ready, // ads are loaded and ready to display
        NoAds, // no ads available
        Showing, // ad is ready to be shown
        Finished; // ads were shown and then closed or clicked. Need to load new ones

        public static final AdManagerState values[] = values();
    }

    enum AdManagerError
    {
        None, // everything is fine
        FailedToInit, // errors in configuration, data from server, exceptions, etc.
        FailedToRequest, // error while calling requestAd
        NoAds, // no ads in any of existing providers
    }


    enum AdState
    {
        None, // not inited
        Loading, // in progress
        Failed, // error loading ad
        Ready, // ad is ready to be shown
        Closed, // was shown but now closed by click or close button
        Shown // showing right now
    }
}