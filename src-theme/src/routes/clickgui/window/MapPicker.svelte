<script lang="ts">
    import {onDestroy, onMount} from "svelte";
    import {fade} from "svelte/transition";
    import {baritoneGoto, getMap, type MapData} from "../../../integration/rest";

    let {onClose}: { onClose: () => void } = $props();

    const MAX_ZOOM = 16;
    const DRAG_THRESHOLD = 4;
    const REFRESH_INTERVAL = 5000;

    let data = $state<MapData | null>(null);
    let error = $state<string | null>(null);
    let sending = $state(false);

    let viewport: HTMLElement;
    let mapImage = $state<HTMLImageElement | null>(null);

    // View: the image is drawn at `zoom` screen pixels per block, its top-left corner at `pan`
    let zoom = $state(1);
    let minZoom = $state(0.25);
    let panX = $state(0);
    let panY = $state(0);
    let fitted = false;

    let selected = $state<{ x: number; z: number } | null>(null);
    let hover = $state<{ x: number; z: number } | null>(null);

    let drag: { startX: number; startY: number; panX: number; panY: number; moved: boolean } | null = null;
    let refreshTimer: number | undefined;

    const distance = $derived(
        selected && data ? Math.round(Math.hypot(selected.x - data.playerX, selected.z - data.playerZ)) : null
    );

    /** Viewport pixels per screen pixel: the ClickGUI as a whole is scaled with the game's GUI scale. */
    function viewportScale() {
        const rect = viewport.getBoundingClientRect();
        return viewport.offsetWidth > 0 ? rect.width / viewport.offsetWidth : 1;
    }

    function fit() {
        if (!data || !viewport) return;
        const size = Math.min(viewport.offsetWidth, viewport.offsetHeight);
        zoom = Math.max(size / data.size, 0.1);
        minZoom = zoom / 2;
        panX = (viewport.offsetWidth - data.size * zoom) / 2;
        panY = (viewport.offsetHeight - data.size * zoom) / 2;
    }

    async function refresh() {
        try {
            const next = await getMap();
            // Keep the same spot of the world under the view when the player has moved
            if (data && fitted) {
                panX += (next.originX - data.originX) * zoom;
                panY += (next.originZ - data.originZ) * zoom;
            }
            data = next;
            error = null;
            if (!fitted) {
                requestAnimationFrame(() => {
                    fit();
                    fitted = true;
                });
            }
        } catch (e) {
            error = (e as Error).message;
        }
    }

    /** World block under a pointer event, or null outside the map image. */
    function blockAt(event: MouseEvent): { x: number; z: number } | null {
        if (!data || !mapImage) return null;
        const rect = mapImage.getBoundingClientRect();
        const px = (event.clientX - rect.left) / rect.width * data.size;
        const pz = (event.clientY - rect.top) / rect.height * data.size;
        if (px < 0 || pz < 0 || px >= data.size || pz >= data.size) return null;
        return {x: data.originX + Math.floor(px), z: data.originZ + Math.floor(pz)};
    }

    function onPointerDown(event: MouseEvent) {
        if (event.button !== 0) return;
        drag = {startX: event.clientX, startY: event.clientY, panX, panY, moved: false};
    }

    function onPointerMove(event: MouseEvent) {
        hover = blockAt(event);
        if (!drag) return;

        const dx = event.clientX - drag.startX;
        const dy = event.clientY - drag.startY;
        if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;

        drag.moved = true;
        const scale = viewportScale();
        panX = drag.panX + dx / scale;
        panY = drag.panY + dy / scale;
    }

    function onPointerUp(event: MouseEvent) {
        if (drag && !drag.moved) {
            const block = blockAt(event);
            if (block) selected = block;
        }
        drag = null;
    }

    function onWheel(event: WheelEvent) {
        event.preventDefault();
        const rect = viewport.getBoundingClientRect();
        const scale = viewportScale();
        const localX = (event.clientX - rect.left) / scale;
        const localY = (event.clientY - rect.top) / scale;

        const next = Math.min(MAX_ZOOM, Math.max(minZoom, zoom * (event.deltaY < 0 ? 1.25 : 0.8)));
        panX = localX - (localX - panX) * next / zoom;
        panY = localY - (localY - panY) * next / zoom;
        zoom = next;
    }

    function centerOnPlayer() {
        if (!data) return;
        panX = viewport.offsetWidth / 2 - (data.playerX - data.originX) * zoom;
        panY = viewport.offsetHeight / 2 - (data.playerZ - data.originZ) * zoom;
    }

    async function go() {
        if (!selected) return;
        sending = true;
        try {
            await baritoneGoto(selected.x, selected.z);
            onClose();
        } catch (e) {
            error = (e as Error).message;
        } finally {
            sending = false;
        }
    }

    /** Position of a world point inside the transformed map layer, in image pixels. */
    function at(x: number, z: number) {
        if (!data) return "";
        return `left: ${x - data.originX}px; top: ${z - data.originZ}px;`;
    }

    onMount(() => {
        refresh();
        refreshTimer = setInterval(refresh, REFRESH_INTERVAL);
    });

    onDestroy(() => clearInterval(refreshTimer));
</script>

<svelte:window onmouseup={onPointerUp}/>

<div class="map-picker" transition:fade={{duration: 140}}>
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
            class="viewport"
            bind:this={viewport}
            onmousedown={onPointerDown}
            onmousemove={onPointerMove}
            onmouseleave={() => (hover = null)}
            onwheel={onWheel}
    >
        {#if data}
            <div
                    class="layer"
                    style="width: {data.size}px; height: {data.size}px;
                           transform: translate({panX}px, {panY}px) scale({zoom});"
            >
                <img bind:this={mapImage} src={data.image} alt="Map" draggable="false"/>

                {#if data.goal?.active}
                    <div class="marker goal" style={at(data.goal.x + 0.5, data.goal.z + 0.5)}>
                        <span style="transform: translate(-50%, -50%) scale({1 / zoom});"></span>
                    </div>
                {/if}

                {#if selected}
                    <div class="marker pin" style={at(selected.x + 0.5, selected.z + 0.5)}>
                        <span style="transform: translate(-50%, -100%) scale({1 / zoom});">
                            <svg viewBox="0 0 24 32" aria-hidden="true">
                                <path d="M12 0C5.4 0 0 5.2 0 11.7 0 20.5 12 32 12 32s12-11.5 12-20.3C24 5.2 18.6 0 12 0z"/>
                                <circle cx="12" cy="11.5" r="4.5"/>
                            </svg>
                        </span>
                    </div>
                {/if}

                <div class="marker player" style={at(data.playerX, data.playerZ)}>
                    <span style="transform: translate(-50%, -50%) scale({1 / zoom}) rotate({data.playerYaw + 180}deg);">
                        <svg viewBox="0 0 20 20" aria-hidden="true">
                            <path d="M10 1 L17 18 L10 14 L3 18 Z"/>
                        </svg>
                    </span>
                </div>
            </div>
        {:else if error}
            <div class="message">{error}</div>
        {:else}
            <div class="message">Drawing the map…</div>
        {/if}

        <div class="compass">N</div>
        {#if hover}
            <div class="hover">X {hover.x} · Z {hover.z}</div>
        {/if}
    </div>

    <footer>
        <div class="target">
            {#if selected}
                <span class="label">Target</span>
                <span class="coords">X {selected.x} · Z {selected.z}</span>
                {#if distance !== null}
                    <span class="distance">{distance} blocks away</span>
                {/if}
            {:else}
                <span class="hint">Click the map to choose where to walk. Drag to move, scroll to zoom.</span>
            {/if}
        </div>

        <div class="actions">
            <button class="ghost" onclick={centerOnPlayer} disabled={!data}>Center</button>
            <button class="ghost" onclick={refresh}>Refresh</button>
            <button class="ghost" onclick={onClose}>Close</button>
            <button
                    class="primary"
                    onclick={go}
                    disabled={!selected || sending || !data?.baritone}
                    title={data && !data.baritone ? "Baritone is not installed" : ""}
            >
                {sending ? "Starting…" : "Go here"}
            </button>
        </div>
    </footer>
</div>

<style lang="scss">
  .map-picker {
    position: absolute;
    inset: 0;
    z-index: 3;
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 12px;
    border-radius: 14px;
    background-color: var(--clickgui-pane-background-color);
  }

  .viewport {
    position: relative;
    flex: 1;
    overflow: hidden;
    border-radius: 12px;
    cursor: crosshair;
    border: 1px solid var(--clickgui-card-border-color);
    background-color: #070818;
    background-image: linear-gradient(to right, color-mix(in srgb, #8b7bff 6%, transparent) 1px, transparent 1px),
    linear-gradient(to bottom, color-mix(in srgb, #8b7bff 6%, transparent) 1px, transparent 1px);
    background-size: 24px 24px;
  }

  .layer {
    position: absolute;
    left: 0;
    top: 0;
    transform-origin: 0 0;

    img {
      display: block;
      width: 100%;
      height: 100%;
      image-rendering: pixelated;
      pointer-events: none;
      user-select: none;
    }
  }

  .marker {
    position: absolute;
    width: 0;
    height: 0;
    pointer-events: none;

    span {
      position: absolute;
      left: 0;
      top: 0;
      display: block;
      transform-origin: 0 0;
    }
  }

  .marker.player span {
    width: 18px;
    height: 18px;
    transform-origin: 50% 50%;

    svg {
      width: 100%;
      height: 100%;
      fill: #ffffff;
      stroke: var(--clickgui-window-accent-color);
      stroke-width: 2;
      stroke-linejoin: round;
      filter: drop-shadow(0 0 3px rgba(0, 0, 0, 0.8));
    }
  }

  .marker.pin span {
    width: 20px;
    height: 27px;
    transform-origin: 50% 100%;

    svg {
      width: 100%;
      height: 100%;
      filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.6));

      path {
        fill: var(--accent-color);
      }

      circle {
        fill: #ffffff;
      }
    }
  }

  .marker.goal span {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 2px solid #ffffff;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-color) 60%, transparent),
    0 0 10px var(--accent-color);
    transform-origin: 50% 50%;
  }

  .compass,
  .hover {
    position: absolute;
    padding: 4px 9px;
    border-radius: 8px;
    font-size: var(--clickgui-font-size-xs);
    font-weight: 600;
    color: var(--clickgui-text-color);
    background-color: color-mix(in srgb, #070818 80%, transparent);
    border: 1px solid var(--clickgui-border-strong-color);
    pointer-events: none;
  }

  .compass {
    top: 10px;
    left: 10px;
    color: color-mix(in srgb, var(--accent-color) 70%, white);
  }

  .hover {
    bottom: 10px;
    left: 10px;
    font-variant-numeric: tabular-nums;
  }

  .message {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--clickgui-font-size-sm);
    color: var(--clickgui-text-dimmed-color);
  }

  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .target {
    display: flex;
    align-items: baseline;
    gap: 10px;
    min-width: 0;

    .label {
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--clickgui-text-dimmed-color);
    }

    .coords {
      font-size: 14px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--clickgui-text-color);
    }

    .distance,
    .hint {
      font-size: var(--clickgui-font-size-xs);
      color: var(--clickgui-text-dimmed-color);
    }
  }

  .actions {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }

  button {
    all: unset;
    padding: 7px 14px;
    border-radius: 9px;
    font-size: var(--clickgui-font-size-xs);
    font-weight: 600;
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    opacity var(--clickgui-duration-fast) var(--clickgui-ease);

    &:disabled {
      opacity: 0.45;
      cursor: default;
    }
  }

  .primary {
    color: #ffffff;
    background-color: var(--accent-color);

    &:not(:disabled):hover {
      background-color: var(--accent-hover-color);
    }
  }

  .ghost {
    color: var(--clickgui-text-dimmed-color);
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-border-strong-color);

    &:not(:disabled):hover {
      color: var(--clickgui-text-color);
    }
  }
</style>
