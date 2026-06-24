import { useEffect, useState, useCallback } from 'react'
import { getBadWords, addBadWord, deleteBadWord } from '../../api/admin'

const PAGE_SIZE = 10

function Spinner() {
  return (
    <div className="flex justify-center py-12">
      <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
    </div>
  )
}

export default function KeywordsTab({ guard }) {
  const [data, setData] = useState({ content: [], total_pages: 0, total_elements: 0, current_page: 0 })
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [input, setInput] = useState('')
  const [searchVal, setSearchVal] = useState('')
  const [activeKeyword, setActiveKeyword] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const [revealedIds, setRevealedIds] = useState(new Set())

  const maskWord = (word) => {
    if (!word) return ''
    if (word.length <= 1) return '*'
    return word[0] + '*'.repeat(word.length - 1)
  }

  const toggleReveal = (id) => {
    setRevealedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const totalPages = data.total_pages
  const keywords = data.content

  const fetchData = useCallback((pageIndex, keywordToUse) => {
    setLoading(true)
    guard(getBadWords({ page: pageIndex, size: PAGE_SIZE, keyword: keywordToUse }))
      .then((res) => {
        setData(res ?? { content: [], total_pages: 0, total_elements: 0, current_page: 0 })
        setRevealedIds(new Set())
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [guard])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchData(page, activeKeyword)
  }, [page, activeKeyword, refreshKey, fetchData])

  async function handleAdd() {
    const kw = input.trim()
    if (!kw) return
    try {
      await guard(addBadWord(kw))
      setInput('')
      setPage(0)
      setActiveKeyword('')
      setSearchVal('')
      setRefreshKey((prev) => prev + 1)
    } catch (err) {
      alert(err.response?.data?.error?.message || '금칙어 추가에 실패했습니다.')
    }
  }

  async function handleRemove(id) {
    if (!confirm('정말로 이 금칙어를 삭제하시겠습니까?')) return
    try {
      await guard(deleteBadWord(id))
      const isLastItemOnPage = keywords.length === 1
      const nextPageIndex = (isLastItemOnPage && page > 0) ? page - 1 : page
      if (nextPageIndex !== page) {
        setPage(nextPageIndex)
      } else {
        setRefreshKey((prev) => prev + 1)
      }
    } catch (err) {
      alert(err.response?.data?.error?.message || '금칙어 삭제에 실패했습니다.')
    }
  }

  function handleSearch() {
    setPage(0)
    setActiveKeyword(searchVal)
  }

  function handleClearSearch() {
    setSearchVal('')
    setPage(0)
    setActiveKeyword('')
  }

  const getPageNumbers = () => {
    const numbers = []
    const maxButtons = 5
    let start = Math.max(0, page - 2)
    let end = Math.min(totalPages - 1, start + maxButtons - 1)

    if (end - start < maxButtons - 1) {
      start = Math.max(0, end - maxButtons + 1)
    }

    for (let i = start; i <= end; i++) {
      numbers.push(i)
    }
    return numbers
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return '—'
    const date = new Date(dateStr)
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    })
  }

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-xl p-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-5">
        <div>
          <h3 className="text-sm font-semibold text-white">전역 금칙어 관리</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            등록된 금칙어가 포함된 메시지는 1차적으로 자동 차단됩니다. (전체 {data.total_elements}건)
          </p>
        </div>

        {/* Search & Add controls */}
        <div className="flex flex-col sm:flex-row items-center gap-3">
          {/* Add Form */}
          <div className="flex w-full sm:w-auto gap-2">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
              placeholder="새 금칙어 추가..."
              className="w-full sm:w-44 bg-gray-900 border border-gray-700 text-white text-xs rounded-lg px-3 py-2 focus:outline-none focus:border-indigo-500 placeholder-gray-600"
            />
            <button
              onClick={handleAdd}
              className="px-3.5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium rounded-lg transition-colors whitespace-nowrap"
            >
              추가
            </button>
          </div>

          {/* Search Form */}
          <div className="flex w-full sm:w-auto gap-2">
            <div className="relative w-full sm:w-48">
              <input
                value={searchVal}
                onChange={(e) => setSearchVal(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                placeholder="금칙어 검색..."
                className="w-full bg-gray-900 border border-gray-700 text-white text-xs rounded-lg pl-3 pr-8 py-2 focus:outline-none focus:border-indigo-500 placeholder-gray-600"
              />
              {activeKeyword && (
                <button
                  onClick={handleClearSearch}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300 text-xs"
                >
                  ✕
                </button>
              )}
            </div>
            <button
              onClick={handleSearch}
              className="px-3.5 py-2 bg-gray-700 hover:bg-gray-600 text-white text-xs font-medium rounded-lg transition-colors whitespace-nowrap"
            >
              검색
            </button>
          </div>
        </div>
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <>
          <div className="bg-gray-900 border border-gray-700/60 rounded-xl overflow-hidden mb-4">
            <div className="overflow-x-auto">
              <table className="w-full text-sm table-fixed text-left">
                <colgroup>
                  <col className="w-[15%]" />
                  <col className="w-[45%]" />
                  <col className="w-[25%]" />
                  <col className="w-[15%]" />
                </colgroup>
                <thead>
                  <tr className="border-b border-gray-700 text-gray-400 text-xs font-medium uppercase tracking-wider bg-gray-800/40">
                    <th className="px-5 py-3.5 text-xs text-gray-500 font-normal">번호</th>
                    <th className="px-4 py-3.5 text-xs text-gray-500 font-normal">금칙어 내용</th>
                    <th className="px-4 py-3.5 text-xs text-gray-500 font-normal">등록일시</th>
                    <th className="px-5 py-3.5 text-xs text-gray-500 font-normal text-right">관리</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/50">
                  {keywords.map((kw, idx) => (
                    <tr key={kw.id} className="hover:bg-gray-800/20 transition-colors">
                      <td className="px-5 py-3 text-xs text-gray-500 font-mono">{page * PAGE_SIZE + idx + 1}</td>
                      <td className="px-4 py-3 text-sm text-gray-200 font-medium">
                        <div className="flex items-center gap-2">
                          <span className="truncate font-mono">
                            {revealedIds.has(kw.id) ? kw.word : maskWord(kw.word)}
                          </span>
                          <button
                            onClick={() => toggleReveal(kw.id)}
                            className="text-gray-500 hover:text-gray-300 transition-colors p-1"
                            title={revealedIds.has(kw.id) ? "가리기" : "직접 보기"}
                          >
                            {revealedIds.has(kw.id) ? (
                              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-3.5 h-3.5">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                              </svg>
                            ) : (
                              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-3.5 h-3.5">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                                <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                              </svg>
                            )}
                          </button>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-400">{formatDate(kw.created_at)}</td>
                      <td className="px-5 py-3 text-right">
                        <button
                          onClick={() => handleRemove(kw.id)}
                          className="inline-flex items-center justify-center w-7 h-7 rounded-lg text-gray-400 hover:text-red-400 hover:bg-red-500/10 transition-all"
                          title="금칙어 삭제"
                        >
                          ✕
                        </button>
                      </td>
                    </tr>
                  ))}
                  {keywords.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-12 text-center text-gray-500 text-xs">
                        {activeKeyword ? '검색 결과와 일치하는 금칙어가 없습니다.' : '등록된 금칙어가 없습니다.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-1.5 mt-4">
              <button
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="w-8 h-8 flex items-center justify-center border border-gray-700 text-gray-400 hover:bg-gray-750 hover:text-white rounded-lg disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-gray-400 transition-colors"
                title="이전 페이지"
              >
                ◀
              </button>

              {getPageNumbers().map((p) => (
                <button
                  key={p}
                  onClick={() => setPage(p)}
                  className={`w-8 h-8 flex items-center justify-center text-xs font-semibold rounded-lg border transition-all ${
                    p === page
                      ? 'bg-indigo-600 border-indigo-600 text-white shadow-lg shadow-indigo-500/20'
                      : 'border-gray-700 text-gray-400 hover:bg-gray-700 hover:text-white'
                  }`}
                >
                  {p + 1}
                </button>
              ))}

              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="w-8 h-8 flex items-center justify-center border border-gray-700 text-gray-400 hover:bg-gray-750 hover:text-white rounded-lg disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-gray-400 transition-colors"
                title="다음 페이지"
              >
                ▶
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
