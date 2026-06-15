import { test, expect } from '@playwright/test'

test('화면 밖 메시지에 moderation.hide 도착 시 블러 처리', async ({ page }) => {
  // 1. 로그인
  await page.goto('http://localhost:5174')
  await page.fill('input[type="text"]', '테스터')
  await page.click('button[type="submit"]')

  // 2. 첫 번째 채팅방 입장
  await page.waitForURL('**/home')
  await page.locator('button').filter({ hasText: 'LCK' }).first().click()
  await page.waitForURL('**/chat/**')

  // 3. sentinel 전송 전 현재 메시지 수 파악
  const input = page.locator('input[placeholder*="채팅"]')
  await page.waitForTimeout(500) // 초기 메시지 렌더 대기
  const initialCount = await page.locator('[data-testid^="msg-"]').count()

  // 4. sentinel 메시지 전송 (BAD_WORD → 3초 후 moderation.hide 발생)
  await input.fill('바보')
  await page.keyboard.press('Enter')

  // 5. sentinel이 DOM에 나타날 때까지 대기 후 testid 캡처
  await page.waitForFunction(
    (count) => document.querySelectorAll('[data-testid^="msg-"]').length > count,
    initialCount,
  )
  const sentinelTestId = await page.locator('[data-testid^="msg-"]').nth(initialCount).getAttribute('data-testid')

  // 6. 메시지 30개 전송 → sentinel을 화면 밖으로 밀어냄
  for (let i = 0; i < 30; i++) {
    await input.fill(`메시지 ${i + 1}`)
    await page.keyboard.press('Enter')
  }

  // 7. moderation.hide 도착 대기 (mock: 3초 후 발생)
  await page.waitForTimeout(4_000)

  // 8. 상태 확인 — sentinel이 BLURRED인지 체크 (핵심 수용 기준)
  const sentinelStatus = await page.evaluate((testId) => {
    const msgs = window.__chatMessages__ ?? []
    const msgId = testId.replace('msg-', '')
    return msgs.find((m) => m.id === msgId)?.status ?? 'NOT_FOUND'
  }, sentinelTestId)

  console.log('sentinel status after moderation.hide:', sentinelStatus)
  expect(sentinelStatus).toBe('BLURRED')

  // 9. DOM에서도 확인 — scrollToIndex로 sentinel 강제 마운트
  // react-virtuoso v4: scrollToIndex는 0-based data index (firstItemIndex 오프셋 없음)
  await page.evaluate((testId) => {
    const msgId = testId.replace('msg-', '')
    const visible = (window.__chatMessages__ ?? []).filter((m) => m.status !== 'DELETED')
    const dataIdx = visible.findIndex((m) => m.id === msgId)
    if (dataIdx !== -1) window.__virtuoso__?.scrollToIndex({ index: dataIdx, behavior: 'auto' })
  }, sentinelTestId)

  // Virtuoso가 해당 위치를 렌더할 때까지 폴링
  await page.waitForFunction(
    (testId) => !!document.querySelector(`[data-testid="${testId}"]`),
    sentinelTestId,
    { timeout: 10_000 },
  )

  // 10. blur-sm 클래스 확인 (AI 검열 = filter: blur 적용)
  await expect(
    page.locator(`[data-testid="${sentinelTestId}"] .blur-sm`)
  ).toBeVisible()
})
