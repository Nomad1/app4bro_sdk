using System;

namespace Apps4Bro
{
    public class ApplicationException : Exception
    {
        public ApplicationException(string text)
            : base(text)
        {
        }

        public ApplicationException(string text, Exception ex)
            : base(text, ex)
        {
        }
    }
}
