// The extension's behind-the-scenes script. Two jobs:
//
// 1. Messenger between YouTube pages and the YouTubeHub app.
//    A normal YouTube page isn't allowed to call a server on your own PC. This script is, because
//    manifest.json lists http://127.0.0.1:8765 under "host_permissions".
//    A page sends a message like { path: "poll", params: { video: "abc" } }; this script adds which
//    tab it came from, calls the app, and passes the app's answer back.
//
// 2. The right-click menu on YouTube pages and links:
//      YouTubeHub > Add song to library
//                   Add song to playlist > Edm / Lofi / ...   (your playlists, read from the app)
//                   Add channel > Favorite / High / Good filler / Desperate
//                   Add podcast > Favorite / High / Good filler / Desperate
//    Right-click a link (e.g. a video thumbnail) to add that; right-click empty page space to add the page.

const APP_URL = "http://127.0.0.1:8765/"; // must match ExtensionServer.PORT in the Java app

// Tier dropdown values are the app's tier levels (model/Tier.java)
const TIERS = [
  ["4", "Favorite"],
  ["3", "High"],
  ["2", "Good filler"],
  ["1", "Desperate"],
];

// ---------- 1. messenger ----------

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!sender.tab) {
    return false; // not from a page
  }
  refreshMenuSoon(); // YouTube pages message us often, so this keeps the playlist names current

  const params = new URLSearchParams(message.params || {});
  params.set("tab", sender.tab.id); // Opera's id for the tab the message came from

  fetch(APP_URL + message.path + "?" + params, { cache: "no-store" })
    .then((response) => (response.ok ? response.json() : null))
    .then((answer) => sendResponse(answer))
    .catch(() => sendResponse(null)); // app not running: answer "null"

  return true; // "I'll answer later": keeps the message open until fetch finishes
});

// ---------- 2. right-click menu ----------

let lastMenuBuild = 0;
let building = false;

// Rebuilds at most once a minute
function refreshMenuSoon() {
  if (Date.now() - lastMenuBuild > 60_000) {
    buildMenu();
  }
}

async function buildMenu() {
  if (building) {
    return;
  }
  building = true;
  lastMenuBuild = Date.now();
  try {
    let playlists = [];
    try {
      const response = await fetch(APP_URL + "playlists", { cache: "no-store" });
      if (response.ok) {
        playlists = (await response.json()).playlists;
      }
    } catch {
      // app not running: build the menu without playlists
    }

    await chrome.contextMenus.removeAll();

    // Every item: shows on YouTube pages, when right-clicking the page itself or a link on it
    const where = { contexts: ["page", "link"], documentUrlPatterns: ["https://www.youtube.com/*"] };
    const add = (id, title, parentId) =>
      chrome.contextMenus.create(parentId ? { id, title, parentId, ...where } : { id, title, ...where });

    // The id says what to do when clicked: "song|<playlist id>" or "channel|<tier>|<podcast?>"
    add("root", "YouTubeHub");
    add("song|", "Add song to library", "root");
    if (playlists.length > 0) {
      add("songTo", "Add song to playlist", "root");
      for (const playlist of playlists) {
        add("song|" + playlist.id, playlist.name, "songTo");
      }
    }
    add("channel", "Add channel", "root");
    add("podcast", "Add podcast", "root");
    for (const [level, name] of TIERS) {
      add("channel|" + level + "|false", name, "channel");
      add("channel|" + level + "|true", name, "podcast");
    }
  } finally {
    building = false;
  }
}

// Menus are kept by the browser, but rebuild when the extension is installed/reloaded or Opera starts.
chrome.runtime.onInstalled.addListener(buildMenu);
chrome.runtime.onStartup.addListener(buildMenu);

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  const url = info.linkUrl || info.pageUrl; // the link you right-clicked, or else the page
  const [kind, first, second] = String(info.menuItemId).split("|");

  let path;
  let params;
  if (kind === "song") {
    path = "add-song";
    params = { url, playlist: first }; // "" = library only
  } else if (kind === "channel") {
    path = "add-channel";
    params = { url, priority: first, podcast: second };
  } else {
    return; // a submenu heading
  }

  let text;
  try {
    const response = await fetch(APP_URL + path + "?" + new URLSearchParams(params), { cache: "no-store" });
    text = response.ok ? (await response.json()).message : "YouTubeHub had a problem adding that.";
  } catch {
    text = "YouTubeHub isn't running. Start the app and try again.";
  }
  if (path === "add-song") {
    lastMenuBuild = 0; // cheap way to pick up any playlist changes next time
  }
  showMessage(tab, text);
});

// Shows the result as a small box on the YouTube page (content.js draws it).
// If that page can't (e.g. it was open before the extension loaded), shows ✓/! on the toolbar icon instead.
function showMessage(tab, text) {
  if (text.length > 160) {
    text = text.slice(0, 160) + "...";
  }
  const fallback = () => {
    const ok = text.startsWith("Added");
    chrome.action.setBadgeText({ text: ok ? "✓" : "!" });
    chrome.action.setBadgeBackgroundColor({ color: ok ? "#2e9e5b" : "#ff4757" });
    chrome.action.setTitle({ title: text });
    setTimeout(() => chrome.action.setBadgeText({ text: "" }), 5000);
  };
  if (!tab || tab.id === undefined) {
    fallback();
    return;
  }
  chrome.tabs.sendMessage(tab.id, { type: "toast", text }).catch(fallback);
}
