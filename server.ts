import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import JSZip from 'jszip';
import { GoogleGenAI, Type } from '@google/genai';
import { createServer as createViteServer } from 'vite';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const isProd = process.env.NODE_ENV === 'production';

const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: {
    headers: {
      'User-Agent': 'aistudio-build',
    },
  },
});

const ANDROID_DIR = path.resolve(__dirname, 'android-native');
const PUBLIC_DIR = path.resolve(__dirname, 'public');
const DIST_DIR = path.resolve(__dirname, 'dist');

function collectFilesRecursive(dir: string, baseDir: string = dir): string[] {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    if (entry.name === '.gradle' || entry.name === 'build') continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectFilesRecursive(fullPath, baseDir));
    } else {
      files.push(path.relative(baseDir, fullPath).replace(/\\/g, '/'));
    }
  }
  return files.sort();
}

function isTextFile(filePath: string): boolean {
  const ext = path.extname(filePath).toLowerCase();
  return [
    '.kt', '.java', '.xml', '.kts', '.gradle', '.properties',
    '.pro', '.md', '.yml', '.yaml', '.json', '.html', '.js', '.txt', '.example',
  ].includes(ext);
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '2mb' }));

  // 1. Server-side Gemini AI Question Generator
  app.post('/api/ai/generate-questions', async (req, res) => {
    try {
      const topic = String(req.body?.topic || '').trim();
      const rawCount = Number(req.body?.count || 5);
      const count = Math.min(10, Math.max(1, Number.isFinite(rawCount) ? rawCount : 5));

      if (!topic) {
        return res.status(400).json({ error: 'Topik materi wajib diisi.' });
      }
      if (!process.env.GEMINI_API_KEY) {
        return res.status(503).json({
          error: 'GEMINI_API_KEY belum dikonfigurasi di Settings > Secrets.',
        });
      }

      const response = await ai.models.generateContent({
        model: 'gemini-4-hydrogen',
        contents: `Buatkan tepat ${count} soal pilihan ganda berkualitas tinggi tentang "${topic}" dalam Bahasa Indonesia untuk siswa sekolah menengah.`,
        config: {
          systemInstruction:
            'Anda adalah penyusun soal ujian profesional Indonesia. Kembalikan array JSON berisi soal pilihan ganda dengan 4 opsi berawalan A., B., C., D. dan kunci jawaban berupa huruf A, B, C, atau D.',
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                question: { type: Type.STRING, description: 'Pertanyaan soal ujian.' },
                options: {
                  type: Type.ARRAY,
                  items: { type: Type.STRING },
                  description: 'Empat pilihan jawaban A sampai D.',
                },
                answer: { type: Type.STRING, description: 'Huruf jawaban benar (A/B/C/D).' },
                explanation: { type: Type.STRING, description: 'Pembahasan singkat jawaban benar.' },
              },
              required: ['question', 'options', 'answer'],
            },
          },
        },
      });

      const parsed = JSON.parse((response.text ?? '[]').trim());
      return res.json({ questions: Array.isArray(parsed) ? parsed : [] });
    } catch (err: unknown) {
      console.error('Gemini generate-questions error:', err);
      const message = err instanceof Error ? err.message : 'Gagal membuat soal AI.';
      return res.status(500).json({ error: message });
    }
  });

  // 2. Native Android Project File Explorer API
  app.get('/api/android/files', (_req, res) => {
    try {
      const relFiles = collectFilesRecursive(ANDROID_DIR);
      const files = relFiles.map((rel) => {
        const full = path.join(ANDROID_DIR, rel);
        const stat = fs.statSync(full);
        const text = isTextFile(rel);
        return {
          path: rel,
          size: stat.size,
          isText: text,
          content: text && stat.size < 260000 ? fs.readFileSync(full, 'utf-8') : null,
        };
      });
      res.json({ root: 'android-native', count: files.length, files });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to list Android files';
      res.status(500).json({ error: message });
    }
  });

  // 3. Download Complete Release-Ready Android Studio Project (.zip)
  app.get('/api/android/download-zip', async (_req, res) => {
    try {
      const zip = new JSZip();
      const relFiles = collectFilesRecursive(ANDROID_DIR);

      for (const rel of relFiles) {
        const full = path.join(ANDROID_DIR, rel);
        zip.file(`prida-exambro-android/${rel}`, fs.readFileSync(full));
      }

      // Sertakan salinan aset web yang sudah disempurnakan
      const webAssetsDir = 'prida-exambro-android/app/src/main/assets/web';
      const exambroPath = path.join(PUBLIC_DIR, 'exambro.html');
      if (fs.existsSync(exambroPath)) {
        zip.file(`${webAssetsDir}/index.html`, fs.readFileSync(exambroPath));
      }
      for (const assetName of ['manifest.json', 'sw.js']) {
        const p = path.join(PUBLIC_DIR, assetName);
        if (fs.existsSync(p)) zip.file(`${webAssetsDir}/${assetName}`, fs.readFileSync(p));
      }
      const iconsDir = path.join(PUBLIC_DIR, 'icons');
      if (fs.existsSync(iconsDir)) {
        for (const icon of fs.readdirSync(iconsDir)) {
          const iconPath = path.join(iconsDir, icon);
          if (fs.statSync(iconPath).isFile()) {
            zip.file(`${webAssetsDir}/icons/${icon}`, fs.readFileSync(iconPath));
          }
        }
      }

      const buffer = await zip.generateAsync({
        type: 'nodebuffer',
        compression: 'DEFLATE',
        compressionOptions: { level: 6 },
      });

      res.setHeader('Content-Type', 'application/zip');
      res.setHeader(
        'Content-Disposition',
        'attachment; filename="prida-exambro-native-android-admob.zip"'
      );
      res.send(buffer);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to generate ZIP';
      res.status(500).json({ error: message });
    }
  });

  // Static PWA assets (.well-known, icons, screenshots, manifest.json, sw.js)
  app.use(express.static(PUBLIC_DIR, { index: false }));

  // Serve polished Exambro PWA at root '/'
  app.get('/', (_req, res) => {
    const exambroFile = path.join(PUBLIC_DIR, 'exambro.html');
    if (fs.existsSync(exambroFile)) {
      return res.sendFile(exambroFile);
    }
    return res.status(404).send('Exambro app not found.');
  });

  if (!isProd) {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(DIST_DIR, { index: false }));
    app.get('/android-studio', (_req, res) => {
      res.sendFile(path.join(DIST_DIR, 'index.html'));
    });
    app.get('*', (_req, res) => {
      res.sendFile(path.join(DIST_DIR, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`PRIDA EXAMBRO Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
