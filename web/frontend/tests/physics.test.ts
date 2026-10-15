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
	gradeFromSinkRatio
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
