using System;

namespace Apps4Bro.Networks
{
    internal class DummyNetwork : AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private object m_data;

        public string Network
        {
            get { return "Dummy"; }
        }

        public DummyNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            return true;
        }

        public void Hide()
        {
            
        }

        public void Display()
        {
            
        }

        public void SetId(string id)
        {
            m_id = id;
        }

    }
}