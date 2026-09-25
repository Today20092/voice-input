"""Collect license texts from the exact locked Rust dependencies into the APK assets."""
import json
import pathlib
import subprocess
import urllib.error
import urllib.request
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]
CRATE = ROOT / "app/src/main/rust/harper_android"
metadata = json.loads(subprocess.check_output(
    ["cargo", "metadata", "--locked", "--format-version", "1", "--filter-platform", "aarch64-linux-android"], cwd=CRATE
))
nodes = {node["id"]: node for node in metadata["resolve"]["nodes"]}
resolved_ids = set()
pending = [metadata["resolve"]["root"]]
while pending:
    package_id = pending.pop()
    if package_id in resolved_ids:
        continue
    resolved_ids.add(package_id)
    pending.extend(dependency["pkg"] for dependency in nodes[package_id]["deps"])
packages = [package for package in metadata["packages"] if package["id"] in resolved_ids]
sections = ["Harper Android beta and locked Rust dependencies\n"
            "Harper 2.11.0, Apache-2.0\nhttps://github.com/Automattic/harper\n"
            "Only the dependencies linked into the Android library apply at runtime.\n"]
missing = []
for package in sorted(packages, key=lambda item: (item["name"], item["version"])):
    if package["name"] == "harper_android":
        continue
    directory = pathlib.Path(package["manifest_path"]).parent
    sections.append(f"\n{'=' * 72}\n{package['name']} {package['version']}\n"
                    f"License: {package.get('license') or 'See texts below'}\n"
                    f"Source: {package.get('repository') or package.get('source') or ''}\n")
    files = set()
    for base in [directory, directory / "licenses", directory / "license"]:
        if base.is_dir():
            files.update(p for p in base.iterdir() if p.is_file() and
                         p.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "NOTICE", "COPYRIGHT")))
    if package.get("license_file"):
        files.add(directory / package["license_file"])
    if package["name"].startswith("harper-"):
        files.update(p for p in directory.parent.glob("LICENSE*") if p.is_file())
    if not files:
        # Some published workspace crates omit their repository-root license texts.
        # Retrieve those from the exact publisher revision embedded in the locked crate,
        # never from a moving default branch or an unrelated crate's license.
        vcs_file = directory / ".cargo_vcs_info.json"
        repository = (package.get("repository") or "").removesuffix(".git").rstrip("/")
        revision = json.loads(vcs_file.read_text()).get("git", {}).get("sha1", "") if vcs_file.is_file() else ""
        fetched = 0
        if repository.startswith("https://github.com/") and len(revision) == 40:
            # Crate metadata may point to a workspace subdirectory via /tree/main/...
            # Licenses are at the repository root, not below that web UI path.
            repo_path = "/".join(urllib.parse.urlparse(repository).path.strip("/").split("/")[:2]).removesuffix(".git")
            for name in ["LICENSE-APACHE", "LICENSE-MIT", "LICENSE.APACHE", "LICENSE.MIT", "LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING"]:
                url = f"https://raw.githubusercontent.com/{repo_path}/{revision}/{name}"
                try:
                    with urllib.request.urlopen(url, timeout=30) as response:
                        contents = response.read().decode("utf-8")
                    sections.append(f"\n--- {url} ---\n{contents}\n")
                    fetched += 1
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        raise
        if not fetched:
            if package.get("license") == "CC0-1.0":
                url = "https://raw.githubusercontent.com/spdx/license-list-data/v3.27.0/text/CC0-1.0.txt"
                with urllib.request.urlopen(url, timeout=30) as response:
                    sections.append(f"\n--- {url} ---\n{response.read().decode('utf-8')}\n")
            else:
                missing.append(f"{package['name']} {package['version']}: {repository} @ {revision}")
    for path in sorted(files):
        sections.append(f"\n--- {path.name} ---\n{path.read_text(errors='replace')}\n")
if missing:
    raise SystemExit("Missing publisher license texts:\n" + "\n".join(missing))
target = ROOT / "app/src/main/assets/HARPER-NOTICES.txt"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text("\n".join(sections))
print(f"Bundled license texts for {len(packages) - 1} Rust packages")
