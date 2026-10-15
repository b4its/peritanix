/**
 * Canonical JSON + Ed25519 signing for the browser (WebCrypto).
 *
 * The float rule MUST match the Go verifier and the Kotlin signer byte-for-byte
 * (see docs/PHYSICS.md §8): integral values keep a single decimal place
 * (52 -> "52.0"), non-integral values use the shortest round-trip form.
 * If this drifts, certificates signed in the browser fail server verification.
 */

/** Deterministic JSON with sorted keys and the shared float rule. */
export function canonicalJSON(value: unknown): string {
	return encode(value);
}

function encode(v: unknown): string {
	if (v === null || v === undefined) return 'null';
	if (typeof v === 'boolean') return v ? 'true' : 'false';
	if (typeof v === 'number') return formatNumber(v);
	if (typeof v === 'string') return quote(v);
	if (Array.isArray(v)) return '[' + v.map(encode).join(',') + ']';
	if (typeof v === 'object') {
		const obj = v as Record<string, unknown>;
		const keys = Object.keys(obj).sort();
		return '{' + keys.map((k) => quote(k) + ':' + encode(obj[k])).join(',') + '}';
	}
	return quote(String(v));
}

/** Integral -> "x.0"; non-integral -> shortest round-trip (JS default). */
function formatNumber(n: number): string {
	if (Number.isInteger(n) && Number.isFinite(n)) {
		return n.toFixed(1);
	}
	return String(n);
}

function quote(s: string): string {
	let out = '"';
	for (const c of s) {
		switch (c) {
			case '"': out += '\\"'; break;
			case '\\': out += '\\\\'; break;
			case '\n': out += '\\n'; break;
			case '\r': out += '\\r'; break;
			case '\t': out += '\\t'; break;
			default:
				out += c < ' ' ? '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0') : c;
		}
	}
	return out + '"';
}

export interface SignedCertificate {
	payload: Record<string, unknown>;
	signature: string; // base64
	public_key: string; // base64
}

/** base64 (standard) of a Uint8Array. */
function b64(bytes: Uint8Array): string {
	let bin = '';
	for (const b of bytes) bin += String.fromCharCode(b);
	return btoa(bin);
}

/** Generate an Ed25519 keypair in the browser. */
export async function generateKeyPair(): Promise<CryptoKeyPair> {
	return crypto.subtle.generateKey({ name: 'Ed25519' } as any, true, [
		'sign',
		'verify'
	]) as Promise<CryptoKeyPair>;
}

/** Sign a payload with an Ed25519 private key; returns the certificate envelope. */
export async function signCertificate(
	payload: Record<string, unknown>,
	keyPair: CryptoKeyPair
): Promise<SignedCertificate> {
	const msg = new TextEncoder().encode(canonicalJSON(payload));
	const sig = await crypto.subtle.sign({ name: 'Ed25519' } as any, keyPair.privateKey, msg);
	const rawPub = await crypto.subtle.exportKey('raw', keyPair.publicKey);
	return {
		payload,
		signature: b64(new Uint8Array(sig)),
		public_key: b64(new Uint8Array(rawPub))
	};
}

/** Verify a certificate locally (optional sanity check before printing). */
export async function verifyCertificate(
	cert: SignedCertificate,
	publicKey: CryptoKey
): Promise<boolean> {
	const msg = new TextEncoder().encode(canonicalJSON(cert.payload));
	const sig = Uint8Array.from(atob(cert.signature), (c) => c.charCodeAt(0));
	return crypto.subtle.verify({ name: 'Ed25519' } as any, publicKey, sig, msg);
}
