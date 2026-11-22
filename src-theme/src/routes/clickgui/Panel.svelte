<script lang="ts">
    import {onMount} from "svelte";
    import type {Module as TModule} from "../../integration/types";
    import {listen} from "../../integration/ws";
    import Module from "./Module.svelte";
    import type {KeyboardKeyEvent, ModuleToggleEvent} from "../../integration/events";
    import {fade} from "svelte/transition";
    import {
        gridSize,
        highlightModuleName,
        maxPanelZIndex,
        scaleFactor,
        showGrid,
        snappingEnabled
    } from "./clickgui_store";
    import {setItem} from "../../integration/persistent_storage";

    export let category: string;
    export let modules: TModule[] = [];
    export let panelIndex: number;
    export let icon: string | undefined = undefined;

    let panelElement: HTMLElement;
    let modulesElement: HTMLElement;
    let expandButtonElement: HTMLElement;

    let moving = false;
    let offsetX = 0;
    let offsetY = 0;

    let scrollPositionSaveTimeout: number | undefined;

    const panelConfig = loadPanelConfig();

    let ignoreGrid = false;

    interface PanelConfig {
        top: number;
        left: number;
        expanded: boolean;
        scrollTop: number;
        zIndex: number;
    }

    function showFallbackIcon(event: Event) {
        (event.currentTarget as HTMLImageElement).src = "img/clickgui/icon-client.svg";
    }

    function clamp(number: number, min: number, max: number) {
        return Math.max(min, Math.min(number, max));
    }

    function loadPanelConfig(): PanelConfig {
        const localStorageItem = localStorage.getItem(
            `clickgui.panel.${category}`,
        );

        if (!localStorageItem) {
            return {
                top: panelIndex * 50 + 20,
                left: 20,
                expanded: false,
                scrollTop: 0,
                zIndex: 0
            };
        } else {
            const config: PanelConfig = JSON.parse(localStorageItem);

            // Migration
            if (!config.zIndex) {
                config.zIndex = 0;
            }

            if (config.zIndex > $maxPanelZIndex) {
                $maxPanelZIndex = config.zIndex;
            }

            return config;
        }
    }

    async function savePanelConfig() {
        await setItem(
            `clickgui.panel.${category}`,
            JSON.stringify(panelConfig),
        );
    }

    function fixPosition() {
        panelConfig.left = clamp(panelConfig.left, 0, document.documentElement.clientWidth * (2 / $scaleFactor) - panelElement.offsetWidth);
        panelConfig.top = clamp(panelConfig.top, 0, document.documentElement.clientHeight * (2 / $scaleFactor) - panelElement.offsetHeight);
    }

    function onMouseDown(e: MouseEvent) {
        if (e.button !== 0 && e.button !== 1) return;

        moving = true;
        offsetX = e.clientX * (2 / $scaleFactor) - panelConfig.left;
        offsetY = e.clientY * (2 / $scaleFactor) - panelConfig.top;
        panelConfig.zIndex = ++$maxPanelZIndex;

        $showGrid = $snappingEnabled && !expandButtonElement.contains(e.target as HTMLElement);
    }

    function onMouseMove(e: MouseEvent) {
        if (moving) {
            const newLeft = (e.clientX * (2 / $scaleFactor) - offsetX);
            const newTop = (e.clientY * (2 / $scaleFactor) - offsetY);

            panelConfig.left = snapToGrid(newLeft);
            panelConfig.top = snapToGrid(newTop);

            fixPosition();
        }
    }

    function onMouseUp() {
        if (moving) {
            savePanelConfig();
        }
        moving = false;
        $showGrid = false;
    }

    function toggleExpanded() {
        panelConfig.expanded = !panelConfig.expanded;

        savePanelConfig();
    }

    /**
     * The panel only reaches its new height once the `max-height` transition of the module
     * list has finished, so the position can only be clamped here. Doing it right after the
     * toggle would measure the height the panel had *before* it was expanded or collapsed.
     */
    function handleModulesTransitionEnd(e: TransitionEvent) {
        if (e.target !== modulesElement || e.propertyName !== "max-height") {
            return;
        }

        const {left, top} = panelConfig;

        fixPosition();

        if (panelConfig.left !== left || panelConfig.top !== top) {
            savePanelConfig();
        }
    }

    function handleModulesScroll() {
        panelConfig.scrollTop = modulesElement.scrollTop;

        if (scrollPositionSaveTimeout !== undefined) {
            clearTimeout(scrollPositionSaveTimeout);
        }
        scrollPositionSaveTimeout = setTimeout(() => {
            savePanelConfig();
        }, 500)
    }

    highlightModuleName.subscribe((name) => {
        const highlightModule = modules.find(
            (m) => m.name === name,
        );
        if (highlightModule) {
            panelConfig.zIndex = ++$maxPanelZIndex;
            panelConfig.expanded = true;
            savePanelConfig();
        }
    });

    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const moduleName = e.moduleName;
        const moduleEnabled = e.enabled;

        const mod = modules.find((m) => m.name === moduleName);
        if (!mod) return;

        mod.enabled = moduleEnabled;
        modules = modules;
    });

    onMount(() => {
        if (!modulesElement) {
            return;
        }

        modulesElement.scrollTop = panelConfig.scrollTop;
    });

    listen("keyboardKey", (e: KeyboardKeyEvent) => {
        if (e.key === "key.keyboard.left.shift") {
            ignoreGrid = e.action === 1;
        }
    });

    function snapToGrid(value: number): number {
        if (ignoreGrid || !$snappingEnabled) return value;

        return Math.round(value / $gridSize) * $gridSize;
    }
</script>

<svelte:window on:mouseup={onMouseUp} on:mousemove={onMouseMove}/>

<div
        class="panel"
        style="left: {panelConfig.left}px; top: {panelConfig.top}px; z-index: {panelConfig.zIndex};"
        bind:this={panelElement}
        transition:fade|global={{duration: 150}}
>
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div
            class="title"
            on:mousedown={onMouseDown}
            on:contextmenu|preventDefault={toggleExpanded}
    >
        <img
                class="icon"
                src={icon ?? `img/clickgui/icon-${category.toLowerCase()}.svg`}
                alt="icon"
                on:error={showFallbackIcon}
        />
        <span class="category">{category}</span>

        <!-- svelte-ignore a11y_consider_explicit_label -->
        <button class="expand-toggle" on:click={toggleExpanded} bind:this={expandButtonElement}>
            <div class="icon" class:expanded={panelConfig.expanded}></div>
        </button>
    </div>

    <div
            class="modules"
            class:expanded={panelConfig.expanded}
            on:scroll={handleModulesScroll}
            on:transitionend={handleModulesTransitionEnd}
            bind:this={modulesElement}
    >
        <slot>
            {#each modules as {name, enabled, description, aliases} (name)}
                <Module {name} {enabled} {description} {aliases}/>
            {/each}
        </slot>
    </div>
</div>

<style lang="scss">
  .panel {
    border-radius: var(--clickgui-radius-lg);
    width: 250px;
    position: absolute;
    overflow: hidden;
    border: 1px solid var(--clickgui-border-color);
    box-shadow: var(--clickgui-elevation-1);
    backdrop-filter: blur(var(--clickgui-blur)) saturate(140%);
    will-change: transform;
    transition: none;
    user-select: none;
  }

  .title {
    display: grid;
    grid-template-columns: max-content 1fr max-content;
    align-items: center;
    column-gap: var(--clickgui-space-3);
    background-color: var(--clickgui-panel-header-background-color);
    border-bottom: 1px solid var(--clickgui-panel-header-border-color);
    padding: var(--clickgui-space-3) var(--clickgui-space-4);
    cursor: grab;

    &:active {
      cursor: grabbing;
    }

    > .icon {
      width: 16px;
      height: 16px;
      opacity: 0.9;
    }

    .category {
      font-size: var(--clickgui-font-size-sm);
      color: var(--clickgui-text-color);
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
  }

  .modules {
    transition: max-height var(--clickgui-duration-base) var(--clickgui-ease);
    scroll-behavior: smooth;
    max-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    background-color: var(--clickgui-panel-body-background-color);

    &.expanded {
      max-height: 545px;
      padding: var(--clickgui-space-1) 0;
    }

    &::-webkit-scrollbar {
      width: 3px;
      height: 3px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: var(--clickgui-radius-pill);
      background-color: var(--clickgui-border-strong-color);
    }
  }

  .expand-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    background-color: transparent;
    border: none;
    border-radius: var(--clickgui-radius-sm);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: var(--clickgui-hover-overlay-color);
    }

    /* Chevron: points right when collapsed, down when expanded */
    .icon {
      width: 7px;
      height: 7px;
      border-right: 2px solid var(--clickgui-panel-toggle-icon-color);
      border-bottom: 2px solid var(--clickgui-panel-toggle-icon-color);
      transform: translateX(-1px) rotate(-45deg);
      transition: transform var(--clickgui-duration-base) var(--clickgui-ease);

      &.expanded {
        transform: translateY(-2px) rotate(45deg);
      }
    }
  }
</style>
