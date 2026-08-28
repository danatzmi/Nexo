# How the `web/` app works

A plain-language guide to this folder, written for someone whose only prior
experience is the Nexo iOS/SwiftUI app.

---

## 1. The folder structure

```
web/
├── package.json         the project's "manifest" — name, dependencies, scripts
├── package-lock.json     exact locked versions of every dependency (auto-generated — never edit by hand)
├── node_modules/         the actual downloaded dependency code (huge, gitignored, rebuilt via `npm install`)
├── index.html            the one real HTML page — just an empty shell
├── vite.config.ts        settings for Vite, the build tool (see below)
├── tsconfig*.json        settings for the TypeScript compiler
├── postcss.config.js     tells the CSS pipeline to run Tailwind
├── public/                static files served as-is (favicon, etc.)
└── src/                   all of your actual application code
    ├── main.tsx           the real entry point — mounts React onto the page
    ├── App.tsx            the root component — layout, nav bar, route list
    ├── index.css          global styles + the Tailwind import
    ├── firebase.ts        Firebase SDK setup (placeholder config for now)
    ├── pages/              one file per screen (Schedule.tsx, Login.tsx)
    └── assets/             images/icons imported by components
```

### Mapping this to what you already know (SwiftUI)

| iOS / SwiftUI | Web / React | Role |
|---|---|---|
| `Package.swift` / Xcode project settings | `package.json` | declares dependencies + build/run commands |
| Swift Package Manager | `npm` | downloads and manages those dependencies |
| `@main struct NexoApp` | `src/main.tsx` | the actual entry point that boots the app |
| `ContentView` | `src/App.tsx` | root view: overall layout + navigation |
| `NavigationStack` / `TabView` | `<Routes>` / `<Route>` (react-router-dom) | decides which screen shows for which "address" |
| A `View` struct | A React **component** (a function returning JSX) | a reusable piece of UI |
| `Nexo/Features/Schedule/ScheduleView.swift` | `src/pages/Schedule.tsx` | one specific screen |

---

## 2. How a page actually gets on screen

1. The browser loads **`index.html`**. It's almost empty — just
   `<div id="root"></div>` and one `<script>` tag pointing at `main.tsx`.
2. **`main.tsx`** runs first. It finds that empty `<div id="root">` and
   tells React: "render the `<App />` component inside this div."
3. **`App.tsx`** renders the dark header/nav bar, then looks at the
   browser's current URL path and picks which page component to show
   (`Schedule.tsx` for `/`, `Login.tsx` for `/login`) — that's what
   `react-router-dom`'s `<Routes>`/`<Route>` do.
4. Each page is just a function that returns some JSX (HTML-looking
   syntax mixed with JavaScript) with Tailwind class names for styling.

Nothing is "compiled into an app bundle" the way Xcode does for iOS —
the browser downloads and runs JavaScript directly. Vite's job (next
section) is to make that fast and to turn your TypeScript into plain
JavaScript the browser can understand.

---

## 3. What Vite actually is

Vite is two tools in one:

- **A dev server** (`npm run dev`) — for working on the app locally, with
  instant reload when you save a file. Roughly the web equivalent of
  Xcode Previews, except it runs in a real browser.
- **A bundler** (`npm run build`) — packages everything into a small
  number of optimized, minified files in `dist/`, ready to be uploaded to
  a real web host. This is the web equivalent of an Xcode Release build.

### `npm run dev` — what happens under the hood

1. `npm` looks up the `"dev"` script in `package.json`, sees it means
   `vite`, and runs that.
2. Vite starts a small local web server (using Node.js) that listens on
   `http://localhost:5173` by default.
3. **Key detail — Vite does *not* bundle your whole app upfront in dev
   mode.** Instead, it serves your source files (`.tsx`, `.css`, etc.)
   more or less as-is, and only transforms a file (strip TypeScript
   types, convert JSX to plain JS) the moment the browser actually
   requests it. That's why the dev server starts almost instantly even
   on a large project.
4. It also opens a **WebSocket** connection between the server and your
   browser tab. When you save a file, Vite recompiles just that one file
   and pushes the update down the WebSocket. The browser swaps the code
   in place — this is called **Hot Module Replacement (HMR)** — usually
   without even a full page reload or losing your current state.
5. Type errors are **not** checked in this fast dev mode (that's a
   separate, slower step — see `npm run build` below). Dev mode only
   cares about running your code quickly.

### `npm run dev -- --host` — the `--host` flag

By default, Vite's dev server only binds to `localhost` (your own
machine) — so only your computer can open `http://localhost:5173`.

Passing the `--host` flag tells Vite to also bind to your machine's
network address (`0.0.0.0`), meaning **any other device on the same
Wi‑Fi network can reach it too** — e.g. opening the app in Safari on your
iPhone using your Mac's local IP, like `http://192.168.1.23:5173`. This
is how you test the web app on a real phone without deploying it
anywhere.

Note the `--`: `npm run <script>` needs a literal `--` before any flags
that should be passed *through* to the underlying command rather than
interpreted by `npm` itself:
```
npm run dev -- --host
```

### `npm run build` — what happens under the hood

```
tsc -b && vite build
```
Two steps, in order:
1. **`tsc -b`** — runs the real TypeScript compiler in "check" mode
   across the whole project. If there's a type error anywhere, the build
   stops here. (This is the type-safety check that dev mode skips.)
2. **`vite build`** — bundles everything into `dist/`: combines and
   minifies your JS/CSS, generates the final `dist/index.html`, and
   fingerprints filenames (e.g. `index-DBkfY6bM.js`) for cache-busting.

---

## 4. Where Tailwind fits in

`src/index.css` contains one line: `@import "tailwindcss";`. When Vite
processes that file, it hands it to **PostCSS**, configured in
`postcss.config.js` to run the `@tailwindcss/postcss` plugin. That plugin
scans your `.tsx` files for class names like `bg-[#121212]` or
`rounded-full`, and generates *only* the actual CSS for the classes you
used — nothing more. In dev mode this happens live and gets pushed via
HMR; in a production build it's written once into the final CSS file.

---

## 5. The npm scripts, summarized

| Command | What it does |
|---|---|
| `npm install` | Downloads every dependency listed in `package.json` into `node_modules/` |
| `npm run dev` | Starts the local dev server with hot reload |
| `npm run dev -- --host` | Same, but reachable from other devices on your Wi-Fi |
| `npm run build` | Type-checks, then produces the optimized `dist/` folder for deployment |
| `npm run preview` | Serves the `dist/` build locally, so you can sanity-check the real production output |
| `npm run lint` | Runs the linter (Oxlint) to catch common code issues |
