'use client';

import { useEffect, useState } from 'react';
import { me, signup } from '@/lib/auth';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    me().then((m) => { if (m.ok) router.replace('/'); });
  }, [router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    const r = await signup(email, password);
    setLoading(false);
    if (!r.ok) return setErr(r.error || 'Signup failed.');
    router.replace('/');
  }

  return (
    <main className="flex min-h-screen flex-col items-center p-8">
      <header className="w-full max-w-md mb-8 flex items-center justify-between">
        <Link href="/" className="text-2xl font-bold">ChaptGen</Link>
        <Link className="text-sm underline" href="/login">Sign in</Link>
      </header>

      <section className="w-full max-w-md">
        <h1 className="text-xl font-semibold mb-4">Create your account</h1>
        <form onSubmit={onSubmit} className="space-y-3">
          <input className="border rounded px-3 py-2 w-full" type="email" placeholder="Email"
                 value={email} onChange={e=>setEmail(e.target.value)} required />
          <input className="border rounded px-3 py-2 w-full" type="password" placeholder="Password"
                 value={password} onChange={e=>setPassword(e.target.value)} required minLength={6} />
          {err && <p className="text-red-600 text-sm">{err}</p>}
          <button className="bg-blue-600 text-white px-4 py-2 rounded disabled:opacity-60" disabled={loading}>
            {loading ? 'Creating…' : 'Sign up'}
          </button>
        </form>
      </section>
    </main>
  );
}