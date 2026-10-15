/**
 * Tests for the QR scanner capability detection (pure, SSR-safe).
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { barcodeDetectorSupported } from '../src/lib/qrscan';

describe('barcodeDetectorSupported', () => {
	afterEach(() => {
		delete (globalThis as any).BarcodeDetector;
	});

	it('false when API absent', () => {
		expect(barcodeDetectorSupported()).toBe(false);
	});

	it('true when API present', () => {
		(globalThis as any).BarcodeDetector = class {};
		expect(barcodeDetectorSupported()).toBe(true);
	});
});
