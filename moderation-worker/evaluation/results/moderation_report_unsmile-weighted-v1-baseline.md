# unsmile-weighted-v1 모델 테스트 결과 리포트

## 1. 테스트 조건

| 항목 | 값 |
| --- | --- |
| 데이터셋 | C:\CE\ChatGuard\team1-chatguard-app\moderation-worker\evaluation\moderation_dataset_200.tsv |
| 테스트 문장 수 | 200개 |
| 모델 | smilegate-ai/kor_unsmile |
| 모델 버전 | unsmile-weighted-v1 |
| 기준값 | BLUR_THRESHOLD=0.40 |
| 판정 기준 | score >= 0.40이면 BLUR, 미만이면 PASS |
| Clean penalty | CLEAN_PENALTY=0.10 |
| 메시지 prefix | [UNSMILE_WEIGHTED]  |
| DB 결과 기준 | messages, moderation_logs 조회 결과 |
| 조회 시작 시각 | (제한 없음) |

## 2. 전체 요약

| 전체 | 정확도 | 정답 | 오탐(FP) | 미탐(FN) | 미수집 | 실제 블러 | 실제 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 200 | 75.00% | 150 | 5 | 45 | 0 | 59 | 141 |

## 3. 결과 해석

- 전체 200개 문장 중 150개를 기대 결과와 동일하게 판정했고, 정확도는 75.00%이다.
- 미탐(FN)은 45개로 오탐(FP) 5개보다 많다. 즉, 검열해야 하는 메시지를 통과시키는 경향이 더 강하다.

## 4. 카테고리별 요약

| 카테고리 | 전체 | 정확도 | 정답 | 오탐 | 미탐 | 미수집 | 블러 | 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 공격적으로 보이는 무해 문장 | 40 | 100.00% | 40 | 0 | 0 | 0 | 0 | 40 |
| 키워드 우회형 | 40 | 67.50% | 27 | 0 | 13 | 0 | 27 | 13 |
| 욕설 없는 비하/혐오 | 40 | 45.00% | 18 | 0 | 22 | 0 | 18 | 22 |
| 경계 케이스 | 40 | 72.50% | 29 | 1 | 10 | 0 | 10 | 30 |
| 독한 PASS | 40 | 90.00% | 36 | 4 | 0 | 0 | 4 | 36 |

## 5. 용어 정리

| 용어 | 의미 |
| --- | --- |
| 정답 | expected_action과 실제 처리 결과가 일치한 케이스 |
| 오탐(FP) | 사람 기준 PASS인데 모델이 BLUR 처리한 케이스 |
| 미탐(FN) | 사람 기준 BLUR인데 모델이 PASS 처리한 케이스 |
| 미수집 | dataset 문장과 매칭되는 moderation_logs 결과를 찾지 못한 케이스 |
| 실제 블러 | DB 결과 기준 BLUR 처리된 메시지 수 |
| 실제 통과 | DB 결과 기준 PASS 처리된 메시지 수 |

> ChatGuard에서는 유해 메시지가 그대로 노출되는 미탐(FN)을 특히 중요하게 본다.

## 6. 오탐(FP) 실제 발생 항목

오탐은 실제 테스트 데이터 중 사람이 보기에는 통과되어야 하지만 모델이 블러 처리한 항목이다. 오탐이 발생한 카테고리만 표시한다.

### 경계 케이스

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 125 | 경계 케이스 | 진짜 역겹게 잘한다 | PASS | BLUR | 0.791031 | unsmile_weighted:악플/욕설=0.791:raw=0.809:weighted=0.809:clean=0.175 |

### 독한 PASS

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 169 | 독한 PASS | 돌았다 진짜 이건 | PASS | BLUR | 0.564507 | unsmile_weighted:악플/욕설=0.565:raw=0.600:weighted=0.600:clean=0.353 |
| 173 | 독한 PASS | 개고수네 진짜 | PASS | BLUR | 0.535374 | unsmile_weighted:악플/욕설=0.535:raw=0.579:weighted=0.579:clean=0.432 |
| 185 | 독한 PASS | 와 진짜 괴물 같다 | PASS | BLUR | 0.722477 | unsmile_weighted:악플/욕설=0.722:raw=0.746:weighted=0.746:clean=0.231 |
| 187 | 독한 PASS | 개든든하다 진짜 | PASS | BLUR | 0.511867 | unsmile_weighted:악플/욕설=0.512:raw=0.555:weighted=0.555:clean=0.431 |

## 7. 미탐(FN) 실제 발생 항목

미탐은 실제 테스트 데이터 중 사람이 보기에는 블러되어야 하지만 모델이 통과시킨 항목이다. 미탐이 발생한 카테고리만 표시한다.

### 키워드 우회형

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 55 | 키워드 우회형 | 너 플레이 진짜 trash네 | BLUR | PASS | 0.019825 | unsmile_weighted:악플/욕설=0.020:raw=0.104:weighted=0.104:clean=0.842 |
| 56 | 키워드 우회형 | what the fxxk are you doing | BLUR | PASS | 0.000000 | unsmile_weighted:악플/욕설=0.000:raw=0.079:weighted=0.079:clean=0.849 |
| 57 | 키워드 우회형 | you are such an a-hole | BLUR | PASS | 0.000000 | unsmile_weighted:악플/욕설=0.000:raw=0.080:weighted=0.080:clean=0.846 |
| 58 | 키워드 우회형 | 그냥 k y s 해라 | BLUR | PASS | 0.156371 | unsmile_weighted:악플/욕설=0.156:raw=0.233:weighted=0.233:clean=0.766 |
| 59 | 키워드 우회형 | 니 실력은 gomi 수준임 | BLUR | PASS | 0.379648 | unsmile_weighted:악플/욕설=0.380:raw=0.434:weighted=0.434:clean=0.540 |

### 욕설 없는 비하/혐오

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 86 | 욕설 없는 비하/혐오 | 나이 많은 사람은 그냥 빠지는 게 낫다 | BLUR | PASS | 0.008207 | unsmile_weighted:악플/욕설=0.008:raw=0.087:weighted=0.087:clean=0.784 |
| 87 | 욕설 없는 비하/혐오 | 어린 애들은 말이 안 통한다 | BLUR | PASS | 0.142028 | unsmile_weighted:악플/욕설=0.142:raw=0.206:weighted=0.206:clean=0.641 |
| 91 | 욕설 없는 비하/혐오 | 그런 사람은 채팅방에 있으면 안 된다 | BLUR | PASS | 0.009298 | unsmile_weighted:악플/욕설=0.009:raw=0.096:weighted=0.096:clean=0.865 |
| 92 | 욕설 없는 비하/혐오 | 저 팀 팬들은 전부 이상하다 | BLUR | PASS | 0.008592 | unsmile_weighted:악플/욕설=0.009:raw=0.094:weighted=0.094:clean=0.852 |
| 93 | 욕설 없는 비하/혐오 | 저 직업 가진 사람들은 다 똑같다 | BLUR | PASS | 0.365097 | unsmile_weighted:악플/욕설=0.365:raw=0.423:weighted=0.423:clean=0.575 |

### 경계 케이스

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 121 | 경계 케이스 | 너 오늘 왜 이렇게 못하냐 | BLUR | PASS | 0.105528 | unsmile_weighted:악플/욕설=0.106:raw=0.184:weighted=0.184:clean=0.789 |
| 134 | 경계 케이스 | 너 때문에 답답해서 못 보겠다 | BLUR | PASS | 0.372800 | unsmile_weighted:악플/욕설=0.373:raw=0.427:weighted=0.427:clean=0.539 |
| 136 | 경계 케이스 | 너는 운빨 아니면 아무것도 못한다 | BLUR | PASS | 0.225824 | unsmile_weighted:악플/욕설=0.226:raw=0.295:weighted=0.295:clean=0.687 |
| 138 | 경계 케이스 | 너 말투가 불쾌하다 | BLUR | PASS | 0.218161 | unsmile_weighted:악플/욕설=0.218:raw=0.286:weighted=0.286:clean=0.682 |
| 147 | 경계 케이스 | 너는 선 넘는 말만 한다 | BLUR | PASS | 0.165773 | unsmile_weighted:악플/욕설=0.166:raw=0.239:weighted=0.239:clean=0.730 |

## 9. 다음 실험 메모

- 같은 데이터셋으로 threshold를 낮추거나 높여 오탐/미탐 변화를 비교한다.
- 다른 모델을 사용할 경우 `MODEL_VERSION`을 다르게 설정하고 같은 데이터셋을 replay한다.
- 모델 비교 시 전체 정확도뿐 아니라 카테고리별 미탐(FN) 감소 여부를 함께 본다.
