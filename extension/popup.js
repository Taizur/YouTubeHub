// Runs inside popup.html each time you click the YouTubeHub icon.
// It reads the address of the tab you're looking at and sends it to the app:
// the same thing as Ctrl+L, Ctrl+C and "Add from clipboard", minus the copying.
//
// The popup belongs to the extension (not to YouTube), so unlike content.js it can call the app directly.

const APP_URL = "http://127.0.0.1:8765/"; // must match ExtensionServer.PORT in the Java app

const pageLabel = document.getElementById("page");
const playlistSelect = document.getElementById("playlist");
const priorityInput = document.getElementById("priority"); // the tier dropdown; its value is the tier level
const podcastBox = document.getElementById("podcast");
const addSongButton = document.getElementById("addSong");
const addChannelButton = document.getElementById("addChannel");
const message = document.getElementById("message");

let pageUrl = null;

// Call the app. Gives back its answer, or null if the app isn't running.
async function askApp(path, params = {}) {
  try {
    const response = await fetch(APP_URL + path + "?" + new URLSearchParams(params), { cache: "no-store" });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

function setButtonsEnabled(enabled) {
  addSongButton.disabled = !enabled;
  addChannelButton.disabled = !enabled;
}

async function start() {
  setButtonsEnabled(false);

  // The tab you're looking at. Clicking the icon gives the extension permission to read its address
  // ("activeTab" in manifest.json), and only for that tab.
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  pageUrl = tab && tab.url;
  pageLabel.textContent = tab ? tab.title : "";
  if (!pageUrl || !pageUrl.includes("youtube.com")) {
    message.textContent = "Open a YouTube video or channel page first.";
    return;
  }

  const answer = await askApp("playlists");
  if (!answer) {
    message.textContent = "YouTubeHub isn't running. Start the app and try again.";
    return;
  }

  // The playlist dropdown: "Library only" first, then your playlists.
  playlistSelect.add(new Option("Library only (no playlist)", ""));
  for (const playlist of answer.playlists) {
    playlistSelect.add(new Option(playlist.name, playlist.id));
  }
  setButtonsEnabled(true);
}

async function add(path, params) {
  setButtonsEnabled(false);
  message.textContent = "Adding...";
  const answer = await askApp(path, { url: pageUrl, ...params });
  message.textContent = answer ? answer.message : "YouTubeHub isn't running.";
  setButtonsEnabled(true);
}

addSongButton.addEventListener("click", () => add("add-song", { playlist: playlistSelect.value }));
addChannelButton.addEventListener("click", () =>
  add("add-channel", { priority: priorityInput.value, podcast: podcastBox.checked })
);

start();
