'use client';

import { useState } from 'react';

export default function Home() {
  const [url, setUrl] = useState('');

  async function handleGenerate() {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}/transcripts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ url }),
      });

      if (!res.ok) {
        console.error('Backend error', await res.text());
        return;
      }

      const data = await res.json();
      console.log('Enqueued OK:', data);
    } catch (e) {
      console.error('Network error:', e);
    }
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-start p-8">
      <h1 className="text-2xl font-bold mb-4">Enter YouTube URL</h1>

      <input
        type="text"
        placeholder="Paste YouTube link here"
        className="border rounded p-2 w-full max-w-md mb-4"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
      />

      <button
        className="bg-blue-600 text-white px-4 py-2 rounded"
        onClick={handleGenerate}
      >
        Generate Chapters
      </button>

      <div className="mt-8 w-full max-w-md">
        {/* Chapters */}
      </div>
    </main>
  );
}