package net.runserver.apps4bro;

import android.app.Activity;

class DummyNetwork implements AdNetworkHandler
{
    private final static String TAG = "Dummy";

    public String getNetwork()
    {
        return TAG;
    }

    public DummyNetwork()
    {
    }

    public AdObject request(AdManager manager, final String id, final Object data)
    {
        return new DummyAd(id, data);
    }

    class DummyAd implements AdObject
    {
        private final String m_id;
        private final Object m_data;
        private AdEnums.AdState m_state;

        public String getId()
        {
            return m_id;
        }

        public AdEnums.AdState getState()
        {
            return m_state;
        }

        public DummyAd(String id, Object data)
        {
            m_id = id;
            m_data = data;
            m_state = AdEnums.AdState.Loading;
        }

        public boolean show(Activity context)
        {
            m_state = AdEnums.AdState.Shown; // consume ad

            return true;
        }

        public void hide()
        {
            m_state = AdEnums.AdState.Closed;
        }
    }
}