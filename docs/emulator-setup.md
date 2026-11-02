# Pleos Connect 에뮬레이터 설정 가이드

BlinkerLeague를 실행·테스트하기 위한 **Pleos Connect Emulator**와 **Virtual Console** 설정 방법을 정리한 문서입니다.

> 공식 문서
> - [Pleos Connect SDK 에뮬레이터 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
> - [Pleos Connect Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

## 요구 사항

- 최신 Android Studio
- Android Emulator [시스템 요구 사항](https://developer.android.com/studio/run/emulator#requirements) 충족
- **Pleos Connect Emulator v2.1.0 이상** (Virtual Console을 쓰려면 2.1.0 이상이어야 함)
- 이전 버전의 Pleos Connect Emulator가 설치돼 있다면 먼저 삭제

## 1. 시스템 이미지 업데이트 사이트 추가

1. Android Studio → **Tools > SDK Manager**
2. **SDK Update Sites** 탭에서 **+** 클릭
3. Name에 `Pleos Connect System Image`를 입력하고, URL은 OS에 맞게 입력

   | OS | URL |
   | --- | --- |
   | macOS (Apple Silicon) | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml` |
   | Windows / Linux | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` |

4. **OK** 클릭

## 2. 시스템 이미지 설치

1. **SDK Platforms** 탭 → 하단 **Show Package Details** 체크
2. **Android API 34** 항목 아래의 **Pleos Connect** 시스템 이미지 체크
3. **OK** → 설치가 끝나면 **Finish**
4. Android Studio 재시작

## 3. AVD 생성

1. **Tools > Device Manager**에서 **Create device** (구버전: Tools > AVD Manager → Create Virtual Device)
2. Form Factor: **Automotive** → **Pleos Connect v2.1.0** 선택 → **Next**
3. **Advanced Settings** 탭 (구버전: Show Advanced Settings)
   - Startup > Default boot: **Cold**
   - Emulated Performance > RAM: **4 GB**
   - (구버전) Graphics: **Hardware**, Boot option: **Cold boot**
4. **Device** 탭으로 돌아와 API: **API 34** → **Pleos Connect v2.1.0 APIs** 선택 → **Finish**

생성된 AVD 이름 예시: `Pleos_Connect_v2.1.0_API_34`

## 4. 에뮬레이터 실행

다음 중 편한 방법을 사용합니다. 첫 부팅(Cold boot)은 몇 분 걸릴 수 있습니다.

**A. Device Manager**
- **View > Tool Windows > Device Manager** (오른쪽 사이드바의 휴대폰 아이콘)
- `Pleos_Connect_v2.1.0_API_34` 줄 오른쪽의 **▶ (Start)** 클릭

**B. 앱 실행과 함께**
- 상단 툴바 기기 드롭다운에서 `Pleos_Connect_v2.1.0_API_34` 선택 → **Run ▶**

**C. 터미널**
```bash
~/Library/Android/sdk/emulator/emulator -list-avds                    # AVD 목록 확인
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0_API_34
```
```powershell
# Windows (PowerShell)
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -list-avds
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pleos_Connect_v2.1.0_API_34
```

실행 확인:
```bash
adb devices
# List of devices attached
# emulator-5554	device
```

## 5. Virtual Console 실행

Virtual Console은 브라우저에서 차량 신호(기어, 속도, 방향지시등 등)를 조작해 앱을 테스트할 수 있는 도구입니다. **에뮬레이터가 실행 중인 상태**에서 진행합니다.

1. 에뮬레이터의 8088 포트 포워딩
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
2. 브라우저에서 접속: <http://127.0.0.1:8088/>

사용 팁:
- **Emulation Controls**: 왼쪽 Domain 목록(Vehicle, Motion, ADAS, Convenience, Safety, Energy, Climate)에서 영역을 고른 뒤 기능 카드의 값을 변경
- **테마 변경**: 기어가 **P**일 때 왼쪽 상단 버튼으로 Highway / Forest 선택 (테스트 기능에는 영향 없음)
- **시점 변경**: Highway 테마에서 오른쪽 상단 버튼으로 1인칭 / 3인칭 전환

> 에뮬레이터를 재시작하면 포트 포워딩이 풀리므로 `adb forward` 명령을 다시 실행해야 합니다.

## 문제 해결

### `zsh: command not found: adb` / `'adb'은(는) ... 아닙니다`

adb는 SDK에 설치돼 있지만 PATH에 등록되지 않은 상태입니다. 한 번만 설정하면 됩니다.

```bash
echo 'export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"' >> ~/.zshrc
source ~/.zshrc
adb devices   # 동작 확인
```

PATH 설정 없이 바로 쓰려면 전체 경로로 실행합니다.
```bash
~/Library/Android/sdk/platform-tools/adb forward tcp:8088 tcp:8088
```

**Windows**: 시스템 속성 → 환경 변수 → 사용자 변수 `Path`에 `%LOCALAPPDATA%\Android\Sdk\platform-tools`를 추가하고 터미널을 다시 엽니다.
전체 경로로 실행하려면:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" forward tcp:8088 tcp:8088
```

### Windows에서 에뮬레이터가 느리거나 실행되지 않을 때

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -accel-check
# "WHPX ... is installed and usable" 이 나오면 정상
```
나오지 않으면 Windows 기능 켜기/끄기에서 **Windows 하이퍼바이저 플랫폼**을 켜고 재부팅합니다. (BIOS에서 가상화(VT-x/AMD-V)도 켜져 있어야 함)

### Virtual Console(`127.0.0.1:8088`)에 접속되지 않을 때

1. Pleos Connect Emulator가 실행 중인지 확인
2. Virtual Console을 지원하는 이미지(v2.1.0 이상)인지 확인
3. `adb devices`로 에뮬레이터가 연결됐는지 확인
4. `adb forward tcp:8088 tcp:8088` 재실행
5. PC의 8088 포트를 다른 프로세스가 쓰고 있는지 확인: `lsof -i :8088` (Windows: `netstat -ano | findstr 8088`)
6. 브라우저에서 <http://127.0.0.1:8088/> 재접속

### 그 외

- [Android Emulator 알려진 문제](https://developer.android.com/studio/run/emulator-troubleshooting)
- [Android Studio 알려진 문제](https://developer.android.com/studio/known-issues)
