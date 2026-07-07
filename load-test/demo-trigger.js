// 시연용 로컬 트리거 서버 — HomePage.jsx의 2번방 클릭이 이 서버를 호출하면
// demo-hmb.js(k6)를 자동 실행한다.
// 이 서버는 실행한 사람 본인 컴퓨터에서만 동작 — 다른 사람이 2번방을 눌러도 트리거되지 않는다.
// 시연 녹화 끝나면 이 파일 + HomePage.jsx의 fetch 훅 둘 다 제거할 것 (임시 데모용).
//
// 기본은 로컬(127.0.0.1:8080) — AWS로 쏘고 싶을 때만 K6_HOST를 지정해서 덮어쓴다.
// 실행:
//   node load-test/demo-trigger.js                                   (로컬 테스트)
//   K6_HOST=<ALB 도메인> node load-test/demo-trigger.js                (AWS 시연)
const http = require('http')
const { spawn } = require('child_process')

const PORT = 4000
const K6_HOST = process.env.K6_HOST || '127.0.0.1:8080'
console.log(`k6 대상: ${K6_HOST}`)
let running = false

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
  if (req.method === 'OPTIONS') {
    res.writeHead(204)
    res.end()
    return
  }
  if (req.method !== 'POST' || req.url !== '/start') {
    res.writeHead(404)
    res.end()
    return
  }
  if (running) {
    res.writeHead(409)
    res.end('already running')
    return
  }

  running = true
  const child = spawn('k6', ['run', 'demo-hmb.js'], {
    cwd: __dirname,
    shell: true,
    env: {
      ...process.env,
      K6_HOST,
      K6_TEST_START: String(Date.now()),
    },
  })
  child.stdout.on('data', (d) => process.stdout.write(d))
  child.stderr.on('data', (d) => process.stderr.write(d))
  child.on('exit', (code) => {
    running = false
    console.log(`k6 종료 (code ${code})`)
  })

  res.writeHead(200)
  res.end('started')
})

server.listen(PORT, () => console.log(`데모 트리거 서버 대기중: http://localhost:${PORT}`))
