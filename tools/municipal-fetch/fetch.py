#!/usr/bin/env python3
"""Download a city's own published collection documents, unchanged.

This is deliberately not a parser. It does not guess which weekday a bin goes
out on; it fetches the city's pages and the PDFs they link to, stores them
byte for byte, and writes a manifest saying where each file came from. Reading
them and writing the migration is a separate, human-checked step -- see
docs/loading-a-city.md.

The crawl stays polite and narrow:
  * only the start URLs' own hosts,
  * robots.txt is honoured,
  * a delay between requests,
  * HTML pages are followed only when their URL or link text looks like it is
    about waste collection (keywords below), up to --max-depth hops,
  * a hard cap on the number of pages fetched.

Standard library only, so it runs on a bare GitHub Actions runner.

Usage:
  python3 fetch.py --request request.json --out data/municipal-sources
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from collections import deque
from html.parser import HTMLParser
from pathlib import Path

USER_AGENT = "tri-rive-nord-source-fetcher/1.0 (reads published collection calendars)"

# Matched against the accent-stripped, lower-cased URL and link text.
KEYWORDS = (
    "collecte", "matiere", "residuel", "recycl", "dechet", "ordure", "compost",
    "organique", "calendrier", "ecocentre", "encombrant", "tri", "bac",
    "environnement", "poubelle", "residus", "rdd", "sapin", "feuille",
)

DOCUMENT_TYPES = ("application/pdf",)
DOCUMENT_SUFFIXES = (".pdf",)


def fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    return "".join(c for c in text if not unicodedata.combining(c)).lower()


def relevant(url: str, link_text: str) -> bool:
    haystack = fold(urllib.parse.unquote(url) + " " + link_text)
    # "tri" and "bac" are short; require them as whole words to avoid noise
    # like "patrimoine" or "baccalaureat".
    for kw in KEYWORDS:
        if kw in ("tri", "bac"):
            if re.search(rf"(?<![a-z]){kw}(?![a-z])", haystack):
                return True
        elif kw in haystack:
            return True
    return False


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, str]] = []
        self._href: str | None = None
        self._text: list[str] = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self._href = dict(attrs).get("href")
            self._text = []

    def handle_data(self, data):
        if self._href is not None:
            self._text.append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self._href is not None:
            self.links.append((self._href, " ".join("".join(self._text).split())))
            self._href = None


def safe_name(url: str, content_type: str) -> str:
    parsed = urllib.parse.urlparse(url)
    stem = (parsed.netloc + parsed.path).strip("/") or parsed.netloc
    if parsed.query:
        stem += "_" + parsed.query
    stem = re.sub(r"[^A-Za-z0-9._-]+", "_", urllib.parse.unquote(stem))[:150]
    digest = hashlib.sha256(url.encode()).hexdigest()[:8]
    if "pdf" in content_type and not stem.lower().endswith(".pdf"):
        stem += ".pdf"
    elif "html" in content_type and not stem.lower().endswith((".html", ".htm")):
        stem += ".html"
    return f"{digest}_{stem}"


class Fetcher:
    def __init__(self, delay: float, timeout: float) -> None:
        self.delay = delay
        self.timeout = timeout
        self.robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
        self.last_request = 0.0

    def _wait(self) -> None:
        pause = self.delay - (time.monotonic() - self.last_request)
        if pause > 0:
            time.sleep(pause)
        self.last_request = time.monotonic()

    def allowed(self, url: str) -> bool:
        parsed = urllib.parse.urlparse(url)
        origin = f"{parsed.scheme}://{parsed.netloc}"
        if origin not in self.robots:
            rp = urllib.robotparser.RobotFileParser()
            try:
                self._wait()
                req = urllib.request.Request(origin + "/robots.txt",
                                             headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    rp.parse(resp.read().decode("utf-8", "replace").splitlines())
            except urllib.error.HTTPError as e:
                # 4xx: no robots.txt, everything allowed. 5xx: be conservative.
                rp = None if e.code < 500 else False
            except Exception:
                rp = None
            self.robots[origin] = rp
        rp = self.robots[origin]
        if rp is False:
            return False
        return rp is None or rp.can_fetch(USER_AGENT, url)

    def get(self, url: str) -> tuple[str, str, bytes]:
        self._wait()
        req = urllib.request.Request(url, headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/pdf;q=0.9,*/*;q=0.5",
            "Accept-Language": "fr-CA,fr;q=0.9,en;q=0.5",
        })
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            ctype = resp.headers.get_content_type()
            return resp.geturl(), ctype, resp.read()


def crawl(city: dict, out_root: Path, max_pages: int, max_depth: int,
          fetcher: Fetcher) -> dict:
    slug = city["slug"]
    stamp = dt.datetime.now(dt.timezone.utc)
    out_dir = out_root / slug / stamp.strftime("%Y-%m-%d")
    out_dir.mkdir(parents=True, exist_ok=True)

    def with_www_variants(host: str) -> set[str]:
        bare = host[4:] if host.startswith("www.") else host
        return {bare, "www." + bare}

    hosts: set[str] = set()
    for u in city["start_urls"]:
        hosts |= with_www_variants(urllib.parse.urlparse(u).netloc)
    hosts |= set(city.get("extra_hosts", []))
    queue: deque[tuple[str, int, str | None]] = deque(
        (u, 0, None) for u in city["start_urls"])
    seen: set[str] = set()
    files, errors = [], []
    pages = 0

    while queue and pages < max_pages:
        url, depth, found_on = queue.popleft()
        url = urllib.parse.urldefrag(url)[0]
        if url in seen:
            continue
        seen.add(url)
        if not fetcher.allowed(url):
            errors.append({"url": url, "error": "disallowed by robots.txt"})
            continue
        try:
            final_url, ctype, body = fetcher.get(url)
        except Exception as e:  # record and move on; a dead candidate is data
            errors.append({"url": url, "error": f"{type(e).__name__}: {e}"})
            print(f"  ! {url}: {e}", file=sys.stderr)
            continue
        pages += 1
        if depth == 0:
            # A start URL that redirects elsewhere (www -> bare, .qc.ca -> .ca)
            # makes the new host part of the city's own site.
            hosts |= with_www_variants(urllib.parse.urlparse(final_url).netloc)
        is_doc = ctype in DOCUMENT_TYPES or final_url.lower().endswith(DOCUMENT_SUFFIXES)
        is_html = ctype in ("text/html", "application/xhtml+xml")
        # Keep every document, and every HTML page that is about collection.
        # The start pages are kept too so the trail can be followed.
        keep = is_doc or depth == 0 or (is_html and relevant(final_url, ""))
        if keep:
            name = safe_name(final_url, ctype)
            (out_dir / name).write_bytes(body)
            files.append({
                "file": name,
                "url": final_url,
                "requested_url": url,
                "found_on": found_on,
                "content_type": ctype,
                "bytes": len(body),
                "sha256": hashlib.sha256(body).hexdigest(),
            })
            print(f"  saved {final_url} ({ctype}, {len(body)} bytes)")
        if not is_html or depth >= max_depth:
            continue
        parser = LinkParser()
        parser.feed(body.decode("utf-8", "replace"))
        for href, text in parser.links:
            nxt = urllib.parse.urljoin(final_url, href.strip())
            # Hrefs written with raw spaces or accents are common on city
            # sites; urllib refuses them unless they are percent-encoded.
            nxt = urllib.parse.quote(nxt, safe=":/?#[]@!$&'()*+,;=%~")
            p = urllib.parse.urlparse(nxt)
            if p.scheme not in ("http", "https") or p.netloc not in hosts:
                continue
            if nxt.lower().endswith(DOCUMENT_SUFFIXES):
                # PDFs on a collection page are what we came for; on any other
                # page only if they look like it.
                if relevant(final_url, "") or relevant(nxt, text):
                    queue.appendleft((nxt, depth + 1, final_url))
            elif relevant(nxt, text):
                queue.append((nxt, depth + 1, final_url))

    manifest = {
        "slug": slug,
        "name": city.get("name", slug),
        "fetched_at": stamp.isoformat(timespec="seconds"),
        "start_urls": city["start_urls"],
        "pages_fetched": pages,
        "page_cap_reached": pages >= max_pages,
        "files": files,
        "errors": errors,
        "note": "Raw copies of the city's own pages and documents. Not parsed, "
                "not verified. See docs/loading-a-city.md before using.",
    }
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--request", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--delay", type=float, default=2.0, help="seconds between requests")
    ap.add_argument("--timeout", type=float, default=30.0)
    args = ap.parse_args()

    request = json.loads(args.request.read_text())
    max_pages = int(request.get("max_pages", 40))
    max_depth = int(request.get("max_depth", 2))
    fetcher = Fetcher(args.delay, args.timeout)

    any_doc = False
    for city in request["cities"]:
        print(f"== {city['slug']}")
        m = crawl(city, args.out, max_pages, max_depth, fetcher)
        docs = [f for f in m["files"] if f["content_type"] in DOCUMENT_TYPES
                or f["url"].lower().endswith(DOCUMENT_SUFFIXES)]
        any_doc |= bool(m["files"])
        print(f"   {m['pages_fetched']} fetched, {len(m['files'])} saved "
              f"({len(docs)} documents), {len(m['errors'])} errors")
    # Exit non-zero only when nothing at all could be saved: that means every
    # start URL was wrong or unreachable, which the run should show as failed.
    return 0 if any_doc else 1


if __name__ == "__main__":
    sys.exit(main())
