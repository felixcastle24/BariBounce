<script lang="ts">
    import type {Component} from "svelte";
    import {fly} from "svelte/transition";

    type Tab = {
        title: string;
        content: Component;
    };

    let {tabs, activeTab = $bindable(0)} = $props<{
        tabs: Tab[];
        activeTab?: number;
    }>();

    const Active = $derived(tabs[activeTab]?.content);
</script>

<div class="tabs" transition:fly={{duration: 150, y: -15}}>
    <div class="available-tabs">
        {#each tabs as tab, index (tab.title)}
            <button
                    class="tab-button"
                    class:active={index === activeTab}
                    onclick={() => (activeTab = index)}
                    type="button"
            >
                {tab.title}
            </button>
        {/each}
    </div>

    <div class="content">
        {#if Active}
            {@render Active()}
        {/if}
    </div>
</div>

<style lang="scss">
  .available-tabs {
    position: fixed;
    top: 15px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: var(--clickgui-space-1);
    padding: var(--clickgui-space-1);
    border-radius: var(--clickgui-radius-pill);
    background-color: var(--clickgui-tabs-background-color);
    border: 1px solid var(--clickgui-border-color);
    box-shadow: var(--clickgui-elevation-1);
    backdrop-filter: blur(var(--clickgui-blur)) saturate(140%);
    z-index: 9999999999;
  }

  .tab-button {
    background: transparent;
    color: var(--clickgui-text-dimmed-color);
    padding: 6px 16px;
    font-family: "Inter", sans-serif;
    font-size: var(--clickgui-font-size-md);
    font-weight: 600;
    border-radius: var(--clickgui-radius-pill);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-base) var(--clickgui-ease),
    color var(--clickgui-duration-base) var(--clickgui-ease);
    border: solid 1px transparent;

    &:hover {
      color: var(--clickgui-text-color);
      background-color: var(--clickgui-tab-hover-background-color);
    }

    &.active {
      color: #ffffff;
      background-color: var(--clickgui-tab-active-background-color);
      border-color: var(--clickgui-tab-active-border-color);
    }
  }
</style>
