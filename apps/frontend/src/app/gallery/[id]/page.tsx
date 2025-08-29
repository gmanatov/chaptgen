'use client';

import { useEffect, useState } from 'react';
import { apiJson, apiMe, apiLogout } from '@/lib/api';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

type Chapter = { start: string; title: string };

type Generation = {
  id: number;
  url: string;
  title?: string | null;
  status: string;
  created_at: string;
  updated_at: string;
  chapters_json?: Chapter[] | null;
  chapters_text?: string | null;
  transcript?: string | null;
  model?: string | null;
  error?: string | null;
};

export default function GenerationDetail({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const router = useRouter();

  const [me, setMe] = useState<{ ok: boolean; email?: string }>({ ok: false });
  const [data, setData] = useState<Generation | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);
  const [text, setText] = useState<string>('');

  useEffect(() => {
    apiMe().then(setMe).catch(() => setMe({ ok: false }));
  }, []);

  async function handleLogout() {
    try {
      await apiLogout();
      setMe({ ok: false });
      router.push('/');
    } catch {}
  }

  async function load() {
    setLoading(true);
    setErrorMsg(null);
    setSavedMsg(null);
    try {
      const d = await apiJson<Generation>(`/transcripts/${id}`, { method: 'GET' });
      setData(d);
      const block = deriveText(d.chapters_json, d.chapters_text);
      setText(block);
    } catch (e: any) {
      setErrorMsg(e.message || 'Failed to load item.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [id]);

  async function handleDelete() {
    if (!confirm('Delete this generation?')) return;
    setDeleting(true);
    setErrorMsg(null);
    try {
      await apiJson(`/transcripts/${id}`, { method: 'DELETE' });
      router.push('/gallery');
    } catch (e: any) {
      setErrorMsg(e.message || 'Delete failed.');
    } finally {
      setDeleting(false);
    }
  }

  async function handleSave() {
    setSaving(true);
    setErrorMsg(null);
    setSavedMsg(null);
    try {
      const resp = await apiJson<{ ok: boolean; updated: boolean }>(`/transcripts/${id}`, {
        method: 'PUT',
        body: JSON.stringify({
          chaptersText: text,
        }),
      });
      if (!resp.ok) {
        setErrorMsg('Save failed.');
        return;
      }
      setSavedMsg('Saved!');
    } catch (e: any) {
      setErrorMsg(e.message || 'Save failed.');
    } finally {
      setSaving(false);
    }
  }

  function deriveText(chapters?: Chapter[] | null, provided?: string | null) {
    if (provided && provided.trim().length) return provided;
    if (Array.isArray(chapters) && chapters.length) {
      return chapters.map((c) => `${c.start} ${c.title}`).join('\n');
    }
    return '';
  }

  const heading =
    data?.title && data.title.trim().length > 0 ? data.title : `Generation #${id}`;

  return (
    <main className="flex min-h-screen flex-col items-center p-8">
      <header className="w-full max-w-3xl mb-8 flex items-center justify-between">
        <nav className="flex items-center gap-3">
          <Link className="text-sm underline" href="/gallery">
            Back to Gallery
          </Link>
          <Link className="text-sm underline" href="/">
            Generate New
          </Link>
          {me.ok ? (
            <>
              <span className="text-sm text-gray-600">{me.email}</span>
              <button className="text-sm underline" onClick={handleLogout}>
                Logout
              </button>
            </>
          ) : (
            <>
              <Link className="text-sm underline" href="/login">Sign in</Link>
              <Link className="text-sm underline" href="/signup">Sign up</Link>
            </>
          )}
        </nav>
      </header>

      <section className="w-full max-w-3xl">
        {loading && <p>Loading…</p>}
        {errorMsg && <p className="text-red-600">{errorMsg}</p>}

        {!loading && data && (
          <div className="space-y-4">
            <div className="border rounded p-4">
              {data.url.includes("youtube.com") || data.url.includes("youtu.be") ? (
                <div className="relative w-full pb-[56.25%]">
                  <iframe
                    className="absolute top-0 left-0 w-full h-full rounded"
                    src={data.url.replace("watch?v=", "embed/")}
                    title="YouTube video player"
                    frameBorder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowFullScreen
                  ></iframe>
                </div>
              ) : (
                <a
                  href={data.url}
                  target="_blank"
                  rel="noreferrer"
                  className="underline break-all"
                >
                  {data.url}
                </a>
              )}

              <div className="mt-2 text-sm text-gray-500">
                Created: {new Date(data.created_at).toLocaleString()}
              </div>
            </div>

            <div>
              <h2 className="font-semibold mb-2">Chapters (editable)</h2>
              <textarea
                className="w-full h-60 border rounded p-3 font-mono text-sm"
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="00:00 Intro&#10;00:45 Main idea&#10;02:10 Deep dive"
              />
              <div className="mt-3 flex items-center gap-3">
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="bg-green-600 text-white px-4 py-2 rounded disabled:opacity-60"
                >
                  {saving ? 'Saving…' : 'Save Changes'}
                </button>
                {savedMsg && <span className="text-green-600 text-sm">{savedMsg}</span>}
              </div>
            </div>

            <div className="pt-2">
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="bg-red-600 text-white px-4 py-2 rounded disabled:opacity-60"
              >
                {deleting ? 'Deleting…' : 'Delete Generated Chapters'}
              </button>
            </div>
          </div>
        )}
      </section>
    </main>
  );
}