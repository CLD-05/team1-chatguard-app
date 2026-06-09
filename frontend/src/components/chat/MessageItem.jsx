const NAME_COLORS = [
  'text-red-400', 'text-yellow-400', 'text-green-400', 'text-cyan-400',
  'text-blue-400', 'text-purple-400', 'text-pink-400', 'text-orange-400',
]

function nameColor(displayName) {
  let hash = 0
  for (let i = 0; i < displayName.length; i++) {
    hash = displayName.charCodeAt(i) + ((hash << 5) - hash)
  }
  return NAME_COLORS[Math.abs(hash) % NAME_COLORS.length]
}

export default function MessageItem({ message, isOwn }) {
  if (message.status === 'DELETED') return null

  const isBlurred = message.status === 'BLURRED'
  const color = isOwn ? 'text-indigo-400' : nameColor(message.display_name)

  return (
    <div className="px-3 py-0.5 hover:bg-white/5 rounded transition-colors group">
      <p className="text-sm leading-relaxed break-words">
        <span className="text-gray-600 text-xs mr-1.5 opacity-0 group-hover:opacity-100 transition-opacity select-none">
          {new Date(message.created_at).toLocaleTimeString('ko-KR', {
            hour: '2-digit', minute: '2-digit',
          })}
        </span>
        <span className={`font-bold mr-1 ${color}`}>{message.display_name}</span>
        <span className="text-gray-500 mr-1 select-none">:</span>
        {isBlurred ? (
          <span className="relative inline">
            <span className="blur-sm select-none text-gray-400">{message.content}</span>
            <span className="ml-1.5 text-[10px] font-medium bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 px-1.5 py-0.5 rounded-full align-middle">
              AI 검열
            </span>
          </span>
        ) : (
          <span className="text-gray-100">{message.content}</span>
        )}
      </p>
    </div>
  )
}
