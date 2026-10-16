/**
 * Tests for the TTS dosing instruction builder (pure, SSR-safe).
 */
import { describe, it, expect } from 'vitest';
import { dosingSpeech, ttsSupported } from '../src/lib/tts';

describe('dosingSpeech', () => {
	it('mentions both water volumes and the countdown', () => {
		const s = dosingSpeech(6.0, 9, 180);
		expect(s).toContain('6.0 gayung air mendidih');
		expect(s).toContain('9 gayung air sumur');
		expect(s).toContain('180 detik');
	});

	it('formats the boiling part to one decimal', () => {
		expect(dosingSpeech(5.04, 9, 180)).toContain('5.0 gayung');
	});

	it('is deterministic', () => {
		expect(dosingSpeech(4.69, 9, 180)).toBe(dosingSpeech(4.69, 9, 180));
	});
});

describe('ttsSupported', () => {
	it('returns a boolean (false in the test env without speechSynthesis)', () => {
		expect(typeof ttsSupported()).toBe('boolean');
	});
});
