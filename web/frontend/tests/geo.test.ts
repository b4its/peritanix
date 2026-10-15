/**
 * Tests for the WKT / area geo helpers.
 */
import { describe, it, expect } from 'vitest';
import { toPolygonWKT, polygonAreaM2 } from '../src/lib/geo';

describe('toPolygonWKT', () => {
	it('builds a closed ring in lon-lat order', () => {
		const wkt = toPolygonWKT([
			[-6.902, 107.601],
			[-6.902, 107.603],
			[-6.904, 107.603]
		]);
		expect(wkt).toBe('POLYGON((107.601 -6.902,107.603 -6.902,107.603 -6.904,107.601 -6.902))');
	});

	it('closes the ring (first point repeated)', () => {
		const wkt = toPolygonWKT([
			[0, 0],
			[0, 1],
			[1, 1]
		]);
		expect(wkt.startsWith('POLYGON((0 0,')).toBe(true);
		expect(wkt.endsWith('0 0))')).toBe(true);
	});

	it('throws on fewer than 3 vertices', () => {
		expect(() => toPolygonWKT([[0, 0], [0, 1]])).toThrow();
		expect(() => toPolygonWKT([])).toThrow();
	});
});

describe('polygonAreaM2', () => {
	it('computes a plausible area for a small plot', () => {
		// ~0.002 deg lat x 0.002 deg lon near the equator ~ 222m x 222m ~ 49k m^2
		const area = polygonAreaM2([
			[0, 0],
			[0, 0.002],
			[0.002, 0.002],
			[0.002, 0]
		]);
		expect(area).toBeGreaterThan(44000);
		expect(area).toBeLessThan(54000);
	});

	it('is zero for degenerate input', () => {
		expect(polygonAreaM2([[0, 0], [0, 1]])).toBe(0);
	});
});
