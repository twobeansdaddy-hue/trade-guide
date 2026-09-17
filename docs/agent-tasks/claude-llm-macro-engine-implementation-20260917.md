# Agent Task Contract

## Identity

- Task ID: `claude-llm-macro-engine-implementation-20260917`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/llm-macro-engine`

## Outcome

리서치 보고서(`llm-macro-strategy-research.md`)의 실증적 연구 결과를 바탕으로 **LLM 기반 매크로 분석 및 동적 매매 가이드 엔진**을 구현합니다. 단순히 LLM에게 계산을 맡기는 것이 아니라, 백엔드가 지표를 계산하여 컨텍스트로 제공하고 LLM은 추론(Reasoning)만 수행하는 아키텍처를 완성해야 합니다.

## Allowed Files

- `build.gradle` (Spring AI 또는 WebClient 의존성)
- `src/main/java/com/tradeguide/config/**`
- `src/main/java/com/tradeguide/domain/strategy/**`
- `src/main/java/com/tradeguide/dto/strategy/**`
- `src/main/java/com/tradeguide/service/strategy/llm/**` (신규 핵심)
- `src/main/java/com/tradeguide/service/market/**`
- `src/test/java/com/tradeguide/service/strategy/llm/**`

## Non-Goals And Guardrails

- 프론트엔드 UI 수정 없음.
- **수학과 추론의 엄격한 분리:** LLM 프롬프트 내에서 이동평균이나 목표가를 계산하도록 지시하지 마십시오. Java 서비스 단에서 1/2차 지지·저항선을 미리 계산하여 LLM 프롬프트에 텍스트 변수로 주입해야 합니다.
- **환각 통제 기준 강제:** 리서치 결과(SOXL의 ATR 기반)에 따라, LLM이 제시한 `target_price`가 **현재가 대비 ±15%** 구간을 벗어나면 해당 TradePlan은 폐기(Reject) 또는 Exception 처리하는 로직이 반드시 포함되어야 합니다.
- API Key는 하드코딩 금지. `application-local.yml` 연동.

## Acceptance Checks

- [ ] LLM API 통신을 위한 WebClient 또는 전용 SDK 연동 구현
- [ ] 시스템(Java)이 지지선/저항선을 계산하여 텍스트로 치환하고, 거시 지표(US10Y 등)와 함께 Context로 조립하는 `PromptBuilder` 구현
- [ ] 리서치 보고서의 [구조화된 금융 CoT 프롬프트] 스키마 적용 및 응답 객체(JSON) 매핑
- [ ] LLM 산출 타겟 가격이 `현재가 ±15%` 밖일 경우 컷오프하는 `TradePlanValidator` 구현 및 단위 테스트 (핵심)
- [ ] 외부 통신 429/500 에러 또는 JSON 파싱 에러 시 시스템이 멈추지 않고 빈 배열/오류 상태를 반환하도록 에러 핸들링 구현

## Handoff

- Files changed: 
- Verification run: `./gradlew test` 전체 테스트 통과 여부
- API / data-model impact: 동적 TradePlan 반환을 위한 신규 응답 스펙 추가 여부
