/**
 * Minimal heatmap + wind-vector overlay for Leaflet, dependency-free.
 *
 * The design doc asks for a spatiotemporal disease heatmap with wind-vs-drainage
 * direction indicators. Rather than pulling in leaflet.heat (extra dep, brittle
 * with SvelteKit SSR), we render a simple intensity grid + wind arrows onto an
 * L.Layer via a canvas. Deterministic and tiny.
 */

/** A weight point to be drawn on the heatmap. */
export interface HeatPoint {
	lat: number;
	lng: number;
	weight: number; // 0..1
}

/** One wind observation. */
export interface WindVector {
	lat: number;
	lng: number;
	direction_deg: number; // meteorological: direction the wind blows FROM
	speed: number; // relative
}

/**
 * Build a canvas overlay of weighted dots (a cheap "heatmap").
 * Points with higher weight are drawn larger and more opaque.
 */
export function heatCanvasLayer(
	L: any,
	points: HeatPoint[],
	opts: { radius?: number; color?: string } = {}
): any {
	const radius = opts.radius ?? 18;
	const color = opts.color ?? 'rgba(220,38,38,0.6)';

	const Layer = L.Layer.extend({
		onAdd(map: any) {
			this._map = map;
			const canvas = L.DomUtil.create('canvas', 'leaflet-heat-layer');
			const size = map.getSize();
			canvas.width = size.x;
			canvas.height = size.y;
			const ctx = canvas.getContext('2d');
			const origin = map.containerPointToLayerPoint([0, 0]);
			L.DomUtil.setPosition(canvas, origin);
			this._canvas = canvas;
			this._ctx = ctx;
			map.getPanes().overlayPane.appendChild(canvas);
			this._redraw();
			map.on('moveend zoomend', this._redraw, this);
		},
		onRemove(map: any) {
			map.getPanes().overlayPane.removeChild(this._canvas);
			map.off('moveend zoomend', this._redraw, this);
		},
		_redraw() {
			const map = this._map;
			const size = map.getSize();
			this._canvas.width = size.x;
			this._canvas.height = size.y;
			const ctx = this._ctx;
			ctx.clearRect(0, 0, size.x, size.y);
			for (const p of points) {
				const pt = map.latLngToContainerPoint([p.lat, p.lng]);
				const r = radius * (0.4 + 0.6 * Math.max(0, Math.min(1, p.weight)));
				const g = ctx.createRadialGradient(pt.x, pt.y, 0, pt.x, pt.y, r);
				g.addColorStop(0, color);
				g.addColorStop(1, 'rgba(220,38,38,0)');
				ctx.fillStyle = g;
				ctx.beginPath();
				ctx.arc(pt.x, pt.y, r, 0, Math.PI * 2);
				ctx.fill();
			}
		}
	});
	return new Layer();
}

/**
 * Build an overlay of wind-direction arrows. `direction_deg` is the direction
 * the wind blows FROM (meteorological); the arrow points where it blows TO.
 */
export function windVectorLayer(
	L: any,
	vectors: WindVector[],
	opts: { lengthPx?: number; color?: string } = {}
): any {
	const lengthPx = opts.lengthPx ?? 22;
	const color = opts.color ?? '#1d4ed8';

	const Layer = L.Layer.extend({
		onAdd(map: any) {
			this._map = map;
			const canvas = L.DomUtil.create('canvas', 'leaflet-wind-layer');
			const origin = map.containerPointToLayerPoint([0, 0]);
			L.DomUtil.setPosition(canvas, origin);
			this._canvas = canvas;
			this._ctx = canvas.getContext('2d');
			map.getPanes().overlayPane.appendChild(canvas);
			this._redraw();
			map.on('moveend zoomend', this._redraw, this);
		},
		onRemove(map: any) {
			map.getPanes().overlayPane.removeChild(this._canvas);
			map.off('moveend zoomend', this._redraw, this);
		},
		_redraw() {
			const map = this._map;
			const size = map.getSize();
			this._canvas.width = size.x;
			this._canvas.height = size.y;
			const ctx = this._ctx;
			ctx.clearRect(0, 0, size.x, size.y);
			ctx.strokeStyle = color;
			ctx.fillStyle = color;
			ctx.lineWidth = 2;
			for (const v of vectors) {
				const pt = map.latLngToContainerPoint([v.lat, v.lng]);
				// Blowing TO = FROM + 180. Convert compass (N=0, cw) to canvas (0=+x).
				const toRad = ((v.direction_deg + 180) % 360) * (Math.PI / 180);
				const dx = Math.sin(toRad) * lengthPx;
				const dy = -Math.cos(toRad) * lengthPx;
				ctx.beginPath();
				ctx.moveTo(pt.x - dx / 2, pt.y - dy / 2);
				ctx.lineTo(pt.x + dx / 2, pt.y + dy / 2);
				ctx.stroke();
				// arrowhead
				const ang = Math.atan2(dy, dx);
				ctx.beginPath();
				ctx.moveTo(pt.x + dx / 2, pt.y + dy / 2);
				ctx.lineTo(
					pt.x + dx / 2 - 7 * Math.cos(ang - 0.4),
					pt.y + dy / 2 - 7 * Math.sin(ang - 0.4)
				);
				ctx.lineTo(
					pt.x + dx / 2 - 7 * Math.cos(ang + 0.4),
					pt.y + dy / 2 - 7 * Math.sin(ang + 0.4)
				);
				ctx.closePath();
				ctx.fill();
			}
		}
	});
	return new Layer();
}

/** Convert a disease-log list into weighted heat points. */
export function diseaseHeatPoints(
	diseases: { lat: number; lon: number; severity_pct?: number }[]
): HeatPoint[] {
	return diseases
		.filter((d) => typeof d.lat === 'number' && typeof d.lon === 'number')
		.map((d) => ({
			lat: d.lat,
			lng: d.lon,
			weight: Math.min(1, Math.max(0.2, (d.severity_pct ?? 10) / 30))
		}));
}
