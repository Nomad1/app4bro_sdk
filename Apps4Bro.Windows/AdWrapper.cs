namespace Apps4Bro
{
    /// <summary>
    /// Ad instance. Also contains stat data
    /// </summary>
    public class AdWrapper
    {
        private readonly AdNetworkHandler m_adNetworkHandler;
        private readonly string m_id;
        private readonly string m_name;

        public int SuccessCount { get; set; }
        public int FailCount { get; set; }
        public int CallCount { get; set; }
        public int ClickCount { get; set; }
        public int ShowCount { get; set; }
        public string LastError { get; set; }

        public AdNetworkHandler AdNetworkHandler
        {
            get { return m_adNetworkHandler; }
        }

        public string Id
        {
            get { return m_id; }
        }

        public string Name
        {
            get { return m_name; }
        }

        public AdWrapper(AdNetworkHandler handler, string id, string name)
        {
            m_adNetworkHandler = handler;
            m_id = id;
            m_name = name;
        }
    }
}
