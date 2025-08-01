namespace Apps4Bro.Networks
{
    internal abstract class BaseNetwork : AdNetworkHandler
    {
        protected readonly AdManager m_adManager;

        protected string m_appId;
        protected string m_unitId;
		protected string [] m_allIds;
		protected AdWrapper m_wrapper;
        protected object m_data;

        public abstract string Network
        {
            get;
        }


        public BaseNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public virtual bool Show(AdWrapper wrapper, object data)
        {
            m_wrapper = wrapper;
            m_data = data;

            Hide();

            return true;
        }

        public abstract void Hide();

        public abstract void Display();

        public void SetId(string id)
        {
            string unitId = id;
            string appId;
            if (unitId.Contains(";"))
            {
				m_allIds = unitId.Split(';');
                appId = m_allIds[0];
                unitId = m_allIds[1];
            }
            else
                appId = id;

            m_appId = appId;
            m_unitId = unitId;
        }
    }
}