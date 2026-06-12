import { useState } from 'react'

const MAX_LENGTH = 500

export default function ChatInput({ onSend, disabled, errorMessage }) {
  const [value, setValue] = useState('')

  function submit() {
    const trimmed = value.trim()
    if (!trimmed || disabled) return
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

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); submit() }}
      className="px-3 py-3 bg-gray-950 border-t border-gray-800"
    >
      <div className="flex gap-2 items-center">
        <input
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value.slice(0, MAX_LENGTH))}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder={disabled ? '연결 중...' : '채팅 메시지 보내기'}
          className="flex-1 bg-gray-800 text-gray-100 placeholder-gray-500 rounded-lg px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-40"
        />
        <button
          type="submit"
          disabled={disabled || !value.trim()}
          className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors shrink-0"
        >
          채팅
        </button>
      </div>
      {errorMessage && (
        <p className="text-xs text-red-400 mt-1">{errorMessage}</p>
      )}
      <div className="flex justify-end mt-1">
        <span className="text-[10px] text-gray-600">{value.length}/{MAX_LENGTH}</span>
      </div>
    </form>
  )
}
