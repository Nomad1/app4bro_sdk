package net.runserver.apps4bro;

import android.app.Activity;

public interface AdNetworkHandler
{
    String getNetwork();

    /** One of {@link AdEnums.NetworkType} values declaring what category of ad this handler serves. */
    int getType();

    AdObject request(final AdManager manager, String id, Object data);

    interface AdObject
    {
        AdEnums.AdState getState();

        boolean show(Activity context);

        void hide();
    }
}
