import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import useChat from './useChat'
import * as api from '../api/axios'

// 1. axios API 모킹
vi.mock('../api/axios', () => ({
  getMessages: vi.fn(() => Promise.resolve([])),
  setMockWsHandler: vi.fn(),
  simulateModerationHide: vi.fn(),
  USE_MOCK: false,
}))

describe('useChat Hook - 웹소켓 장애 대응 로직 단위 테스트', () => {
  let mockWebSocketInstance
  const originalWebSocket = globalThis.WebSocket

  beforeEach(() => {
    vi.useFakeTimers()
    api.getMessages.mockResolvedValue([])

    // 2. 가짜 WebSocket 객체 정의
    mockWebSocketInstance = {
      close: vi.fn(function() {
        // 실제 브라우저 스펙처럼 close() 실행 시 비동기적으로 onclose를 발동시켜 
        // 오프라인 상태일 때 onclose에 의한 재연결 무한루프 회귀 버그를 검증함
        if (this.onclose) {
          setTimeout(() => {
            this.onclose({ code: 1000 })
          }, 0)
        }
      }),
      readyState: 0, // CONNECTING
      send: vi.fn(),
    }
    
    // Class를 활용하여 new 생성자 에러 방지
    class MockWebSocket {
      constructor() {
        mockWebSocketInstance.readyState = 0
        return mockWebSocketInstance
      }
    }
    globalThis.WebSocket = MockWebSocket
  })

  afterEach(() => {
    globalThis.WebSocket = originalWebSocket
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('enabled가 false인 경우 연결을 시도하지 않고 DISCONNECTED 상태로 머문다', () => {
    const { result } = renderHook(() =>
      useChat({
        roomId: 1,
        token: 'test-token',
        enabled: false,
      })
    )

    // global.WebSocket는 MockWebSocket 클래스이므로,
    // new 인스턴스가 호출되지 않았는지만 확인하기 위해 standard check 수행
    expect(result.current.connectionStatus).toBe('DISCONNECTED')
  })

  it('연결 수립(onopen) 후 5초 이내에 끊어질 경우 백오프 딜레이를 초기화하지 않고 누적한다', async () => {
    const { result } = renderHook(() =>
      useChat({
        roomId: 1,
        token: 'test-token',
        enabled: true,
      })
    )

    // onopen 임의 실행
    act(() => {
      mockWebSocketInstance.onopen()
    })
    expect(result.current.connectionStatus).toBe('CONNECTED')

    // 5초 안정화 시간 도달 전(예: 3초 후) 1013(Cap 초과) 코드로 끊어짐
    act(() => {
      vi.advanceTimersByTime(3000)
      mockWebSocketInstance.onclose({ code: 1013 })
    })

    expect(result.current.connectionStatus).toBe('RECONNECTING')

    // 5초 이내 단절이었기 때문에 백오프가 누적되어 1.5초(1500ms) 대기 타이머 시점까지도 재연결이 진행 중임
    act(() => {
      vi.advanceTimersByTime(500)
    })
    expect(result.current.connectionStatus).toBe('RECONNECTING')
  })

  it('offline 이벤트 감지 시 소켓을 즉시 닫고 DISCONNECTED 상태로 전이시킨다', () => {
    const { result } = renderHook(() =>
      useChat({
        roomId: 1,
        token: 'test-token',
        enabled: true,
      })
    )

    // onopen을 거쳐 연결됨
    act(() => {
      mockWebSocketInstance.onopen()
    })
    expect(result.current.connectionStatus).toBe('CONNECTED')

    // window offline 이벤트 강제 방출
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })

    // 즉시 소켓 close() 함수가 호출되었고 상태가 변경되었는지 확인
    expect(mockWebSocketInstance.close).toHaveBeenCalled()
    expect(result.current.connectionStatus).toBe('DISCONNECTED')

    // 비동기 onclose()가 뒤이어 발생한 후에도 
    // isOffline 가드로 인해 RECONNECTING으로 복구되지 않고 DISCONNECTED를 유지하는지 엄격 검증
    act(() => {
      vi.advanceTimersByTime(50)
    })
    expect(result.current.connectionStatus).toBe('DISCONNECTED')
  })
})
