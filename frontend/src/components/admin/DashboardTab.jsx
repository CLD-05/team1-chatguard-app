import { useEffect, useState } from 'react'
import { getStats, getLogs } from '../../api/admin'

const STAGE_BADGE = {
  KEYWORD: <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-900/30 text-red-300 border border-red-700/40">키워드 차단</span>,
  AI:      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-900/30 text-amber-300 border border-amber-700/40">AI 블러</span>,
}

export default function DashboardTab({ guard }) {
  const [stats, setStats] = useState(null)
  const [recentLogs, setRecentLogs] = useState([])

  useEffect(() => {
    guard(getStats()).then(setStats).catch(() => {})
    guard(getLogs({ verdict: 'BLOCK', limit: 5 }))
      .then((res) => setRecentLogs(res ?? []))
      .catch(() => {})
  }, [guard])

  const STAT_CARDS = [
    { label: '총 메시지',   key: 'total_messages',  compute: null, sub: null, color: 'text-emerald-400' },
    { label: '키워드 차단', key: 'keyword_blocked',  compute: null, sub: (s) => ((s.keyword_blocked / Math.max(s.total_messages, 1)) * 100).toFixed(1) + '%', color: 'text-red-400' },
    { label: 'AI 블러',     key: 'ai_blurred',       compute: null, sub: (s) => ((s.ai_blurred / Math.max(s.total_messages, 1)) * 100).toFixed(1) + '%', color: 'text-amber-400' },
    { label: '전체 차단율', key: null, compute: (s) => (((s.keyword_blocked + s.ai_blurred) / Math.max(s.total_messages, 1)) * 100).toFixed(1) + '%', sub: null, color: 'text-orange-400' },
  ]

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {STAT_CARDS.map(({ label, key, compute, sub, color }) => (
          <div key={label} className="bg-gray-800/60 border border-gray-700/50 rounded-xl p-5">
            <p className="text-xs text-gray-500 mb-2">{label}</p>
            <p className="text-3xl font-semibold text-white tracking-tight">
              {stats
                ? compute ? compute(stats) : stats[key]?.toLocaleString()
                : <span className="text-gray-600">—</span>}
            </p>
            {sub && stats && (
              <p className={`text-xs mt-1.5 ${color}`}>{sub(stats)}</p>
            )}
          </div>
        ))}
      </div>

      <div>
        <p className="text-sm font-medium text-gray-300 mb-3">최근 검열 내역</p>
        <div className="bg-gray-800/60 border border-gray-700/50 rounded-xl overflow-hidden">
          <table className="w-full table-fixed">
            <colgroup>
              <col className="w-[55%]" />
              <col className="w-[20%]" />
              <col className="w-[25%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-gray-700/60">
                <th className="px-5 py-3 text-left text-xs text-gray-600 font-medium">메시지</th>
                <th className="px-4 py-3 text-left text-xs text-gray-600 font-medium">처리</th>
                <th className="px-5 py-3 text-right text-xs text-gray-600 font-medium">시각</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/40">
              {recentLogs.map((log) => (
                <tr key={log.id} className="hover:bg-gray-700/20 transition-colors">
                  <td className="px-5 py-3.5 text-sm text-gray-300 max-w-[180px]">
                    <span className="block truncate">{log.content ?? '—'}</span>
                  </td>
                  <td className="px-4 py-3.5">{STAGE_BADGE[log.stage] ?? log.stage}</td>
                  <td className="px-5 py-3.5 text-xs text-gray-500 text-right whitespace-nowrap">
                    {new Date(log.checked_at).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                  </td>
                </tr>
              ))}
              {recentLogs.length === 0 && (
                <tr><td colSpan={3} className="py-10 text-center text-gray-600 text-sm">검열 내역이 없습니다.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
