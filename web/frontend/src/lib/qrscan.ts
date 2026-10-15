/**
 * QR scanner for the public verifier — uses the native BarcodeDetector API when
 * available (Chrome/Edge/Android), with a graceful fallback to manual paste.
 *
 * Dependency-free: no qr-scanner library needed. Requires a secure context
 * (HTTPS or localhost) for camera access — this is why the production ingress
 * terminates TLS (see k8s/base/30-ingress-hpa.yaml).
 */

export interface ScanResult {
	text: string;
}

/** True when the browser exposes the BarcodeDetector API. */
export function barcodeDetectorSupported(): boolean {
	return typeof (globalThis as any).BarcodeDetector !== 'undefined';
}

/**
 * Start scanning with the device camera. Calls onResult once with the decoded
 * text, then stops. Returns a stop() function. Throws on permission/unsupported.
 */
export async function startQrScan(
	videoEl: HTMLVideoElement,
	onResult: (text: string) => void
): Promise<() => void> {
	if (!barcodeDetectorSupported()) {
		throw new Error('BarcodeDetector tidak didukung browser ini.');
	}
	const Detector = (globalThis as any).BarcodeDetector;
	const detector = new Detector({ formats: ['qr_code'] });

	const stream = await navigator.mediaDevices.getUserMedia({
		video: { facingMode: 'environment' }
	});
	videoEl.srcObject = stream;
	videoEl.setAttribute('playsinline', 'true');
	await videoEl.play();

	let raf = 0;
	let stopped = false;

	const tick = async () => {
		if (stopped) return;
		try {
			const codes = await detector.detect(videoEl);
			if (codes && codes.length > 0) {
				onResult(codes[0].rawValue as string);
				stop();
				return;
			}
		} catch {
			/* transient decode errors — keep scanning */
		}
		raf = requestAnimationFrame(tick);
	};
	raf = requestAnimationFrame(tick);

	function stop() {
		stopped = true;
		cancelAnimationFrame(raf);
		(stream.getTracks() || []).forEach((t: MediaStreamTrack) => t.stop());
		if (videoEl.srcObject) videoEl.srcObject = null;
	}

	return stop;
}
