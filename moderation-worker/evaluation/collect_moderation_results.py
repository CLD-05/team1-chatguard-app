import argparse
import csv
import os
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse


EVALUATION_DIR = Path(__file__).resolve().parent
WORKER_DIR = EVALUATION_DIR.parent
PROJECT_ROOT = WORKER_DIR.parent
DEFAULT_DATASET = EVALUATION_DIR / "moderation_dataset_200.tsv"
DEFAULT_OUTPUT_DIR = EVALUATION_DIR / "results"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Collect ChatGuard moderation DB results and compare them with the dataset labels."
    )
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--from-results",
        type=Path,
        default=None,
        help="Read an existing moderation_results_*.tsv and regenerate summaries/reports without DB access.",
    )
    parser.add_argument("--model-version", default=None, help="Example: unsmile-weighted-v1, unsmile-weighted-t035-c010")
    parser.add_argument("--prefix", default="", help="Same prefix used when replaying the dataset.")
    parser.add_argument("--model-id", default="smilegate-ai/kor_unsmile", help="Model id used for this evaluation.")
    parser.add_argument("--blur-threshold", default="0.40", help="Final score threshold for BLUR.")
    parser.add_argument("--clean-penalty", default=None, help="Optional clean penalty value for weighted scoring.")
    parser.add_argument(
        "--since",
        default=None,
        help="Only use logs checked at or after this UTC time. Example: 2026-06-17T10:00:00",
    )
    parser.add_argument("--run-name", default=None, help="Output file name suffix. Defaults to model/timestamp.")
    return parser.parse_args()


def load_backend_env():
    env_path = PROJECT_ROOT / "backend" / ".env"
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        os.environ.setdefault(name.strip(), value.strip())


def parse_db_url(db_url):
    normalized = db_url
    if normalized.startswith("jdbc:"):
        normalized = normalized[len("jdbc:"):]
    parsed = urlparse(normalized)
    query = parse_qs(parsed.query)
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 3306,
        "database": parsed.path.lstrip("/") or "chatguard_dev",
        "connect_timeout": int(first(query, "connectTimeout", "5")),
        "read_timeout": int(first(query, "socketTimeout", "5")),
        "write_timeout": int(first(query, "socketTimeout", "5")),
    }


def first(query, key, default):
    values = query.get(key)
    return values[0] if values else default


def connect_db():
    import pymysql

    load_backend_env()
    db_url = os.getenv(
        "DB_URL",
        "jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
    )
    return pymysql.connect(
        **parse_db_url(db_url),
        user=os.getenv("DB_USER", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def read_dataset(path):
    with path.open("r", encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file, delimiter="\t"))


def parse_since(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=None)


def find_latest_log(cursor, content, model_version, since):
    query = """
        SELECT
            ml.id AS log_id,
            ml.message_id,
            ml.stage,
            ml.verdict,
            ml.score,
            ml.model_version,
            ml.reason,
            ml.checked_at,
            m.status AS message_status,
            COALESCE(m.content, ml.content) AS content
        FROM moderation_logs ml
        LEFT JOIN messages m ON m.id = ml.message_id
        WHERE (m.content = %s OR ml.content = %s)
    """
    params = [content, content]

    if model_version:
        query += " AND (ml.model_version = %s OR ml.stage = 'KEYWORD')"
        params.append(model_version)
    if since:
        query += " AND ml.checked_at >= %s"
        params.append(since)

    query += " ORDER BY ml.checked_at DESC, ml.id DESC LIMIT 1"
    cursor.execute(query, params)
    return cursor.fetchone()


def actual_action(log):
    if not log:
        return "MISSING"
    if log["stage"] == "KEYWORD" and log["verdict"] == "BLOCK":
        return "BLUR"
    if log["verdict"] == "BLOCK":
        return "BLUR"
    if log.get("message_status") == "BLURRED":
        return "BLUR"
    return "PASS"


def error_type(expected, actual):
    if actual == "MISSING":
        return "MISSING"
    if expected == actual:
        return ""
    if expected == "PASS" and actual == "BLUR":
        return "FP"
    if expected == "BLUR" and actual == "PASS":
        return "FN"
    return "MISMATCH"


def write_tsv(path, rows, fieldnames):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def summarize(rows):
    total = len(rows)
    counts = Counter(row["error_type"] or "CORRECT" for row in rows)
    actual = Counter(row["actual_action"] for row in rows)
    correct = counts["CORRECT"]
    return {
        "total": total,
        "correct": correct,
        "accuracy": correct / total if total else 0.0,
        "fp": counts["FP"],
        "fn": counts["FN"],
        "missing": counts["MISSING"],
        "actual_blur": actual["BLUR"],
        "actual_pass": actual["PASS"],
    }


def summarize_by_category(rows):
    grouped = defaultdict(list)
    for row in rows:
        grouped[row["category"]].append(row)

    summaries = []
    for category, items in grouped.items():
        summary = summarize(items)
        summaries.append({
            "category": category,
            "total": summary["total"],
            "accuracy": f"{summary['accuracy']:.4f}",
            "correct": summary["correct"],
            "fp": summary["fp"],
            "fn": summary["fn"],
            "missing": summary["missing"],
            "actual_blur": summary["actual_blur"],
            "actual_pass": summary["actual_pass"],
        })
    return summaries


def markdown_table(headers, rows):
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(str(row.get(header, "")) for header in headers) + " |")
    return "\n".join(lines)


def normalize_category_summary(category_rows):
    rows = []
    for row in category_rows:
        rows.append({
            "카테고리": row["category"],
            "전체": row["total"],
            "정확도": f"{float(row['accuracy']):.2%}",
            "정답": row["correct"],
            "오탐": row["fp"],
            "미탐": row["fn"],
            "미수집": row["missing"],
            "블러": row["actual_blur"],
            "통과": row["actual_pass"],
        })
    return rows


def normalize_example_rows(rows):
    return [
        {
            "번호": row["id"],
            "카테고리": row["category"],
            "테스트 문장": row["message"],
            "기대": row["expected_action"],
            "실제": row["actual_action"],
            "점수": row["score"],
            "판단 근거": row["reason"],
        }
        for row in rows
    ]


def grouped_error_examples(detail_rows, error_code):
    grouped = defaultdict(list)
    for row in detail_rows:
        if row["error_type"] == error_code:
            grouped[row["category"]].append(row)
    return grouped


def build_interpretation(summary):
    lines = [
        f"전체 {summary['total']}개 문장 중 {summary['correct']}개를 기대 결과와 동일하게 판정했고, "
        f"정확도는 {summary['accuracy']:.2%}이다."
    ]
    if summary["fn"] > summary["fp"]:
        lines.append(
            f"미탐(FN)은 {summary['fn']}개로 오탐(FP) {summary['fp']}개보다 많다. "
            "즉, 검열해야 하는 메시지를 통과시키는 경향이 더 강하다."
        )
    elif summary["fp"] > summary["fn"]:
        lines.append(
            f"오탐(FP)은 {summary['fp']}개로 미탐(FN) {summary['fn']}개보다 많다. "
            "즉, 정상 메시지를 과하게 블러 처리하는 경향이 더 강하다."
        )
    else:
        lines.append(
            f"오탐(FP)과 미탐(FN)은 각각 {summary['fp']}개로 동일하다. "
            "threshold 조정 시 두 지표를 함께 비교해야 한다."
        )
    if summary["missing"]:
        lines.append(
            f"미수집 케이스가 {summary['missing']}개 있다. replay가 끝나기 전에 수집했거나 "
            "prefix/model_version/since 조건이 replay 조건과 맞지 않을 수 있다."
        )
    return lines


def write_report(path, summary, category_rows, detail_rows, args):
    missing_rows = [row for row in detail_rows if row["error_type"] == "MISSING"][:10]
    fp_groups = grouped_error_examples(detail_rows, "FP")
    fn_groups = grouped_error_examples(detail_rows, "FN")
    category_summary = normalize_category_summary(category_rows)
    model_name = args.model_version or "latest(any)"

    lines = [
        f"# {model_name} 모델 테스트 결과 리포트",
        "",
        "## 1. 테스트 조건",
        "",
        markdown_table(
            ["항목", "값"],
            [
                {"항목": "데이터셋", "값": str(args.dataset)},
                {"항목": "테스트 문장 수", "값": f"{len(detail_rows)}개"},
                {"항목": "모델", "값": args.model_id},
                {"항목": "모델 버전", "값": model_name},
                {"항목": "기준값", "값": f"BLUR_THRESHOLD={args.blur_threshold}"},
                {"항목": "판정 기준", "값": f"score >= {args.blur_threshold}이면 BLUR, 미만이면 PASS"},
                *(
                    [{"항목": "Clean penalty", "값": f"CLEAN_PENALTY={args.clean_penalty}"}]
                    if args.clean_penalty is not None else []
                ),
                {"항목": "메시지 prefix", "값": args.prefix or "(없음)"},
                {"항목": "DB 결과 기준", "값": "messages, moderation_logs 조회 결과"},
                {"항목": "조회 시작 시각", "값": args.since or "(제한 없음)"},
            ],
        ),
        "",
        "## 2. 전체 요약",
        "",
        markdown_table(
            ["전체", "정확도", "정답", "오탐(FP)", "미탐(FN)", "미수집", "실제 블러", "실제 통과"],
            [{
                "전체": summary["total"],
                "정확도": f"{summary['accuracy']:.2%}",
                "정답": summary["correct"],
                "오탐(FP)": summary["fp"],
                "미탐(FN)": summary["fn"],
                "미수집": summary["missing"],
                "실제 블러": summary["actual_blur"],
                "실제 통과": summary["actual_pass"],
            }],
        ),
        "",
        "## 3. 결과 해석",
        "",
        *[f"- {line}" for line in build_interpretation(summary)],
        "",
        "## 4. 카테고리별 요약",
        "",
        markdown_table(
            ["카테고리", "전체", "정확도", "정답", "오탐", "미탐", "미수집", "블러", "통과"],
            category_summary,
        ),
        "",
        "## 5. 용어 정리",
        "",
        markdown_table(
            ["용어", "의미"],
            [
                {"용어": "정답", "의미": "expected_action과 실제 처리 결과가 일치한 케이스"},
                {"용어": "오탐(FP)", "의미": "사람 기준 PASS인데 모델이 BLUR 처리한 케이스"},
                {"용어": "미탐(FN)", "의미": "사람 기준 BLUR인데 모델이 PASS 처리한 케이스"},
                {"용어": "미수집", "의미": "dataset 문장과 매칭되는 moderation_logs 결과를 찾지 못한 케이스"},
                {"용어": "실제 블러", "의미": "DB 결과 기준 BLUR 처리된 메시지 수"},
                {"용어": "실제 통과", "의미": "DB 결과 기준 PASS 처리된 메시지 수"},
            ],
        ),
        "",
        "> ChatGuard에서는 유해 메시지가 그대로 노출되는 미탐(FN)을 특히 중요하게 본다.",
    ]

    if fp_groups:
        lines.extend([
            "",
            "## 6. 오탐(FP) 실제 발생 항목",
            "",
            "오탐은 실제 테스트 데이터 중 사람이 보기에는 통과되어야 하지만 모델이 블러 처리한 항목이다. "
            "오탐이 발생한 카테고리만 표시한다.",
        ])
        for category, rows in fp_groups.items():
            lines.extend([
                "",
                f"### {category}",
                "",
                markdown_table(
                    ["번호", "카테고리", "테스트 문장", "기대", "실제", "점수", "판단 근거"],
                    normalize_example_rows(rows[:5]),
                ),
            ])

    if fn_groups:
        lines.extend([
            "",
            "## 7. 미탐(FN) 실제 발생 항목",
            "",
            "미탐은 실제 테스트 데이터 중 사람이 보기에는 블러되어야 하지만 모델이 통과시킨 항목이다. "
            "미탐이 발생한 카테고리만 표시한다.",
        ])
        for category, rows in fn_groups.items():
            lines.extend([
                "",
                f"### {category}",
                "",
                markdown_table(
                    ["번호", "카테고리", "테스트 문장", "기대", "실제", "점수", "판단 근거"],
                    normalize_example_rows(rows[:5]),
                ),
            ])

    if missing_rows:
        lines.extend([
            "",
            "## 8. 미수집(MISSING) 예시",
            "",
            markdown_table(
                ["번호", "카테고리", "테스트 문장", "기대", "실제", "점수", "판단 근거"],
                normalize_example_rows(missing_rows),
            ),
        ])

    lines.extend([
        "",
        "## 9. 다음 실험 메모",
        "",
        "- 같은 데이터셋으로 threshold를 낮추거나 높여 오탐/미탐 변화를 비교한다.",
        "- 다른 모델을 사용할 경우 `MODEL_VERSION`을 다르게 설정하고 같은 데이터셋을 replay한다.",
        "- 모델 비교 시 전체 정확도뿐 아니라 카테고리별 미탐(FN) 감소 여부를 함께 본다.",
    ])

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_category_summary_markdown(path, summary, category_rows, args):
    model_name = args.model_version or "latest(any)"
    category_summary = normalize_category_summary(category_rows)
    lines = [
        f"# {model_name} 카테고리별 요약",
        "",
        "## 전체 요약",
        "",
        markdown_table(
            ["전체", "정확도", "정답", "오탐(FP)", "미탐(FN)", "미수집", "실제 블러", "실제 통과"],
            [{
                "전체": summary["total"],
                "정확도": f"{summary['accuracy']:.2%}",
                "정답": summary["correct"],
                "오탐(FP)": summary["fp"],
                "미탐(FN)": summary["fn"],
                "미수집": summary["missing"],
                "실제 블러": summary["actual_blur"],
                "실제 통과": summary["actual_pass"],
            }],
        ),
        "",
        "## 카테고리별 요약",
        "",
        markdown_table(
            ["카테고리", "전체", "정확도", "정답", "오탐", "미탐", "미수집", "블러", "통과"],
            category_summary,
        ),
        "",
        "## 해석 포인트",
        "",
        "- 정확도는 해당 카테고리에서 기대 결과와 실제 결과가 일치한 비율이다.",
        "- 오탐(FP)은 정상 메시지를 과하게 블러 처리한 경우이다.",
        "- 미탐(FN)은 블러되어야 할 메시지를 통과시킨 경우이다.",
        "- ChatGuard 기준에서는 미탐(FN)이 실제 유해 메시지 노출로 이어질 수 있어 특히 중요하게 본다.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def output_paths(output_dir, run_name):
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    suffix = run_name or timestamp
    return {
        "details": output_dir / f"moderation_results_{suffix}.tsv",
        "category": output_dir / f"moderation_category_summary_{suffix}.tsv",
        "category_markdown": output_dir / f"moderation_category_summary_{suffix}.md",
        "report": output_dir / f"moderation_report_{suffix}.md",
    }


def main():
    args = parse_args()
    output_dir = args.output_dir

    if args.from_results:
        result_rows = read_dataset(args.from_results)
        dataset_rows = result_rows
    else:
        since = parse_since(args.since)
        dataset_rows = read_dataset(args.dataset)
        result_rows = []
        with connect_db() as connection:
            with connection.cursor() as cursor:
                for row in dataset_rows:
                    expected = row["expected_action"]
                    content = f"{args.prefix}{row['message']}"
                    log = find_latest_log(cursor, content, args.model_version, since)
                    actual = actual_action(log)
                    err = error_type(expected, actual)

                    result_rows.append({
                        **row,
                        "sent_message": content,
                        "actual_action": actual,
                        "is_correct": str(err == "").upper(),
                        "error_type": err,
                        "stage": log["stage"] if log else "",
                        "verdict": log["verdict"] if log else "",
                        "score": f"{float(log['score']):.6f}" if log and log["score"] is not None else "",
                        "model_version": log["model_version"] if log and log["model_version"] else "",
                        "reason": log["reason"] if log and log["reason"] else "",
                        "message_status": log["message_status"] if log and log["message_status"] else "",
                        "message_id": log["message_id"] if log else "",
                        "checked_at": log["checked_at"].isoformat(sep=" ") if log and log["checked_at"] else "",
                    })

    summary = summarize(result_rows)
    category_rows = summarize_by_category(result_rows)
    run_base = args.run_name
    if not run_base:
        model = args.model_version or "latest"
        run_base = f"{model}_{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
    paths = output_paths(output_dir, run_base)

    detail_fields = list(result_rows[0].keys())
    write_tsv(paths["details"], result_rows, detail_fields)
    write_tsv(paths["category"], category_rows, [
        "category",
        "total",
        "accuracy",
        "correct",
        "fp",
        "fn",
        "missing",
        "actual_blur",
        "actual_pass",
    ])
    write_category_summary_markdown(paths["category_markdown"], summary, category_rows, args)
    write_report(paths["report"], summary, category_rows, result_rows, args)

    print(f"total={summary['total']}")
    print(f"accuracy={summary['accuracy']:.2%}")
    print(f"correct={summary['correct']} fp={summary['fp']} fn={summary['fn']} missing={summary['missing']}")
    print(f"details={paths['details']}")
    print(f"category_summary={paths['category']}")
    print(f"category_summary_markdown={paths['category_markdown']}")
    print(f"report={paths['report']}")


if __name__ == "__main__":
    main()
