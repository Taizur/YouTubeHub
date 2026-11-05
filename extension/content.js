// Runs inside every YouTube page you open in Opera GX.
//
// 1. When a video page loads, it asks the app "is this tab your player?" (hello).
//    Yes = this tab is the one the app opened (or already uses) for playing.
// 2. If yes, it keeps asking the app "anything for me?" (poll). When the app answers
//    "load abc123", this tab switches to that video. That's how Skip, and double-clicking
//    in the app, reuse this tab instead of opening a new one.
// 3. When the video really finishes (the <video> element's "ended" event; pausing never fires it),
//    it tells the app (ended). The app answers with the next video, and this tab loads it.
//    Loading a new page also wipes out YouTube's own "Up next" countdown, so the app's pick wins.
//
// It can't call the app itself (web pages aren't allowed to), so every question goes
// through background.js with chrome.runtime.sendMessage.

let isPlayer = false;

// "https://www.youtube.com/watch?v=abc123&t=10" -> "abc123". null on any other kind of page.
function currentVideoId() {
  if (location.pathname !== "/watch") {
    return null;
  }
  return new URLSearchParams(location.search).get("v");
}

// Ask the app something. Gives back the app's answer, or null if the app isn't running.
function ask(path, params = {}) {
  return chrome.runtime.sendMessage({ path, params }).catch(() => null);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function start() {
  while (true) {
    const videoId = currentVideoId();
    if (!videoId) {
      return; // not a video page
    }
    const answer = await ask("hello", { video: videoId });
    if (answer) {
      if (answer.player) {
        becomePlayer();
      }
      return;
    }
    await sleep(10000); // app isn't running yet: try again in 10 seconds
  }
}

function becomePlayer() {
  isPlayer = true;
  // "ended" doesn't bubble up the page like a click does, but listening with `true`
  // (the "capture" phase) still catches it on the way down to the <video> element.
  document.addEventListener("ended", onEnded, true);
  pollLoop();
}

async function pollLoop() {
  while (isPlayer) {
    const answer = await ask("poll"); // the app holds this open for up to 20 seconds
    if (!answer) {
      await sleep(5000); // app closed or restarting
      continue;
    }
    await handle(answer);
  }
}

async function onEnded(event) {
  if (!isPlayer || event.target.tagName !== "VIDEO") {
    return;
  }
  // Safety net: never treat the end of an ad as the end of the video.
  const player = document.querySelector("#movie_player");
  if (player && player.classList.contains("ad-showing")) {
    return;
  }
  const answer = await ask("ended", { video: currentVideoId() });
  if (answer) {
    await handle(answer);
  }
}

// The app's answer: { command: "load", videoId } / { command: "none" } / { command: "stop" }
async function handle(answer) {
  if (answer.command === "stop") {
    isPlayer = false; // another tab is the player now
  } else if (answer.command === "load") {
    if (answer.videoId === currentVideoId()) {
      await ask("hello", { video: answer.videoId }); // already showing it: just say so
      return;
    }
    // Press YouTube's "Cancel" on its "Up next" countdown, if it's showing.
    const cancel = document.querySelector(".ytp-autonav-endscreen-upnext-cancel-button");
    if (cancel) {
      cancel.click();
    }
    isPlayer = false; // this page is going away; the new page says hello and takes over
    location.href = "https://www.youtube.com/watch?v=" + encodeURIComponent(answer.videoId);
  }
}

start();
