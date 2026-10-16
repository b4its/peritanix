/**
 * Browser physics mirror tests — must match the Python/Kotlin engines.
 */
import { describe, it, expect } from 'vitest';
import {
	boilingPoint,
	hotWaterRatio,
	mixTemperature,
	twi,
	drainageRiskClass,
	requiredBedHeightCm,
	gddDay,
	phaseForGdd,
	archimedesSinkRatio,
	gradeFromSinkRatio,
	hourlyBlastRisk,
	microclimateRisk
} from '../src/lib/physics';

describe('calorimetry', () => {
	it('boiling point at sea level is 100', () => {
		expect(boilingPoint(0)).toBeCloseTo(100, 9);
	});
	it('boiling point at 1000 m is 96.7', () => {
		expect(boilingPoint(1000)).toBeCloseTo(96.7, 9);
	});
	it('ratio hits 52 C via forward model (lowland)', () => {
		const r = hotWaterRatio(100, 27, 52, 9);
		expect(mixTemperature(100, r.vBoilParts, 27, r.vWellParts)).toBeCloseTo(52, 6);
	});
	it('ratio hits 52 C via forward model (highland)', () => {
		const r = hotWaterRatio(1000, 22, 52, 9);
		expect(mixTemperature(1000, r.vBoilParts, 22, r.vWellParts)).toBeCloseTo(52, 6);
	});
	it('unreachable target throws', () => {
		expect(() => hotWaterRatio(100, 60, 52)).toThrow();
	});
});

describe('twi', () => {
	it('known value 45 deg', () => {
		expect(twi(10, Math.PI / 4)).toBeCloseTo(Math.log(10), 9);
	});
	it('flat slope does not explode', () => {
		expect(Number.isFinite(twi(50, 0))).toBe(true);
	});
	it('risk bands', () => {
		expect(drainageRiskClass(11)).toBe('CRITICAL');
		expect(drainageRiskClass(9)).toBe('HIGH');
		expect(drainageRiskClass(7)).toBe('MODERATE');
		expect(drainageRiskClass(4)).toBe('LOW');
	});
	it('bed height prescription', () => {
		expect(requiredBedHeightCm(11)).toBe(50);
		expect(requiredBedHeightCm(4)).toBe(20);
	});
});

describe('gdd', () => {
	it('standard day', () => {
		expect(gddDay(20, 30)).toBeCloseTo(10, 9);
	});
	it('clamped below base', () => {
		expect(gddDay(10, 12)).toBe(0);
	});
	it('phase thresholds', () => {
		expect(phaseForGdd(100)).toBe('VEGETATIVE');
		expect(phaseForGdd(350)).toBe('FLOWERING');
		expect(phaseForGdd(700)).toBe('FRUITING');
		expect(phaseForGdd(1050)).toBe('HARVEST');
	});
});

describe('archimedes', () => {
	it('all sink -> 1', () => {
		expect(archimedesSinkRatio(10, 0, 0)).toBe(1);
	});
	it('neutral half weight', () => {
		expect(archimedesSinkRatio(5, 10, 5)).toBeCloseTo(0.5, 9);
	});
	it('grade boundaries', () => {
		expect(gradeFromSinkRatio(0.85)).toBe('A+');
		expect(gradeFromSinkRatio(0.5)).toBe('B');
	});
});

describe('microclimate EWS mirror', () => {
	it('high risk when humid and warm', () => {
		expect(hourlyBlastRisk(90, 26)).toBeGreaterThan(0.6);
	});
	it('zero risk when dry', () => {
		expect(hourlyBlastRisk(50, 26)).toBe(0);
	});
	it('zero risk when cold', () => {
		expect(hourlyBlastRisk(95, 10)).toBe(0);
	});
	it('humid series triggers high risk + low irrigation', () => {
		const r = microclimateRisk([88, 90, 92, 94, 93, 91], [25, 26, 27, 26, 25, 26]);
		expect(r.peak_risk).toBeGreaterThan(0.5);
		expect(r.irrigation_minutes_per_bed).toBeLessThanOrEqual(10);
	});
	it('dry series low risk + normal irrigation', () => {
		const r = microclimateRisk([50, 48, 52, 49, 51, 50], [30, 31, 32, 30, 31, 32]);
		expect(r.peak_risk).toBeLessThan(0.4);
		expect(r.irrigation_minutes_per_bed).toBe(20);
	});
	it('rejects mismatched series', () => {
		expect(() => microclimateRisk([80, 85, 90], [25, 26])).toThrow();
	});
});

import { prescribeFertilizer } from '../src/lib/physics';

describe('prescribeFertilizer', () => {
	it('acidic pH triggers dolomite', () => {
		const p = prescribeFertilizer(5.0, 15, 20);
		expect(p.dolomite_g_per_m2).toBeGreaterThan(0);
	});
	it('vegetative has higher NPK than flowering', () => {
		const veg = prescribeFertilizer(6.5, 15, 20);
		const flow = prescribeFertilizer(6.5, 15, 45);
		expect(veg.npk_16_16_16_g_per_l).toBeGreaterThan(flow.npk_16_16_16_g_per_l);
	});
	it('flowering adds calcium and forbids N', () => {
		const p = prescribeFertilizer(6.5, 15, 45);
		expect(p.calcium_g_per_l).toBeGreaterThan(0);
		expect(p.recommendation.toLowerCase()).toContain('hindari');
	});
	it('fruiting is KNO3 dominant', () => {
		const p = prescribeFertilizer(6.5, 15, 75);
		expect(p.kno3_g_per_l).toBeGreaterThan(p.npk_16_16_16_g_per_l);
	});
	it('neutral pH -> no dolomite', () => {
		expect(prescribeFertilizer(6.8, 15, 20).dolomite_g_per_m2).toBe(0);
	});
});

import { validateTrace } from '../src/lib/physics';

describe('validateTrace (offline mirror)', () => {
	const mkMoving = () =>
		Array.from({ length: 60 }, (_, i) => ({
			t: (180 * i) / 59,
			gx: 0.05 * Math.sin(i),
			gy: 0.05 * Math.cos(i * 1.3),
			gz: 0.02 * Math.sin(i * 0.7)
		}));
	it('accepts a real immersion trace', () => {
		const r = validateTrace(mkMoving());
		expect(r.valid).toBe(true);
		expect(r.duration_s).toBeGreaterThan(170);
	});
	it('rejects a constant (cheat) trace', () => {
		const consts = Array.from({ length: 60 }, (_, i) => ({ t: (180 * i) / 59, gx: 0, gy: 0, gz: 0 }));
		const r = validateTrace(consts);
		expect(r.valid).toBe(false);
		expect(r.reasons.some((x) => x.includes('konstan'))).toBe(true);
	});
	it('rejects a too-short trace', () => {
		expect(validateTrace(mkMoving().slice(0, 3)).valid).toBe(false);
	});
	it('rejects wrong duration', () => {
		const short = mkMoving().map((s) => ({ ...s, t: s.t * 0.15 }));
		expect(validateTrace(short).valid).toBe(false);
	});
});
