package net.runserver.apps4bro;

import android.app.Activity;

public interface AdNetworkHandler
{
    String getNetwork();

    AdObject request(final AdManager manager, String id, Object data);

    interface AdObject
    {
        AdEnums.AdState getState();

        boolean show(Activity context);

        void hide();
    }
}
