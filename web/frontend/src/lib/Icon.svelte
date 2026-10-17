<script lang="ts">
	/**
	 * Minimalist inline-SVG icon set (replaces all emoji).
	 *
	 * Stroke-based, inherits `currentColor`, sized via the `size` prop. No icon
	 * dependency — keeps the PWA offline-first with zero extra requests.
	 *
	 * Usage: <Icon name="chili" /> <Icon name="check" size={14} />
	 */
	type IconName =
		| 'chili' | 'check' | 'check-circle' | 'warning' | 'cancel' | 'refresh'
		| 'camera' | 'save' | 'trash' | 'leaf' | 'lock' | 'unlock' | 'search'
		| 'volume' | 'vibrate' | 'pin' | 'money' | 'eye' | 'upload' | 'back'
		| 'beaker' | 'compass' | 'brain' | 'pulse' | 'sync' | 'plus' | 'minus'
		| 'dot' | 'pill' | 'microscope' | 'database' | 'chevron-right' | 'info';

	let { name, size = 18, stroke = 2, class: cls = '' }: {
		name: IconName;
		size?: number;
		stroke?: number;
		class?: string;
	} = $props();
</script>

<svg
	class="icon {cls}"
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="none"
	stroke="currentColor"
	stroke-width={stroke}
	stroke-linecap="round"
	stroke-linejoin="round"
	aria-hidden="true"
	focusable="false"
>
	{#if name === 'chili'}
		<path d="M14 4c2.5 0 4 1.8 4 4 0 5.5-4.5 10-10 10-2 0-3.5-.8-4.5-2" />
		<path d="M14 4c0 1.5-.5 2.5-1.5 3.2" />
		<path d="M15.5 3.5c1.4-.6 2.8-.3 3.5.6" />
	{:else if name === 'check'}
		<path d="M20 6 9 17l-5-5" />
	{:else if name === 'check-circle'}
		<circle cx="12" cy="12" r="9" />
		<path d="M8.5 12.5 11 15l4.5-5" />
	{:else if name === 'warning'}
		<path d="M12 4.5 2.8 19a1.2 1.2 0 0 0 1 1.8h16.4a1.2 1.2 0 0 0 1-1.8L12 4.5Z" />
		<path d="M12 10v4" />
		<path d="M12 17.2h.01" />
	{:else if name === 'cancel'}
		<circle cx="12" cy="12" r="9" />
		<path d="m9 9 6 6M15 9l-6 6" />
	{:else if name === 'refresh'}
		<path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
		<path d="M21 3v5h-5" />
		<path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
		<path d="M3 21v-5h5" />
	{:else if name === 'camera'}
		<path d="M4 8h2.5L8 6h8l1.5 2H20a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z" />
		<circle cx="12" cy="13" r="3.2" />
	{:else if name === 'save'}
		<path d="M5 4h11l3 3v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z" />
		<path d="M8 4v5h7V4" />
		<rect x="8" y="13" width="8" height="6" rx="1" />
	{:else if name === 'trash'}
		<path d="M4 7h16" />
		<path d="M9 7V4.5h6V7" />
		<path d="M6.5 7 7.5 20a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1l1-13" />
	{:else if name === 'leaf'}
		<path d="M5 19c8 1 15-4 15-14C10 5 4 10 5 19Z" />
		<path d="M5 19c2.5-4.5 6-7.5 10.5-9" />
	{:else if name === 'lock'}
		<rect x="5" y="10.5" width="14" height="9.5" rx="2" />
		<path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
	{:else if name === 'unlock'}
		<rect x="5" y="10.5" width="14" height="9.5" rx="2" />
		<path d="M8 10.5V8a4 4 0 0 1 7.5-1.8" />
	{:else if name === 'search'}
		<circle cx="11" cy="11" r="6.5" />
		<path d="m16 16 4 4" />
	{:else if name === 'volume'}
		<path d="M4 9.5h3l4.5-3.6v12.2L7 14.5H4Z" />
		<path d="M15.5 8.5a5 5 0 0 1 0 7" />
	{:else if name === 'vibrate'}
		<rect x="8.5" y="3.5" width="7" height="17" rx="1.6" />
		<path d="M4 9v6M20 9v6" />
	{:else if name === 'pin'}
		<path d="M12 21c4.5-5 7-8.2 7-11a7 7 0 1 0-14 0c0 2.8 2.5 6 7 11Z" />
		<circle cx="12" cy="10" r="2.6" />
	{:else if name === 'money'}
		<rect x="3" y="6" width="18" height="12" rx="2" />
		<circle cx="12" cy="12" r="2.6" />
	{:else if name === 'eye'}
		<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
		<circle cx="12" cy="12" r="2.8" />
	{:else if name === 'upload'}
		<path d="M12 16V4" />
		<path d="m7 9 5-5 5 5" />
		<path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
	{:else if name === 'back'}
		<path d="M9 15 4 12l5-3" />
		<path d="M4 12h9a6 6 0 0 1 6 6" />
	{:else if name === 'beaker'}
		<path d="M9 3h6" />
		<path d="M10 3v6.5L5.6 17a2 2 0 0 0 1.7 3h9.4a2 2 0 0 0 1.7-3L14 9.5V3" />
		<path d="M8 15h8" />
	{:else if name === 'compass'}
		<circle cx="12" cy="12" r="9" />
		<path d="m15.5 8.5-2 5-5 2 2-5Z" />
	{:else if name === 'brain'}
		<path d="M12 5.5a2.5 2.5 0 0 0-4.8 1A2.6 2.6 0 0 0 5 9.4a2.6 2.6 0 0 0 1 4.8A2.6 2.6 0 0 0 8.5 19 2.5 2.5 0 0 0 12 18V5.5Z" />
		<path d="M12 5.5a2.5 2.5 0 0 1 4.8 1A2.6 2.6 0 0 1 19 9.4a2.6 2.6 0 0 1-1 4.8A2.6 2.6 0 0 1 15.5 19 2.5 2.5 0 0 1 12 18" />
	{:else if name === 'pulse'}
		<path d="M2.5 12h4l2-5 4 10 2-5h7" />
	{:else if name === 'sync'}
		<path d="M4 11a8 8 0 0 1 13.6-5.6L20 8" />
		<path d="M20 4v4h-4" />
		<path d="M20 13a8 8 0 0 1-13.6 5.6L4 16" />
		<path d="M4 20v-4h4" />
	{:else if name === 'plus'}
		<path d="M12 5v14M5 12h14" />
	{:else if name === 'minus'}
		<path d="M5 12h14" />
	{:else if name === 'dot'}
		<circle cx="12" cy="12" r="4" fill="currentColor" stroke="none" />
	{:else if name === 'pill'}
		<rect x="3.5" y="8.5" width="17" height="7" rx="3.5" transform="rotate(-45 12 12)" />
		<path d="M9 9l6 6" />
	{:else if name === 'microscope'}
		<path d="M6 20h12" />
		<path d="M9 20a5 5 0 0 0 8-5" />
		<path d="m11 4 3 3-4 4-3-3Z" />
		<path d="M13.5 6.5 16 4" />
	{:else if name === 'database'}
		<ellipse cx="12" cy="6" rx="7" ry="3" />
		<path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
		<path d="M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" />
	{:else if name === 'chevron-right'}
		<path d="m9 6 6 6-6 6" />
	{:else if name === 'info'}
		<circle cx="12" cy="12" r="9" />
		<path d="M12 11v5" />
		<path d="M12 7.8h.01" />
	{/if}
</svg>

<style>
	.icon {
		display: inline-block;
		vertical-align: -0.18em;
		flex: none;
	}
</style>
