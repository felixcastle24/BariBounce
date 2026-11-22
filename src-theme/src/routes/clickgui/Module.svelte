<script lang="ts">
    import {onMount} from "svelte";
    import {
        getModuleSettings,
        setModuleSettings,
        setModuleEnabled,
    } from "../../integration/rest";
    import type {ConfigurableSetting} from "../../integration/types";
    import GenericSetting from "./setting/common/GenericSetting.svelte";
    import {slide} from "svelte/transition";
    import {description as descriptionStore, highlightModuleName} from "./clickgui_store";
    import {setItem} from "../../integration/persistent_storage";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";
    import {scaleFactor} from "./clickgui_store";

    export let name: string;
    export let enabled: boolean;
    export let description: string;
    export let aliases: string[];

    let moduleNameElement: HTMLElement;
    let configurable: ConfigurableSetting;
    const path = `clickgui.${name}`;
    let expanded = false;
    let hasSettings = false;

    onMount(async () => {
        await fetchModuleSettings();
        expanded = localStorage.getItem(path) === "true";
    });

    highlightModuleName.subscribe((m) => {
        if (name !== m) {
            return;
        }

        setTimeout(() => {
            if (!moduleNameElement) {
                return;
            }
            moduleNameElement.scrollIntoView({
                behavior: "smooth",
                block: "center",
            });
        }, 150);
    });

    async function fetchModuleSettings() {
        configurable = await getModuleSettings(name);
        hasSettings = configurable.value.filter(v => v.name !== "Bind" && v.name !== "Hidden").length > 0;
    }

    async function updateModuleSettings() {
        await setModuleSettings(name, configurable);
        await fetchModuleSettings();
    }

    async function toggleModule() {
        await setModuleEnabled(name, !enabled);
    }

    function setDescription() {
        if (!moduleNameElement) return;

        const boundingRect = moduleNameElement.getBoundingClientRect();
        const y = (boundingRect.top + (moduleNameElement.clientHeight / 2)) * (2 / $scaleFactor);

        let moduleDescription = description;
        if (aliases.length > 0) {
            moduleDescription += ` (aka ${aliases.map(name => $spaceSeperatedNames ? convertToSpacedString(name) : name).join(", ")})`;
        }

        // If element is less than 300px from the right, display description on the left
        if (window.innerWidth - boundingRect.right > 300) {
            const x = boundingRect.right * (2 / $scaleFactor);
            descriptionStore.set({
                x,
                y,
                anchor: "right",
                description: moduleDescription
            });
        } else {
            const x = boundingRect.left * (2 / $scaleFactor);

            descriptionStore.set({
                x,
                y,
                anchor: "left",
                description: moduleDescription
            });
        }
    }

    async function toggleExpanded(e: MouseEvent) {
        e.stopPropagation();

        expanded = !expanded;
        await setItem(path, expanded.toString());
    }
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<div
        class="module"
        class:expanded
>
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <div
            class="name"
            on:contextmenu|preventDefault={toggleExpanded}
            on:click={toggleModule}
            on:mouseenter={setDescription}
            on:mouseleave={() => descriptionStore.set(null)}
            bind:this={moduleNameElement}
            class:enabled
            class:highlight={name === $highlightModuleName}
    >
        {$spaceSeperatedNames ? convertToSpacedString(name) : name}

        {#if hasSettings}
            <button
                    class="expand-arrow"
                    aria-label="Expand settings"
                    aria-expanded={expanded}
                    on:click={toggleExpanded}
            >
                <span class="expand-arrow-icon"></span>
            </button>
        {/if}
    </div>

    {#if expanded && configurable}
        <div class="settings" in:slide|global={{ duration: 150 }} out:slide|global={{ duration: 120 }}>
            {#each configurable.value as setting (setting.name)}
                <GenericSetting {path} bind:setting on:change={updateModuleSettings}/>
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">
  .module {
    position: relative;
    padding: 1px var(--clickgui-space-2);

    .name {
      cursor: pointer;
      transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
      color var(--clickgui-duration-fast) var(--clickgui-ease);

      color: var(--clickgui-text-dimmed-color);
      text-align: left;
      font-size: var(--clickgui-font-size-md);
      font-weight: 500;
      position: relative;
      padding: 9px 40px 9px var(--clickgui-space-3);
      border-radius: var(--clickgui-radius-sm);

      /* Enabled indicator bar */
      &::after {
        content: "";
        position: absolute;
        left: 0;
        top: 50%;
        width: 3px;
        height: 0;
        border-radius: var(--clickgui-radius-pill);
        background-color: var(--clickgui-module-enabled-indicator-color);
        transform: translateY(-50%);
        transition: height var(--clickgui-duration-base) var(--clickgui-ease);
      }

      &.highlight::before {
        content: "";
        position: absolute;
        inset: 0;
        border-radius: var(--clickgui-radius-sm);
        box-shadow: 0 0 0 2px var(--clickgui-module-highlight-color) inset;
        pointer-events: none;
      }

      &:hover {
        background-color: var(--clickgui-module-hover-background-color);
        color: var(--clickgui-text-color);
      }

      &.enabled {
        color: var(--clickgui-module-enabled-color);
        background-color: var(--clickgui-module-enabled-background-color);

        &::after {
          height: 16px;
        }
      }
    }

    .settings {
      margin: var(--clickgui-space-1) 0 var(--clickgui-space-2);
      background-color: var(--clickgui-module-settings-background-color);
      border: 1px solid var(--clickgui-border-color);
      border-left: 2px solid var(--clickgui-module-settings-border-color);
      border-radius: var(--clickgui-radius-sm);
      padding: var(--clickgui-space-1) var(--clickgui-space-3) var(--clickgui-space-1) var(--clickgui-space-2);
    }

    .expand-arrow {
      all: unset;
      position: absolute;
      top: 0;
      right: 0;
      width: 36px;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }

    .expand-arrow-icon {
      width: 10px;
      height: 10px;
      display: block;
      background-image: url("/img/clickgui/icon-settings-expand.svg");
      background-position: center;
      background-repeat: no-repeat;
      opacity: 0.4;
      transform: rotate(-90deg);
      transition: opacity var(--clickgui-duration-fast) var(--clickgui-ease),
      transform var(--clickgui-duration-base) var(--clickgui-ease);
    }

    .name:hover .expand-arrow-icon {
      opacity: 0.8;
    }

    &.expanded .expand-arrow-icon {
      transform: rotate(0);
      opacity: 1;
    }
  }
</style>
