# unsmile-hatescore-v1 모델 테스트 결과 리포트

## 1. 테스트 조건

| 항목 | 값 |
| --- | --- |
| 데이터셋 | C:\CE\ChatGuard\team1-chatguard-app\moderation-worker\evaluation\moderation_dataset_200.tsv |
| 모델 버전 | unsmile-hatescore-v1 |
| 메시지 prefix | [UNSMILE_HATESCORE]  |
| 조회 시작 시각 | (제한 없음) |

## 2. 전체 요약

| 전체 | 정확도 | 정답 | 오탐(FP) | 미탐(FN) | 미수집 | 실제 블러 | 실제 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 200 | 64.00% | 128 | 2 | 70 | 0 | 31 | 169 |

## 3. 결과 해석

- 전체 200개 문장 중 128개를 기대 결과와 동일하게 판정했고, 정확도는 64.00%이다.
- 미탐(FN)은 70개로 오탐(FP) 2개보다 많다. 즉, 검열해야 하는 메시지를 통과시키는 경향이 더 강하다.

## 4. 카테고리별 요약

| 카테고리 | 전체 | 정확도 | 정답 | 오탐 | 미탐 | 미수집 | 블러 | 통과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 공격적으로 보이는 무해 문장 | 40 | 100.00% | 40 | 0 | 0 | 0 | 0 | 40 |
| 키워드 우회형 | 40 | 55.00% | 22 | 0 | 18 | 0 | 22 | 18 |
| 욕설 없는 비하/혐오 | 40 | 15.00% | 6 | 0 | 34 | 0 | 6 | 34 |
| 경계 케이스 | 40 | 52.50% | 21 | 1 | 18 | 0 | 2 | 38 |
| 독한 PASS | 40 | 97.50% | 39 | 1 | 0 | 0 | 1 | 39 |

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

## 6. 오탐(FP) 예시

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 125 | 경계 케이스 | 진짜 역겹게 잘한다 | PASS | BLUR | 0.792296 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.792:block_by_hatescore_high |
| 185 | 독한 PASS | 와 진짜 괴물 같다 | PASS | BLUR | 0.715191 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.715:block_by_hatescore_high |

## 7. 미탐(FN) 유형별 예시

미탐은 사람이 보기에는 블러되어야 하지만 모델이 통과시킨 문장이다. 한 카테고리에만 몰려 보이지 않도록 카테고리별 대표 예시를 분리했다.

### 키워드 우회형

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 43 | 키워드 우회형 | 진짜 ㅈㄴ 못하네 | BLUR | PASS | 0.509814 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.617:ambiguous->hatescore:None=0.510 |
| 48 | 키워드 우회형 | 너는 멍 청한 소리만 하네 | BLUR | PASS | 0.021951 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.606:ambiguous->hatescore:None=0.022 |
| 49 | 키워드 우회형 | 개ㅅㅐ끼처럼 굴지 마 | BLUR | PASS | 0.019055 | unsmile-hatescore-v1:cascade:unsmile:clean=0.417:ambiguous->hatescore:None=0.019 |
| 55 | 키워드 우회형 | 너 플레이 진짜 trash네 | BLUR | PASS | 0.112244 | unsmile-hatescore-v1:cascade:unsmile:clean=0.112:pass_by_hatescore_low |
| 56 | 키워드 우회형 | what the fxxk are you doing | BLUR | PASS | 0.077543 | unsmile-hatescore-v1:cascade:unsmile:clean=0.078:pass_by_hatescore_low |

### 욕설 없는 비하/혐오

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 81 | 욕설 없는 비하/혐오 | 저 사람들은 원래 다 저래 | BLUR | PASS | 0.009631 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.566:ambiguous->hatescore:None=0.010 |
| 82 | 욕설 없는 비하/혐오 | 그 지역 출신은 믿기 어렵다 | BLUR | PASS | 0.004926 | unsmile-hatescore-v1:cascade:unsmile:지역=0.546:ambiguous->hatescore:None=0.005 |
| 83 | 욕설 없는 비하/혐오 | 여자라서 게임 이해를 못 하는 듯 | BLUR | PASS | 0.016561 | unsmile-hatescore-v1:cascade:unsmile:여성/가족=0.486:ambiguous->hatescore:None=0.017 |
| 84 | 욕설 없는 비하/혐오 | 남자들은 원래 공감 능력이 없다 | BLUR | PASS | 0.019783 | unsmile-hatescore-v1:cascade:unsmile:남성=0.625:ambiguous->hatescore:여성/가족=0.020 |
| 85 | 욕설 없는 비하/혐오 | 외국인은 우리 팀에 안 맞는다 | BLUR | PASS | 0.006920 | unsmile-hatescore-v1:cascade:unsmile:인종/국적=0.419:ambiguous->hatescore:None=0.007 |

### 경계 케이스

| 번호 | 카테고리 | 테스트 문장 | 기대 | 실제 | 점수 | 판단 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 121 | 경계 케이스 | 너 오늘 왜 이렇게 못하냐 | BLUR | PASS | 0.169791 | unsmile-hatescore-v1:cascade:unsmile:clean=0.170:pass_by_hatescore_low |
| 123 | 경계 케이스 | 그 판단은 민폐였어 | BLUR | PASS | 0.011927 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.697:ambiguous->hatescore:None=0.012 |
| 126 | 경계 케이스 | 상대 운영이 역겹다 | BLUR | PASS | 0.014761 | unsmile-hatescore-v1:cascade:unsmile:clean=0.474:ambiguous->hatescore:None=0.015 |
| 128 | 경계 케이스 | 너 진짜 미쳤냐 | BLUR | PASS | 0.009454 | unsmile-hatescore-v1:cascade:unsmile:clean=0.438:ambiguous->hatescore:None=0.009 |
| 129 | 경계 케이스 | 이건 사람 할 짓이 아니다 | BLUR | PASS | 0.014325 | unsmile-hatescore-v1:cascade:unsmile:악플/욕설=0.632:ambiguous->hatescore:None=0.014 |

## 9. 다음 실험 메모

- 같은 데이터셋으로 threshold를 낮추거나 높여 오탐/미탐 변화를 비교한다.
- 다른 모델을 사용할 경우 `MODEL_VERSION`을 다르게 설정하고 같은 데이터셋을 replay한다.
- 모델 비교 시 전체 정확도뿐 아니라 카테고리별 미탐(FN) 감소 여부를 함께 본다.
