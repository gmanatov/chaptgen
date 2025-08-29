'use client';

import { useEffect, useState } from 'react';
import { apiJson, apiMe, apiLogout } from '@/lib/api';
import Link from 'next/link';
import { useRouter } from 'next/navigation';


type Chapter = { start: string; title: string };

export default function Home() {
  const router = useRouter();
  const [me, setMe] = useState<{ ok: boolean; email?: string }>({ ok: false });
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [chapters, setChapters] = useState<Chapter[] | null>(null);
  const [chaptersText, setChaptersText] = useState<string>('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    apiMe().then(setMe).catch(() => setMe({ ok: false }));
  }, []);

  async function handleLogout() {
    try {
      await apiLogout();
      setMe({ ok: false });
      setUrl('');
      setChapters(null);
      setChaptersText('');
      setSaved(false);
      setErrorMsg(null);
      router.push('/');
    } catch {}
  }

  async function handleGenerate() {
    setSaved(false);
    setErrorMsg(null);
    setChapters(null);
    setChaptersText('');
    if (!url.trim()) {
      setErrorMsg('Please paste a YouTube URL.');
      return;
    }
    setLoading(true);
    try {
      const data = await apiJson<{
        ok: boolean;
        chapters: Chapter[];
        chapters_text?: string;
        note?: string;
        error?: string;
        url: string;
      }>('/transcripts/preview', {
        method: 'POST',
        body: JSON.stringify({ url }),
      });

      if (!data.ok) {
        setErrorMsg(data.error || 'Failed to generate chapters.');
      } else {
        const arr = data.chapters || [];
        setChapters(arr);
        setChaptersText(
          data.chapters_text || arr.map((c) => `${c.start} ${c.title}`).join('\n')
        );
        if (!arr.length) {
          setErrorMsg('No chapters were generated for this video.');
        }
      }
    } catch (e: any) {
      setErrorMsg(e.message || 'Request failed.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!chapters || !chapters.length) return;
    setErrorMsg(null);
    setSaved(false);
    try {
      const data = await apiJson<{
        id?: number;
        status?: string;
        ok?: boolean;
        error?: string;
      }>('/transcripts', {
        method: 'POST',
        body: JSON.stringify({
          url,
          chaptersJson: chapters,
        }),
      });

      if (data?.ok === false) {
        setErrorMsg(data.error || 'Save failed.');
        return;
      }

      setSaved(true);
    } catch (e: any) {
      setErrorMsg(e.message || 'Save failed.');
    }
  }

  return (
    <main className="relative flex min-h-screen flex-col items-center p-8 overflow-hidden">
      {}
      {!me.ok && (
        <video
          className="pointer-events-none absolute inset-0 -z-10 h-full w-full object-cover"
          src="/stars.mp4"
          autoPlay
          loop
          muted
          playsInline
        />
      )}

      <header className="w-full max-w-3xl mb-8 flex items-center justify-between">
        <h1 className="text-4xl font-extrabold text-white">
          <Link href="/">ChaptGen</Link>
        </h1>
        <nav className="flex items-center gap-3">
          {me.ok && (
            <Link className="text-sm underline" href="/gallery">
              Gallery
            </Link>
          )}
          {me.ok ? (
            <>
              <span className="text-sm text-gray-200 md:text-gray-600">{me.email}</span>
              <button
                className="text-sm underline"
                onClick={handleLogout}
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link className="text-xl font-semibold text-white" href="/login">
                Sign in
              </Link>
              <Link className="text-xl font-semibold text-white ml-4" href="/signup">
                Sign up
              </Link>
            </>
          )}
        </nav>
      </header>

      <section className="w-full max-w-3xl">
        {}
        {!me.ok ? (
          <div className="text-center text-white drop-shadow">
            <div className="flex items-center justify-center min-h-screen text-center">
              <p className="text-white text-6xl md:text-8xl font-bold drop-shadow-2xl leading-tight max-w-4xl -mt-12">
                Optimize your video viewing experience with help of AI
              </p>
            </div>
          </div>
        ) : (
          <>
            <h2 className="text-xl font-semibold mb-3">Enter YouTube URL</h2>
            <div className="flex gap-2">
              <input
                type="text"
                className="border rounded px-3 py-2 flex-1"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
              />
              <button
                className="bg-blue-600 text-white px-4 py-2 rounded disabled:opacity-60"
                onClick={handleGenerate}
                disabled={loading}
              >
                {loading ? 'Generating…' : 'Generate Chapters'}
              </button>
            </div>

            {errorMsg && <p className="text-red-600 text-sm mt-3">{errorMsg}</p>}

            {chapters && (
              <div className="mt-6">
                <h3 className="font-semibold mb-2">Preview (copy & paste)</h3>

                <textarea
                  readOnly
                  className="w-full h-60 border rounded p-3 font-mono text-sm bg-gray-50"
                  value={chaptersText}
                />

                <div className="mt-4 flex items-center gap-3">
                  <button
                    className="bg-green-600 text-white px-4 py-2 rounded disabled:opacity-60"
                    onClick={handleSave}
                    disabled={!chapters.length}
                  >
                    Save Generation
                  </button>
                </div>

                {saved && <div className="mt-3 text-green-600 text-sm">Saved!</div>}
              </div>
            )}
          </>
        )}
      </section>
    </main>
  );
}