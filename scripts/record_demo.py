#!/usr/bin/env python3
r"""시연 영상 녹화 (README "해야 할 일": 좌회전 깜빡이 성공 → 우회전 실패 → 운행 종료 → 점수·퀘스트·업적 화면).

Pleos 에뮬레이터에서 한 번에 진행하며 화면을 녹화합니다.
  1. 앱을 디버그 옵션 --ei parkEndSec 10 으로 다시 띄움 (P단 10초면 운행 종료, 기본은 3분)
  2. 녹화 시작 (adb screenrecord, 1920x1080). screenrecord 한 번은 최대 3분이라 끝나면 이어서 다음 파일로 녹화
  3. Virtual Console로 vehicle_probe_sim.py demo 재생: D → 좌회전(깜빡이 켬) → 우회전(깜빡이 없음) → 약 2.2km → P
     (회전 구간 40km/h × 약 28초 ≈ 0.3km + 100km/h × DEMO_CRUISE_SEC(기본 70초) ≈ 1.9km.
      짧은 운행 기준 1km를 넘기고, 평점 50이어도 floor(2.2 × 50 / 100) = 1이라 결과 화면에 +1P가 보임.
      환경 변수 DEMO_CRUISE_SEC로 덮어쓸 수 있음. 서버 모드에서 서버 기준값이 3km이면 DEMO_CRUISE_SEC=115)
     (주행 중에는 시스템이 앱 화면을 "주행 중에는 사용할 수 없습니다"로 덮고, 앱은 "기록 중" 화면)
  4. 운행 종료 음성이 끝나면 홈 → 최근 경기 → 경기 결과 → 플레이 보기 → 퀘스트 → 홈 → 업적(배지) → 홈 순서로 화면을 넘김
  5. 녹화를 멈추고 automotive/build/demo/blinker_demo_1.mp4, _2.mp4 … 로 가져옴 (편집 때 이어 붙이기)

준비: 디버그 빌드 설치(Fake 모드 권장: ./gradlew :automotive:installDebug), 등록(온보딩) 완료,
      adb forward tcp:8088 tcp:8088, 속도 권한 허용.
      앱 데이터 초기화가 필요하면 앱이 user 10에 설치돼 있으므로
      adb shell pm clear --user 10 com.example.blinkerleague  (초기화 뒤 온보딩·속도 권한을 다시 진행)
      Windows: PYTHONUTF8=1과 ADB(adb.exe 경로)를 꼭 지정 (vconsole.py의 기본 adb 경로가 macOS 경로라서)
        Git Bash:   PYTHONUTF8=1 ADB=/c/android/sdk/platform-tools/adb.exe python scripts/record_demo.py
        PowerShell: $env:PYTHONUTF8=1; $env:ADB="C:\android\sdk\platform-tools\adb.exe"; python scripts\record_demo.py
사용: python3 scripts/record_demo.py
"""
import os
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

PKG = "com.example.blinkerleague"
ADB = os.environ.get(
    "ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb") if sys.platform == "darwin" else "adb")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "..", "automotive", "build", "demo")
DEVICE_PATTERN = "/sdcard/blinker_demo_{}.mp4"
SEGMENT_SEC = 180  # screenrecord --time-limit 최대값
PARK_END_SEC = int(os.environ.get("PARK_END_SEC", "10"))
# 운행 종료 뒤 결과·플레이·퀘스트·업적 화면을 각각 보여 주는 시간(초)
SCREEN_SEC = float(os.environ.get("DEMO_SCREEN_SEC", "4"))
# vehicle_probe_sim.py demo의 100km/h 구간 길이. 70초 ≈ 1.9km, 회전 구간 포함 약 2.2km (+1P가 보이게)
os.environ.setdefault("DEMO_CRUISE_SEC", "70")


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace",
                          check=check).stdout


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def wait_logcat(pattern, timeout, after=None):
    """logcat에서 pattern이 나올 때까지 기다림. after가 있으면 그 줄 다음부터 찾음"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        # 기다리는 로그는 TripCollection(운행 끝) · PleosTtsAnnouncer(안내) 태그뿐이라 그 둘만 받음
        lines = adb("logcat", "-d", "-s", "TripCollection", "PleosTtsAnnouncer").splitlines()
        if after is not None:
            idx = next((i for i, l in enumerate(lines) if re.search(after, l)), None)
            lines = lines[idx + 1:] if idx is not None else []
        if any(re.search(pattern, l) for l in lines):
            return True
        time.sleep(2)
    return False


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml"))
    return root.iter("node")


def tap_text(match, label):
    """화면에서 글자가 match(정규식)인 첫 요소를 누름"""
    for n in nodes():
        text = n.get("text") or ""
        if text and re.search(match, text):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            log(f"누름: {label} ({text})")
            return True
    log(f"못 찾음: {label}")
    return False


class Recorder:
    """screenrecord를 끝날 때마다 다음 파일로 이어서 실행"""

    def __init__(self):
        self.stopped = threading.Event()
        self.count = 0
        self.started = None
        self.thread = threading.Thread(target=self._loop, daemon=True)

    def _loop(self):
        while not self.stopped.is_set():
            self.count += 1
            path = DEVICE_PATTERN.format(self.count)
            adb("shell", "rm", "-f", path, check=False)
            subprocess.run([ADB, "shell", "screenrecord", "--size", "1920x1080",
                            "--time-limit", str(SEGMENT_SEC), path], check=False)

    def start(self):
        self.started = time.time()
        self.thread.start()

    def stop_and_pull(self):
        elapsed = time.time() - self.started if self.started else 0
        self.stopped.set()
        adb("shell", "pkill", "-INT", "screenrecord", check=False)
        self.thread.join(timeout=20)
        time.sleep(2)
        for i in range(1, self.count + 1):
            out = os.path.abspath(os.path.join(OUT_DIR, f"blinker_demo_{i}.mp4"))
            adb("pull", DEVICE_PATTERN.format(i), out, check=False)
            log(f"저장: {out}")
        log(f"녹화 약 {elapsed:.0f}초 (한 파일 최대 {SEGMENT_SEC}초)")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    log(f"앱 다시 시작 (P단 {PARK_END_SEC}초면 운행 종료)")
    adb("shell", "am", "force-stop", PKG)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--ei", "parkEndSec", str(PARK_END_SEC))
    time.sleep(6)

    log("녹화 시작")
    for old in os.listdir(OUT_DIR):
        if old.startswith("blinker_demo"):
            os.remove(os.path.join(OUT_DIR, old))
    recorder = Recorder()
    recorder.start()
    time.sleep(3)
    try:
        log("운행 재생: 좌회전 깜빡이 켬 → 우회전 깜빡이 없음 → P")
        subprocess.run([sys.executable, os.path.join(HERE, "vehicle_probe_sim.py"), "demo"], check=True,
                       stdout=subprocess.DEVNULL)
        if not wait_logcat(r"TripCollection: 운행 끝", timeout=PARK_END_SEC + 60):
            log("운행 종료를 못 봄 — 앱 로그를 확인하세요")
        wait_logcat(r"PleosTtsAnnouncer: 안내", timeout=20, after=r"TripCollection: 운행 끝")
        time.sleep(3)  # 홈 화면 보여주기

        tap_text(r"^오늘 \d\d:\d\d$", "최근 경기 첫 줄"); time.sleep(SCREEN_SEC)
        tap_text(r"^플레이 보기$", "플레이 보기"); time.sleep(SCREEN_SEC)
        adb("shell", "input", "keyevent", "BACK"); time.sleep(2)
        tap_text(r"^퀘스트 · ", "퀘스트"); time.sleep(SCREEN_SEC)
        adb("shell", "input", "keyevent", "BACK"); time.sleep(1)
        adb("shell", "input", "keyevent", "BACK"); time.sleep(3)
        tap_text(r"^업적$", "업적 탭"); time.sleep(SCREEN_SEC)
        tap_text(r"^홈$", "홈 탭"); time.sleep(2)
    finally:
        log("녹화 멈춤")
        recorder.stop_and_pull()


if __name__ == "__main__":
    main()
