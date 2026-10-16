// =============================================================================
// DAPE — k6 load test
// -----------------------------------------------------------------------------
// Exercises the hot read paths and a few light compute paths at a modest load
// so a small VM is not overwhelmed. Run:
//   k6 run scripts/loadtest.js
//   API=http://localhost:58139 k6 run scripts/loadtest.js
//
// Install k6: https://k6.io/docs/get-started/installation/
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';

const API = __ENV.API || 'http://localhost:58139';

export const options = {
	scenarios: {
		reads: {
			executor: 'constant-vus',
			vus: 10,
			duration: '20s',
			exec: 'reads'
		},
		compute: {
			executor: 'constant-vus',
			vus: 3,
			duration: '20s',
			startTime: '20s',
			exec: 'compute'
		}
	},
	thresholds: {
		http_req_failed: ['rate<0.05'],
		http_req_duration: ['p(95)<800']
	}
};

export function reads() {
	const h = http.get(`${API}/api/v1/health`);
	check(h, { 'health 200': (r) => r.status === 200 });

	const l = http.get(`${API}/api/v1/lands`);
	check(l, { 'lands 200': (r) => r.status === 200 });

	const g = http.get(`${API}/api/v1/lands/geojson`);
	check(g, { 'geojson 200': (r) => r.status === 200 });

	const f = http.get(`${API}/api/v1/soil/fertility/geojson`);
	check(f, { 'fertility 200': (r) => r.status === 200 });

	sleep(0.3);
}

export function compute() {
	// Deterministic physics compute (calorimetry) — fast, no ML round-trip.
	const c = http.post(
		`${API}/api/v1/postharvest/calorimetry`,
		JSON.stringify({ elevation_m: 1000, well_temp_c: 22 }),
		{ headers: { 'Content-Type': 'application/json' } }
	);
	check(c, { 'calorimetry 200': (r) => r.status === 200 });

	// Harvest decision (pure rule engine).
	const d = http.post(
		`${API}/api/v1/postharvest/decision`,
		JSON.stringify({
			accumulated_gdd: 1150, current_phase: 'HARVEST',
			archimedes_sink_ratio: 0.85, red_pct: 95, defect_pct: 1
		}),
		{ headers: { 'Content-Type': 'application/json' } }
	);
	check(d, { 'decision 200': (r) => r.status === 200 });

	sleep(0.5);
}
