# Moderation Dataset Replay

AI 모델 테스트용 데이터셋을 실제 ChatGuard 채팅방에 흘려보내기 위한 로컬 실행 도구이다.

## 파일

- `moderation_dataset_200.tsv`: 200개 테스트 문장 데이터셋
- `replay_dataset_to_chat.py`: 데이터셋을 실제 WebSocket 채팅으로 전송하는 스크립트

## 데이터셋 구성

| 카테고리 | 개수 | 목적 |
| --- | ---: | --- |
| 공격적으로 보이는 무해 문장 | 40 | 강한 표현이지만 실제 공격은 아닌 문장의 오탐 확인 |
| 키워드 우회형 | 40 | 초성, 띄어쓰기, 숫자, 영문 혼합 욕설 탐지 확인 |
| 욕설 없는 비하/혐오 | 40 | 직접 욕설 없이 비하/차별하는 문장 탐지 확인 |
| 경계 케이스 | 40 | 문맥에 따라 PASS/BLUR가 갈릴 수 있는 문장 확인 |
| 독한 PASS | 40 | 욕설처럼 보이는 강한 표현이지만 실제로는 칭찬/감탄인 문장의 오탐 확인 |

## 실행 전 준비

아래 서비스가 실행 중이어야 한다.

- Backend
- Frontend
- MySQL / Redis
- Moderation Worker

프론트엔드에서 테스트할 채팅방 화면을 열어둔 뒤 replay 스크립트를 실행하면 메시지가 실제 채팅처럼 올라간다.

## 모델 교체 방식

worker는 `MODERATOR_MODEL_ID`, `MODEL_VERSION`, `BLOCK_THRESHOLD` 값만 바꾸면 같은 데이터셋을 다른 모델로 다시 검증할 수 있다.

기본 UnSmile 모델:

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
.\run-model.ps1 -ModelId "smilegate-ai/kor_unsmile" -ModelVersion "unsmile-v1" -ScoringStrategy "unsmile" -BlockThreshold 0.70
```

HateScore 단독 모델:

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
.\run-model.ps1 -ModelId "sgunderscore/hatescore-korean-hate-speech" -ModelVersion "hatescore-v1" -ScoringStrategy "hatescore" -BlockThreshold 0.70
```

다른 Hugging Face text-classification 모델:

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
.\run-model.ps1 -ModelId "<다른_모델_ID>" -ModelVersion "candidate-v1" -ScoringStrategy "hatescore" -BlockThreshold 0.70
```

모델을 바꿔 테스트할 때는 worker를 종료한 뒤 위 명령으로 다시 실행하고, 아래 replay 명령은 그대로 사용한다.

DB의 `moderation_logs.model_version`에 `unsmile-v1`, `candidate-v1`처럼 기록되므로 같은 데이터셋을 모델별로 비교할 수 있다.

`SCORING_STRATEGY` 의미:

- `unsmile`: UnSmile 라벨 중 악플/욕설/혐오 계열 라벨의 최대 score를 사용한다.
- `hatescore`: HateScore의 유해 라벨 확률을 hate score로 사용한다. 기본 유해 라벨 후보는 `LABEL_1`, `hate`, `offensive`, `toxic` 등이다.

## 의존성 설치

`moderation-worker` 디렉터리에서 실행한다.

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```

## 전체 데이터셋 재생

```powershell
python evaluation\replay_dataset_to_chat.py --username user1 --password <비밀번호> --room-id 1
```

## 빠르게 재생

```powershell
python evaluation\replay_dataset_to_chat.py --username user1 --password <비밀번호> --room-id 1 --delay 0
```

## 일부만 재생

```powershell
python evaluation\replay_dataset_to_chat.py --username user1 --password <비밀번호> --room-id 1 --limit 20 --delay 0.2
```

## 특정 카테고리만 재생

```powershell
python evaluation\replay_dataset_to_chat.py --username user1 --password <비밀번호> --room-id 1 --category "경계 케이스"
```

## 동작 방식

1. `/api/login`으로 로그인해서 JWT를 받는다.
2. `/ws?token=...&room_id=...`으로 WebSocket에 연결한다.
3. TSV의 `message`를 `chat.send` 이벤트로 순서대로 전송한다.
4. 화면에는 일반 채팅처럼 메시지가 표시된다.
5. Moderation Worker가 유해하다고 판단한 메시지는 이후 `moderation.hide` 이벤트로 블러 처리된다.

주의: 이 스크립트는 실제 백엔드에 메시지를 전송하므로 테스트 메시지가 DB에 저장된다.

## DB 결과 수집

replay가 끝난 뒤 DB의 `moderation_logs`와 `messages`를 읽어서 기대 결과와 실제 결과를 비교한다.

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
.\.venv\Scripts\Activate.ps1
python evaluation\collect_moderation_results.py --model-version unsmile-v1 --run-name unsmile-v1-baseline
```

다른 모델을 같은 데이터셋으로 검증한 경우:

```powershell
python evaluation\collect_moderation_results.py --model-version candidate-v1 --run-name candidate-v1-baseline
```

replay 실행 시 `--prefix`를 사용했다면 결과 수집 때도 같은 값을 넘긴다.

```powershell
python evaluation\replay_dataset_to_chat.py --username user1 --password <비밀번호> --room-id 1 --prefix "[T1] "
python evaluation\collect_moderation_results.py --model-version unsmile-v1 --prefix "[T1] " --run-name unsmile-v1-t1
```

생성 파일:

- `evaluation/results/moderation_results_<run>.tsv`: 케이스별 상세 결과
- `evaluation/results/moderation_category_summary_<run>.tsv`: 카테고리별 정확도/오탐/미탐 요약
- `evaluation/results/moderation_report_<run>.md`: 노션에 붙여넣기 좋은 Markdown 리포트
