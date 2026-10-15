/**
 * Tests for the on-device vision helpers (pure likelihood mapping).
 */
import { describe, it, expect } from 'vitest';
import { visualLikelihoods, type LeafAnalysis } from '../src/lib/vision';

function mk(partial: Partial<LeafAnalysis>): LeafAnalysis {
	return {
		severity_pct: 0,
		healthy_pct: 100,
		mean_rgb: [100, 150, 80],
		dominant_hue: 'green',
		attenuation_proxy_pct: 0,
		...partial
	};
}

describe('visualLikelihoods', () => {
	it('returns a normalised distribution summing to ~1', () => {
		const lik = visualLikelihoods(mk({}));
		const sum = Object.values(lik).reduce((s, v) => s + v, 0);
		expect(sum).toBeCloseTo(1, 2);
	});

	it('yellow mosaic favours Begomovirus', () => {
		const lik = visualLikelihoods(mk({ dominant_hue: 'yellow', severity_pct: 20 }));
		expect(lik.Begomovirus).toBeGreaterThan(lik.Keriting_Thrips);
	});

	it('brown lesions favour Antraknosa', () => {
		const lik = visualLikelihoods(mk({ dominant_hue: 'brown', severity_pct: 15 }));
		expect(lik.Antraknosa).toBeGreaterThan(lik.Begomovirus);
	});

	it('high attenuation raises Antraknosa', () => {
		const low = visualLikelihoods(mk({ attenuation_proxy_pct: 1 }));
		const high = visualLikelihoods(mk({ attenuation_proxy_pct: 20 }));
		expect(high.Antraknosa).toBeGreaterThan(low.Antraknosa);
	});

	it('high severity raises vascular diseases', () => {
		const lik = visualLikelihoods(mk({ severity_pct: 40, dominant_hue: 'brown' }));
		expect(lik.Ralstonia + lik.Fusarium).toBeGreaterThan(0.2);
	});

	it('keeps all values in [0,1]', () => {
		const lik = visualLikelihoods(mk({ severity_pct: 60, dominant_hue: 'red', attenuation_proxy_pct: 30 }));
		for (const v of Object.values(lik)) {
			expect(v).toBeGreaterThanOrEqual(0);
			expect(v).toBeLessThanOrEqual(1);
		}
	});
});
