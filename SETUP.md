# Orca에 리서치 전용 에이전트 적용하기

## 왜 이렇게 구성했는가

- Orca는 에이전트마다 **별도 git worktree(= 별도 브랜치의 체크아웃)** 를 만들어 돌립니다.
  그래서 "리서치 전용 브랜치"를 하나 파서 그 워크트리에만 이 구성을 넣으면,
  메인 개발 브랜치(코드/ChatGPT 작업물)는 전혀 건드리지 않습니다.
- CLAUDE.md 한 장으로 "코드 수정 금지"라고 적어두는 건 **가이드라인일 뿐, 강제는 아닙니다.**
  그래서 `.claude/settings.json` + `.claude/hooks/block-non-research-writes.py` 조합으로
  실제로 `research/` 바깥에 쓰기 시도가 들어오면 도구 호출 자체를 차단하도록 했습니다.
  (Claude Code의 `permissions.deny` 규칙만으로는 우회되는 사례가 보고된 적이 있어,
  실제 강제는 PreToolUse 훅이 담당하고 settings.json은 1차 안내 역할입니다.)

## 단계

1. **로컬 저장소에서 리서치 전용 브랜치 생성**
   ```bash
   cd /Users/beansdaaddy/Desktop/_Study/Java/trade-guide
   git checkout -b research/strategy-survey
   ```

2. **이 압축 파일의 내용을 그 브랜치 루트에 풀기**
   (`CLAUDE.md`, `research/`, `.claude/` 가 프로젝트 루트와 같은 위치에 오도록)

3. 커밋
   ```bash
   git add CLAUDE.md research .claude
   git commit -m "chore: research-only agent workflow"
   ```

4. **Orca에서 새 워크트리 생성**
   - Orca 앱에서 `trade-guide` 저장소를 열고, 방금 만든 `research/strategy-survey` 브랜치로
     새 워크트리를 만듭니다.
   - 에이전트로는 **Claude Code**를 추천합니다 — 이유:
     - 프로젝트 루트의 `CLAUDE.md`를 세션 시작 시 자동으로 읽어 위 규칙을 그대로 따릅니다.
     - `.claude/settings.json`의 훅/권한 설정을 그대로 인식합니다.
     - 리서치 특성상 웹 검색이 많이 필요한데, 웹 검색 도구 연동이 잘 되어 있습니다.
   - 다른 CLI 에이전트(Codex 등)를 쓰고 싶으시면, 그 에이전트가 이 훅/설정 방식을
     지원하는지 먼저 확인하시는 게 좋습니다 (에이전트마다 설정 파일 형식이 다릅니다).

5. **첫 프롬프트로 이렇게 시작하세요** (에이전트 터미널에 입력)
   ```
   CLAUDE.md와 research/TASKS.md를 읽고, 가장 우선순위 높은 미완료 항목 1개만
   조사해서 research/reports/에 리포트를 쓰고 research/data/strategies.json에
   항목을 추가해줘. 끝나면 결과를 요약해서 보고하고 다음 항목 진행 여부를 물어봐줘.
   ```

6. 리포트/데이터가 쌓이면 **사용자가 직접 diff를 리뷰하고 커밋**하세요.
   (이 구성에서는 git commit/push도 훅과 별개로 deny 규칙에 걸려 있어 에이전트가
   직접 커밋하지 않습니다 — 실수로 원치 않는 변경이 커밋되는 걸 막기 위함입니다.)

## 나중에 코드 수정 에이전트로 넘어갈 때

지금은 "조사 전용"만 요청하셨으니 이 구성은 코드를 전혀 건드리지 않습니다.
이후 리서치 결과(`research/data/strategies.json`)를 실제 앱 로직에 반영하는
개발 에이전트가 필요해지면, 그건 메인 개발 브랜치의 별도 워크트리 + 별도
`CLAUDE.md`(코드 수정 허용)로 분리해서 진행하는 걸 권장드립니다. 필요하시면
그 워크플로우도 이어서 설계해드릴게요.
