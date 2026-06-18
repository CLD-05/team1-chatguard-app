# hatescore-unsmile-v1 모델 테스트 결과 리포트

## 1. 테스트 조건

| 항목 | 값 |
| --- | --- |
| 데이터셋 | C:\CE\ChatGuard\team1-chatguard-app\moderation-worker\evaluation\moderation_dataset_200.tsv |
| 모델 버전 | hatescore-unsmile-v1 |
| 메시지 prefix | [HATESCORE_UNSMILE]  |
| 조회 시작 시각 | (제한 없음) |

## 2. 전체 요약

| 전체 | 정확도 | 정답 | 오탐(FP) | 미탐(FN) | 미수집 | 실제 블러 | 실제 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 200 | 58.00% | 116 | 0 | 84 | 0 | 15 | 185 |

## 3. 결과 해석

- 전체 200개 문장 중 116개를 기대 결과와 동일하게 판정했고, 정확도는 58.00%이다.
- 미탐(FN)은 84개로 오탐(FP) 0개보다 많다. 즉, 검열해야 하는 메시지를 통과시키는 경향이 더 강하다.

## 4. 카테고리별 요약

| 카테고리 | 전체 | 정확도 | 정답 | 오탐 | 미탐 | 미수집 | 블러 | 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 공격적으로 보이는 무해 문장 | 40 | 100.00% | 40 | 0 | 0 | 0 | 0 | 40 |
| 키워드 우회형 | 40 | 37.50% | 15 | 0 | 25 | 0 | 15 | 25 |
| 욕설 없는 비하/혐오 | 40 | 0.00% | 0 | 0 | 40 | 0 | 0 | 40 |
| 경계 케이스 | 40 | 52.50% | 21 | 0 | 19 | 0 | 0 | 40 |
| 독한 PASS | 40 | 100.00% | 40 | 0 | 0 | 0 | 0 | 40 |

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

## 7. 미탐(FN) 유형별 예시

미탐은 사람이 보기에는 블러되어야 하지만 모델이 통과시킨 문장이다. 한 카테고리에만 몰려 보이지 않도록 카테고리별 대표 예시를 분리했다.

### 키워드 우회형

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 43 | 키워드 우회형 | 진짜 ㅈㄴ 못하네 | BLUR | PASS | 0.602218 | hatescore-unsmile-v1:cascade:hatescore:단순 악플=0.545:ambiguous->unsmile:악플/욕설=0.602 |
| 48 | 키워드 우회형 | 너는 멍 청한 소리만 하네 | BLUR | PASS | 0.023135 | hatescore-unsmile-v1:cascade:hatescore:None=0.023:pass_by_hatescore_low |
| 49 | 키워드 우회형 | 개ㅅㅐ끼처럼 굴지 마 | BLUR | PASS | 0.019824 | hatescore-unsmile-v1:cascade:hatescore:None=0.020:pass_by_hatescore_low |
| 52 | 키워드 우회형 | 너 때문에 팀이 ㅈ망함 | BLUR | PASS | 0.222945 | hatescore-unsmile-v1:cascade:hatescore:None=0.223:pass_by_hatescore_low |
| 53 | 키워드 우회형 | 그냥 ㄲㅈ라 | BLUR | PASS | 0.182326 | hatescore-unsmile-v1:cascade:hatescore:None=0.182:pass_by_hatescore_low |

### 욕설 없는 비하/혐오

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 81 | 욕설 없는 비하/혐오 | 저 사람들은 원래 다 저래 | BLUR | PASS | 0.009593 | hatescore-unsmile-v1:cascade:hatescore:None=0.010:pass_by_hatescore_low |
| 82 | 욕설 없는 비하/혐오 | 그 지역 출신은 믿기 어렵다 | BLUR | PASS | 0.004960 | hatescore-unsmile-v1:cascade:hatescore:None=0.005:pass_by_hatescore_low |
| 83 | 욕설 없는 비하/혐오 | 여자라서 게임 이해를 못 하는 듯 | BLUR | PASS | 0.018102 | hatescore-unsmile-v1:cascade:hatescore:None=0.018:pass_by_hatescore_low |
| 84 | 욕설 없는 비하/혐오 | 남자들은 원래 공감 능력이 없다 | BLUR | PASS | 0.019732 | hatescore-unsmile-v1:cascade:hatescore:여성/가족=0.020:pass_by_hatescore_low |
| 85 | 욕설 없는 비하/혐오 | 외국인은 우리 팀에 안 맞는다 | BLUR | PASS | 0.006781 | hatescore-unsmile-v1:cascade:hatescore:None=0.007:pass_by_hatescore_low |

### 경계 케이스

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 121 | 경계 케이스 | 너 오늘 왜 이렇게 못하냐 | BLUR | PASS | 0.006884 | hatescore-unsmile-v1:cascade:hatescore:None=0.007:pass_by_hatescore_low |
| 123 | 경계 케이스 | 그 판단은 민폐였어 | BLUR | PASS | 0.012552 | hatescore-unsmile-v1:cascade:hatescore:None=0.013:pass_by_hatescore_low |
| 126 | 경계 케이스 | 상대 운영이 역겹다 | BLUR | PASS | 0.014940 | hatescore-unsmile-v1:cascade:hatescore:None=0.015:pass_by_hatescore_low |
| 128 | 경계 케이스 | 너 진짜 미쳤냐 | BLUR | PASS | 0.009629 | hatescore-unsmile-v1:cascade:hatescore:None=0.010:pass_by_hatescore_low |
| 129 | 경계 케이스 | 이건 사람 할 짓이 아니다 | BLUR | PASS | 0.015108 | hatescore-unsmile-v1:cascade:hatescore:None=0.015:pass_by_hatescore_low |

## 9. 다음 실험 메모

- 같은 데이터셋으로 threshold를 낮추거나 높여 오탐/미탐 변화를 비교한다.
- 다른 모델을 사용할 경우 `MODEL_VERSION`을 다르게 설정하고 같은 데이터셋을 replay한다.
- 모델 비교 시 전체 정확도뿐 아니라 카테고리별 미탐(FN) 감소 여부를 함께 본다.
