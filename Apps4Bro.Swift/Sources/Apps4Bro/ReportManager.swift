import Foundation

// Port of ReportManager.cs. C# used ConcurrentQueue + a polling background thread
// at 100ms intervals. Swift port uses a serial DispatchQueue + URLSession, which
// gives equivalent ordering guarantees without a manual sleep loop.
public final class ReportManager {

    private struct EventData {
        let event: String
        let parameter: String
        let time: Date
        let id: Int
    }

    private static let unixEpoch = Date(timeIntervalSince1970: 0)

    private let appId: String
    private let session: URLSession

    private let queue = DispatchQueue(label: "Apps4Bro.ReportManager", qos: .utility)
    private var pending: [EventData] = []           // FIFO awaiting send
    private var inFlight: [Int: EventData] = [:]    // sent, awaiting response
    private var eventCounter: Int = 0
    private var sending: Bool = false

    public init(applicationId: String) {
        self.appId = applicationId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? applicationId
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 5
        cfg.waitsForConnectivity = false
        self.session = URLSession(configuration: cfg)
    }

    public func reportEvent(_ name: String, parameter: String) {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.eventCounter += 1
            let evt = EventData(event: name, parameter: parameter, time: Date(), id: self.eventCounter)
            self.pending.append(evt)
            self.drainIfIdle()
        }
    }

    private func drainIfIdle() {
        guard !sending else { return }
        guard let next = pending.first else { return }
        pending.removeFirst()
        inFlight[next.id] = next
        sending = true
        send(next)
    }

    private func send(_ data: EventData) {
        guard let url = URL(string: formatRequest(data)) else {
            // Malformed event — drop it and continue draining.
            queue.async { [weak self] in
                self?.inFlight.removeValue(forKey: data.id)
                self?.sending = false
                self?.drainIfIdle()
            }
            return
        }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData)
        req.timeoutInterval = 5
        req.httpMethod = "GET"

        let task = session.dataTask(with: req) { [weak self] _, response, error in
            guard let self = self else { return }
            self.queue.async {
                let ok: Bool
                if let http = response as? HTTPURLResponse, http.statusCode == 200, error == nil {
                    ok = true
                } else {
                    // Match C# WebExceptionStatus.NameResolutionFailure handling:
                    // a DNS miss drops the event silently; any other failure requeues.
                    if let urlErr = error as? URLError, urlErr.code == .cannotFindHost {
                        ok = true
                    } else {
                        ok = false
                    }
                }
                if ok {
                    self.inFlight.removeValue(forKey: data.id)
                } else if let evt = self.inFlight.removeValue(forKey: data.id) {
                    // Re-queue at the tail; matches C# ReturnEvent putting it back in m_events.
                    self.pending.append(evt)
                }
                self.sending = false
                self.drainIfIdle()
            }
        }
        task.resume()
    }

    private func formatRequest(_ event: EventData) -> String {
        let unixSeconds = Int(event.time.timeIntervalSince1970)
        // C# replaces '[' and ']' inside the parameter to avoid breaking the URL parser
        // server-side. Preserve that transformation.
        let sanitized = event.parameter.replacingOccurrences(of: "[", with: "{").replacingOccurrences(of: "]", with: "}")
        let eventEncoded = event.event.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? event.event
        let paramEncoded = sanitized.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sanitized

        return String(
            format: Apps4BroSDK.reportUrl,
            Apps4BroSDK.advertisingId,
            appId,
            eventEncoded,
            paramEncoded,
            unixSeconds,
            event.id
        )
    }
}
