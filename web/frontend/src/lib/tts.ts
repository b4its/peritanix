/**
 * TTS helper for field instructions (TAHAP 5.1 / FR-4.1).
 *
 * Reads Indonesian dosing instructions aloud (a farmer's hands are busy mixing
 * water). Wrapped so SSR and environments without speechSynthesis degrade
 * gracefully, and so the utterance text is a pure function that can be tested.
 */

/** Build the spoken instruction for the 52 °C hot-water mix. */
export function dosingSpeech(vBoilParts: number, vWellParts: number, countdownS: number): string {
	const boil = vBoilParts.toFixed(1);
	return (
		`Campurkan ${boil} gayung air mendidih dengan ${vWellParts} gayung air sumur. ` +
		`Rendam cabai selama ${countdownS} detik. Tunggu hitungan waktu mundur.`
	);
}

export function ttsSupported(): boolean {
	return typeof window !== 'undefined' && 'speechSynthesis' in window;
}

/** Speak Indonesian text. Returns false if unavailable. */
export function speak(text: string, lang = 'id-ID'): boolean {
	if (!ttsSupported()) return false;
	try {
		const u = new SpeechSynthesisUtterance(text);
		u.lang = lang;
		u.rate = 0.95;
		window.speechSynthesis.cancel();
		window.speechSynthesis.speak(u);
		return true;
	} catch {
		return false;
	}
}

/** Stop any in-progress speech. */
export function stopSpeaking(): void {
	if (ttsSupported()) window.speechSynthesis.cancel();
}
