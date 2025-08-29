export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE?.replace(/\/+$/, '') || 'http://localhost:8080';

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
  });

  if (res.ok) {
    return res.json() as Promise<T>;
  }

  
  let detail = '';
  try {
    const cloned = res.clone();
    const j = await cloned.json();
    detail = (j && (j.error || j.message)) ? (j.error || j.message) : JSON.stringify(j);
  } catch {
    try {
      detail = await res.text();
    } catch {}
  }
  throw new Error(`${res.status} ${res.statusText}${detail ? ` ${detail}` : ''}`);
}

export async function apiMe(): Promise<{ok:boolean; id?:number; email?:string}> {
  return apiJson('/auth/me', { method: 'GET' });
}

export async function apiLogout(): Promise<{ok:boolean}> {
  return apiJson('/auth/logout', { method: 'POST' });
}