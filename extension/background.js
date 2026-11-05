// The messenger between YouTube pages and the YouTubeHub app.
//
// Why a middleman? A normal YouTube page isn't allowed to call a server on your own PC.
// The extension's background script is allowed, because manifest.json lists
// http://127.0.0.1:8765 under "host_permissions".
//
// A page sends a message like { path: "poll", params: { video: "abc" } }.
// This script adds which tab it came from, calls the app, and passes the app's answer back.

const APP_URL = "http://127.0.0.1:8765/"; // must match ExtensionServer.PORT in the Java app

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const params = new URLSearchParams(message.params || {});
  params.set("tab", sender.tab.id); // Opera's id for the tab the message came from

  fetch(APP_URL + message.path + "?" + params, { cache: "no-store" })
    .then((response) => (response.ok ? response.json() : null))
    .then((answer) => sendResponse(answer))
    .catch(() => sendResponse(null)); // app not running: answer "null"

  return true; // "I'll answer later": keeps the message open until fetch finishes
});
