<script lang="ts">
    import {fly} from "svelte/transition";
    import type {ConfigurableSetting, Module} from "../../../integration/types";
    import {getModuleSettings, setModuleEnabled, setModuleSettings} from "../../../integration/rest";
    import GenericSetting from "../setting/common/GenericSetting.svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    let {module, onClose}: {
        module: Module;
        onClose: () => void;
    } = $props();

    let configurable = $state<ConfigurableSetting | null>(null);

    const displayName = $derived($spaceSeperatedNames ? convertToSpacedString(module.name) : module.name);
    const path = $derived(`clickgui.${module.name}`);

    async function fetchSettings(name: string) {
        const result = await getModuleSettings(name);
        // Ignore a late answer for a module that is no longer open
        if (name === module.name) {
            configurable = result;
        }
    }

    async function updateSettings() {
        if (!configurable) return;

        await setModuleSettings(module.name, $state.snapshot(configurable));
        await fetchSettings(module.name);
    }

    $effect(() => {
        configurable = null;
        fetchSettings(module.name);
    });
</script>

<aside class="pane" transition:fly={{x: 24, duration: 180}}>
    <header>
        <div class="titles">
            <h2>{displayName}</h2>
            <p>{module.description}</p>
        </div>
        <button class="close" aria-label="Close settings" onclick={onClose}>
            <img src="img/clickgui/icon-cross.svg" alt=""/>
        </button>
    </header>

    <button
            class="state"
            class:on={module.enabled}
            onclick={() => setModuleEnabled(module.name, !module.enabled)}
    >
        <span class="dot"></span>
        {module.enabled ? "Enabled" : "Disabled"}
    </button>

    <div class="settings">
        {#if configurable}
            {#each configurable.value as _, i (configurable.value[i].name)}
                <GenericSetting
                        {path}
                        bind:setting={configurable.value[i]}
                        on:change={updateSettings}
                />
            {/each}
        {:else}
            <div class="loading">Loading settings…</div>
        {/if}
    </div>
</aside>

<style lang="scss">
  .pane {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 320px;
    display: flex;
    flex-direction: column;
    background-color: var(--clickgui-pane-background-color);
    border-left: 1px solid var(--clickgui-border-strong-color);
    box-shadow: -16px 0 40px color-mix(in srgb, black 45%, transparent);
    z-index: 2;
  }

  header {
    display: flex;
    align-items: flex-start;
    gap: var(--clickgui-space-3);
    padding: var(--clickgui-space-4) var(--clickgui-space-4) var(--clickgui-space-3);
    border-bottom: 1px solid var(--clickgui-border-color);
  }

  .titles {
    flex: 1;
    min-width: 0;

    h2 {
      font-size: var(--clickgui-font-size-lg);
      font-weight: 600;
      color: var(--clickgui-text-color);
    }

    p {
      margin-top: 4px;
      font-size: var(--clickgui-font-size-xs);
      line-height: 1.45;
      color: var(--clickgui-text-dimmed-color);
    }
  }

  .close {
    all: unset;
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--clickgui-radius-sm);
    cursor: pointer;

    img {
      width: 10px;
      height: 10px;
      opacity: 0.7;
    }

    &:hover {
      background-color: var(--clickgui-hover-overlay-color);

      img {
        opacity: 1;
      }
    }
  }

  .state {
    all: unset;
    margin: var(--clickgui-space-3) var(--clickgui-space-4) 0;
    display: inline-flex;
    align-items: center;
    gap: var(--clickgui-space-2);
    align-self: flex-start;
    padding: 5px 12px;
    border-radius: var(--clickgui-radius-pill);
    font-size: var(--clickgui-font-size-xs);
    font-weight: 600;
    color: var(--clickgui-text-dimmed-color);
    background-color: var(--clickgui-hover-overlay-color);
    border: 1px solid var(--clickgui-border-color);
    cursor: pointer;

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: var(--clickgui-text-dimmed-color);
    }

    &.on {
      color: var(--clickgui-text-color);
      background-color: var(--clickgui-module-enabled-background-color);
      border-color: var(--clickgui-card-enabled-border-color);

      .dot {
        background-color: var(--accent-color);
      }
    }
  }

  .settings {
    flex: 1;
    overflow-y: auto;
    padding: var(--clickgui-space-2) var(--clickgui-space-4) var(--clickgui-space-4);

    &::-webkit-scrollbar {
      width: 3px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: var(--clickgui-radius-pill);
      background-color: var(--clickgui-border-strong-color);
    }
  }

  .loading {
    padding: var(--clickgui-space-4) 0;
    font-size: var(--clickgui-font-size-sm);
    color: var(--clickgui-text-dimmed-color);
  }
</style>
