# POWER-Z KM003C Android App (v2.0.0)

ChargerLAB **POWER-Z KM003C / KM002C** USB-C 테스터를 안드로이드 스마트폰에 USB-C OTG로 직접 연결해 사용할 수 있는 네이티브 안드로이드 앱(Kotlin + Jetpack Compose)입니다.

## 주요 기능

1. **프리미엄 계측 대시보드 (`측정` 탭)**
   - **VBUS(V), IBUS(A + 전류 방향), POWER(W)** 고정밀 디지털 계측 및 실시간 레벨 게이지 바
   - 누적 **용량(mAh)**, **에너지(Wh)**, 경과 시간, Peak/Avg 통계
   - **D+, D-, CC1, CC2, VDD, 내부 온도(°C)** 실시간 표시
   - **원하는 시점 측정 시작 + 조건부 자동 종료 엔진**
     - 측정/기록 간격 자유 설정 (`10ms ~ 60000ms` 프리셋 + 직접 입력) 및 하드웨어 SPS 연동 (`Simple 10Hz`, `2/10/50/1000 SPS`)
     - **자동 시작 트리거**: 전류 ≥ N A 감지 시 기록 자동 시작
     - **복수 자동 종료 조건**: 저전류 유지 종료(완충 감지), 시간 경과 종료, 저전력 유지 종료, 전압 기준(≤/≥) 유지 종료, 목표 용량(mAh) 도달 종료
     - 조건 유지 중 실시간 카운트다운 진행 바 표시 및 종료 시 자동 로컬 아카이브 저장
2. **인터랙티브 그래프 & 저장 목록 (`그래프` 탭)**
   - **실시간 / 현재 기록 / 저장 목록** 전환 지원
   - VBUS / IBUS / POWER 그라데이션 곡선 렌더링 + **터치·드래그 크로스헤어 커서**로 특정 시점의 정확한 V/A/W 확인
   - **1600×920 고해상도 다크 테마 PNG 그래프 이미지** 생성 → 갤러리(`Pictures/POWER-Z/`) 바로 저장 또는 공유
   - **CSV / JSON 파일** → 다운로드 폴더(`Download/POWER-Z/`) 바로 저장 또는 공유
   - 저장된 측정 기록 목록에서 과거 측정 다시 열기 / 재내보내기 / 삭제
3. **USB PD 프로토콜 분석기 (`PD 분석` 탭)**
   - 실시간 PD 패킷 캡처 (`GetData attr=0x0010`)
   - **PD 2.0 / 3.0 / 3.1 / 3.2** Control/Data/Extended 메시지 및 Source Capabilities PDO(Fixed, PPS, EPR AVS) 자동 디코딩
4. **충전 프로토콜 트리거 (`트리거` 탭 — PDM 가상 시리얼)**
   - KM003C CDC ACM 가상 시리얼(`translations/cmd.pdf` 규격) 지원
   - 프로토콜 자동감지(`entry list`), PD/PPS PDO 읽기 및 슬라이더 전압 트리거, QC 2.0/3.0, AFC, FCP, UFCS, 시리얼 콘솔
5. **시뮬레이터 모드**
   - 상단 플라스크(🧪) 아이콘으로 실제 장비 없이 모든 측정·자동종료·그래프 저장 기능 테스트 가능

## 빌드 및 테스트

```bash
gradle testDebugUnitTest assembleDebug
```