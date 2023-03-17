using System;
using System.Collections.Concurrent;
using System.Threading;
using System.Net;

#if __IOS__
using Foundation;
#endif

namespace Apps4Bro
{
    public class ReportManager
    {
        private class EventData
        {
            public readonly string Event;
            public readonly string Parameter;
            public readonly DateTime Time;
            public readonly int ID;
            
            public EventData(string nevent, string nparam, DateTime time, int eventId)
            {
                Event = nevent;
                Parameter = nparam;
                Time = time;
                ID = eventId;
            }
        }
        
        private static readonly long s_unixTicks = (new DateTime(1970, 1, 1)).Ticks;
        
        private readonly string m_appId;
        
        private readonly ConcurrentQueue<EventData> m_events;
        private readonly ConcurrentDictionary<int, EventData> m_pendingEvents;
        private int m_eventCounter;
        
        private Thread m_sendThread;
        
        private int m_sending;
        private bool m_terminating;
        
        public ReportManager(string applicationId)
        {
            m_appId = WebUtility.UrlEncode(applicationId);
            m_events = new ConcurrentQueue<EventData>();
            m_pendingEvents = new ConcurrentDictionary<int, EventData>();
            m_eventCounter = 0;
        }

        ~ReportManager()
        {
            Thread sendThread = m_sendThread;
            if (sendThread != null && sendThread.IsAlive)
            {
                m_terminating = true;
                sendThread.Join();
            }
            m_sendThread = null;

            if (m_events.Count > 0)
                Console.Error.WriteLine("Disposing report manager with {0} events in queue", m_events.Count);

            if (m_pendingEvents.Count > 0)
                Console.Error.WriteLine("Disposing report manager with {0} pending events in queue", m_pendingEvents.Count);
        }
        
        public void ReportEvent(string name, string param)
        {
            int id = Interlocked.Increment(ref m_eventCounter);

            m_events.Enqueue(new EventData(name, param, DateTime.UtcNow, id));

            StartSend();
        }
        
        private string FormatRequest(EventData nevent)
        {
            return string.Format(Apps4BroSDK.ReportUrl,
                Apps4BroSDK.AdvertisingId,
                m_appId,
                WebUtility.UrlEncode(nevent.Event),
                WebUtility.UrlEncode(nevent.Parameter.Replace('[','{').Replace(']','}')),
                (int)((nevent.Time.Ticks - s_unixTicks) / TimeSpan.TicksPerSecond),
                nevent.ID
            );
        }
        
        private void StartSend()
        {
            if (Interlocked.CompareExchange(ref m_sending, 1, 0) == 0)
            {
                m_sendThread = new Thread(SendThread);
                m_sendThread.Start();
            }
        }
        
        private void SendThread()
        {
            try
            {
                while (!m_terminating)
                {
                    EventData data = null;
#if __IOS__
                    NSOperationQueue queue = new NSOperationQueue();
#endif

                    if (m_events.TryDequeue(out data) && data != null)
                    {
                        m_pendingEvents[data.ID] = data;

#if !__IOS__
                        HttpWebRequest request = HttpWebRequest.Create(FormatRequest(data));
                        request.ProtocolVersion = HttpVersion.Version20;
                        request.UserAgent = "WinHTTP";
                        request.ContentType = "application/text";
                        request.Method = "GET";
                        request.Timeout = 5000;

                        //await TrySend(new Tuple<int, WebRequest>(data.ID, request));

                        request.BeginGetResponse(SendAsync, new Tuple<int, WebRequest>(data.ID, request));
#else
                        NSUrl nsurl = NSUrl.FromString(FormatRequest(data));

                        NSMutableUrlRequest request = new NSMutableUrlRequest(nsurl);
                        request.TimeoutInterval = 5.0;

                        int dataID = data.ID;

                        NSUrlConnection.SendAsynchronousRequest(request, queue, (response, ndata, error)=>
                        {
                            if (error != null)
                            {
                                Console.Error.WriteLine("Error sending data. Server returned response: {0}, error {1}", response, error);
                                ReturnEvent(dataID);
                                return;
                            }
                            RemoveEvent(dataID);
                        }
                        );
#endif
                    }

                    Thread.Sleep(100);
                }
            }
            catch(Exception ex)
            {
                Console.Error.WriteLine("Reporting thread failed with error: " + ex);
            }
            finally
            {
                m_sendThread = null;
                Interlocked.Exchange(ref m_sending, 0);
            }
        }

        //private async Task TrySend(Tuple<int, WebRequest> request)
        //{
        //    try
        //    {
        //        HttpWebResponse response = (HttpWebResponse)await request.Item2.GetResponseAsync();

        //        if (response == null || response.StatusCode != HttpStatusCode.OK)
        //        {
        //            Console.Error.WriteLine("Error sending data. Server returned status code: {0}", response == null ? HttpStatusCode.Unused : response.StatusCode);
        //            ReturnEvent(request.Item1);
        //        }
        //        RemoveEvent(request.Item1);
        //    }
        //    catch (WebException ex)
        //    {
        //        if (ex.Status != WebExceptionStatus.NameResolutionFailure)
        //        {
        //            Console.Error.WriteLine("Error in SendAsync {0}, request {1}", ex, request.Item2.ToString());
        //            ReturnEvent(request.Item1);
        //        }
        //    }
        //    catch (Exception ex)
        //    {
        //        Console.Error.WriteLine("Error in SendAsync {0}, request {1}", ex, request.Item2.ToString());
        //        ReturnEvent(request.Item1);
        //    }
        //}

        private void SendAsync(IAsyncResult result)
        {
            Tuple<int, WebRequest> request = (Tuple<int, WebRequest>)result.AsyncState;
            try
            {
                HttpWebResponse response = (HttpWebResponse)request.Item2.EndGetResponse(result);
                if (response == null || response.StatusCode != HttpStatusCode.OK)
                {
                    Console.Error.WriteLine("Error sending data. Server returned status code: {0}", response == null ? HttpStatusCode.Unused : response.StatusCode);
                    ReturnEvent(request.Item1);
                    return;
                }
                RemoveEvent(request.Item1);
            }
            catch(WebException ex)
            {
                if (ex.Status != WebExceptionStatus.NameResolutionFailure)
                {
                    Console.Error.WriteLine("Error in SendAsync {0}, request {1}", ex, request.Item2.ToString());
                    ReturnEvent(request.Item1);
                }
            }
            catch(Exception ex)
            {
                Console.Error.WriteLine("Error in SendAsync {0}", ex);
                ReturnEvent(request.Item1);
            }            
        }

        private void RemoveEvent(int id)
        {
            EventData data;
            if (!m_pendingEvents.TryRemove(id, out data))
                Console.Error.WriteLine("Error removing event {0}", id);
        }
        
        private void ReturnEvent(int id)
        {
            EventData data;
            if (!m_pendingEvents.TryRemove(id, out data))
            {
                Console.Error.WriteLine("Error returning event {0}", id);
            }
            else
                m_events.Enqueue(data);
        }
    }
}

