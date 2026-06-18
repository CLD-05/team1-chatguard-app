import { useEffect, useState } from 'react'
import { getLogs } from '../../api/admin'

const STAGE_LABEL = { KEYWORD: '키워드 차단', AI: 'AI 블러' }
const VERDICT_COLOR = { BLOCK: 'text-red-400', PASS: 'text-green-400' }
const LIMIT = 50

function Spinner() {
  return (
    <div className="flex justify-center py-12">
      <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
    </div>
  )
}

export default function LogsTab({ guard }) {
  const [logs, setLogs] = useState([])
  const [stage, setStage] = useState('')
  const [cursors, setCursors] = useState([])  // 이전 페이지 커서 스택
  const [loading, setLoading] = useState(true)

  const before = cursors[cursors.length - 1]  // 현재 커서 (undefined = 첫 페이지)

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    guard(getLogs({ stage: stage || undefined, verdict: stage === 'AI' ? 'BLOCK' : undefined, before, limit: LIMIT }))
      .then((data) => setLogs(data ?? []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [guard, stage, before])

  function handleNext() {
    const lastId = logs[logs.length - 1]?.id
    if (lastId) setCursors((prev) => [...prev, lastId])
  }

  function handlePrev() {
    setCursors((prev) => prev.slice(0, -1))
  }

  function handleStageChange(e) {
    setStage(e.target.value)
    setCursors([])
  }

  return (
    <div>
      <div className="flex gap-3 mb-4">
        <select
          value={stage}
          onChange={handleStageChange}
          className="bg-gray-800 border border-gray-700 text-gray-300 text-sm rounded-lg px-3 py-2 focus:outline-none focus:border-indigo-500"
        >
          <option value="">전체</option>
          <option value="KEYWORD">키워드</option>
          <option value="AI">AI</option>
        </select>
      </div>

      {loading ? <Spinner /> : (
        <>
          <div className="bg-gray-800 border border-gray-700 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-gray-800 z-10">
                <tr className="border-b border-gray-700 text-left">
                  <th className="px-5 py-3 text-xs text-gray-500 font-normal">메시지</th>
                  <th className="px-4 py-3 text-xs text-gray-500 font-normal">유저</th>
                  <th className="px-4 py-3 text-xs text-gray-500 font-normal">단계</th>
                  <th className="px-4 py-3 text-xs text-gray-500 font-normal">판정</th>
                  <th className="px-4 py-3 text-xs text-gray-500 font-normal">점수</th>
                  <th className="px-5 py-3 text-xs text-gray-500 font-normal">시각</th>
                </tr>
              </thead>
            </table>
            <div className="overflow-y-auto max-h-[calc(100vh-260px)]">
              <table className="w-full text-sm">
                <tbody>
                  {logs.map((log, i) => (
                    <tr key={log.id} className={i !== logs.length - 1 ? 'border-b border-gray-700/60' : ''}>
                      <td className="px-5 py-3 text-gray-300 max-w-[160px] truncate">{log.content ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">{log.sender_name ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-400 text-xs">{STAGE_LABEL[log.stage] ?? log.stage}</td>
                      <td className={`px-4 py-3 text-xs font-medium ${VERDICT_COLOR[log.verdict] ?? 'text-gray-400'}`}>{log.verdict}</td>
                      <td className="px-4 py-3 text-gray-400 text-xs">{log.score != null ? log.score.toFixed(2) : '—'}</td>
                      <td className="px-5 py-3 text-gray-500 text-xs">{new Date(log.checked_at).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</td>
                    </tr>
                  ))}
                  {logs.length === 0 && (
                    <tr><td colSpan={6} className="py-10 text-center text-gray-500 text-sm">로그가 없습니다.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="flex justify-center gap-2 mt-4">
            <button disabled={cursors.length === 0} onClick={handlePrev}
              className="px-3 py-1.5 text-xs border border-gray-700 text-gray-400 hover:bg-gray-800 rounded-lg disabled:opacity-30 transition-colors">
              이전
            </button>
            <button disabled={logs.length < LIMIT} onClick={handleNext}
              className="px-3 py-1.5 text-xs border border-gray-700 text-gray-400 hover:bg-gray-800 rounded-lg disabled:opacity-30 transition-colors">
              다음
            </button>
          </div>
        </>
      )}
    </div>
  )
}
