# POWER-Z KM003C Android App

ChargerLAB **POWER-Z KM003C / KM002C** USB-C 테스터를 안드로이드 스마트폰에 USB-C OTG로 직접 연결해 사용할 수 있는 네이티브 안드로이드 앱(Kotlin + Jetpack Compose)입니다.

## 주요 기능

1. **실시간 측정 대시보드 (`측정` 탭)**
   - **VBUS(V), IBUS(A + 전류 방향), POWER(W)** 고정밀 디지털 계측
   - 누적 **용량(mAh)**, **에너지(mWh)**, 경과 시간, Peak/Avg 통계
   - **D+, D-, CC1, CC2, VDD, 내부 온도(°C)** 실시간 표시
   - **AdcQueue 고속 스트리밍** (`Simple 10Hz`, `2 / 10 / 50 / 1000 SPS`) — AES-128-ECB 인증(`0x4C`) 자동 수행
   - 측정 데이터 **CSV 내보내기 / 공유**
2. **실시간 그래프 (`그래프` 탭)**
   - VBUS / IBUS / POWER 다중 채널 실시간 오실로스코프 (10초 / 30초 / 1분 / 전체 창)
3. **USB PD 프로토콜 분석기 (`PD 분석` 탭)**
   - 실시간 PD 패킷 캡처 (`GetData attr=0x0010`)
   - **PD 2.0 / 3.0 / 3.1 / 3.2** Control/Data/Extended 메시지 및 Source Capabilities PDO(Fixed, PPS, EPR AVS) 자동 디코딩
4. **충전 프로토콜 트리거 (`트리거` 탭 — PDM 가상 시리얼)**
   - KM003C CDC ACM 가상 시리얼(`translations/cmd.pdf` 규격) 지원
   - 프로토콜 자동감지(`entry list`), PD/PPS PDO 읽기 및 전압 트리거, QC 2.0/3.0, AFC, FCP, UFCS, 커스텀 시리얼 콘솔
5. **시뮬레이터 모드**
   - 상단 플라스크(🧪) 아이콘으로 실제 장비 없이 UI와 기능을 미리 테스트 가능

## USB 연결 방법

- 안드로이드 스마트폰 USB-C 포트 ↔ **KM003C 우측 HID/PC 포트**를 USB-C to C 데이터 케이블(또는 OTG 어댑터)로 연결합니다.
- 연결 시 자동으로 USB 권한 팝업이 뜨며, 승인하면 **Vendor Bulk (IF0)** 및 **CDC ACM 가상 시리얼 (IF1/IF2)** 인터페이스가 자동 초기화됩니다. (Vendor Bulk 미지원 환경에서는 **HID IF3**로 자동 폴백)

## 빌드 방법

```bash
gradle testDebugUnitTest assembleDebug
```

빌드된 APK 경로: `app/build/outputs/apk/debug/app-debug.apk`