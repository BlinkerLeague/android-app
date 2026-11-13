"""Pleos Virtual Console·에뮬레이터 조작 공통 도구 (다른 scripts/*.py에서 import).

- Virtual Console 쓰기는 브라우저 세션(POST /browser/session, 3초 타임아웃)이 살아 있어야 받음 → 1초마다 세션 신호를 보냄
- 주입할 때마다 기기 시각으로 환산한 주입 시각을 Logcat `ProbeInject` 태그에 남김 → 콜백 로그와 같은 시계로 지연을 계산
  (호스트↔기기 시계 차이는 시작할 때 `adb shell date`로 몇 번 재서 왕복 시간이 가장 짧은 값을 씀, 오차 대략 ±10ms)
"""
import os
import subprocess
import threading
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
CONSOLE = "http://127.0.0.1:8088"
APP = "com.example.blinkerleague"


def adb(*args, check=False):
    return subprocess.run([ADB, *args], check=check, capture_output=True, text=True, encoding="utf-8",
                          errors="replace").stdout


class Console:
    def __init__(self):
        adb("forward", "tcp:8088", "tcp:8088")
        self.client = f"probe-{uuid.uuid4().hex[:8]}"
        self._stop = threading.Event()
        threading.Thread(target=self._heartbeat, daemon=True).start()
        self.offset_ms, self.tz = self._device_clock()
        self._measured = time.time()
        time.sleep(1.5)

    # ---- Virtual Console ----
    def _request(self, path, method):
        try:
            urllib.request.urlopen(urllib.request.Request(CONSOLE + path, method=method), timeout=5).read()
            return True
        except Exception as e:
            print(f"Virtual Console 요청 실패 {path}: {e}")
            return False

    def _heartbeat(self):
        while not self._stop.is_set():
            self._request(f"/browser/session?client={self.client}", "POST")
            self._stop.wait(1.0)

    def put(self, path, label):
        """PUT 요청을 보내고, 보내기 직전 시각을 주입 시각으로 기록함"""
        # 호스트·기기 시계가 몇 분 사이에도 100ms 이상 벌어지는 것을 봐서 15초마다 다시 잼
        if time.time() - self._measured > 15:
            self.offset_ms, self.tz = self._device_clock(quiet=True)
            self._measured = time.time()
        sent = time.time() * 1000
        ok = self._request(path, "PUT")
        self.mark(label if ok else f"{label} (실패)", sent)
        return ok

    def close(self):
        self._stop.set()

    # ---- 시각 ----
    def _device_clock(self, quiet=False):
        best = None
        for _ in range(5):
            t0 = time.time() * 1000
            dev = int(adb("shell", "date", "+%s%3N").strip())
            t1 = time.time() * 1000
            if best is None or t1 - t0 < best[0]:
                best = (t1 - t0, dev - (t0 + t1) / 2)
        z = adb("shell", "date", "+%z").strip()  # 예: +0900
        sign = -1 if z.startswith("-") else 1
        tz = timezone(sign * timedelta(hours=int(z[1:3]), minutes=int(z[3:5])))
        if not quiet:
            print(f"기기 시계 차이 {best[1]:+.0f}ms (왕복 {best[0]:.0f}ms), 기기 시간대 {z}")
        return best[1], tz

    def device_time(self, host_ms):
        return datetime.fromtimestamp((host_ms + self.offset_ms) / 1000, self.tz).strftime("%H:%M:%S.%f")[:-3]

    def mark(self, label, host_ms=None):
        """Logcat ProbeInject에 `기기시각 라벨`을 남김 (adb 호출은 기다리지 않음)"""
        host_ms = host_ms if host_ms is not None else time.time() * 1000
        line = f"{self.device_time(host_ms)} {label}"
        print("inject", line)
        subprocess.Popen([ADB, "shell", "log", "-t", "ProbeInject", "-p", "i", f"'{line}'"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # ---- 앱 프로세스 상태 ----
    def proc_state(self, note=""):
        """dumpsys activity로 앱 프로세스 상태를 보고 ProbeInject에 남김 (cached·frozen 여부)"""
        out = adb("shell", "dumpsys", "activity", "processes", APP)
        state = next((l.strip() for l in out.splitlines() if "curProcState=" in l), "프로세스 없음")
        cached = next((l.strip() for l in out.splitlines() if l.strip().startswith("cached=")), "")
        # 동결 여부는 dumpsys에 잘 드러나지 않아 이벤트 로그의 마지막 am_freeze/am_unfreeze로 판단함
        events = [l for l in adb("logcat", "-d", "-b", "events", "-s", "am_freeze", "am_unfreeze").splitlines() if APP in l]
        last = events[-1].split(" I ")[-1].strip() if events else "기록 없음"
        frozen = "yes" if events and "am_freeze" in events[-1] else "no"
        self.mark(f"procState {note} | {state} | {cached} | frozen={frozen} (마지막 이벤트: {last})")


def geo_fix(lat, lng):
    subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.7f}", f"{lat:.7f}"], check=False, capture_output=True)
