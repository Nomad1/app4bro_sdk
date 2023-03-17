using System;
using System.Collections.Generic;
using System.Threading;
using System.Net;
using System.Diagnostics;
using System.Threading.Tasks;
using System.Collections.Concurrent;

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
        private readonly LinkedList<EventData> m_pendingEvents;
        private readonly object m_syncRoot;
        private int m_eventCounter;

#if US_THREADS
        private Thread m_sendThread;
#endif

        private int m_sending;
        private bool m_terminating;

        public ReportManager(string applicationId)
        {
            m_appId = WebUtility.UrlEncode(applicationId);
            m_events = new ConcurrentQueue<EventData>();
            m_pendingEvents = new LinkedList<EventData>();
            m_syncRoot = new object();
            m_eventCounter = 0;
        }

        ~ReportManager()
        {
            m_terminating = true;
#if USE_THREADS

            Thread sendThread = m_sendThread;
            if (sendThread != null && sendThread.IsAlive)
            {
                sendThread.Join();
            }
            m_sendThread = null;
#endif

            lock (m_syncRoot)
            {
                if (m_events.Count > 0)
                    Debug.WriteLine("Disposing report manager with {0} events in queue", m_events.Count);

                if (m_pendingEvents.Count > 0)
                    Debug.WriteLine("Disposing report manager with {0} pending events in queue", m_pendingEvents.Count);
            }
        }

        public void ReportEvent(string name, string param)
        {
            m_events.Enqueue(new EventData(name, param, DateTime.UtcNow, ++m_eventCounter));

            StartSend();
        }

        private string FormatRequest(EventData nevent)
        {
            return string.Format(Apps4BroSDK.ReportUrl,
                Apps4BroSDK.AdvertisingId,
                m_appId,
                WebUtility.UrlEncode(nevent.Event),
                WebUtility.UrlEncode(nevent.Parameter),
                (int)((nevent.Time.Ticks - s_unixTicks) / TimeSpan.TicksPerSecond),
                nevent.ID
            );
        }

        private void StartSend()
        {
            if (Interlocked.CompareExchange(ref m_sending, 1, 0) == 0)
            {
#if USE_THREADS
                m_sendThread = new Thread(SendThread);
                m_sendThread.Start();
#else
                Task task = new Task(SendThread);
                task.Start();
#endif
            }
        }

        private void SendThread()
        {
            try
            {
                while (!m_terminating)
                {
                    EventData data;
                        
                    if (!m_events.TryDequeue(out data))
                    {
                        Debug.WriteLine("No events to send");
                        break;
                    }

                    lock(m_syncRoot)
                        m_pendingEvents.AddFirst(data);

                    WebRequest request = HttpWebRequest.Create(FormatRequest(data));
                    request.ContentType = "application/text";
                    request.Method = "GET";
#if !NETFX_CORE
                    request.Timeout = 5000;
#endif
                    request.BeginGetResponse(SendAsync, new KeyValuePair<int, WebRequest>(data.ID, request));
                }

            }
            catch (Exception ex)
            {
                Debug.WriteLine("Reporting thread failed with error: " + ex);
            }
            finally
            {
#if USE_THREADS
                m_sendThread = null;
#endif
                Interlocked.Exchange(ref m_sending, 0);
            }
        }

        private void SendAsync(IAsyncResult result)
        {
            KeyValuePair<int, WebRequest> request = (KeyValuePair<int, WebRequest>)result.AsyncState;
            try
            {
                HttpWebResponse response = (HttpWebResponse)request.Value.EndGetResponse(result);
                if (response == null || response.StatusCode != HttpStatusCode.OK)
                {
                    Debug.WriteLine("Error sending data. Server returned status code: {0}", response == null ? HttpStatusCode.Unused : response.StatusCode);
                    ReturnEvent(request.Key);
                    return;
                }
                RemoveEvent(request.Key);
            }
            catch (WebException ex)
            {
                if (ex.Status != WebExceptionStatus.NameResolutionFailure)
                {
                    Debug.WriteLine("Error in SendAsync {0}", ex);
                    ReturnEvent(request.Key);
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Error in SendAsync {0}", ex);
                ReturnEvent(request.Key);
            }
        }

        private void RemoveEvent(int id)
        {
            lock (m_syncRoot)
            {
                LinkedListNode<EventData> node = m_pendingEvents.First;

                while (node != null && node.Value.ID != id)
                    node = node.Next;

                if (node != null)
                    m_pendingEvents.Remove(node);
                else
                    Debug.WriteLine("Error removing event {0}", id);
            }
        }

        private void ReturnEvent(int id)
        {
            lock (m_syncRoot)
            {
                LinkedListNode<EventData> node = m_pendingEvents.First;

                while (node != null && node.Value.ID != id)
                    node = node.Next;

                if (node == null)
                {
                    Debug.WriteLine("Error returning event {0}", id);
                    return;
                }

                m_events.Enqueue(node.Value);
                m_pendingEvents.Remove(node);
            }
        }
    }
}

