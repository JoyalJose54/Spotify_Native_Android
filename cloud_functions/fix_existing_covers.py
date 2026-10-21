"""
Utility script to upgrade existing tracks in Firestore with studio-grade
Apple Music / iTunes Store 1200x1200 cover art and Cloudinary 1000x1000 square crops.
Eliminates letterbox black bars from YouTube thumbnails.
"""

import os
import sys
import time
import requests
import urllib.parse
import re
from pathlib import Path
from dotenv import load_dotenv

sys.stdout.reconfigure(encoding='utf-8')

# Load environment
env_path = Path(__file__).parent / ".env"
load_dotenv(env_path)

import cloudinary
import cloudinary.uploader
import firebase_admin
from firebase_admin import credentials, firestore

# Configure Cloudinary
cloudinary.config(
    cloud_name=os.getenv("CLOUDINARY_CLOUD_NAME"),
    api_key=os.getenv("CLOUDINARY_API_KEY"),
    api_secret=os.getenv("CLOUDINARY_API_SECRET"),
    secure=True,
)

# Configure Firebase
cred_path = Path(__file__).parent.parent / "database" / "firebase-key.json"
cred = credentials.Certificate(str(cred_path))
try:
    firebase_admin.get_app()
except ValueError:
    firebase_admin.initialize_app(cred)

db = firestore.client()
TRACKS_COL = "tracks"

def clean_track_title(raw_title: str) -> str:
    """Clean title formatting for search and studio metadata matching."""
    t = raw_title
    t = re.sub(r"[\(\[][^\)\]]*$", "", t)
    t = re.sub(r"\.{2,}", "", t)
    if "|" in t:
        parts = [p.strip() for p in t.split("|") if p.strip()]
        if parts:
            t = parts[0]
    t = re.sub(r"\(.*?\)", "", t)
    t = re.sub(r"\[.*?\]", "", t)
    buzzwords = [
        "official video", "video song", "music video", "lyric video", "lyrics",
        "audio song", "official audio", "full video song", "full song", "hd", "4k",
        "remastered", "premiere", "official", "video"
    ]
    for b in buzzwords:
        t = re.sub(r"\b" + re.escape(b) + r"\b", "", t, flags=re.IGNORECASE)
    t = re.sub(r"^@[A-Za-z0-9_.-]+\s*[-–:]\s*", "", t)
    t = re.sub(r"\s+", " ", t).strip(" -_")
    return t or raw_title


def fetch_itunes_cover(title: str, artist: str = "") -> str | None:
    """Search Apple Music / iTunes API for 1200x1200bb uncompressed studio cover."""
    cleaned = clean_track_title(title)
    queries = [cleaned]

    if " - " in cleaned:
        queries.append(cleaned.replace(" - ", " "))
        parts = [p.strip() for p in cleaned.split(" - ", 1) if p.strip()]
        if len(parts) == 2:
            queries.append(f"{parts[1]} {parts[0]}")
            queries.append(parts[1])
            queries.append(parts[0])

    generic_terms = ["records", "vevo", "music", "studios", "channel", "official", "entertainment", "south"]
    is_generic_artist = any(k in artist.lower() for k in generic_terms) if artist else True
    if artist and not is_generic_artist:
        queries.insert(1, f"{cleaned} {artist}")

    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    seen = set()

    for q in queries:
        q_clean = q.strip()
        if not q_clean or q_clean.lower() in seen:
            continue
        seen.add(q_clean.lower())

        for entity in ["song", "album"]:
            url = f"https://itunes.apple.com/search?term={urllib.parse.quote(q_clean)}&entity={entity}&limit=3"
            try:
                resp = requests.get(url, headers=headers, timeout=5)
                if resp.status_code == 200:
                    data = resp.json()
                    if data.get("resultCount", 0) > 0:
                        item = data["results"][0]
                        art = item.get("artworkUrl100") or ""
                        if art:
                            return art.replace("100x100bb", "1200x1200bb")
            except Exception:
                pass
    return None


def sanitize_public_id(raw: str) -> str:
    return re.sub(r"[^a-zA-Z0-9/_-]", "_", raw)[:200]


def upload_studio_cover(image_url: str, title: str, artist: str) -> tuple[str, str]:
    """Upload to Cloudinary with 1000x1000 fill and auto gravity."""
    public_id = sanitize_public_id(f"covers/{artist}/{title}")
    res = cloudinary.uploader.upload(
        image_url,
        public_id=public_id,
        overwrite=True,
        transformation=[{
            "width": 1000,
            "height": 1000,
            "crop": "fill",
            "gravity": "auto",
            "fetch_format": "auto",
            "quality": "auto:best",
        }],
    )
    return res["secure_url"], public_id


def fix_track(doc_id: str, title: str, artist: str):
    """Upgrade a single track's cover art."""
    print(f"\nProcessing '{title}' by '{artist}' (doc={doc_id})...")
    cover_url = fetch_itunes_cover(title, artist)
    if not cover_url:
        print(f"  ❌ No iTunes studio cover found.")
        return False

    print(f"  ✓ Found iTunes 1200x1200 studio cover: {cover_url[:70]}...")
    secure_url, public_id = upload_studio_cover(cover_url, title, artist)
    print(f"  ✓ Uploaded to Cloudinary: {secure_url}")

    db.collection(TRACKS_COL).document(doc_id).update({
        "imageUrl": secure_url,
        "cover_public_id": public_id,
    })
    print(f"  ✓ Firestore document updated successfully!")
    return True


if __name__ == "__main__":
    target_names = ["Asalayavale", "Sabi", "OK Kanmani", "Radhimaa", "Varnajaalam", "Indrajaalam"]
    print(f"Starting cover upgrade check for: {', '.join(target_names)}...")

    try:
        for kw in target_names:
            docs = list(db.collection(TRACKS_COL).where("title", ">=", kw).where("title", "<=", kw + "\uf8ff").stream())
            for d in docs:
                data = d.to_dict()
                t = data.get("title", "")
                a = data.get("artist", "")
                fix_track(d.id, t, a)
                time.sleep(1)
    except Exception as e:
        print(f"\nFirestore check note: {e}")
        print("Note: If Firestore quota is temporarily exhausted on Spark plan, run this script once the daily quota resets.")
