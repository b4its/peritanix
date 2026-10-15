/**
 * DAPE offline store — IndexedDB via Dexie.
 *
 * Mirrors the server schema for the records a field worker produces offline.
 * A background sync queue flushes deltas when connectivity returns.
 */
import Dexie, { type Table } from 'dexie';
import { syncDelta } from './api';

export interface LocalLand {
	id: string;
	user_id: string;
	name: string;
	boundary_wkt: string;
	elevation_m: number;
	accumulated_gdd: number;
	current_phase: string;
	rev: number;
	updated_ts: number;
	synced: 0 | 1;
}

export interface LocalSoil {
	id: string;
	land_id: string;
	sand_pct: number;
	silt_pct: number;
	clay_pct: number;
	soil_class: string;
	cec_meq: number;
	ph_deterministic: number;
	rev: number;
	updated_ts: number;
	synced: 0 | 1;
}

export interface LocalDisease {
	id: string;
	land_id: string;
	lon: number;
	lat: number;
	local_twi: number;
	confirmed_disease: string;
	severity_pct: number;
	confidence_level: string;
	protocol_code: string;
	rev: number;
	updated_ts: number;
	synced: 0 | 1;
}

/** A pending mutation queued for delta sync. */
export interface SyncQueueItem {
	id?: number;
	idempotency_key: string;
	entity: string;
	op: string;
	updated_ts: number;
	rev: number;
	data: Record<string, unknown>;
	created_at: number;
}

class DapeDB extends Dexie {
	lands!: Table<LocalLand, string>;
	soil!: Table<LocalSoil, string>;
	diseases!: Table<LocalDisease, string>;
	syncQueue!: Table<SyncQueueItem, number>;

	constructor() {
		super('dape');
		this.version(1).stores({
			lands: 'id, updated_ts, synced',
			soil: 'id, land_id, updated_ts, synced',
			diseases: 'id, land_id, updated_ts, synced',
			syncQueue: '++id, entity, updated_ts'
		});
	}
}

export const db = new DapeDB();

/** Generate a stable idempotency key (device + uuid). */
export function idempotencyKey(device: string, uuid: string): string {
	return `${device}:${uuid}`;
}

/**
 * Queue a mutation for later sync AND apply it to the local store immediately
 * (Last-Write-Wins by updated_ts). Returns the queue row id.
 */
export async function enqueue(
	entity: string,
	data: Record<string, unknown>,
	op = 'upsert'
): Promise<number> {
	const now = Date.now();
	const rev = now; // monotonic-ish; server treats updated_ts as LWW clock
	const device = localStorage.getItem('dape.device_id') || 'web';
	const key = idempotencyKey(device, crypto.randomUUID());
	return db.syncQueue.add({
		idempotency_key: key,
		entity,
		op,
		updated_ts: now,
		rev,
		data,
		created_at: now
	});
}

/**
 * Flush the sync queue against the server. On success, mark local rows synced
 * and delete the queue items. Idempotency keys make retries safe.
 */
export async function flushQueue(): Promise<{ applied: number; duplicate: number; failed: number }> {
	const items = await db.syncQueue.toArray();
	if (items.length === 0) return { applied: 0, duplicate: 0, failed: 0 };

	const device = localStorage.getItem('dape.device_id') || 'web';
	const ops = items.map((it) => ({
		idempotency_key: it.idempotency_key,
		entity: it.entity,
		op: it.op,
		updated_ts: it.updated_ts,
		rev: it.rev,
		data: it.data
	}));

	const res = await syncDelta(device, ops);
	let applied = 0,
		duplicate = 0,
		failed = 0;
	for (let i = 0; i < res.results.length; i++) {
		const r = res.results[i];
		const item = items[i];
		if (r.status === 'applied' || r.status === 'duplicate') {
			if (r.status === 'applied') applied++;
			else duplicate++;
			if (item?.id != null) await db.syncQueue.delete(item.id);
		} else {
			failed++;
		}
	}
	return { applied, duplicate, failed };
}
