import base64, hashlib, os, re, urllib.parse, requests

BASE = os.environ.get("CT_BASE", "http://localhost:8080")
CLIENT = "chicktrack-mobile"
REDIRECT = "chicktrack://oauth2/callback"

def _field(html, name):
    m = re.search(r'name="%s"[^>]*value="([^"]*)"' % re.escape(name), html) or \
        re.search(r'value="([^"]*)"[^>]*name="%s"' % re.escape(name), html)
    return m.group(1) if m else None

def login(username, password, scopes=("openid", "profile", "api.read", "api.write")):
    """Authorization Code + PKCE, exactly as the mobile app does it. Returns the token response."""
    s = requests.Session()
    verifier = base64.urlsafe_b64encode(os.urandom(40)).rstrip(b"=").decode()
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = base64.urlsafe_b64encode(os.urandom(12)).decode()
    params = dict(response_type="code", client_id=CLIENT, redirect_uri=REDIRECT, scope=" ".join(scopes),
                  state=state, code_challenge=challenge, code_challenge_method="S256")
    html = {"Accept": "text/html"}
    r = s.get(BASE + "/oauth2/authorize", params=params, headers=html, allow_redirects=False)
    assert r.status_code == 302 and "/login" in r.headers["Location"], (r.status_code, r.headers)
    page = s.get(BASE + "/login", headers=html).text
    r = s.post(BASE + "/login", data=dict(username=username, password=password, _csrf=_field(page, "_csrf")),
               headers=html, allow_redirects=False)
    if "error" in r.headers.get("Location", ""):
        raise PermissionError("login failed for " + username)
    r = s.get(BASE + "/oauth2/authorize", params=params, headers=html, allow_redirects=False)
    if r.status_code == 200:  # consent screen
        consent = r.text
        data = [("client_id", CLIENT), ("state", _field(consent, "state"))]
        data += [("scope", sc) for sc in scopes if sc != "openid"]
        csrf = _field(consent, "_csrf")
        if csrf:
            data.append(("_csrf", csrf))
        r = s.post(BASE + "/oauth2/authorize", data=data, headers=html, allow_redirects=False)
    loc = r.headers.get("Location", "")
    assert loc.startswith(REDIRECT), (r.status_code, loc, r.text[:300])
    q = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query)
    assert q["state"][0] == state
    t = requests.post(BASE + "/oauth2/token", data=dict(grant_type="authorization_code", code=q["code"][0],
                      redirect_uri=REDIRECT, client_id=CLIENT, code_verifier=verifier))
    assert t.status_code == 200, (t.status_code, t.text)
    return t.json()

def refresh(refresh_token):
    return requests.post(BASE + "/oauth2/token", data=dict(grant_type="refresh_token",
                         refresh_token=refresh_token, client_id=CLIENT))
