"""
Display-only daily leaderboard with simulated competition.

- Pool of 100+ fake names; 10 picked per period (seeded by period date).
- Fake turnover grows through the day (low morning, surges, random daily scene).
- Real users merged by actual UserDailyTurnover — can climb ranks during the day.
- 22:00–23:00 IST: two designated fakes lock at +30% / +20% above top real turnover.

Prize payout (award_leaderboard_prizes) uses DB only — not this display layer.
"""

from __future__ import annotations

import hashlib
import math
import random
from datetime import datetime, time as dtime, timedelta
from typing import Any

try:
    import pytz

    IST = pytz.timezone("Asia/Kolkata")
except Exception:
    IST = None

DISPLAY_COUNT = 10
LOCK_IN_HOUR = 22  # 10 PM IST — top 6 fakes locked on display
LOCK_WINNER_COUNT = 6  # End of day: ranks 1–6 are fake; #7+ can be real
FAKE_POOL_SIZE = 100
MEANINGFUL_TURNOVER = 50

# Display prizes (7 winners). Payout command still uses DB LeaderboardSetting unless extended.
PRIZE_AMOUNTS = {
    "1st": 3000,
    "2nd": 1500,
    "3rd": 1000,
    "4th": 750,
    "5th": 500,
    "6th": 300,
    "7th": 100,
}

# End-of-day fake turnover = top real × multiplier (rank 1 highest)
LOCK_MULTIPLIERS = (1.35, 1.28, 1.22, 1.16, 1.10, 1.04)


def get_display_prizes() -> dict[str, str]:
    return {key: f"₹{amount:,}" for key, amount in PRIZE_AMOUNTS.items()}


def _round_turnover(value: float) -> float:
    """Display turnover must always be a multiple of 10 (e.g. 350, 860, 1200)."""
    return float(max(0, round(value / 10) * 10))

# --- 100 fake display names (Indian-style usernames) ---
_NAME_PARTS = (
    "Rajesh", "Priya", "Amit", "Sneha", "Rahul", "Anjali", "Vikram", "Kiran",
    "Rohit", "Divya", "Arjun", "Pooja", "Suresh", "Meera", "Karthik", "Lakshmi",
    "Manoj", "Deepa", "Sanjay", "Nisha", "Arun", "Kavya", "Gopal", "Swati",
    "Harish", "Rekha", "Naveen", "Shreya", "Prakash", "Anita", "Mohan", "Geeta",
    "Sunil", "Radha", "Ashok", "Uma", "Ravi", "Sunita", "Vinod", "Lata",
    "Mahesh", "Sita", "Dinesh", "Gita", "Ramesh", "Mala", "Sachin", "Neha",
    "Varun", "Isha", "Tarun", "Ritu", "Gaurav", "Simran", "Harsh", "Tanvi",
    "Yogesh", "Payal", "Nitin", "Muskan", "Pankaj", "Bhavna", "Sandeep", "Jyoti",
    "Alok", "Nidhi", "Dev", "Khushi", "Jay", "Palak", "Om", "Tanya",
    "Aditya", "Sakshi", "Vivek", "Preeti", "Akash", "Monika", "Rohit", "Sonali",
    "Kunal", "Aarti", "Manish", "Pallavi", "Siddharth", "Rashmi", "Abhishek", "Komal",
    "Vijay", "Shilpa", "Ankit", "Riya", "Shiva", "Nandini", "Bharat", "Chitra",
    "Chetan", "Swati", "Nikhil", "Anushka", "Sameer", "Trisha", "Farhan", "Zoya",
)

_SUFFIXES = ("", "_K", "_M", "_92", "_007", "_88", "_21", "_99", "_pro", "_x")


def _build_name_pool() -> list[str]:
    pool: list[str] = []
    for part in _NAME_PARTS:
        for suf in _SUFFIXES:
            name = f"{part}{suf}" if suf else part
            if name not in pool:
                pool.append(name)
            if len(pool) >= FAKE_POOL_SIZE:
                return pool
    i = 0
    while len(pool) < FAKE_POOL_SIZE:
        pool.append(f"Player_{1000 + i}")
        i += 1
    return pool


FAKE_NAME_POOL = _build_name_pool()


def _seed_for_period(period_date) -> random.Random:
    key = f"lb-{period_date.isoformat()}"
    digest = hashlib.sha256(key.encode()).hexdigest()
    return random.Random(int(digest[:16], 16))


def _now_ist(now=None) -> datetime:
    if now is None:
        from django.utils import timezone

        now = timezone.now()
    if IST is None:
        return now
    if now.tzinfo is None:
        from django.utils import timezone

        now = timezone.make_aware(now)
    return now.astimezone(IST)


def _day_progress(now_ist: datetime) -> float:
    """0 at midnight IST, ~1 at LOCK_IN_HOUR."""
    minutes = now_ist.hour * 60 + now_ist.minute + now_ist.second / 60.0
    end = LOCK_IN_HOUR * 60
    return min(1.0, max(0.0, minutes / end))


def _in_lock_in_window(now_ist: datetime) -> bool:
    return now_ist.hour >= LOCK_IN_HOUR


def _pick_daily_fakes(rng: random.Random) -> list[dict[str, Any]]:
    """10 fakes for this period, each with curve parameters."""
    names = rng.sample(FAKE_NAME_POOL, DISPLAY_COUNT)
    scene = rng.randint(0, 2)  # 0=hot start, 1=quiet morning, 2=mixed
    surge_idx = rng.randint(0, DISPLAY_COUNT - 1)
    surge_start_progress = rng.uniform(0.35, 0.75)
    surge_strength = rng.uniform(0.12, 0.28)
    lock_indices = sorted(rng.sample(range(DISPLAY_COUNT), LOCK_WINNER_COUNT))

    fakes = []
    for i, name in enumerate(names):
        # Evening target before lock-in: higher slots → higher turnover (~700–5500 band)
        slot_factor = 1.0 - (i / max(1, DISPLAY_COUNT - 1)) * 0.55
        evening_target = rng.uniform(2800, 5200) * slot_factor + rng.uniform(400, 900)
        growth_speed = rng.uniform(0.75, 1.35)
        peak_progress = rng.uniform(0.25, 0.85)
        jitter = rng.uniform(0.92, 1.08)

        morning_base = rng.uniform(320, 480) + (DISPLAY_COUNT - i) * rng.uniform(35, 65)
        lock_rank = (lock_indices.index(i) + 1) if i in lock_indices else 0

        fakes.append({
            "username": name,
            "is_fake": True,
            "evening_target": evening_target,
            "morning_base": morning_base,
            "growth_speed": growth_speed,
            "peak_progress": peak_progress,
            "jitter": jitter,
            "slot_index": i,
            "is_surge": i == surge_idx,
            "surge_start_progress": surge_start_progress,
            "surge_strength": surge_strength,
            "is_lock_winner": lock_rank > 0,
            "lock_rank": lock_rank,
            "_scene": scene,
        })
    return fakes


def _fake_turnover(fake: dict[str, Any], progress: float, rng: random.Random) -> float:
    """Compute display turnover for one fake at current time."""
    morning_base = float(fake.get("morning_base") or 450.0)
    slot = fake.get("slot_index", 0)

    # Early morning: use per-fake baseline (₹350–₹950), not a flat ₹120 for everyone
    if progress < 0.10:
        spread = (DISPLAY_COUNT - slot) * rng.uniform(18, 42)
        low = morning_base + spread * progress * 3.0
        return _round_turnover(max(280.0, low + rng.uniform(-40, 60)))

    target = fake["evening_target"] * fake["growth_speed"] * fake["jitter"]

    # Ease-in curve toward target (slow morning, faster mid-day)
    t = progress
    peak = fake["peak_progress"]
    if t <= peak:
        curve = (t / peak) ** 1.4 if peak > 0 else t
    else:
        rest = 1.0 - peak
        curve = 1.0 - 0.08 * ((t - peak) / rest) if rest > 0 else 1.0

    turnover = target * curve

    # Blend from morning base so values don't jump from flat 120
    if progress < 0.25:
        blend = 1.0 - (progress - 0.10) / 0.15
        turnover = turnover * (1.0 - blend) + morning_base * blend

    # Morning scene modifiers (first ~15% of day)
    scene = fake.get("_scene", 2)
    if progress < 0.15:
        if scene == 0:  # hot start — fakes look strong early
            turnover *= rng.uniform(1.15, 1.35)
        elif scene == 1:  # quiet — fakes look weak early
            turnover *= rng.uniform(0.55, 0.75)

    # Surge event — one fake jumps mid-day
    if fake.get("is_surge") and progress >= fake["surge_start_progress"]:
        surge_phase = min(1.0, (progress - fake["surge_start_progress"]) / 0.12)
        turnover *= 1.0 + fake["surge_strength"] * surge_phase

    # Morning cap: top fakes ~₹700–₹950, lower slots less
    if progress < 0.20:
        morning_cap = 420 + (DISPLAY_COUNT - slot) * rng.uniform(38, 58)
        morning_cap = min(morning_cap, 980)
        turnover = min(turnover, morning_cap)

    return _round_turnover(max(turnover, morning_base * 0.75))


def _apply_lock_in(
    entries: list[dict[str, Any]],
    fakes: list[dict[str, Any]],
    top_real_turnover: float,
) -> list[dict[str, Any]]:
    """End of day: ranks 1–6 are fake with turnover above top real player."""
    if top_real_turnover <= 0:
        top_real_turnover = 500.0

    lock_fakes = sorted(
        [f for f in fakes if f.get("is_lock_winner") and f.get("lock_rank")],
        key=lambda x: x["lock_rank"],
    )
    if len(lock_fakes) < LOCK_WINNER_COUNT:
        return entries

    lock_names: dict[str, float] = {}
    for fake in lock_fakes:
        rank = int(fake["lock_rank"])
        mult = LOCK_MULTIPLIERS[rank - 1]
        lock_names[fake["username"]] = _round_turnover(top_real_turnover * mult)

    sixth_turnover = lock_names[lock_fakes[LOCK_WINNER_COUNT - 1]["username"]]
    step = max(30.0, _round_turnover(top_real_turnover * 0.03))

    for e in entries:
        if not e.get("is_fake"):
            continue
        name = e.get("username")
        if name in lock_names:
            e["turnover"] = float(lock_names[name])
        else:
            # Non-lock fakes stay below 6th place
            e["turnover"] = min(float(e["turnover"]), max(250.0, sixth_turnover - step))

    entries.sort(key=lambda x: (-x["turnover"], x.get("username", "")))
    return entries


def build_display_leaderboard(
    period_date,
    real_rows: list[dict[str, Any]],
    current_user_id: int | None,
    user_turnover_real: float,
    now=None,
) -> dict[str, Any]:
    """
    Build display leaderboard (max DISPLAY_COUNT rows).

    real_rows: [{"user_id", "username", "turnover"}, ...] from UserDailyTurnover.
    """
    now_ist = _now_ist(now)
    rng = _seed_for_period(period_date)
    progress = _day_progress(now_ist)
    fakes = _pick_daily_fakes(rng)

    real_entries = [
        {
            "username": (r.get("username") or "").strip() or "Player",
            "turnover": _round_turnover(float(r.get("turnover") or 0)),
            "is_fake": False,
            "user_id": r.get("user_id"),
        }
        for r in real_rows
        if float(r.get("turnover") or 0) > 0
    ]
    top_real = max((e["turnover"] for e in real_entries), default=0.0)

    fake_entries = []
    for fake in fakes:
        t = _fake_turnover(fake, progress, rng)
        # During the day, keep fakes beatable when real players are active
        if not _in_lock_in_window(now_ist) and top_real > MEANINGFUL_TURNOVER:
            cap_mult = 1.55 if fake.get("is_surge") else 1.28
            t = min(t, _round_turnover(top_real * cap_mult))
        fake_entries.append({
            "username": fake["username"],
            "turnover": t,
            "is_fake": True,
        })

    combined = fake_entries + real_entries
    if _in_lock_in_window(now_ist):
        combined = _apply_lock_in(combined, fakes, top_real)

    combined.sort(key=lambda x: (-x["turnover"], x.get("username", "")))
    prizes = get_display_prizes()
    prize_by_rank = {
        1: prizes["1st"],
        2: prizes["2nd"],
        3: prizes["3rd"],
        4: prizes["4th"],
        5: prizes["5th"],
        6: prizes["6th"],
        7: prizes["7th"],
    }
    leaderboard = []
    for idx, e in enumerate(combined[:DISPLAY_COUNT], start=1):
        entry: dict[str, Any] = {
            "rank": idx,
            "username": e["username"],
            "turnover": e["turnover"],
        }
        if idx <= 7:
            entry["prize"] = prize_by_rank[idx]
        leaderboard.append(entry)

    # User rank among full combined list (display semantics)
    user_rank = 0
    if user_turnover_real > MEANINGFUL_TURNOVER and current_user_id is not None:
        full_sorted = sorted(combined, key=lambda x: (-x["turnover"], x.get("username", "")))
        for idx, e in enumerate(full_sorted, start=1):
            if not e.get("is_fake") and e.get("user_id") == current_user_id:
                user_rank = idx
                break
        if user_rank == 0:
            # User may not be in combined if turnover > 0 but below fakes — count position
            above = sum(1 for e in full_sorted if e["turnover"] > user_turnover_real)
            user_rank = above + 1

    return {
        "leaderboard": leaderboard,
        "user_stats": {
            "rank": user_rank,
            "turnover": _round_turnover(user_turnover_real),
        },
        "prizes": get_display_prizes(),
    }
