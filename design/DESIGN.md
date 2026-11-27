---
name: POWER-Z
tagline: WHOOP식 데이터 우선 다크 UI — 지표마다 색 하나, 큰 숫자, 링 게이지, 절제된 크롬.
spec: google-labs-code/design.md (frontmatter tokens + markdown rationale)
platform: Android (Jetpack Compose, Material3 dark only)
benchmark:
  primary: WHOOP 앱 (공식 Brand & Design Guidelines, 2023-07)
  secondary: webdesignhot WHOOP DESIGN.md (비공식, whoop.com 스토어프론트 기반 재구성)
  rule: 두 자료가 충돌하면 공식 PDF 우선. 비공식 파일은 타입 스케일·컴포넌트 구조·모션만 차용.

colors:
  # ── 배경 (공식: Background Gradient #283339 → #101518) ──
  bg-top: '#283339'
  bg-bottom: '#101518'
  bg-solid: '#101518'          # 그라데이션 못 쓰는 곳 (시트, 다이얼로그 뒤)
  surface: '#1A2227'           # 카드 (그라데이션 중간톤에서 한 단계 밝게)
  surface-strong: '#232D33'    # 눌림·선택된 카드, 시트
  border: '#2A353C'            # 1px 헤어라인 (필요할 때만)
  scrim: 'rgba(0,0,0,0.72)'

  # ── 텍스트 (공식: Black / White) ──
  text: '#FFFFFF'
  text-muted: '#9AA7AF'        # 라벨, 단위
  text-faint: '#5E6B73'        # 비활성, 보조 캡션
  text-on-brand: '#000000'

  # ── 브랜드 / 액션 (공식: TEAL #00F19F — CTA, 하이라이트, 긍정 평가) ──
  brand: '#00F19F'
  brand-pressed: '#00C482'
  brand-soft: '#0A2A22'        # 선택된 칩·탭 배경

  # ── 측정 데이터 색 (지표 1개 = 색 1개, 공식 팔레트에서 매핑) ──
  vbus: '#FFDE00'              # 전압   ← 공식 Medium Recovery 노랑
  ibus: '#0093E7'              # 전류   ← 공식 Strain 파랑
  power: '#FF7A2F'             # 전력   ← WHOOP에 없음. 기존 POWER-Z 주황 유지 (전력은 이 앱의 주인공)
  energy: '#00F19F'            # mAh/Wh ← 공식 TEAL (누적·긍정 지표)
  aux: '#7BA1BB'               # D+/D-/CC1/CC2/VDD ← 공식 Sleep (평가 없는 보조 데이터)
  pd: '#67AEE6'                # PD 패킷·PDO ← 공식 Recovery Blue (평가 없는 정보)

  # ── 상태 (공식: Recovery 3단계) ──
  ok: '#16EC06'                # 정상 / 연결됨
  warn: '#FFDE00'              # 주의 (고온, 임계 근접)
  danger: '#FF0026'            # 오류 / 과전류
  record: '#FF0026'            # 녹화 중 점·버튼

typography:
  # 공식: 글자 = Proxima Nova, 숫자 = DINPro. 둘 다 유료라 무료 대체 폰트를 앱에 넣음.
  words:
    family: Montserrat         # Proxima Nova 대체 (SIL OFL, Google Fonts)
    fallback: [sans-serif]
  numbers:
    family: D-DIN              # DINPro 대체 (SIL OFL, res/font에 번들)
    fallback: [Barlow, sans-serif]
    features: [tnum]           # 숫자 폭 고정 → 실시간 값이 흔들리지 않음
  scale:
    # 이름            size  weight  tracking  font     transform
    headline:       { size: 14, weight: 700, tracking: 0.10em, family: words,   transform: uppercase }  # 공식: Bold, 10%, 대문자
    section:        { size: 12, weight: 700, tracking: 0.10em, family: words,   transform: uppercase }
    body-lg:        { size: 16, weight: 600, tracking: 0,      family: words }  # 공식 B1
    body:           { size: 14, weight: 600, tracking: 0,      family: words }  # 공식 B2
    body-sm:        { size: 13, weight: 600, tracking: 0,      family: words }  # 공식 B3
    caption:        { size: 11, weight: 600, tracking: 0,      family: words }  # 공식 B4
    label:          { size: 11, weight: 700, tracking: 0.12em, family: words,   transform: uppercase }  # 지표 라벨 (VBUS, IBUS…)
    hero-number:    { size: 88, weight: 700, tracking: -0.02em, family: numbers }  # 링 중앙 메인 값 (W)
    metric-lg:      { size: 44, weight: 700, tracking: -0.01em, family: numbers }  # V / A 타일
    metric:         { size: 28, weight: 700, tracking: 0,      family: numbers }  # mAh, Wh, 시간
    metric-sm:      { size: 18, weight: 700, tracking: 0,      family: numbers }  # D+/D-/CC, 통계
    unit:           { size: 14, weight: 600, tracking: 0,      family: words }    # 숫자 옆 단위, text-muted
    mono:           { size: 12, weight: 500, tracking: 0,      family: monospace } # PD 패킷 hex, 시리얼 콘솔

radius:
  sm: 4        # 배지, 작은 태그
  md: 8        # 버튼, 카드, 입력창 — 같은 값 (WHOOP 원칙: 버튼·카드 반경 통일)
  lg: 16       # 바텀시트 상단
  pill: 9999   # 상태 필, 칩, 링

spacing:
  base: 4
  scale: [4, 8, 12, 16, 20, 24, 32, 40, 48]
  screen-gutter: 16
  card-padding: 20

elevation:
  # 그림자 대신 밝기 차이로 깊이 표현. 그림자 없음.
  level-0: bg gradient
  level-1: surface
  level-2: surface-strong
  glow: '0 0 24dp brand @ 20%'   # 녹화 중 메인 링, 선택된 프리셋에만

motion:
  standard: { easing: 'cubic-bezier(0.4, 0, 0.2, 1)', duration: 150ms }
  emphasized: { easing: 'cubic-bezier(0.2, 0, 0, 1)', duration: 240ms }
  gauge-fill: { easing: 'cubic-bezier(0.65, 0, 0.35, 1)', duration: 1200ms }  # 화면 진입 시 링 채움 (1회)
  live-value: { duration: 0ms }  # 실시간 숫자는 애니메이션 없이 즉시 갱신

components:
  power-ring:
    use: '측정 탭 메인. 원형 링 = 현재 W / 최대 W(또는 PDO 최대). 중앙에 hero-number W, 아래 label "POWER".'
    stroke: power, 12dp, round cap
    track: surface-strong
  metric-tile:
    use: 'VBUS / IBUS 2열 타일. label + metric-lg + unit + 미니 스파크라인(최근 30초).'
    bg: surface
    radius: md
    padding: card-padding
    accent: '숫자 색 = 지표 색. 카드 테두리·배경엔 색 넣지 않음.'
  stat-row:
    use: 'mAh / Wh / 경과시간 / Peak / Avg. 라벨 위, 숫자 아래, 구분선 없이 간격으로만 분리.'
  progress-arc:
    use: '자동 종료 조건 카운트다운, 목표 mAh 진행률. 반원 아크, 색 = energy.'
  status-pill:
    use: '연결 상태, 녹화 상태, PD 버전. pill 반경, 상태색 배경 + 검정 글자, label 스타일.'
  button-primary:
    bg: brand
    color: text-on-brand
    radius: md
    height: 48
    font: headline
    use: '녹화 시작, 저장, 트리거 실행 — 화면당 하나만.'
  button-secondary:
    bg: transparent
    border: '1.5dp text'
    color: text
    radius: md
    height: 48
  record-button:
    use: '녹화 중에는 button-primary 대신 danger 배경의 정지 버튼 + 깜빡이는 점.'
  chart:
    use: '그래프 탭. 배경 그리드 최소화(가로선 3~4개, border 색). 선 2dp, 아래 영역 지표색 12% → 0% 그라데이션.'
    crosshair: '세로선 text @ 40% + 상단 플로팅 카드에 V/A/W를 각 지표 색으로.'
    range-chips: '30s / 1m / 5m / 전체 — pill, 선택 시 brand-soft 배경 + brand 글자.'
  session-card:
    use: '저장 목록. 날짜(label) / 총 mAh(metric) / 최대 W / 소요시간 + 오른쪽에 미니 W 곡선.'
  bottom-nav:
    use: '측정 / 그래프 / PD 분석 / 트리거. 아이콘 + label, 선택 = text, 비선택 = text-faint. 인디케이터 없음.'
  log-row:
    use: 'PD 분석·시리얼 콘솔. mono 폰트, 방향(SRC→SNK)만 색 구분, 나머지 text-muted.'
---

# POWER-Z 디자인 시스템

## 1. 컨셉

**"충전기를 위한 WHOOP."** WHOOP이 심박·회복을 다루는 방식 그대로 전압·전류·전력을 다룬다.

- **데이터가 유일한 색**: 배경·카드·테두리는 무채색. 색은 오직 측정값과 상태에만 쓴다.
- **지표 하나 = 색 하나**: VBUS는 어디서나 노랑, IBUS는 어디서나 파랑, POWER는 어디서나 주황. 숫자·곡선·라벨 점·내보낸 PNG까지 같은 색.
- **큰 숫자가 주인공**: 화면을 열면 지금 몇 W인지 1초 안에 읽혀야 한다.
- **링과 아크**: 비율(현재/최대, 진행/목표)은 막대 대신 링·아크로.
- **크롬은 조용히**: 헤드라인은 작은 대문자 + 넓은 자간. 장식용 아이콘·그라데이션 테두리·네온 글로우 없음.

## 2. 벤치마크 출처와 판단

| 항목 | 공식 PDF | 비공식 webdesignhot | 채택 |
|---|---|---|---|
| 배경 | 그라데이션 `#283339 → #101518` | 순검정 `#000000` | **공식** — 앱 화면 기준이라서. 비공식은 웹 스토어 기준 |
| 브랜드색 | TEAL `#00F19F` | 라임 `#a0d10b` | **공식 TEAL** — 라임은 웹 마케팅용 |
| 글자 폰트 | Proxima Nova Semibold | Inter | Proxima 대체 **Montserrat** |
| 숫자 폰트 | DINPro Bold | Inter tnum | DIN 대체 **D-DIN** |
| 헤드라인 | Bold, 대문자, 자간 10% | 800, 대문자, 음수 자간 | **공식** |
| 상태 3색 | `#16EC06 / #FFDE00 / #FF0026` | 동일 | 동일 |
| 반경·간격·모션·컴포넌트 구조 | 없음 | 8px 통일, 4 기반 간격, 링 채움 모션 | **비공식에서 차용** |

원본은 `design/reference/` 폴더에 있다 (git 제외).

## 3. 화면별 적용

### 측정 탭
1. 상단: 연결 상태 필 + 시뮬레이터 토글 + 녹화 상태 필.
2. 중앙: **power-ring** (화면 폭의 70%). 중앙에 `27.4` + `W`.
3. 그 아래 2열 **metric-tile**: VBUS / IBUS(전류 방향 화살표 포함).
4. **stat-row**: mAh · Wh · 경과시간 / Peak · Avg.
5. 접히는 보조 섹션: D+ D- CC1 CC2 VDD 온도 (`aux` 색, metric-sm).
6. 하단 고정: **button-primary** "녹화 시작" (녹화 중엔 record-button). 자동 종료 조건이 있으면 버튼 위에 **progress-arc** 카운트다운.

### 그래프 탭
- 상단 세그먼트: 실시간 / 현재 기록 / 저장 목록.
- 범례 칩(V / A / W 켜고 끄기) + 범위 칩.
- 차트는 화면 폭 꽉 채움, 카드에 넣지 않음.
- 저장 목록은 **session-card** 리스트.
- PNG 내보내기도 같은 토큰 사용 (배경 그라데이션, 지표 색, D-DIN 숫자).

### PD 분석 / 트리거 탭
- 이 두 탭만 정보 밀도 우선. **log-row** + mono 폰트.
- PDO 목록은 카드형: 전압(metric, vbus 색) / 최대 전류(metric-sm, ibus 색) / 타입 필(Fixed·PPS·AVS).
- 트리거 슬라이더 트랙은 surface-strong, 손잡이와 채움은 vbus 색.

## 4. 해야 할 것 / 하지 말 것

**해야 할 것**
- 모든 숫자에 tabular figures — 실시간 값이 바뀌어도 자릿수 폭이 흔들리지 않게.
- 단위는 숫자보다 작고 `text-muted`로 (`27.4` 크게, `W` 작게).
- 카드 하나에 지표 색은 하나만.
- 터치 영역 최소 48dp.
- 대비: 지표 색 숫자는 surface 위에서 4.5:1 이상 확인 (`ibus #0093E7`은 큰 숫자 전용, 작은 글자엔 `#3AB0FF` 사용).

**하지 말 것**
- 카드 테두리에 색 넣기, 네온 글로우 남발 (glow는 녹화 중 메인 링에만).
- 같은 지표를 화면마다 다른 색으로 쓰기.
- 녹색·노랑·빨강을 데이터 색으로 쓰기 — 이 셋은 **상태 전용**. (예외: VBUS 노랑은 상태 warn과 같은 값이므로 경고 표시는 항상 아이콘 + 필로 구분)
- 버튼·카드 반경 섞기 (8dp 통일).
- WHOOP 로고·이름·고유 지표명(Recovery, Strain 등) 사용 — **시스템만 차용, 브랜드는 POWER-Z**.

## 5. Compose 반영 순서

1. `ui/theme/Color.kt` — 위 colors 토큰을 그대로 상수화, 기존 `VoltYellow` 등은 새 이름으로 교체.
2. `ui/theme/Type.kt` — Montserrat / D-DIN 폰트 패밀리 등록 (`res/font/`), scale을 `TextStyle`로. 숫자 스타일엔 `fontFeatureSettings = "tnum"`.
3. `ui/theme/Theme.kt` — Material3 `darkColorScheme` 매핑 (primary=brand, background=bg-solid, surface=surface…) + 배경 그라데이션 `Modifier`.
4. 공통 컴포넌트 `ui/components/` — PowerRing, MetricTile, StatRow, ProgressArc, StatusPill, SessionCard.
5. 화면별 교체: 측정 → 그래프 → PD 분석 → 트리거.
