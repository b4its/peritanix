/// <reference types="@sveltejs/kit" />
/// <reference lib="webworker" />

/**
 * DAPE Service Worker — offline-first asset caching.
 *
 * Strategy:
 *  - Precache the app shell on install.
 *  - Cache-first for static assets; network-first with cache fallback for
 *    navigation so the PWA opens without connectivity.
 *  - API calls (PUBLIC_API_BASE_URL) are NEVER cached: the app reasons on
 *    IndexedDB + local physics when offline, and syncs when back online.
 */
import { build, files, version } from '$service-worker';

const CACHE = `dape-${version}`;
const ASSETS = [...build, ...files];

self.addEventListener('install', (event: ExtendableEvent) => {
	event.waitUntil(
		(async () => {
			const cache = await caches.open(CACHE);
			await cache.addAll(ASSETS);
			await (self as any).skipWaiting();
		})()
	);
});

self.addEventListener('activate', (event: ExtendableEvent) => {
	event.waitUntil(
		(async () => {
			for (const key of await caches.keys()) {
				if (key !== CACHE) await caches.delete(key);
			}
			await (self as any).clients.claim();
		})()
	);
});

self.addEventListener('fetch', (event: FetchEvent) => {
	const url = new URL(event.request.url);

	// Never intercept API or cross-origin requests.
	if (url.origin !== location.origin) return;

	if (event.request.method !== 'GET') return;

	// Navigation requests: network-first, fall back to cached shell.
	if (event.request.mode === 'navigate') {
		event.respondWith(
			(async () => {
				try {
					return await fetch(event.request);
				} catch {
					const cache = await caches.open(CACHE);
					const cached = await cache.match('/');
					return cached ?? Response.error();
				}
			})()
		);
		return;
	}

	// Static assets: cache-first.
	if (ASSETS.includes(url.pathname)) {
		event.respondWith(
			(async () => {
				const cache = await caches.open(CACHE);
				const cached = await cache.match(url.pathname);
				return cached ?? fetch(event.request);
			})()
		);
	}
});
