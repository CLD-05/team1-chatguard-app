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

describe('ChatInput - connectionStatus 기반 3단계 UI/UX 대응', () => {
  it('connectionStatus가 RECONNECTING이면 입력창이 잠기고 재접속 중 안내가 표시된다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} connectionStatus="RECONNECTING" />)
    const input = screen.getByPlaceholderText('서버와 연결이 끊어졌습니다. 재접속 중...')
    expect(input.disabled).toBe(true)
  })

  it('connectionStatus가 DISCONNECTED이면 입력창이 잠기고 단절 안내가 표시된다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} connectionStatus="DISCONNECTED" />)
    const input = screen.getByPlaceholderText('연결이 단절되었습니다')
    expect(input.disabled).toBe(true)
  })

  it('비활성화 상태(RECONNECTING)일 때 전송 시도는 완전 차단된다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} connectionStatus="RECONNECTING" />)
    const input = screen.getByPlaceholderText('서버와 연결이 끊어졌습니다. 재접속 중...')
    
    // 강제 값 변경 후 엔터 이벤트 주입
    fireEvent.change(input, { target: { value: '안녕' } })
    fireEvent.keyDown(input, { key: 'Enter', isComposing: false })
    expect(onSend).not.toHaveBeenCalled()
  })
})

describe('ChatInput - 관리자(ADMIN) 채팅 얼음 모드 우회', () => {
  it('방이 얼어있을 때 일반 사용자는 입력창이 잠기고 중지 안내가 표시된다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} frozen={true} isAdmin={false} />)
    const input = screen.getByPlaceholderText('채팅이 일시중지되었습니다')
    expect(input.disabled).toBe(true)
  })

  it('방이 얼어있을 때 관리자(ADMIN)는 입력창이 활성화되고 전용 안내가 표시된다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} frozen={true} isAdmin={true} />)
    const input = screen.getByPlaceholderText('관리자 권한으로 메시지를 전송합니다.')
    expect(input.disabled).toBe(false)
  })

  it('방이 얼어있고 관리자(ADMIN)이지만, 서버 연결이 끊어진 경우(DISCONNECTED) 입력창이 물리적으로 잠긴다', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} frozen={true} isAdmin={true} connectionStatus="DISCONNECTED" />)
    const input = screen.getByPlaceholderText('연결이 단절되었습니다')
    expect(input.disabled).toBe(true)
  })
})
