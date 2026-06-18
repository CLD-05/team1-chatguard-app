import { useEffect, useRef, useState } from 'react'
import { getBadWords, addBadWord, deleteBadWord, uploadBadWords } from '../../api/admin'

export default function KeywordsTab({ guard }) {
  const [keywords, setKeywords] = useState([])
  const [input, setInput] = useState('')
  const fileRef = useRef(null)

  useEffect(() => {
    guard(getBadWords()).then(setKeywords).catch(() => {})
  }, [guard])

  async function handleAdd() {
    const kw = input.trim()
    if (!kw) return
    await guard(addBadWord(kw)).catch(() => {})
    setInput('')
    guard(getBadWords()).then(setKeywords).catch(() => {})
  }

  async function handleRemove(id) {
    await guard(deleteBadWord(id)).catch(() => {})
    guard(getBadWords()).then(setKeywords).catch(() => {})
  }

  async function handleUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return
    await guard(uploadBadWords(file))
    guard(getBadWords()).then(setKeywords).catch(() => {})
    e.target.value = ''
  }

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-xl p-5">
      <p className="text-sm font-medium text-white mb-3">전역 금칙어 목록</p>

      <div className="flex flex-wrap gap-2 mb-4 min-h-[36px]">
        {keywords.map((kw) => (
          <span key={kw.id ?? kw.word} className="flex items-center gap-1.5 bg-gray-700 text-gray-200 text-xs px-3 py-1.5 rounded-full">
            {kw.word ?? kw}
            <button onClick={() => handleRemove(kw.id)} className="text-gray-400 hover:text-red-400 transition-colors leading-none">✕</button>
          </span>
        ))}
        {keywords.length === 0 && <span className="text-gray-500 text-xs self-center">금칙어가 없습니다.</span>}
      </div>

      <div className="flex gap-2 mb-3">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
          placeholder="금칙어 추가..."
          className="flex-1 bg-gray-900 border border-gray-600 text-white text-sm rounded-lg px-3 py-2 focus:outline-none focus:border-indigo-500 placeholder-gray-600"
        />
        <button onClick={handleAdd}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm rounded-lg transition-colors">
          추가
        </button>
      </div>

      <div>
        <input type="file" accept=".txt" ref={fileRef} onChange={handleUpload} className="hidden" />
        <button onClick={() => fileRef.current?.click()}
          className="text-sm px-4 py-2 border border-gray-600 text-gray-300 hover:bg-gray-700 rounded-lg transition-colors">
          .txt 파일 업로드
        </button>
      </div>
    </div>
  )
}
