/**
 * Auth store — JWT persistence in localStorage, reactive across the app.
 * The token is attached to API calls by the api client when present.
 */
import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export interface AuthUser {
	id: string;
	name: string;
	role: string;
}

function createAuth() {
	const token0 = browser ? localStorage.getItem('dape.token') : null;
	const user0 = browser ? JSON.parse(localStorage.getItem('dape.user') || 'null') : null;
	const { subscribe, set } = writable<{ token: string | null; user: AuthUser | null }>({
		token: token0,
		user: user0
	});

	return {
		subscribe,
		setSession(token: string, user: AuthUser) {
			if (browser) {
				localStorage.setItem('dape.token', token);
				localStorage.setItem('dape.user', JSON.stringify(user));
			}
			set({ token, user });
		},
		clear() {
			if (browser) {
				localStorage.removeItem('dape.token');
				localStorage.removeItem('dape.user');
			}
			set({ token: null, user: null });
		}
	};
}

export const auth = createAuth();

/** True when a session exists. */
export function isLoggedIn(): boolean {
	if (!browser) return false;
	return !!localStorage.getItem('dape.token');
}
