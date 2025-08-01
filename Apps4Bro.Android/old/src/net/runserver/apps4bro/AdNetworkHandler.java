package net.runserver.apps4bro;

public interface AdNetworkHandler
{
    String getNetwork();

    AdObject request(String id, Object data);

    enum AdState
    {
        Loading, // in progress
        Failed, // error loading ad
        Ready, // ad is ready to be shown
        Closed, // was shown but now closed by click or close button
        Used // showing right now
    }

    interface AdObject
    {
        AdState getState();

        boolean show();

        void hide();
    }
}
