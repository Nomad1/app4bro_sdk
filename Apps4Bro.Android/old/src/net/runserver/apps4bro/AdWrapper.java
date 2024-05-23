package net.runserver.apps4bro;

public class AdWrapper
{
    private final AdNetworkHandler m_adNetworkHandler;
    private final String m_id;
    private final String m_name;

    public int SuccessCount;
    public int FailCount;
    public int CallCount;
    public int ClickCount;
    public String LastError;
    public AdNetworkHandler.AdObject AdRequest;

    public AdNetworkHandler getNetworkHandler()
    {
        return m_adNetworkHandler;
    }

    public String getId()
    {
        return m_id;
    }

    public String getName()
    {
        return m_name;
    }

    public AdWrapper(AdNetworkHandler handler, String id, String name)
    {
        m_adNetworkHandler = handler;
        m_id = id;
        m_name = name;
    }
}
