"""End-to-end HTTP test of the ChickTrack API. Signs in each role through OAuth2 + PKCE and
exercises every endpoint with positive and negative cases. Usage: python3 api_test.py"""
import json
import os
import sys
import time
import uuid

import requests

import oauth

ADMIN_USERNAME = os.environ.get("CT_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ["CT_ADMIN_PASSWORD"]

BASE = oauth.BASE
API = BASE + "/api/v1"
RESULTS = []
SFX = uuid.uuid4().hex[:5]
TODAY = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 5.5 * 3600))  # Asia/Colombo date


class Client:
    def __init__(self, name, token):
        self.name, self.token = name, token

    def call(self, method, path, body=None, params=None, headers=None, raw=False):
        h = {"Authorization": "Bearer " + self.token} if self.token else {}
        h.update(headers or {})
        r = requests.request(method, API + path if path.startswith("/") else path,
                             json=body, params=params, headers=h, timeout=30)
        if raw:
            return r
        try:
            j = r.json()
        except ValueError:
            j = None
        return r.status_code, j, r


def check(area, name, ok, detail=""):
    RESULTS.append((area, name, bool(ok), detail))
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {area}: {name}" + ("" if ok else f"  -> {detail}"))
    return ok


def expect(area, name, resp, status, code=None):
    s, j, r = resp
    ok = s == status and (code is None or (j or {}).get("code") == code)
    check(area, name, ok, f"got {s} {json.dumps(j)[:300] if j is not None else r.text[:200]}")
    return j


def data(resp):
    body = resp if isinstance(resp, dict) or resp is None else resp[1]
    return (body or {}).get("data")


def login(user, password):
    return Client(user, oauth.login(user, password)["access_token"])


def main():
    # ---------------------------------------------------------------- platform
    A = "Platform"
    check(A, "health is public and UP", requests.get(BASE + "/actuator/health").json().get("status") == "UP")
    check(A, "OpenAPI JSON served", requests.get(BASE + "/v3/api-docs").status_code == 200)
    check(A, "OIDC discovery served", requests.get(BASE + "/.well-known/openid-configuration").json()["issuer"] == BASE)
    check(A, "JWKS published", len(requests.get(BASE + "/oauth2/jwks").json()["keys"]) == 1)
    anon = Client("anon", None)
    expect(A, "no token -> 401", anon.call("GET", "/me"), 401)
    expect(A, "garbage token -> 401", Client("x", "abc.def.ghi").call("GET", "/me"), 401)
    try:
        oauth.login(ADMIN_USERNAME, "wrong-password")
        check(A, "wrong password is rejected", False, "login succeeded")
    except PermissionError:
        check(A, "wrong password is rejected", True)

    admin = login(ADMIN_USERNAME, ADMIN_PASSWORD)
    t = oauth.login(ADMIN_USERNAME, ADMIN_PASSWORD)
    r1 = oauth.refresh(t["refresh_token"])
    check(A, "refresh token issued and accepted", r1.status_code == 200 and "access_token" in r1.json(), r1.text[:200])
    check(A, "refresh token rotates", r1.json().get("refresh_token") not in (None, t["refresh_token"]))
    check(A, "used refresh token is rejected", oauth.refresh(t["refresh_token"]).status_code == 400)
    me = data(admin.call("GET", "/me"))
    check(A, "/me returns roles", me and me["roles"] == ["OWNER"], me)
    s, j, r = admin.call("GET", "/me", headers={"X-Request-Id": "req-42"})
    s, j, r = anon.call("GET", "/branches/" + str(uuid.uuid4()), headers={"X-Request-Id": "req-42"})

    # ---------------------------------------------------------------- master data
    A = "Branches"
    kandy = data(expect(A, "create Kandy", admin.call("POST", "/branches", {"code": "KDY" + SFX, "name": "Kandy " + SFX}), 201))
    colombo = data(expect(A, "create Colombo", admin.call("POST", "/branches", {"code": "CMB" + SFX, "name": "Colombo " + SFX}), 201))
    closed = data(expect(A, "create Gampaha", admin.call("POST", "/branches", {"code": "GMP" + SFX, "name": "Gampaha " + SFX}), 201))
    expect(A, "duplicate code -> 409", admin.call("POST", "/branches", {"code": "KDY" + SFX, "name": "X"}), 409, "CONFLICT")
    expect(A, "blank name -> 400", admin.call("POST", "/branches", {"code": "Z" + SFX, "name": ""}), 400, "VALIDATION_FAILED")
    upd = data(expect(A, "update", admin.call("PUT", f"/branches/{kandy['id']}", {"name": "Kandy Central " + SFX, "phone": "081"}), 200))
    check(A, "update applied", upd and upd["name"].startswith("Kandy Central"))
    expect(A, "deactivate Gampaha", admin.call("PATCH", f"/branches/{closed['id']}/status", {"active": False}), 200)
    lst = data(expect(A, "list with search", admin.call("GET", "/branches", params={"search": SFX}), 200))
    check(A, "list finds 3", lst and lst["totalElements"] == 3, lst and lst["totalElements"])
    expect(A, "get", admin.call("GET", f"/branches/{kandy['id']}"), 200)
    expect(A, "unknown id -> 404", admin.call("GET", f"/branches/{uuid.uuid4()}"), 404, "RESOURCE_NOT_FOUND")
    expect(A, "bad uuid -> 400", admin.call("GET", "/branches/not-a-uuid"), 400, "VALIDATION_FAILED")

    A = "Suppliers"
    sup = data(expect(A, "create", admin.call("POST", "/suppliers", {"name": "Farm " + SFX, "phone": "077", "email": "farm@example.com", "paymentTerms": "7 days"}), 201))
    expect(A, "invalid email -> 400", admin.call("POST", "/suppliers", {"name": "Bad", "email": "nope"}), 400, "VALIDATION_FAILED")
    expect(A, "update", admin.call("PUT", f"/suppliers/{sup['id']}", {"name": "Farm " + SFX, "contactPerson": "Silva"}), 200)
    expect(A, "deactivate", admin.call("PATCH", f"/suppliers/{sup['id']}/status", {"active": False}), 200)
    expect(A, "reactivate", admin.call("PATCH", f"/suppliers/{sup['id']}/status", {"active": True}), 200)
    expect(A, "list", admin.call("GET", "/suppliers", params={"search": SFX}), 200)
    expect(A, "get", admin.call("GET", f"/suppliers/{sup['id']}"), 200)

    A = "Chicken types"
    broiler = data(expect(A, "create Broiler", admin.call("POST", "/chicken-types", {"name": "Broiler " + SFX}), 201))
    layer = data(expect(A, "create Layer", admin.call("POST", "/chicken-types", {"name": "Layer " + SFX}), 201))
    expect(A, "duplicate name -> 409", admin.call("POST", "/chicken-types", {"name": "Broiler " + SFX}), 409)
    expect(A, "update", admin.call("PUT", f"/chicken-types/{layer['id']}", {"name": "Layer " + SFX, "description": "Spent hens"}), 200)
    expect(A, "list", admin.call("GET", "/chicken-types"), 200)
    expect(A, "get", admin.call("GET", f"/chicken-types/{broiler['id']}"), 200)

    A = "Expense categories"
    transport = data(expect(A, "create", admin.call("POST", "/expense-categories", {"name": "Transport " + SFX}), 201))
    expect(A, "update", admin.call("PUT", f"/expense-categories/{transport['id']}", {"name": "Transport " + SFX, "description": "Lorry"}), 200)
    expect(A, "list", admin.call("GET", "/expense-categories"), 200)
    expect(A, "get", admin.call("GET", f"/expense-categories/{transport['id']}"), 200)

    # ---------------------------------------------------------------- users
    A = "Users"
    roles = data(expect(A, "list roles", admin.call("GET", "/roles"), 200))
    check(A, "8 roles", roles and len(roles) == 8, roles)
    pw = "Start-pass-123"
    def mk(name, role, branches=()):
        return data(expect(A, f"create {role.lower()}", admin.call("POST", "/users", {
            "username": name + SFX, "password": pw, "fullName": name.title(), "roles": [role],
            "branchIds": [b["id"] for b in branches], "mustChangePassword": False}), 201))
    u_proc = mk("proc", "PROCUREMENT_MANAGER")
    u_wh = mk("wh", "WAREHOUSE_MANAGER")
    u_dist = mk("dist", "DISTRIBUTION_MANAGER")
    u_acc = mk("acc", "ACCOUNTANT")
    u_kandy = mk("kandy", "BRANCH_RECEIVER", [kandy])
    u_cmb = mk("cmb", "BRANCH_RECEIVER", [colombo])
    u_none = mk("nobranch", "BRANCH_RECEIVER")
    u_view = mk("viewer", "VIEWER", [kandy])
    check(A, "password not returned", u_proc and "password" not in json.dumps(u_proc) and "passwordHash" not in json.dumps(u_proc))
    expect(A, "duplicate username (case) -> 409", admin.call("POST", "/users", {"username": ("PROC" + SFX).upper(), "password": pw, "fullName": "X", "roles": ["VIEWER"]}), 409, "CONFLICT")
    expect(A, "unknown role -> 400", admin.call("POST", "/users", {"username": "x" + SFX, "password": pw, "fullName": "X", "roles": ["KING"]}), 400, "VALIDATION_FAILED")
    expect(A, "short password -> 400", admin.call("POST", "/users", {"username": "y" + SFX, "password": "short", "fullName": "X", "roles": ["VIEWER"]}), 400, "VALIDATION_FAILED")
    expect(A, "inactive branch -> 400", admin.call("POST", "/users", {"username": "z" + SFX, "password": pw, "fullName": "X", "roles": ["VIEWER"], "branchIds": [closed["id"]]}), 400, "BUSINESS_RULE_VIOLATION")
    lst = data(expect(A, "list by role", admin.call("GET", "/users", params={"role": "BRANCH_RECEIVER", "search": SFX}), 200))
    check(A, "3 receivers listed", lst and lst["totalElements"] == 3, lst and lst["totalElements"])
    lst = data(expect(A, "list by branch", admin.call("GET", "/users", params={"branchId": kandy["id"]}), 200))
    check(A, "2 users on Kandy", lst and lst["totalElements"] == 2, lst and lst["totalElements"])
    expect(A, "get", admin.call("GET", f"/users/{u_view['id']}"), 200)
    expect(A, "update", admin.call("PUT", f"/users/{u_view['id']}", {"fullName": "Viewer One", "email": "v@example.com"}), 200)
    v = data(expect(A, "set branches", admin.call("PUT", f"/users/{u_view['id']}/branches", {"branchIds": [kandy["id"], colombo["id"]]}), 200))
    check(A, "branches replaced", v and len(v["branchIds"]) == 2)
    expect(A, "self-deactivate -> 400", admin.call("PATCH", f"/users/{me['id']}/status", {"active": False}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "self-demote owner -> 400", admin.call("PUT", f"/users/{me['id']}/roles", {"roles": ["ACCOUNTANT"]}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "owner password reset via /users -> 400", admin.call("POST", f"/users/{me['id']}/password", {}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "create second owner -> 400", admin.call("POST", "/users", {"username": "own2" + SFX, "fullName": "X", "roles": ["OWNER"]}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "promote to owner -> 400", admin.call("PUT", f"/users/{u_view['id']}/roles", {"roles": ["OWNER"]}), 400, "BUSINESS_RULE_VIOLATION")
    # generated credentials + forced change at first sign-in
    gen = data(expect(A, "create with generated password", admin.call("POST", "/users", {"username": "gen" + SFX, "fullName": "Gen", "roles": ["VIEWER"]}), 201))
    check(A, "temporary password returned once", gen and len(gen.get("temporaryPassword", "")) == 12 and gen["mustChangePassword"], gen)
    g = login(gen["username"], gen["temporaryPassword"])
    expect(A, "must-change user: /me allowed", g.call("GET", "/me"), 200)
    expect(A, "must-change user: other API -> 403", g.call("GET", "/branches"), 403, "PASSWORD_CHANGE_REQUIRED")
    expect(A, "must-change user changes password", g.call("POST", "/me/password", {"currentPassword": gen["temporaryPassword"], "newPassword": "Own-pass-123"}), 200)
    expect(A, "same token works after change", g.call("GET", "/branches"), 200)
    rs = data(expect(A, "reset with generated password", admin.call("POST", f"/users/{gen['id']}/password"), 200))
    check(A, "reset returns new temporary password", rs and len(rs.get("temporaryPassword", "")) == 12 and rs["mustChangePassword"], rs)

    # login every role
    proc, wh, dist, acc = (login(u["username"], pw) for u in (u_proc, u_wh, u_dist, u_acc))
    kr, cr, nb, viewer = (login(u["username"], pw) for u in (u_kandy, u_cmb, u_none, u_view))
    expect(A, "non-owner cannot list users -> 403", acc.call("GET", "/users"), 403, "FORBIDDEN")
    expect(A, "non-owner cannot create users -> 403", kr.call("POST", "/users", {"username": "h" + SFX, "password": pw, "fullName": "H", "roles": ["OWNER"]}), 403, "FORBIDDEN")

    # deactivate / revoke / reset
    tok = oauth.login(u_none["username"], pw)
    expect(A, "deactivate user", admin.call("PATCH", f"/users/{u_none['id']}/status", {"active": False}), 200)
    check(A, "deactivated user's refresh token revoked", oauth.refresh(tok["refresh_token"]).status_code == 400)
    try:
        oauth.login(u_none["username"], pw)
        check(A, "deactivated user cannot sign in", False)
    except PermissionError:
        check(A, "deactivated user cannot sign in", True)
    expect(A, "reactivate", admin.call("PATCH", f"/users/{u_none['id']}/status", {"active": True}), 200)
    expect(A, "reset password", admin.call("POST", f"/users/{u_none['id']}/password", {"newPassword": "Reset-pass-456", "mustChangePassword": False}), 200)
    nb = login(u_none["username"], "Reset-pass-456")
    expect(A, "own password: wrong current -> 400", nb.call("POST", "/me/password", {"currentPassword": "bad", "newPassword": "New-pass-789"}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "own password change", nb.call("POST", "/me/password", {"currentPassword": "Reset-pass-456", "newPassword": "New-pass-789"}), 200)
    nb = login(u_none["username"], "New-pass-789")

    # ---------------------------------------------------------------- pricing
    A = "Daily prices"
    expect(A, "receiver cannot set prices -> 403", kr.call("POST", "/daily-prices", {"chickenTypeId": broiler["id"], "purchasePricePerKg": "1", "sellingPricePerKg": "1", "effectiveFrom": "2026-01-01T00:00:00Z"}), 403)
    p_kandy = data(expect(A, "branch price Broiler@Kandy", proc.call("POST", "/daily-prices", {"chickenTypeId": broiler["id"], "branchId": kandy["id"], "purchasePricePerKg": "800", "sellingPricePerKg": "1000", "effectiveFrom": "2026-01-01T00:00:00Z"}), 201))
    expect(A, "global price Broiler", proc.call("POST", "/daily-prices", {"chickenTypeId": broiler["id"], "purchasePricePerKg": "790", "sellingPricePerKg": "980", "effectiveFrom": "2026-01-01T00:00:00Z"}), 201)
    expect(A, "negative price -> 400", proc.call("POST", "/daily-prices", {"chickenTypeId": broiler["id"], "purchasePricePerKg": "-1", "sellingPricePerKg": "1", "effectiveFrom": "2026-01-01T00:00:00Z"}), 400)
    cur = data(expect(A, "current for Kandy", kr.call("GET", "/daily-prices/current", params={"chickenTypeId": broiler["id"], "branchId": kandy["id"]}), 200))
    check(A, "branch price wins", cur and float(cur["sellingPricePerKg"]) == 1000, cur)
    expect(A, "current with no price -> 404", kr.call("GET", "/daily-prices/current", params={"chickenTypeId": layer["id"], "branchId": kandy["id"]}), 404)
    expect(A, "trend", proc.call("GET", "/daily-prices/trend", params={"chickenTypeId": broiler["id"]}), 200)
    expect(A, "list", proc.call("GET", "/daily-prices", params={"chickenTypeId": broiler["id"]}), 200)
    expect(A, "get", proc.call("GET", f"/daily-prices/{p_kandy['id']}"), 200)

    A = "Margin rules"
    expect(A, "procurement cannot set margin -> 403", proc.call("POST", "/company-margin-rules", {"chickenTypeId": broiler["id"], "markupPercent": "10", "effectiveFrom": "2026-01-01T00:00:00Z"}), 403)
    rule = data(expect(A, "create 10% Broiler", acc.call("POST", "/company-margin-rules", {"chickenTypeId": broiler["id"], "markupPercent": "10", "effectiveFrom": "2026-01-01T00:00:00Z"}), 201))
    expect(A, "duplicate rule -> 409", acc.call("POST", "/company-margin-rules", {"chickenTypeId": broiler["id"], "markupPercent": "12", "effectiveFrom": "2026-01-01T00:00:00Z"}), 409)
    expect(A, "list", acc.call("GET", "/company-margin-rules", params={"chickenTypeId": broiler["id"]}), 200)
    expect(A, "get", acc.call("GET", f"/company-margin-rules/{rule['id']}"), 200)

    # ---------------------------------------------------------------- procurement
    A = "Procurement"
    body = {"supplierId": sup["id"], "procurementDate": TODAY, "notes": "Morning load",
            "items": [{"chickenTypeId": broiler["id"], "paidWeightKg": "500", "purchasePricePerKg": "800",
                       "commissionMethod": "PER_KG", "commissionValue": "10", "quantity": 250},
                      {"chickenTypeId": layer["id"], "paidWeightKg": "200", "purchasePricePerKg": "600",
                       "commissionMethod": "FIXED", "commissionValue": "0"}],
            "expenses": [{"categoryId": transport["id"], "description": "Lorry", "amount": "7000"}]}
    expect(A, "receiver cannot buy -> 403", kr.call("POST", "/procurements", body), 403)
    prc = data(expect(A, "create", proc.call("POST", "/procurements", body), 201))
    if prc:
        check(A, "reference PRC-", prc["reference"].startswith("PRC-"), prc["reference"])
        check(A, "status RECEIVED", prc["status"] == "RECEIVED", prc["status"])
        check(A, "totals: 520000 + 5000 + 7000", float(prc["totalCost"]) == 532000, prc["totalCost"])
        b_item = next(i for i in prc["items"] if i["chickenTypeId"] == broiler["id"])
        check(A, "Broiler landed cost 820/kg", float(b_item["landedCostPerKg"]) == 820, b_item)
    expect(A, "future date -> 400", proc.call("POST", "/procurements", {**body, "procurementDate": "2099-01-01"}), 400, "VALIDATION_FAILED")
    expect(A, "no items -> 400", proc.call("POST", "/procurements", {**body, "items": []}), 400, "VALIDATION_FAILED")
    expect(A, "list", proc.call("GET", "/procurements", params={"supplierId": sup["id"]}), 200)
    expect(A, "get", proc.call("GET", f"/procurements/{prc['id']}"), 200)

    # ---------------------------------------------------------------- inventory
    A = "Inventory"
    expect(A, "receiver cannot view warehouse -> 403", kr.call("GET", "/stock-batches"), 403)
    batches = data(expect(A, "list batches", wh.call("GET", "/stock-batches", params={"chickenTypeId": broiler["id"], "available": "true"}), 200))
    bb = batches["content"][0] if batches and batches["content"] else None
    check(A, "Broiler batch 500 kg available", bb and float(bb["availableWeightKg"]) == 500, bb)
    lb = data(wh.call("GET", "/stock-batches", params={"chickenTypeId": layer["id"]}))["content"][0]
    expect(A, "get batch", wh.call("GET", f"/stock-batches/{bb['id']}"), 200)
    mv = data(expect(A, "list movements", wh.call("GET", "/inventory-movements", params={"batchId": bb["id"]}), 200))
    check(A, "RECEIPT movement", mv and [m["type"] for m in mv["content"]] == ["RECEIPT"], mv)
    adj = data(expect(A, "damage 10 kg of Layer", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "DAMAGE", "weightKg": "10", "reason": "Crate damage"}), 201))
    check(A, "adjustment reference ADJ-", adj and adj["reference"].startswith("ADJ-") and float(adj["weightChangeKg"]) == -10, adj)
    expect(A, "loss above stock -> 409", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "LOSS", "weightKg": "500", "reason": "x"}), 409, "INSUFFICIENT_STOCK")
    expect(A, "adjustment without direction -> 400", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "ADJUSTMENT", "weightKg": "1", "reason": "x"}), 400, "VALIDATION_FAILED")
    expect(A, "increase above received -> 400", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "RETURN", "weightKg": "20", "reason": "x"}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "missing reason -> 400", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "RETURN", "weightKg": "1", "reason": ""}), 400, "VALIDATION_FAILED")
    expect(A, "recount +5", wh.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "ADJUSTMENT", "direction": "INCREASE", "weightKg": "5", "reason": "Recount"}), 201)
    expect(A, "distribution manager cannot adjust -> 403", dist.call("POST", "/stock-adjustments", {"stockBatchId": lb["id"], "type": "DAMAGE", "weightKg": "1", "reason": "x"}), 403)
    expect(A, "list adjustments", wh.call("GET", "/stock-adjustments", params={"batchId": lb["id"]}), 200)
    expect(A, "movements by type", wh.call("GET", "/inventory-movements", params={"type": "DAMAGE"}), 200)

    # ---------------------------------------------------------------- distribution
    A = "Distribution"
    dbody = {"branchId": kandy["id"], "distributionDate": TODAY,
             "items": [{"chickenTypeId": broiler["id"], "weightKg": "300"}],
             "expenses": [{"categoryId": transport["id"], "description": "Delivery", "amount": "3000", "allocationMethod": "BY_WEIGHT"}]}
    expect(A, "receiver cannot create -> 403", kr.call("POST", "/distributions", dbody), 403)
    d1 = data(expect(A, "create draft", dist.call("POST", "/distributions", dbody), 201))
    if d1:
        line = d1["items"][0]
        check(A, "transfer price 820 + 82 + 10 = 912", float(line["transferPricePerKg"]) == 912, line)
        check(A, "total transfer 273600", float(d1["totalTransferValue"]) == 273600, d1["totalTransferValue"])
        check(A, "our margin 24600", float(d1["totalOurMargin"]) == 24600, d1["totalOurMargin"])
    bstate = data(wh.call("GET", f"/stock-batches/{bb['id']}"))
    check(A, "draft reserves 300 kg", float(bstate["reservedWeightKg"]) == 300 and float(bstate["availableWeightKg"]) == 200, bstate)
    expect(A, "over available stock -> 409", dist.call("POST", "/distributions", {**dbody, "items": [{"chickenTypeId": broiler["id"], "weightKg": "250"}], "expenses": []}), 409, "INSUFFICIENT_STOCK")
    d2 = data(expect(A, "second draft 100 kg", dist.call("POST", "/distributions", {**dbody, "items": [{"chickenTypeId": broiler["id"], "weightKg": "100"}], "expenses": []}), 201))
    expect(A, "reverse draft", dist.call("POST", f"/distributions/{d2['id']}/reverse", {"reason": "Not needed"}), 200)
    check(A, "reservation released", float(data(wh.call("GET", f"/stock-batches/{bb['id']}"))["reservedWeightKg"]) == 300)
    expect(A, "receive before dispatch -> 409", kr.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "items": [{"distributionItemId": d1["items"][0]["id"], "receivedWeightKg": "300"}]}), 409, "INVALID_STATUS_TRANSITION")
    dd = data(expect(A, "dispatch", dist.call("POST", f"/distributions/{d1['id']}/dispatch"), 200))
    check(A, "status DISPATCHED", dd and dd["status"] == "DISPATCHED")
    expect(A, "dispatch twice -> 409", dist.call("POST", f"/distributions/{d1['id']}/dispatch"), 409, "INVALID_STATUS_TRANSITION")
    bstate = data(wh.call("GET", f"/stock-batches/{bb['id']}"))
    check(A, "stock issued: remaining 200, reserved 0", float(bstate["remainingWeightKg"]) == 200 and float(bstate["reservedWeightKg"]) == 0, bstate)
    expect(A, "Colombo receiver cannot see Kandy delivery -> 403", cr.call("GET", f"/distributions/{d1['id']}"), 403, "FORBIDDEN")
    expect(A, "receiver without branch -> 403", nb.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "items": [{"distributionItemId": d1["items"][0]["id"], "receivedWeightKg": "300"}]}), 403, "FORBIDDEN")
    expect(A, "receipt missing lines -> 400", kr.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "items": [{"distributionItemId": str(uuid.uuid4()), "receivedWeightKg": "1"}]}), 400, "VALIDATION_FAILED")
    expect(A, "shortfall without reason -> 400", kr.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "items": [{"distributionItemId": d1["items"][0]["id"], "receivedWeightKg": "295"}]}), 400, "VALIDATION_FAILED")
    expect(A, "receive more than sent -> 400", kr.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "items": [{"distributionItemId": d1["items"][0]["id"], "receivedWeightKg": "301"}]}), 400)
    rc = data(expect(A, "Kandy receives 295 kg (5 kg melt water)", kr.call("POST", f"/distributions/{d1['id']}/receipts", {"receivedAt": "2026-11-04T08:00:00Z", "notes": "ok", "items": [{"distributionItemId": d1["items"][0]["id"], "receivedWeightKg": "295", "discrepancyReason": "MELT_WATER"}]}), 200))
    check(A, "discrepancy 5 kg recorded", rc and float(rc["items"][0]["discrepancyWeightKg"]) == 5, rc)
    lst = data(expect(A, "Kandy receiver lists own deliveries", kr.call("GET", "/distributions"), 200))
    check(A, "receiver sees 2 (received + reversed draft)", lst and lst["totalElements"] == 2, lst and lst["totalElements"])
    lst = data(cr.call("GET", "/distributions"))
    check(A, "Colombo receiver sees none", lst and lst["totalElements"] == 0, lst and lst["totalElements"])
    expect(A, "Colombo receiver asking for Kandy -> 403", cr.call("GET", "/distributions", params={"branchId": kandy["id"]}), 403)
    expect(A, "get", dist.call("GET", f"/distributions/{d1['id']}"), 200)

    # ---------------------------------------------------------------- sales
    A = "Branch sales"
    sbody = {"branchId": kandy["id"], "saleDate": TODAY, "notes": "Counter",
             "items": [{"chickenTypeId": broiler["id"], "weightKg": "200"}]}
    expect(A, "Colombo receiver cannot record Kandy -> 403", cr.call("POST", "/branch-sales", sbody), 403, "FORBIDDEN")
    expect(A, "sell more than held -> 409", kr.call("POST", "/branch-sales", {**sbody, "items": [{"chickenTypeId": broiler["id"], "weightKg": "296"}]}), 409, "INSUFFICIENT_BRANCH_STOCK")
    expect(A, "no price configured -> 409/400", kr.call("POST", "/branch-sales", {**sbody, "items": [{"chickenTypeId": layer["id"], "weightKg": "1"}]}), 409, "INSUFFICIENT_BRANCH_STOCK")
    sale = data(expect(A, "record 200 kg", kr.call("POST", "/branch-sales", sbody), 201))
    if sale:
        check(A, "revenue 200000", float(sale["totalSales"]) == 200000, sale["totalSales"])
        check(A, "transfer value 182400 (912/kg)", float(sale["transferValue"]) == 182400, sale["transferValue"])
        check(A, "branch profit 17600", float(sale["branchProfit"]) == 17600, sale["branchProfit"])
    again = data(expect(A, "re-record same day replaces", kr.call("POST", "/branch-sales", {**sbody, "items": [{"chickenTypeId": broiler["id"], "weightKg": "210"}]}), 201))
    check(A, "same reference kept", again and sale and again["reference"] == sale["reference"] and again["id"] == sale["id"])
    stock = data(expect(A, "Kandy stock", kr.call("GET", f"/branches/{kandy['id']}/stock"), 200))
    held = {r["chickenTypeId"]: r["availableWeightKg"] for r in (stock or [])}
    check(A, "Kandy holds 85 kg Broiler after 210 sold", held.get(broiler["id"]) == "85.000", held)
    expect(A, "other branch stock -> 403", cr.call("GET", f"/branches/{kandy['id']}/stock"), 403)
    expect(A, "list own", kr.call("GET", "/branch-sales"), 200)
    lst = data(cr.call("GET", "/branch-sales"))
    check(A, "other branch sees none", lst and lst["totalElements"] == 0)
    expect(A, "other branch get -> 403", cr.call("GET", f"/branch-sales/{sale['id']}"), 403)
    expect(A, "reverse received distribution after sale -> 409", dist.call("POST", f"/distributions/{d1['id']}/reverse", {"reason": "x"}), 409, "BUSINESS_RULE_VIOLATION")

    # ---------------------------------------------------------------- settlements
    A = "Settlements"
    stbody = {"branchId": kandy["id"], "settlementDate": TODAY, "amount": "100000.00", "method": "BANK", "reference": "TX1",
              "allocations": [{"branchSaleId": sale["id"], "amount": "100000.00"}]}
    expect(A, "receiver cannot settle -> 403", kr.call("POST", "/branch-settlements", stbody), 403)
    expect(A, "allocations must sum -> 400", acc.call("POST", "/branch-settlements", {**stbody, "amount": "90000.00"}), 400, "BUSINESS_RULE_VIOLATION")
    expect(A, "over-allocation -> 409", acc.call("POST", "/branch-settlements", {**stbody, "amount": "999999.00", "allocations": [{"branchSaleId": sale["id"], "amount": "999999.00"}]}), 409, "OVER_ALLOCATION")
    key = str(uuid.uuid4())
    s1 = acc.call("POST", "/branch-settlements", stbody, headers={"Idempotency-Key": key})
    st = data(expect(A, "settle 100000 with Idempotency-Key", s1, 201))
    s2 = acc.call("POST", "/branch-settlements", stbody, headers={"Idempotency-Key": key})
    check(A, "retry with same key replays, no duplicate", s2[0] == 201 and data(s2) and data(s2)["id"] == st["id"] and s2[2].headers.get("Idempotent-Replayed") == "true", s2[0])
    expect(A, "same key, different body -> 422", acc.call("POST", "/branch-settlements", {**stbody, "reference": "TX2"}, headers={"Idempotency-Key": key}), 422, "IDEMPOTENCY_KEY_REUSED")
    bal = data(expect(A, "Kandy balance", kr.call("GET", f"/branches/{kandy['id']}/balance"), 200))
    check(A, "outstanding = 191520 - 100000", bal and float(bal["outstandingAmount"]) == 91520, bal)
    expect(A, "other branch balance -> 403", cr.call("GET", f"/branches/{kandy['id']}/balance"), 403)
    expect(A, "re-record below settled -> 409", kr.call("POST", "/branch-sales", {**sbody, "items": [{"chickenTypeId": broiler["id"], "weightKg": "50"}]}), 409, "BUSINESS_RULE_VIOLATION")
    expect(A, "list", acc.call("GET", "/branch-settlements", params={"branchId": kandy["id"]}), 200)
    expect(A, "get", acc.call("GET", f"/branch-settlements/{st['id']}"), 200)
    expect(A, "reverse", acc.call("POST", f"/branch-settlements/{st['id']}/reverse", {"reason": "Bounced"}), 200)
    expect(A, "reverse twice -> 409", acc.call("POST", f"/branch-settlements/{st['id']}/reverse", {"reason": "x"}), 409, "INVALID_STATUS_TRANSITION")
    bal = data(acc.call("GET", f"/branches/{kandy['id']}/balance"))
    check(A, "balance restored after reversal", bal and float(bal["outstandingAmount"]) == 191520, bal)
    expect(A, "settle again 50000", acc.call("POST", "/branch-settlements", {**stbody, "amount": "50000.00", "reference": "TX3",
           "allocations": [{"branchSaleId": sale["id"], "amount": "50000.00"}]}), 201)
    opens = data(expect(A, "open sales", acc.call("GET", f"/branches/{kandy['id']}/open-sales"), 200))
    check(A, "open sale left to pay 141520", opens and len(opens) == 1 and opens[0]["branchSaleId"] == sale["id"] and opens[0]["outstandingAmount"] == "141520.00", opens)
    expect(A, "other branch open sales -> 403", cr.call("GET", f"/branches/{kandy['id']}/open-sales"), 403)

    # ---------------------------------------------------------------- reports
    A = "Reports"
    dash = data(expect(A, "dashboard (owner)", admin.call("GET", "/dashboard", params={"from": TODAY, "to": TODAY}), 200))
    check(A, "dashboard has figures", dash and len(dash) > 5, dash)
    for name, params in [("procurement", {"from": TODAY, "to": TODAY}), ("stock", {}), ("distribution", {"from": TODAY, "to": TODAY}),
                         ("profit", {"from": TODAY, "to": TODAY}), ("sales", {"from": TODAY, "to": TODAY}),
                         ("shrinkage", {"from": TODAY, "to": TODAY}), ("settlements", {"from": TODAY, "to": TODAY})]:
        rep = data(expect(A, f"{name} report", acc.call("GET", f"/reports/{name}", params=params), 200))
        check(A, f"{name} report has rows", rep and len(rep.get("rows", [])) > 0, rep and {k: v for k, v in rep.items() if k != "rows"})
    for fmt, ctype in (("csv", "text/csv"), ("pdf", "application/pdf")):
        r = acc.call("GET", "/reports/sales/export", params={"format": fmt, "from": TODAY, "to": TODAY}, raw=True)
        check(A, f"sales export {fmt}", r.status_code == 200 and ctype in r.headers.get("Content-Type", "") and len(r.content) > 50,
              f"{r.status_code} {r.headers.get('Content-Type')}")
    expect(A, "unknown report export -> 404", acc.call("GET", "/reports/nope/export"), 404, "REPORT_NOT_FOUND")
    expect(A, "unsupported format -> 400", acc.call("GET", "/reports/sales/export", params={"format": "xls"}), 400, "VALIDATION_FAILED")
    expect(A, "receiver has no report access -> 403", kr.call("GET", "/dashboard"), 403)
    rep = data(expect(A, "viewer sales report (own branches)", viewer.call("GET", "/reports/sales", params={"from": TODAY, "to": TODAY}), 200))
    check(A, "viewer sees Kandy sales", rep and len(rep["rows"]) == 1, rep)
    rep = data(viewer.call("GET", "/reports/stock"))
    check(A, "viewer sees no warehouse stock rows", rep is not None and len(rep["rows"]) == 0, rep)
    expect(A, "viewer cannot ask for other branch -> 403", viewer.call("GET", "/reports/sales", params={"branchId": closed["id"]}), 403)

    # ---------------------------------------------------------------- operating expenses + profit and loss
    A = "Profit & Loss"
    exp = data(expect(A, "create branch expense", acc.call("POST", "/expenses", {"expenseDate": TODAY, "categoryId": transport["id"], "branchId": kandy["id"], "amount": "2500", "payee": "Lorry hire"}), 201))
    check(A, "expense reference", exp and exp["reference"].startswith("EXP-"), exp)
    data(expect(A, "create head-office expense", admin.call("POST", "/expenses", {"expenseDate": TODAY, "categoryId": transport["id"], "amount": "1000"}), 201))
    dup = data(expect(A, "create duplicate", admin.call("POST", "/expenses", {"expenseDate": TODAY, "categoryId": transport["id"], "amount": "1000"}), 201))
    expect(A, "void duplicate", admin.call("POST", f"/expenses/{dup['id']}/void", {"reason": "typed twice"}), 200)
    expect(A, "void again -> 409", admin.call("POST", f"/expenses/{dup['id']}/void", {"reason": "again"}), 409, "BUSINESS_RULE_VIOLATION")
    expect(A, "zero amount -> 400", admin.call("POST", "/expenses", {"expenseDate": TODAY, "categoryId": transport["id"], "amount": "0"}), 400, "VALIDATION_FAILED")
    expect(A, "update", admin.call("PUT", f"/expenses/{exp['id']}", {"expenseDate": TODAY, "categoryId": transport["id"], "branchId": kandy["id"], "amount": "2400"}), 200)
    lst = data(expect(A, "list posted today", admin.call("GET", "/expenses", params={"from": TODAY, "to": TODAY, "status": "POSTED", "categoryId": transport["id"]}), 200))
    check(A, "2 posted expenses", lst and lst["totalElements"] == 2, lst and lst["totalElements"])
    expect(A, "receiver cannot see expenses -> 403", kr.call("GET", "/expenses"), 403, "FORBIDDEN")
    pl = data(expect(A, "daily P&L", acc.call("GET", "/reports/profit-loss", params={"period": "daily", "date": TODAY}), 200))
    if pl:
        s_ = pl["summary"]
        net = float(s_["headOffice"]["margin"]) + float(s_["branches"]["profit"]) - float(s_["stockLosses"]["value"]) - float(s_["operatingExpenses"]["total"])
        check(A, "net = margin + branch profit - losses - expenses", abs(net - float(s_["netProfit"])) < 0.01, s_)
        check(A, "expenses include 3400 today", float(s_["operatingExpenses"]["total"]) >= 3400, s_["operatingExpenses"])
        check(A, "P&L margin matches dashboard", s_["headOffice"]["margin"] == dash["ourMargin"] and s_["branches"]["sales"] == dash["totalSales"], (s_, dash))
    m = data(expect(A, "monthly P&L by day", acc.call("GET", "/reports/profit-loss", params={"month": TODAY[:7]}), 200))
    check(A, "monthly breakdown has every day", m and m["groupBy"] == "DAY" and len(m["breakdown"]) >= 28, m and len(m["breakdown"]))
    y = data(expect(A, "yearly P&L by month", acc.call("GET", "/reports/profit-loss", params={"period": "yearly", "year": TODAY[:4]}), 200))
    check(A, "yearly breakdown has 12 months", y and len(y["breakdown"]) == 12, y and len(y["breakdown"]))
    expect(A, "custom P&L", acc.call("GET", "/reports/profit-loss", params={"period": "custom", "from": TODAY, "to": TODAY, "branchId": kandy["id"]}), 200)
    expect(A, "custom without to -> 400", acc.call("GET", "/reports/profit-loss", params={"period": "custom", "from": TODAY}), 400, "VALIDATION_FAILED")
    expect(A, "bad period -> 400", acc.call("GET", "/reports/profit-loss", params={"period": "weekly"}), 400, "VALIDATION_FAILED")
    expect(A, "viewer cannot see P&L -> 403", viewer.call("GET", "/reports/profit-loss"), 403)
    for fmt, ctype in (("csv", "text/csv"), ("pdf", "application/pdf")):
        r = acc.call("GET", "/reports/profit-loss/export", params={"format": fmt, "month": TODAY[:7]}, raw=True)
        check(A, f"P&L export {fmt}", r.status_code == 200 and ctype in r.headers.get("Content-Type", "") and len(r.content) > 50,
              f"{r.status_code} {r.headers.get('Content-Type')}")
    A = "Reports"

    vd = data(viewer.call("GET", "/dashboard", params={"from": TODAY, "to": TODAY}))
    check(A, "viewer dashboard hides warehouse figures", vd and vd["stockValue"] == "0.00" and vd["procurementValue"] == "0.00", vd)
    check(A, "viewer dashboard shows own sales", vd and float(vd["totalSales"]) > 0, vd)

    A = "JSON"
    check(A, "money is a string", isinstance(prc["totalCost"], str) and prc["totalCost"] == "532000.00", prc["totalCost"])
    check(A, "weight is a string with 3 decimals", isinstance(bb["receivedWeightKg"], str) and bb["receivedWeightKg"] == "500.000", bb["receivedWeightKg"])
    check(A, "unit price keeps 4 decimals", d1["items"][0]["transferPricePerKg"] == "912.0000", d1["items"][0]["transferPricePerKg"])

    # ---------------------------------------------------------------- errors
    A = "Errors"
    r = requests.post(API + "/branches", data="{bad json", headers={"Authorization": "Bearer " + admin.token, "Content-Type": "application/json", "X-Request-Id": "rid-7"})
    check(A, "malformed JSON -> 400 VALIDATION_FAILED", r.status_code == 400 and r.json().get("code") == "VALIDATION_FAILED", r.text[:200])
    check(A, "X-Request-Id echoed in error", r.json().get("requestId") == "rid-7", r.text[:200])
    s, j, _ = admin.call("GET", f"/distributions/{uuid.uuid4()}")
    check(A, "error envelope has code, message, timestamp", s == 404 and {"code", "message", "timestamp"} <= set(j or {}), j)

    # ---------------------------------------------------------------- in-app sign-in (password grant)
    A = "App sign-in"
    GRANT = "urn:chicktrack:params:oauth:grant-type:password"
    def app_login(username, password, client=oauth.CLIENT):
        return requests.post(BASE + "/oauth2/token", data=dict(grant_type=GRANT, client_id=client,
                             username=username, password=password))
    r = app_login(ADMIN_USERNAME, ADMIN_PASSWORD)
    tj = r.json() if r.status_code == 200 else {}
    check(A, "owner signs in -> 200 with access + refresh token", r.status_code == 200 and tj.get("access_token") and tj.get("refresh_token"), r.text[:200])
    if tj:
        me2 = requests.get(API + "/me", headers={"Authorization": "Bearer " + tj["access_token"]})
        check(A, "token works on /me with OWNER role", me2.status_code == 200 and "OWNER" in me2.json()["data"]["roles"], me2.text[:200])
        rr = oauth.refresh(tj["refresh_token"])
        check(A, "refresh works", rr.status_code == 200 and rr.json().get("refresh_token") != tj["refresh_token"], rr.text[:200])
        check(A, "old refresh token is rotated out", oauth.refresh(tj["refresh_token"]).status_code == 400)
    r = app_login(ADMIN_USERNAME, "wrong-password")
    check(A, "wrong password -> 400 invalid_grant", r.status_code == 400 and r.json().get("error") == "invalid_grant", r.text[:200])
    r = app_login("nobody" + SFX, "whatever-123")
    check(A, "unknown user -> 400 invalid_grant", r.status_code == 400 and r.json().get("error") == "invalid_grant", r.text[:200])
    r = app_login(ADMIN_USERNAME, ADMIN_PASSWORD, client="not-a-client")
    check(A, "unknown client -> 401", r.status_code == 401, r.status_code)
    r = requests.post(BASE + "/oauth2/token", data=dict(grant_type=GRANT, client_id=oauth.CLIENT, username=ADMIN_USERNAME))
    check(A, "missing password -> 400 invalid_request", r.status_code == 400 and r.json().get("error") == "invalid_request", r.text[:200])
    expect(A, "deactivate for sign-in check", admin.call("PATCH", f"/users/{u_none['id']}/status", {"active": False}), 200)
    r = app_login(u_none["username"], pw)
    check(A, "inactive user -> 400 invalid_grant", r.status_code == 400 and r.json().get("error") == "invalid_grant", r.text[:200])
    admin.call("PATCH", f"/users/{u_none['id']}/status", {"active": True})
    r = app_login(u_kandy["username"], pw)
    if r.status_code == 200:
        kt = r.json()["access_token"]
        mine = requests.get(API + f"/branches/{colombo['id']}/stock", headers={"Authorization": "Bearer " + kt})
        check(A, "branch user keeps branch limits", mine.status_code == 403, mine.status_code)
    else:
        check(A, "branch user signs in", False, r.text[:200])

    # ---------------------------------------------------------------- role change revokes
    A = "Sessions"
    tok = oauth.login(u_view["username"], pw)
    expect(A, "change viewer roles", admin.call("PUT", f"/users/{u_view['id']}/roles", {"roles": ["ACCOUNTANT"]}), 200)
    check(A, "role change revokes refresh token", oauth.refresh(tok["refresh_token"]).status_code == 400)

    return admin


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        traceback.print_exc()
        RESULTS.append(("Runner", "script crashed", False, ""))
    finally:
        passed = sum(1 for r in RESULTS if r[2])
        print(f"\n{passed}/{len(RESULTS)} checks passed")
        json.dump(RESULTS, open("api-test-results.json", "w"), indent=1)
        sys.exit(0 if passed == len(RESULTS) else 1)
