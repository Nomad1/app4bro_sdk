namespace Apps4Bro.Networks
{
    internal class DummyNetwork : BaseNetwork
    {
        public override string Network
        {
            get { return "Dummy"; }
        }

        public DummyNetwork(AdManager manager)
            : base(manager)
        {
        }
       
        public override void Hide()
        {

        }

        public override void Display()
        {

        }
    }
}