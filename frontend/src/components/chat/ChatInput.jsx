import { useState } from 'react'

const MAX_LENGTH = 500

export default function ChatInput({ onSend, disabled, frozen, errorMessage }) {
  const [value, setValue] = useState('')

  const isBlocked = disabled || frozen

  function submit() {
    const trimmed = value.trim()
    if (!trimmed || isBlocked) return
    onSend(trimmed)
    setValue('')
  }

  function handleKeyDown(e) {
    // IME(한글 등) 조합 중 Enter는 음절 확정용이므로 전송하지 않는다.
    // 조합 중 전송하면 마지막 음절이 확정되며 중복 입력되는 버그가 발생한다.
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  function placeholder() {
    if (frozen) return '채팅이 일시중지되었습니다'
    if (disabled) return '연결 중...'
    return '채팅 메시지 보내기'
  }

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); submit() }}
      className={`px-3 py-3 border-t transition-colors ${frozen ? 'bg-cyan-950/30 border-cyan-900/40' : 'bg-gray-950 border-gray-800'}`}
    >
      <div className="flex gap-2 items-center">
        <input
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value.slice(0, MAX_LENGTH))}
          onKeyDown={handleKeyDown}
          disabled={isBlocked}
          placeholder={placeholder()}
          className={`flex-1 text-gray-100 rounded-lg px-3 py-2 text-sm outline-none disabled:opacity-50 disabled:cursor-not-allowed transition-colors ${
            frozen
              ? 'bg-cyan-900/20 placeholder-cyan-700 border border-cyan-800/40 focus:ring-1 focus:ring-cyan-700'
              : 'bg-gray-800 placeholder-gray-500 focus:ring-1 focus:ring-indigo-500'
          }`}
        />
        <button
          type="submit"
          disabled={isBlocked || !value.trim()}
          className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors shrink-0"
        >
          채팅
        </button>
      </div>
      {errorMessage && (
        <p className="text-xs text-red-400 mt-1">{errorMessage}</p>
      )}
      {!frozen && (
        <div className="flex justify-end mt-1">
          <span className="text-[10px] text-gray-600">{value.length}/{MAX_LENGTH}</span>
        </div>
      )}
    </form>
  )
}
