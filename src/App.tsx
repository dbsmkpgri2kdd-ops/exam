import { useEffect, useMemo, useState } from 'react';
import {
  Check,
  Copy,
  Download,
  ExternalLink,
  FileCode2,
  FolderTree,
  Search,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';

interface AndroidFileItem {
  path: string;
  size: number;
  isText: boolean;
  content: string | null;
}

interface AdMobUnit {
  format: string;
  unitId: string;
  placement: string;
  examPolicy: string;
}

const ADMOB_APP_ID = 'ca-app-pub-3979615977174432~1153915437';
const TEST_DEVICE_GAID = 'd9fb552c-cf63-4133-a677-8130f7262b14';

const ADMOB_UNITS: AdMobUnit[] = [
  {
    format: 'Banner Adaptif',
    unitId: 'ca-app-pub-3979615977174432/1997378769',
    placement: 'Login & Dashboard Siswa (bagian bawah layar)',
    examPolicy: 'Otomatis disembunyikan saat ujian terkunci',
  },
  {
    format: 'Interstisial',
    unitId: 'ca-app-pub-3979615977174432/1125995275',
    placement: 'Setelah siswa menekan Selesai Ujian',
    examPolicy: 'Hanya dipanggil pasca finishExam() dan setExamLock(false)',
  },
  {
    format: 'Pengantara Berbonus',
    unitId: 'ca-app-pub-3979615977174432/2596144566',
    placement: 'Simulasi / Pratinjau Tryout Mandiri',
    examPolicy: 'Tidak pernah memblokir ujian terjadwal',
  },
  {
    format: 'Iklan Reward',
    unitId: 'ca-app-pub-3979615977174432/1466895725',
    placement: 'Tombol Latihan Soal AI di Dashboard Siswa',
    examPolicy: 'Membuka bank soal latihan setelah reward terpenuhi',
  },
  {
    format: 'Native Advanced',
    unitId: 'ca-app-pub-3979615977174432/4572230359',
    placement: 'Kartu NativeAdView di Dashboard Siswa',
    examPolicy: 'Dinonaktifkan total saat sesi ujian aktif',
  },
  {
    format: 'Pembukaan Aplikasi',
    unitId: 'ca-app-pub-3979615977174432/9058270279',
    placement: 'Cold start / foreground resume via AppOpenAd',
    examPolicy: 'Diblokir otomatis jika isExamActiveGlobally = true',
  },
];

const AUDIT_FIXES = [
  {
    area: 'Deteksi Split-Screen & Keyboard Google Forms',
    issue:
      'Handler resize sebelumnya hanya mengecualikan tag INPUT/TEXTAREA/SELECT. Saat siswa mengetik di dalam iframe Google Forms, activeElement bernilai IFRAME sehingga keyboard Android memicu pelanggaran palsu.',
    resolution:
      'Menambahkan pengecualian focus IFRAME di web lockdown serta deteksi native onMultiWindowModeChanged pada MainActivity.kt.',
  },
  {
    area: 'Keamanan Kunci Gemini AI',
    issue:
      'Kunci API Gemini sebelumnya tertanam langsung di index.html sisi klien.',
    resolution:
      'Pembuatan soal AI kini diproses melalui endpoint server POST /api/ai/generate-questions menggunakan @google/genai dan variabel lingkungan server.',
  },
  {
    area: 'Kompatibilitas Dialog Android WebView',
    issue:
      'Pemanggilan window.alert() dan window.confirm() pada beberapa fungsi admin dan siswa sering terblokir di iframe/WebView.',
    resolution:
      'Seluruh dialog diganti dengan modal in-app custom (showConfirm & showNotify) serta handler native onJsAlert/onJsConfirm di WebChromeClient.',
  },
  {
    area: 'Panel Mode Aman Perangkat Pemilik',
    issue:
      'Fungsi syncOwnerAdProtection & toggleOwnerAdDeviceMode sudah ada di JS tetapi elemen HTML #owner-ad-safe-status dan #owner-admob-hash belum tersedia.',
    resolution:
      'Menambahkan panel kontrol Proteksi Iklan AdMob di Dashboard Admin dan sinkronisasi SharedPreferences di AdMobConfig.kt.',
  },
  {
    area: 'Sanitasi XSS & Integrasi 6 Format AdMob',
    issue:
      'Nama mapel/kelas pada kartu ujian belum di-escape dan bridge native baru mendukung Interstisial saja.',
    resolution:
      'Menerapkan escapeHtml() pada render kartu serta melengkapi bridge AndroidApp untuk seluruh 6 format AdMob dan Test Device ID.',
  },
  {
    area: 'Isolasi Mutlak Bebas Iklan Saat Ujian',
    issue:
      'Iklan Banner, Native, maupun App Open berpotensi muncul saat proses persiapan ujian, sinkronisasi akun Google, atau auto-resume ujian.',
    resolution:
      'Iklan kini ditahan hingga halaman non-ujian terkonfirmasi, diblokir total di level native (AdMobConfig.isExamInProgress, isExamPreparing, isExamLocked) maupun web (syncNativeAdsVisibility), dan tidak menimpa WebView.',
  },
  {
    area: 'Menu Setting Terkunci, WebViewAssetLoader & GitHub Actions',
    issue:
      'Origin file:// lokal dapat ditolak oleh checkSupabaseConnection, dan admin membutuhkan pengaturan terpusat untuk Supabase, AdMob, serta distribusi APK siswa.',
    resolution:
      'WebView memakai WebViewAssetLoader HTTPS, tersedia menu Setting admin terkunci password, dan workflow .github/workflows/build-android.yml siap kompilasi APK & AAB.',
  },
];

export default function App() {
  const [files, setFiles] = useState<AndroidFileItem[]>([]);
  const [loadingFiles, setLoadingFiles] = useState(true);
  const [filesError, setFilesError] = useState<string | null>(null);
  const [selectedPath, setSelectedPath] = useState<string>(
    'app/src/main/java/app/web/examprida/twa/MainActivity.kt'
  );
  const [fileFilter, setFileFilter] = useState<'all' | 'kotlin' | 'xml' | 'gradle'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState<'audit' | 'admob' | 'code' | 'release'>('audit');

  useEffect(() => {
    let active = true;
    fetch('/api/android/files')
      .then(async (res) => {
        if (!res.ok) throw new Error('Gagal memuat daftar file Android');
        return res.json();
      })
      .then((data) => {
        if (!active) return;
        const list: AndroidFileItem[] = Array.isArray(data.files) ? data.files : [];
        setFiles(list);
        if (list.length > 0 && !list.some((f) => f.path === selectedPath)) {
          const firstText = list.find((f) => f.isText);
          if (firstText) setSelectedPath(firstText.path);
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        setFilesError(err instanceof Error ? err.message : 'Gagal memuat file');
      })
      .finally(() => {
        if (active) setLoadingFiles(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const filteredFiles = useMemo(() => {
    return files.filter((f) => {
      if (!f.isText) return false;
      if (fileFilter === 'kotlin' && !f.path.endsWith('.kt')) return false;
      if (fileFilter === 'xml' && !f.path.endsWith('.xml')) return false;
      if (
        fileFilter === 'gradle' &&
        !f.path.endsWith('.kts') &&
        !f.path.endsWith('.properties') &&
        !f.path.endsWith('.pro') &&
        !f.path.endsWith('.yml')
      ) {
        return false;
      }
      if (searchQuery.trim() && !f.path.toLowerCase().includes(searchQuery.trim().toLowerCase())) {
        return false;
      }
      return true;
    });
  }, [files, fileFilter, searchQuery]);

  const activeFile = useMemo(
    () => files.find((f) => f.path === selectedPath) || filteredFiles[0] || null,
    [files, selectedPath, filteredFiles]
  );

  const handleCopy = async (text: string, key: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(key);
      setTimeout(() => setCopiedId((prev) => (prev === key ? null : prev)), 1800);
    } catch {
      // Clipboard blocked in non-secure context
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      {/* Top Bar Contract: 3 zones */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between gap-8">
          <a
            href="/android-studio"
            className="text-base font-extrabold tracking-tight text-slate-900 whitespace-nowrap shrink-0"
          >
            PRIDA EXAMBRO Studio
          </a>

          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600">
            <a
              href="#audit"
              onClick={() => setActiveSection('audit')}
              className={`hover:text-slate-900 transition-colors whitespace-nowrap shrink-0 ${
                activeSection === 'audit' ? 'text-indigo-600 font-semibold' : ''
              }`}
            >
              Audit Perbaikan
            </a>
            <a
              href="#admob"
              onClick={() => setActiveSection('admob')}
              className={`hover:text-slate-900 transition-colors whitespace-nowrap shrink-0 ${
                activeSection === 'admob' ? 'text-indigo-600 font-semibold' : ''
              }`}
            >
              Unit AdMob
            </a>
            <a
              href="#code"
              onClick={() => setActiveSection('code')}
              className={`hover:text-slate-900 transition-colors whitespace-nowrap shrink-0 ${
                activeSection === 'code' ? 'text-indigo-600 font-semibold' : ''
              }`}
            >
              Kode Android
            </a>
            <a
              href="#release"
              onClick={() => setActiveSection('release')}
              className={`hover:text-slate-900 transition-colors whitespace-nowrap shrink-0 ${
                activeSection === 'release' ? 'text-indigo-600 font-semibold' : ''
              }`}
            >
              Panduan Rilis
            </a>
            <a
              href="/"
              className="hover:text-slate-900 transition-colors whitespace-nowrap shrink-0 inline-flex items-center gap-1.5"
            >
              Buka Aplikasi Ujian
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </nav>

          <div className="flex items-center gap-3 shrink-0">
            <a
              href="/api/android/download-zip"
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors whitespace-nowrap shrink-0"
            >
              <Download className="w-4 h-4" />
              Download Project Android (.zip)
            </a>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-10 space-y-12">
        {/* Hero Summary */}
        <section className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
          <div className="lg:col-span-8 bg-white border border-slate-200 rounded-2xl p-8 flex flex-col justify-between">
            <div>
              <p className="text-xs font-semibold text-indigo-600 mb-2">
                Native Android Kotlin · Kiosk LockTask · Google Mobile Ads SDK 23.6.0
              </p>
              <h1 className="text-3xl font-extrabold tracking-tight text-slate-900 max-w-2xl">
                Project Exambro Terintegrasi Native Android &amp; 6 Unit AdMob Siap Rilis
              </h1>
              <p className="mt-3 text-sm text-slate-600 leading-relaxed max-w-2xl">
                Repository Exambro Anda telah diperiksa, diperbaiki bug keamanannya, disinkronkan
                aset web terbarunya, dan dibungkus ke dalam project Android Studio (Kotlin) siap
                kompilasi untuk APK maupun Android App Bundle (.aab) Play Store.
              </p>
            </div>

            <div className="mt-8 pt-6 border-t border-slate-100 grid grid-cols-2 sm:grid-cols-4 gap-6">
              <div>
                <p className="text-xs text-slate-500">Application ID</p>
                <p className="mt-1 font-mono text-xs font-semibold text-slate-900 truncate">
                  app.web.examprida.twa
                </p>
              </div>
              <div>
                <p className="text-xs text-slate-500">Target SDK</p>
                <p className="mt-1 font-mono text-sm font-bold text-slate-900">API 35 (Min 24)</p>
              </div>
              <div>
                <p className="text-xs text-slate-500">Unit Iklan AdMob</p>
                <p className="mt-1 font-mono text-sm font-bold text-slate-900">6 Format Aktif</p>
              </div>
              <div>
                <p className="text-xs text-slate-500">Total File Project</p>
                <p className="mt-1 font-mono text-sm font-bold text-slate-900">
                  {loadingFiles ? '...' : `${files.length} file`}
                </p>
              </div>
            </div>
          </div>

          <div className="lg:col-span-4 bg-slate-900 text-white rounded-2xl p-8 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2.5 text-indigo-300 text-xs font-semibold mb-3">
                <Smartphone className="w-4 h-4" />
                Paket Build Siap Kompilasi
              </div>
              <h2 className="text-xl font-bold tracking-tight">
                Ekspor Langsung ke Android Studio atau GitHub Actions
              </h2>
              <p className="mt-2.5 text-xs text-slate-300 leading-relaxed">
                Unduh arsip ZIP berisi struktur lengkap Gradle Kotlin DSL, AndroidManifest.xml,
                AdMobManager.kt, ProGuard rules, keystore template, aset web terbaru, dan workflow
                CI otomatis.
              </p>
            </div>

            <div className="mt-6 space-y-3">
              <a
                href="/api/android/download-zip"
                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 bg-indigo-500 hover:bg-indigo-400 text-white text-xs font-bold rounded-xl transition-colors"
              >
                <Download className="w-4 h-4" />
                Unduh prida-exambro-native-android-admob.zip
              </a>
              <a
                href="/"
                className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-xl transition-colors"
              >
                Jalankan Aplikasi Exambro Web
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>
        </section>

        {/* Section 1: Audit Perbaikan */}
        <section id="audit" className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
          <div className="px-6 py-5 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                01. Hasil Pemeriksaan &amp; Penyempurnaan Project GitHub
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Sumber diperiksa: https://github.com/dbsmkpgri2kdd-ops/exam.git
              </p>
            </div>
            <span className="text-xs font-semibold text-emerald-700 inline-flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4" />
              {AUDIT_FIXES.length} Perbaikan Diterapkan
            </span>
          </div>

          <div className="divide-y divide-slate-100">
            {AUDIT_FIXES.map((item) => (
              <div
                key={item.area}
                className="px-6 py-4 grid grid-cols-1 md:grid-cols-12 gap-4 items-start hover:bg-slate-50/70 transition-colors"
              >
                <div className="md:col-span-3">
                  <p className="text-sm font-semibold text-slate-900">{item.area}</p>
                </div>
                <div className="md:col-span-4">
                  <p className="text-xs text-slate-600 leading-relaxed">{item.issue}</p>
                </div>
                <div className="md:col-span-5">
                  <p className="text-xs text-slate-800 font-medium leading-relaxed">
                    {item.resolution}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Section 2: AdMob Configuration Table */}
        <section id="admob" className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
          <div className="px-6 py-5 border-b border-slate-200 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                02. Konfigurasi 6 Unit Iklan AdMob &amp; Perangkat Uji
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Seluruh iklan otomatis dinonaktifkan saat Mode Kunci Ujian aktif dan mendukung Mode
                Aman Perangkat Pemilik.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-4 text-xs">
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 px-3 py-2 rounded-lg">
                <span className="text-slate-500">App ID:</span>
                <code className="font-mono font-semibold text-slate-900">{ADMOB_APP_ID}</code>
                <button
                  type="button"
                  onClick={() => handleCopy(ADMOB_APP_ID, 'app-id')}
                  className="text-slate-500 hover:text-indigo-600 p-1"
                  title="Salin App ID"
                >
                  {copiedId === 'app-id' ? (
                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 px-3 py-2 rounded-lg">
                <span className="text-slate-500">Test Device:</span>
                <code className="font-mono font-semibold text-slate-900">{TEST_DEVICE_GAID}</code>
                <button
                  type="button"
                  onClick={() => handleCopy(TEST_DEVICE_GAID, 'test-id')}
                  className="text-slate-500 hover:text-indigo-600 p-1"
                  title="Salin Test Device ID"
                >
                  {copiedId === 'test-id' ? (
                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-6 py-3">Format Iklan</th>
                  <th className="px-6 py-3">Ad Unit ID</th>
                  <th className="px-6 py-3">Penempatan</th>
                  <th className="px-6 py-3">Kebijakan Saat Ujian</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {ADMOB_UNITS.map((unit) => (
                  <tr key={unit.unitId} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-6 py-3.5 font-semibold text-slate-900 whitespace-nowrap">
                      {unit.format}
                    </td>
                    <td className="px-6 py-3.5 font-mono text-slate-700 whitespace-nowrap">
                      <div className="inline-flex items-center gap-2">
                        <span>{unit.unitId}</span>
                        <button
                          type="button"
                          onClick={() => handleCopy(unit.unitId, unit.unitId)}
                          className="text-slate-400 hover:text-indigo-600 p-1"
                          title="Salin Unit ID"
                        >
                          {copiedId === unit.unitId ? (
                            <Check className="w-3.5 h-3.5 text-emerald-600" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>
                    </td>
                    <td className="px-6 py-3.5 text-slate-600">{unit.placement}</td>
                    <td className="px-6 py-3.5 text-emerald-700 font-medium">{unit.examPolicy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {/* Section 3: Android Native Code Explorer */}
        <section id="code" className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
          <div className="px-6 py-5 border-b border-slate-200 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                03. Eksplorasi Kode Native Android (Kotlin &amp; Gradle)
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Pratinjau langsung seluruh file sumber project Android Studio yang siap dikompilasi.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-lg">
                {(
                  [
                    ['all', 'Semua'],
                    ['kotlin', 'Kotlin'],
                    ['xml', 'XML'],
                    ['gradle', 'Gradle & CI'],
                  ] as const
                ).map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setFileFilter(key)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap ${
                      fileFilter === key
                        ? 'bg-white text-slate-900 shadow-sm'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="relative">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Filter file..."
                  className="pl-8 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg outline-none focus:border-indigo-500 w-44"
                />
              </div>
            </div>
          </div>

          {filesError ? (
            <div className="p-8 text-center text-xs text-rose-600 font-semibold">{filesError}</div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-12 min-h-[520px]">
              {/* File Tree */}
              <div className="lg:col-span-4 border-b lg:border-b-0 lg:border-r border-slate-200 max-h-[540px] overflow-y-auto divide-y divide-slate-100">
                {loadingFiles ? (
                  <div className="p-6 space-y-3">
                    {Array.from({ length: 6 }).map((_, idx) => (
                      <div key={idx} className="h-8 bg-slate-100 rounded animate-pulse" />
                    ))}
                  </div>
                ) : filteredFiles.length === 0 ? (
                  <div className="p-8 text-center text-xs text-slate-500">
                    Tidak ada file yang cocok dengan pencarian Anda.
                  </div>
                ) : (
                  filteredFiles.map((file) => {
                    const isSelected = activeFile?.path === file.path;
                    return (
                      <button
                        key={file.path}
                        type="button"
                        onClick={() => setSelectedPath(file.path)}
                        className={`w-full text-left px-4 py-3 flex items-center justify-between gap-3 transition-colors ${
                          isSelected
                            ? 'bg-indigo-50 text-indigo-700'
                            : 'hover:bg-slate-50 text-slate-700'
                        }`}
                      >
                        <span className="inline-flex items-center gap-2 min-w-0">
                          <FileCode2 className="w-4 h-4 shrink-0 text-slate-400" />
                          <span className="font-mono text-xs truncate">{file.path}</span>
                        </span>
                        <span className="font-mono text-[11px] text-slate-400 shrink-0">
                          {(file.size / 1024).toFixed(1)} KB
                        </span>
                      </button>
                    );
                  })
                )}
              </div>

              {/* Code Preview */}
              <div className="lg:col-span-8 flex flex-col bg-slate-950 text-slate-100">
                <div className="px-5 py-3 border-b border-slate-800 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-2 min-w-0">
                    <FolderTree className="w-4 h-4 text-indigo-400 shrink-0" />
                    <span className="font-mono text-xs text-slate-300 truncate">
                      {activeFile ? activeFile.path : 'Pilih file'}
                    </span>
                  </div>
                  {activeFile?.content && (
                    <button
                      type="button"
                      onClick={() => handleCopy(activeFile.content || '', 'active-code')}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 rounded-lg transition-colors shrink-0"
                    >
                      {copiedId === 'active-code' ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-emerald-400" />
                          Tersalin
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          Salin Kode
                        </>
                      )}
                    </button>
                  )}
                </div>

                <pre className="p-5 text-xs leading-relaxed overflow-auto max-h-[490px] font-mono text-slate-200">
                  <code>{activeFile?.content || '// Memuat konten file...'}</code>
                </pre>
              </div>
            </div>
          )}
        </section>

        {/* Section 4: Panduan Rilis Play Store */}
        <section id="release" className="bg-white border border-slate-200 rounded-2xl p-6 md:p-8">
          <h2 className="text-lg font-bold text-slate-900">
            04. Langkah Build APK &amp; AAB Siap Rilis Play Store
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Ikuti 3 langkah di bawah ini setelah mengunduh paket project Android Studio.
          </p>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="border border-slate-200 rounded-xl p-5">
              <p className="text-xs font-bold text-indigo-600 mb-2">Langkah 1 · Signing Keystore</p>
              <p className="text-xs text-slate-600 leading-relaxed mb-3">
                Buat file keystore produksi (simpan baik-baik) lalu salin{' '}
                <code className="font-mono text-slate-900">keystore.properties.example</code> menjadi{' '}
                <code className="font-mono text-slate-900">keystore.properties</code>.
              </p>
              <pre className="bg-slate-900 text-slate-100 p-3 rounded-lg text-[11px] font-mono overflow-x-auto">
                keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048
                -validity 10000 -alias exambro_release
              </pre>
            </div>

            <div className="border border-slate-200 rounded-xl p-5">
              <p className="text-xs font-bold text-indigo-600 mb-2">
                Langkah 2 · Build via Android Studio
              </p>
              <p className="text-xs text-slate-600 leading-relaxed">
                Buka folder <code className="font-mono text-slate-900">prida-exambro-android</code>{' '}
                di Android Studio, tunggu Gradle Sync selesai, lalu pilih{' '}
                <strong className="text-slate-900">
                  Build &gt; Generate Signed Bundle / APK
                </strong>{' '}
                untuk menghasilkan file <code className="font-mono text-slate-900">.aab</code>{' '}
                (Play Store) atau <code className="font-mono text-slate-900">.apk</code>.
              </p>
            </div>

            <div className="border border-slate-200 rounded-xl p-5">
              <p className="text-xs font-bold text-indigo-600 mb-2">
                Langkah 3 · Auto-Build GitHub Actions
              </p>
              <p className="text-xs text-slate-600 leading-relaxed">
                Atau push isi folder <code className="font-mono text-slate-900">android-native</code>{' '}
                ke repository GitHub Anda. Workflow{' '}
                <code className="font-mono text-slate-900">
                  .github/workflows/build-android.yml
                </code>{' '}
                sudah disiapkan untuk mengompilasi APK dan AAB secara otomatis di cloud.
              </p>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
