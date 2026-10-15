/**
 * Tests for the disease-heatmap / wind-vector helpers (pure functions).
 */
import { describe, it, expect } from 'vitest';
import { diseaseHeatPoints } from '../src/lib/heatmap';

describe('diseaseHeatPoints', () => {
	it('maps lat/lon to heat points', () => {
		const pts = diseaseHeatPoints([{ lat: -6.9, lon: 107.6, severity_pct: 15 }]);
		expect(pts).toHaveLength(1);
		expect(pts[0].lat).toBe(-6.9);
		expect(pts[0].lng).toBe(107.6);
	});

	it('weight scales with severity and is clamped', () => {
		const low = diseaseHeatPoints([{ lat: 0, lon: 0, severity_pct: 3 }]);
		const high = diseaseHeatPoints([{ lat: 0, lon: 0, severity_pct: 90 }]);
		expect(high[0].weight).toBeGreaterThan(low[0].weight);
		expect(high[0].weight).toBeLessThanOrEqual(1);
		expect(low[0].weight).toBeGreaterThanOrEqual(0.2);
	});

	it('drops points without coordinates', () => {
		const pts = diseaseHeatPoints([
			{ lat: 0, lon: 0, severity_pct: 10 },
			{ lat: undefined as any, lon: 1, severity_pct: 10 }
		]);
		expect(pts).toHaveLength(1);
	});

	it('defaults missing severity to a modest weight', () => {
		const pts = diseaseHeatPoints([{ lat: 0, lon: 0 }]);
		expect(pts[0].weight).toBeGreaterThan(0);
	});

	it('handles empty input', () => {
		expect(diseaseHeatPoints([])).toEqual([]);
	});
});

import { fertilityColor, fertilityLayer } from '../src/lib/heatmap';

describe('fertilityColor', () => {
	it('optimal is green', () => {
		expect(fertilityColor('optimal')).toBe('#16a34a');
	});
	it('acid is amber', () => {
		expect(fertilityColor('acid')).toBe('#d97706');
	});
	it('alkaline is blue', () => {
		expect(fertilityColor('alkaline')).toBe('#2563eb');
	});
	it('unknown falls back to grey', () => {
		expect(fertilityColor('weird')).toBe('#9ca3af');
	});
});

describe('fertilityLayer', () => {
	it('handles null FeatureCollection', () => {
		const fakeL = { layerGroup: () => ({ addLayer: () => {} }) };
		const layer = fertilityLayer(fakeL, null);
		expect(layer).toBeDefined();
	});
	it('handles missing features array', () => {
		const fakeL = { layerGroup: () => ({ addLayer: () => {} }) };
		expect(fertilityLayer(fakeL, {})).toBeDefined();
	});
});
