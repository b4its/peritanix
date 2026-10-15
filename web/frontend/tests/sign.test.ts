/**
 * Tests for canonical JSON — MUST match the Go verifier & Kotlin signer.
 * The float rule is the critical cross-platform contract (docs/PHYSICS.md §8).
 */
import { describe, it, expect } from 'vitest';
import { canonicalJSON } from '../src/lib/sign';

describe('canonicalJSON', () => {
	it('sorts keys deterministically', () => {
		expect(canonicalJSON({ b: 2, a: 1, c: 3 })).toBe('{"a":1.0,"b":2.0,"c":3.0}');
	});

	it('is order-independent', () => {
		const a = canonicalJSON({ z: 1, a: 2 });
		const b = canonicalJSON({ a: 2, z: 1 });
		expect(a).toBe(b);
	});

	it('applies the integral float rule (52 -> 52.0)', () => {
		expect(canonicalJSON({ sterilization_c: 52 })).toBe('{"sterilization_c":52.0}');
	});

	it('keeps non-integral shortest round-trip', () => {
		expect(canonicalJSON({ ratio: 0.82 })).toBe('{"ratio":0.82}');
	});

	it('handles nested objects with sorted keys', () => {
		expect(canonicalJSON({ c: { z: true, y: false }, a: 1 })).toBe(
			'{"a":1.0,"c":{"y":false,"z":true}}'
		);
	});

	it('handles arrays and null', () => {
		expect(canonicalJSON({ arr: [1, 2, null] })).toBe('{"arr":[1.0,2.0,null]}');
	});

	it('escapes strings', () => {
		expect(canonicalJSON({ s: 'a"b\n' })).toBe('{"s":"a\\"b\\n"}');
	});

	it('booleans', () => {
		expect(canonicalJSON({ t: true, f: false })).toBe('{"f":false,"t":true}');
	});
});
