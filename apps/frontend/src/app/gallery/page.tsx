'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { apiJson, apiMe, apiLogout } from '@/lib/api';
import { useRouter } from 'next/navigation';

type Row = {
  id: number;
  url: string;
  title?: string | null;
  status: string;
  created_at: string;
  updated_at: string;
};

export default function GalleryPage() {
  const router = useRouter();
  const [me, setMe] = useState<{ ok: boolean; email?: string }>({ ok: false });
  const [items, setItems] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [q, setQ] = useState('');

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
    setErr(null);
    try {
      const data = await apiJson<Row[]>('/transcripts/mine', { method: 'GET' });
      setItems(data);
    } catch (e: any) {
      setErr(e.message || 'Failed to load gallery');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const filtered = q.trim()
    ? items.filter((g) => (g.title || '').toLowerCase().includes(q.toLowerCase()))
    : items;

  return (
    <main className="flex min-h-screen flex-col items-center p-8">
      <header className="w-full max-w-3xl mb-8 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Gallery</h1>
        <nav className="flex items-center gap-3">
          <Link href="/" className="underline text-sm">Generate New</Link>
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
        {items.length > 0 && (
          <div className="mb-4">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search by title…"
              className="border rounded px-3 py-2 w-full"
            />
          </div>
        )}

        {loading && <p>Loading…</p>}
        {err && <p className="text-red-600">{err}</p>}

        {!loading && !err && (
          <ul className="space-y-3">
            {filtered.map((g) => {
              const title =
                (g.title && g.title.trim().length > 0) ? g.title : `Generation #${g.id}`;
              return (
                <li key={g.id}>
                  <Link
                    href={`/gallery/${g.id}`}
                    className="block border rounded p-3 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <div className="font-medium">{title}</div>
                    <div className="text-xs text-gray-500 mt-1 break-all">
                      {g.url}
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                      {new Date(g.created_at).toLocaleString()}
                    </div>
                  </Link>
                </li>
              );
            })}
            {filtered.length === 0 && items.length > 0 && (
              <p className="text-gray-600">No results.</p>
            )}
            {items.length === 0 && <p className="text-gray-600">No saved generations yet.</p>}
          </ul>
        )}
      </section>
    </main>
  );
}