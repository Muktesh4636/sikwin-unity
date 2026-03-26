import fs from 'node:fs'
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** Serve Unity WebGL .gz assets with Content-Encoding: gzip so the browser decompresses them. */
function unityWebGLGzip() {
  const gzipExtensions = ['.js', '.data', '.wasm']
  return {
    name: 'unity-webgl-gzip',
    enforce: 'pre' as const,
    configureServer(server: { middlewares: { use: (fn: (req: any, res: any, next: () => void) => void) => void } }) {
      server.middlewares.use((req, res, next) => {
        const url = req.url?.split('?')[0] ?? ''
        if (!url.startsWith('/game/Build/')) {
          next()
          return
        }
        const base = path.basename(url)
        const ext = path.extname(base)
        if (!gzipExtensions.includes(ext)) {
          next()
          return
        }
        const publicDir = path.resolve(__dirname, 'public')
        const gzPath = path.join(publicDir, 'game', 'Build', base + '.gz')
        if (!fs.existsSync(gzPath)) {
          next()
          return
        }
        const contentType =
          ext === '.js' ? 'application/javascript'
          : ext === '.wasm' ? 'application/wasm'
          : 'application/octet-stream'
        res.setHeader('Content-Encoding', 'gzip')
        res.setHeader('Content-Type', contentType)
        res.setHeader('Cache-Control', 'public, max-age=31536000')
        if (req.method === 'HEAD') {
          res.statusCode = 200
          res.end()
          return
        }
        fs.createReadStream(gzPath).pipe(res)
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [unityWebGLGzip(), react()],
  server: {
    host: true, // listen on 0.0.0.0 so mobile on same WiFi can connect
    port: 5173,
  },
})
