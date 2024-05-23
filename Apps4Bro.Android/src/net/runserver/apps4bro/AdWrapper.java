package net.runserver.apps4bro;

import android.app.Activity;

public class AdWrapper
{
    private final AdNetworkHandler m_adNetworkHandler;
    private final String m_id;
    private final String m_name;
    private AdNetworkHandler.AdObject m_adRequest;

    public int SuccessCount;
    public int FailCount;
    public int CallCount;
    public int ClickCount;
    public String LastError;

    public String getId()
    {
        return m_id;
    }

    public String getName()
    {
        return m_name;
    }

    public AdEnums.AdState getRequestState()
    {
        return m_adRequest == null ? AdEnums.AdState.None : m_adRequest.getState();
    }

    public AdWrapper(AdNetworkHandler handler, String id, String name)
    {
        m_adNetworkHandler = handler;
        m_id = id;
        m_name = name;
    }

    public void request(final AdManager manager)
    {
        clear();

        CallCount ++;
        m_adRequest = m_adNetworkHandler.request(manager, m_id, this);
    }

    public void clear()
    {
        if (m_adRequest != null)
        {
            m_adRequest.hide();
            m_adRequest = null;
        }
    }

    public boolean show(Activity context)
    {
        if (m_adRequest != null && m_adRequest.getState() == AdEnums.AdState.Ready)
            return m_adRequest.show(context);
        return false;
    }
}
