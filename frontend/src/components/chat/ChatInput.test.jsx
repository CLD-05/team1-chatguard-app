import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import ChatInput from './ChatInput'

afterEach(cleanup)

// 한글 등 IME 조합 중 Enter는 음절 확정용이라 전송하면 안 된다.
// 조합 중 전송하면 마지막 음절이 중복 출력되는 회귀 버그가 생긴다.
describe('ChatInput - IME 조합 처리', () => {
  function setup() {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} disabled={false} />)
    const input = screen.getByPlaceholderText('채팅 메시지 보내기')
    return { onSend, input }
  }

  it('영어 입력 후 Enter는 정상 전송된다', () => {
    const { onSend, input } = setup()
    fireEvent.change(input, { target: { value: 'hello' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onSend).toHaveBeenCalledWith('hello')
  })

  it('한글 조합 중(isComposing) Enter는 전송하지 않는다', () => {
    const { onSend, input } = setup()
    fireEvent.change(input, { target: { value: '가나다' } })
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true })
    expect(onSend).not.toHaveBeenCalled()
  })

  it('조합이 끝난 뒤 Enter는 정상 전송된다', () => {
    const { onSend, input } = setup()
    fireEvent.change(input, { target: { value: '가나다' } })
    fireEvent.keyDown(input, { key: 'Enter', isComposing: false })
    expect(onSend).toHaveBeenCalledWith('가나다')
  })
})
