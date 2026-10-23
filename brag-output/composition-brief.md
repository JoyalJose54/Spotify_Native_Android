# Hyperframes Composition Brief: Spotify Native Android

## Objective
Create a short, high-energy, polished launch-style brag video for Spotify Native Android.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 18.0 seconds

## Source Material
- Project root: `d:\RSET\PROJECT\Spotify_Native_Android`
- Primary files read: `README.md`, `app/src/main/kotlin/...`, `spotify_server_control.pyw`, assets
- Product name: Spotify Native Android
- Tagline / strongest claim: "A flagship native Android music streaming client engineered with Kotlin, Jetpack Compose, AndroidX Media3, and Real-Time STFT Audio DSP."
- Key UI or visual moments to recreate:
  - The Now Playing screen with album art, orbital fluid mesh backdrop, rolling tabular timer, and controls.
  - The Real-Time Vocal Suppression (Apple Sing / Karaoke) STFT audio visualizer and toggle.
  - Instant Zero-Reload SQLite Room caching & 256k AAC ingestion badges.
- Copy that must appear verbatim:
  - "STREAMING MUSIC IS STANDARD."
  - "WHAT IF YOUR CLIENT WAS AN AUDIO POWERHOUSE?"
  - "REAL-TIME ON-DEVICE VOCAL SUPPRESSION"
  - "4096-point STFT injected directly into ExoPlayer audio graph"
  - "BUILT FROM SCRATCH. NO COMPROMISES."

## Creative Direction
- Tone preset: `polished`
- Creative direction: Sleek flagship tech launch film
- Interpretation: Confident, modern, high-contrast dark aesthetic with vibrant Spotify green and karaoke purple accents, snappy rhythm-coupled transitions, and readable text holds.
- Angle: Showcase that this isn't another generic clone or web wrapper — it's an Android engineering feat featuring on-device real-time DSP, 120Hz fluid Compose animations, and offline-first persistence.
- Hook: First 3 seconds challenge standard streaming apps before revealing the glowing Spotify Native Android emblem.
- Outro / punchline: "Built from scratch. No compromises."
- Avoid:
  - Generic SaaS language
  - Abstract filler visuals
  - Boring slides without product realism

## Visual Identity
- Background: `#0B0B0E` (Obsidian Dark) with radial glow
- Card Surfaces: `#141419` / `#1E1E26` with border `#2A2A35`
- Primary Accent: `#1DB954` (Spotify Green)
- Secondary DSP Accent: `#A855F7` (Electric Purple) and `#38BDF8` (Audio Cyan)
- Text: Primary `#FFFFFF`, Muted `#A1A1AA`
- Display font: `Inter`, `Segoe UI`, system-ui
- Visual references from the project: `assets/images/logo.webp`, `spotify_logo.png`

## Storyboard
Use the storyboard in `brag-output/brag-plan.md` as the creative contract:
1. The Engineering Hook — 3.5s (0.00s - 3.50s) — Typography slam + glowing Spotify emblem
2. 120Hz Fluid Motion Player — 4.5s (3.50s - 8.00s) — Now Playing mockup with orbital fluid mesh backdrop
3. 4096-pt STFT Vocal Suppression — 5.0s (8.00s - 13.00s) — Karaoke toggle tap + live audio spectrum DSP morph
4. Zero-Reload Architecture — 2.5s (13.00s - 15.50s) — 3 cascading stat cards (0ms Cache, 256k AAC, Auto Sync)
5. Outro & Launch Punchline — 2.5s (15.50s - 18.00s) — Big logo lockup + "Built from scratch. Zero compromises."

## Audio
- Audio role: Warm upbeat driving electronic groove
- Music: `happy-beats-business-moves-vol-1-by-ende-dot-app.mp3`
- Music treatment: Starts at 0.0s, steady 120 BPM tempo, smooth fade under final logo
- SFX: Tasteful UI switches and clicks for card entries and button toggle
- Audio files: Copied into `brag-output/composition/assets/`
