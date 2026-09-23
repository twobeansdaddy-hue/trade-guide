# Claude backend task: 장전 가이드 시세 근거 연결

상태: **Codex가 직접 구현·검증**. 2026-09-23 로컬 Claude Code 실행은 파일 변경 없이 오류로 종료됐고 쓰기 범위는 읽기 전용으로 되돌렸다. 이후 Codex가 캐시 로드 시각과 완료 주봉 해시의 자산별 저장을 구현했다. 전체 감사 상태는 여전히 `UNVERIFIED`다. 원본 응답 시각·조정 정책·원장 버전 연결은 후속 작업이다.

## 목표

이미 존재하는 `PremarketGuideInputAudit`와 `MarketHistoryService.getObservedCandles`를 연결하되, 실제로 전략에 사용한 완료 주봉의 근거만 기록한다. UI나 투자 전략 규칙은 바꾸지 않는다.

## 허용 범위

- `src/main/java/com/tradeguide/service/market/**`
- `src/main/java/com/tradeguide/service/strategy/**`
- `src/main/java/com/tradeguide/domain/strategy/**`
- `src/main/resources/db/migration/**`
- 대응하는 `src/test/java/com/tradeguide/service/market/**`, `src/test/java/com/tradeguide/service/strategy/**`, `src/test/java/com/tradeguide/repository/strategy/**`

`frontend/**`, `research/**`, 정책/하네스, 비밀 설정, Git 커밋/푸시는 수정하지 않는다. 현재 사용자 소유의 `research/scripts/track-a-infinite-buy/results/`는 건드리지 않는다.

## 계약

1. 제공자 호출이 성공한 시각은 `loadCompletedAt`으로만 취급한다. 거래소 공표 시각이나 원본 HTTP 응답 수신 시각으로 이름을 바꾸지 않는다.
2. 주봉 캐시 적중 시 새 시각을 만들지 말고, 최초 로드 관측 정보가 없다면 누락으로 기록한다. 동시 요청도 같은 로드 결과를 공유한다.
3. **전략이 사용한 완료 주봉**을 결정적 직렬화·SHA-256으로 자산별 식별한다. 원본 응답, 계좌번호, 키/토큰, 전체 보유내역을 저장하지 않는다. 조정 기준이 불명확하면 `UNKNOWN`/null로 남긴다.
4. 다수 자산의 시각/해시를 감사 헤더의 단일 `response_received_at`/`input_sha256`에 대입하지 않는다. 필요하면 별도 자산별 additive 테이블을 만든다. 가이드 전체 입력(시세+원장+위험 설정)이 확보되지 않았으므로 `UNVERIFIED`를 유지한다.
5. 강제 재생성에서 기존 근거가 남지 않도록 같은 트랜잭션으로 갱신한다. 조회 실패·후보/보유 혼합·제공자 변경·캐시 적중·기존 가이드를 테스트한다. 공개 API는 현재 상태 표시 외 불필요하게 확장하지 않는다.

## 완료 증거

집중 테스트와 전체 백엔드 테스트 결과, PostgreSQL Flyway/JPA 검증 가능 여부, 변경 파일·남은 누락 근거를 한국어로 보고한다. 커밋/푸시하지 않는다.
