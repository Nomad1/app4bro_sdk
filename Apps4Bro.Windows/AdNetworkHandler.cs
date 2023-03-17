namespace Apps4Bro
{
    public interface AdNetworkHandler
    {
        /// <summary>
        /// Network name
        /// </summary>
        string Network { get; }

        /// <summary>
        /// Shows the banner or interstitial
        /// </summary>
        /// <param name="data">Parameter usually containing parent control sepcific for this OS</param>
        /// <returns></returns>
        bool Show(AdWrapper wrapper, object data);

        void Hide();

        void Display();

        void SetId(string id);

    }
}
