import { API_BASE } from '@/lib/api';

export async function me() {
  const res = await fetch(`${API_BASE}/auth/me`, { credentials: 'include' });
  return res.json() as Promise<{ ok: boolean; id?: number; email?: string }>;
}

export async function login(email: string, password: string) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });
  return res.json() as Promise<{ ok: boolean; id?: number; email?: string; error?: string }>;
}

export async function signup(email: string, password: string) {
  const res = await fetch(`${API_BASE}/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });
  return res.json() as Promise<{ ok: boolean; id?: number; email?: string; error?: string }>;
}