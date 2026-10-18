import os, sys, glob, base64, json, urllib.request, urllib.error

tok = os.environ["GH_TOKEN"]
repo = os.environ["GITHUB_REPOSITORY"]
run = os.environ.get("GITHUB_RUN_NUMBER", "0")
dirn = f"apkbin/run{run}"

chunks = sorted(glob.glob("chunks/part_*"))
print("chunks:", len(chunks))
ok = 0
for i, f in enumerate(chunks):
    b64 = base64.b64encode(open(f, "rb").read()).decode()
    path = f"{dirn}/p{i}.b64"
    body = json.dumps({"message": f"chunk {i}", "content": b64, "branch": "main"}).encode()
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/contents/{path}", method="PUT", data=body)
    req.add_header("Authorization", f"token {tok}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            print("ok", path, r.status); ok += 1
    except urllib.error.HTTPError as e:
        print("fail", path, e.code, e.read().decode()[:200])
print(f"uploaded {ok}/{len(chunks)}")
