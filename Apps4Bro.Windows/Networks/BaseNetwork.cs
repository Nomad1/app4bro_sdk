namespace Apps4Bro.Networks
{
    internal abstract class BaseNetwork : AdNetworkHandler
    {
        protected readonly AdManager m_adManager;

        protected string m_appId;
        protected string m_unitId;
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
                string[] nsplit = unitId.Split(';');
                appId = nsplit[0];
                unitId = nsplit[1];
            }
            else
                appId = id;

            m_appId = appId;
            m_unitId = unitId;
        }
    }
}