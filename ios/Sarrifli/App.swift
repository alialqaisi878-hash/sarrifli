import UIKit
import WebKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

/* Firebase values for the iOS app registered in the Firebase console (bundle com.alsaqi.sarrifli).
   Until IOS_APP_ID is filled in, the app runs without phone notifications. */
enum Config {
    static let remote = URL(string: "https://alialqaisi878-hash.github.io/sarrifli/")!
    static let iosAppID = "IOS_APP_ID"
    static let apiKey = "AIzaSyDfuIL1VTuFLnVxCpSozV8zip6NPVt7gRw"
    static let project = "sarrifli-a37aa"
    static let sender = "452057270832"
    static var firebaseReady: Bool { iosAppID.hasPrefix("1:") }
}

@main
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    var window: UIWindow?
    static var token: String?

    func application(_ app: UIApplication, didFinishLaunchingWithOptions opts: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        if Config.firebaseReady {
            let o = FirebaseOptions(googleAppID: Config.iosAppID, gcmSenderID: Config.sender)
            o.apiKey = Config.apiKey
            o.projectID = Config.project
            o.bundleID = Bundle.main.bundleIdentifier ?? "com.alsaqi.sarrifli"
            FirebaseApp.configure(options: o)
            Messaging.messaging().delegate = self
        }
        UNUserNotificationCenter.current().delegate = self
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = WebVC()
        window?.makeKeyAndVisible()
        return true
    }

    /* Called when the page asks for notifications (opening the app). */
    static func askPush() {
        guard Config.firebaseReady else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            guard ok else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    func application(_ app: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken t: Data) {
        Messaging.messaging().apnsToken = t
    }

    func messaging(_ m: Messaging, didReceiveRegistrationToken t: String?) {
        AppDelegate.token = t
        NotificationCenter.default.post(name: .pushToken, object: t)
    }

    /* Show notices as banners even while the app is open. */
    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification, withCompletionHandler done: @escaping (UNNotificationPresentationOptions) -> Void) {
        done([.banner, .sound, .list])
    }
}

extension Notification.Name { static let pushToken = Notification.Name("pushToken") }

/* The web app in a WKWebView: the online copy first (updates without a new build), the bundled copy when offline. */
class WebVC: UIViewController, WKNavigationDelegate, WKScriptMessageHandler, WKUIDelegate {
    var web: WKWebView!
    var usedLocal = false
    var timer: Timer?

    override func loadView() {
        let cfg = WKWebViewConfiguration()
        cfg.userContentController.add(self, name: "ios")
        cfg.allowsInlineMediaPlayback = true
        cfg.websiteDataStore = .default()
        web = WKWebView(frame: .zero, configuration: cfg)
        web.navigationDelegate = self
        web.uiDelegate = self
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0x0E/255, green: 0x14/255, blue: 0x24/255, alpha: 1)
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.allowsBackForwardNavigationGestures = false
        view = web
        NotificationCenter.default.addObserver(self, selector: #selector(gotToken(_:)), name: .pushToken, object: nil)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        load(Config.remote)
    }

    func load(_ u: URL) {
        web.load(URLRequest(url: u, cachePolicy: .reloadRevalidatingCacheData, timeoutInterval: 15))
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: false) { [weak self] _ in self?.offline() }
    }

    func offline() {
        timer?.invalidate()
        guard !usedLocal, let f = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") else { return }
        usedLocal = true
        web.loadFileURL(f, allowingReadAccessTo: f.deletingLastPathComponent())
    }

    func webView(_ w: WKWebView, didFinish n: WKNavigation!) {
        timer?.invalidate()
        sendToken()
    }

    func webView(_ w: WKWebView, didFailProvisionalNavigation n: WKNavigation!, withError e: Error) { offline() }
    func webView(_ w: WKWebView, didFail n: WKNavigation!, withError e: Error) { if w.url == nil { offline() } }

    /* Links to other sites (maps, WhatsApp, phone) open outside the app. */
    func webView(_ w: WKWebView, decidePolicyFor a: WKNavigationAction, decisionHandler done: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let u = a.request.url else { return done(.allow) }
        if u.isFileURL || u.scheme == "about" || u.scheme == "blob" || u.scheme == "data" || u.host == Config.remote.host || a.targetFrame?.isMainFrame == false {
            return done(.allow)
        }
        UIApplication.shared.open(u)
        done(.cancel)
    }

    func webView(_ w: WKWebView, createWebViewWith c: WKWebViewConfiguration, for a: WKNavigationAction, windowFeatures f: WKWindowFeatures) -> WKWebView? {
        if let u = a.request.url { UIApplication.shared.open(u) }
        return nil
    }

    func webView(_ w: WKWebView, runJavaScriptAlertPanelWithMessage m: String, initiatedByFrame f: WKFrameInfo, completionHandler done: @escaping () -> Void) {
        let a = UIAlertController(title: nil, message: m, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "تمام", style: .default) { _ in done() })
        present(a, animated: true)
    }

    func webView(_ w: WKWebView, runJavaScriptConfirmPanelWithMessage m: String, initiatedByFrame f: WKFrameInfo, completionHandler done: @escaping (Bool) -> Void) {
        let a = UIAlertController(title: nil, message: m, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "إلغاء", style: .cancel) { _ in done(false) })
        a.addAction(UIAlertAction(title: "نعم", style: .destructive) { _ in done(true) })
        present(a, animated: true)
    }

    func webView(_ w: WKWebView, runJavaScriptTextInputPanelWithPrompt p: String, defaultText d: String?, initiatedByFrame f: WKFrameInfo, completionHandler done: @escaping (String?) -> Void) {
        let a = UIAlertController(title: nil, message: p, preferredStyle: .alert)
        a.addTextField { t in t.text = d; t.isSecureTextEntry = p.contains("الرمز") }
        a.addAction(UIAlertAction(title: "إلغاء", style: .cancel) { _ in done(nil) })
        a.addAction(UIAlertAction(title: "تمام", style: .default) { _ in done(a.textFields?.first?.text) })
        present(a, animated: true)
    }

    @objc func gotToken(_ n: Notification) { sendToken() }

    func sendToken() {
        guard let t = AppDelegate.token else { return }
        web.evaluateJavaScript("window.iosPush&&window.iosPush(\(jsString(t)))")
    }

    func jsString(_ s: String) -> String {
        let d = try? JSONSerialization.data(withJSONObject: [s])
        let a = d.flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        return String(a.dropFirst().dropLast())
    }

    /* Messages from the page: {a:'push'} | {a:'share', d:dataURL} | {a:'board', on:1|0} */
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        guard let o = m.body as? [String: Any], let a = o["a"] as? String else { return }
        switch a {
        case "push":
            AppDelegate.askPush()
            sendToken()
        case "share":
            guard let s = o["d"] as? String, let i = s.firstIndex(of: ","),
                  let data = Data(base64Encoded: String(s[s.index(after: i)...])), let img = UIImage(data: data) else { return }
            let v = UIActivityViewController(activityItems: [img], applicationActivities: nil)
            v.popoverPresentationController?.sourceView = view
            v.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 80, width: 1, height: 1)
            present(v, animated: true)
        case "board":
            UIApplication.shared.isIdleTimerDisabled = ((o["on"] as? Int) ?? 0) == 1
        default:
            break
        }
    }
}
