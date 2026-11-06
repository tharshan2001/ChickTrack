#!/usr/bin/env python3
"""
Fill a local ChickTrack backend with about three months of realistic demo data, through the API
(so every number is calculated by the backend, exactly as in real use).

  python3 scripts/seed/seed_demo.py                     # http://localhost:8090, admin / change-me
  CT_BASE=http://192.168.1.20:8090 CT_ADMIN_PASSWORD=... python3 scripts/seed/seed_demo.py

Creates, for the last DAYS days (default 92):
  * products Broiler, Layer, Country chicken (+ Kuroiler, inactive); 4 suppliers; 4 branches
    (Gampaha opens a month in; Galle is closed); expense categories
  * weekly buying and selling prices (Colombo sells a little higher), margins per product
  * daily purchases with commission and transport, a few stock adjustments
  * daily dispatches to each open branch: draft -> dispatched -> received (with the odd shortfall)
  * a day close per branch (closing stock + takings) and payments against deliveries every few days
  * today: a draft holding stock, a load on the way, one branch not closed yet, money owed

Run it once against an empty database (it stops if purchases already exist; pass --force to add
more anyway). Only the Python standard library is used.
"""
import datetime as dt
import json
import os
import random
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from decimal import ROUND_HALF_UP, Decimal

BASE = os.environ.get("CT_BASE", "http://localhost:8090").rstrip("/")
USER = os.environ.get("CT_ADMIN_USERNAME", "admin")
PASSWORD = os.environ.get("CT_ADMIN_PASSWORD", "change-me")
DAYS = int(os.environ.get("CT_DAYS", "92"))
GRANT = "urn:chicktrack:params:oauth:grant-type:password"
COLOMBO = dt.timezone(dt.timedelta(hours=5, minutes=30))
rnd = random.Random(20261105)

TOKEN = None
CALLS = 0


# ---------------------------------------------------------------- http
def _request(method, url, body=None, form=None, auth=True):
    global CALLS
    CALLS += 1
    headers = {"Accept": "application/json"}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if auth:
        headers["Authorization"] = "Bearer " + TOKEN
    if method != "GET":
        headers["Idempotency-Key"] = str(uuid.uuid4())
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            raw = res.read()
            return res.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"message": raw.decode(errors="replace")}


def login():
    global TOKEN
    status, body = _request("POST", BASE + "/oauth2/token", auth=False,
                            form={"grant_type": GRANT, "client_id": "chicktrack-mobile",
                                  "username": USER, "password": PASSWORD})
    if status != 200:
        sys.exit(f"Sign-in failed ({status}): {body}. Set CT_ADMIN_USERNAME / CT_ADMIN_PASSWORD.")
    TOKEN = body["access_token"]


def api(method, path, body=None, params=None, ok=(200, 201)):
    url = BASE + "/api/v1" + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    status, res = _request(method, url, body)
    if status == 401:  # access token expired (15 min): sign in again and retry once
        login()
        status, res = _request(method, url, body)
    if status not in ok:
        raise RuntimeError(f"{method} {path} -> {status}: {json.dumps(res)[:400]}")
    return res["data"] if isinstance(res, dict) and "data" in res else res


# ---------------------------------------------------------------- helpers
def money(x):
    return str(Decimal(x).quantize(Decimal("0.01"), ROUND_HALF_UP))


def kg(x):
    return str(Decimal(x).quantize(Decimal("0.001"), ROUND_HALF_UP))


def day_start(d):
    return dt.datetime(d.year, d.month, d.day, tzinfo=COLOMBO).astimezone(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def at(d, hour, minute=0):
    return dt.datetime(d.year, d.month, d.day, hour, minute, tzinfo=COLOMBO).astimezone(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def find_or_create(path, name, body):
    page = api("GET", path, params={"search": name, "size": 50})
    for item in page["content"]:
        if item["name"].lower() == name.lower():
            return item
    return api("POST", path, body)


def progress(i, total, label):
    bar = "#" * int(30 * i / total)
    print(f"\r  [{bar:<30}] {i}/{total} {label:<28}", end="", flush=True)


# ---------------------------------------------------------------- main
def main():
    force = "--force" in sys.argv
    print(f"ChickTrack demo data -> {BASE} as {USER}")
    login()
    existing = api("GET", "/procurements", params={"size": 1})
    if existing["totalElements"] and not force:
        sys.exit(f"The database already has {existing['totalElements']} purchases. "
                 "Run on an empty database, or pass --force to add more.")

    today = dt.datetime.now(COLOMBO).date()
    start = today - dt.timedelta(days=DAYS - 1)

    # --- master data
    print("Setup: products, suppliers, branches, categories")
    P = {
        "broiler": find_or_create("/chicken-types", "Broiler", {"name": "Broiler", "description": "Live broiler, sold by kg"}),
        "layer": find_or_create("/chicken-types", "Layer", {"name": "Layer", "description": "Spent hens"}),
        "country": find_or_create("/chicken-types", "Country chicken", {"name": "Country chicken", "description": "Free-range village birds"}),
    }
    kuroiler = find_or_create("/chicken-types", "Kuroiler", {"name": "Kuroiler", "description": "Not bought this season"})
    if kuroiler["active"]:
        api("PATCH", f"/chicken-types/{kuroiler['id']}/status", {"active": False})

    suppliers = [
        find_or_create("/suppliers", n, {"name": n, "contactPerson": c, "phone": ph, "email": None, "address": a, "paymentTerms": t})
        for n, c, ph, a, t in [
            ("Silva Farm", "Nimal Silva", "077 123 4567", "Kurunegala Road, Kandy", "Cash on delivery"),
            ("Perera Broilers", "Sunil Perera", "071 555 0192", "Kegalle", "Pay within 7 days"),
            ("Hill Country Poultry", "Kumar", "072 811 3344", "Matale", "Weekly"),
            ("Lanka Layers", "Ruwan", "070 222 9090", "Puttalam", "Cash on delivery"),
        ]
    ]
    def branch(code, name, phone, address):
        page = api("GET", "/branches", params={"search": name, "size": 50})
        for b in page["content"]:
            if b["code"] == code:
                return b
        return api("POST", "/branches", {"code": code, "name": name, "phone": phone, "address": address})
    B = {
        "KDY": branch("KDY", "Kandy Central", "081 222 1100", "12 Dalada Veediya, Kandy"),
        "CMB": branch("CMB", "Colombo", "011 250 3344", "45 Main Street, Pettah"),
        "GMP": branch("GMP", "Gampaha", "033 222 4410", "Colombo Road, Gampaha"),
        "GAL": branch("GAL", "Galle", "091 223 7788", "Wakwella Road, Galle"),
    }
    cats = {n: find_or_create("/expense-categories", n, {"name": n, "description": d}) for n, d in [
        ("Transport", "Lorry hire and fuel"), ("Labour", "Loading and slaughter"),
        ("Ice & packing", "Ice, bags, crates"), ("Market fees", "Not used any more")]}
    if cats["Market fees"]["active"]:
        api("PATCH", f"/expense-categories/{cats['Market fees']['id']}/status", {"active": False})

    # --- branch plans: daily demand in kg per product, and when the branch is open
    plan = {
        "KDY": {"open": start, "demand": {"broiler": 260, "layer": 60, "country": 25}},
        "CMB": {"open": start, "demand": {"broiler": 320, "layer": 80, "country": 30}},
        "GMP": {"open": start + dt.timedelta(days=30), "demand": {"broiler": 150, "layer": 40, "country": 0}},
        "GAL": {"open": start, "close": start + dt.timedelta(days=40), "demand": {"broiler": 120, "layer": 0, "country": 0}},
    }

    # --- price history: buying price drifts weekly; selling price = buying + spread
    base_buy = {"broiler": Decimal("780"), "layer": Decimal("590"), "country": Decimal("1250")}
    spread = {"broiler": Decimal("220"), "layer": Decimal("150"), "country": Decimal("350")}
    api("POST", "/company-margin-rules", {"chickenTypeId": P["broiler"]["id"], "branchId": None, "markupPercent": "10", "effectiveFrom": day_start(start)})
    api("POST", "/company-margin-rules", {"chickenTypeId": P["layer"]["id"], "branchId": None, "markupPercent": "8", "effectiveFrom": day_start(start)})
    api("POST", "/company-margin-rules", {"chickenTypeId": P["country"]["id"], "branchId": None, "markupPercent": "12", "effectiveFrom": day_start(start)})
    api("POST", "/company-margin-rules", {"chickenTypeId": P["broiler"]["id"], "branchId": None, "markupPercent": "12", "effectiveFrom": day_start(start + dt.timedelta(days=45))})

    buy_today = {}
    totals = {"purchases": 0, "dispatches": 0, "sales": 0, "payments": 0, "adjustments": 0}
    total_days = (today - start).days + 1

    print(f"Daily activity from {start} to {today}")
    for i in range(total_days):
        d = start + dt.timedelta(days=i)
        is_today = d == today
        progress(i + 1, total_days, d.isoformat())

        # Branch closes: Galle is shut after its close date.
        for code, p in plan.items():
            if p.get("close") == d:
                api("PATCH", f"/branches/{B[code]['id']}/status", {"active": False})

        # Prices every Monday (and on day one).
        if i == 0 or d.weekday() == 0:
            for key, prod in P.items():
                drift = Decimal(rnd.randint(-40, 45))
                base_buy[key] = max(base_buy[key] + drift, Decimal("400"))
                buy_today[key] = base_buy[key]
                sell = base_buy[key] + spread[key]
                api("POST", "/daily-prices", {"chickenTypeId": prod["id"], "branchId": None, "supplierId": None,
                                              "purchasePricePerKg": money(buy_today[key]), "sellingPricePerKg": money(sell),
                                              "effectiveFrom": day_start(d)})
                # Colombo sells a little higher.
                api("POST", "/daily-prices", {"chickenTypeId": prod["id"], "branchId": B["CMB"]["id"], "supplierId": None,
                                              "purchasePricePerKg": money(buy_today[key]), "sellingPricePerKg": money(sell + 20),
                                              "effectiveFrom": day_start(d)})

        # Purchases (not on full moon Poya days, roughly every 29 days).
        if i % 29 != 14:
            open_codes = [c for c, p in plan.items() if p["open"] <= d and (p.get("close") is None or d < p["close"])]
            need = {k: sum(plan[c]["demand"][k] for c in open_codes) for k in P}
            items = []
            # Broiler every day, Layer most days, Country chicken every third day.
            b_kg = need["broiler"] * Decimal(rnd.uniform(0.9, 1.04))
            items.append({"chickenTypeId": P["broiler"]["id"], "paidWeightKg": kg(b_kg),
                          "purchasePricePerKg": money(buy_today["broiler"] + rnd.randint(-15, 15)),
                          "commissionMethod": "PER_KG", "commissionValue": "10", "quantity": int(b_kg / Decimal("2.1"))})
            if i % 3 != 2 and need["layer"]:
                l_kg = need["layer"] * Decimal(rnd.uniform(1.3, 1.6))
                items.append({"chickenTypeId": P["layer"]["id"], "paidWeightKg": kg(l_kg),
                              "purchasePricePerKg": money(buy_today["layer"] + rnd.randint(-10, 10)),
                              "commissionMethod": "PERCENTAGE", "commissionValue": "1.5", "quantity": None})
            if i % 3 == 0 and need["country"]:
                c_kg = need["country"] * Decimal(rnd.uniform(2.6, 3.2))
                items.append({"chickenTypeId": P["country"]["id"], "paidWeightKg": kg(c_kg),
                              "purchasePricePerKg": money(buy_today["country"] + rnd.randint(-30, 30)),
                              "commissionMethod": "PER_BIRD", "commissionValue": "25", "quantity": int(c_kg / Decimal("1.4"))})
            expenses = [{"categoryId": cats["Transport"]["id"], "description": "Lorry", "amount": money(rnd.randint(55, 85) * 100)}]
            if i % 4 == 0:
                expenses.append({"categoryId": cats["Labour"]["id"], "description": "Loading", "amount": money(rnd.randint(15, 30) * 100)})
            api("POST", "/procurements", {"supplierId": suppliers[i % 3 if i % 7 else 3]["id"], "procurementDate": d.isoformat(),
                                          "notes": None, "items": items, "expenses": expenses})
            totals["purchases"] += 1

        # A little damage now and then.
        if i % 9 == 4:
            batches = api("GET", "/stock-batches", params={"available": "true", "size": 5, "sort": "receivedAt,desc"})["content"]
            if batches:
                b = batches[0]
                api("POST", "/stock-adjustments", {"stockBatchId": b["id"], "type": "DAMAGE",
                                                    "weightKg": kg(min(Decimal(b["availableWeightKg"]), Decimal(rnd.randint(2, 8)))),
                                                    "direction": None, "reason": rnd.choice(["Crates crushed while unloading", "Birds died in transit", "Dropped at the scale"])})
                totals["adjustments"] += 1

        # Free warehouse stock per product, to size today's dispatches.
        free = {k: Decimal(0) for k in P}
        by_id = {v["id"]: k for k, v in P.items()}
        for b in api("GET", "/stock-batches", params={"available": "true", "size": 200})["content"]:
            if b["chickenTypeId"] in by_id:
                free[by_id[b["chickenTypeId"]]] += Decimal(b["availableWeightKg"])

        for code, p in plan.items():
            if p["open"] > d or (p.get("close") is not None and d >= p["close"]):
                continue
            br = B[code]
            # Send what the branch should sell tomorrow, minus what it still holds.
            held = {x["chickenTypeId"]: Decimal(x["availableWeightKg"]) for x in api("GET", f"/branches/{br['id']}/stock")}
            items = []
            for key, demand in p["demand"].items():
                if not demand:
                    continue
                want = Decimal(demand) * Decimal(rnd.uniform(0.95, 1.1)) - held.get(P[key]["id"], Decimal(0)) * Decimal("0.5")
                send = min(want, free[key] - Decimal("1"))
                if send >= 5:
                    send = send.quantize(Decimal("1"))
                    items.append({"chickenTypeId": P[key]["id"], "weightKg": kg(send)})
                    free[key] -= send
            if items:
                dist = api("POST", "/distributions", {
                    "branchId": br["id"], "distributionDate": d.isoformat(), "notes": None, "items": items,
                    "expenses": [{"categoryId": cats["Transport"]["id"], "description": "Delivery",
                                  "amount": money(rnd.randint(15, 30) * 100), "allocationMethod": "BY_WEIGHT"}]})
                totals["dispatches"] += 1
                # Today: leave Colombo's load as a draft and Kandy's on the way.
                if not (is_today and code == "CMB"):
                    dist = api("POST", f"/distributions/{dist['id']}/dispatch")
                    if not (is_today and code == "KDY"):
                        lines = []
                        for line in dist["items"]:
                            sent = Decimal(line["weightKg"])
                            short = Decimal(0)
                            reason = None
                            if rnd.random() < 0.18:
                                short = (sent * Decimal(rnd.uniform(0.004, 0.015))).quantize(Decimal("0.001"))
                                reason = rnd.choice(["MELT_WATER", "WEIGHT_DIFFERENCE", "DAMAGE"])
                            lines.append({"distributionItemId": line["id"], "receivedWeightKg": kg(sent - short),
                                          "discrepancyReason": reason if short > 0 else None})
                        api("POST", f"/distributions/{dist['id']}/receipts",
                            {"receivedAt": at(d, 7, rnd.randint(5, 55)), "notes": None, "items": lines})

            # Day close: the branch counts what is left and enters its takings (Gampaha hasn't closed today yet).
            if is_today and code == "GMP":
                continue
            opening = api("GET", f"/branches/{br['id']}/stock", params={"date": d.isoformat()})
            close_items = []
            for x in opening:
                have = Decimal(x["availableWeightKg"])
                if have <= 0:
                    continue
                left = have if have < 3 else min(have, (have * Decimal(rnd.uniform(0.04, 0.22))).quantize(Decimal("0.5")))
                close_items.append({"chickenTypeId": x["chickenTypeId"], "closingWeightKg": kg(left)})
            if close_items:
                body = {"branchId": br["id"], "saleDate": d.isoformat(), "notes": None, "totalSales": "0", "items": close_items}
                sale = api("POST", "/branch-sales", body)
                # Takings land near the expected figure at the branch price: usually a little short, sometimes over.
                expected = Decimal(sale["expectedSales"] or sale["transferValue"])
                body["totalSales"] = money((expected * Decimal(rnd.uniform(0.97, 1.01))).quantize(Decimal("0.01")))
                api("POST", "/branch-sales", body)
                totals["sales"] += 1

            # Payments every 3 days (Colombo pays weekly, so it carries a bigger balance), oldest delivery first.
            every = 7 if code == "CMB" else 3
            if (i % every == every - 1) and not is_today:
                open_deliveries = api("GET", f"/branches/{br['id']}/open-deliveries")
                owed = sum(Decimal(o["outstandingAmount"]) for o in open_deliveries)
                pay = (owed * Decimal(rnd.uniform(0.8, 1.0))).quantize(Decimal("0.01"))
                allocations, left = [], pay
                for o in open_deliveries:
                    if left <= 0:
                        break
                    part = min(left, Decimal(o["outstandingAmount"]))
                    if part > 0:
                        allocations.append({"distributionId": o["distributionId"], "amount": money(part)})
                        left -= part
                if allocations:
                    api("POST", "/branch-settlements", {
                        "branchId": br["id"], "settlementDate": d.isoformat(),
                        "amount": money(sum(Decimal(a["amount"]) for a in allocations)),
                        "method": rnd.choice(["CASH", "BANK"]), "reference": f"{code}-{d:%m%d}", "notes": None,
                        "allocations": allocations})
                    totals["payments"] += 1

    print()
    print("Done: " + ", ".join(f"{v} {k}" for k, v in totals.items()) + f" ({CALLS} API calls)")
    for code, br in B.items():
        bal = api("GET", f"/branches/{br['id']}/balance")["outstandingAmount"]
        print(f"  {br['name']:<14} owes Rs {Decimal(bal):,.2f}")
    print(f"Sign in to the app as {USER}.")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print()
        sys.exit(f"Stopped: {e}")
