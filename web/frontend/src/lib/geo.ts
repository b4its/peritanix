/**
 * Geo helpers for the DAPE web UI — pure, testable.
 */

/** A geographic vertex as [lat, lng]. */
export type Vertex = [number, number];

/**
 * Build a closed WKT POLYGON from [lat, lng] vertices.
 * WKT uses "lon lat" order for EPSG:4326, and the ring must be closed
 * (first point repeated at the end).
 */
export function toPolygonWKT(vertices: Vertex[]): string {
	if (vertices.length < 3) {
		throw new Error('Butuh minimal 3 titik untuk membentuk poligon.');
	}
	const ring = vertices.map(([lat, lng]) => `${lng} ${lat}`);
	ring.push(ring[0]);
	return `POLYGON((${ring.join(',')}))`;
}

/** Approximate polygon area (m^2) via the shoelace formula on a local plane. */
export function polygonAreaM2(vertices: Vertex[]): number {
	if (vertices.length < 3) return 0;
	// Convert to metres using the centroid latitude for the lon scale.
	const lat0 = vertices.reduce((s, v) => s + v[0], 0) / vertices.length;
	const mPerDegLat = 111320;
	const mPerDegLon = 111320 * Math.cos((lat0 * Math.PI) / 180);
	let area = 0;
	for (let i = 0; i < vertices.length; i++) {
		const [lat1, lon1] = vertices[i];
		const [lat2, lon2] = vertices[(i + 1) % vertices.length];
		const x1 = lon1 * mPerDegLon;
		const y1 = lat1 * mPerDegLat;
		const x2 = lon2 * mPerDegLon;
		const y2 = lat2 * mPerDegLat;
		area += x1 * y2 - x2 * y1;
	}
	return Math.abs(area / 2);
}
