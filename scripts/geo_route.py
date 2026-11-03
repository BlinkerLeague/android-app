"""경로 좌표 계산 공통 도구 (scripts/*.py에서 import).

- road_points: 공개 OSRM 경로 서버로 출발지→목적지 도로 좌표를 받음 (macOS 기본 Python SSL 문제로 curl 사용)
- walk: 폴리라인을 일정 간격(m)의 점으로 나눔
- straight: 시작점에서 방위각 방향으로 직진하는 점들
"""
import json
import math
import subprocess

M_PER_DEG = 111_320.0


def distance_m(a, b):
    dlat = (b[0] - a[0]) * M_PER_DEG
    dlng = (b[1] - a[1]) * M_PER_DEG * math.cos(math.radians(a[0]))
    return math.hypot(dlat, dlng)


def bearing_deg(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    y = math.sin(lo2 - lo1) * math.cos(la2)
    x = math.cos(la1) * math.sin(la2) - math.sin(la1) * math.cos(la2) * math.cos(lo2 - lo1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def road_points(start, dest):
    url = (
        "https://router.project-osrm.org/route/v1/driving/"
        f"{start[1]},{start[0]};{dest[1]},{dest[0]}?overview=full&geometries=geojson"
    )
    body = subprocess.run(["curl", "-s", "-m", "20", url], check=True, capture_output=True, text=True).stdout
    coords = json.loads(body)["routes"][0]["geometry"]["coordinates"]
    return [(lat, lng) for lng, lat in coords]


def walk(points, step_m):
    out, carry = [points[0]], 0.0
    for a, b in zip(points, points[1:]):
        seg = distance_m(a, b)
        pos = step_m - carry
        while pos <= seg:
            t = pos / seg
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            pos += step_m
        carry = (carry + seg) % step_m
    return out


def straight(start, bearing, step_m, n):
    pts, (lat, lng), b = [start], start, math.radians(bearing)
    for _ in range(n):
        lat += step_m * math.cos(b) / M_PER_DEG
        lng += step_m * math.sin(b) / (M_PER_DEG * math.cos(math.radians(lat)))
        pts.append((lat, lng))
    return pts
